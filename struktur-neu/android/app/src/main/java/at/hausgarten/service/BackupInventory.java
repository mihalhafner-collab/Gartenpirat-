package at.hausgarten.service;

import org.json.*;
import java.util.*;

/** Describes existing defects. These observations never decide whether bytes can be backed up. */
final class BackupInventory {
    interface Originals { String problem(String kind, JSONObject row) throws Exception; }
    static JSONObject inspect(JSONObject effective, Originals originals) throws Exception {
        JSONObject counts = new JSONObject();
        JSONArray warnings = new JSONArray(), issued = new JSONArray();
        Set<String> inspected = new HashSet<>();
        for (String collection : new String[]{"customers","orders","worklogs","photos","offers","invoices","appointments"}) {
            JSONArray rows = effective.optJSONArray(collection);
            counts.put(collection, rows == null ? 0 : rows.length());
            for (int i = 0; rows != null && i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                boolean document = collection.equals("invoices") && IssuedInvoiceRules.isIssued(row)
                        || collection.equals("offers") && !row.optString("finalized_at").isEmpty();
                if (collection.equals("invoices") && document) issued.put(row.optString("id"));
                if (collection.equals("photos") || document)
                    inspectRow(collection.equals("photos") ? "photo" : "document", row, originals, warnings, inspected);
            }
        }
        JSONObject archive = effective.optJSONObject("owner_archive");
        if (archive != null) for (Iterator<String> keys = archive.keys(); keys.hasNext();) {
            JSONObject entry = archive.optJSONObject(keys.next());
            if (entry == null || entry.optBoolean("purged")) continue;
            JSONObject row = entry.optJSONObject("record");
            if (row == null) continue;
            String kind = row.has("content_sha256") || row.has("filename") ? "photo"
                    : row.has("pdf_sha256") || row.has("pdf_storage_name") || row.has("document_job_id") ? "document" : "";
            if (!kind.isEmpty()) inspectRow(kind, row, originals, warnings, inspected);
        }
        return new JSONObject().put("counts", counts).put("warnings", warnings).put("lastIssuedDocumentIds", issued);
    }
    private static void inspectRow(String kind, JSONObject row, Originals originals, JSONArray warnings, Set<String> inspected) throws Exception {
        String id = row.optString("id"), identity = kind + ":" + id;
        if (!id.isEmpty() && !inspected.add(identity)) return;
        String problem;
        try { problem = originals.problem(kind, row); }
        catch (Exception unavailable) { problem = "ORIGINAL_NOT_VERIFIABLE"; }
        if (problem == null || problem.isEmpty()) return;
        warnings.put(new JSONObject().put("kind", kind).put("record_id", id).put("reason", problem)
                .put("message", kind.equals("photo") ? "Fotooriginal fehlt oder ist nicht verifiziert; vorhandene Dateien bleiben enthalten."
                        : "Dokumentoriginal fehlt oder ist nicht verifiziert; vorhandene Dateien bleiben enthalten."));
    }
    static String message(JSONArray warnings) {
        int count = warnings == null ? 0 : warnings.length();
        return count == 0 ? "" : " " + count + " Hinweise zum Ausgangsbestand. Details stehen in der ZIP-Datei unter data/backup-notes.json.";
    }
    private BackupInventory() {}
}
