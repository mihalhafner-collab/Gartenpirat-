"""Prepare a private, verifiable source snapshot for external code review.

Generated artefacts only; never modifies the app or productive data.
"""
import argparse
import base64
import hashlib
import json
import os
import re
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--target', choices=('gemini', 'codex-cloud'), default='gemini')
parser.add_argument('--revision', default='')
arguments = parser.parse_args()
TARGET = arguments.target
if arguments.revision and not re.fullmatch(r'[a-zA-Z0-9-]+', arguments.revision):
    raise SystemExit('Ungültige Exportrevision.')
REVISION = '-' + arguments.revision if arguments.revision else ''
DATE = datetime.now(timezone.utc).date().isoformat()
OUT = ROOT / 'outputs' / (TARGET + '-source-' + (DATE + REVISION if TARGET == 'codex-cloud' else '2026-10-12'))
OUT.mkdir(parents=True, exist_ok=True)
TEXT = {'.py', '.js', '.cjs', '.css', '.html', '.java', '.xml', '.gradle', '.properties', '.md', '.txt', '.ps1', '.cmd', '.json', '.svg', '.webmanifest'}
BINARY = {'.png', '.ttf'}
SKIP_DIRS = {'build', '__pycache__', '.gradle', '.git'}
FILES = {}
EXCLUDED = []
SANITIZED = []
HISTORICAL = {'import_existing_data.py', 'import_google_notes.py',
              'repair_confirmed_photo_links.py', 'prepare_confirmed_drive_sync.py', 'verify_invoice_pdf.py'}
def source_files(directory):
    def fail(error):
        raise error
    for current, directories, names in os.walk(directory, onerror=fail):
        directories[:] = [name for name in directories if name not in SKIP_DIRS]
        for name in names:
            yield Path(current) / name

for file in ROOT.iterdir():
    if TARGET == 'codex-cloud' and file.is_file() and file.name in HISTORICAL:
        EXCLUDED.append(file.name)
        continue
    if file.is_file() and (file.suffix in (TEXT if TARGET == 'gemini' else {'.py'})
                          or (TARGET == 'codex-cloud' and file.name == 'AGENTS.md')):
        FILES[file.relative_to(ROOT).as_posix()] = file.read_bytes()
for folder in ('android', 'mcp_integration', 'static', 'assets', 'tests'):
    for file in source_files(ROOT / folder):
        if not file.is_file() or any(part in SKIP_DIRS for part in file.relative_to(ROOT).parts):
            continue
        relative = file.relative_to(ROOT).as_posix()
        if file.name in {'local.properties', 'keystore.properties', 'google-services.json'} or file.suffix not in TEXT | BINARY:
            continue
        if TARGET == 'codex-cloud' and file.suffix in {'.md', '.txt'}:
            continue
        if file.name == 'initial-data.json':
            seed = json.loads(file.read_text(encoding='utf-8'))
            if TARGET == 'codex-cloud' and any(seed.get(k) != [] for k in ('customers', 'orders', 'worklogs', 'invoices', 'photos')):
                raise SystemExit('Cloud-Export abgebrochen: Startdaten nicht leer.')
            FILES[relative] = file.read_bytes() if TARGET == 'codex-cloud' else json.dumps({'format': 1, 'customers': [], 'orders': [], 'worklogs': [], 'invoices': [], 'photos': [], 'accepted_ids': [], 'capabilities': {'worklog_compare_and_save': True, 'entity_compare_and_save': True}}, ensure_ascii=False, indent=2).encode('utf-8')
        else:
            FILES[relative] = file.read_bytes()

if TARGET == 'codex-cloud':
    from apk_privacy_guard import private_markers, scan
    from source_privacy_rules import (anonymize, sanitize_snapshot_verifier,
                                      sanitize_device_tools, scan_cloud_metadata)
    markers = private_markers()
    for relative, data in list(FILES.items()):
        original = data
        if Path(relative).suffix in TEXT:
            data = sanitize_device_tools(data, relative)
            data = anonymize(data, markers, Path(relative).suffix)
            if relative == 'verify.py':
                data = sanitize_snapshot_verifier(data)
            if data != original:
                SANITIZED.append(relative)
        FILES[relative] = data
    # Adapt only the COPY's build launcher for Linux; original build unchanged.
    gradle = 'android/app/build.gradle'
    old = b"commandLine 'py', '-3.11', 'tests/apk_privacy_guard.py'"
    new = b"commandLine (System.getProperty('os.name').toLowerCase().contains('windows') ? ['py', '-3.11', 'tests/apk_privacy_guard.py'] : ['python3', 'tests/apk_privacy_guard.py'])"
    if old in FILES[gradle]:
        FILES[gradle] = FILES[gradle].replace(old, new)
        SANITIZED.append(gradle)
    FILES['requirements-mcp.txt'] = (ROOT / 'requirements-mcp.txt').read_bytes()
    FILES['.gitignore'] = b'data/\noutputs/\noutput/\ntmp/\n__pycache__/\n*.pyc\n.dependencies/\n.tools/\n.gradle*/\nandroid-toolchain/\n.android-user/\nandroid/.gradle/\n**/build/\nnode_modules/\nandroid/local.properties\n*.keystore\n*.jks\n*.apk\n*.idsig\n.env\n.env.*\n'
    scan(FILES, markers)
    scan_cloud_metadata(FILES)

secret_patterns = [re.compile(rb'\bsk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{30,}'), re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'), re.compile(rb'\bAIza[A-Za-z0-9_-]{30,}')]
findings = []
for relative, data in FILES.items():
    if Path(relative).suffix in TEXT and any(pattern.search(data) for pattern in secret_patterns):
        findings.append(relative)
if findings:
    raise SystemExit('Abbruch: mögliche Zugangsdaten in Dateien: ' + ', '.join(findings))

guide = '''# Haus & Garten App – Quellcodekopie für Gemini

Stand: 12.10.2026. Bestehende App, keine Neuentwicklung.

Das ZIP enthält den vorhandenen Android-, Web- und MCP-Quellcode, die Tests,
Konfiguration und Grafiken. Die TXT-Datei enthält denselben textuellen Code
mit Dateipfaden als Abschnittsüberschriften und kann zur Analyse verwendet werden.
Zur Bearbeitung einzelner Dateien das ZIP herunterladen und entpacken.

Aktueller Entwicklungsstand: 1.32.0 / Versionscode 49.
Die Designumsetzung läuft noch; diese Kopie ist ein Arbeitsstand, keine
abgenommene APK 1.32.0. Das letzte bereitgestellte Release ist 1.31.0.
Ein Snapshot einer laufenden Entwicklung sollte nicht ungeprüft produktiv installiert werden.

Nicht enthalten: produktive Datenbanken, Drive-Datenpakete, Kundenrechnungen,
betriebliche Fotos, private Sicherungen, API-Schlüssel, Android-Signaturschlüssel,
Build-Ausgaben, SDK/JDK/Gradle-Distributionen und Caches. Die Android-Startdaten
initial-data.json wurden ausschließlich in der Kopie durch einen leeren
kompatiblen Datenstand ersetzt. Das produktive Projekt bleibt unverändert.
Hardcodierte Tunnel- und Organisationskennungen im Quellcode sind keine
Zugangsschlüssel und bleiben zur strukturellen Prüfung erhalten.

Aufbau:
- android/app/src/main/java/at/hausgarten/service/: Android, Datenbank, Brücken, MCP, Tunnel.
- android/app/src/main/assets/: Oberfläche, Rechnungseditor, Designmodule und Branding.
- static/: vorhandene Laptop-Web-App.
- db.py, server.py, invoicing.py: bestehendes Python-Backend.
- mcp_integration/: private MCP-Schnittstelle für den bisherigen Laptop-/Drive-Kanal.
- tests/: automatische Tests mit isolierten Daten.

Build: JDK 17, Gradle 9.3.1, Android Gradle Plugin 9.1.1,
Android-Plattform API 37 und Build Tools 37.0.0. Einen eigenen lokalen
SDK-Pfad in android/local.properties eintragen. Für Updates auf dem bestehenden
Pixel bleibt der ursprüngliche Signaturschlüssel erforderlich; dieser wird
bewusst nicht an die Review-Kopie angehängt.
Laptop: Python-Abhängigkeiten laut vorhandenen requirements-Dateien installieren.
Die Testskripte enthalten teilweise lokale Windows-Werkzeugpfade; für eine
andere Entwicklungsumgebung anpassen, ohne Geschäftslogik zu verändern.

Arbeitsauftrag für Gemini:
Analysiere und verbessere die bestehende Anwendung. Erstelle zuerst einen
Prüfbericht und anschließend konkrete Dateiänderungen/Diffs. Erhalte IDs,
Relationen, Speichermodelle, Offline-Verhalten, Drive-Dateiformate und
bestehende MCP-Werkzeugnamen. Verändere keine produktiven Daten oder Kontoverbindungen.
Unterscheide statische Prüfung von tatsächlich ausgeführten Tests.
Die begonnenen Designmodule design-concept.js/.css und deren mobile Tests sind
besonders zu prüfen: Navigation, Kundenprofile, Formularentwürfe, Zurück,
Suche, optionale Auftragszuordnung, Fotos und Rechnungszugänge.
Die Übernahme ins Original erfolgt erst nach Prüfung und Tests in Codex.

Diese Kopie gibt Gemini keine neuen Konto-, Plugin- oder Geräteberechtigungen.
Sie ist ein separates Code-Artefakt, keine zweite betriebliche Datenquelle.
'''
if TARGET == 'codex-cloud':
    version = re.search(r"versionName '([^']+)'", FILES['android/app/build.gradle'].decode()).group(1)
    code = int(re.search(r'versionCode (\d+)', FILES['android/app/build.gradle'].decode()).group(1))
    guide = f'''Haus & Garten App – Quellcode für Codex Cloud
Stand {DATE}; vorhandene Version {version}, versionCode {code}. Keine neue App.
Ein Entwicklungsstand, KEINE freigegebene neue APK.

Ziel: Quellcode in der Cloud bearbeiten. Betriebsdaten bleiben auf Pixel/Drive.
Enthalten: Android-, Web-, MCP-Code, Tests und öffentliche Marken-/Grafikdateien.
Nicht enthalten: Datenbanken, Kunden-/Arbeits-/Rechnungsdaten, Drive-Dateien,
Betriebsfotos, Sicherungen, Konto-/Signaturschlüssel, Caches, SDK/JDK und APKs.
Historische produktionsbezogene Import-/Reparaturskripte und alte Berichte ausgelassen.
Bekannte private Referenzen in Programm-/Testbeispielen wurden NUR IN DIESER KOPIE
konsistent durch Cloudfixture-Werte ersetzt. Ursprüngliche Betriebsdaten unverändert.
verify.py prüft in der Kopie weiterhin Dubletten und Kundenverweise, aber keine
privaten historischen Importzahlen oder Rechnungsumsätze als feste Sollwerte.
SOURCE-MANIFEST.json enthält Dateihashes, Auslassungen und Kopie-Anpassungen.
AGENTS.md enthält die dauerhaften Projektregeln für selbstständige, getestete
Verbesserungen innerhalb des Auftrags und die unveränderten Sicherheitsgrenzen.

Nächste Schritte:
1. Archiv in ein PRIVATES GitHub-Repository importieren; nicht öffentlich freigeben.
2. Codex -> Arbeiten in / Work in -> Cloud -> Umgebung erstellen.
3. GitHub verbinden und Zugriff ausschließlich auf dieses Repository gewähren.
   Die ChatGPT-GitHub-Pluginverbindung ersetzt die Codex-Repositoryfreigabe nicht.
4. Cloud-Setup einrichten und testen lassen; Umgebung erst danach veröffentlichen.
5. Cloud-Auftrag am bestehenden Code starten, Ergebnisse und Tests prüfen.
Offizielle Anleitung: https://learn.chatgpt.com/docs/environments/cloud-environments

Werkzeuge: Python 3.11, JDK 17, Gradle 9.3.1, AGP 9.1.1, Android SDK/Build Tools 37,
Node für JS-Tests. Python-MCP laut requirements-mcp.txt; Rechnungen/Fotos benötigen
ReportLab/Pillow. Windows-/USB-/Browser-Skripte können Anpassung benötigen.
Gradle-Privacy-Prüfung nutzt auf Linux in dieser Kopie python3 statt Windows-py.
Eine tatsächliche Linux-/Codex-Cloud-Umgebung wurde NOCH NICHT ausgeführt oder getestet.

Update-Sicherheit: Original Application ID at.hausgarten.service behalten.
Vorhandenen Signaturschlüssel NICHT ersetzen. Er ist nicht im Archiv enthalten.
Cloud-Signierung benötigt eine gesonderte sichere Schlüssel-/Secret-Freigabe.
Ohne sie kein freigegebenes In-place-Update. Vor jedem Release echte Neuinstallation
UND echtes Update mit Datenerhalt plus finalen APK-Privacy-Check nachweisen.

Auftrag fortsetzen: Abrechnungsstruktur 1.42 prüfen. Lokal bestanden: 148 Python-,
267 Java-, 55 MCP-E2E- und 10 neue UI-Prüfungen. Android Fresh Install/Update und
neue native Abnahme noch offen (Windows-Testemulator blockiert). Diese Nachweise
sind keine Cloud-Testergebnisse. Keine automatische Release-Freigabe.

IDs, Relationen, Offline-/Drive-Formate, MCP-Werkzeugnamen und Pixel-Plugin erhalten.
Keine produktiven Daten für Tests; keine neue App oder konkurrierende Datenbank.
Kein Live-Drive-/Pixel-Zugriff ohne konkrete Freigabe. Nur synthetische Testdaten.
Statisch geprüft / tatsächlich ausgeführt / E2E / nicht testbar klar unterscheiden.
'''
    guide_name = 'CLOUD-START.txt'
else:
    version, code, guide_name = '1.32.0-development', None, 'GEMINI-ANLEITUNG.md'
FILES[guide_name] = guide.encode('utf-8')
manifest = {'created_at_utc': datetime.now(timezone.utc).isoformat(), 'target': TARGET, 'version': version, 'version_code': code, 'production_data_included': False, 'credentials_included': False, 'cloud_connected': False, 'release_approved': False, 'known_private_markers_checked': len(markers) if TARGET == 'codex-cloud' else None, 'excluded_files': EXCLUDED, 'sanitized_files': SANITIZED if TARGET == 'codex-cloud' else ['android/app/src/main/assets/initial-data.json'], 'files': [{'path': name, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()} for name, data in sorted(FILES.items())]}
FILES['SOURCE-MANIFEST.json'] = json.dumps(manifest, ensure_ascii=False, indent=2).encode('utf-8')
archive = OUT / ('Haus-und-Garten-App-Quellcode-codex-cloud-' + DATE + REVISION + '.zip' if TARGET == 'codex-cloud' else 'Haus-und-Garten-App-Quellcode-2026-10-12.zip')
if TARGET == 'codex-cloud' and archive.exists():
    raise SystemExit('Cloud-Export existiert bereits; keine Datei überschrieben.')
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as target:
    for name, data in sorted(FILES.items()):
        target.writestr('haus-garten-app/' + name, data)
with zipfile.ZipFile(archive) as source:
    assert source.testzip() is None
    for item in manifest['files']:
        data = source.read('haus-garten-app/' + item['path'])
        assert hashlib.sha256(data).hexdigest() == item['sha256']
    if TARGET == 'codex-cloud':
        scan({name: source.read(name) for name in source.namelist()}, markers)
        scan_cloud_metadata({name: source.read(name) for name in source.namelist()})
        # A bounded, source-only payload for native GitHub tools; no credentials.
        payload = [{'path': name.removeprefix('haus-garten-app/'),
                    'content': base64.b64encode(source.read(name)).decode('ascii'),
                    'git_sha': hashlib.sha1(b'blob ' + str(len(source.read(name))).encode() + b'\0' + source.read(name)).hexdigest()}
                   for name in source.namelist()]
        (OUT / 'GITHUB-PAYLOAD.json').write_text(json.dumps(payload, ensure_ascii=True, separators=(',', ':')), encoding='utf-8')
text = '\n\n'.join('===== DATEI: ' + name + ' =====\n' + data.decode('utf-8-sig') for name, data in sorted(FILES.items()) if Path(name).suffix in TEXT)
text_name = 'CLOUD-QUELLCODE.txt' if TARGET == 'codex-cloud' else 'GEMINI-QUELLCODE.txt'
(OUT / text_name).write_text(text, encoding='utf-8')
(OUT / guide_name).write_text(guide, encoding='utf-8')
(OUT / 'SOURCE-MANIFEST.json').write_bytes(FILES['SOURCE-MANIFEST.json'])
print(json.dumps({'source_files': len(manifest['files']), 'zip_bytes': archive.stat().st_size, 'zip_sha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'txt_bytes': (OUT / text_name).stat().st_size, 'output_directory': str(OUT), 'archive_verified': True, 'cloud_connected': False, 'private_markers_checked': len(markers) if TARGET == 'codex-cloud' else None}, ensure_ascii=False))
