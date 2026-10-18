"""Ordinary Austrian domestic small-business invoices; no cross-border tax automation."""
import hashlib
import json
import re
import sys
from pathlib import Path
from datetime import date, timedelta
from io import BytesIO
from xml.sax.saxutils import escape

import db
sys.path.insert(0, str(Path(__file__).resolve().parent / '.dependencies'))


def prepare(database, identifier):
    row = database.execute('SELECT * FROM invoices WHERE id=?', (identifier,)).fetchone()
    if not row:
        raise ValueError('Rechnung nicht gefunden.')
    invoice = dict(row)
    detail = database.execute('SELECT payload FROM invoice_details WHERE invoice_id=?', (identifier,)).fetchone()
    if not detail or invoice['sevdesk_id']:
        raise ValueError('Nur in der App erstellte Rechnungsentwürfe können hier abgeschlossen werden.')
    details = json.loads(detail['payload'])
    if details.get('issued_at'):
        return {**invoice, **details}
    if invoice['status'] != 'Entwurf':
        raise ValueError('Nur Entwürfe können abgeschlossen werden.')
    customer = database.execute('SELECT * FROM customers WHERE id=?', (invoice['customer_id'],)).fetchone()
    settings = {r['key']: r['value'] for r in database.execute('SELECT * FROM settings')}
    errors = []
    required = [('owner_name', 'vollständiger rechtlicher Ausstellername'), ('business_street', 'Geschäftsstraße'),
                ('business_postal_code', 'Geschäfts-PLZ'), ('business_city', 'Geschäftsort')]
    for key, label in required:
        if not settings.get(key, '').strip(): errors.append(label)
    if settings.get('invoice_issuer_confirmed') != '1': errors.append('Bestätigung der rechtlichen Ausstellerdaten')
    if settings.get('invoice_numbering_confirmed') != '1': errors.append('Bestätigung des eigenen Nummernkreises')
    prefix = settings.get('invoice_prefix', '').strip()
    number_format = settings.get('invoice_number_format','own')
    if number_format == 'sevdesk':
        if not settings.get('invoice_sevdesk_checked_at'): errors.append('aktueller sevDesk-Nummernstand')
    elif not re.fullmatch(r'[A-Za-z][A-Za-z0-9-]{0,19}', prefix): errors.append('eindeutiges Rechnungspräfix (z. B. HG)')
    if not customer or any(not str(customer[k] or '').strip() for k in ('name','street','postal_code','city')):
        errors.append('vollständiger Kundenname und Rechnungsadresse')
    if customer and str(customer['country'] or '').strip().lower() not in ('österreich','austria','at'):
        errors.append('Kundenland Österreich; Ausland muss gesondert geprüft werden')
    if not details.get('domestic_service_confirmed'): errors.append('Bestätigung einer gewöhnlichen Inlandsleistung ohne Sonderfall')
    dates = {}
    for key,label in [('invoice_date','Rechnungsdatum'),('service_start','Leistungsbeginn'),('service_end','Leistungsende')]:
        try: dates[key] = date.fromisoformat(invoice[key] or '')
        except ValueError: errors.append(label)
    if dates.get('service_start') and dates.get('service_end') and dates['service_end'] < dates['service_start']:
        errors.append('Leistungsende darf nicht vor Leistungsbeginn liegen')
    if dates.get('invoice_date') and dates['invoice_date'] != date.today(): errors.append('Rechnungsdatum muss beim Abschluss heute sein')
    if dates.get('service_end') and dates['service_end'] > date.today(): errors.append('Leistung liegt noch in der Zukunft; Anzahlungen sind nicht unterstützt')
    calculated = db.calculate_invoice(details)
    if abs(calculated['gross_total']) <= 0: errors.append('positiver Rechnungsbetrag')
    for worklog_id in calculated['worklog_ids']:
        worklog = database.execute('SELECT customer_id,billing_customer_id,billed FROM worklogs WHERE id=?',(worklog_id,)).fetchone()
        if not worklog or (worklog['billing_customer_id'] or worklog['customer_id']) != invoice['customer_id'] or worklog['billed']:
            errors.append('verknüpfte Arbeit ist bereits abgerechnet oder gehört nicht zum Kunden')
    if calculated.get('document_type') in ('replacement','cancellation'):
        original=database.execute('SELECT i.status,d.payload FROM invoices i JOIN invoice_details d ON d.invoice_id=i.id WHERE i.id=?',(calculated['correction_of'],)).fetchone()
        if not original or not json.loads(original['payload']).get('issued_at'):errors.append('ausgestellter Originalbeleg der Korrektur fehlt')
        elif calculated['document_type']=='cancellation':
            if original['status']=='Storniert':errors.append('Originalbeleg wurde bereits storniert')
            frozen=json.loads(original['payload'])
            expected_positions=[{**p,'worklog_id':''} for p in frozen.get('positions',[])]
            actual=[{**p,'net':abs(p.get('net',0)),'tax':abs(p.get('tax',0))} for p in calculated['positions']]
            import sync_review
            if sync_review.digest(expected_positions)!=sync_review.digest(actual):errors.append('Stornopositionen entsprechen nicht dem unveränderten Original')
    allocated = set()
    for detail_row in database.execute('SELECT invoice_id,payload FROM invoice_details WHERE invoice_id<>?', (identifier,)):
        previous = json.loads(detail_row['payload'])
        if previous.get('issued_at'): allocated.update(previous.get('worklog_ids', []))
    if allocated.intersection(calculated['worklog_ids']): errors.append('Arbeitsaufzeichnung wurde bereits in einer abgeschlossenen Rechnung verwendet')
    if errors: raise ValueError('Vor dem Abschluss bitte prüfen: ' + '; '.join(errors) + '.')
    issuer = {key:settings.get(key,'') for key in ['business_name','owner_name','business_street','business_postal_code','business_city','business_legal_info','business_iban','business_phone','business_email','business_bank','business_bic']}
    receiver = {key:customer[key] for key in ['name','company','street','postal_code','city','country','email','phone','sevdesk_customer_number']}
    return {**invoice, **calculated, 'issuer':issuer, 'recipient':receiver, 'prefix':prefix,
            'number_format':number_format,'sevdesk_last_number':int(settings.get('invoice_sevdesk_last_number','0')),
            'due_date':(dates['invoice_date'] + timedelta(days=calculated['payment_days'])).isoformat()}


def render_pdf(invoice, draft=False):
    from reportlab.lib import colors
    from reportlab.lib.styles import ParagraphStyle
    from reportlab.lib.units import mm
    from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether
    from reportlab.pdfgen.canvas import Canvas
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    font_dir=Path(__file__).parent/'assets'/'fonts'
    for font in ['Regular','Bold']:
        name='OpenSans-'+font
        if name not in pdfmetrics.getRegisteredFontNames(): pdfmetrics.registerFont(TTFont(name,str(font_dir/(name+'.ttf'))))
    styles = {name:ParagraphStyle(name,fontName=font,fontSize=size,leading=leading,alignment=align)
              for name,font,size,leading,align in [('Normal','OpenSans-Regular',9,12,0),('Bold','OpenSans-Bold',9,12,0),
              ('Title','OpenSans-Bold',13,17,0),('Right','OpenSans-Regular',9,12,2),('RightBold','OpenSans-Bold',9,12,2),('Small','OpenSans-Regular',7,10,0)]}
    def p(text, style='Normal'): return Paragraph(escape(str(text or '')).replace('\n','<br/>'), styles[style])
    def currency(value): return f'{float(value):,.2f}'.replace(',','X').replace('.',',').replace('X','.') + ' EUR'
    def display_date(value): return date.fromisoformat(value).strftime('%d.%m.%Y')
    output = BytesIO()
    issuer, recipient = invoice['issuer'], invoice['recipient']
    headline = 'ENTWURF - keine ausgestellte Rechnung' if draft else ('Stornorechnung Nr. ' if invoice.get('document_type')=='cancellation' else 'Korrekturrechnung Nr. ' if invoice.get('document_type')=='replacement' else 'Rechnungs Nr. ')+invoice['invoice_number']
    story = [p(headline,'Title'),Spacer(1,6*mm),p(invoice.get('header_text') or
        'Sehr geehrte Damen und Herren,\n\nvielen Dank für Ihren Auftrag und das damit verbundene Vertrauen!\nHiermit stelle ich Ihnen die folgenden Leistungen in Rechnung:'),Spacer(1,4*mm)]
    rows = [[p('Pos.','Bold'),p('Beschreibung','Bold'),p('Menge','RightBold'),p('Einzelpreis','RightBold'),p('Gesamtpreis','RightBold')]]
    for index,line in enumerate(invoice['positions'],1):
        description = line.get('description','') + (f"\nRabatt: {line['discount']:g} %" if line['discount'] else '')
        rows.append([p(f'{index}.'),[p(line['title'],'Bold'),p(description)] if description else p(line['title'],'Bold'),
            p(f"{line['quantity']:.2f}".replace('.',',')+' '+line['unit'],'Right'),p(currency(line['price']),'Right'),p(currency(line['net']),'Right')])
    table = Table(rows,colWidths=[11*mm,89*mm,23*mm,23*mm,24*mm],repeatRows=1,hAlign='LEFT')
    grey=colors.HexColor('#ebebeb')
    table.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),grey),('VALIGN',(0,0),(-1,-1),'TOP'),
        ('LEFTPADDING',(0,0),(-1,-1),3),('RIGHTPADDING',(0,0),(-1,-1),3),('TOPPADDING',(0,0),(-1,-1),5),('BOTTOMPADDING',(0,0),(-1,-1),7)]))
    summary=Table([[p('Gesamtbetrag netto'),p(currency(invoice['net_total']),'Right')],
        [p(invoice['tax_exemption']), ''],[p('Gesamtbetrag brutto','Bold'),p(currency(invoice['gross_total']),'RightBold')]],colWidths=[141*mm,29*mm])
    summary.setStyle(TableStyle([('BACKGROUND',(0,0),(-1,0),grey),('BACKGROUND',(0,2),(-1,2),grey),('SPAN',(0,1),(-1,1)),
        ('TOPPADDING',(0,0),(-1,-1),4),('BOTTOMPADDING',(0,0),(-1,-1),4)]))
    story += [table,Spacer(1,2*mm),KeepTogether([summary]),Spacer(1,7*mm),
        p('Gutschrift aus Storno. Die Verrechnung oder Rückzahlung wird gesondert vereinbart.' if invoice.get('document_type')=='cancellation' else f"Zahlungsbedingungen: Zahlung innerhalb von {invoice['payment_days']} Tagen, ohne Abzüge.\nFällig am {display_date(invoice['due_date'])}."),Spacer(1,4*mm),
        p(('Bezug und Originalbeleg bleiben im Archiv erhalten.' if invoice.get('document_type')=='cancellation' else invoice.get('footer_text')) or 'Bitte überweisen Sie den Rechnungsbetrag unter Angabe der Rechnungsnummer auf das unten angegebene Konto.\n\nMit freundlichen Grüßen\n'+issuer['owner_name'])]
    logo=Path(__file__).parent/'android'/'app'/'src'/'main'/'assets'/'hafner-logo.png'
    def heading(canvas,doc):
        canvas.setFillColor(colors.black)
        if logo.exists(): canvas.drawImage(str(logo),155*mm,248*mm,width=38*mm,height=38*mm,preserveAspectRatio=True,mask='auto')
        if doc.page == 1:
            canvas.setFont('OpenSans-Regular',6.5);canvas.drawString(20*mm,247*mm,f"{issuer['owner_name']} - {issuer['business_street']} - {issuer['business_postal_code']} {issuer['business_city']}")
            receiver = '\n'.join(dict.fromkeys(filter(None,[recipient.get('company'),recipient['name'],recipient['street'],f"{recipient['postal_code']} {recipient['city']}",'Österreich'])))
            block=p(receiver);block.wrap(95*mm,45*mm);block.drawOn(canvas,20*mm,236*mm-block.height)
            meta=[['Rechnungs-Nr.','ENTWURF' if draft else invoice['invoice_number']],['Rechnungsdatum',display_date(invoice['invoice_date'])],
                ['Leistungszeitraum',f"{display_date(invoice['service_start'])} - {display_date(invoice['service_end'])}"],
                ['Ihre Kundennummer',recipient.get('sevdesk_customer_number') or '-'],['Ihr Ansprechpartner',issuer['owner_name']]]
            block=Table([[p(a,'Bold' if i==0 else 'Small'),p(b,'RightBold' if i==0 else 'Right')] for i,(a,b) in enumerate(meta)],colWidths=[31*mm,35*mm])
            block.setStyle(TableStyle([('LEFTPADDING',(0,0),(-1,-1),0),('RIGHTPADDING',(0,0),(-1,-1),0),('VALIGN',(0,0),(-1,-1),'TOP')]))
            block.wrap(66*mm,50*mm);block.drawOn(canvas,124*mm,244*mm-block._height)
        else:
            canvas.setFont('OpenSans-Bold',11);canvas.drawString(20*mm,270*mm,headline)
    class PageCanvas(Canvas):
        def __init__(self,*args,**kwargs): super().__init__(*args,**kwargs);self.saved=[]
        def showPage(self): self.saved.append(dict(self.__dict__));self._startPage()
        def save(self):
            pages=len(self.saved)
            for state in self.saved:
                self.__dict__.update(state)
                self.setFont('OpenSans-Regular',7);self.drawRightString(190*mm,32*mm,f'{self._pageNumber}/{pages}')
                blocks=[f"{issuer['owner_name']}\n{issuer['business_street']}\n{issuer['business_postal_code']} {issuer['business_city']}\nÖsterreich",
                    '\n'.join(filter(None,['Tel. '+issuer['business_phone'] if issuer.get('business_phone') else '', 'E-Mail '+issuer['business_email'] if issuer.get('business_email') else ''])),
                    issuer.get('business_legal_info',''), '\n'.join(filter(None,[issuer.get('business_bank',''),'IBAN '+issuer['business_iban'] if issuer.get('business_iban') else '', 'BIC '+issuer['business_bic'] if issuer.get('business_bic') else '']))]
                foot=Table([[p(text,'Small') for text in blocks]],colWidths=[43*mm,43*mm,43*mm,43*mm]);foot.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),0),('RIGHTPADDING',(0,0),(-1,-1),6)]));foot.wrap(172*mm,25*mm);foot.drawOn(self,20*mm,max(6*mm,21*mm-foot._height))
                Canvas.showPage(self)
            Canvas.save(self)
    SimpleDocTemplate(output,pagesize=(210*mm,297*mm),rightMargin=20*mm,leftMargin=20*mm,topMargin=85*mm,bottomMargin=40*mm,
        title=headline,author=issuer['owner_name']).build(story,onFirstPage=heading,onLaterPages=heading,canvasmaker=PageCanvas)
    return output.getvalue()


def preview(identifier):
    with db.connect() as database:
        invoice = prepare(database,identifier)
    return render_pdf(invoice, draft=True)


def issue(identifier, confirmed=False, expected=None, expected_worklogs=None):
    if not confirmed: raise ValueError('Bitte den Abschluss ausdrücklich bestätigen.')
    with db.connect() as database:
        database.execute('BEGIN IMMEDIATE')
        existing = database.execute('SELECT 1 FROM issued_invoice_files WHERE invoice_id=?',(identifier,)).fetchone()
        if existing:
            return dict(database.execute('SELECT * FROM invoices WHERE id=?',(identifier,)).fetchone())
        if expected is not None:
            from owner_journal import compare_base, record
            compare_base(expected,record(database,'invoice',identifier))
        if expected_worklogs is not None:
            from owner_journal import compare_base, record
            if not isinstance(expected_worklogs,dict):raise ValueError('Ungültige Leistungs-Vergleichsstände.')
            for linked,base in expected_worklogs.items():compare_base(base,record(database,'worklog',linked))
        invoice = prepare(database, identifier)
        numbers = [r[0] for r in database.execute('SELECT invoice_number FROM invoices')]
        if invoice['number_format'] == 'sevdesk':
            counters = [int(match.group(1)) for n in numbers if (match := re.fullmatch(r'RE-(\d+)(?:/\d{4})?', n))]
            counter = max(counters + [invoice['sevdesk_last_number']]) + 1
            invoice['invoice_number'] = f'RE-{counter}/{date.today().year}'
        else:
            prefix = f"{invoice['prefix']}-{date.today().year}-"
            counter = max([int(n[len(prefix):]) for n in numbers if n.startswith(prefix) and n[len(prefix):].isdigit()] or [0])+1
            invoice['invoice_number'] = f'{prefix}{counter:04d}'
        invoice['issued_at'] = db.now()
        pdf = render_pdf(invoice)
        invoice['pdf_sha256']=hashlib.sha256(pdf).hexdigest()
        database.execute('INSERT INTO issued_invoice_files(invoice_id,pdf,sha256) VALUES(?,?,?)',(identifier,pdf,hashlib.sha256(pdf).hexdigest()))
        database.execute("UPDATE invoices SET invoice_number=?,status='Gestellt',updated_at=? WHERE id=?",(invoice['invoice_number'],invoice['issued_at'],identifier))
        for worklog_id in invoice['worklog_ids']:
            database.execute('UPDATE worklogs SET billed=1,updated_at=? WHERE id=?',(invoice['issued_at'],worklog_id))
        if invoice.get('document_type')=='cancellation':
            database.execute("UPDATE invoices SET status='Storniert',updated_at=? WHERE id=?",(invoice['issued_at'],invoice['correction_of']))
        details = {k:v for k,v in invoice.items() if k not in ('id','customer_id','order_id','status','amount','created_at','updated_at','sevdesk_id')}
        database.execute('UPDATE invoice_details SET payload=? WHERE invoice_id=?',(json.dumps(details,ensure_ascii=False),identifier))
        return {'id':identifier,'invoice_number':invoice['invoice_number'],'status':'Gestellt'}


def issued_pdf(identifier):
    with db.connect() as database:
        row = database.execute('SELECT pdf,sha256 FROM issued_invoice_files WHERE invoice_id=?',(identifier,)).fetchone()
    if not row: raise ValueError('Für diesen Eintrag liegt kein in der App ausgestelltes PDF vor.')
    if hashlib.sha256(row['pdf']).hexdigest() != row['sha256']: raise ValueError('Archivprüfung fehlgeschlagen. PDF nicht verändern; Sicherung prüfen.')
    return row['pdf']


def create_correction(payload):
    """Create a separate draft; finalization still requires explicit issue confirmation."""
    from owner_journal import compare_base, record
    import sync_review
    action_id=str(payload.get('_action_id') or '')
    if not action_id:raise ValueError('Anfragekennung der Korrektur fehlt.')
    identifier='inv_correction_'+sync_review.digest(action_id)[:32]
    with db.atomic() as database:
        existing=record(database,'invoice',identifier)
        if existing:return existing
        original=record(database,'invoice',str(payload.get('id') or ''))
        compare_base(payload.get('_expected_invoice'),original)
        if not original.get('issued_at'):raise ValueError('Korrektur benötigt einen ausgestellten Originalbeleg.')
        kind=payload.get('correction_type')
        if kind not in ('replacement','cancellation') or not str(payload.get('reason') or '').strip():raise ValueError('Korrekturart und Grund sind erforderlich.')
        if kind=='cancellation' and original['status']=='Storniert':raise ValueError('Originalbeleg wurde bereits storniert.')
        data={**original,'id':identifier,'invoice_number':'Entwurf-'+identifier,'invoice_date':date.today().isoformat(),
              'status':'Entwurf','issued_at':'','sevdesk_id':None,'document_type':kind,'correction_of':original['id'],
              'correction_reason':payload['reason'],'positions':[{**p,'worklog_id':''} for p in original.get('positions',[])],
              'header_text':('Storno' if kind=='cancellation' else 'Korrektur')+' zu '+original['invoice_number']+' – '+payload['reason']}
        saved=db.save_invoice(data)
        return {**saved,'correction_state':'draft_created','original_unchanged':True}
