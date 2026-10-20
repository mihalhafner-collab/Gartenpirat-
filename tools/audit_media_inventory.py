"""Read-only P2 metadata audit. No deletion, downloads or business-store access.

Call audit() with a normalized, in-memory connector manifest, or pass a manifest
already held below HG_PRIVATE_DATA_ROOT. Output contains aggregates and hashes
only. A complete inventory is NOT inferred from an empty search response.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import runtime_paths

KINDS = frozenset({'photo', 'document', 'history', 'receipt', 'backup', 'release', 'temporary', 'unknown'})
SOURCES = frozenset({'active', 'archive', 'final', 'pending', 'backup', 'history'})
# Financial retention is a separate decision, even for an unreferenced PDF.
# Only an independently classified temporary derivative can leave this class.
PROTECTED = frozenset({'document', 'history', 'receipt', 'backup', 'release'})
CLASSES = ('referenced', 'orphaned', 'temporary', 'protected', 'unknown')
COVERAGE = frozenset({'inventory_complete', 'references_complete', 'backups_complete', 'history_complete', 'queue_empty'})


def _require(valid):
    if not valid:
        # Never interpolate input IDs, names or contents into diagnostics.
        raise ValueError('Invalid normalized audit manifest; no files changed.')


def _hash(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def audit(manifest):
    _require(isinstance(manifest, dict) and set(manifest) == {'version', 'files', 'references', 'coverage'})
    _require(type(manifest['version']) is int and manifest['version'] == 1)
    files, references, coverage = (manifest[k] for k in ('files', 'references', 'coverage'))
    _require(isinstance(files, list) and isinstance(references, list) and len(files) <= 100000 and len(references) <= 500000)
    _require(isinstance(coverage, dict) and set(coverage) == COVERAGE | {'before_revision', 'after_revision'})
    _require(all(type(coverage[k]) is bool for k in COVERAGE))
    _require(all(isinstance(coverage[k], str) and len(coverage[k]) <= 256 for k in ('before_revision', 'after_revision')))
    complete = all(coverage[k] for k in COVERAGE) and bool(coverage['before_revision']) and coverage['before_revision'] == coverage['after_revision']
    objects, refs, collisions = {}, {}, set()
    for row in files:
        _require(isinstance(row, dict) and set(row) == {'object_id', 'kind', 'byte_size'})
        key, kind, size = row['object_id'], row['kind'], row['byte_size']
        _require(isinstance(key, str) and 0 < len(key) <= 256 and isinstance(kind, str) and kind in KINDS)
        _require(size is None or type(size) is int and 0 <= size <= 2**63 - 1)
        if key in objects and row != objects[key]:
            collisions.add(key)
        objects[key] = row
    normalized = set()
    for row in references:
        _require(isinstance(row, dict) and set(row) == {'object_id', 'source'})
        key, source = row['object_id'], row['source']
        _require(isinstance(key, str) and 0 < len(key) <= 256 and isinstance(source, str) and source in SOURCES)
        normalized.add((key, source))
        refs.setdefault(key, set()).add(source)
    missing = set(refs) - set(objects)
    if collisions or missing:
        complete = False
    totals = {name: {'count': 0, 'known_bytes': 0, 'unknown_sizes': 0} for name in CLASSES}
    kinds = {kind: 0 for kind in sorted(KINDS)}
    for key, row in objects.items():
        kind, size = row['kind'], row['byte_size']
        kinds[kind] += 1
        if kind in PROTECTED or refs.get(key, set()) & {'final', 'backup', 'history'}:
            classification = 'protected'
        elif key in collisions:
            classification = 'unknown'
        elif key in refs:
            classification = 'referenced'
        elif not complete or size is None or kind == 'unknown':
            classification = 'unknown'
        elif kind == 'temporary':
            classification = 'temporary'
        else:
            classification = 'orphaned'
        total = totals[classification]
        total['count'] += 1
        total['known_bytes'] += size or 0
        total['unknown_sizes'] += size is None
    return {
        'mode': 'READ_ONLY', 'status': 'PASS' if complete else 'INCOMPLETE',
        'files': len(objects), 'types': kinds, 'classes': totals,
        'missing_reference_objects': len(missing), 'inconsistent_object_metadata': len(collisions),
        'reference_set_sha256': _hash(sorted(normalized)),
        'inventory_sha256': _hash(sorted((k, v['kind'], v['byte_size']) for k, v in objects.items())),
        'coverage': {k: coverage[k] for k in sorted(COVERAGE)},
        'stable_revision': bool(coverage['before_revision']) and coverage['before_revision'] == coverage['after_revision'],
        # Even a complete read-only classification is never a purge permission.
        'deletion_authorized': False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', required=True)
    args = parser.parse_args()
    path = runtime_paths.business_path(args.manifest)
    _require(path.is_file() and path.stat().st_size <= 64 * 1024 * 1024)
    print(json.dumps(audit(json.loads(path.read_text())), sort_keys=True))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError):
        print('AUDIT_FAILED: private normalized manifest unavailable or invalid; no files changed.', file=sys.stderr)
        raise SystemExit(1)
