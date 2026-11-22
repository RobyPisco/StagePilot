import { transpose, spellLine, flatFor, roman } from '../core/chords';
import { esc } from '../core/text';
import type { DB, Band, SetList, SetItem, Song } from '../core/model';
import { createStore } from '../storage/store';
import { t } from '../i18n';
import { toast } from './dialog';
import { syncStatusBar } from './native';

/* Lo stato condiviso fra i moduli dell'interfaccia. Un oggetto solo e non
   tante `let` sparse: un modulo ES non può riassegnare una variabile
   importata, ma può cambiare un campo di un oggetto importato. */
export const S = {
  db: null as unknown as DB,
  view: 'set' as 'set' | 'lib' | 'opts',
  libQ: '',
  libTag: '',
  /* editor */
  editing: null as string | null,
  editNotes: {} as Record<string, string>,
  editSets: new Set<string>(),
  editMark: '',
  /* trascinamento: un click subito dopo un rilascio va ignorato */
  dragTs: 0,
  saveT: 0 as ReturnType<typeof setTimeout> | 0
};

export const live = {
  i: 0, fs: 22, play: false, struct: false, lock: false,
  offSong: null as string | null,
  pps: 0, acc: 0, last: 0, t0: 0, wait: 0,
  wake: null as WakeLockSentinel | null,
  raf: 0, tmr: 0 as ReturnType<typeof setInterval> | 0
};

/* localStorage può mancare o lanciare (navigazione privata, permessi):
   meglio un salvataggio solo su IndexedDB che nessun avvio. */
const ls = (() => { try { return localStorage; } catch { return undefined; } })();
export const store = createStore(typeof indexedDB !== 'undefined' ? indexedDB : undefined, ls);

export const save = async (): Promise<void> => {
  if (!(await store.save(S.db))) toast(t('err.space'), true);
};
export const saveSoon = (): void => {
  clearTimeout(S.saveT); S.saveT = setTimeout(save, 300);
};

export function applyTheme(): void {
  const th = S.db.opt.theme;
  document.documentElement.dataset.theme = th;
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (meta) meta.content = th === 'light' ? '#F4F5F8' : '#101114';
  void syncStatusBar(th);
}

export const curBand = (): Band => S.db.bands.find(b => b.id === S.db.curBand) || S.db.bands[0];
export const curSet = (): SetList => { const b = curBand(); return b.sets.find(s => s.id === b.cur) || b.sets[0]; };
export const setItems = (): SetItem[] => curSet().items;
export const byId = (id: string): Song | undefined => curBand().songs.find(s => s.id === id);

/* Voci di scaletta risolte: il brano al posto del suo id. */
export type Entry = { type: 'mark'; label: string; dur: string } | { type: 'song'; song: Song };
export const entries = (): Entry[] => setItems().map((it): Entry | null => {
  if (it.type === 'mark') return { type: 'mark', label: it.label, dur: it.dur };
  const song = byId(it.id);
  return song ? { type: 'song', song } : null;
}).filter((e): e is Entry => !!e);

export const liveEntry = (): Entry | undefined => {
  if (live.offSong) {
    const song = byId(live.offSong);
    if (song) return { type: 'song', song };
    live.offSong = null;
  }
  return entries()[live.i];
};

export const isIt = (): boolean => S.db.opt.notation === 'it';
/* Tonalità reale del pezzo, quella che diresti al cantante: il capo non
   la cambia, sposta solo le posizioni sotto le dita. */
export const soundKey = (s: Song): string => transpose(s.key || '?', s.tr || 0);
export const keyLabel = (s: Song): string => {
  const k = soundKey(s);
  return spellLine(k, isIt(), flatFor(k, S.db.opt.accid));
};
/* Come si legge sul chart: qui il capo conta. */
export const readTr = (s: Song): number => (s.tr || 0) - (s.capo || 0);
export const readKey = (s: Song): string => transpose(s.key || '?', readTr(s));
export const subLine = (s: Song): string =>
  `${esc(s.artist || '—')} &nbsp;·&nbsp; ${s.bpm || '—'} bpm &nbsp;·&nbsp; ${esc(s.dur || '—')}`
  + (s.tags.length ? ` &nbsp;·&nbsp; ${s.tags.map(x => '#' + esc(x)).join(' ')}` : '')
  + (s.tr ? ` &nbsp;·&nbsp; ${s.tr > 0 ? '+' : '−'}${Math.abs(s.tr)}` : '')
  + (s.capo ? ` &nbsp;·&nbsp; ${t('song.capo')} ${roman(s.capo)}` : '');
