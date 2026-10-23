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
    BackupRecoveryKeyStore(Context context)throws Exception {
        File directory=new File(context.getNoBackupFilesDir(),"backup-key-protection");
        protectedKey=new AtomicFile(new File(directory,"recovery.v1"));
        // An isolated QA Context receives a different alias; never access the active key in tests.
        byte[] scope=MessageDigest.getInstance("SHA-256").digest(context.getFilesDir().getCanonicalPath().getBytes(StandardCharsets.UTF_8));
        StringBuilder name=new StringBuilder("hausgarten.backup.v1.");for(int i=0;i<16;i++)name.append(String.format(java.util.Locale.ROOT,"%02x",scope[i]&255));alias=name.toString();
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
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(AAD);byte[] sealed=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
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
    private JSONObject readProtected()throws Exception {
        if(!configured())throw new GeneralSecurityException("RECOVERY_NOT_CONFIGURED");
        byte[] iv=new byte[12],sealed;
        try(DataInputStream in=new DataInputStream(protectedKey.openRead())){
            if(in.readInt()!=1)throw new IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED");in.readFully(iv);int length=in.readInt();
            if(length<16||length>4096)throw new IOException("RECOVERY_STORAGE_INVALID");sealed=new byte[length];in.readFully(sealed);if(in.read()!=-1)throw new IOException("RECOVERY_STORAGE_INVALID");
        }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,iv));cipher.updateAAD(AAD);byte[] plain=cipher.doFinal(sealed);
        try{JSONObject value=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(value.getInt("formatVersion")!=1)throw new IOException("RECOVERY_STORAGE_VERSION_UNSUPPORTED");return value;}finally{Arrays.fill(plain,(byte)0);}
    }
    private SecretKey key(boolean create)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(alias)){
            if(!create)throw new GeneralSecurityException("RECOVERY_KEYSTORE_UNAVAILABLE_USE_RECOVERY_CODE");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generator.generateKey();
        }
        return (SecretKey)store.getKey(alias,null);
    }
}
