var BL = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // entry.ts
  var entry_exports = {};
  __export(entry_exports, {
    isChord: () => isChord,
    isChordLine: () => isChordLine,
    looksRawTab: () => looksRawTab,
    normalizeBody: () => normalizeBody,
    tabToChordPro: () => tabToChordPro,
    toEnglish: () => toEnglish,
    toEnglishLine: () => toEnglishLine
  });

  // ../../../backline/src/core/chords.ts
  var SHARP = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"];
  var FLAT = ["C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"];
  var IT_PC = { Do: 0, Re: 2, Mi: 4, Fa: 5, Sol: 7, La: 9, Si: 11 };
  var SUFFIX = /^(?:min|maj|dim|aug|sus|add|alt|m|M|°|ø|\+|-|\d|#|b|\(|\))*$/;
  function isChord(tok) {
    const m = /^([A-G][#b]?)(.*)$/.exec(tok);
    if (!m) return false;
    let rest = m[2];
    const sl = rest.indexOf("/");
    if (sl >= 0) {
      const bass = rest.slice(sl + 1);
      if (bass && !/^[A-G][#b]?$/.test(bass)) return false;
      rest = rest.slice(0, sl);
    }
    return SUFFIX.test(rest);
  }
  var tokens = (str) => String(str != null ? str : "").split(/(\s+|\|)/);
  function toEnglish(tok) {
    if (!tok || isChord(tok)) return tok;
    const conv = (p) => {
      const m = /^(Sol|Do|Re|Mi|Fa|La|Si)([#b]?)(.*)$/.exec(p);
      if (!m) return null;
      const pc = ((IT_PC[m[1]] + (m[2] === "#" ? 1 : m[2] === "b" ? -1 : 0)) % 12 + 12) % 12;
      return (m[2] === "b" ? FLAT[pc] : SHARP[pc]) + m[3];
    };
    const out = tok.split("/").map((p) => {
      var _a;
      return (_a = conv(p)) != null ? _a : p;
    }).join("/");
    return isChord(out) ? out : tok;
  }
  var toEnglishLine = (str) => tokens(str).map(toEnglish).join("");
  var normalizeBody = (body) => String(body != null ? body : "").replace(/\[([^\]]+)\]/g, (_m, inner) => "[" + toEnglishLine(inner) + "]");

  // ../../../backline/src/core/convert.ts
  var ANNOT = /^(?:[xX]\d{1,2}|\d{1,2}[xX]|N\.?C\.?|[|:%]+)$/;
  var blanks = (s) => " ".repeat(s.length);
  var maskAnnot = (line) => String(line != null ? line : "").replace(/(^|\s)(\([^()]*\))(?=\s|$)/g, (_m, pre, grp) => pre + blanks(grp)).replace(/\S+/g, (t) => ANNOT.test(t) ? blanks(t) : t);
  var chordTokens = (line) => [...maskAnnot(line).matchAll(/\S+/g)].filter((m) => isChord(m[0]));
  var isChordLine = (line) => {
    const toks = [...maskAnnot(line).matchAll(/\S+/g)];
    return toks.length > 0 && toks.every((m) => isChord(m[0]));
  };
  var countTokens = (line) => chordTokens(line).length;
  function wrapChordsInPlace(line) {
    const toks = chordTokens(line);
    let out = String(line != null ? line : "");
    for (let i = toks.length - 1; i >= 0; i--) {
      const m = toks[i];
      out = out.slice(0, m.index) + "[" + m[0] + "]" + out.slice(m.index + m[0].length);
    }
    return out;
  }
  function mergeChordLyric(chordLines, lyricLine) {
    const lines = Array.isArray(chordLines) ? chordLines : [chordLines];
    let seq = 0;
    const toks = lines.flatMap((l) => chordTokens(l)).map((m) => ({ tok: m[0], index: m.index, seq: seq++ })).sort((a, b) => b.index - a.index || b.seq - a.seq);
    let out = lyricLine || "";
    for (const { tok, index } of toks) {
      if (index > out.length) out = out.padEnd(index, " ");
      out = out.slice(0, index) + "[" + tok + "]" + out.slice(index);
    }
    return out;
  }
  function chordProDirective(line) {
    const m = /^\s*\{([^:{}]+)(?::\s*([^{}]*))?\}\s*$/.exec(String(line || ""));
    return m ? { name: m[1].trim().toLowerCase(), value: (m[2] || "").trim() } : null;
  }
  var CP_SECTIONS = {
    soc: "Chorus",
    start_of_chorus: "Chorus",
    sov: "Verse",
    start_of_verse: "Verse",
    sob: "Bridge",
    start_of_bridge: "Bridge",
    sot: "Tab",
    start_of_tab: "Tab"
  };
  var CP_COMMENTS = ["c", "comment", "ci", "comment_italic", "cb", "comment_box"];
  function tabToChordPro(text) {
    const lines = String(text || "").replace(/\r\n?/g, "\n").split("\n");
    const secOnly = (l) => /^\s*\[([^\[\]]+)\]\s*$/.exec(l);
    const out = [];
    let i = 0;
    while (i < lines.length) {
      const line = lines[i];
      const dir = chordProDirective(line);
      if (dir) {
        if (CP_COMMENTS.includes(dir.name) && dir.value) out.push("# " + dir.value);
        else if (CP_SECTIONS[dir.name]) out.push("# " + CP_SECTIONS[dir.name]);
        i++;
        continue;
      }
      const sec = secOnly(line);
      if (sec && !isChordLine(line)) {
        out.push("# " + sec[1].trim());
        i++;
        continue;
      }
      if (isChordLine(line)) {
        const run = [line];
        let j = i + 1;
        while (j < lines.length && isChordLine(lines[j])) {
          run.push(lines[j]);
          j++;
        }
        const after = lines[j];
        const afterIsLyric = after !== void 0 && after.trim() !== "" && !isChordLine(after) && !secOnly(after);
        if (afterIsLyric) {
          let k = run.length - 1;
          while (k > 0 && countTokens(run[k - 1]) <= 2) k--;
          run.slice(0, k).forEach((l) => out.push(wrapChordsInPlace(l)));
          out.push(mergeChordLyric(run.slice(k), after));
          i = j + 1;
        } else {
          run.forEach((l) => out.push(wrapChordsInPlace(l)));
          i = j;
        }
        continue;
      }
      out.push(line);
      i++;
    }
    return out.join("\n").replace(/\n{3,}/g, "\n\n").trim();
  }
  var looksRawTab = (body) => String(body != null ? body : "").split("\n").some((l) => !l.includes("[") && isChordLine(l));
  return __toCommonJS(entry_exports);
})();
