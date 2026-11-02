"""Non-secret release fingerprint. Never accesses signing values."""
from pathlib import Path
import hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[1]

def version_policy(root=ROOT):
    values = {}
    for line in (Path(root)/'app-versions.properties').read_text().splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key,value=line.split('=',1)
            if key.strip() in values:raise ValueError('Duplicate version policy key')
            values[key.strip()]=value.strip()
    previous=int(values['productionPreviousVersionCode'])
    code=int(values['productionVersionCode']);name=values['productionVersionName']
    test_build=int(values['testBuildNumber']);test_code=int(values['testAndroidVersionCode']);test_name=values['testVersionName']
    if code!=previous+1 or previous<121 or not re.fullmatch(r'1\.\d+\.\d+',name):
        raise ValueError('Production must advance its own confirmed sequence by one')
    if test_build<2 or test_code!=116+test_build or not re.fullmatch(r'2\.\d+\.\d+',test_name):
        raise ValueError('TEST uses its own 2.x version / build series and the fixed Android migration offset')
    return dict(version_name=name,version_code=code,test_version_name=test_name,test_build_number=test_build,test_version_code=test_code)

if __name__=='__main__':
    policy=version_policy()
    sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    inputs=['app/src/main','app/src/fullTest','app/build.gradle','app-versions.properties','tools/build_fulltest_assets.py','tools/check_release_baseline.py']
    files=[]
    for name in inputs:
        path=ROOT/name
        files.extend(p for p in path.rglob('*') if p.is_file()) if path.is_dir() else files.append(path)
    h=hashlib.sha256()
    for p in sorted(files):h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
    dirty=bool(subprocess.check_output(['git','status','--porcelain','--',*inputs],cwd=ROOT,text=True).strip())
    print(json.dumps(dict(**policy,git_sha=sha,source_sha256=h.hexdigest(),source_dirty=dirty)))
