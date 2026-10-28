"""Fail-closed host paths. Pixel/Drive remains the operative adapter.

No runtime path is resolved and no directory is created at import time. Tests
register disposable OS-temp roots explicitly; they never select the private root.
"""
from __future__ import annotations

import os
import tempfile
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parent
_synthetic_roots: set[Path] = set()
_synthetic_only = False


def synthetic_only():
    """QA cannot fall through to a configured owner store, even on test failure."""
    global _synthetic_only
    _synthetic_only = True


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


def private_root() -> Path:
    configured = os.environ.get('HG_PRIVATE_DATA_ROOT', '').strip()
    if not configured:
        raise PrivateDataPathError('HG_PRIVATE_DATA_ROOT fehlt; kein Betriebsdatenzugriff.')
    root=external(configured)
    if root == Path(tempfile.gettempdir()).resolve() or not root.is_dir():
        raise PrivateDataPathError('Ein vorhandener eigener privater Datenroot ist erforderlich.')
    if os.name == 'posix' and root.stat().st_mode & 0o077:
        raise PrivateDataPathError('Privater Datenroot benötigt ausschließlich Eigentümerrechte (0700).')
    return root


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


def business_path(path) -> Path:
    p = external(os.fspath(path))
    if synthetic_root(p) is not None:
        return p
    if _synthetic_only:
        raise PrivateDataPathError('Automatisierte QA darf keinen privaten Datastore öffnen.')
    root = private_root()
    if p != root and root not in p.parents:
        raise PrivateDataPathError('Betriebsdatenpfad liegt außerhalb HG_PRIVATE_DATA_ROOT.')
    return p


class PrivatePath(os.PathLike):
    """Lazy compatibility adapter for the existing host repository contract."""
    def __init__(self, relative):
        self.relative = relative

    def resolve(self):
        return business_path(private_root() / self.relative)

    def __fspath__(self):
        return str(self.resolve())

    def __str__(self):
        return self.__fspath__()

    def __truediv__(self, other):
        return business_path(self.resolve() / other)

    def __getattr__(self, name):
        return getattr(self.resolve(), name)


def checked_paths(*paths):
    return tuple(business_path(p) for p in paths)


def staging_directory(source):
    """Short-lived private backup processing never enters a source/test output."""
    source = business_path(source)
    owner = synthetic_root(source) or private_root()
    staging = business_path(owner / '.staging')
    staging.mkdir(mode=0o700, exist_ok=True)
    return tempfile.TemporaryDirectory(prefix='private_processing_', dir=staging)
