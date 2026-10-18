package at.hausgarten.service;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.Locale;

/** Immutable PDF files. Only IDs, SHA256 and size enter snapshots/journals. */
final class BusinessDocumentStore {
    private final File directory;
    BusinessDocumentStore(Context c){directory=new File(c.getFilesDir(),"business-documents");}
    JSONObject createInvoice(JSONObject invoice)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();PdfDocument pdf=new PdfDocument();Writer w=null;
        try {
            w=new Writer(pdf,invoice.getString("invoice_number"));String type=invoice.optString("document_type");
            w.line((type.equals("cancellation")?"Stornorechnung ":type.equals("replacement")?"Korrekturrechnung ":"Rechnung ")+invoice.getString("invoice_number"));
            w.line("Rechnungsdatum: "+invoice.getString("invoice_date"));w.line("Leistung: "+invoice.getString("service_start")+" bis "+invoice.getString("service_end"));
            JSONObject issuer=invoice.getJSONObject("issuer"),recipient=invoice.getJSONObject("recipient");
            for(JSONObject person:new JSONObject[]{issuer,recipient}){w.line("");for(String key:person==issuer?new String[]{"business_name","owner_name","business_street"}:new String[]{"name","company","street"})if(!person.optString(key).isEmpty())w.line(person.getString(key));w.line(person.optString(person==issuer?"business_postal_code":"postal_code")+" "+person.optString(person==issuer?"business_city":"city"));}
            if(!invoice.optString("recipient_vat_id").isEmpty())w.line("Empfänger-UID: "+invoice.getString("recipient_vat_id"));
            w.line(invoice.optString("header_text"));if(!invoice.optString("correction_of").isEmpty())w.line("Originalbezug: "+invoice.optString("correction_original_number",invoice.getString("correction_of"))+" | "+invoice.optString("correction_reason"));
            JSONArray positions=invoice.getJSONArray("positions");for(int i=0;i<positions.length();i++){JSONObject p=positions.getJSONObject(i);w.line("");w.line((i+1)+". "+p.optString("title",p.optString("description")));if(p.has("title"))w.line(p.optString("description"));w.line(p.opt("quantity")+" "+p.optString("unit")+" × "+money(p.getDouble("price"))+" netto | Nachlass "+p.optDouble("discount",0)+" %");w.line("Netto "+money(p.getDouble("net"))+" | USt "+p.optDouble("tax_rate",0)+" % = "+money(p.getDouble("tax")));}
            w.line("");w.line("Netto: "+money(invoice.getDouble("net_total")));w.line("Umsatzsteuer: "+money(invoice.getDouble("tax_total")));w.line("Gesamtbetrag: "+money(invoice.getDouble("gross_total")));
            if(invoice.getString("tax_mode").equals("small_business"))w.line(issuer.getString("tax_exemption_text"));else w.line("Aussteller-UID: "+issuer.getString("business_vat_id"));
            w.line("Zahlbar bis: "+invoice.getString("due_date"));for(String key:new String[]{"default_payment_terms","business_iban","business_bank","business_bic","business_phone","business_email","business_legal_info"})if(!issuer.optString(key).isEmpty())w.line(issuer.getString(key));
            w.line(invoice.optString("notes"));w.line(invoice.optString("footer_text"));w.finish();pdf.writeTo(bytes);
        }finally{if(w!=null)w.finish();pdf.close();}
        byte[] raw=bytes.toByteArray();String sha=PhotoAttachmentStore.sha256(raw);JSONObject meta=new JSONObject().put("document_id","invoice:"+invoice.getString("id")).put("pdf_storage_name",sha+".pdf").put("pdf_sha256",sha).put("pdf_byte_size",raw.length);store(raw,meta);return meta;
    }
    JSONObject create(JSONObject offer)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        PdfDocument pdf=new PdfDocument();Writer w=null;
        try{
            w=new Writer(pdf,offer.optString("offer_number"));JSONObject issuer=offer.getJSONObject("issuer"),recipient=offer.getJSONObject("recipient");
            w.line(OfferRules.label(offer.getString("document_type")));w.line(offer.getString("offer_number"));
            w.line("Datum: "+offer.getString("issue_date")+" | Gültig bis: "+offer.getString("valid_until"));
            for(JSONObject p:new JSONObject[]{issuer,recipient}){w.line("");for(String k:p==issuer?new String[]{"business_name","owner_name","business_street"}:new String[]{"name","company","street"})if(!p.optString(k).isEmpty())w.line(p.getString(k));w.line(p.optString(p==issuer?"business_postal_code":"postal_code")+" "+p.optString(p==issuer?"business_city":"city"));}
            w.line("");w.line(offer.getString("title"));w.line(offer.optString("header_text"));w.line("Ausführung: "+offer.getString("execution_window"));if(!offer.optString("location").isEmpty())w.line("Einsatzort: "+offer.getString("location"));
            JSONArray lines=offer.getJSONArray("positions");for(int i=0;i<lines.length();i++){JSONObject p=lines.getJSONObject(i);w.line("");w.line((i+1)+". "+p.optString("title"));w.line(p.optString("description"));w.line(p.opt("quantity")+" "+p.optString("unit")+" × "+money(p.getDouble("price"))+" netto = "+money(p.getDouble("net"))+" netto | USt "+p.optDouble("tax_rate",0)+" %");}
            w.line("");w.line("Netto: "+money(offer.getDouble("net_total")));w.line("Umsatzsteuer: "+money(offer.getDouble("tax_total")));w.line("Gesamt inklusive Umsatzsteuer: "+money(offer.getDouble("gross_total")));
            if(offer.optString("tax_mode").equals("small_business"))w.line(issuer.optString("tax_exemption_text"));else w.line("UID: "+issuer.optString("business_vat_id"));
            if(offer.getString("document_type").equals("time_material"))w.line("Regieangebot: Abrechnung nach tatsächlich geleistetem, vereinbartem Aufwand zu den angeführten Einheitspreisen. Die Mengen sind Planwerte.");
            if(offer.getString("document_type").equals("estimate_nonbinding"))w.line("Unverbindlicher Kostenvoranschlag ohne Gewährleistung für seine Richtigkeit. Eine unvermeidliche beträchtliche Überschreitung wird unverzüglich angezeigt; die gesetzlichen Rechte bleiben erhalten.");
            if(offer.getString("document_type").equals("estimate_binding"))w.line("Kostenvoranschlag mit Gewährleistung für seine Richtigkeit.");
            for(String[] pair:new String[][]{{"payment_terms","Zahlungsbedingungen"},{"assumptions","Annahmen"},{"exclusions","Nicht enthalten"},{"notes","Hinweise"},{"consumer_information","Verbraucherinformationen"},{"footer_text",""}})if(!offer.optString(pair[0]).isEmpty()){w.line("");w.line(pair[1]);w.line(offer.getString(pair[0]));}
            w.finish();pdf.writeTo(bytes);
        }finally{if(w!=null)w.finish();pdf.close();}
        byte[] raw=bytes.toByteArray();if(raw.length<5||raw.length>20*1024*1024)throw new IOException("Angebots-PDF überschreitet die sichere Dateigrenze.");String hash=PhotoAttachmentStore.sha256(raw);JSONObject meta=new JSONObject().put("document_id","offer:"+offer.getString("id")).put("pdf_storage_name",hash+".pdf").put("pdf_sha256",hash).put("pdf_byte_size",raw.length);store(raw,meta);return meta;
    }
    void store(byte[] raw,JSONObject meta)throws Exception{
        verify(raw,meta);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Dokumentspeicher fehlt.");File f=file(meta);if(f.exists()){read(meta);return;}AtomicFile atomic=new AtomicFile(f);FileOutputStream out=null;try{out=atomic.startWrite();out.write(raw);atomic.finishWrite(out);}catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    byte[] read(JSONObject meta)throws Exception{File f=file(meta);if(f.length()>20*1024*1024)throw new IOException("Dokument ist zu groß.");byte[] raw=java.nio.file.Files.readAllBytes(f.toPath());verify(raw,meta);return raw;}
    private File file(JSONObject meta)throws Exception{String name=meta.getString("pdf_storage_name");if(!name.matches("[0-9a-f]{64}\\.pdf"))throw new IllegalArgumentException("Ungültige Dokumentreferenz.");return new File(directory,name);}
    private static void verify(byte[] raw,JSONObject meta)throws Exception{if(raw.length<5||raw.length>20*1024*1024||!meta.getString("pdf_storage_name").equals(meta.getString("pdf_sha256")+".pdf")||!new String(raw,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")||raw.length!=meta.getLong("pdf_byte_size")||!PhotoAttachmentStore.sha256(raw).equals(meta.getString("pdf_sha256")))throw new IOException("Dokument-Prüfsumme stimmt nicht überein.");}
    private static String money(double n){return String.format(Locale.GERMAN,"%.2f EUR",n);}
    private static final class Writer {
        final PdfDocument pdf;final String number;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);PdfDocument.Page page;float y;int count;
        Writer(PdfDocument pdf,String number){this.pdf=pdf;this.number=number;paint.setTextSize(11);newPage();}
        void newPage(){if(page!=null)pdf.finishPage(page);if(++count>100)throw new IllegalArgumentException("Angebot überschreitet 100 Seiten.");page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,count).create());y=50;page.getCanvas().drawText(number+" · "+count,45,y,paint);y+=30;}
        void line(String text){for(String paragraph:String.valueOf(text).split("\n",-1)){String rest=paragraph;do{if(y>790)newPage();int n=rest.isEmpty()?0:paint.breakText(rest,true,505,null);if(n==0&&!rest.isEmpty())n=1;if(n<rest.length()){int space=rest.lastIndexOf(' ',n);if(space>0)n=space;}page.getCanvas().drawText(rest.substring(0,n),45,y,paint);y+=16;rest=rest.substring(n).replaceFirst("^ +", "");}while(!rest.isEmpty());}}
        void finish(){if(page!=null){pdf.finishPage(page);page=null;}}
    }
}
