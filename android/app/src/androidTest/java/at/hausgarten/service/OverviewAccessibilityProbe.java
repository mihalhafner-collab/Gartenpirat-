package at.hausgarten.service;
import android.app.*;
import android.content.*;
import android.os.SystemClock;
import android.webkit.WebView;
import org.json.*;

/** Actual bundled WebView/font configuration; synthetic read responses in memory, no business writes. */
final class OverviewAccessibilityProbe {
    static void require(boolean ok){if(!ok)throw new AssertionError("Overview accessibility condition failed");}
    static void run(Instrumentation runner,Context context)throws Exception{
        float scale=context.getResources().getConfiguration().fontScale;require(Math.abs(scale-1.5f)<.01f||Math.abs(scale-2f)<.01f);
        Activity activity=runner.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("webView");field.setAccessible(true);WebView view=(WebView)field.get(activity);
        long until=SystemClock.elapsedRealtime()+120000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<until){if("true".equals(ReleaseLifecycleProbe.js(runner,view,"!!window.TodayUI&&!!window.PlanningUI&&document.body.dataset.fieldReady==='true'"))){ready=true;break;}SystemClock.sleep(250);}require(ready);
        int[] zoom={0};runner.runOnMainSync(()->zoom[0]=view.getSettings().getTextZoom());require(zoom[0]==Math.round(scale*100));
        String fixture="(()=>{window.__qaOverviewNative=AndroidData;const native=AndroidData,s=JSON.parse(native.getState());s.master.customers=[{id:'qa_font_customer',name:'QA Musterkunde mit einem langen Namen',active:1}];s.master.orders=[];s.master.appointments=Array.from({length:4},(_,i)=>({id:'qa_font_'+i,customer_id:'qa_font_customer',title:'QA Schriftprüfung '+i,location:'QA Garten '+i,timezone:'Europe/Vienna',status:'tentative',starts_at:localDate()+'T'+String(8+i*2).padStart(2,'0')+':00:00Z',ends_at:localDate()+'T'+String(9+i*2).padStart(2,'0')+':00:00Z'}));const entry=a=>({appointment:a,record_hash:'qa_font_hash',weather:{severity:'unknown',label:'QA Wetter ungeprüft'},calendar:{state:'not_connected'}}),bridge=Object.create(native);bridge.getState=()=>JSON.stringify(s);bridge.ownerCall=(name,raw)=>{const a=JSON.parse(raw),rows=s.master.appointments;if(name==='list_appointments')return JSON.stringify({ok:true,data:{appointments:rows.slice(a.offset||0,(a.offset||0)+(a.limit||30)).map(entry),total:rows.length,integration:{calendar_configured:false,weather_configured:false}}});if(name==='get_appointment')return JSON.stringify({ok:true,data:entry(rows.find(x=>x.id===a.appointment_id))});return native.ownerCall(name,raw);};window.AndroidData=bridge;load();openPage('home');TodayUI.invalidate();return document.querySelectorAll('#planning-home [data-appointment-open]').length===3;})()";
        try{
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,fixture)));
            String bounds="(()=>{const page=document.querySelector('.page.active'),visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>1&&r.height>1&&s.visibility!=='hidden'&&s.display!=='none';};return document.documentElement.scrollWidth<=innerWidth+1&&[...page.querySelectorAll('button,summary,[role=button]')].filter(visible).every(e=>{const r=e.getBoundingClientRect();return r.width>=47.5&&r.height>=47.5;});})()";
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,bounds)));
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,"(()=>{PlanningUI.open();return currentPage==='planning-page'&&!!document.getElementById('planning-new')&&document.querySelectorAll('#planning-rows [data-appointment-open]').length===4;})()")));
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,bounds)));
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,"(()=>{const b=document.getElementById('planning-new'),r=b.getBoundingClientRect(),n=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=n.top+1;})()")));
            screenshot(runner,"planning",scale);
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,"(()=>{openPage('home');const button=document.querySelector('[data-session-action=stop]')||document.querySelector('[data-session-action=start]');button.scrollIntoView({block:'center'});return true;})()")));
            runner.waitForIdleSync();SystemClock.sleep(500);
            require("true".equals(ReleaseLifecycleProbe.js(runner,view,"(()=>{const b=document.querySelector('[data-session-action=stop]')||document.querySelector('[data-session-action=start]'),r=b.getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=nav.top+1&&b.scrollWidth<=b.clientWidth+1;})()")));
            screenshot(runner,"today",scale);
        }finally{ReleaseLifecycleProbe.js(runner,view,"(()=>{if(window.__qaOverviewNative){window.AndroidData=window.__qaOverviewNative;delete window.__qaOverviewNative;load();openPage('home');PlanningUI.refresh();}return true;})()");}
    }
    private static void screenshot(Instrumentation runner,String page,float scale)throws Exception{
        String file="/data/local/tmp/gartenpirat-cloud-font-"+page+"-"+Math.round(scale*100)+".png";
        try(android.os.ParcelFileDescriptor shot=runner.getUiAutomation().executeShellCommand("screencap -p "+file);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(shot)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
    }
    private OverviewAccessibilityProbe(){}
}
