"""Non-secret release fingerprint. Never accesses signing values."""
from pathlib import Path
import hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[1]
if __name__=='__main__':
    s=(ROOT/'app/build.gradle').read_text();code=int(re.search(r'versionCode (\d+)',s)[1]);name=re.search(r"versionName '([^']+)'",s)[1]
    sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    files=[p for p in (ROOT/'app/src/main').rglob('*') if p.is_file()]+[ROOT/'app/build.gradle']
    h=hashlib.sha256()
    for p in sorted(files):h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
    dirty=bool(subprocess.check_output(['git','status','--porcelain','--','app/src/main','app/build.gradle'],cwd=ROOT,text=True).strip())
    print(json.dumps(dict(version_name=name,version_code=code,git_sha=sha,source_sha256=h.hexdigest(),source_dirty=dirty)))
