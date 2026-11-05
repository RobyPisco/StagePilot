"""BPM e durata da songbpm.com.

Il sito non ha un'API: la ricerca è un modulo (POST su /searches) e la pagina dei risultati elenca, per ogni brano,
artista, titolo, tonalità, durata e BPM. Basta quindi una richiesta per ricerca. Qui si legge quella pagina e si
sceglie il risultato giusto; la tonalità non si usa: nell'elenco manca il modo (maggiore/minore) e deriva dalla
registrazione, non dallo spartito.

Se il sito non risponde o cambia struttura, `lookup` ritorna None e identifica.py ripiega su Deezer.
"""
import html
import re
import urllib.error
import urllib.parse
import urllib.request

import identifica

URL = "https://songbpm.com/searches"
UA = "Mozilla/5.0 (compatible; BacklinePC/1; uso personale)"
CARD = re.compile(r'<a class="[^"]*" href="(/@[^"]+)">(.*?)</a>', re.S)

_CACHE = {}


def _text(s):
    return html.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", s))).strip()


def parse_results(page):
    """[{artist, title, key, dur, bpm, url}] nell'ordine del sito."""
    out = []
    for m in CARD.finditer(page or ""):
        body = m.group(2)
        ps = [_text(x) for x in re.findall(r"<p[^>]*>(.*?)</p>", body, re.S)]
        spans = [_text(x) for x in re.findall(r"<span[^>]*>(.*?)</span>", body, re.S)]
        if len(ps) < 2:
            continue
        vals = {k: v for k, v in zip(spans[0::2], spans[1::2])}
        try:
            bpm = int(vals.get("BPM", ""))
        except ValueError:
            bpm = None
        out.append({"artist": ps[0], "title": ps[1], "key": vals.get("Key", ""),
                    "dur": vals.get("Duration", ""), "bpm": bpm, "url": m.group(1)})
    return out


def secs(mmss):
    m = re.fullmatch(r"(\d+):(\d{2})", mmss or "")
    return int(m.group(1)) * 60 + int(m.group(2)) if m else 0


def squash(s):
    return re.sub(r"[^a-z0-9]", "", (s or "").lower().replace("!", "i"))     # "P!nk" e "Pink"


def pick(results, title, artist, ref_seconds=None):
    """Il risultato dello stesso brano e artista. Fra più versioni (originale, edit, remaster) vince quella la cui
    durata è più vicina a quella di riferimento; con le versioni live o remix solo se non c'è altro."""
    want = identifica.norm_title(title)
    same = [r for r in results
            if r["bpm"] and identifica.norm_title(r["title"]) == want and squash(r["artist"]) == squash(artist)]
    same = [r for r in same if not identifica.LIVE.search(r["title"].split(" - ", 1)[-1] if " - " in r["title"] else "")] or same
    if not same:
        return None
    if ref_seconds:
        same.sort(key=lambda r: abs(secs(r["dur"]) - ref_seconds) if secs(r["dur"]) else 999)
    return same[0]


def search(title, artist, timeout=12):
    q = f"{title} {artist}".strip()
    if q in _CACHE:
        return _CACHE[q]
    data = urllib.parse.urlencode({"query": q}).encode()
    req = urllib.request.Request(URL, data=data, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:      # il POST rimanda alla pagina dei risultati
        res = parse_results(r.read().decode("utf-8", "replace"))
    _CACHE[q] = res
    return res


def lookup(title, artist, ref_seconds=None, log=print):
    """{bpm, dur, title, artist} oppure None."""
    try:
        results = search(title, artist)
    except (urllib.error.URLError, OSError, ValueError) as e:
        log("  songbpm non risponde: %s" % e)
        return None
    best = pick(results, title, artist, ref_seconds)
    log(f"  songbpm: {len(results)} risultati, scelto: {best and (best['title'], best['bpm'], best['dur'])}")
    return best
