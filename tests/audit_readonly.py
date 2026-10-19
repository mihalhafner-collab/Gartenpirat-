"""Explicit private read-only integrity audit. Outputs technical metadata only."""
import hashlib,json,sqlite3,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import runtime_paths


def audit(target):
    target=runtime_paths.business_path(target)
    before=hashlib.sha256(target.read_bytes()).hexdigest()
    with sqlite3.connect(target.as_uri()+'?mode=ro',uri=True) as database:
        database.execute('PRAGMA query_only=ON');database.execute('BEGIN')
        tables={r[0] for r in database.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        result={'sqlite_integrity':database.execute('PRAGMA integrity_check').fetchone()[0],'foreign_key_violation_count':len(database.execute('PRAGMA foreign_key_check').fetchall()),'counts':{},'collection_hashes':{},'schema_hashes':{},'relation_hashes':{}}
        for table in ('customers','orders','worklogs','invoices','offers','photos','service_catalog'):
            if table not in tables:continue
            cursor=database.execute('SELECT * FROM '+table+' ORDER BY id');names=[r[0] for r in cursor.description];rows=cursor.fetchall()
            result['counts'][table]=len(rows)
            result['collection_hashes'][table]=hashlib.sha256(json.dumps(rows,sort_keys=True,separators=(',',':'),default=str).encode()).hexdigest()
            result['schema_hashes'][table]=hashlib.sha256(json.dumps(database.execute('PRAGMA table_info('+table+')').fetchall(),separators=(',',':')).encode()).hexdigest()
            columns=[i for i,n in enumerate(names) if n=='id' or n.endswith('_id')]
            result['relation_hashes'][table]=hashlib.sha256(json.dumps([[r[i] for i in columns] for r in rows],separators=(',',':')).encode()).hexdigest()
    result['database_file_unchanged']=before==hashlib.sha256(target.read_bytes()).hexdigest()
    return result


if __name__=='__main__':
    print(json.dumps(audit(runtime_paths.private_root()/'data/haus-garten.db')))
