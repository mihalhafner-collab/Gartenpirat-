package at.hausgarten.service;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public final class BackupStorageBudgetTests {
    static int checks;
    static void yes(boolean value){checks++;if(!value)throw new AssertionError("Storage reserve check "+checks);}
    interface Task{void run()throws Exception;}
    static void refused(Task action)throws Exception {try{action.run();}catch(IOException expected){yes(expected.getMessage().equals("BACKUP_STORAGE_RESERVE_REQUIRED"));return;}throw new AssertionError("Expected reserve refusal");}
    public static void main(String[] args)throws Exception {
        long reserve=BackupStorageBudget.BUSINESS_RESERVE_BYTES;
        BackupStorageBudget.requireAvailable(reserve,0);checks++;
        refused(()->BackupStorageBudget.requireAvailable(reserve,1));refused(()->BackupStorageBudget.requireAvailable(reserve-1,0));refused(()->BackupStorageBudget.requireAvailable(Long.MAX_VALUE,-1));
        BackupStorageBudget.requireAvailable(30L*1024*1024*1024,20L*1024*1024*1024);checks++;
        Path directory=Files.createTempDirectory("qa_backup_reserve_");Path good=directory.resolve("qa_last_good.hgb"),next=directory.resolve("qa_new_partial.hgb");byte[] original={2,5,7};Files.write(good,original);
        try{
            long[] free={reserve+4096};try(OutputStream output=BackupStorageBudget.guard(Files.newOutputStream(next),()->free[0])){
                output.write(new byte[2048]);free[0]=reserve+1024;refused(()->output.write(new byte[2048]));yes(Files.size(next)==2048);
                free[0]=reserve;refused(()->output.write(7));
            }
            yes(Arrays.equals(Files.readAllBytes(good),original));
            ByteArrayOutputStream output=new ByteArrayOutputStream();try(OutputStream guarded=BackupStorageBudget.guard(output,()->Long.MAX_VALUE)){
                Thread.currentThread().interrupt();try{guarded.write(new byte[1024]);throw new AssertionError("Stopped job continued");}catch(InterruptedIOException expected){yes(output.size()==0);}finally{Thread.interrupted();}
            }
            System.out.println("PASS "+checks+" storage reserve/long-size/mid-write shortage/cancellation/previous-generation checks;synthetic space port,not physical disk-full proof");
        }finally{Files.deleteIfExists(next);Files.delete(good);Files.delete(directory);}
    }
}
