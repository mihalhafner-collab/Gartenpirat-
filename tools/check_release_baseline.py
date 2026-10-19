"""Non-secret release fingerprint and installed-baseline guard. Never accesses signing values."""
from pathlib import Path
import hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[1]
s=(ROOT/'android/app/build.gradle').read_text();code=int(re.search(r'versionCode (\d+)',s)[1]);name=re.search(r"versionName '([^']+)'",s)[1]
baseline=77
if code<=baseline:raise SystemExit('Release versionCode must exceed the confirmed installed baseline77')
sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
files=[p for p in (ROOT/'android/app/src/main').rglob('*') if p.is_file()]+[ROOT/'android/app/build.gradle']
h=hashlib.sha256()
for p in sorted(files):h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
dirty=bool(subprocess.check_output(['git','status','--porcelain','--','android/app/src/main','android/app/build.gradle'],cwd=ROOT,text=True).strip())
print(json.dumps(dict(version_name=name,version_code=code,git_sha=sha,source_sha256=h.hexdigest(),source_dirty=dirty,installed_baseline=baseline)))
