"""Run the actual laptop server with disposable fixtures, never productive data."""
import json
import os
import sys
import tempfile
from pathlib import Path
from http.server import ThreadingHTTPServer
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import db
import server


if __name__ == '__main__':
    with __import__("synthetic_fixtures").temporary_directory(prefix='hausgarten-qa-') as staging:
        root=Path(staging)
        db.DATA_DIR,db.DB_PATH,db.PHOTO_DIR,db.BACKUP_DIR=root,root/'qa.db',root/'photos',root/'backups'
        __import__("synthetic_fixtures").configure_db(db.DATA_DIR); db.init()
        c=db.save_customer({'id':'qa_cus_qa','name':'QA Gartenkunde','street':'Gartenstraße 1','city':'QA Beispielort','postal_code':'9560','country':'Österreich','phone':'+430000000','email':'test@example.invalid','active':1})
        o=db.save_order({'id':'qa_ord_qa','customer_id':c['id'],'title':'Herbstpflege','location':'Gartenstraße 1','status':'Geplant'})
        w=db.save_worklog({'id':'qa_wrk_qa','customer_id':c['id'],'order_id':o['id'],'work_date':'2026-10-08','hours':3,'activity':'Hecke pflegen','notes':'Interne Notiz'})
        db.save_invoice({'id':'qa_inv_qa','customer_id':c['id'],'invoice_number':'Entwurf-QA','invoice_date':'2026-10-08','service_start':'2026-10-08','service_end':'2026-10-08','positions':[{'title':'Pflege','quantity':3,'price':50,'worklog_id':w['id']}],'domestic_service_confirmed':True})
        http=server.AppHTTPServer(('127.0.0.1',int(os.environ.get('QA_PORT','18765'))),server.Handler)
        print(f'QA laptop: http://127.0.0.1:{http.server_port} — temporary synthetic data only',flush=True)
        try: http.serve_forever()
        finally: http.server_close()
