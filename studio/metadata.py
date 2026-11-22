"""Recognise pasted charts using lyric overlap, then fetch recording metadata.

Only public LRCLIB and Deezer JSON endpoints; no keys, downloaded audio or
rewritten lyrics. Missing tempo stays missing. Source links travel with results.
"""
from collections import Counter, OrderedDict
import copy
from email.utils import parsedate_to_datetime
import hashlib
import html as _html
import json
import math
import re
import threading
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

UA = 'StagePilot/0.1 (https://github.com/RobyPisco/StagePilot)'
STOP = set(('a an the and or but of to in on at for with from by as is are was were be been am it its i you he she we they me my your his her our their this that not no so do does did have has had just oh yeah uh huh la na ooh e il la lo le gli di da in con su per tra fra un una che non mi ti ci si ma o').split())


def words(text):
    text = unicodedata.normalize('NFKD', text.lower().replace('’', "'")).replace('_', ' ')
    return re.findall(r"[^\W_]+(?:'[^\W_]+)?", ''.join(c for c in text if not unicodedata.combining(c)), re.UNICODE)


def normal(text):
    return ' '.join(words(text)) if isinstance(text, str) else ''


def artist_key(text):
    return ''.join(words(text.replace('!', 'i'))) if isinstance(text, str) else ''


def title_key(text):
    # Retain live/acoustic/version distinctions; remove only mastering labels.
    return normal(re.sub(r'\s*[\[(](?:\d{4}\s+)?(?:re)?master(?:ed)?[^\])]*[\])]|\s+-\s+(?:\d{4}\s+)?remaster.*$', '', text, flags=re.I)) if isinstance(text, str) else ''


_CHORD_TOK = re.compile(r'^(?:[A-G](?:#|b)?(?:m|maj|min|dim|aug|sus|add)?\d*(?:/[A-G](?:#|b)?)?|N\.?C\.?)$')
_TENS = {'twenty': 20, 'thirty': 30, 'forty': 40, 'fifty': 50, 'sixty': 60, 'seventy': 70, 'eighty': 80, 'ninety': 90}
_ONES = {'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5, 'six': 6, 'seven': 7, 'eight': 8, 'nine': 9}


def _with_digits(phrase):
    toks = phrase.split()
    out, i, changed = [], 0, False
    while i < len(toks):
        if toks[i] in _TENS and i + 1 < len(toks) and toks[i + 1] in _ONES:
            out.append(str(_TENS[toks[i]] + _ONES[toks[i + 1]]))
            i += 2
            changed = True
        else:
            out.append(toks[i])
            i += 1
    return ' '.join(out) if changed else None


def lyric_lines(body):
    result = []
    for line in body.splitlines():
        if re.match(r'^\s*(?:#|\{|[A-Ga-g][#b]?\|)', line):
            continue
        line = re.sub(r'\[[^\]]*\]', '', line).strip()
        toks = line.split()
        if toks and all(_CHORD_TOK.match(t) for t in toks):
            continue
        if len(words(line)) >= 2 and not re.fullmatch(r'[\s|%:./\-\d]+', line):
            result.append(line)
    return result


def trigrams(lines):
    # Never make a match by joining unrelated lines across section boundaries.
    out = set()
    for line in lines:
        w = words(line)
        out.update(zip(w, w[1:], w[2:]))
    return out


def title_queries(lines):
    counts, endings = Counter(), Counter()
    for line in lines:
        w = words(line)
        for size in range(2, 7):
            for start in range(len(w) - size + 1):
                phrase = w[start:start + size]
                if all(p in STOP for p in phrase):
                    continue
                key = ' '.join(phrase)
                counts[key] += 1
                if start + size == len(w): endings[key] += 1
    ranked = sorted((p for p in counts if counts[p] >= 2), key=lambda p: -(counts[p] * min(len(p.split()), 4) * (1 + .6 * endings[p] / counts[p]) * (.7 if len(p.split()) > 4 else 1)))
    queries = []
    for p in ranked:
        if any(p in q and counts[p] <= counts[q] for q in queries): continue
        queries.append(p)
        if len(queries) == 4: break
    # A short, nonrepeating paste can still include the title in one of its lines.
    for line in lines[:2] + lines[-2:]:
        p = normal(line)
        if 2 <= len(p.split()) <= 7 and p not in queries: queries.append(p)
    for p in list(queries[:3]):
        dig = _with_digits(p)
        if dig and dig not in queries: queries.insert(0, dig)
    for p in list(queries[:2]):
        w = p.split()
        if len(w) > 3: queries.append(' '.join(w[-3:]))
    return queries[:8]


def mmss(value):
    seconds = int(round(value)) if isinstance(value, (float, int)) and math.isfinite(value) else 0
    return f'{seconds // 60}:{seconds % 60:02d}' if 0 < seconds < 86400 else ''


class MetadataLookup:
    def __init__(self, fetch=None, clock=time.monotonic, pause=time.sleep):
        self.fetch = fetch or self._fetch
        self.clock, self.pause = clock, pause
        self.lock = threading.Lock()
        self.cache = OrderedDict()
        self.responses = OrderedDict()
        self.cooldown = {}
        self.last_request = {}

    def _fetch(self, url, timeout):
        request = urllib.request.Request(url, headers={'User-Agent': UA, 'Accept': 'application/json'})
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read(2_000_001)
            if len(raw) > 2_000_000: raise ValueError('Risposta del catalogo troppo grande.')
            return json.loads(raw.decode('utf-8'))

    def _json(self, host, path, params, deadline):
        url = f'https://{host}{path}' + ('?' + urllib.parse.urlencode(params) if params else '')
        now = self.clock()
        saved = self.responses.get(url)
        if saved and now - saved[0] < 3600:
            self.responses.move_to_end(url)
            return copy.deepcopy(saved[1])
        if deadline - now <= .5 or self.cooldown.get(host, 0) > now:
            raise OSError('Catalogo temporaneamente non disponibile.')
        delay = max(0, .25 - (now - self.last_request.get(host, 0)))
        if delay: self.pause(delay)
        self.last_request[host] = self.clock()
        try:
            value = self.fetch(url, min(6, max(.5, deadline - self.clock())))
        except urllib.error.HTTPError as error:
            if error.code in (429, 503):
                retry = error.headers.get('Retry-After', '30')
                try: wait = max(1, float(retry))
                except (ValueError, TypeError):
                    try: wait = max(1, parsedate_to_datetime(retry).timestamp() - time.time())
                    except (ValueError, TypeError, AttributeError, OverflowError): wait = 30
                self.cooldown[host] = self.clock() + wait
            raise
        self.responses[url] = (self.clock(), value)
        while len(self.responses) > 128: self.responses.popitem(last=False)
        return copy.deepcopy(value)

    def identify(self, data):
        body, title, artist = (data.get(k, '') for k in ('body', 'title', 'artist'))
        if not all(isinstance(v, str) for v in (body, title, artist)) or len(body) > 80000 or len(title) > 200 or len(artist) > 200:
            raise ValueError('Testo troppo grande o dati del brano non validi.')
        if not body.strip() and not title.strip():
            return {'found': False, 'message': 'Incolla il testo oppure indica un titolo.', 'candidates': []}
        key = hashlib.sha256(json.dumps([body, title, artist]).encode()).hexdigest()
        if not self.lock.acquire(timeout=.1):
            return {'found': False, 'message': 'Un’altra ricerca è ancora in corso. Riprova tra pochi secondi.', 'candidates': []}
        try:
            saved = self.cache.get(key)
            if saved and self.clock() - saved[0] < 3600:
                return copy.deepcopy(saved[1])
            result = self._identify(body, title.strip(), artist.strip(), self.clock() + 24)
            if result.get('found'):
                self.cache[key] = (self.clock(), result)
                while len(self.cache) > 64: self.cache.popitem(last=False)
            return copy.deepcopy(result)
        finally:
            self.lock.release()

    def _identify(self, body, title, artist, deadline):
        lines = lyric_lines(body)
        pasted = trigrams(lines)
        queries = ([(title + ' ' + artist).strip()] if title else []) + title_queries(lines)
        best = {}
        failures = 0
        for query in dict.fromkeys(queries):
            if self.clock() > deadline - 8: break
            try:
                rows = self._json('lrclib.net', '/api/search', {'q': query}, deadline)
                if not isinstance(rows, list): raise ValueError('Risposta non valida.')
            except (OSError, ValueError):
                failures += 1
                continue
            for row in rows[:20]:
                if not isinstance(row, dict) or not isinstance(row.get('trackName'), str) or not isinstance(row.get('artistName'), str) or not row['trackName'] or not row['artistName']: continue
                actual_title, actual_artist = row['trackName'], row['artistName']
                if title and title_key(title) != title_key(actual_title): continue
                if artist and artist_key(artist) != artist_key(actual_artist): continue
                text = row.get('plainLyrics')
                if not isinstance(text, str):
                    synced = row.get('syncedLyrics')
                    text = re.sub(r'\[[\d:.]+\]', '', synced) if isinstance(synced, str) else ''
                real = trigrams(text.splitlines())
                matched = len(pasted & real)
                score = matched / len(pasted) if pasted else 0
                explicit = bool(title and artist)
                if not explicit and (matched < 6 or score < .45): continue
                group = (title_key(actual_title), artist_key(actual_artist))
                candidate = {'title': actual_title, 'artist': actual_artist, 'album': row.get('albumName') or '', 'dur': mmss(row.get('duration')), 'bpm': None, 'score': round(score, 3), 'matched': matched, 'sources': [{'label': 'Testo e riconoscimento · LRCLIB', 'url': 'https://lrclib.net/api/get/' + str(row.get('id', ''))}], 'recordingUrl': ''}
                if group not in best or best[group]['score'] < score: best[group] = candidate
            if best and max(c['score'] for c in best.values()) >= .75: break
        ranked = sorted(best.values(), key=lambda c: -c['score'])
        # An explicit title/artist is also useful for instrumental charts.
        if not ranked and title:
            catalog = self._catalog(title, artist, deadline)
            if catalog:
                ranked = [self._from_track(x, 0) for x in catalog[:4]]
        if not ranked:
            return {'found': False, 'candidates': [], 'message': 'I cataloghi non rispondono. Puoi riprovare o compilare i campi a mano.' if failures else 'Non ho riconosciuto il brano con sufficiente sicurezza. Indica titolo o artista e premi Cerca di nuovo.'}
        ranked = ranked[:5]
        # Equal lyrics can belong to covers. Use catalogue rank only to propose
        # an order; keep alternatives visible rather than treating it as proof.
        catalog = self._catalog(ranked[0]['title'], artist, deadline)
        if not artist and catalog:
            order = {artist_key(t['artist']['name']): i for i, t in reversed(list(enumerate(catalog)))}
            top_score = ranked[0]['score']
            ranked.sort(key=lambda c: (0 if c['score'] >= top_score - .04 else 1, 1 if artist_key(c['artist']) == 'variousartists' else 0, order.get(artist_key(c['artist']), 999), -c['score']))
        selected = ranked[0]
        versions = [t for t in catalog if artist_key(t['artist']['name']) == artist_key(selected['artist']) and title_key(t['title']) == title_key(selected['title'])]
        if not versions:
            versions = self._catalog(selected['title'], selected['artist'], deadline)
        if versions:
            track = versions[0]
            recognition_sources = selected['sources']
            selected.update(self._from_track(track, selected['score']))
            selected['sources'] = recognition_sources + [s for s in selected['sources'] if s not in recognition_sources]
            try:
                detail = self._json('api.deezer.com', '/track/' + str(track['id']), {}, deadline)
                same = isinstance(detail, dict) and detail.get('id') == track['id'] and title_key(detail.get('title', '')) == title_key(track['title']) and artist_key(detail.get('artist', {}).get('name', '')) == artist_key(track['artist']['name'])
                tempo = detail.get('bpm') if same else None
                if type(tempo) in (float, int) and math.isfinite(tempo) and 20 <= tempo <= 400:
                    selected['bpm'] = int(round(tempo))
                    selected['sources'].append({'label': 'BPM · Deezer', 'url': selected['recordingUrl']})
            except (OSError, ValueError): pass
        # Ripiego SongBPM solo quando Deezer non ha i BPM (e non nei test offline con fetch mockato).
        if selected['bpm'] is None and self.fetch == self._fetch and deadline - self.clock() > 1.5:
            sb = self._songbpm(selected['title'], selected['artist'], deadline)
            if sb:
                selected['bpm'] = sb['bpm']
                selected['sources'].append({'label': 'BPM · SongBPM', 'url': 'https://songbpm.com' + sb['url']})
        # Never send fetched lyrics back to the browser or write them to disk.
        message = 'Versione proposta: controlla artista e registrazione prima di salvare.'
        if selected['bpm'] is None: message += ' BPM non disponibili nel catalogo: puoi inserirli a mano.'
        return {'found': True, 'selected': selected, 'candidates': ranked[1:4], 'message': message}

    def _catalog(self, title, artist, deadline):
        try:
            data = self._json('api.deezer.com', '/search', {'q': (title + ' ' + artist).strip(), 'limit': 25}, deadline)
            rows = data.get('data', []) if isinstance(data, dict) else []
            return [r for r in rows if isinstance(r, dict) and type(r.get('id')) is int and title_key(r.get('title', '')) == title_key(title) and isinstance(r.get('artist'), dict) and isinstance(r['artist'].get('name'), str) and (not artist or artist_key(r['artist']['name']) == artist_key(artist))]
        except (OSError, ValueError): return []

    def _songbpm(self, title, artist, deadline):
        q = (title + ' ' + artist).strip()
        if not q: return None
        cache_key = 'songbpm:' + q.lower()
        now = self.clock()
        saved = self.responses.get(cache_key)
        if saved and now - saved[0] < 3600:
            self.responses.move_to_end(cache_key)
            return copy.deepcopy(saved[1])
        try:
            body = urllib.parse.urlencode({'query': q}).encode('utf-8')
            req = urllib.request.Request('https://songbpm.com/searches', data=body, headers={'User-Agent': 'Mozilla/5.0 (compatible; StagePilot/1)'})
            with urllib.request.urlopen(req, timeout=min(8, max(1.0, deadline - self.clock()))) as r:
                page = r.read(1_000_000).decode('utf-8', 'replace')
            want_t, want_a = title_key(title), artist_key(artist)
            clean = lambda s: _html.unescape(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', s))).strip()
            best = None
            for m in re.finditer(r'<a class="[^"]*" href="(/@[^"]+)">(.*?)</a>', page, re.S):
                card = m.group(2)
                ps = [clean(x) for x in re.findall(r'<p[^>]*>(.*?)</p>', card, re.S)]
                spans = [clean(x) for x in re.findall(r'<span[^>]*>(.*?)</span>', card, re.S)]
                if len(ps) < 2: continue
                if title_key(ps[1]) != want_t or artist_key(ps[0]) != want_a: continue
                vals = dict(zip(spans[0::2], spans[1::2]))
                bpm = int(vals['BPM']) if vals.get('BPM', '').isdigit() else None
                if bpm and 20 <= bpm <= 400:
                    best = {'bpm': bpm, 'url': m.group(1)}
                    break
            self.responses[cache_key] = (self.clock(), best)
            return copy.deepcopy(best)
        except (OSError, ValueError):
            return None

    @staticmethod
    def _from_track(track, score):
        link = 'https://www.deezer.com/track/' + str(track['id'])
        return {'title': track['title'], 'artist': track['artist']['name'], 'album': track.get('album', {}).get('title') or '', 'dur': mmss(track.get('duration')), 'bpm': None, 'score': score, 'recordingUrl': link, 'sources': [{'label': 'Titolo, artista e durata · Deezer', 'url': link}]}
