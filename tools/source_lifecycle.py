"""Git source verification, transient local review artifacts and historical evidence.

No network publisher, credentials or runtime writes. Administrative Drive plans
are disabled by the current GitHub-only policy. Pure historical comparison
helpers remain for synthetic regressions; application backups are separate.
"""
import argparse
import collections
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from privacy_guard import artifact_gate, scan_files
from tools.source_preservation import verify_preserved_source


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def fingerprint(files):
    result = hashlib.sha256()
    for name, raw in sorted(files.items()):
        result.update(name.encode('utf-8') + b'\0' + raw + b'\0')
    return result.hexdigest()


def category(name):
    for prefix, group in [('android/app/src/main/java/', '03-ANDROID'),
                          ('android/app/src/main/assets/', '04-OBERFLAECHE'),
                          ('tests/', '06-TESTS'), ('tools/', '07-WERKZEUGE')]:
        if name.startswith(prefix):
            return group
    return '05-PYTHON-MCP' if name.endswith('.py') else '02-ARCHITEKTUR'


def utf8_parts(raw, limit=512 * 1024):
    offset = 0
    while offset < len(raw):
        end = min(len(raw), offset + limit)
        while True:
            try:
                raw[offset:end].decode('utf-8')
                break
            except UnicodeDecodeError:
                end -= 1
                if end <= offset:
                    raise ValueError('Invalid UTF-8 source')
        yield offset, raw[offset:end]
        offset = end
    if not raw:
        yield 0, b''


def verify_text_exports(paths, originals):
    restored = collections.defaultdict(dict)
    for path in paths:
        raw = path.read_bytes()
        raw.decode('utf-8')
        position = raw.index(b'\n') + 1
        while position < len(raw):
            if raw[position:position + 7] != b'@@FILE ':
                raise ValueError('Invalid text export header')
            header_end = raw.index(b'\n', position)
            meta = json.loads(raw[position + 7:header_end])
            start = header_end + 1
            end = start + meta['bytes']
            chunk = raw[start:end]
            if digest(chunk) != meta['chunk_sha256'] or raw[end:end + 12] != b'\n@@END_FILE\n':
                raise ValueError('Text export bytes changed')
            if meta['offset'] in restored[meta['path']]:
                raise ValueError('Repeated text export segment')
            restored[meta['path']][meta['offset']] = chunk
            position = end + 12
    if set(restored) != set(originals):
        raise ValueError('Text export inventory differs')
    for name, pieces in restored.items():
        offset = 0
        for at, raw in sorted(pieces.items()):
            if at != offset:
                raise ValueError('Missing text export segment')
            offset += len(raw)
        if b''.join(raw for _, raw in sorted(pieces.items())) != originals[name]:
            raise ValueError('Original source cannot be reconstructed')


def review_bundle(archive_path, output, commit):
    """Create lossless UTF-8 review files from one immutable original ZIP."""
    archive_path, output = Path(archive_path), Path(output)
    output.mkdir(parents=True, exist_ok=True)
    if list(output.glob('*.txt')) or list(output.glob('*.json')):
        raise ValueError('Use a new immutable export generation')
    artifact_gate(archive_path)
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Repeated source ZIP entry')
        files = {name: archive.read(name) for name in names}
    scan_files(files)
    inventory, texts, groups = [], {}, collections.defaultdict(list)
    for name, raw in sorted(files.items()):
        try:
            raw.decode('utf-8')
            is_text = b'\0' not in raw
        except UnicodeDecodeError:
            is_text = False
        inventory.append({'path': name, 'bytes': len(raw), 'sha256': digest(raw),
                          'format': 'UTF-8' if is_text else 'PUBLIC_BINARY'})
        if not is_text:
            continue
        texts[name] = raw
        for offset, chunk in utf8_parts(raw):
            meta = {'path': name, 'offset': offset, 'bytes': len(chunk),
                    'file_sha256': digest(raw), 'chunk_sha256': digest(chunk)}
            groups[category(name)].append(b'@@FILE ' + json.dumps(meta, ensure_ascii=False).encode() + b'\n' + chunk + b'\n@@END_FILE\n')
    text_paths = []
    for group, blocks in sorted(groups.items()):
        buffers, buf = [], bytearray()
        for block in blocks:
            if buf and len(buf) + len(block) > 768 * 1024:
                buffers.append(bytes(buf)); buf.clear()
            buf.extend(block)
        if buf:
            buffers.append(bytes(buf))
        for number, content in enumerate(buffers, 1):
            path = output / f'{group}-TEIL-{number:02}.txt'
            path.write_bytes(f'HAUS & GARTEN | Commit {commit} | UTF-8 Originalbytes\n'.encode() + content)
            text_paths.append(path)
    verify_text_exports(text_paths, texts)
    main = {n: raw for n, raw in files.items() if n.startswith('android/app/src/main/') or n == 'android/app/build.gradle'}
    manifest = {'format': 'hausgarten.review-generation.v1', 'git_sha': commit,
                'source_sha256': fingerprint(main), 'source_files': len(files),
                'text_files': len(texts), 'public_binary_files': len(files) - len(texts),
                'zip_name': archive_path.name, 'zip_sha256': digest(archive_path.read_bytes()),
                'text_reconstruction': 'PASS', 'apk_for_this_commit': 'NOT ATTESTED BY SOURCE EXPORT', 'files': inventory}
    (output / '01-MANIFEST.json').write_bytes(json_bytes(manifest))
    (output / '00-START.txt').write_text(
        f'HAUS & GARTEN – lokale GitHub-Quellprüfung, keine Drive-Quellsicherung\n'
        f'Git-Commit: {commit}\nOriginal-ZIP: {archive_path.name}\n'
        f'Quellfingerprint: {manifest["source_sha256"]}\n'
        f'{len(files)} Originaldateien; {len(texts)} UTF-8-Dateien vollständig rekonstruierbar.\n'
        'Zuerst AGENTS.md und AKTUELLER-STAND.json lesen. Entwicklungsstand und ausgelieferte APK getrennt behandeln.\n'
        'Dateiabschnitte sind längencodiert und enthalten die unveränderten Originalbytes.\n', encoding='utf-8')
    (output / 'SHA256SUMS.txt').write_text(''.join(
        f'{digest(p.read_bytes())}  {p.name}\n' for p in sorted(output.iterdir()) if p.is_file()), encoding='utf-8')
    for path in output.iterdir():
        if path.is_file():
            artifact_gate(path)
    return {k: v for k, v in manifest.items() if k != 'files'}


def stamp(row):
    return {key: row.get(key) for key in ('id', 'name', 'mime_type', 'bytes', 'modified_time', 'folder_id')}


def cleanup_plan(policy, registry, inventory, now=None):
    """A complete inventory plus an explicit unchanged registered file is required."""
    now = now or dt.datetime.now(dt.timezone.utc)
    if inventory.get('complete') is not True:
        raise ValueError('Incomplete inventory; cleanup is blocked')
    folders = set(policy['folders'].values())
    protected_folders = set(policy['runtime_folder_ids'])
    generation_parents = {policy['folders'].get('development'), policy['folders'].get('legacy_exports')}
    for folder in registry.get('generation_folders', []):
        if folder.get('verified') is True and folder.get('parent_id') in generation_parents and folder['id'] not in protected_folders:
            folders.add(folder['id'])
    protected_files = set(policy['protected_file_ids'])
    records = registry.get('files', [])
    if len({r['id'] for r in records}) != len(records):
        raise ValueError('Repeated registry ID')
    known = {r['id']: r for r in records}
    seen, delete, retain = set(), [], []
    for row in inventory['files']:
        identifier = row['id']
        if identifier in seen:
            raise ValueError('Repeated inventory ID')
        seen.add(identifier)
        old = known.get(identifier)
        reason = None
        if row.get('folder_id') in protected_folders or row.get('folder_id') not in folders:
            reason = 'UNMANAGED_OR_RUNTIME_FOLDER'
        elif identifier in protected_files or not old or old.get('protected') or old.get('active'):
            reason = 'PROTECTED_OR_UNKNOWN'
        elif stamp(row) != stamp(old) or not row.get('modified_time'):
            reason = 'METADATA_CHANGED'
        elif row.get('shared') is not False or row.get('trashed'):
            reason = 'SHARING_OR_TRASH_CHANGED'
        elif old.get('role') not in policy['retention_days']:
            reason = 'UNMANAGED_ROLE'
        elif not old.get('replacement_verified') or not old.get('retired_at'):
            reason = 'NO_VERIFIED_REPLACEMENT'
        elif old.get('build') in policy['keep_builds']:
            reason = 'RETAINED_RELEASE'
        elif old['role'] in ('apk_retired', 'test_apk') and row.get('mime_type') != 'application/vnd.android.package-archive':
            reason = 'UNEXPECTED_MIME'
        elif old['role'] in ('development_export', 'ki_export') and row.get('mime_type') not in ('text/plain', 'application/json', 'application/zip'):
            reason = 'UNEXPECTED_MIME'
        elif any((row.get('name') or '').startswith(p) for p in ('HausGarten-', '.env')):
            reason = 'RUNTIME_OR_SECRET_NAME'
        else:
            retired = dt.datetime.fromisoformat(old['retired_at'].replace('Z', '+00:00'))
            if retired.tzinfo is None:
                raise ValueError('Timezone required')
            days = policy['retention_days'][old['role']]
            if now < retired + dt.timedelta(days=days):
                reason = 'RETENTION_PERIOD'
        if reason:
            retain.append({'id': identifier, 'reason': reason})
        else:
            delete.append({'id': identifier, 'expected': stamp(row), 'role': old['role']})
    return {'format': 'hausgarten.cleanup-plan.v1', 'delete': delete, 'retain': retain,
            'delete_bytes': sum(int(known[r['id']]['bytes']) for r in delete),
            'execution': 'CONNECTED_DRIVE_TOOL_WITH_FRESH_METADATA_REQUIRED'}


def check_before_delete(candidate, current, policy):
    if stamp(current) != candidate['expected'] or current.get('shared') is not False or current.get('trashed'):
        raise ValueError('File changed since cleanup plan')
    if current['folder_id'] in policy['runtime_folder_ids'] or current['id'] in policy['protected_file_ids']:
        raise ValueError('Protected file or runtime folder')
    return True


def cleanup_local(root, records, now=None, apply=False, retained_builds=(89, 90)):
    """Only registered generated files in explicit build/output roots; no recursion."""
    root = Path(root).resolve()
    now = now or dt.datetime.now(dt.timezone.utc)
    result = []
    for item in records:
        path = root / item['path']
        allowed = any(path.resolve().is_relative_to(root / base) for base in ('outputs', 'android/app/build/outputs'))
        if '..' in Path(item['path']).parts or not allowed or path.is_symlink() or not path.resolve().is_relative_to(root):
            raise ValueError('Unmanaged local path')
        if item.get('protected') or item.get('active') or not item.get('completed'):
            continue
        retired_apk = item.get('role') == 'apk_retired' and item.get('classification') == 'PUBLIC_ARTIFACT'
        generated = item.get('role') in ('build_output', 'log') and (item.get('classification') == 'SYNTHETIC_TEST' or
                    (item.get('classification') == 'PUBLIC_ARTIFACT' and item.get('replacement_verified')))
        if not (retired_apk or generated) or not path.is_file():
            continue
        if retired_apk and (not item.get('replacement_verified') or item.get('build') in retained_builds or path.suffix != '.apk'):
            continue
        threshold = 0 if retired_apk else 7 if item['role'] == 'build_output' else 30
        retired = dt.datetime.fromisoformat(item['retired_at'].replace('Z', '+00:00'))
        if retired.tzinfo is None:
            raise ValueError('Timezone required')
        if now < retired + dt.timedelta(days=threshold) or digest(path.read_bytes()) != item['sha256']:
            continue
        artifact_gate(path)
        if apply:
            path.unlink()
        result.append(item['path'])
    return {'files': result, 'applied': apply}


def git_bytes(*args):
    env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
    return subprocess.check_output(['git', *args], cwd=ROOT, env=env)


def cleanup_export_cache(cache, records, now=None, apply=False):
    """Expire explicitly registered public exports outside the checkout only."""
    cache = Path(cache)
    if cache.is_symlink() or cache.resolve().is_relative_to(ROOT) or cache.name != 'hausgarten-source-exports':
        raise ValueError('Explicit external source export cache required')
    now = now or dt.datetime.now(dt.timezone.utc)
    removed = []
    for item in records:
        path = cache / item['path']
        if '..' in Path(item['path']).parts or path.is_symlink() or not path.resolve().is_relative_to(cache.resolve()):
            raise ValueError('Source export path escapes cache')
        if item.get('protected') or item.get('classification') != 'PUBLIC_SOURCE' or not item.get('replacement_verified'):
            continue
        retired = dt.datetime.fromisoformat(item['retired_at'].replace('Z', '+00:00'))
        if retired.tzinfo is None:
            raise ValueError('Timezone required')
        if now < retired + dt.timedelta(days=7) or not path.is_file() or digest(path.read_bytes()) != item['sha256']:
            continue
        artifact_gate(path)
        if apply:
            path.unlink()
        removed.append(item['path'])
    return {'files': removed, 'applied': apply}


def restore_check(releases, git=git_bytes):
    """Actually restore ZIP bytes from each pinned Git tree and verify contents."""
    results = []
    for release in releases:
        remote = git('ls-remote', 'origin', 'refs/tags/' + release['tag'] + '^{}').decode().split()
        if not remote or remote[0] != release['git_sha']:
            raise ValueError('Original source tag changed or is unavailable')
        raw = git('archive', '--format=zip', release['git_sha'])
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            names = [name for name in archive.namelist() if not archive.getinfo(name).is_dir()]
            if len(names) != len(set(names)) or len(names) != release['source_files']:
                raise ValueError('Restored original source inventory differs')
            files = {name: archive.read(name) for name in names}
        scan_files(files)
        main = {name: value for name, value in files.items()
                if name.startswith('android/app/src/main/') or name == 'android/app/build.gradle'}
        if fingerprint(main) != release['source_sha256']:
            raise ValueError('Restored original source hash differs')
        results.append({'tag': release['tag'], 'git_sha': release['git_sha'],
                        'source_files': len(files), 'source_sha256': fingerprint(main), 'status': 'PASS'})
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('check', 'export', 'plan', 'restore-check', 'clean-local'))
    parser.add_argument('--output', type=Path)
    parser.add_argument('--inventory', type=Path)
    parser.add_argument('--registry', type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    policy = json.loads((ROOT / 'source-lifecycle.json').read_text())
    if args.command == 'check':
        print(json.dumps(verify_preserved_source(ROOT)))
    elif args.command == 'restore-check':
        releases = json.loads((ROOT / 'source-releases.json').read_text())['releases']
        print(json.dumps(restore_check(releases)))
    elif args.command == 'clean-local':
        if not args.registry:
            parser.error('Explicit local artifact registry required')
        registry = json.loads(args.registry.read_text())
        result = {'generated': cleanup_local(ROOT, registry.get('local_files', []), apply=args.apply, retained_builds=policy['keep_builds'])}
        if registry.get('export_cache'):
            result['exports'] = cleanup_export_cache(registry['export_cache'], registry.get('local_exports', []), apply=args.apply)
        print(json.dumps(result))
    elif args.command == 'plan':
        if policy.get('administrative_drive_access_enabled') is False:
            parser.error('Administrative Drive workflow retired; maintain sources and evidence in GitHub.')
        if not args.inventory or not args.registry:
            parser.error('Fresh inventory and explicit registry required')
        print(json.dumps(cleanup_plan(policy, json.loads(args.registry.read_text()), json.loads(args.inventory.read_text()))))
    else:
        if not args.output or args.output.exists():
            parser.error('New output directory required')
        proof = verify_preserved_source(ROOT)
        args.output.mkdir(parents=True)
        archive = args.output / f'Haus-Garten-Quellcode-{proof["git_sha"][:12]}.zip'
        env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
        subprocess.run([sys.executable, '-B', str(ROOT / 'tests/package_source_copy.py'), '--output', str(archive)],
                       cwd=ROOT, env=env, check=True, stdout=subprocess.DEVNULL)
        print(json.dumps(review_bundle(archive, args.output, proof['git_sha'])))


if __name__ == '__main__':
    main()
