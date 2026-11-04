/* Pulizia di un tab incollato da Ultimate Guitar e simili, e ricerca di indizi sul brano.
   Gira nel browser (la pagina del PC) e in node (prova.js). Dipende da `BL` (convert.bundle.js). */
(function (root) {
  'use strict';

  // ── riconoscere le righe che non servono ─────────────────────────────────
  var TAB1 = /^\s*[A-Ga-g][#b]?\|[-\dhpbrsx\/\\~^()|.*\s]*$/;          // e|---0---2---|
  var TAB2 = /^\s*\|[-\dhpbrsx\/\\~^()|.*\s]{10,}$/;                     // |---0---2---|
  function isTabLine(l) {
    return (TAB1.test(l) || TAB2.test(l)) && l.indexOf('-') >= 0;
  }
  var HEADER = /^\s*(Tuning|Key|Capo)\s*:/i;
  var JUNK = [
    /^\s*Last update\s*:/i,
    /^\s*(Chords|Tabs?|Transcribed|Tabbed)\s+by\b/i,
    /^\s*X\s*$/,                                  // il "chiudi" che resta copiando dalla pagina
    /^\s*(Rating|Difficulty|Views|Favorites)\b/i
  ];
  var SECTION = /^\s*\[([^\[\]]+)\]\s*$/;

  /** Toglie dal testo grezzo tutto quello che non è testo e accordi. Non converte ancora in ChordPro.
   *  Ritorna { text, meta:{key,capo,title,artist}, removed:{tab,header,junk,emptySections} }. */
  function cleanRaw(raw, opt) {
    opt = opt || {};
    var dropTab = opt.dropTab !== false;
    var removed = { tab: 0, header: 0, junk: 0, emptySections: 0 };
    var meta = {};
    var keep = [];

    String(raw || '').replace(/\r\n?/g, '\n').split('\n').forEach(function (l) {
      if (HEADER.test(l)) {
        var k = /Key:\s*([A-G][#b]?m?)/.exec(l); if (k && !meta.key) meta.key = k[1];
        var c = /Capo:\s*(\d{1,2})/.exec(l); if (c && !meta.capo) meta.capo = c[1];
        removed.header++; return;
      }
      var d = /^\s*\{\s*(title|t|artist|subtitle|st|key|capo)\s*:\s*([^}]*)\}\s*$/i.exec(l);
      if (d) {
        var n = d[1].toLowerCase(), v = d[2].trim();
        if ((n === 'title' || n === 't') && !meta.title) meta.title = v;
        else if ((n === 'artist' || n === 'subtitle' || n === 'st') && !meta.artist) meta.artist = v;
        else if (n === 'key' && !meta.key) meta.key = v;
        else if (n === 'capo' && !meta.capo) meta.capo = v;
        removed.header++; return;
      }
      for (var i = 0; i < JUNK.length; i++) if (JUNK[i].test(l)) { removed.junk++; return; }
      if (dropTab && isTabLine(l)) { removed.tab++; return; }
      keep.push(l.replace(/\s+$/, ''));
    });

    // sezioni rimaste vuote (erano solo tablatura): via l'intestazione
    var out = [];
    for (var j = 0; j < keep.length; j++) {
      if (SECTION.test(keep[j])) {
        var k2 = j + 1;
        while (k2 < keep.length && !keep[k2].trim()) k2++;
        if (k2 >= keep.length || SECTION.test(keep[k2])) { removed.emptySections++; continue; }
      }
      out.push(keep[j]);
    }
    return { text: out.join('\n').replace(/\n{3,}/g, '\n\n').trim(), meta: meta, removed: removed };
  }

  /** Il ChordPro finale dalla pulizia + conversione dell'app web. Toglie anche le sezioni rimaste senza niente. */
  function toChordPro(raw, opt) {
    var c = cleanRaw(raw, opt);
    var body = c.text ? BL.normalizeBody(BL.tabToChordPro(c.text)) : '';
    // dopo la conversione una sezione "# X" seguita solo da un'altra sezione non serve
    var lines = body.split('\n'), out = [];
    for (var i = 0; i < lines.length; i++) {
      if (/^#/.test(lines[i])) {
        var k = i + 1;
        while (k < lines.length && !lines[k].trim()) k++;
        if (k >= lines.length || /^#/.test(lines[k])) { c.removed.emptySections++; continue; }
      }
      out.push(lines[i]);
    }
    c.body = out.join('\n').replace(/\n{3,}/g, '\n\n').trim();
    return c;
  }

  // ── testo cantato e frasi ricorrenti (candidati per il titolo) ───────────
  /** Le righe cantate di un ChordPro, senza accordi né sezioni. */
  function lyricLines(body) {
    return String(body || '').split('\n').filter(function (l) { return l.trim() && !/^#/.test(l); })
      .map(function (l) { return l.replace(/\[[^\]]+\]/g, '').replace(/\s+/g, ' ').trim(); })
      .filter(Boolean);
  }
  function words(s) {
    return (String(s).toLowerCase().replace(/[’‘`]/g, "'").match(/[a-zà-ÿ0-9']+/g) || []);
  }
  var STOP = {};
  ('a an the and or but of to in on at for with from by as is are was were be been am it its i you he she we they me my your ' +
   'his her our their this that these those not no so if then than do does did have has had just oh yeah uh huh la na ooh ' +
   'e il la lo le gli di da in con su per tra fra un una che non mi ti ci si ma o').split(' ').forEach(function (w) { STOP[w] = 1; });

  /** Le frasi (2–6 parole) che tornano più volte, soprattutto a fine riga: di solito il titolo è una di queste.
   *  Ritorna fino a `max` frasi, dalla più probabile. */
  function guessTitles(body, max) {
    max = max || 8;
    var lines = lyricLines(body).map(words);
    var count = {}, ends = {};
    lines.forEach(function (w) {
      for (var n = 1; n <= 6; n++) {
        for (var i = 0; i + n <= w.length; i++) {
          var g = w.slice(i, i + n), key = g.join(' ');
          if (g.every(function (x) { return STOP[x]; })) continue;
          if (n === 1 && (g[0].length < 5)) continue;                 // una parola sola: solo se lunga
          count[key] = (count[key] || 0) + 1;
          if (i + n === w.length) ends[key] = (ends[key] || 0) + 1;
        }
      }
    });
    var ranked = Object.keys(count).filter(function (k) { return count[k] >= 2; }).map(function (k) {
      var n = k.split(' ').length;
      // più ricorrente, più lunga e a fine riga = più probabile; frasi troppo lunghe perdono
      var score = count[k] * Math.min(n, 4) * (1 + 0.6 * ((ends[k] || 0) / count[k])) * (n > 4 ? 0.7 : 1);
      return { phrase: k, score: score, count: count[k] };
    }).sort(function (a, b) { return b.score - a.score; });
    // se una frase ne contiene un'altra con lo stesso conteggio resta la più lunga fino a 4 parole
    var out = [];
    ranked.forEach(function (r) {
      if (out.length >= max) return;
      var dup = out.some(function (o) { return o.phrase.indexOf(r.phrase) >= 0 && o.count >= r.count; });
      if (!dup) out.push(r);
    });
    return out.map(function (r) { return r.phrase; });
  }

  // ── tonalità dagli accordi, quando manca la testata ──────────────────────
  var PC = { C: 0, 'C#': 1, Db: 1, D: 2, 'D#': 3, Eb: 3, E: 4, F: 5, 'F#': 6, Gb: 6, G: 7, 'G#': 8, Ab: 8, A: 9, 'A#': 10, Bb: 10, B: 11 };
  var NAMES_SHARP = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B'];
  var NAMES_FLAT = ['C', 'Db', 'D', 'Eb', 'E', 'F', 'Gb', 'G', 'Ab', 'A', 'Bb', 'B'];
  function parseChord(c) {
    var m = /^([A-G][#b]?)(m(?!aj)|min)?/.exec(c);
    return m ? { pc: PC[m[1]], minor: !!m[2] } : null;
  }
  /** Indovina la tonalità: quale delle 24 spiega meglio gli accordi, contando di più il primo e l'ultimo. */
  function inferKey(body) {
    var chords = (String(body || '').match(/\[([^\]]+)\]/g) || []).map(function (s) { return parseChord(s.slice(1, -1)); })
      .filter(function (c) { return c && c.pc !== undefined; });
    if (chords.length < 3) return '';
    var MAJ = [[0, false], [2, true], [4, true], [5, false], [7, false], [9, true]];   // I ii iii IV V vi
    var MIN = [[0, true], [3, false], [5, true], [7, false], [8, false], [10, false]]; // i III iv v(M) VI VII
    var best = null;
    for (var root = 0; root < 12; root++) {
      [false, true].forEach(function (minor) {
        var set = (minor ? MIN : MAJ).map(function (d) { return ((root + d[0]) % 12) + ':' + (d[1] ? 'm' : 'M'); });
        var score = 0;
        chords.forEach(function (c) { if (set.indexOf(c.pc + ':' + (c.minor ? 'm' : 'M')) >= 0) score++; });
        var tonic = function (c) { return c.pc === root && c.minor === minor; };
        chords.forEach(function (c) { if (tonic(c)) score += 0.5; });
        if (tonic(chords[0])) score += 2;
        if (tonic(chords[chords.length - 1])) score += 3;
        if (!best || score > best.score) best = { root: root, minor: minor, score: score };
      });
    }
    var flat = [1, 3, 5, 8, 10].indexOf(best.root) >= 0 || (best.root === 5 && !best.minor) || (best.minor && [2, 7, 0].indexOf(best.root) >= 0);
    return (flat ? NAMES_FLAT : NAMES_SHARP)[best.root] + (best.minor ? 'm' : '');
  }

  var api = { cleanRaw: cleanRaw, toChordPro: toChordPro, lyricLines: lyricLines, guessTitles: guessTitles,
              inferKey: inferKey, isTabLine: isTabLine };
  if (typeof module !== 'undefined' && module.exports) module.exports = api; else root.PC = api;
})(typeof window !== 'undefined' ? window : globalThis);
