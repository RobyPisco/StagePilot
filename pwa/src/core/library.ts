import { transpose, PC } from './chords';
import type { Song, Sort } from './model';

type SongLike = Pick<Song, 'id'> & Partial<Song>;

/* ── ordinamento del repertorio ──────────────────────────── */
/* Ordina per come si mostra, non riscrive il repertorio: l'ordine di
   inserimento resta sempre recuperabile. */
const keyPc = (s: SongLike): number => {
  const m = /^([A-G][#b]?)/.exec(transpose(s.key || '', s.tr || 0));
  const p = m ? PC[m[1]] : undefined;
  return p === undefined ? 99 : p;
};
export function sortSongs<T extends SongLike>(songs: T[] | null | undefined, mode: Sort): T[] {
  const a = (songs || []).slice();
  const txt = (x: unknown, y: unknown) =>
    String(x || '').localeCompare(String(y || ''), 'it', { sensitivity: 'base' });
  const byTitle = (x: T, y: T) => txt(x.title, y.title);
  if (mode === 'title')  a.sort(byTitle);
  if (mode === 'artist') a.sort((x, y) => txt(x.artist, y.artist) || byTitle(x, y));
  if (mode === 'bpm')    a.sort((x, y) => ((+(x.bpm ?? '') || 1e9) - (+(y.bpm ?? '') || 1e9)) || byTitle(x, y));
  if (mode === 'key')    a.sort((x, y) => (keyPc(x) - keyPc(y)) || byTitle(x, y));
  return a;
}
