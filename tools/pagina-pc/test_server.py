"""Prova di server.py: coda con doppioni, scarico, storico, backup. Usa una cartella di stato temporanea."""
import json, os, shutil, subprocess, sys, tempfile, time, urllib.request, urllib.error

SRC = os.path.dirname(os.path.abspath(__file__))
tmp = tempfile.mkdtemp()
for f in ("server.py", "identifica.py", "bpm.py", "songbpm.py"):
    shutil.copy(os.path.join(SRC, f), tmp)
port = "8791"
env = dict(os.environ, BACKLINE_PORT=port)
proc = subprocess.Popen([sys.executable, os.path.join(tmp, "server.py"), "--no-browser"], env=env,
                        stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
base = "http://127.0.0.1:" + port
time.sleep(1.5)


def call(path, data=None, code=None, raw=None):
    req = urllib.request.Request(base + path, data=raw if raw is not None else (json.dumps(data).encode() if data is not None else None),
                                 method="POST" if (data is not None or raw is not None) else "GET")
    if code:
        req.add_header("X-Code", code)
    try:
        with urllib.request.urlopen(req) as r:
            return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


try:
    st, status = call("/api/status")
    code = status["code"]
    song = {"title": "Who Knew", "artist": "P!nk", "body": "[Am]x"}
    assert call("/api/queue", {"songs": [song, dict(song, title="WHO KNEW!"), {"title": "Altro", "body": "[C]y"}]})[1] == \
        {"ok": True, "queued": 2, "skipped": ["WHO KNEW!"]}, "dedupe in coda"
    assert call("/api/queue", {"songs": [song]})[1]["queued"] == 0, "doppione di uno gia' in coda"
    st, inbox = call("/api/inbox", code=code)
    assert st == 200 and len(inbox["songs"]) == 2
    assert call("/api/inbox", code="0000")[0] == 403, "senza codice no"
    ids = [x["id"] for x in inbox["songs"]]
    assert call("/api/inbox/ack", {"ids": ids[:1]}, code=code)[0] == 200
    status = call("/api/status")[1]
    assert [e["title"] for e in status["sent"]] == ["Who Knew"] and len(status["queue"]) == 1, status
    # backup
    assert call("/api/backup", raw=b"{}", code="0000")[0] == 403
    assert call("/api/backup", raw=b"non json", code=code)[0] == 400
    assert call("/api/backup", raw=b'{"x":1}', code=code)[0] == 400
    db = {"v": 4, "curBand": "b1", "bands": [{"id": "b1", "songs": [{"title": "Who Knew", "artist": "P!nk"}, {"title": "Èa", "artist": ""}]}]}
    st, r = call("/api/backup", raw=json.dumps(db).encode(), code=code)
    assert st == 200 and r["file"].startswith("backline-"), r
    status = call("/api/status")[1]
    assert status["library"] == ["whoknew|pnk", "ea|"], status["library"]
    assert status["backup"] is not None
    # solo gli ultimi 10
    for _ in range(12):
        time.sleep(1.01)
        call("/api/backup", raw=json.dumps(db).encode(), code=code)
    assert len(os.listdir(os.path.join(tmp, "backup"))) == 10
    print("server OK")
finally:
    proc.kill()
    shutil.rmtree(tmp, ignore_errors=True)
