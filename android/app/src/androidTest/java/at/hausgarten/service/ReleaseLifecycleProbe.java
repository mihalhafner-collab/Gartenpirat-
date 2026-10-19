package at.hausgarten.service;
import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import org.json.*;

/** Original signed package lifecycle on the dedicated emulator ONLY. */
final class ReleaseLifecycleProbe {
    static String js(Instrumentation runner,android.webkit.WebView view,String script)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] result={null};
        runner.runOnMainSync(()->view.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));
        if(!done.await(20,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("WebView callback timed out");return result[0];
    }
    static void ui(Instrumentation runner,Context context)throws Exception{
        android.app.Activity activity=runner.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("webView");field.setAccessible(true);android.webkit.WebView view=(android.webkit.WebView)field.get(activity);
        long deadline=SystemClock.elapsedRealtime()+120000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<deadline){if("true".equals(js(runner,view,"typeof WorklogArchiveUI==='object'&&typeof PhotoLibraryUI==='object'&&typeof openPage==='function'&&!!document.querySelector('[data-open-garden-menu]')"))){ready=true;break;}SystemClock.sleep(250);}
        require(ready);
        require("true".equals(js(runner,view,"(()=>{const nav=[...document.querySelectorAll('.bottom-inner [data-open]')].map(x=>x.dataset.open);return nav.length===4&&nav.includes('more-page')&&typeof crypto.randomUUID==='function'&&[1,2].at(-1)===2&&Object.hasOwn({a:1},'a')&&'a-a'.replaceAll('-','')==='aa';})()")));
        require("true".equals(js(runner,view,"(()=>{openPage('worklogs');document.querySelector('[data-select-start]').click();document.querySelector('[data-select-visible]').click();const n=document.querySelectorAll('#worklog-list .worklog-selected').length;document.querySelector('[data-select-cancel]').click();return n>0&&document.querySelectorAll('#worklog-list .worklog-entry[aria-pressed]').length===0&&currentPage==='worklogs';})()")));
        require("true".equals(js(runner,view,"(()=>{openPage('more-page');document.querySelector('[data-open-garden-menu]').click();return document.querySelector('#gp-dialog').open;})()")));
        require("true".equals(js(runner,view,"(()=>{Gartenpirat.close();openPage('home');return !!document.querySelector('#workday-timer.has-active-session');})()")));
        require("true".equals(js(runner,view,"document.getElementById('gp-launcher').hidden")));
        runner.waitForIdleSync();SystemClock.sleep(1000);
        try(android.os.ParcelFileDescriptor screenshot=runner.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/gartenpirat-cloud-ui.png");java.io.InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(screenshot)){byte[] buffer=new byte[1024];while(stream.read(buffer)!=-1){}}
        require("true".equals(js(runner,view,"(()=>{openCustomer('qa_site');document.querySelector('[data-customer-tab=\"garden\"]').click();return currentPage==='customer-profile-page'&&document.querySelector('[data-customer-tab=\"garden\"]').getAttribute('aria-pressed')==='true';})()")));
        require("true".equals(js(runner,view,"(()=>{TrashUI.open();return currentPage==='trash-page'&&document.getElementById('trash-content').innerText.length>0&&!!document.getElementById('trash-empty');})()")));
        require("true".equals(js(runner,view,"(()=>{openPage('more-page');return document.querySelectorAll('#more-page [data-more-group]').length===4&&document.querySelectorAll('#more-page [data-open-trash]').length===1;})()")));
        require("true".equals(js(runner,view,"(()=>{PlanningUI.openForOrder('qa_order');return currentPage==='planning-page'&&document.querySelector('#appointment-form [name=customer_id]').value==='qa_site'&&document.querySelector('#appointment-form [name=order_id]').value==='qa_order'&&!!document.getElementById('planning-discard');})()")));
        require("true".equals(js(runner,view,"(()=>{document.getElementById('planning-back').click();return !!document.querySelector('[data-planning-resume]')&&!!document.querySelector('#planning-page [data-open=weeks]');})()")));
    }
    static void require(boolean value){if(!value)throw new AssertionError("Lifecycle condition failed");}
    static String fingerprint(MainActivity.LocalStore store)throws Exception{
        JSONObject state=store.getState();
        JSONObject master=SyntheticBusinessData.object(state.getJSONObject("master").toString());
        // Only fields introduced AFTER the recorded source release may be absent in its baseline.
        int baseline=Integer.parseInt(store.ownerValue("release_lifecycle_baseline_version","74"));JSONObject capabilities=master.optJSONObject("capabilities");
        if(capabilities!=null){if(baseline<78)capabilities.remove("offer_workflow_v2");if(baseline<75)capabilities.remove("trash_empty_v1");if(baseline<76)capabilities.remove("appointments_v1");}
        if(baseline<76&&master.optJSONArray("appointments")!=null&&master.getJSONArray("appointments").length()==0)master.remove("appointments");
        return PixelMcpWriteTools.hash(SyntheticBusinessData.object().put("master",master).put("pending",state.getJSONArray("pending")));
    }
    static void run(Instrumentation runner,Context context,String mode){
        Bundle report=new Bundle();MainActivity.LocalStore store=null;
        try{
            require(Build.FINGERPRINT.startsWith("google/sdk_gphone")||Build.FINGERPRINT.contains("generic")||Build.MODEL.startsWith("sdk_"));
            SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            store=new MainActivity.LocalStore(context);
            if(mode.equals("ui")){
                ui(runner,context);report.putString("stream","PASS native WebView boot, compatible JavaScript, complete navigation, work selection/cancel, customer garden and trash on isolated emulator\n");
            }else if(mode.equals("fresh")){
                JSONObject state=store.getState(),master=state.getJSONObject("master");
                for(String key:new String[]{"customers","orders","worklogs","invoices","photos"})require(master.getJSONArray(key).length()==0);
                require(state.getJSONArray("pending").length()==0);
                PixelTunnelConfig config=new PixelTunnelConfig(context);
                require(config.id().isEmpty()&&config.organization().isEmpty()&&!config.configured()&&!config.enabled()&&!config.writeEnabled());
                require(prefs.getString("drive_tree_uri","").isEmpty());
                report.putString("stream","PASS fresh empty business data, no credentials and no automatic Drive/tunnel access\n");
            }else if(mode.equals("prepare")){
                require(prefs.getString("drive_snapshot","").isEmpty());
                JSONObject master=SyntheticBusinessData.object("{\"customers\":[{\"id\":\"qa_direct\",\"name\":\"QA Direkt\"},{\"id\":\"qa_payer\",\"name\":\"QA Auftraggeber\"},{\"id\":\"qa_site\",\"name\":\"QA Einsatzort\",\"billing_mode\":\"via_customer\",\"default_billing_customer_id\":\"qa_payer\"}],\"orders\":[{\"id\":\"qa_order\",\"customer_id\":\"qa_site\",\"title\":\"QA Pflege\"}],\"worklogs\":[{\"id\":\"qa_legacy\",\"customer_id\":\"qa_direct\",\"work_date\":\"2026-10-13\",\"activity\":\"QA Bestand\",\"hours\":2},{\"id\":\"qa_billed_site\",\"customer_id\":\"qa_site\",\"billing_customer_id\":\"qa_payer\",\"work_date\":\"2026-10-13\",\"activity\":\"QA Einsatz\",\"hours\":3}],\"photos\":[],\"invoices\":[],\"accepted_ids\":[],\"sync_errors\":[],\"capabilities\":{\"worklog_compare_and_save\":true,\"entity_compare_and_save\":true}}");
                require(prefs.edit().putString("drive_snapshot",master.toString()).putString("qa_setting","keep-this").commit());
                require(context.getSharedPreferences("pixel_tunnel_private",0).edit().putString("id",(""+("tunnel_"+"a".repeat(32))+"")).putString("organization","org-QAOnly1234").commit());
                PixelTunnelConfig configured=new PixelTunnelConfig(context);
                configured.save((""+("tunnel_"+"a".repeat(32))+""),"org-QAOnly1234","qa-runtime-not-a-valid-api-key-00000000");
                configured.writeEnabled(true);configured.enabled(false); // No external connection.
                store.saveWorklog(SyntheticBusinessData.object().put("id","qa_offline").put("customer_id","qa_site").put("billing_customer_id","qa_payer").put("work_date","2026-10-13").put("activity","QA offline").put("hours",1));
                WorkSessionStore timer=new WorkSessionStore(store.getWritableDatabase(),context);
                timer.start(SyntheticBusinessData.object().put("request_id","qa-upgrade-start").put("customer_id","qa_site").put("billing_customer_id","qa_payer").put("work_date","2026-10-13"));
                store.putOwnerValue("release_lifecycle_baseline_version",Integer.toString(context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode));
                require(prefs.edit().putString("qa_release_baseline_hash",fingerprint(store)).commit());
                report.putString("stream","PASS synthetic upgrade fixture prepared on previous original version\n");
            }else if(mode.equals("verify")){
                require(fingerprint(store).equals(prefs.getString("qa_release_baseline_hash","")));
                require(prefs.getString("qa_setting","").equals("keep-this"));
                PixelTunnelConfig c=new PixelTunnelConfig(context);
                require(c.id().equals((""+("tunnel_"+"a".repeat(32))+""))&&c.organization().equals("org-QAOnly1234")&&c.configured()&&!c.enabled()&&c.writeEnabled());
                require(c.secret().equals("qa-runtime-not-a-valid-api-key-00000000"));
                JSONObject session=new WorkSessionStore(store.getWritableDatabase(),context).current();
                require(session.getString("customer_id").equals("qa_site")&&session.getString("billing_customer_id").equals("qa_payer"));
                JSONObject merged=PixelMcpReadTools.effective(store.getState());
                require(merged.getJSONArray("customers").length()==3&&merged.getJSONArray("worklogs").length()==3);
                require(BillingRules.recipient(PixelMcpWriteTools.find(merged.getJSONArray("worklogs"),"qa_legacy")).equals("qa_direct"));
                require(BillingRules.recipient(PixelMcpWriteTools.find(merged.getJSONArray("worklogs"),"qa_offline")).equals("qa_payer"));
                report.putString("stream","PASS in-place update preserves complete snapshot, IDs, pending records, settings, tunnel configuration and active timer recipient\n");
            }else throw new IllegalArgumentException("Unknown lifecycle mode");
            runner.finish(-1,report);
        }catch(Throwable failure){report.putString("stream","FAIL lifecycle "+mode+" ["+failure.getClass().getSimpleName()+"] "+(failure.getStackTrace().length>0?failure.getStackTrace()[0].toString():"no frame")+"\n");runner.finish(0,report);}
        finally{if(store!=null)store.close();}
    }
}
