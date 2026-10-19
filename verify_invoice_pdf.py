"""Read-only technical check of an explicitly selected private document.

Does not issue an invoice, select a real customer or check invented legal data.
"""
import argparse,hashlib,json,re
from pathlib import Path
import runtime_paths

def inspect(path,expected_sha256=None):
    path=runtime_paths.business_path(path);raw=path.read_bytes();digest=hashlib.sha256(raw).hexdigest()
    if expected_sha256 and digest!=expected_sha256:raise ValueError('Document hash mismatch')
    if not raw.startswith(b'%PDF-') or b'%%EOF' not in raw[-2048:]:raise ValueError('Invalid PDF framing')
    return {'result':'PASS','check':'PDF framing and byte hash only','bytes':len(raw),'sha256':digest,'page_objects':len(re.findall(rb'/Type\s*/Page\b',raw))}

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('pdf',type=Path);parser.add_argument('--expected-sha256');args=parser.parse_args()
    runtime_paths.private_root();print(json.dumps(inspect(args.pdf,args.expected_sha256)))
