/* Controllo statico di markup, CSS e codice dell'interfaccia — era check.js.
   Non sostituisce una prova nel browser: verifica solo che i pezzi siano
   coerenti fra loro. Gli errori che scappano sono sempre gli stessi: un id
   rinominato nel markup ma non nel gestore, una classe usata dal codice
   che nel CSS non esiste più. */
import { test, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const root = join(__dirname, '..');
const html = readFileSync(join(root, 'index.html'), 'utf8');
const css = readFileSync(join(root, 'src/styles.css'), 'utf8');
const dir = (d: string) => readdirSync(join(root, d)).filter(f => f.endsWith('.ts'))
  .map(f => readFileSync(join(root, d, f), 'utf8')).join('\n');
const ui = dir('src/ui');
const all = ui + '\n' + dir('src/core');

const ids = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]));

test("ogni $('#x') punta a un id del markup", () => {
  const used = [...ui.matchAll(/\$(?:in|<[^>]+>)?\('#([\w-]+)'\)/g)].map(m => m[1]);
  expect(used.length).toBeGreaterThan(50);
  expect([...new Set(used)].filter(i => !ids.has(i))).toEqual([]);
});

/* questo, ai tempi di check.js, ha beccato un pulsante rimasto orfano
   dopo che era migrato nella barra live */
test('ogni gestore b.id === x punta a un id reale', () => {
  const handled = [...ui.matchAll(/\bb\.id\s*===\s*'([\w-]+)'/g)].map(m => m[1]);
  expect(handled.length).toBeGreaterThan(40);
  expect([...new Set(handled)].filter(i => !ids.has(i))).toEqual([]);
});

/* add/remove: tutti gli argomenti sono classi. toggle: solo il primo, il
   secondo è la condizione e le sue stringhe non c'entrano niente. */
test('le classi manipolate da classList esistono nel CSS', () => {
  const cls = new Set<string>();
  for (const m of all.matchAll(/classList\.(add|remove)\(([^)]*)\)/g))
    for (const s of m[2].matchAll(/'([\w-]+)'/g)) cls.add(s[1]);
  for (const m of all.matchAll(/classList\.toggle\(\s*'([\w-]+)'/g)) cls.add(m[1]);
  expect([...cls].filter(c => !css.includes('.' + c))).toEqual([]);
});

test('le classi scritte nei template esistono nel CSS', () => {
  const tpl = new Set<string>();
  for (const m of all.matchAll(/class="([\w -]+)"/g))
    for (const c of m[1].split(/\s+/)) if (c) tpl.add(c);
  for (const m of html.matchAll(/class="([\w -]+)"/g))
    for (const c of m[1].split(/\s+/)) if (c) tpl.add(c);
  expect([...tpl].filter(c => !css.includes('.' + c))).toEqual([]);
});
