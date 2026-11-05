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
    # repertorio dal backup
    db2 = {"v": 4, "curBand": "b1", "bands": [{"id": "b1", "name": "Mia", "cur": "t1",
           "songs": [{"id": "s1", "title": "Uno", "artist": "A", "key": "Am", "bpm": 100, "dur": "3:00", "tags": [], "body": "[Am]x", "tr": 2, "scroll": 2.0},
                     {"id": "s2", "title": "Due", "artist": "B", "body": "[C]y"}],
           "sets": [{"id": "t1", "name": "Serata", "items": [{"type": "song", "id": "s1"}, {"type": "mark", "label": "Pausa", "dur": "5:00"}]}]}]}
    assert call("/api/backup", raw=json.dumps(db2).encode(), code=code)[0] == 200
    lib = call("/api/library")[1]
    assert lib["band"] == {"id": "b1", "name": "Mia"} and [x["id"] for x in lib["songs"]] == ["s1", "s2"], lib
    assert "tr" not in lib["songs"][0] and lib["sets"][0]["items"][1]["label"] == "Pausa", lib
    # modifiche: validazione, accorpamento, consegna e ack
    assert call("/api/edits", {"edits": [{"op": "boh", "id": "s1"}, {"op": "song"}]})[0] == 400
    st, r = call("/api/edits", {"edits": [
        {"op": "song", "id": "s1", "band": "b1", "title": "Uno bis", "bpm": 9999, "tags": ["Cover", "cover"], "inutile": 1},
        {"op": "song", "id": "s1", "band": "b1", "key": "Do", "title": ""},
        {"op": "items", "id": "t1", "band": "b1", "base": ["song:s1"], "items": [{"type": "mark", "label": "Pausa", "dur": "5:00"}, {"type": "song", "id": "s1"}, {"type": "boh"}]},
        {"op": "delete", "id": "s2", "band": "b1", "title": "Due"}]})
    assert st == 200 and r["queued"] == 4, r
    inbox = call("/api/inbox", code=code)[1]
    songs_ed = [e for e in inbox["edits"] if e["op"] == "song"]
    assert len(songs_ed) == 1 and songs_ed[0]["title"] == "Uno bis" and songs_ed[0]["key"] == "Do" and songs_ed[0]["bpm"] == 0, inbox
    assert songs_ed[0]["tags"] == ["cover"] and "inutile" not in songs_ed[0]
    its = [e for e in inbox["edits"] if e["op"] == "items"][0]
    assert [i["type"] for i in its["items"]] == ["mark", "song"] and its["base"] == ["song:s1"], its
    assert len(inbox["editIds"]) == 3 and call("/api/status")[1]["edits"][0]["op"] in ("song", "items", "delete")
    # togliere un brano cancella le correzioni in sospeso, e una nuova sostituisce la vecchia scaletta
    call("/api/edits", {"edits": [{"op": "delete", "id": "s1", "title": "Uno"}, {"op": "items", "id": "t1", "items": []}]})
    ops = [(e["op"], e["id"]) for e in call("/api/inbox", code=code)[1]["edits"]]
    assert sorted(ops) == [("delete", "s1"), ("delete", "s2"), ("items", "t1")], ops
    assert call("/api/inbox")[0] == 403
    call("/api/inbox/ack", {"ids": call("/api/inbox", code=code)[1]["editIds"]}, code=code)
    assert call("/api/inbox", code=code)[1]["edits"] == []
    # solo gli ultimi 10
    for _ in range(12):
        time.sleep(1.01)
        call("/api/backup", raw=json.dumps(db).encode(), code=code)
    assert len(os.listdir(os.path.join(tmp, "backup"))) == 10
    print("server OK")
finally:
    proc.kill()
    shutil.rmtree(tmp, ignore_errors=True)
