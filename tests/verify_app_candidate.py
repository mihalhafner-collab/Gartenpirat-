#!/usr/bin/env python3
"""Read-only check of the existing package, assets and optional original APK identity.

An unsigned candidate is reviewable, never a verified installable update.
This tool performs no installation, signing or device/data access.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile

from compare_local_update import compare_assets_to_source, dex_classes, empty_seed

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
from tools.sign_original_apk import SigningError, single_signer_certificate

PACKAGE = 'at.hausgarten.service'


def metadata(apk, aapt, apksigner):
    result = subprocess.run([aapt, 'dump', 'badging', str(apk)], capture_output=True, text=True, check=True)
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", result.stdout)
    if not match:
        raise ValueError('APK metadata unavailable')
    signed = subprocess.run([apksigner, 'verify', '--verbose', '--print-certs', str(apk)], capture_output=True, text=True)
    certs = []
    if signed.returncode == 0:
        try:
            certs = [single_signer_certificate(signed.stdout)]
        except SigningError:
            pass
    return {'package': match[1], 'version_code': int(match[2]), 'version_name': match[3],
            'signature_valid': signed.returncode == 0 and bool(certs), 'certificates': sorted(c.lower() for c in certs)}


def compare_identity(previous, candidate):
    if previous['package'] != PACKAGE or candidate['package'] != previous['package']:
        raise ValueError('Operative package identity differs')
    if candidate['version_code'] <= previous['version_code']:
        raise ValueError('Candidate versionCode must exceed original APK')
    if not previous['signature_valid'] or not candidate['signature_valid']:
        raise ValueError('Original and candidate must both have valid APK signatures')
    if len(previous['certificates']) != 1 or previous['certificates'] != candidate['certificates']:
        raise ValueError('Original signing certificate differs')
    return 'PASS'


def verify(apk, aapt, apksigner, baseline=None):
    meta = metadata(apk, aapt, apksigner)
    if meta['package'] != PACKAGE or meta['version_code'] <= 65:
        raise ValueError('Unexpected package or old development version')
    assets = compare_assets_to_source(apk, ROOT/'android/app/src/main/assets')
    if not assets['matches']:
        raise ValueError('Packaged assets differ: ' + json.dumps(assets))
    seed = empty_seed(apk)
    if not seed['all_business_collections_empty']:
        raise ValueError('Nonempty operational seed')
    required = {'MainActivity', 'WorkSessionStore', 'DriveSyncCoordinator', 'PhotoCaptureProvider',
                'PhotoAttachmentStore', 'PendingWorkPhotoStore'}
    classes = dex_classes(apk)
    if not all('at.hausgarten.service.' + name in classes for name in required):
        raise ValueError('Required operative classes missing from DEX')
    with zipfile.ZipFile(apk) as archive:
        catalog_raw = archive.read('assets/plant-catalog.json')
        catalog = json.loads(catalog_raw)
        html = archive.read('assets/mobile.html').decode()
        for component in ('plant-catalog.js', 'gartenpirat.js', 'design-concept.js', 'workday-ui.js', 'personal-payment.js'):
            if 'src="' + component + '"' not in html:
                raise ValueError('Component not connected to existing entry: ' + component)
        images = {image['path'] for plant in catalog['plants'] for image in plant['images']}
        if any('assets/' + image not in archive.namelist() for image in images):
            raise ValueError('Missing packaged plant image')
    original = metadata(baseline, aapt, apksigner) if baseline else None
    identity = compare_identity(original, meta) if original else 'NOT_RUN_original_APK_unavailable'
    return {'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), **meta,
            'assets': assets, 'seed': seed, 'operative_classes_present': sorted(required),
            'plant_profiles': len(catalog['plants']), 'plant_images': len(images),
            'catalog_sha256': hashlib.sha256(catalog_raw).hexdigest(),
            'original_apk_identity_comparison': identity,
            'fresh_install': 'NOT_RUN', 'in_place_update_data_retention': 'NOT_RUN',
            'pixel_installation': 'NOT_PERFORMED', 'release_approved': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--baseline-apk', type=Path)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.apk, args.aapt, args.apksigner, args.baseline_apk)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
