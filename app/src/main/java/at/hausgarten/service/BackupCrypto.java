package at.hausgarten.service;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.aead.AeadConfig2026;
import com.google.crypto.tink.aead.AesGcmKey;
import com.google.crypto.tink.aead.AesGcmParameters;
import com.google.crypto.tink.streamingaead.StreamingAeadConfig2026;
import com.google.crypto.tink.util.SecretBytes;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import java.util.Base64;

/** Reader for existing encrypted archives. New backups use plaintext ZIP only. */
final class BackupCrypto {
    private static final byte[] MAGIC="HG-BACKUP-1\n".getBytes(StandardCharsets.US_ASCII);
    private static final int MAX_ENVELOPE=16*1024;
    static byte[] recoveryKey(String code)throws GeneralSecurityException {
        if(code==null||!code.trim().matches("HG1\\.[A-Za-z0-9_-]{43}\\.[0-9a-f]{8}"))throw new GeneralSecurityException("RECOVERY_CODE_INVALID");
        String value=code.trim();byte[] key;
        try{key=Base64.getUrlDecoder().decode(value.substring(4,47));}catch(IllegalArgumentException invalid){throw new GeneralSecurityException("RECOVERY_CODE_INVALID");}
        if(key.length!=32||!MessageDigest.isEqual(value.getBytes(StandardCharsets.US_ASCII),code(key).getBytes(StandardCharsets.US_ASCII))){Arrays.fill(key,(byte)0);throw new GeneralSecurityException("RECOVERY_CODE_INVALID");}
        return key;
    }
    private static String code(byte[] key)throws GeneralSecurityException {
        MessageDigest d=MessageDigest.getInstance("SHA-256");d.update("Hausgarten recovery checksum v1\n".getBytes(StandardCharsets.US_ASCII));byte[] hash=d.digest(key);
        StringBuilder checksum=new StringBuilder();for(int i=0;i<4;i++)checksum.append(String.format(java.util.Locale.ROOT,"%02x",hash[i]&255));
        return "HG1."+Base64.getUrlEncoder().withoutPadding().encodeToString(key)+"."+checksum;
    }
    private static Aead recoveryAead(String recovery)throws GeneralSecurityException {
        byte[] key=recoveryKey(recovery);
        try{
            AesGcmParameters parameters=AesGcmParameters.builder().setKeySizeBytes(32).setIvSizeBytes(12).setTagSizeBytes(16).setVariant(AesGcmParameters.Variant.NO_PREFIX).build();
            AesGcmKey imported=AesGcmKey.builder().setParameters(parameters).setKeyBytes(SecretBytes.copyFrom(key,InsecureSecretKeyAccess.get())).build();
            return KeysetHandle.newBuilder().setConfiguration(AeadConfig2026.get()).addEntry(KeysetHandle.importKey(imported).withFixedId(1).makePrimary()).build().getPrimitive(AeadConfig2026.get(),Aead.class);
        }finally{Arrays.fill(key,(byte)0);}
    }
    static InputStream decrypt(InputStream source,String recovery)throws Exception {
        DataInputStream in=new DataInputStream(source);byte[] magic=new byte[MAGIC.length];in.readFully(magic);if(!Arrays.equals(magic,MAGIC))throw new IOException("BACKUP_FORMAT_UNSUPPORTED");
        int length=in.readInt();if(length<1||length>MAX_ENVELOPE)throw new IOException("BACKUP_ENVELOPE_INVALID");byte[] wrapped=new byte[length];in.readFully(wrapped);
        ByteArrayOutputStream header=new ByteArrayOutputStream();DataOutputStream bytes=new DataOutputStream(header);bytes.write(magic);bytes.writeInt(length);bytes.write(wrapped);bytes.flush();
        KeysetHandle handle=TinkJsonProtoKeysetFormat.parseEncryptedKeyset(new String(wrapped,StandardCharsets.UTF_8),recoveryAead(recovery),MAGIC,StreamingAeadConfig2026.get());
        return handle.getPrimitive(StreamingAeadConfig2026.get(),StreamingAead.class).newDecryptingStream(source,header.toByteArray());
    }
    private BackupCrypto(){}
}
