package at.hausgarten.service;

import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import org.json.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

/** Actual isolated SQLite, PNG decoding and private originals; no camera launch or physical disk filling. */
final class RevierPhotoNativeAcceptance {
    private static final String[] ROLES={"before","during","after","documentation","damage"};
    private static final class Clock implements WorkSessionStore.Clock {
        long wall=Instant.parse("2027-05-18T06:00:00Z").toEpochMilli(),elapsed=1000;
        public long wallMillis(){return wall;}public long elapsedMillis(){return elapsed;}public String bootMarker(){return "qa_revier_photo_boot";}
        void minutes(int value){wall+=value*60000L;elapsed+=value*60000L;}
    }
    private static final class ContextIds {
        final String property,visit,session,otherProperty;
        ContextIds(String property,String visit,String session,String otherProperty){this.property=property;this.visit=visit;this.session=session;this.otherProperty=otherProperty;}
    }
    private static ContextIds start(OwnerNativeAcceptance.Fixture fixture,String suffix)throws Exception {
        JSONObject settings=new JSONObject(fixture.store.ownerValue("settings","{}"));settings.put("photo_quality","original");fixture.store.putOwnerValue("settings",settings.toString());
        RevierApplicationService app=new RevierApplicationService(new RevierLocalRepository(fixture.store,null));
        String property=app.execute("create_property",new JSONObject().put("request_id","qa_photo_property_"+suffix).put("customer_id","qa_c").put("name","QA Fotoobjekt")).getString("property_id");
        String other=app.execute("create_property",new JSONObject().put("request_id","qa_photo_other_property_"+suffix).put("customer_id","qa_c").put("name","QA anderes Fotoobjekt")).getString("property_id");
        String visit=app.execute("plan_visit",new JSONObject().put("request_id","qa_photo_visit_"+suffix).put("property_id",property).put("title","QA Fotodokumentation").put("date","2027-05-18").put("planned_minutes",120)).getString("visit_id");
        JSONObject session=fixture.store.startWorkSession(new JSONObject().put("request_id","qa_photo_start_"+suffix).put("customer_id","qa_c").put("property_id",property).put("visit_id",visit).put("work_date","2027-05-18").put("activity","QA laufende Pflege"));
        return new ContextIds(property,visit,session.getString("id"),other);
    }
    private static byte[] png(int seed)throws Exception {
        Bitmap bitmap=Bitmap.createBitmap(12,12,Bitmap.Config.ARGB_8888);
        try{for(int y=0;y<12;y++)for(int x=0;x<12;x++)bitmap.setPixel(x,y,Color.rgb((seed*31+x*17)%256,(seed*43+y*19)%256,(seed*59+x*y*7)%256));ByteArrayOutputStream output=new ByteArrayOutputStream();if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output)||output.size()<1)throw new AssertionError("QA PNG encoding failed");return output.toByteArray();}finally{bitmap.recycle();}
    }
    private static JSONObject photo(String id,String action,byte[] bytes,String role,ContextIds context,Clock clock)throws Exception {
        return new JSONObject().put("id",id).put("_photo_request_id",action).put("customer_id","qa_c").put("session_id",context.session).put("property_id",context.property).put("visit_id",context.visit).put("role",role).put("caption","QA synthetische Einsatzaufnahme").put("capture_time",Instant.ofEpochMilli(clock.wall).toString()).put("original_name",id+".png").put("data","data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP));
    }
    private static JSONObject copy(JSONObject source)throws Exception{return new JSONObject(source.toString());}
    private static long attachments(PixelLocalStore store)throws Exception {try(Cursor count=store.getReadableDatabase().rawQuery("SELECT count(*) FROM photo_attachments",null)){if(!count.moveToFirst())throw new AssertionError("QA attachment count unavailable");return count.getLong(0);}}
    private static JSONObject find(PixelLocalStore store,String id)throws Exception{return PixelMcpWriteTools.find(PixelMcpReadTools.effective(store.getState()).getJSONArray("photos"),id);}
    private static void context(PixelMcpInstrumentation test,JSONObject row,ContextIds expected)throws Exception {test.yes(row.getString("customer_id").equals("qa_c")&&row.getString("property_id").equals(expected.property)&&row.getString("visit_id").equals(expected.visit)&&row.getString("session_id").equals(expected.session));}
    private static void rejected(PixelMcpInstrumentation.Task operation)throws Exception {try{operation.run();}catch(IllegalArgumentException expected){return;}throw new AssertionError("Changed photo action/context was accepted");}
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("photo capture context survives lost result and refuses an unproven restored action without blocking a fresh photo",()->{Clock clock=new Clock();try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext(),clock)){
            ContextIds ids=start(f,"origin");BackupDeviceGuard guard=new BackupDeviceGuard(f.context);JSONObject identity=guard.current(),origin=new JSONObject().put("formatVersion",1).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch"));JSONObject original=photo("qa_origin_photo","qa_origin_photo_action",png(91),"before",ids,clock).put("_capture_context",origin);test.yes(f.store.savePhoto(copy(original)).equals("qa_origin_photo"));guard.advanceForRestore("qa_origin_restore",identity.getString("restoreEpoch"));test.yes(f.store.savePhoto(copy(original)).equals("qa_origin_photo"));JSONObject stale=copy(original).put("id","qa_unproven_restored_photo").put("_photo_request_id","qa_unproven_restored_action");try{f.store.savePhoto(stale);throw new AssertionError("QA stale photo action was written");}catch(IOException expected){test.yes(expected.getMessage().equals("PHOTO_RESTORE_ACTION_REVIEW_REQUIRED"));}test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("photos").length()==1);JSONObject current=guard.current();stale.put("_capture_context",new JSONObject().put("formatVersion",1).put("installation_id",current.getString("installationId")).put("restore_epoch",current.getString("restoreEpoch")));test.yes(f.store.savePhoto(stale).equals("qa_unproven_restored_photo"));test.yes(PixelMcpReadTools.effective(f.store.getState()).getJSONArray("photos").length()==2);test.yes(!find(f.store,"qa_origin_photo").has("_capture_context"));
        }});
        test.probe("Revier native25 distinct PNG originals retain immutable session context roles pagination and one active timer through reopen",()->{
            Clock clock=new Clock();try(OwnerNativeAcceptance.Fixture fixture=new OwnerNativeAcceptance.Fixture(test.getTargetContext(),clock)){
                ContextIds ids=start(fixture,"many");String timerRevision=fixture.store.workSessions().current(ids.session).getString("revision_hash"),worklogs=PixelMcpWriteTools.hash(PixelMcpReadTools.effective(fixture.store.getState()).getJSONArray("worklogs"));int pending=fixture.store.pendingActions().length();
                Set<String> expectedIds=new HashSet<>(),hashes=new HashSet<>();Map<String,String> originalHashes=new HashMap<>();JSONObject first=null;
                for(int i=0;i<25;i++){
                    String id=String.format(Locale.ROOT,"qa_revier_photo_%02d",i);byte[] bytes=png(i+1);String sha=PhotoAttachmentStore.sha256(bytes);test.yes(hashes.add(sha));JSONObject input=photo(id,"qa_revier_photo_action_"+i,bytes,ROLES[i%ROLES.length],ids,clock);if(i==0)first=copy(input);
                    test.yes(fixture.store.savePhoto(copy(input)).equals(id)&&fixture.store.savePhoto(copy(input)).equals(id));JSONObject attachment=fixture.store.photoAttachment(id),row=find(fixture.store,id);context(test,row,ids);test.yes(attachment.getString("content_sha256").equals(sha)&&attachment.getString("role").equals(ROLES[i%ROLES.length])&&PhotoAttachmentStore.sha256(fixture.store.photoAttachmentStore().read(attachment)).equals(sha));test.yes(!row.has("data")&&!row.has("thumbnail_data"));expectedIds.add(id);originalHashes.put(id,sha);clock.minutes(1);
                }
                test.yes(hashes.size()==25&&attachments(fixture.store)==25&&fixture.store.photoAttachmentStore().storageSummary().getInt("local_files")==25&&fixture.store.pendingActions().length()==pending+25);JSONObject repeated=first;rejected(()->fixture.store.savePhoto(copy(repeated).put("role","after")));rejected(()->fixture.store.savePhoto(copy(repeated).put("data","data:image/png;base64,"+Base64.encodeToString(png(500),Base64.NO_WRAP))));
                JSONObject changedContext=photo("qa_revier_photo_wrong_context","qa_revier_photo_wrong_context_action",png(501),"during",ids,clock).put("property_id",ids.otherProperty);rejected(()->fixture.store.savePhoto(copy(changedContext)));test.yes(attachments(fixture.store)==25&&fixture.store.pendingActions().length()==pending+25);
                Set<String> paged=new HashSet<>();for(int offset=0;offset<28;offset+=7){JSONArray page=fixture.call("list_photos",new JSONObject().put("customer_id","qa_c").put("session_id",ids.session).put("offset",offset).put("limit",7)).getJSONArray("photos");test.yes(page.length()==(offset<21?7:4));for(int i=0;i<page.length();i++){JSONObject row=page.getJSONObject(i);context(test,row,ids);test.yes(paged.add(row.getString("id"))&&!row.has("data"));}if(offset==0)test.yes(page.getJSONObject(0).getString("id").equals("qa_revier_photo_24"));}test.yes(paged.equals(expectedIds)&&fixture.call("list_photos",new JSONObject().put("session_id",ids.session).put("offset",25).put("limit",7)).getJSONArray("photos").length()==0);
                for(String role:ROLES){JSONArray filtered=fixture.call("list_photos",new JSONObject().put("customer_id","qa_c").put("session_id",ids.session).put("role",role).put("date_from","2027-05-18").put("date_to","2027-05-18").put("limit",100)).getJSONArray("photos");test.yes(filtered.length()==5);for(int i=0;i<filtered.length();i++)test.yes(filtered.getJSONObject(i).getString("role").equals(role));}
                byte[] profile=png(999);fixture.store.savePhoto(new JSONObject().put("id","qa_revier_profile_photo").put("_photo_request_id","qa_revier_profile_action").put("customer_id","qa_c").put("role","profile").put("original_name","qa_profile.png").put("data","data:image/png;base64,"+Base64.encodeToString(profile,Base64.NO_WRAP)));JSONArray profiles=fixture.call("list_photos",new JSONObject().put("customer_id","qa_c").put("role","profile").put("limit",100)).getJSONArray("photos");test.yes(profiles.length()==1&&profiles.getJSONObject(0).optString("session_id").isEmpty()&&profiles.getJSONObject(0).optString("property_id").isEmpty()&&attachments(fixture.store)==26&&fixture.store.photoAttachmentStore().storageSummary().getInt("local_files")==26);
                JSONObject running=fixture.store.workSessions().current(ids.session);test.yes(running.getString("state").equals("RUNNING")&&running.getLong("worked_ms")==25*60000L&&running.getString("revision_hash").equals(timerRevision));test.yes(new RevierApplicationService(new RevierLocalRepository(fixture.store,null)).validateRelationships().getString("status").equals("PASS"));fixture.store.close();
                try(PixelLocalStore reopened=new PixelLocalStore(fixture.context,clock)){
                    test.yes(attachments(reopened)==26&&PhotoAttachmentStore.sha256(reopened.photoAttachmentStore().read(reopened.photoAttachment("qa_revier_profile_photo"))).equals(PhotoAttachmentStore.sha256(profile))&&reopened.pendingActions().length()==pending+26&&worklogs.equals(PixelMcpWriteTools.hash(PixelMcpReadTools.effective(reopened.getState()).getJSONArray("worklogs"))));for(String id:expectedIds){JSONObject row=find(reopened,id);context(test,row,ids);test.yes(PhotoAttachmentStore.sha256(reopened.photoAttachmentStore().read(reopened.photoAttachment(id))).equals(originalHashes.get(id)));}test.yes(reopened.savePhoto(copy(repeated)).equals("qa_revier_photo_00")&&attachments(reopened)==26);clock.minutes(5);JSONObject stillRunning=reopened.workSessions().current(ids.session);test.yes(stillRunning.getString("state").equals("RUNNING")&&stillRunning.getLong("worked_ms")==30*60000L&&stillRunning.getString("revision_hash").equals(timerRevision));
                }
            }
        });
        test.probe("Revier native attachment commit abort preserves earlier originals timer and exact action retry creates one photo",()->{
            Clock clock=new Clock();try(OwnerNativeAcceptance.Fixture fixture=new OwnerNativeAcceptance.Fixture(test.getTargetContext(),clock)){
                ContextIds ids=start(fixture,"abort");byte[] original=png(71);JSONObject old=photo("qa_revier_photo_before_abort","qa_revier_photo_before_abort_action",original,"before",ids,clock);fixture.store.savePhoto(copy(old));String oldSha=PhotoAttachmentStore.sha256(original),timer=fixture.store.workSessions().current(ids.session).getString("revision_hash"),photos=PixelMcpWriteTools.hash(PixelMcpReadTools.effective(fixture.store.getState()).getJSONArray("photos"));int pending=fixture.store.pendingActions().length();
                clock.minutes(2);JSONObject input=photo("qa_revier_photo_aborted","qa_revier_photo_aborted_action",png(72),"after",ids,clock);fixture.store.getWritableDatabase().execSQL("CREATE TEMP TRIGGER qa_revier_photo_attachment_abort BEFORE INSERT ON photo_attachments WHEN NEW.photo_id='qa_revier_photo_aborted' BEGIN SELECT RAISE(ABORT,'qa synthetic attachment commit abort'); END");boolean aborted=false;try{fixture.store.savePhoto(copy(input));}catch(SQLiteException expected){aborted=true;}finally{fixture.store.getWritableDatabase().execSQL("DROP TRIGGER qa_revier_photo_attachment_abort");}test.yes(aborted);
                test.yes(attachments(fixture.store)==1&&fixture.store.photoAttachmentStore().storageSummary().getInt("local_files")==1&&fixture.store.photoAttachment("qa_revier_photo_aborted")==null&&fixture.store.ownerValue("photo-ui:qa_revier_photo_aborted_action","").isEmpty()&&fixture.store.pendingActions().length()==pending&&photos.equals(PixelMcpWriteTools.hash(PixelMcpReadTools.effective(fixture.store.getState()).getJSONArray("photos")))) ;test.yes(PhotoAttachmentStore.sha256(fixture.store.photoAttachmentStore().read(fixture.store.photoAttachment("qa_revier_photo_before_abort"))).equals(oldSha));JSONObject running=fixture.store.workSessions().current(ids.session);test.yes(running.getString("state").equals("RUNNING")&&running.getLong("worked_ms")==2*60000L&&running.getString("revision_hash").equals(timer));
                test.yes(fixture.store.savePhoto(copy(input)).equals("qa_revier_photo_aborted")&&fixture.store.savePhoto(copy(input)).equals("qa_revier_photo_aborted")&&attachments(fixture.store)==2&&fixture.store.pendingActions().length()==pending+1);context(test,find(fixture.store,"qa_revier_photo_aborted"),ids);fixture.store.close();clock.minutes(1);
                try(PixelLocalStore reopened=new PixelLocalStore(fixture.context,clock)){test.yes(reopened.savePhoto(copy(input)).equals("qa_revier_photo_aborted")&&attachments(reopened)==2&&reopened.pendingActions().length()==pending+1);test.yes(PhotoAttachmentStore.sha256(reopened.photoAttachmentStore().read(reopened.photoAttachment("qa_revier_photo_before_abort"))).equals(oldSha));JSONObject persisted=reopened.workSessions().current(ids.session);test.yes(persisted.getString("state").equals("RUNNING")&&persisted.getLong("worked_ms")==3*60000L&&persisted.getString("revision_hash").equals(timer));}
            }
        });
    }
    private RevierPhotoNativeAcceptance(){}
}
