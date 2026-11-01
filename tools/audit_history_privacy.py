"""Read-only metadata audit; never checks out, copies or rewrites Git objects."""
import hashlib,json,subprocess,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
PATH='app/src/main/assets/initial-data.json'
sys.path.insert(0,str(ROOT))
from privacy_guard import retired_literal

def audit():
    revisions=subprocess.check_output(['git','log','--all','--format=%H','--',PATH],cwd=ROOT,text=True).splitlines()
    seen=set();findings=[];synthetic=0
    for revision in revisions:
        probe=subprocess.run(['git','show',revision+':'+PATH],cwd=ROOT,capture_output=True)
        if probe.returncode:continue
        digest=hashlib.sha256(probe.stdout).hexdigest()
        if digest in seen:continue
        seen.add(digest)
        try:value=json.loads(probe.stdout)
        except ValueError:findings.append({'object_sha256':digest,'classification':'UNREADABLE'});continue
        counts={k:len(value.get(k,[])) for k in ('customers','orders','worklogs','invoices','offers','photos') if isinstance(value.get(k,[]),list)}
        if any(counts.values()):
            rows=[r for k in counts for r in value.get(k,[]) if isinstance(r,dict)]
            fake=all(str(r.get('id','')).startswith(('qa_','test_')) and r.get('test_record') is True for r in rows)
            if fake:synthetic+=1
            else:findings.append({'object_sha256':digest,'counts':counts,'classification':'SENSITIVE_OPERATIONAL_HISTORY'})
    for name in ('tests/test_app.py','static/app.js','verify_invoice_pdf.py'):
        commits=subprocess.check_output(['git','log','--all','--format=%H','--',name],cwd=ROOT,text=True).splitlines()
        for revision in commits:
            probe=subprocess.run(['git','show',revision+':'+name],cwd=ROOT,capture_output=True)
            digest=hashlib.sha256(probe.stdout).hexdigest()
            if probe.returncode or digest in seen:continue
            seen.add(digest)
            if retired_literal(probe.stdout):findings.append({'object_sha256':digest,'path_sha256':hashlib.sha256(name.encode()).hexdigest(),'classification':'SENSITIVE_ISSUER_FIXTURE_HISTORY'})
    return {'history_rewritten':False,'historical_objects_checked':len(seen),'synthetic_objects':synthetic,'classification':'produktive sensible Daten in Historie gefunden' if findings else 'keine produktiven Daten gefunden','findings':findings,'scope':'all reachable bootstrap and known issuer-fixture revisions; no complete forensic history claim'}

if __name__=='__main__':print(json.dumps(audit(),ensure_ascii=False))
