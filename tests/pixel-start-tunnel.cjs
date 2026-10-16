'use strict';
const {execFileSync}=require('node:child_process');
const path=require('node:path');
const {chromium}=require('C:/Users/Michael/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const adbPath=path.resolve(__dirname,'../android-toolchain/sdk/platform-tools/adb.exe');
const serial='58151FDCH005U8';
const adb=(args)=>execFileSync(adbPath,['-s',serial,...args],{encoding:'utf8',windowsHide:true,timeout:30000});
function nodes(){adb(['shell','uiautomator','dump','/data/local/tmp/tunnel.xml']);const xml=adb(['shell','cat','/data/local/tmp/tunnel.xml']);return [...xml.matchAll(/<node\b([^>]+)>/g)].map(m=>Object.fromEntries([...m[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(a=>[a[1],a[2]])));}
function tap(node){const b=node.bounds.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/).slice(1).map(Number);adb(['shell','input','tap',String((b[0]+b[2])>>1),String((b[1]+b[3])>>1)]);}
async function findButton(text){for(let i=0;i<5;i++){const button=nodes().find(n=>n.text===text&&n.clickable==='true'&&n.bounds!=='[0,0][0,0]');if(button)return button;adb(['shell','input','swipe','640','2350','640','850','350']);await new Promise(r=>setTimeout(r,300));}return null;}
(async()=>{let browser,port;try{
  adb(['shell','am','start','-n','at.hausgarten.service/.MainActivity']);
  await new Promise(r=>setTimeout(r,1500));
  const pid=adb(['shell','pidof','at.hausgarten.service']).trim();
  port=adb(['forward','tcp:0','localabstract:webview_devtools_remote_'+pid]).trim();
  browser=await chromium.connectOverCDP('http://127.0.0.1:'+port);
  const page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().includes('/android_asset/mobile.html'));
  if(!page)throw Error('App-Oberfläche nicht erreichbar');
  const status=await page.evaluate(()=>JSON.parse(AndroidData.getPixelTunnelStatus()));
  if(!status.configured)throw Error('Tunnel ist nicht eingerichtet');
  if(!status.running){let open=page.locator('.bottom [data-open="sync-page"]');if(!await open.count()){await page.locator('.bottom [data-open="home"]').click();open=page.locator('#home [data-open="sync-page"]:visible').first();}await open.click();await page.locator('#sync-page.active').waitFor();await page.locator('#pixel-tunnel-setup').click();await page.waitForTimeout(500);const button=await findButton('Verbindung starten');if(!button)throw Error('Startknopf nicht gefunden');tap(button);await new Promise(r=>setTimeout(r,5000));}
  const final=await page.evaluate(()=>JSON.parse(AndroidData.getPixelTunnelStatus()));
  console.log(JSON.stringify(final));
  if(!final.running)throw Error('Tunnel-Dienst läuft nicht');
}finally{if(browser)await browser.close();if(port)try{adb(['forward','--remove','tcp:'+port]);}catch{}}})().catch(e=>{console.error(e.message);process.exitCode=1;});
