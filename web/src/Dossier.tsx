import { useEffect, useRef, useState } from "react";
import { ArrowLeft, Check, Plus, Printer, Save, Trash2 } from "lucide-react";
import { type Band, type DB, type Gig, id } from "./domain";
import {
  dossierDefaults,
  mergeGigDraft,
  stageCoordinate,
} from "./dossier-domain";
type Commit = (
  fn: (db: DB, band: Band) => void,
  message?: string,
) => Promise<boolean>;

export function Dossier({
  gig,
  commit,
  close,
}: {
  gig: Gig;
  commit: Commit;
  close: () => void;
}) {
  const draftKey = "stagepilot-dossier-" + gig.id;
  const [saved] = useState(() => {
    try {
      return JSON.parse(sessionStorage.getItem(draftKey) || "null");
    } catch {
      return null;
    }
  });
  const [draft, setDraft] = useState(() =>
      dossierDefaults(saved?.draft?.id === gig.id ? saved.draft : gig),
    ),
    [editing, setEditing] = useState(!!saved?.draft),
    [error, setError] = useState("");
  const baseline = useRef<Gig>(
      saved?.baseline?.id === gig.id ? saved.baseline : gig,
    ),
    drag = useRef<string | null>(null),
    [saving, setSaving] = useState(false);
  useEffect(() => {
    try {
      if (editing)
        sessionStorage.setItem(
          draftKey,
          JSON.stringify({ draft, baseline: baseline.current }),
        );
      else sessionStorage.removeItem(draftKey);
    } catch {
      /* The current draft remains in memory. */
    }
  }, [draft, editing, draftKey]);
  const shown = dossierDefaults(editing ? draft : gig);
  function field<K extends keyof Gig>(key: K, value: Gig[K]) {
    setDraft((d) => ({ ...d, [key]: value }));
  }
  function move(pid: string, x: number, y: number) {
    setDraft((d) => ({
      ...d,
      stagePlot: d.stagePlot!.map((p) =>
        p.id === pid
          ? { ...p, x: stageCoordinate(x), y: stageCoordinate(y) }
          : p,
      ),
    }));
  }
  async function save() {
    setSaving(true);
    try {
      if (
        await commit((_, band) => {
          const current = band.events.find((g) => g.id === gig.id);
          if (!current)
            throw new Error("La serata è stata rimossa. La bozza resta qui.");
          band.events = band.events.map((g) =>
            g.id === gig.id
              ? mergeGigDraft(baseline.current, draft, current)
              : g,
          );
        }, "Dossier salvato.")
      )
        setEditing(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setSaving(false);
    }
  }
  const input = (
    label: string,
    value: string,
    change: (v: string) => void,
    type = "text",
  ) => (
    <label className="sp-form-label">
      {label}
      {editing ? (
        <input
          className="sp-input"
          type={type}
          value={value}
          onChange={(e) => change(e.target.value)}
          onInput={type==='time'?(e)=>change(e.currentTarget.value):undefined}
        />
      ) : (
        <strong>{value || "Da definire"}</strong>
      )}
    </label>
  );
  return (
    <article className="sp-dossier">
      <div className="sp-page-head">
        <div>
          <button className="sp-link" onClick={close}>
            <ArrowLeft />
            Concerti
          </button>
          <h1 className="sp-title">{gig.title}</h1>
          <p className="sp-sub">
            {gig.date} · {gig.location} · Dossier del concerto
          </p>
        </div>
        <div className="sp-row">
          {!editing ? (
            <>
              <button className="sp-secondary" onClick={() => window.print()}>
                <Printer />
                Stampa / PDF
              </button>
              <button
                className="sp-primary"
                onClick={() => {
                  baseline.current = structuredClone(gig);
                  setDraft(dossierDefaults(gig));
                  setEditing(true);
                }}
              >
                Modifica dossier
              </button>
            </>
          ) : (
            <>
              <button
                className="sp-secondary"
                disabled={saving}
                onClick={() => setEditing(false)}
              >
                Annulla
              </button>
              <button className="sp-primary" disabled={saving} onClick={save}>
                <Save />
                Salva dossier
              </button>
            </>
          )}
        </div>
      </div>
      {error && (
        <p className="sp-error" role="alert">
          {error}
        </p>
      )}
      <section className="sp-task-card">
        <h2>La giornata</h2>
        <div className="sp-form-grid">
          {input("Indirizzo", shown.address!, (v) => field("address", v))}
          {input(
            "Arrivo e scarico",
            shown.arrival!,
            (v) => field("arrival", v),
            "time",
          )}
          {input(
            "Soundcheck",
            shown.soundcheck!,
            (v) => field("soundcheck", v),
            "time",
          )}
          {input(
            "Apertura porte",
            shown.doors!,
            (v) => field("doors", v),
            "time",
          )}
          {input(
            "Inizio concerto",
            shown.time,
            (v) => field("time", v),
            "time",
          )}
          {input("Fine prevista", shown.end!, (v) => field("end", v), "time")}
        </div>
      </section>
      <section className="sp-task-card">
        <h2>Contatti utili</h2>
        {shown.contacts!.map((c) => (
          <div className="sp-dossier-row" key={c.id}>
            {input("Nome", c.name, (v) =>
              field(
                "contacts",
                shown.contacts!.map((x) =>
                  x.id === c.id ? { ...x, name: v } : x,
                ),
              ),
            )}
            {input("Ruolo", c.role, (v) =>
              field(
                "contacts",
                shown.contacts!.map((x) =>
                  x.id === c.id ? { ...x, role: v } : x,
                ),
              ),
            )}
            {input(
              "Telefono",
              c.phone,
              (v) =>
                field(
                  "contacts",
                  shown.contacts!.map((x) =>
                    x.id === c.id ? { ...x, phone: v } : x,
                  ),
                ),
              "tel",
            )}
            {editing && (
              <button
                className="sp-iconbtn"
                aria-label={"Rimuovi contatto " + c.name}
                onClick={() =>
                  field(
                    "contacts",
                    shown.contacts!.filter((x) => x.id !== c.id),
                  )
                }
              >
                <Trash2 />
              </button>
            )}
          </div>
        ))}
        {!shown.contacts!.length && (
          <p className="sp-sub">
            Fonico, locale, referente della serata: aggiungili qui.
          </p>
        )}
        {editing && (
          <button
            className="sp-secondary"
            onClick={() =>
              field("contacts", [
                ...shown.contacts!,
                { id: id("contact"), name: "", role: "", phone: "" },
              ])
            }
          >
            <Plus />
            Aggiungi contatto
          </button>
        )}
      </section>
      <section className="sp-task-card">
        <h2>Da portare e da fare</h2>
        <p className="sp-sub">
          {shown.checklist!.filter((c) => c.done).length} di{" "}
          {shown.checklist!.length} completati
        </p>
        {shown.checklist!.map((c) => (
          <div className="sp-dossier-row" key={c.id}>
            <label className="sp-checkbox">
              <input
                type="checkbox"
                aria-label={"Completato: " + c.text}
                checked={c.done}
                onChange={async () => {
                  if (editing)
                    field(
                      "checklist",
                      shown.checklist!.map((x) =>
                        x.id === c.id ? { ...x, done: !x.done } : x,
                      ),
                    );
                  else
                    await commit((_, b) => {
                      b.events = b.events.map((g) =>
                        g.id === gig.id
                          ? {
                              ...g,
                              checklist: (g.checklist || []).map((x) =>
                                x.id === c.id ? { ...x, done: !x.done } : x,
                              ),
                            }
                          : g,
                      );
                    });
                }}
              />
              <Check />
            </label>
            {input("Attività / attrezzatura", c.text, (v) =>
              field(
                "checklist",
                shown.checklist!.map((x) =>
                  x.id === c.id ? { ...x, text: v } : x,
                ),
              ),
            )}
            {input("Responsabile", c.owner, (v) =>
              field(
                "checklist",
                shown.checklist!.map((x) =>
                  x.id === c.id ? { ...x, owner: v } : x,
                ),
              ),
            )}
            {editing && (
              <button
                className="sp-iconbtn"
                aria-label={"Rimuovi attività " + c.text}
                onClick={() =>
                  field(
                    "checklist",
                    shown.checklist!.filter((x) => x.id !== c.id),
                  )
                }
              >
                <Trash2 />
              </button>
            )}
          </div>
        ))}
        {editing && (
          <button
            className="sp-secondary"
            onClick={() =>
              field("checklist", [
                ...shown.checklist!,
                { id: id("check"), text: "", owner: "", done: false },
              ])
            }
          >
            <Plus />
            Aggiungi attività
          </button>
        )}
      </section>
      <section className="sp-task-card">
        <h2>Disposizione sul palco</h2>
        <p className="sp-sub">
          Vista dal pubblico.{" "}
          {editing
            ? "Trascina i musicisti oppure usa le frecce dopo averli selezionati."
            : ""}
        </p>
        <div
          className="sp-stageplot"
          aria-label="Disposizione sul palco"
          onPointerMove={(e) => {
            if (!editing || !drag.current) return;
            const r = e.currentTarget.getBoundingClientRect();
            move(
              drag.current,
              ((e.clientX - r.left - 55) / Math.max(1, r.width - 110)) * 100,
              ((e.clientY - r.top - 45) / Math.max(1, r.height - 90)) * 100,
            );
          }}
          onPointerUp={() => (drag.current = null)}
          onPointerCancel={() => (drag.current = null)}
        >
          <span className="sp-stageplot-back">FONDO PALCO</span>
          {shown.stagePlot!.map((p) => (
            <button
              key={p.id}
              type="button"
              className="sp-stage-person"
              aria-label={
                "Posizione " + (p.label || p.instrument || "musicista")
              }
              disabled={!editing}
              style={{
                left: `calc(${p.x}% + ${55 - 1.1 * p.x}px)`,
                top: `calc(${p.y}% + ${45 - 0.9 * p.y}px)`,
              }}
              onPointerDown={(e) => {
                if (editing) {
                  drag.current = p.id;
                  e.currentTarget.parentElement?.setPointerCapture(e.pointerId);
                }
              }}
              onKeyDown={(e) => {
                if (!editing) return;
                const dx =
                    e.key === "ArrowRight" ? 5 : e.key === "ArrowLeft" ? -5 : 0,
                  dy = e.key === "ArrowDown" ? 5 : e.key === "ArrowUp" ? -5 : 0;
                if (dx || dy) {
                  e.preventDefault();
                  move(p.id, p.x + dx, p.y + dy);
                }
              }}
            >
              <strong>{p.label || "Musicista"}</strong>
              <span>{p.instrument}</span>
            </button>
          ))}
          <span className="sp-stageplot-front">PUBBLICO</span>
        </div>
        {editing && (
          <>
            {shown.stagePlot!.map((p) => (
              <div className="sp-dossier-row" key={p.id}>
                {input("Nome sul palco", p.label, (v) =>
                  field(
                    "stagePlot",
                    shown.stagePlot!.map((x) =>
                      x.id === p.id ? { ...x, label: v } : x,
                    ),
                  ),
                )}
                {input("Strumento", p.instrument, (v) =>
                  field(
                    "stagePlot",
                    shown.stagePlot!.map((x) =>
                      x.id === p.id ? { ...x, instrument: v } : x,
                    ),
                  ),
                )}
                <button
                  className="sp-iconbtn"
                  aria-label={"Rimuovi posizione " + p.label}
                  onClick={() =>
                    field(
                      "stagePlot",
                      shown.stagePlot!.filter((x) => x.id !== p.id),
                    )
                  }
                >
                  <Trash2 />
                </button>
              </div>
            ))}
            <button
              className="sp-secondary"
              onClick={() =>
                field("stagePlot", [
                  ...shown.stagePlot!,
                  {
                    id: id("position"),
                    label: "",
                    instrument: "",
                    x: 50,
                    y: 50,
                  },
                ])
              }
            >
              <Plus />
              Aggiungi musicista
            </button>
          </>
        )}
      </section>
      <section className="sp-task-card">
        <h2>Ingressi del mixer</h2>
        {shown.inputs!.map((c) => (
          <div className="sp-dossier-row" key={c.id}>
            {input("Canale", c.channel, (v) =>
              field(
                "inputs",
                shown.inputs!.map((x) =>
                  x.id === c.id ? { ...x, channel: v } : x,
                ),
              ),
            )}
            {input("Sorgente", c.label, (v) =>
              field(
                "inputs",
                shown.inputs!.map((x) =>
                  x.id === c.id ? { ...x, label: v } : x,
                ),
              ),
            )}
            {input("Microfono / DI", c.mic, (v) =>
              field(
                "inputs",
                shown.inputs!.map((x) =>
                  x.id === c.id ? { ...x, mic: v } : x,
                ),
              ),
            )}
            {input("Note tecniche", c.notes, (v) =>
              field(
                "inputs",
                shown.inputs!.map((x) =>
                  x.id === c.id ? { ...x, notes: v } : x,
                ),
              ),
            )}
            {editing && (
              <button
                className="sp-iconbtn"
                aria-label={"Rimuovi ingresso " + c.channel}
                onClick={() =>
                  field(
                    "inputs",
                    shown.inputs!.filter((x) => x.id !== c.id),
                  )
                }
              >
                <Trash2 />
              </button>
            )}
          </div>
        ))}
        {!shown.inputs!.length && (
          <p className="sp-sub">Prepara la lista da consegnare al fonico.</p>
        )}
        {editing && (
          <button
            className="sp-secondary"
            onClick={() =>
              field("inputs", [
                ...shown.inputs!,
                {
                  id: id("input"),
                  channel: String(shown.inputs!.length + 1),
                  label: "",
                  mic: "",
                  notes: "",
                },
              ])
            }
          >
            <Plus />
            Aggiungi ingresso
          </button>
        )}
      </section>
    </article>
  );
}
