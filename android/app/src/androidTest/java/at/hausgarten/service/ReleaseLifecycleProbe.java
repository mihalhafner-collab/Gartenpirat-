package at.hausgarten.service;
import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import org.json.*;

/** Original signed package lifecycle on the dedicated emulator ONLY. */
final class ReleaseLifecycleProbe {
    static void require(boolean value){if(!value)throw new AssertionError("Lifecycle condition failed");}
    static String fingerprint(MainActivity.LocalStore store)throws Exception{
        JSONObject state=store.getState();
        return PixelMcpWriteTools.hash(new JSONObject().put("master",state.getJSONObject("master")).put("pending",state.getJSONArray("pending")));
    }
    static void run(Instrumentation runner,Context context,String mode){
        Bundle report=new Bundle();MainActivity.LocalStore store=null;
        try{
            require(Build.FINGERPRINT.startsWith("google/sdk_gphone")||Build.FINGERPRINT.contains("generic")||Build.MODEL.startsWith("sdk_"));
            SharedPreferences prefs=context.getSharedPreferences("haus_garten",0);
            store=new MainActivity.LocalStore(context);
            if(mode.equals("fresh")){
                JSONObject state=store.getState(),master=state.getJSONObject("master");
                for(String key:new String[]{"customers","orders","worklogs","invoices","photos"})require(master.getJSONArray(key).length()==0);
                require(state.getJSONArray("pending").length()==0);
                PixelTunnelConfig config=new PixelTunnelConfig(context);
                require(config.id().isEmpty()&&config.organization().isEmpty()&&!config.configured()&&!config.enabled()&&!config.writeEnabled());
                require(prefs.getString("drive_tree_uri","").isEmpty());
                report.putString("stream","PASS fresh empty business data, no credentials and no automatic Drive/tunnel access\n");
            }else if(mode.equals("prepare")){
                require(prefs.getString("drive_snapshot","").isEmpty());
                JSONObject master=new JSONObject("{\"customers\":[{\"id\":\"qa_direct\",\"name\":\"QA Direkt\"},{\"id\":\"qa_payer\",\"name\":\"QA Auftraggeber\"},{\"id\":\"qa_site\",\"name\":\"QA Einsatzort\",\"billing_mode\":\"via_customer\",\"default_billing_customer_id\":\"qa_payer\"}],\"orders\":[{\"id\":\"qa_order\",\"customer_id\":\"qa_site\",\"title\":\"QA Pflege\"}],\"worklogs\":[{\"id\":\"qa_legacy\",\"customer_id\":\"qa_direct\",\"work_date\":\"2026-10-13\",\"activity\":\"QA Bestand\",\"hours\":2},{\"id\":\"qa_billed_site\",\"customer_id\":\"qa_site\",\"billing_customer_id\":\"qa_payer\",\"work_date\":\"2026-10-13\",\"activity\":\"QA Einsatz\",\"hours\":3}],\"photos\":[],\"invoices\":[],\"accepted_ids\":[],\"sync_errors\":[],\"capabilities\":{\"worklog_compare_and_save\":true,\"entity_compare_and_save\":true}}");
                require(prefs.edit().putString("drive_snapshot",master.toString()).putString("qa_setting","keep-this").commit());
                require(context.getSharedPreferences("pixel_tunnel_private",0).edit().putString("id","tunnel_00000000000000000000000000000001").putString("organization","org-QAOnly1234").commit());
                PixelTunnelConfig configured=new PixelTunnelConfig(context);
                configured.save("tunnel_00000000000000000000000000000001","org-QAOnly1234","qa-runtime-not-a-valid-api-key-00000000");
                configured.writeEnabled(true);configured.enabled(false); // No external connection.
                store.saveWorklog(new JSONObject().put("id","qa_offline").put("customer_id","qa_site").put("billing_customer_id","qa_payer").put("work_date","2026-10-13").put("activity","QA offline").put("hours",1));
                WorkSessionStore timer=new WorkSessionStore(store.getWritableDatabase(),context);
                timer.start(new JSONObject().put("request_id","qa-upgrade-start").put("customer_id","qa_site").put("billing_customer_id","qa_payer").put("work_date","2026-10-13"));
                require(prefs.edit().putString("qa_release_baseline_hash",fingerprint(store)).commit());
                report.putString("stream","PASS synthetic upgrade fixture prepared on previous original version\n");
            }else if(mode.equals("verify")){
                require(fingerprint(store).equals(prefs.getString("qa_release_baseline_hash","")));
                require(prefs.getString("qa_setting","").equals("keep-this"));
                PixelTunnelConfig c=new PixelTunnelConfig(context);
                require(c.id().equals("tunnel_00000000000000000000000000000001")&&c.organization().equals("org-QAOnly1234")&&c.configured()&&!c.enabled()&&c.writeEnabled());
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
        }catch(Throwable failure){report.putString("stream","FAIL lifecycle "+mode+" ["+failure.getClass().getSimpleName()+"]\n");runner.finish(0,report);}
        finally{if(store!=null)store.close();}
    }
}
