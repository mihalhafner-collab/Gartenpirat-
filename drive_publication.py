"""Verify Pixel's immutable manifests in the existing exchange folder.

A local mounted-file read is NOT an independent Google-cloud acknowledgement.
No journals or original files are mutated by this reader.
"""
import hashlib
import json
import re
from pathlib import Path
import runtime_paths

MAX_BYTES=64*1024*1024

def read(path):
    path=runtime_paths.business_path(path)
    if path.stat().st_size>MAX_BYTES:raise ValueError('Drive-Veröffentlichung ist zu groß.')
    raw=path.read_bytes()
    if len(raw)>MAX_BYTES:raise ValueError('Drive-Veröffentlichung ist zu groß.')
    return raw

def latest(folder):
    folder=runtime_paths.business_path(folder);manifests={};publishers=set()
    for path in folder.glob('HausGarten-Commit-*.json'):
        if not re.fullmatch(r'HausGarten-Commit-[0-9a-f]{64}\.json',path.name):raise ValueError('Ungültiger Drive-Veröffentlichungsname.')
        raw=read(path)
        if hashlib.sha256(raw).hexdigest()!=path.name[18:-5]:raise ValueError('Drive-Veröffentlichung wurde verändert.')
        m=json.loads(raw)
        if m.get('format')!='hausgarten.drive.commit.v1' or not isinstance(m.get('sequence'),int) or m['sequence']<1 or not m.get('publisher_id'):raise ValueError('Ungültige Drive-Veröffentlichung.')
        publishers.add(m['publisher_id']);key=(m['publisher_id'],m['sequence'])
        if key in manifests and manifests[key]!=m:raise ValueError('WRITE_CONFLICT: Mehrere Veröffentlichungen desselben Ausgangsstands. Keine Variante ausgewählt.')
        manifests[key]=m
    if not manifests:return None
    if len(publishers)!=1:raise ValueError('WRITE_CONFLICT: Mehrere primäre Pixel-Herausgeber. Eigentümerprüfung erforderlich.')
    m=max(manifests.values(),key=lambda x:x['sequence']);sha=m.get('state_sha256','')
    if not re.fullmatch('[0-9a-f]{64}',sha) or m.get('state_name')!='HausGarten-State-'+sha+'.json':raise ValueError('Ungültige Drive-Datenreferenz.')
    raw=read(folder/m['state_name'])
    if hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Drive-Datenstand fehlt oder seine Prüfsumme weicht ab.')
    data=json.loads(raw)
    if not isinstance(data,dict) or not isinstance(m.get('receipts'),dict):raise ValueError('Ungültiger Drive-Datenstand.')
    data['_publication']={'sequence':m['sequence'],'state_sha256':sha,'media_complete':m.get('media_complete') is True,'confirmation_scope':'local_publication_read','cloud_confirmed':False}
    return data
