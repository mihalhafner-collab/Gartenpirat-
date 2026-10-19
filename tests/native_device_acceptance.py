"""Run only the isolated test instrumentation against the ORIGINAL release.

Does not update/delete the original app, rotate credentials or enable the tunnel.
The diagnostic package is removed afterwards. Failed probes remain failures.
"""
import json
import hashlib
import os
import re
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import runtime_paths
ADB=ROOT/'android-toolchain/sdk/platform-tools/adb.exe'
APK=ROOT/'outputs/Pixel-Instrumentation-Architecture.apk'
TARGET='at.hausgarten.service'
DIAGNOSTIC=TARGET+'.test'
FLAGS=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0

def main(serial):
    if not serial or os.environ.get('HG_TEST_DEVICE_CONFIRMED') != '1':
        raise RuntimeError('Device acceptance requires an explicitly selected device and user confirmation; no device operation performed.')
    private=runtime_paths.business_path(runtime_paths.private_root()/'diagnostics/native-device')
    private.mkdir(parents=True,exist_ok=True,mode=0o700)
    def run(args, timeout=60):
        return subprocess.run([str(ADB),'-s',serial,*args],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
            timeout=timeout,creationflags=FLAGS,check=True).stdout.decode('utf-8',errors='replace')
    if run(['get-state']).strip()!='device':raise RuntimeError('Pixel must be authorized')
    if run(['shell','getprop','ro.product.model']).strip()!='Pixel 10 Pro':raise RuntimeError('Wrong device')
    version=run(['shell','dumpsys','package',TARGET])
    expected_code=int(os.environ.get('HG_EXPECTED_VERSION_CODE','58'))
    if not re.search(r'versionCode='+str(expected_code)+r'\b',version):raise RuntimeError('Installed original APK does not match the expected test version code')
    if not APK.is_file():raise RuntimeError('Build and sign the diagnostic APK first; no original update is performed')
    installed=False
    try:
        run(['install','-r','-t',str(APK)]);installed=True
        output=run(['shell','am','instrument','-w',DIAGNOSTIC+'/at.hausgarten.service.PixelMcpInstrumentation'],180)
        records=[{'name':line[5:],'result':'PASS'} if line.startswith('PASS ') else {'name':line[5:],'result':'FAIL'}
                 for line in output.splitlines() if line.startswith(('PASS ','FAIL '))]
        counts_line=next((line.split(' ',1)[1] for line in output.splitlines() if line.startswith('READONLY_PIXEL_COUNTS ')),None)
        report={'actual_device':True,'model':'Pixel 10 Pro','api':int(run(['shell','getprop','ro.build.version.sdk']).strip()),
            'original_app_updated_by_test_runner':False,'version_code':expected_code,'business_writes':'isolated namespaces only','results':records,
            'productive_snapshot_counts':json.loads(counts_line) if counts_line else None,
            'success':bool(records) and not any(r['result']=='FAIL' for r in records) and 'FAILED ' not in output and 'RESULT ' in output}
        (private/'Architecture-Native-Device.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        (private/'Architecture-Native-Device.log').write_text(output,encoding='utf-8')
        print(json.dumps({'passed':sum(r['result']=='PASS' for r in records),'failed':sum(r['result']=='FAIL' for r in records),
            'report_path_sha256':hashlib.sha256(str(private/'Architecture-Native-Device.json').encode()).hexdigest(),'success':report['success']}))
        return 0 if report['success'] else 1
    finally:
        if installed:
            # Android instrumentation interrupts the target process/service. Restore only
            # the already enabled connection; never opt in or change credentials here.
            restore=run(['shell','am','instrument','-w','-e','restore_enabled_tunnel','true',
                DIAGNOSTIC+'/at.hausgarten.service.PixelMcpInstrumentation'])
            (private/'Architecture-Native-Runtime-Restore.log').write_text(restore,encoding='utf-8')
            run(['uninstall',DIAGNOSTIC])
        if os.environ.get('HG_SKIP_OPEN_APP')!='1':
            run(['shell','am','start','-n',TARGET+'/.MainActivity'])
        service=run(['shell','dumpsys','activity','services',TARGET+'/.PixelTunnelService'])
        (private/'Architecture-Native-Runtime-State.json').write_text(json.dumps({
            'service_observed_after_test':'ServiceRecord' in service,
            'configuration_changed_by_cleanup':False,
            'live_mcp_reachability_confirmed':False,
            'note':'A start request is not proof of a running service or an available MCP connection.'},indent=2),encoding='utf-8')

if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('Usage: py -3.11 tests/native_device_acceptance.py DEVICE_SERIAL')
    raise SystemExit(main(sys.argv[1]))
