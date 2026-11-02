"""Generate thumbnail authority overlays for the separate full-function test app."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def generate(output):
    package = 'at.hausgarten.service.fulltest'
    output = Path(output).resolve()
    main = ROOT / 'app/src/main/assets'
    if output == main or main in output.parents:
        raise ValueError('Test overlays must never overwrite operative assets')
    output.mkdir(parents=True, exist_ok=True)
    html = (main / 'mobile.html').read_text()
    # Only the isolated test package may serve its local thumbnail images.
    html = html.replace('content://at.hausgarten.service.photothumbnails',
                        'content://' + package + '.photothumbnails')
    if html.count('</head>') != 1:
        raise ValueError('Expected one original HTML head')
    (output / 'mobile.html').write_text(html)
    # Existing photo safety allowlists must refer exclusively to this package.
    for name in ('design-concept.js', 'customer-hub.js', 'workflow-ui.js'):
        source = (main / name).read_text()
        source = source.replace(r'at\.hausgarten\.service\.photothumbnails',
                                package.replace('.', r'\.') + r'\.photothumbnails')
        (output / name).write_text(source)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    generate(args.output)
