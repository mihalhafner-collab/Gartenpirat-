"""Visual test only: produces a marked sample, never inserts or issues real records."""
from pathlib import Path
import invoicing
import db

invoice = {
    **db.calculate_invoice({'positions':[
        {'title':'Pflege der Thuja-Hecke: abgestorbene Äste entfernen und Rückschnitt','quantity':5.5,'unit':'h','price':45},
        {'title':'Lärchenrindenmulch fein - gelieferte Materialmenge','quantity':3,'unit':'Säcke','price':12},
        {'title':'An- und Rückfahrt','quantity':77.7,'unit':'km','price':0.5},
    ],'payment_days':14}),
    'invoice_number':'MUSTER - KEINE RECHNUNG','invoice_date':invoicing.date.today().isoformat(),
    'service_start':'2026-10-01','service_end':'2026-10-01',
    'due_date':(invoicing.date.today()+invoicing.timedelta(days=14)).isoformat(),
    'issuer':{'business_name':'Hafner Grünraum Handwerk','owner_name':'Hafner Michael',
        'business_street':'Goethestraße 2','business_postal_code':'9560','business_city':'Feldkirchen',
        'business_iban':db.settings().get('business_iban',''),'business_bank':db.settings().get('business_bank',''),
        'business_bic':db.settings().get('business_bic',''),'business_legal_info':''},
    'recipient':{'name':'Musterkunde - nur Layoutprüfung','street':'Beispielstraße 1','postal_code':'9560','city':'Feldkirchen'},
    'footer_text':'Unverbindliches Gestaltungsmuster. Keine Zahlungsaufforderung.'
}
target=Path(__file__).parent/'output'/'pdf'/'Rechnungsmenue-Musterentwurf.pdf'
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(invoicing.render_pdf(invoice,draft=True))
print(target)
