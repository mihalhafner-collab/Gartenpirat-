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

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from privacy_guard import source_gate,workspace_gate
from tools.drive_source_transfer import write_verified_zip


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--target',choices=('gemini','codex-cloud'),default='codex-cloud')
    parser.add_argument('--revision',default='current')
    parser.add_argument('--output',type=Path)
    parser.add_argument('--reuse-existing',action='store_true',help='Reuse only after exact inventory, original byte, mode and privacy verification.')
    args=parser.parse_args()
    if not re.fullmatch('[A-Za-z0-9-]+',args.revision):parser.error('Invalid source revision label')
    source_gate();workspace_gate()
    target=args.output or ROOT/'outputs'/f'{args.target}-source-{args.revision}.zip'
    if target.suffix!='.zip' or (target.exists() and not args.reuse_existing):raise ValueError('An unused ZIP target is required.')
    names=subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0')
    excluded={'.apk','.aab','.idsig','.pyc'}
    files=[ROOT/n for n in names if n and (ROOT/n).is_file() and Path(n).suffix not in excluded]
    for path in files:
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT):raise ValueError('Source path escapes repository')
    payload={path.relative_to(ROOT).as_posix():path.read_bytes() for path in files}
    modes={path.relative_to(ROOT).as_posix():path.stat().st_mode&0o777 for path in files}
    result=write_verified_zip(payload,target,modes,reuse_existing=args.reuse_existing)
    print(json.dumps({**result,'target_path_sha256':hashlib.sha256(str(target).encode()).hexdigest()}))


if __name__=='__main__':main()
