package at.hausgarten.service;

import android.app.Instrumentation;
import android.os.Bundle;
import org.json.*;
import java.util.*;

/** Test-only observation of the real production router. Never logs arguments or records. */
final class McpAuditTools {
    private final PixelMcpReadTools actual;
    private static final Map<String,int[]> COUNTS=new TreeMap<>();
    private static final Set<String> RPC=new HashSet<>();
    private static final Map<String,Set<String>> CODES=new TreeMap<>();
    private static final Map<String,long[]> TIMES=new TreeMap<>();
    McpAuditTools(PixelMcpReadTools.Source source,PixelMcpWriteTools.Sink writer,PixelMcpOwnerTools.Access access){actual=new PixelMcpReadTools(source,writer,access);}
    McpAuditTools(PixelMcpReadTools.Source source,PixelMcpWriteTools.Sink writer){this(source,writer,null);}
    JSONArray tools()throws Exception{return actual.tools();}
    static void count(String name,boolean success){int[] c=COUNTS.computeIfAbsent(name,k->new int[2]);c[success?0:1]++;}
    static void elapsed(String name,long started){long duration=System.nanoTime()-started;long[] t=TIMES.computeIfAbsent(name,k->new long[2]);t[0]+=duration;t[1]=Math.max(t[1],duration);}
    JSONObject call(String name,JSONObject args)throws Exception{
        long started=System.nanoTime();
        try{JSONObject result=actual.call(name,args);count(name,result.optBoolean("ok"));return result;}
        catch(Exception error){count(name,false);CODES.computeIfAbsent(name,k->new TreeSet<>()).add(error instanceof PixelMcpWriteTools.Rejected?((PixelMcpWriteTools.Rejected)error).code:error.getClass().getSimpleName());throw error;}
        finally{elapsed(name,started);}
    }
    JSONObject rpc(JSONObject request)throws Exception{
        long started=System.nanoTime();
        JSONObject result=actual.rpc(request);
        if(request.optString("method").equals("tools/call")){
            String name=request.getJSONObject("params").getString("name");JSONObject body=result.getJSONObject("result");
            boolean success=!body.optBoolean("isError");count(name,success);RPC.add(name);
            if(!success){String text=body.toString();if(text.contains("Unbekanntes Werkzeug")||text.contains("Unknown tool")||text.contains("UNKNOWN_TOOL")||text.contains("Eigentümerfunktion fehlt")||text.contains("Lesefunktion fehlt")||text.contains("UNSUPPORTED_TOOL")||text.contains("NOT_IMPLEMENTED"))throw new AssertionError("Registered tool has no production handler: "+name);
                JSONObject content=body.optJSONObject("structuredContent"),error=content==null?null:content.optJSONObject("error");String code=error==null?"GUARDED_ERROR":error.optString("code","GUARDED_ERROR");if(code.matches("[A-Z0-9_]{1,60}"))CODES.computeIfAbsent(name,k->new TreeSet<>()).add(code);
            }
            elapsed(name,started);
        }
        return result;
    }
    static void report(Instrumentation runner,JSONArray catalog)throws Exception{
        Bundle metadata=new Bundle();metadata.putString("stream","MCP_CATALOG "+new JSONObject().put("tools",catalog.length()).put("wire_hash",PixelMcpWriteTools.hash(catalog)).put("semantic",CatalogSemanticHash.describe(catalog))+"\n");runner.sendStatus(0,metadata);
        for(int i=0;i<catalog.length();i++){
            String name=catalog.getJSONObject(i).getString("name");int[] c=COUNTS.getOrDefault(name,new int[2]);
            long[] t=TIMES.getOrDefault(name,new long[2]);JSONObject row=new JSONObject().put("tool",name).put("success_calls",c[0]).put("guard_rejections",c[1]).put("guard_codes",new JSONArray(CODES.getOrDefault(name,Collections.emptySet()))).put("real_rpc_dispatched",RPC.contains(name)).put("total_ms",t[0]/1e6).put("max_ms",t[1]/1e6).put("measurement","isolated emulator router/SQLite; no productive provider latency");
            Bundle b=new Bundle();b.putString("stream","MCP_AUDIT "+row+"\n");runner.sendStatus(0,b);
        }
    }
}
