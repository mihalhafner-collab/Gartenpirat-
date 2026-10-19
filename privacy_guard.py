"""Source/workspace/artifact privacy contracts; diagnostics never contain values.

Git object history is audited separately, read-only. Ignored workspace files are
not excluded. File classifications are derived from content and central rules.
"""
from __future__ import annotations
import ast
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import zipfile

from bootstrap_contract import COLLECTIONS, verify_bootstrap, verify_legacy_empty_bootstrap

ROOT = Path(__file__).resolve().parent
TEXT = {'.py','.js','.cjs','.java','.json','.html','.css','.xml','.gradle','.properties','.md','.txt','.cmd','.ps1','.log','.csv','.tsv','.yml','.yaml'}
SECRET = (re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
          re.compile(rb'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{24,}'),
          re.compile(rb'tunnel_[a-z0-9]{32}|org-[A-Za-z0-9]{18,64}'),
          re.compile(rb'\bAIza[A-Za-z0-9_-]{30,}|\bya29\.[A-Za-z0-9_-]{20,}'))
PRIVATE_SUFFIX = {'.db','.sqlite','.sqlite3','.jks','.keystore','.p12','.pfx','.key','.tar','.gz','.pdf','.docx','.xlsx','.7z'}
IMAGES={'.jpg','.jpeg','.png','.webp','.gif','.heic'}
RAW_BUSINESS_ID=re.compile(rb'\b(?:(?:cus|ord|wrk|inv|pho|off|svc)_(?:[A-Za-z0-9]+_)*[a-f0-9]{24,64}|(?:delete|customer)_change_[a-f0-9-]{32,36})\b')
MAX_MEMBER = 64 * 1024 * 1024
POLICY=json.loads((ROOT/'privacy_policy.json').read_text())
RETIRED=set(POLICY['retired_private_literal_hashes'])
RETIRED_LENGTHS={tuple(x) for x in POLICY['retired_literal_word_lengths']}
_archive_cache={}
_public_images=None


def retired_literal(raw):
    text=raw.decode('utf-8',errors='ignore')
    words=re.findall(r'[\wÄÖÜäöüß]+',text.casefold())
    for size in {len(x) for x in RETIRED_LENGTHS}:
        for i in range(len(words)-size+1):
            if tuple(map(len,words[i:i+size])) in RETIRED_LENGTHS and hashlib.sha256(' '.join(words[i:i+size]).encode()).hexdigest() in RETIRED:return True
    return False


class PrivacyFailure(ValueError):
    def __init__(self, findings):
        self.findings = findings
        super().__init__(json.dumps(findings, ensure_ascii=False))


def finding(name, kind):
    # A private filename can itself contain a name; report its digest only.
    return {'path_sha256': hashlib.sha256(str(name).encode()).hexdigest(), 'category': kind}


def _business_rows(value):
    if isinstance(value,list):
        for row in value:
            if isinstance(row,dict) and 'id' in row and any(k in row for k in ('customer_id','work_date','invoice_number','email','street')):yield row
    if isinstance(value, dict):
        if isinstance(value.get('id'),str) and any(k in value and not isinstance(value[k],(dict,list)) for k in ('customer_id','work_date','invoice_number','email','street')):
            yield value
        for key, item in value.items():
            if key in COLLECTIONS and isinstance(item, list):
                yield from (r for r in item if isinstance(r, dict))
            elif isinstance(item, dict):
                yield from _business_rows(item)


def synthetic_records(value):
    rows = list(_business_rows(value))
    return bool(rows) and all(str(r.get('id','')).startswith(('qa_','test_'))
                              and r.get('test_record') is True
                              and (not r.get('email') or str(r['email']).endswith('@example.invalid'))
                              and (not r.get('street') or str(r['street']).startswith(('QA ','Test '))) for r in rows)


def private_settings(value):
    if not isinstance(value,dict):return False
    for k,v in value.items():
        if k in {'owner_name','business_street','business_email','business_phone','business_iban','business_bic','home_address'} and isinstance(v,str) and v.strip():
            if not (v.startswith(('QA ','Test ')) or k=='business_email' and v.endswith('@example.invalid')):return True
        if isinstance(v,dict) and private_settings(v):return True
    return False


def public_image_hashes():
    global _public_images
    if _public_images is not None:return set(_public_images)
    paths=[]
    catalog=json.loads((ROOT/'android/app/src/main/assets/plant-catalog.json').read_text())
    paths += [ROOT/'android/app/src/main/assets'/image['path'] for plant in catalog['plants'] for image in plant['images']]
    _public_images=set(POLICY['public_brand_image_sha256']) | {hashlib.sha256(p.read_bytes()).hexdigest() for p in paths if p.is_file() and p.suffix.lower() in IMAGES}
    return set(_public_images)


def packaged_public_images(archive):
    allowed=public_image_hashes()
    # Older public botanical editions remain valid. Their own licensed catalog
    # must enumerate each picture; arbitrary photos are never admitted by folder.
    for name in archive.namelist():
        if not name.endswith('/plant-catalog.json'):continue
        try:
            catalog=json.loads(archive.read(name));parent=Path(name).parent
            for plant in catalog['plants']:
                for image in plant['images']:
                    relative=Path(image['path'])
                    if relative.is_absolute() or '..' in relative.parts or not str(relative).startswith('plant-images/'):continue
                    if not all(image.get(k) for k in ('author','license','source')):continue
                    member=archive.getinfo(str(parent/relative))
                    if member.file_size>MAX_MEMBER:continue
                    allowed.add(hashlib.sha256(archive.read(member)).hexdigest())
        except (KeyError,ValueError,TypeError):continue
    return allowed


def bootstrap_writers(source):
    """Conservative AST taint check for direct/aliased bootstrap writers."""
    tree = ast.parse(source); tainted = set()
    def seed(node):
        return any(isinstance(n, ast.Constant) and isinstance(n.value,str) and 'initial-data.json' in n.value
                   or isinstance(n,ast.Name) and n.id in tainted for n in ast.walk(node))
    for _ in range(4):
        for node in ast.walk(tree):
            if isinstance(node, (ast.Assign,ast.AnnAssign)) and node.value is not None and seed(node.value):
                for target in node.targets if isinstance(node,ast.Assign) else [node.target]:
                    if isinstance(target,ast.Name): tainted.add(target.id)
    for node in ast.walk(tree):
        if not isinstance(node,ast.Call): continue
        if isinstance(node.func,ast.Attribute) and node.func.attr in {'copy','copy2','copyfile','move','replace','rename'} and any(seed(arg) for arg in node.args):
            return True
        if isinstance(node.func,ast.Attribute) and node.func.attr in {'write_text','write_bytes','open'} and seed(node.func.value):
            if node.func.attr != 'open' or any(isinstance(n,ast.Constant) and isinstance(n.value,str) and any(c in n.value for c in 'wax+') for n in node.args):
                return True
        if isinstance(node.func,ast.Name) and node.func.id=='open' and node.args and seed(node.args[0]):
            if any(isinstance(n,ast.Constant) and isinstance(n.value,str) and any(c in n.value for c in 'wax+') for n in node.args[1:]): return True
    return False


def inspect_bytes(name, raw, *, artifact=False, depth=0):
    failures=[]; p=Path(name); suffix=p.suffix.lower()
    archive=suffix in {'.apk','.aab','.zip','.jar'} or raw.startswith(b'PK\x03\x04')
    cache_key=(name,hashlib.sha256(raw).digest()) if archive else None
    if cache_key in _archive_cache:return list(_archive_cache[cache_key])
    if RAW_BUSINESS_ID.search(raw):failures.append(finding(name,'RAW_OPERATIONAL_IDENTIFIER'))
    if raw.startswith(b'\xfe\xed\xfe\xed'):failures.append(finding(name,'SECRET_KEYSTORE'))
    if any(pattern.search(raw) for pattern in SECRET): failures.append(finding(name,'SECRET'))
    if suffix in TEXT | {'.dex','.class','.arsc'} and retired_literal(raw):failures.append(finding(name,'RETIRED_PRIVATE_LITERAL'))
    if p.name.startswith('.env') or suffix in PRIVATE_SUFFIX or p.name.endswith(('-wal','-shm','-journal')):
        failures.append(finding(name,'PRIVATE_RUNTIME_OR_SECRET'))
    if suffix=='.json' or raw.lstrip().startswith((b'{',b'[')):
        try:
            value=json.loads(raw)
            if list(_business_rows(value)) and not synthetic_records(value):
                failures.append(finding(name,'BUSINESS_RECORDS'))
            if private_settings(value):failures.append(finding(name,'PRIVATE_BUSINESS_SETTINGS'))
            if p.name=='initial-data.json': verify_legacy_empty_bootstrap(raw) if artifact else verify_bootstrap(raw)
        except (ValueError,UnicodeError):
            if p.name=='initial-data.json': failures.append(finding(name,'NONEMPTY_OR_INVALID_BOOTSTRAP'))
    if raw.startswith(b'SQLite format 3\x00') and suffix not in PRIVATE_SUFFIX:
        failures.append(finding(name,'PRIVATE_SQLITE_CONTAINER'))
    if archive and depth>=3:failures.append(finding(name,'ARCHIVE_DEPTH_LIMIT'))
    if depth<3 and archive:
        try:
            with zipfile.ZipFile(io.BytesIO(raw)) as z:
                if len(z.infolist())>50000 or sum(x.file_size for x in z.infolist())>1024*1024*1024: raise ValueError()
                allowed_images=None
                for member in z.infolist():
                    if member.is_dir():continue
                    if member.file_size>MAX_MEMBER: raise ValueError()
                    content=z.read(member)
                    if Path(member.filename).suffix.lower() in IMAGES and not member.filename.startswith(('res/','base/res/')):
                        if allowed_images is None:allowed_images=packaged_public_images(z)
                        digest=hashlib.sha256(content).hexdigest()
                        if digest not in allowed_images:
                            try:
                                proof=json.loads(z.read(member.filename+'.privacy.json'))
                                valid=proof.get('classification')=='SYNTHETIC_TEST' and str(proof.get('fixture','')).startswith('qa_') and proof.get('sha256')==digest
                            except (KeyError,ValueError):valid=False
                            if not valid:failures.append(finding(name+'!/'+member.filename,'UNCLASSIFIED_IMAGE'))
                    failures.extend(inspect_bytes(name+'!/'+member.filename,content,artifact=True,depth=depth+1))
        except (ValueError,zipfile.BadZipFile,RuntimeError): failures.append(finding(name,'UNSCANNABLE_ARCHIVE'))
    if cache_key is not None:_archive_cache[cache_key]=list(failures)
    return failures


def scan_files(files, *, artifact=False):
    failures=[]
    for name,raw in files.items(): failures.extend(inspect_bytes(name,raw,artifact=artifact))
    if failures: raise PrivacyFailure(failures)


def source_gate(root=ROOT, filenames=None):
    root=Path(root).resolve()
    verify_bootstrap((root/'android/app/src/main/assets/initial-data.json').read_bytes())
    names=filenames if filenames is not None else subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=root).decode().split('\0')
    failures=[];count=0
    for name in names:
        if not name:continue
        path=root/name
        if not path.is_file():continue
        raw=path.read_bytes(); count+=1
        failures.extend(inspect_bytes(name,raw))
        if path.suffix=='.py' and name!='tools/empty_bootstrap.py' and bootstrap_writers(raw.decode('utf-8-sig')):
            failures.append(finding(name,'BOOTSTRAP_WRITER'))
        if path.suffix in {'.js','.cjs','.ps1','.sh','.cmd','.bat'}:
            code=raw.decode('utf-8-sig')
            if 'initial-data.json' in code and re.search(r'writeFile(?:Sync)?\s*\(|copyFile(?:Sync)?\s*\(|rename(?:Sync)?\s*\(|WriteAll(?:Text|Bytes)|Set-Content|Copy-Item|Move-Item|\b(?:cp|mv|tee)\s|>\s*[^\n]*initial-data',code):
                failures.append(finding(name,'BOOTSTRAP_WRITER'))
    if failures:raise PrivacyFailure(failures)
    return {'status':'PASS','files':count,'operational_seed_rows':0}


def workspace_gate(root=ROOT):
    root=Path(root).resolve(); failures=[]; count=0
    for base,dirs,names in os.walk(root,followlinks=False):
        # Only this checkout's actual Git history is a separate audit scope.
        # A nested directory merely named .git must not hide runtime files.
        if Path(base)==root:dirs[:]=[n for n in dirs if n!='.git']
        for name in list(dirs)+names:
            path=Path(base)/name; rel=path.relative_to(root).as_posix()
            if path.is_symlink():
                failures.append(finding(rel,'UNCLASSIFIED_SYMLINK'));continue
            if not path.is_file():continue
            count+=1
            raw=path.read_bytes()
            failures.extend(inspect_bytes(rel,raw,artifact=rel.startswith("android/app/build/")))
            if path.suffix.lower() in IMAGES:
                public=hashlib.sha256(raw).hexdigest() in public_image_hashes() or (
                    rel.startswith('android/app/build/') and '/res/' in rel)
                if not public:
                    try:
                        proof=json.loads(Path(str(path)+'.privacy.json').read_text())
                        synthetic=(proof.get('classification')=='SYNTHETIC_TEST' and str(proof.get('fixture','')).startswith('qa_')
                                   and proof.get('sha256')==hashlib.sha256(raw).hexdigest())
                    except (OSError,ValueError):synthetic=False
                    if not synthetic:failures.append(finding(rel,'UNCLASSIFIED_IMAGE'))
    if failures:raise PrivacyFailure(failures)
    return {'status':'PASS','files':count,'includes_ignored_untracked':True,'git_history':'SEPARATE_READONLY_AUDIT'}


def artifact_gate(path, *, instrumentation=False):
    path=Path(path)
    name=str(path.resolve().relative_to(ROOT)) if path.resolve().is_relative_to(ROOT) else path.name
    failures=inspect_bytes(name,path.read_bytes(),artifact=True)
    if failures:raise PrivacyFailure(failures)
    if path.suffix in {'.apk','.aab'} and not instrumentation:
        with zipfile.ZipFile(path) as z:verify_legacy_empty_bootstrap(z.read('assets/initial-data.json' if path.suffix=='.apk' else 'base/assets/initial-data.json'))
    return {'status':'PASS','sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'operational_seed_rows':0}
