import { secs, mmss } from './time';
import { t } from '../i18n';
import type { SetItem, Song } from './model';

type SongLike = Pick<Song, 'id'> & Partial<Song>;

/* ── scorrimento ─────────────────────────────────────────── */
/* Il chart deve scorrere tutto nella durata del brano: così la velocità
   non è un numero da azzeccare a orecchio ma un dato che discende dal
   pezzo. `lead` è l'intro in cui non si legge ancora niente, quindi va
   tolta dal tempo utile. `factor` è la correzione che ci si registra
   sopra una volta in prova. Senza durata non c'è niente da cui dedurla
   e si ripiega. */
export function speedFor(scrollable: number, durSecs: number, factor?: number, lead?: number): number {
  if (!(scrollable > 0)) return 0;
  const tt = durSecs > 0 ? Math.max(1, durSecs - (lead && lead > 0 ? lead : 0)) : 0;
  const base = tt > 0 ? scrollable / tt : 20;
  return Math.max(1, base * (factor && factor > 0 ? factor : 1));
}

/* ── scalette ────────────────────────────────────────────── */
/* Somma le durate: i segnaposto contano come i brani, è tutto tempo
   che passa e che il locale conteggia. */
export function setDuration(items: (SetItem | null | undefined)[] | null | undefined, songs: SongLike[] | null | undefined): number {
  return (items || []).reduce((a, it) => {
    if (!it) return a;
    if (it.type === 'mark') return a + secs(it.dur);
    const s = (songs || []).find(x => x.id === it.id);
    return a + (s ? secs(s.dur) : 0);
  }, 0);
}
/* Numerazione: contano solo i brani, i segnaposto prendono 0. "02" deve
   voler dire il secondo pezzo, che è quello che si conta sul palco. */
export function ordinals(items: (SetItem | null | undefined)[] | null | undefined): number[] {
  let n = 0;
  return (items || []).map(it => it && it.type === 'mark' ? 0 : ++n);
}
/* La scaletta in chiaro, da mandare alla band la sera prima. Dice le stesse
   cose che si leggono a schermo: numero, titolo, tonalità che suona davvero,
   durata. I segnaposto restano al loro posto — la pausa fa parte della
   serata — e non prendono numero, come nella lista.
   `keyOf` arriva da fuori: la tonalità dipende da notazione e alterazioni,
   che sono scelte di chi legge e non proprietà della scaletta. */
export function setAsText(
  items: SetItem[] | null | undefined, songs: SongLike[] | null | undefined,
  o: { band?: string; name?: string; keyOf?: (s: SongLike) => string } = {}
): string {
  const keyOf = o.keyOf || ((s: SongLike) => s.key || '');
  const list = items || [], ord = ordinals(list);
  const righe: string[] = [];
  list.forEach((it, i) => {
    if (!it) return;
    if (it.type === 'mark') {
      righe.push(`--- ${it.label || t('mark.word')}${it.dur ? ` (${it.dur})` : ''}`);
      return;
    }
    const s = (songs || []).find(x => x.id === it.id);
    if (!s) return;                                  // brano cancellato: si salta
    const k = keyOf(s), d = s.dur || '';
    righe.push([String(ord[i]).padStart(2, '0') + '.', s.title, k && '— ' + k, d && '— ' + d]
      .filter(Boolean).join(' '));
  });
  const nS = list.filter(i => i && i.type === 'song').length;
  const nM = list.length - nS;
  const testa = [o.band, o.name].filter(Boolean).join(' — ');
  const conto = t('set.count', { n: nS }) + (nM ? ' · ' + t('set.marks', { n: nM }) : '')
              + ' · ' + mmss(setDuration(list, songs));
  return [testa, conto, '', ...righe].filter((r, i) => i || r).join('\n').trim();
}

/* Sposta un elemento da una posizione all'altra, senza toccare l'array
   originale: è il riordino a trascinamento. */
export function moveItem<T>(arr: T[] | null | undefined, from: number, to: number): T[] {
  const a = (arr || []).slice();
  if (from < 0 || from >= a.length) return a;
  to = Math.max(0, Math.min(a.length - 1, to));
  a.splice(to, 0, a.splice(from, 1)[0]);
  return a;
}
