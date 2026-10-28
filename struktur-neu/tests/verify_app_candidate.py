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


def candidate_assets(apk, assets_root, intermediates):
    comparison = compare_assets_to_source(apk, assets_root)
    profiles = {'dexopt/baseline.prof': ('binary_art_profile', b'pro\0'),
                'dexopt/baseline.profm': ('binary_art_profile_metadata', b'prm\0')}
    verified = {}
    with zipfile.ZipFile(apk) as archive:
        for name in comparison['added']:
            if name not in profiles:
                continue  # Every unknown addition continues to fail the asset gate.
            folder, magic = profiles[name]
            reference = Path(intermediates) / folder / 'release/compileReleaseArtProfile' / Path(name).name
            raw = archive.read('assets/' + name)
            if (not reference.is_file() or reference.is_symlink()
                    or not raw.startswith(magic) or raw != reference.read_bytes()):
                raise ValueError('Generated ART profile lacks an identical compiler output')
            verified[name] = hashlib.sha256(raw).hexdigest()
    comparison['added'] = [name for name in comparison['added'] if name not in verified]
    comparison['generated_profiles'] = verified
    comparison['matches'] = not any(comparison[key] for key in ('added', 'removed', 'changed'))
    return comparison


def verify(apk, aapt, apksigner, baseline=None):
    meta = metadata(apk, aapt, apksigner)
    if meta['package'] != PACKAGE or meta['version_code'] <= 65:
        raise ValueError('Unexpected package or old development version')
    assets = candidate_assets(apk, ROOT/'android/app/src/main/assets', ROOT/'android/app/build/intermediates')
    if not assets['matches']:
        raise ValueError('Packaged assets differ: ' + json.dumps(assets))
    seed = empty_seed(apk)
    if not seed['all_business_collections_empty']:
        raise ValueError('Nonempty operational seed')
    production = ROOT / 'android/app/src/main/java/at/hausgarten/service'
    required = {path.stem for path in production.glob('*.java')}
    classes = dex_classes(apk)
    if not required or not all('at.hausgarten.service.' + name in classes for name in required):
        raise ValueError('Required operative classes missing from DEX')
    retired = {'DriveAccount', 'DriveRestClient', 'DriveBackupUpload', 'DriveDirectFiles',
               'BackupFolderPicker', 'BackupAutomation', 'FullBackupJob',
               'BackupPortableExport', 'BackupSafService'}
    test_sources = ROOT / 'android/app/src/androidTest/java/at/hausgarten/service'
    retired.update(path.stem for path in test_sources.glob('*.java') if path.stem not in required)
    if any('at.hausgarten.service.' + name in classes for name in retired):
        raise ValueError('Retired transport or test-only class present in release DEX')
    with zipfile.ZipFile(apk) as archive:
        catalog_raw = archive.read('assets/plant-catalog.json')
        catalog = json.loads(catalog_raw)
        html = archive.read('assets/mobile.html').decode()
        for component in ('plant-catalog.js', 'gartenpirat.js', 'design-concept.js', 'workday-ui.js', 'personal-payment.js','owner-ui.js','customer-hub.js','sync-dashboard.js','photo-settings.js','legacy-conflicts-ui.js','offers-ui.js'):
            if 'src="' + component + '"' not in html:
                raise ValueError('Component not connected to existing entry: ' + component)
        images = {image['path'] for plant in catalog['plants'] for image in plant['images']}
        if any('assets/' + image not in archive.namelist() for image in images):
            raise ValueError('Missing packaged plant image')
    original = metadata(baseline, aapt, apksigner) if baseline else None
    identity = compare_identity(original, meta) if original else 'NOT_RUN_original_APK_unavailable'
    return {'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), **meta,
            'assets': assets, 'seed': seed, 'operative_classes_present': sorted(required),
            'retired_and_test_classes_absent': sorted(retired),
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
