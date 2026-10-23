package at.hausgarten.service;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class BackupFilePinsTests {
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("test_backup_pin_");
        try{
            File source=Files.createDirectory(root.resolve("source")).toFile(),stage=Files.createDirectory(root.resolve("stage")).toFile();
            Path original=source.toPath().resolve("qa_Ä.txt");Files.write(original,new byte[]{1,2,3});BackupFilePins.pinImmutable(source,stage,"files");
            Path replacement=root.resolve("test_replacement.txt");Files.write(replacement,new byte[]{4,5,6});Files.move(replacement,original,StandardCopyOption.REPLACE_EXISTING);
            if(!Arrays.equals(Files.readAllBytes(stage.toPath().resolve("files/qa_Ä.txt")),new byte[]{1,2,3}))throw new AssertionError("Pinned original lost after atomic replacement");
            File copied=Files.createDirectory(root.resolve("copied")).toFile();BackupFilePins.capture(source,copied,"files");Files.write(original,new byte[]{9,9,9});
            if(!Arrays.equals(Files.readAllBytes(copied.toPath().resolve("files/qa_Ä.txt")),new byte[]{4,5,6}))throw new AssertionError("Mutable source changed the captured copy");
            if(BackupFilePins.sources(stage).size()!=1||!BackupFilePins.sources(stage).get(0).path.equals("files/qa_Ä.txt"))throw new AssertionError("Filename/reference changed");
            Files.createSymbolicLink(source.toPath().resolve("test_link"),original);
            try{BackupFilePins.capture(source,Files.createDirectory(root.resolve("rejected")).toFile(),"files");throw new AssertionError("Symlink must fail closed");}catch(IOException expected){}
            if(!Arrays.equals(Files.readAllBytes(original),new byte[]{9,9,9}))throw new AssertionError("Source changed");
            System.out.println("PASS5 immutable pin/mutable copy/UTF8/symlink/source preservation checks");
        }finally{
            try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}
        }
    }
}
