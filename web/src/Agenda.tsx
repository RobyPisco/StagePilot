import { useEffect, useRef, useState, type ReactNode } from "react";
import {
  Plus,
  Play,
  Pause,
  X,
  Check,
  Pencil,
  Trash2,
  CalendarDays,
} from "lucide-react";
import {
  type DB,
  type Band,
  type Task,
  type Gig,
  id,
  time,
  sections,
  preparation,
} from "./domain";
import { Dossier } from "./Dossier";
import { mergeGigDraft } from "./dossier-domain";

type Commit = (
  fn: (db: DB, band: Band) => void,
  msg?: string,
) => Promise<boolean>;
function Modal({
  title,
  close,
  children,
}: {
  title: string;
  close: () => void;
  children: ReactNode;
}) {
  return (
    <div className="sp-modal-overlay">
      <section
        className="sp-modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="sp-row sp-between">
          <h2>{title}</h2>
          <button className="sp-iconbtn" aria-label="Chiudi" onClick={close}>
            <X />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}
export function Agenda({
  band,
  commit,
  openPassage,
  scopeSetId = "",
}: {
  band: Band;
  commit: Commit;
  openPassage: (song: string, section: string) => void;
  scopeSetId?: string;
}) {
  const [draft, setDraft] = useState<Task | null>(null),
    [filter, setFilter] = useState("open"),
    [started, setStarted] = useState(0),
    [elapsed, setElapsed] = useState(0);
  const session = useRef<string[]>([]);
  const scopedSet = band.sets.find((s) => s.id === scopeSetId),
    scopedSongs = scopedSet ? new Set(scopedSet.items.map((i) => i.id)) : null;
  const tasks = (band.practiceTasks || []).filter(
      (t) => !scopedSongs || scopedSongs.has(t.songId),
    ),
    pending = tasks.filter((t) => !t.done),
    shown = tasks.filter((t) =>
      filter === "done"
        ? t.done
        : !t.done && (filter !== "mine" || ["Tutti", band.me].includes(t.role)),
    );
  useEffect(() => {
    if (!started) return;
    const timer = setInterval(
      () => setElapsed(Math.floor((performance.now() - started) / 1000)),
      500,
    );
    return () => clearInterval(timer);
  }, [started]);
  return (
    <>
      <div className="sp-page-head">
        <div>
          <h1 className="sp-title">La prova, con un obiettivo.</h1>
          <p className="sp-sub">
            Passaggi precisi. Decisioni che non si perdono.
          </p>
        </div>
      </div>
      <section className="sp-practice-hero">
        <span className="sp-pill">AGENDA DELLA BAND</span>
        <h2>
          {pending.length} {pending.length === 1 ? "passaggio" : "passaggi"} da
          risolvere
        </h2>
        <p className="sp-sub">
          {new Set(pending.map((t) => t.songId)).size}{" "}
          {new Set(pending.map((t) => t.songId)).size === 1
            ? "brano coinvolto"
            : "brani coinvolti"}{" "}
          · {tasks.filter((t) => t.done).length} passaggi risolti
        </p>
        <div className="sp-session-bar">
          <span className="sp-timer">{time(elapsed)}</span>
          <button
            className="sp-primary"
            onClick={async () => {
              if (!started) {
                setElapsed(0);
                setStarted(performance.now());
                session.current = pending.map((t) => t.id);
              } else {
                const seconds = Math.floor(
                    (performance.now() - started) / 1000,
                  ),
                  finished = tasks.filter((t) =>
                    session.current.includes(t.id),
                  );
                if (
                  await commit((_, b) => {
                    b.rehearsals ||= [];
                    b.rehearsals.push({
                      id: id("r"),
                      date: new Date().toLocaleDateString("sv-SE"),
                      seconds,
                      songIds: [...new Set(finished.map((t) => t.songId))],
                      notes: `${finished.filter((t) => t.done).length} passaggi risolti su ${finished.length}`,
                    });
                  }, "Sessione e riepilogo salvati.")
                ) {
                  setStarted(0);
                  setElapsed(seconds);
                }
              }
            }}
          >
            {started ? <Pause /> : <Play />}
            {started ? "Termina sessione" : "Inizia sessione"}
          </button>
        </div>
      </section>
      <div className="sp-section-head">
        <h2>I passaggi della prova</h2>
        <button
          className="sp-primary"
          disabled={!band.songs.length}
          onClick={() =>
            setDraft({
              id: id("task"),
              songId: band.songs[0].id,
              section: "Brano intero",
              goal: "",
              role: "Tutti",
              decision: "",
              done: false,
            })
          }
        >
          <Plus />
          Aggiungi passaggio
        </button>
      </div>
      <div className="sp-filters">
        {[
          ["open", "Da risolvere"],
          ["mine", "Per me"],
          ["done", "Risolti"],
        ].map(([key, label]) => (
          <button
            key={key}
            className={"sp-chip " + (key === filter ? "active" : "")}
            onClick={() => setFilter(key)}
          >
            {label}
          </button>
        ))}
      </div>
      {!shown.length && (
        <div className="sp-empty">
          Aggiungi un obiettivo concreto: allineare l’attacco del ritornello,
          <br />
          decidere il finale, ripassare il bridge.
        </div>
      )}
      {shown.map((t) => (
        <section className="sp-task-card" key={t.id}>
          <div className="sp-row">
            <input
              type="checkbox"
              aria-label={`Risolto: ${t.goal}`}
              checked={t.done}
              onChange={(e) =>
                commit((_, b) => {
                  const current = b.practiceTasks?.find((x) => x.id === t.id);
                  if (current) current.done = e.target.checked;
                })
              }
            />
            <div>
              <h3>{t.goal}</h3>
              <p className="sp-sub" style={{ color: "var(--sp-accent)" }}>
                {band.songs.find((s) => s.id === t.songId)?.title} · {t.section}
              </p>
            </div>
            <span className="sp-task-role">PER {t.role.toUpperCase()}</span>
          </div>
          {t.decision && (
            <p className="sp-note" style={{ marginTop: 15 }}>
              Decisione: {t.decision}
            </p>
          )}
          <div className="sp-end-actions">
            <button
              className="sp-primary"
              onClick={() => openPassage(t.songId, t.section)}
            >
              <Play />
              Apri il passaggio
            </button>
            <button className="sp-secondary" onClick={() => setDraft({ ...t })}>
              <Pencil />
              Appunti e decisioni
            </button>
          </div>
        </section>
      ))}
      {!!band.rehearsals?.length && (
        <div className="sp-section-head">
          <h2>Registro delle prove</h2>
        </div>
      )}
      {band.rehearsals
        ?.slice(-4)
        .reverse()
        .map((r) => (
          <div className="sp-songrow" key={r.id}>
            <span>{r.date}</span>
            <span className="sp-sub">
              {time(r.seconds)} · {r.notes || `${r.songIds.length} brani`}
            </span>
          </div>
        ))}
      {draft && (
        <Modal title="Prepara un passaggio" close={() => setDraft(null)}>
          <label className="sp-form-label">
            Brano
            <select
              className="sp-input"
              value={draft.songId}
              onChange={(e) =>
                setDraft({
                  ...draft,
                  songId: e.target.value,
                  section: "Brano intero",
                })
              }
            >
              {band.songs.map((s) => (
                <option value={s.id} key={s.id}>
                  {s.title}
                </option>
              ))}
            </select>
          </label>
          <label className="sp-form-label">
            Passaggio
            <select
              className="sp-input"
              value={draft.section}
              onChange={(e) => setDraft({ ...draft, section: e.target.value })}
            >
              {[
                "Brano intero",
                ...sections(
                  band.songs.find((s) => s.id === draft.songId)?.body || "",
                ),
              ].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </label>
          <label className="sp-form-label">
            Cosa vogliamo sistemare?
            <input
              className="sp-input"
              value={draft.goal}
              onChange={(e) => setDraft({ ...draft, goal: e.target.value })}
              placeholder="Es. allineare l’attacco del ritornello"
            />
          </label>
          <label className="sp-form-label">
            Chi ci lavora
            <select
              className="sp-input"
              value={draft.role}
              onChange={(e) => setDraft({ ...draft, role: e.target.value })}
            >
              {[
                "Tutti",
                "Voce",
                "Chitarra",
                "Basso",
                "Tastiere",
                "Batteria",
                "Fiati",
              ].map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
          </label>
          <label className="sp-form-label">
            Decisioni e appunti
            <textarea
              className="sp-input"
              value={draft.decision}
              onChange={(e) => setDraft({ ...draft, decision: e.target.value })}
            />
          </label>
          <div className="sp-end-actions">
            <button className="sp-secondary" onClick={() => setDraft(null)}>
              Annulla
            </button>
            <button
              className="sp-primary"
              disabled={!draft.goal.trim()}
              onClick={async () => {
                if (
                  await commit((_, b) => {
                    b.practiceTasks = (b.practiceTasks || [])
                      .filter((t) => t.id !== draft.id)
                      .concat({ ...draft, goal: draft.goal.trim() });
                  }, "Passaggio salvato nell’agenda.")
                )
                  setDraft(null);
              }}
            >
              <Check />
              Salva passaggio
            </button>
          </div>
          {tasks.some((t) => t.id === draft.id) && (
            <button
              className="sp-link sp-danger"
              onClick={async () => {
                if (
                  await commit((_, b) => {
                    b.practiceTasks = b.practiceTasks?.filter(
                      (t) => t.id !== draft.id,
                    );
                  })
                )
                  setDraft(null);
              }}
            >
              <Trash2 />
              Elimina passaggio
            </button>
          )}
        </Modal>
      )}
    </>
  );
}

export function Concerts({
  band,
  commit,
  openStage,
  openPractice,
}: {
  band: Band;
  commit: Commit;
  openStage: (set: string) => void;
  openPractice: (set: string) => void;
}) {
  const [draft, setDraft] = useState<Gig | null>(null);
  const initialEvent = useRef<Gig | null>(null);
  const [dossierId, setDossierId] = useState("");
  const dossier = band.events?.find((g) => g.id === dossierId);
  if (dossier)
    return (
      <Dossier gig={dossier} commit={commit} close={() => setDossierId("")} />
    );
  return (
    <>
      <div className="sp-page-head">
        <div>
          <h1 className="sp-title">Ogni serata, preparata.</h1>
          <p className="sp-sub">
            Una data, una scaletta e quello che resta da fare.
          </p>
        </div>
        <button
          className="sp-primary"
          onClick={() =>
            setDraft({
              id: id("gig"),
              title: "",
              date: new Date().toLocaleDateString("sv-SE"),
              time: "21:00",
              location: "",
              setId: band.cur || band.sets[0]?.id || "",
              notes: "",
              kind: "gig",
            })
          }
        >
          <Plus />
          Prepara una serata
        </button>
      </div>
      {!band.events?.length && (
        <div className="sp-empty">
          Aggiungi un concerto o una prova aperta.
          <br />
          StagePilot raccoglie scaletta e lavoro ancora da completare.
        </div>
      )}
      {band.events
        ?.slice()
        .sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time))
        .map((g) => {
          const set = band.sets.find((s) => s.id === g.setId),
            p = preparation(band, set);
          return (
            <section className="sp-task-card" key={g.id}>
              <span className="sp-pill">
                <CalendarDays />
                {g.date} · {g.time} · {g.location}
              </span>
              <h2 style={{ marginTop: 15 }}>{g.title}</h2>
              <p className="sp-sub" style={{ marginTop: 7 }}>
                {set?.name || "Scaletta da scegliere"} ·{" "}
                {set?.items.filter((i) => i.type === "song").length || 0} brani
                · {time(p.duration)}
                {p.missing ? " + durate da definire" : " minuti"}
              </p>
              <div className="sp-preparation">
                <span>{p.pending} brani non ancora pronti</span>
                <span>{p.tasks} passaggi da risolvere</span>
                {p.missing > 0 && <span>{p.missing} durate mancanti</span>}
              </div>
              {g.notes && <div className="sp-note">{g.notes}</div>}
              <div className="sp-end-actions">
                <button
                  className="sp-secondary"
                  onClick={() => {
                    initialEvent.current = structuredClone(g);
                    setDraft({ ...g });
                  }}
                >
                  <Pencil />
                  Modifica
                </button>
                <button
                  className="sp-secondary"
                  onClick={() => setDossierId(g.id)}
                >
                  Dossier del concerto
                </button>
                <button
                  className="sp-secondary"
                  onClick={() => openPractice(g.setId)}
                >
                  Prepara in prova
                </button>
                <button
                  className="sp-primary"
                  disabled={!set?.items.length}
                  onClick={() => openStage(g.setId)}
                >
                  <Play />
                  Sul palco
                </button>
              </div>
            </section>
          );
        })}
      {draft && (
        <Modal title="Prepara una serata" close={() => setDraft(null)}>
          <label className="sp-form-label">
            Nome della serata
            <input
              className="sp-input"
              value={draft.title}
              onChange={(e) => setDraft({ ...draft, title: e.target.value })}
            />
          </label>
          <div className="sp-form-grid">
            <label className="sp-form-label">
              Data
              <input
                className="sp-input"
                type="date"
                value={draft.date}
                onChange={(e) => setDraft({ ...draft, date: e.target.value })}
              />
            </label>
            <label className="sp-form-label">
              Inizio
              <input
                className="sp-input"
                type="time"
                value={draft.time}
                onChange={(e) => setDraft({ ...draft, time: e.target.value })}
              />
            </label>
          </div>
          <label className="sp-form-label">
            Locale / luogo
            <input
              className="sp-input"
              value={draft.location}
              onChange={(e) => setDraft({ ...draft, location: e.target.value })}
            />
          </label>
          <label className="sp-form-label">
            Scaletta
            <select
              className="sp-input"
              value={draft.setId}
              onChange={(e) => setDraft({ ...draft, setId: e.target.value })}
            >
              {band.sets.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
          <label className="sp-form-label">
            Appunti della serata
            <textarea
              className="sp-input"
              value={draft.notes}
              onChange={(e) => setDraft({ ...draft, notes: e.target.value })}
            />
          </label>
          <div className="sp-end-actions">
            <button className="sp-secondary" onClick={() => setDraft(null)}>
              Annulla
            </button>
            <button
              className="sp-primary"
              disabled={!draft.title.trim() || !draft.date || !draft.time}
              onClick={async () => {
                if (
                  await commit((_, b) => {
                    const current = b.events.find((g) => g.id === draft.id);
                    const next =
                      initialEvent.current?.id === draft.id
                        ? current
                          ? mergeGigDraft(initialEvent.current, draft, current)
                          : (() => {
                              throw new Error(
                                "La serata è stata rimossa. La bozza è conservata.",
                              );
                            })()
                        : { ...draft, title: draft.title.trim() };
                    b.events = (b.events || [])
                      .filter((g) => g.id !== draft.id)
                      .concat(next);
                  }, "Serata salvata.")
                )
                  setDraft(null);
              }}
            >
              Salva serata
            </button>
          </div>
          {band.events?.some((g) => g.id === draft.id) && (
            <button
              className="sp-link sp-danger"
              onClick={async () => {
                if (
                  await commit((_, b) => {
                    b.events = b.events.filter((g) => g.id !== draft.id);
                  })
                )
                  setDraft(null);
              }}
            >
              <Trash2 />
              Elimina serata
            </button>
          )}
        </Modal>
      )}
    </>
  );
}
