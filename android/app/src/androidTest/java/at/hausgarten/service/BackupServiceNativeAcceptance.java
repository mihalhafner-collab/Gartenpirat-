package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared production backup use case with real DB/Keystore/files and an isolated network port. */
final class BackupServiceNativeAcceptance {
    private static void denied(PixelMcpInstrumentation.Task action)throws Exception{try{action.run();}catch(IOException expected){return;}throw new AssertionError("Expected backup refusal");}
    static final class Server implements DriveRestClient.Wire {
        final File encrypted;JSONObject metadata;int starts,completed,readbacks;long bytes,total;boolean dropFinal=true;
        Server(File encrypted){this.encrypted=encrypted;}
        DriveRestClient.Reply reply(int status,String body,Map<String,String> headers){return new DriveRestClient.Reply(status,body.getBytes(StandardCharsets.UTF_8),headers);}
        public DriveRestClient.Reply send(String method,String url,Map<String,String> headers,byte[] body,int maximum)throws Exception {
            if(!"Bearer qa_backup_token".equals(headers.get("Authorization")))throw new AssertionError("Only synthetic backup credentials allowed");
            if(url.contains("generateIds"))return reply(200,"{\"ids\":[\"qa_service_remote_123\"]}",Collections.emptyMap());
            if(method.equals("GET"))return reply(completed>0?200:404,completed>0?metadata.toString():"{}",Collections.emptyMap());
            if(method.equals("POST")){starts++;metadata=new JSONObject(new String(body,StandardCharsets.UTF_8));total=Long.parseLong(headers.get("X-Upload-Content-Length"));metadata.put("size",total).put("version","qa_v1");return reply(200,"{}",Collections.singletonMap("Location","https://www.googleapis.com/upload/drive/v3/files?upload_id=qa_service"));}
            String range=headers.get("Content-Range");if(range.startsWith("bytes */"))return reply(completed>0?200:308,completed>0?"{\"id\":\"qa_service_remote_123\"}":"",bytes==0?Collections.emptyMap():Collections.singletonMap("Range","bytes=0-"+(bytes-1)));
            long start=Long.parseLong(range.substring(6,range.indexOf('-')));if(start!=bytes||body.length>1024*1024)throw new AssertionError("Invalid synthetic upload range");
            try(RandomAccessFile out=new RandomAccessFile(encrypted,"rw")){out.seek(start);out.write(body);}bytes+=body.length;
            if(bytes==total){completed++;if(dropFinal){dropFinal=false;throw new IOException("qa_lost_final_reply");}return reply(200,"{\"id\":\"qa_service_remote_123\"}",Collections.emptyMap());}
            return reply(308,"",Collections.singletonMap("Range","bytes=0-"+(bytes-1)));
        }
        public DriveRestClient.HashReply readHash(String url,Map<String,String> headers,long expected)throws Exception{readbacks++;return new DriveRestClient.HashReply(reply(200,"",Collections.emptyMap()),encrypted.length(),EncryptedBackupArchive.sha256(encrypted));}
    }
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("backup native shared use case seals verifies and resumes one encrypted generation after lost remote reply and SQLite reopen",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);String code=BackupCrypto.newRecoveryCode();
            try{
                keys.confirmAndStore("qa_service_key_setup",code,code);f.store.putOwnerValue("backup_business_id","qa_business");
                JSONObject browser=new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_draft","QA pending editor"));JSONObject destination=new JSONObject().put("kind","drive").put("folderId","qa_service_folder_123").put("account","qa_backup@example.invalid");
                Server server=new Server(new File(f.context.getCacheDir(),"qa_remote_archive.hgb"));int[] tokens={0};LocalBackupCoordinator service=new LocalBackupCoordinator(f.context,f.store,server,account->{tokens[0]++;return "qa_backup_token";});
                String before=PixelMcpWriteTools.hash(f.store.getState().getJSONObject("master"));JSONObject job=service.create("qa_service_backup_request","qa_service_backup",destination,browser);test.yes(!job.getBoolean("replayed")&&service.create("qa_service_backup_request","qa_service_backup",destination,browser).getBoolean("replayed"));
                denied(()->service.create("qa_service_backup_request","qa_service_backup",destination,new JSONObject(browser.toString()).put("storage",new JSONObject().put("qa_draft","QA changed"))));
                JSONObject local=service.prepareLocal("qa_service_backup");String sha=local.getJSONObject("archive").getString("sha256");test.yes(local.getString("phase").equals("LOCAL_VERIFIED")&&tokens[0]==0&&server.starts==0);
                test.yes(service.prepareLocal("qa_service_backup").getJSONObject("archive").getString("sha256").equals(sha));
                denied(()->service.uploadToDrive("qa_service_backup"));test.yes(server.completed==1&&service.status("qa_service_backup").getString("phase").equals("UPLOADING"));
                f.store.close();try(PixelLocalStore reopened=new PixelLocalStore(f.context)){
                    LocalBackupCoordinator resumed=new LocalBackupCoordinator(f.context,reopened,server,account->"qa_backup_token");JSONObject complete=resumed.uploadToDrive("qa_service_backup");
                    test.yes(complete.getString("phase").equals("REMOTE_VERIFIED")&&server.completed==1&&server.starts==1&&server.readbacks==1&&complete.getJSONObject("archive").getString("sha256").equals(sha));
                    test.yes(PixelMcpWriteTools.hash(reopened.getState().getJSONObject("master")).equals(before));
                    try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(new FileInputStream(server.encrypted),code,new File(f.context.getNoBackupFilesDir(),"qa_service_remote_readback"),128L*1024*1024,PixelLocalStore.SCHEMA_VERSION)){test.yes(verified.manifest.getString("backupId").equals("qa_service_backup"));LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);}
                    BackupDeviceGuard guard=new BackupDeviceGuard(f.context);guard.advanceForRestore("qa_service_restore_context",guard.current().getString("restoreEpoch"));denied(()->resumed.status("qa_service_backup"));test.yes(!new LocalBackupCoordinator(f.context,reopened,server,account->"qa_backup_token").inventory().getJSONObject(0).getBoolean("resumeContextMatches"));
                }
            }finally{BackupCoreNativeAcceptance.deleteQaKey(f,keys);}
        }});
        test.probe("backup native committed archive and capture metadata are never silently recreated or blessed",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupRecoveryKeyStore keys=new BackupRecoveryKeyStore(f.context);String code=BackupCrypto.newRecoveryCode();
            try{
                keys.confirmAndStore("qa_service_guard_setup",code,code);LocalBackupCoordinator service=new LocalBackupCoordinator(f.context,f.store);JSONObject browser=new JSONObject().put("formatVersion",1).put("storage",new JSONObject());
                service.create("qa_service_guard_request","qa_service_guard_backup",new JSONObject().put("kind","drive").put("folderId","qa_service_folder_123").put("account","qa_backup@example.invalid"),browser);JSONObject job=service.prepareLocal("qa_service_guard_backup");
                File root=new File(f.context.getNoBackupFilesDir(),"full-backup-work/qa_service_guard_backup"),sealed=new File(root,job.getJSONObject("archive").getString("file")),saved=new File(root,"qa_saved_archive.hgb");java.nio.file.Files.move(sealed.toPath(),saved.toPath());denied(()->service.prepareLocal("qa_service_guard_backup"));test.yes(!sealed.exists()&&saved.isFile());java.nio.file.Files.move(saved.toPath(),sealed.toPath());
                File metadata=new File(root,job.getJSONObject("capture").getString("metadataFile"));byte[] original=java.nio.file.Files.readAllBytes(metadata.toPath());try(FileOutputStream out=new FileOutputStream(metadata,true)){out.write('\n');}String hash=EncryptedBackupArchive.sha256(metadata);denied(()->service.prepareLocal("qa_service_guard_backup"));test.yes(EncryptedBackupArchive.sha256(metadata).equals(hash)&&EncryptedBackupArchive.sha256(sealed).equals(job.getJSONObject("archive").getString("sha256")));try(FileOutputStream out=new FileOutputStream(metadata)){out.write(original);}test.yes(service.prepareLocal("qa_service_guard_backup").getString("phase").equals("LOCAL_VERIFIED"));
            }finally{BackupCoreNativeAcceptance.deleteQaKey(f,keys);}
        }});
    }
}
