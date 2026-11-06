import test from "node:test";
import assert from "node:assert/strict";
import {
  removeSong,
  seconds,
  transpose,
  mergeSongDraft,
  songDefaults,
  passage,
} from "../src/domain.ts";

test("deleting one song removes every reference but preserves pauses and other songs", () => {
  const b = {
    songs: [{ id: "s1" }, { id: "s2" }],
    sets: [
      {
        items: [
          { type: "song", id: "s1" },
          { type: "mark", label: "Pausa" },
          { type: "song", id: "s1" },
          { type: "song", id: "s2" },
        ],
      },
      { items: [{ type: "song", id: "s1" }] },
    ],
  };
  removeSong(b, "s1");
  assert.deepEqual(b.songs, [{ id: "s2" }]);
  assert.deepEqual(b.sets[0].items, [
    { type: "mark", label: "Pausa" },
    { type: "song", id: "s2" },
  ]);
  assert.deepEqual(b.sets[1].items, []);
});
test("musical timing rejects invalid seconds and transposes slash bass independently", () => {
  assert.equal(seconds("3:40"), 220);
  assert.equal(seconds("3:99"), 0);
  assert.equal(transpose("C/G", 2), "D/A");
  assert.equal(transpose("Am7", -2), "Gm7");
});
test("editing a draft preserves independent device edits and rejects same-field changes", () => {
  const base = { ...songDefaults(), title: "Uno", bpm: 120 };
  assert.deepEqual(
    mergeSongDraft(base, { ...base, title: "Due" }, { ...base, bpm: 96 }),
    { ...base, title: "Due", bpm: 96 },
  );
  assert.throws(() =>
    mergeSongDraft(base, { ...base, bpm: 100 }, { ...base, bpm: 110 }),
  );
  assert.equal(passage("# A\n[Am]x\n# B\n[F]y", "B"), "# B\n[F]y");
});
