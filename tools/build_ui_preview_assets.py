"""Generate only the preview UI overlays; operative assets are never rewritten."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def generate(output):
    output = Path(output).resolve()
    main = ROOT / 'android/app/src/main/assets'
    if output == main or main in output.parents:
        raise ValueError('Preview overlays must never overwrite operative assets')
    output.mkdir(parents=True, exist_ok=True)
    html = (main / 'mobile.html').read_text()
    if html.count('</head>') != 1:
        raise ValueError('Expected one original HTML head')
    html = html.replace('</head>', '<link rel="stylesheet" href="preview-ui.css">\n'
                        '<script src="preview-ui.js" defer></script>\n</head>')
    (output / 'mobile.html').write_text(html)
    # Existing photo safety allowlists must refer exclusively to this package.
    for name in ('design-concept.js', 'customer-hub.js', 'workflow-ui.js'):
        source = (main / name).read_text()
        source = source.replace(r'at\.hausgarten\.service\.photothumbnails',
                                r'at\.hausgarten\.service\.preview\.photothumbnails')
        (output / name).write_text(source)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    generate(parser.parse_args().output)
