// @ts-nocheck — molti casi passano apposta dati storti (bpm come stringa,
// segnaposto senza durata, null): è proprio la robustezza che si verifica.
/* Test delle funzioni pure, portati da test.js così com'erano: stessi
   casi, stessi nomi, stesso confronto per JSON. Ogni `eq` è un test.
   Prima si estraeva un blocco PURE-START/PURE-END da index.html; adesso
   le funzioni sono moduli e si importano e basta. */
import { test, expect } from 'vitest';
import { transpose, spell, spellLine, showChords, useFlats, flatFor, rootOf, rootsLine,
         toEnglish, toEnglishLine, normalizeBody, roman } from '../src/core/chords';
import { segs, renderSheet, renderNotes } from '../src/core/sheet';
import { secs, mmss, signed, bpmFromTaps } from '../src/core/time';
import { speedFor, setDuration, ordinals, moveItem, setAsText } from '../src/core/setlist';
import { sortSongs } from '../src/core/library';
import { migrate, parseTags, parseBpm } from '../src/core/model';
import { isChordLine, wrapChordsInPlace, mergeChordLyric, tabToChordPro as ugToChordPro,
         chordProDirective, looksRawTab } from '../src/core/convert';

const eq = (got: any, exp: any, name: string) => {
  const g = JSON.stringify(got), e = JSON.stringify(exp);
  test(name, () => { expect(g).toBe(e); });
};
const has = (h: string, s: string) => h.includes(s);

/* ── trasposizione ─────────────────────────────────────── */
eq(transpose('Am', 2), 'Bm', 'triade minore');
eq(transpose('F#m7', 1), 'Gm7', 'diesis in fondamentale');
eq(transpose('Bb', 2), 'C', 'bemolle in fondamentale');
eq(transpose('Ebmaj7', 1), 'Emaj7', 'bemolle + sigla');
eq(transpose('Am/G', 3), 'Cm/A#', 'accordo con basso');
eq(transpose('Cmaj7', 5), 'Fmaj7', 'maj7');
eq(transpose('Dm7b5', 2), 'Em7b5', 'semidiminuito: la b5 non si tocca');
eq(transpose('E7#9', -4), 'C7#9', 'tensione alterata intatta');
eq(transpose('Csus4', 2), 'Dsus4', 'sospeso');
eq(transpose('Fadd9', -1), 'Eadd9', 'add9');
eq(transpose('G', 12), 'G', 'ottava = identità');
eq(transpose('G', -1), 'F#', 'passo negativo');
eq(transpose('Am', 0), 'Am', 'zero = identità');
eq(transpose('Am  F  C  G', 2), 'Bm  G  D  A', 'riga di soli accordi, spazi preservati');
eq(transpose('Em | C | D', 1), 'Fm | C# | D#', 'separatore pipe');

/* ── quello che NON deve essere trasposto ──────────────── */
eq(transpose('N.C.', 3), 'N.C.', 'N.C. resta N.C.');
eq(transpose('Break', 2), 'Break', 'parola che inizia per B');
eq(transpose('Tacet', 5), 'Tacet', 'indicazione testuale');
eq(transpose('Am/Sol', 2), 'Am/Sol', 'basso non valido: non tocco niente');

/* ── parser ChordPro ───────────────────────────────────── */
eq(segs('[Am]ciao[F]bella'), [{c:'Am',t:'ciao'},{c:'F',t:'bella'}], 'due accordi');
eq(segs('Testo senza accordi'), [{c:null,t:'Testo senza accordi'}], 'solo testo');
eq(segs('[Am][F]'), [{c:'Am',t:''},{c:'F',t:''}], 'accordi adiacenti');
eq(segs('prima [C]dopo'), [{c:null,t:'prima '},{c:'C',t:'dopo'}], 'testo prima del primo accordo');
eq(segs(''), [{c:null,t:''}], 'riga vuota');
eq(segs('[Am]coda finale'), [{c:'Am',t:'coda finale'}], 'testo in coda');

/* ── notazione italiana in uscita ──────────────────────── */
eq(spell('Am', true, false), 'Lam', 'La minore');
eq(spell('C', true, false), 'Do', 'Do maggiore');
eq(spell('G', true, false), 'Sol', 'Sol');
eq(spell('F#m7', true, false), 'Fa#m7', 'la sigla non si tocca');
eq(spell('Cmaj7', true, false), 'Domaj7', 'maj7 resta maj7');
eq(spell('Am/G', true, false), 'Lam/Sol', 'anche il basso si traduce');
eq(spell('Bb', true, true), 'Sib', 'Si bemolle');
eq(spell('Eb', true, true), 'Mib', 'Mi bemolle');
eq(spell('Db', true, true), 'Reb', 'Re bemolle');
eq(spell('Gb', true, true), 'Solb', 'Sol bemolle');
eq(spell('Ab', true, true), 'Lab', 'La bemolle');
eq(spell('A#', true, false), 'La#', 'La diesis quando si vogliono i diesis');
eq(spell('A#', true, true), 'Sib', 'lo stesso suono coi bemolli è Si bemolle');
eq(spell('A#', false, true), 'Bb', 'in inglese coi bemolli');
eq(spell('N.C.', true, false), 'N.C.', 'N.C. non è un accordo e non si traduce');
eq(spell('Break', true, false), 'Break', 'una parola non diventa un accordo');
eq(spellLine('Am  F  C  G', true, false), 'Lam  Fa  Do  Sol', 'riga di accordi, spazi preservati');
eq(spellLine('Em | C | D', true, false), 'Mim | Do | Re', 'separatore pipe');
eq(spellLine('', true, false), '', 'stringa vuota');
eq(showChords('Am', 2, true, false), 'Sim', 'traspone e poi traduce');
eq(showChords('Am', 0, false, false), 'Am', 'senza opzioni non cambia niente');

/* ── diesis o bemolli ──────────────────────────────────── */
eq(useFlats('F'), true, 'Fa maggiore ha i bemolli');
eq(useFlats('Bb'), true, 'Si bemolle maggiore');
eq(useFlats('Eb'), true, 'Mi bemolle maggiore');
eq(useFlats('C'), false, 'Do maggiore: nessuna alterazione, si usano i diesis');
eq(useFlats('D'), false, 'Re maggiore ha i diesis');
eq(useFlats('A'), false, 'La maggiore ha i diesis');
eq(useFlats('Dm'), true, 'Re minore ha un bemolle');
eq(useFlats('Gm'), true, 'Sol minore ha due bemolli');
eq(useFlats('Cm'), true, 'Do minore ha tre bemolli');
eq(useFlats('Am'), false, 'La minore: nessuna alterazione');
eq(useFlats('Em'), false, 'Mi minore ha un diesis');
eq(useFlats('Cmaj7'), false, 'maj7 non è una tonalità minore');
eq(useFlats('Fmaj7'), true, 'Fa maggiore settima resta Fa');
eq(useFlats(''), false, 'tonalità vuota');
eq(useFlats('?'), false, 'tonalità non riconosciuta');
eq(flatFor('D', 'flat'), true, 'forzati i bemolli');
eq(flatFor('F', 'sharp'), false, 'forzati i diesis');
eq(flatFor('F', 'auto'), true, 'in automatico decide la tonalità');
eq(flatFor('D', 'auto'), false, 'in automatico, Re prende i diesis');

/* ── notazione italiana in ingresso ────────────────────── */
eq(toEnglish('Lam'), 'Am', 'Lam → Am');
eq(toEnglish('Sol'), 'G', 'Sol → G');
eq(toEnglish('Sib'), 'Bb', 'Sib → Bb');
eq(toEnglish('Do#m7'), 'C#m7', 'Do#m7 → C#m7');
eq(toEnglish('Mi'), 'E', 'Mi → E');
eq(toEnglish('Re'), 'D', 'Re → D');
eq(toEnglish('Si'), 'B', 'Si → B');
eq(toEnglish('Fa'), 'F', 'Fa → F');
eq(toEnglish('Solb'), 'Gb', 'Solb → Gb');
eq(toEnglish('Lab'), 'Ab', 'Lab → Ab');
eq(toEnglish('Lam/Sol'), 'Am/G', 'anche il basso');
eq(toEnglish('Am/Sol'), 'Am/G', 'notazione mista: si sistema quel che manca');
/* l'inglese ha la precedenza, ed è quello che toglie ogni ambiguità */
eq(toEnglish('Fadd9'), 'Fadd9', 'Fadd9 è inglese valido, non Fa + dd9');
eq(toEnglish('Faug'), 'Faug', 'Faug è inglese valido, non Fa + ug');
eq(toEnglish('Am'), 'Am', 'un accordo inglese non si tocca');
eq(toEnglish('F'), 'F', 'una lettera sola resta');
eq(toEnglish('Break'), 'Break', 'una parola qualsiasi resta');
eq(toEnglish('N.C.'), 'N.C.', 'N.C. resta');
eq(toEnglish(''), '', 'stringa vuota');
eq(toEnglishLine('Lam  Fa  Do  Sol'), 'Am  F  C  G', 'riga intera, spazi preservati');
eq(normalizeBody('[Lam]ciao [Sol]bella'), '[Am]ciao [G]bella', 'conversione dentro le quadre');
eq(normalizeBody('La sala chiude ma noi'), 'La sala chiude ma noi',
   'il testo cantato non si tocca: "La" resta una parola');
eq(normalizeBody('[Lam]La sala chiude'), '[Am]La sala chiude',
   'si converte l\'accordo e si lascia stare il testo');
eq(normalizeBody('# Strofa\n[Sib]testo'), '# Strofa\n[Bb]testo', 'le sezioni non si toccano');
eq(normalizeBody(''), '', 'body vuoto');

/* ── modalità solo-struttura ───────────────────────────── */
const chart = '# Strofa\n[Am]ciao [F]bella\nsolo testo qui\n\n[C]  [G]';
const full = renderSheet(chart, {});
eq(has(full, 'ciao '), true, 'vista normale: il testo c\'è');
eq(has(full, 'solo testo qui'), true, 'vista normale: riga di solo testo presente');

const st = renderSheet(chart, {struct:true});
eq(has(st, 'ciao'), false, 'struttura: il testo sparisce');
eq(has(st, 'solo testo qui'), false, 'struttura: la riga di solo testo sparisce del tutto');
eq(has(st, '>Strofa<'), true, 'struttura: le sezioni restano');
eq((st.match(/class="ch"/g)||[]).length, 4, 'struttura: restano tutti e quattro gli accordi');
eq(has(st, '<div class="blank">'), true, 'struttura: le righe vuote restano, spaziano la pagina');
eq(has(renderSheet(chart, {tr:2, struct:true}), '>Bm<'), true, 'struttura: la trasposizione si applica lo stesso');
eq(renderSheet('solo testo', {struct:true}), '', 'struttura: un brano di solo testo non produce niente');
eq(has(renderSheet(chart, {it:true}), '>Lam<'), true, 'il chart si legge in italiano');
eq(has(renderSheet('[Bb]x', {it:true, flat:true}), '>Sib<'), true, 'italiano coi bemolli');
eq(has(renderSheet('[A#]x', {flat:true}), '>Bb<'), true, 'inglese coi bemolli');

/* ── modalità senza accordi (il foglio di chi canta) ───── */
/* Lo specchio esatto di solo-struttura: lì spariscono le parole, qui gli
   accordi. Sul `chart` qui sopra: '# Strofa', '[Am]ciao [F]bella',
   'solo testo qui', riga vuota, '[C]  [G]'. */
const pl = renderSheet(chart, {plain:true});
eq(has(pl, 'ciao bella'), true, 'senza accordi: il testo resta, e resta intero');
eq(has(pl, 'solo testo qui'), true, 'senza accordi: la riga di solo testo resta');
eq((pl.match(/class="ch"/g)||[]).length, 0, 'senza accordi: nessun accordo a schermo');
eq(has(pl, '>Strofa<'), true, 'senza accordi: le sezioni restano, dicono dove sei');
eq(has(pl, '<div class="blank">'), true, 'senza accordi: le righe vuote spaziano ancora');
/* Una riga di soli accordi — intro, stacco, giro strumentale — sparisce
   del tutto: senza accordi lì non c'era niente da leggere, e lasciarla
   vuota aggiungerebbe solo un buco in mezzo al testo. */
eq((pl.match(/class="lyr"/g)||[]).length, 2,
   'senza accordi: la riga di soli accordi sparisce, non lascia il vuoto');
eq(renderSheet('[C]  [G]\n[Am]  [F]', {plain:true}), '',
   'senza accordi: un brano di soli accordi non produce niente');
/* Il testo si ricompone unito: le quadre spezzano la riga in pezzi, e
   se non si rincollassero si leggerebbe "ciao" e "bella" staccati. */
eq(has(renderSheet('[Am]par[F]ola', {plain:true}), '>parola<'), true,
   'senza accordi: una parola spezzata da un accordo torna intera');
/* Solo-struttura e senza-accordi si escludono: se arrivano insieme vince
   la struttura, che e' un tasto premuto adesso e non una preferenza. */
eq((renderSheet(chart, {struct:true, plain:true}).match(/class="ch"/g)||[]).length, 4,
   'insieme: vince la struttura, che e\' l\'azione esplicita');

/* ── escaping ──────────────────────────────────────────── */
eq(has(renderSheet('<script>x</script>', {}), '&lt;script&gt;'), true, 'il testo viene escapato');
eq(has(renderNotes({Tutti:'<b>x</b>'}, 'Basso'), '&lt;b&gt;'), true, 'anche le note vengono escapate');

/* ── note per musicista ────────────────────────────────── */
eq(renderNotes({}, 'Basso'), '', 'nessuna nota: niente da mostrare');
eq(renderNotes(null, 'Basso'), '', 'note assenti: nessun errore');
eq(has(renderNotes({Voce:'attacca tu'}, 'Basso'), 'attacca tu'), false, 'la nota di un altro ruolo non si vede');
eq(has(renderNotes({Basso:'mia nota'}, 'Basso'), 'mia nota'), true, 'la mia nota si vede');
eq(has(renderNotes({Tutti:'stop sul 3'}, 'Basso'), 'stop sul 3'), true, 'la nota "Tutti" si vede sempre');
{
  const out = renderNotes({Tutti:'generale', Basso:'personale'}, 'Basso');
  eq(out.indexOf('personale') < out.indexOf('generale'), true, 'la mia nota viene prima di quella della band');
  eq((out.match(/class="note"/g)||[]).length, 2, 'due note, due blocchi');
}
eq((renderNotes({Tutti:'x'}, 'Tutti').match(/class="note"/g)||[]).length, 1,
   'ruolo Tutti: la nota generale non viene duplicata');

/* ── capotasto ─────────────────────────────────────────── */
eq(roman(3), 'III', 'capo al terzo tasto');
eq(roman(0), '', 'capo a zero: niente');
eq(roman(5), 'V', 'capo al quinto');
eq(roman(11), 'XI', 'capo all\'undicesimo');
/* il capo abbassa quello che leggi, non quello che suoni */
eq(transpose('Eb', 0 - 3), 'C', 'pezzo in Mi bemolle, capo al III: si legge in Do');
eq(transpose('Ab', 0 - 3), 'F', 'e il Lab diventa Fa');

/* ── tempo ─────────────────────────────────────────────── */
eq(secs('3:40'), 220, 'durata in secondi');
eq(secs('0:05'), 5, 'durata sotto il minuto');
eq(secs(''), 0, 'durata vuota');
eq(secs('tre minuti'), 0, 'durata non valida');
eq(mmss(220), '3:40', 'secondi in mm:ss');
eq(mmss(5), '0:05', 'lo zero davanti ai secondi c\'è');
eq(signed(130), '+2:10', 'scarto positivo');
eq(signed(-45), '−0:45', 'scarto negativo');
eq(signed(0), '+0:00', 'scarto nullo');
eq(signed(3930), '+65:30', 'oltre l\'ora si continua in minuti');

/* ── velocità di scorrimento ───────────────────────────── */
eq(speedFor(0, 220, 1, 0), 0, 'niente da scorrere: fermo');
eq(speedFor(-10, 220, 1, 0), 0, 'scrollable negativo: fermo');
eq(speedFor(2200, 220, 1, 0), 10, 'il chart finisce esattamente col brano');
eq(speedFor(2200, 220, 1.5, 0), 15, 'la correzione moltiplica');
eq(speedFor(2200, 220, 0.5, 0), 5, 'correzione al ribasso');
eq(speedFor(1000, 0, 1, 0), 20, 'senza durata si ripiega su 20 px/s');
eq(speedFor(1000, 0, 2, 0), 40, 'la correzione vale anche sul ripiego');
eq(speedFor(10, 220, 1, 0), 1, 'mai sotto 1 px/s, se no non si muove');
eq(speedFor(2200, 220, 0, 0), 10, 'correzione a zero: vale 1, non blocca lo scorrimento');
eq(speedFor(2200, 220, undefined, 0), 10, 'correzione assente: vale 1');
/* l'attesa iniziale toglie tempo utile, quindi si scorre più in fretta */
eq(speedFor(2000, 220, 1, 20), 10, 'con 20s di attesa restano 200s per 2000px');
eq(speedFor(2200, 220, 1, undefined), 10, 'attesa assente: come attesa a zero');
eq(speedFor(2200, 220, 1, 300), 2200, 'attesa più lunga del brano: resta un secondo utile');
eq(speedFor(1000, 0, 1, 30), 20, 'senza durata l\'attesa non c\'entra niente');

/* ── durata, numerazione e riordino della scaletta ─────── */
{
  const songs = [{id:'a',dur:'3:00'},{id:'b',dur:'2:30'}];
  const items = [{type:'song',id:'a'},{type:'mark',label:'Pausa',dur:'15:00'},{type:'song',id:'b'}];
  eq(setDuration(items, songs), 180+900+150, 'i segnaposto contano nella durata');
  eq(setDuration([{type:'song',id:'zz'}], songs), 0, 'brano sconosciuto: zero, non esplode');
  eq(setDuration([], songs), 0, 'scaletta vuota');
  eq(setDuration(null, songs), 0, 'items assenti');
  eq(setDuration([{type:'mark',label:'Pausa'}], songs), 0, 'segnaposto senza durata');
  eq(ordinals(items), [1,0,2], 'i segnaposto non consumano un numero');
  eq(ordinals([{type:'mark',label:'x'}]), [0], 'solo segnaposto: nessun numero');
  eq(ordinals([]), [], 'niente items, niente numeri');
}
{
  const a = [1,2,3,4];
  eq(moveItem(a, 0, 2), [2,3,1,4], 'sposta in avanti');
  eq(moveItem(a, 3, 0), [4,1,2,3], 'sposta all\'inizio');
  eq(moveItem(a, 1, 1), [1,2,3,4], 'stessa posizione: niente cambia');
  eq(moveItem(a, 0, 99), [2,3,4,1], 'destinazione oltre la fine: va in fondo');
  eq(moveItem(a, 0, -5), [1,2,3,4], 'destinazione negativa: va in cima');
  eq(a, [1,2,3,4], 'l\'array di partenza non viene toccato');
  eq(moveItem(a, 9, 0), [1,2,3,4], 'partenza fuori range: copia invariata');
  eq(moveItem(null, 0, 1), [], 'array assente');
}

/* ── ordinamento del repertorio ────────────────────────── */
{
  const songs = [
    { id:'c', title:'Zebra',   artist:'Nada',    key:'G',  bpm:100, tr:0 },
    { id:'a', title:'ancora',  artist:'copycut', key:'C',  bpm:140, tr:0 },
    { id:'b', title:'Mezzo',   artist:'Alfa',    key:'Am', bpm:'',  tr:2 }
  ];
  eq(sortSongs(songs,'add').map(s=>s.id), ['c','a','b'], 'inserimento: l\'ordine resta');
  eq(sortSongs(songs,'title').map(s=>s.id), ['a','b','c'], 'titolo, ignorando maiuscole e accenti');
  eq(sortSongs(songs,'artist').map(s=>s.id), ['b','a','c'], 'artista');
  eq(sortSongs(songs,'bpm').map(s=>s.id), ['c','a','b'], 'bpm crescente, i vuoti in fondo');
  eq(sortSongs(songs,'key').map(s=>s.id), ['a','c','b'], 'tonalità: Do, Sol, e il La+2 diventa Si');
  eq(songs.map(s=>s.id), ['c','a','b'], 'il repertorio non viene riordinato davvero');
  eq(sortSongs(null,'title'), [], 'nessun brano');
}

/* ── tag e bpm ─────────────────────────────────────────── */
eq(parseTags('Rock, Matrimonio'), ['rock','matrimonio'], 'tag normalizzati in minuscolo');
eq(parseTags(' rock ,, , cover '), ['rock','cover'], 'spazi e vuoti scartati');
eq(parseTags('rock, Rock, ROCK'), ['rock'], 'niente duplicati');
eq(parseTags(''), [], 'stringa vuota: nessun tag');
eq(parseTags(undefined), [], 'undefined: nessun tag');
eq(parseBpm('128'), 128, 'bpm da stringa a numero');
eq(parseBpm(' 96 '), 96, 'bpm con spazi');
eq(parseBpm(140), 140, 'bpm già numero');
eq(parseBpm(''), '', 'bpm vuoto resta vuoto');
eq(parseBpm('presto'), '', 'bpm non numerico scartato');

/* ── la scaletta in chiaro, da mandare alla band ────────── */
{
  const songs = [
    { id:'s1', title:'Primo pezzo', key:'Am', dur:'3:40' },
    { id:'s2', title:'Secondo pezzo', key:'F',  dur:'4:20' },
    { id:'s3', title:'Terzo senza durata', key:'C' }
  ];
  const items = [
    { type:'song', id:'s1' },
    { type:'mark', label:'Pausa', dur:'15:00' },
    { type:'song', id:'s2' },
    { type:'song', id:'s3' }
  ];
  const txt = setAsText(items, songs, { band:'COPYCUT', name:'Sabato sera' });
  const righe = txt.split('\n');

  eq(righe[0], 'COPYCUT — Sabato sera', 'in testa gruppo e scaletta');
  eq(righe[1], '3 brani · 1 segnaposto · 23:00', 'conto e durata, segnaposto compreso');
  eq(righe[2], '', 'una riga vuota prima dell\'elenco');
  eq(righe[3], '01. Primo pezzo — Am — 3:40', 'numero, titolo, tonalità, durata');
  eq(righe[4], '--- Pausa (15:00)', 'il segnaposto resta al suo posto');
  /* La numerazione salta i segnaposto: "02" deve voler dire il secondo
     PEZZO, che è quello che si conta sul palco. */
  eq(righe[5], '02. Secondo pezzo — F — 4:20', 'il segnaposto non consuma un numero');
  eq(righe[6], '03. Terzo senza durata — C', 'campi vuoti: niente trattini a vuoto');

  /* La tonalità dipende da notazione e alterazioni, che sono scelte di chi
     legge: la funzione non le conosce e se le fa passare da fuori. */
  eq(setAsText(items, songs, { keyOf: s => ({Am:'Lam', F:'Fa', C:'Do'}[s.key]) }).split('\n')[2],
     '01. Primo pezzo — Lam — 3:40', 'la tonalità arriva da fuori, in italiano');

  eq(setAsText([], [], { band:'X', name:'Vuota' }),
     'X — Vuota\n0 brani · 0:00', 'scaletta vuota: intestazione e basta');
  eq(setAsText(items, songs).split('\n')[0], '3 brani · 1 segnaposto · 23:00',
     'senza nomi non resta una riga vuota in cima');
  /* Un brano cancellato dal repertorio ma rimasto in scaletta viene saltato
     senza rompere niente, però il suo numero resta speso: "02" con un solo
     pezzo a schermo. È lo stesso identico comportamento della lista a video,
     ed è voluto — il testo deve dire quello che vedi, non correggerlo di
     nascosto. Nell'uso normale il caso non capita: eliminare un brano lo
     toglie anche da tutte le scalette. */
  eq(setAsText([{type:'song',id:'fantasma'},{type:'song',id:'s1'}], songs).split('\n')[2],
     '02. Primo pezzo — Am — 3:40', 'brano sparito: saltato, ma il numero resta speso come a schermo');
}

/* ── solo fondamentali (per chi suona il basso) ─────────── */
eq(rootOf('F#m7'), 'F#', 'la sigla si butta, resta la nota');
eq(rootOf('Cmaj7'), 'C', 'anche le sigle lunghe');
eq(rootOf('Bb'), 'Bb', 'una nota sola resta uguale');
eq(rootOf('Am'), 'A', 'minore: la nota non cambia');
/* Le rivolti sono l'unico caso in cui prendere la fondamentale sarebbe
   l'errore: la barra è scritta lì apposta per il bassista. */
eq(rootOf('C/G'), 'G', 'rivolto: vince il basso, non la fondamentale');
eq(rootOf('D/F#'), 'F#', 'rivolto con alterazione al basso');
eq(rootOf('Am7/G'), 'G', 'rivolto con sigla: sempre il basso');
eq(rootOf('N.C.'), 'N.C.', 'quello che non è un accordo non si tocca');
eq(rootOf('x2'), 'x2', 'le annotazioni restano come sono');
eq(rootsLine('F#m7  C/G  Am'), 'F#  G  A', 'riga intera, spazi conservati');
eq(rootsLine('Am | F | C'), 'A | F | C', 'le stanghette di battuta non si toccano');

/* L'ordine dei passaggi conta: prima si traspone l'accordo intero, poi si
   riduce alla nota, e solo alla fine si riscrive nella notazione scelta. */
eq(showChords('F#m7', 0, false, false, true), 'F#', 'fondamentale in inglese');
eq(showChords('F#m7', 0, true, false, true), 'Fa#', 'fondamentale in italiano');
eq(showChords('F#m7', 2, false, false, true), 'G#', 'trasposta e poi ridotta');
eq(showChords('C/G', 0, true, false, true), 'Sol', 'rivolto ridotto e tradotto');
eq(showChords('F#m7', 0, false, false, false), 'F#m7', 'spenta: accordo intero');
eq(showChords('Bbmaj7', 0, true, true, true), 'Sib', 'bemolli rispettati anche ridotta');

/* ── convertitore Ultimate Guitar ───────────────────────── */
eq(isChordLine('Am     C       G'), true, 'riga di soli accordi');
eq(isChordLine('Am     C       Grease'), false, 'una parola non è un accordo: non è chord line');
eq(isChordLine('e|--0--2--3--'), false, 'riga di tab per chitarra: non è chord line');
eq(isChordLine(''), false, 'riga vuota: non è chord line');

/* Le annotazioni che UG mette in mezzo agli accordi non devono far
   scartare la riga: prima una sola bastava a mandarla a schermo come
   testo cantato. */
eq(isChordLine('C           G        Am   x2'), true, 'ripetizione x2 in coda: resta chord line');
eq(isChordLine('Am  F  2x'), true, 'ripetizione scritta 2x: resta chord line');
eq(isChordLine('N.C.        F'), true, 'N.C. fra gli accordi: resta chord line');
eq(isChordLine('C  (let ring)   G'), true, 'nota fra parentesi: resta chord line');
eq(isChordLine('Am | F | C'), true, 'stanghette di battuta: restano chord line');
eq(isChordLine('x2'), false, 'solo annotazioni, nessun accordo: non è chord line');
eq(isChordLine('Cmaj7(add9)  F'), true, 'parentesi attaccate alla sigla: fanno parte dell\'accordo');
eq(isChordLine('C  (let ring)   Grease'), false,
   'una parola vera resta parola: le annotazioni non salvano la riga');

eq(wrapChordsInPlace('Am     C   x2'), '[Am]     [C]   x2',
   'riga di soli accordi: l\'annotazione resta, serve al musicista');
{
  const chordLine = 'N.C.' + ' ' + 'F';   // F alla colonna di 'b' in "bella"
  eq(mergeChordLyric(chordLine, 'Ciao bella'), 'Ciao [F]bella',
     'sopra il testo l\'annotazione si scarta: non va infilata fra le parole');
}
{
  const chordLine = 'C' + ' '.repeat(9) + 'G   x2';   // G alla colonna di "corsa"
  eq(ugToChordPro(chordLine + '\nSempre di corsa'),
     '[C]Sempre di [G]corsa',
     'chord line con x2 sopra il testo: ora si fonde invece di finire cantata');
}

eq(wrapChordsInPlace('Am     C       G'), '[Am]     [C]       [G]',
   'accordi incorniciati sul posto, spazi intatti');

{
  const chordLine = 'Am' + ' '.repeat(3) + 'F';   // F alla colonna di 'b' in "bella"
  eq(mergeChordLyric(chordLine, 'Ciao bella'), '[Am]Ciao [F]bella',
     "accordi inseriti alla colonna giusta, sotto l'inizio della sillaba");
}
/* Un accordo la cui colonna cade oltre la fine del verso entra DOPO le
   parole: è l'ultimo giro che parte mentre la frase è già finita. Prima
   veniva appiccicato all'ultima sillaba e sembrava che ci si cantasse
   sopra; la riga si allunga di spazi e l'accordo resta alla sua colonna. */
eq(mergeChordLyric('     G', 'Hey'), 'Hey  [G]',
   'colonna oltre la fine riga: l\'accordo resta dov\'era, non in coda alla parola');
eq(mergeChordLyric('          G', 'Hey'), 'Hey       [G]',
   'colonna molto oltre la fine: la distanza è quella scritta nel tab');
{
  /* L'invariante che conta davvero: tolte le quadre, ogni accordo deve
     ritrovarsi alla colonna esatta che aveva nel tab. Contare gli spazi a
     occhio non basta, perché una quadra inserita a sinistra spezza il
     riempimento di quelle a destra senza spostarle. */
  const colonneDi = fuso => {
    const cols = []; let testo = '';
    for(const pezzo of fuso.split(/(\[[^\]]+\])/)){
      if(pezzo.startsWith('[')) cols.push(testo.length); else testo += pezzo;
    }
    return cols;
  };
  const dueOltre = ' '.repeat(5) + 'G' + '  ' + 'C';   // G a col 5, C a col 8
  eq(colonneDi(mergeChordLyric(dueOltre, 'Hey')), [5, 8],
     'due accordi oltre la fine: entrambi restano alla loro colonna');
  const dentroEfuori = 'Am' + ' '.repeat(8) + 'F';      // Am a col 0, F a col 10
  eq(colonneDi(mergeChordLyric(dentroEfuori, 'Ciao')), [0, 10],
     'uno dentro il verso e uno oltre: le colonne valgono per entrambi');
  eq(colonneDi(mergeChordLyric('  Am    F', 'Ciao bella')), [2, 8],
     'entrambi dentro il verso: nessun riempimento, colonne intatte');
}
eq(mergeChordLyric('Am   F', 'Ciao'), '[Am]Ciao [F]',
   'uno dentro e uno oltre: quello dentro non si sposta');
eq(mergeChordLyric('Am', ''), '[Am]', 'nessun testo sotto: accordo comunque incorniciato');
eq(mergeChordLyric('  Am', ''), '  [Am]', 'testo vuoto e colonna rientrata: la colonna si rispetta');

{
  const chordLine = 'Am' + ' '.repeat(3) + 'F';
  eq(ugToChordPro('[Chorus]\n' + chordLine + '\nCiao bella'),
     '# Chorus\n[Am]Ciao [F]bella', 'etichetta di sezione tra quadre diventa riga #');
}
{
  const chordLine = 'Am' + ' '.repeat(7) + 'F' + ' '.repeat(6) + 'C' + ' '.repeat(3) + 'G';
  eq(ugToChordPro(chordLine + '\nTanto il giorno non arriva mai'),
     '[Am]Tanto il [F]giorno [C]non [G]arriva mai',
     'chord line + riga di testo fuse in una sola riga ChordPro');
}
eq(ugToChordPro('Intro\nAm  F  C  G'),
   'Intro\n[Am]  [F]  [C]  [G]',
   'chord line senza testo sotto: incorniciata sul posto, la riga sopra passa invariata');
eq(ugToChordPro('e|-----0-----2-----|\nB|-----1-----3-----|'),
   'e|-----0-----2-----|\nB|-----1-----3-----|',
   'righe di tab per chitarra non riconosciute come accordi: passano invariate');

/* UG a volte spezza gli accordi di UNA riga di testo su più righe
   sovrapposte quando altrimenti si scriverebbero uno sull'altro: vanno
   fuse tutte insieme nel verso, non solo l'ultima. */
eq(ugToChordPro('Ab\nB   Db\nWhoa la la'), '[Ab][B]Whoa[Db] la la',
   'più righe di frammenti (pochi accordi) prima del verso: fuse tutte insieme');
/* Ma una riga con un giro completo (molti accordi) letta a ritroso è
   dichiarata per conto suo, non un frammento del verso che segue: solo
   le righe corte più vicine al testo entrano nella fusione. */
eq(ugToChordPro('Am F C\nG\nC\nLa la la la'), '[Am] [F] [C]\n[G][C]La la la la',
   'un giro completo prima dei frammenti resta separato dal verso');
/* A parità di colonna fra righe impilate vince l'ordine con cui erano
   scritte, non quello che verrebbe inserendole da destra a sinistra. */
eq(mergeChordLyric(['G','C'], 'Hey'), '[G][C]Hey',
   'due accordi alla stessa colonna: resta l\'ordine delle righe originali');

/* Un tab intero con le annotazioni sparse dentro, come li scrivono su UG:
   ripetizioni in coda al giro, N.C. sull'attacco, stanghette di battuta,
   una nota fra parentesi. Le righe isolate qui sopra non bastavano a dire
   se il convertitore regge un brano vero dall'inizio alla fine. */
{
  /* Mette ogni accordo sopra la parola indicata invece di contare spazi a
     mano: il test dichiara dove lo vuole e la riga si costruisce da sola. */
  const sopra = (testo, coppie) => {
    let riga = '';
    for(const [parola, acc] of coppie) riga = riga.padEnd(testo.indexOf(parola), ' ') + acc;
    return riga;
  };
  const l1 = 'Canto la prima riga';
  const l2 = 'e poi la seconda';
  const l3 = 'la terza chiude il giro';

  const tab = [
    '[Intro]',
    'Am  F  C  G  x2',
    '',
    '[Verse]',
    sopra(l1, [['Canto','Am'], ['prima','F']]), l1,
    'N.C.' + sopra(l2, [['seconda','C']]).slice(4), l2,
    sopra(l3, [['la','Am'], ['chiude','C']]) + '  (let ring)', l3,
    '',
    '[Bridge]',
    'Am | F | C',
    '',
    '[Outro]',
    'Am  F  4x'
  ].join('\n');
  const righe = ugToChordPro(tab).split('\n').filter(r => r.trim());

  eq(righe[0], '# Intro', 'sezione riconosciuta');
  eq(righe[1], '[Am]  [F]  [C]  [G]  x2',
     'giro isolato: accordi incorniciati, la ripetizione resta a vista');
  eq(righe[3], '[Am]Canto la [F]prima riga', 'riga normale, accordi sulla sillaba');
  eq(righe[4], 'e poi la [C]seconda', 'N.C. scartato, l\'accordo dopo resta alla sua colonna');
  eq(righe[5], '[Am]la terza [C]chiude il giro',
     'stanghette e nota fra parentesi tolte, gli accordi al loro posto');
  eq(righe[7], '[Am] | [F] | [C]', 'giro a battute: le stanghette restano fra gli accordi');
  eq(righe[9], '[Am]  [F]  4x', 'ripetizione in coda anche nel finale');
  eq(righe.some(r => /(^|\s)(x2|4x|N\.C\.|let ring)/.test(r.replace(/\[[^\]]*\]/g,'')) &&
                     r.replace(/\[[^\]]*\]/g,'').trim().split(/\s+/).length > 3), false,
     'nessuna annotazione finita in mezzo a una riga cantata');
  eq(righe.join('\n').match(/\[/g).length, 14, 'quattordici accordi, nessuno perso per strada');
}

/* Rete di sicurezza al salvataggio: riconoscere un tab incollato grezzo
   nel campo "Accordi e testo", che l'app altrimenti si mangia in silenzio
   trasformando le etichette di sezione in accordi. */
eq(looksRawTab('Am     F\nCiao bella'), true, 'accordi senza quadre sopra il testo: tab grezzo');
eq(looksRawTab('[Intro]\nAm F C G'), true, 'etichetta fra quadre e giro sciolto: tab grezzo');
eq(looksRawTab('[Am]Ciao [F]bella'), false, 'già convertito: nessun allarme');
eq(looksRawTab('# Intro\n[Am]  [F]  [C]'), false, 'sezione # e giro fra quadre: nessun allarme');
eq(looksRawTab('# Intro\n[Am]Ciao bella\nSolo testo qui'), false,
   'testo senza accordi in un corpo convertito: nessun allarme');
eq(looksRawTab(''), false, 'corpo vuoto: nessun allarme');
eq(looksRawTab('Prendo io il secondo giro'), false, 'una nota a parole non è un tab grezzo');
eq(looksRawTab('C           G   x2\nSempre di corsa'), true,
   'chord line con annotazione: riconosciuta come grezza, non lasciata passare');

/* Convertire un testo già convertito non deve cambiare niente: è quello
   che rende sicuro il tasto "Converti questo campo", che uno può premere
   anche due volte senza accorgersene. */
{
  const grezzo = '[Verse]\nAm     F\nCiao bella\nC   G\nCome stai';
  const uno = ugToChordPro(grezzo);
  eq(ugToChordPro(uno), uno, 'riconvertire un testo già convertito non lo cambia');
  eq(looksRawTab(uno), false, 'dopo la conversione la guardia tace');
}

/* Chi rifinisce l'allineamento in un editor ChordPro vero prima di
   incollare qui ottiene già [Accordo]parola uguale al nostro: contano
   solo le direttive tra graffe, che altrimenti resterebbero a schermo. */
eq(chordProDirective('{soc}'), {name:'soc', value:''}, 'direttiva senza valore');
eq(chordProDirective('{comment: Bridge}'), {name:'comment', value:'Bridge'}, 'direttiva con valore');
eq(chordProDirective('[Am]Non è una direttiva'), null, 'riga normale: non è una direttiva');
eq(ugToChordPro('{title: Black Velvet}\n{key: Am}\n{soc}\n[Am]Black velvet\n{eoc}\n{comment: Bridge}\n[Am]Ogni parola'),
   '# Chorus\n[Am]Black velvet\n# Bridge\n[Am]Ogni parola',
   'direttive standard: soc/comment diventano #, le altre spariscono, le righe già [Accordo]parola restano intatte');

/* ── migrazione: v1, una scaletta sola nel campo `set` ─── */
{
  const m = migrate({ band:'COPYCUT', songs:[{id:'a',title:'A',bpm:'120'},{id:'b',title:'B'}], set:['b','a'] });
  eq(m.v, 4, 'v1 → v4');
  eq(m.bands.length, 1, 'v1: un gruppo');
  eq(m.bands[0].name, 'COPYCUT', 'v1: il nome della band diventa il nome del gruppo');
  eq(m.bands[0].sets[0].items, [{type:'song',id:'b'},{type:'song',id:'a'}],
     'v1: gli id diventano items, nell\'ordine di prima');
  eq(m.curBand, m.bands[0].id, 'v1: il gruppo creato è quello attivo');
  eq(m.bands[0].songs[0].bpm, 120, 'v1: bpm convertito a numero');
  eq(m.bands[0].songs[0].tags, [], 'v1: tags inizializzati');
  eq(m.bands[0].songs[0].notes, {}, 'v1: notes inizializzate');
  eq(m.bands[0].songs[0].tr, 0, 'v1: trasposizione a zero');
  eq(m.bands[0].songs[0].capo, 0, 'v1: capo a zero');
  eq(m.bands[0].songs[0].scroll, 1, 'v1: scorrimento a uno');
  eq(m.bands[0].songs[0].lead, 0, 'v1: attesa a zero');
  eq(m.bands[0].me, 'Tutti', 'v1: ruolo di default');
}

/* ── migrazione: v2, un gruppo solo con `sets` in cima ─── */
{
  const m = migrate({ band:'COPYCUT', me:'Basso',
    songs:[{id:'a',title:'A',tags:['rock'],notes:{Basso:'n'}}],
    sets:[{id:'s1',name:'Uno',songs:['a']},{id:'s2',name:'Due',songs:[]}], cur:'s2' });
  eq(m.bands.length, 1, 'v2: un gruppo');
  eq(m.bands[0].me, 'Basso', 'v2: ruolo preservato');
  eq(m.bands[0].sets.map(s=>s.name), ['Uno','Due'], 'v2: scalette preservate');
  eq(m.bands[0].cur, 's2', 'v2: scaletta attiva preservata');
  eq(m.bands[0].sets[0].items, [{type:'song',id:'a'}], 'v2: songs → items');
  eq(m.bands[0].songs[0].notes, {Basso:'n'}, 'v2: note preservate');
}

/* ── migrazione: v3, gruppi senza opzioni ──────────────── */
{
  const m = migrate({ v:3, curBand:'b2', ui:{bar:false}, bands:[
    { id:'b1', name:'COPYCUT', me:'Basso', songs:[{id:'a',title:'A',tr:2,scroll:1.5}],
      sets:[{id:'s1',name:'Uno',items:[{type:'song',id:'a'}]}], cur:'s1' },
    { id:'b2', name:'Trio Jazz', me:'Voce', songs:[], sets:[{id:'s9',name:'Set',items:[]}], cur:'s9' }
  ]});
  eq(m.bands.map(b=>b.name), ['COPYCUT','Trio Jazz'], 'v3: gruppi preservati');
  eq(m.curBand, 'b2', 'v3: gruppo attivo preservato');
  eq([m.bands[0].me, m.bands[1].me], ['Basso','Voce'], 'v3: il ruolo è indipendente fra i gruppi');
  eq(m.bands[0].songs[0].tr, 2, 'v3: trasposizione preservata');
  eq(m.bands[0].songs[0].scroll, 1.5, 'v3: scorrimento preservato');
  eq(m.opt.bar, false, 'v3: il vecchio ui.bar diventa opt.bar');
  eq(m.opt.notation, 'en', 'v3: notazione di default');
  eq(m.opt.accid, 'auto', 'v3: alterazioni automatiche di default');
}

/* ── migrazione: v4, le opzioni ────────────────────────── */
{
  const m = migrate({ v:4, bands:[{id:'b1',name:'X',songs:[],sets:[{id:'s1',items:[]}],cur:'s1'}],
    curBand:'b1', opt:{ theme:'light', notation:'it', accid:'flat', fs:30, bar:false, timer:false, caps:true, roots:true, plain:true, pedal:'page', metroSound:true, sort:'title' } });
  eq(m.opt, { theme:'light', notation:'it', accid:'flat', fs:30, bar:false, timer:false, caps:true, roots:true, plain:true, pedal:'page', metroSound:true, sort:'title' },
     'v4: le opzioni passano intatte');
}
eq(migrate({}).opt, { theme:'dark', notation:'en', accid:'auto', fs:22, bar:true, timer:true, caps:false, roots:false, plain:false, pedal:'song', metroSound:false, sort:'add' },
   'opzioni di default');
/* Come il maiuscolo: è una scelta di chi legge, non un default, e un
   database di prima non deve ritrovarsela accesa. */
eq(migrate({}).opt.roots, false, 'fondamentali spente se non richieste');
eq(migrate({opt:{roots:'si'}}).opt.roots, false, 'fondamentali: solo true vero le accende');
eq(migrate({opt:{roots:true}}).opt.roots, true, 'fondamentali accese se richieste');
/* Il maiuscolo è una scelta di chi legge, non un default: chi aveva già un
   database non deve ritrovarselo acceso, e un valore storto non lo accende. */
eq(migrate({}).opt.caps, false, 'maiuscolo spento se non richiesto');
eq(migrate({opt:{caps:'si'}}).opt.caps, false, 'maiuscolo: solo true vero lo accende');
eq(migrate({opt:{caps:true}}).opt.caps, true, 'maiuscolo acceso se richiesto');
eq(migrate({opt:{notation:'klingon', accid:'boh', sort:'colore'}}).opt.notation, 'en', 'notazione non valida');
eq(migrate({opt:{accid:'boh'}}).opt.accid, 'auto', 'alterazione non valida');
eq(migrate({opt:{sort:'colore'}}).opt.sort, 'add', 'ordinamento non valido');
eq(migrate({opt:{fs:200}}).opt.fs, 48, 'corpo del testo fuori scala');
eq(migrate({opt:{fs:2}}).opt.fs, 14, 'corpo del testo troppo piccolo');

/* ── migrazione: segnaposto ────────────────────────────── */
{
  const m = migrate({ bands:[{ id:'b1', name:'X', songs:[{id:'a'}], cur:'s1',
    sets:[{ id:'s1', name:'S', items:[
      {type:'mark', label:'Pausa', dur:'15:00'},
      {type:'song', id:'a'},
      {type:'song', id:'fantasma'},
      {type:'mark', dur:'2:00'}
    ]}]}]});
  eq(m.bands[0].sets[0].items, [
    {type:'mark', label:'Pausa', dur:'15:00'},
    {type:'song', id:'a'}
  ], 'segnaposto tenuti, brano cancellato e segnaposto senza etichetta scartati');
}

/* ── migrazione: input sporco ──────────────────────────── */
{
  const m = migrate({ bands:[{ id:'b1', songs:[{id:'a',tr:50,capo:99,scroll:9,lead:900},{title:'senza id'}],
                               sets:[{id:'s1',songs:['a','fantasma']}], cur:'inesistente',
                               me:'Direttore d\'orchestra' }], curBand:'sbagliato' });
  eq(m.bands[0].songs.length, 1, 'brani senza id scartati');
  eq(m.bands[0].songs[0].tr, 11, 'trasposizione fuori scala riportata al limite');
  eq(m.bands[0].songs[0].capo, 11, 'capo fuori scala riportato al limite');
  eq(m.bands[0].songs[0].scroll, 4, 'scorrimento fuori scala riportato al limite');
  eq(m.bands[0].songs[0].lead, 60, 'attesa fuori scala riportata al limite');
  eq(m.bands[0].sets[0].items, [{type:'song',id:'a'}], 'riferimenti a brani cancellati rimossi');
  eq(m.bands[0].sets[0].name, 'Scaletta 1', 'scaletta senza nome: nome di ripiego');
  eq(m.bands[0].cur, 's1', 'scaletta attiva inesistente: ricade sulla prima');
  eq(m.bands[0].me, 'Tutti', 'ruolo non valido: ricade su Tutti');
  eq(m.curBand, m.bands[0].id, 'gruppo attivo inesistente: ricade sul primo');
}
eq(migrate({}).bands.length, 1, 'database vuoto: un gruppo comunque');
eq(migrate({}).bands[0].sets[0].items, [], 'database vuoto: scaletta vuota');
eq(migrate(null).bands.length, 1, 'null: non esplode');
eq(migrate({bands:[]}).bands.length, 1, 'lista gruppi vuota: ne crea uno');
eq(migrate({songs:[{id:'a',tr:-99}]}).bands[0].songs[0].tr, -11, 'trasposizione negativa fuori scala');
eq(migrate({songs:[{id:'a',capo:-5}]}).bands[0].songs[0].capo, 0, 'capo negativo: zero');
eq(migrate({songs:[{id:'a',scroll:0.01}]}).bands[0].songs[0].scroll, 0.5, 'scorrimento minimo');
eq(migrate({songs:[{id:'a',scroll:'boh'}]}).bands[0].songs[0].scroll, 1, 'scorrimento non numerico');

/* ── nuove opzioni (tema, pedale, metronomo) ───────────── */
eq(migrate({}).opt.theme, 'dark', 'tema di default scuro');
eq(migrate({opt:{theme:'light'}}).opt.theme, 'light', 'tema chiaro conservato');
eq(migrate({opt:{theme:'neon'}}).opt.theme, 'dark', 'tema sconosciuto ripiega su scuro');
eq(migrate({}).opt.pedal, 'song', 'pedale di default cambio brano');
eq(migrate({opt:{pedal:'page'}}).opt.pedal, 'page', 'pedale mezza pagina conservato');
eq(migrate({}).opt.metroSound, false, 'suono metronomo spento di default');
eq(migrate({opt:{metroSound:true}}).opt.metroSound, true, 'suono metronomo acceso conservato');

/* ── tap tempo ─────────────────────────────────────────── */
eq(bpmFromTaps([]), null, 'tap vuoto');
eq(bpmFromTaps([1000]), null, 'un solo tap non basta');
eq(bpmFromTaps([1000, 1500, 2000]), 120, 'tap regolare a 120 bpm');
eq(bpmFromTaps([1000, 1600, 2200]), 100, 'tap regolare a 100 bpm');
eq(bpmFromTaps([1000, 5000, 5500]), 120, 'pausa lunga azzera la sequenza precedente');
eq(bpmFromTaps([1000, 1050]), 300, 'tap velocissimo limitato a 300 bpm');
eq(bpmFromTaps([1000, 3400]), 30, 'tap lentissimo limitato a 30 bpm');
