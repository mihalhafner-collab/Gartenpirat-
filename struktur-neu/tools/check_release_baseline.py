"""Non-secret release fingerprint and installed-baseline guard. Never accesses signing values."""
from pathlib import Path
import hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[1]
def verify_source_layout(root=ROOT):
    root=Path(root).resolve()
    repository=root.parent
    if root.name != 'struktur-neu':
        raise ValueError('Only struktur-neu is an approved candidate source')
    for legacy in (repository/'app', repository/'android'):
        candidates=[legacy/name for name in ('build.gradle','build.gradle.kts','settings.gradle','settings.gradle.kts')]
        candidates.extend(legacy.glob('**/src/**/AndroidManifest.xml'))
        candidates.extend(legacy.glob('**/src/**/*.java'))
        candidates.extend(legacy.glob('**/src/**/*.kt'))
        if any(p.is_file() for p in candidates):
            raise ValueError('Retired app/build source detected: '+str(legacy))
    main=root/'android/app/src/main'
    path=main/'java/at/hausgarten/service/MainActivity.kt'
    if not path.is_file() or not path.resolve().is_relative_to(main.resolve()):
        raise ValueError('Canonical Kotlin activity missing or redirected')
    if 'setContent' not in path.read_text():
        raise ValueError('Canonical Compose entry point missing')
    for path in (root/'android/app/src').rglob('*'):
        if path.is_symlink():
            raise ValueError('Redirected candidate source is not allowed: '+str(path))
    settings=(root/'android/settings.gradle').read_text()
    if re.findall(r"include\s+['\"](:[^'\"]+)['\"]",settings) != [':app'] or 'projectDir' in settings or 'includeBuild' in settings:
        raise ValueError('Only the canonical :app module is allowed')
    build=(root/'android/app/build.gradle').read_text()
    if 'sourceSets' in build or 'srcDirs' in build or 'srcDir ' in build:
        raise ValueError('Alternative functional source directories are not allowed')
    return 'struktur-neu/android/app/src/main'

def require_newer_version(code,state):
    observations=state.get('observed_installation_history',[])
    if not isinstance(observations,list) or not observations:raise ValueError('Observed installation evidence is required')
    versions=[row['version_code'] for row in observations if type(row.get('version_code')) is int and row.get('observed_at_utc') and re.fullmatch(r'[0-9a-f]{40}',row.get('reported_base_git_sha','')) and re.fullmatch(r'[0-9a-f]{64}',row.get('source_sha256',''))]
    if not versions:raise ValueError('Versioned installation evidence is required')
    current=state.get('last_observed_installed_build')
    if current is not None:
        if not isinstance(current,dict):raise ValueError('Current installation evidence is required')
        source=current.get('reported_base_git_sha','')
        if not source and current.get('source_binding','').split(';',1)[0]=='EXACT_INSTALLED_APK_SHA256_MATCH_TO_RELEASE_REGISTRY' and re.fullmatch(r'[0-9a-f]{64}',current.get('apk_sha256','')):
            source=current.get('matched_release_source_git_sha','')
        if (type(current.get('version_code')) is not int or not current.get('observed_at_utc')
                or not re.fullmatch(r'[0-9a-f]{40}',source)
                or not re.fullmatch(r'[0-9a-f]{64}',current.get('source_sha256',''))):
            raise ValueError('Current installation evidence is incomplete')
        versions.append(current['version_code'])
    baseline=max(versions)
    if code<=baseline:raise ValueError('Release versionCode must exceed the observed installed baseline'+str(baseline))
    return baseline

def verify_source_version(code, source_hash, state):
    current=state.get('last_observed_installed_build',{})
    if code==current.get('version_code'):
        if not re.fullmatch(r'[0-9a-f]{64}',source_hash) or source_hash!=current.get('source_sha256'):
            raise ValueError('Source does not match the observed installed Pixel build; assign a newer version to changed sources')
        return code
    return require_newer_version(code,state)

if __name__=='__main__':
    verify_source_layout()
    s=(ROOT/'android/app/build.gradle').read_text();code=int(re.search(r'versionCode (\d+)',s)[1]);name=re.search(r"versionName '([^']+)'",s)[1]
    sha=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    files=[p for p in (ROOT/'android/app/src/main').rglob('*') if p.is_file()]+[ROOT/'android/app/build.gradle']
    h=hashlib.sha256()
    for p in sorted(files):h.update(str(p.relative_to(ROOT)).encode());h.update(b'\0');h.update(p.read_bytes());h.update(b'\0')
    baseline=verify_source_version(code,h.hexdigest(),json.loads((ROOT/'AKTUELLER-STAND.json').read_text()))
    dirty=bool(subprocess.check_output(['git','status','--porcelain','--','android/app/src/main','android/app/build.gradle'],cwd=ROOT,text=True).strip())
    print(json.dumps(dict(version_name=name,version_code=code,git_sha=sha,source_sha256=h.hexdigest(),source_dirty=dirty,installed_baseline=baseline)))
