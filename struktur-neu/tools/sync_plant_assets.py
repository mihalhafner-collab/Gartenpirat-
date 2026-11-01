#!/usr/bin/env python3
"""Generate/check Web mirrors from the existing Android catalogue, never business data.

Edit only android/app/src/main/assets/plant-catalog.json and its referenced media.
The standalone Web catalogue is an output. The Android UI reads JSON in Kotlin.
--write updates outputs without deleting any files; the default is read-only.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'android/app/src/main/assets'
WEB = ROOT / 'static'
SHARED = ('gartenpirat.js', 'gartenpirat.css', 'gartenpirat.png', 'gartenpirat-photo-store.js')


def synchronize(write=False):
    raw = (ASSETS/'plant-catalog.json').read_bytes()
    catalog = json.loads(raw)
    plants = catalog['plants']
    if len({p['id'] for p in plants}) != len(plants):
        raise ValueError('Duplicate plant IDs')
    adapter = ('/* Offline botanical database. Generated from plant-catalog.json. */\n'
               'window.GartenpiratCatalog=' + json.dumps(catalog, ensure_ascii=False, separators=(',', ':')) + ';\n').encode()
    outputs = {WEB/'plant-catalog.js': adapter, WEB/'plant-catalog.json': raw}
    images = set()
    for plant in plants:
        if not plant['sources'] or not plant['images']:
            raise ValueError('Missing source/image: ' + plant['id'])
        for image in plant['images']:
            relative = Path(image['path'])
            if relative.parts[0] != 'plant-images' or '..' in relative.parts or relative.is_absolute():
                raise ValueError('Unsafe image path')
            if not all(image.get(k) for k in ('author', 'license', 'licenseUrl', 'source')):
                raise ValueError('Missing attribution: ' + str(relative))
            images.add(relative.as_posix())
    for name in sorted(images):
        outputs[WEB/name] = (ASSETS/name).read_bytes()
    worker = (WEB/'sw.js').read_text()
    match = re.search(r'const GP_ASSETS = (\[[^\n]*\]);', worker)
    if not match:
        raise ValueError('Missing service-worker asset list')
    old = json.loads(match[1])
    paths = [p for p in old if not p.startswith('/plant-images/')]
    paths += ['/' + name for name in sorted(images)]
    worker = worker[:match.start(1)] + json.dumps(paths, ensure_ascii=False, separators=(',', ':')) + worker[match.end(1):]
    # Content address the botanical cache, including viewer code and media.
    shared = b''.join((WEB/name).read_bytes() for name in SHARED)
    digest = hashlib.sha256(raw + adapter + shared + b''.join(outputs[p] for p in sorted(outputs))).hexdigest()[:16]
    worker = re.sub(r"const CACHE = '[^']+';", "const CACHE = 'haus-garten-plants-" + digest + "';", worker, count=1)
    outputs[WEB/'sw.js'] = worker.encode()
    different = []
    for path, content in outputs.items():
        if not path.is_file() or path.read_bytes() != content:
            if write:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            else:
                different.append(path.relative_to(ROOT).as_posix())
    extra = sorted(p.name for p in (WEB/'plant-images').glob('*') if 'plant-images/' + p.name not in images)
    if different or extra:
        raise ValueError(json.dumps({'outdated_generated_files': different, 'unreferenced_web_images': extra}))
    return {'canonical': 'android/app/src/main/assets/plant-catalog.json', 'profiles': len(plants),
            'images': len(images), 'generated_outputs_checked': len(outputs), 'mode': 'write' if write else 'check'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    print(json.dumps(synchronize(parser.parse_args().write)))
