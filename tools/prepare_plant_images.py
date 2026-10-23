"""Fetch public photo candidates into an external review folder; never approve/import.

Fresh individual source credits are required before a download or legacy reuse.
The generated proposal deliberately has no visual approval. Use the separate
manifest/importer only after actually opening and reviewing the selected files.
"""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import html
from html.parser import HTMLParser
import io
import json
from pathlib import Path
import re
import sys
import threading
from urllib.parse import urlsplit, unquote, urlencode
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from import_plant_library import decode, license_url_matches
from PIL import Image, ImageOps

def digest(raw):
    return hashlib.sha256(raw).hexdigest()

def clean(value):
    return re.sub(r'\s+', ' ', html.unescape(re.sub(r'<[^>]*>', ' ', value))).strip()

def same_url(a, b):
    return unquote(a.split('?')[0]).replace('http:', 'https:').rstrip('/') == unquote(b.split('?')[0]).replace('http:', 'https:').rstrip('/')

class Credits(HTMLParser):
    def __init__(self):
        super().__init__(); self.images = []
    def handle_starttag(self, tag, attrs):
        data = dict(attrs)
        if tag == 'img' and data.get('data-attrib') and data.get('data-license'):
            self.images.append(data)

def prepare(output, workers=4):
    output = output.resolve()
    if output.is_relative_to(ROOT) or output.is_symlink():
        raise ValueError('Review originals must remain outside the checkout')
    output.mkdir(parents=True, exist_ok=True)
    for name in ('originals', 'derivatives', 'source-cache'):
        (output / name).mkdir(exist_ok=True)
    library = json.loads((ROOT / 'libraries/plants/plant-library.json').read_text())
    reviews = json.loads((ROOT / 'libraries/plants/integration-legacy-review.json').read_text())
    excluded = {x['sha256'] for x in reviews['records'] if x['decision'] in {'EXCLUDE_FROM_INTEGRATION', 'HOLD_TAXON_REVIEW'}}
    locks = {}; guard = threading.Lock()
    prior_path = output / 'proposal.json'
    prior = {p['id']: p for p in json.loads(prior_path.read_text())} if prior_path.exists() else {}
    commons = json.loads((output / 'commons-batch-index.json').read_text()) if (output / 'commons-batch-index.json').exists() else {}
    overrides = json.loads((output / 'selection-overrides.json').read_text()) if (output / 'selection-overrides.json').exists() else {}
    supplemental = {}
    for path in (ROOT / 'libraries/plants/integration-sources').glob('*.json'):
        evidence = json.loads(path.read_text())
        supplemental[evidence['plantId']] = evidence['images']

    def fetch(url):
        if urlsplit(url).hostname not in {'plants.ces.ncsu.edu', 'www.rhs.org.uk', 'commons.wikimedia.org', 'upload.wikimedia.org', 'eit-planttoolbox-prod.s3.amazonaws.com'}:
            raise ValueError('Unexpected public source host')
        key = digest(url.encode())
        with guard:
            lock = locks.setdefault(key, threading.Lock())
        with lock:
            path = output / 'source-cache' / key
            if path.exists():
                return path.read_bytes()
            with urlopen(Request(url, headers={'User-Agent': 'GartenpiratOfflineReference/1.54.9 (https://github.com/mihalhafner-collab/Gartenpirat-; public botanical attribution audit)'}), timeout=45) as response:
                raw = response.read(50 * 1024 * 1024 + 1)
            if len(raw) > 50 * 1024 * 1024:
                raise ValueError('Source exceeds public image size limit')
            path.write_bytes(raw)
            return raw

    def credit(photo):
        url = photo['sourcePage']
        if urlsplit(url).hostname == 'plants.ces.ncsu.edu':
            raw = fetch(url); parser = Credits(); parser.feed(raw.decode())
            candidates = [a for a in parser.images if same_url(a.get('data-downloadurl') or a.get('src', ''), photo['originalLocator'])]
            for a in candidates:
                link = re.search(r'href=[\'\"]([^\'\"]+)', a['data-license'])
                if (clean(a['data-attrib']) == clean(photo['author']) and
                        link and license_url_matches(photo['license'], html.unescape(link[1]).replace('http:', 'https:'))):
                    return a.get('data-downloadurl') or a['src'], digest(raw), 'NCSU_ORIGINAL_IMAGE_ATTRIBUTES'
            raise ValueError('Fresh source credit does not match selected reference')
        if urlsplit(url).hostname == 'commons.wikimedia.org':
            title = unquote(url.split('/wiki/', 1)[1]).replace('_', ' ')
            endpoint = 'https://commons.wikimedia.org/w/api.php?' + urlencode({'action':'query','format':'json','titles':title,'prop':'imageinfo','iiprop':'url|extmetadata'})
            batch = commons.get(title)
            raw = (output / batch['path']).read_bytes() if batch else fetch(endpoint)
            data = json.loads(raw)
            for page in data.get('query', {}).get('pages', {}).values():
                for item in page.get('imageinfo', []):
                    meta = item.get('extmetadata', {})
                    author = clean(meta.get('Artist', {}).get('value', ''))
                    license_url = meta.get('LicenseUrl', {}).get('value', '').replace('http:', 'https:')
                    if (same_url(item['url'], photo['originalLocator']) and clean(photo['author']) in author and
                            license_url_matches(photo['license'], license_url)):
                        return item['url'], digest(raw), 'COMMONS_INDIVIDUAL_IMAGEINFO_EXTMETADATA'
            raise ValueError('Fresh Commons individual credit does not match')
        raise ValueError('Unsupported source credit page')

    def plant_task(plant):
        expected = overrides.get(plant['id'])
        previous = prior.get(plant['id'], {}).get('images', [])
        if len(previous) == 2 and (not expected or [i['referenceId'] for i in previous] == expected):
            return prior[plant['id']]
        chosen = []; errors = []; used = set()
        legacy = {p['path']:p for p in plant.get('legacyPhotos', []) if p['editorialDecision'] == 'RETAIN_ELIGIBLE_DERIVATIVE' and p['sha256'] not in excluded}
        def historical_ref(photo):
            explicit = legacy.get(photo.get('existingPath'))
            if explicit:
                return explicit
            matches = [p for p in legacy.values() if clean(p['caption']) == clean(photo['caption'])
                       and clean(p['author']) == clean(photo['author'])]
            # Identical captions are common. They cannot bind two distinct
            # originals to one historical slot; download those originals instead.
            return matches[0] if len(matches) == 1 else None
        # Eligible published photos first; then living form/details over harvested material.
        def rank(photo):
            old = historical_ref(photo)
            caption = photo['caption'].lower()
            bad = bool(re.search(r'black racer|\broot[s]?\b|uproot|harvest|dried|drawing|illustration|seed potato|assorted|\bbulb\b|gathering|acorn squash', caption))
            form = bool(re.search(r'\bform\b|\bhabit\b|whole plant|potted|\bplants?\b', caption))
            return (0 if old else 1, 2 if bad else 0 if form else 1)
        candidates = plant['images'] + supplemental.get(plant['id'], [])
        if expected:
            candidates = [next(p for p in candidates if p['id'] == name) for name in expected]
        else:
            candidates = sorted(candidates, key=rank)
        for photo in candidates:
            if len(chosen) == 2: break
            if photo['originalLocator'] in used: continue
            try:
                url, source_sha, method = credit(photo)
                old = historical_ref(photo)
                number = len(chosen) + 1
                if old:
                    raw = (ROOT / old['path']).read_bytes()
                    if digest(raw) != old['sha256']: raise ValueError('Legacy bytes changed')
                    original_sha = digest(raw)
                    choice = {'sourceKind':'legacy_derivative', 'legacyPath':old['path']}
                    changes = old['changes']
                else:
                    original = fetch(url); decode(original)
                    original_sha = digest(original)
                    original_path = 'originals/' + original_sha
                    (output / original_path).write_bytes(original)
                    image = ImageOps.exif_transpose(Image.open(io.BytesIO(original))).convert('RGB')
                    image.thumbnail((1200, 1000))
                    buffer = io.BytesIO(); image.save(buffer, 'JPEG', quality=85, optimize=True); raw = buffer.getvalue()
                    choice = {'sourceKind':'downloaded_original','referenceId':photo['id'],'originalPath':original_path}
                    changes = 'Erstes Originalbild; Orientierung gemäß Original; proportional auf höchstens 1200×1000 Pixel verkleinert, JPEG-Neukodierung, EXIF/GPS/XMP entfernt; kein Ausschnitt.'
                dimensions = decode(raw, derivative=True)
                if digest(raw) in excluded: raise ValueError('Excluded legacy image')
                derivative_path = 'derivatives/' + plant['id'] + '-' + str(number) + '.jpg'
                (output / derivative_path).write_bytes(raw)
                chosen.append({**choice,'referenceId':photo['id'],'originalSha256':original_sha,'derivativePath':derivative_path,
                    'derivativeSha256':digest(raw),'width':dimensions['width'],'height':dimensions['height'],
                    'changes':changes,'creditReconfirmedAt':'2026-10-23', 'creditEvidenceSha256':source_sha,
                    'creditEvidenceMethod':method,'caption':photo['caption'],'sourcePage':photo['sourcePage']})
                used.add(photo['originalLocator'])
            except Exception as error:
                errors.append({'referenceId':photo['id'],'reason':type(error).__name__ + ': ' + str(error).split('?')[0][:150]})
        return {'id':plant['id'],'images':chosen,'errors':errors,'visualReview':'NOT_PERFORMED'}

    results = []
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(plant_task, p) for p in library['plants']]
        for future in as_completed(futures):
            results.append(future.result())
            (output / 'proposal.json').write_text(json.dumps(sorted(results,key=lambda x:x['id']),ensure_ascii=False,indent=2)+'\n')
            if len(results) % 10 == 0 or len(results) == len(futures):
                print(json.dumps({'processed':len(results),'completePairs':sum(len(p['images'])==2 for p in results),'images':sum(len(p['images']) for p in results)}),flush=True)
    return results

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--workers',type=int,choices=range(1,7),default=4)
    args = parser.parse_args()
    prepare(args.output,args.workers)
