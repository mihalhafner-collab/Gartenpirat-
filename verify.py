import json
import db


def scalar(database, sql):
    return database.execute(sql).fetchone()[0]


db.init()
with db.connect() as database:
    result = {
        "customers": scalar(database, "SELECT COUNT(*) FROM customers"),
        "invoices": scalar(database, "SELECT COUNT(*) FROM invoices"),
        "paid_invoices": scalar(database, "SELECT COUNT(*) FROM invoices WHERE status='Bezahlt'"),
        "open_invoices": scalar(database, "SELECT COUNT(*) FROM invoices WHERE status='Gestellt'"),
        "invoice_amount_sum": scalar(database, "SELECT ROUND(COALESCE(SUM(amount),0),2) FROM invoices"),
        "duplicate_customer_source_ids": scalar(database, """SELECT COUNT(*) FROM (
            SELECT sevdesk_contact_id FROM customers WHERE sevdesk_contact_id IS NOT NULL
            GROUP BY sevdesk_contact_id HAVING COUNT(*)>1)"""),
        "duplicate_invoice_source_ids": scalar(database, """SELECT COUNT(*) FROM (
            SELECT sevdesk_id FROM invoices WHERE sevdesk_id IS NOT NULL
            GROUP BY sevdesk_id HAVING COUNT(*)>1)"""),
        "invoices_without_customer": scalar(database, """SELECT COUNT(*) FROM invoices i
            LEFT JOIN customers c ON c.id=i.customer_id WHERE c.id IS NULL"""),
        "worklogs": scalar(database, "SELECT COUNT(*) FROM worklogs"),
        "orders": scalar(database, "SELECT COUNT(*) FROM orders"),
        "photos": scalar(database, "SELECT COUNT(*) FROM photos"),
    }

# Cloud copy: structural integrity, not private import totals.
expected = {'duplicate_customer_source_ids': 0, 'duplicate_invoice_source_ids': 0, 'invoices_without_customer': 0}
result["checks"] = {key: result[key] == value for key, value in expected.items()}
result["all_checks_passed"] = all(result["checks"].values())
print(json.dumps(result, ensure_ascii=False, indent=2))
raise SystemExit(0 if result["all_checks_passed"] else 1)
