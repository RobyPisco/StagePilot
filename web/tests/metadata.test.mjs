import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import fs from "node:fs";
import { prepare } from "../src/domain.ts";
import { mergeImportFields, emptyImportFields } from "../src/import-domain.ts";

const context = vm.createContext({});
for (const file of ["convert.bundle.js", "pulizia.js"])
  vm.runInContext(
    fs.readFileSync(new URL("../public/" + file, import.meta.url), "utf8"),
    context,
  );
globalThis.window = { PC: context.PC };

test("pasted timing headers populate actual importer fields and leave the lyrics intact", () => {
  const result = prepare(
    "Title: Example\nArtist: A Band\nBPM: 128 bpm\nDuration: 3:28\n[Chorus]\nAm       F\nCarry us home",
  );
  assert.equal(result.title, "Example");
  assert.equal(result.artist, "A Band");
  assert.equal(result.bpm, 128);
  assert.equal(result.dur, "3:28");
  assert.doesNotMatch(result.body, /BPM|Duration|Artist|Title/);
  assert.match(result.body.replace(/\[[^\]]*\]/g, ""), /Carry us home/);
});
test("ChordPro metadata and Ultimate Guitar identity both survive conversion", () => {
  const cp = prepare(
    "{title: Original}\n{artist: Original Band}\n{tempo: 115}\n{duration: 4:05}\n[C]New words for the song",
  );
  assert.equal(cp.title, "Original");
  assert.equal(cp.bpm, 115);
  assert.equal(cp.dur, "4:05");
  const ug = prepare(
    "Another song chords by Another Band\nKey: Am\n[Verse]\n[Am]Hello again",
  );
  assert.equal(ug.title, "Another song");
  assert.equal(ug.artist, "Another Band");
  assert.equal(ug.key, "Am");
});
test("invalid or absent timing does not create guessed values", () => {
  const result = prepare("BPM: 999\nDuration: 3:99\n[C]Hello again");
  assert.equal(result.bpm, null);
  assert.equal(result.dur, "");
});
test("late online results preserve manual edits including an intentionally cleared field", () => {
  const draft = {
    ...emptyImportFields(),
    title: "My arrangement",
    bpm: "105",
    dur: "",
  };
  const merged = mergeImportFields(
    draft,
    { title: "Catalog title", artist: "Catalog band", bpm: "129", dur: "3:28" },
    new Set(["title", "bpm", "dur"]),
  );
  assert.equal(merged.title, "My arrangement");
  assert.equal(merged.bpm, "105");
  assert.equal(merged.dur, "");
  assert.equal(merged.artist, "Catalog band");
});
test("choosing another recording clears unavailable old tempo while preserving newer manual edits", () => {
  const original = { ...emptyImportFields(), bpm: "129", dur: "3:28" };
  const next = mergeImportFields(
    original,
    { title: "Cover", artist: "Another Band", bpm: "", dur: "4:00" },
    new Set(),
    true,
  );
  assert.equal(next.bpm, "");
  assert.equal(next.dur, "4:00");
  const edited = mergeImportFields(
    { ...original, bpm: "110" },
    { bpm: "", dur: "4:00" },
    new Set(["bpm"]),
    true,
  );
  assert.equal(edited.bpm, "110");
});
