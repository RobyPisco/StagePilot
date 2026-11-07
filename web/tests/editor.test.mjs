import test from "node:test";
import assert from "node:assert/strict";
import {
  readLine,
  writeLine,
  rewriteLyrics,
  putChord,
  moveChord,
  moveSection,
  transposeBody,
  isChord,
} from "../src/editor-domain.ts";

test("inline chords round-trip without altering words, spacing or multiple chords at the same anchor", () => {
  for (const line of [
    "[Am]  Testo [F#/A#]con àccenti [G]",
    "[C][G]",
    "Parole senza accordi",
    "",
    "Fine[C]",
  ]) {
    const p = readLine(line);
    assert.equal(writeLine(p.text, p.anchors), line);
  }
});
test("inserting and replacing lyrics keeps chords attached to the following word", () => {
  assert.equal(
    rewriteLyrics("[Am]Canta [G]forte", "Ora Canta forte"),
    "Ora [Am]Canta [G]forte",
  );
  assert.equal(
    rewriteLyrics("[Am]Canta [G]forte", "Canta più forte"),
    "[Am]Canta più [G]forte",
  );
  assert.equal(rewriteLyrics("Qui [G]canto", "Qui suono"), "Qui [G]suono");
  assert.equal(rewriteLyrics("[Am]Canta [G]forte", ""), "[Am][G]");
  assert.equal(rewriteLyrics("[C]Una [G]luce", "Una\nluce"), "[C]Una\n[G]luce");
});
test("chord insertion, replacement, removal and movement preserve the lyric exactly", () => {
  assert.equal(putChord("La parola", 3, "Am7"), "La [Am7]parola");
  assert.equal(putChord("La [Am7]parola", 3, "F#/A#"), "La [F#/A#]parola");
  assert.equal(putChord("La [Am7]parola", 3, ""), "La parola");
  assert.equal(moveChord("La [G]parola", 3, 5), "La pa[G]rola");
  assert.throws(() => putChord("Canto", 9, "G"));
  assert.throws(() => putChord("Canto", 0, "G]\n[F"));
});
test("transpose changes chord roots and slash bass, preserving labels, lyrics and no-chord marks", () => {
  assert.equal(
    transposeBody("# C Intro\n[Am7]Canto [Bb/D]qui [N.C.]\n[Bridge]", 2),
    "# C Intro\n[Bm7]Canto [C/E]qui [N.C.]\n[Bridge]",
  );
  assert.equal(transposeBody("[C]Canto", -1), "[B]Canto");
  assert.throws(() => transposeBody("[C]", 12));
  for (const value of [
    "C",
    "Am7",
    "F#/A#",
    "Bbmaj7",
    "C7(#9)",
    "Dm7b5",
    "N.C.",
  ])
    assert.ok(isChord(value), value);
  for (const value of ["Bridge", "", "C\nG", "[Am]", "Chorus"])
    assert.equal(isChord(value), false, value);
});
test("moving a section moves its full passage and preserves the intro and all blank lines", () => {
  const body =
    "Nota iniziale\n# Strofa\n[C]uno\n\n# Ritornello\n[G]due\n# Finale\nStop";
  const swapped =
    "Nota iniziale\n# Ritornello\n[G]due\n# Strofa\n[C]uno\n\n# Finale\nStop";
  assert.equal(moveSection(body, 1, 1), swapped);
  assert.equal(moveSection(body, 4, -1), swapped);
  assert.equal(moveSection(body, 1, -1), body);
  assert.equal(moveSection(body, 2, 1), body);
});
