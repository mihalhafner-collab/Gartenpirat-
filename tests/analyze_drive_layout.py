"""Read-only inventory: output counts/filenames, not business contents or credentials."""
import json
import sys
from pathlib import Path

def analyze(folder):
    root=Path(folder)
    seen={};overlaps=0;conflicts=0;files=[]
    for path in sorted(root.glob('HausGarten-Mobile-*.json')):
        package=json.loads(path.read_text(encoding='utf-8-sig'))
        actions=package.get('actions',[])
        files.append({'file':path.name,'actions':len(actions),'bytes':path.stat().st_size})
        for action in actions:
            identifier=action.get('client_id')
            value=json.dumps(action,sort_keys=True,ensure_ascii=False)
            if identifier in seen:
                overlaps+=1
                conflicts+=int(seen[identifier]!=value)
            seen[identifier]=value
    return {'mode':'read_only','files':files,'unique_action_ids':len(seen),'overlapping_ids':overlaps,'conflicting_ids':conflicts}

if __name__=='__main__':print(json.dumps(analyze(sys.argv[1]),ensure_ascii=False))
