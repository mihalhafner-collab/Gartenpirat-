package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared HTTP values for calendar, weather and delivery. No Drive API or backup client. */
class HttpTransport {
    static final int MAX_JSON=64*1024*1024, CHUNK=256*1024;
    static final int LARGE_CHUNK=4*1024*1024, CACHE_LIMIT=4*1024*1024, CACHE_ENTRY_LIMIT=512*1024;
    static final int HTTP_CONCURRENCY=6;
    private static final java.util.concurrent.Semaphore HTTP_SLOTS=new java.util.concurrent.Semaphore(HTTP_CONCURRENCY,true);
    interface Token { String get() throws Exception; }
    interface Checkpoints { String get(String key); void put(String key,String value); }
    interface Wire {
        Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception;
        default HashReply readHash(String url,Map<String,String> headers,long expectedBytes)throws Exception{throw new IOException("HTTP_STREAMING_READBACK_UNSUPPORTED");}
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
    static boolean validId(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{10,200}");}
    static long retryAfter(String value){try{return Math.max(1,Math.min(3600,Long.parseLong(value.trim())))*1000;}catch(Exception e){try{return Math.min(3600000,Math.max(1000,java.time.ZonedDateTime.parse(value,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-System.currentTimeMillis()));}catch(Exception ignored){return 30000;}}}
    static String sha256(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
