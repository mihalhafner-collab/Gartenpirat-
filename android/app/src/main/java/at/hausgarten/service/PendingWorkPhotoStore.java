package at.hausgarten.service;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Crash-safe handoff for a work photo whose worklog has already been stored. */
final class PendingWorkPhotoStore {
    private static final long MAX_JSON_BYTES = ((PhotoAttachmentStore.MAX_BYTES + 2L) / 3L) * 4L + 64L * 1024L;
    private final Context context;
    private final AtomicFile file;

    PendingWorkPhotoStore(Context context) {
        LocalAuthorityGuard.assertStoreMayOpen(context);this.context=context;
        file = new AtomicFile(new File(context.getFilesDir(), "pending-work-photo.json"));
    }

    synchronized String stage(String raw) throws Exception {
        LocalAuthorityGuard.assertStoreMayOpen(context);
        if (raw == null) throw new IllegalArgumentException("Der Foto-Wiederholauftrag fehlt.");
        JSONObject payload = new JSONObject(raw);
        if (payload.optString("id").isEmpty())
            payload.put("id", "pho_mobile_" + UUID.randomUUID().toString().replace("-", ""));
        validate(payload);
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON_BYTES) throw new IllegalArgumentException("Der Foto-Wiederholauftrag ist zu groß.");
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
        } catch (Exception error) {
            if (output != null) file.failWrite(output);
            throw error;
        }
        return payload.toString();
    }

    synchronized String read() throws Exception {
        LocalAuthorityGuard.assertStoreMayOpen(context);
        File source = file.getBaseFile();
        if (!source.isFile()&&!new File(source+".bak").isFile()) return "";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int count;
        try (FileInputStream input = file.openRead()) {
            // openRead restores AtomicFile's last valid .bak after an interrupted
            // write. Checking only the missing base file would hide that draft.
            if (source.length() <= 0 || source.length() > MAX_JSON_BYTES)
                throw new IllegalStateException("Der gespeicherte Foto-Wiederholauftrag ist beschädigt oder zu groß.");
            while ((count = input.read(block)) >= 0) {
                if (output.size() + count > MAX_JSON_BYTES)
                    throw new IllegalStateException("Der gespeicherte Foto-Wiederholauftrag ist zu groß.");
                output.write(block, 0, count);
            }
        }
        JSONObject payload = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
        validate(payload);
        return payload.toString();
    }

    synchronized void clear() {
        LocalAuthorityGuard.assertStoreMayOpen(context);
        file.delete();
    }

    private static void validate(JSONObject payload) {
        String id = payload.optString("id").trim();
        if (!id.matches("[A-Za-z0-9._:-]{1,180}"))
            throw new IllegalArgumentException("Die Foto-ID des Wiederholauftrags ist ungültig.");
        if (payload.optString("customer_id").trim().isEmpty() || payload.optString("worklog_id").trim().isEmpty())
            throw new IllegalArgumentException("Kunde und Arbeitsaufzeichnung des Fotos fehlen.");
        String data = payload.optString("data").trim();
        String prefix = "data:image/jpeg;base64,";
        if (!data.startsWith(prefix)) throw new IllegalArgumentException("Der Wiederholauftrag enthält kein vorbereitetes JPEG-Foto.");
        String encoded = data.substring(prefix.length());
        if (encoded.length() > ((PhotoAttachmentStore.MAX_BYTES + 2L) / 3L) * 4L + 8L || encoded.length() % 4 != 0)
            throw new IllegalArgumentException("Das vorbereitete Foto ist zu groß oder beschädigt.");
        byte[] bytes;
        try { bytes = Base64.decode(encoded, Base64.DEFAULT); }
        catch (IllegalArgumentException error) { throw new IllegalArgumentException("Das vorbereitete Foto ist beschädigt."); }
        if (bytes.length == 0 || bytes.length > PhotoAttachmentStore.MAX_BYTES || !"image/jpeg".equals(PhotoAttachmentStore.sniff(bytes)))
            throw new IllegalArgumentException("Der Wiederholauftrag enthält kein gültiges JPEG-Foto.");
    }
}
