import { useEffect, useRef, useState } from "react";
import {
  ArrowDown,
  ArrowUp,
  Redo2,
  Save,
  Trash2,
  Undo2,
  X,
} from "lucide-react";
import { type Song, labels, seconds, songDefaults, transpose } from "./domain";
import { Chart } from "./Chart";
import {
  isChord,
  moveChord,
  moveSection,
  putChord,
  readLine,
  rewriteLyrics,
  transposeBody,
} from "./editor-domain";

type Props = {
  initial: Song;
  bandId: string;
  busy: boolean;
  saveError?: string;
  latest?: Song;
  save: (song: Song, baseline: Song) => Promise<boolean>;
  remove: () => Promise<boolean>;
  close: () => void;
};
type History = { past: Song[]; current: Song; future: Song[] };

export function SongEditor({
  initial,
  bandId,
  busy,
  saveError,
  latest,
  save,
  remove,
  close,
}: Props) {
  const draftKey = `stagepilot-editor-v1:${bandId}:${initial.title ? initial.id : "new"}`;
  const [restored] = useState(() => {
    try {
      const cached = JSON.parse(sessionStorage.getItem(draftKey) || "null");
      if (
        cached?.base?.id === cached?.song?.id &&
        typeof cached?.song?.body === "string" &&
        typeof cached?.song?.title === "string" &&
        (!initial.title || cached.song.id === initial.id)
      )
        return cached as { base: Song; song: Song };
    } catch {
      /* A corrupt draft must not replace the library. */
    }
    return null;
  });
  const [baseline, setBaseline] = useState(restored?.base || initial);
  const [history, setHistory] = useState<History>({
    past: [],
    current: { ...songDefaults(), ...(restored?.song || initial) },
    future: [],
  });
  const song = history.current;
  const [mode, setMode] = useState<"visual" | "source">("visual");
  const [target, setTarget] = useState(() => ({
    line: Math.max(
      0,
      song.body
        .split("\n")
        .findIndex((line) => !line.startsWith("#") && line.trim()),
    ),
    at: 0,
  }));
  const [chord, setChord] = useState("Am");
  const [section, setSection] = useState("Ritornello");
  const [error, setError] = useState("");
  const [storageError, setStorageError] = useState(false);
  const [closing, setClosing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [transposing, setTransposing] = useState(false);
  const [steps, setSteps] = useState(1);
  const [font, setFont] = useState(22);
  const dialog = useRef<HTMLElement>(null);
  const pendingCaret = useRef<{ line: number; at: number } | null>(null);
  const lines = song.body.split("\n");
  const lineIndex = Math.min(target.line, lines.length - 1);
  const selected = readLine(lines[lineIndex]);
  const position = Math.min(target.at, selected.text.length);
  const lyricSelected = !lines[lineIndex].startsWith("#");
  const dirty = JSON.stringify(song) !== JSON.stringify(baseline);
  const conflictKeys = latest
    ? (Object.keys(song) as (keyof Song)[]).filter(
        (k) =>
          JSON.stringify(song[k]) !== JSON.stringify(baseline[k]) &&
          JSON.stringify(latest[k]) !== JSON.stringify(baseline[k]) &&
          JSON.stringify(latest[k]) !== JSON.stringify(song[k]),
      )
    : [];
  const conflictLabel = (key: keyof Song) =>
    ({
      body: "Testo e accordi",
      title: "Titolo",
      artist: "Artista",
      key: "Tonalità",
      notes: "Note",
      practice: "Appunto di prova",
      dur: "Durata",
    })[key as string] || key;
  const showValue = (value: unknown) =>
    typeof value === "string" ? value : JSON.stringify(value, null, 2);
  const valid =
    !!song.title.trim() &&
    (!song.dur || seconds(song.dur) > 0) &&
    (song.bpm === null ||
      (Number.isFinite(song.bpm) && song.bpm >= 20 && song.bpm <= 400));

  function change(next: Song) {
    setHistory((h) =>
      JSON.stringify(next) === JSON.stringify(h.current)
        ? h
        : {
            past: [...h.past.slice(-199), h.current],
            current: next,
            future: [],
          },
    );
    setError("");
  }
  function field<K extends keyof Song>(key: K, value: Song[K]) {
    change({ ...song, [key]: value });
  }
  function undo() {
    setHistory((h) =>
      h.past.length
        ? {
            past: h.past.slice(0, -1),
            current: h.past.at(-1)!,
            future: [h.current, ...h.future],
          }
        : h,
    );
  }
  function redo() {
    setHistory((h) =>
      h.future.length
        ? {
            past: [...h.past, h.current],
            current: h.future[0],
            future: h.future.slice(1),
          }
        : h,
    );
  }
  function editLine(index: number, value: string) {
    field("body", lines.map((l, i) => (i === index ? value : l)).join("\n"));
  }
  function choose(line: number, at: number) {
    setTarget({ line, at });
    const existing = readLine(lines[line]).anchors.find((a) => a.at === at);
    if (existing) setChord(existing.chord);
  }
  function apply(value: string) {
    if (!lyricSelected) return;
    try {
      editLine(lineIndex, putChord(lines[lineIndex], position, value.trim()));
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function shift(delta: number) {
    const next = position + delta;
    if (next < 0 || next > selected.text.length) return;
    editLine(lineIndex, moveChord(lines[lineIndex], position, next));
    setTarget({ line: lineIndex, at: next });
  }
  function insert(value: string) {
    const next = [...lines];
    next.splice(lineIndex + 1, 0, value);
    field("body", next.join("\n"));
    setTarget({ line: lineIndex + 1, at: 0 });
  }
  function moveLine(index: number, delta: -1 | 1) {
    if (lines[index].startsWith("#")) {
      const starts = lines.flatMap((line, i) =>
        line.startsWith("#") ? [i] : [],
      );
      const n = starts.indexOf(index),
        other = starts[n + delta];
      if (other === undefined) return;
      const to =
        delta < 0 ? other : index + ((starts[n + 2] ?? lines.length) - other);
      field("body", moveSection(song.body, index, delta));
      setTarget({ line: to, at: 0 });
      return;
    }
    const next = [...lines],
      to = index + delta;
    if (to < 0 || to >= next.length) return;
    [next[index], next[to]] = [next[to], next[index]];
    field("body", next.join("\n"));
    setTarget({ line: to, at: 0 });
  }
  function clearDraft() {
    try {
      sessionStorage.removeItem(draftKey);
    } catch {
      /* Storage may be disabled. */
    }
  }
  async function persist() {
    if (!valid || busy) return;
    const ok = await save(
      { ...song, title: song.title.trim(), tags: song.tags.filter(Boolean) },
      baseline,
    );
    if (ok) clearDraft();
    else
      setError(
        "Il brano non è stato salvato. La bozza resta aperta: controlla il collegamento o il conflitto e riprova.",
      );
  }

  useEffect(() => {
    try {
      if (dirty)
        sessionStorage.setItem(
          draftKey,
          JSON.stringify({ base: baseline, song }),
        );
      else sessionStorage.removeItem(draftKey);
      setStorageError(false);
    } catch {
      setStorageError(true);
    }
  }, [song, dirty, draftKey, baseline]);
  useEffect(() => {
    const caret = pendingCaret.current;
    if (!caret) return;
    const input = dialog.current?.querySelector<HTMLTextAreaElement>(
      `textarea[data-editor-line="${caret.line}"]`,
    );
    input?.focus();
    input?.setSelectionRange(caret.at, caret.at);
    pendingCaret.current = null;
  }, [song.body]);
  useEffect(() => {
    if (!dirty) return;
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty]);
  useEffect(() => {
    const before = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    dialog.current?.focus();
    return () => {
      document.body.style.overflow = overflow;
      before?.focus();
    };
  }, []);

  const chordTools = (
    <div className="sp-editor-chords">
      <strong>1. Tocca una lettera. 2. Aggiungi l’accordo.</strong>
      <p className="sp-editor-help">
        Oppure posiziona il cursore nel testo della riga. Gli accordi restano
        agganciati alle parole.
      </p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          apply(chord);
        }}
      >
        <label>
          Accordo
          <input
            className="sp-input"
            aria-label="Accordo da inserire"
            value={chord}
            onChange={(e) => setChord(e.target.value)}
            placeholder="Am7"
          />
        </label>
        <button
          className="sp-primary"
          disabled={!lyricSelected || !isChord(chord.trim())}
        >
          Inserisci / sostituisci
        </button>
      </form>
      <div className="sp-editor-chips">
        {["C", "D", "E", "F", "G", "A", "B", "Am", "Dm", "Em"].map((c) => (
          <button
            key={c}
            disabled={!lyricSelected}
            onClick={() => {
              setChord(c);
              apply(c);
            }}
          >
            {c}
          </button>
        ))}
      </div>
      <div className="sp-editor-anchor">
        <span>
          Riga {lineIndex + 1} · posizione {position + 1}
        </span>
        <button
          className="sp-secondary"
          disabled={
            !lyricSelected ||
            !selected.anchors.some((a) => a.at === position) ||
            position === 0
          }
          onClick={() => shift(-1)}
        >
          ← Sposta accordo
        </button>
        <button
          className="sp-secondary"
          disabled={
            !lyricSelected ||
            !selected.anchors.some((a) => a.at === position) ||
            position === selected.text.length
          }
          onClick={() => shift(1)}
        >
          Sposta accordo →
        </button>
        <button
          className="sp-secondary"
          disabled={
            !lyricSelected || !selected.anchors.some((a) => a.at === position)
          }
          onClick={() => apply("")}
        >
          Rimuovi accordo
        </button>
      </div>
    </div>
  );

  return (
    <div className="sp-editor-overlay">
      <section
        className="sp-song-editor"
        role="dialog"
        aria-modal="true"
        aria-label="Editor brano"
        tabIndex={-1}
        ref={dialog}
        onKeyDown={(e) => {
          if (e.key === "Escape" && !busy) {
            e.preventDefault();
            dirty ? setClosing(true) : close();
          }
          if (
            (e.ctrlKey || e.metaKey) &&
            ["z", "y"].includes(e.key.toLowerCase())
          ) {
            e.preventDefault();
            if (!busy)
              (e.shiftKey || e.key.toLowerCase() === "y" ? redo : undo)();
          }
          if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") {
            e.preventDefault();
            void persist();
          }
          if (e.key === "Tab") {
            const focusable = [
              ...(dialog.current?.querySelectorAll<HTMLElement>(
                'button:not(:disabled):not([tabindex="-1"]), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), summary',
              ) || []),
            ].filter((n) => n.getClientRects().length);
            const first = focusable[0],
              last = focusable.at(-1);
            if (
              e.shiftKey &&
              (document.activeElement === first ||
                document.activeElement === dialog.current)
            ) {
              e.preventDefault();
              last?.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault();
              first?.focus();
            }
          }
        }}
      >
        <header className="sp-editor-head">
          <div>
            <span className="sp-eyebrow">STUDIO / EDITOR BRANO</span>
            <h2>{song.title || "Il tuo nuovo brano"}</h2>
            <p>
              {!valid
                ? "Controlla titolo, durata o BPM in Dati del brano · "
                : ""}
              {restored ? "Bozza recuperata · " : ""}
              {dirty
                ? storageError
                  ? "Bozza solo in memoria"
                  : "Bozza protetta in questa scheda"
                : "Nessuna modifica da salvare"}
            </p>
          </div>
          <div className="sp-editor-actions">
            <button
              className="sp-secondary"
              disabled={busy || !history.past.length}
              onClick={undo}
              aria-label="Annulla modifica"
            >
              <Undo2 />
            </button>
            <button
              className="sp-secondary"
              disabled={busy || !history.future.length}
              onClick={redo}
              aria-label="Ripristina modifica"
            >
              <Redo2 />
            </button>
            <button
              className="sp-primary"
              disabled={busy || !valid}
              onClick={() => void persist()}
            >
              <Save />
              {busy ? "Salvataggio…" : "Salva brano"}
            </button>
            <button
              className="sp-iconbtn"
              aria-label="Chiudi editor"
              disabled={busy}
              onClick={() => (dirty ? setClosing(true) : close())}
            >
              <X />
            </button>
          </div>
        </header>
        {(saveError || error) && (
          <p role="alert" className="sp-editor-alert">
            {saveError || error}
          </p>
        )}
        {latest && conflictKeys.length > 0 && (
          <details className="sp-editor-alert" open>
            <summary>
              Confronta le modifiche arrivate da un altro dispositivo
            </summary>
            <p>
              Per ogni campo scegli quale versione conservare. Poi premi Salva
              brano.
            </p>
            {conflictKeys.map((key) => (
              <div className="sp-editor-conflict" key={key}>
                <strong>{conflictLabel(key)}</strong>
                <div className="sp-form-grid">
                  <label className="sp-form-label">
                    La tua bozza
                    <textarea
                      className="sp-input"
                      readOnly
                      value={showValue(song[key])}
                    />
                  </label>
                  <label className="sp-form-label">
                    Versione salvata
                    <textarea
                      className="sp-input"
                      readOnly
                      value={showValue(latest[key])}
                    />
                  </label>
                </div>
                <div className="sp-editor-actions">
                  <button
                    className="sp-secondary"
                    disabled={busy}
                    onClick={() =>
                      setBaseline((b) => ({ ...b, [key]: latest[key] }))
                    }
                  >
                    Mantieni la mia versione di {conflictLabel(key)}
                  </button>
                  <button
                    className="sp-secondary"
                    disabled={busy}
                    onClick={() => {
                      setBaseline((b) => ({ ...b, [key]: latest[key] }));
                      change({ ...song, [key]: latest[key] });
                    }}
                  >
                    Prendi la versione salvata di {conflictLabel(key)}
                  </button>
                </div>
              </div>
            ))}
          </details>
        )}
        {storageError && (
          <p role="alert" className="sp-editor-alert">
            Il browser non può conservare la bozza. Salva il brano prima di
            chiudere o ricaricare.
          </p>
        )}
        {closing && (
          <div className="sp-editor-alert" role="alert">
            <strong>Ci sono modifiche non salvate.</strong>
            <p>
              Puoi tornare all’editor o uscire conservando la bozza in questa
              scheda del browser.
            </p>
            <div className="sp-editor-actions">
              <button
                className="sp-secondary"
                onClick={() => setClosing(false)}
              >
                Continua a modificare
              </button>
              <button
                className="sp-secondary"
                disabled={storageError}
                onClick={close}
              >
                Esci e conserva bozza
              </button>
              <button
                className="sp-secondary"
                onClick={() => {
                  clearDraft();
                  close();
                }}
              >
                Scarta modifiche ed esci
              </button>
            </div>
          </div>
        )}
        <div className="sp-editor-columns">
          <div className="sp-editor-work">
            <fieldset disabled={busy}>
              <details className="sp-editor-details" open={!initial.title}>
                <summary>
                  Dati del brano ·{" "}
                  {song.artist || "titolo, artista, tonalità e note"}
                </summary>
                <div className="sp-form-grid">
                  <label className="sp-form-label">
                    Titolo
                    <input
                      className="sp-input"
                      value={song.title}
                      onChange={(e) => field("title", e.target.value)}
                    />
                  </label>
                  <label className="sp-form-label">
                    Artista
                    <input
                      className="sp-input"
                      value={song.artist}
                      onChange={(e) => field("artist", e.target.value)}
                    />
                  </label>
                </div>
                <div className="sp-editor-metadata">
                  <label className="sp-form-label">
                    Tonalità
                    <input
                      className="sp-input"
                      value={song.key}
                      onChange={(e) => field("key", e.target.value)}
                      placeholder="Am"
                    />
                  </label>
                  <label className="sp-form-label">
                    BPM
                    <input
                      className="sp-input"
                      type="number"
                      min={20}
                      max={400}
                      value={song.bpm ?? ""}
                      onChange={(e) =>
                        field(
                          "bpm",
                          e.target.value ? Number(e.target.value) : null,
                        )
                      }
                    />
                  </label>
                  <label className="sp-form-label">
                    Durata
                    <input
                      className="sp-input"
                      placeholder="3:40"
                      value={song.dur}
                      onChange={(e) => field("dur", e.target.value)}
                    />
                  </label>
                </div>
                {!valid && (
                  <p className="sp-editor-help">
                    Per salvare: titolo obbligatorio, durata m:ss e BPM da 20 a
                    400 (oppure vuoti).
                  </p>
                )}
                <div className="sp-form-grid">
                  <label className="sp-form-label">
                    Preparazione
                    <select
                      className="sp-input"
                      value={song.status}
                      onChange={(e) =>
                        field("status", e.target.value as Song["status"])
                      }
                    >
                      {Object.entries(labels).map(([v, l]) => (
                        <option key={v} value={v}>
                          {l}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label className="sp-form-label">
                    Tag, separati da virgola
                    <input
                      className="sp-input"
                      value={song.tags.join(",")}
                      onChange={(e) => field("tags", e.target.value.split(","))}
                    />
                  </label>
                </div>
                <label className="sp-form-label">
                  Appunto per la prossima prova
                  <textarea
                    className="sp-input"
                    value={song.practice}
                    onChange={(e) => field("practice", e.target.value)}
                  />
                </label>
                <label className="sp-form-label">
                  Nota per tutta la band
                  <textarea
                    className="sp-input"
                    value={song.notes.Tutti || ""}
                    onChange={(e) =>
                      field("notes", { ...song.notes, Tutti: e.target.value })
                    }
                  />
                </label>
              </details>
              <div
                className="sp-editor-modes"
                aria-label="Modalità di modifica"
              >
                <button
                  className={mode === "visual" ? "active" : ""}
                  aria-pressed={mode === "visual"}
                  onClick={() => setMode("visual")}
                >
                  Accordi e sezioni
                </button>
                <button
                  className={mode === "source" ? "active" : ""}
                  aria-pressed={mode === "source"}
                  onClick={() => setMode("source")}
                >
                  Testo ChordPro
                </button>
                <button onClick={() => setTransposing(!transposing)}>
                  Trasponi il brano
                </button>
              </div>
              {transposing && (
                <div className="sp-editor-transpose">
                  <strong>Cambia gli accordi di questo arrangiamento</strong>
                  <p>
                    Modifica accordi e tonalità del brano salvato. La
                    trasposizione personale del lettore resta invariata.
                  </p>
                  <label>
                    Semitoni
                    <select
                      className="sp-input"
                      value={steps}
                      onChange={(e) => setSteps(Number(e.target.value))}
                    >
                      {Array.from({ length: 23 }, (_, n) => n - 11)
                        .filter((n) => n !== 0)
                        .map((n) => (
                          <option key={n} value={n}>
                            {n > 0 ? "+" : ""}
                            {n}
                          </option>
                        ))}
                    </select>
                  </label>
                  <button
                    className="sp-secondary"
                    onClick={() => {
                      change({
                        ...song,
                        body: transposeBody(song.body, steps),
                        key:
                          isChord(song.key) && song.key !== "N.C."
                            ? transpose(song.key, steps)
                            : song.key,
                      });
                      setTransposing(false);
                    }}
                  >
                    Applica trasposizione al brano
                  </button>
                  <p className="sp-editor-help">
                    Si trasportano gli accordi riconosciuti in notazione C / Am
                    / Bb; le altre annotazioni restano intatte. Puoi annullare.
                  </p>
                </div>
              )}
              {mode === "source" ? (
                <label className="sp-form-label">
                  Testo e accordi ChordPro
                  <textarea
                    className="sp-editor-source sp-input"
                    aria-label="Testo e accordi ChordPro"
                    spellCheck={false}
                    value={song.body}
                    onChange={(e) => field("body", e.target.value)}
                  />
                  <span className="sp-editor-help">
                    Scrivi [Am] davanti alla sillaba. Una sezione inizia con #,
                    per esempio # Ritornello.
                  </span>
                </label>
              ) : (
                <>
                  <p className="sp-editor-help">
                    Tocca una lettera per aggiungere un accordo. Se modifichi
                    una sezione, le frecce spostano tutto il suo passaggio.
                  </p>
                  <div className="sp-editor-insert">
                    <label>
                      Nuova sezione
                      <input
                        className="sp-input"
                        value={section}
                        onChange={(e) =>
                          setSection(e.target.value.replace(/[\r\n#]/g, ""))
                        }
                      />
                    </label>
                    <button
                      className="sp-secondary"
                      disabled={!section.trim()}
                      onClick={() => insert("# " + section.trim())}
                    >
                      Aggiungi sezione
                    </button>
                    <button className="sp-secondary" onClick={() => insert("")}>
                      Aggiungi riga
                    </button>
                    <small>Dopo la riga selezionata</small>
                  </div>
                  <div className="sp-editor-lines">
                    {lines.map((line, i) => {
                      const parsed = readLine(line),
                        heading = line.startsWith("#");
                      return (
                        <div
                          key={i}
                          className={
                            "sp-editor-line " +
                            (i === lineIndex ? "selected" : "") +
                            (heading ? " section" : "")
                          }
                        >
                          <div className="sp-editor-linehead">
                            <span>
                              {heading ? "SEZIONE" : "RIGA"} {i + 1}
                            </span>
                            <div>
                              <button
                                aria-label={`Sposta ${heading ? "sezione" : "riga"} ${i + 1} su`}
                                disabled={i === 0}
                                onClick={() => moveLine(i, -1)}
                              >
                                <ArrowUp />
                              </button>
                              <button
                                aria-label={`Sposta ${heading ? "sezione" : "riga"} ${i + 1} giù`}
                                disabled={i === lines.length - 1}
                                onClick={() => moveLine(i, 1)}
                              >
                                <ArrowDown />
                              </button>
                              <button
                                aria-label={
                                  heading
                                    ? `Rimuovi intestazione ${i + 1}`
                                    : `Elimina riga ${i + 1}`
                                }
                                onClick={() => {
                                  field(
                                    "body",
                                    lines.filter((_, n) => n !== i).join("\n"),
                                  );
                                  setTarget({
                                    line: Math.max(0, i - 1),
                                    at: 0,
                                  });
                                }}
                              >
                                <Trash2 />
                              </button>
                            </div>
                          </div>
                          {heading ? (
                            <label className="sp-form-label">
                              Nome sezione
                              <input
                                className="sp-input"
                                value={line.slice(1).trimStart()}
                                onFocus={() => setTarget({ line: i, at: 0 })}
                                onChange={(e) =>
                                  editLine(i, "# " + e.target.value)
                                }
                              />
                            </label>
                          ) : (
                            <>
                              <div
                                className="sp-editor-syllables"
                                aria-label={`Posizioni accordi riga ${i + 1}`}
                              >
                                {[...Array.from(parsed.text), ""].map(
                                  (char, n, chars) => {
                                    const offset = chars
                                      .slice(0, n)
                                      .join("").length;
                                    return (
                                      <button
                                        key={n}
                                        tabIndex={-1}
                                        className={
                                          i === lineIndex && offset === position
                                            ? "active"
                                            : ""
                                        }
                                        aria-label={`Riga ${i + 1}, posizione ${offset + 1}`}
                                        onClick={() => choose(i, offset)}
                                      >
                                        <b>
                                          {parsed.anchors
                                            .filter((a) => a.at === offset)
                                            .map((a) => a.chord)
                                            .join(" · ") || "\u00a0"}
                                        </b>
                                        <span>{char || "↵"}</span>
                                      </button>
                                    );
                                  },
                                )}
                              </div>
                              {i === lineIndex && chordTools}
                              <label className="sp-form-label">
                                Testo riga {i + 1}
                                <textarea
                                  className="sp-input"
                                  data-editor-line={i}
                                  rows={2}
                                  value={parsed.text}
                                  placeholder="Scrivi una riga del brano…"
                                  onSelect={(e) =>
                                    choose(i, e.currentTarget.selectionStart)
                                  }
                                  onChange={(e) => {
                                    const value = e.target.value.replace(
                                      /\r/g,
                                      "",
                                    );
                                    const prefix = value.slice(
                                        0,
                                        e.target.selectionStart,
                                      ),
                                      parts = prefix.split("\n");
                                    const caret = {
                                      line: i + parts.length - 1,
                                      at: parts.at(-1)!.length,
                                    };
                                    if (value.includes("\n"))
                                      pendingCaret.current = caret;
                                    editLine(i, rewriteLyrics(line, value));
                                    setTarget(caret);
                                  }}
                                />
                              </label>
                            </>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </>
              )}
              {initial.title && (
                <div className="sp-editor-delete">
                  <button
                    className="sp-secondary"
                    onClick={() => setDeleting(!deleting)}
                  >
                    <Trash2 />
                    Elimina brano
                  </button>
                  {deleting && (
                    <>
                      <p>
                        Il brano sarà rimosso anche dalle scalette. Puoi
                        recuperarlo con Annulla nello studio.
                      </p>
                      <button
                        className="sp-secondary"
                        onClick={async () => {
                          if (await remove()) clearDraft();
                          else
                            setError(
                              "Eliminazione non riuscita. La bozza resta disponibile.",
                            );
                        }}
                      >
                        Conferma eliminazione
                      </button>
                    </>
                  )}
                </div>
              )}
            </fieldset>
          </div>
          <aside className="sp-editor-preview">
            <div className="sp-editor-previewhead">
              <div>
                <span className="sp-eyebrow">ANTEPRIMA DAL VIVO</span>
                <h3>{song.title || "Il tuo brano"}</h3>
                <p>
                  {[
                    song.artist,
                    song.key,
                    song.bpm ? `${song.bpm} BPM` : "",
                    song.dur,
                  ]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </div>
              <label>
                Dimensione testo
                <select
                  className="sp-input"
                  value={font}
                  onChange={(e) => setFont(Number(e.target.value))}
                >
                  <option value={18}>Piccolo</option>
                  <option value={22}>Medio</option>
                  <option value={28}>Grande</option>
                  <option value={34}>Palco</option>
                </select>
              </label>
            </div>
            <div
              className="sp-editor-sheet"
              style={{ "--editor-font": `${font}px` } as React.CSSProperties}
            >
              <Chart body={song.body} />
            </div>
            <p className="sp-editor-help">
              Anteprima degli accordi salvati. Dopo il salvataggio, usa Invia al
              tablet per aggiornare Android.
            </p>
          </aside>
        </div>
      </section>
    </div>
  );
}
