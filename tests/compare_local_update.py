"""Read-only APK/source comparison. Never exports old seeds or private values."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile


ROOT = Path(__file__).resolve().parents[1]
COLLECTIONS = ('customers', 'orders', 'worklogs', 'invoices', 'photos', 'offers')
# Individually reviewed export-only label corruption, NOT a business-data value.
# Keep the original readable UI; do not import the export's anonymized label.
REVIEWED_LABEL = (b'Cloudname_189a14e78c', b'Arbeitsaufzeichnungen')


def apk_inventory(path):
    with zipfile.ZipFile(path) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in archive.namelist() if not name.endswith('/')}


def compare_inventory(before, after):
    return {
        'added': sorted(after.keys() - before.keys()),
        'removed': sorted(before.keys() - after.keys()),
        'changed': sorted(name for name in before.keys() & after.keys()
                          if before[name] != after[name]),
        'unchanged_count': sum(before[name] == after[name]
                               for name in before.keys() & after.keys()),
    }


def compare_assets_to_source(apk, assets):
    with zipfile.ZipFile(apk) as archive:
        packaged = {name[7:]: hashlib.sha256(archive.read(name)).hexdigest()
                    for name in archive.namelist()
                    if name.startswith('assets/') and not name.endswith('/')}
    expected = {p.relative_to(assets).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in assets.rglob('*') if p.is_file()}
    comparison = compare_inventory(expected, packaged)
    comparison['matches'] = not any(comparison[key] for key in ('added', 'removed', 'changed'))
    return comparison


def empty_seed(apk):
    with zipfile.ZipFile(apk) as archive:
        seed = json.loads(archive.read('assets/initial-data.json'))
    # Only return counts, never old seed rows, names, addresses or connection values.
    return {'all_business_collections_empty': all(seed.get(key, []) == [] for key in COLLECTIONS),
            'operational_seed_rows': sum(len(seed.get(key, [])) for key in COLLECTIONS)}


def dex_classes(apk):
    classes = set()
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.endswith('.dex'):
                continue
            data = archive.read(name)
            if not data.startswith(b'dex\n'):
                raise ValueError('Unsupported DEX header')
            _, string_offset, _, type_offset = struct.unpack_from('<IIII', data, 56)
            count, class_offset = struct.unpack_from('<II', data, 96)
            for index in range(count):
                type_index = struct.unpack_from('<I', data, class_offset + index*32)[0]
                string_index = struct.unpack_from('<I', data, type_offset + type_index*4)[0]
                offset = struct.unpack_from('<I', data, string_offset + string_index*4)[0]
                while data[offset] & 0x80:
                    offset += 1
                offset += 1
                descriptor = data[offset:data.index(b'\0', offset)].decode('utf-8')
                if descriptor.startswith('Lat/hausgarten/service/'):
                    classes.add(descriptor[1:-1].replace('/', '.'))
    return classes


def source_equivalence(original, provided):
    if original == provided:
        return 'identical'
    if original == provided.replace(*REVIEWED_LABEL):
        return 'reviewed_export_label_only'
    return 'unreviewed_difference'


def compare_source_copy(source, provided):
    paths = [p.relative_to(source) for p in (source/'app/src/main').rglob('*')
             if p.is_file()]
    different, missing, labels = [], [], []
    for relative in paths:
        other = provided/relative
        if not other.is_file():
            missing.append(relative.as_posix())
        else:
            equivalence = source_equivalence((source/relative).read_bytes(), other.read_bytes())
            if equivalence == 'reviewed_export_label_only':
                labels.append(relative.as_posix())
            elif equivalence == 'unreviewed_difference':
                different.append(relative.as_posix())
    return {'checked_files': len(paths), 'unreviewed_differences': different, 'missing': missing,
            'reviewed_export_label_only': labels,
            'matches': not different and not missing}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-apk', type=Path, required=True)
    parser.add_argument('--candidate-apk', type=Path, required=True)
    parser.add_argument('--source-copy', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    baseline, candidate = apk_inventory(args.baseline_apk), apk_inventory(args.candidate_apk)
    result = {
        'method': 'read_only_zip_bytes_and_source_comparison',
        'full_v141_source_available': False,
        'limitation': 'No complete 1.41.0 Java source snapshot found; baseline is the local signed APK.',
        'baseline_apk': args.baseline_apk.name,
        'candidate_apk': args.candidate_apk.name,
        'baseline_sha256': hashlib.sha256(args.baseline_apk.read_bytes()).hexdigest(),
        'candidate_sha256': hashlib.sha256(args.candidate_apk.read_bytes()).hexdigest(),
        'assets_delta': compare_inventory(
            {k: v for k, v in baseline.items() if k.startswith('assets/')},
            {k: v for k, v in candidate.items() if k.startswith('assets/')}),
        'program_delta': compare_inventory(
            {k: v for k, v in baseline.items() if k.endswith('.dex')},
            {k: v for k, v in candidate.items() if k.endswith('.dex')}),
        'native_classes_added': sorted(dex_classes(args.candidate_apk) - dex_classes(args.baseline_apk)),
        'native_classes_removed': sorted(dex_classes(args.baseline_apk) - dex_classes(args.candidate_apk)),
        'candidate_source_assets': compare_assets_to_source(
            args.candidate_apk, ROOT/'app/src/main/assets'),
        'candidate_seed': empty_seed(args.candidate_apk),
    }
    if args.source_copy:
        result['provided_v142_main_source'] = compare_source_copy(ROOT, args.source_copy)
    result['artifact_source_checks_passed'] = (
        result['candidate_source_assets']['matches']
        and result['candidate_seed']['all_business_collections_empty']
        and result.get('provided_v142_main_source', {}).get('matches', True))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result['artifact_source_checks_passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
