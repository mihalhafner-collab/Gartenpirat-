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
        if(stage.equals("fullscreen"))yes(t,view,"(()=>{const i=document.querySelector('#gp-full-image'),r=i.getBoundingClientRect(),bar=document.querySelector('.gp-image-toolbar').getBoundingClientRect();return i.complete&&i.naturalWidth>0&&r.width>100&&r.height>100&&r.bottom>100&&r.top<innerHeight-100&&bar.height>=40;})()");
        else yes(t,view,"(()=>{const d=document.querySelector('#gp-dialog'),r=document.querySelector('.gp-portrait h3').getBoundingClientRect();return d.open&&r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight;})()");
        String scale=Float.toString(t.getTargetContext().getResources().getConfiguration().fontScale);
        try(android.os.ParcelFileDescriptor descriptor=t.getUiAutomation().executeShellCommand(
                "screencap -p /data/local/tmp/qa-plant98-"+scale+"-"+stage+".png");
                InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer=new byte[8192];while(stream.read(buffer)!=-1){}
        }
    }

    static void yes(PixelMcpInstrumentation t,WebView view,String expression)throws Exception {
        t.yes("true".equals(ReleaseLifecycleProbe.js(t,view,expression)));
    }
    static void ui(PixelMcpInstrumentation t)throws Exception {
        Activity activity=t.startActivitySync(new Intent(t.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("webView");field.setAccessible(true);
        WebView view=(WebView)field.get(activity);
        long deadline=SystemClock.elapsedRealtime()+120000;
        while(SystemClock.elapsedRealtime()<deadline&&!"true".equals(ReleaseLifecycleProbe.js(t,view,"document.body.dataset.fieldReady==='true'&&typeof Gartenpirat==='object'")))SystemClock.sleep(250);
        String previous=ReleaseLifecycleProbe.js(t,view,"localStorage.getItem('hausgarten.planning.draft.v1')");
        try {
            yes(t,view,"(()=>{localStorage.setItem('hausgarten.planning.draft.v1','qa_plant98_preserved_draft');openPage('more-page');document.querySelector('[data-open-garden-menu]').click();document.querySelector('[data-gp-action=lexicon]').click();return document.querySelector('#gp-dialog').open&&GartenpiratCatalog.plants.length===314;})()");
            for(String query:new String[]{"Pennisetum alopecuroides","Cenchrus alopecuroides","Coreopsis verticillata","Anacis verticillata","Cymbopogon citratus"}) {
                yes(t,view,"(()=>{const q=document.querySelector('#gp-query');q.value="+JSONObject.quote(query)+";q.dispatchEvent(new Event('input',{bubbles:true}));return document.querySelectorAll('.gp-plant-card').length===1;})()");
            }
            yes(t,view,"(()=>{document.querySelector('.gp-plant-card').click();window.qaPlant98Images='WAIT';Promise.all([...document.querySelectorAll('.gp-gallery img')].map(i=>i.decode())).then(()=>qaPlant98Images='PASS',()=>qaPlant98Images='FAIL');return document.querySelectorAll('.gp-gallery img').length===2;})()");
            deadline=SystemClock.elapsedRealtime()+30000;
            while(SystemClock.elapsedRealtime()<deadline&&!"true".equals(ReleaseLifecycleProbe.js(t,view,"window.qaPlant98Images==='PASS'")))SystemClock.sleep(250);
            yes(t,view,"window.qaPlant98Images==='PASS'&&[...document.querySelectorAll('.gp-gallery img')].every(i=>i.complete&&i.naturalWidth>0)");
            capture(t,view,"portrait");
            yes(t,view,"(()=>{document.querySelector('[data-gp-image=\"0\"]').click();return document.querySelector('#gp-dialog').classList.contains('gp-image-mode')&&document.querySelector('#gp-image-count').innerText==='Bild 1 von 2'&&document.querySelector('#gp-image-caption').innerText.length>10;})()");
            yes(t,view,"(()=>{document.querySelector('[data-gp-action=image-next]').click();return document.querySelector('#gp-image-count').innerText==='Bild 2 von 2';})()");
            capture(t,view,"fullscreen");
            yes(t,view,"(()=>{handleAndroidBack();return !document.querySelector('#gp-dialog').classList.contains('gp-image-mode')&&document.querySelector('.gp-portrait h3').innerText.length>0&&document.documentElement.scrollWidth<=innerWidth+1&&localStorage.getItem('hausgarten.planning.draft.v1')==='qa_plant98_preserved_draft';})()");
            capture(t,view,"back");
        } finally {
            ReleaseLifecycleProbe.js(t,view,"(()=>{Gartenpirat.close();const previous="+previous+";if(previous===null)localStorage.removeItem('hausgarten.planning.draft.v1');else localStorage.setItem('hausgarten.planning.draft.v1',previous);delete window.qaPlant98Images;return true;})()");
            t.runOnMainSync(activity::finish);t.waitForIdleSync();
        }
    }
}
