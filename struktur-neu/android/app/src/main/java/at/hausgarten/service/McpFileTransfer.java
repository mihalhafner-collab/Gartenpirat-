package at.hausgarten.service;
import java.util.*;
import org.json.*;

/** Bounded, contiguous, hash-verified upload blocks; no platform I/O. */
final class McpFileTransfer {
    static String sha(byte[] bytes)throws Exception{
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder s=new StringBuilder();for(byte b:hash)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();
    }
    static byte[] append(byte[] prior,String encoded,int offset,int total,String expected)throws Exception{
        byte[] block;try{block=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT","Ungültiger Dateiblock.");}
        if(!Base64.getEncoder().encodeToString(block).equals(encoded)||block.length<1||block.length>PixelMcpOwnerTools.CHUNK||offset!=prior.length||total<1||total>20*1024*1024||prior.length+block.length>total||!sha(block).equals(expected))throw new PixelMcpWriteTools.Rejected("FILE_VERSION_CONFLICT","Dateiblock, Position, Größe oder Prüfsumme stimmt nicht. Upload bleibt erhalten.");
        byte[] out=Arrays.copyOf(prior,prior.length+block.length);System.arraycopy(block,0,out,prior.length,block.length);return out;
    }
}
