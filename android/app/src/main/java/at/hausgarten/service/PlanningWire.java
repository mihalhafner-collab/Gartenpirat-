package at.hausgarten.service;
import java.net.*;
import java.io.*;
import java.util.*;

/** Fixed HTTPS destinations only; never includes provider bodies/URLs/keys in errors. */
final class PlanningWire implements DriveRestClient.Wire {
    public DriveRestClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception {
        URL target=new URL(url);if(!"https".equals(target.getProtocol())||target.getPort()!=-1||!Arrays.asList("www.googleapis.com","customer-api.open-meteo.com","warnungen.zamg.at").contains(target.getHost()))throw new SecurityException("Nicht freigegebenes Planungsziel.");
        HttpURLConnection c=(HttpURLConnection)target.openConnection();c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);
        try{for(Map.Entry<String,String> h:headers.entrySet())c.setRequestProperty(h.getKey(),h.getValue());if(body!=null){c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);try(OutputStream out=c.getOutputStream()){out.write(body);}}
            int code=c.getResponseCode();Map<String,String> hs=new HashMap<>();for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet())if(h.getKey()!=null&&!h.getValue().isEmpty())hs.put(h.getKey(),h.getValue().get(0));
            InputStream input=code>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream result=new ByteArrayOutputStream();if(input!=null)try(InputStream in=input){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(result.size()+n>maximum)throw new IOException("Planungsantwort zu groß.");result.write(buffer,0,n);}}return new DriveRestClient.Reply(code,result.toByteArray(),hs);
        }catch(IOException e){throw new IOException("Planungsdienst nicht erreichbar. Gespeicherte Daten bleiben erhalten.");}finally{c.disconnect();}
    }
}
