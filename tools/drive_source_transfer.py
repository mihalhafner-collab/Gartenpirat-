"""Prepare safe, incremental Codex -> Drive source transfers; no credentials or network.

Use one freshly read folder inventory, then upload only ``upload`` actions. Verify
new bytes before recording a receipt. Re-run the plan after an ambiguous upload;
``verify_existing``/``blocked_duplicates`` must never trigger a blind second POST.
This module never deletes files, changes sharing, or marks stream-size proof as SHA.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from privacy_guard import artifact_gate, scan_files

MIMES = {'.zip': 'application/zip', '.txt': 'text/plain', '.json': 'application/json'}
COMPRESSED = {'.jpg', '.jpeg', '.png', '.webp', '.gif'}


def sha256(raw):
    return hashlib.sha256(raw).hexdigest()


def write_verified_zip(files, target, modes=None, *, reuse_existing=False):
    """Original public bytes, stable ordering/time/modes, CRC + byte comparison.

    Images already contain compression. Storing those bytes avoids redundant CPU
    work without new resources or changes to the unpacked source.
    """
    target = Path(target)
    modes = modes or {}
    for name in files:
        rel = PurePosixPath(name)
        if rel.is_absolute() or '..' in rel.parts or str(rel) != name or '\\' in name:
            raise ValueError('Unsafe source member path')
    scan_files(files)

    def verify(path):
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)) or set(names) != set(files):
                raise ValueError('Source archive inventory differs')
            for name, raw in files.items():
                # read() also validates CRC; no redundant testzip() pass.
                if archive.read(name) != raw:
                    raise ValueError('Source archive bytes differ')
                if (archive.getinfo(name).external_attr >> 16) & 0o777 != modes.get(name, 0o644):
                    raise ValueError('Source archive modes differ')
        return artifact_gate(path)

    if target.exists():
        if not reuse_existing:
            raise FileExistsError('An unused ZIP target is required')
        privacy = verify(target)
        return {'status': 'PASS', 'reused': True, 'files': len(files), 'artifact': privacy}
    if target.suffix != '.zip':
        raise ValueError('ZIP target required')
    with tempfile.TemporaryDirectory(prefix='qa_source_transfer_') as temp:
        staging = Path(temp) / 'source.zip'
        with zipfile.ZipFile(staging, 'x') as archive:
            for name, raw in sorted(files.items()):
                info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_STORED if Path(name).suffix.lower() in COMPRESSED else zipfile.ZIP_DEFLATED
                info.external_attr = (0o100000 | modes.get(name, 0o644)) << 16
                archive.writestr(info, raw)
        privacy = verify(staging)
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as output:
            output.write(staging.read_bytes())
    return {'status': 'PASS', 'reused': False, 'files': len(files), 'artifact': privacy}


def local_artifacts(directory):
    """Only an explicitly prepared public export folder (ZIP, UTF-8 TXT, JSON)."""
    result = []
    for path in sorted(Path(directory).iterdir()):
        if path.is_symlink() or not path.is_file() or path.suffix not in MIMES:
            raise ValueError('Export folder contains an unexpected entry')
        privacy = artifact_gate(path)
        raw = path.read_bytes()
        if path.suffix in {'.txt', '.json'}:
            raw.decode('utf-8')
        result.append({'name': path.name, 'path': str(path.resolve()), 'bytes': len(raw),
                       'sha256': sha256(raw), 'mime_type': MIMES[path.suffix], 'privacy': privacy})
    if not result:
        raise ValueError('Empty export folder')
    return result


def observed(row, folder_id):
    """Normalize official Drive metadata and connector folder-list metadata."""
    return {'folder_id': folder_id, 'id': row.get('id'),
            'name': row.get('name', row.get('title')), 'bytes': str(row.get('size', '')),
            'mime_type': row.get('mimeType', row.get('mime_type')),
            'modified_time': row.get('modifiedTime', row.get('modified_time')),
            'version': row.get('version'), 'md5': row.get('md5Checksum'),
            'shared': row.get('shared'), 'trashed': row.get('trashed', False)}


def verified_receipt(artifact, row, folder_id, *, readback_sha256=None, stream_bytes=None):
    """Call only after actual readback; distinct proof levels remain explicit."""
    stamp = observed(row, folder_id)
    if (stamp['name'] != artifact['name'] or stamp['bytes'] != str(artifact['bytes'])
            or stamp['mime_type'] != artifact['mime_type'] or stamp['shared'] is not False
            or stamp['trashed'] or not stamp['id'] or not stamp['modified_time']):
        raise ValueError('Fresh private metadata does not match export')
    if readback_sha256 is not None:
        if readback_sha256 != artifact['sha256']:
            raise ValueError('Drive readback hash differs')
        level = 'SHA256_BYTES'
    elif stream_bytes == artifact['bytes'] and artifact['mime_type'] == 'application/zip':
        level = 'STREAM_SIZE_ONLY'  # E.g. connector materialization limit; no remote SHA claim.
    else:
        raise ValueError('Actual readback proof required')
    return {'observed': stamp, 'local_sha256': artifact['sha256'], 'verification': level}


def plan_transfer(artifacts, inventory, previous=None):
    """Inventory must be a fresh complete listing of the explicitly named folder.

    File names and sizes alone do not authorize reuse. Previously verified local
    hashes are bound to immutable file IDs and the observed modification stamp.
    No implicit cleanup: obsolete IDs are reported only, never queued for delete.
    """
    folder = inventory.get('folder_id', '')
    if not re.fullmatch(r'[A-Za-z0-9_-]{10,200}', folder):
        raise ValueError('Explicit Drive folder ID required')
    previous = previous or {'folder_id': folder, 'receipts': []}
    if previous.get('folder_id') != folder:
        raise ValueError('Receipt folder differs')
    rows = inventory['files']
    if inventory.get('complete') is not True:
        raise ValueError('A complete folder inventory is required before upload planning')
    if len({row['id'] for row in rows}) != len(rows):
        raise ValueError('Inventory contains repeated IDs')
    if len({a['name'] for a in artifacts}) != len(artifacts):
        raise ValueError('Export names must be unique')
    proofs = {p['observed']['id']: p for p in previous['receipts']}
    actions, used = [], set()
    for artifact in artifacts:
        candidates = [r for r in rows if observed(r, folder)['name'] == artifact['name']]
        item = {'name': artifact['name'], 'sha256': artifact['sha256'], 'bytes': artifact['bytes']}
        if len(candidates) > 1:
            item.update(action='blocked_duplicates', ids=[r['id'] for r in candidates])
            used.update(item['ids'])
        elif not candidates:
            item.update(action='upload')
        else:
            row = candidates[0]; stamp = observed(row, folder); proof = proofs.get(row['id'])
            used.add(row['id'])
            matches = (proof and proof['observed'] == stamp and stamp['shared'] is False
                       and not stamp['trashed'] and stamp['modified_time']
                       and proof.get('verification') in {'SHA256_BYTES', 'STREAM_SIZE_ONLY'}
                       and stamp['bytes'] == str(artifact['bytes']) and stamp['mime_type'] == artifact['mime_type'])
            if matches and proof['local_sha256'] == artifact['sha256']:
                item.update(action='reuse', id=row['id'], verification=proof['verification'],
                            reuse_basis='Prior readback plus unchanged observed metadata; no new body hash')
            elif proof and proof['observed'] == stamp and proof['local_sha256'] != artifact['sha256']:
                item.update(action='upload', supersedes_id=row['id'])
            else:
                item.update(action='verify_existing', id=row['id'])
        actions.append(item)
    counts = {kind: sum(a['action'] == kind for a in actions)
              for kind in ('upload', 'reuse', 'verify_existing', 'blocked_duplicates')}
    return {'format': 'hausgarten.source-transfer-plan.v1', 'folder_id': folder, 'counts': counts,
            'upload_bytes': sum(a['bytes'] for a in actions if a['action'] == 'upload'),
            'actions': actions, 'unreferenced_ids': [r['id'] for r in rows if r['id'] not in used],
            'deletions': [], 'ambiguous_upload': 'Read fresh inventory and re-plan before any retry',
            'metadata_reads': 'One complete folder inventory; no per-file metadata calls required',
            'execution': transfer_schedule(actions)}


def transfer_schedule(actions, concurrency=3):
    """Independent artifact pipelines overlap; verification and cleanup stay ordered.

    The connector is invoked by Codex, not by a credential-bearing local client.
    Start the next task as a slot becomes free. Each upload is followed immediately
    by its own readback; never publish a complete-export claim before the barrier.
    """
    if not 1 <= concurrency <= 3:
        raise ValueError('Source transfer concurrency must be between one and three')
    tasks = []
    for action in actions:
        if action['action'] in {'reuse', 'blocked_duplicates'}:
            continue
        name = action['name']
        stage = ('start' if name.startswith('00-START') else 'indexes'
                 if name.startswith(('01-', '99-')) or name in {'SHA256SUMS.txt', 'VERSIONSNACHWEIS.json'} else 'payloads')
        tasks.append({'name': name, 'bytes': action.get('bytes', 0), 'stage': stage,
                      'expected_sha256': action['sha256'],
                      'steps': (['upload_once'] if action['action'] == 'upload' else []) +
                               ['readback', 'verify_size_and_bytes_or_explicit_stream_limit', 'record_receipt'],
                      'ambiguous_upload': 'Release slot; reconcile fresh folder inventory before retry'})
    stages = ('payloads', 'indexes', 'start')
    tasks.sort(key=lambda task: (stages.index(task['stage']), -task['bytes']))
    return {'target': 'latency_and_throughput', 'mobile_data_budget': None,
            'max_parallel_artifacts': concurrency, 'tasks': tasks,
            'stage_barriers': ['Verify all payloads before indexes', 'Verify all indexes before start file'],
            'within_stage': 'Largest files first; start next eligible file immediately when a slot is free',
            'barrier': 'All expected artifact receipts verified before claiming completion or authorized cleanup',
            'cleanup': [], 'blocked_names': [a['name'] for a in actions if a['action'] == 'blocked_duplicates']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exports', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, required=True, help='Fresh {folder_id,complete:true,files} JSON from full Drive folder listing')
    parser.add_argument('--verified-state', type=Path, help='Previous {folder_id,receipts} from actual readback')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.exports.resolve()):
        parser.error('Transfer state must remain outside the export folder')
    artifacts = local_artifacts(args.exports)
    inventory = json.loads(args.inventory.read_text(encoding='utf-8'))
    previous = json.loads(args.verified_state.read_text(encoding='utf-8')) if args.verified_state else None
    plan = plan_transfer(artifacts, inventory, previous)
    args.output.write_text(json.dumps(plan, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({'status': 'PLANNED', 'counts': plan['counts'], 'upload_bytes': plan['upload_bytes']}))


if __name__ == '__main__':
    main()
