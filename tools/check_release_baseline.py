"""Non-secret release fingerprint and installed-baseline guard. Never accesses signing values."""
from pathlib import Path
import hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[1]
def require_newer_version(code,state):
    observations=state.get('observed_installation_history',[])
    if not isinstance(observations,list) or not observations:raise ValueError('Observed installation evidence is required')
    versions=[row['version_code'] for row in observations if type(row.get('version_code')) is int and row.get('observed_at_utc') and re.fullmatch(r'[0-9a-f]{40}',row.get('reported_base_git_sha','')) and re.fullmatch(r'[0-9a-f]{64}',row.get('source_sha256',''))]
    if not versions:raise ValueError('Versioned installation evidence is required')
    baseline=max(versions)
    if code<=baseline:raise ValueError('Release versionCode must exceed the observed installed baseline'+str(baseline))
    return baseline

if __name__=='__main__':
    s=(ROOT/'android/app/build.gradle').read_text();code=int(re.search(r'versionCode (\d+)',s)[1]);name=re.search(r"versionName '([^']+)'",s)[1]
    baseline=require_newer_version(code,json.loads((ROOT/'AKTUELLER-STAND.json').read_text()))
    sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    files=[p for p in (ROOT/'android/app/src/main').rglob('*') if p.is_file()]+[ROOT/'android/app/build.gradle']
    h=hashlib.sha256()
    for p in sorted(files):h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
    dirty=bool(subprocess.check_output(['git','status','--porcelain','--','android/app/src/main','android/app/build.gradle'],cwd=ROOT,text=True).strip())
    print(json.dumps(dict(version_name=name,version_code=code,git_sha=sha,source_sha256=h.hexdigest(),source_dirty=dirty,installed_baseline=baseline)))
