"""Pagina del PC per preparare i brani di Backline e mandarli al telefono.

Si avvia con `python server.py` (o con avvia.cmd) e apre http://localhost:8765.
Solo libreria standard.

Due modi per far arrivare un brano al telefono:
  - Wi-Fi: la pagina mette il brano in una coda sul PC; l'app Backline, aprendosi (o dal pulsante
    in Impostazioni), la scarica con l'indirizzo e il codice che la pagina mostra. Serve che telefono
    e PC siano sulla stessa rete. La coda è protetta dal codice.
  - Cavo: la pagina manda il brano con adb e Backline lo propone subito.
"""
import json
import os
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import uuid
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
PORT = int(os.environ.get("BACKLINE_PORT", "8765"))
STATE_FILE = HERE / "stato.json"          # codice e coda: restano fra un avvio e l'altro
STATIC = {"/": "index.html", "/index.html": "index.html", "/convert.bundle.js": "convert.bundle.js"}
LOCK = threading.Lock()


# ── stato: codice e coda ────────────────────────────────────────────────────
def load_state():
    try:
        s = json.loads(STATE_FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        s = {}
    s.setdefault("code", "".join(secrets.choice("0123456789") for _ in range(4)))
    s.setdefault("queue", [])
    return s


def save_state(s):
    STATE_FILE.write_text(json.dumps(s, ensure_ascii=False, indent=1), encoding="utf-8")


STATE = load_state()
save_state(STATE)


def lan_ip():
    """L'indirizzo del PC nella rete di casa (quello che il telefono deve raggiungere)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))          # non manda niente: serve solo a scegliere l'interfaccia
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


# ── adb ─────────────────────────────────────────────────────────────────────
def find_adb():
    found = shutil.which("adb")
    if found:
        return found
    local = os.environ.get("LOCALAPPDATA", "")
    for p in (Path(local) / "Android" / "Sdk" / "platform-tools" / "adb.exe",):
        if p.exists():
            return str(p)
    return None


def adb_devices():
    adb = find_adb()
    if not adb:
        return None
    try:
        out = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=8).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    return [ln.split()[0] for ln in out.splitlines()[1:] if ln.strip().endswith("device")]


def adb_send(package):
    """Manda il pacchetto di brani a Backline sul dispositivo collegato. Ritorna (ok, messaggio)."""
    adb = find_adb()
    if not adb:
        return False, "adb non trovato: serve per l'invio via cavo (usa il Wi-Fi, oppure installa platform-tools)."
    devs = adb_devices()
    if not devs:
        return False, "Nessun dispositivo collegato con adb. Collega il telefono col cavo e consenti il debug USB."
    tmp = Path(tempfile.gettempdir()) / "backline-brani.json"
    tmp.write_text(json.dumps(package, ensure_ascii=False), encoding="utf-8")
    try:
        subprocess.run([adb, "push", str(tmp), "/sdcard/Download/backline-brani.json"],
                       capture_output=True, text=True, timeout=30, check=True)
        # il testo passa da un file sul dispositivo: niente limiti di riga di comando né problemi di virgolette
        cmd = ('am start -n it.backline.app/.MainActivity -a android.intent.action.SEND -t text/plain '
               '--es android.intent.extra.TEXT "$(cat /sdcard/Download/backline-brani.json)"')
        subprocess.run([adb, "shell", cmd], capture_output=True, text=True, timeout=30, check=True)
    except subprocess.CalledProcessError as e:
        return False, "adb ha risposto con un errore: " + (e.stderr or e.stdout or str(e)).strip()
    except (OSError, subprocess.SubprocessError) as e:
        return False, "adb non risponde: " + str(e)
    return True, "Inviato: guarda il telefono."


# ── server ──────────────────────────────────────────────────────────────────
def clean_song(raw):
    """Prende solo i campi che l'app conosce, con i tipi giusti."""
    def s(v):
        return v.strip() if isinstance(v, str) else ""

    def i(v, lo, hi):
        try:
            return max(lo, min(hi, int(v)))
        except (TypeError, ValueError):
            return 0

    bpm = None
    try:
        b = int(raw.get("bpm"))
        bpm = b if 20 <= b <= 400 else None
    except (TypeError, ValueError):
        pass
    notes = {k: s(v) for k, v in (raw.get("notes") or {}).items() if isinstance(v, str) and v.strip()}
    tags = [s(t).lower() for t in (raw.get("tags") or []) if s(t)]
    return {
        "title": s(raw.get("title")),
        "artist": s(raw.get("artist")),
        "key": s(raw.get("key")),
        "bpm": bpm,
        "dur": s(raw.get("dur")),
        "capo": i(raw.get("capo"), 0, 11),
        "lead": i(raw.get("lead"), 0, 60),
        "tags": list(dict.fromkeys(tags)),
        "notes": notes,
        "body": raw.get("body") if isinstance(raw.get("body"), str) else "",
        "inSet": bool(raw.get("inSet", True)),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "BacklinePC/1"

    def log_message(self, fmt, *args):          # silenzio: la finestra resta pulita
        pass

    # --- utilità
    def local(self):
        return self.client_address[0] in ("127.0.0.1", "::1")

    def code_ok(self):
        sent = self.headers.get("X-Code", "")
        return secrets.compare_digest(sent, STATE["code"])

    def send_json(self, obj, status=200):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def read_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n > 2_000_000:
            return None
        try:
            return json.loads(self.rfile.read(n).decode("utf-8") or "{}")
        except ValueError:
            return None

    # --- GET
    def do_GET(self):
        path = self.path.split("?")[0]
        if path in STATIC:
            if not self.local():
                return self.send_json({"error": "solo dal PC"}, 403)
            f = HERE / STATIC[path]
            data = f.read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8" if f.suffix == ".html" else "text/javascript; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(data)
            return
        if path == "/api/status" and self.local():
            with LOCK:
                q = [{"id": e["id"], "title": e["song"]["title"], "artist": e["song"]["artist"]} for e in STATE["queue"]]
            return self.send_json({"ip": lan_ip(), "port": PORT, "code": STATE["code"], "queue": q, "adb": adb_devices()})
        if path == "/api/inbox":                  # il telefono: coda dei brani, con il codice
            if not self.code_ok():
                return self.send_json({"error": "codice errato"}, 403)
            with LOCK:
                return self.send_json({"songs": [dict(e["song"], id=e["id"]) for e in STATE["queue"]]})
        self.send_json({"error": "non trovato"}, 404)

    # --- POST
    def do_POST(self):
        path = self.path.split("?")[0]
        data = self.read_json()
        if data is None:
            return self.send_json({"error": "richiesta non valida"}, 400)

        if path == "/api/inbox/ack":              # il telefono: ha preso questi brani
            if not self.code_ok():
                return self.send_json({"error": "codice errato"}, 403)
            ids = set(data.get("ids") or [])
            with LOCK:
                STATE["queue"] = [e for e in STATE["queue"] if e["id"] not in ids]
                save_state(STATE)
            return self.send_json({"ok": True})

        if not self.local():
            return self.send_json({"error": "solo dal PC"}, 403)

        songs = [clean_song(s) for s in (data.get("songs") or []) if isinstance(s, dict)]
        songs = [s for s in songs if s["title"] and s["body"].strip()]

        if path == "/api/queue":
            if not songs:
                return self.send_json({"error": "servono almeno un titolo e un testo"}, 400)
            with LOCK:
                for s in songs:
                    STATE["queue"].append({"id": uuid.uuid4().hex[:10], "song": s, "at": time.time()})
                save_state(STATE)
            return self.send_json({"ok": True, "queued": len(songs)})

        if path == "/api/queue/remove":
            with LOCK:
                STATE["queue"] = [e for e in STATE["queue"] if e["id"] != data.get("id")]
                save_state(STATE)
            return self.send_json({"ok": True})

        if path == "/api/adb-send":
            if not songs:
                return self.send_json({"error": "servono almeno un titolo e un testo"}, 400)
            ok, msg = adb_send({"backline": "songs", "v": 1, "songs": songs})
            return self.send_json({"ok": ok, "message": msg}, 200 if ok else 502)

        self.send_json({"error": "non trovato"}, 404)


def main():
    try:
        httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    except OSError as e:
        sys.exit(f"Non riesco ad aprire la porta {PORT} (forse è già in uso): {e}")
    ip = lan_ip()
    print(f"Backline — pagina del PC su http://localhost:{PORT}")
    print(f"Sul telefono (Impostazioni → Brani dal PC): indirizzo {ip}:{PORT}   codice {STATE['code']}")
    print("Lascia questa finestra aperta finché usi la pagina. Ctrl+C per chiudere.")
    if "--no-browser" not in sys.argv:
        threading.Timer(0.6, lambda: webbrowser.open(f"http://localhost:{PORT}")).start()
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
