package at.hausgarten.service;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-only protection of a separately recoverable random backup key. No silent reset. */
final class BackupRecoveryKeyStore {
    private static final byte[] AAD="at.hausgarten.service/backup-recovery/v1".getBytes(StandardCharsets.US_ASCII);
    private static final Object KEY_LOCK=new Object();
    private final AtomicFile protectedKey;
    private final String alias;
    private final byte[] capsuleAad;
    BackupRecoveryKeyStore(Context context)throws Exception {this(context,"");}
    static BackupRecoveryKeyStore scoped(Context context,String scope)throws Exception {
        if(scope==null||!scope.matches("[A-Za-z0-9_-]{8,120}"))throw new IllegalArgumentException("RECOVERY_SCOPE_INVALID");return new BackupRecoveryKeyStore(context,scope);
    }
    private BackupRecoveryKeyStore(Context context,String scope)throws Exception {
        File directory=new File(context.getNoBackupFilesDir(),"backup-key-protection");
        protectedKey=new AtomicFile(new File(directory,scope.isEmpty()?"recovery.v1":"recovery-"+scope+".v1"));
        capsuleAad=scope.isEmpty()?AAD:(new String(AAD,StandardCharsets.US_ASCII)+"/scope/"+scope).getBytes(StandardCharsets.US_ASCII);
        // An isolated QA Context receives a different alias; never access the active key in tests.
        String binding=context.getFilesDir().getCanonicalPath()+(scope.isEmpty()?"":"/scope/"+scope);byte[] digest=MessageDigest.getInstance("SHA-256").digest(binding.getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder("hausgarten.backup.v1.");for(int i=0;i<16;i++)name.append(String.format(java.util.Locale.ROOT,"%02x",digest[i]&255));alias=name.toString();
    }
    boolean configured(){synchronized(KEY_LOCK){return protectedKey.getBaseFile().exists()||new File(protectedKey.getBaseFile()+".bak").exists();}}
    void confirmAndStore(String requestId,String candidate,String confirmation)throws Exception {
        synchronized(KEY_LOCK){confirmAndStoreLocked(requestId,candidate,confirmation);}
    }
    private void confirmAndStoreLocked(String requestId,String candidate,String confirmation)throws Exception {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}"))throw new IllegalArgumentException("BACKUP_REQUEST_ID_REQUIRED");
        byte[] candidateKey=BackupCrypto.recoveryKey(candidate),confirmedKey=null;
        try{
            confirmedKey=BackupCrypto.recoveryKey(confirmation);
            if(!MessageDigest.isEqual(candidateKey,confirmedKey))throw new GeneralSecurityException("RECOVERY_CONFIRMATION_MISMATCH");
            if(configured()){
                JSONObject saved=readProtected();
                if(saved.getString("request_id").equals(requestId)&&MessageDigest.isEqual(saved.getString("recovery_code").getBytes(StandardCharsets.US_ASCII),candidate.trim().getBytes(StandardCharsets.US_ASCII)))return;
                if(saved.getString("request_id").equals(requestId))throw new GeneralSecurityException("REPLAY_MISMATCH");
                throw new GeneralSecurityException("RECOVERY_ALREADY_CONFIGURED");
            }
            JSONObject value=new JSONObject().put("formatVersion",1).put("request_id",requestId).put("recovery_code",candidate.trim());
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(capsuleAad);byte[] sealed=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
            File parent=protectedKey.getBaseFile().getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("RECOVERY_STORAGE_UNAVAILABLE");
            FileOutputStream output=null;
            try{
                output=protectedKey.startWrite();DataOutputStream data=new DataOutputStream(output);data.writeInt(1);data.write(cipher.getIV());data.writeInt(sealed.length);data.write(sealed);data.flush();protectedKey.finishWrite(output);output=null;
            }catch(Exception failure){if(output!=null)protectedKey.failWrite(output);throw failure;}
            // Readback includes platform authentication and a recovery-code checksum.
            if(!MessageDigest.isEqual(read().getBytes(StandardCharsets.US_ASCII),candidate.trim().getBytes(StandardCharsets.US_ASCII)))throw new GeneralSecurityException("RECOVERY_READBACK_FAILED");
        }finally{Arrays.fill(candidateKey,(byte)0);if(confirmedKey!=null)Arrays.fill(confirmedKey,(byte)0);}
    }
    String read()throws Exception {
        synchronized(KEY_LOCK){String code=readProtected().getString("recovery_code");byte[] checked=BackupCrypto.recoveryKey(code);Arrays.fill(checked,(byte)0);return code;}
    }
    /** Only call after authenticating and inspecting a complete known archive with this candidate. */
    JSONObject repairAfterVerifiedArchive(String requestId,String candidate,String confirmation,JSONObject proof)throws Exception {
        synchronized(KEY_LOCK){
            if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}"))throw new GeneralSecurityException("BACKUP_REQUEST_ID_REQUIRED");
            if(proof.optInt("formatVersion")!=1||!proof.optBoolean("archive_verified")||!proof.optString("backup_id").matches("[A-Za-z0-9_-]{8,100}")||proof.optString("business_id").isEmpty()||!proof.optString("archive_sha256").matches("[0-9a-f]{64}")||!proof.optString("database_sha256").matches("[0-9a-f]{64}"))throw new GeneralSecurityException("RECOVERY_REPAIR_PROOF_REQUIRED");
            byte[] candidateKey=BackupCrypto.recoveryKey(candidate),confirmationKey=null;
            try{
                confirmationKey=BackupCrypto.recoveryKey(confirmation);if(!MessageDigest.isEqual(candidateKey,confirmationKey))throw new GeneralSecurityException("RECOVERY_CONFIRMATION_MISMATCH");
                String fingerprint=PixelMcpWriteTools.hash(new JSONObject().put("request_id",requestId).put("proof",proof).put("key_sha256",hex(MessageDigest.getInstance("SHA-256").digest(candidateKey))));
                if(!configured())throw new GeneralSecurityException("RECOVERY_NOT_CONFIGURED");
                JSONObject current=null;try{current=readProtected();}catch(Exception unavailable){/* The externally authenticated archive is the separate recovery path. */}
                if(current!=null){if(requestId.equals(current.optString("request_id"))&&fingerprint.equals(current.optString("repair_fingerprint")))return new JSONObject().put("replayed",true);throw new GeneralSecurityException("RECOVERY_REPAIR_NOT_REQUIRED");}
                byte[] original;try(InputStream input=protectedKey.openRead();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){byte[] block=new byte[1024];int count;while((count=input.read(block))!=-1){if(count>8192-bytes.size())throw new IOException("RECOVERY_STORAGE_INVALID");bytes.write(block,0,count);}original=bytes.toByteArray();}
                if(original.length>=4){int savedVersion=new DataInputStream(new ByteArrayInputStream(original)).readInt();if(savedVersion!=1&&savedVersion!=2)throw new IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED");}
                File preserved=new File(protectedKey.getBaseFile().getParentFile(),protectedKey.getBaseFile().getName()+".preserved-"+hex(MessageDigest.getInstance("SHA-256").digest(original)));
                if(!preserved.exists()){AtomicFile prior=new AtomicFile(preserved);FileOutputStream old=null;try{old=prior.startWrite();old.write(original);prior.finishWrite(old);old=null;}catch(Exception failure){if(old!=null)prior.failWrite(old);throw failure;}}
                if(!MessageDigest.isEqual(original,java.nio.file.Files.readAllBytes(preserved.toPath())))throw new IOException("RECOVERY_PRESERVATION_FAILED");
                String repairedAlias=alias+".repair."+java.util.UUID.randomUUID();JSONObject value=new JSONObject().put("formatVersion",1).put("request_id",requestId).put("recovery_code",candidate.trim()).put("repair_fingerprint",fingerprint).put("archive_proof_sha256",PixelMcpWriteTools.hash(proof));
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(repairedAlias,true));cipher.updateAAD(repairAad(repairedAlias));byte[] sealed=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));FileOutputStream output=null;
                try{output=protectedKey.startWrite();DataOutputStream data=new DataOutputStream(output);data.writeInt(2);data.writeUTF(repairedAlias);data.write(cipher.getIV());data.writeInt(sealed.length);data.write(sealed);data.flush();protectedKey.finishWrite(output);output=null;}catch(Exception failure){if(output!=null)protectedKey.failWrite(output);throw failure;}
                if(!fingerprint.equals(readProtected().optString("repair_fingerprint")))throw new GeneralSecurityException("RECOVERY_READBACK_FAILED");
                return new JSONObject().put("replayed",false);
            }finally{Arrays.fill(candidateKey,(byte)0);if(confirmationKey!=null)Arrays.fill(confirmationKey,(byte)0);}
        }
    }
    private static String hex(byte[] digest){StringBuilder text=new StringBuilder();for(byte value:digest)text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();}
    private byte[] repairAad(String repairedAlias){return (new String(capsuleAad,StandardCharsets.US_ASCII)+"/v2/"+repairedAlias).getBytes(StandardCharsets.US_ASCII);}
    private JSONObject readProtected()throws Exception {
        if(!configured())throw new GeneralSecurityException("RECOVERY_NOT_CONFIGURED");
        byte[] iv=new byte[12],sealed;String selectedAlias=alias;int version;
        try(DataInputStream in=new DataInputStream(protectedKey.openRead())){
            version=in.readInt();if(version!=1&&version!=2)throw new IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED");if(version==2){selectedAlias=in.readUTF();if(!selectedAlias.matches(java.util.regex.Pattern.quote(alias)+"\\.repair\\.[0-9a-f-]{36}"))throw new IOException("RECOVERY_STORAGE_INVALID");}in.readFully(iv);int length=in.readInt();
            if(length<16||length>4096)throw new IOException("RECOVERY_STORAGE_INVALID");sealed=new byte[length];in.readFully(sealed);if(in.read()!=-1)throw new IOException("RECOVERY_STORAGE_INVALID");
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(selectedAlias,false),new GCMParameterSpec(128,iv));cipher.updateAAD(version==1?capsuleAad:repairAad(selectedAlias));byte[] plain=cipher.doFinal(sealed);
        try{JSONObject value=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(value.getInt("formatVersion")!=1)throw new IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED");return value;}finally{Arrays.fill(plain,(byte)0);}
    }
    private SecretKey key(boolean create)throws Exception {
        return key(alias,create);
    }
    private SecretKey key(String selectedAlias,boolean create)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(selectedAlias)){
            if(!create)throw new GeneralSecurityException("RECOVERY_KEYSTORE_UNAVAILABLE_USE_RECOVERY_CODE");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(selectedAlias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generator.generateKey();
        }
        return (SecretKey)store.getKey(selectedAlias,null);
    }
}
