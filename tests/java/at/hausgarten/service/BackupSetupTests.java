package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Actual backup setup ports; no account, network, database or business records. */
public final class BackupSetupTests {
    private static int checks;
    private static void yes(boolean value){checks++;if(!value)throw new AssertionError("setup check "+checks);}
    interface Action {void run()throws Exception;}
    static void refused(Action action)throws Exception{try{action.run();throw new AssertionError("Expected refusal");}catch(IOException|IllegalArgumentException expected){checks++;}}
    static final class Wire implements RetiredBackupDriveClient.Wire {
        String response="{}",url,method;int calls,status=200;
        public RetiredBackupDriveClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int max){this.method=method;this.url=url;calls++;yes(method.equals("GET")&&body==null&&headers.get("Authorization").equals("Bearer qa_token"));return new RetiredBackupDriveClient.Reply(status,response.getBytes(StandardCharsets.UTF_8),Collections.emptyMap());}
    }
    public static void main(String[] args)throws Exception {
        Wire wire=new Wire();RetiredBackupDriveClient client=new RetiredBackupDriveClient(()->"qa_token",wire);
        JSONObject folder=new JSONObject().put("id","qa_folder_123456").put("name","QA Backup & <Ordner>").put("mimeType","application/vnd.google-apps.folder").put("trashed",false);
        wire.response=new JSONObject().put("files",new JSONArray().put(folder)).put("nextPageToken","qa_next+' &=").toString();
        yes(client.backupFolderPage("root","").getJSONArray("files").length()==1);
        String decoded=URLDecoder.decode(wire.url,"UTF-8");yes(decoded.contains("'root' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false"));
        yes(decoded.contains("fields=nextPageToken,files(id,name,mimeType,trashed)")&&!decoded.contains("alt=media")&&!decoded.contains("applicationDataFolder"));
        client.backupFolderPage("qa_folder_123456","qa_next+' &=");yes(wire.url.contains("pageToken=qa_next%2B%27+%26%3D"));
        int before=wire.calls;refused(()->client.backupFolderPage("root' or trashed=true", ""));refused(()->client.backupFolderPage("root",new String(new char[2049]).replace('\0','x')));yes(wire.calls==before);
        wire.response=new JSONObject().put("files",new JSONArray().put(new JSONObject(folder.toString()).put("mimeType","application/octet-stream"))).toString();refused(()->client.backupFolderPage("root",""));
        wire.response=new JSONObject().put("files",new JSONArray().put(new JSONObject(folder.toString()).put("trashed",true))).toString();refused(()->client.backupFolderPage("root",""));
        wire.response=new JSONObject(folder.toString()).put("capabilities",new JSONObject().put("canAddChildren",false)).toString();refused(()->client.backupFolder("qa_folder_123456"));
        wire.response=new JSONObject(folder.toString()).put("capabilities",new JSONObject().put("canAddChildren",true)).toString();yes(client.backupFolder("qa_folder_123456").getString("name").equals(folder.getString("name")));
        wire.response=new JSONObject(folder.toString()).put("id","qa_other_folder_123456").put("capabilities",new JSONObject().put("canAddChildren",true)).toString();refused(()->client.backupFolder("qa_folder_123456"));
        before=wire.calls;refused(()->new RetiredDriveBackupUpload(client).upload(null,null,null,null));yes(before==wire.calls);
        wire.status=403;refused(()->client.backupFolderPage("root",""));wire.status=200;

        String code=BackupCrypto.newRecoveryCode();ByteArrayOutputStream saved=new ByteArrayOutputStream();
        String readback=BackupRecoveryExport.writeAndVerify(code,()->saved,()->new ByteArrayInputStream(saved.toByteArray()));yes(readback.equals(code)&&Arrays.equals(saved.toByteArray(),(code+"\n").getBytes(StandardCharsets.US_ASCII)));
        refused(()->BackupRecoveryExport.writeAndVerify(code,()->null,()->new ByteArrayInputStream(new byte[0])));
        refused(()->BackupRecoveryExport.writeAndVerify(code,()->new ByteArrayOutputStream(),()->null));
        refused(()->BackupRecoveryExport.writeAndVerify(code,()->new ByteArrayOutputStream(),()->new ByteArrayInputStream((code+"wrong").getBytes(StandardCharsets.US_ASCII))));
        refused(()->BackupRecoveryExport.writeAndVerify(code,()->new ByteArrayOutputStream(),()->new ByteArrayInputStream(new byte[4097])));
        refused(()->BackupRecoveryExport.writeAndVerify(code,()->new OutputStream(){public void write(int value)throws IOException{throw new IOException("qa_full_target");}},()->{throw new AssertionError("Readback after failed write");}));
        refused(()->BackupRecoveryExport.writeAndVerify(code,()->new ByteArrayOutputStream(),()->{throw new IOException("qa_lost_read_permission");}));
        System.out.println("PASS "+checks+" backup folder selection and independent recovery-copy checks");
    }
}
