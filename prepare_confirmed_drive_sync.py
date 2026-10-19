"""User-approved: full data sync, quarantine all previously queued deletions first."""
import json
from pathlib import Path
import db
import runtime_paths

folder=runtime_paths.business_path(db.settings()['backup_folder'])
sources=[*folder.glob('HausGarten-Mobile-*.json')]
if (folder/'HausGarten-ChatGPT.json').exists():sources.append(folder/'HausGarten-ChatGPT.json')
target=db.DATA_DIR/'drive-held-deletions.json'
held=set(json.loads(target.read_text(encoding='utf-8'))) if target.exists() else set()
counts={}
for source in sources:
    package=json.loads(source.read_text(encoding='utf-8'))
    for action in package.get('actions',[]):
        kind=action.get('kind','unknown');counts[kind]=counts.get(kind,0)+1
        if kind=='delete':held.add(action['client_id'])
target.write_text(json.dumps(sorted(held)),encoding='utf-8')
print(json.dumps({'held_deletions':len(held),'queued_types':counts,'next_step':'Erst Server mit Halteschutz neu starten, danach vollständigen Abgleich aktivieren.'}))
