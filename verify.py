"""External read-only integrity audit; no seed, migration or historical totals."""
import json
import sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/'tests'))
from audit_readonly import audit
import runtime_paths

if __name__=='__main__':
    print(json.dumps(audit(runtime_paths.private_root()/'data/haus-garten.db')))
