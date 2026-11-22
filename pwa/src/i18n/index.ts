import { it } from './it';
import { en } from './en';

/* Le stringhe dell'interfaccia stanno nei dizionari, non nel codice.
   L'italiano è il riferimento: `en` deve avere esattamente le stesse
   chiavi, e se ne manca una è TypeScript a dirlo.
   La lingua è una cosa diversa dalla notazione: un inglese può voler
   leggere Do Re Mi e un italiano C D E. Le due scelte restano separate. */
export type Key = keyof typeof it;
export type Lang = 'it' | 'en';
export const LANGS: Lang[] = ['it', 'en'];
const DICT: Record<Lang, Record<Key, string>> = { it, en };

let cur: Lang = 'it';
export const setLang = (l: Lang) => { cur = l; };
export const getLang = (): Lang => cur;

/* Se l'utente non ha scelto, decide la lingua del sistema: italiano per
   chi ce l'ha in italiano, inglese per tutti gli altri. */
export const pickLang = (pref: string | null | undefined, sys: string | undefined): Lang =>
  pref === 'it' || pref === 'en' ? pref : /^it\b/i.test(sys || '') ? 'it' : 'en';

/* `{n}` nel testo si sostituisce col parametro omonimo. */
export function t(key: Key, p?: Record<string, string | number>): string {
  const s = DICT[cur][key] ?? it[key] ?? '';
  return p ? s.replace(/\{(\w+)\}/g, (m, k) => (k in p ? String(p[k]) : m)) : s;
}
