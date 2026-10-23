package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Fault tests of real Tink and disk streams; never opens an operational store. */
public final class EncryptedBackupArchiveTests {
    private static int passed;
    interface Case {void run()throws Exception;}
    private static void yes(boolean value){if(!value)throw new AssertionError();}
    private static void reject(Case run)throws Exception {try{run.run();}catch(Exception expected){passed++;return;}throw new AssertionError("Expected refusal");}
    private static JSONObject metadata(int schema)throws Exception {
        return new JSONObject().put("schemaVersion",schema).put("applicationId","at.hausgarten.service").put("appVersion","test_1").put("backupId","test_backup_1").put("installationId","test_installation").put("businessId","test_business").put("capturedAt","2027-05-18T09:00:00Z").put("counts",new JSONObject().put("photos",25)).put("numberState",new JSONObject().put("next",12)).put("lastIssuedDocumentIds",new JSONArray().put("test_invoice_11"));
    }
    private static InputStream input(File file)throws IOException {return new FileInputStream(file);}
    private static void remove(File root)throws IOException {if(root.isDirectory())try(java.util.stream.Stream<Path> walk=Files.walk(root.toPath())){for(Path p:(Iterable<Path>)walk.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}else Files.deleteIfExists(root.toPath());}
    private static void mutate(File file,long offset)throws Exception {try(RandomAccessFile edit=new RandomAccessFile(file,"rw")){edit.seek(offset);int b=edit.read();edit.seek(offset);edit.write(b^64);}}
    public static void main(String[] args)throws Exception {
        File root=Files.createTempDirectory("test_backup_stream_").toFile();String recovery=BackupCrypto.newRecoveryCode();
        try{
            yes(BackupCrypto.recoveryKey(recovery).length==32);passed++;
            reject(()->BackupCrypto.recoveryKey(recovery.substring(0,recovery.length()-1)+(recovery.endsWith("0")?"1":"0")));
            File media=new File(root,"source.bin");byte[] block=new byte[65536];new Random(17).nextBytes(block);
            try(OutputStream out=new FileOutputStream(media)){for(int i=0;i<768;i++)out.write(block);} // 48MiB, beyond the requested40MiB threshold.
            File active=new File(root,"active.txt");Files.write(active.toPath(),"test active store unchanged".getBytes(java.nio.charset.StandardCharsets.UTF_8));String activeHash=EncryptedBackupArchive.sha256(active);
            List<EncryptedBackupArchive.Source> sources=new ArrayList<>();sources.add(new EncryptedBackupArchive.Source("database.sqlite",active));
            for(int i=0;i<25;i++)sources.add(new EncryptedBackupArchive.Source("files/test_photo_"+i+".bin",i==0?media:active));
            File backup=new File(root,"test_generation.hgb");JSONObject written;
            try(OutputStream out=new FileOutputStream(backup)){written=EncryptedBackupArchive.write(out,recovery,metadata(4),sources);}
            yes(backup.length()>40L*1024*1024&&written.getJSONArray("files").length()==26);passed++;
            String originalHash=EncryptedBackupArchive.sha256(backup);
            try(EncryptedBackupArchive.Verified restore=EncryptedBackupArchive.verifyAndStage(input(backup),new String(recovery),new File(root,"test_restore"),160L*1024*1024,4)){
                yes(restore.manifest.getLong("totalBytes")==written.getLong("totalBytes"));
                yes(EncryptedBackupArchive.sha256(restore.file("files/test_photo_0.bin")).equals(EncryptedBackupArchive.sha256(media)));
                yes(restore.manifest.getJSONObject("numberState").getInt("next")==12);passed++;
            }
            yes(!new File(root,"test_restore").exists());passed++;
            reject(()->EncryptedBackupArchive.verifyAndStage(input(backup),BackupCrypto.newRecoveryCode(),new File(root,"test_wrong_code"),160L*1024*1024,4));
            yes(!new File(root,"test_wrong_code").exists());passed++;
            File damaged=new File(root,"test_corrupt.hgb");Files.copy(backup.toPath(),damaged.toPath());mutate(damaged,damaged.length()-1);
            reject(()->EncryptedBackupArchive.verifyAndStage(input(damaged),recovery,new File(root,"test_corrupt"),160L*1024*1024,4));
            yes(!new File(root,"test_corrupt").exists());passed++;
            File truncated=new File(root,"test_truncated.hgb");Files.copy(backup.toPath(),truncated.toPath());try(RandomAccessFile f=new RandomAccessFile(truncated,"rw")){f.setLength(f.length()-21);}
            reject(()->EncryptedBackupArchive.verifyAndStage(input(truncated),recovery,new File(root,"test_truncated"),160L*1024*1024,4));
            reject(()->EncryptedBackupArchive.verifyAndStage(input(backup),recovery,new File(root,"test_space"),1024*1024,4));
            reject(()->EncryptedBackupArchive.verifyAndStage(input(backup),recovery,new File(root,"test_schema"),160L*1024*1024,3));
            reject(()->EncryptedBackupArchive.verifyAndStage(input(backup),recovery,root,160L*1024*1024,4));
            reject(()->new EncryptedBackupArchive.Source("../active.txt",active));
            reject(()->new EncryptedBackupArchive.Source("files/../../active.txt",active));
            reject(()->new EncryptedBackupArchive.Source("/active.txt",active));
            File interrupted=new File(root,"test_interrupted.hgb");
            try(OutputStream file=new FileOutputStream(interrupted)){
                OutputStream disconnect=new FilterOutputStream(file){long bytes;@Override public void write(byte[] b,int o,int n)throws IOException {if(bytes+n>2*1024*1024)throw new IOException("test USB disconnect/full provider");out.write(b,o,n);bytes+=n;}@Override public void write(int b)throws IOException{write(new byte[]{(byte)b},0,1);}};
                reject(()->EncryptedBackupArchive.write(disconnect,recovery,metadata(4),sources));
            }
            reject(()->EncryptedBackupArchive.verifyAndStage(input(interrupted),recovery,new File(root,"test_disconnect"),160L*1024*1024,4));
            File unsafe=new File(root,"test_unsafe.hgb");
            try(ZipOutputStream zip=new ZipOutputStream(BackupCrypto.encrypt(new FileOutputStream(unsafe),recovery))){
                zip.putNextEntry(new ZipEntry("data/../active.txt"));zip.write(block);zip.closeEntry();
                JSONObject m=metadata(4).put("formatVersion",1).put("completion","COMPLETE").put("totalBytes",block.length).put("files",new JSONArray().put(new JSONObject().put("path","../active.txt").put("size",block.length).put("sha256",originalHash)));
                zip.putNextEntry(new ZipEntry("manifest.json"));zip.write(m.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
            }
            reject(()->EncryptedBackupArchive.verifyAndStage(input(unsafe),recovery,new File(root,"test_unsafe"),160L*1024*1024,4));
            yes(EncryptedBackupArchive.sha256(active).equals(activeHash)&&EncryptedBackupArchive.sha256(backup).equals(originalHash));passed++;
            try(InputStream file=input(backup)){byte[] prefix=new byte[4096];int n=file.read(prefix);String plain=new String(prefix,0,n,java.nio.charset.StandardCharsets.ISO_8859_1);yes(!plain.contains("test_business")&&!plain.contains("test_photo_")&&!plain.contains(recovery));passed++;}
            System.out.println("PASS "+passed+" encrypted archive fault checks;48MiB and25 synthetic media;no Keystore;active data unchanged");
        }finally{remove(root);}
    }
}
