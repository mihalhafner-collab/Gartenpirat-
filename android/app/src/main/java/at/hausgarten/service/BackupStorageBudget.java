package at.hausgarten.service;

import java.io.*;
import java.util.function.LongSupplier;

/** Leaves space for local business writes. This is a free-space reserve, not an archive limit. */
final class BackupStorageBudget {
    static final long BUSINESS_RESERVE_BYTES=256L*1024*1024;
    static void require(File volume,long additionalBytes)throws IOException {requireAvailable(volume.getUsableSpace(),additionalBytes);}
    static void requireAvailable(long available,long additionalBytes)throws IOException {
        if(additionalBytes<0||available<BUSINESS_RESERVE_BYTES||additionalBytes>available-BUSINESS_RESERVE_BYTES)throw new IOException("BACKUP_STORAGE_RESERVE_REQUIRED");
    }
    static OutputStream guard(OutputStream output,File volume){return guard(output,volume::getUsableSpace);}
    static OutputStream guard(OutputStream output,LongSupplier available){
        return new FilterOutputStream(output){
            @Override public void write(int value)throws IOException {EncryptedBackupArchive.checkCancelled();requireAvailable(available.getAsLong(),1);out.write(value);}
            @Override public void write(byte[] bytes,int offset,int length)throws IOException {EncryptedBackupArchive.checkCancelled();requireAvailable(available.getAsLong(),length);out.write(bytes,offset,length);}
        };
    }
    private BackupStorageBudget(){}
}
