package at.hausgarten.service;

import org.json.*;

/** Native adapter security checks; browser execution is exercised separately. */
final class BackupBrowserUiNativeAcceptance {
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("backup browser native adapter excludes secret and file controls while retaining exact draft and personal storage",()->{
            JSONArray fields=new JSONArray().put(new JSONObject().put("id","qa_note").put("name","note").put("type","textarea").put("value","QA original draft"))
                .put(new JSONObject().put("id","qa_password").put("type","password").put("value","test_forbidden_password"))
                .put(new JSONObject().put("name","qa_file").put("type","file").put("value","test_forbidden_file"))
                .put(new JSONObject().put("name","qa_accessToken").put("type","text").put("value","test_forbidden_token"));
            JSONObject mirror=new JSONObject().put("formatVersion",1).put("pages",new JSONObject().put("qa_page",new JSONObject().put("forms",new JSONArray().put(new JSONObject().put("fields",fields)))));
            JSONObject source=new JSONObject().put("formatVersion",1).put("qa_recovery_code","test_forbidden_top").put("storage",new JSONObject().put("qa_personal_paid","[\"qa_work\"]").put("qa_accessToken","test_forbidden_storage").put("hausgarten.fullbackup.formdrafts.v1",mirror.toString())).put("drafts",new JSONObject().put("forms",new JSONArray().put(new JSONObject().put("fields",fields))));String original=source.toString();
            JSONObject safe=MainActivity.sanitizeBackupCheckpoint(source);test.yes(source.toString().equals(original));test.yes(safe.getJSONObject("storage").getString("qa_personal_paid").equals("[\"qa_work\"]"));test.yes(!safe.toString().contains("test_forbidden"));test.yes(safe.getJSONObject("drafts").getJSONArray("forms").getJSONObject(0).getJSONArray("fields").length()==1);test.yes(safe.toString().contains("QA original draft"));
        });
        test.probe("backup browser native adapter rejects unknown format and nonstring storage without mutating input",()->{
            for(JSONObject value:new JSONObject[]{new JSONObject().put("formatVersion",2).put("storage",new JSONObject()),new JSONObject().put("formatVersion",1).put("storage",new JSONObject().put("qa_number",1))}){String before=value.toString();try{MainActivity.sanitizeBackupCheckpoint(value);throw new AssertionError("Expected browser contract refusal");}catch(java.io.IOException expected){}test.yes(value.toString().equals(before));}
        });
        test.probe("local invoice share ignores client PDF and recipient and refuses missing or changed original",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            byte[] raw="%PDF-1.4\nQA immutable source\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);String sha=PhotoAttachmentStore.sha256(raw);JSONObject invoice=new JSONObject().put("id","qa_verified_share").put("customer_id","qa_c").put("issued_at","2027-05-18T12:00:00Z").put("invoice_number","QA-VERIFY").put("recipient",new JSONObject().put("email","qa_frozen@example.invalid")).put("pdf_storage_name",sha+".pdf").put("pdf_sha256",sha).put("pdf_byte_size",raw.length);new BusinessDocumentStore(f.context).store(raw,invoice);
            JSONObject base=f.store.getState().getJSONObject("master");base.getJSONArray("invoices").put(invoice);f.store.commitSnapshot(base,new java.util.HashSet<>());JSONObject forged=new JSONObject().put("id","qa_verified_share").put("pdf_base64","test_invalid_client_pdf").put("recipient",new JSONObject().put("email","qa_wrong@example.invalid"));MainActivity.VerifiedInvoiceShare verified=MainActivity.verifiedLocalInvoiceShare(f.context,f.store,forged);test.yes(java.util.Arrays.equals(raw,verified.bytes));test.yes(verified.invoice.getJSONObject("recipient").getString("email").equals("qa_frozen@example.invalid"));test.yes(forged.getString("pdf_base64").equals("test_invalid_client_pdf"));
            java.io.File file=new java.io.File(f.context.getFilesDir(),"business-documents/"+sha+".pdf");try(java.io.FileOutputStream output=new java.io.FileOutputStream(file)){output.write("%PDF-1.4\nQA changed bytes\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));}try{MainActivity.verifiedLocalInvoiceShare(f.context,f.store,forged);throw new AssertionError("Expected immutable PDF rejection");}catch(java.io.IOException expected){}test.yes(file.delete());try{MainActivity.verifiedLocalInvoiceShare(f.context,f.store,forged);throw new AssertionError("Expected missing PDF rejection");}catch(java.io.IOException expected){}test.yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("invoices"),"qa_verified_share").getJSONObject("recipient").getString("email").equals("qa_frozen@example.invalid"));
        }});
    }
}
