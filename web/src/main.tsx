import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { createRoot } from "react-dom/client";
import {
  House,
  Library,
  ListMusic,
  AudioLines,
  RadioTower,
  Play,
  Plus,
  ClipboardPaste,
  ArrowUp,
  ArrowDown,
  ArrowUpRight,
  Check,
  CheckCheck,
  Clock3,
  Music2,
  TabletSmartphone,
  Search,
  X,
  Pencil,
  Trash2,
  Undo2,
  Download,
  Upload,
  Pause,
  SkipForward,
  ArrowLeft,
  Lock,
  Unlock,
  Settings2,
  Save,
  CalendarDays,
} from "lucide-react";
import {
  type Song,
  type Band,
  type Setlist,
  type DB,
  type Snapshot,
  type Page,
  labels,
  id,
  songDefaults,
  seconds,
  time,
  duration,
  removeSong,
  transpose,
  passage,
  mergeSongDraft,
} from "./domain";
import { Chart } from "./Chart";
import "./style.css";
import { Agenda, Concerts } from "./Agenda";
import { Importer } from "./Importer";

const NAV = [
  ["home", "Oggi", House],
  ["songs", "Repertorio", Library],
  ["sets", "Scalette", ListMusic],
  ["practice", "Prove", AudioLines],
  ["concerts", "Concerti", CalendarDays],
] as const;
type Mutation = (db: DB, band: Band) => void;
type Info = { address: string; code?: string; requiresCode: boolean };
type Transfer = {
  sequence: number;
  status: string;
  message: string;
  online: number;
};
async function api<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch("/api/" + path, {
    method: body ? "POST" : "GET",
    headers: {
      "Content-Type": "application/json",
      "X-Code": sessionStorage.getItem("stagepilot-code") || "",
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const result = await response.json();
  if (!response.ok)
    throw Object.assign(new Error(result.error || "Operazione non riuscita."), {
      status: response.status,
    });
  return result;
}
function Button({
  children,
  onClick,
  primary = false,
  disabled = false,
}: {
  children: ReactNode;
  onClick: () => void;
  primary?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      disabled={disabled}
      className={primary ? "sp-primary" : "sp-secondary"}
      onClick={onClick}
    >
      {children}
    </button>
  );
}
function Heading({
  title,
  sub,
  children,
}: {
  title: string;
  sub: string;
  children?: ReactNode;
}) {
  return (
    <div className="sp-page-head">
      <div>
        <h1 className="sp-title">{title}</h1>
        <p className="sp-sub">{sub}</p>
      </div>
      {children}
    </div>
  );
}
function Status({ song }: { song: Song }) {
  return (
    <span className={"sp-status " + (song.status || "study")}>
      <i />
      {labels[song.status] || labels.study}
    </span>
  );
}
function Modal({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  return (
    <div className="sp-modal-overlay" onClick={onClose}>
      <section
        className="sp-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="sp-row sp-between">
          <h2>{title}</h2>
          <button className="sp-iconbtn" aria-label="Chiudi" onClick={onClose}>
            <X />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

function App() {
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null),
    [page, setPage] = useState<Page>("home");
  const [info, setInfo] = useState<Info | null>(null),
    [error, setError] = useState(""),
    [message, setMessage] = useState(""),
    [busy, setBusy] = useState(false);
  const [practiceScope, setPracticeScope] = useState("");
  const [transfer, setTransfer] = useState<Transfer | null>(null);
  const [sending, setSending] = useState(false);
  useEffect(() => {
    let mounted = true;
    const refresh = () =>
      api<Transfer>("transfer")
        .then((t) => {
          if (mounted) setTransfer(t);
        })
        .catch(() => {});
    void refresh();
    const timer = setInterval(refresh, 2500);
    return () => {
      mounted = false;
      clearInterval(timer);
    };
  }, []);
  async function sendToTablet() {
    if (busy || sending) return;
    setSending(true);
    try {
      setTransfer(await api<Transfer>("transfer/request", {}));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Trasferimento non riuscito.");
    } finally {
      setSending(false);
    }
  }
  const [focus, setFocus] = useState<{ song: string; section: string } | null>(
    null,
  );
  const [code, setCode] = useState(""),
    [modal, setModal] = useState<"connect" | "settings" | null>(null),
    [edit, setEdit] = useState<Song | null>(null);
  const file = useRef<HTMLInputElement>(null);
  useEffect(() => {
    api<Info>("info")
      .then(setInfo)
      .catch((e) => setError(e.message));
    api<Snapshot>("state")
      .then(setSnapshot)
      .catch((e) => setError(e.message));
  }, []);
  useEffect(() => {
    if (message) {
      const t = setTimeout(() => setMessage(""), 4000);
      return () => clearTimeout(t);
    }
  }, [message]);
  useEffect(() => {
    if (edit || modal || busy || page === "stage") return;
    const t = setInterval(() => {
      if (document.visibilityState === "visible")
        api<Snapshot>("state")
          .then((next) =>
            setSnapshot((old) =>
              old && next.revision > old.revision ? next : old,
            ),
          )
          .catch(() => {});
    }, 5000);
    return () => clearInterval(t);
  }, [edit, modal, busy, page]);
  const db = snapshot?.db;
  const band = db?.bands.find((b) => b.id === db.curBand) || db?.bands[0];
  const current = band?.sets.find((s) => s.id === band.cur) || band?.sets[0];
  async function commit(change: Mutation, success = "Salvato sul PC.") {
    if (!snapshot || busy) return false;
    const next = structuredClone(snapshot.db),
      b = next.bands.find((x) => x.id === next.curBand) || next.bands[0];
    setBusy(true);
    setError("");
    try {
      change(next, b);
      setSnapshot(
        await api<Snapshot>("state", { revision: snapshot.revision, db: next }),
      );
      setMessage(success);
      return true;
    } catch (e) {
      if ((e as { status?: number }).status === 409) {
        try {
          setSnapshot(await api<Snapshot>("state"));
        } catch {}
      }
      setError((e as Error).message);
      return false;
    } finally {
      setBusy(false);
    }
  }
  async function undo() {
    if (!snapshot) return;
    try {
      setSnapshot(await api<Snapshot>("undo", { revision: snapshot.revision }));
      setMessage("Modifica precedente ripristinata.");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  function backup() {
    if (!db) return;
    const link = document.createElement("a");
    link.href = URL.createObjectURL(
      new Blob([JSON.stringify(db, null, 2)], { type: "application/json" }),
    );
    link.download =
      "StagePilot-backup-" + new Date().toISOString().slice(0, 10) + ".json";
    link.click();
    setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  }
  async function restore(f: File) {
    if (!snapshot) return;
    try {
      const next = JSON.parse(await f.text());
      if (
        !confirm(
          "Sostituire la libreria del PC con questo backup? La libreria precedente resterà nella cronologia.",
        )
      )
        return;
      setSnapshot(
        await api<Snapshot>("state", { revision: snapshot.revision, db: next }),
      );
      setMessage("Backup importato.");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  if (!db || !band)
    return (
      <div id="stagepilot-preview">
        <main className="sp-login">
          <div className="sp-logo">
            <span className="sp-logo-mark">
              <img src="/stagepilot-icon.png" alt="" width="40" height="40" />
            </span>
            StagePilot Studio
          </div>
          <h1>Il tuo studio, con te.</h1>
          <p className="sp-sub">{error || "Apertura della libreria…"}</p>
          {info?.requiresCode && (
            <form
              onSubmit={async (e) => {
                e.preventDefault();
                sessionStorage.setItem("stagepilot-code", code);
                try {
                  setSnapshot(await api<Snapshot>("state"));
                  setError("");
                } catch (e) {
                  setError((e as Error).message);
                }
              }}
            >
              <label className="sp-form-label">
                Codice mostrato sul PC
                <input
                  className="sp-input"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  inputMode="numeric"
                  maxLength={6}
                />
              </label>
              <button className="sp-primary">Collega</button>
            </form>
          )}
        </main>
      </div>
    );
  const viewedBand = focus
    ? {
        ...band,
        songs: band.songs.map((s) =>
          s.id === focus.song
            ? { ...s, body: passage(s.body, focus.section) }
            : s,
        ),
      }
    : band;
  const stageSet: Setlist | undefined = focus
    ? {
        id: "practice-view",
        name: focus.section,
        items: [{ type: "song", id: focus.song }],
      }
    : current;
  const nav = (
    <>
      {NAV.map(([p, title, Icon]) => (
        <button
          key={p}
          className={page === p ? "active" : ""}
          onClick={() => {
            if (p === "practice") setPracticeScope("");
            setPage(p);
          }}
          aria-current={page === p ? "page" : undefined}
        >
          <Icon />
          <span>{title}</span>
        </button>
      ))}
    </>
  );
  return (
    <div id="stagepilot-preview">
      <div className="sp-app sp-web" aria-busy={busy}>
        {page === "stage" ? (
          <Stage
            band={viewedBand}
            set={stageSet}
            onClose={() => {
              setPage(focus ? "practice" : "sets");
              setFocus(null);
            }}
          />
        ) : (
          <>
            <aside className="sp-sidebar">
              <div className="sp-logo">
                <span className="sp-logo-mark">
                  <img src="/stagepilot-icon.png" alt="" width="40" height="40" />
                </span>
                StagePilot
              </div>
              <nav className="sp-navlist" aria-label="Navigazione studio">
                {nav}
              </nav>
              <div className="sp-side-bottom">
                <button
                  className="sp-secondary"
                  onClick={() => setModal("connect")}
                >
                  <TabletSmartphone />
                  Collega Android
                </button>
                <button
                  className="sp-secondary"
                  onClick={() => setModal("settings")}
                >
                  <Settings2 />
                  Impostazioni
                </button>
                <div className="sp-band">
                  <span className="sp-avatar">
                    {band.name.slice(0, 2).toUpperCase()}
                  </span>
                  <div>
                    {band.name}
                    <br />
                    <span className="sp-tiny">La tua band</span>
                  </div>
                </div>
              </div>
            </aside>
            <div className="sp-content">
              <header className="sp-top">
                <span className="sp-kicker">STUDIO / {band.name}</span>
                <div className="sp-row">
                  <button
                    className="sp-primary sp-send-tablet"
                    disabled={busy || sending}
                    onClick={sendToTablet}
                  >
                    <TabletSmartphone />
                    {sending ? "Invio…" : "Invia al tablet"}
                  </button>
                  <button
                    className="sp-iconbtn"
                    onClick={undo}
                    aria-label="Annulla ultima modifica"
                  >
                    <Undo2 />
                  </button>
                  <button
                    className="sp-iconbtn"
                    onClick={backup}
                    aria-label="Esporta backup"
                  >
                    <Download />
                  </button>
                  <span className="sp-connected">
                    <CheckCheck />
                    {busy ? "SALVATAGGIO…" : "SALVATO SUL PC"}
                  </span>
                </div>
              </header>
              <main className="sp-main">
                <div
                  className={"sp-transfer " + (transfer?.status || "idle")}
                  aria-live="polite"
                >
                  <TabletSmartphone />
                  <div>
                    <strong>
                      {transfer?.status === "done"
                        ? "Tablet aggiornato"
                        : "Dal PC al tuo tablet"}
                    </strong>
                    <p>
                      {transfer?.message ||
                        "Apri StagePilot sul tablet per ricevere brani e scalette."}
                    </p>
                  </div>
                  <button
                    className="sp-link"
                    onClick={() => setModal("connect")}
                  >
                    Come collegarlo
                  </button>
                </div>
                {error && (
                  <div className="sp-error" role="alert">
                    {error}
                    <button
                      onClick={() => setError("")}
                      aria-label="Chiudi errore"
                    >
                      <X />
                    </button>
                    <button
                      onClick={() =>
                        api<Snapshot>("state")
                          .then(setSnapshot)
                          .then(() => setError(""))
                          .catch((e) => setError(e.message))
                      }
                    >
                      Ricarica libreria
                    </button>
                  </div>
                )}
                {page === "home" && (
                  <Home
                    band={band}
                    set={current}
                    navigate={setPage}
                    edit={setEdit}
                  />
                )}
                {page === "songs" && (
                  <Repertoire
                    band={band}
                    edit={setEdit}
                    onImport={() => setPage("import")}
                  />
                )}
                {page === "sets" && (
                  <Sets
                    band={band}
                    set={current}
                    commit={commit}
                    onStage={() => setPage("stage")}
                  />
                )}
                {page === "import" && (
                  <Importer
                    busy={busy}
                    onClose={() => setPage("songs")}
                    onSave={async (song) => {
                      if (
                        await commit((_, b) => {
                          b.songs.push(song);
                        }, "Brano aggiunto al repertorio.")
                      ) {
                        setPage("songs");
                        return true;
                      }
                      return false;
                    }}
                  />
                )}
                {page === "practice" && (
                  <Agenda
                    band={band}
                    commit={commit}
                    scopeSetId={practiceScope}
                    openPassage={(song, section) => {
                      setFocus({ song, section });
                      setPage("stage");
                    }}
                  />
                )}
                {page === "concerts" && (
                  <Concerts
                    band={band}
                    commit={commit}
                    openStage={async (setId) => {
                      if (
                        await commit((_, b) => {
                          b.cur = setId;
                        })
                      )
                        setPage("stage");
                    }}
                    openPractice={async (setId) => {
                      if (
                        await commit((_, b) => {
                          b.cur = setId;
                        })
                      ) {
                        setPracticeScope(setId);
                        setPage("practice");
                      }
                    }}
                  />
                )}
              </main>
              <nav className="sp-bottom" aria-label="Navigazione">
                {nav}
              </nav>
            </div>
          </>
        )}
        {edit && (
          <SongEditor
            initial={edit}
            busy={busy}
            close={() => setEdit(null)}
            save={async (s) => {
              if (
                await commit((_, b) => {
                  const at = b.songs.findIndex((x) => x.id === s.id);
                  if (at < 0) {
                    if (edit.title)
                      throw new Error(
                        "Questo brano è stato eliminato da un altro dispositivo. La bozza resta aperta.",
                      );
                    b.songs.push(s);
                  } else b.songs[at] = mergeSongDraft(edit, s, b.songs[at]);
                })
              ) {
                setEdit(null);
              }
            }}
            remove={async () => {
              if (
                await commit(
                  (_, b) => removeSong(b, edit.id),
                  "Brano eliminato. Puoi recuperarlo con Annulla.",
                )
              )
                setEdit(null);
            }}
          />
        )}
        {modal === "connect" && (
          <Modal title="Collega il tuo Android" onClose={() => setModal(null)}>
            <p className="sp-sub">
              Le modifiche sono salvate sul PC. Per portare brani e scalette sul
              tablet, lascia aperto questo studio e usa la stessa Wi-Fi. Sul
              tablet apri Oggi → Sincronizza con il PC. Al primo collegamento,
              apri Impostazioni → Studio web e inserisci questi dati.
            </p>
            <label className="sp-form-label">
              Indirizzo del PC
              <input
                className="sp-input"
                readOnly
                value={info?.address || ""}
              />
            </label>
            <label className="sp-form-label">
              Codice di collegamento
              <input
                className="sp-input"
                readOnly
                value={info?.code || "Visibile solo sul PC"}
              />
            </label>
            <div className="sp-note" style={{ marginTop: 18 }}>
              Tocca Sincronizza sul tablet. Le modifiche indipendenti si
              uniscono; per quelle in conflitto scegli tu quale tenere.
            </div>
            <Button
              onClick={() => {
                api<Snapshot>("state").then(setSnapshot);
                setModal(null);
              }}
            >
              Ricarica dopo la sincronizzazione
            </Button>
          </Modal>
        )}
        {modal === "settings" && (
          <Modal title="Il tuo studio" onClose={() => setModal(null)}>
            <label className="sp-form-label">
              Gruppo
              <select
                className="sp-input"
                value={band.id}
                onChange={(e) =>
                  commit((d) => {
                    d.curBand = e.target.value;
                  })
                }
              >
                {db.bands.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="sp-end-actions">
              <Button
                onClick={() => {
                  const name = prompt("Nome del gruppo", band.name);
                  if (name?.trim())
                    commit((_, b) => {
                      b.name = name.trim();
                    });
                }}
              >
                <Pencil />
                Rinomina
              </Button>
              <Button
                onClick={() => {
                  const name = prompt("Nome del nuovo gruppo");
                  if (name?.trim())
                    commit((d) => {
                      const b: Band = {
                        id: id("b"),
                        name: name.trim(),
                        me: "Tutti",
                        songs: [],
                        sets: [
                          { id: id("set"), name: "Prima scaletta", items: [] },
                        ],
                        cur: "",
                        events: [],
                        rehearsals: [],
                      };
                      b.cur = b.sets[0].id;
                      d.bands.push(b);
                      d.curBand = b.id;
                    });
                }}
              >
                <Plus />
                Nuovo gruppo
              </Button>
            </div>
            <div className="sp-hairline" />
            <div className="sp-end-actions">
              <Button onClick={backup}>
                <Download />
                Esporta backup
              </Button>
              <Button onClick={() => file.current?.click()}>
                <Upload />
                Importa backup
              </Button>
            </div>
          </Modal>
        )}
        <input
          hidden
          ref={file}
          type="file"
          accept=".json,application/json"
          onChange={(e) => {
            const f = e.target.files?.[0];
            if (f) restore(f);
            e.target.value = "";
          }}
        />
        {message && (
          <div className="sp-toast" role="status">
            <Check />
            {message}
          </div>
        )}
      </div>
    </div>
  );
}

function SongRow({ song, open }: { song: Song; open: (s: Song) => void }) {
  return (
    <div className="sp-songrow">
      <button className="sp-song-open" onClick={() => open(song)}>
        <span className="sp-cover">
          <Music2 />
        </span>
        <span className="sp-songtext">
          <strong>{song.title}</strong>
          <small>
            {[song.artist, song.bpm ? `${song.bpm} BPM` : ""]
              .filter(Boolean)
              .join(" · ")}
          </small>
        </span>
      </button>
      <span className="sp-hide-mobile">
        <Status song={song} />
      </span>
      <span className="sp-key">{song.key || "—"}</span>
      <span className="sp-songmeta">{song.dur || "—"}</span>
    </div>
  );
}
function Home({
  band,
  set,
  navigate,
  edit,
}: {
  band: Band;
  set?: Setlist;
  navigate: (p: Page) => void;
  edit: (s: Song) => void;
}) {
  const ready = band.songs.filter((s) => s.status === "ready").length;
  return (
    <>
      <Heading
        title="La tua musica. Il tuo prossimo palco."
        sub="Tutto pronto, un brano alla volta."
      />
      <div className="sp-web-grid">
        <div>
          <section className="sp-hero">
            <span className="sp-pill">
              <RadioTower />
              LA TUA PROSSIMA SERATA
            </span>
            <h2>{set?.name || "Prepara la tua prima scaletta"}</h2>
            <p className="sp-sub">{band.name}</p>
            <div className="sp-hero-meta">
              <span>
                <ListMusic />
                {set?.items.filter((i) => i.type === "song").length || 0} brani
              </span>
              <span>
                <Clock3 />
                {time(duration(band, set))} minuti
              </span>
            </div>
            <Button
              primary
              disabled={!set?.items.length}
              onClick={() => navigate("stage")}
            >
              <Play />
              Vai sul palco
            </Button>
          </section>
          <div className="sp-stats">
            <div className="sp-stat">
              <strong>{band.songs.length}</strong>
              <small>brani in repertorio</small>
            </div>
            <div className="sp-stat">
              <strong>{ready}</strong>
              <small>pronti da suonare</small>
            </div>
            <div className="sp-stat">
              <strong>{band.sets.length}</strong>
              <small>scalette</small>
            </div>
          </div>
        </div>
        <div>
          <div className="sp-section-head">
            <h2>La prossima prova</h2>
            <button className="sp-link" onClick={() => navigate("practice")}>
              Apri <ArrowUpRight />
            </button>
          </div>
          <button className="sp-task" onClick={() => navigate("practice")}>
            <span className="sp-task-icon">
              <AudioLines />
            </span>
            <span>
              <strong>
                {band.songs.length - ready} brani su cui concentrarsi
              </strong>
              <span className="sp-tiny">I passaggi da preparare, già qui.</span>
            </span>
          </button>
          <div className="sp-section-head">
            <h2>Dal tuo repertorio</h2>
          </div>
          {band.songs.slice(-2).map((s) => (
            <SongRow key={s.id} song={s} open={edit} />
          ))}
          {!band.songs.length && (
            <p className="sp-sub">Incolla il primo brano per iniziare.</p>
          )}
          <div className="sp-quick">
            <Button onClick={() => navigate("import")}>
              <ClipboardPaste />
              Importa brano
            </Button>
            <Button onClick={() => navigate("sets")}>
              <ListMusic />
              Le tue scalette
            </Button>
          </div>
          <div className="sp-hairline" />
          <div className="sp-note">
            Prepara sul PC. Suona sul tablet.
            <br />I tuoi brani e i tuoi appunti, anche senza Internet.
          </div>
        </div>
      </div>
    </>
  );
}
function Repertoire({
  band,
  edit,
  onImport,
}: {
  band: Band;
  edit: (s: Song) => void;
  onImport: () => void;
}) {
  const [query, setQuery] = useState(""),
    [filter, setFilter] = useState("all");
  const shown = band.songs.filter(
    (s) =>
      (filter === "all" || (s.status || "study") === filter) &&
      (s.title + " " + s.artist + " " + (s.tags || []).join(" "))
        .toLowerCase()
        .includes(query.toLowerCase()),
  );
  return (
    <>
      <Heading
        title="Repertorio"
        sub={`${band.songs.length} brani. Un posto per tutte le tue canzoni.`}
      >
        <Button primary onClick={onImport}>
          <ClipboardPaste />
          Importa
        </Button>
      </Heading>
      <label className="sp-search">
        <Search />
        <input
          aria-label="Cerca brani"
          placeholder="Cerca titolo, artista o tag…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </label>
      <div className="sp-filters">
        {[
          ["all", "Tutti"],
          ["ready", "Pronti"],
          ["rehearse", "Da ripassare"],
          ["study", "Da studiare"],
        ].map(([f, label]) => (
          <button
            key={f}
            className={"sp-chip " + (filter === f ? "active" : "")}
            onClick={() => setFilter(f)}
          >
            {label}
          </button>
        ))}
      </div>
      <div className="sp-list-head">
        <span>BRANO / ARTISTA</span>
        <span>TONALITÀ · DURATA</span>
      </div>
      {shown.map((s) => (
        <SongRow key={s.id} song={s} open={edit} />
      ))}
      {!shown.length && <div className="sp-empty">Nessun brano trovato.</div>}
      <div className="sp-end-actions">
        <Button onClick={() => edit(songDefaults())}>
          <Plus />
          Nuovo brano
        </Button>
      </div>
    </>
  );
}
function SongEditor({
  initial,
  save,
  remove,
  close,
  busy,
}: {
  initial: Song;
  save: (s: Song) => void;
  remove: () => void;
  close: () => void;
  busy: boolean;
}) {
  const [song, setSong] = useState<Song>({ ...songDefaults(), ...initial }),
    [confirmDelete, setConfirmDelete] = useState(false);
  const field = <K extends keyof Song>(k: K, v: Song[K]) =>
    setSong((s) => ({ ...s, [k]: v }));
  const valid =
    song.title.trim() &&
    (!song.dur || seconds(song.dur) > 0) &&
    (!song.bpm || (song.bpm >= 20 && song.bpm <= 400));
  return (
    <Modal
      title={initial.title ? "Modifica brano" : "Nuovo brano"}
      onClose={close}
    >
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
      <div className="sp-form-grid">
        <label className="sp-form-label">
          Tonalità
          <input
            className="sp-input"
            value={song.key}
            placeholder="Am, F#m…"
            onChange={(e) => field("key", e.target.value)}
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
              field("bpm", e.target.value ? Number(e.target.value) : null)
            }
          />
        </label>
      </div>
      <div className="sp-form-grid">
        <label className="sp-form-label">
          Durata
          <input
            className="sp-input"
            value={song.dur}
            placeholder="3:40"
            onChange={(e) => field("dur", e.target.value)}
          />
        </label>
        <label className="sp-form-label">
          Preparazione
          <select
            className="sp-input"
            value={song.status}
            onChange={(e) => field("status", e.target.value as Song["status"])}
          >
            {Object.entries(labels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="sp-form-label">
        Tag, separati da virgola
        <input
          className="sp-input"
          value={song.tags.join(", ")}
          onChange={(e) =>
            field(
              "tags",
              e.target.value.split(",").map((t) => t.trim()),
            )
          }
        />
      </label>
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
      <label className="sp-form-label">
        Testo e accordi ChordPro
        <textarea
          className="sp-input"
          spellCheck={false}
          value={song.body}
          onChange={(e) => field("body", e.target.value)}
        />
      </label>
      <div className="sp-end-actions">
        <Button onClick={close}>Annulla</Button>
        <Button
          primary
          disabled={!valid || busy}
          onClick={() =>
            save({
              ...song,
              title: song.title.trim(),
              tags: song.tags.filter(Boolean),
            })
          }
        >
          <Save />
          Salva
        </Button>
      </div>
      {initial.title && (
        <button
          className="sp-link sp-danger"
          onClick={() => (confirmDelete ? remove() : setConfirmDelete(true))}
        >
          <Trash2 />
          {confirmDelete
            ? "Conferma eliminazione (recuperabile con Annulla)"
            : "Elimina brano"}
        </button>
      )}
    </Modal>
  );
}

type Commit = (change: Mutation, success?: string) => Promise<boolean>;
function Sets({
  band,
  set,
  commit,
  onStage,
}: {
  band: Band;
  set?: Setlist;
  commit: Commit;
  onStage: () => void;
}) {
  const [add, setAdd] = useState(false),
    [mark, setMark] = useState(false),
    [label, setLabel] = useState("Pausa"),
    [dur, setDur] = useState("5:00");
  const drag = useRef<number | null>(null);
  const change = (fn: (s: Setlist) => void) =>
    commit((_, b) => {
      const s = b.sets.find((x) => x.id === set?.id);
      if (s) fn(s);
    });
  function move(from: number, to: number) {
    if (!set || to < 0 || to >= set.items.length) return;
    change((s) => {
      s.items.splice(to, 0, s.items.splice(from, 1)[0]);
    });
  }
  return (
    <>
      <Heading title="Le tue scalette" sub="La serata prende forma qui.">
        <Button
          onClick={() => {
            const name = prompt("Nome della scaletta");
            if (name?.trim())
              commit((_, b) => {
                const s = { id: id("set"), name: name.trim(), items: [] };
                b.sets.push(s);
                b.cur = s.id;
              });
          }}
        >
          <Plus />
          Nuova scaletta
        </Button>
      </Heading>
      <label className="sp-form-label">
        Scaletta
        <select
          className="sp-input"
          value={set?.id || ""}
          onChange={(e) =>
            commit((_, b) => {
              b.cur = e.target.value;
            })
          }
        >
          {band.sets.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      </label>
      {set && (
        <>
          <section className="sp-set-info">
            <span className="sp-pill">
              <RadioTower />
              LIVE
            </span>
            <div className="sp-row sp-between">
              <h2>{set.name}</h2>
              <button
                className="sp-iconbtn"
                aria-label="Rinomina scaletta"
                onClick={() => {
                  const name = prompt("Nome della scaletta", set.name);
                  if (name?.trim())
                    change((s) => {
                      s.name = name.trim();
                    });
                }}
              >
                <Pencil />
              </button>
            </div>
            <div className="sp-row sp-between">
              <span className="sp-sub">
                {set.items.filter((i) => i.type === "song").length} brani ·{" "}
                {time(duration(band, set))} minuti
              </span>
              <Button primary disabled={!set.items.length} onClick={onStage}>
                <Play />
                Avvia il set
              </Button>
            </div>
          </section>
          <div className="sp-list-head">
            <span>ORDINE DEL SET</span>
            <span>SPOSTA / RIMUOVI</span>
          </div>
          {set.items.map((item, i) => {
            const s = band.songs.find((s) => s.id === item.id);
            return (
              <div
                className="sp-songrow"
                key={i}
                draggable
                onDragStart={() => {
                  drag.current = i;
                }}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  if (drag.current !== null) move(drag.current, i);
                  drag.current = null;
                }}
              >
                <span className="sp-number">
                  {String(i + 1).padStart(2, "0")}
                </span>
                <span className="sp-songtext">
                  <strong>
                    {item.type === "mark" ? item.label : s?.title}
                  </strong>
                  <small>
                    {item.type === "mark"
                      ? "Pausa · " + (item.dur || "—")
                      : [s?.bpm ? `${s.bpm} BPM` : "", s?.key, s?.dur]
                          .filter(Boolean)
                          .join(" · ")}
                  </small>
                </span>
                <div className="sp-reorder">
                  <button
                    disabled={!i}
                    onClick={() => move(i, i - 1)}
                    aria-label={`Sposta ${s?.title || item.label} su`}
                  >
                    <ArrowUp />
                  </button>
                  <button
                    disabled={i === set.items.length - 1}
                    onClick={() => move(i, i + 1)}
                    aria-label={`Sposta ${s?.title || item.label} giù`}
                  >
                    <ArrowDown />
                  </button>
                  <button
                    onClick={() =>
                      change((s) => {
                        s.items.splice(i, 1);
                      })
                    }
                    aria-label="Rimuovi dalla scaletta"
                  >
                    <X />
                  </button>
                </div>
              </div>
            );
          })}
          <div className="sp-end-actions">
            <Button onClick={() => setAdd(true)}>
              <Plus />
              Aggiungi brano
            </Button>
            <Button onClick={() => setMark(true)}>
              <Clock3 />
              Inserisci pausa
            </Button>
            <Button
              onClick={() =>
                commit((_, b) => {
                  const copy = structuredClone(set);
                  copy.id = id("set");
                  copy.name += " (copia)";
                  b.sets.push(copy);
                  b.cur = copy.id;
                })
              }
            >
              Duplica
            </Button>
          </div>
          {band.sets.length > 1 && (
            <button
              className="sp-link sp-danger"
              onClick={() => {
                if (
                  confirm(
                    "Eliminare questa scaletta? I brani restano nel repertorio.",
                  )
                )
                  commit((_, b) => {
                    b.sets = b.sets.filter((s) => s.id !== set.id);
                    b.cur = b.sets[0].id;
                  });
              }}
            >
              <Trash2 />
              Elimina scaletta
            </button>
          )}
        </>
      )}
      {add && (
        <Modal title="Aggiungi alla scaletta" onClose={() => setAdd(false)}>
          {band.songs.map((s) => (
            <button
              className="sp-task"
              style={{ marginTop: 10 }}
              key={s.id}
              onClick={async () => {
                if (
                  await change((t) => {
                    t.items.push({ type: "song", id: s.id });
                  })
                )
                  setAdd(false);
              }}
            >
              <span className="sp-songtext">
                <strong>{s.title}</strong>
                <small>{s.artist}</small>
              </span>
              <Plus />
            </button>
          ))}
          {!band.songs.length && (
            <p className="sp-sub">Importa il primo brano dal repertorio.</p>
          )}
        </Modal>
      )}
      {mark && (
        <Modal title="Pausa o cambio strumento" onClose={() => setMark(false)}>
          <label className="sp-form-label">
            Nome
            <input
              className="sp-input"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
            />
          </label>
          <label className="sp-form-label">
            Durata
            <input
              className="sp-input"
              value={dur}
              onChange={(e) => setDur(e.target.value)}
            />
          </label>
          <Button
            primary
            disabled={!label.trim() || (!!dur && !seconds(dur))}
            onClick={async () => {
              if (
                await change((s) => {
                  s.items.push({ type: "mark", label: label.trim(), dur });
                })
              )
                setMark(false);
            }}
          >
            Inserisci
          </Button>
        </Modal>
      )}
    </>
  );
}

function Stage({
  band,
  set,
  onClose,
}: {
  band: Band;
  set?: Setlist;
  onClose: () => void;
}) {
  const [at, setAt] = useState(0),
    [playing, setPlaying] = useState(false),
    [steps, setSteps] = useState(0),
    [size, setSize] = useState(24),
    [locked, setLocked] = useState(false);
  const sheet = useRef<HTMLDivElement>(null),
    item = set?.items[at],
    song = band.songs.find((s) => s.id === item?.id),
    next = set?.items[at + 1];
  function go(n: number) {
    if (set && at + n >= 0 && at + n < set.items.length) {
      setAt(at + n);
      setSteps(0);
      setPlaying(false);
    }
  }
  useEffect(() => {
    if (sheet.current) sheet.current.scrollTop = 0;
  }, [at]);
  useEffect(() => {
    if (!playing) return;
    let frame = 0,
      last = performance.now();
    const tick = (now: number) => {
      const el = sheet.current;
      if (!el) return;
      const max = el.scrollHeight - el.clientHeight;
      el.scrollTop +=
        (((now - last) / 1000) * max) / (seconds(song?.dur) || 180);
      last = now;
      if (el.scrollTop >= max) {
        setPlaying(false);
        return;
      }
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [playing, song?.dur]);
  useEffect(() => {
    const listener = (e: KeyboardEvent) => {
      if (e.key === "ArrowRight" || e.key === "PageDown") {
        e.preventDefault();
        go(1);
      }
      if (e.key === "ArrowLeft" || e.key === "PageUp") {
        e.preventDefault();
        go(-1);
      }
      if (e.key === " ") {
        e.preventDefault();
        setPlaying((p) => !p);
      }
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", listener);
    return () => window.removeEventListener("keydown", listener);
  }, [at, set]);
  useEffect(() => {
    let lock: { release: () => Promise<void> } | undefined;
    const nav = navigator as Navigator & {
      wakeLock?: {
        request: (s: string) => Promise<{ release: () => Promise<void> }>;
      };
    };
    nav.wakeLock
      ?.request("screen")
      .then((v) => {
        lock = v;
      })
      .catch(() => {});
    return () => {
      lock?.release();
    };
  }, []);
  return (
    <div className={"sp-stage " + (locked ? "locked" : "")}>
      <div className="sp-stage-top">
        <button
          className="sp-iconbtn"
          aria-label="Torna alla scaletta"
          onClick={onClose}
        >
          <ArrowLeft />
        </button>
        <div>
          <div className="sp-kicker">
            {at + 1} / {set?.items.length || 0} · {set?.name}
          </div>
          <h1 className="sp-stage-title">
            {item?.type === "mark"
              ? item.label
              : song?.title || "Scaletta vuota"}
          </h1>
          <div className="sp-stage-sub">
            {song
              ? [song.artist, song.bpm ? `${song.bpm} BPM` : "", song.dur]
                  .filter(Boolean)
                  .join(" · ")
              : item?.dur}
          </div>
        </div>
        <span className="sp-stage-key">
          {transpose(song?.key || "", steps)}
        </span>
      </div>
      <div
        ref={sheet}
        className="sp-stage-chart"
        style={{ fontSize: size, pointerEvents: locked ? "none" : "auto" }}
      >
        {song?.capo ? (
          <div className="sp-pill">CAPOTASTO {song.capo}</div>
        ) : null}
        {song?.notes?.Tutti && (
          <div className="sp-note">{song.notes.Tutti}</div>
        )}
        {song ? (
          <Chart body={song.body} steps={steps} />
        ) : (
          <div className="sp-empty">
            {item?.label || "Aggiungi un brano alla scaletta."}
          </div>
        )}
      </div>
      <div className="sp-stage-next">
        PROSSIMO{" "}
        <strong>
          {next?.type === "mark"
            ? next.label
            : band.songs.find((s) => s.id === next?.id)?.title ||
              "Fine scaletta"}
        </strong>
      </div>
      <div className="sp-stage-controls">
        <div className="sp-transpose">
          <button
            aria-label="Abbassa un semitono"
            onClick={() => setSteps((s) => Math.max(-11, s - 1))}
          >
            −
          </button>
          <span>
            {steps > 0 ? "+" : ""}
            {steps}
          </span>
          <button
            aria-label="Alza un semitono"
            onClick={() => setSteps((s) => Math.min(11, s + 1))}
          >
            +
          </button>
        </div>
        <Button primary disabled={!song} onClick={() => setPlaying((p) => !p)}>
          {playing ? <Pause /> : <Play />}
          {playing ? "Pausa" : "Scorri"}
        </Button>
        <button
          className="sp-iconbtn"
          disabled={!next}
          aria-label="Prossimo brano"
          onClick={() => go(1)}
        >
          <SkipForward />
        </button>
        <button
          className="sp-iconbtn"
          aria-label="Dimensione testo"
          onClick={() => setSize((s) => (s >= 34 ? 20 : s + 2))}
        >
          A+
        </button>
        <button
          className="sp-iconbtn"
          aria-label={locked ? "Sblocca touch" : "Blocca touch"}
          onClick={() => setLocked((s) => !s)}
        >
          {locked ? <Lock /> : <Unlock />}
        </button>
      </div>
    </div>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
