import test from "node:test";
import assert from "node:assert/strict";
import {
  dossierDefaults,
  mergeGigDraft,
  stageCoordinate,
} from "../src/dossier-domain.ts";
const original = {
  id: "gig",
  title: "Serata",
  date: "2026-11-07",
  time: "21:00",
  location: "Sala",
  setId: "set",
  notes: "",
  kind: "gig",
};
test("old concerts gain editable empty dossier fields without changing their original information", () => {
  const d = dossierDefaults(original);
  assert.equal(d.title, "Serata");
  assert.deepEqual(d.contacts, []);
  assert.deepEqual(d.inputs, []);
  assert.equal(d.arrival, "");
});
test("saving a dossier preserves an independent title update and rejects overlapping checklist edits", () => {
  const initial = dossierDefaults(original),
    draft = { ...initial, address: "Via esempio" };
  const merged = mergeGigDraft(initial, draft, {
    ...initial,
    title: "Serata aggiornata",
  });
  assert.equal(merged.title, "Serata aggiornata");
  assert.equal(merged.address, "Via esempio");
  assert.throws(() =>
    mergeGigDraft(
      initial,
      {
        ...initial,
        checklist: [{ id: "a", text: "Cavi", done: false, owner: "" }],
      },
      {
        ...initial,
        checklist: [{ id: "b", text: "Pedale", done: true, owner: "" }],
      },
    ),
  );
});
test("stage positions stay inside the visible area for touch and keyboard movement", () => {
  assert.equal(stageCoordinate(-40), 5);
  assert.equal(stageCoordinate(150), 95);
  assert.equal(stageCoordinate(62.34), 62.3);
});
