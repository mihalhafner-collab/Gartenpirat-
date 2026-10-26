package at.hausgarten.service;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.webkit.WebView;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bundled public assets and real WebView only; no business commands or network. */
final class PlantLibraryAcceptance {
    static void run(PixelMcpInstrumentation t) throws Exception {
        t.probe("plant98 all314 bundled profiles and628 actual Android bitmap decodes", () -> {
            JSONObject catalog;
            try (InputStream in=t.getTargetContext().getAssets().open("plant-catalog.json")) {
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);
                catalog=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
            JSONArray plants=catalog.getJSONArray("plants"); t.yes(plants.length()==314);
            Set<String> ids=new HashSet<>(), files=new HashSet<>();
            for(int n=0;n<plants.length();n++) {
                JSONObject p=plants.getJSONObject(n); t.yes(ids.add(p.getString("id")));
                JSONArray images=p.getJSONArray("images"); t.yes(images.length()==2);
                for(int k=0;k<images.length();k++) {
                    JSONObject image=images.getJSONObject(k);String path=image.getString("path");
                    t.yes(files.add(path)&&path.startsWith("plant-images/")&&!path.contains(".."));
                    t.yes(!image.getString("author").isEmpty()&&image.getString("licenseUrl").startsWith("https://"));
                    try(InputStream in=t.getTargetContext().getAssets().open(path)) {
                        Bitmap bitmap=BitmapFactory.decodeStream(in);
                        t.yes(bitmap!=null&&bitmap.getWidth()>=120&&bitmap.getHeight()>=120);
                        bitmap.recycle();
                    }
                }
            }
            t.yes(files.size()==628&&ids.contains("cymbopogon-citratus"));
        });
        t.probe("plant98 actual native catalogue queries retain aliases and future EU date", () -> {
            try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(t.getTargetContext())) {
                JSONObject old=f.call("search_catalog",new JSONObject().put("query","Pennisetum alopecuroides"));
                JSONObject accepted=f.call("search_catalog",new JSONObject().put("query","Cenchrus alopecuroides"));
                t.yes(old.getJSONArray("entries").getJSONObject(0).getString("id").equals(accepted.getJSONArray("entries").getJSONObject(0).getString("id")));
                JSONObject p=f.call("get_catalog_entry",new JSONObject().put("entry_id","celastrus-orbiculatus"));
                t.yes(p.getJSONObject("regionalRestrictions").getJSONObject("EU").getString("effectiveDate").equals("2027-08-02"));
                t.yes(p.getJSONObject("regionalRestrictions").getJSONObject("CH").getString("annex").equals("2.1"));
            }
        });
        runUi(t);
    }

    static void runUi(PixelMcpInstrumentation t) throws Exception {
        t.probe("plant98 real WebView navigation search offline images fullscreen and draft preservation", () -> ui(t));
    }

    static void capture(PixelMcpInstrumentation t,WebView view,String stage) throws Exception {
        ReleaseLifecycleProbe.js(t,view,"(()=>{window.qaPlant98Paint=false;requestAnimationFrame(()=>requestAnimationFrame(()=>qaPlant98Paint=true));return true;})()");
        long deadline=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<deadline&&!"true".equals(ReleaseLifecycleProbe.js(t,view,"window.qaPlant98Paint===true")))SystemClock.sleep(100);
        java.util.concurrent.CountDownLatch drawn=new java.util.concurrent.CountDownLatch(1);
        t.runOnMainSync(()->view.postVisualStateCallback(98,new WebView.VisualStateCallback(){public void onComplete(long id){drawn.countDown();}}));
        t.yes(drawn.await(30,java.util.concurrent.TimeUnit.SECONDS));
        t.waitForIdleSync();SystemClock.sleep(1000);
        android.os.Bundle geometry=new android.os.Bundle();
        geometry.putString("stream","LAYOUT "+stage+" "+ReleaseLifecycleProbe.js(t,view,"JSON.stringify({viewport:[innerWidth,innerHeight],open:document.querySelector('#gp-dialog').open,nodes:['#gp-dialog','.gp-content','#gp-full-image','.gp-image-stage','.gp-image-toolbar','#gp-image-caption','.gp-portrait h3'].map(s=>{const e=document.querySelector(s);if(!e)return {selector:s,missing:true};const r=e.getBoundingClientRect();return {selector:s,x:r.x,y:r.y,w:r.width,h:r.height,scrollTop:e.scrollTop,naturalWidth:e.naturalWidth||0,display:getComputedStyle(e).display};})})")+"\n");
        t.sendStatus(0,geometry);
        if(stage.equals("fullscreen"))yes(t,view,"(()=>{const i=document.querySelector('#gp-full-image'),r=i.getBoundingClientRect(),bar=document.querySelector('.gp-image-toolbar').getBoundingClientRect(),caption=document.querySelector('#gp-image-caption').getBoundingClientRect(),help=document.querySelector('.gp-image-help').getBoundingClientRect();return i.complete&&i.naturalWidth>0&&r.width>100&&r.height>100&&r.bottom>100&&r.top<innerHeight-100&&bar.height>=40&&caption.bottom+4<=help.top&&help.bottom<=innerHeight+1;})()");
        else yes(t,view,"(()=>{const d=document.querySelector('#gp-dialog'),r=document.querySelector('.gp-portrait h3').getBoundingClientRect(),title=document.querySelector('#gp-title'),close=document.querySelector('.gp-close').getBoundingClientRect();return d.open&&r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight&&title.scrollWidth<=title.clientWidth+1&&title.getBoundingClientRect().right<=close.left;})()");
        String scale=Float.toString(t.getTargetContext().getResources().getConfiguration().fontScale);
        try(android.os.ParcelFileDescriptor descriptor=t.getUiAutomation().executeShellCommand(
                "screencap -p /data/local/tmp/qa-plant98-"+scale+"-"+stage+".png");
                InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer=new byte[8192];while(stream.read(buffer)!=-1){}
        }
    }

    static void yes(PixelMcpInstrumentation t,WebView view,String expression)throws Exception {
        yes(t,view,"expression",expression);
    }
    static void yes(PixelMcpInstrumentation t,WebView view,String phase,String expression)throws Exception {
        String observed=ReleaseLifecycleProbe.js(t,view,"(()=>{delete window.qaPlant98LastException;try{return ("+expression+");}catch(error){const frame=String(error?.stack||'').match(/([a-z-]+\\.js):(\\d+):(\\d+)/);window.qaPlant98LastException={name:['Error','TypeError','ReferenceError','SyntaxError','RangeError'].includes(error?.name)?error.name:'other',frame:frame?frame[0]:'none'};return false;}})()");
        if(!"true".equals(observed)){
            String state=ReleaseLifecycleProbe.js(t,view,"JSON.stringify((()=>{const state={page:typeof currentPage==='string'&&/^[a-z-]+$/.test(currentPage)?currentPage:'unknown',fieldReady:document.body.dataset.fieldReady==='true',revierReady:document.body.dataset.revierReady==='true',gardenButtons:document.querySelectorAll('[data-open-garden-menu]').length,moreActive:!!document.querySelector('#more-page.active'),openDialogs:document.querySelectorAll('dialog[open]').length,plantDialogOpen:!!document.querySelector('#gp-dialog')?.open,queryPresent:!!document.querySelector('#gp-query'),cards:document.querySelectorAll('.gp-plant-card').length,galleryImages:document.querySelectorAll('.gp-gallery img').length,decodedImages:[...document.querySelectorAll('.gp-gallery img')].filter(image=>image.complete&&image.naturalWidth>0).length,imageMode:!!document.querySelector('#gp-dialog.gp-image-mode'),legacyPlanningPresent:localStorage.getItem('hausgarten.planning.draft.v1')!==null,expectedDraftPresent:typeof window.qaPlant98ExpectedDraft==='string',exception:window.qaPlant98LastException||null};try{const native=JSON.parse(AndroidData.getBrowserDraftState(JSON.stringify({formatVersion:1,action:'list'})));state.nativeAck=native.ok===true;state.nativeStates=Array.isArray(native.data?.states)?native.data.states.length:null;state.nativeReviewRequired=native.data?.review_required??null;}catch(_){state.nativeAck=false;}return state;})())");
            android.os.Bundle report=new android.os.Bundle();report.putString("stream","QA_PLANT_CHECK "+phase+" expression_sha256="+PixelMcpWriteTools.hash(expression)+" result="+("false".equals(observed)?"false":"null".equals(observed)?"null":"other")+" state="+state+"\n");t.sendStatus(0,report);
        }
        t.yes("true".equals(observed));
    }
    private static String draftPreserved(){return "(()=>{const row=qaPlant98NativeRead('get',HGFormDrafts.key),saved=JSON.parse(row.value).entries[qaPlant98DraftId];return row.present&&row.record_hash===qaPlant98ExpectedRecordHash&&HGFormDrafts.canonical(row.context)===qaPlant98ExpectedContext&&HGFormDrafts.canonical(saved)===qaPlant98ExpectedDraft&&HGFormDrafts.canonical(HGFormDrafts.list('appointment').find(edit=>edit.session_id===qaPlant98DraftId))===qaPlant98ExpectedDraft&&['hausgarten.planning.draft.v1','hausgarten.planning.draft.v1.others'].every(key=>localStorage.getItem(key)===qaPlant98BeforeRaw[key]);})()";}
    private static String prepareDraft(){return "(()=>{"
        +"window.qaPlant98NativeRead=(action,key)=>{const args={formatVersion:1,action};if(key)args.key=key;const result=JSON.parse(AndroidData.getBrowserDraftState(JSON.stringify(args)));if(!result.ok||result.data?.formatVersion!==1||result.data.review_required||!/^[0-9a-f]{64}$/.test(result.data.record_hash||'')||!result.data.context?.installationId||!result.data.context?.restoreEpoch)throw Error('QA_PLANT_NATIVE_READ_NOT_ACKNOWLEDGED');return result.data;};"
        +"HGFormDrafts.list();window.qaPlant98BeforeNative=HGOwnerClient.clone(qaPlant98NativeRead('list'));window.qaPlant98BeforeRaw=Object.fromEntries(['hausgarten.planning.draft.v1','hausgarten.planning.draft.v1.others','hausgarten.fullbackup.formdrafts.v1'].map(key=>[key,localStorage.getItem(key)]));"
        +"if(!customers().some(row=>row.id==='qa_site')||!orders().some(row=>row.id==='qa_order'&&row.customer_id==='qa_site'))throw Error('QA_PLANT_SYNTHETIC_CONTEXT_MISSING');"
        +"const baseline={data:{id:'',title:'',customer_id:'qa_site',order_id:'qa_order',purpose:'work',timezone:'Europe/Vienna',status:'tentative',weather_profile:'general',notes:'',location:'',travel_minutes:0,break_minutes:0,latitude:null,longitude:null},localStart:'2026-10-23T08:00',duration:null,endOpen:true,utcOffset:'',occurrences:1,intervalWeeks:1};"
        +"const edit=HGFormDrafts.start('appointment',baseline,{workflow:'planning'});edit.session_id='qa_plant98_'+crypto.randomUUID();window.qaPlant98DraftId=edit.session_id;if(HGFormDrafts.list().some(row=>row.session_id===edit.session_id)||HGFormDrafts.removed(edit.session_id))throw Error('QA_PLANT_DRAFT_ID_COLLISION');edit.values.data.title='QA Pflanzenprüfung';Object.assign(edit,HGOwnerClient.clone(edit.values));HGFormDrafts.put(edit);"
        +"const row=qaPlant98NativeRead('get',HGFormDrafts.key);window.qaPlant98ExpectedDraft=HGFormDrafts.canonical(edit);window.qaPlant98ExpectedContext=HGFormDrafts.canonical(row.context);window.qaPlant98ExpectedRecordHash=row.record_hash;return row.initialized&&row.present&&edit.dirty&&HGFormDrafts.canonical(JSON.parse(row.value).entries[edit.session_id])===qaPlant98ExpectedDraft;})()";}
    private static String cleanupDraft(){return "(()=>{Gartenpirat.close();if(!window.qaPlant98BeforeNative)return true;"
        +"if(window.qaPlant98DraftId){const own=HGFormDrafts.list('appointment').find(edit=>edit.session_id===qaPlant98DraftId);if(own){if(own.pending)throw Error('QA_PLANT_UNEXPECTED_PENDING_ACTION');HGFormDrafts.remove(own,{discard:true});}if(HGFormDrafts.list().some(edit=>edit.session_id===qaPlant98DraftId)||!HGFormDrafts.removed(qaPlant98DraftId))return false;}"
        +"const after=qaPlant98NativeRead('list'),canonical=value=>{if(value==null)return value;try{const parsed=JSON.parse(value);if(parsed?.formatVersion===1&&parsed.entries&&parsed.removed&&window.qaPlant98DraftId){delete parsed.entries[qaPlant98DraftId];delete parsed.removed[qaPlant98DraftId];}return HGFormDrafts.canonical(parsed);}catch(_){return value;}};"
        +"if(HGFormDrafts.canonical(after.context)!==HGFormDrafts.canonical(qaPlant98BeforeNative.context))return false;for(const before of qaPlant98BeforeNative.states){const actual=after.states.find(row=>row.key===before.key);if(!actual)return false;if(before.key===HGFormDrafts.key){const empty=JSON.stringify({formatVersion:1,entries:{},removed:{}});if(canonical(actual.present?actual.value:empty)!==canonical(before.present?before.value:empty))return false;}else if(actual.present!==before.present||canonical(actual.value)!==canonical(before.value))return false;}"
        +"for(const key of ['hausgarten.planning.draft.v1','hausgarten.planning.draft.v1.others'])if(localStorage.getItem(key)!==qaPlant98BeforeRaw[key])return false;if(canonical(localStorage.getItem('hausgarten.fullbackup.formdrafts.v1'))!==canonical(qaPlant98BeforeRaw['hausgarten.fullbackup.formdrafts.v1']))return false;const formState=after.states.find(row=>row.key===HGFormDrafts.key);if(formState?.present&&localStorage.getItem(HGFormDrafts.key)!==formState.value)return false;"
        +"delete window.qaPlant98Images;delete window.qaPlant98Paint;delete window.qaPlant98ExpectedDraft;delete window.qaPlant98ExpectedRecordHash;delete window.qaPlant98ExpectedContext;delete window.qaPlant98DraftId;delete window.qaPlant98BeforeNative;delete window.qaPlant98BeforeRaw;delete window.qaPlant98NativeRead;delete window.qaPlant98LastException;return true;})()";}
    static void ui(PixelMcpInstrumentation t)throws Exception {
        Activity activity=t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("webView");field.setAccessible(true);
        WebView view=(WebView)field.get(activity);
        long deadline=SystemClock.elapsedRealtime()+120000;
        String ready="document.body.dataset.fieldReady==='true'&&document.body.dataset.revierReady==='true'&&typeof Gartenpirat==='object'&&typeof HGFormDrafts==='object'&&typeof HGDraftStorage==='object'&&!!document.querySelector('[data-open-garden-menu]')";
        while(SystemClock.elapsedRealtime()<deadline&&!"true".equals(ReleaseLifecycleProbe.js(t,view,ready)))SystemClock.sleep(250);
        yes(t,view,"startup_ready",ready);
        // Repair only this probe's exact obsolete marker on the dedicated synthetic AVD.
        // Arbitrary malformed drafts remain untouched and fail closed in the product.
        yes(t,view,"obsolete_marker_cleanup","(()=>{for(const key of ['hausgarten.planning.draft.v1','hausgarten.planning.draft.v1.others'])if(localStorage.getItem(key)==='qa_plant98_preserved_draft')localStorage.removeItem(key);return true;})()");
        try {
            yes(t,view,"more_navigation","(()=>{openPage('more-page');return currentPage==='more-page'&&!!document.querySelector('#more-page.active [data-open-garden-menu]');})()");
            t.waitForIdleSync();
            yes(t,view,"durable_planning_draft_ack",prepareDraft());
            yes(t,view,"open_lexicon","(()=>{document.querySelector('[data-open-garden-menu]').click();document.querySelector('[data-gp-action=lexicon]').click();return document.querySelector('#gp-dialog').open&&GartenpiratCatalog.plants.length===314;})()");
            yes(t,view,"draft_after_menu",draftPreserved());
            for(String query:new String[]{"Pennisetum alopecuroides","Cenchrus alopecuroides","Coreopsis verticillata","Anacis verticillata","Cymbopogon citratus"}) {
                yes(t,view,"search_"+PixelMcpWriteTools.hash(query).substring(0,12),"(()=>{const q=document.querySelector('#gp-query');q.value="+JSONObject.quote(query)+";q.dispatchEvent(new Event('input',{bubbles:true}));return document.querySelectorAll('.gp-plant-card').length===1;})()");
            }
            yes(t,view,"draft_after_search",draftPreserved());
            yes(t,view,"open_portrait","(()=>{document.querySelector('.gp-plant-card').click();window.qaPlant98Images='WAIT';Promise.all([...document.querySelectorAll('.gp-gallery img')].map(i=>i.decode())).then(()=>qaPlant98Images='PASS',()=>qaPlant98Images='FAIL');return document.querySelectorAll('.gp-gallery img').length===2;})()");
            deadline=SystemClock.elapsedRealtime()+30000;
            while(SystemClock.elapsedRealtime()<deadline&&!"true".equals(ReleaseLifecycleProbe.js(t,view,"window.qaPlant98Images==='PASS'")))SystemClock.sleep(250);
            yes(t,view,"images_decoded","window.qaPlant98Images==='PASS'&&[...document.querySelectorAll('.gp-gallery img')].every(i=>i.complete&&i.naturalWidth>0)");
            capture(t,view,"portrait");
            yes(t,view,"draft_after_portrait",draftPreserved());
            yes(t,view,"open_fullscreen","(()=>{document.querySelector('[data-gp-image=\"0\"]').click();return document.querySelector('#gp-dialog').classList.contains('gp-image-mode')&&document.querySelector('#gp-image-count').innerText==='Bild 1 von 2'&&document.querySelector('#gp-image-caption').innerText.length>10;})()");
            yes(t,view,"next_image","(()=>{document.querySelector('[data-gp-action=image-next]').click();return document.querySelector('#gp-image-count').innerText==='Bild 2 von 2';})()");
            capture(t,view,"fullscreen");
            yes(t,view,"draft_after_fullscreen",draftPreserved());
            yes(t,view,"back_and_draft_preserved","(()=>{handleAndroidBack();return !document.querySelector('#gp-dialog').classList.contains('gp-image-mode')&&document.querySelector('.gp-portrait h3').innerText.length>0&&document.documentElement.scrollWidth<=innerWidth+1;})()");
            yes(t,view,"draft_after_back",draftPreserved());
            capture(t,view,"back");
        } finally {
            try{yes(t,view,"discard_owned_draft_preserve_prior_native_state",cleanupDraft());}
            finally{t.runOnMainSync(activity::finish);t.waitForIdleSync();}
        }
    }
}
