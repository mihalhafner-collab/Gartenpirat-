package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real filesystem rename/journal recovery with synthetic roots; no app database activation. */
public final class BackupDatasetExchangeTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("Dataset exchange check "+checks);}
    static final class Crash extends Error{}
    static final class Journal implements BackupDatasetExchange.Journal {
        final Path path;Journal(Path path,JSONObject initial)throws Exception{this.path=path;if(!Files.exists(path))save(initial);}
        public JSONObject read()throws Exception{return new JSONObject(Files.readString(path));}
        public void save(JSONObject state)throws Exception{Path next=path.resolveSibling(path.getFileName()+".new");Files.writeString(next,state.toString());Files.move(next,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    }
    static final class Fixture {
        final Path root,live,prepared,rollback;final LinkedHashMap<String,File> paths=new LinkedHashMap<>(),sources=new LinkedHashMap<>();final Journal journal;
        Fixture(Path root)throws Exception{this.root=root;live=Files.createDirectories(root.resolve("live"));prepared=Files.createDirectories(root.resolve("prepared"));rollback=root.resolve("rollback");
            for(String key:new String[]{"database","files","preferences_haus_garten"}){Path a=live.resolve(key),b=prepared.resolve(key);if(key.equals("files")){Files.createDirectory(a);Files.createDirectory(b);a=a.resolve("qa_photo");b=b.resolve("qa_photo");}Files.writeString(a,"qa_old_"+key);Files.writeString(b,"qa_new_"+key);paths.put(key,live.resolve(key).toFile());sources.put(key,prepared.resolve(key).toFile());}
            journal=new Journal(root.resolve("journal.json"),new JSONObject().put("phase","ACTIVATING").put("targets",BackupDatasetExchange.targets(paths,sources,rollback.toFile())));
        }
        void original()throws Exception{for(String key:paths.keySet()){Path file=paths.get(key).toPath();if(key.equals("files"))file=file.resolve("qa_photo");yes(Files.readString(file).equals("qa_old_"+key));}}
        void installed()throws Exception{for(String key:paths.keySet()){Path file=paths.get(key).toPath(),old=rollback.resolve(key);if(key.equals("files")){file=file.resolve("qa_photo");old=old.resolve("qa_photo");}yes(Files.readString(file).equals("qa_new_"+key));yes(Files.readString(old).equals("qa_old_"+key));}}
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("qa_dataset_exchange_");try{
            for(int failAt=1;failAt<=6;failAt++){
                Fixture f=new Fixture(Files.createDirectory(root.resolve("qa_install_"+failAt)));int stop=failAt;int[] moves={0};
                try{BackupDatasetExchange.install(f.journal,(a,b)->{BackupDatasetExchange.ATOMIC.move(a,b);if(++moves[0]==stop)throw new Crash();});throw new AssertionError("Crash not injected");}catch(Crash expected){checks++;}
                Journal reopened=new Journal(f.journal.path,null);BackupDatasetExchange.install(reopened,BackupDatasetExchange.ATOMIC);f.installed();yes(reopened.read().getString("phase").equals("SWAPPED"));BackupDatasetExchange.install(reopened,BackupDatasetExchange.ATOMIC);f.installed();
            }
            Fixture failure=new Fixture(Files.createDirectory(root.resolve("qa_io_failure")));int[] n={0};try{BackupDatasetExchange.install(failure.journal,(a,b)->{BackupDatasetExchange.ATOMIC.move(a,b);if(++n[0]==4)throw new IOException("qa interrupted component exchange");});throw new AssertionError("IO failure not injected");}catch(IOException expected){checks++;}
            BackupDatasetExchange.rollback(new Journal(failure.journal.path,null),BackupDatasetExchange.ATOMIC);failure.original();yes(failure.journal.read().getString("phase").equals("ROLLED_BACK"));
            for(int failAt=1;failAt<=6;failAt++){
                Fixture f=new Fixture(Files.createDirectory(root.resolve("qa_rollback_"+failAt)));BackupDatasetExchange.install(f.journal,BackupDatasetExchange.ATOMIC);int stop=failAt;int[] moves={0};
                try{BackupDatasetExchange.rollback(f.journal,(a,b)->{BackupDatasetExchange.ATOMIC.move(a,b);if(++moves[0]==stop)throw new Crash();});throw new AssertionError("Rollback crash not injected");}catch(Crash expected){checks++;}
                BackupDatasetExchange.rollback(new Journal(f.journal.path,null),BackupDatasetExchange.ATOMIC);f.original();yes(f.journal.read().getString("phase").equals("ROLLED_BACK"));for(String key:f.sources.keySet()){Path file=f.sources.get(key).toPath();if(key.equals("files"))file=file.resolve("qa_photo");yes(Files.readString(file).equals("qa_new_"+key));}
            }
            Path side=Files.createDirectory(root.resolve("qa_sidecars"));File liveDb=side.resolve("qa.db").toFile(),wal=new File(liveDb+"-wal"),newDb=side.resolve("qa_new.db").toFile();Files.writeString(liveDb.toPath(),"qa_old_db");Files.writeString(wal.toPath(),"qa_old_wal");Files.writeString(newDb.toPath(),"qa_new_db");LinkedHashMap<String,File> paths=new LinkedHashMap<>();paths.put("database_wal",wal);paths.put("database",liveDb);Map<String,File> source=new HashMap<>();source.put("database",newDb);Journal j=new Journal(side.resolve("journal.json"),new JSONObject().put("phase","ACTIVATING").put("targets",BackupDatasetExchange.targets(paths,source,side.resolve("rollback").toFile())));BackupDatasetExchange.install(j,BackupDatasetExchange.ATOMIC);Files.writeString(wal.toPath(),"qa_new_wal");BackupDatasetExchange.rollback(j,BackupDatasetExchange.ATOMIC);yes(Files.readString(wal.toPath()).equals("qa_old_wal"));yes(Files.readString(side.resolve("rollback/database_wal_restored_sidecar")).equals("qa_new_wal"));yes(Files.readString(liveDb.toPath()).equals("qa_old_db"));
            System.out.println("PASS "+checks+" real filesystem cold-start journal/each-move process death/reopen/replay/rollback/sidecar preservation checks;synthetic roots,no active app or SQLite swap");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}
