"""Central synthetic profiles and disposable host adapter. No production inputs."""
import json
import tempfile
from copy import deepcopy
from pathlib import Path

import runtime_paths

FIXTURES = Path(__file__).parent / 'fixtures/synthetic-business.json'


def profile(name='minimal'):
    specification = json.loads(FIXTURES.read_text())
    if name not in specification['profiles']:
        raise ValueError('Unknown synthetic profile')
    result = deepcopy(specification['minimal'])
    if name == 'edge_cases':
        result['customers'][0]['name'] = 'QA Musterkunde ÄÖÜ < & >'
        result['customers'][0]['custom_fields'] = {'qa_multiline': 'QA\nMehrzeilig'}
    if name == 'scale':
        base = result['worklogs'][0]
        result['worklogs'] = [dict(base, id=f'qa_work_{i:05d}') for i in range(specification['scale_rows'])]
    return result


def configure_db(root):
    """Register a disposable store already selected by an isolated test case."""
    root = Path(root).resolve()
    temp = Path(tempfile.gettempdir()).resolve()
    while root.parent != temp and temp in root.parents:
        root = root.parent
    return runtime_paths.register_synthetic_root(root)


class temporary_directory:
    """Drop-in central disposable-store factory, independent of private env."""
    def __init__(self, *args, **kwargs):
        self._temp = tempfile.TemporaryDirectory(*args, **kwargs)
        self.name = str(configure_db(self._temp.name))
    def __enter__(self):return self.name
    def __exit__(self,*error):self.cleanup()
    def cleanup(self):self._temp.cleanup()


class SyntheticStore:
    def __enter__(self):
        import db
        self.temporary = tempfile.TemporaryDirectory(prefix='qa_hausgarten_')
        self.root = configure_db(self.temporary.name)
        self.previous = db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = self.root, self.root/'qa.db', self.root/'photos', self.root/'backups'
        db.init()
        return self

    def __exit__(self, *error):
        import db
        db.DATA_DIR, db.DB_PATH, db.PHOTO_DIR, db.BACKUP_DIR = self.previous
        self.temporary.cleanup()
