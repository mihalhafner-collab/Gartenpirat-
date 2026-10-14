"""Read-only localhost QA of the real Android assets with memory-only demo data.

No productive database, Drive, invoice issue or outbound sharing access.
"""
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'android' / 'app' / 'src' / 'main' / 'assets'
MOCK = r'''<script>
(() => {
  const customer={id:'cus_demo',name:'Demo Gartenkunde',street:'Gartenweg 1',postal_code:'9555',city:'Glanegg',country:'Österreich',active:1};
  const work={id:'wrk_demo',customer_id:customer.id,work_date:'2026-10-08',activity:'Hecke pflegen und Schnittgut entfernen',hours:3,billed:0,material:'',kilometers:0};
  const master={customers:[customer],orders:[{id:'ord_demo',customer_id:customer.id,title:'Herbstpflege',status:'Geplant'}],worklogs:[work],photos:[],invoices:[{id:'inv_demo',customer_id:customer.id,invoice_number:'Entwurf-Demo',status:'Entwurf',invoice_date:'2026-10-08',service_start:'2026-10-08',service_end:'2026-10-08',payment_days:14,amount:150,positions:[{title:work.activity,description:'08.10.2026',quantity:3,price:50,unit:'Std.',tax_rate:0,discount:0,worklog_id:work.id}],domestic_service_confirmed:true}],invoice_issuer:{owner_name:'Demo Aussteller',business_street:'Betriebsweg 2',business_postal_code:'9560',business_city:'Feldkirchen',business_email:'demo@example.invalid'}};
  const pending=[];
  function save(kind,content){const payload=JSON.parse(content);payload.id||=kind+'_demo_'+Date.now();pending.push({client_id:crypto.randomUUID(),kind,payload});return payload.id;}
  window.AndroidData={getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:false,syncing:false,pending:pending.length}),saveInvoice:content=>save('invoice',content),saveCustomer:content=>save('customer',content),saveWorklog:content=>save('worklog',content),saveOrder:content=>save('order',content),savePhoto:content=>save('photo',content),synchronize:()=>{},requestInvoiceIssue:()=> 'ERROR: QA: kein Rechnungsabschluss erlaubt',shareInvoiceVia:()=> 'ERROR: QA: kein Versand erlaubt',removeEntity:()=> 'ERROR: QA: Löschen nicht erlaubt',undoRemoveEntity:()=> 'ERROR: QA: Löschen nicht erlaubt'};
  window.AndroidSpeech={start:()=>{}};
})();
</script>'''


class Handler(SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path in ('/', '/mobile.html'):
            text=(ASSETS/'mobile.html').read_text(encoding='utf-8').replace('<script src="invoice-flow.js"', MOCK+'<script src="invoice-flow.js"',1)
            content=text.encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type','text/html; charset=utf-8')
            self.send_header('Cache-Control','no-store')
            self.send_header('Content-Length',str(len(content)))
            self.end_headers()
            self.wfile.write(content)
        elif self.path.startswith('/initial-data.json'):
            self.send_error(404)
        else:
            super().do_GET()


if __name__=='__main__':
    server=ThreadingHTTPServer(('127.0.0.1',0),partial(Handler,directory=str(ASSETS)))
    print(f'Android UI QA: http://127.0.0.1:{server.server_address[1]}; demo data only',flush=True)
    server.serve_forever()
