"""Read public historical plant sources from a pinned Git object, never live assets."""
import os
from pathlib import Path, PurePosixPath
import re
import subprocess

BASELINE_COMMIT = 'e499e745832449ce0202d04d9903495248e8b034'
ROOT = Path(__file__).resolve().parents[2]


def baseline_bytes(name, *, root=ROOT, commit=BASELINE_COMMIT):
    path = PurePosixPath(name)
    if (not re.fullmatch(r'[0-9a-f]{40}', commit) or path.is_absolute()
            or '..' in path.parts or '\\' in name or str(path) != name):
        raise ValueError('Invalid historical baseline reference')
    env = {k: v for k, v in os.environ.items() if not k.startswith('HG_ORIGINAL_')}
    result = subprocess.run(['git', 'show', commit + ':' + name], cwd=root, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise ValueError('Pinned historical source is unavailable')
    return result.stdout
