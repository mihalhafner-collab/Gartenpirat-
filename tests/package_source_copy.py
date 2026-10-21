"""Package the current original code for review; all three privacy gates apply.

No anonymized alternate runtime, copied production database or mutated bootstrap.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from privacy_guard import source_gate,workspace_gate
from tools.drive_source_transfer import write_verified_zip
from tools.source_preservation import verify_preserved_source, SourcePreservationError


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=('gemini','perplexity','codex-cloud'),default='codex-cloud')
    parser.add_argument('--revision',default='current')
    parser.add_argument('--output',type=Path)
    parser.add_argument('--reuse-existing',action='store_true',help='Reuse only after exact inventory, original byte, mode and privacy verification.')
    args=parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9-]+',args.revision):parser.error('Invalid source revision label')
    preservation=verify_preserved_source(ROOT)
    source_gate();workspace_gate()
    target=args.output or ROOT/'outputs'/f'{args.target}-source-{args.revision}.zip'
    if target.suffix!='.zip' or (target.exists() and not args.reuse_existing):raise ValueError('An unused ZIP target is required.')
    commit=preservation['git_sha']
    git_env={key:value for key,value in os.environ.items() if not key.startswith('HG_ORIGINAL_')}
    entries=subprocess.check_output(['git','ls-tree','-r','-z',commit],cwd=ROOT,env=git_env).split(b'\0')
    excluded={'.apk','.aab','.idsig','.pyc'}
    payload={};modes={}
    for entry in entries:
        if not entry:continue
        metadata,raw_name=entry.split(b'\t',1)
        mode,kind,_=metadata.split()
        name=raw_name.decode('utf-8')
        if mode not in (b'100644',b'100755') or kind!=b'blob':
            raise ValueError('Only original regular source files may be exported')
        if Path(name).suffix in excluded:continue
        payload[name]=subprocess.check_output(['git','show',commit+':'+name],cwd=ROOT,env=git_env)
        modes[name]=int(mode,8)&0o777
    result=write_verified_zip(payload,target,modes,reuse_existing=args.reuse_existing)
    print(json.dumps({**result,'source_preservation':preservation,'target_path_sha256':hashlib.sha256(str(target).encode()).hexdigest()}))


if __name__=='__main__':
    try:main()
    except SourcePreservationError as error:
        print(str(error),file=sys.stderr)
        raise SystemExit(1)
