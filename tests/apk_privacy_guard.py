"""Permanent release gate: distributable program, never embedded operational data.
Prints categories/filenames only, not private values. Optional APK scan is read-only.
"""
import argparse,json,re,sqlite3,zipfile,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'android/app/src/main/assets'
COLLECTIONS=('customers','orders','worklogs','invoices','photos')
def private_markers():
    values=set()
    # Read-only reference, no migrations or connection to Drive.
    path=ROOT/'data/haus-garten.db'
    if path.exists():
        c=sqlite3.connect(path.as_uri()+'?mode=ro',uri=True)
        try:
            for table in COLLECTIONS:
                for row in c.execute('SELECT * FROM '+table):
                    names=[d[0] for d in c.execute('SELECT * FROM '+table+' LIMIT 0').description]
                    data=dict(zip(names,row))
                    for key in ('id','email','phone','street','sevdesk_contact_id',*(('name','company') if table=='customers' else ())):
                        value=str(data.get(key) or '').strip()
                        if len(value)>=8:values.add(value)
            for key,value in c.execute("SELECT key,value FROM settings"):
                if key in ('business_iban','owner_name','business_street','business_email') and len(value or '')>=8:values.add(value)
        finally:c.close()
    return values
def inspect():
    seed=json.loads((ASSETS/'initial-data.json').read_text(encoding='utf-8'))
    if any(seed.get(k)!=[] for k in COLLECTIONS):raise ValueError('Startdaten sind nicht leer.')
    if set(seed)-set(COLLECTIONS)-{'format','accepted_ids','sync_errors','capabilities'}:raise ValueError('Private Konfiguration in Startdaten.')
    files=list(ASSETS.rglob('*'))+list((ROOT/'android/app/src/main/java').rglob('*'))+list((ROOT/'android/app/src/main/res').rglob('*'))
    return {str(p.relative_to(ROOT)):p.read_bytes() for p in files if p.is_file()}
def scan(files,markers):
    failures=[]
    for name,content in files.items():
        if re.search(rb'\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}',content):failures.append((name,'API-Schlüssel'))
        if re.search(rb'\bAIza[A-Za-z0-9_-]{30,}|\bya29\.[A-Za-z0-9_-]{20,}',content):failures.append((name,'Google-Zugangsschlüssel'))
        if re.search(rb'tunnel_[a-z0-9]{32}|org-[a-zA-Z0-9]{18,64}',content):failures.append((name,'Private Verbindungskennung'))
        if any(v.encode('utf-8') in content or v.encode('utf-16le') in content for v in markers):failures.append((name,'Betriebsdaten-Marker'))
        if name.lower().endswith(('.db','.sqlite','.sqlite3','.keystore','.jks','.p12','.pfx','.key','.env','qa-signing-password.txt')):failures.append((name,'Private Speicherdatei'))
    if failures:raise ValueError(json.dumps(failures,ensure_ascii=False))
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apk');args=parser.parse_args()
    markers=private_markers();scan(inspect(),markers)
    if args.apk:
        with zipfile.ZipFile(args.apk) as z:
            files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
            seed=json.loads(files['assets/initial-data.json'])
            if any(seed.get(k)!=[] for k in COLLECTIONS):raise ValueError('APK enthält Betriebsdaten.')
            scan(files,markers)
    print(json.dumps({'source_privacy':'PASS','artifact_privacy':'PASS' if args.apk else 'not_run','private_markers_checked':len(markers),'operational_seed_rows':0,'secret_values_printed':False}))
if __name__=='__main__':
    try:main()
    except Exception as error:print('PRIVACY_GATE_FAILED '+str(error));sys.exit(1)
