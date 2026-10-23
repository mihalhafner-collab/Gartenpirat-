import fs from 'node:fs';

function compactFullProfile(p){
 const dec=s=>{for(let i=0;i<2;i++)s=s.replace(/&amp;/g,"&").replace(/&quot;/g,'"').replace(/&#39;|&apos;/g,"'").replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&nbsp;/g," ").replace(/&#x([0-9a-f]+);/gi,(_,n)=>String.fromCodePoint(parseInt(n,16))).replace(/&#(\d+);/g,(_,n)=>String.fromCodePoint(+n));return s;};
 const clean=s=>dec(dec(s).replace(/<\/?[a-zA-Z][^>]*>/g," ")).replace(/\s+/g," ").trim();
 const allowed=new Set(["Genus","Species","Family","Life Cycle","Country Or Region Of Origin","Dimensions","Woody Plant Leaf Characteristics","Habit/Form","Growth Rate","Maintenance","Light","Soil Texture","Soil pH","Soil Drainage","USDA Plant Hardiness Zone","Flower Bloom Time","Flower Color","Leaf Color","Recommended Propagation Strategy","Poison Severity","Causes Contact Dermatitis","Plant Type","Attracts","Resistance To Challenges","pH","Moisture","Foliage","Plant Range","Habit","Potentially harmful","Name Status","Name status"]);
 const facts={},t=p.text||"";for(const m of t.matchAll(/<dt\b[^>]*>([\s\S]*?)<\/dt>([\s\S]*?)(?=<dt\b|<\/dl>)/g)){const k=clean(m[1]).replace(/:$/,"");if(!allowed.has(k))continue;const vs=[...m[2].matchAll(/<dd\b[^>]*>([\s\S]*?)<\/dd>/g)].map(x=>clean(x[1])).filter(v=>v&&v.length<650);if(vs.length)facts[k]=[...new Set(vs)];}
 const figures=[];for(const m of t.matchAll(/<img\b[^>]*>/g)){const a={};for(const x of m[0].matchAll(/([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')/g))a[x[1]]=dec(x[2]??x[3]);if(!a["data-license"]||!a["data-attrib"])continue;const licenseRaw=clean(a["data-license"]),author=clean(a["data-attrib"]),caption=clean(a["data-caption"]||a.alt||"");const lm=licenseRaw.match(/^CC[- ]BY([- ]SA)?[- ](\d\.\d)(?: ([a-z]{2}))?$/i);const cc0=/^CC0(?: 1\.0)?$/i.test(licenseRaw);const licenseId=lm?"CC-BY"+(lm[1]?"-SA":"")+"-"+lm[2]+(lm[3]?"-"+lm[3].toUpperCase():""):cc0?"CC0-1.0":null;const href=a["data-license"].match(/href=['"]([^'"]+)['"]/);const licenseUrl=cc0?"https://creativecommons.org/publicdomain/zero/1.0/":href?href[1].replace(/^http:/,"https:"):null;const raw=a["data-downloadurl"]||a.src;const url=raw?raw.split("?")[0]:null;if(!url)continue;let label=null;try{const d=raw.split("?")[1]?.match(/(?:^|&)response-content-disposition=([^&]+)/)?.[1];if(d)label=dec(decodeURIComponent(decodeURIComponent(d)));}catch{}
 figures.push({caption,author,licenseRaw,licenseId,licenseUrl,eligible:!!licenseId,url,creditFilename:label,association:"original_image_data_attributes",providerImageId:a["data-image-id"]||null});}
 const fieldAliases={};if(p.url.includes("rhs.org.uk")){const cards={};for(const m of t.matchAll(/<h2\b[^>]*>([\s\S]*?)<\/h2>([\s\S]*?)(?=<h[23]\b|$)/g)){const k=clean(m[1]);if(["Position","Soil Types","Max Height","Max Spread"].includes(k)&&!cards[k])cards[k]=clean(m[2]);}
 if(cards.Position){facts.Light=["Full sun","Partial shade","Full shade"].filter(v=>cards.Position.includes(v));fieldAliases.Light="Position";}
 if(cards["Soil Types"]){facts["Soil Texture"]=["Chalk","Clay","Loam","Sand"].filter(v=>cards["Soil Types"].includes(v));fieldAliases["Soil Texture"]="Soil Types";}
 if(facts.Moisture){facts["Soil Drainage"]=facts.Moisture.flatMap(v=>v.split(" or "));fieldAliases["Soil Drainage"]="Moisture";}
 if(facts.pH){facts["Soil pH"]=facts.pH.flatMap(v=>v.split(" or "));fieldAliases["Soil pH"]="pH";}
 const dims=[];for(const [field,label] of [["Max Height","Height"],["Max Spread","Width"]]){const x=cards[field]?.match(/\d+(?:\.\d+)?\s*[-–]\s*\d+(?:\.\d+)?\s*metres?|\d+(?:\.\d+)?\s*metres?/);if(x)dims.push(label+": "+x[0]);}if(dims.length)facts.Dimensions=dims;
 }
 return{url:p.url,finalUrl:p.final_url||p.url,title:p.title,checkedAt:"2026-10-23",method:"Public original DOM via TinyFish include_selectors body; original dt/dd and image data-caption/data-attrib/data-license/data-downloadurl attributes.",identityHeadings:[...t.matchAll(/<h1\b[^>]*>([\s\S]*?)<\/h1>/g)].map(m=>clean(m[1]).replace(/ Play pronunciation$/, "").trim()),facts,fieldAliases,figures,photoCount:figures.filter(f=>f.eligible&&f.url).length};
}

// Input is the public original-DOM result of the connected research tool.
// Emit factual fields and image credits; never persist signed query parameters.
if (process.argv[2]) {
  const input = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  const result = (input.results || input).map(compactFullProfile);
  fs.writeFileSync(process.argv[3], JSON.stringify(result, null, 2) + '\n');
}

export { compactFullProfile };
