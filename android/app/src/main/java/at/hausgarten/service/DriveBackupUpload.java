package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable encrypted-generation upload. Never reads/applies a business snapshot from Drive. */
final class DriveBackupUpload {
    static final int CHUNK_BYTES=1024*1024;
    interface Checkpoint {JSONObject read()throws Exception;void save(JSONObject state)throws Exception;}
    private final DriveRestClient client;
    DriveBackupUpload(DriveRestClient client){this.client=client;}

    JSONObject upload(File sealed,String backupId,String expectedSha256,Checkpoint checkpoint)throws Exception {
        if(backupId==null||!backupId.matches("[A-Za-z0-9_-]{8,100}")||!expectedSha256.matches("[0-9a-f]{64}")||!sealed.isFile()||java.nio.file.Files.isSymbolicLink(sealed.toPath())||sealed.length()<1)throw new IOException("BACKUP_UPLOAD_SOURCE_INVALID");
        long size=sealed.length();if(!expectedSha256.equals(EncryptedBackupArchive.sha256(sealed)))throw new IOException("BACKUP_UPLOAD_SOURCE_CHANGED");
        String binding=PixelMcpWriteTools.hash(new JSONObject().put("backupId",backupId).put("folder",client.folder).put("size",size).put("sha256",expectedSha256));
        JSONObject saved=checkpoint.read(),job=saved==null?new JSONObject().put("formatVersion",1).put("binding",binding).put("state","UPLOAD_PENDING").put("remoteId","").put("session",""):new JSONObject(saved.toString());
        if(job.getInt("formatVersion")!=1||!job.getString("binding").equals(binding))throw new IOException("BACKUP_UPLOAD_REPLAY_MISMATCH");
        job.put("state","UPLOAD_PENDING");checkpoint.save(new JSONObject(job.toString()));String id=job.getString("remoteId");
        if(id.isEmpty()){
            DriveRestClient.Reply generated=request("GET",DriveRestClient.API+"files/generateIds?count=1&space=drive&type=files",null,Collections.emptyMap());
            if(generated.status!=200||generated.json().getJSONArray("ids").length()!=1)throw new IOException("BACKUP_UPLOAD_ID_UNCONFIRMED");
            id=generated.json().getJSONArray("ids").getString(0);if(!DriveRestClient.validId(id))throw new IOException("BACKUP_UPLOAD_ID_INVALID");job.put("remoteId",id);checkpoint.save(new JSONObject(job.toString()));
        }
        if(!DriveRestClient.validId(id))throw new IOException("BACKUP_UPLOAD_ID_INVALID");
        // Probe the same fixed ID after every restart, including a lost final reply.
        JSONObject existing=metadata(id);if(existing!=null)return verify(existing,id,backupId,size,expectedSha256,job,checkpoint);
        String location=job.getString("session");long offset=0;
        if(!location.isEmpty()){
            session(location);DriveRestClient.Reply status=request("PUT",location,new byte[0],Collections.singletonMap("Content-Range","bytes */"+size));
            if(status.status==200||status.status==201)return completed(status,id,backupId,size,expectedSha256,job,checkpoint);
            if(status.status==308)offset=offset(status.header("Range"),size);
            else if(status.status==404||status.status==410){job.put("session","");checkpoint.save(new JSONObject(job.toString()));location="";}
            else throw new IOException("BACKUP_UPLOAD_SESSION_UNCONFIRMED");
        }
        if(location.isEmpty()){
            JSONObject meta=new JSONObject().put("id",id).put("name",backupId+".hgb").put("mimeType","application/octet-stream").put("parents",new JSONArray().put(client.folder));
            Map<String,String> headers=new HashMap<>();headers.put("Content-Type","application/json; charset=UTF-8");headers.put("X-Upload-Content-Type","application/octet-stream");headers.put("X-Upload-Content-Length",Long.toString(size));
            DriveRestClient.Reply start=request("POST",DriveRestClient.UPLOAD+"?uploadType=resumable&fields=id&supportsAllDrives=true",meta.toString().getBytes(StandardCharsets.UTF_8),headers);
            if(start.status==409){JSONObject found=metadata(id);if(found==null)throw new IOException("BACKUP_UPLOAD_CONFLICT_UNCONFIRMED");return verify(found,id,backupId,size,expectedSha256,job,checkpoint);}
            if(start.status!=200&&start.status!=201)throw new IOException("BACKUP_UPLOAD_START_UNCONFIRMED");location=start.header("Location");session(location);
            job.put("session",location).put("state","UPLOADING");checkpoint.save(new JSONObject(job.toString()));
        }
        try(RandomAccessFile source=new RandomAccessFile(sealed,"r")){
            while(offset<size){
                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("BACKUP_UPLOAD_INTERRUPTED");
                int length=(int)Math.min(CHUNK_BYTES,size-offset);byte[] chunk=new byte[length];source.seek(offset);source.readFully(chunk);long end=offset+length;
                Map<String,String> headers=new HashMap<>();headers.put("Content-Type","application/octet-stream");headers.put("Content-Range","bytes "+offset+"-"+(end-1)+"/"+size);
                DriveRestClient.Reply reply=request("PUT",location,chunk,headers);
                if(reply.status==200||reply.status==201){if(end!=size)throw new IOException("BACKUP_UPLOAD_EARLY_COMPLETION");return completed(reply,id,backupId,size,expectedSha256,job,checkpoint);}
                if(reply.status!=308)throw new IOException("BACKUP_UPLOAD_CHUNK_UNCONFIRMED");long next=offset(reply.header("Range"),size);
                if(next<=offset||next>end)throw new IOException("BACKUP_UPLOAD_RANGE_INVALID");offset=next;
                // The server range is the resume authority. Persist progress only for truthful UI.
                job.put("uploadedBytes",offset);checkpoint.save(new JSONObject(job.toString()));
            }
        }
        throw new IOException("BACKUP_UPLOAD_FINAL_RECEIPT_PENDING");
    }
    private DriveRestClient.Reply request(String method,String url,byte[] body,Map<String,String> headers)throws Exception{return client.send(method,url,body,headers,8192);}
    private JSONObject metadata(String id)throws Exception {
        DriveRestClient.Reply reply=request("GET",DriveRestClient.API+"files/"+id+"?fields=id,name,size,mimeType,parents,trashed,version&supportsAllDrives=true",null,Collections.emptyMap());
        if(reply.status==404)return null;if(reply.status!=200)throw new IOException("BACKUP_UPLOAD_METADATA_UNCONFIRMED");return reply.json();
    }
    private JSONObject completed(DriveRestClient.Reply reply,String id,String backupId,long size,String sha,JSONObject job,Checkpoint checkpoint)throws Exception {
        if(!id.equals(reply.json().optString("id")))throw new IOException("BACKUP_UPLOAD_RECEIPT_ID_MISMATCH");
        job.put("state","REMOTE_VERIFY_PENDING");checkpoint.save(new JSONObject(job.toString()));JSONObject info=metadata(id);
        if(info==null)throw new IOException("BACKUP_UPLOAD_METADATA_UNCONFIRMED");return verify(info,id,backupId,size,sha,job,checkpoint);
    }
    private JSONObject verify(JSONObject info,String id,String backupId,long size,String sha,JSONObject job,Checkpoint checkpoint)throws Exception {
        JSONArray parents=info.optJSONArray("parents");boolean parent=false;for(int i=0;parents!=null&&i<parents.length();i++)if(client.folder.equals(parents.getString(i)))parent=true;
        if(!id.equals(info.optString("id"))||!parent||info.optBoolean("trashed")||!info.optString("name").equals(backupId+".hgb")||info.optLong("size",-1)!=size||!info.optString("mimeType").equals("application/octet-stream"))throw new IOException("BACKUP_UPLOAD_IMMUTABLE_CONFLICT");
        job.put("state","REMOTE_VERIFY_PENDING");checkpoint.save(new JSONObject(job.toString()));DriveRestClient.HashReply readback=client.readBackupHash(id,size);
        if(!sha.equals(readback.sha256))throw new IOException("BACKUP_UPLOAD_REMOTE_HASH_MISMATCH");
        job.put("state","REMOTE_VERIFIED").put("session","").put("uploadedBytes",size).put("remoteVerifiedAt",java.time.Instant.now().toString()).put("remoteSha256",sha).put("remoteVersion",info.optString("version"));
        checkpoint.save(new JSONObject(job.toString()));return new JSONObject(job.toString());
    }
    private static long offset(String range,long size)throws IOException {
        if(range.isEmpty())return 0;if(!range.matches("bytes=0-[0-9]+"))throw new IOException("BACKUP_UPLOAD_RANGE_INVALID");
        try{long next=Math.addExact(Long.parseLong(range.substring(8)),1);if(next<0||next>size)throw new IOException("BACKUP_UPLOAD_RANGE_INVALID");return next;}catch(ArithmeticException|NumberFormatException invalid){throw new IOException("BACKUP_UPLOAD_RANGE_INVALID");}
    }
    private static void session(String url)throws Exception {
        URI uri=new URI(url);if(!"https".equals(uri.getScheme())||!"www.googleapis.com".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getFragment()!=null||!"/upload/drive/v3/files".equals(uri.getPath())||uri.getRawQuery()==null||!uri.getRawQuery().contains("upload_id="))throw new SecurityException("BACKUP_UPLOAD_SESSION_TARGET_REJECTED");
    }
}
