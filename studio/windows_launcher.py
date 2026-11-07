"""Portable Windows entry point; user data lives outside the executable."""
import os
import sys
from pathlib import Path
import server


def main():
    if not any(a == '--data-dir' or a.startswith('--data-dir=') for a in sys.argv[1:]):
        directory = Path(os.environ.get('LOCALAPPDATA', Path.home() / 'AppData' / 'Local')) / 'StagePilot' / 'Studio'
        sys.argv.extend(['--data-dir', str(directory)])
    print('StagePilot Studio 0.2.1 - Windows', flush=True)
    print('Si apre nel browser. Chiudi questa finestra per terminare.', flush=True)
    server.main()


if __name__ == '__main__':
    main()
