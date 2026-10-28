package at.hausgarten.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Private, content-addressed originals. Metadata remains in the existing photo entity or equipment owner record. */
final class PhotoAttachmentStore {
    static final int MAX_BYTES = 20 * 1024 * 1024;
    static final long MAX_PIXELS = 120_000_000L;
    static final int MAX_EDGE = 16_384;
    private static final Object FILE_LOCK = new Object();
    private static final Set<String> ROLES = new HashSet<>(Arrays.asList("profile","documentation","before","during","after","damage","other"));
    private final File directory;

    static final class Prepared {
        final byte[] bytes;
        final JSONObject metadata;
        final boolean created;
        Prepared(byte[] bytes,JSONObject metadata,boolean created){this.bytes=bytes;this.metadata=metadata;this.created=created;}
    }

    PhotoAttachmentStore(Context context) {
        directory = new File(context.getFilesDir(), "customer-photo-originals");
    }

    Prepared prepare(JSONObject payload) throws Exception { return prepare(payload,false); }
    Prepared prepareLegacy(JSONObject payload) throws Exception { return prepare(payload,true); }
    private Prepared prepare(JSONObject payload,boolean legacy) throws Exception {
        String value=payload.optString("data","").trim();
        if(value.isEmpty())throw new IllegalArgumentException("Kunde und Foto sind erforderlich.");
        String declared="";int comma=value.indexOf(',');
        if(value.startsWith("data:")){
            if(comma<0||!value.substring(0,comma).toLowerCase(Locale.ROOT).contains(";base64"))throw new IllegalArgumentException("Das Foto besitzt kein gültiges Base64-Datenformat.");
            String header=value.substring(5,comma);int semicolon=header.indexOf(';');declared=canonicalMime(semicolon<0?header:header.substring(0,semicolon));value=value.substring(comma+1);
        }
        if(value.length()>((MAX_BYTES+2L)/3L)*4L+8L||value.length()%4!=0||!value.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException("Das Foto besitzt keine gültigen Base64-Daten.");
        byte[] bytes;
        try{bytes=Base64.decode(value,Base64.DEFAULT);}catch(IllegalArgumentException bad){throw new IllegalArgumentException("Das Foto besitzt keine gültigen Base64-Daten.");}
        JSONObject before=inspect(bytes,declared);
        String role=payload.optString("role","").trim().toLowerCase(Locale.ROOT),caption=payload.optString("caption","").trim().toLowerCase(Locale.ROOT);
        if(role.isEmpty()&&(caption.equals("profilbild")||caption.equals("profile")||caption.equals("profilfoto")))role="profile";
        if(!ROLES.contains(role))role="documentation";
        if(!legacy)bytes=optimize(bytes,before,payload.optString("_photo_quality","optimized"),role.equals("profile"));
        JSONObject metadata=inspect(bytes,"");metadata.put("role",role);
        metadata.put("original_width",before.getInt("width")).put("original_height",before.getInt("height")).put("original_byte_size",before.getLong("byte_size")).put("optimization",legacy?"preserved_legacy":payload.optString("_photo_quality","optimized"));
        boolean created=store(bytes,metadata);
        if(!legacy)try{makeThumbnail(bytes,metadata);}catch(Exception ignored){/* Original remains verified; preview can be regenerated. */}
        return new Prepared(bytes,metadata,created);
    }

    private void makeThumbnail(byte[] bytes,JSONObject metadata)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;while(Math.max(metadata.getInt("width"),metadata.getInt("height"))/(o.inSampleSize*2)>480)o.inSampleSize*=2;
        Bitmap b=null,oriented=null,small=null;try{b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(b==null)throw new IOException("Vorschau nicht lesbar.");oriented=orient(b,PhotoPolicy.orientation(bytes));double scale=Math.min(1,240.0/Math.max(oriented.getWidth(),oriented.getHeight()));small=Bitmap.createScaledBitmap(oriented,Math.max(1,(int)(oriented.getWidth()*scale)),Math.max(1,(int)(oriented.getHeight()*scale)),true);ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,65,out);byte[] raw=out.toByteArray();String hash=sha256(raw);storeThumbnail(raw,hash+".jpg");metadata.put("thumbnail_storage_name",hash+".jpg").put("thumbnail_sha256",hash);}
        finally{if(small!=null&&small!=oriented)small.recycle();if(oriented!=null&&oriented!=b)oriented.recycle();if(b!=null)b.recycle();}
    }
    File thumbnailFile(String name)throws Exception{if(!name.matches("[0-9a-f]{64}\\.jpg"))throw new IllegalArgumentException("Ungültige Vorschaureferenz.");return new File(new File(directory,"thumbnails"),name);}
    void storeThumbnail(byte[] bytes,String name)throws Exception{
        if(bytes.length==0||bytes.length>65536||!"image/jpeg".equals(sniff(bytes))||!name.equals(sha256(bytes)+".jpg"))throw new IOException("Vorschau-Prüfsumme ist ungültig.");File f=thumbnailFile(name);File dir=f.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Vorschauspeicher fehlt.");if(f.isFile())return;AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();out.write(bytes);a.finishWrite(out);}catch(Exception e){if(out!=null)a.failWrite(out);throw e;}
    }
    byte[] readThumbnail(String name)throws Exception{File f=thumbnailFile(name);if(f.length()>65536)throw new IOException("Vorschau ist zu groß.");byte[] b=java.nio.file.Files.readAllBytes(f.toPath());if(b.length>65536||!name.equals(sha256(b)+".jpg"))throw new IOException("Vorschau-Prüfsumme ist ungültig.");return b;}

    private static Bitmap orient(Bitmap decoded,int orientation){
        Matrix m=new Matrix();
        switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;default:break;}
        return orientation==1?decoded:Bitmap.createBitmap(decoded,0,0,decoded.getWidth(),decoded.getHeight(),m,true);
    }

    private byte[] optimize(byte[] bytes,JSONObject before,String quality,boolean profile)throws Exception {
        if("original".equals(quality))return bytes; // Explicit choice; existing files are never reprocessed.
        int orientation=PhotoPolicy.orientation(bytes),edge=PhotoPolicy.edge(quality,profile);
        if(Math.max(before.getInt("width"),before.getInt("height"))<=edge&&bytes.length<=3*1024*1024&&orientation==1)
            return PhotoPolicy.withoutMetadata(bytes,before.getString("mime_type")); // Small images keep their encoded pixels.
        Bitmap decoded=null,oriented=null,scaled=null;
        try {
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;
            while(Math.max(before.getInt("width"),before.getInt("height"))/(options.inSampleSize*2)>edge)options.inSampleSize*=2;
            decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(decoded==null)throw new IOException("Foto kann nicht optimiert werden.");
            oriented=orient(decoded,orientation);
            double scale=Math.min(1,edge/(double)Math.max(oriented.getWidth(),oriented.getHeight()));
            scaled=Bitmap.createScaledBitmap(oriented,Math.max(1,(int)Math.round(oriented.getWidth()*scale)),Math.max(1,(int)Math.round(oriented.getHeight()*scale)),true);
            byte[] result=null;
            for(int q:new int[]{"high".equals(quality)?92:84,80,76}){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                if(!scaled.compress(Bitmap.CompressFormat.JPEG,q,out)||out.size()==0)throw new IOException("Optimiertes Foto ist leer.");
                result=out.toByteArray();if(result.length<=3*1024*1024)break;
            }
            inspect(result,"image/jpeg");return result;
        }catch(OutOfMemoryError exhausted){throw new IOException("Für dieses Foto ist zu wenig Arbeitsspeicher verfügbar. Entwurf bleibt erhalten.");}
        finally{if(scaled!=null&&scaled!=oriented)scaled.recycle();if(oriented!=null&&oriented!=decoded)oriented.recycle();if(decoded!=null)decoded.recycle();}
    }

    JSONObject storageSummary() throws Exception {
        long bytes=0;int count=0;File[] files=directory.listFiles();
        if(files!=null)for(File f:files)if(f.isFile()&&canonicalName(f.getName())){bytes+=f.length();count++;}
        long thumbnailBytes=0;File[] thumbnails=new File(directory,"thumbnails").listFiles();if(thumbnails!=null)for(File f:thumbnails)if(f.isFile()&&f.getName().matches("[0-9a-f]{64}\\.jpg"))thumbnailBytes+=f.length();
        return new JSONObject().put("local_files",count).put("original_bytes",bytes).put("thumbnail_bytes",thumbnailBytes).put("local_bytes",bytes+thumbnailBytes).put("available_bytes",directory.getParentFile().getUsableSpace()).put("automatic_deletion",false);
    }

    JSONObject inspect(byte[] bytes,String declaredMime)throws Exception{
        if(bytes==null||bytes.length==0)throw new IllegalArgumentException("Das Foto ist leer.");
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Das Foto darf maximal 20 MB groß sein.");
        String mime=sniff(bytes);if(mime.isEmpty())throw new IllegalArgumentException("Die Datei ist kein unterstütztes Foto.");
        declaredMime=canonicalMime(declaredMime);if(!declaredMime.isEmpty()&&!mime.equals(declaredMime))throw new IllegalArgumentException("Dateityp und tatsächlicher Bildinhalt stimmen nicht überein.");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("Das Foto ist beschädigt oder kann nicht gelesen werden.");
        if(bounds.outWidth>MAX_EDGE||bounds.outHeight>MAX_EDGE||(long)bounds.outWidth*bounds.outHeight>MAX_PIXELS)throw new IllegalArgumentException("Das Foto besitzt unzulässig große Bildabmessungen.");
        BitmapFactory.Options decode=new BitmapFactory.Options();decode.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/decode.inSampleSize>2048)decode.inSampleSize*=2;
        Bitmap verified=null;try{verified=BitmapFactory.decodeByteArray(bytes,0,bytes.length,decode);if(verified==null)throw new IllegalArgumentException("Das Foto ist beschädigt oder kann nicht gelesen werden.");}catch(OutOfMemoryError exhausted){throw new IllegalArgumentException("Das Foto kann nicht speicherschonend geprüft werden.");}finally{if(verified!=null)verified.recycle();}
        String hash=sha256(bytes),extension=mime.equals("image/jpeg")?".jpg":mime.equals("image/png")?".png":mime.equals("image/webp")?".webp":mime.equals("image/gif")?".gif":".heic";
        return new JSONObject().put("content_sha256",hash).put("mime_type",mime).put("byte_size",bytes.length)
            .put("storage_name",hash+extension).put("width",bounds.outWidth).put("height",bounds.outHeight);
    }

    boolean store(byte[] bytes,JSONObject metadata)throws Exception{
        String name=metadata.getString("storage_name");if(!canonicalName(name))throw new IllegalArgumentException("Ungültiger interner Foto-Dateiname.");
        synchronized(FILE_LOCK){
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Privater Foto-Speicher ist nicht verfügbar.");
            File target=new File(directory,name);
            if(target.exists()){verify(target,metadata);return false;}
            AtomicFile atomic=new AtomicFile(target);FileOutputStream out=null;
            try{out=atomic.startWrite();out.write(bytes);atomic.finishWrite(out);}catch(Exception error){if(out!=null)atomic.failWrite(out);throw error;}
            verify(target,metadata);return true;
        }
    }

    void storeExpected(byte[] bytes,JSONObject expected)throws Exception{
        JSONObject actual=inspect(bytes,expected.optString("mime_type"));
        if(!actual.getString("content_sha256").equals(expected.optString("content_sha256"))||actual.getLong("byte_size")!=expected.optLong("byte_size")||!actual.getString("storage_name").equals(expected.optString("storage_name")))throw new IllegalArgumentException("Die Foto-Metadaten stimmen nicht mit dem Original überein.");
        store(bytes,actual);
    }

    File file(JSONObject metadata){return new File(directory,metadata.optString("storage_name"));}

    byte[] read(JSONObject metadata)throws Exception{return verify(file(metadata),metadata);}

    void deleteThumbnailVerified(String name)throws Exception{
        synchronized(FILE_LOCK){
            if(!name.matches("[0-9a-f]{64}\\.jpg"))throw new IOException("Ungültige Vorschau.");
            File file=thumbnailFile(name);if(!file.exists())return;
            if(!name.equals(sha256(readThumbnail(name))+".jpg"))throw new IOException("Vorschau verändert.");
            if(!file.delete())throw new IOException("Vorschau konnte nicht entfernt werden.");
        }
    }
    void discardCreated(JSONObject metadata)throws Exception{
        synchronized(FILE_LOCK){
            File target=file(metadata);if(!target.exists())return;
            verify(target,metadata);
            if(!target.delete())throw new IOException("Unvollständig gespeichertes Foto konnte nicht bereinigt werden.");
        }
    }

    String dataUri(JSONObject metadata)throws Exception{
        return "data:"+metadata.getString("mime_type")+";base64,"+Base64.encodeToString(read(metadata),Base64.NO_WRAP);
    }

    private byte[] verify(File file,JSONObject metadata)throws Exception{
        if(!file.isFile()||file.length()>MAX_BYTES)throw new IOException("Das Foto-Original fehlt oder ist zu groß.");
        ByteArrayOutputStream output=new ByteArrayOutputStream((int)file.length());byte[] block=new byte[8192];int count;
        try(FileInputStream input=new FileInputStream(file)){while((count=input.read(block))>=0){if(output.size()+count>MAX_BYTES)throw new IOException("Das Foto-Original ist zu groß.");output.write(block,0,count);}}
        byte[] bytes=output.toByteArray();JSONObject actual=inspect(bytes,metadata.optString("mime_type"));
        if(!actual.getString("content_sha256").equals(metadata.optString("content_sha256"))||actual.getLong("byte_size")!=metadata.optLong("byte_size")||!file.getName().equals(metadata.optString("storage_name")))throw new IOException("Das gespeicherte Foto-Original hat eine abweichende Prüfsumme.");
        return bytes;
    }

    static String legacyName(JSONObject photo)throws Exception {
        String name=photo.optString("filename");if(!name.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,179}\\.(?:jpg|jpeg|png|webp|gif|heic|heif)")||name.contains("..")||!photo.optString("storage_name").isEmpty())throw new PixelMcpWriteTools.Rejected("LEGACY_MEDIA_UNAVAILABLE","Altes Foto benötigt eine sichere Dateireferenz und sein Original im freigegebenen Fotoordner.");return name;
    }
    static boolean canonicalName(String name){return name!=null&&name.matches("[0-9a-f]{64}\\.(?:jpg|png|webp|gif|heic)");}
    static String canonicalMime(String value){value=value==null?"":value.trim().toLowerCase(Locale.ROOT);if(value.equals("image/jpg")||value.equals("image/pjpeg"))return "image/jpeg";if(value.equals("image/heif"))return "image/heic";return value;}
    static String sniff(byte[] bytes){
        if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)return "image/jpeg";
        if(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G'&&(bytes[4]&255)==13&&(bytes[5]&255)==10&&(bytes[6]&255)==26&&(bytes[7]&255)==10)return "image/png";
        if(bytes.length>=6&&(new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII).equals("GIF87a")||new String(bytes,0,6,java.nio.charset.StandardCharsets.US_ASCII).equals("GIF89a")))return "image/gif";
        if(bytes.length>=12&&new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP"))return "image/webp";
        if(bytes.length>=12&&new String(bytes,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")){String brand=new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII);if(Arrays.asList("heic","heix","hevc","hevx","heim","heis","mif1","msf1").contains(brand))return "image/heic";}
        return "";
    }
    static String sha256(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",value&255));return out.toString();}
}
