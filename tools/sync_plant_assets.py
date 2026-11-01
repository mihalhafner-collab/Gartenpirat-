#!/usr/bin/env python3
"""Check/generate the bundled JS adapter from the native plant catalogue."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'


def synchronize(write=False):
    catalog = json.loads((ASSETS / 'plant-catalog.json').read_bytes())
    plants = catalog['plants']
    if len({plant['id'] for plant in plants}) != len(plants):
        raise ValueError('Duplicate plant IDs')
    images = set()
    for plant in plants:
        if not plant['sources'] or not plant['images']:
            raise ValueError('Missing source/image: ' + plant['id'])
        for image in plant['images']:
            relative = Path(image['path'])
            if relative.is_absolute() or '..' in relative.parts or relative.parts[0] != 'plant-images':
                raise ValueError('Unsafe image path')
            if not all(image.get(key) for key in ('author', 'license', 'licenseUrl', 'source')):
                raise ValueError('Missing attribution: ' + str(relative))
            if not (ASSETS / relative).is_file():
                raise ValueError('Missing image: ' + str(relative))
            images.add(relative.as_posix())
    adapter = ('/* Offline botanical database. Generated from plant-catalog.json. */\n'
               'window.GartenpiratCatalog=' + json.dumps(catalog, ensure_ascii=False, separators=(',', ':')) + ';\n').encode()
    target = ASSETS / 'plant-catalog.js'
    if write:
        target.write_bytes(adapter)
    elif target.read_bytes() != adapter:
        raise ValueError('Stale bundled plant-catalog.js')
    return {'profiles': len(plants), 'images': len(images), 'mode': 'write' if write else 'check'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true')
    print(json.dumps(synchronize(parser.parse_args().write)))
