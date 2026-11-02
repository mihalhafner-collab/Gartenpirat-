#!/usr/bin/env python3
"""Validate the only distributed plant catalogue in Android source assets.

The earlier standalone Web UI and duplicated media were retired. No export,
mirrors, download, or writes are needed to check the live Android catalogue.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'android/app/src/main/assets'


def synchronize():
    catalog = json.loads((ASSETS / 'plant-catalog.json').read_text())
    plants = catalog['plants']
    if len({p['id'] for p in plants}) != len(plants):
        raise ValueError('Duplicate plant IDs')
    checked = set()
    for plant in plants:
        if not plant['sources'] or not plant['images']:
            raise ValueError('Missing source/image: ' + plant['id'])
        for image in plant['images']:
            path = Path(image['path'])
            if (not path.parts or path.parts[0] != 'plant-images'
                    or '..' in path.parts or path.is_absolute()):
                raise ValueError('Unsafe image path')
            if not all(image.get(key) for key in ('author', 'license', 'licenseUrl', 'source')):
                raise ValueError('Missing attribution: ' + str(path))
            file = ASSETS / path
            if not file.is_file() or file.stat().st_size == 0:
                raise ValueError('Missing Android plant image: ' + str(path))
            checked.add(path.as_posix())
    return {'canonical': 'android/app/src/main/assets/plant-catalog.json',
            'profiles': len(plants), 'images_verified': len(checked)}


if __name__ == '__main__':
    print(json.dumps(synchronize()))
