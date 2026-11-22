import { esc } from './text';
import { showChords } from './chords';
import { t } from '../i18n';
import type { Key } from '../i18n';

/* ── parser ChordPro ─────────────────────────────────────── */
export interface Seg { c: string | null; t: string }
export function segs(line: string): Seg[] {
  const out: Seg[] = []; const re = /\[([^\]]+)\]/g; let idx = 0, m: RegExpExecArray | null;
  while ((m = re.exec(line))) {
    const tx = line.slice(idx, m.index);
    if (tx) { if (out.length) out[out.length - 1].t += tx; else out.push({ c: null, t: tx }); }
    out.push({ c: m[1], t: '' });
    idx = m.index + m[0].length;
  }
  const tail = line.slice(idx);
  if (tail) { if (out.length) out[out.length - 1].t += tail; else out.push({ c: null, t: tail }); }
  return out.length ? out : [{ c: null, t: '' }];
}

export interface SheetOpts {
  tr?: number; struct?: boolean; it?: boolean; flat?: boolean; roots?: boolean; plain?: boolean;
}
/* struct = modalità solo-struttura: restano sezioni e accordi, il testo
   sparisce e con lui le righe che erano di solo testo. Serve a chi la
   struttura la deve seguire, non cantare. */
export function renderSheet(body: unknown, o: SheetOpts = {}): string {
  const tr = o.tr || 0, it = !!o.it, flat = !!o.flat, roots = !!o.roots;
  return String(body ?? '').split('\n').map(line => {
    if (!line.trim()) return '<div class="blank"></div>';
    if (line.trim().startsWith('#')) return `<div class="sec">${esc(line.replace(/^\s*#\s?/, ''))}</div>`;
    const parts = segs(line);
    if (o.struct) {
      const ch = parts.filter(s => s.c);
      if (!ch.length) return '';
      return `<div class="lyr">${ch.map(s =>
        `<span class="seg"><b class="ch">${esc(showChords(s.c!, tr, it, flat, roots))}</b></span>`
      ).join('')}</div>`;
    }
    /* Lo specchio della modalità struttura: lì spariscono le parole, qui
       gli accordi. Per chi canta sono rumore. Le righe di soli accordi —
       intro, stacchi, giri strumentali — spariscono del tutto invece di
       lasciare una riga vuota: senza accordi lì non c'era niente da
       leggere. Le intestazioni di sezione restano, quelle servono anche
       a chi canta per sapere dove si è. */
    if (o.plain) {
      const testo = parts.map(s => s.t).join('');
      return testo.trim()
        ? `<div class="lyr"><span class="seg"><i class="tx">${esc(testo)}</i></span></div>`
        : '';
    }
    const inner = parts.map(s =>
      `<span class="seg"><b class="ch">${esc(showChords(s.c || '', tr, it, flat, roots)) || '&nbsp;'}</b><i class="tx">${esc(s.t) || '&nbsp;'}</i></span>`
    ).join('');
    return `<div class="lyr">${inner}</div>`;
  }).join('');
}

/* Il ruolo è salvato col suo nome italiano, che è anche la chiave delle
   note: a schermo si mostra nella lingua di chi legge. */
export const roleLabel = (r: string): string => t(('role.' + r) as Key) || r;

/* La mia nota prima di tutto, poi quella di arrangiamento che vale
   per la band. Se non ho niente di mio resta solo la seconda. */
export function renderNotes(notes: Partial<Record<string, string>> | null | undefined, me: string): string {
  if (!notes) return '';
  const out: [string, string][] = [];
  if (me && me !== 'Tutti' && notes[me]) out.push([me, notes[me]!]);
  if (notes.Tutti) out.push(['Tutti', notes.Tutti]);
  return out.map(([who, txt]) =>
    `<div class="note"><span class="who">${esc(roleLabel(who))}</span>${esc(txt)}</div>`
  ).join('');
}
