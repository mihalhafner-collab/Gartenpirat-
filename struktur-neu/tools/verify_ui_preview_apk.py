"""Read-only checks of the actual standalone UI APK; never installs or connects."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def verify(apk, baseline=None):
    apk = Path(apk)
    sdk = Path(os.environ['ANDROID_HOME'])
    aapt = sdk/'build-tools/37.0.0/aapt'
    badging = subprocess.check_output([aapt, 'dump', 'badging', apk], text=True)
    tree = subprocess.check_output([aapt, 'dump', 'xmltree', apk, 'AndroidManifest.xml'], text=True)
    package = 'at.hausgarten.service.preview'
    assert "package: name='"+package+"'" in badging
    assert "versionCode='88'" in badging and "versionName='1.53.1-preview1'" in badging
    assert "application-label:'Haus & Garten Test'" in badging
    assert "uses-permission: name='android.permission.INTERNET'" not in badging
    assert 'android:sharedUserId' not in tree and not re.search(r'E: instrumentation ', tree)
    assert re.search(r'android:allowBackup[^\n]*=\(type 0x12\)0x0', tree)
    components = {}
    current = None
    for line in tree.splitlines():
        if re.match(r'^      E: (activity|service|receiver|provider) ', line):
            current = []; components[len(components)] = current
        elif re.match(r'^      E:', line):
            current = None
        if current is not None:
            current.append(line)
    components = ['\n'.join(lines) for lines in components.values()]
    disabled = ['DriveConnectionActivity', 'PlanningConnectionActivity', 'PixelTunnelActivity',
                'DriveSyncJob', 'PlanningJob', 'PixelTunnelService', 'PixelTunnelBootReceiver']
    for name in disabled:
        block = [x for x in components if '="at.hausgarten.service.'+name+'"' in x]
        assert len(block) == 1 and re.search(r'android:enabled[^\n]*=\(type 0x12\)0x0', block[0]), name
    authorities = re.findall(r'android:authorities[^\n]*="([^"]+)"', tree)
    assert set(authorities) == {package+'.'+suffix for suffix in ['invoicefiles','photocapture','photothumbnails']}
    main = ROOT/'android/app/src/main/assets'
    generated = ROOT/'android/app/build/generated/preview-assets'
    preview = ROOT/'android/app/src/preview/assets'
    overlays = {'mobile.html','design-concept.js','customer-hub.js','workflow-ui.js'}
    expected = {p.relative_to(main).as_posix():p for p in main.rglob('*') if p.is_file()}
    expected.update({name:generated/name for name in overlays})
    expected.update({p.name:p for p in preview.iterdir() if p.is_file()})
    with zipfile.ZipFile(apk) as archive:
        assert archive.testzip() is None
        names = archive.namelist()
        assert len(names) == len(set(names))
        assert {n[7:] for n in names if n.startswith('assets/') and not n.endswith('/')} == set(expected)
        for name, path in expected.items():
            assert archive.read('assets/'+name) == path.read_bytes(), name
        dex = b''.join(archive.read(n) for n in names if re.fullmatch(r'classes\d*\.dex',n))
        for name in ['PixelLocalStore','PixelMcpReadTools','PixelMcpWriteTools','AppointmentRuntime','MainActivity']:
            assert ('Lat/hausgarten/service/'+name+';').encode() in dex
    changed = []
    if baseline:
        before = json.loads(Path(baseline).read_text())
        changed = [name for name,digest in before.items() if hashlib.sha256((ROOT/name).read_bytes()).hexdigest()!=digest]
        assert set(changed) == {'android/app/src/main/java/at/hausgarten/service/MainActivity.java',
                                'android/app/src/main/assets/ui-mcp-actions.json'}, changed
    fingerprint = hashlib.sha256()
    sources = sorted([p for p in (ROOT/'android/app/src/preview').rglob('*') if p.is_file()]
                     + [ROOT/'android/app/build.gradle', ROOT/'tools/build_ui_preview_assets.py'])
    for path in sources:
        fingerprint.update(path.relative_to(ROOT).as_posix().encode()+b'\0'+path.read_bytes()+b'\0')
    return {'status':'PASS','package':package,'version_name':'1.53.1-preview1','version_code':88,
            'apk_bytes':apk.stat().st_size,'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
            'preview_source_sha256':fingerprint.hexdigest(),'packaged_assets_byte_equal':len(expected),
            'internet_permission':False,'live_components_disabled':disabled,'provider_authorities':authorities,
            'baseline_checked':bool(baseline),'baseline_changed_only_ui_isolation_and_catalog':changed,
            'device_installation':'NOT EXECUTED'}


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--baseline',type=Path)
    parser.add_argument('--report',type=Path)
    args=parser.parse_args()
    report=verify(args.apk,args.baseline)
    if args.report:args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))
