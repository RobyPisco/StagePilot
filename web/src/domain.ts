export type Song = {
  id: string;
  title: string;
  artist: string;
  key: string;
  bpm: number | null;
  dur: string;
  body: string;
  tags: string[];
  notes: Record<string, string>;
  status: "study" | "rehearse" | "ready";
  practice: string;
  energy: number;
  tr: number;
  capo: number;
  scroll: number;
  lead: number;
  source: string;
};
export type Item = {
  type: "song" | "mark";
  id?: string;
  label?: string;
  dur?: string;
};
export type Setlist = { id: string; name: string; items: Item[] };
export type Session = {
  id: string;
  date: string;
  seconds: number;
  songIds: string[];
  notes: string;
};
export type Task = {
  id: string;
  songId: string;
  section: string;
  goal: string;
  role: string;
  decision: string;
  done: boolean;
};
export type Gig = {
  id: string;
  title: string;
  date: string;
  time: string;
  location: string;
  setId: string;
  notes: string;
  kind: string;
};
export type Band = {
  id: string;
  name: string;
  me: string;
  songs: Song[];
  sets: Setlist[];
  cur: string;
  rehearsals: Session[];
  events: Gig[];
  practiceTasks?: Task[];
};
export type DB = {
  v: number;
  curBand: string;
  opt: Record<string, unknown>;
  bands: Band[];
};
export type Snapshot = { revision: number; db: DB };
export type Page =
  | "home"
  | "songs"
  | "sets"
  | "practice"
  | "import"
  | "stage"
  | "concerts";
export const labels = {
  study: "Da studiare",
  rehearse: "Da ripassare",
  ready: "Pronto",
};
export function id(prefix: string) {
  return (
    prefix +
    "_" +
    (globalThis.crypto?.randomUUID?.() ||
      Date.now().toString(36) + Math.random().toString(36).slice(2))
  );
}
export function songDefaults(): Song {
  return {
    id: id("s"),
    title: "",
    artist: "",
    key: "",
    bpm: null,
    dur: "",
    body: "",
    tags: [],
    notes: {},
    status: "study",
    practice: "",
    energy: 3,
    tr: 0,
    capo: 0,
    scroll: 1,
    lead: 0,
    source: "",
  };
}
export function seconds(d: string | undefined) {
  const m = /^(\d+):([0-5]\d)$/.exec(d || "");
  return m ? Number(m[1]) * 60 + Number(m[2]) : 0;
}
export function time(n: number) {
  return `${Math.floor(Math.max(0, n) / 60)}:${String(Math.floor(Math.max(0, n) % 60)).padStart(2, "0")}`;
}
export function duration(band: Band, set?: Setlist) {
  return (
    set?.items.reduce(
      (sum, i) =>
        sum +
        seconds(
          i.type === "mark"
            ? i.dur
            : band.songs.find((s) => s.id === i.id)?.dur,
        ),
      0,
    ) || 0
  );
}
export function transpose(chord: string, steps: number) {
  const pc: Record<string, number> = {
    C: 0,
    "C#": 1,
    Db: 1,
    D: 2,
    "D#": 3,
    Eb: 3,
    E: 4,
    F: 5,
    "F#": 6,
    Gb: 6,
    G: 7,
    "G#": 8,
    Ab: 8,
    A: 9,
    "A#": 10,
    Bb: 10,
    B: 11,
  };
  if (!steps) return chord;
  return chord.replace(
    /[A-G][#b]?/g,
    (m) =>
      ["C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"][
        (pc[m] + steps + 120) % 12
      ],
  );
}
export function removeSong(band: Band, songId: string) {
  band.songs = band.songs.filter((s) => s.id !== songId);
  band.sets.forEach(
    (s) =>
      (s.items = s.items.filter((i) => i.type !== "song" || i.id !== songId)),
  );
  band.practiceTasks = (band.practiceTasks || []).filter(
    (t) => t.songId !== songId,
  );
}
export function sections(body: string) {
  return [
    ...new Set(
      body
        .split("\n")
        .filter((l) => l.startsWith("#"))
        .map((l) => l.slice(1).trim())
        .filter(Boolean),
    ),
  ];
}
export function passage(body: string, section: string) {
  if (section === "Brano intero") return body;
  const lines = body.split("\n"),
    start = lines.findIndex(
      (l) => l.startsWith("#") && l.slice(1).trim() === section,
    );
  if (start < 0) return body;
  const end = lines.findIndex((l, i) => i > start && l.startsWith("#"));
  return lines
    .slice(start, end < 0 ? undefined : end)
    .join("\n")
    .trim();
}
export function preparation(band: Band, set?: Setlist) {
  const ids = new Set(
    set?.items.filter((i) => i.type === "song").map((i) => i.id),
  );
  const songs = band.songs.filter((s) => ids.has(s.id));
  return {
    duration: duration(band, set),
    pending: songs.filter((s) => s.status !== "ready").length,
    missing: songs.filter((s) => !seconds(s.dur)).length,
    tasks: (band.practiceTasks || []).filter(
      (t) => !t.done && ids.has(t.songId),
    ).length,
  };
}
export function mergeSongDraft(
  initial: Song,
  draft: Song,
  current: Song,
): Song {
  const result = { ...current };
  for (const key of Object.keys(draft) as (keyof Song)[]) {
    if (JSON.stringify(draft[key]) === JSON.stringify(initial[key])) continue;
    if (
      JSON.stringify(current[key]) !== JSON.stringify(initial[key]) &&
      JSON.stringify(current[key]) !== JSON.stringify(draft[key])
    )
      throw new Error(
        `Il campo «${key}» è cambiato anche sull’altro dispositivo. Chiudi e riapri il brano per confrontarlo; la bozza è ancora qui.`,
      );
    Object.assign(result, { [key]: draft[key] });
  }
  return result;
}

export const SAMPLE = `Ultimo giro chords by COPYCUT\nTuning: E A D G B E\nKey: Am\nCapo: No capo\nDifficulty: beginner\nLast update: today\n\n[Verse]\nAm             F\nLa città si accende\nC                 G\nLa notte ci assomiglia\n\n[Chorus]\nF              C\nSuona ancora, suona forte\nG                Am\nFino all'ultimo giro`;

export function prepare(
  raw: string,
  keepTab = false,
): {
  body: string;
  title: string;
  artist: string;
  key: string;
  capo: number;
  bpm: number | null;
  dur: string;
  removed: number;
} {
  let bpm: number | null = null,
    dur = "",
    headerTitle = "",
    headerArtist = "";
  let timingRemoved = 0;
  raw = raw.replace(/\r\n?/g, "\n").replace(/\u00a0/g, " ");
  raw = raw
    .split("\n")
    .filter((line) => {
      const match =
        /^\s*(?:\{\s*)?(tempo|bpm|duration|durata|title|titolo|artist|artista)\s*:\s*([^}]+)\}?\s*$/i.exec(
          line,
        );
      if (!match) return true;
      const name = match[1].toLowerCase(),
        value = match[2].trim();
      if (["tempo", "bpm"].includes(name)) {
        const n = Number(value.replace(/\s*bpm\s*$/i, ""));
        if (Number.isInteger(n) && n >= 20 && n <= 400) bpm = n;
      } else if (["duration", "durata"].includes(name)) {
        if (seconds(value)) dur = value;
      } else if (["title", "titolo"].includes(name)) headerTitle ||= value;
      else headerArtist ||= value;
      timingRemoved++;
      return false;
    })
    .join("\n");
  const titleLine = raw
    .split("\n")
    .map((l) => l.match(/^\s*(.+?)\s+(?:chords?|tab)\s+by\s+(.+?)\s*$/i))
    .find(Boolean);
  const text = raw
    .split("\n")
    .filter((l) => !/^\s*.+?\s+(?:chords?|tab)\s+by\s+.+$/i.test(l))
    .join("\n");
  const pc = (
    window as unknown as {
      PC: {
        toChordPro: (
          s: string,
          o: unknown,
        ) => {
          body: string;
          meta: Record<string, string>;
          removed: Record<string, number>;
        };
      };
    }
  ).PC;
  const result = pc.toChordPro(text, { dropTab: !keepTab });
  return {
    body: result.body,
    title: headerTitle || result.meta.title || titleLine?.[1] || "",
    artist: headerArtist || result.meta.artist || titleLine?.[2] || "",
    key: result.meta.key || "",
    capo: Number(result.meta.capo) || 0,
    bpm,
    dur,
    removed:
      Object.values(result.removed).reduce((n, v) => n + v, 0) +
      (titleLine ? 1 : 0) +
      timingRemoved,
  };
}
