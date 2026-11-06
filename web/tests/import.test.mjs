import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";

test("the shipped browser converter preserves isolated chords and removes site debris", () => {
  const context = vm.createContext({});
  vm.runInContext(
    fs.readFileSync(
      new URL("../public/convert.bundle.js", import.meta.url),
      "utf8",
    ),
    context,
  );
  vm.runInContext(
    fs.readFileSync(new URL("../public/pulizia.js", import.meta.url), "utf8"),
    context,
  );
  assert.equal(context.PC.toChordPro("[Intro]\n[Am]").body, "# Intro\n[Am]");
  const converted = context.PC.toChordPro(
    "Difficulty: easy\nKey: Am\n[Verse]\nAm      F\nhello world",
  );
  assert.equal(converted.meta.key, "Am");
  assert.match(converted.body, /\[Am\]hello wo\[F\]rld/);
  assert.doesNotMatch(converted.body, /Difficulty/);
});
