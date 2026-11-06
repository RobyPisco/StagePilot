"""SQLite persistence and deterministic three-way synchronization; standard library only."""
import copy
import json
import sqlite3
import threading
import time
from pathlib import Path

MISSING = object()
ENTITY_LISTS = {'bands', 'songs', 'sets', 'events', 'rehearsals'}

def validate_db(db):
    if not isinstance(db, dict) or not isinstance(db.get('bands'), list) or not db['bands']:
        raise ValueError('Il file deve contenere almeno un gruppo.')
    def ids(records):
        if not isinstance(records, list) or any(not isinstance(x, dict) or not isinstance(x.get('id'), str) or not x['id'] for x in records):
            raise ValueError('Record o identificativo non valido.')
        keys = [x['id'] for x in records]
        if len(keys) != len(set(keys)): raise ValueError('Identificativi duplicati.')
    ids(db['bands'])
    for band in db['bands']:
        for name in ('songs', 'sets', 'events', 'rehearsals'): ids(band.get(name, []))
        have = {s['id'] for s in band.get('songs', [])}
        for song in band.get('songs', []):
            if not isinstance(song.get('title', ''), str) or not isinstance(song.get('body', ''), str): raise ValueError('Titolo o testo non valido.')
            if song.get('bpm') not in (None, '') and (type(song['bpm']) is not int or not 20 <= song['bpm'] <= 400): raise ValueError('BPM fuori intervallo (20–400).')
            if song.get('status', 'study') not in ('study', 'rehearse', 'ready'): raise ValueError('Stato del brano non valido.')
        for st in band.get('sets', []):
            if not isinstance(st.get('items', []), list): raise ValueError('Scaletta non valida.')
            for item in st.get('items', []):
                if not isinstance(item, dict) or item.get('type') not in ('song', 'mark'): raise ValueError('Voce di scaletta non valida.')
                if item['type'] == 'song' and item.get('id') not in have: raise ValueError('La scaletta contiene un brano inesistente.')
    return db

def shared_view(db):
    result = copy.deepcopy(db)
    result.pop('opt', None)
    result.pop('curBand', None)
    for band in result.get('bands', []):
        band.pop('me', None)
        band.pop('cur', None)
        for s in band.get('songs', []):
            for k in ('tr', 'scroll'): s.pop(k, None)
    return result

def personal_view(shared, personal):
    db = copy.deepcopy(shared)
    db['opt'] = copy.deepcopy(personal.get('opt', {}))
    bands = {b['id']: b for b in personal.get('bands', [])}
    ids = {b['id'] for b in db.get('bands', [])}
    cur = personal.get('curBand', '')
    db['curBand'] = cur if cur in ids else next(iter(ids), '')
    for b in db.get('bands', []):
        prev = bands.get(b['id'], {})
        b['me'] = prev.get('me', 'Tutti')
        sets = b.get('sets', [])
        b['cur'] = prev.get('cur') if any(s['id'] == prev.get('cur') for s in sets) else (sets[0]['id'] if sets else '')
        songs = {s['id']: s for s in prev.get('songs', [])}
        for s in b.get('songs', []):
            old = songs.get(s['id'], {})
            s['tr'], s['scroll'] = old.get('tr', 0), old.get('scroll', 1)
    return db

def merge_libraries(base, device, pc, policy=None):
    """Independent fields merge. Same-field changes and delete/edit conflicts need an explicit choice."""
    conflicts = []
    def merge(b, l, r, path):
        if l == r: return copy.deepcopy(l) if l is not MISSING else MISSING
        if l == b: return copy.deepcopy(r) if r is not MISSING else MISSING
        if r == b: return copy.deepcopy(l) if l is not MISSING else MISSING
        if isinstance(l, dict) and isinstance(r, dict) and (isinstance(b, dict) or b is MISSING):
            old = b if isinstance(b, dict) else {}
            out = {}
            for k in sorted(set(old) | set(l) | set(r)):
                value = merge(old.get(k, MISSING), l.get(k, MISSING), r.get(k, MISSING), path + '/' + k)
                if value is not MISSING: out[k] = value
            return out
        if isinstance(l, list) and isinstance(r, list) and path.split('/')[-1] in ENTITY_LISTS:
            old = {x['id']: x for x in b} if isinstance(b, list) else {}
            left, right = {x['id']: x for x in l}, {x['id']: x for x in r}
            out = []
            for key in dict.fromkeys([x['id'] for x in r] + [x['id'] for x in l] + list(old)):
                value = merge(old.get(key, MISSING), left.get(key, MISSING), right.get(key, MISSING), path + '/' + key)
                if value is not MISSING: out.append(value)
            return out
        conflicts.append(path)
        chosen = l if policy == 'device' else r
        return copy.deepcopy(chosen) if chosen is not MISSING else MISSING
    shared = merge(shared_view(base) if base else MISSING, shared_view(device), shared_view(pc), '')
    # A deletion wins over dangling references, independently of arrival order.
    for band in shared.get('bands', []):
        have = {s['id'] for s in band.get('songs', [])}
        for st in band.get('sets', []): st['items'] = [i for i in st.get('items', []) if i.get('type') == 'mark' or i.get('id') in have]
    return personal_view(shared, device), personal_view(shared, pc), conflicts

class Library:
    def __init__(self, path, seed):
        self.path = Path(path)
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.lock = threading.RLock()
        with self.connection() as c:
            c.execute('PRAGMA journal_mode=WAL')
            c.execute('CREATE TABLE IF NOT EXISTS state (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL, data TEXT NOT NULL)')
            c.execute('CREATE TABLE IF NOT EXISTS history (revision INTEGER PRIMARY KEY, saved_at INTEGER NOT NULL, data TEXT NOT NULL)')
            c.execute('CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
            c.execute('INSERT OR IGNORE INTO state VALUES(1,1,?)', (json.dumps(validate_db(seed), ensure_ascii=False),))
    def connection(self):
        c = sqlite3.connect(self.path, timeout=15)
        c.execute('PRAGMA foreign_keys=ON')
        return c
    def get(self):
        with self.lock, self.connection() as c:
            revision, raw = c.execute('SELECT revision,data FROM state WHERE id=1').fetchone()
            return {'revision': revision, 'db': json.loads(raw)}
    def save(self, db, expected):
        validate_db(db)
        with self.lock, self.connection() as c:
            c.execute('BEGIN IMMEDIATE')
            rev, raw = c.execute('SELECT revision,data FROM state WHERE id=1').fetchone()
            if rev != expected: raise ValueError('La libreria è cambiata. Ricarica prima di salvare.')
            if json.loads(raw) == db: return {'revision': rev, 'db': db}
            c.execute('INSERT INTO history VALUES(?,?,?)', (rev, int(time.time()*1000), raw))
            c.execute('UPDATE state SET revision=?,data=? WHERE id=1', (rev+1, json.dumps(db, ensure_ascii=False)))
            c.execute('DELETE FROM history WHERE revision NOT IN (SELECT revision FROM history ORDER BY revision DESC LIMIT 40)')
            return {'revision': rev+1, 'db': db}
    def sync(self, base, device, policy=None):
        validate_db(device)
        if base is not None: validate_db(base)
        if policy not in (None, 'device', 'pc'): raise ValueError('Scelta del conflitto non valida.')
        with self.lock:
            current = self.get()
            merged, canonical, conflicts = merge_libraries(base, device, current['db'], policy)
            if conflicts and policy is None: return {'conflicts': conflicts}, 409
            saved = self.save(canonical, current['revision'])
            return {'db': merged, 'revision': saved['revision'], 'conflicts': []}, 200
    def undo(self, expected):
        with self.lock, self.connection() as c:
            row = c.execute('SELECT data FROM history ORDER BY revision DESC LIMIT 1').fetchone()
            if row is None: raise ValueError('Non ci sono modifiche da annullare.')
            return self.save(json.loads(row[0]), expected)
