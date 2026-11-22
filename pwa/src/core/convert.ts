import { isChord } from './chords';

/* ── convertitore "accordi sopra il testo" ───────────────── */
/* I siti di tab incollano gli accordi su una riga sopra il testo, allineati
   per colonna. Li spostiamo dentro le quadre nel punto esatto della
   sillaba, che è già il formato che l'app legge. Le etichette tra quadre
   tipo [Chorus] diventano righe con #, come vuole l'app; il resto passa
   invariato — quello che non riconosce si rifinisce a mano. */
/* Fra gli accordi si infilano annotazioni che accordi non sono: x2, N.C.,
   le stanghette di battuta, le note fra parentesi tipo (let ring). Una
   sola bastava a far scartare tutta la riga, che finiva a schermo come se
   fosse cantata. Qui le si copre di spazi: la riga resta lunga uguale,
   quindi le colonne degli accordi non si spostano e il resto si converte.
   Le parentesi si coprono solo quando stanno per conto loro: quelle
   attaccate a un accordo, come C(add9), sono parte della sigla. */
const ANNOT = /^(?:[xX]\d{1,2}|\d{1,2}[xX]|N\.?C\.?|[|:%]+)$/;
const blanks = (s: string) => ' '.repeat(s.length);
const maskAnnot = (line: unknown): string =>
  String(line ?? '')
    .replace(/(^|\s)(\([^()]*\))(?=\s|$)/g, (_m, pre: string, grp: string) => pre + blanks(grp))
    .replace(/\S+/g, t => ANNOT.test(t) ? blanks(t) : t);
/* Gli accordi di una riga con la colonna in cui stanno. Le posizioni
   valgono anche sulla riga vera: mascherare non cambia le lunghezze. */
const chordTokens = (line: unknown) =>
  [...maskAnnot(line).matchAll(/\S+/g)].filter(m => isChord(m[0]));
export const isChordLine = (line: unknown): boolean => {
  const toks = [...maskAnnot(line).matchAll(/\S+/g)];
  return toks.length > 0 && toks.every(m => isChord(m[0]));
};
const countTokens = (line: unknown) => chordTokens(line).length;

/* Chord line isolata (niente testo sotto): incornicia ogni accordo dove
   sta, mantenendo gli spazi originali fra un accordo e l'altro. Le
   annotazioni restano dove sono: su una riga di soli accordi un "x2"
   scritto in coda è un'indicazione che al musicista serve. */
export function wrapChordsInPlace(line: string): string {
  const toks = chordTokens(line);
  let out = String(line ?? '');
  for (let i = toks.length - 1; i >= 0; i--) {
    const m = toks[i];
    out = out.slice(0, m.index) + '[' + m[0] + ']' + out.slice(m.index! + m[0].length);
  }
  return out;
}

/* Una o più chord line + riga di testo sotto: inserisce ogni accordo nel
   testo alla stessa colonna in cui cadeva sopra. Servono più righe perché
   i tab a volte spezzano gli accordi di una sola riga di testo su due o
   più righe sovrapposte, quando altrimenti si scriverebbero uno
   sull'altro (tipico su un unico "Whoa" lungo con più cambi ravvicinati).
   Le colonne sono tutte relative allo stesso inizio riga, quindi si
   accumulano e si inseriscono insieme, da destra a sinistra: da sinistra
   ogni inserimento sposterebbe gli indici di quelli dopo. */
export function mergeChordLyric(chordLines: string | string[], lyricLine: string): string {
  const lines = Array.isArray(chordLines) ? chordLines : [chordLines];
  let seq = 0;
  /* qui le annotazioni si scartano invece di tenerle: infilare "x2" in
     mezzo alle parole sporcherebbe il testo cantato */
  const toks = lines.flatMap(l => chordTokens(l))
    .map(m => ({ tok: m[0], index: m.index!, seq: seq++ }))
    /* a parità di colonna (due righe impilate con un accordo alla stessa
       posizione) vince l'ordine con cui erano scritte, non quello inverso
       che verrebbe inserendo tutti da destra a sinistra */
    .sort((a, b) => b.index - a.index || b.seq - a.seq);
  let out = lyricLine || '';
  for (const { tok, index } of toks) {
    /* Colonna oltre la fine del verso: l'accordo entra DOPO le parole, ed
       è il caso dell'ultimo giro che parte sul silenzio mentre la frase è
       già finita. Prima veniva appiccicato all'ultima sillaba, come se ci
       si cantasse sopra; adesso si allunga la riga di spazi e l'accordo
       resta alla colonna in cui il trascrittore l'aveva messo. Si inserisce
       da destra a sinistra, quindi il primo è il più a destra e allungare
       una volta basta per tutti. */
    if (index > out.length) out = out.padEnd(index, ' ');
    out = out.slice(0, index) + '[' + tok + ']' + out.slice(index);
  }
  return out;
}

/* Una riga `{direttiva: valore}` o `{direttiva}` del vero ChordPro. Chi
   rifinisce il tab in un editor ChordPro vero, prima di incollarlo qui,
   ottiene già `[Accordo]parola` uguale al nostro — cambiano solo queste,
   che noi non useremmo altrimenti e finirebbero come testo a schermo. */
export function chordProDirective(line: unknown): { name: string; value: string } | null {
  const m = /^\s*\{([^:{}]+)(?::\s*([^{}]*))?\}\s*$/.exec(String(line || ''));
  return m ? { name: m[1].trim().toLowerCase(), value: (m[2] || '').trim() } : null;
}
const CP_SECTIONS: Record<string, string> = {
  soc: 'Chorus', start_of_chorus: 'Chorus',
  sov: 'Verse', start_of_verse: 'Verse',
  sob: 'Bridge', start_of_bridge: 'Bridge',
  sot: 'Tab', start_of_tab: 'Tab'
};
const CP_COMMENTS = ['c', 'comment', 'ci', 'comment_italic', 'cb', 'comment_box'];

export function tabToChordPro(text: unknown): string {
  const lines = String(text || '').replace(/\r\n?/g, '\n').split('\n');
  const secOnly = (l: string) => /^\s*\[([^\[\]]+)\]\s*$/.exec(l);
  const out: string[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const dir = chordProDirective(line);
    if (dir) {
      if (CP_COMMENTS.includes(dir.name) && dir.value) out.push('# ' + dir.value);
      else if (CP_SECTIONS[dir.name]) out.push('# ' + CP_SECTIONS[dir.name]);
      /* le altre — title, artist, key, capo, tempo, eoc/eov/eob... — non
         hanno posto nel corpo: si leggono e si scartano */
      i++; continue;
    }
    const sec = secOnly(line);
    if (sec && !isChordLine(line)) { out.push('# ' + sec[1].trim()); i++; continue; }
    if (isChordLine(line)) {
      const run = [line];
      let j = i + 1;
      while (j < lines.length && isChordLine(lines[j])) { run.push(lines[j]); j++; }
      const after = lines[j];
      const afterIsLyric = after !== undefined && after.trim() !== '' &&
                           !isChordLine(after) && !secOnly(after);
      if (afterIsLyric) {
        /* L'ultima riga del blocco entra sempre nella fusione. Quelle
           prima entrano solo finché sono un frammento (pochi accordi):
           una riga con più accordi letta all'indietro è un giro dichiarato
           per conto suo — es. il richiamo strumentale prima di un
           interlude — e resta separata invece di finire fusa nel verso. */
        let k = run.length - 1;
        while (k > 0 && countTokens(run[k - 1]) <= 2) k--;
        run.slice(0, k).forEach(l => out.push(wrapChordsInPlace(l)));
        out.push(mergeChordLyric(run.slice(k), after));
        i = j + 1;
      } else { run.forEach(l => out.push(wrapChordsInPlace(l))); i = j; }
      continue;
    }
    out.push(line); i++;
  }
  return out.join('\n').replace(/\n{3,}/g, '\n\n').trim();
}

/* Un tab incollato grezzo nel campo sbagliato: righe di soli accordi senza
   quadre. Di suo l'app se lo mangia senza fiatare — le etichette [Chorus]
   diventano accordi e le colonne non tornano più — e te ne accorgi solo a
   brano aperto, che è il momento peggiore. Meglio dirlo al salvataggio.
   Le righe già convertite hanno le quadre, quindi non fanno rumore. */
export const looksRawTab = (body: unknown): boolean => String(body ?? '').split('\n')
  .some(l => !l.includes('[') && isChordLine(l));
