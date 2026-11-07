"""Exercise the packaged EXE without Python on PATH or any personal data."""
from pathlib import Path
import json
import os
import socket
import subprocess
import tempfile
import time
import urllib.request


def main():
    root = Path(__file__).resolve().parents[1]
    exe = root / 'dist/windows/StagePilot-Studio.exe'
    with tempfile.TemporaryDirectory(prefix='stagepilot-exe-test-') as directory:
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        base = f'http://127.0.0.1:{port}'
        env = {**os.environ, 'PATH': '', 'LOCALAPPDATA': directory}

        def start():
            proc = subprocess.Popen([str(exe), '--port', str(port), '--no-browser'],
                                    env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                    creationflags=subprocess.CREATE_NO_WINDOW)
            for _ in range(100):
                if proc.poll() is not None:
                    raise RuntimeError('EXE stopped before opening its server')
                try:
                    with urllib.request.urlopen(base + '/api/state', timeout=1) as response:
                        return proc, json.load(response)
                except OSError:
                    time.sleep(.2)
            stop(proc)
            raise RuntimeError('EXE startup timed out')

        def stop(proc):
            subprocess.run(['taskkill', '/PID', str(proc.pid), '/T', '/F'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True)
            proc.wait(timeout=10)

        proc, state = start()
        try:
            assert state['db']['bands'][0]['songs'] == [], 'Distribution contains personal songs'
            for asset in ['/', '/guida.html', '/stagepilot.ico']:
                with urllib.request.urlopen(base + asset) as response:
                    assert response.status == 200
                    if asset == '/guida.html':
                        assert 'Windows-x64.exe' in response.read().decode('utf-8')
            state['db']['bands'][0]['name'] = 'EXE persistence test'
            request = urllib.request.Request(base + '/api/state', data=json.dumps(state).encode(),
                                             headers={'Content-Type': 'application/json'})
            with urllib.request.urlopen(request) as response:
                assert response.status == 200
        finally:
            stop(proc)
        assert (Path(directory) / 'StagePilot/Studio/stagepilot.sqlite').is_file()
        proc, state = start()
        try:
            assert state['db']['bands'][0]['name'] == 'EXE persistence test'
        finally:
            stop(proc)
    print('Windows EXE: standalone startup, assets, default data directory and persistence OK')


if __name__ == '__main__':
    main()
