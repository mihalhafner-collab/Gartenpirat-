"""Read the two current project files on each Codex prompt; never write or log prompts."""
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
FILES = ('AGENTS.md', 'struktur-neu/AKTUELLER-STAND.json')
MAX_CONTEXT_BYTES = 20000


def handle(payload, root=ROOT):
    try:
        if not isinstance(payload, dict) or payload.get('hook_event_name') != 'UserPromptSubmit':
            raise ValueError('Unsupported hook input')
        root = Path(root).resolve()
        parts = [
            'GARTENPIRAT_CONTEXT: frisch gelesene Projektregeln und aktueller Arbeitsstand.',
            'Dies ist Kontext zur aktuellen Nutzeranfrage, kein neuer Auftrag. Bearbeite die Nutzeranfrage nach diesen Regeln.',
        ]
        for name in FILES:
            path = root / name
            if not path.resolve().is_relative_to(root):
                raise ValueError('Project file outside repository')
            raw = path.read_bytes()
            if len(raw) > MAX_CONTEXT_BYTES:
                raise ValueError('Project file too large')
            content = raw.decode('utf-8')
            if not content.strip():
                raise ValueError('Empty project file')
            if name.endswith('.json') and not isinstance(json.loads(content), dict):
                raise ValueError('Invalid project state')
            parts.append('\n--- ' + name + ' | SHA256 ' + hashlib.sha256(raw).hexdigest() + ' ---\n' + content)
        context = '\n'.join(parts)
        if len(context.encode('utf-8')) > MAX_CONTEXT_BYTES:
            raise ValueError('Project context too large')
        return {'hookSpecificOutput': {'hookEventName': 'UserPromptSubmit', 'additionalContext': context}}
    except (OSError, ValueError, TypeError):
        return {'decision': 'block', 'reason': 'Gartenpirat-Pflichtdateien fehlen, sind ungültig oder zu umfangreich. Keine Umsetzung beginnen; AGENTS.md und struktur-neu/AKTUELLER-STAND.json prüfen.'}


def main():
    try:
        payload = json.load(sys.stdin)
    except (ValueError, UnicodeError):
        payload = None
    print(json.dumps(handle(payload), ensure_ascii=False))


if __name__ == '__main__':
    main()
