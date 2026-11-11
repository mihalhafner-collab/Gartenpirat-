"""Verify Eisbär's authority and evidence; this never tests or changes the app."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SPEC_DIR = Path('docs/fachregeln/eisbaer')
STATUSES = {'NOT_STARTED', 'NOT_RUN', 'IN_PROGRESS', 'PASS', 'FAIL'}
KINDS = {'runtime', 'ui', 'code', 'data', 'migration', 'mcp', 'ci', 'pixel'}
GATES = {
    'GATE:FULL_CI': 'ci',
    'GATE:PIXEL': 'pixel',
    'GATE:UI_MCP': 'mcp',
    'GATE:DATA_PRESERVATION': 'data',
    'GATE:BACKUP_RESTORE': 'pixel',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load(path):
    try:
        value = json.loads(path.read_text(encoding='utf-8'))
    except (OSError, ValueError) as exc:
        raise ValueError(f'{path.name}: unreadable JSON: {exc}') from exc
    require(isinstance(value, dict), f'{path.name}: expected an object')
    return value


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def local_file(base, name):
    require(isinstance(name, str) and name and not Path(name).is_absolute(),
            'Evidence and specification paths must be relative')
    require('..' not in Path(name).parts and '\\' not in name,
            'Path traversal is not allowed')
    path = (base / name).resolve()
    require(path.is_relative_to(base.resolve()) and path.is_file(),
            f'Missing or escaping file: {name}')
    return path


def checked_file(base, reference):
    require(isinstance(reference, dict), 'File reference must be an object')
    path = local_file(base, reference.get('file'))
    expected = reference.get('sha256', '')
    require(isinstance(expected, str) and re.fullmatch(r'[0-9a-f]{64}', expected),
            f'Missing SHA-256: {path.name}')
    require(digest(path) == expected, f'SHA-256 mismatch: {path.name}')
    return path


def expected_items(spec):
    """Derive coverage from the immutable specification, never from a checkbox count."""
    result = {}

    def add(identity, kind):
        require(isinstance(identity, str) and identity, 'Missing coverage ID')
        require(identity not in result, f'Duplicate coverage ID: {identity}')
        result[identity] = kind

    for item in spec['requirements']:
        add(item['id'], 'requirement')
    for menu in spec['menus']:
        add(menu['id'], 'ui')
        for action in menu.get('primary_actions', []):
            add(action['id'], 'ui')
        require(len(menu.get('step_ids', [])) == len(menu.get('steps', [])),
                f'Missing step IDs: {menu["id"]}')
        for identity in menu.get('step_ids', []):
            add(identity, 'ui')
    for field, kind in [('figures', 'ui'), ('wireframes', 'ui'),
                        ('code_impact', 'code'), ('data_metadata', 'data'),
                        ('acceptance_tests', 'runtime')]:
        for item in spec[field]:
            add(item['id'], kind)
    for flow in spec['finance']['workflows']:
        add(flow['id'], 'runtime')
    for index, _ in enumerate(spec['migration_plan'], 1):
        add(f'MIG-{index:02}', 'migration')
    for tool in spec['mcp']['catalog']['tools']:
        add('MCP:' + tool['name'], 'mcp')
    for identity, kind in GATES.items():
        add(identity, kind)
    return result


def candidate_identity(candidate):
    require(isinstance(candidate, dict), 'A tested candidate identity is required')
    require(candidate.get('package_name') == 'at.hausgarten.service',
            'Unexpected candidate package')
    require(isinstance(candidate.get('version_name'), str) and candidate['version_name'],
            'Candidate version_name is required')
    require(type(candidate.get('version_code')) is int and candidate['version_code'] > 0,
            'Candidate version_code must be an integer')
    for key, size in [('git_sha', 40), ('source_sha256', 64)]:
        value = candidate.get(key, '')
        require(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{' + str(size) + '}', value),
                f'Invalid candidate {key}')


def actual_candidate(root, candidate, spec):
    candidate_identity(candidate)
    build = root / 'android/app/build.gradle'
    text = build.read_text(encoding='utf-8')
    code = re.search(r'\bversionCode\s+(\d+)', text)
    name = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", text)
    require(code is not None and name is not None, 'Cannot read Android candidate version')
    require(candidate['version_code'] == int(code[1]) and candidate['version_name'] == name[1],
            'Candidate version does not match the checked-out app')
    baseline = spec['baseline']['app_version_code']
    require(candidate['version_code'] > baseline,
            'The Eisbär candidate must be newer than its installed app baseline')
    files = [p for p in (root / 'android/app/src/main').rglob('*') if p.is_file()] + [build]
    require(len(files) > 1, 'Missing native app sources')
    source = hashlib.sha256()
    for path in sorted(files, key=lambda p: p.relative_to(root).parts):
        source.update(path.relative_to(root).as_posix().encode())
        source.update(b'\0')
        source.update(path.read_bytes())
        source.update(b'\0')
    require(source.hexdigest() == candidate['source_sha256'],
            'Candidate source_sha256 does not match the checked-out app')


def timestamp(value):
    require(isinstance(value, str) and value.endswith('Z'), 'Evidence needs a UTC execution timestamp')
    try:
        parsed = datetime.datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError as exc:
        raise ValueError('Invalid evidence execution timestamp') from exc
    require(parsed.utcoffset() == datetime.timedelta(0), 'Evidence timestamp must be UTC')


def check_evidence(base, registry, candidate, identities):
    require(isinstance(registry, dict), 'Evidence registry must be an object')
    payloads = {}
    for identity, reference in registry.items():
        require(isinstance(identity, str) and identity, 'Missing evidence ID')
        path = checked_file(base, reference)
        require(path.suffix == '.json', 'Evidence must be a structured JSON report')
        payload = load(path)
        kind = reference.get('kind')
        require(kind in KINDS and payload.get('kind') == kind,
                f'Evidence kind mismatch: {identity}')
        require(payload.get('result') in {'PASS', 'FAIL', 'NOT_RUN'},
                f'Invalid evidence result: {identity}')
        candidate_identity(candidate)
        require(payload.get('candidate') == candidate,
                f'Evidence refers to a different candidate: {identity}')
        timestamp(payload.get('executed_at_utc'))
        require(isinstance(payload.get('command'), str) and payload['command'].strip(),
                f'Evidence command or runner is missing: {identity}')
        covers = payload.get('covers')
        require(isinstance(covers, list) and covers and all(isinstance(x, str) for x in covers)
                and len(covers) == len(set(covers)) and set(covers) <= set(identities),
                f'Evidence has unknown, missing or duplicate coverage IDs: {identity}')
        observations = payload.get('observations')
        require(isinstance(observations, (dict, list)) and observations,
                f'Actual observations are missing: {identity}')
        if kind == 'ci' and payload['result'] == 'PASS':
            require(payload.get('full_run') is True and payload.get('focused_flags') is False
                    and payload.get('build_result') == 'success'
                    and payload.get('emulator_result') == 'success',
                    'Partial, skipped or unsuccessful CI is not completion evidence')
            require(re.fullmatch(r'https://github\.com/mihalhafner-collab/Gartenpirat-/actions/runs/[0-9]+',
                                 payload.get('run_url', '')) is not None,
                    'Full CI evidence must reference the repository workflow run')
        if kind == 'pixel' and payload['result'] == 'PASS':
            require(payload.get('physical_device') is True,
                    'Emulator evidence cannot replace physical Pixel acceptance')
        payloads[identity] = payload
    return payloads


def compatible(kind, evidence_kind):
    allowed = {
        'requirement': KINDS,
        'ui': {'ui', 'pixel'},
        'code': {'code', 'ci'},
        'data': {'data', 'runtime', 'migration', 'pixel'},
        'runtime': {'runtime', 'mcp', 'pixel', 'ci'},
        'migration': {'migration', 'data', 'pixel'},
        'mcp': {'mcp', 'pixel'},
        'ci': {'ci'},
        'pixel': {'pixel'},
    }
    return evidence_kind in allowed[kind]


def check(root=ROOT, completion=False, ci_build_result=None, ci_emulator_result=None,
          ci_full_run=None):
    root = Path(root).resolve()
    base = root / SPEC_DIR
    authority = load(base / 'EISBAER.json')
    require(authority.get('schema') == 'eisbaer.authority.v1'
            and authority.get('concept_name') == 'Eisbär'
            and authority.get('sole_authority') is True
            and authority.get('status') == 'AUTHORITATIVE',
            'Eisbär must be the sole authoritative design and functional specification')
    document_path = checked_file(base, authority.get('document'))
    require(document_path.read_bytes().startswith(b'%PDF-'), 'Eisbär document is not a PDF')
    specification_path = checked_file(base, authority.get('specification'))
    checksums = load(checked_file(base, authority.get('checksums')))
    spec = load(specification_path)
    document = spec.get('document', {})
    require(document.get('concept_name') == 'Eisbär'
            and document.get('id') == authority.get('document_id')
            and document.get('version') == authority.get('version'),
            'Authority register and original Eisbär specification disagree')
    require(checksums.get('document_id') == document['id']
            and checksums.get('concept_version') == document['version'],
            'Eisbär checksum document identity mismatch')
    for key, authority_key in [('pdf', 'document'), ('manifest', 'specification')]:
        require(checksums.get(key, {}).get('file') == authority[authority_key]['file']
                and checksums[key].get('sha256') == authority[authority_key]['sha256'],
                f'Original checksum reference mismatch: {key}')
    progress = load(local_file(base, authority.get('progress_file')))
    require(progress.get('schema') == 'eisbaer.implementation.v1'
            and progress.get('document_id') == document['id']
            and progress.get('eisbaer_version') == document['version']
            and progress.get('specification_sha256') == authority['specification']['sha256'],
            'Implementation tracking belongs to a different specification')
    status = progress.get('overall_status')
    require(status in {'NOT_STARTED', 'IN_PROGRESS', 'COMPLETE'}, 'Invalid overall implementation status')
    identities = expected_items(spec)
    items = progress.get('items')
    require(isinstance(items, dict) and set(items) == set(identities),
            'Completion coverage must contain every Eisbär ID exactly once; no additions or omissions')
    state = load(root / 'AKTUELLER-STAND.json')
    task = state.get('current_task', {})
    require(isinstance(task, dict), 'current_task must be an object')
    task_status = task.get('status')
    task_closed = isinstance(task_status, str) and task_status.strip().upper() in {'COMPLETE', 'COMPLETED'}
    strict = (completion or status == 'COMPLETE' or state.get('eisbaer_completed') is True
              or task.get('completed') is True or task.get('eisbaer_completed') is True
              or task_closed)
    candidate = progress.get('candidate')
    if candidate is not None:
        candidate_identity(candidate)
    payloads = check_evidence(base, progress.get('evidence'), candidate, identities)
    passed = 0
    for identity, item in items.items():
        require(isinstance(item, dict) and item.get('status') in STATUSES,
                f'Invalid or skipped status: {identity}')
        refs = item.get('evidence')
        require(isinstance(refs, list) and len(refs) == len(set(refs))
                and all(isinstance(x, str) and x in payloads for x in refs),
                f'Unknown or duplicate evidence reference: {identity}')
        for reference in refs:
            require(identity in payloads[reference]['covers'],
                    f'Evidence does not cover {identity}: {reference}')
        if item['status'] == 'PASS':
            require(refs, f'PASS without actual evidence: {identity}')
            for reference in refs:
                proof = payloads[reference]
                if identity in GATES:
                    require(proof['kind'] == GATES[identity],
                            f'Gate {identity} requires {GATES[identity]} evidence')
                require(proof['result'] == 'PASS' and compatible(identities[identity], proof['kind']),
                        f'Wrong or unsuccessful evidence for {identity}: {reference}')
            passed += 1
    if strict:
        open_ids = [key for key, item in items.items() if item['status'] != 'PASS']
        require(status == 'COMPLETE' and not open_ids,
                f'Eisbär completion blocked: status {status}; {len(open_ids)} open items'
                + (': ' + ', '.join(open_ids[:6]) if open_ids else ''))
        actual_candidate(root, candidate, spec)
        if ci_build_result is not None or ci_emulator_result is not None or ci_full_run is not None:
            require(ci_build_result == 'success' and ci_emulator_result == 'success',
                    'The current full CI build and emulator jobs must both succeed')
            require(ci_full_run is True or ci_full_run == 'true',
                    'A focused or partial current CI run cannot complete Eisbär')
    return {'check': 'PASS', 'scope': 'Eisbär authority, integrity and completion evidence',
            'concept_name': 'Eisbär', 'version': document['version'],
            'items': len(items), 'passed': passed, 'open': len(items) - passed,
            'implementation_status': status, 'complete': bool(strict)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--completion', action='store_true', help='Require the entire Eisbär rebuild to be accepted')
    parser.add_argument('--ci-build-result')
    parser.add_argument('--ci-emulator-result')
    parser.add_argument('--ci-full-run', choices=['true', 'false'])
    args = parser.parse_args()
    try:
        print(json.dumps(check(completion=args.completion, ci_build_result=args.ci_build_result,
                               ci_emulator_result=args.ci_emulator_result,
                               ci_full_run=args.ci_full_run), ensure_ascii=False))
    except (ValueError, KeyError, TypeError, OSError) as exc:
        parser.exit(1, f'Eisbär: {exc}\n')


if __name__ == '__main__':
    main()
