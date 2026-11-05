// Prove della pulizia (senza rete): node test-pulizia.js
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('assert');

vm.runInThisContext(fs.readFileSync(path.join(__dirname, 'convert.bundle.js'), 'utf8') + '\n;globalThis.BL = BL;');
const PC = require('./pulizia.js');

const sample = [
  'Tuning: E A D G B EKey: ACapo: No capo',
  '',
  '[Intro]',
  '',
  'e|-----------------|-----------------|',
  'G|-6---6-6---6-6-6-|-9---9-9---9-9-9-| x2',
  'E|-----------------|-----------------|',
  '',
  '[Verse 1]',
  'A',
  '  You took my hand,  You showed me how',
  '                              Bm      F#m          E',
  '  You promised me you\'d be around, Uh huh,  that\'s right',
  '',
  '[Chorus]',
  '   D            E',
  'Who knew what we know',
  '   D            E',
  'Who knew what we know',
  '',
  '[Outro]',
  '',
  'Last update: Feb 23, 2026',
  'X',
].join('\n');

let n = 0;
function t(name, fn) { fn(); n++; console.log('ok  ' + name); }

t('toglie tablatura, testata e residui', () => {
  const r = PC.toChordPro(sample);
  assert.strictEqual(r.removed.tab, 3);
  assert.strictEqual(r.removed.header, 1);
  assert.strictEqual(r.removed.junk, 2);
  assert.ok(!r.body.includes('e|---'));
  assert.ok(!r.body.includes('Tuning'));
  assert.ok(!/Last update/.test(r.body));
});

t('la sezione rimasta vuota sparisce, le altre restano', () => {
  const r = PC.toChordPro(sample);
  assert.ok(!/# Intro/.test(r.body), 'Intro era solo tablatura');
  assert.ok(!/# Outro/.test(r.body), 'Outro è rimasta senza niente');
  assert.ok(/# Verse 1/.test(r.body) && /# Chorus/.test(r.body));
});

t('con la tablatura tenuta, resta e non si perde la sezione', () => {
  const r = PC.toChordPro(sample, { dropTab: false });
  assert.ok(/# Intro/.test(r.body));
  assert.strictEqual(r.body.split('\n').filter(PC.isTabLine).length, 3);
});

t('gli accordi vanno nelle parole', () => {
  const r = PC.toChordPro(sample);
  assert.ok(r.body.includes('[A]  You took my hand'));
  assert.ok(/\[Bm\]/.test(r.body) && /\[F#m\]/.test(r.body) && /\[E\]/.test(r.body));
});

t('tonalità e capotasto dalla testata', () => {
  const r = PC.cleanRaw(sample);
  assert.strictEqual(r.meta.key, 'A');
  assert.strictEqual(r.meta.capo, undefined);
  assert.strictEqual(PC.cleanRaw('Tuning: E A D G B E\nKey: Am\nCapo: 3\n[Am]x').meta.capo, '3');
});

t('righe di tablatura riconosciute, accordi e parole no', () => {
  assert.ok(PC.isTabLine('G|-6---6-6---6-6-6-|-9---9-9---9-9-9-| x2'));
  assert.ok(PC.isTabLine('|---0---2---3---|'));
  assert.ok(!PC.isTabLine('Am F C G'));
  assert.ok(!PC.isTabLine('A | F | C'));
  assert.ok(!PC.isTabLine('[Am]Ciao | bella'));
});

t('la frase ripetuta è fra i candidati del titolo', () => {
  const r = PC.toChordPro(sample);
  const g = PC.guessTitles(r.body);
  assert.ok(g.includes('who knew what we know') || g.some(x => x.indexOf('who knew') >= 0), g.join(' | '));
});

t('tonalità dedotta dagli accordi quando manca la testata', () => {
  assert.strictEqual(PC.inferKey('[A]x [D]x [E]x [A]x'), 'A');
  assert.strictEqual(PC.inferKey('[Am]x [F]x [C]x [G]x [Am]x'), 'Am');
  assert.strictEqual(PC.inferKey('[F]x [Bb]x [C]x [F]x'), 'F');
  assert.strictEqual(PC.inferKey('[A]'), '');
});

t('i crediti prima della prima sezione si tolgono, quelli dentro il testo no', () => {
  const r = PC.toChordPro('Tuning: E A D G B ECapo: No capo\n\nWords & Music by Debbie Harry, 1978.\n\n[Verse 1]\nD\nOne way or another\n\n[Chorus]\nG\nWords by the river');
  assert.ok(r.body.indexOf('Words & Music') < 0, r.body);
  assert.ok(r.body.indexOf('Words by the river') >= 0, r.body);
  assert.strictEqual(r.removed.header, 1);
  assert.strictEqual(r.removed.junk, 1);
  // senza sezioni non si tocca niente: la prima riga potrebbe essere cantata
  assert.ok(PC.toChordPro('Written by hand\nAm\nCiao ciao').body.indexOf('Written by hand') >= 0);
});

t('il % nelle righe a battute diventa la battuta precedente', () => {
  assert.strictEqual(PC.expandRepeats('| D | % | % | D    D C# C | B5 | % | % | B   B C C# |'),
    '| D | D | D | D    D C# C | B5 | B5 | B5 | B   B C C# |');
  assert.strictEqual(PC.expandRepeats('| D    D C# C | % | G |'), '| D    D C# C | D    D C# C | G |');
  assert.strictEqual(PC.expandRepeats('| % | D |'), '| % | D |');             // niente prima: resta
  assert.strictEqual(PC.expandRepeats('100% sicuro | davvero'), '100% sicuro | davvero');
  const b = PC.toChordPro('[Intro]\n| D | % | % | G |\n\n[Verse]\nD\nCiao').body;
  assert.ok(b.indexOf('| [D] | [D] | [D] | [G] |') >= 0, b);
  assert.ok(b.indexOf('%') < 0, b);
});

t('accordi insoliti per la tonalità', () => {
  assert.deepStrictEqual(PC.suspectChords('[C]x [F]x [G]x [Bb]x [Am]x', 'C'), []);          // Bb = bVII, si perdona
  assert.deepStrictEqual(PC.suspectChords('[C]x [F#]x [G]x [Dbm7]x', 'C'), ['F#', 'Dbm7']);
  assert.deepStrictEqual(PC.suspectChords('[Am]x [E7]x [G]x [Dm]x', 'Am'), []);              // V maggiore nel minore
  assert.deepStrictEqual(PC.suspectChords('[Am]x [C#/G#]x', 'Am'), ['C#/G#']);
  assert.deepStrictEqual(PC.suspectChords('[A]x [Eb]x', ''), []);                             // senza tonalità non si giudica
  assert.deepStrictEqual(PC.suspectChords('[Am]x [Eb]x', 'Am Capo 2'), ['Eb']);
});

t('un testo vuoto non rompe niente', () => {
  const r = PC.toChordPro('');
  assert.strictEqual(r.body, '');
  assert.deepStrictEqual(PC.guessTitles(''), []);
});

console.log('\n' + n + ' prove passate');
