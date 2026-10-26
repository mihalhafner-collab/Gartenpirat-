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
        try{
        float scale=activity.getResources().getConfiguration().fontScale;trace(runner,"activity_font_scale="+scale);require(Math.abs(scale-1f)<.01f||Math.abs(scale-1.5f)<.01f||Math.abs(scale-2f)<.01f);
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("webView");field.setAccessible(true);WebView view=(WebView)field.get(activity);
        long until=SystemClock.elapsedRealtime()+120000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<until){if("true".equals(ReleaseLifecycleProbe.js(runner,view,"!!window.TodayUI&&!!window.PlanningUI&&document.body.dataset.fieldReady==='true'&&document.body.dataset.revierReady==='true'"))){ready=true;break;}SystemClock.sleep(250);}trace(runner,"ready="+ready);require(ready);foreground(runner);
        int[] zoom={0};runner.runOnMainSync(()->zoom[0]=view.getSettings().getTextZoom());trace(runner,"webview_text_zoom="+zoom[0]);require(zoom[0]==Math.round(scale*100));
        String fixture="(()=>{window.__qaOverviewNative=AndroidData;const native=AndroidData,s=JSON.parse(native.getState());s.master.customers=[{id:'qa_font_customer',name:'QA Musterkunde mit einem langen Namen',active:1}];const active=JSON.parse(native.getWorkSession());window.__qaOverviewSessionProjection=value=>JSON.stringify(Object.fromEntries(Object.entries(value).filter(([key])=>!['worked_ms','hours','break_minutes'].includes(key)).sort(([a],[b])=>a.localeCompare(b))));window.__qaOverviewOriginalSession=__qaOverviewSessionProjection(active);window.__qaOverviewTimerPrimary=()=>{const session=JSON.parse(AndroidData.getWorkSession()),action=session.review_required?'review':({RUNNING:'stop',PAUSED:'resume',STOPPED_PENDING_REVIEW:'complete',IDLE:'start',FINALIZED:'start',CANCELLED:'start'})[session.state||'IDLE'];if(!action)throw Error('QA_OVERVIEW_SESSION_STATE_UNSUPPORTED');const button=document.querySelector('#workday-timer #today-action-dock [data-session-action='+action+']');if(!button)throw Error('QA_OVERVIEW_SESSION_ACTION_MISSING');return button;};if(active.customer_id&&active.customer_id!=='qa_font_customer')s.master.customers.push({id:active.customer_id,name:'QA Musterkunde mit einem langen Namen',active:1});s.master.orders=[];const dateParts=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Vienna',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date()),qaDay=['year','month','day'].map(k=>dateParts.find(p=>p.type===k).value).join('-');s.master.appointments=Array.from({length:4},(_,i)=>({id:'qa_font_'+i,customer_id:'qa_font_customer',title:'QA Schriftprüfung '+i,location:'QA Garten '+i,timezone:'Europe/Vienna',status:'tentative',starts_at:qaDay+'T'+String(8+i*2).padStart(2,'0')+':00:00Z',ends_at:''}));const entry=a=>({appointment:a,record_hash:'qa_font_hash',weather:{severity:'unknown',label:'QA Wetter ungeprüft'},calendar:{state:'not_connected'}}),bridge={};Object.keys(native).forEach(name=>{if(typeof native[name]==='function')bridge[name]=(...args)=>native[name](...args);});bridge.getState=()=>JSON.stringify(s);bridge.ownerCall=(name,raw)=>{const a=JSON.parse(raw),rows=s.master.appointments;if(name==='list_appointments')return JSON.stringify({ok:true,data:{appointments:rows.slice(a.offset||0,(a.offset||0)+(a.limit||30)).map(entry),total:rows.length,integration:{calendar_configured:false,weather_configured:false}}});if(name==='get_appointment')return JSON.stringify({ok:true,data:entry(rows.find(x=>x.id===a.appointment_id))});return native.ownerCall(name,raw);};window.AndroidData=bridge;load();openPage('home');TodayUI.invalidate();return document.querySelectorAll('#planning-home [data-appointment-open]').length===1;})()";
        try{
            checkJs(runner,view,"step1",fixture);settle(runner,view);
            String bounds="(()=>{const page=document.querySelector('.page.active'),visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>1&&r.height>1&&s.visibility!=='hidden'&&s.display!=='none';};return document.documentElement.scrollWidth<=innerWidth+1&&[...page.querySelectorAll('button,summary,[role=button]')].filter(visible).every(e=>{const r=e.getBoundingClientRect();return r.width>=47.5&&r.height>=47.5;});})()";
            checkJs(runner,view,"step2",bounds);
            if(Math.abs(scale-1f)<.01f){scrollTo(runner,view,"today_appointment","#planning-home [data-appointment-open]");checkJs(runner,view,"normal_today_appointment","[...document.querySelectorAll('#planning-home .today-time,#planning-home .today-customer')].every(e=>{const r=e.getBoundingClientRect(),dock=document.getElementById('today-action-dock').getBoundingClientRect();return r.top>=0&&r.bottom<=dock.top+1;})");}
            checkJs(runner,view,"viewport","innerWidth===360");
            checkJs(runner,view,"lower_controls","(()=>{const dock=document.querySelector('#today-action-dock').getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();return dock.top>=0&&dock.bottom<=nav.top+1&&[...document.querySelectorAll('#today-action-dock button')].every(b=>{const r=b.getBoundingClientRect();return r.top>=dock.top&&r.bottom<=dock.bottom+1&&b.scrollWidth<=b.clientWidth+1;});})()");
            checkJs(runner,view,"step3","(()=>{PlanningUI.open();return currentPage==='planning-page'&&!!document.getElementById('planning-new')&&document.querySelectorAll('#planning-rows [data-appointment-open]').length===4;})()");
            checkJs(runner,view,"step4",bounds);
            if(Math.abs(scale-1f)<.01f){scrollTo(runner,view,"planning_appointment","#planning-rows [data-appointment-open]");checkJs(runner,view,"normal_planning","(()=>{const b=document.querySelector('#planning-rows [data-appointment-open]').getBoundingClientRect(),n=document.querySelector('.planning-list-actions').getBoundingClientRect();return b.top>=0&&b.bottom<=n.top+1;})()");}
            scrollTo(runner,view,"planning_new","#planning-new");
            checkJs(runner,view,"step5","(()=>{const b=document.getElementById('planning-new'),r=b.getBoundingClientRect(),n=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=n.top+1;})()");
            screenshot(runner,"planning",scale);
            checkJs(runner,view,"step6","(()=>{openPage('home');const button=__qaOverviewTimerPrimary();button.scrollIntoView({block:'center'});return true;})()");
            runner.waitForIdleSync();SystemClock.sleep(500);
            checkJs(runner,view,"step7","(()=>{const b=__qaOverviewTimerPrimary(),r=b.getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=nav.top+1&&b.scrollWidth<=b.clientWidth+1;})()");
            screenshot(runner,"today",scale);
            checkJs(runner,view,"step8","(()=>{AndroidData.getWorkSession=()=>JSON.stringify({state:'IDLE'});TodayUI.render();const button=document.querySelector('[data-session-action=start]');button.scrollIntoView({block:'center'});return !!button;})()");
            runner.waitForIdleSync();SystemClock.sleep(500);
            checkJs(runner,view,"step9",bounds);
            checkJs(runner,view,"step10","(()=>{const b=document.querySelector('[data-session-action=start]'),r=b.getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=nav.top+1&&b.scrollWidth<=b.clientWidth+1;})()");
            screenshot(runner,"today-idle",scale);
        }finally{checkJs(runner,view,"original_session_unchanged","(()=>{if(!window.__qaOverviewNative)return true;window.AndroidData=window.__qaOverviewNative;let unchanged=false;try{unchanged=typeof window.__qaOverviewSessionProjection==='function'&&__qaOverviewSessionProjection(JSON.parse(AndroidData.getWorkSession()))===__qaOverviewOriginalSession;}finally{delete window.__qaOverviewNative;delete window.__qaOverviewFrames;delete window.__qaOverviewScroll;delete window.__qaOverviewSessionProjection;delete window.__qaOverviewOriginalSession;delete window.__qaOverviewTimerPrimary;load();openPage('home');PlanningUI.refresh();}return unchanged;})()");}
        }finally{runner.runOnMainSync(activity::finish);runner.waitForIdleSync();}
    }
    private static void scrollTo(Instrumentation runner,WebView view,String step,String selector)throws Exception{
        // A lower-priority appointment may follow the active-work card. Exercise
        // ordinary scrolling, then keep the original viewport/dock bounds unchanged.
        checkJs(runner,view,"scroll_"+step,"(()=>{const target=document.querySelector("+JSONObject.quote(selector)+");if(!target)return false;const before=target.getBoundingClientRect();window.__qaOverviewScroll={step:"+JSONObject.quote(step)+",beforeY:scrollY,beforeTop:before.top,beforeBottom:before.bottom};target.scrollIntoView({block:'start',inline:'nearest',behavior:'instant'});return true;})()");
        settle(runner,view);
    }
    private static void settle(Instrumentation runner,WebView view)throws Exception{
        ReleaseLifecycleProbe.js(runner,view,"window.__qaOverviewFrames=false;requestAnimationFrame(()=>requestAnimationFrame(()=>window.__qaOverviewFrames=true));true");
        long until=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<until){if("true".equals(ReleaseLifecycleProbe.js(runner,view,"window.__qaOverviewFrames===true"))){trace(runner,"layout_settled=true");return;}SystemClock.sleep(100);}throw new AssertionError("Overview layout did not settle");
    }
    private static void trace(Instrumentation runner,String value){android.os.Bundle report=new android.os.Bundle();report.putString("stream","FONT_PROBE "+value+"\n");runner.sendStatus(0,report);}
    private static void checkJs(Instrumentation runner,WebView view,String step,String script)throws Exception{String result=ReleaseLifecycleProbe.js(runner,view,script);trace(runner,step+"="+result);if(!"true".equals(result)){geometry(runner,view,step);screenshot(runner,"failed-"+step,view.getResources().getConfiguration().fontScale);}require("true".equals(result));}
    private static void geometry(Instrumentation runner,WebView view,String step)throws Exception{
        String observed=ReleaseLifecycleProbe.js(runner,view,"JSON.stringify({viewport:[innerWidth,innerHeight],scrollY,documentHeight:document.documentElement.scrollHeight,previousScroll:window.__qaOverviewScroll||null,nodes:['#planning-home [data-appointment-open]','#planning-home .today-time','#planning-home .today-customer','#today-action-dock','.bottom','#planning-rows [data-appointment-open]','.planning-list-actions','#planning-new'].map(selector=>{const element=document.querySelector(selector);if(!element)return {selector,missing:true};const rect=element.getBoundingClientRect(),style=getComputedStyle(element);return {selector,x:rect.x,y:rect.y,width:rect.width,height:rect.height,top:rect.top,bottom:rect.bottom,scrollTop:element.scrollTop,scrollWidth:element.scrollWidth,clientWidth:element.clientWidth,position:style.position,display:style.display};})})");
        trace(runner,"geometry_"+step+"="+observed);
    }
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
