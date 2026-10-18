package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Folder-scoped transport. Tokens are obtained per request and are never persisted here. */
final class DriveRestClient {
    static final String API="https://www.googleapis.com/drive/v3/", UPLOAD="https://www.googleapis.com/upload/drive/v3/files";
    static final int MAX_JSON=64*1024*1024, CHUNK=256*1024;
    interface Token { String get() throws Exception; }
    interface Checkpoints { String get(String key); void put(String key,String value); }
    interface Wire { Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception; }
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
        public Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception{
            URL target=new URL(url);if(!"https".equals(target.getProtocol())||!"www.googleapis.com".equals(target.getHost())||target.getPort()!=-1)throw new SecurityException("Unzulässiges Drive-Ziel.");
            HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);
            try{
                for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());
                if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
                int status=c.getResponseCode();Map<String,String> hs=new HashMap<>();for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null&&!h.getValue().isEmpty())hs.put(h.getKey(),h.getValue().get(0));
                InputStream input=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(input!=null)try(InputStream in=input){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>maximum)throw new Failure("PAYLOAD_LIMIT","Drive-Datei überschreitet die zulässige Größe.",0,true);out.write(b,0,n);}}
                return new Reply(status,out.toByteArray(),hs);
            }finally{c.disconnect();}
        }
    }
    final String folder;private final Token token;private final Wire wire;private final Checkpoints checkpoints;
    private final Map<String,JSONArray> listings=new HashMap<>();private final Map<String,Map<String,JSONObject>> indexes=new HashMap<>();
    String checkpoint(String key){return checkpoints.get("drive-meta:"+folder+":"+key);}
    void checkpoint(String key,String value){checkpoints.put("drive-meta:"+folder+":"+key,value);}
    DriveRestClient(String folder,Token token,Wire wire,Checkpoints checkpoints){if(!validId(folder))throw new IllegalArgumentException("Ungültige Drive-Ordnerkennung.");this.folder=folder;this.token=token;this.wire=wire;this.checkpoints=checkpoints;}
    static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{10,200}");}
    static String folderId(String input){String value=input.trim();if(value.startsWith("https://drive.google.com/drive/folders/"))value=value.substring("https://drive.google.com/drive/folders/".length()).split("[?#/]",2)[0];if(!validId(value))throw new IllegalArgumentException("Bitte einen gültigen Drive-Ordnerlink verwenden.");return value;}
    private static String enc(String s)throws Exception{return URLEncoder.encode(s,"UTF-8");}
    private Reply send(String method,String url,byte[] body,Map<String,String> extra,int max)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Drive-Übertragung wurde unterbrochen. Gesicherte Teile bleiben erhalten.");
        Map<String,String> h=new HashMap<>(extra);String t=token.get();if(t==null||t.isEmpty())throw new Failure("AUTH_REQUIRED","Google Drive erneut verbinden.",0,true);h.put("Authorization","Bearer "+t);
        Reply r=wire.send(method,url,h,body,max);if(r.status==401)throw new Failure("AUTH_REQUIRED","Google-Drive-Anmeldung ist abgelaufen. Erneut verbinden.",0,true);
        if(r.status==403)throw new Failure("PERMISSION_REQUIRED","Google-Freigabe für diesen Ordner fehlt oder das API-Kontingent ist eingeschränkt. Verbindung prüfen.",0,true);
        if(r.status==429||r.status>=500)throw new Failure("TEMPORARY_UNAVAILABLE","Google Drive ist vorübergehend nicht erreichbar. Automatischer neuer Versuch folgt.",retryAfter(r.header("Retry-After")),false);
        return r;
    }
    static long retryAfter(String value){try{return Math.max(1,Math.min(3600,Long.parseLong(value.trim())))*1000;}catch(Exception e){try{return Math.min(3600000,Math.max(1000,java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()));}catch(Exception ignored){return 30000;}}}
    private JSONObject get(String path)throws Exception{Reply r=send("GET",API+path,null,Collections.emptyMap(),MAX_JSON);if(r.status!=200)throw new Failure("DRIVE_READ_FAILED","Drive-Datei oder Ordner ist nicht erreichbar.",0,true);return r.json();}
    JSONObject validateFolder()throws Exception{
        JSONObject info=get("files/"+folder+"?fields=id,mimeType,trashed,capabilities(canAddChildren)&supportsAllDrives=true");
        if(!"application/vnd.google-apps.folder".equals(info.optString("mimeType"))||info.optBoolean("trashed")||info.optJSONObject("capabilities")==null||!info.getJSONObject("capabilities").optBoolean("canAddChildren"))throw new Failure("PERMISSION_REQUIRED","Der gewählte Drive-Ordner benötigt Lese- und Schreibzugriff.",0,true);return info;
    }
    JSONArray list(String parent)throws Exception{
        if(listings.containsKey(parent))return listings.get(parent);
        if(!validId(parent))throw new IllegalArgumentException("Ungültiger Drive-Ordner.");JSONArray all=new JSONArray();String page="";Set<String> pages=new HashSet<>();
        do{JSONObject data=get("files?q="+enc("'"+parent+"' in parents and trashed = false")+"&fields=nextPageToken,files(id,name,mimeType,size,md5Checksum,version,modifiedTime)&pageSize=1000&supportsAllDrives=true&includeItemsFromAllDrives=true"+(page.isEmpty()?"":"&pageToken="+enc(page)));
            JSONArray rows=data.getJSONArray("files");for(int i=0;i<rows.length();i++){if(all.length()>=20000)throw new Failure("FOLDER_LIMIT","Drive-Ordner enthält zu viele Dateien. Prüfung erforderlich.",0,true);all.put(rows.getJSONObject(i));}page=data.optString("nextPageToken");if(!page.isEmpty()&&!pages.add(page))throw new IOException("Drive-Seitennavigation wiederholt sich.");
        }while(!page.isEmpty());listings.put(parent,all);return all;
    }
    JSONObject unique(String parent,String name)throws Exception{
        if(!indexes.containsKey(parent)){Map<String,JSONObject> index=new HashMap<>();JSONArray rows=list(parent);for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String key=row.getString("name");if(index.containsKey(key))index.put(key,null);else index.put(key,row);}indexes.put(parent,index);}Map<String,JSONObject> index=indexes.get(parent);if(index.containsKey(name)&&index.get(name)==null)throw new Failure("DUPLICATE_FILES","Gleichnamige Drive-Dateien benötigen Prüfung. Keine Variante überschrieben.",0,true);return index.get(name);
    }
    byte[] read(String id,int max)throws Exception{if(!validId(id))throw new IllegalArgumentException("Ungültige Drive-Datei.");Reply r=send("GET",API+"files/"+id+"?alt=media&supportsAllDrives=true",null,Collections.emptyMap(),max);if(r.status!=200)throw new Failure("DRIVE_READ_FAILED","Drive-Original ist nicht verfügbar.",0,true);return r.body;}
    void confirm(String parent,String name,String expected,int maximum)throws Exception{
        JSONObject file=unique(parent,name);if(file==null)throw new IOException("Original ist in Drive noch nicht verfügbar.");String key="drive-proof:"+file.getString("id");
        String proof=expected+":"+file.optString("version")+":"+file.optString("md5Checksum")+":"+file.optString("size");
        if(!file.optString("version").isEmpty()&&!file.optString("md5Checksum").isEmpty()&&proof.equals(checkpoints.get(key)))return;
        if(!sha256(read(file.getString("id"),maximum)).equals(expected))throw new Failure("MEDIA_HASH_CONFLICT","Drive-Original hat eine abweichende Prüfsumme. Originale bleiben erhalten.",0,true);checkpoints.put(key,proof);
    }
    String ensureDirectory(String name)throws Exception{
        JSONObject old=unique(folder,name);if(old!=null){if(!"application/vnd.google-apps.folder".equals(old.optString("mimeType")))throw new IOException("Erwarteter Drive-Fotoordner ist keine Mappe.");return old.getString("id");}
        JSONObject m=new JSONObject().put("name",name).put("mimeType","application/vnd.google-apps.folder").put("parents",new JSONArray().put(folder));Reply r=send("POST",API+"files?fields=id&supportsAllDrives=true",m.toString().getBytes(StandardCharsets.UTF_8),Collections.singletonMap("Content-Type","application/json"),8192);
        if(r.status!=200&&r.status!=201)throw new IOException("Drive-Ordner konnte nicht angelegt werden.");listings.clear();indexes.clear();return r.json().getString("id");
    }
    /** Immutable, resumable upload. A server readback is mandatory, including after crash/replay. */
    JSONObject put(String parent,String name,String mime,byte[] bytes)throws Exception{
        progress(mime,0,bytes.length,"uploading");
        String sha=sha256(bytes),key="drive-upload:"+sha256((parent+"/"+name+"/"+sha).getBytes(StandardCharsets.UTF_8));JSONObject existing=unique(parent,name);
        if(existing!=null){if(!Arrays.equals(read(existing.getString("id"),Math.max(bytes.length,1)),bytes))throw new Failure("IMMUTABLE_FILE_CONFLICT","Vorhandene Drive-Datei hat einen anderen Inhalt. Beide Varianten bleiben erhalten.",0,true);checkpoints.put(key,"");progress(mime,bytes.length,bytes.length,"confirmed");return existing;}
        String location=checkpoints.get(key);long offset=0;
        if(location!=null&&!location.isEmpty()){
            validateSession(location);Reply status=send("PUT",location,new byte[0],Collections.singletonMap("Content-Range","bytes */"+bytes.length),8192);
            if(status.status==200||status.status==201){JSONObject done=status.json();verifyUploaded(done,bytes,key);progress(mime,bytes.length,bytes.length,"confirmed");return done;}
            if(status.status==308)offset=offset(status.header("Range"),bytes.length);else if(status.status==404||status.status==410){location="";checkpoints.put(key,"");}else throw new IOException("Drive-Uploadstatus ist unklar. Keine neue Datei angelegt.");
        }
        if(location==null||location.isEmpty()){
            JSONObject meta=new JSONObject().put("name",name).put("parents",new JSONArray().put(parent)).put("appProperties",new JSONObject().put("hg_sha256",sha));Map<String,String> hs=new HashMap<>();hs.put("Content-Type","application/json; charset=UTF-8");hs.put("X-Upload-Content-Type",mime);hs.put("X-Upload-Content-Length",Integer.toString(bytes.length));
            Reply start=send("POST",UPLOAD+"?uploadType=resumable&fields=id,name&supportsAllDrives=true",meta.toString().getBytes(StandardCharsets.UTF_8),hs,8192);
            if(start.status!=200&&start.status!=201)throw new IOException("Drive-Übertragung konnte nicht begonnen werden.");location=start.header("Location");validateSession(location);checkpoints.put(key,location);
        }
        while(offset<bytes.length){int end=(int)Math.min(bytes.length,offset+CHUNK);Map<String,String> hs=new HashMap<>();hs.put("Content-Type",mime);hs.put("Content-Range","bytes "+offset+"-"+(end-1)+"/"+bytes.length);Reply chunk=send("PUT",location,Arrays.copyOfRange(bytes,(int)offset,end),hs,8192);
            if(chunk.status==200||chunk.status==201){JSONObject done=chunk.json();progress(mime,bytes.length,bytes.length,"verifying");verifyUploaded(done,bytes,key);progress(mime,bytes.length,bytes.length,"confirmed");return done;}
            if(chunk.status!=308)throw new IOException("Drive-Übertragung ist noch nicht bestätigt.");long next=offset(chunk.header("Range"),bytes.length);if(next<=offset||next>end)throw new IOException("Drive-Uploadposition ist widersprüchlich.");offset=next;progress(mime,offset,bytes.length,"uploading");
        }
        throw new IOException("Drive-Abschlussbeleg fehlt.");
    }
    private void progress(String mime,long offset,long total,String state)throws Exception{checkpoint("progress",new JSONObject().put("kind",mime.startsWith("image/")?"photo":mime.equals("application/pdf")?"document":"data").put("bytes",offset).put("total",total).put("state",state).toString());}
    private void verifyUploaded(JSONObject done,byte[] bytes,String key)throws Exception{if(!Arrays.equals(read(done.getString("id"),Math.max(bytes.length,1)),bytes))throw new IOException("Drive-Prüfsumme stimmt nach dem Upload nicht überein.");checkpoints.put(key,"");listings.clear();indexes.clear();}
    private static long offset(String range,int total)throws Exception{if(range.isEmpty())return 0;if(!range.matches("bytes=0-[0-9]+"))throw new IOException("Ungültige Drive-Uploadposition.");long n=Long.parseLong(range.substring(8))+1;if(n<0||n>total)throw new IOException("Ungültige Drive-Uploadposition.");return n;}
    private static void validateSession(String url)throws Exception{URI u=new URI(url);if(!"https".equals(u.getScheme())||!"www.googleapis.com".equals(u.getHost())||u.getPort()!=-1||!u.getPath().startsWith("/upload/drive/"))throw new SecurityException("Unzulässiges Drive-Uploadziel.");}
    static String sha256(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
