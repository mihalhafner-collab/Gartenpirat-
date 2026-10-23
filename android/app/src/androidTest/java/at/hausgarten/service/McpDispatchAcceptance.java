package at.hausgarten.service;

import android.content.Context;
import java.io.File;
import org.json.*;

/** Every registered tool crosses the real JSON-RPC boundary in its own disposable store. */
final class McpDispatchAcceptance {
    static Object value(JSONObject schema,String key)throws Exception{
        JSONArray choices=schema.optJSONArray("enum");if(choices!=null&&choices.length()>0)return choices.get(0);
        Object declared=schema.opt("type");String type=declared instanceof JSONArray?((JSONArray)declared).getString(0):String.valueOf(declared);
        if(type.equals("object")){
            JSONObject out=new JSONObject(),props=schema.optJSONObject("properties");JSONArray required=schema.optJSONArray("required");
            if(required!=null)for(int i=0;i<required.length();i++){String k=required.getString(i);out.put(k,value(props.getJSONObject(k),k));}
            if(out.length()==0&&schema.optInt("minProperties")>0&&props!=null&&props.keys().hasNext()){String k=props.keys().next();out.put(k,value(props.getJSONObject(k),k));}return out;
        }
        if(type.equals("array")){JSONArray out=new JSONArray();for(int i=0;i<schema.optInt("minItems",0);i++)out.put(value(schema.getJSONObject("items"),key));return out;}
        if(type.equals("boolean"))return false;
        if(type.equals("integer"))return Long.valueOf(Math.max(0,schema.optLong("minimum",0)));
        if(type.equals("number"))return Math.max(0,schema.optDouble("minimum",0));
        String pattern=schema.optString("pattern");if(pattern.equals("[0-9a-f]{64}"))return PixelMcpWriteTools.hash("qa_dispatch");if(!pattern.isEmpty())return "QA";
        String text=key.equals("request_id")?"qa_dispatch_request":key.contains("date")?"2026-10-23":key.contains("base64")?"UUE=":key.equals("mime_type")?"text/plain":key.equals("name")||key.equals("activity")||key.equals("title")?"QA Dispatch probe":"qa_missing";
        while(text.length()<schema.optInt("minLength",0))text+="q";return text.substring(0,Math.min(text.length(),schema.optInt("maxLength",text.length())));
    }
    static void remove(File root){boolean link=java.nio.file.Files.isSymbolicLink(root.toPath());if(root.isDirectory()&&!link){File[] children=root.listFiles();if(children!=null)for(File child:children)remove(child);}if((root.exists()||link)&&!root.delete())throw new AssertionError("Disposable MCP test file not removed");}
    static void cleanup(OwnerNativeAcceptance.Fixture f){
        Context base=f.context.getBaseContext();String prefix=f.context.prefix;f.store.close();
        // databaseList includes journal/WAL sidecars already removed with their database.
        for(String name:base.databaseList())if(name.startsWith(prefix)&&base.getDatabasePath(name).exists()&&!base.deleteDatabase(name))throw new AssertionError("Disposable MCP database not removed");
        File prefs=new File(base.getApplicationInfo().dataDir,"shared_prefs");String[] names=prefs.list();if(names!=null)for(String name:names)if(name.startsWith(prefix)&&name.endsWith(".xml")){String n=name.substring(0,name.length()-4);if(!base.getSharedPreferences(n,0).edit().commit())throw new AssertionError("Disposable preference writes not settled");base.deleteSharedPreferences(n);}
        remove(f.context.getFilesDir());remove(f.context.getCacheDir());remove(f.context.getNoBackupFilesDir());
        for(String name:base.databaseList())if(name.startsWith(prefix))throw new AssertionError("Disposable MCP database remains");
        names=prefs.list();if(names!=null)for(String name:names)if(name.startsWith(prefix))throw new AssertionError("Disposable MCP preferences remain");
    }
    static void run(PixelMcpInstrumentation test)throws Exception{
        test.probe("advertised empty authority passes guarded native settings update",()->{
            OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext());
            try{
                JSONObject current=f.call("get_business_settings",new JSONObject());
                f.call("update_business_settings",new JSONObject().put("request_id","qa_empty_authority").put("expected_record_hash",current.getString("record_hash")).put("updates",new JSONObject().put("invoice_numbering_authority","")));
                test.yes(f.store.pendingActions().length()==1);
                test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("invoices").length()==0);
            }finally{cleanup(f);}
        });
        JSONArray catalog;
        OwnerNativeAcceptance.Fixture initial=new OwnerNativeAcceptance.Fixture(test.getTargetContext());try{catalog=initial.tools.tools();}finally{cleanup(initial);}
        for(int i=0;i<catalog.length();i++){
            JSONObject spec=catalog.getJSONObject(i);String name=spec.getString("name");
            test.probe("real JSON-RPC dispatch and isolated cleanup: "+name,()->{
                OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext());
                try{
                    JSONObject args=(JSONObject)value(spec.getJSONObject("inputSchema"),"arguments");
                    if(args.has("request_id"))args.put("request_id","qa_dispatch_"+name);
                    if(spec.getJSONObject("inputSchema").getJSONObject("properties").has("test_record"))args.put("test_record",true);
                    McpSchema.validate(args,spec.getJSONObject("inputSchema"));
                    JSONObject request=new JSONObject().put("jsonrpc","2.0").put("id",1).put("method","tools/call").put("params",new JSONObject().put("name",name).put("arguments",args));
                    JSONObject response=f.tools.rpc(request);test.yes(response.has("result"));
                    // Guarded errors are valid negative paths, not successful end-to-end provider tests.
                    request.getJSONObject("params").put("arguments",new JSONObject().put("_qa_unknown",true));
                    test.yes(f.tools.rpc(request).getJSONObject("result").getBoolean("isError"));
                }finally{cleanup(f);}
            });
        }
        McpAuditTools.report(test,catalog);
    }
    private McpDispatchAcceptance(){}
}
