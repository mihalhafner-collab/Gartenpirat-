"""Read-only production integrity audit. Never calls db.init or writable connect."""
import ast
import hashlib
import json
import math
import re
import sqlite3
from datetime import date
from pathlib import Path

root=Path(__file__).resolve().parents[1]
target=root/'data'/'haus-garten.db'
before=hashlib.sha256(target.read_bytes()).hexdigest()
database=sqlite3.connect(target.as_uri()+'?mode=ro',uri=True)
database.row_factory=sqlite3.Row
result={'sqlite_integrity':database.execute('PRAGMA integrity_check').fetchone()[0],
        'foreign_key_violations':[tuple(row) for row in database.execute('PRAGMA foreign_key_check')],
        'counts':{},'relations':{},'invalid_worklogs':[],'missing_photo_files':[],
        'duplicate_primary_key_groups':{},'duplicate_source_key_groups':{},'invoice_link_errors':[]}
for table in ('customers','orders','worklogs','invoices','photos'):
    result['counts'][table]=database.execute(f'SELECT count(*) FROM {table}').fetchone()[0]
    result['duplicate_primary_key_groups'][table]=database.execute(f'SELECT count(*) FROM (SELECT id FROM {table} GROUP BY id HAVING count(*)>1)').fetchone()[0]
for table,field in [('customers','sevdesk_contact_id'),('invoices','sevdesk_id')]:
    result['duplicate_source_key_groups'][table]=database.execute(f'SELECT count(*) FROM (SELECT {field} FROM {table} WHERE {field} IS NOT NULL GROUP BY {field} HAVING count(*)>1)').fetchone()[0]
for name,sql in {
    'worklog_customer_order_mismatch':'SELECT w.id FROM worklogs w JOIN orders o ON o.id=w.order_id WHERE w.customer_id<>o.customer_id',
    'invoice_customer_order_mismatch':'SELECT i.id FROM invoices i JOIN orders o ON o.id=i.order_id WHERE i.customer_id<>o.customer_id',
    'photo_customer_worklog_mismatch':'SELECT p.id FROM photos p JOIN worklogs w ON w.id=p.worklog_id WHERE p.customer_id<>w.customer_id',
    'photo_customer_order_mismatch':'SELECT p.id FROM photos p JOIN orders o ON o.id=p.order_id WHERE p.customer_id<>o.customer_id',
    'historical_worklogs_without_order':'SELECT id FROM worklogs WHERE order_id IS NULL',
}.items(): result['relations'][name]=[row[0] for row in database.execute(sql)]
for row in database.execute('SELECT id,work_date,hours,break_minutes,kilometers FROM worklogs'):
    problems=[]
    try: date.fromisoformat(row['work_date'])
    except (ValueError,TypeError): problems.append('invalid_date')
    for field in ('hours','break_minutes','kilometers'):
        if row[field] is None or not math.isfinite(row[field]) or row[field]<0: problems.append('invalid_'+field)
    if problems: result['invalid_worklogs'].append({'id':row['id'],'problems':problems})
for row in database.execute('SELECT id,filename FROM photos'):
    if not (root/'data'/'photos'/Path(row['filename']).name).is_file(): result['missing_photo_files'].append(row['id'])
for row in database.execute('SELECT d.invoice_id,d.payload,i.customer_id FROM invoice_details d LEFT JOIN invoices i ON i.id=d.invoice_id'):
    try: detail=json.loads(row['payload'])
    except (TypeError,ValueError):
        result['invoice_link_errors'].append({'invoice_id':row['invoice_id'],'error':'invalid_payload'});continue
    for identifier in detail.get('worklog_ids',[]):
        work=database.execute('SELECT customer_id,billed FROM worklogs WHERE id=?',(identifier,)).fetchone()
        if work is None or work['customer_id']!=row['customer_id']:
            result['invoice_link_errors'].append({'invoice_id':row['invoice_id'],'worklog_id':identifier,'error':'invalid_relation'})
        elif detail.get('issued_at') and not work['billed']:
            result['invoice_link_errors'].append({'invoice_id':row['invoice_id'],'worklog_id':identifier,'error':'issued_but_unbilled'})
database.close()
result['database_file_unchanged']=before==hashlib.sha256(target.read_bytes()).hexdigest()
inventory=[]
for file in [*root.glob('*.py'),*root.glob('static/*.js'),*root.glob('android/app/src/main/assets/*.js'),root/'android/app/src/main/assets/mobile.html',*root.glob('android/app/src/main/java/**/*.java')]:
    text=file.read_text(encoding='utf-8')
    if file.suffix=='.py':
        functions=[n.name for n in ast.walk(ast.parse(text)) if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef))]
    else: functions=re.findall(r'(?:function\s+([\w]+)\s*\(|(?:public|private|protected)\s+(?:static\s+)?\w+\s+(\w+)\s*\()',text)
    inventory.append({'file':str(file.relative_to(root)), 'bytes':file.stat().st_size,'functions':functions})
(root/'outputs'/'QA-Produktivdaten-readonly.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
(root/'outputs'/'QA-Code-Inventar.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({**result,'relations':{key:len(ids) for key,ids in result['relations'].items()}},ensure_ascii=False,indent=2))
