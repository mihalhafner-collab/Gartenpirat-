"""Restrict host test fixtures to disposable directories outside the repository."""
from __future__ import annotations

import os
import tempfile
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parent
_synthetic_roots: set[Path] = set()


class PrivateDataPathError(ValueError):
    pass


def external(path) -> Path:
    p = Path(path).expanduser()
    if not p.is_absolute():
        raise PrivateDataPathError('Ein absoluter externer Datenpfad ist erforderlich.')
    lexical=Path(os.path.abspath(p))
    if lexical == REPOSITORY or REPOSITORY in lexical.parents:
        raise PrivateDataPathError('Auch ein Repository-Symlink ist kein privater Datenroot.')
    p = p.resolve()
    if p == REPOSITORY or REPOSITORY in p.parents or p in REPOSITORY.parents:
        raise PrivateDataPathError('Betriebsdaten im Repository oder dessen übergeordnetem Root sind gesperrt.')
    return p


def register_synthetic_root(path) -> Path:
    """Only a newly owned disposable OS-temp store may opt in, never a checkout."""
    root = external(path)
    temp = Path(tempfile.gettempdir()).resolve()
    if root == temp or temp not in root.parents:
        raise PrivateDataPathError('Synthetische Stores benötigen einen eigenen OS-Tempordner.')
    root.mkdir(parents=True, exist_ok=True)
    marker = root / '.hg-synthetic-store'
    expected = b'hausgarten.synthetic.v1\n'
    if marker.exists() and marker.read_bytes() != expected:
        raise PrivateDataPathError('Unbekannter Store; keine automatische Testumwidmung.')
    if not marker.exists():
        if any(root.iterdir()):
            raise PrivateDataPathError('Nichtleere fremde Ordner werden nicht als Teststore umgewidmet.')
        marker.write_bytes(expected)
    _synthetic_roots.add(root)
    return root


def synthetic_root(path) -> Path | None:
    p = external(path)
    for root in sorted(_synthetic_roots, key=lambda x: len(x.parts), reverse=True):
        if (p == root or root in p.parents) and (root / '.hg-synthetic-store').is_file():
            return root
    return None
