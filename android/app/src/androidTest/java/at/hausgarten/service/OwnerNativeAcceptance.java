package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.util.*;

/** Prepared device acceptance only. Never invokes a production store/provider or tunnel. */
final class OwnerNativeAcceptance {
    static final class Fixture implements AutoCloseable {
        final PixelMcpInstrumentation.Isolated context;
        final MainActivity.LocalStore store;
        final PixelMcpReadTools tools;
        Fixture(Context base)throws Exception{
            context=new PixelMcpInstrumentation.Isolated(base);
            JSONObject snapshot=new JSONObject("{customers:[{id:'c',name:'QA Eigentümer',active:1,billing_mode:'direct',updated_at:'2026-10-18T08:00:00Z'}],orders:[],worklogs:[{id:'w',customer_id:'c',billing_customer_id:'c',work_date:'2026-10-18',activity:'QA Pflege',hours:2,break_minutes:0,kilometers:0,billed:0,customer_visible:1,notes:'Original',updated_at:'2026-10-18T08:00:00Z'}],invoices:[],photos:[],capabilities:{entity_compare_and_save:true,worklog_compare_and_save:true,customer_custom_fields_compare_and_save:true,invoice_compare_and_save:true,photo_compare_and_save:true,owner_journal_v2:true}}");
            JSONObject config=new JSONObject();for(String key:PixelMcpOwnerTools.BUSINESS_KEYS)config.put(key,"");snapshot.put("business_settings",config);
            context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",snapshot.toString()).commit();store=new MainActivity.LocalStore(context);tools=new PixelMcpReadTools(store::getState,store,new PixelOwnerAccess(context,store));
        }
        JSONObject call(String n,JSONObject a)throws Exception{return tools.call(n,a).getJSONObject("data");}
        public void close(){store.close();}
    }
    static JSONObject request(String id)throws Exception{return new JSONObject().put("request_id",id);}
    static void run(PixelMcpInstrumentation test)throws Exception{
        test.probe("owner native settings/catalog/capability reads and settings replay",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject cap=f.call("get_capabilities",new JSONObject());test.yes(cap.getInt("protocol_version")==2);
            JSONObject settings=f.call("get_settings",new JSONObject());JSONObject args=request("owner-settings").put("expected_record_hash",settings.getString("record_hash")).put("updates",new JSONObject().put("default_activity","QA Garten"));f.call("update_settings",args);test.yes(f.call("update_settings",args).getBoolean("replayed"));
            test.yes(f.call("search_catalog",new JSONObject().put("query","Thuja")).getInt("total")>0);test.yes(f.call("get_catalog_entry",new JSONObject().put("entry_id","thuja-occidentalis")).getString("id").equals("thuja-occidentalis"));
            test.yes(f.call("get_sync_conflicts",new JSONObject()).getInt("total")==0);test.yes(f.store.pendingActions().length()==0);
        }});
        test.probe("owner native file blocks/photo metadata and exact original read",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            byte[] bytes=Base64.getDecoder().decode(PixelMcpInstrumentation.VALID_GIF.split(",",2)[1]);String hash=McpFileTransfer.sha(bytes);
            JSONObject upload=f.call("begin_file_upload",request("owner-upload").put("byte_size",bytes.length).put("sha256",hash).put("mime_type","image/gif"));String id=upload.getString("upload_id");
            JSONObject block=request("owner-file-block").put("upload_id",id).put("offset",0).put("chunk_base64",Base64.getEncoder().encodeToString(bytes)).put("chunk_sha256",hash);f.call("write_file_chunk",block);test.yes(f.call("write_file_chunk",block).getBoolean("replayed"));
            JSONObject create=request("owner-photo-create").put("upload_id",id).put("customer_id","c").put("caption","QA Original");String photo=f.call("create_photo",create).getString("photo_id");test.yes(f.call("create_photo",create).getString("photo_id").equals(photo));
            JSONObject meta=f.call("get_file_metadata",new JSONObject().put("resource_type","photo").put("resource_id",photo));test.yes(meta.getString("sha256").equals(hash));
            JSONObject data=f.call("read_file_chunk",new JSONObject().put("resource_type","photo").put("resource_id",photo).put("expected_sha256",hash).put("offset",0));test.yes(Arrays.equals(bytes,Base64.getDecoder().decode(data.getString("chunk_base64"))));
            JSONObject record=f.call("get_photo",new JSONObject().put("photo_id",photo));f.call("update_photo",request("owner-photo-edit").put("photo_id",photo).put("expected_record_hash",record.getString("record_hash")).put("updates",new JSONObject().put("caption","QA Nachtrag")));
            test.yes(f.store.pendingActions().length()==2);test.yes(f.store.photoAttachmentStore().read(f.store.photoAttachment(photo)).length==bytes.length);
        }});
        test.probe("owner native archive/restore retains queue and original ID",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            String hash=f.tools.call("get_customer",new JSONObject().put("customer_id","c")).getString("entity_hash");f.call("archive_entity",request("owner-archive").put("entity","customer").put("entity_id","c").put("expected_record_hash",hash));
            JSONObject saved=f.call("list_archive",new JSONObject()).getJSONArray("archive").getJSONObject(0);f.call("restore_entity",request("owner-restore").put("entity","customer").put("entity_id","c").put("expected_record_hash",saved.getString("record_hash")));
            test.yes(f.call("get_customer",new JSONObject().put("customer_id","c")).getInt("active")==1);test.yes(f.store.pendingActions().length()==2);
        }});
        test.probe("owner native personal marks are local and cannot bypass migration",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            test.rejected(()->f.call("set_personal_paid",request("owner-paid-before").put("work_record_id","w").put("paid",true)));f.store.putOwnerValue("personal_paid_migrated","1");f.call("set_personal_paid",request("owner-paid").put("work_record_id","w").put("paid",true));
            test.yes(f.call("get_settings",new JSONObject()).getJSONArray("personal_paid").getString(0).equals("w"));test.yes(f.store.pendingActions().length()==0);test.yes(f.call("get_work_record",new JSONObject().put("work_record_id","w")).getInt("billed")==0);
        }});
        test.probe("owner native backup inspection/restore refuses changed versions",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject backup=f.call("create_backup",request("owner-backup"));String id=backup.getString("backup_id");test.yes(f.call("list_backups",new JSONObject()).getJSONArray("backups").length()==1);JSONObject info=f.call("inspect_backup",new JSONObject().put("backup_id",id));
            test.yes(f.call("restore_backup",request("owner-restore-identical").put("backup_id",id).put("expected_state_hash",info.getString("state_hash"))).getInt("overwritten_records")==0);
            JSONObject snapshot=f.store.getState().getJSONObject("master");snapshot.getJSONArray("worklogs").getJSONObject(0).put("notes","Parallel");f.store.commitSnapshot(snapshot,new HashSet<>());JSONObject changed=f.call("inspect_backup",new JSONObject().put("backup_id",id));
            test.rejected(()->f.call("restore_backup",request("owner-restore-stale").put("backup_id",id).put("expected_state_hash",changed.getString("state_hash"))));test.yes(f.call("get_work_record",new JSONObject().put("work_record_id","w")).getString("notes").equals("Parallel"));
        }});
        test.probe("owner native conflict decision retains original action until Drive review",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject original=new JSONObject("{id:'c',name:'QA andere Variante',_mcp_create:true}");f.store.saveAction("customer","cus_",original);JSONObject snapshot=f.store.getState().getJSONObject("master");DriveJournalRules.apply(snapshot,f.store.pendingActions());f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject conflict=f.call("get_sync_conflicts",new JSONObject()).getJSONArray("conflicts").getJSONObject(0);f.call("resolve_sync_conflict",request("owner-resolution").put("client_id",conflict.getString("client_id")).put("expected_action_hash",conflict.getString("action_hash")).put("expected_current_hash",conflict.getString("current_hash")).put("resolution","keep_current"));
            test.yes(f.store.pendingActions().length()==2);test.yes(f.call("get_customer",new JSONObject().put("customer_id","c")).getString("name").equals("QA Eigentümer"));
        }});
        test.probe("owner native large metadata avoids CursorWindow-sized rows",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            StringBuilder builder=new StringBuilder();for(int i=0;i<1_100_000;i++)builder.append("Ä🌱");String text=builder.toString();f.store.putOwnerValue("large-fixture",text);test.yes(f.store.ownerValue("large-fixture","").equals(text));
        }});
        test.probe("owner native business configuration uses journal and excludes credentials",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject old=f.call("get_business_settings",new JSONObject());f.call("update_business_settings",request("owner-business-settings").put("expected_record_hash",old.getString("record_hash")).put("updates",new JSONObject().put("owner_name","QA Aussteller").put("invoice_issuer_confirmed",true)));
            test.yes(f.call("get_business_settings",new JSONObject()).getJSONObject("settings").getString("owner_name").equals("QA Aussteller"));test.yes(f.store.pendingActions().length()==1);
        }});
        test.probe("owner native invoice draft updates and finalization remain distinct",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject create=request("owner-draft").put("customer_id","c").put("invoice_date","2026-10-18").put("positions",new JSONArray().put(new JSONObject().put("title","QA Pflege").put("quantity",2).put("price",45)));
            String id=f.call("create_invoice_draft",create).getString("entity_id");JSONObject snapshot=f.store.getState().getJSONObject("master");DriveJournalRules.apply(snapshot,f.store.pendingActions());f.store.commitSnapshot(snapshot,DriveJournalRules.ids(snapshot.getJSONArray("drive_applied_ids")));
            String hash=f.tools.call("get_invoice",new JSONObject().put("invoice_id",id)).getString("record_hash");f.call("update_invoice_draft",request("owner-draft-update").put("invoice_id",id).put("expected_record_hash",hash).put("updates",new JSONObject().put("notes","QA Nachtrag")));
            String revised=f.tools.call("get_invoice",new JSONObject().put("invoice_id",id)).getString("record_hash");JSONObject issue=f.call("request_invoice_issue",request("owner-issue").put("invoice_id",id).put("expected_record_hash",revised).put("confirmed",true));test.yes(issue.getString("finalization").equals("central_numbering_service_pending"));test.yes(f.call("get_invoice",new JSONObject().put("invoice_id",id)).getString("status").equals("Entwurf"));
            test.yes(f.call("request_sync",request("owner-sync")).getString("state").equals("requested"));
        }});
        test.probe("owner native correction/payment/share preserve issued original",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject snapshot=f.store.getState().getJSONObject("master"),invoice=new JSONObject("{id:'i',customer_id:'c',invoice_number:'QA-1',invoice_date:'2026-10-18',status:'Gestellt',issued_at:'2026-10-18T08:00:00Z',updated_at:'2026-10-18T08:00:00Z',amount:90,positions:[{title:'QA Pflege',quantity:2,price:45}],worklog_ids:[]}");invoice.put("pdf_base64",Base64.getEncoder().encodeToString("%PDF-1.4\nSynthetic header only".getBytes(java.nio.charset.StandardCharsets.UTF_8)));snapshot.getJSONArray("invoices").put(invoice);f.store.commitSnapshot(snapshot,new HashSet<>());
            JSONObject read=f.tools.call("get_invoice",new JSONObject().put("invoice_id","i"));JSONObject correction=f.call("create_invoice_correction",request("owner-correction").put("invoice_id","i").put("expected_record_hash",read.getString("record_hash")).put("correction_type","cancellation").put("reason","QA Korrektur"));test.yes(correction.getBoolean("requires_issue_confirmation"));
            JSONObject paid=f.call("update_invoice_payment",request("owner-invoice-paid").put("invoice_id","i").put("expected_record_hash",read.getString("entity_hash")).put("paid",true));test.yes(paid.getString("sync_state").equals("sync_pending"));
            JSONObject share=f.call("prepare_invoice_share",request("owner-share").put("invoice_id","i").put("channel","share"));test.yes(share.getString("state").equals("device_interaction_required")&&!share.getBoolean("sent"));test.yes(f.call("get_invoice",new JSONObject().put("invoice_id","i")).getString("issued_at").equals(invoice.getString("issued_at")));
        }});
        test.probe("owner native manual billing flag and explicit assignment use shared work saver",()->{try(Fixture f=new Fixture(test.getTargetContext())){
            JSONObject work=f.tools.call("get_work_record",new JSONObject().put("work_record_id","w"));f.call("set_work_record_billed",request("owner-manual-billing").put("work_record_id","w").put("expected_record_hash",work.getString("record_hash")).put("billed",true).put("reason","QA manuelle Markierung"));test.yes(f.call("get_work_record",new JSONObject().put("work_record_id","w")).getInt("billed")==1);
            JSONObject current=f.tools.call("get_work_record",new JSONObject().put("work_record_id","w"));f.call("reassign_work_record",request("owner-explicit-assignment").put("work_record_id","w").put("expected_record_hash",current.getString("record_hash")).put("customer_id","c").put("billing_customer_id","c").put("reason","QA ausdrückliche Zuordnung"));test.yes(f.store.pendingActions().length()==2);
        }});
    }
}
