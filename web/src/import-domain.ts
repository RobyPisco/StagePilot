export type ImportFields = {
  title: string;
  artist: string;
  key: string;
  bpm: string;
  dur: string;
};
export type MetadataCandidate = {
  title: string;
  artist: string;
  album: string;
  dur: string;
  bpm: number | null;
  score: number;
  recordingUrl: string;
  sources: { label: string; url: string }[];
};
export type MetadataResult = {
  found: boolean;
  selected?: MetadataCandidate;
  candidates: MetadataCandidate[];
  message: string;
};
export const emptyImportFields = (): ImportFields => ({
  title: "",
  artist: "",
  key: "",
  bpm: "",
  dur: "",
});

/** Merge at response time so edits made while the request was pending survive. */
export function mergeImportFields(
  current: ImportFields,
  incoming: Partial<ImportFields>,
  protectedFields: ReadonlySet<keyof ImportFields>,
  clearMissing = false,
): ImportFields {
  const next = { ...current };
  for (const key of Object.keys(incoming) as (keyof ImportFields)[]) {
    if (!protectedFields.has(key) && (incoming[key] || clearMissing))
      next[key] = incoming[key] || "";
  }
  return next;
}
