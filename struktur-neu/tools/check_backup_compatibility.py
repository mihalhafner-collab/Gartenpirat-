"""Cross-check compiled Kotlin archive code with the pinned Build117 Java reference.

Requires an existing JVM classpath containing production classes, Kotlin test
classes, org.json and Kotlin stdlib. No downloads, real backups or device access.
This checks archive compatibility, not Android activation or UI acceptance.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import sqlite3
import struct
import subprocess
import tempfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
REFERENCE_COMMIT = '035d6503d16d63a7468498611060f99640a67284'
REFERENCE_PATH = 'struktur-neu/android/app/src/main/java/at/hausgarten/service/EncryptedBackupArchive.java'
REFERENCE_SHA256 = '8995bbfdb10cc65416829c41ece5734581734ca67beec6313fc85b39286c1fa7'


def source(path):
    return subprocess.check_output(['git', 'show', f'{REFERENCE_COMMIT}:{path}'], cwd=ROOT)


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encoded(value))


def png():
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(b'\x00\x00\x80\x00')) + chunk(b'IEND', b''))


def database(path, schema):
    # Use the pinned original six-table schema, plus an arbitrary typed table.
    # The extra table proves that opaque archive copying retains unknown data.
    sql = []
    for name in ('PixelLocalStore', 'WorkSessionStore'):
        text = source(f'struktur-neu/android/app/src/main/java/at/hausgarten/service/{name}.java').decode()
        sql.extend(re.findall(r'(?:database|db)\.execSQL\("(CREATE (?:TABLE|INDEX)[^"\\]+)"\)', text))
    with sqlite3.connect(path) as connection:
        for statement in sql:
            connection.execute(statement)
        connection.execute('CREATE TABLE qa_types(id TEXT PRIMARY KEY,nullable_value TEXT,long_value INTEGER,real_value REAL,text_value TEXT,blob_value BLOB)')
        connection.execute('INSERT INTO qa_types VALUES(?,?,?,?,?,?)',
                           ('qa_types_1', None, 9223372036854775806, 1.25, 'QA Grünfläche 🌿\nZeile 2', bytes(range(256)) * 17))
        snapshot = {'format': 1, 'schema_version': 2, 'customers': [
            {'id': 'qa_customer_1', 'name': 'QA Musterkunde', 'street': 'QA Musterweg 1', 'email': 'qa_customer@example.invalid', 'test_record': True}],
            'orders': [], 'worklogs': [], 'invoices': [], 'photos': [], 'offers': [], 'appointments': [], 'service_catalog': [], 'accepted_ids': [], 'sync_errors': []}
        connection.execute('INSERT INTO owner_state VALUES(?,?)', ('snapshot', encoded(snapshot).decode()))
        connection.execute('INSERT INTO mcp_uploads VALUES(?,?,?)',
                           ('qa_upload_1', encoded({'id': 'qa_upload_1', 'test_record': True, 'mime_type': 'application/octet-stream'}).decode(), bytes(range(256)) * 17))
        connection.execute(f'PRAGMA user_version={schema}')
        connection.commit()
        counts = {name: connection.execute('SELECT count(*) FROM "' + name.replace('"', '""') + '"').fetchone()[0]
                  for name, in connection.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")}
        definitions = {kind + ':' + name: definition or '' for kind, name, definition in connection.execute('SELECT type,name,sql FROM sqlite_master ORDER BY type,name')}
        assert connection.execute('PRAGMA integrity_check').fetchone() == ('ok',)
    return {'schemaVersion': schema, 'foreignKeyViolations': 0, 'tableCounts': counts,
            'schemaSha256': hashlib.sha256(encoded(definitions)).hexdigest(), 'size': path.stat().st_size,
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}


def fixture(root, schema):
    base = root / f'schema_{schema}'
    data = base / 'data'
    data.mkdir(parents=True)
    db = database(data / 'database.sqlite', schema)
    preferences = {'formatVersion': 1, 'groups': {group: {'values': {}, 'types': {}}
                   for group in ('haus_garten', 'planning_private', 'haus_garten_mail')}}
    preferences['groups']['haus_garten'] = {
        'values': {'qa_text': 'QA Grünfläche 🌿', 'qa_bool': True, 'qa_int': 7, 'qa_long': 9223372036854775806,
                   'qa_float': '0x1.8p1', 'qa_set': ['QA alpha', 'QA beta']},
        'types': {'qa_text': 'string', 'qa_bool': 'boolean', 'qa_int': 'int', 'qa_long': 'long', 'qa_float': 'float', 'qa_set': 'string_set'}}
    write_json(data / 'preferences.json', preferences)
    write_json(data / 'browser-storage.json', {'formatVersion': 1, 'storage': {
        'hausgarten.entry.draft.v1': encoded({'id': 'qa_draft_1', 'activity': 'QA Arbeit 🌿', 'test_record': True}).decode()}})
    write_json(data / 'backup-notes.json', {'counts': {'customers': 1}, 'warnings': [
        {'kind': 'photo', 'record_id': 'qa_absent_photo', 'reason': 'ORIGINAL_FILE_MISSING'}], 'lastIssuedDocumentIds': []})
    for name in ('initial-data.json', 'service-catalog.json'):
        target = data / 'bootstrap' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(source(f'struktur-neu/android/app/src/main/assets/{name}'))
    image = png()
    target = data / 'files/customer-photo-originals' / (hashlib.sha256(image).hexdigest() + '.png')
    target.parent.mkdir(parents=True)
    target.write_bytes(image)
    target = data / 'files/business-documents/qa-original.txt'
    target.parent.mkdir(parents=True)
    target.write_bytes('QA synthetic original\nGrünfläche 🌿\n'.encode())
    metadata = {'applicationId': 'at.hausgarten.service', 'appVersion': 'QA-build117', 'backupId': f'qa_compat_schema_{schema}',
                'installationId': '00000000-0000-0000-0000-000000000001', 'restoreEpoch': '00000000-0000-0000-0000-000000000002',
                'businessId': 'at.hausgarten.service', 'capturedAt': '2026-10-29T00:00:00Z', 'schemaVersion': schema,
                'datasetFormat': 'pixel.snapshot.v2', 'database': db, 'counts': {'customers': 1}, 'numberState': {},
                'lastIssuedDocumentIds': [], 'warning_count': 1, 'captureScope': 'database_all_tables_private_files_preferences_browser_storage_and_legacy_backups',
                'media_coverage': 'AVAILABLE_FILES_WITH_NOTES',
                'fixturePaths': sorted(path.relative_to(data).as_posix() for path in data.rglob('*') if path.is_file())}
    write_json(base / 'metadata.json', metadata)


def check_restored_databases(root):
    checks = 0
    for schema in (6, 7):
        base = root / f'schema_{schema}'
        for name in ('legacy_to_kotlin.sqlite', 'kotlin_to_legacy.sqlite'):
            database_file = base / name
            assert database_file.read_bytes() == (base / 'data/database.sqlite').read_bytes()
            with sqlite3.connect(database_file.as_uri() + '?mode=ro', uri=True) as connection:
                assert connection.execute('PRAGMA user_version').fetchone() == (schema,)
                assert connection.execute('PRAGMA integrity_check').fetchone() == ('ok',)
                assert connection.execute('SELECT nullable_value,long_value,real_value,text_value,blob_value FROM qa_types').fetchone() == (
                    None, 9223372036854775806, 1.25, 'QA Grünfläche 🌿\nZeile 2', bytes(range(256)) * 17)
                assert connection.execute('SELECT data FROM mcp_uploads WHERE id=?', ('qa_upload_1',)).fetchone() == (bytes(range(256)) * 17,)
                assert json.loads(connection.execute("SELECT value FROM owner_state WHERE key='snapshot'").fetchone()[0])['customers'][0]['test_record'] is True
                checks += 6
    print(f'PASS restored synthetic SQLite type/content checks: {checks}; no Android restore or customer backup tested')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--classpath', required=True, help='Compiled production/test classes and their pinned JVM dependencies')
    args = parser.parse_args()
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        raise RuntimeError('A JDK is required')
    try:
        reference = source(REFERENCE_PATH)
    except subprocess.CalledProcessError as unavailable:
        raise RuntimeError('Pinned legacy ZIP source is missing; fetch it first: git fetch --no-tags --depth=1 origin ' + REFERENCE_COMMIT) from unavailable
    if hashlib.sha256(reference).hexdigest() != REFERENCE_SHA256:
        raise RuntimeError('Pinned legacy source does not match its expected SHA256')
    with tempfile.TemporaryDirectory(prefix='qa_backup_compat_') as temporary:
        root = Path(temporary)
        write_json(root / 'fixture-spec.json', {'format': 'hausgarten.synthetic.backup.compat.v1', 'synthetic_only': True})
        for schema in (6, 7):
            fixture(root, schema)
        legacy = root / 'LegacyBackupArchive.java'
        legacy.write_text(reference.decode().replace('EncryptedBackupArchive', 'LegacyBackupArchive'))
        classes = root / 'classes'
        classes.mkdir()
        subprocess.run([javac, '-encoding', 'UTF-8', '-cp', args.classpath, '-d', str(classes), str(legacy)], cwd=ROOT, check=True, timeout=120)
        classpath = os.pathsep.join([str(classes), args.classpath])
        subprocess.run([java, '-Xmx256m', '-Dfile.encoding=UTF-8', '-cp', classpath,
                        'at.hausgarten.service.BackupFormatCompatibilityTests', str(root)], cwd=ROOT, check=True, timeout=180)
        check_restored_databases(root)
    print(f'PASS reference binding: Build117 {REFERENCE_COMMIT}, source SHA256 {REFERENCE_SHA256}; temporary fixtures removed')


if __name__ == '__main__':
    main()
