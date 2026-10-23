package at.hausgarten.service;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Pins app-private immutable originals. Never deletes or changes the source files. */
final class BackupFilePins {
    static void capture(File sourceRoot,File privateStage,String prefix)throws Exception {
        capture(sourceRoot,privateStage,prefix,false);
    }
    static void pinImmutable(File sourceRoot,File privateStage,String prefix)throws Exception {
        capture(sourceRoot,privateStage,prefix,true);
    }
    private static void capture(File sourceRoot,File privateStage,String prefix,boolean immutable)throws Exception {
        if(!sourceRoot.exists())return;
        if(Files.isSymbolicLink(sourceRoot.toPath())||!sourceRoot.isDirectory())throw new IOException("BACKUP_SOURCE_LINK_OR_DIRECTORY_INVALID");
        File[] children=sourceRoot.listFiles();if(children==null)throw new IOException("BACKUP_SOURCE_UNREADABLE");
        Arrays.sort(children,Comparator.comparing(File::getName));
        for(File source:children){
            EncryptedBackupArchive.checkCancelled();
            String path=prefix+"/"+source.getName();new EncryptedBackupArchive.Source(path,source);
            if(Files.isSymbolicLink(source.toPath()))throw new IOException("BACKUP_SOURCE_LINK_REJECTED");
            if(source.isDirectory()){capture(source,privateStage,path,immutable);continue;}
            if(!source.isFile())throw new IOException("BACKUP_SOURCE_UNREADABLE");
            File target=new File(privateStage,path);if(!target.getCanonicalPath().startsWith(privateStage.getCanonicalPath()+File.separator))throw new IOException("BACKUP_PATH_INVALID");
            if(target.exists())continue;File parent=target.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("BACKUP_STAGING_UNAVAILABLE");
            if(!immutable){
                String hash=EncryptedBackupArchive.sha256(source);copy(source,target);
                if(!hash.equals(EncryptedBackupArchive.sha256(source))||!hash.equals(EncryptedBackupArchive.sha256(target)))throw new IOException("BACKUP_SOURCE_CHANGED");
                continue;
            }
            try{Files.createLink(target.toPath(),source.toPath());}
            catch(UnsupportedOperationException|java.nio.file.FileSystemException unsupported){
                // No hardlinks on this private filesystem: copy instead, never move.
                copy(source,target);
            }
        }
    }
    private static void copy(File source,File target)throws Exception {
        try(InputStream input=new FileInputStream(source);java.nio.channels.FileChannel output=java.nio.channels.FileChannel.open(target.toPath(),java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE)){
            EncryptedBackupArchive.copy(input,java.nio.channels.Channels.newOutputStream(output),null,source.length());output.force(true);
        }
    }
    static List<EncryptedBackupArchive.Source> sources(File privateStage)throws Exception {
        List<EncryptedBackupArchive.Source> result=new ArrayList<>();collect(privateStage,"",result);return result;
    }
    private static void collect(File directory,String prefix,List<EncryptedBackupArchive.Source> result)throws Exception {
        File[] entries=directory.listFiles();if(entries==null)throw new IOException("BACKUP_STAGING_UNREADABLE");Arrays.sort(entries,Comparator.comparing(File::getName));
        for(File file:entries){if(Files.isSymbolicLink(file.toPath()))throw new IOException("BACKUP_STAGING_LINK_REJECTED");String path=prefix+file.getName();if(file.isDirectory())collect(file,path+"/",result);else if(file.isFile())result.add(new EncryptedBackupArchive.Source(path,file));else throw new IOException("BACKUP_STAGING_UNREADABLE");}
    }
    private BackupFilePins(){}
}
