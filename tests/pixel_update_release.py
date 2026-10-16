"""Approved update with private BUSINESS-only backup. No credential extraction."""
import base64, hashlib, json, os, re, subprocess, sys
from datetime import datetime, timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ADB=ROOT/'android-toolchain/sdk/platform-tools/adb.exe'
APP='at.hausgarten.service'
DIAG=APP+'.test'
def main(serial):
    def run(args,timeout=90):
        return subprocess.run([str(ADB),'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=True,timeout=timeout,creationflags=subprocess.CREATE_NO_WINDOW).stdout.decode('utf-8',errors='replace')
    if run(['get-state']).strip()!='device' or run(['shell','getprop','ro.product.model']).strip()!='Pixel 10 Pro':raise RuntimeError('Wrong or unauthorized device')
    version=os.environ.get('HG_UPDATE_VERSION','1.39.1'); code=int(os.environ.get('HG_EXPECTED_VERSION_CODE','58'))
    if not re.fullmatch(r'\d+\.\d+\.\d+',version) or code<=0:raise RuntimeError('Invalid expected release')
    apk=ROOT/('outputs/Haus-und-Gartenservice-Pixel-v'+version+'.apk')
    diagnostic=ROOT/'outputs/Pixel-Instrumentation-Architecture.apk'
    report=ROOT/('outputs/Pixel-v'+version+'-Update-Preservation.json')
    if report.exists():raise RuntimeError('Preservation report exists; inspect before retry')
    def capture():
        output=run(['shell','am','instrument','-w','-e','business_capture','true',DIAG+'/at.hausgarten.service.PixelMcpInstrumentation'])
        if 'BUSINESS_CAPTURE_OK' not in output or 'BUSINESS_CAPTURE_FAILED' in output:raise RuntimeError('Restricted business capture failed; no raw data logged')
        chunks=[]
        for line in output.splitlines():
            if 'BUSINESS_CAPTURE ' not in line:continue
            value=line.split('BUSINESS_CAPTURE ',1)[1].strip()
            if re.fullmatch(r'[A-Za-z0-9+/=]+',value):chunks.append(value)
        if not chunks:raise RuntimeError('No business capture chunks; no original update')
        return json.loads(base64.b64decode(''.join(chunks),validate=True))
    def ledger(state):
        return {'master':state['master'],'pending':state.get('pending',[])}
    def digest(state):
        return hashlib.sha256(json.dumps(ledger(state),sort_keys=True,separators=(',',':'),ensure_ascii=False).encode()).hexdigest()
    installed=False
    try:
        run(['shell','am','force-stop',APP])
        run(['install','-r','-t',str(diagnostic)]);installed=True
        before=capture()
        backup=ROOT/'data/backups/pixel-usb'/('before-v'+version+'-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%SZ')+'.business.json')
        backup.parent.mkdir(parents=True,exist_ok=True)
        with backup.open('x',encoding='utf-8') as handle:json.dump(before,handle,ensure_ascii=False)
        # Verify the backup we just wrote before touching the original APK.
        if digest(json.loads(backup.read_text(encoding='utf-8')))!=digest(before):raise RuntimeError('Business backup verification failed')
        run(['shell','am','force-stop',APP])
        run(['install','-r',str(apk)])
        installed_version=run(['shell','dumpsys','package',APP])
        if not re.search(r'versionCode='+str(code)+r'\b',installed_version) or 'versionName='+version not in installed_version:raise RuntimeError('Unexpected installed version')
        after=capture()
        preserved=digest(before)==digest(after)
        result={'updated':True,'version':version,'version_code':code,'business_backup':str(backup),'backup_excludes_credentials':True,'before_hash':digest(before),'after_hash':digest(after),'productive_ledger_identical':preserved,'counts':{key:len(after['master'].get(key,[])) for key in ['customers','orders','worklogs','invoices','photos']},'pending_count':len(after.get('pending',[]))}
        report.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps({k:v for k,v in result.items() if k not in ['before_hash','after_hash']}),flush=True)
        if not preserved:raise RuntimeError('Business ledger changed; investigate before claiming preservation')
    finally:
        if installed:
            run(['shell','am','instrument','-w','-e','restore_enabled_tunnel','true',DIAG+'/at.hausgarten.service.PixelMcpInstrumentation'])
            run(['uninstall',DIAG])
        if os.environ.get('HG_SKIP_OPEN_APP')!='1':
            run(['shell','am','start','-n',APP+'/.MainActivity'])
if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('Physical Pixel serial required')
    main(sys.argv[1])
