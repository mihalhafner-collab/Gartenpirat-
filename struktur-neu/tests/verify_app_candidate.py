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
NATIVE_ENTRIES = {
    'MainActivity', 'RevierApplication', 'NativeAppController', 'NativeRoute',
    'FullBackupActivity', 'RestoreRecoveryActivity', 'NativeComposeKt',
    'NativeFormsKt', 'NativePrimaryScreensKt', 'NativeRestoredDraftScreensKt',
    'ComposeMediaScreensKt', 'ComposePlanningScreensKt',
    'ComposeRevierScreensKt', 'ComposeFinanceScreensKt',
}


def kotlin_tokens(source):
    """Keep literals opaque, including nested Kotlin string interpolation/comments."""
    def comment(at):
        if source.startswith('//', at):
            end = source.find('\n', at + 2)
            return len(source) if end < 0 else end
        level, at = 1, at + 2
        while level and at < len(source):
            if source.startswith('/*', at):
                level, at = level + 1, at + 2
            elif source.startswith('*/', at):
                level, at = level - 1, at + 2
            else:
                at += 1
        if level:
            raise ValueError('Unterminated Kotlin comment')
        return at

    def interpolation(at):
        level = 1
        while at < len(source):
            if source.startswith(('//', '/*'), at):
                at = comment(at)
            elif source[at] in ('"', "'"):
                at = literal(at)
            elif source[at] == '{':
                level, at = level + 1, at + 1
            elif source[at] == '}':
                level, at = level - 1, at + 1
                if not level:
                    return at
            else:
                at += 1
        raise ValueError('Unterminated Kotlin interpolation')

    def literal(at):
        raw = source.startswith('"""', at)
        delimiter = '"""' if raw else source[at]
        at += len(delimiter)
        while at < len(source):
            if source.startswith(delimiter, at):
                return at + len(delimiter)
            if not raw and source[at] == '\\':
                at += 2
            elif delimiter != "'" and source.startswith('${', at):
                at = interpolation(at + 2)
            else:
                at += 1
        raise ValueError('Unterminated Kotlin literal')

    tokens, at = [], 0
    token_pattern = re.compile(r'[^\W\d]\w*|::|.')
    while at < len(source):
        if source[at].isspace():
            at += 1
        elif source.startswith(('//', '/*'), at):
            at = comment(at)
        elif source[at] in ('"', "'"):
            end = literal(at)
            tokens.append(source[at:end])
            at = end
        elif source[at] == '`':
            end = source.find('`', at + 1)
            if end < 0:
                raise ValueError('Unterminated Kotlin identifier')
            tokens.append(source[at:end + 1])
            at = end + 1
        else:
            match = token_pattern.match(source, at)
            tokens.append(match[0])
            at += len(match[0])
    return tokens


def kotlin_jvm_targets(path):
    """Named top-level types and compiler file facades, rather than source stems."""
    tokens = kotlin_tokens(path.read_text(encoding='utf-8'))
    names, package, facade, facade_name = set(), '', False, path.stem + 'Kt'
    braces = parentheses = brackets = 0
    for index, token in enumerate(tokens):
        following = tokens[index + 1] if index + 1 < len(tokens) else ''
        if tokens[index:index + 4] == ['@', 'file', ':', 'JvmMultifileClass']:
            raise ValueError('Multifile Kotlin facade needs an explicit DEX contract: ' + path.name)
        if tokens[index:index + 5] == ['@', 'file', ':', 'JvmName', '(']:
            name = tokens[index + 5] if index + 5 < len(tokens) else ''
            match = re.fullmatch(r'"([A-Za-z_$][\w$]*)"', name)
            if not match:
                raise ValueError('Unsupported Kotlin file JVM name: ' + path.name)
            facade_name = match[1]
        if not braces and not parentheses and not brackets:
            if token == 'package':
                parts, at = [], index + 1
                while at < len(tokens) and (re.fullmatch(r'[^\W\d]\w*', tokens[at]) or tokens[at] == '.'):
                    if parts and tokens[at] != '.' and parts[-1] != '.':
                        break
                    parts.append(tokens[at])
                    at += 1
                package = ''.join(parts)
            elif token in ('class', 'interface', 'object') and (index == 0 or tokens[index - 1] != '::'):
                if re.fullmatch(r'[^\W\d]\w*|`[^`]+`', following):
                    names.add(following.strip('`'))
            elif token in ('val', 'var') or token == 'fun' and following != 'interface':
                facade = True
        braces += (token == '{') - (token == '}')
        parentheses += (token == '(') - (token == ')')
        brackets += (token == '[') - (token == ']')
        if min(braces, parentheses, brackets) < 0:
            raise ValueError('Unbalanced Kotlin source: ' + path.name)
    if braces or parentheses or brackets:
        raise ValueError('Unbalanced Kotlin source: ' + path.name)
    if package != PACKAGE and not package.startswith(PACKAGE + '.'):
        raise ValueError('Unexpected Kotlin package: ' + path.name)
    if facade:
        names.add(facade_name)
    return {package + '.' + name for name in names}, package + '.' + facade_name if facade else None


def production_targets(production):
    java = sorted(path.relative_to(production).as_posix() for path in production.rglob('*.java'))
    if java:
        raise ValueError('Application Java source remains: ' + ', '.join(java))
    paths = sorted(production.rglob('*.kt'))
    if not paths:
        raise ValueError('No operative Kotlin source')
    required, facades, proof = set(), set(), {}
    for path in paths:
        targets, facade = kotlin_jvm_targets(path)
        if not targets:
            raise ValueError('No inspectable Kotlin JVM target: ' + path.name)
        if required & targets:
            raise ValueError('Ambiguous Kotlin JVM target: ' + path.name)
        required.update(targets)
        if facade:
            facades.add(facade)
        proof[path.relative_to(production).as_posix()] = sorted(targets)
    if not {PACKAGE + '.' + name for name in NATIVE_ENTRIES} <= required:
        raise ValueError('Required native Compose entry source missing')
    return required, facades, proof


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
            # Build Tools 37 names an ordinary Gradle test signer "V2 Signer:".
            # apksigner must still verify the complete APK and report exactly one certificate.
            lines = [line for line in signed.stdout.splitlines() if 'certificate SHA-256 digest:' in line]
            v2 = re.fullmatch(r'V2 Signer: certificate SHA-256 digest: ([a-fA-F0-9]{64})', lines[0]) if len(lines) == 1 else None
            if v2 and re.findall(r'^Number of signers: ([0-9]+)[ \t]*$', signed.stdout, re.MULTILINE) == ['1']:
                certs = [v2[1].lower()]
    return {'package': match[1], 'version_code': int(match[2]), 'version_name': match[3],
            'signature_valid': signed.returncode == 0 and bool(certs), 'certificates': sorted(c.lower() for c in certs),
            'internet_permission': "uses-permission: name='android.permission.INTERNET'" in result.stdout,
            'application_label': (re.search(r"application-label:'([^']*)'", result.stdout).group(1) if re.search(r"application-label:'([^']*)'", result.stdout) else '')}


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


def candidate_assets(apk, assets_root, intermediates, variant="release"):
    comparison = compare_assets_to_source(apk, assets_root)
    profiles = {'dexopt/baseline.prof': ('binary_art_profile', b'pro\0'),
                'dexopt/baseline.profm': ('binary_art_profile_metadata', b'prm\0')}
    verified = {}
    with zipfile.ZipFile(apk) as archive:
        for name in comparison['added']:
            if name not in profiles:
                continue  # Every unknown addition continues to fail the asset gate.
            folder, magic = profiles[name]
            reference = Path(intermediates) / folder / variant / ('compile' + variant[0].upper() + variant[1:] + 'ArtProfile') / Path(name).name
            raw = archive.read('assets/' + name)
            if (not reference.is_file() or reference.is_symlink()
                    or not raw.startswith(magic) or raw != reference.read_bytes()):
                raise ValueError('Generated ART profile lacks an identical compiler output')
            verified[name] = hashlib.sha256(raw).hexdigest()
    comparison['added'] = [name for name in comparison['added'] if name not in verified]
    comparison['generated_profiles'] = verified
    comparison['matches'] = not any(comparison[key] for key in ('added', 'removed', 'changed'))
    return comparison


def verify(apk, aapt, apksigner, baseline=None, ui_preview=False):
    meta = metadata(apk, aapt, apksigner)
    if meta['package'] != (PACKAGE + '.preview' if ui_preview else PACKAGE) or meta['version_code'] <= 65:
        raise ValueError('Unexpected package or old development version')
    if ui_preview and (baseline is not None or meta['internet_permission'] or meta['application_label'] != 'Haus & Garten Test' or not meta['version_name'].endswith('-test') or not meta['signature_valid']):
        raise ValueError('Preview must be a separately signed, labelled test app; never an operative update')
    assets = candidate_assets(apk, ROOT/'android/app/src/main/assets', ROOT/'android/app/build/intermediates', 'uiPreview' if ui_preview else 'release')
    if not assets['matches']:
        raise ValueError('Packaged assets differ: ' + json.dumps(assets))
    seed = empty_seed(apk)
    if not seed['all_business_collections_empty']:
        raise ValueError('Nonempty operational seed')
    production = ROOT / 'android/app/src/main/java/at/hausgarten/service'
    required, facades, source_proof = production_targets(production)
    classes = dex_classes(apk)
    missing = sorted(required - classes)
    if missing:
        raise ValueError('Required operative Kotlin classes/facades missing from DEX: ' + ', '.join(missing))
    retired = {'DriveAccount', 'DriveRestClient', 'DriveBackupUpload', 'DriveDirectFiles',
               'BackupFolderPicker', 'BackupAutomation', 'FullBackupJob',
               'BackupPortableExport', 'BackupSafService'}
    test_sources = ROOT / 'android/app/src/androidTest/java/at/hausgarten/service'
    retired = {PACKAGE + '.' + name for name in retired}
    for path in test_sources.rglob('*'):
        if path.suffix == '.kt':
            retired.update(kotlin_jvm_targets(path)[0] - required)
        elif path.suffix == '.java':
            retired.add(PACKAGE + '.' + path.stem)
    retired -= required
    if classes & retired:
        raise ValueError('Retired transport or test-only class present in release DEX')
    with zipfile.ZipFile(apk) as archive:
        catalog_raw = archive.read('assets/plant-catalog.json')
        catalog = json.loads(catalog_raw)
        web_assets = sorted(name for name in archive.namelist() if name.startswith('assets/') and Path(name).suffix.lower() in ('.js', '.css', '.html', '.htm'))
        if web_assets:
            raise ValueError('Obsolete application web assets packaged: ' + ', '.join(web_assets))
        dex = [archive.read(name) for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)]
        if not any(b'Landroidx/compose/runtime/Composer;' in data for data in dex):
            raise ValueError('Native Compose runtime missing from DEX')
        images = {image['path'] for plant in catalog['plants'] for image in plant['images']}
        if any('assets/' + image not in archive.namelist() for image in images):
            raise ValueError('Missing packaged plant image')
    original = metadata(baseline, aapt, apksigner) if baseline else None
    identity = compare_identity(original, meta) if original else 'NOT_RUN_original_APK_unavailable'
    return {'apk_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), **meta,
            'assets': assets, 'seed': seed, 'operative_classes_present': sorted(name.removeprefix(PACKAGE + '.') for name in required),
            'kotlin_source_files': len(source_proof), 'source_jvm_targets': source_proof,
            'kotlin_file_facades_present': sorted(facades), 'native_ui': 'KOTLIN_COMPOSE',
            'critical_native_entries_present': sorted(NATIVE_ENTRIES),
            'retired_and_test_classes_absent': sorted(name.removeprefix(PACKAGE + '.') for name in retired),
            'plant_profiles': len(catalog['plants']), 'plant_images': len(images),
            'catalog_sha256': hashlib.sha256(catalog_raw).hexdigest(),
            'original_apk_identity_comparison': identity,
            'fresh_install': 'NOT_RUN', 'in_place_update_data_retention': 'NOT_RUN',
            'pixel_installation': 'NOT_PERFORMED', 'release_approved': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--baseline-apk', type=Path)
    parser.add_argument('--ui-preview', action='store_true', help='Verify only the separate .preview test application')
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--apksigner', required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.apk, args.aapt, args.apksigner, args.baseline_apk, args.ui_preview)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
