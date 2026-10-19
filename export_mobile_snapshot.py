#!/usr/bin/env python3
"""Explicit external read-only host export; never builds Android seed data."""
import argparse
import hashlib
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
import runtime_paths


def export_snapshot(database, target):
    source = runtime_paths.business_path(database)
    target = runtime_paths.business_path(target)
    if not source.is_file() or target.exists() or source == target:
        raise ValueError('Externe Quelle fehlt oder Exportziel existiert bereits.')
    with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True) as connection:
        connection.row_factory = sqlite3.Row
        connection.execute('PRAGMA query_only=ON')
        connection.execute('BEGIN')
        tables = {r[0] for r in connection.execute("SELECT name FROM sqlite_master WHERE type='table'")}
        payload = {'format': 1, 'schema_version': 2, 'accepted_ids': []}
        for name in ('customers', 'orders', 'worklogs', 'invoices', 'photos'):
            payload[name] = [dict(r) for r in connection.execute('SELECT * FROM ' + name)] if name in tables else []
        for name in ('offers', 'service_catalog'):
            payload[name] = [json.loads(r[0]) for r in connection.execute('SELECT payload FROM ' + name)] if name in tables else []
        if 'settings' in tables:
            payload['business_settings'] = {r['key']: r['value'] for r in connection.execute('SELECT * FROM settings') if r['key'] in {'owner_name','business_name','business_street','business_postal_code','business_city','business_email','business_phone','business_iban','business_bic','business_bank','business_vat_id','tax_mode','tax_exemption_text','default_payment_terms','default_payment_days','invoice_prefix','invoice_number_format','invoice_issuer_confirmed','invoice_numbering_confirmed'}}
        raw = json.dumps(payload, ensure_ascii=False, separators=(',', ':')).encode()
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with target.open('xb') as output:
        output.write(raw)
    return {'result': 'PASS', 'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest(), 'counts': {k: len(v) for k, v in payload.items() if isinstance(v, list)}}


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--output', type=Path); args = parser.parse_args()
    root = runtime_paths.private_root()
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    print(json.dumps(export_snapshot(root / 'data/haus-garten.db', args.output or root / 'exports' / ('mobile-' + stamp + '.json'))))


if __name__ == '__main__':
    main()
