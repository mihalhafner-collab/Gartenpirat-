#!/usr/bin/env python3
"""Read-only source handoff guard. No keys, writes, installs or Git mutations."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = 'mihalhafner-collab/Gartenpirat-'
SHA = re.compile(r'[a-f0-9]{40}')


class SourcePreservationError(ValueError):
    """Fixed diagnostics only: never include Git stderr, credentials or paths."""


def git(root, *arguments):
    try:
        result = subprocess.run(['git', *arguments], cwd=root, stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                text=True, check=False, timeout=25,
                                env={key: value for key, value in os.environ.items()
                                     if not key.startswith('HG_ORIGINAL_')})
    except (OSError, subprocess.SubprocessError):
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Git verification unavailable.') from None
    if result.returncode:
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Git verification failed.')
    return result.stdout.strip()


def expected_repository(url):
    if url.startswith('git@github.com:'):
        path = url[len('git@github.com:'):]
    else:
        try:
            parsed = urlsplit(url)
            if (parsed.hostname != 'github.com' or parsed.scheme not in ('https', 'ssh')
                    or parsed.query or parsed.fragment
                    or parsed.port not in (None, 443 if parsed.scheme == 'https' else 22)):
                return False
            path = parsed.path.lstrip('/')
        except ValueError:
            return False
    path = path.removesuffix('.git')
    return path.casefold() == REPOSITORY.casefold()


def verify_preserved_source(root=ROOT):
    root = Path(root).resolve()
    if Path(git(root, 'rev-parse', '--show-toplevel')).resolve() != root:
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Expected repository root required.')
    head = git(root, 'rev-parse', 'HEAD')
    if not SHA.fullmatch(head):
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Source commit unavailable.')
    if git(root, 'status', '--porcelain', '--untracked-files=all'):
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Commit and push working changes first.')
    if git(root, 'ls-files', '--others', '--ignored', '--exclude-standard', '--',
           'android/app/src/main', 'android/app/build.gradle'):
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Unversioned Android build inputs found.')
    if not expected_repository(git(root, 'config', '--get', 'remote.origin.url')):
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Expected private GitHub repository required.')
    refs = []
    for line in git(root, 'ls-remote', '--heads', '--tags', 'origin').splitlines():
        fields = line.split()
        if (len(fields) == 2 and fields[0] == head
                and fields[1].startswith(('refs/heads/', 'refs/tags/'))):
            refs.append(fields[1])
    if not refs:
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Exact source commit is not confirmed on GitHub.')
    # Do not attest to a checkout changed during the remote read.
    if (git(root, 'rev-parse', 'HEAD') != head
            or git(root, 'status', '--porcelain', '--untracked-files=all')
            or git(root, 'ls-files', '--others', '--ignored', '--exclude-standard', '--',
                   'android/app/src/main', 'android/app/build.gradle')):
        raise SourcePreservationError('SOURCE_NOT_PRESERVED: Checkout changed during verification.')
    return {'status': 'PASS', 'git_sha': head, 'remote_refs': sorted(refs),
            'repository': REPOSITORY, 'scope': 'clean_checkout_and_remote_commit_only'}


def main():
    try:
        print(json.dumps(verify_preserved_source(), sort_keys=True))
        return 0
    except SourcePreservationError as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
