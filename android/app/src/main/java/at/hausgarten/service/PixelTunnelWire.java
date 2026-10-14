package at.hausgarten.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** OpenAI's public plural /v1/tunnels wire contract; no incoming network port. */
public final class PixelTunnelWire {
    public interface Transport { Reply request(String method,String path,JSONObject body,String shard,int timeoutMs) throws Exception; }
    public static final class Reply {
        public final int code; public final JSONObject json; public final long receivedNanos;
        public Reply(int code,JSONObject json){this(code,json,System.nanoTime());}
        public Reply(int code,JSONObject json,long time){this.code=code;this.json=json;this.receivedNanos=time;}
    }
    public static final class AccessError extends IOException {
        public final int httpCode; public final String reason;
        public AccessError(int code){this(code,"auth_denied");}
        public AccessError(int code,String reason){super("Tunnel-ID oder Runtime-Berechtigung prüfen.");httpCode=code;this.reason=reason;}
        public String userMessage(){
            switch(reason){
                case "organization_required":return "Die OpenAI-Organisationsangabe fehlt. Verbindungseinstellungen öffnen und die Organisation des Pixel-Tunnels bestätigen.";
                case "organization_mismatch":return "Der gespeicherte Schlüssel gehört zu einer anderen OpenAI-Organisation als der Pixel-Tunnel. Bitte einen Schlüssel aus der Organisation des Tunnels eingeben.";
                case "tunnel_access_denied":return "Der Schlüssel darf diesen Tunnel nicht verwenden. Im richtigen OpenAI-Konto Tunnels Read + Use prüfen.";
                case "invalid_api_key":return "OpenAI erkennt den gespeicherten Schlüssel nicht. Bitte den gültigen Runtime-Schlüssel erneut im privaten Einrichtungsfenster eingeben.";
                default:return "OpenAI verweigert die Tunnel-Anmeldung (HTTP "+httpCode+"). Organisation, Schlüssel und Tunnels Read + Use prüfen.";
            }
        }
    }
    private final String id;
    private final Transport transport;
    private final PixelMcpReadTools tools;
    private static final class Cached {
        final String fingerprint; final JSONObject response;
        Cached(String fingerprint,JSONObject response){this.fingerprint=fingerprint;this.response=response;}
    }
    private final Map<String,Cached> results=new LinkedHashMap<String,Cached>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Cached> e){return size()>128;}
    };
    public PixelTunnelWire(String id,Transport transport,PixelMcpReadTools tools){
        PixelTunnelConfig.validateId(id);this.id=id;this.transport=transport;this.tools=tools;
    }
    private String path(String suffix){return "/v1/tunnels/"+id+suffix;}
    private static void check(Reply r) throws IOException {
        if(r.code==401||r.code==403)throw new AccessError(r.code,r.json.optString("access_reason","auth_denied"));
        if(r.code<200||r.code>=300)throw new IOException("Tunnel vorübergehend nicht erreichbar (HTTP "+r.code+").");
    }
    public void verify() throws Exception {check(transport.request("GET",path(""),null,null,15000));}
    static long deadline(Object timeout,long receipt) {
        if(!(timeout instanceof String)||!((String)timeout).matches("[0-9]+(?:ns|us|ms|s|m|h)"))return Long.MAX_VALUE;
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9]+)([a-z]+)").matcher((String)timeout);m.matches();
        try{long amount=Long.parseLong(m.group(1)),factor;
            switch(m.group(2)){case "ns":factor=1;break;case "us":factor=1000;break;case "ms":factor=1000000;break;case "s":factor=1000000000;break;case "m":factor=60000000000L;break;default:factor=3600000000000L;}
            return Math.addExact(receipt,Math.multiplyExact(amount,factor));
        }catch(Exception invalid){return Long.MAX_VALUE;}
    }
    public int cycle() throws Exception {
        Reply poll=transport.request("GET",path("/poll?limit=1&timeout_ms=15000"),null,null,35000);check(poll);
        if(poll.code==204)return 0;
        JSONArray commands=poll.json.getJSONArray("commands");int delivered=0;
        for(int i=0;i<commands.length();i++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            JSONObject command=commands.getJSONObject(i);long expiry=deadline(command.opt("response_timeout"),poll.receivedNanos);
            if(expiry!=Long.MAX_VALUE&&System.nanoTime()>=expiry)continue;
            String requestId=command.getString("request_id"),shard=command.getString("shard_token");
            if(requestId.isEmpty()||requestId.length()>4096||shard.isEmpty()||shard.length()>16384||shard.indexOf('\r')>=0||shard.indexOf('\n')>=0)throw new IOException("Ungültige Tunnel-Antwort.");
            String type=command.optString("command_type"),channel=command.optString("channel","main");
            if(!"jsonrpc".equals(type)&&!"session_termination".equals(type))continue;
            String fingerprint=type+"\n"+channel+"\n"+canonical(command.optJSONObject("jsonrpc"));
            Cached cached=results.get(requestId);
            if(cached!=null&&!cached.fingerprint.equals(fingerprint))throw new IOException("Widersprüchliche Anfrage-ID. Verbindung wird erneut geprüft.");
            JSONObject response=cached==null?null:cached.response;
            if(response==null){
                response=new JSONObject().put("request_id",requestId).put("channel",channel);
                if(type.equals("session_termination"))response.put("resp_code",204).put("resp_type","session_termination_response");
                else{
                    JSONObject rpc=command.getJSONObject("jsonrpc"),result;
                    if(!"main".equals(channel))result=new JSONObject().put("jsonrpc","2.0").put("id",rpc.opt("id")==null?JSONObject.NULL:rpc.opt("id")).put("error",new JSONObject().put("code",-32601).put("message","Channel nicht unterstützt."));
                    else result=tools.rpc(rpc);
                    response.put("resp_code",result==null?202:200).put("resp_type",result==null?"notify_ack":"jsonrpc_response");
                    if(result!=null)response.put("resp_json",result).put("resp_headers",new JSONObject().put("Content-Type",new JSONArray().put("application/json")));
                }
                results.put(requestId,new Cached(fingerprint,response));
            }
            if(expiry!=Long.MAX_VALUE&&System.nanoTime()>=expiry)continue;
            int timeout=expiry==Long.MAX_VALUE?15000:(int)Math.max(1,Math.min(15000,(expiry-System.nanoTime())/1000000));
            check(transport.request("POST",path("/response"),response,shard,timeout));delivered++;
        }
        return delivered;
    }
    private static String canonical(Object value) throws Exception {
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> iterator=object.keys();while(iterator.hasNext())keys.add(iterator.next());Collections.sort(keys);
            StringBuilder out=new StringBuilder("{");for(String key:keys)out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))).append(',');return out.append('}').toString();}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<array.length();i++)out.append(canonical(array.get(i))).append(',');return out.append(']').toString();}
        return value==null||value==JSONObject.NULL?"null":value instanceof String?JSONObject.quote((String)value):value.toString();
    }
    public static final class HttpTransport implements Transport {
        private final String root,secret,organization,instance=UUID.randomUUID().toString();
        private volatile HttpURLConnection active;
        public HttpTransport(String secret){this("https://api.openai.com",secret,false);}
        public HttpTransport(String secret,String organization){this("https://api.openai.com",secret,false,organization);}
        /** Loopback-only endpoint injection for instrumentation, never exposed in UI. */
        public HttpTransport(String root,String secret,boolean test){
            this(root,secret,test,"");
        }
        public HttpTransport(String root,String secret,boolean test,String organization){
            if(!root.equals("https://api.openai.com")&&!(test&&root.matches("http://127\\.0\\.0\\.1:[0-9]{1,5}")))throw new IllegalArgumentException("Nicht erlaubtes Tunnel-Ziel.");
            PixelTunnelConfig.validateOrganization(organization);
            this.root=root;this.secret=secret;this.organization=organization;
        }
        public void close(){HttpURLConnection current=active;if(current!=null)current.disconnect();}
        @Override public Reply request(String method,String path,JSONObject body,String shard,int timeout) throws Exception {
            if(!path.startsWith("/v1/tunnels/")||path.contains(".."))throw new IOException("Ungültiger Tunnel-Pfad.");
            HttpURLConnection connection=(HttpURLConnection)new URL(root+path).openConnection();active=connection;
            try{
                connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(Math.min(timeout,15000));connection.setReadTimeout(timeout);connection.setRequestMethod(method);
                connection.setRequestProperty("Authorization","Bearer "+secret);connection.setRequestProperty("Accept","application/json");
                if(!organization.isEmpty())connection.setRequestProperty("OpenAI-Organization",organization);
                connection.setRequestProperty("X-Tunnel-Client-Name","hausgarten-android");connection.setRequestProperty("X-Tunnel-Client-Version","0.1.0");
                connection.setRequestProperty("X-Tunnel-Client-Instance-Id",instance);
                connection.setRequestProperty("X-Tunnel-MCP-Server-Info","{\"version\":1,\"channels\":[{\"name\":\"main\",\"proc_affinity\":true}]}");
                if(shard!=null)connection.setRequestProperty("X-Tunnel-Shard-Token",shard);
                if(body!=null){connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
                int code=connection.getResponseCode();long receipt=System.nanoTime();
                if(code==204)return new Reply(code,new JSONObject(),receipt);
                if(code<200||code>=300){
                    JSONObject safe=new JSONObject();
                    if(code==401||code==403)safe.put("access_reason",readAccessReason(connection));
                    return new Reply(code,safe,receipt); // Only a fixed category; never provider messages/credentials.
                }
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int count,total=0;
                    while((count=in.read(buffer))!=-1){total+=count;if(total>2*1024*1024)throw new IOException("Tunnel-Antwort zu groß.");out.write(buffer,0,count);}
                    String value=new String(out.toByteArray(),StandardCharsets.UTF_8);
                    return new Reply(code,value.trim().isEmpty()?new JSONObject():new JSONObject(value),receipt);
                }
            }finally{connection.disconnect();if(active==connection)active=null;}
        }
        private static String readAccessReason(HttpURLConnection connection){
            try(InputStream in=connection.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(in==null)return "auth_denied";
                byte[] buffer=new byte[4096];int count,total=0;
                while((count=in.read(buffer))!=-1){total+=count;if(total>65536)return "auth_denied";out.write(buffer,0,count);}
                return accessReason(new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)));
            }catch(Exception ignored){return "auth_denied";}
        }
    }
    static String accessReason(JSONObject json){
        JSONObject error=json.optJSONObject("error");
        String message=(error==null?"":error.optString("message"))+" "+json.optString("detail")+" "+json.optString("message");
        if(json.opt("error") instanceof String)message+=" "+json.optString("error");
        message=message.toLowerCase(Locale.ROOT);
        if(message.contains("openai-organization header should match organization for api key"))return "organization_mismatch";
        if(message.contains("requires an active organization context"))return "organization_required";
        if(message.contains("does not have permission to use this tunnel"))return "tunnel_access_denied";
        if(error!=null&&"invalid_api_key".equals(error.optString("code")))return "invalid_api_key";
        return "auth_denied";
    }
}
