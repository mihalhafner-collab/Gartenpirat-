/* Actual Chromium tests of the new card. Android bridge is a stub, not a device test. */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('./browser.cjs');
const results=[];
async function main(){
  const browser=await chromium.launch({headless:true,channel:'chrome'});
  try{
    const page=await browser.newPage({viewport:{width:360,height:800}});
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    async function check(name,task){await task();results.push({name,result:'PASS'});console.log('PASS '+name);}
    await page.setContent('<html><body style="margin:0;font:16px sans-serif"><section id="sync-page" class="page active" style="padding:16px"><h2>Drive-Abgleich</h2><button id="sync">Jetzt abgleichen</button></section></body></html>');
    await page.evaluate(()=>{
      window.nativeOpened=0;
      window.bridgeInfo={detail:'Pixel-Verbindung ist beendet.'};
      window.AndroidData={getPixelTunnelStatus:()=>JSON.stringify(window.bridgeInfo),openPixelTunnelSettings:()=>{window.nativeOpened++;}};
    });
    const source=path.resolve(__dirname,'../android/app/src/main/assets/pixel-tunnel-ui.js');
    await page.addScriptTag({path:source});
    await check('Card appears once inside existing Drive page',async()=>assert.equal(await page.locator('#pixel-tunnel-card').count(),1));
    await check('Accurate stopped state visible',async()=>assert.equal(await page.locator('#pixel-tunnel-state').innerText(),'Pixel-Verbindung ist beendet.'));
    await check('Native settings reachable by touch',async()=>{await page.locator('#pixel-tunnel-setup').click();assert.equal(await page.evaluate(()=>window.nativeOpened),1);});
    await check('Button is at least 48px and no horizontal overflow at 360px',async()=>{const rect=await page.locator('#pixel-tunnel-setup').boundingBox();assert(rect.height>=48);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));});
    await check('Reinstallation does not duplicate card',async()=>{await page.addScriptTag({path:source});assert.equal(await page.locator('#pixel-tunnel-card').count(),1);});
    await page.evaluate(()=>window.bridgeInfo={detail:'<img src=x onerror="window.injected=true"> Verbindung wird erneut geprüft.'});
    await check('Status updates as text, no HTML injection',async()=>{await page.waitForFunction(()=>document.getElementById('pixel-tunnel-state').textContent.startsWith('<img'));assert.equal(await page.locator('#pixel-tunnel-state img').count(),0);assert.equal(await page.evaluate(()=>window.injected),undefined);});
    await check('Card introduces no username, credential or secret field',async()=>assert.equal(await page.locator('#pixel-tunnel-card input').count(),0));
    await page.evaluate(()=>delete window.AndroidData);
    await check('Browser-only fallback is understandable',async()=>{await page.locator('#pixel-tunnel-setup').click();assert((await page.locator('#pixel-tunnel-state').innerText()).includes('Android-Testversion'));});
    await check('No runtime errors with missing native bridge',async()=>assert.deepEqual(errors,[]));
    await page.screenshot({path:path.resolve(__dirname,'../outputs/Pixel-Tunnel-Bedienung.png'),fullPage:true});
    fs.writeFileSync(path.resolve(__dirname,'../outputs/Pixel-Tunnel-UI-Tests.json'),JSON.stringify({actualBrowser:true,androidBridge:'stub',results},null,2));
    console.log('RESULT '+results.length+' passed, 0 failed');
  }finally{await browser.close();}
}
main().catch(error=>{console.error(error);process.exitCode=1;});
