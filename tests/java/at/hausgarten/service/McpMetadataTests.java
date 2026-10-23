package at.hausgarten.service;
import org.json.*;
import java.util.Arrays;

/** Real metadata responses stay consistent, fresh and read-only; timing is host CPU only. */
public final class McpMetadataTests {
    public static void main(String[] unused)throws Exception{
        int[] writes={0};JSONObject state=SyntheticBusinessData.object().put("master",SyntheticBusinessData.object().put("customers",new JSONArray()).put("orders",new JSONArray()).put("worklogs",new JSONArray()).put("invoices",new JSONArray())).put("pending",new JSONArray());
        PixelMcpWriteTools.Sink sink=new PixelMcpWriteTools.Sink(){public JSONObject execute(String name,JSONObject args){writes[0]++;throw new AssertionError("Metadata wrote data");}public JSONObject status(String id,JSONObject s){return new JSONObject();}};
        PixelMcpReadTools tools=new PixelMcpReadTools(()->state,sink);
        JSONObject first=tools.call("get_capabilities",new JSONObject()).getJSONObject("data");
        if(!first.getString("tool_catalog_hash").equals(PixelMcpWriteTools.hash(first.getJSONArray("tools"))))throw new AssertionError("Catalog hash differs from advertised tools");
        first.getJSONArray("tools").remove(0);
        if(tools.call("get_capabilities",new JSONObject()).getJSONObject("data").getJSONArray("tools").length()!=117)throw new AssertionError("Caller changed subsequent metadata");
        for(String name:new String[]{"get_capabilities","integration_health"}){
            for(int i=0;i<5;i++)tools.call(name,new JSONObject());long[] samples=new long[15];
            for(int i=0;i<samples.length;i++){long start=System.nanoTime();JSONObject data=tools.call(name,new JSONObject()).getJSONObject("data");samples[i]=System.nanoTime()-start;if(data.getString("tool_catalog_hash").isEmpty())throw new AssertionError("Missing hash");}
            Arrays.sort(samples);System.out.println("MCP_METADATA_CPU "+new JSONObject().put("tool",name).put("samples",samples.length).put("p50_ms",samples[7]/1e6).put("p95_ms",samples[14]/1e6).put("native_or_network_measured",false));
        }
        if(writes[0]!=0)throw new AssertionError("Metadata must remain read-only");
        System.out.println("PASS metadata catalog/hash consistency, fresh results and zero writes; host CPU measurements only");
    }
}
