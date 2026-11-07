import type { Gig } from "./domain";
export const dossierDefaults = (gig: Gig): Gig => ({
  ...gig,
  address: gig.address || "",
  arrival: gig.arrival || "",
  soundcheck: gig.soundcheck || "",
  doors: gig.doors || "",
  end: gig.end || "",
  contacts: gig.contacts || [],
  checklist: gig.checklist || [],
  stagePlot: gig.stagePlot || [],
  inputs: gig.inputs || [],
});
export function mergeGigDraft(initial: Gig, draft: Gig, current: Gig): Gig {
  const a = dossierDefaults(initial),
    b = dossierDefaults(draft),
    c = dossierDefaults(current),
    out = { ...current };
  for (const key of Object.keys(b) as (keyof Gig)[]) {
    if (JSON.stringify(a[key]) === JSON.stringify(b[key])) continue;
    if (
      JSON.stringify(c[key]) !== JSON.stringify(a[key]) &&
      JSON.stringify(c[key]) !== JSON.stringify(b[key])
    )
      throw new Error(
        "Il dossier è cambiato anche su un altro dispositivo. La bozza è conservata: confronta le modifiche prima di salvare.",
      );
    Object.assign(out, { [key]: b[key] });
  }
  return out;
}
export function stageCoordinate(value: number) {
  return Math.round(Math.max(5, Math.min(95, value)) * 10) / 10;
}
