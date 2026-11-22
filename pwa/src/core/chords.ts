/* ── note e accordi ──────────────────────────────────────── */
export const SHARP = ['C','C#','D','D#','E','F','F#','G','G#','A','A#','B'];
export const FLAT  = ['C','Db','D','Eb','E','F','Gb','G','Ab','A','Bb','B'];
const IT_SHARP = ['Do','Do#','Re','Re#','Mi','Fa','Fa#','Sol','Sol#','La','La#','Si'];
const IT_FLAT  = ['Do','Reb','Re','Mib','Mi','Fa','Solb','Sol','Lab','La','Sib','Si'];
export const PC: Record<string, number> =
  { C:0,'C#':1,Db:1,D:2,'D#':3,Eb:3,E:4,F:5,'F#':6,Gb:6,G:7,'G#':8,Ab:8,A:9,'A#':10,Bb:10,B:11 };
const IT_PC: Record<string, number> = { Do:0, Re:2, Mi:4, Fa:5, Sol:7, La:9, Si:11 };
const SUFFIX = /^(?:min|maj|dim|aug|sus|add|alt|m|M|°|ø|\+|-|\d|#|b|\(|\))*$/;

/* Un token è un accordo solo se parte da una fondamentale valida e tutto
   quello che segue è una sigla plausibile. Senza questo controllo
   "N.C." diventerebbe "N.D#." e "Break" diventerebbe "C#reak".
   Dentro l'app gli accordi sono SEMPRE in notazione inglese: l'italiano
   entra solo in scrittura ed esce solo a schermo. */
export function isChord(tok: string): boolean {
  const m = /^([A-G][#b]?)(.*)$/.exec(tok);
  if (!m) return false;
  let rest = m[2];
  const sl = rest.indexOf('/');
  if (sl >= 0) {
    const bass = rest.slice(sl + 1);
    if (bass && !/^[A-G][#b]?$/.test(bass)) return false;
    rest = rest.slice(0, sl);
  }
  return SUFFIX.test(rest);
}

/* Spezza una riga in token conservando spazi e stanghette, così la si
   può ricomporre identica dopo aver toccato solo gli accordi. */
const tokens = (str: unknown): string[] => String(str ?? '').split(/(\s+|\|)/);

export function transpose(str: string, n: number): string {
  if (!str || !n) return str;
  return tokens(str).map(tok => {
    if (!isChord(tok)) return tok;
    return tok.replace(/[A-G][#b]?/g, r => {
      const i = PC[r];
      return i === undefined ? r : SHARP[(((i + n) % 12) + 12) % 12];
    });
  }).join('');
}

/* Tonalità con bemolli in armatura: Fa, Si♭, Mi♭, La♭, Re♭ e le relative
   minori. Serve a non scrivere La# dove il mondo scrive Si♭. */
const FLAT_MAJ = [5, 10, 3, 8, 1], FLAT_MIN = [2, 7, 0, 5, 10];
export function useFlats(key: unknown): boolean {
  const m = /^([A-G][#b]?)(.*)$/.exec(String(key || '').trim());
  if (!m) return false;
  const pc = PC[m[1]];
  if (pc === undefined) return false;
  if (/b$/.test(m[1])) return true;               // già scritta con un bemolle
  return (/^m(?!aj)/.test(m[2]) ? FLAT_MIN : FLAT_MAJ).includes(pc);
}
export type Accid = 'auto' | 'sharp' | 'flat';
export const flatFor = (key: unknown, mode: Accid): boolean =>
  mode === 'flat' ? true : mode === 'sharp' ? false : useFlats(key);

/* Riscrive un accordo inglese nella notazione da mostrare. Cambia solo il
   nome della fondamentale e del basso: la sigla non si tocca. */
export function spell(tok: string, it: boolean, flat: boolean): string {
  if (!isChord(tok)) return tok;
  const names = it ? (flat ? IT_FLAT : IT_SHARP) : (flat ? FLAT : SHARP);
  return tok.replace(/[A-G][#b]?/g, r => {
    const pc = PC[r];
    return pc === undefined ? r : names[pc];
  });
}
export const spellLine = (str: unknown, it: boolean, flat: boolean): string =>
  tokens(str).map(t => spell(t, it, flat)).join('');

/* Chi suona il basso legge la nota, non la sigla: di F#m7 gli serve F#, e
   maggiore o minore lo sente dagli altri. Le rivolti sono l'eccezione, ed
   è proprio il caso in cui prendere la fondamentale sarebbe l'errore: in
   C/G il basso suona SOL, non DO. Quindi vince la nota dopo la barra —
   quella barra è scritta lì apposta per il bassista. */
export function rootOf(tok: string): string {
  if (!isChord(tok)) return tok;
  const sl = tok.lastIndexOf('/');
  if (sl >= 0) {
    const basso = tok.slice(sl + 1);
    if (/^[A-G][#b]?$/.test(basso)) return basso;
  }
  const m = /^([A-G][#b]?)/.exec(tok);
  return m ? m[1] : tok;
}
export const rootsLine = (str: unknown): string => tokens(str).map(rootOf).join('');

/* Trasposizione e riscrittura in un colpo solo: è quello che serve
   ovunque si mostri un accordo. L'ordine conta: prima si traspone
   l'accordo intero, poi semmai lo si riduce alla nota, e solo alla fine
   si riscrive nella notazione da mostrare. */
export const showChords = (str: string, tr: number, it: boolean, flat: boolean, roots = false): string => {
  const t = transpose(str, tr);
  return spellLine(roots ? rootsLine(t) : t, it, flat);
};

/* Do Re Mi → C D E, per quello che si scrive nell'editor. Prima si prova
   l'inglese: "Fadd9" è un accordo inglese valido e va lasciato stare,
   mentre "Fa" da solo non lo è e allora è italiano. Questa precedenza
   toglie ogni ambiguità. */
export function toEnglish(tok: string): string {
  if (!tok || isChord(tok)) return tok;
  const conv = (p: string): string | null => {
    const m = /^(Sol|Do|Re|Mi|Fa|La|Si)([#b]?)(.*)$/.exec(p);
    if (!m) return null;
    const pc = (((IT_PC[m[1]] + (m[2] === '#' ? 1 : m[2] === 'b' ? -1 : 0)) % 12) + 12) % 12;
    return (m[2] === 'b' ? FLAT[pc] : SHARP[pc]) + m[3];
  };
  const out = tok.split('/').map(p => conv(p) ?? p).join('/');
  return isChord(out) ? out : tok;
}
export const toEnglishLine = (str: unknown): string => tokens(str).map(toEnglish).join('');

/* Normalizza solo dentro le parentesi quadre: il testo cantato non si
   tocca, se no "La sala chiude" diventerebbe un accordo. */
export const normalizeBody = (body: unknown): string =>
  String(body ?? '').replace(/\[([^\]]+)\]/g, (_m, inner: string) => '[' + toEnglishLine(inner) + ']');

/* ── capotasto ───────────────────────────────────────────── */
/* I chitarristi il capo lo contano in numeri romani, sul manico e sui
   fogli. Scrivere "CAPO 3" invece di "CAPO III" si nota. */
const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X', 'XI'];
export const roman = (n: number): string => ROMAN[n] ?? String(n);   // ?? e non ||: ROMAN[0] è '' ed è giusto così
