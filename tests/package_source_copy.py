"""Package the current original code for review; all three privacy gates apply.

No anonymized alternate runtime, copied production database or mutated bootstrap.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from privacy_guard import source_gate,workspace_gate,artifact_gate


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=('gemini','codex-cloud'),default='codex-cloud')
    parser.add_argument('--revision',default='current')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9-]+',args.revision):parser.error('Invalid source revision label')
    source_gate();workspace_gate()
    target=args.output or ROOT/'outputs'/f'{args.target}-source-{args.revision}.zip'
    if target.suffix!='.zip' or target.exists():raise ValueError('An unused ZIP target is required.')
    names=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0')
    excluded={'.apk','.aab','.idsig','.pyc'}
    files=[ROOT/n for n in names if n and (ROOT/n).is_file() and Path(n).suffix not in excluded]
    with tempfile.TemporaryDirectory(prefix='qa_source_package_') as folder:
        staging=Path(folder)/'source.zip'
        with zipfile.ZipFile(staging,'w',compression=zipfile.ZIP_DEFLATED) as archive:
            for path in sorted(files):archive.write(path,path.relative_to(ROOT).as_posix())
        result=artifact_gate(staging)
        target.parent.mkdir(parents=True,exist_ok=True)
        with target.open('xb') as output:output.write(staging.read_bytes())
    print(json.dumps({'status':'PASS','files':len(files),'artifact':result,'target_path_sha256':hashlib.sha256(str(target).encode()).hexdigest()}))


if __name__=='__main__':main()
