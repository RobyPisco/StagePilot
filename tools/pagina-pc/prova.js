// Prova a riga di comando della pulizia: node prova.js file1.txt [file2.txt ...] [-v] [--json]
// Mostra cosa viene tolto, le tonalità, le frasi candidate come titolo e il risultato.
// Con --json stampa invece, per ogni file, i dati che la pagina manda al server per riconoscere il brano.
const fs = require('fs');
const vm = require('vm');
const path = require('path');

vm.runInThisContext(fs.readFileSync(path.join(__dirname, 'convert.bundle.js'), 'utf8') + '\n;globalThis.BL = BL;');
const PC = require('./pulizia.js');

const verbose = process.argv.includes('-v');
const asJson = process.argv.includes('--json');
const out = [];
for (const f of process.argv.slice(2).filter(a => !a.startsWith('-'))) {
  const raw = fs.readFileSync(f, 'utf8');
  const r = PC.toChordPro(raw);
  const key = r.meta.key || PC.inferKey(r.body);
  const guesses = PC.guessTitles(r.body);
  if (asJson) { out.push({ file: path.basename(f), lyrics: PC.lyricLines(r.body), guesses: guesses, key: key }); continue; }
  console.log('\n=== ' + path.basename(f));
  console.log('tolto:', JSON.stringify(r.removed), '| tonalità:', r.meta.key || '(' + key + ' dedotta)', '| capo:', r.meta.capo || 0);
  console.log('titolo, candidati:', guesses.join(' | '));
  console.log('righe tablatura rimaste:', r.body.split('\n').filter(PC.isTabLine).length,
    '| accordi:', (r.body.match(/\[[^\]]+\]/g) || []).length, '| sezioni:', (r.body.match(/^#/mg) || []).length);
  if (verbose) console.log('\n' + r.body);
}
if (asJson) console.log(JSON.stringify(out));
