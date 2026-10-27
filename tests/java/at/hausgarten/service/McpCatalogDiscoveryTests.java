package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Synthetic discovery only: no business writes, credentials or live tunnel. */
public final class McpCatalogDiscoveryTests {
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static JSONObject rpc(int id,String method){return new JSONObject().put("jsonrpc","2.0").put("id",id).put("method",method);}
    private static Set<String> names(JSONArray rows)throws Exception{
        Set<String> names=new HashSet<>();
        for(int i=0;i<rows.length();i++){
            JSONObject t=rows.getJSONObject(i);String name=t.getString("name");
            require(name.matches("[A-Za-z0-9_-]{1,64}"),"Invalid tool name: "+name);
            require(names.add(name),"Duplicate tool: "+name);
            require(!t.getString("description").isBlank(),"Missing description: "+name);
            JSONObject s=t.getJSONObject("inputSchema");require(s.getString("type").equals("object"),"Non-object input: "+name);
            JSONArray required=s.getJSONArray("required");JSONObject props=s.getJSONObject("properties");
            for(int j=0;j<required.length();j++)require(props.has(required.getString(j)),"Unknown required field: "+name);
        }
        return names;
    }
    public static void main(String[] args)throws Exception{
        JSONObject state=SyntheticBusinessData.object().put("master",SyntheticBusinessData.object().put("customers",new JSONArray()).put("orders",new JSONArray()).put("worklogs",new JSONArray()).put("invoices",new JSONArray())).put("pending",new JSONArray());
        String before=PixelMcpWriteTools.hash(state);int[] writes={0},reads={0};
        PixelMcpWriteTools.Sink sink=new PixelMcpWriteTools.Sink(){
            public JSONObject execute(String name,JSONObject a){writes[0]++;throw new AssertionError("Discovery attempted a write");}
            public JSONObject status(String id,JSONObject s){throw new AssertionError("Discovery queried action status");}
        };
        PixelMcpReadTools tools=new PixelMcpReadTools(()->{reads[0]++;return state;},sink);
        JSONArray full=tools.tools();Set<String> fullNames=names(full);String semantic=CatalogSemanticHash.hash(full);
        for(String protocol:new String[]{"2024-11-05","2025-03-26","2025-06-18","2025-11-25"}){
            JSONObject init=tools.rpc(rpc(1,"initialize").put("params",new JSONObject().put("protocolVersion",protocol))).getJSONObject("result");
            require(init.getString("protocolVersion").equals(protocol),"Protocol mismatch");
            JSONArray listed=tools.rpc(rpc(2,"tools/list")).getJSONObject("result").getJSONArray("tools");
            require(CatalogSemanticHash.hash(listed).equals(semantic),"RPC discovery lost tools");
        }
        require(reads[0]==0,"tools/list read operating state");
        JSONObject caps=tools.call("get_capabilities",new JSONObject()).getJSONObject("data"),health=tools.call("integration_health",new JSONObject()).getJSONObject("data");
        require(CatalogSemanticHash.hash(caps.getJSONArray("tools")).equals(semantic),"Capabilities differ from tools/list");
        require(health.getInt("tool_count")==full.length(),"Health count differs");
        require(caps.getString("tool_catalog_hash").equals(health.getString("tool_catalog_hash")),"Health hash differs");
        require(caps.getBoolean("write_enabled"),"Synthetic writable gate missing");
        PixelMcpReadTools readonly=new PixelMcpReadTools(()->state);
        Set<String> readNames=names(readonly.rpc(rpc(3,"tools/list")).getJSONObject("result").getJSONArray("tools"));
        for(String name:LocalWorkflowTools.READS)require(fullNames.contains(name)&&readNames.contains(name),"Local read missing: "+name);
        for(String name:LocalWorkflowTools.WRITES)require(fullNames.contains(name)&&!readNames.contains(name),"Write gate mismatch: "+name);
        final List<JSONObject> posted=new ArrayList<>();
        PixelTunnelWire.Transport transport=(method,path,body,shard,timeout)->{
            if(method.equals("POST")){posted.add(new JSONObject(body.toString()));return new PixelTunnelWire.Reply(204,new JSONObject());}
            JSONObject command=new JSONObject().put("request_id","qa_catalog_discovery").put("shard_token","qa_catalog_shard").put("command_type","jsonrpc").put("channel","main").put("jsonrpc",rpc(4,"tools/list"));
            return new PixelTunnelWire.Reply(200,new JSONObject().put("commands",new JSONArray().put(command)));
        };
        PixelTunnelWire wire=new PixelTunnelWire("tunnel_"+"a".repeat(32),transport,tools);
        require(wire.cycle()==1&&posted.size()==1,"Tunnel response missing");
        JSONArray delivered=posted.get(0).getJSONObject("resp_json").getJSONObject("result").getJSONArray("tools");
        require(CatalogSemanticHash.hash(delivered).equals(semantic),"Tunnel serialization lost definitions");
        require(writes[0]==0&&before.equals(PixelMcpWriteTools.hash(state)),"Discovery mutated synthetic state");
        System.out.println("CATALOG_JSON "+new JSONObject().put("tools",delivered).put("semantic",CatalogSemanticHash.describe(delivered)).put("read_only_count",readNames.size()).put("local_reads",new JSONArray(LocalWorkflowTools.READS)).put("local_writes",new JSONArray(LocalWorkflowTools.WRITES)).put("business_writes",writes[0]));
        System.out.println("PASS discovery, four protocols, complete local block, capabilities/health, write gate, tunnel serialization and zero writes");
    }
}
