import { useEffect, useMemo, useRef, useState } from "react";
import {
  Check,
  CheckCheck,
  ClipboardPaste,
  Search,
  Upload,
  X,
} from "lucide-react";
import { Chart } from "./Chart";
import { prepare, SAMPLE, seconds, songDefaults, type Song } from "./domain";
import {
  emptyImportFields,
  mergeImportFields,
  type ImportFields,
  type MetadataCandidate,
  type MetadataResult,
} from "./import-domain";

const DRAFT_KEY = "stagepilot-import-draft";
function readDraft() {
  try {
    return JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "null");
  } catch {
    return null;
  }
}

export function Importer({
  onSave,
  onClose,
  busy,
}: {
  onSave: (song: Song) => Promise<boolean>;
  onClose: () => void;
  busy: boolean;
}) {
  const saved = useMemo(readDraft, []);
  const [raw, setRaw] = useState<string>(saved?.raw || "");
  const [keep, setKeep] = useState<boolean>(saved?.keep || false);
  const [ready, setReady] = useState<boolean>(saved?.ready || false);
  const [fields, setFields] = useState<ImportFields>(
    saved?.fields || emptyImportFields(),
  );
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [selected, setSelected] = useState<MetadataCandidate | undefined>(
    saved?.selected,
  );
  const [candidates, setCandidates] = useState<MetadataCandidate[]>([]);
  const [auto, setAuto] = useState(false);
  const input = useRef<HTMLInputElement>(null);
  const controller = useRef<AbortController | null>(null);
  const request = useRef(0);
  const protectedFields = useRef(
    new Set<keyof ImportFields>(saved?.protectedFields || []),
  );
  const converted = useMemo(() => prepare(raw, keep), [raw, keep]);

  useEffect(() => {
    try {
      sessionStorage.setItem(
        DRAFT_KEY,
        JSON.stringify({
          raw,
          keep,
          ready,
          fields,
          selected,
          protectedFields: [...protectedFields.current],
        }),
      );
    } catch {
      /* Draft still remains in memory. */
    }
  }, [raw, keep, ready, fields, selected]);
  useEffect(
    () => () => {
      request.current++;
      controller.current?.abort();
    },
    [],
  );

  function replaceRaw(value: string) {
    request.current++;
    controller.current?.abort();
    setLoading(false);
    setRaw(value);
    setFields(emptyImportFields());
    setSelected(undefined);
    setCandidates([]);
    setMessage("");
    setReady(false);
    protectedFields.current.clear();
    setAuto(true);
  }
  function edit(key: keyof ImportFields, value: string) {
    protectedFields.current.add(key);
    setFields((f) => ({ ...f, [key]: value }));
    if (key === "title" || key === "artist") {
      request.current++;
      controller.current?.abort();
      setLoading(false);
      setSelected(undefined);
      setCandidates([]);
      setMessage(
        "Hai modificato il brano. Premi Cerca di nuovo per aggiornare i dati online.",
      );
    }
  }
  async function lookup(base: ImportFields, choice?: MetadataCandidate) {
    controller.current?.abort();
    if (choice) protectedFields.current.clear();
    const current = ++request.current;
    const abort = new AbortController();
    controller.current = abort;
    let timedOut = false;
    const timeout = setTimeout(() => {
      timedOut = true;
      abort.abort();
    }, 32000);
    setLoading(true);
    setMessage("Riconosco il testo e cerco la registrazione…");
    setCandidates([]);
    try {
      const response = await fetch("/api/metadata", {
        method: "POST",
        signal: abort.signal,
        headers: {
          "Content-Type": "application/json",
          "X-Code": sessionStorage.getItem("stagepilot-code") || "",
        },
        body: JSON.stringify({
          body: converted.body,
          title: choice?.title ?? base.title,
          artist: choice?.artist ?? base.artist,
        }),
      });
      const result = (await response.json()) as MetadataResult & {
        error?: string;
      };
      if (current !== request.current) return;
      if (!response.ok)
        throw new Error(result.error || "Ricerca online non disponibile.");
      setMessage(result.message);
      setCandidates(result.candidates || []);
      if (result.found && result.selected) {
        const pick = result.selected;
        setSelected(pick);
        setFields((f) =>
          mergeImportFields(
            f,
            {
              title: pick.title,
              artist: pick.artist,
              bpm: pick.bpm ? String(pick.bpm) : "",
              dur: pick.dur,
            },
            protectedFields.current,
            !!choice,
          ),
        );
      }
    } catch (error) {
      if (current === request.current && (timedOut || !abort.signal.aborted))
        setMessage(
          timedOut
            ? "La ricerca sta impiegando troppo tempo. Puoi riprovare; la bozza è conservata."
            : error instanceof Error
              ? error.message
              : "Ricerca non riuscita. Puoi riprovare; testo e accordi sono conservati.",
        );
    } finally {
      clearTimeout(timeout);
      if (current === request.current) setLoading(false);
    }
  }
  function convert() {
    const parsed = {
      title: converted.title,
      artist: converted.artist,
      key: converted.key,
      bpm: converted.bpm ? String(converted.bpm) : "",
      dur: converted.dur,
    };
    for (const key of Object.keys(parsed) as (keyof ImportFields)[])
      if (parsed[key]) protectedFields.current.add(key);
    const base = {
      ...parsed,
      ...Object.fromEntries(
        (Object.keys(fields) as (keyof ImportFields)[])
          .filter((k) => fields[k])
          .map((k) => [k, fields[k]]),
      ),
    };
    setFields(base);
    setReady(true);
    setAuto(false);
    if (!base.title || !base.artist || !base.bpm || !base.dur)
      void lookup(base);
    else
      setMessage(
        "Dati già presenti nel testo. Puoi cercare anche una registrazione online.",
      );
  }
  useEffect(() => {
    if (!auto || raw.trim().length < 15) return;
    const timer = setTimeout(convert, 850);
    return () => clearTimeout(timer);
    // A paste/typing pause starts one lookup; changing a metadata field never does.
  }, [raw, auto]);

  return (
    <>
      <div className="sp-page-head">
        <div>
          <h1 className="sp-title">Da testo a canzone.</h1>
          <p className="sp-sub">
            Incolla. StagePilot riconosce il brano e cerca i dati.
          </p>
        </div>
        <button
          className="sp-iconbtn"
          onClick={onClose}
          aria-label="Chiudi importazione"
        >
          <X />
        </button>
      </div>
      <div className="sp-import-grid">
        <div>
          <div className="sp-row sp-between">
            <span className="sp-sub">
              <span className="sp-step">1</span>INCOLLA
            </span>
            <button className="sp-link" onClick={() => replaceRaw(SAMPLE)}>
              Usa l’esempio
            </button>
          </div>
          <label className="sp-form-label">
            Testo da Ultimate Guitar o ChordPro
            <textarea
              className="sp-input"
              spellCheck={false}
              value={raw}
              onChange={(e) => replaceRaw(e.target.value)}
              placeholder="Incolla qui testo e accordi…"
            />
          </label>
          <label className="sp-checkbox">
            <input
              type="checkbox"
              checked={keep}
              onChange={(e) => setKeep(e.target.checked)}
            />
            Mantieni tablatura per chitarra
          </label>
          <div className="sp-end-actions">
            <button
              className="sp-secondary"
              onClick={() => input.current?.click()}
            >
              <Upload />
              Apri file
            </button>
            <button
              className="sp-primary"
              disabled={!raw.trim() || loading}
              onClick={convert}
            >
              {loading ? "Ricerca in corso…" : "Pulisci e riconosci"}
            </button>
          </div>
          <input
            ref={input}
            hidden
            type="file"
            accept=".txt,.cho,.crd,.chordpro,.pro"
            onChange={async (e) => {
              const f = e.target.files?.[0];
              if (f && f.size <= 80000) replaceRaw(await f.text());
              else if (f)
                setMessage(
                  "Il file è troppo grande: massimo 80 KB per l’importazione.",
                );
              e.target.value = "";
            }}
          />
          <p className="sp-tiny" style={{ marginTop: 13 }}>
            Riconoscimento online con LRCLIB e Deezer. Le parole e gli accordi
            restano i tuoi. Senza rete puoi compilare i dati a mano. La bozza
            resta in questa scheda anche se ricarichi.
          </p>
        </div>
        <div>
          {ready ? (
            <>
              <span className="sp-sub">
                <span className="sp-step">2</span>CONTROLLA IL BRANO
              </span>
              <div className="sp-form-grid">
                {(
                  [
                    ["title", "Titolo"],
                    ["artist", "Artista"],
                    ["bpm", "BPM"],
                    ["dur", "Durata"],
                    ["key", "Tonalità del tuo arrangiamento"],
                  ] as const
                ).map(([key, label]) => (
                  <label className="sp-form-label" key={key}>
                    {label}
                    <input
                      className="sp-input"
                      type={key === "bpm" ? "number" : "text"}
                      min={key === "bpm" ? 20 : undefined}
                      max={key === "bpm" ? 400 : undefined}
                      step={key === "bpm" ? 1 : undefined}
                      value={fields[key]}
                      placeholder={
                        key === "dur"
                          ? "3:40"
                          : key === "bpm"
                            ? "Non ancora disponibili"
                            : ""
                      }
                      onChange={(e) => edit(key, e.target.value)}
                    />
                  </label>
                ))}
              </div>
              <div
                className="sp-metadata"
                aria-live="polite"
                aria-busy={loading}
              >
                <p>{message || "Puoi controllare i dati o cercarli online."}</p>
                {selected && (
                  <>
                    <strong>
                      {selected.title} · {selected.artist}
                    </strong>
                    <p>
                      {selected.album}
                      {selected.dur ? ` · ${selected.dur}` : ""}
                      {selected.bpm ? ` · ${selected.bpm} BPM` : ""}
                    </p>
                    <div className="sp-row">
                      {selected.sources.map((s, i) => (
                        <a
                          key={i}
                          href={s.url}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {s.label}
                        </a>
                      ))}
                    </div>
                  </>
                )}
                <button
                  className="sp-link"
                  disabled={loading}
                  onClick={() => lookup(fields)}
                >
                  <Search />
                  Cerca di nuovo
                </button>
                {candidates.length > 0 && (
                  <details>
                    <summary>Altre versioni e artisti</summary>
                    {candidates.map((c, i) => (
                      <button
                        key={i}
                        className="sp-metadata-choice"
                        disabled={loading}
                        onClick={() => lookup(fields, c)}
                      >
                        <strong>
                          {c.title} · {c.artist}
                        </strong>
                        <span>
                          {c.album} · {c.dur}
                        </span>
                      </button>
                    ))}
                  </details>
                )}
              </div>
              <div className="sp-import-success">
                <CheckCheck />
                {converted.removed} righe di servizio rimosse
              </div>
              <div className="sp-preview-chart sp-import-chart">
                <Chart body={converted.body} />
              </div>
              <div className="sp-end-actions">
                <button
                  className="sp-primary"
                  disabled={
                    busy ||
                    loading ||
                    !fields.title.trim() ||
                    !converted.body ||
                    (!!fields.dur && !seconds(fields.dur)) ||
                    (!!fields.bpm &&
                      (!Number.isInteger(+fields.bpm) ||
                        +fields.bpm < 20 ||
                        +fields.bpm > 400))
                  }
                  onClick={async () => {
                    const ok = await onSave({
                      ...songDefaults(),
                      title: fields.title.trim(),
                      artist: fields.artist.trim(),
                      key: fields.key,
                      bpm: fields.bpm ? +fields.bpm : null,
                      dur: fields.dur,
                      capo: converted.capo,
                      body: converted.body,
                      source:
                        selected?.sources
                          .map((s) => `${s.label}: ${s.url}`)
                          .join("\n") || "",
                    });
                    if (ok) sessionStorage.removeItem(DRAFT_KEY);
                  }}
                >
                  <Check />
                  Aggiungi ai brani
                </button>
              </div>
            </>
          ) : (
            <div className="sp-drop">
              <ClipboardPaste />
              <h3>Incolla e lascia fare a StagePilot.</h3>
              <p className="sp-sub">
                Titolo, artista e dati della registrazione.
                <br />
                Poi controlli e salvi.
              </p>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
