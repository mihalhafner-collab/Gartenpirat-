"""Build the offline botanical reference from attributable, cached primary sources.
No application data is imported. Only short factual traits are translated; no source prose.
"""
from __future__ import annotations
from pathlib import Path
from html.parser import HTMLParser
import concurrent.futures,hashlib,html,io,json,re,sys,time,urllib.request,urllib.parse
ROOT=Path(__file__).resolve().parents[1]
TASK=ROOT/"outputs/plant-catalog-work";TASK.mkdir(parents=True,exist_ok=True)
# Optional existing local dependency folder; a regular virtualenv also works.
if (ROOT/".dependencies").is_dir():sys.path.insert(0,str(ROOT/".dependencies"))
from PIL import Image,ImageOps
CACHE=TASK/"research/catalog";CACHE.mkdir(parents=True,exist_ok=True)
ASSETS=ROOT/"app/src/main/assets";MEDIA=ASSETS/"plant-images";MEDIA.mkdir(exist_ok=True)
UA="GartenpiratOfflineReference/1.0 (horticultural reference)"
class Page(HTMLParser):
 def __init__(self):super().__init__(convert_charrefs=True);self.images=[]
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if tag=="img" and a.get("data-license") and a.get("data-attrib"):self.images.append(a)
def clean(text):return re.sub(r"\s+"," ",html.unescape(re.sub(r"<[^>]*>"," ",text))).strip()
def download(url):
 host=urllib.parse.urlsplit(url).hostname or ""
 if not (host in {"plants.ces.ncsu.edu","commons.wikimedia.org","upload.wikimedia.org","thumb.wikimedia.org","eit-planttoolbox-prod.s3.amazonaws.com"}):raise ValueError("Unexpected public source host")
 key=CACHE/(hashlib.sha256(url.encode()).hexdigest()+".cache")
 if key.exists():return key.read_bytes()
 for attempt in range(3):
  try:
   request=urllib.request.Request(url,headers={"User-Agent":UA})
   content=urllib.request.urlopen(request,timeout=25).read()
   if len(content)>16000000:raise ValueError("Image exceeds limit")
   key.write_bytes(content);return content
  except Exception:
   if attempt==2:raise
   time.sleep(1+attempt)
def traits(text):
 out={}
 for dt,body in re.findall(r"<dt\b[^>]*>(.*?)</dt>(.*?)(?=<dt\b|</dl>)",text,re.S):
  key=clean(dt).rstrip(":")
  values=[]
  for dd in re.findall(r"<dd\b[^>]*>(.*?)</dd>",body,re.S):
   items=[clean(i) for i in re.findall(r"<li\b[^>]*>(.*?)</li>",dd,re.S)]
   values.extend(items or [clean(dd)])
  if values:out[key]=list(dict.fromkeys(values))
 return out
TERMS={"Full sun (6 or more hours of direct sunlight a day)":"Sonne (mind. 6 Stunden)","Partial Shade (Direct sunlight only part of the day, 2-6 hours)":"Halbschatten (2–6 Stunden Sonne)","Deep shade (Less than 2 hours to no direct sunlight)":"Schatten (unter 2 Stunden Sonne)","Clay":"Lehm / Ton","Loam (Silt)":"Lehmig / schluffig","Sand":"Sand","Shallow Rocky":"Steinig / flachgründig","High Organic Matter":"Humusreich","Good Drainage":"Gut durchlässig","Moist":"Frisch bis feucht","Occasionally Dry":"Zeitweise trocken","Occasionally Wet":"Zeitweise nass","Occasional Flooding":"Zeitweise überflutet","Frequent Standing Water":"Häufig stehendes Wasser","Acid (<6.0)":"Sauer (pH unter 6)","Neutral (6.0-8.0)":"Neutral (pH 6–8)","Alkaline (>8.0)":"Alkalisch (pH über 8)","Spring":"Frühling","Summer":"Sommer","Fall":"Herbst","Winter":"Winter","Early Spring":"Vorfrühling","Late Spring":"Spätfrühling","Early Summer":"Frühsommer","Late Summer":"Spätsommer","Early Fall":"Frühherbst","Late Winter":"Spätwinter","White":"Weiß","Pink":"Rosa","Red":"Rot","Yellow":"Gelb","Orange":"Orange","Purple/Lavender":"Violett / lavendel","Blue":"Blau","Gold/Yellow":"Gold / gelb","Green":"Grün","Cream/Tan":"Creme / beige","Insignificant":"Unscheinbar","Brown/Copper":"Braun / kupfer","Deciduous":"Sommergrün","Broadleaf Evergreen":"Immergrünes Laub","Needled Evergreen":"Immergrüne Nadeln","Semi-evergreen":"Wintergrün / teilweise immergrün","Herbaceous Perennial":"Mehrjährige Staude","Perennial":"Mehrjährig","Annual":"Einjährig","Biennial":"Zweijährig","Woody":"Verholzend","Rapid":"Schnell","Medium":"Mittel","Slow":"Langsam","Low":"Gering","High":"Hoch","Division":"Teilung","Seed":"Aussaat","Stem Cutting":"Stecklinge","Root Cutting":"Wurzelstecklinge","Layering":"Absenker","Grafting":"Veredelung","Bulb":"Zwiebel / Knolle","Spores":"Sporen","Drought":"Trockenheit","Deer":"Wildverbiss","Salt":"Salz","Heat":"Hitze","Pollution":"Luftbelastung","Black Walnut":"Walnuss-Nachbarschaft","Wet Soil":"Nasser Boden","Poor Soil":"Armer Boden","Compaction":"Verdichtung","Humidity":"Luftfeuchte"}
def translate(values):return [TERMS.get(v,v) for v in values]
def commons_images(latin):
 params={"action":"query","format":"json","generator":"search","gsrnamespace":6,"gsrsearch":'intitle:"'+latin+'" filetype:bitmap',"gsrlimit":8,"prop":"imageinfo","iiprop":"url|extmetadata","iiurlwidth":720}
 result=json.loads(download("https://commons.wikimedia.org/w/api.php?"+urllib.parse.urlencode(params)))
 out=[]
 for page in result.get("query",{}).get("pages",{}).values():
  item=(page.get("imageinfo") or [{}])[0];meta=item.get("extmetadata",{})
  license=meta.get("LicenseShortName",{}).get("value","")
  if not eligible(license):continue
  out.append({"src":item.get("thumburl") or item.get("url"),"author":clean(meta.get("Artist",{}).get("value","")),"license":license,"licenseUrl":meta.get("LicenseUrl",{}).get("value",""),"source":item.get("descriptionurl",""),"caption":clean(meta.get("ObjectName",{}).get("value",latin))})
 return out
def eligible(name):return bool(re.search(r"CC BY(?:-SA)? [1-4]\.[0-9]|CC0|Public domain",name,re.I)) and not re.search(r"NC|ND",name,re.I)
def images(text,latin,slug,url):
 parser=Page();parser.feed(text);candidates=[]
 for a in parser.images:
  license=clean(a["data-license"])
  if not eligible(license):continue
  href=re.search(r"href=['\"]([^'\"]+)",a["data-license"])
  candidates.append({"src":a.get("src"),"author":clean(a["data-attrib"]),"license":license,"licenseUrl":html.unescape(href.group(1)) if href else "","source":url,"caption":clean(a.get("data-caption",""))})
 out=[];seen=set()
 for candidate in candidates:
  if len(out)==2:break
  raw=candidate.get("src")
  if not raw or not candidate["author"]:continue
  identity=urllib.parse.urlsplit(raw).path
  if identity in seen:continue
  seen.add(identity)
  try:
   picture=ImageOps.exif_transpose(Image.open(io.BytesIO(download(raw)))).convert("RGB")
   if min(picture.size)<160:continue
   picture.thumbnail((800,650))
   name=slug+"-"+str(len(out)+1)+".jpg"
   # Re-encoding discards public-image EXIF/GPS. Licensing attribution remains in catalog.
   picture.save(MEDIA/name,"JPEG",quality=80,optimize=True)
   out.append({**{k:v for k,v in candidate.items() if k!="src"},"path":"plant-images/"+name,"changes":"Verkleinert und als JPEG ohne EXIF/GPS gespeichert."})
  except Exception:continue
 if len(out)<2:
  for candidate in commons_images(latin):
   if len(out)==2:break
   raw=candidate["src"]
   if not raw or not candidate["author"] or urllib.parse.urlsplit(raw).path in seen:continue
   seen.add(urllib.parse.urlsplit(raw).path)
   try:
    picture=ImageOps.exif_transpose(Image.open(io.BytesIO(download(raw)))).convert("RGB");picture.thumbnail((800,650))
    name=slug+"-"+str(len(out)+1)+".jpg";picture.save(MEDIA/name,"JPEG",quality=80,optimize=True)
    out.append({**{k:v for k,v in candidate.items() if k!="src"},"path":"plant-images/"+name,"changes":"Verkleinert und als JPEG ohne EXIF/GPS gespeichert."})
   except Exception:continue
 return out
ALIASES={"anemone-hupehensis":"eriocapitella-hupehensis","gaura-lindheimeri":"oenothera-lindheimeri","bergenia-cordifolia":"bergenia-crassifolia","hylotelephium-spectabile":"sedum-spectabile","pennisetum-alopecuroides":"cenchrus-alopecuroides","potentilla-fruticosa":"dasiphora-fruticosa","nassella-tenuissima":"stipa-tenuissima"}
def build(row):
 german,latin,group,care=row.split("|")
 slug=latin.lower().replace(" ","-")
 urls=[slug]+([ALIASES[slug]] if slug in ALIASES else [])
 source=None
 for candidate in urls:
  try:
   url="https://plants.ces.ncsu.edu/plants/"+candidate+"/"
   raw=download(url);source=raw.decode("utf-8")
   if "<h1" not in source:raise ValueError("Unexpected source")
   break
  except Exception:source=None
 if source is None:return {"failure":latin,"reason":"No verified primary profile"}
 facts=traits(source)
 def take(key):return translate(facts.get(key,[]))
 portrait={"id":slug,"name":german,"scientificName":latin,"category":group,
 "family":(facts.get("Family") or ["Nicht angegeben"])[0],
 "light":take("Light"),"soil":take("Soil Texture"),"ph":take("Soil pH"),"water":take("Soil Drainage"),
 "bloom":take("Flower Bloom Time"),"colors":take("Flower Color"),"life":take("Life Cycle"),
 "foliage":take("Woody Plant Leaf Characteristics"),"growth":take("Growth Rate"),"propagation":take("Recommended Propagation Strategy"),
 "hardiness":facts.get("USDA Plant Hardiness Zone",[]),"dimensions":facts.get("Dimensions",[]),
 "origin":facts.get("Country Or Region Of Origin",[]),"care":care,
 "poison":take("Poison Severity"),"contact":take("Causes Contact Dermatitis"),
 "sources":[{"title":"NC State Extension · Plant Toolbox","url":url,"checked":"2026-10-15"}],
 "images":images(source,latin,slug,url)}
 if not portrait["light"] or not portrait["water"]:return {"failure":latin,"reason":"Missing core traits","keys":sorted(facts)}
 if len(portrait["images"])<2:return {"failure":latin,"reason":"Fewer than two reusable images","profile":portrait}
 return portrait
def main():
 rows=[r for r in (ROOT/"tools/plant_catalog_seed.tsv").read_text(encoding="utf-8").splitlines() if r.strip() and not r.startswith("#")]
 all_rows=list(rows)
 prior=json.loads((TASK/"catalog-build-results.json").read_text(encoding="utf-8")) if (TASK/"catalog-build-results.json").exists() else {}
 missing=[x for x in prior.get("failures",[]) if x["reason"]=="No verified primary profile"]
 if "--retry-missing" in sys.argv:missing=[]
 missing_names={x["failure"] for x in missing}
 rows=[r for r in rows if r.split("|")[1] not in missing_names]
 records=[];failures=list(missing)
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
  futures=[pool.submit(build,row) for row in rows]
  for i,future in enumerate(futures,1):
   try:result=future.result()
   except Exception as error:result={"failure":rows[i-1].split("|")[1],"reason":type(error).__name__}
   (failures if "failure" in result else records).append(result)
   if i%10==0 or i==len(rows):print(json.dumps({"completed":i,"total":len(rows),"verified_plants":len(records),"unresolved":len(failures)}),flush=True)
 records.sort(key=lambda p:p["name"].casefold())
 catalog={"schemaVersion":1,"edition":"2026-10-15","region":"Deutschland, Österreich und Schweiz","scope":"Kuratierte Auswahl häufig verwendeter Gartenpflanzen; kein vollständiges Artenverzeichnis.","climateNote":"Quellprofile stammen aus der NC State Extension (USA). Blütezeiten und Größen sind Richtwerte. USDA-Zonen sind keine Frostgarantie für DACH; Sorte, Höhenlage, Mikroklima und Winternässe beachten.","safetyNote":"Bilder und Namen ersetzen keine sichere Bestimmung oder Verzehrfreigabe. Fehlende Giftigkeitsangabe bedeutet nicht ungiftig. Haustiere können anders reagieren.","plants":records}
 (ASSETS/"plant-catalog.json").write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding="utf-8")
 (ASSETS/"plant-catalog.js").write_text("/* Offline botanical database. Generated from plant-catalog.json. */\nwindow.GartenpiratCatalog="+json.dumps(catalog,ensure_ascii=False,separators=(",",":"))+";\n",encoding="utf-8")
 (TASK/"catalog-build-results.json").write_text(json.dumps({"requested":len(all_rows),"verified":len(records),"failures":failures},ensure_ascii=False,indent=2),encoding="utf-8")
 print(json.dumps({"catalogue":len(records),"failures":[{"plant":x["failure"],"reason":x["reason"]} for x in failures],"offline_images":sum(len(p["images"]) for p in records)}),flush=True)
if __name__=="__main__":
 main()
 from sync_plant_assets import synchronize
 synchronize(write=True)
