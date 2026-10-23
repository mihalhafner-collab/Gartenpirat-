"""Build a separate editorial library; never write application assets.

Input is public factual evidence collected through connected research tools.
No network requests, live business data, or application migration.
"""
from __future__ import annotations

import ast
from collections import Counter
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
import unicodedata
from urllib.parse import unquote

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
DATE = '2026-10-23'


def read(name):
    return json.loads((HERE / name).read_text(encoding='utf-8'))


def write(name, value):
    (HERE / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                    separators=(',', ':')).encode()).hexdigest()


def norm(value):
    value = unicodedata.normalize('NFKD', value).casefold()
    return ''.join(c for c in value if c.isalnum() and not unicodedata.combining(c))


def license_key(value):
    if value.strip().upper() in {'CC0', 'CC0 1.0', 'CC0-1.0'}:
        return 'cc010'
    return norm(value)


def taxon_good(taxon):
    return (taxon.get('matchType') == 'EXACT' and taxon.get('confidence', 0) >= 95
            and taxon.get('kingdom') == 'Plantae'
            and taxon.get('rank') in {'SPECIES', 'SUBSPECIES', 'VARIETY', 'GENUS'})


def accepted_key(taxon):
    return taxon.get('acceptedUsageKey') or taxon.get('usageKey')


def main():
    legacy_file = ROOT / 'android/app/src/main/assets/plant-catalog.json'
    legacy = json.loads(legacy_file.read_text())['plants']
    profiles = {e['url']: e for e in read('evidence/profiles.json')}
    taxons = {t['query']: t for t in read('evidence/taxonomy.json')}
    commons = {e['url']: e for e in read('evidence/commons.json')['sources']}
    replacements = {e['url']: e for e in read('evidence/replacements.json')['sources']}
    supplemental = read('evidence/supplemental-photos.json')['sources']
    excluded_sources = {unquote(e['sourcePage']) for e in read('evidence/photo-exclusions.json')['records']}
    legal_reviews = {e['plantId']: e for e in read('evidence/legal-review.json')['records']}
    visual = {e['path']: e for e in read('evidence/visual-screening.json')['records']}
    official = {e['url']: e for e in read('evidence/official-context.json')['sources']}
    resolutions = {e['plantId']: e for e in read('evidence/taxonomic-resolution.json')['records']}
    selected_ids = set(read('review-scope.json')['selectedIds'])
    # Reuse the established factual vocabulary without executing its network builder.
    tree = ast.parse((ROOT / 'tools/build_plant_catalog.py').read_text())
    terms = next(ast.literal_eval(n.value) for n in tree.body if isinstance(n, ast.Assign)
                 and any(isinstance(t, ast.Name) and t.id == 'TERMS' for t in n.targets))
    terms.update({'Low': 'Gering', 'Medium': 'Mittel', 'High': 'Hoch'})
    terms.update({'Full sun': 'Sonne', 'Partial shade': 'Halbschatten',
                  'Full shade': 'Schatten', 'Loam': 'Lehmig', 'Chalk': 'Kalkhaltig'})
    terms.update({'Moist but well–drained': 'Frisch bis feucht, gut durchlässig',
                  'Well–drained': 'Gut durchlässig', 'Poorly–drained': 'Wenig durchlässig / nass',
                  'Acid': 'Sauer', 'Neutral': 'Neutral', 'Alkaline': 'Alkalisch'})
    fields = {
        'family': 'Family', 'light': 'Light', 'soilTexture': 'Soil Texture',
        'soilReaction': 'Soil pH', 'soilDrainage': 'Soil Drainage',
        'bloomSeason': 'Flower Bloom Time', 'flowerColors': 'Flower Color',
        'lifeCycle': 'Life Cycle', 'foliage': 'Woody Plant Leaf Characteristics',
        'habit': 'Habit/Form', 'growthRate': 'Growth Rate',
        'propagation': 'Recommended Propagation Strategy',
        'hardinessUSDA': 'USDA Plant Hardiness Zone',
        'dimensionsSource': 'Dimensions', 'originSource': 'Country Or Region Of Origin',
    }
    plants, held, duplicates = [], [], []
    occupied = {}

    def make(name, latin, category, old=None):
        slug = old['id'] if old else latin.lower().replace(' ', '-')
        t = taxons.get('Rosa' if slug == 'rosa' else latin, {})
        tax = t.get('result', {})
        resolution = resolutions.get(slug)
        source_urls = [s['url'] for s in old['sources']] if old else [
            'https://plants.ces.ncsu.edu/plants/' + slug + '/']
        if slug == 'rosa':
            source_urls.insert(0, 'https://plants.ces.ncsu.edu/plants/rosa/')
        if old and ('invasive' in old or 'neophyte' in old):
            nc_url = 'https://plants.ces.ncsu.edu/plants/' + slug + '/'
            if nc_url in profiles and nc_url not in source_urls:
                source_urls.insert(0, nc_url)
        sources = [profiles[u] for u in source_urls if u in profiles]
        source = next((s for s in sources if s.get('facts', {}).get('Light')), sources[0] if sources else None)
        facts = source.get('facts', {}) if source else {}
        source_url = source['url'] if source else None
        blockers = []
        if not taxon_good(tax) and not resolution:
            blockers.append('taxonomic_match_requires_review')
        reference_only = bool(old and ('invasive' in old or 'neophyte' in old))
        scope_warnings = []
        if not reference_only and any(not any(s.get('facts', {}).get(field) for s in sources)
                                      for field in ('Light', 'Soil Drainage')):
            blockers.append('cultivation_evidence_incomplete')
        # Taxonomic ranks and horticultural aggregates must stay explicit.
        scope = 'species'
        if slug in {'rosa', 'dahlia'}:
            scope = 'horticultural_group'
            scope_warnings.append('Gattungs-/Gartengruppenübersicht: konkrete Sorten können andere Standortansprüche haben.')
        if slug == 'rubus-fruticosus':
            scope = 'species_aggregate'
            scope_warnings.append('Artaggregat; keine einzelne Brombeersorte und keine pauschale Verzehrfreigabe.')
        key = accepted_key(tax) if taxon_good(tax) else None
        if not old and key and key in occupied:
            duplicates.append({'query': latin, 'sameAs': occupied[key], 'acceptedUsageKey': key})
            return None
        if key:
            occupied.setdefault(key, slug)
        identities = (source or {}).get('identityHeadings', [])
        official_identity = legal_reviews.get(slug, {}).get('EU', {}).get('listedName')
        if not identities and reference_only and official_identity:
            identities = [official_identity]
        expected_names = [latin, tax.get('canonicalName', ''), tax.get('species', '')]
        if resolution:
            expected_names.extend([resolution.get('acceptedName', ''), resolution.get('sourceName', '')])
        identity_same = any(norm(n) and norm(n) in norm(h)
                            for n in expected_names for h in identities)
        if not identity_same:
            identity_same = any(taxon_good(taxons.get(h, {}).get('result', {}))
                                and accepted_key(taxons[h]['result']) == key for h in identities)
        if source and facts and not identity_same:
            blockers.append('profile_identity_requires_review')
        traits, provenance = {}, {}
        for target, field in fields.items():
            if reference_only and target == 'propagation':
                traits[target] = []
                continue
            field_source = next((s for s in sources if s.get('facts', {}).get(field)), source)
            vals = (field_source or {}).get('facts', {}).get(field, [])
            traits[target] = [terms.get(v, v) for v in vals]
            if vals:
                provenance[target] = {'source': field_source['url'], 'field': field, 'values': vals}
        # RHS uses a different vocabulary and is not silently converted to NCSU facts.
        if source and 'rhs.org.uk' in source_url and not facts.get('Light'):
            blockers.append('rhs_layout_needs_manual_field_mapping')
        photos = []
        rejected_credits = {(norm(i['caption']), norm(i['author'])) for i in old.get('images', [])
                            if visual.get('android/app/src/main/assets/' + i['path'], {}).get('status', '').startswith(('REJECT_', 'HOLD_'))} if old else set()
        for f in (source or {}).get('figures', []):
            if (not f.get('eligible') or not f.get('url') or not f.get('licenseUrl')
                    or re.search(r'illustration|drawing|herbarium|map|painting', f.get('caption', ''), re.I)
                    or (norm(f.get('caption', '')), norm(f.get('author', ''))) in rejected_credits):
                continue
            if any(p.get('originalLocator') == f['url'] for p in photos):
                continue
            photo = {
                'id': slug + '-ref-' + str(len(photos) + 1),
                'caption': f['caption'], 'author': f['author'],
                'license': f['licenseId'], 'licenseUrl': f['licenseUrl'],
                'sourcePage': source_url, 'originalLocator': f['url'],
                'locatorPolicy': 'Resolve a fresh download URL at sourcePage; do not persist signed query parameters.',
                'checkedAt': DATE, 'creditVerification': f['association'],
                'visualReview': 'NOT_PERFORMED', 'binaryVerification': 'NOT_DOWNLOADED',
                'changes': 'Keine; externe Fotoreferenz.',
                'depictedCultivar': 'See caption; cultivar identity is not independently verified.',
                'attribution': f"{f['caption']} — {f['author']} — {f['licenseId']} — {source_url}",
            }
            photos.append(photo)
            if len(photos) == 4:
                break
        # Existing bytes remain attached only when their own credits match current evidence.
        legacy_photo_audit = []
        for image in old.get('images', []) if old else []:
            matched = next((f for s in sources for f in s.get('figures', [])
                            if norm(f.get('author', '')) == norm(image['author'])
                            and norm(f.get('caption', '')) == norm(image['caption'])
                            and license_key(f.get('licenseRaw', '')) == license_key(image['license'])), None)
            path = ROOT / 'android/app/src/main/assets' / image['path']
            cp = commons.get(image['source'], {})
            commons_confirmed = (norm(cp.get('fields', {}).get('Author', '')) == norm(image['author'])
                                 and bool(cp.get('fields', {}).get('Author'))
                                 and any(license_key(lic) == license_key(image['license']) for lic in cp.get('licenses', [])))
            assessment = visual.get(str(path.relative_to(ROOT)), {})
            local = {'path': str(path.relative_to(ROOT)), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                     'status': 'CREDIT_RECONFIRMED' if (matched and matched.get('eligible')) or commons_confirmed else 'REQUIRES_SOURCE_REVIEW',
                     'caption': image['caption'], 'author': image['author'],
                     'license': image['license'], 'source': image['source'],
                     'licenseUrl': image['licenseUrl'], 'changes': image.get('changes', ''),
                     'visualReview': assessment.get('status', 'NOT_PERFORMED'),
                     'visualNote': assessment.get('note', ''),
                     'usableAsLivingPlantPhoto': assessment.get('status') == 'CONTACT_SHEET_SCREENED_NO_GROSS_MISMATCH_DETECTED'}
            local['editorialDecision'] = ('RETAIN_ELIGIBLE_DERIVATIVE' if local['status'] == 'CREDIT_RECONFIRMED'
                                          and local['usableAsLivingPlantPhoto'] else 'CLOSED_EXCLUDED_FROM_LIBRARY_USE')
            local['decisionReason'] = ('Current credit and overview motif screening match.' if local['editorialDecision'] == 'RETAIN_ELIGIBLE_DERIVATIVE'
                                       else 'Motif rejected or credit not exactly reconfirmed; original app asset preserved, no reuse permission inferred.')
            if local['editorialDecision'] == 'RETAIN_ELIGIBLE_DERIVATIVE':
                reuse_id = matched['licenseId'] if matched and matched.get('eligible') else next(
                    v for v in cp['licenses'] if license_key(v) == license_key(image['license']))
                local['reuseLicenseId'] = reuse_id
                if reuse_id == 'CC0-1.0':
                    local['reuseLicenseUrl'] = 'https://creativecommons.org/publicdomain/zero/1.0/'
                else:
                    parts = reuse_id.lower().split('-')
                    local['reuseLicenseUrl'] = 'https://creativecommons.org/licenses/' + ('by-sa/' if parts[2] == 'sa' else 'by/')
                    local['reuseLicenseUrl'] += '/'.join(parts[3:] if parts[2] == 'sa' else parts[2:]) + '/'
            legacy_photo_audit.append(local)
            # Reuse existing Commons photos only after separate credit and motif checks.
            if (commons_confirmed and local['usableAsLivingPlantPhoto'] and cp.get('originalLocator')
                    and not any(p['originalLocator'] == cp['originalLocator'] for p in photos)):
                lic = next((v for v in cp['licenses'] if license_key(v) == license_key(image['license'])), None)
                if lic and lic.startswith(('CC-BY-', 'CC0-')):
                    photos.append({'id': slug + '-existing-' + str(len(photos) + 1),
                                   'caption': image['caption'], 'author': image['author'],
                                   'license': lic, 'licenseUrl': image['licenseUrl'],
                                   'sourcePage': image['source'], 'originalLocator': cp['originalLocator'],
                                   'locatorPolicy': 'Resolve current original at sourcePage before reusing original bytes.',
                                   'checkedAt': DATE, 'creditVerification': 'commons_file_author_and_license_matched',
                                   'visualReview': 'CONTACT_SHEET_SCREENED',
                                   'binaryVerification': 'EXISTING_DERIVATIVE_HASHED',
                                   'existingPath': local['path'], 'existingSha256': local['sha256'],
                                   'changes': image.get('changes', ''),
                                   'depictedCultivar': 'See original caption; no independent species determination.',
                                   'attribution': f"{image['caption']} — {image['author']} — {lic} — {image['source']}"})
        if slug in {'rosa', 'nymphaea-alba'}:
            for cp in replacements.values():
                if (slug == 'rosa') != ('Rosa_sp.' in cp['url']):
                    continue
                if not cp['fields'].get('Author') or not cp.get('originalLocator'):
                    continue
                lic = next((v for v in cp['licenses'] if v.startswith('CC-BY-')), None)
                if not lic:
                    continue
                license_url = next((v for v in cp['licenseUrls']
                                    if ('/' + lic.rsplit('-', 1)[1] + '/') in v), None)
                if not license_url:
                    continue
                caption = cp['fields'].get('Description', cp['title'])
                photos.append({'id': slug + '-replacement-' + str(len(photos) + 1),
                               'caption': caption, 'author': cp['fields']['Author'],
                               'license': lic, 'licenseUrl': license_url,
                               'sourcePage': cp['url'], 'originalLocator': cp['originalLocator'],
                               'locatorPolicy': 'Resolve fresh original at sourcePage.',
                               'checkedAt': DATE, 'creditVerification': 'commons_individual_file_metadata',
                               'visualReview': 'NOT_PERFORMED', 'binaryVerification': 'NOT_DOWNLOADED',
                               'changes': 'Keine; externe Ersatzreferenz.',
                               'depictedCultivar': 'Explicit cultivar in caption; group illustration only for Rosa.',
                               'attribution': f"{caption} — {cp['fields']['Author']} — {lic} — {cp['url']}"})
        for cp in supplemental:
            if (cp['plantId'] != slug or len(photos) >= 4 or unquote(cp['url']) in excluded_sources
                    or any(p['originalLocator'] == cp['originalLocator'] for p in photos)):
                continue
            lic = cp['licenses'][0]
            caption = cp['fields'].get('Description') or cp['title']
            photos.append({'id': slug + '-supplement-' + str(len(photos) + 1),
                           'caption': caption, 'author': cp['fields']['Author'],
                           'license': lic, 'licenseUrl': cp['licenseUrls'][0],
                           'sourcePage': cp['url'], 'originalLocator': cp['originalLocator'],
                           'checkedAt': DATE, 'creditVerification': 'commons_individual_file_api_extmetadata',
                           'visualReview': 'NOT_PERFORMED', 'binaryVerification': 'NOT_DOWNLOADED',
                           'changes': 'Keine; externe Ersatzreferenz.',
                           'attribution': f"{caption} — {cp['fields']['Author']} — {lic} — {cp['url']}"})
        if len(photos) < 2:
            blockers.append('fewer_than_two_reconfirmed_photo_references')
        # A metadata source check is never an assertion of visual inspection.
        blockers.append('photo_visual_review_and_original_download_pending')
        poison = facts.get('Poison Severity', [])
        rhs_warning = list(dict.fromkeys(v for s in sources for v in s.get('facts', {}).get('Potentially harmful', [])))
        safety = {
            'poisonSeveritySource': poison,
            'poisonSeverity': [terms.get(v, v) for v in poison],
            'contactDermatitisSource': facts.get('Causes Contact Dermatitis', []),
            'rhsWarningSource': rhs_warning,
            'rhsWarningEvidence': [{'source': s['url'], 'field': 'Potentially harmful',
                                    'values': s['facts']['Potentially harmful']}
                                   for s in sources if s.get('facts', {}).get('Potentially harmful')],
            'status': 'SOURCE_WARNING_PRESENT' if poison or rhs_warning else 'UNKNOWN_NOT_PROVEN_HARMLESS',
            'source': source_url,
            'notice': 'Keine Verzehrfreigabe. Fehlende Giftangaben bedeuten nicht ungiftig. Tierverträglichkeit separat prüfen.',
        }
        if old:
            safety['legacyReportedWarningsPendingReconfirmation'] = {
                'poison': old.get('poison', []), 'contact': old.get('contact', []),
                'status': 'Historical warnings retained; do not interpret missing new evidence as withdrawal of a warning.'}
        entry = {
            'id': slug, 'name': name, 'scientificName': latin, 'category': category,
            'taxonScope': scope, 'origin': 'legacy_reaudited' if old else 'extension',
            'taxonomy': {'source': t.get('source'), 'checkedAt': t.get('checkedAt'),
                         'query': t.get('query'), 'match': tax,
                         'acceptedUsageKey': key,
                         'status': 'EXACT_MATCH' if taxon_good(tax) else 'VERIFIED_BY_KEW' if resolution else 'REQUIRES_REVIEW',
                         'authorityResolution': resolution},
            'traits': traits, 'fieldEvidence': provenance,
            'cultivationScope': 'IDENTIFICATION_AND_PREVENTION_REFERENCE_NO_CULTIVATION_ADVICE' if reference_only else 'SOURCE_REGION_FACTS_ONLY',
            'scopeWarnings': scope_warnings,
            'sourceIdentity': identities,
            'officialIdentitySource': legal_reviews[slug]['EU']['source'] if reference_only and official_identity else None,
            'safety': safety, 'images': photos, 'legacyPhotos': legacy_photo_audit,
            'sources': [{'url': s['url'], 'checkedAt': s['checkedAt'],
                         'evidenceSha256': digest(s)} for s in sources],
            'review': {'sourceFacts': 'CHECKED' if facts else 'OFFICIAL_IDENTIFICATION_REFERENCE_NO_CULTIVATION_DATA' if reference_only else 'UNAVAILABLE',
                       'photoCredits': 'CHECKED' if len(photos) >= 2 else 'INCOMPLETE',
                       'expertBotanicalReview': 'NOT_PERFORMED',
                       'desktopSourceReview': 'COMPLETED' if identity_same and (taxon_good(tax) or resolution) else 'REQUIRES_REVIEW',
                       'legacyImageDecisions': 'CLOSED',
                       'implementation': 'HOLD', 'blockers': blockers},
        }
        if old:
            entry['legacyId'] = old['id']
            entry['legacyEditorialNote'] = 'Frühere freie Pflege- und Pflanztexte sind nicht ungeprüft als neue Empfehlung übernommen.'
            if 'invasive' in old or 'neophyte' in old:
                entry['historicalLegalData'] = {k: deepcopy(old[k]) for k in ('invasive', 'neophyte') if k in old}
                entry['officialContextEvidence'] = [official[s['url']] for s in old['sources'] if s['url'] in official]
        if slug in legal_reviews:
            entry['legalReview'] = legal_reviews[slug]
            entry['legalStatus'] = 'OFFICIAL_SOURCES_CHECKED_WITH_REGIONAL_SCOPE_LIMIT'
        if reference_only and slug not in legal_reviews:
            entry['review']['blockers'].append('current_neophyte_legal_and_management_review_pending')
        return entry

    for old in legacy:
        plants.append(make(old['name'], old['scientificName'], old['category'], old))
    for line in (HERE / 'seed.tsv').read_text().splitlines():
        if not line or line.startswith('#'):
            continue
        name, latin, category = line.split('|')
        if any(p['scientificName'] == latin for p in plants):
            duplicates.append({'query': latin, 'sameAs': next(p['id'] for p in plants if p['scientificName'] == latin)})
            continue
        p = make(name, latin, category)
        if p is None:
            continue
        # Candidates with unresolved evidence are retained outside the usable selection.
        (plants if p['id'] in selected_ids else held).append(p)
    plants.sort(key=lambda p: p['name'].casefold())
    library = {
        'format': 'gartenpirat.editorial-plant-library.v1', 'edition': DATE,
        'purpose': 'Eigenständige redaktionelle Bibliothek zur späteren Implementierung',
        'region': 'DACH-Zielgruppe; Gartenmerkmale überwiegend aus US-/UK-Quellen',
        'applicationAssetsChanged': False,
        'baseline': {'repositoryCommit': 'e499e745832449ce0202d04d9903495248e8b034',
                     'catalogSha256': hashlib.sha256(legacy_file.read_bytes()).hexdigest(), 'profiles': len(legacy)},
        'limitations': [
            'Foto-Credits und Taxonzuordnung anhand der Quellen geprüft; keine unabhängige botanische Expertenabnahme.',
            'Neue Fotos sind belegte externe Referenzen; Originaldownload und persönliche Sichtprüfung stehen aus.',
            'Blühzeiten und Winterhärte stammen aus dem Quellenklima; keine lokale Frost- oder Blühgarantie.',
            'Bodendurchlässigkeit ist kein gemessener Wasserbedarf; USDA-Zonen sind keine Gieß- oder Frostempfehlung.',
            'Gattungs- und Aggregatprofile dürfen nicht als einzelne Sorten behandelt werden.',
            'Amtliche EU-/CH-Beschränkungen sind datiert belegt; regionale Einzelfallregeln und Bekämpfungsverfahren sind nicht vollständig geprüft.',
        ],
        'plants': plants,
    }
    write('plant-library.json', library)
    write('review-queue.json', {'heldCandidates': held, 'duplicateCandidates': duplicates})
    index = ['# Pflanzenverzeichnis', '',
             f'Stand {DATE}: {len(plants)} Profile, davon {sum(p["origin"] == "extension" for p in plants)} neu.', '',
             'Alle Einträge sind redaktionelle Arbeitsstände. Sichtprüfung neuer Originalbilder und fachliche Freigaben stehen aus. '
             'Offene Punkte: `entry-review.csv`; Bildrechte: `photo-credits.csv`.', '']
    for category in sorted({p['category'] for p in plants}):
        index.extend(['## ' + category, '', '| Pflanze | Wissenschaftlicher Name | Fotoverweise | Quelle |',
                      '| --- | --- | ---: | --- |'])
        for p in plants:
            if p['category'] != category:
                continue
            url = p['sources'][0]['url'] if p['sources'] else p['taxonomy'].get('source')
            index.append(f'| {p["name"]} | {p["scientificName"]} | {len(p["images"])} | [Beleg]({url}) |')
        index.append('')
    (HERE / 'PFLANZENVERZEICHNIS.md').write_text('\n'.join(index) + '\n', encoding='utf-8')
    print(json.dumps({'profiles': len(plants), 'new': sum(p['origin'] == 'extension' for p in plants),
                      'photoReferences': sum(len(p['images']) for p in plants),
                      'held': len(held), 'duplicates': len(duplicates),
                      'categories': Counter(p['category'] for p in plants)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
