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
        Activity activity=runner.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        float scale=activity.getResources().getConfiguration().fontScale;trace(runner,"activity_font_scale="+scale);require(Math.abs(scale-1f)<.01f||Math.abs(scale-1.5f)<.01f||Math.abs(scale-2f)<.01f);
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("webView");field.setAccessible(true);WebView view=(WebView)field.get(activity);
        long until=SystemClock.elapsedRealtime()+120000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<until){if("true".equals(ReleaseLifecycleProbe.js(runner,view,"!!window.TodayUI&&!!window.PlanningUI&&document.body.dataset.fieldReady==='true'"))){ready=true;break;}SystemClock.sleep(250);}trace(runner,"ready="+ready);require(ready);foreground(runner);
        int[] zoom={0};runner.runOnMainSync(()->zoom[0]=view.getSettings().getTextZoom());trace(runner,"webview_text_zoom="+zoom[0]);require(zoom[0]==Math.round(scale*100));
        String fixture="(()=>{window.__qaOverviewNative=AndroidData;const native=AndroidData,s=JSON.parse(native.getState());s.master.customers=[{id:'qa_font_customer',name:'QA Musterkunde mit einem langen Namen',active:1}];s.master.orders=[];const dateParts=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Vienna',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date()),qaDay=['year','month','day'].map(k=>dateParts.find(p=>p.type===k).value).join('-');s.master.appointments=Array.from({length:4},(_,i)=>({id:'qa_font_'+i,customer_id:'qa_font_customer',title:'QA Schriftprüfung '+i,location:'QA Garten '+i,timezone:'Europe/Vienna',status:'tentative',starts_at:qaDay+'T'+String(8+i*2).padStart(2,'0')+':00:00Z',ends_at:qaDay+'T'+String(9+i*2).padStart(2,'0')+':00:00Z'}));const entry=a=>({appointment:a,record_hash:'qa_font_hash',weather:{severity:'unknown',label:'QA Wetter ungeprüft'},calendar:{state:'not_connected'}}),bridge={};Object.keys(native).forEach(name=>{if(typeof native[name]==='function')bridge[name]=(...args)=>native[name](...args);});bridge.getState=()=>JSON.stringify(s);bridge.ownerCall=(name,raw)=>{const a=JSON.parse(raw),rows=s.master.appointments;if(name==='list_appointments')return JSON.stringify({ok:true,data:{appointments:rows.slice(a.offset||0,(a.offset||0)+(a.limit||30)).map(entry),total:rows.length,integration:{calendar_configured:false,weather_configured:false}}});if(name==='get_appointment')return JSON.stringify({ok:true,data:entry(rows.find(x=>x.id===a.appointment_id))});return native.ownerCall(name,raw);};window.AndroidData=bridge;load();openPage('home');TodayUI.invalidate();return document.querySelectorAll('#planning-home [data-appointment-open]').length===3;})()";
        try{
            checkJs(runner,view,"step1",fixture);
            String bounds="(()=>{const page=document.querySelector('.page.active'),visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>1&&r.height>1&&s.visibility!=='hidden'&&s.display!=='none';};return document.documentElement.scrollWidth<=innerWidth+1&&[...page.querySelectorAll('button,summary,[role=button]')].filter(visible).every(e=>{const r=e.getBoundingClientRect();return r.width>=47.5&&r.height>=47.5;});})()";
            checkJs(runner,view,"step2",bounds);
            checkJs(runner,view,"viewport","innerWidth===360");
            if(Math.abs(scale-1f)<.01f)checkJs(runner,view,"normal_today","(()=>{const b=document.querySelectorAll('#planning-home [data-appointment-open]')[1].getBoundingClientRect(),n=document.querySelector('.bottom').getBoundingClientRect();return b.top>=0&&b.bottom<=n.top+1;})()");
            checkJs(runner,view,"step3","(()=>{PlanningUI.open();return currentPage==='planning-page'&&!!document.getElementById('planning-new')&&document.querySelectorAll('#planning-rows [data-appointment-open]').length===4;})()");
            checkJs(runner,view,"step4",bounds);
            if(Math.abs(scale-1f)<.01f)checkJs(runner,view,"normal_planning","(()=>{const b=document.querySelector('#planning-rows [data-appointment-open]').getBoundingClientRect(),n=document.querySelector('.planning-list-actions').getBoundingClientRect();return b.top>=0&&b.bottom<=n.top+1;})()");
            checkJs(runner,view,"step5","(()=>{const b=document.getElementById('planning-new'),r=b.getBoundingClientRect(),n=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=n.top+1;})()");
            screenshot(runner,"planning",scale);
            checkJs(runner,view,"step6","(()=>{openPage('home');const button=document.querySelector('[data-session-action=stop]')||document.querySelector('[data-session-action=start]');button.scrollIntoView({block:'center'});return true;})()");
            runner.waitForIdleSync();SystemClock.sleep(500);
            checkJs(runner,view,"step7","(()=>{const b=document.querySelector('[data-session-action=stop]')||document.querySelector('[data-session-action=start]'),r=b.getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=nav.top+1&&b.scrollWidth<=b.clientWidth+1;})()");
            screenshot(runner,"today",scale);
            checkJs(runner,view,"step8","(()=>{AndroidData.getWorkSession=()=>JSON.stringify({state:'IDLE'});TodayUI.render();const button=document.querySelector('[data-session-action=start]');button.scrollIntoView({block:'center'});return !!button;})()");
            runner.waitForIdleSync();SystemClock.sleep(500);
            checkJs(runner,view,"step9",bounds);
            checkJs(runner,view,"step10","(()=>{const b=document.querySelector('[data-session-action=start]'),r=b.getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=nav.top+1&&b.scrollWidth<=b.clientWidth+1;})()");
            screenshot(runner,"today-idle",scale);
        }finally{ReleaseLifecycleProbe.js(runner,view,"(()=>{if(window.__qaOverviewNative){window.AndroidData=window.__qaOverviewNative;delete window.__qaOverviewNative;load();openPage('home');PlanningUI.refresh();}return true;})()");}
    }
    private static void trace(Instrumentation runner,String value){android.os.Bundle report=new android.os.Bundle();report.putString("stream","FONT_PROBE "+value+"\n");runner.sendStatus(0,report);}
    private static void checkJs(Instrumentation runner,WebView view,String step,String script)throws Exception{String result=ReleaseLifecycleProbe.js(runner,view,script);trace(runner,step+"="+result);if(!"true".equals(result))screenshot(runner,"failed-"+step,view.getResources().getConfiguration().fontScale);require("true".equals(result));}
    private static void foreground(Instrumentation runner)throws Exception{
        // The accessibility root is temporarily null while UiAutomation attaches.
        // WindowManager focus is authoritative even before WebView exposes its tree.
        String windows;
        try(android.os.ParcelFileDescriptor descriptor=runner.getUiAutomation().executeShellCommand("dumpsys window");java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1){out.write(buffer,0,count);if(out.size()>1024*1024)throw new java.io.IOException("Window diagnostic limit");}
            windows=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        }
        boolean own=false;for(String line:windows.split("\\n"))if(line.contains("mCurrentFocus="))own=line.contains("at.hausgarten.service/")&&line.contains("MainActivity");
        trace(runner,"main_window_focused="+own);require(own);
    }
    private static void screenshot(Instrumentation runner,String page,float scale)throws Exception{
        foreground(runner);
        String file="/data/local/tmp/gartenpirat-cloud-font-"+page+"-"+Math.round(scale*100)+".png";
        try(android.os.ParcelFileDescriptor shot=runner.getUiAutomation().executeShellCommand("screencap -p "+file);java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(shot)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
    }
    private OverviewAccessibilityProbe(){}
}
