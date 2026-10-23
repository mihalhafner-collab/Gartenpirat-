"""Independent read-only audit of the complete editorial library.

Produces reports only when --report is explicit. Does not import the builder.
An audit of records/credits is not an expert botanical or visual certification.
"""
from __future__ import annotations

import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import re
import unicodedata
from urllib.parse import urlparse, unquote
from historical_baseline import baseline_bytes, BASELINE_COMMIT

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def digest_bytes(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def credit_token(value):
    if value.strip().upper() in {'CC0', 'CC0 1.0', 'CC0-1.0'}:
        return 'cc010'
    return ''.join(c for c in unicodedata.normalize('NFKD', value).casefold()
                   if c.isalnum() and not unicodedata.combining(c))


def license_url_matches(identifier, url):
    if urlparse(url).hostname != 'creativecommons.org':
        return False
    if identifier == 'CC0-1.0':
        return urlparse(url).path.rstrip('/') in {'/publicdomain/zero/1.0', '/publicdomain/zero/1.0/deed.en'}
    match = re.fullmatch(r'CC-BY(-SA)?-([1234]\.0|2\.5|2\.1)(?:-([A-Z]{2}))?', identifier)
    if not match:
        return False
    expected = '/licenses/by' + ('-sa' if match[1] else '') + '/' + match[2]
    if match[3]:
        expected += '/' + match[3].lower()
    return urlparse(url).path.rstrip('/') in {expected, expected + '/deed.en', expected + '/deed.de', expected + '/legalcode'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', choices=('initial', 'final'))
    parser.add_argument('--baseline-commit', default=BASELINE_COMMIT,
                        help='Pinned historical Git object; a different object must match the library baseline')
    args = parser.parse_args()
    library_path = HERE / 'plant-library.json'
    library = json.loads(library_path.read_text())
    baseline_path = ROOT / 'android/app/src/main/assets/plant-catalog.json'
    baseline_raw = baseline_bytes(baseline_path.relative_to(ROOT).as_posix(), root=ROOT, commit=args.baseline_commit)
    baseline = json.loads(baseline_raw)
    original_images = {'android/app/src/main/assets/' + i['path']: i
                       for p in baseline['plants'] for i in p.get('images', [])}
    evidence = {e['url']: e for e in json.loads((HERE / 'evidence/profiles.json').read_text())}
    taxons = {e['query']: e for e in json.loads((HERE / 'evidence/taxonomy.json').read_text())}
    commons = {e['url']: e for e in json.loads((HERE / 'evidence/commons.json').read_text())['sources']}
    replacements = {e['url']: e for e in json.loads((HERE / 'evidence/replacements.json').read_text())['sources']}
    supplemental = {e['url']: e for e in json.loads((HERE / 'evidence/supplemental-photos.json').read_text())['sources']}
    resolutions = {e['plantId']: e for e in json.loads((HERE / 'evidence/taxonomic-resolution.json').read_text())['records']}
    legal = {e['plantId']: e for e in json.loads((HERE / 'evidence/legal-review.json').read_text())['records']}
    bfn = json.loads((HERE / 'evidence/legal-sources.json').read_text())['sources'][0]
    excluded_sources = {unquote(e['sourcePage']) for e in json.loads((HERE / 'evidence/photo-exclusions.json').read_text())['records']}
    screening = {e['path']: e for e in json.loads((HERE / 'evidence/visual-screening.json').read_text())['records']}
    plants = library['plants']
    queue = json.loads((HERE / 'review-queue.json').read_text())
    held = queue.get('heldCandidates', [])
    errors = []
    ids = [p['id'] for p in plants + held]
    if len(set(ids)) != len(ids):
        errors.append('DUPLICATE_IDS')
    if (library['baseline']['catalogSha256'] != hashlib.sha256(baseline_raw).hexdigest()
            or library['baseline'].get('repositoryCommit') != args.baseline_commit):
        errors.append('APPLICATION_BASELINE_CHANGED')
    if {p['id'] for p in baseline['plants']} != {p['legacyId'] for p in plants if 'legacyId' in p}:
        errors.append('LEGACY_INVENTORY_MISMATCH')
    scope = json.loads((HERE / 'review-scope.json').read_text())
    if scope.get('selectedIds') and set(scope['selectedIds']) != {p['id'] for p in plants}:
        errors.append('SELECTED_REVIEW_SCOPE_CHANGED')
    new_keys = set()
    old_keys = {p['taxonomy']['acceptedUsageKey'] for p in plants if p['origin'] == 'legacy_reaudited'}
    credit_rows, entry_rows = [], []
    local_bytes_checked = 0
    for p in plants + held:
        def fail(code):
            errors.append(p['id'] + ':' + code)
        selected = p in plants
        is_new = p['origin'] == 'extension' and selected
        tax = p['taxonomy']
        record = taxons.get(tax.get('query'), {})
        if tax['match'] != record.get('result', {}):
            fail('TAXONOMY_EVIDENCE_MISMATCH')
        if tax.get('authorityResolution') != resolutions.get(p['id']):
            fail('TAXONOMIC_RESOLUTION_MISMATCH')
        if tax['status'] == 'VERIFIED_BY_KEW' and p['id'] not in resolutions:
            fail('UNSUPPORTED_KEW_CLAIM')
        if p.get('legalReview') != legal.get(p['id']):
            fail('LEGAL_REVIEW_EVIDENCE_MISMATCH')
        if p.get('legalReview'):
            eu = p['legalReview']['EU']
            if 'effectiveDate' in eu:
                row = next((r for r in bfn['rows'] if r['scientificName'] == eu['listedName']), None)
                if not row or '-'.join(reversed(row['effectiveDate'].split('.'))) != eu['effectiveDate']:
                    fail('LEGAL_EFFECTIVE_DATE_MISMATCH')
                expected = 'RESTRICTIONS_IN_FORCE' if eu['effectiveDate'] <= p['legalReview']['checkedAt'] else 'LISTED_FUTURE_EFFECTIVE_DATE'
                if eu['status'] != expected:
                    fail('LEGAL_EFFECTIVE_STATUS_MISMATCH')
        if is_new:
            key = tax['acceptedUsageKey']
            if key in old_keys or key in new_keys:
                fail('DUPLICATE_ACCEPTED_TAXON')
            new_keys.add(key)
            match = tax['match']
            if match.get('matchType') != 'EXACT' or match.get('kingdom') != 'Plantae' or match.get('confidence', 0) < 95:
                fail('UNRESOLVED_NEW_TAXON')
            if len(p['images']) < 2:
                fail('MISSING_PHOTO_REFERENCES')
        for field, prov in p['fieldEvidence'].items():
            e = evidence.get(prov['source'], {})
            if prov['values'] != e.get('facts', {}).get(prov['field']):
                fail('UNSUPPORTED_FIELD:' + field)
        for source in p.get('sources', []):
            original = evidence.get(source['url'])
            actual = hashlib.sha256(json.dumps(original, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()
            if not original or source['evidenceSha256'] != actual:
                fail('SOURCE_HASH_MISMATCH')
        for prov in p['safety'].get('rhsWarningEvidence', []):
            if prov['values'] != evidence.get(prov['source'], {}).get('facts', {}).get(prov['field']):
                fail('UNSUPPORTED_RHS_WARNING')
        if p['safety']['status'] == 'UNKNOWN_NOT_PROVEN_HARMLESS' and p['safety']['poisonSeveritySource']:
            fail('WARNING_LOST')
        if p['review']['implementation'] != 'HOLD':
            fail('UNPROVEN_IMPLEMENTATION_APPROVAL')
        if p['review'].get('expertBotanicalReview', 'NOT_PERFORMED') != 'NOT_PERFORMED':
            fail('UNSUPPORTED_EXPERT_APPROVAL')
        seen_photos = set()
        for photo in p['images']:
            if unquote(photo['sourcePage']) in excluded_sources:
                fail('EXCLUDED_PHOTO_REINTRODUCED')
            if not re.fullmatch(r'CC-BY(?:-SA)?-(?:[1234]\.0|2\.5)(?:-[A-Z]{2})?|CC-BY-SA-2\.1-JP|CC0-1\.0', photo['license']):
                fail('INELIGIBLE_LICENSE')
            if photo['originalLocator'] in seen_photos:
                fail('DUPLICATE_PHOTO')
            seen_photos.add(photo['originalLocator'])
            if '?' in photo['originalLocator']:
                fail('SIGNED_OR_UNSTABLE_URL')
            if not license_url_matches(photo['license'], photo['licenseUrl']):
                fail('LICENSE_URL')
            figures = evidence.get(photo['sourcePage'], {}).get('figures', [])
            ncsu_supported = any(f.get('url') == photo['originalLocator'] and f.get('author') == photo['author']
                       and f.get('caption') == photo['caption'] and f.get('licenseId') == photo['license']
                       and f.get('eligible') is True for f in figures)
            cp = commons.get(photo['sourcePage']) or replacements.get(photo['sourcePage']) or supplemental.get(photo['sourcePage']) or {}
            commons_supported = (cp.get('originalLocator') == photo['originalLocator']
                                 and cp.get('fields', {}).get('Author') == photo['author']
                                 and photo['license'] in cp.get('licenses', []))
            if not ncsu_supported and not commons_supported:
                fail('UNSUPPORTED_PHOTO_CREDIT')
            if photo['visualReview'] == 'CONTACT_SHEET_SCREENED':
                screen = screening.get(photo.get('existingPath'), {})
                if (screen.get('status') != 'CONTACT_SHEET_SCREENED_NO_GROSS_MISMATCH_DETECTED'
                        or screen.get('sha256') != photo.get('existingSha256')
                        or photo['binaryVerification'] != 'EXISTING_DERIVATIVE_HASHED'):
                    fail('UNSUPPORTED_LOCAL_PHOTO_REVIEW_CLAIM')
            elif photo['visualReview'] != 'NOT_PERFORMED' or photo['binaryVerification'] != 'NOT_DOWNLOADED':
                fail('UNSUPPORTED_PHOTO_REVIEW_CLAIM')
            credit_rows.append({'plantId': p['id'], 'plantName': p['name'],
                                'selection': 'SELECTED_METADATA' if selected else 'HELD_CANDIDATE', **photo})
        for photo in p['legacyPhotos']:
            original = original_images.get(photo['path'], {})
            if any(photo.get(k) != original.get(k) for k in ('caption', 'author', 'license', 'source', 'licenseUrl')):
                fail('LEGACY_CREDIT_HISTORY_CHANGED')
            cp = commons.get(photo['source'], {})
            cp_ok = (bool(cp.get('fields', {}).get('Author'))
                     and credit_token(cp['fields']['Author']) == credit_token(photo['author'])
                     and any(credit_token(v) == credit_token(photo['license']) for v in cp.get('licenses', [])))
            figure_ok = any(f.get('eligible') and credit_token(f.get('author', '')) == credit_token(photo['author'])
                            and credit_token(f.get('caption', '')) == credit_token(photo['caption'])
                            and credit_token(f.get('licenseRaw', '')) == credit_token(photo['license'])
                            for s in p.get('sources', []) for f in evidence.get(s['url'], {}).get('figures', []))
            if photo['status'] == 'CREDIT_RECONFIRMED' and not (cp_ok or figure_ok):
                fail('UNSUPPORTED_LEGACY_CREDIT_RECONFIRMATION')
            if photo.get('editorialDecision') == 'RETAIN_ELIGIBLE_DERIVATIVE':
                if (credit_token(photo.get('reuseLicenseId', '')) != credit_token(photo['license'])
                        or not license_url_matches(photo.get('reuseLicenseId', ''), photo.get('reuseLicenseUrl', ''))):
                    fail('INVALID_LEGACY_REUSE_LICENSE')
            raw = baseline_bytes(photo['path'], root=ROOT, commit=args.baseline_commit)
            if hashlib.sha256(raw).hexdigest() != photo['sha256']:
                fail('LEGACY_PHOTO_BYTES_CHANGED')
            screen = screening.get(photo['path'], {})
            if screen.get('sha256') != photo['sha256'] or photo['visualReview'] != screen.get('status'):
                fail('LEGACY_SCREENING_EVIDENCE_MISMATCH')
            if photo['visualReview'].startswith(('REJECT_', 'HOLD_')) and photo['usableAsLivingPlantPhoto']:
                fail('REJECTED_IMAGE_MARKED_USABLE')
            if photo.get('editorialDecision') == 'RETAIN_ELIGIBLE_DERIVATIVE' and (photo['status'] != 'CREDIT_RECONFIRMED' or not photo['usableAsLivingPlantPhoto']):
                fail('UNSUPPORTED_LEGACY_REUSE_DECISION')
            local_bytes_checked += 1
        entry_rows.append({
            'id': p['id'], 'name': p['name'], 'scientificName': p['scientificName'],
            'category': p['category'], 'origin': p['origin'],
            'selection': 'SELECTED_METADATA' if selected else 'HELD_CANDIDATE',
            'taxonomy': p['taxonomy']['status'],
            'sourceFacts': p['review']['sourceFacts'], 'photoReferences': len(p['images']),
            'legacyCreditsReconfirmed': sum(i['status'] == 'CREDIT_RECONFIRMED' for i in p['legacyPhotos']),
            'legacyImagesScreened': len(p['legacyPhotos']),
            'legacyImagesFlagged': sum(not i['usableAsLivingPlantPhoto'] for i in p['legacyPhotos']),
            'legacyExcludedFromReuse': sum(i.get('editorialDecision') == 'CLOSED_EXCLUDED_FROM_LIBRARY_USE' for i in p['legacyPhotos']),
            'newImageVisualReview': 'NOT_PERFORMED', 'implementation': 'HOLD',
            'blockers': '; '.join(p['review']['blockers']),
        })
    # Hash and count every checked input. The second pass is reproducible.
    report = {
        'format': 'gartenpirat.plant-library-audit.v1',
        'pass': args.report or 'read-only',
        'status': 'PASS_WITH_DOCUMENTED_HOLDS' if not errors else 'FAIL',
        'counts': {'profiles': len(plants), 'legacyProfiles': sum('legacyId' in p for p in plants),
                   'newProfiles': sum(p['origin'] == 'extension' for p in plants),
                   'heldCandidatesAudited': len(held), 'totalRecordsAudited': len(plants) + len(held),
                   'photoReferences': sum(len(p['images']) for p in plants),
                   'heldCandidatePhotoReferences': sum(len(p['images']) for p in held),
                   'localPhotosByteChecked': local_bytes_checked,
                   'newPhotosDownloaded': 0, 'newPhotoVisualReviews': 0,
                   'legacyImagesScreened': len(screening),
                   'legacyImagesFlagged': sum(e['status'].startswith(('REJECT_', 'HOLD_')) for e in screening.values()),
                   'botanicalExpertApprovals': 0,
                   'legacyCreditsReconfirmed': sum(p['status'] == 'CREDIT_RECONFIRMED' for e in plants for p in e['legacyPhotos']),
                   'legacyImagesEligibleForReuse': sum(i.get('editorialDecision') == 'RETAIN_ELIGIBLE_DERIVATIVE' for p in plants for i in p['legacyPhotos']),
                   'legacyImagesExcludedFromReuse': sum(i.get('editorialDecision') == 'CLOSED_EXCLUDED_FROM_LIBRARY_USE' for p in plants for i in p['legacyPhotos']),
                   'officialLegalReviews': sum('legalReview' in p for p in plants),
                   'taxonomyAuthorityResolutions': sum(bool(p['taxonomy'].get('authorityResolution')) for p in plants),
                   'taxonomyExact': sum(p['taxonomy']['status'] == 'EXACT_MATCH' for p in plants),
                   'factsChecked': sum(p['review']['sourceFacts'] == 'CHECKED' for p in plants)},
        'categories': Counter(p['category'] for p in plants),
        'holds': Counter(b for p in plants for b in p['review']['blockers']),
        'heldCandidateReasons': Counter(b for p in held for b in p['review']['blockers']),
        'errors': errors,
        'inputs': {str(p.relative_to(ROOT)): hashlib.sha256(baseline_raw).hexdigest() if p == baseline_path else digest_bytes(p) for p in (
            library_path, HERE / 'evidence/profiles.json', HERE / 'evidence/taxonomy.json',
            HERE / 'review-queue.json', HERE / 'evidence/commons.json',
            HERE / 'evidence/replacements.json', HERE / 'evidence/visual-screening.json',
            HERE / 'evidence/official-context.json', HERE / 'evidence/commons-api.json',
            HERE / 'evidence/supplemental-photos.json', HERE / 'evidence/taxonomic-resolution.json',
            HERE / 'evidence/legal-review.json', HERE / 'evidence/legal-sources.json',
            HERE / 'evidence/photo-exclusions.json',
            HERE / 'evidence/eu-restrictions.json', HERE / 'review-scope.json', baseline_path)},
        'scope': 'Full record/field/credit/baseline audit; no independent botanical expert or remote image visual inspection.',
    }
    if args.report:
        (HERE / ('audit-' + args.report + '.json')).write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        for name, rows in [('entry-review.csv', entry_rows), ('photo-credits.csv', credit_rows)]:
            with (HERE / name).open('w', encoding='utf-8-sig', newline='') as f:
                writer = csv.DictWriter(f, fieldnames=list(dict.fromkeys(k for row in rows for k in row)), delimiter=';', lineterminator='\n')
                writer.writeheader()
                writer.writerows(rows)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(bool(errors))


if __name__ == '__main__':
    main()
