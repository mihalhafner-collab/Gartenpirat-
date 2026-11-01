"""Fill gaps in the editorial DACH selection with exact RHS profiles.
Reads short factual attributes, not descriptions; images from attributable Commons files.
"""
from pathlib import Path
from html.parser import HTMLParser
import concurrent.futures,html,importlib.util,json,re,runpy,sys,urllib.parse
ROOT=Path(__file__).resolve().parents[1]
TASK=ROOT/"outputs/plant-catalog-work";TASK.mkdir(parents=True,exist_ok=True)
m=runpy.run_path(str(ROOT/"tools/build_plant_catalog.py"),run_name="plant_catalog_library")
class Node:
 def __init__(self,tag="",attrs=(),parent=None):self.tag=tag;self.attrs=dict(attrs);self.parent=parent;self.children=[]
 def text(self):return " ".join(x.text() if isinstance(x,Node) else x for x in self.children).strip()
 def all(self,tag):
  found=[]
  for child in self.children:
   if isinstance(child,Node):
    if child.tag==tag:found.append(child)
    found.extend(child.all(tag))
  return found
class Tree(HTMLParser):
 def __init__(self):super().__init__(convert_charrefs=True);self.root=Node("root");self.node=self.root
 def handle_starttag(self,tag,attrs):
  n=Node(tag,attrs,self.node);self.node.children.append(n)
  if tag not in {"img","meta","link","input","hr","br","source","area","wbr","embed","param","track"}:self.node=n
 def handle_endtag(self,tag):
  p=self.node
  while p.parent:
   if p.tag==tag:self.node=p.parent;return
   p=p.parent
 def handle_data(self,data):self.node.children.append(data)
original_download=m["download"]
def get(url):
 # RHS is an additional primary publisher; reuse the same hash cache without persisting cookies.
 if urllib.parse.urlsplit(url).hostname=="www.rhs.org.uk":
  import hashlib
  from urllib import request as urlrequest
  cache=m["CACHE"]/(hashlib.sha256(url.encode()).hexdigest()+".cache")
  if cache.exists():return cache.read_bytes()
  request=urlrequest.Request(url,headers={"User-Agent":m["UA"]})
  content=urlrequest.urlopen(request,timeout=25).read();cache.write_bytes(content);return content
 return original_download(url)
KNOWN=json.loads((ROOT/"tools/plant_catalog_rhs_sources.json").read_text(encoding="utf-8"))
def resolve(latin):
 if latin in KNOWN:return KNOWN[latin]
 text=get("https://www.rhs.org.uk/plants/search-results?query="+urllib.parse.quote(latin)).decode("utf-8")
 slug=latin.lower().replace(" ","-")
 paths=re.findall(r'href=["\']([^"\']+)["\']',text)
 for path in paths:
  path=html.unescape(path);full=urllib.parse.urljoin("https://www.rhs.org.uk",path)
  if re.fullmatch(r"https://www\.rhs\.org\.uk/plants/\d+/"+re.escape(slug)+r"/details/?",full):return full
 raise ValueError("Exact RHS profile not resolved")
MAP={"Full sun":"Sonne","Partial shade":"Halbschatten","Full shade":"Schatten","Clay":"Lehm / Ton","Loam":"Lehmig","Sand":"Sand","Chalk":"Kalkhaltig","Moist but well–drained":"Frisch bis feucht, durchlässig","Well–drained":"Gut durchlässig","Poorly–drained":"Nass / wenig durchlässig","Acid":"Sauer","Alkaline":"Alkalisch","Neutral":"Neutral","Deciduous":"Sommergrün","Semi evergreen":"Teilweise immergrün","Evergreen":"Immergrün"}
def from_rhs(row):
 german,latin,group,care=row.split("|");url=resolve(latin)
 text=get(url).decode("utf-8");tree=Tree();tree.feed(text);node=tree.root
 facts=m["traits"](text)
 def card(title):
  for h in node.all("h2"):
   if h.text()==title:
    parent=h.parent
    while parent.parent and "card" not in parent.attrs.get("class","").split():parent=parent.parent
    return parent.text()
  return ""
 def values(raw,words):return [MAP.get(word,word) for word in words if word in raw]
 light=values(card("Position"),["Full sun","Partial shade","Full shade"])
 soils=values(card("Soil Types"),["Chalk","Clay","Loam","Sand"])
 water=values(" ".join(facts.get("Moisture",[])),["Moist but well–drained","Well–drained","Poorly–drained"])
 if not light or not water:raise ValueError("Exact profile lacks cultivation attributes")
 dimensions=[]
 for title,label in [("Max Height","Höhe"),("Max Spread","Breite")]:
  size=card(title).replace(title,"").strip()
  if size:dimensions.append(label+": "+size.replace("metres","m"))
 images=m["images"]("",latin,latin.lower().replace(" ","-"),url)
 if len(images)!=2:raise ValueError("Two licensed exact-taxon images unavailable")
 hardiness=[]
 for key,values_ in facts.items():
  if key.startswith("Hardiness"):
   match=re.search(r"\bH[1-7][ABCabc]?\b"," ".join(values_))
   if match:hardiness=["RHS "+match.group(0)+" (UK-Klassifikation)"]
 p={"id":latin.lower().replace(" ","-"),"name":german,"scientificName":latin,"category":group,"family":(facts.get("Family") or ["Nicht angegeben"])[0],"light":light,"soil":soils,"water":water,"ph":values(" ".join(facts.get("pH",[])),["Acid","Alkaline","Neutral"]),"dimensions":dimensions,"life":[],"foliage":[MAP.get(v,v) for v in facts.get("Foliage",[])],"growth":[],"propagation":[],"origin":facts.get("Plant Range",[]),"bloom":[],"colors":[],"hardiness":hardiness,"care":care,"poison":[],"contact":[],"images":images,"sources":[{"title":"Royal Horticultural Society · Pflanzenprofil","url":url,"checked":"2026-10-15"}]}
 return p
def generic_rose():
 url="https://www.rhs.org.uk/plants/roses/growing-guide"
 get(url)
 # This is explicitly a group profile, not fabricated species/cultivar-specific metrics.
 latin="Rosa";pictures=m["images"]("",latin,"rosa",url)
 if len(pictures)!=2:raise ValueError("Rose illustration licensing incomplete")
 return {"id":"rosa","name":"Gartenrosen","scientificName":"Rosa · Gartengruppen","category":"Rosen","family":"Rosaceae","light":["Sonne","Halbschatten (sortenabhängig)"],"soil":["Humos und nährstoffreich"],"water":["Frisch, gut durchlässig; keine Staunässe"],"ph":[],"dimensions":["Von kompakten Beet- bis großen Kletterrosen; Rosengruppe und Sorte prüfen"],"life":["Mehrjährig"],"foliage":[],"growth":[],"propagation":["Stecklinge (je nach Sorte)"],"origin":["Gartenzüchtungen verschiedener Herkunft"],"bloom":["Sommer; remontierende Sorten auch im Herbst"],"colors":["Sortenabhängig"],"hardiness":["Sorten- und lagenabhängig"],"care":"Schnitt nach Rosengruppe planen. Verblühtes entfernen, bodennah wässern und auf Blattkrankheiten achten.","poison":[],"contact":[],"images":pictures,"sources":[{"title":"RHS · Rosen-Kulturanleitung","url":url,"checked":"2026-10-15"}]}
def main():
 assets=ROOT/"app/src/main/assets"
 catalog=json.loads((assets/"plant-catalog.json").read_text(encoding="utf-8"));by_id={p["id"]:p for p in catalog["plants"]}
 rows=[r for r in (ROOT/"tools/plant_catalog_seed.tsv").read_text(encoding="utf-8").splitlines() if r and not r.startswith("#") and r.split("|")[1].lower().replace(" ","-") not in by_id]
 added=[];unresolved=[]
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
  jobs=[(r,pool.submit(from_rhs,r)) for r in rows if r.split("|")[1]!="Rosa"]
  for row,future in jobs:
   try:
    p=future.result();by_id[p["id"]]=p;added.append(p["scientificName"]);print(json.dumps({"added_rhs":p["scientificName"]}),flush=True)
   except Exception as error:unresolved.append({"plant":row.split("|")[1],"reason":str(error)})
 for row in ["Dahlien|Dahlia|Zwiebeln und Knollen|Nicht frosthart; Knollen nach Frost trocken und frostfrei überwintern.","Kletter-Hortensie|Hydrangea petiolaris|Kletterpflanzen|Nach der Blüte bei Bedarf begrenzen; Haftwurzeln und Untergrund beachten."]:
  try:
   p=m["build"](row)
   if "failure" in p:raise ValueError(p["reason"])
   by_id[p["id"]]=p;added.append(p["scientificName"])
  except Exception as error:unresolved.append({"plant":row.split("|")[1],"reason":str(error)})
 try:p=generic_rose();by_id[p["id"]]=p;added.append("Rosa · Gartengruppen")
 except Exception as error:unresolved.append({"plant":"Rosa","reason":str(error)})
 catalog["plants"]=sorted(by_id.values(),key=lambda p:p["name"].casefold())
 catalog["climateNote"]="Quellprofile stammen aus der NC State Extension (USA) und der RHS (Großbritannien). Größen und Blütezeiten sind Richtwerte. USDA-Zonen und RHS-Klassen sind keine Frostgarantie für DACH; Sorte, Höhenlage, Mikroklima und Winternässe beachten."
 (assets/"plant-catalog.json").write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding="utf-8")
 (TASK/"catalog-extensions.json").write_text(json.dumps({"added":added,"unresolved":unresolved},ensure_ascii=False,indent=2),encoding="utf-8")
 print(json.dumps({"total":len(by_id),"added":added,"unresolved":unresolved}),flush=True)
if __name__=="__main__":
 main()
 from sync_plant_assets import synchronize
 synchronize(write=True)
