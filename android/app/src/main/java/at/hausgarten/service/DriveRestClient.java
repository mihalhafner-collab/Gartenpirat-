package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Encrypted-backup transport and shared HTTP value types; no business sync operations. */
class DriveRestClient {
    static final String API="https://www.googleapis.com/drive/v3/", UPLOAD="https://www.googleapis.com/upload/drive/v3/files";
    static final int MAX_JSON=64*1024*1024, CHUNK=256*1024;
    static final int LARGE_CHUNK=4*1024*1024, CACHE_LIMIT=4*1024*1024, CACHE_ENTRY_LIMIT=512*1024;
    static final int HTTP_CONCURRENCY=6;
    private static final java.util.concurrent.Semaphore HTTP_SLOTS=new java.util.concurrent.Semaphore(HTTP_CONCURRENCY,true);
    interface Token { String get() throws Exception; }
    interface Checkpoints { String get(String key); void put(String key,String value); }
    interface Wire {
        Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception;
        default HashReply readHash(String url,Map<String,String> headers,long expectedBytes)throws Exception{throw new IOException("DRIVE_STREAMING_READBACK_UNSUPPORTED");}
        default void cancel(Thread thread){}
    }
    static final class HashReply {
        final Reply response;final long bytes;final String sha256;
        HashReply(Reply response,long bytes,String sha256){this.response=response;this.bytes=bytes;this.sha256=sha256;}
    }
    static final class Reply {
        final int status;final byte[] body;final Map<String,String> headers;
        Reply(int status,byte[] body,Map<String,String> headers){this.status=status;this.body=body;this.headers=headers;}
        String header(String key){for(Map.Entry<String,String> h:headers.entrySet())if(h.getKey()!=null&&key.equalsIgnoreCase(h.getKey()))return h.getValue();return "";}
        JSONObject json()throws Exception{return new JSONObject(new String(body,StandardCharsets.UTF_8));}
    }
    static final class Failure extends IOException {
        final String code;final long retryAfter;final boolean userAction;
        Failure(String code,String message,long retryAfter,boolean userAction){super(message);this.code=code;this.retryAfter=retryAfter;this.userAction=userAction;}
    }
    static final class HttpWire implements Wire {
        interface Connections {HttpURLConnection open(URL target)throws IOException;}
        private final Connections connections;
        HttpWire(){this(target->(HttpURLConnection)target.openConnection());}
        HttpWire(Connections connections){this.connections=connections;}
        private final java.util.concurrent.ConcurrentHashMap<Thread,HttpURLConnection> active=new java.util.concurrent.ConcurrentHashMap<>();
        public void cancel(Thread thread){HttpURLConnection connection=active.get(thread);if(connection!=null)connection.disconnect();}
        public Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception{
            URL target=new URL(url);if(!"https".equals(target.getProtocol())||!"www.googleapis.com".equals(target.getHost())||target.getPort()!=-1)throw new SecurityException("Unzulässiges Drive-Ziel.");
            HttpURLConnection c=connections.open(target);c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);
            boolean drained=false;
            active.put(Thread.currentThread(),c);
            try{
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Drive-Übertragung unterbrochen.");
                for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
                if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
                int status=c.getResponseCode();Map<String,String> hs=new HashMap<>();for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null&&!h.getValue().isEmpty())hs.put(h.getKey(),h.getValue().get(0));
                InputStream input=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(input!=null)try(InputStream in=input){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>maximum)throw new Failure("PAYLOAD_LIMIT","Drive-Datei überschreitet die zulässige Größe.",0,true);out.write(b,0,n);}}
                drained=true;return new Reply(status,out.toByteArray(),hs);
            }finally{ // Closed, fully consumed streams return to Android's connection pool; disconnect aborts failures.
                active.remove(Thread.currentThread(),c);if(!drained)c.disconnect();
            }
        }
        public HashReply readHash(String url,Map<String,String> headers,long expectedBytes)throws Exception{
            URI target=new URI(url);if(!"https".equals(target.getScheme())||!"www.googleapis.com".equals(target.getHost())||target.getPort()!=-1||target.getUserInfo()!=null||target.getFragment()!=null)throw new SecurityException("Unzulässiges Drive-Ziel.");
            HttpURLConnection c=connections.open(target.toURL());c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod("GET");boolean drained=false;active.put(Thread.currentThread(),c);
            try{
                for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());c.setRequestProperty("Accept-Encoding","identity");
                int status=c.getResponseCode();Map<String,String> hs=new HashMap<>();for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null&&!h.getValue().isEmpty())hs.put(h.getKey(),h.getValue().get(0));
                java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long count=0;ByteArrayOutputStream error=new ByteArrayOutputStream();
                InputStream source=status>=400?c.getErrorStream():c.getInputStream();if(source!=null)try(InputStream in=source){byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("DRIVE_BACKUP_INTERRUPTED");
                    if(status==200){if(n>expectedBytes-count)throw new IOException("DRIVE_BACKUP_READBACK_SIZE_MISMATCH");digest.update(buffer,0,n);count+=n;}
                    else {if(error.size()+n>8192)throw new IOException("DRIVE_BACKUP_ERROR_RESPONSE_TOO_LARGE");error.write(buffer,0,n);}
                }}
                drained=true;StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));
                return new HashReply(new Reply(status,error.toByteArray(),hs),count,status==200?hash.toString():"");
            }finally{active.remove(Thread.currentThread(),c);if(!drained)c.disconnect();}
        }
    }
    final String folder;private final Token token;private final Wire wire;private final Checkpoints checkpoints;
    private final Set<Thread> activeRequests=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final class Traffic {final java.util.concurrent.atomic.AtomicLong retryAt=new java.util.concurrent.atomic.AtomicLong();}
    private Traffic traffic=new Traffic();
    String checkpoint(String key){return checkpoints.get("drive-meta:"+folder+":"+key);}
    void checkpoint(String key,String value){checkpoints.put("drive-meta:"+folder+":"+key,value);}
    DriveRestClient(String folder,Token token,Wire wire,Checkpoints checkpoints){if(!validId(folder))throw new IllegalArgumentException("Ungültige Drive-Ordnerkennung.");this.folder=folder;this.token=token;this.wire=wire;this.checkpoints=checkpoints;}
    void cancelActiveTransfers(){for(Thread thread:activeRequests)wire.cancel(thread);}
    static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{10,200}");}
    static String folderId(String input){String value=input.trim();if(value.startsWith("https://drive.google.com/drive/folders/"))value=value.substring("https://drive.google.com/drive/folders/".length()).split("[?#/]",2)[0];if(!validId(value))throw new IllegalArgumentException("Bitte einen gültigen Drive-Ordnerlink verwenden.");return value;}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    Reply send(String method,String url,byte[] body,Map<String,String> extra,int max)throws Exception{
        return exchange(extra,headers->wire.send(method,url,headers,body,max));
    }
    private interface Exchange {Reply send(Map<String,String> headers)throws Exception;}
    private Reply exchange(Map<String,String> extra,Exchange request)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Drive-Übertragung wurde unterbrochen. Gesicherte Teile bleiben erhalten.");
        long remaining=traffic.retryAt.get()-System.currentTimeMillis();
        if(remaining>0)throw new Failure("TEMPORARY_UNAVAILABLE","Google Drive ist vorübergehend nicht erreichbar. Automatischer neuer Versuch folgt.",remaining,false);
        Map<String,String> h=new HashMap<>(extra);String t=token.get();if(t==null||t.isEmpty())throw new Failure("AUTH_REQUIRED","Google Drive erneut verbinden.",0,true);h.put("Authorization","Bearer "+t);
        try{HTTP_SLOTS.acquire();}catch(InterruptedException stopped){Thread.currentThread().interrupt();throw new InterruptedIOException("Drive-Übertragung unterbrochen.");}
        Reply r;activeRequests.add(Thread.currentThread());
        try{
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Drive-Übertragung unterbrochen.");
            remaining=traffic.retryAt.get()-System.currentTimeMillis();if(remaining>0)throw new Failure("TEMPORARY_UNAVAILABLE","Google Drive ist vorübergehend nicht erreichbar. Automatischer neuer Versuch folgt.",remaining,false);
            r=request.send(h);
            if(r.status==429||r.status>=500)traffic.retryAt.accumulateAndGet(System.currentTimeMillis()+retryAfter(r.header("Retry-After")),Math::max);
        }finally{activeRequests.remove(Thread.currentThread());HTTP_SLOTS.release();}
        if(r.status==401)throw new Failure("AUTH_REQUIRED","Google-Drive-Anmeldung ist abgelaufen. Erneut verbinden.",0,true);
        if(r.status==403)throw new Failure("PERMISSION_REQUIRED","Google-Freigabe für diesen Ordner fehlt oder das API-Kontingent ist eingeschränkt. Verbindung prüfen.",0,true);
        if(r.status==429||r.status>=500)throw new Failure("TEMPORARY_UNAVAILABLE","Google Drive ist vorübergehend nicht erreichbar. Automatischer neuer Versuch folgt.",retryAfter(r.header("Retry-After")),false);
        return r;
    }
    HashReply readBackupHash(String id,long expectedBytes)throws Exception{
        if(!validId(id)||expectedBytes<1)throw new IllegalArgumentException("DRIVE_BACKUP_REFERENCE_INVALID");
        final HashReply[] observed={null};Reply response=exchange(Collections.emptyMap(),headers->{observed[0]=wire.readHash(API+"files/"+id+"?alt=media&supportsAllDrives=true",headers,expectedBytes);return observed[0].response;});
        if(response.status!=200||observed[0].bytes!=expectedBytes)throw new IOException("DRIVE_BACKUP_READBACK_UNCONFIRMED");return observed[0];
    }
    static long retryAfter(String value){try{return Math.max(1,Math.min(3600,Long.parseLong(value.trim())))*1000;}catch(Exception e){try{return Math.min(3600000,Math.max(1000,java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()));}catch(Exception ignored){return 30000;}}}
    private JSONObject get(String path)throws Exception{Reply r=send("GET",API+path,null,Collections.emptyMap(),MAX_JSON);if(r.status!=200)throw new Failure("DRIVE_READ_FAILED","Drive-Datei oder Ordner ist nicht erreichbar.",0,true);return r.json();}
    JSONObject validateFolder()throws Exception{
        JSONObject info=get("files/"+folder+"?fields=id,mimeType,trashed,capabilities(canAddChildren)&supportsAllDrives=true");
        if(!"application/vnd.google-apps.folder".equals(info.optString("mimeType"))||info.optBoolean("trashed")||info.optJSONObject("capabilities")==null||!info.getJSONObject("capabilities").optBoolean("canAddChildren"))throw new Failure("PERMISSION_REQUIRED","Der gewählte Drive-Ordner benötigt Lese- und Schreibzugriff.",0,true);return info;
    }
    static String sha256(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
