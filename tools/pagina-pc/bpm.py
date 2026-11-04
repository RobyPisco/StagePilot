"""Stima del BPM da un'anteprima audio (circa 30 secondi), senza numpy.

Serve il componente opzionale `miniaudio` (`pip install miniaudio`), che sa leggere l'mp3. Il resto è
Python puro: si misura l'energia a piccole finestre, si prende dove cresce di colpo (gli attacchi) e si
cerca ogni quanto si ripete (autocorrelazione). Il risultato si riporta fra 70 e 160 BPM: un brano lento
può uscire al doppio (63 → 126), ed è per questo che nell'app resta il ×… per correggere.
"""
import math
import urllib.request
from operator import mul

SR = 22050
HOP = 128
WIN = 512


def estimate(mp3_bytes):
    import miniaudio
    dec = miniaudio.decode(mp3_bytes, output_format=miniaudio.SampleFormat.SIGNED16, nchannels=1, sample_rate=SR)
    s = dec.samples
    n = (len(s) - WIN) // HOP
    if n < 400:
        return None
    energy = [sum(map(mul, s[i * HOP:i * HOP + WIN], s[i * HOP:i * HOP + WIN])) for i in range(n)]
    lg = [math.log1p(e) for e in energy]
    onset = [max(0.0, lg[i] - lg[i - 1]) for i in range(1, len(lg))]
    mean = sum(onset) / len(onset)
    o = [x - mean for x in onset]
    N = len(o)
    var = sum(x * x for x in o) or 1.0
    fps = SR / HOP
    cache = {}

    def ac(lag):
        if lag not in cache:
            cache[lag] = sum(map(mul, o[:N - lag], o[lag:])) / var if 0 < lag < N else 0.0
        return cache[lag]

    lo, hi = int(fps * 60 / 210), int(fps * 60 / 50) + 1
    best_lag, best = None, -1e9
    for lag in range(lo, hi):
        # un tempo vero si ripete anche a 2, 3 e 4 volte la distanza: si sommano i picchi
        score = ac(lag) + 0.6 * ac(2 * lag) + 0.4 * ac(3 * lag) + 0.3 * ac(4 * lag)
        if score > best:
            best, best_lag = score, lag
    if best_lag is None:
        return None

    def refine(center):
        window = range(max(2, center - 3), center + 4)
        return max(window, key=ac)

    # si affina guardando dove cade il picco ai multipli: la precisione sale con la distanza
    estimates = []
    for k in (1, 2, 3, 4):
        peak = refine(k * best_lag)
        a, b, c = ac(peak - 1), ac(peak), ac(peak + 1)
        denom = a - 2 * b + c
        shift = 0.5 * (a - c) / denom if denom else 0.0
        estimates.append((peak + max(-0.5, min(0.5, shift))) / k)
    lag = sum(estimates) / len(estimates)
    bpm = 60.0 * fps / lag
    while bpm < 70:
        bpm *= 2
    while bpm >= 160:
        bpm /= 2
    return int(round(bpm))


def estimate_from_url(url, timeout=15):
    req = urllib.request.Request(url, headers={"User-Agent": "BacklinePC/1 (uso personale)"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = r.read(3_000_000)
    return estimate(data)
