"""Build a standalone Windows x64 executable (run npm run build first)."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
if not (root / 'web/dist/index.html').is_file():
    raise SystemExit('Compile the web app first: cd web && npm ci && npm run build')
subprocess.run([
    sys.executable, '-m', 'PyInstaller', '--noconfirm', '--clean', '--onefile', '--console',
    '--name', 'StagePilot-Studio', '--icon', str(root/'web/public/stagepilot.ico'),
    '--paths', str(root/'studio'),
    '--add-data', str(root/'web/dist') + ';web/dist',
    '--add-data', str(root/'THIRD_PARTY_NOTICES.md') + ';.',
    '--add-data', str(root/'LICENSE') + ';.',
    '--add-data', str(root/'docs/Python-LICENSE.txt') + ';docs',
    '--add-data', str(root/'docs/PyInstaller-COPYING.txt') + ';docs',
    '--add-data', str(root/'docs/NanoHTTPD-LICENSE.txt') + ';docs',
    '--distpath', str(root/'dist/windows'), '--workpath', str(root/'build/windows'),
    '--specpath', str(root/'build/windows'), str(root/'studio/windows_launcher.py')
], check=True, cwd=root)
