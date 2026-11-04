"""Riconoscere una canzone dal testo incollato, e recuperarne durata e BPM.

1. Si cercano su LRCLIB le frasi più ricorrenti del testo (di solito una è il titolo).
2. Ogni risultato ha il testo completo: si confronta con quello incollato (sequenze di tre parole in comune)
   e vince chi ne spiega di più. Così non basta il titolo giusto: deve essere proprio quella canzone.
3. Da Deezer si prendono la durata della versione in studio e, se c'è, il BPM.
4. Se Deezer non ha il BPM, e il componente opzionale `miniaudio` è installato, lo si stima dall'anteprima
   audio di 30 secondi (bpm.py).

Tutto con la libreria standard, tranne l'ultimo passo. Se la rete non c'è, ritorna senza niente e la pagina
lascia i campi da compilare a mano.
"""
import concurrent.futures as cf
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "BacklinePC/1 (uso personale)"
LIVE = re.compile(r"\b(live|remix|karaoke|acoustic|instrumental|demo|medley|cover|tribute|edit|mix|version)\b", re.I)


def get_json(url, timeout=12, retries=2):
    """GET con qualche ritentativo: i servizi gratuiti rifiutano ogni tanto le richieste ravvicinate (429)."""
    last = None
    for attempt in range(retries + 1):
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            last = e
            if e.code not in (429, 500, 502, 503, 504):
                raise
        except (urllib.error.URLError, OSError) as e:
            last = e
        time.sleep(1.2 * (attempt + 1))
    raise last


def words(s):
    s = (s or "").lower().replace("’", "'").replace("‘", "'")
    return re.findall(r"[a-zà-ÿ0-9']+", s)


def trigrams(w):
    return set(zip(w, w[1:], w[2:]))


def norm_title(t):
    t = re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", t or "")
    t = re.sub(r"\s+-\s+.*$", "", t)                      # "Titolo - Remastered 2011"
    return " ".join(words(t))


def overlap(pasted_tri, real_text):
    """Quanta parte del testo incollato si ritrova nel testo vero (0–1)."""
    if not pasted_tri:
        return 0.0
    return len(pasted_tri & trigrams(words(real_text))) / len(pasted_tri)


_CACHE = {}


def lrclib_search(q):
    if q in _CACHE:
        return _CACHE[q]
    try:
        res = get_json("https://lrclib.net/api/search?q=" + urllib.parse.quote(q))
    except (urllib.error.URLError, OSError, ValueError):
        return []                                          # non si memorizza: la prossima volta si riprova
    _CACHE[q] = res
    return res


def with_shorter_variants(guesses, top=3, per=2):
    """Il titolo spesso è la coda di una frase più lunga ("have become comfortably numb" → "comfortably numb")."""
    out = []
    for g in guesses:
        if g not in out:
            out.append(g)
    for g in guesses[:top]:
        w = g.split()
        for cut in range(1, per + 1):
            if len(w) - cut >= 2:
                v = " ".join(w[cut:])
                if v not in out:
                    out.append(v)
    return out


def find_candidates(lyrics, guesses, title="", artist="", log=print):
    """I brani di LRCLIB che spiegano il testo incollato, dal più probabile."""
    pasted = trigrams(words(" ".join(lyrics)))
    queries = []
    if title:
        queries.append((title + " " + artist).strip())
    for g in with_shorter_variants(guesses[:6]):
        if g and g not in queries:
            queries.append(g)
    queries = queries[:11]
    with cf.ThreadPoolExecutor(max_workers=3) as ex:
        results = list(ex.map(lrclib_search, queries))
    best = {}
    for q, items in zip(queries, results):
        for it in items or []:
            text = it.get("plainLyrics") or ""
            if not text or it.get("instrumental"):
                continue
            key = (norm_title(it.get("trackName")), norm_title(it.get("artistName")))
            s = overlap(pasted, text)
            cur = best.get(key)
            if cur is None or s > cur["score"]:
                best[key] = {
                    "title": (it.get("trackName") or "").strip(),
                    "artist": (it.get("artistName") or "").strip(),
                    "duration": it.get("duration"),
                    "score": round(s, 3),
                    "query": q,
                }
    ranked = sorted(best.values(), key=lambda c: -c["score"])
    log(f"  {len(queries)} ricerche, {len(best)} brani confrontati")
    return prefer_the_famous(ranked, log)


def prefer_the_famous(ranked, log=print):
    """Cover e originale hanno lo stesso testo: a parità si sceglie l'artista più in alto nelle ricerche di Deezer
    (che ordina per popolarità), senza mai scavalcare chi ha un testo chiaramente più vicino."""
    if len(ranked) < 2:
        return ranked
    top = ranked[0]
    near = [c for c in ranked if c["score"] >= top["score"] - 0.05 and norm_title(c["title"]) == norm_title(top["title"])]
    if len(near) < 2:
        return ranked
    try:
        data = get_json("https://api.deezer.com/search?q=" + urllib.parse.quote(top["title"]))["data"]
    except (urllib.error.URLError, OSError, ValueError, KeyError):
        return ranked
    order = []
    for x in data:
        if norm_title(x.get("title")) == norm_title(top["title"]):
            order.append(" ".join(words(x["artist"]["name"])))

    def rank(c):
        a = " ".join(words(c["artist"]))
        for i, o in enumerate(order):
            if a and (a in o or o in a):
                return i
        return 99
    pick = min(near, key=rank)
    if rank(pick) == 99 or pick is top:
        return ranked
    log(f"  a parità di testo scelgo l'artista più noto: {pick['artist']}")
    return [pick] + [c for c in ranked if c is not pick]


def deezer_enrich(title, artist, lrclib_duration=None, log=print):
    """Durata e BPM della versione in studio. Ritorna dict con duration (s), bpm (float|None), preview (url|None)."""
    out = {"duration": None, "bpm": None, "preview": None, "deezer": None}
    try:
        data = get_json("https://api.deezer.com/search?q=" + urllib.parse.quote(f"{title} {artist}"))["data"]
    except (urllib.error.URLError, OSError, ValueError, KeyError):
        return out
    want = norm_title(title)

    def squash(s):                                  # "P!nk" e "Pink" non coincidono: si confronta senza punteggiatura
        return re.sub(r"[^a-z0-9]", "", (s or "").lower())

    def same_title(x):
        return norm_title(x.get("title")) == want

    def not_live(x):
        return not LIVE.search(x.get("title", ""))

    # prima l'artista identico (non "Celtic Pink Floyd" per "Pink Floyd"), poi, se non c'è, che lo contenga
    exact = [x for x in data if same_title(x) and squash(x["artist"]["name"]) == squash(artist)]
    loose = [x for x in data if same_title(x) and (words(artist) or [""])[0] in " ".join(words(x["artist"]["name"]))]
    pool = [x for x in exact if not_live(x)] or exact or [x for x in loose if not_live(x)] or loose
    if not pool:
        return out
    ref = lrclib_duration or pool[0].get("duration") or 0
    pool.sort(key=lambda x: abs((x.get("duration") or 0) - ref))
    top = pool[0]
    out.update(duration=top.get("duration"), preview=top.get("preview"), deezer=top.get("id"))
    # il BPM c'è solo su alcune tracce: si guardano le versioni in studio più vicine
    for x in pool[:6]:
        try:
            t = get_json("https://api.deezer.com/track/%s" % x["id"])
        except (urllib.error.URLError, OSError, ValueError):
            continue
        if t.get("bpm"):
            out["bpm"] = round(float(t["bpm"]), 1)
            out["bpm_source"] = "deezer"
            break
    log(f"  deezer: durata={out['duration']} bpm={out['bpm']}")
    return out


def mmss(seconds):
    s = int(round(seconds or 0))
    return f"{s // 60}:{s % 60:02d}" if s > 0 else ""


def identify(lyrics, guesses, title="", artist="", want_bpm_from_audio=True, log=print):
    """Ritorna {found, title, artist, dur, bpm, bpm_source, score, alternatives:[…]} — found=False se non è sicuro."""
    cands = find_candidates(lyrics, guesses, title, artist, log)
    if not cands or cands[0]["score"] < 0.30:
        return {"found": False, "alternatives": cands[:4], "tried": guesses[:7]}
    best = cands[0]
    extra = deezer_enrich(best["title"], best["artist"], best.get("duration"), log)
    res = {
        "found": True,
        "title": best["title"], "artist": best["artist"],
        "dur": mmss(extra["duration"] or best.get("duration")),
        "bpm": extra["bpm"], "bpm_source": extra.get("bpm_source"),
        "score": best["score"],
        "alternatives": [{"title": c["title"], "artist": c["artist"], "score": c["score"]} for c in cands[1:4]],
    }
    if res["bpm"] is None and want_bpm_from_audio and extra.get("preview"):
        try:
            import bpm as bpm_mod
            est = bpm_mod.estimate_from_url(extra["preview"])
            if est:
                res["bpm"], res["bpm_source"] = est, "audio"
        except ImportError:
            res["bpm_hint"] = "manca"           # il componente opzionale non c'è: la pagina lo propone
        except Exception as e:                  # rete, formato… non deve rompere il resto
            log("  stima del BPM non riuscita: %s" % e)
    return res
