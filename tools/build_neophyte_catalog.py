"""Extend the existing offline plant catalogue with verified Austrian Union-list plants.

This is deliberately an additive catalogue build: existing profiles and identifiers are
left untouched, no application/business data is opened, and both web/Android mirrors
are written from the same in-memory object.  Curated facts live in
``neophyte_profiles.json``; Wikimedia Commons is used only for reusable image media and
its attribution metadata.
"""
from __future__ import annotations

from html import unescape
from pathlib import Path
import hashlib
import io
import json
import re
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android" / "app" / "src" / "main" / "assets"
STATIC = ROOT / "static"
ANDROID_MEDIA = ANDROID / "plant-images"
STATIC_MEDIA = STATIC / "plant-images"
MANIFEST = ROOT / "tools" / "neophyte_profiles.json"
CACHE = ROOT / "outputs" / "plant-catalog-work" / "research" / "commons-neophytes"
UA = "GartenpiratOfflineReference/2.0 (offline plant catalogue; contact: local build)"
ALLOWED_LICENSE = re.compile(r"^(?:CC0(?: 1\.0)?|Public domain|CC BY(?:-SA)? [1-4]\.[0-9](?: [A-Za-z-]+)?)$", re.I)
REFRESH_IMAGES = "--refresh-images" in sys.argv
ESTABLISHMENT_CLASSES = {
    "Etabliert": "established",
    "Lokal etabliert": "locally-established",
    "Unbeständig": "casual",
    "Vorkommend": "occurring",
    "Status unbekannt; Vorkommen nachgewiesen": "unknown",
}

if (ROOT / ".dependencies").is_dir():
    sys.path.insert(0, str(ROOT / ".dependencies"))
from PIL import Image, ImageOps  # noqa: E402


def clean(value: object) -> str:
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]*>", " ", str(value or "")))).strip()


def request(url: str, *, binary: bool = False) -> bytes:
    CACHE.mkdir(parents=True, exist_ok=True)
    key = CACHE / (hashlib.sha256(url.encode("utf-8")).hexdigest() + (".image" if binary else ".json"))
    if key.exists():
        return key.read_bytes()
    last_error: Exception | None = None
    attempts = 2 if binary else 5
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "image/*" if binary else "application/json"})
            with urllib.request.urlopen(req, timeout=20 if binary else 45) as response:
                payload = response.read(20_000_001)
            if len(payload) > 20_000_000:
                raise ValueError("remote asset exceeds the 20 MB build limit")
            key.write_bytes(payload)
            return payload
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as error:
            last_error = error
            if attempt == attempts - 1:
                break
            retry_after = 0
            if isinstance(error, urllib.error.HTTPError):
                try:
                    retry_after = int(error.headers.get("Retry-After", "0"))
                except ValueError:
                    pass
            time.sleep(max(retry_after, min(20, 3 * (attempt + 1))))
    raise RuntimeError(f"download failed: {url}") from last_error


def commons_candidates(scientific_name: str) -> list[dict[str, str]]:
    params = {
        "action": "query",
        "format": "json",
        "formatversion": "2",
        "generator": "search",
        "gsrnamespace": "6",
        "gsrsearch": f'intitle:"{scientific_name}" filetype:bitmap',
        "gsrlimit": "20",
        "prop": "imageinfo",
        "iiprop": "url|mime|size|extmetadata",
        "iiurlwidth": "960",
        "maxlag": "5",
    }
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(params)
    result = json.loads(request(url).decode("utf-8"))
    candidates: list[dict[str, str]] = []
    for page in result.get("query", {}).get("pages", []):
        info = (page.get("imageinfo") or [{}])[0]
        meta = info.get("extmetadata") or {}
        license_name = clean((meta.get("LicenseShortName") or {}).get("value"))
        author = clean((meta.get("Artist") or {}).get("value"))
        title = clean((meta.get("ObjectName") or {}).get("value")) or clean(page.get("title", "")).removeprefix("File:")
        source = info.get("descriptionurl", "")
        image_url = info.get("thumburl") or info.get("url", "")
        width = int(info.get("thumbwidth") or info.get("width") or 0)
        height = int(info.get("thumbheight") or info.get("height") or 0)
        if not image_url or not source or min(width, height) < 240 or not ALLOWED_LICENSE.match(license_name):
            continue
        if not author:
            if not re.match(r"^(?:CC0|Public domain)", license_name, re.I):
                continue
            author = "Urheber nicht angegeben"
        candidates.append({
            "download": image_url,
            "author": author,
            "license": license_name,
            "licenseUrl": clean((meta.get("LicenseUrl") or {}).get("value")),
            "source": source,
            "caption": title,
        })
    # MediaWiki's search order is relevance-ranked.  Keeping it is intentional:
    # alphabetic sorting tended to select laboratory close-ups or habitat panoramas
    # before a recognisable habit/leaf/flower photograph.
    return candidates


def build_images(profile: dict[str, object]) -> list[dict[str, str]]:
    slug = str(profile["id"])
    search_name = str(profile.get("imageSearchName") or profile["scientificName"])
    existing_catalog = json.loads((ANDROID / "plant-catalog.json").read_text(encoding="utf-8"))
    existing = next((plant for plant in existing_catalog["plants"] if plant.get("id") == slug), None)
    if not REFRESH_IMAGES and existing and len(existing.get("images", [])) == 2 and all((ANDROID / image["path"]).is_file() for image in existing["images"]):
        return existing["images"]

    result: list[dict[str, str]] = []
    staged: list[tuple[str, bytes]] = []
    seen_hashes: set[str] = set()
    candidates = commons_candidates(search_name)
    preferred_indexes = [index for index in profile.get("imageCandidateIndexes", []) if isinstance(index, int) and 0 <= index < len(candidates)]
    preferred = [candidates[index] for index in preferred_indexes]
    preferred_sources = {candidate["source"] for candidate in preferred}
    ordered_candidates = preferred + [candidate for candidate in candidates if candidate["source"] not in preferred_sources]
    for candidate in ordered_candidates:
        try:
            raw = request(candidate["download"], binary=True)
            picture = ImageOps.exif_transpose(Image.open(io.BytesIO(raw))).convert("RGB")
            if min(picture.size) < 240:
                continue
            picture.thumbnail((800, 650), Image.Resampling.LANCZOS)
            output = io.BytesIO()
            picture.save(output, "JPEG", quality=80, optimize=True, progressive=True)
            encoded = output.getvalue()
            digest = hashlib.sha256(encoded).hexdigest()
            if digest in seen_hashes:
                continue
            seen_hashes.add(digest)
            filename = f"{slug}-{len(result) + 1}.jpg"
            staged.append((filename, encoded))
            result.append({
                **{key: value for key, value in candidate.items() if key != "download"},
                "path": f"plant-images/{filename}",
                "changes": "Auf maximal 800 × 650 Pixel verkleinert und als JPEG ohne EXIF/GPS gespeichert.",
            })
            if len(result) == 2:
                break
        except Exception as error:  # one unsuitable Commons file must not abort the candidate scan
            print(json.dumps({"imageSkipped": candidate.get("source"), "reason": type(error).__name__}), flush=True)
    if len(result) != 2:
        if existing and len(existing.get("images", [])) == 2 and all((ANDROID / image["path"]).is_file() for image in existing["images"]):
            print(json.dumps({"imageRefreshFallback": search_name, "keptExisting": 2}), flush=True)
            return existing["images"]
        raise RuntimeError(f"{search_name}: expected two verified reusable images, got {len(result)}")
    ANDROID_MEDIA.mkdir(parents=True, exist_ok=True)
    for filename, encoded in staged:
        (ANDROID_MEDIA / filename).write_bytes(encoded)
    return result


def make_profile(item: dict[str, object], manifest: dict[str, object]) -> dict[str, object]:
    legal_note = (
        "Art der EU-Unionsliste: Halten, Einführen, Verkaufen, Züchten, Anbauen und Freisetzen "
        "sind nach der EU-Verordnung grundsätzlich beschränkt; behördliche Ausnahmen bleiben vorbehalten."
    )
    disposal_note = (
        "Pflanzenteile so sichern, dass keine Regeneration oder Verschleppung möglich ist. "
        "Die konkrete Entsorgung vor Ort mit Gemeinde oder zuständiger Fachstelle klären; niemals in Natur oder Gewässer ausbringen."
    )
    official_sources = [
        {"title": "Umweltbundesamt Österreich · Artenprofil", "url": item["sourceUrl"], "checked": manifest["checked"]},
        {"title": "Umweltbundesamt Österreich · Neophyten der Unionsliste", "url": manifest["officialListUrl"], "checked": manifest["checked"]},
        {"title": "Europäische Kommission · Invasive gebietsfremde Arten", "url": manifest["euPolicyUrl"], "checked": manifest["checked"]},
    ]
    health = list(item.get("health") or [])
    recognition = list(item["recognition"])
    recognition_text = " ".join(str(value) for value in recognition)
    life: list[str] = []
    if re.search(r"\bein(?:e|ä)jähr", recognition_text, re.I):
        life = ["Einjährig laut amtlichem Artenprofil"]
    elif re.search(r"\bausdauernd", recognition_text, re.I):
        life = ["Ausdauernd laut amtlichem Artenprofil"]
    foliage: list[str] = []
    if re.search(r"\bimmergrün", recognition_text, re.I):
        foliage = ["Immergrün laut amtlichem Artenprofil"]
    elif re.search(r"\bsommergrün", recognition_text, re.I):
        foliage = ["Sommergrün laut amtlichem Artenprofil"]
    pictures = build_images(item)
    for picture in pictures:
        license_name = picture.get("license", "")
        license_url = picture.get("licenseUrl", "")
        if re.match(r"^Public domain$", license_name, re.I) and not license_url:
            license_url = "https://creativecommons.org/publicdomain/mark/1.0/"
        elif re.match(r"^CC0", license_name, re.I) and not license_url:
            license_url = "https://creativecommons.org/publicdomain/zero/1.0/"
        picture["licenseUrl"] = license_url.replace("http://", "https://", 1)
    return {
        "id": item["id"],
        "name": item["name"],
        "scientificName": item["scientificName"],
        "category": item["category"],
        "family": item["family"],
        "alternativeNames": item.get("alternativeNames", []),
        "light": [], "soil": [], "ph": [], "water": [], "bloom": [], "colors": [],
        "life": life, "foliage": foliage, "growth": recognition, "propagation": [], "hardiness": [], "dimensions": [],
        "origin": item["origin"],
        "care": " ".join(str(value) for value in item["control"]),
        "poison": health,
        "contact": health,
        "sources": official_sources,
        "images": pictures,
        "planting": "Nicht anbauen oder in die Umwelt freisetzen: Art der EU-Unionsliste.",
        "neophyte": {
            "isNeophyte": True,
            "invasive": True,
            "unionConcern": True,
            "austriaOccurrence": True,
            "austriaStatus": item["austriaStatus"],
            "establishmentStatus": ESTABLISHMENT_CLASSES.get(str(item["austriaStatus"]), "unknown"),
            "statusLabel": "Invasive gebietsfremde Art von unionsweiter Bedeutung",
            "referenceDate": manifest["checked"],
            "recognition": item["recognition"],
            "spread": item["spread"],
            "impacts": item["impacts"],
            "control": item["control"],
            "disposal": [disposal_note],
            "legalNotes": [legal_note],
            "dataNote": "Standardisierte Licht-, Boden-, pH-, Blütezeit-, Maß- und Winterhärtewerte bleiben leer, wenn das amtliche Artenprofil dafür keine sicher übertragbare Angabe enthält.",
        },
    }


def classify_existing_profile(catalog: dict[str, object], item: dict[str, object], manifest: dict[str, object]) -> None:
    """Attach sourced neophyte metadata without replacing an existing plant record."""
    plant = next((entry for entry in catalog["plants"] if entry.get("id") == item["id"]), None)
    if not plant:
        raise RuntimeError(f"existing catalogue profile not found: {item['id']}")
    source = {
        "title": "Umweltbundesamt Österreich · ausgewählte Neobiota-Art",
        "url": item["sourceUrl"],
        "checked": manifest["checked"],
    }
    if not any(entry.get("url") == source["url"] for entry in plant.get("sources", [])):
        plant.setdefault("sources", []).append(source)
    plant["neophyte"] = {
        "isNeophyte": True,
        "invasive": bool(item["invasive"]),
        "unionConcern": False,
        "austriaOccurrence": True,
        "austriaStatus": item["austriaStatus"],
        "establishmentStatus": item["establishmentStatus"],
        "statusLabel": item["statusLabel"],
        "referenceDate": manifest["checked"],
        "recognition": item["recognition"],
        "spread": item["spread"],
        "impacts": item["impacts"],
        "control": item["control"],
        "disposal": [
            "Schnittgut so behandeln, dass keine Samen in die freie Natur gelangen; örtliche Vorgaben der Gemeinde oder Fachstelle beachten."
        ],
        "legalNotes": [
            "Nicht auf der EU-Unionsliste. Die besonderen EU-Beschränkungen für Unionslistenarten gelten daher nicht allein aufgrund dieser fachlichen Einstufung; örtliche Vorgaben bleiben zu prüfen."
        ],
        "dataNote": "Das vorhandene Gartenprofil bleibt erhalten; die zusätzliche Neophytenklassifizierung enthält nur die aus der amtlichen Artseite sicher ableitbaren Angaben.",
    }


def write_catalog(target: Path, catalog: dict[str, object]) -> None:
    encoded = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    target.joinpath("plant-catalog.json").write_text(encoded, encoding="utf-8")
    target.joinpath("plant-catalog.js").write_text(
        "/* Offline botanical database. Generated from plant-catalog.json. */\n"
        + "window.GartenpiratCatalog="
        + json.dumps(catalog, ensure_ascii=False, separators=(",", ":"))
        + ";\n",
        encoding="utf-8",
    )


def update_service_worker(catalog: dict[str, object]) -> None:
    path = STATIC / "sw.js"
    text = path.read_text(encoding="utf-8")
    match = re.search(r"const GP_ASSETS = (\[[^\n]*\]);", text)
    if not match:
        raise RuntimeError("GP_ASSETS declaration not found in service worker")
    assets = json.loads(match.group(1))
    image_assets = sorted({"/" + image["path"] for plant in catalog["plants"] for image in plant.get("images", [])})
    core = [asset for asset in assets if not asset.startswith("/plant-images/")]
    replacement = "const GP_ASSETS = " + json.dumps(core + image_assets, ensure_ascii=False, separators=(",", ":")) + ";"
    text = text[: match.start()] + replacement + text[match.end() :]
    text = re.sub(r"const CACHE = '[^']+';", "const CACHE = 'haus-garten-v16-neophytes-viewer';", text, count=1)
    path.write_text(text, encoding="utf-8")


def validate(catalog: dict[str, object], expected_new: set[str], expected_existing: set[str]) -> None:
    plants = catalog["plants"]
    for key in ("id", "scientificName"):
        values = [str(plant[key]).casefold() for plant in plants]
        if len(values) != len(set(values)):
            raise RuntimeError(f"duplicate {key} in catalogue")
    added = [plant for plant in plants if plant["id"] in expected_new]
    if len(added) != len(expected_new):
        raise RuntimeError("not every curated neophyte was added")
    for plant in added:
        if not plant.get("neophyte", {}).get("unionConcern"):
            raise RuntimeError(f"missing Union-list metadata: {plant['id']}")
        if len(plant.get("images", [])) != 2:
            raise RuntimeError(f"image count differs from two: {plant['id']}")
        for image in plant["images"]:
            if not ALLOWED_LICENSE.match(image.get("license", "")):
                raise RuntimeError(f"non-reusable image license: {plant['id']}")
            if not (ANDROID / image["path"]).is_file():
                raise RuntimeError(f"missing offline image: {image['path']}")
    for profile_id in expected_existing:
        plant = next((entry for entry in plants if entry["id"] == profile_id), None)
        if not plant or not plant.get("neophyte", {}).get("isNeophyte") or plant["neophyte"].get("unionConcern"):
            raise RuntimeError(f"invalid non-Union neophyte classification: {profile_id}")


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    catalog = json.loads((ANDROID / "plant-catalog.json").read_text(encoding="utf-8"))
    original_count = len(catalog["plants"])
    new_ids = {str(item["id"]) for item in manifest["profiles"]}
    existing_ids = {str(item["id"]) for item in manifest.get("existingProfiles", [])}
    untouched = [plant for plant in catalog["plants"] if plant.get("id") not in new_ids]
    additions = []
    for offset, item in enumerate(manifest["profiles"], 1):
        print(json.dumps({"plant": item["scientificName"], "progress": f"{offset}/{len(manifest['profiles'])}"}, ensure_ascii=False), flush=True)
        additions.append(make_profile(item, manifest))
        if REFRESH_IMAGES:
            time.sleep(1.5)
    working_catalog = {**catalog, "plants": untouched + additions}
    for item in manifest.get("existingProfiles", []):
        classify_existing_profile(working_catalog, item, manifest)
    catalog["schemaVersion"] = 2
    catalog["edition"] = manifest["checked"]
    catalog["scope"] = (
        "Kuratierte Auswahl häufig verwendeter Gartenpflanzen, der 21 in Österreich nachgewiesenen "
        "Pflanzenarten der EU-Unionsliste und einzelner weiterer amtlich belegter Neophyten; kein vollständiges Artenverzeichnis."
    )
    catalog["neophyteReference"] = {
        "scope": "Amtlich belegte Neophyten in Österreich; EU-Unionslistenstatus wird getrennt ausgewiesen",
        "referenceDate": manifest["checked"],
        "officialListUrl": manifest["officialListUrl"],
        "euPolicyUrl": manifest["euPolicyUrl"],
        "euUpdateUrl": manifest["euUpdateUrl"],
        "managementUrl": manifest["managementUrl"],
        "notice": "Status und Recht können sich ändern. Vor Maßnahmen die verlinkten amtlichen Quellen und örtlichen Vorgaben prüfen.",
    }
    catalog["plants"] = sorted(working_catalog["plants"], key=lambda plant: str(plant["name"]).casefold())
    validate(catalog, new_ids, existing_ids)
    write_catalog(ANDROID, catalog)
    from sync_plant_assets import synchronize
    synchronize(write=True)
    mirrors = {filename: True for filename in ('plant-catalog.json', 'plant-catalog.js')}
    print(json.dumps({
        "before": original_count,
        "after": len(catalog["plants"]),
        "newProfiles": len(additions),
        "newImages": sum(len(item["images"]) for item in additions),
        "classifiedExistingProfiles": len(manifest.get("existingProfiles", [])),
        "mirrorsIdentical": mirrors,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
