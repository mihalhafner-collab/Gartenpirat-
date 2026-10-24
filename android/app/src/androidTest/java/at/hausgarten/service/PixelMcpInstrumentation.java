package at.hausgarten.service;

import android.app.Instrumentation;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import org.json.*;
import java.io.File;
import java.util.*;

/** Separate test APK. Every database/preference access is namespaced; no production sync. */
public final class PixelMcpInstrumentation extends Instrumentation {
    static final String VALID_GIF="data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
    static String repeat(String value,int count){StringBuilder out=new StringBuilder();for(int i=0;i<count;i++)out.append(value);return out.toString();}
    int passed;
    int acceptanceFailed;
    boolean liveTunnelProbe;
    String installRuntimeKeyBase64;
    String installTunnelId="",installOrganization="";
    boolean startTunnelService;
    boolean restoreEnabledTunnel;
    boolean businessCapture;
    String releaseMode="";
    interface Task { void run() throws Exception; }
    static final class FakeClock implements WorkSessionStore.Clock {
        long wall=1_760_000_000_000L,elapsed=100_000L;String boot="boot-a";
        public long wallMillis(){return wall;} public long elapsedMillis(){return elapsed;} public String bootMarker(){return boot;}
        void advance(long millis){wall+=millis;elapsed+=millis;}
    }
    void check(String title,Task task)throws Exception{task.run();passed++;Bundle b=new Bundle();b.putString("stream","PASS "+title+"\n");sendStatus(0,b);}
    void yes(boolean b){if(!b)throw new AssertionError("Assertion failed");}
    void rejected(Task t)throws Exception{try{t.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Expected rejection");}
    void probe(String title,Task task){try{check(title,task);}catch(Throwable error){acceptanceFailed++;Bundle b=new Bundle();b.putString("stream","FAIL "+title+" ["+error.getClass().getSimpleName()+"] "+(error.getStackTrace().length>0?(error.getStackTrace()[0].getMethodName().equals("yes")&&error.getStackTrace().length>1?error.getStackTrace()[1]:error.getStackTrace()[0]).toString():"no frame")+"\n");sendStatus(0,b);}}
    PixelLocalStore acceptanceStore()throws Exception{
        Isolated isolated=new Isolated(getTargetContext());JSONObject snapshot=fixture();
        snapshot.getJSONObject("capabilities").put("entity_compare_and_save",true).put("customer_custom_fields_compare_and_save",true);
        snapshot.getJSONArray("customers").getJSONObject(0).put("updated_at","2026-10-12T08:00:00Z").put("created_at","2020-01-01T00:00:00Z").put("sevdesk_contact_id","qa_existing_contact").put("custom_fields","{\"Torcode\":\"1234\",\"Hinweis\":\"Behalten\"}");
        snapshot.getJSONArray("customers").put(SyntheticBusinessData.object().put("id","qa_c2").put("name","QA Other").put("active",0));
        snapshot.getJSONArray("worklogs").put(SyntheticBusinessData.object().put("id","qa_w1").put("customer_id","qa_c1").put("order_id","qa_o1").put("activity","QA Bestand").put("hours",1).put("work_date","2026-10-12"));
        snapshot.getJSONArray("invoices").put(SyntheticBusinessData.object().put("id","qa_i1").put("customer_id","qa_c1").put("status","Gestellt").put("issued_at","2026-10-12T08:00:00Z"));
        yes(isolated.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",snapshot.toString()).commit());
        return new PixelLocalStore(isolated);
    }
    void acceptance()throws Exception{
        billingAcceptance();
        probe("P3 invoice detail bridge exposes existing invoice hashes and protected payment",()->{
            Isolated ctx=new Isolated(getTargetContext());yes(ctx.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",fixture().put("invoices",new JSONArray().put(SyntheticBusinessData.object().put("id","qa_p3_invoice").put("customer_id","qa_c1").put("invoice_number","QA-P3-1").put("status","Gestellt").put("issued_at","2026-10-20T08:00:00Z"))).toString()).commit());
            try(PixelLocalStore local=new PixelLocalStore(ctx)){BusinessDataBridge bridge=new BusinessDataBridge(ctx,local,new PendingWorkPhotoStore(ctx),()->{});JSONObject invoice=new JSONObject(bridge.ownerCall("get_invoice","{\"invoice_id\":\"qa_p3_invoice\"}"));yes(invoice.getBoolean("ok"));yes(invoice.getString("entity_hash").matches("[0-9a-f]{64}"));
                JSONObject args=SyntheticBusinessData.object().put("request_id","qa_p3_payment").put("invoice_id","qa_p3_invoice").put("expected_record_hash",invoice.getString("entity_hash")).put("paid",true);yes(new JSONObject(bridge.ownerCall("update_invoice_payment",args.toString())).getBoolean("ok"));yes(new JSONObject(bridge.ownerCall("update_invoice_payment",args.toString())).getBoolean("ok"));yes(local.pendingActions().length()==1);yes(PixelMcpReadTools.effective(local.getState()).getJSONArray("invoices").getJSONObject(0).getString("status").equals("Bezahlt"));yes(local.ownerValue("personal_paid","[]").equals("[]"));
            }
        });
        probe("P3 Drive automatic pause retains manual request and connection without business writes",()->{
            Isolated ctx=new Isolated(getTargetContext());android.content.SharedPreferences prefs=ctx.getSharedPreferences("haus_garten",0);
            yes(DriveSyncCoordinator.automaticEnabled(ctx));yes(prefs.edit().putString("drive_transport","rest").putString("drive_account","qa_owner@example.invalid").putString("drive_folder_id","qa_private_folder_123456789").commit());
            yes(DriveSyncCoordinator.automaticAllowed(ctx));yes(prefs.edit().putBoolean("drive_automatic_enabled",false).commit());yes(!DriveSyncCoordinator.automaticAllowed(ctx));
            yes(DriveSyncCoordinator.prepareExplicit(ctx,"qa_p3_manual_sync"));yes(DriveSyncCoordinator.automaticAllowed(ctx));yes(!DriveSyncCoordinator.automaticEnabled(ctx));yes(DriveAccount.configured(ctx));yes(!prefs.contains("drive_snapshot"));
        });
        probe("MCP customer all fields persist offline with stable replay ID",()->{PixelLocalStore local=acceptanceStore();try{
            JSONObject args=SyntheticBusinessData.object().put("request_id","qa-customer-all-fields").put("name","QA MCP Neue Kundin").put("company","QA Gartenfirma").put("first_name","Anna").put("last_name","Muster").put("street","Gartenweg 4").put("postal_code","9560").put("city","QA Beispielort").put("country","Österreich").put("phone","+43664123456").put("email","qa_603ba7ede2fe@example.invalid").put("notes","Tor links").put("garden_area",120.5).put("sevdesk_customer_number","QA42").put("active",true).put("custom_fields",SyntheticBusinessData.object().put("Torcode","4321").put("Breite",2.5));
            JSONObject result=local.execute("create_customer",args).getJSONObject("data");String id=result.getString("entity_id");
            local.close();JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("customers"),id);
            yes(row.getString("email").equals("qa_603ba7ede2fe@example.invalid")&&row.getString("street").equals("Gartenweg 4")&&row.getJSONObject("custom_fields").getDouble("Breite")==2.5);
            yes(local.execute("create_customer",args).getJSONObject("data").getBoolean("replayed")&&local.pendingActions().length()==1);
        }finally{local.close();}});
        probe("MCP customer update preserves identity relations and custom fields",()->{PixelLocalStore local=acceptanceStore();try{
            JSONObject old=PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("customers"),"qa_c1");
            JSONObject args=SyntheticBusinessData.object().put("request_id","qa-customer-update").put("customer_id","qa_c1").put("expected_record_hash",PixelMcpWriteTools.hash(PixelMcpEntityWriteTools.recordForHash("customer",old))).put("updates",SyntheticBusinessData.object().put("phone","+436649999").put("custom_fields",SyntheticBusinessData.object().put("Torcode","4321")));
            local.execute("update_customer",args);JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("customers"),"qa_c1");
            yes(row.getString("created_at").equals("2020-01-01T00:00:00Z")&&row.getString("sevdesk_contact_id").equals("qa_existing_contact")&&row.getJSONObject("custom_fields").getString("Hinweis").equals("Behalten"));
            yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("worklogs"),"qa_w1").getString("customer_id").equals("qa_c1"));
        }finally{local.close();}});
        probe("MCP customer duplicates never enter the native queue",()->{PixelLocalStore local=acceptanceStore();try{
            rejected(()->local.execute("create_customer",SyntheticBusinessData.object().put("request_id","qa-duplicate").put("name"," qa other ")));
            yes(local.pendingActions().length()==0);
        }finally{local.close();}});
        probe("MCP customer stale revision fails without any stored change",()->{PixelLocalStore local=acceptanceStore();try{
            rejected(()->local.execute("update_customer",SyntheticBusinessData.object().put("request_id","qa-stale-customer").put("customer_id","qa_c1").put("expected_record_hash","stale").put("updates",SyntheticBusinessData.object().put("phone","123"))));
            yes(local.pendingActions().length()==0);
        }finally{local.close();}});
        probe("MCP customer rejects protected IDs and request reuse with changed values",()->{PixelLocalStore local=acceptanceStore();try{
            rejected(()->local.execute("create_customer",SyntheticBusinessData.object().put("request_id","qa-protected").put("name","QA Neu").put("id","qa_c1")));
            JSONObject args=SyntheticBusinessData.object().put("request_id","qa-idempotency-customer").put("name","QA Idempotent");local.execute("create_customer",args);
            rejected(()->local.execute("create_customer",SyntheticBusinessData.object(args.toString()).put("name","QA Andere Eingabe")));yes(local.pendingActions().length()==1);
        }finally{local.close();}});
        probe("MCP customer distinguishes local save from Drive confirmation",()->{PixelLocalStore local=acceptanceStore();try{
            JSONObject result=local.execute("create_customer",SyntheticBusinessData.object().put("request_id","qa-status-customer").put("name","QA Status")).getJSONObject("data");
            yes(result.getString("local_state").equals("local_saved")&&result.getString("sync_state").equals("sync_pending"));
            yes(!result.getJSONObject("sync").getBoolean("confirmed_by_app"));
        }finally{local.close();}});
        probe("offline customer order worklog chain remains linked after reopen",()->{PixelLocalStore local=acceptanceStore();try{String customer=local.saveCustomer(SyntheticBusinessData.object().put("name","QA Offline New"));String order=local.saveOrder(SyntheticBusinessData.object().put("customer_id",customer).put("title","QA New order"));String work=local.saveWorklog(SyntheticBusinessData.object().put("customer_id",customer).put("order_id",order).put("activity","QA Offline chain").put("hours",2).put("work_date","2026-10-12"));JSONObject effective=PixelMcpReadTools.effective(local.getState());JSONObject row=PixelMcpWriteTools.find(effective.getJSONArray("worklogs"),work);yes(row.getString("customer_id").equals(customer)&&row.getString("order_id").equals(order));local.close();yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("worklogs"),work).getString("id").equals(work));}finally{local.close();}});
        probe("partial offline customer edit retains original name",()->{PixelLocalStore local=acceptanceStore();try{local.saveCustomer(SyntheticBusinessData.object().put("id","qa_c1").put("name","QA Kunde").put("notes","QA Nachtrag"));yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("customers"),"qa_c1").getString("notes").equals("QA Nachtrag"));}finally{local.close();}});
        probe("photo rejects nonexistent customer before local queue write",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_unknown").put("data","cWE=")));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("photo rejects mismatched customer order before local queue write",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c2").put("order_id","qa_o1").put("data","cWE=")));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("manual order reassignment cannot orphan existing worklogs",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.saveOrder(SyntheticBusinessData.object().put("id","qa_o1").put("customer_id","qa_c2").put("title","QA changed")));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("manual customer duplicate includes inactive master record",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.saveCustomer(SyntheticBusinessData.object().put("name","QA Other")));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("issued invoice delete is refused before hiding it locally",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.removeEntity("invoice","qa_i1"));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("manual delete undo appends restore and preserves the archive action",()->{PixelLocalStore local=acceptanceStore();try{local.removeEntity("worklog","qa_w1");yes(PixelMcpReadTools.effective(local.getState()).getJSONArray("worklogs").length()==0);yes(SyntheticBusinessData.object(local.undoRemoveEntity("worklog","qa_w1")).getBoolean("ok"));yes(local.pendingActions().length()==2);yes(PixelMcpReadTools.effective(local.getState()).getJSONArray("worklogs").length()==1);}finally{local.close();}});
        probe("valid offline photo retains relations and keeps state and Drive journal binary-free",()->{PixelLocalStore local=acceptanceStore();try{String id=local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("order_id","qa_o1").put("worklog_id","qa_w1").put("original_name","QA.gif").put("role","before").put("data",VALID_GIF));JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("photos"),id);yes(row.getString("customer_id").equals("qa_c1")&&row.getString("worklog_id").equals("qa_w1")&&row.getString("role").equals("before")&&!row.has("data"));JSONObject statePayload=local.pendingActions().getJSONObject(0).getJSONObject("payload"),transfer=local.pendingActionsForDrive().getJSONObject(0).getJSONObject("payload");yes(!statePayload.has("data")&&!transfer.has("data")&&transfer.getString("content_sha256").matches("[0-9a-f]{64}")&&transfer.getLong("byte_size")>0&&transfer.getString("mime_type").equals("image/gif"));yes(local.loadPhotoData(id).equals(VALID_GIF));}finally{local.close();}});
        probe("pending work photo survives native store reopen and clears explicitly",()->{Isolated context=new Isolated(getTargetContext());PendingWorkPhotoStore first=new PendingWorkPhotoStore(context);JSONObject payload=SyntheticBusinessData.object().put("customer_id","qa_c1").put("worklog_id","qa_w1").put("original_name","QA.jpg").put("mime_type","image/jpeg").put("data","data:image/jpeg;base64,/9j/");JSONObject staged=SyntheticBusinessData.object(first.stage(payload.toString()));yes(staged.getString("id").startsWith("pho_mobile_")&&!staged.getString("id").isEmpty());PendingWorkPhotoStore reopened=new PendingWorkPhotoStore(context);JSONObject restored=SyntheticBusinessData.object(reopened.read());yes(restored.getString("id").equals(staged.getString("id"))&&restored.getString("worklog_id").equals("qa_w1"));reopened.clear();yes(new PendingWorkPhotoStore(context).read().isEmpty());});
        probe("pending work photo rejects invalid content without replacing a valid stage",()->{Isolated context=new Isolated(getTargetContext());PendingWorkPhotoStore pending=new PendingWorkPhotoStore(context);JSONObject valid=SyntheticBusinessData.object().put("id","qa_pho_mobile_qa_retry").put("customer_id","qa_c1").put("worklog_id","qa_w1").put("data","data:image/jpeg;base64,/9j/");String before=pending.stage(valid.toString());rejected(()->pending.stage(SyntheticBusinessData.object(valid.toString()).put("data","data:image/png;base64,iVBORw0KGgo=").toString()));yes(pending.read().equals(before));pending.clear();});
        probe("confirmed photo delete removes only its unshared private original",()->{PixelLocalStore local=acceptanceStore();try{
            String first=local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("original_name","First.gif").put("data",VALID_GIF));String second=local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("original_name","Second.gif").put("data",VALID_GIF));
            JSONArray submitted=local.pendingActionsForDrive();Set<String> confirmed=new HashSet<>();for(int i=0;i<submitted.length();i++)confirmed.add(submitted.getJSONObject(i).getString("client_id"));
            JSONObject deleteFirst=SyntheticBusinessData.object().put("client_id","qa-delete-first").put("kind","delete").put("payload",SyntheticBusinessData.object().put("entity","photo").put("id",first));submitted.put(deleteFirst);confirmed.add("qa-delete-first");
            JSONObject snapshot=fixture().put("photos",new JSONArray().put(SyntheticBusinessData.object(submitted.getJSONObject(1).getJSONObject("payload").toString())));local.cleanupConfirmedPhotoDeletes(submitted,confirmed,snapshot);
            yes(local.photoAttachment(first)==null&&local.photoAttachment(second)!=null&&local.loadPhotoData(second).equals(VALID_GIF));
            JSONObject deleteSecond=SyntheticBusinessData.object().put("client_id","qa-delete-second").put("kind","delete").put("payload",SyntheticBusinessData.object().put("entity","photo").put("id",second));JSONArray finalDelete=new JSONArray().put(deleteSecond);local.cleanupConfirmedPhotoDeletes(finalDelete,Collections.singleton("qa-delete-second"),fixture().put("photos",new JSONArray()));yes(local.photoAttachment(second)==null);
        }finally{local.close();}});
        probe("confirmed photo cleanup resumes safely after crash before queue acknowledgement",()->{Isolated ctx=new Isolated(getTargetContext());JSONObject initial=fixture();yes(ctx.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",initial.toString()).commit());try(PixelLocalStore local=new PixelLocalStore(ctx)){
            String photo=local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("original_name","Crash.gif").put("data",VALID_GIF));JSONArray submitted=local.pendingActionsForDrive();String createClient=submitted.getJSONObject(0).getString("client_id");String deleteClient="qa-delete-after-create";submitted.put(SyntheticBusinessData.object().put("client_id",deleteClient).put("kind","delete").put("payload",SyntheticBusinessData.object().put("entity","photo").put("id",photo)));
            JSONObject committed=fixture().put("photos",new JSONArray()).put("drive_applied_ids",new JSONArray().put(createClient).put(deleteClient));local.commitSnapshot(committed,new HashSet<>());Set<String> confirmed=new HashSet<>();confirmed.add(createClient);confirmed.add(deleteClient);local.cleanupConfirmedPhotoDeletes(submitted,confirmed,committed);yes(local.photoAttachment(photo)==null);yes(local.pendingPhotoAttachments(local.pendingActionsForDrive()).length()==0);
            local.commitSnapshot(fixture(),new HashSet<>());JSONArray missing=local.pendingPhotoAttachments(local.pendingActionsForDrive());yes(missing.length()==1&&missing.getJSONObject(0).getBoolean("missing"));
        }});
        probe("v2 inline photo queue migrates additively while legacy Drive action remains replayable",()->{Isolated ctx=new Isolated(getTargetContext());JSONObject payload=SyntheticBusinessData.object().put("id","qa_legacy_photo").put("customer_id","qa_c1").put("original_name","Legacy.gif").put("data",VALID_GIF);SQLiteDatabase legacy=ctx.openOrCreateDatabase("haus-garten-mobile.db",0,null);try{legacy.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL,created_at TEXT NOT NULL)");android.content.ContentValues row=new android.content.ContentValues();row.put("id","qa_legacy-change");row.put("kind","photo");row.put("payload",payload.toString());row.put("created_at","2026-10-13T00:00:00Z");legacy.insertOrThrow("pending",null,row);legacy.setVersion(2);}finally{legacy.close();}yes(ctx.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",fixture().put("photos",new JSONArray().put(SyntheticBusinessData.object(payload.toString()))).toString()).commit());try(PixelLocalStore local=new PixelLocalStore(ctx)){JSONObject state=local.pendingActions().getJSONObject(0).getJSONObject("payload"),drive=local.pendingActionsForDrive().getJSONObject(0).getJSONObject("payload");yes(!state.has("data")&&state.getString("content_sha256").matches("[0-9a-f]{64}")&&drive.getString("data").equals(VALID_GIF));yes(!local.getState().getJSONObject("master").getJSONArray("photos").getJSONObject(0).has("data"));JSONObject merged=fixture().put("photos",new JSONArray());yes(DriveJournalRules.apply(merged,local.pendingActionsForDrive()).length()==0);local.recordSnapshotPhotoAttachments(merged);JSONObject migrated=merged.getJSONArray("photos").getJSONObject(0);yes(!migrated.has("data")&&migrated.getString("storage_name").endsWith(".gif"));yes(local.loadPhotoData("qa_legacy_photo").equals(VALID_GIF));}});
        probe("invalid image for valid customer never enters local queue",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("data","R0lGODlhLW5vdC1hLXJlYWwtaW1hZ2U=")));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("unknown worklog photo fails without a pending action",()->{PixelLocalStore local=acceptanceStore();try{rejected(()->local.savePhoto(SyntheticBusinessData.object().put("customer_id","qa_c1").put("worklog_id","qa_unknown").put("data","cWE=")));yes(local.pendingActions().length()==0);}finally{local.close();}});
        probe("explicit duplicate confirmation preserves both customer IDs",()->{PixelLocalStore local=acceptanceStore();try{String id=local.saveCustomer(SyntheticBusinessData.object().put("name","QA Other").put("allow_duplicate",true));yes(!id.equals("qa_c2"));JSONArray customers=PixelMcpReadTools.effective(local.getState()).getJSONArray("customers");yes(customers.length()==3);yes(PixelMcpWriteTools.find(customers,"qa_c2").getString("name").equals("QA Other"));}finally{local.close();}});
        probe("local unissued draft can still be removed and restored",()->{PixelLocalStore local=acceptanceStore();try{String id=local.saveInvoiceDraft(SyntheticBusinessData.object().put("customer_id","qa_c1").put("positions",new JSONArray("[{\"title\":\"QA\",\"quantity\":1,\"price\":10}]")));local.removeEntity("invoice",id);yes(SyntheticBusinessData.object(local.undoRemoveEntity("invoice",id)).getBoolean("ok"));yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("invoices"),id).getString("status").equals("Entwurf"));}finally{local.close();}});
    }
    void billingAcceptance()throws Exception{
        probe("billing native direct, subcontractor, override and offline reload",()->{
            Isolated context=new Isolated(getTargetContext());JSONObject master=fixture();master.put("photos",new JSONArray());
            master.getJSONArray("customers").put(SyntheticBusinessData.object().put("id","qa_payer").put("name","QA Rechnungsempfänger"));
            master.getJSONArray("customers").getJSONObject(0).put("billing_mode","via_customer").put("default_billing_customer_id","qa_payer");
            yes(context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",master.toString()).commit());
            PixelLocalStore local=new PixelLocalStore(context);
            try{
                JSONObject request=args("qa-native-billing");String id=local.execute("create_customer_work_record",request).getJSONObject("data").getString("entity_id");
                local.close();JSONObject read=PixelMcpReadTools.effective(local.getState());JSONObject saved=PixelMcpWriteTools.find(read.getJSONArray("worklogs"),id);
                yes(saved.getString("customer_id").equals("qa_c1")&&saved.getString("billing_customer_id").equals("qa_payer"));
                yes(local.execute("create_customer_work_record",request).getJSONObject("data").getBoolean("replayed"));
                String override=local.saveWorklog(args("qa-native-override").put("billing_customer_id","qa_c1"));
                yes(PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("worklogs"),override).getString("billing_customer_id").equals("qa_c1"));
                rejected(()->local.removeEntity("customer","qa_payer"));
            }finally{local.close();}
        });
        probe("billing timer keeps recipient through pause resume stop and completion",()->{
            Isolated context=new Isolated(getTargetContext());JSONObject master=fixture();master.put("photos",new JSONArray());
            master.getJSONArray("customers").put(SyntheticBusinessData.object().put("id","qa_payer").put("name","QA Rechnungsempfänger"));
            master.getJSONArray("customers").getJSONObject(0).put("billing_mode","via_customer").put("default_billing_customer_id","qa_payer");
            yes(context.getSharedPreferences("haus_garten",0).edit().putString("drive_snapshot",master.toString()).commit());
            PixelLocalStore local=new PixelLocalStore(context);
            try{
                JSONObject start=local.startWorkSession(SyntheticBusinessData.object().put("request_id","qa-billing-start").put("customer_id","qa_c1").put("order_id","qa_o1").put("work_date","2026-10-13"));yes(start.getString("billing_customer_id").equals("qa_payer"));
                WorkSessionStore real=new WorkSessionStore(local.getWritableDatabase(),context);real.discard(start.getString("id"),"qa-discard-instant");
                FakeClock clock=new FakeClock();WorkSessionStore timer=new WorkSessionStore(local.getWritableDatabase(),clock);
                JSONObject s=timer.start(SyntheticBusinessData.object().put("request_id","qa-billing-time").put("customer_id","qa_c1").put("order_id","qa_o1").put("billing_customer_id","qa_payer").put("work_date","2026-10-13"));String sid=s.getString("id");
                clock.advance(3600000);timer.pause(sid,"qa-billing-pause");clock.advance(1800000);timer.resume(sid,"qa-billing-resume");clock.advance(3600000);timer.stop(sid,"qa-billing-stop");
                JSONObject stopped=timer.current(sid);yes(stopped.getDouble("hours")==2&&stopped.getString("billing_customer_id").equals("qa_payer"));
                String id=local.completeWorkSession(sid,SyntheticBusinessData.object().put("activity","QA Timerpflege"),"qa-billing-complete");
                JSONObject row=PixelMcpWriteTools.find(PixelMcpReadTools.effective(local.getState()).getJSONArray("worklogs"),id);
                yes(row.getString("billing_customer_id").equals("qa_payer")&&row.getString("customer_id").equals("qa_c1")&&row.getString("order_id").equals("qa_o1")&&row.getDouble("hours")==2);
                yes(local.completeWorkSession(sid,SyntheticBusinessData.object().put("activity","QA Timerpflege"),"qa-billing-complete").equals(id));
            }finally{local.close();}
        });
    }
    static final class Isolated extends ContextWrapper {
        // All billing writes below remain isolated like the existing acceptance suite.
        final String prefix="mcp_qa_"+UUID.randomUUID().toString().replace("-","")+"_";
        final Set<String> preferenceNames=java.util.concurrent.ConcurrentHashMap.newKeySet();
        Isolated(Context base){super(base);}
        String name(String n){if(!n.matches("[a-zA-Z0-9_.-]+"))throw new IllegalArgumentException("Invalid test path");return prefix+n;}
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){return getBaseContext().getDir(prefix+"files",MODE_PRIVATE);}
        @Override public File getCacheDir(){return getBaseContext().getDir(prefix+"cache",MODE_PRIVATE);}
        @Override public File getNoBackupFilesDir(){return getBaseContext().getDir(prefix+"no_backup",MODE_PRIVATE);}
        @Override public SharedPreferences getSharedPreferences(String n,int mode){String scoped=name(n);preferenceNames.add(scoped);return super.getSharedPreferences(scoped,mode);}
        @Override public File getDatabasePath(String n){return super.getDatabasePath(name(n));}
        @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f){return super.openOrCreateDatabase(name(n),mode,f);}
        @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return super.openOrCreateDatabase(name(n),mode,f,h);}
        @Override public Object getSystemService(String name){if(Context.JOB_SCHEDULER_SERVICE.equals(name))return null;return super.getSystemService(name);}
        @Override public void sendBroadcast(Intent intent){} // never invoke the real Drive synchronizer
    }
    @Override public android.app.Activity newActivity(ClassLoader loader,String className,android.content.Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        android.app.Activity fixture=RevierWorkflowUiNativeAcceptance.newActivityIfRequested(className,intent);
        return fixture!=null?fixture:super.newActivity(loader,className,intent);
    }
    @Override public void onCreate(Bundle args){
        super.onCreate(args);
        liveTunnelProbe=args!=null&&"true".equals(args.getString("live_tunnel_probe"));
        installRuntimeKeyBase64=args==null?null:args.getString("install_runtime_key_base64");
        installTunnelId=args==null?"":args.getString("tunnel_id","");
        installOrganization=args==null?"":args.getString("organization","");
        startTunnelService=args!=null&&"true".equals(args.getString("start_tunnel_service"));
        restoreEnabledTunnel=args!=null&&"true".equals(args.getString("restore_enabled_tunnel"));
        businessCapture=args!=null&&"true".equals(args.getString("business_capture"));
        releaseMode=args==null?"":args.getString("release_lifecycle","");
        start();
    }
    JSONObject fixture()throws Exception{return SyntheticBusinessData.object("{\"capabilities\":{\"worklog_compare_and_save\":true},\"customers\":[{\"id\":\"qa_c1\",\"name\":\"QA Kunde\"}],\"orders\":[{\"id\":\"qa_o1\",\"customer_id\":\"qa_c1\",\"location\":\"QA Garten\"}],\"worklogs\":[],\"photos\":[],\"invoices\":[],\"accepted_ids\":[],\"sync_errors\":[]}");}
    String businessSnapshotHash() throws Exception {
        String snapshot=getTargetContext().getSharedPreferences("haus_garten",0).getString("drive_snapshot","");
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return android.util.Base64.encodeToString(digest,android.util.Base64.NO_WRAP);
    }
    void reportProductionCounts()throws Exception{
        String raw=getTargetContext().getSharedPreferences("haus_garten",0).getString("drive_snapshot","");
        JSONObject counts=SyntheticBusinessData.object().put("source","Pixel stored Drive snapshot").put("snapshot_present",!raw.isEmpty());
        if(!raw.isEmpty()){JSONObject snapshot=SyntheticBusinessData.object(raw);for(String name:new String[]{"customers","orders","worklogs","photos","invoices"}){JSONArray rows=snapshot.optJSONArray(name);counts.put(name,rows==null?JSONObject.NULL:rows.length());}}
        Bundle b=new Bundle();b.putString("stream","READONLY_PIXEL_COUNTS "+counts.toString()+"\n");sendStatus(0,b);
    }
    JSONObject args(String request)throws Exception{return SyntheticBusinessData.object().put("request_id",request).put("customer_id","qa_c1").put("work_date","2026-10-10").put("activity","QA Heckenschnitt").put("hours",3);}
    @Override public void onStart(){
        if(businessCapture){
            // Restricted to the existing business API. No preferences, tokens,
            // credentials, WebView session files or arbitrary file access.
            Bundle result=new Bundle();PixelLocalStore local=null;
            try{
                local=new PixelLocalStore(getTargetContext());
                byte[] bytes=local.getState().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if(bytes.length>32*1024*1024)throw new IllegalStateException("Business backup exceeds limit");
                String encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
                for(int offset=0;offset<encoded.length();offset+=50000){
                    Bundle chunk=new Bundle();chunk.putString("stream","BUSINESS_CAPTURE "+encoded.substring(offset,Math.min(encoded.length(),offset+50000))+"\n");sendStatus(0,chunk);
                }
                result.putString("stream","BUSINESS_CAPTURE_OK\n");finish(-1,result);
            }catch(Throwable error){result.putString("stream","BUSINESS_CAPTURE_FAILED "+error.getClass().getSimpleName()+"\n");finish(0,result);}
            finally{if(local!=null)local.close();}
            return;
        }
        if(restoreEnabledTunnel){
            Bundle restore=new Bundle();
            try{PixelTunnelConfig config=new PixelTunnelConfig(getTargetContext());
                if(config.enabled()&&config.configured()){
                    getTargetContext().startForegroundService(new Intent(getTargetContext(),PixelTunnelService.class));
                    restore.putString("stream","PREVIOUSLY_ENABLED_TUNNEL_START_REQUESTED; no configuration changed; reachability not confirmed\n");
                }else restore.putString("stream","TUNNEL_RESTORE_SKIPPED; disabled or unconfigured; no configuration changed\n");
                finish(-1,restore);
            }catch(Throwable error){restore.putString("stream","TUNNEL_RESTORE_FAILED "+error.getClass().getSimpleName()+"; no configuration changed\n");finish(0,restore);}
            return;
        }
        if(startTunnelService){
            Bundle result=new Bundle();
            try{
                PixelTunnelConfig config=new PixelTunnelConfig(getTargetContext());
                if(!config.configured())throw new IllegalStateException("Runtime key missing");
                config.enabled(true);
                getTargetContext().startForegroundService(new Intent(getTargetContext(),PixelTunnelService.class));
                result.putString("stream","PIXEL_TUNNEL_SERVICE_STARTED\n");
                finish(-1,result);
            }catch(Throwable error){
                result.putString("stream","PIXEL_TUNNEL_SERVICE_START_FAILED "+error.getClass().getSimpleName()+"\n");
                finish(0,result);
            }
            return;
        }        if(installRuntimeKeyBase64!=null){
            Bundle result=new Bundle();
            try{
                byte[] decoded=android.util.Base64.decode(installRuntimeKeyBase64,android.util.Base64.NO_WRAP|android.util.Base64.URL_SAFE);
                String secret=new String(decoded,java.nio.charset.StandardCharsets.UTF_8);
                PixelTunnelConfig config=new PixelTunnelConfig(getTargetContext());
                config.save(installTunnelId,installOrganization,secret);
                config.enabled(true);
                getTargetContext().startForegroundService(new Intent(getTargetContext(),PixelTunnelService.class));
                java.util.Arrays.fill(decoded,(byte)0);
                result.putString("stream","RUNTIME_KEY_INSTALLED securely\n");
                finish(-1,result);
            }catch(Throwable error){
                result.putString("stream","RUNTIME_KEY_INSTALL_FAILED "+error.getClass().getSimpleName()+"\n");
                finish(0,result);
            }
            return;
        }
        if(releaseMode.equals("estimates")||releaseMode.equals("provider")||releaseMode.equals("mcp_workflows")){Bundle result=new Bundle();try{String before=businessSnapshotHash();if(releaseMode.equals("mcp_workflows")){McpWorkflowAcceptance.run(this);DriveProviderAcceptance.run(this);try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(getTargetContext())){McpAuditTools.report(this,f.tools.tools());}}else if(releaseMode.equals("provider"))DriveProviderAcceptance.run(this);else EstimateNativeAcceptance.run(this);check("business snapshot unchanged after isolated probes",()->yes(before.equals(businessSnapshotHash())));result.putString("stream","RESULT "+passed+" Android SQLite/MCP tests passed; acceptance failures="+acceptanceFailed+"; isolated group only\n");finish(acceptanceFailed==0?-1:0,result);}catch(Throwable error){result.putString("stream","FAILED "+error.getClass().getSimpleName()+"\n");finish(0,result);}return;}
        if(releaseMode.equals("plants")||releaseMode.equals("plants_ui")){Bundle result=new Bundle();try{String before=businessSnapshotHash();if(releaseMode.equals("plants"))PlantLibraryAcceptance.run(this);else PlantLibraryAcceptance.runUi(this);check("business snapshot unchanged after plant98 probes",()->yes(before.equals(businessSnapshotHash())));result.putString("stream","RESULT "+passed+" Android plant98 tests passed; acceptance failures="+acceptanceFailed+"\n");finish(acceptanceFailed==0?-1:0,result);}catch(Throwable error){result.putString("stream","FAILED "+error.getClass().getSimpleName()+"\n");finish(0,result);}return;}
        if(releaseMode.equals("fresh_isolated")){ReleaseLifecycleProbe.run(this,new Isolated(getTargetContext()),"fresh");return;}
        if(!releaseMode.isEmpty()){ReleaseLifecycleProbe.run(this,getTargetContext(),releaseMode);return;}
        if(liveTunnelProbe){PixelTunnelLiveProbe.run(this,getTargetContext());return;}
        Bundle result=new Bundle();PixelLocalStore store=null;
        try{
            String productionBefore=businessSnapshotHash();
            reportProductionCounts();
            Isolated context=new Isolated(getTargetContext());SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            check("synthetic database media cache and backup paths remain inside their isolated QA context",()->{
                for(File path:new File[]{context.getFilesDir(),context.getCacheDir(),context.getNoBackupFilesDir(),context.getDatabasePath("haus-garten-mobile.db")})yes(path.getCanonicalPath().contains(context.prefix));
                yes(!context.getNoBackupFilesDir().getCanonicalPath().equals(new File(getTargetContext().getApplicationInfo().dataDir,"no_backup").getCanonicalPath()));
            });
            check("legacy tunnel preferences stay unchanged without user save",()->{PixelTunnelConfig c=new PixelTunnelConfig(context);yes(c.organization().isEmpty()&&!c.configured());yes(!context.getSharedPreferences("pixel_tunnel_private",0).contains("organization"));});
            check("organization persists separately without changing IDs or credentials",()->{PixelTunnelConfig c=new PixelTunnelConfig(context);String id=c.id();c.organization("org-IsolatedTest1234");PixelTunnelConfig reopenedConfig=new PixelTunnelConfig(context);yes(reopenedConfig.organization().equals("org-IsolatedTest1234")&&reopenedConfig.id().equals(id)&&!reopenedConfig.configured());rejected(()->c.organization("org-unsafe\r\n"));yes(c.organization().equals("org-IsolatedTest1234"));});
            JSONObject master=fixture();yes(prefs.edit().putString("drive_snapshot",master.toString()).commit());
            FakeClock timerClock=new FakeClock();store=new PixelLocalStore(context,timerClock);final PixelLocalStore first=store;
            WorkSessionStore timer=first.workSessions();
            JSONObject timerStarted=timer.start(SyntheticBusinessData.object().put("request_id","timer-start").put("work_date","2026-10-11"));String sessionId=timerStarted.getString("id");
            check("timer starts without customer and persists a running draft",()->{yes(timerStarted.getString("state").equals(WorkSessionStore.RUNNING));yes(timerStarted.getString("customer_id").isEmpty());});
            timerClock.advance(3_600_000);JSONObject timerPaused=timer.pause(sessionId,"timer-pause");
            check("timer pause accrues exactly one hour",()->yes(timerPaused.getLong("worked_ms")==3_600_000L&&timerPaused.getDouble("hours")==1.0));
            timerClock.advance(600_000);JSONObject timerResumed=timer.resume(sessionId,"timer-resume");timerClock.advance(1_800_000);JSONObject timerStopped=timer.stop(sessionId,"timer-stop");
            check("timer excludes pause and supports resume stop",()->yes(timerResumed.getString("state").equals(WorkSessionStore.RUNNING)&&timerStopped.getLong("worked_ms")==5_400_000L&&timerStopped.getDouble("hours")==1.5));
            check("timer requests are idempotent",()->{yes(timer.stop(sessionId,"timer-stop").getLong("worked_ms")==5_400_000L);try{timer.stop(sessionId,"different-stop");throw new AssertionError("Expected rejection");}catch(IllegalStateException expected){}});
            timerClock.advance(1_200_000);
            JSONObject continued=timer.continueStopped(sessionId,"timer-continue");
            check("continue preserves ID and excludes stopped gap",()->{yes(continued.getString("id").equals(sessionId)&&continued.getLong("worked_ms")==5_400_000L);yes(timer.continueStopped(sessionId,"timer-continue").getString("state").equals(WorkSessionStore.RUNNING));});
            timerClock.advance(1_800_000);JSONObject stoppedAgain=timer.stop(sessionId,"timer-stop-again");
            check("native pause total includes pause and continued stop gap",()->yes(stoppedAgain.getDouble("hours")==2.0&&stoppedAgain.getInt("break_minutes")==30&&!stoppedAgain.getBoolean("pause_unverified")));
            JSONObject timerFinal=timer.finalizeSession(sessionId,"qa_worklog_final","timer-finalize");
            check("timer finalizes once with stable worklog reference",()->{yes(timerFinal.getString("state").equals(WorkSessionStore.FINALIZED));yes(timer.finalizeSession(sessionId,"qa_worklog_final","timer-finalize").getString("finalized_worklog_id").equals("qa_worklog_final"));});
            FakeClock referenceClock=new FakeClock();referenceClock.wall=java.time.Instant.parse("2027-05-18T06:00:00Z").toEpochMilli();
            WorkSessionStore referenceTimer=new WorkSessionStore(first.getWritableDatabase(),referenceClock);
            JSONObject reference=referenceTimer.start(SyntheticBusinessData.object().put("request_id","qa_reference_start").put("work_date","2027-05-18"));String referenceId=reference.getString("id");
            check("second start returns existing persistent session",()->yes(referenceTimer.start(SyntheticBusinessData.object().put("request_id","qa_reference_duplicate")).getString("id").equals(referenceId)));
            referenceClock.advance(75*60_000L);referenceTimer.pause(referenceId,"qa_reference_pause");referenceClock.advance(15*60_000L);rejected(()->referenceTimer.resume(referenceId,"qa_reference_pause"));referenceTimer.resume(referenceId,"qa_reference_resume");referenceClock.advance(60*60_000L);
            check("reference day 10:30 has135 net minutes",()->yes(new WorkSessionStore(first.getWritableDatabase(),referenceClock).current(referenceId).getLong("worked_ms")==135*60_000L));
            referenceClock.advance(15*60_000L);JSONObject referenceStopped=referenceTimer.stop(referenceId,"qa_reference_stop");
            check("reference day 10:45 has150 net minutes and15 pause",()->yes(referenceStopped.getLong("worked_ms")==150*60_000L&&referenceStopped.getInt("break_minutes")==15&&!referenceStopped.getBoolean("review_required")));
            referenceTimer.finalizeSession(referenceId,"qa_reference_worklog","qa_reference_finalize");
            JSONObject latchInput=SyntheticBusinessData.object().put("request_id","qa_latch_start");JSONObject latch=timer.start(latchInput);String latchId=latch.getString("id");timerClock.advance(60_000);timerClock.wall+=300_000;
            check("direct start replay persists review even after clock is restored",()->{yes(timer.start(latchInput).getBoolean("review_required"));timerClock.wall-=300_000;JSONObject frozen=timer.current(latchId);yes(frozen.getBoolean("review_required")&&frozen.getLong("worked_ms")==0);timer.discard(latchId,"qa_latch_discard");});
            JSONObject second=timer.start(SyntheticBusinessData.object().put("request_id","timer-second"));timerClock.advance(300_000);timerClock.boot="boot-b";timerClock.wall+=300_000;timerClock.elapsed=10_000;
            check("reboot preserves original session and blocks guessed elapsed time",()->{
                JSONObject unverified=timer.current(second.getString("id"));yes(unverified.getBoolean("review_required")&&unverified.getLong("worked_ms")==0&&unverified.getString("review_reason").equals("DEVICE_RESTART"));
                try{timer.pause(second.getString("id"),"timer-after-boot");throw new AssertionError("Guessed time accepted");}catch(IllegalStateException expected){}
                JSONObject input=SyntheticBusinessData.object().put("request_id","qa_timer_review").put("expected_revision_hash",unverified.getString("revision_hash")).put("worked_minutes",5).put("break_minutes",0).put("reason","QA explicitly verified after reboot").put("confirmed",true);
                input.put("session_id",second.getString("id"));JSONObject checked=first.execute("review_work_session",input).getJSONObject("data").getJSONObject("session");yes(!checked.getBoolean("review_required")&&checked.getLong("worked_ms")==300_000L&&checked.getString("state").equals(WorkSessionStore.STOPPED));
                yes(first.execute("review_work_session",input).getJSONObject("data").getBoolean("replayed"));
                try(android.database.Cursor audit=first.getReadableDatabase().rawQuery("SELECT payload FROM work_session_events WHERE session_id=? AND event_type='TIME_REVIEW'",new String[]{second.getString("id")})){
                    yes(audit.moveToFirst());JSONObject original=new JSONObject(audit.getString(0)).getJSONObject("original_storage");yes(original.getString("state").equals(WorkSessionStore.RUNNING)&&original.getString("boot_marker").equals("boot-a")&&!original.isNull("segment_started_elapsed_ms"));
                }
                rejected(()->first.execute("review_work_session",SyntheticBusinessData.object(input.toString()).put("worked_minutes",6)));
                timer.continueStopped(second.getString("id"),"qa_timer_review_continue");
            });
            final JSONObject created=first.execute("create_customer_work_record",args("qa-request-one"));
            String id=created.getJSONObject("data").getString("work_record_id"),client=created.getJSONObject("data").getJSONObject("sync").getString("client_id");
            check("real SQLite create without order",()->{yes(first.pendingActions().length()==1);yes(PixelMcpReadTools.effective(first.getState()).getJSONArray("worklogs").getJSONObject(0).getString("order_id").isEmpty());});
            check("internal receipt not exported",()->{yes(first.pendingActionsForDrive().length()==1);yes(first.pendingActionsForDrive().getJSONObject(0).getString("kind").equals("worklog"));});
            check("same request idempotent",()->{yes(first.execute("create_customer_work_record",args("qa-request-one")).getJSONObject("data").getBoolean("replayed"));yes(first.pendingActions().length()==1);});
            check("same request changed rejected",()->rejected(()->first.execute("create_customer_work_record",args("qa-request-one").put("hours",4))));
            check("different request duplicate blocked",()->rejected(()->first.execute("create_customer_work_record",args("qa-request-two"))));
            check("bad reference rollback",()->{rejected(()->first.execute("create_customer_work_record",args("qa-bad-customer").put("customer_id","qa_unknown")));yes(first.pendingActions().length()==1);});
            check("queued is NOT confirmed",()->{JSONObject s=first.status(client,first.getState());yes(s.getString("status").equals("queued")&&!s.getBoolean("confirmed_by_app")&&!s.getBoolean("uploaded_to_drive"));});
            check("transfer distinct from laptop acceptance",()->{first.markUploaded(first.pendingActionsForDrive());JSONObject s=first.status(client,first.getState());yes(s.getString("status").equals("transferred")&&!s.getBoolean("confirmed_by_app"));});
            store.close();store=new PixelLocalStore(context,timerClock);final PixelLocalStore reopened=store;WorkSessionStore timerReopened=reopened.workSessions();
            check("replay persists after database reopen",()->{yes(reopened.execute("create_customer_work_record",args("qa-request-one")).getJSONObject("data").getBoolean("replayed"));yes(reopened.pendingActions().length()==1);});
            JSONObject saved=SyntheticBusinessData.object(reopened.pendingActions().getJSONObject(0).getJSONObject("payload").toString());saved.remove("_mcp_create");
            master.getJSONArray("worklogs").put(saved);master.getJSONArray("accepted_ids").put(client);yes(prefs.edit().putString("drive_snapshot",master.toString()).commit());reopened.acknowledge(Collections.singleton(client));
            check("accepted only after actual snapshot receipt",()->{JSONObject s=reopened.status(client,reopened.getState());yes(s.getString("status").equals("accepted")&&s.getBoolean("confirmed_by_app"));yes(reopened.pendingActions().length()==0);});
            String hash=PixelMcpWriteTools.hash(PixelMcpWriteTools.base(saved));
            JSONObject edit=SyntheticBusinessData.object().put("request_id","qa-update-one").put("work_record_id",id).put("expected_record_hash",hash).put("updates",SyntheticBusinessData.object().put("notes","QA Nachtrag"));
            JSONObject updated=reopened.execute("update_work_record",edit);
            check("update keeps customer/entity ID",()->{JSONObject row=reopened.pendingActions().getJSONObject(0).getJSONObject("payload");yes(row.getString("id").equals(id)&&row.getString("customer_id").equals("qa_c1"));yes(row.has("_sync_base")&&row.getString("notes").equals("QA Nachtrag"));});
            check("parallel pending edit rejected",()->rejected(()->reopened.execute("update_work_record",SyntheticBusinessData.object(edit.toString()).put("request_id","qa-update-two"))));
            String updateClient=updated.getJSONObject("data").getJSONObject("sync").getString("client_id");
            check("manual follow-up preserves pending MCP request as guarded action chain",()->{reopened.saveWorklog(SyntheticBusinessData.object(saved.toString()).put("notes","QA manual"));yes(reopened.status(updateClient,reopened.getState()).getString("status").equals("queued"));JSONArray chain=reopened.pendingActions();yes(chain.length()==2);yes(chain.getJSONObject(0).getString("client_id").equals(updateClient));yes(chain.getJSONObject(1).getJSONObject("payload").getString("id").equals(id));yes(chain.getJSONObject(1).getJSONObject("payload").getJSONObject("_sync_base").getString("notes").equals("QA Nachtrag"));JSONObject mirrored=SyntheticBusinessData.object(reopened.getState().getJSONObject("master").toString()).put("photos",new JSONArray());yes(DriveJournalRules.apply(mirrored,chain).length()==0);yes(PixelMcpWriteTools.find(mirrored.getJSONArray("worklogs"),id).getString("notes").equals("QA manual"));});
            check("create with order uses existing saver",()->{JSONObject a=args("qa-order-request").put("order_id","qa_o1").put("activity","QA mit Auftrag");a.remove("customer_id");reopened.execute("create_work_record",a);JSONArray rows=reopened.pendingActions();JSONObject p=rows.getJSONObject(rows.length()-1).getJSONObject("payload");yes(p.getString("order_id").equals("qa_o1")&&p.getString("customer_id").equals("qa_c1")&&p.getString("location").equals("QA Garten"));});
            check("write RPC executes real persistence",()->{PixelMcpReadTools tools=new PixelMcpReadTools(reopened::getState,reopened);JSONObject rpc=SyntheticBusinessData.object().put("jsonrpc","2.0").put("id",10).put("method","tools/call").put("params",SyntheticBusinessData.object().put("name","create_customer_work_record").put("arguments",args("qa-rpc-request").put("activity","QA RPC")));yes(!tools.rpc(rpc).getJSONObject("result").getBoolean("isError"));});
            timerReopened.stop(second.getString("id"),"timer-second-stop");int pendingBefore=reopened.pendingActions().length();
            String timerWorklog=reopened.completeWorkSession(second.getString("id"),SyntheticBusinessData.object().put("customer_id","qa_c1").put("activity","QA Timer Arbeit").put("break_minutes",0).put("kilometers",0),"timer-second-finalize");
            check("stopped timer creates exactly one stable worklog",()->{yes(timerWorklog.startsWith("wrk_session_"));yes(reopened.pendingActions().length()==pendingBefore+1);yes(timerReopened.current(second.getString("id")).getString("state").equals(WorkSessionStore.FINALIZED));yes(reopened.completeWorkSession(second.getString("id"),SyntheticBusinessData.object(),"timer-second-finalize").equals(timerWorklog));yes(reopened.pendingActions().length()==pendingBefore+1);});
            JSONObject locationTimer=timerReopened.start(SyntheticBusinessData.object().put("request_id","timer-location-start").put("customer_id","qa_c1").put("location","QA Gartenweg 2"));
            String locationSession=locationTimer.getString("id");
            timerClock.advance(900_000);timerReopened.pause(locationSession,"timer-location-pause");
            check("timer location and customer survive native persistence",()->{JSONObject s=timerReopened.current(locationSession);yes(s.getString("customer_id").equals("qa_c1"));yes(s.getString("location").equals("QA Gartenweg 2"));yes(s.getLong("worked_ms")==900_000L);});
            int beforeDiscard=reopened.pendingActions().length();
            check("discard is recoverable and never deletes existing worklogs",()->{yes(timerReopened.discard(locationSession,"timer-discard").getString("state").equals(WorkSessionStore.CANCELLED));yes(timerReopened.current().getString("state").equals("IDLE"));yes(reopened.pendingActions().length()==beforeDiscard);yes(timerReopened.discard(locationSession,"timer-discard").getLong("worked_ms")==900_000L);});
            timerClock.advance(3_600_000);
            check("undo restores paused time without counting discarded interval",()->{yes(timerReopened.restore(locationSession,"timer-restore").getString("state").equals(WorkSessionStore.PAUSED));yes(timerReopened.current(locationSession).getLong("worked_ms")==900_000L);yes(timerReopened.restore(locationSession,"timer-restore").getLong("worked_ms")==900_000L);});
            timerReopened.resume(locationSession,"timer-location-resume");timerClock.advance(300_000);timerReopened.stop(locationSession,"timer-location-stop");
            String locationWorklog=reopened.completeWorkSession(locationSession,SyntheticBusinessData.object().put("activity","QA Standort Arbeit").put("break_minutes",0).put("kilometers",0),"timer-location-finalize");
            check("final worklog keeps confirmed timer customer and place",()->{JSONObject row=PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs").getJSONObject(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs").length()-1);yes(row.getString("id").equals(locationWorklog));yes(row.getString("customer_id").equals("qa_c1"));yes(row.getString("location").equals("QA Gartenweg 2"));yes(row.getDouble("hours")==0.33);});
            check("finalized timer cannot be discarded",()->{try{timerReopened.discard(locationSession,"timer-bad-discard");throw new AssertionError("Expected rejection");}catch(IllegalStateException expected){}yes(reopened.pendingActions().length()==beforeDiscard+1);});
            check("native start rejects invented customer and conflicting order",()->{rejected(()->reopened.startWorkSession(SyntheticBusinessData.object().put("request_id","timer-bad-reference").put("customer_id","qa_unknown")));rejected(()->reopened.startWorkSession(SyntheticBusinessData.object().put("request_id","timer-bad-order").put("customer_id","").put("order_id","qa_o1")));});
            JSONObject rollbackTimer=timerReopened.start(SyntheticBusinessData.object().put("request_id","timer-rollback-start").put("customer_id","qa_c1"));
            timerClock.advance(60_000);timerReopened.stop(rollbackTimer.getString("id"),"timer-rollback-stop");
            int beforeRollback=reopened.pendingActions().length();
            check("finalization failure rolls back business write and timer together",()->{rejected(()->reopened.completeWorkSession(rollbackTimer.getString("id"),SyntheticBusinessData.object().put("activity","QA rollback"),""));yes(reopened.pendingActions().length()==beforeRollback);yes(timerReopened.current(rollbackTimer.getString("id")).getString("state").equals(WorkSessionStore.STOPPED));});
            timerReopened.discard(rollbackTimer.getString("id"),"timer-rollback-discard");
            JSONObject occupied=timerReopened.start(SyntheticBusinessData.object().put("request_id","timer-occupied-start"));
            check("restore cannot collide with a new active timer",()->{try{timerReopened.restore(rollbackTimer.getString("id"),"timer-collision");throw new AssertionError("Expected rejection");}catch(IllegalStateException expected){}yes(timerReopened.current().getString("id").equals(occupied.getString("id")));});
            timerReopened.discard(occupied.getString("id"),"timer-occupied-discard");
            JSONObject authoritative=timerReopened.start(SyntheticBusinessData.object().put("request_id","timer-authoritative-start").put("customer_id","qa_c1"));
            String authoritativeId=authoritative.getString("id");timerClock.advance(3_600_000);timerReopened.pause(authoritativeId,"timer-authoritative-pause");timerClock.advance(1_800_000);timerReopened.resume(authoritativeId,"timer-authoritative-resume");timerClock.advance(1_800_000);timerReopened.stop(authoritativeId,"timer-authoritative-stop");
            String authoritativeWorklog=reopened.completeWorkSession(authoritativeId,SyntheticBusinessData.object().put("activity","QA authoritative totals").put("hours",99).put("break_minutes",999).put("start_time","00:00").put("end_time","23:00"),"timer-authoritative-finalize");
            check("completion uses native net time and pause rather than stale form values",()->{JSONArray rows=PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs");JSONObject row=rows.getJSONObject(rows.length()-1);yes(row.getString("id").equals(authoritativeWorklog)&&row.getDouble("hours")==1.5&&row.getInt("break_minutes")==30);yes(row.optString("start_time").isEmpty()&&row.optString("end_time").isEmpty());yes(row.getString("notes").contains("Timer"));});
            check("completion replay cannot create a second worklog",()->{int count=reopened.pendingActions().length();yes(reopened.completeWorkSession(authoritativeId,SyntheticBusinessData.object(),"timer-authoritative-finalize").equals(authoritativeWorklog));yes(reopened.pendingActions().length()==count);});
            check("productive Drive snapshot unchanged during isolated tests",()->yes(productionBefore.equals(businessSnapshotHash())));
            acceptance();Sync96NativeAcceptance.run(this);OwnerNativeAcceptance.run(this);BackupCoreNativeAcceptance.run(this);BackupJobsNativeAcceptance.run(this);BackupServiceNativeAcceptance.run(this);BackupAutomationNativeAcceptance.run(this);FullBackupControlNativeAcceptance.run(this);BackupBrowserUiNativeAcceptance.run(this);BackupRestoreNativeAcceptance.run(this);RevierNativeAcceptance.run(this);LocalWorkflowNativeAcceptance.run(this);OfferDecisionNativeAcceptance.run(this);LocalFinanceNativeAcceptance.run(this);RevierWorkflowUiNativeAcceptance.run(this);LocalSessionContextNativeAcceptance.run(this);LocalAuthorityNativeAcceptance.run(this);LocalAuthorityEvidenceNativeAcceptance.run(this);RevierPhotoNativeAcceptance.run(this);PhotoDraftRestoreNativeAcceptance.run(this);EstimateNativeAcceptance.run(this);PlanningNativeAcceptance.run(this);NativeDriveAcceptance.run(this);DriveProviderAcceptance.run(this);McpDispatchAcceptance.run(this);check("productive Drive snapshot unchanged after acceptance probes",()->yes(productionBefore.equals(businessSnapshotHash())));
            result.putString("stream","RESULT "+passed+" Android SQLite/MCP tests passed; acceptance failures="+acceptanceFailed+"; isolated namespace="+context.prefix+"\n");result.putInt("passed",passed);result.putInt("failed",acceptanceFailed);finish(acceptanceFailed==0?-1:0,result);
        }catch(Throwable error){result.putString("stream","FAILED "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");finish(0,result);}
        finally{if(store!=null)store.close();}
    }
}
