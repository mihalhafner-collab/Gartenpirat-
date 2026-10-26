package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.Locale;

/** Read-only verification of existing historical evidence; no remote backup creation. */
final class LegacyConflictBackup {
    private static PixelMcpWriteTools.Rejected retired(){return new PixelMcpWriteTools.Rejected("LEGACY_SYNC_RETIRED","Der frühere Drive-Geschäftsabgleich ist entfernt. Verschlüsselte Vollsicherung gesondert prüfen.");}
    static void requireReady(Context context,PixelLocalStore store)throws Exception{throw retired();}
    static void requireReady(Context context,PixelLocalStore store,String expected)throws Exception{throw retired();}
    static JSONObject create(Context context,PixelLocalStore store,String request)throws Exception{throw retired();}
    static JSONObject create(Context context,PixelLocalStore store,String request,String expected)throws Exception{throw retired();}
    static JSONObject verify(Context c,PixelLocalStore s,String id,String preview)throws Exception {
        if(!id.matches("legacy_[0-9a-f]{32}"))throw new IllegalArgumentException("Ungültige Sicherungskennung.");
        JSONObject meta=new JSONObject(s.ownerValue("legacy-backup:"+id,"{}"));File file=new File(new File(new File(c.getNoBackupFilesDir(),"legacy-conflict-backups"),id),"backup.zip");
        if(!meta.optBoolean("complete")||!preview.equals(meta.optString("preview_hash"))||!file.isFile()||!sha(file).equals(meta.optString("sha256")))throw new IllegalArgumentException("Vollständige unveränderte Sicherung fehlt. Nichts abgeschlossen.");if(meta.has("business_hash")&&!meta.getString("business_hash").equals(businessHash(s.getState())))throw new PixelMcpWriteTools.Rejected("WRITE_CONFLICT","Geschäftsdaten seit der Sicherung geändert. Neu sichern; nichts abgeschlossen.");return meta;
    }
    private static String businessHash(JSONObject state)throws Exception {
        JSONObject all=PixelMcpReadTools.effective(state);return PixelMcpWriteTools.hash(new JSONObject().put("business",PixelDriveState.integrity(all)).put("settings",all.optJSONObject("business_settings")).put("archive",OwnerJournalRules.archives(all)));
    }
    private static String sha(File f)throws Exception{java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}StringBuilder h=new StringBuilder();for(byte b:d.digest())h.append(String.format(Locale.ROOT,"%02x",b&255));return h.toString();}
}
