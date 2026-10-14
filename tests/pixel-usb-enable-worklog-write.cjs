/* Only run following explicit user consent to enable the narrow native write opt-in. */
'use strict';
// Export copy: no implicit device or live operation.
if (!(process.argv[2] || process.env.HG_TEST_DEVICE_SERIAL) || process.env.HG_TEST_DEVICE_CONFIRMED !== '1') {
  throw Error('Manual device test requires an explicitly selected device and HG_TEST_DEVICE_CONFIRMED=1 after user consent.');
}

const {execFileSync}=require('node:child_process');
const {chromium}=require(process.env.HG_PLAYWRIGHT_MODULE || 'playwright');
const path=require('node:path');const assert=require('node:assert/strict');
const adbPath=path.join(__dirname,'../android-toolchain/sdk/platform-tools/adb.exe');
const serial=process.argv[2] || process.env.HG_TEST_DEVICE_SERIAL,app='at.hausgarten.service';
function adb(args){return execFileSync(adbPath,['-s',serial,...args],{encoding:'utf8',windowsHide:true,timeout:30000,maxBuffer:8*1024*1024});}
function nodes(){adb(['shell','uiautomator','dump','/data/local/tmp/hausgarten-usb-window.xml']);const xml=adb(['shell','cat','/data/local/tmp/hausgarten-usb-window.xml']);return [...xml.matchAll(/<node\b([^>]+)>/g)].map(m=>{const a=Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(x=>[x[1],x[2]]));if(a.password==='true')a.text='';return a;});}
function bounds(n){const m=n?.bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/);assert(m,'Observed bounds required');return m.slice(1).map(Number);}
function tap(n){const [x1,y1,x2,y2]=bounds(n);assert(x2>x1&&y2>y1);adb(['shell','input','tap',String(Math.round((x1+x2)/2)),String(Math.round((y1+y2)/2))]);}
async function main(){let browser,port;
  try{
    assert(adb(['shell','getprop','ro.product.model']).trim()==='Pixel 10 Pro');
    adb(['shell','am','start','-n',app+'/.MainActivity']);
    let sockets;for(let i=0;i<20;i++){sockets=adb(['shell','cat','/proc/net/unix']);if(sockets.includes('webview_devtools_remote_'))break;await new Promise(r=>setTimeout(r,300));}
    const pid=adb(['shell','pidof',app]).trim().split(/\s+/)[0];assert(sockets.includes('@webview_devtools_remote_'+pid));
    port=adb(['forward','tcp:0','localabstract:webview_devtools_remote_'+pid]).trim();
    browser=await chromium.connectOverCDP('http://127.0.0.1:'+port);
    const page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().startsWith('file:///android_asset/mobile.html'));assert(page);
    // Existing internal settings bridge, not a bypass of the protected Activity.
    await page.evaluate(()=>AndroidData.openPixelTunnelSettings());
    let list=nodes();assert(list.some(n=>n.text==='ChatGPT auf dem Pixel'));
    let check=list.find(n=>n.class==='android.widget.CheckBox'&&n.text==='ChatGPT darf Cloudname_189a14e78c anlegen und korrigieren');
    if(!check){const root=list.find(n=>n.class==='android.widget.ScrollView');const [x1,y1,x2,y2]=bounds(root);adb(['shell','input','swipe',String((x1+x2)/2),String(y2-100),String((x1+x2)/2),String(y1+100),'350']);list=nodes();check=list.find(n=>n.class==='android.widget.CheckBox'&&n.text==='ChatGPT darf Cloudname_189a14e78c anlegen und korrigieren');}
    assert(check,'Consent checkbox must be visible');if(check.checked!=='true')tap(check);
    list=nodes();assert(list.find(n=>n.class==='android.widget.CheckBox').checked==='true');
    const button=list.find(n=>n.text==='Verbindung starten'&&n.clickable==='true');assert(button);tap(button);
    await new Promise(r=>setTimeout(r,1500));
    const status=await page.evaluate(()=>JSON.parse(AndroidData.getPixelTunnelStatus()));
    console.log(JSON.stringify({native_write_consent:status.write_enabled,tunnel_state:status.state,detail:status.detail,configured:status.configured,running:status.running}));
    assert(status.write_enabled===true,'Write consent must be persisted');
  }finally{if(browser)await browser.close();if(port)adb(['forward','--remove','tcp:'+port]);}
}
main().catch(e=>{console.error(e.message);process.exitCode=1;});
