"""Preview/import reviewed public plants into the one existing offline catalogue.

The default is read-only. --write requires every selected profile to be reviewed.
Editorial HOLD records are never promoted by this tool. No network or business
store access; image originals remain outside the distributed application assets.
"""
from __future__ import annotations

import argparse
from copy import deepcopy
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import tempfile
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
LIBRARY = ROOT / 'libraries/plants'
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(LIBRARY))
from audit_library import license_url_matches

FORMAT = 'gartenpirat.plant-integration.v1'
LIBRARY_COMMIT = '78e0204cd7155799c61c4a88f98150baffeb9ffd'
CATALOG_PATH = 'android/app/src/main/assets/plant-catalog.json'
ASSET_PATH = 'android/app/src/main/assets/'
TRAITS = {'light': 'light', 'soil': 'soilTexture', 'ph': 'soilReaction',
          'water': 'soilDrainage', 'bloom': 'bloomSeason', 'colors': 'flowerColors',
          'life': 'lifeCycle', 'foliage': 'foliage', 'growth': 'growthRate',
          'propagation': 'propagation', 'dimensions': 'dimensionsSource',
          'origin': 'originSource'}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def entry_sha(value):
    return sha(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode())


def unique(values):
    return list(dict.fromkeys(v for v in values if v))


def historical_warnings(values):
    return unique('Historische Angabe „' + text + '“ ist nicht erneut bestätigt.'
                  if re.search(r'\bungiftig\b|\bnicht giftig\b|\bharmlos\b', text, re.I)
                  and not text.startswith('Historische Angabe „') else text for text in values)


def checked_date(value):
    if not isinstance(value, str) or dt.date.fromisoformat(value).isoformat() != value:
        raise ValueError('Invalid review date')
    if dt.date.fromisoformat(value) > dt.date.today():
        raise ValueError('Review date is in the future')
    return value


def public_file(root, relative):
    """Reject traversal and symlinks, including parent directories."""
    if not isinstance(relative, str):
        raise ValueError('Public file path is required')
    rel = PurePosixPath(relative)
    if rel.is_absolute() or '..' in rel.parts or '\\' in relative or str(rel) != relative:
        raise ValueError('Unsafe public file path')
    root = Path(root)
    if root.is_symlink():
        raise ValueError('Symlinked public source root')
    current = root
    for part in rel.parts:
        current /= part
        if current.is_symlink():
            raise ValueError('Symlinked public source')
    if not current.is_file() or not current.resolve().is_relative_to(root.resolve()):
        raise ValueError('Missing public source file')
    return current


def review_file(root, review, plant_id):
    checked_date(review.get('checkedAt'))
    if review.get('status') != 'ACCEPTED_LIVING_PLANT' or not review.get('note', '').strip():
        raise ValueError('Actual image review is missing')
    rel = review.get('evidencePath', '')
    if not rel.startswith('libraries/plants/integration-reviews/') or not rel.endswith('.md'):
        raise ValueError('A public visual review record is required')
    raw = public_file(root, rel).read_bytes()
    if sha(raw) != review.get('evidenceSha256') or plant_id not in raw.decode('utf-8'):
        raise ValueError('Visual review evidence differs')


def decode(raw, *, derivative=False):
    from PIL import Image
    if not raw or len(raw) > 50 * 1024 * 1024:
        raise ValueError('Image byte size is invalid')
    with Image.open(io.BytesIO(raw)) as image:
        width, height = image.size
        if min(width, height) < 120 or width * height > 32_000_000:
            raise ValueError('Image dimensions are invalid')
        # Some public camera JPEGs contain a second MPO frame. Only their first
        # frame is decoded for the app; app derivatives remain plain JPEGs.
        if image.format not in {'JPEG', 'PNG', 'WEBP', 'MPO'} or getattr(image, 'n_frames', 1) > 10:
            raise ValueError('Unsupported botanical image format')
        if derivative and (image.format != 'JPEG' or max(width, height) > 2048
                           or image.getexif() or image.info.get('xmp')
                           or b'Exif\x00\x00' in raw or b'http://ns.adobe.com/xap/' in raw):
            raise ValueError('App derivative must be a metadata-free JPEG')
        image.load()
        return {'width': width, 'height': height, 'mime': Image.MIME[image.format]}


def selected_images(plant, choices, *, root, photo_root, supplemental=()):
    if len(choices) != 2:
        raise ValueError('Exactly two reviewed images are required')
    refs = {p['id']: p for p in plant['images']}
    for extra in supplemental:
        rel = extra.get('evidencePath', '')
        if not rel.startswith('libraries/plants/integration-sources/') or not rel.endswith('.json'):
            raise ValueError('Supplemental photo source evidence is required')
        raw = public_file(root, rel).read_bytes()
        if sha(raw) != extra.get('evidenceSha256'):
            raise ValueError('Supplemental source evidence differs')
        evidence = json.loads(raw)
        checked_date(evidence.get('checkedAt'))
        if evidence.get('plantId') != plant['id'] or not re.fullmatch('[0-9a-f]{64}', evidence.get('responseSha256', '')):
            raise ValueError('Supplemental source profile differs')
        photo = next((p for p in evidence.get('images', []) if p['id'] == extra.get('referenceId')), None)
        if not photo or photo['id'] in refs:
            raise ValueError('Supplemental reference is missing or duplicated')
        refs[photo['id']] = photo
    legacy = {p['path']: p for p in plant.get('legacyPhotos', [])}
    review_path = Path(root) / 'libraries/plants/integration-legacy-review.json'
    excluded = {}
    if review_path.is_file():
        reviewed = json.loads(public_file(root, 'libraries/plants/integration-legacy-review.json').read_text('utf-8'))
        excluded = {r['sha256']: r for r in reviewed['records']
                    if r['decision'] in {'EXCLUDE_FROM_INTEGRATION', 'HOLD_TAXON_REVIEW'}}
    exclusion_path = Path(root) / 'libraries/plants/integration-photo-exclusions.json'
    rejected_refs = set()
    if exclusion_path.is_file():
        rejected_refs = {r['referenceId'] for r in json.loads(public_file(root,
            'libraries/plants/integration-photo-exclusions.json').read_text())['records'] if r['plantId'] == plant['id']}
    output, files, identities, hashes = [], {}, set(), set()
    for number, choice in enumerate(choices, 1):
        if choice.get('referenceId') in rejected_refs:
            raise ValueError('The reviewed photo reference is excluded, including re-encoded originals')
        review_file(root, choice.get('visualReview', {}), plant['id'])
        checked_date(choice.get('creditReconfirmedAt'))
        kind = choice.get('sourceKind')
        if kind == 'downloaded_original':
            photo = refs.get(choice.get('referenceId'))
            if not photo:
                raise ValueError('Photo is not in the selected editorial profile')
            original = public_file(photo_root, choice.get('originalPath')).read_bytes()
            if sha(original) != choice.get('originalSha256'):
                raise ValueError('Original photo hash differs')
            decode(original)
            identity = photo['originalLocator']
            credit = {key: photo[source] for key, source in (
                ('caption', 'caption'), ('author', 'author'), ('license', 'license'),
                ('licenseUrl', 'licenseUrl'), ('source', 'sourcePage'))}
        elif kind == 'legacy_derivative':
            photo = legacy.get(choice.get('legacyPath'))
            if not photo or photo.get('editorialDecision') != 'RETAIN_ELIGIBLE_DERIVATIVE':
                raise ValueError('Historical image is excluded from reuse')
            if Path(root).resolve() == ROOT:
                # Output slots can change during import; legacy provenance always
                # refers to the preserved pre-integration image, never that slot.
                from historical_baseline import baseline_bytes
                original = baseline_bytes(photo['path'])
            else:
                original = public_file(root, photo['path']).read_bytes()
            if sha(original) != photo['sha256'] or choice.get('originalSha256') != photo['sha256']:
                raise ValueError('Historical derivative hash differs')
            if photo['sha256'] in excluded:
                raise ValueError('Renewed visual review excludes this historical image')
            identity = photo['path']
            credit = {key: photo[key] for key in ('caption', 'author', 'source')}
            credit.update(license=photo['reuseLicenseId'], licenseUrl=photo['reuseLicenseUrl'])
        else:
            raise ValueError('Image provenance is missing')
        if identity in identities:
            raise ValueError('The same image was selected twice')
        identities.add(identity)
        if not license_url_matches(credit['license'], credit['licenseUrl']):
            raise ValueError('Ineligible or mismatched image license')
        if not all(credit.values()) or urlsplit(credit['source']).scheme != 'https':
            raise ValueError('Incomplete image attribution')
        raw = public_file(photo_root, choice.get('derivativePath')).read_bytes()
        if sha(raw) != choice.get('derivativeSha256'):
            raise ValueError('App image hash differs')
        if sha(raw) in excluded:
            raise ValueError('Renewed visual review excludes this app image')
        dimensions = decode(raw, derivative=True)
        if any(choice.get(key) != dimensions[key] for key in ('width', 'height')):
            raise ValueError('Recorded image dimensions differ')
        if sha(raw) in hashes:
            raise ValueError('Two distinct image files are required')
        hashes.add(sha(raw))
        review_raw = public_file(root, choice['visualReview']['evidencePath']).read_text('utf-8')
        if choice['derivativeSha256'] not in review_raw:
            raise ValueError('Visual review does not bind the actual app image')
        changes = choice.get('changes', '').strip()
        if not changes:
            raise ValueError('Image transformations must be documented')
        path = 'plant-images/' + plant['id'] + '-' + str(number) + '.jpg'
        if not re.fullmatch(r'plant-images/[a-z0-9-]+-[12]\.jpg', path):
            raise ValueError('Invalid plant identifier')
        credit.update(path=path, changes=changes)
        output.append(credit)
        files[ASSET_PATH + path] = raw
    return output, files


def adapt_profile(plant, old, decision, images):
    """Map sourced facts; missing values never become invented care or safety."""
    out = deepcopy(old) if old else {}
    out.update({k: plant[k] for k in ('id', 'name', 'scientificName', 'category')})
    traits = plant['traits']
    for target, source in TRAITS.items():
        out[target] = deepcopy(traits.get(source, []))
    out['family'] = ' · '.join(traits.get('family', [])) or (old or {}).get('family', '')
    out['hardiness'] = ['USDA ' + value for value in traits.get('hardinessUSDA', [])]
    out['cultivationScope'] = plant['cultivationScope']
    out['taxonScope'] = plant.get('taxonScope', 'species')
    resolution = plant['taxonomy'].get('authorityResolution') or {}
    out['acceptedName'] = resolution.get('acceptedName', '')
    out['alternativeNames'] = unique([*(old or {}).get('alternativeNames', []),
        *plant.get('sourceIdentity', []), resolution.get('legacyName'), resolution.get('acceptedName')])
    out['scopeWarnings'] = deepcopy(plant.get('scopeWarnings', []))
    safety = plant['safety']
    historic = safety.get('legacyReportedWarningsPendingReconfirmation', {})
    out['poison'] = unique([*historical_warnings([*(old or {}).get('poison', []), *historic.get('poison', [])]),
                            *safety.get('poisonSeverity', [])])
    out['contact'] = unique([*(old or {}).get('contact', []), *historic.get('contact', []),
                             *safety.get('contactDermatitisSource', [])])
    out['safetyNotice'] = safety.get('notice', '')
    out['rhsWarnings'] = deepcopy(safety.get('rhsWarningSource', []))
    # Free historical care prose is not automatically a newly verified recommendation.
    for field in ('care', 'planting'):
        retained = decision.get('retainedTextEvidence', {}).get(field)
        out[field] = ''
        if retained:
            text = (old or {}).get(field, '')
            if (not text or sha(text.encode()) != retained.get('sha256')
                    or not any(s['url'] == retained.get('source') for s in plant.get('sources', []))):
                raise ValueError('Retained care text lacks matching source evidence')
            checked_date(retained.get('checkedAt'))
            out[field] = text
    if plant['cultivationScope'] == 'IDENTIFICATION_AND_PREVENTION_REFERENCE_NO_CULTIVATION_ADVICE':
        out['propagation'] = []
        out['care'] = out['planting'] = ''
    sources = {(s['url']): deepcopy(s) for s in (old or {}).get('sources', [])}
    for s in plant.get('sources', []):
        sources[s['url']] = {'url': s['url'], 'title': urlsplit(s['url']).hostname,
                             'checked': s['checkedAt'], 'evidenceSha256': s['evidenceSha256']}
    for s in plant.get('officialContextEvidence', []):
        sources[s['url']] = {'url': s['url'], 'title': s['title'], 'checked': s['checkedAt']}
    for url in resolution.get('sourceUrls', []):
        sources[url] = {'url': url, 'title': 'Kew · botanischer Namensabgleich',
                        'checked': plant['taxonomy']['checkedAt']}
    legal = plant.get('legalReview')
    if legal:
        out['regionalRestrictions'] = deepcopy(legal)
        for key, s in [('EU', legal['EU']), ('CH', legal['CH'])]:
            for name in ('source', 'restrictionsSource', 'lawSource', 'currentAuthoritySource'):
                if s.get(name):
                    sources[s[name]] = {'url': s[name], 'title': key + ' · amtliche Rechtsquelle',
                                        'checked': legal['checkedAt']}
        neo = out.get('neophyte')
        if neo:
            eu = legal['EU']
            if eu.get('effectiveDate'):
                future = eu['effectiveDate'] > legal['checkedAt']
                neo['unionConcern'] = True  # List membership is distinct from effect.
                neo['legalNotes'] = [('EU-Beschränkungen gelten ab ' if future else 'EU-Beschränkungen seit ')
                                     + eu['effectiveDate'] + '. ' + eu.get('restrictionsSummary', '')]
                neo['euEffectiveDate'] = eu['effectiveDate']
            neo['legalNotes'] = unique([*neo.get('legalNotes', []), legal.get('regionalRules', '')])
    out['sources'] = list(sources.values())
    out['images'] = images
    out['editorialRevision'] = {'sourceCommit': LIBRARY_COMMIT,
        'profileSha256': entry_sha(plant), 'checked': decision['factsCheckedAt']}
    return out


def compile_catalog(library, current, manifest, *, root=ROOT, photo_root=None, require_complete=False):
    if (manifest.get('format') != FORMAT or manifest.get('sourceCommit') != LIBRARY_COMMIT
            or manifest.get('sourceLibrarySha256') != entry_sha(library)):
        raise ValueError('Manifest does not bind the editorial library')
    selected = {p['id']: p for p in library['plants']}
    if len(selected) != len(library['plants']):
        raise ValueError('Duplicate editorial identifiers')
    decisions = {p['id']: p for p in manifest['profiles']}
    if len(decisions) != len(manifest['profiles']) or set(decisions) != set(selected):
        raise ValueError('Manifest profile inventory differs')
    current_ids = [p['id'] for p in current['plants']]
    if len(set(current_ids)) != len(current_ids):
        raise ValueError('Duplicate app identifiers')
    result = deepcopy(current)
    by_id = {p['id']: p for p in result['plants']}
    changes, files, held, taxons = [], {}, [], {}
    for plant in library['plants']:
        decision = decisions[plant['id']]
        if decision.get('profileSha256') != entry_sha(plant):
            raise ValueError('Profile review is stale')
        if decision.get('decision') == 'HOLD':
            if not decision.get('reasons'):
                raise ValueError('Held profile needs a concrete reason')
            held.append(plant['id'])
            continue
        if decision.get('decision') != 'APPROVED':
            raise ValueError('Unknown integration decision')
        checked_date(decision.get('factsCheckedAt'))
        if plant['review']['sourceFacts'] not in {'CHECKED', 'OFFICIAL_IDENTIFICATION_REFERENCE_NO_CULTIVATION_DATA'}:
            raise ValueError('Unverified source facts')
        if plant['taxonomy']['status'] not in {'EXACT_MATCH', 'VERIFIED_BY_KEW'}:
            raise ValueError('Unresolved taxonomy')
        identity = plant['taxonomy'].get('authorityResolution') or {}
        taxon = identity.get('acceptedName') or plant['taxonomy'].get('acceptedUsageKey')
        if not taxon or taxon in taxons:
            raise ValueError('Duplicate or unresolved accepted taxon')
        taxons[taxon] = plant['id']
        images, media = selected_images(plant, decision.get('images', []), root=root, photo_root=photo_root,
                                        supplemental=decision.get('additionalPhotoReferences', ()))
        legacy_id = plant.get('legacyId', plant['id'])
        if legacy_id != plant['id']:
            raise ValueError('Existing app identifiers must not be renamed')
        updated = adapt_profile(plant, by_id.get(legacy_id), decision, images)
        by_id[plant['id']] = updated
        files.update(media)
        changes.append(plant['id'])
    if require_complete and held:
        raise ValueError('Integration blocked: ' + str(len(held)) + ' selected profiles lack actual image approval')
    result['plants'] = [by_id[p] for p in current_ids]
    result['plants'] += [by_id[p['id']] for p in library['plants'] if p['id'] not in current_ids and p['id'] in changes]
    if changes:
        result['edition'] = manifest['edition']
        result['scope'] = 'Quellenbelegte Pflanzenprofile für Gartenalltag und Identifikation; regionale Grenzen beachten.'
    return result, files, {'selected': len(selected), 'approved': len(changes), 'held': len(held),
                           'heldIds': held, 'changedIds': changes, 'outputProfiles': len(result['plants'])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--photo-root', type=Path)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--require-complete', action='store_true')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if args.write and args.check:
        parser.error('--write and --check are mutually exclusive')
    library = json.loads((LIBRARY / 'plant-library.json').read_text('utf-8'))
    from historical_baseline import baseline_bytes
    pinned = json.loads(baseline_bytes('libraries/plants/plant-library.json', commit=LIBRARY_COMMIT))
    if library != pinned:
        raise ValueError('Editorial library differs from the preserved revision2 source')
    manifest = json.loads((LIBRARY / 'integration-manifest.json').read_text('utf-8'))
    current_raw = (ROOT / CATALOG_PATH).read_bytes()
    current = json.loads(current_raw)
    result, files, report = compile_catalog(library, current, manifest, root=ROOT,
        photo_root=args.photo_root, require_complete=args.require_complete or args.write)
    # An empty preview preserves the published bytes, including original formatting.
    raw = encoded(result) if report['approved'] else current_raw
    files[CATALOG_PATH] = raw
    report['catalogSha256'] = sha(raw)
    report['mode'] = 'write' if args.write else 'check' if args.check else 'preview'
    report['releaseReady'] = report['held'] == 0
    changed = [name for name, data in files.items() if not (ROOT / name).is_file() or (ROOT / name).read_bytes() != data]
    report['changedFiles'] = changed
    if args.check and changed:
        raise ValueError('App catalogue/assets differ from their integration manifest')
    if args.write:
        # Validate the complete output set before mutating any public asset.
        from privacy_guard import scan_files
        scan_files(files)
        targets = []
        for name, data in files.items():
            path = ROOT / name
            parent = path.parent
            if path.is_symlink() or parent.is_symlink() or not parent.resolve().is_relative_to(ROOT.resolve()):
                raise ValueError('Unsafe catalogue output')
            parent.mkdir(parents=True, exist_ok=True)
            targets.append((path, data))
        with tempfile.TemporaryDirectory(prefix='qa_plant_import_') as temp:
            backups = {p: p.read_bytes() if p.is_file() else None for p, _ in targets}
            written = []
            try:
                for number, (path, data) in enumerate(targets):
                    staged = Path(temp) / str(number)
                    staged.write_bytes(data)
                    # Repository and OS temp can be different filesystems.
                    with tempfile.NamedTemporaryFile(dir=path.parent, prefix='.plant-import-', delete=False) as f:
                        staged_copy = Path(f.name)
                        try:
                            f.write(staged.read_bytes())
                            f.flush()
                            os.fsync(f.fileno())
                            os.replace(staged_copy, path)
                        finally:
                            staged_copy.unlink(missing_ok=True)
                    written.append(path)
            except BaseException:
                for path in reversed(written):
                    if backups[path] is None:
                        path.unlink(missing_ok=True)
                    else:
                        path.write_bytes(backups[path])
                raise
    print(json.dumps(report, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError) as error:
        print(json.dumps({'status': 'BLOCKED', 'reason': str(error)}, ensure_ascii=False))
        raise SystemExit(1)
