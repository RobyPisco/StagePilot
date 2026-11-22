import { describe, test, expect, afterEach } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { it as IT } from '../src/i18n/it';
import { en as EN } from '../src/i18n/en';
import { t, setLang, pickLang } from '../src/i18n';
import { migrate } from '../src/core/model';
import { setAsText } from '../src/core/setlist';

const root = join(__dirname, '..');
const html = readFileSync(join(root, 'index.html'), 'utf8');
const walk = (d: string): string[] => readdirSync(d).flatMap(f => {
  const p = join(d, f);
  return statSync(p).isDirectory() ? walk(p) : p.endsWith('.ts') ? [p] : [];
});
const src = walk(join(root, 'src')).map(f => readFileSync(f, 'utf8')).join('\n');
const params = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort();

afterEach(() => setLang('it'));

describe('dizionari', () => {
  test('inglese e italiano hanno le stesse chiavi', () => {
    expect(Object.keys(EN).sort()).toEqual(Object.keys(IT).sort());
  });
  test('nessun testo vuoto', () => {
    for (const [k, v] of [...Object.entries(IT), ...Object.entries(EN)]) expect(v, k).not.toBe('');
  });
  /* Un {n} dimenticato in una traduzione lascerebbe "songs" senza numero. */
  test('stessi segnaposto {x} nelle due lingue', () => {
    for (const k of Object.keys(IT) as (keyof typeof IT)[]) expect(params(EN[k]), k).toEqual(params(IT[k]));
  });
  test('ogni data-i18n del markup ha la sua chiave', () => {
    const keys = [...html.matchAll(/data-i18n(?:-html|-title|-aria|-placeholder)?="([^"]+)"/g)].map(m => m[1]);
    expect(keys.length).toBeGreaterThan(50);
    expect(keys.filter(k => !(k in IT))).toEqual([]);
  });
  test("ogni t('chiave') del codice ha la sua chiave", () => {
    const keys = [...src.matchAll(/\bt\(\s*'([\w.]+)'/g)].map(m => m[1]);
    expect(keys.filter(k => !(k in IT))).toEqual([]);
  });
});

describe('lingua', () => {
  test('scelta esplicita', () => {
    expect(pickLang('en', 'it-IT')).toBe('en');
    expect(pickLang('it', 'en-US')).toBe('it');
  });
  test('automatica: italiano solo per chi ha il sistema in italiano', () => {
    expect(pickLang(null, 'it-IT')).toBe('it');
    expect(pickLang(null, 'it')).toBe('it');
    expect(pickLang('auto', 'en-GB')).toBe('en');
    expect(pickLang(null, 'de-DE')).toBe('en');
    expect(pickLang(null, undefined)).toBe('en');
  });
  test('parametri sostituiti', () => {
    expect(t('set.count', { n: 3 })).toBe('3 brani');
    setLang('en');
    expect(t('set.count', { n: 3 })).toBe('3 songs');
  });
  /* Le graffe del ChordPro negli aiuti non sono parametri. */
  test('senza parametri le graffe restano', () => {
    expect(t('ug.hint')).toContain('{soc}');
  });
  test('la scaletta in chiaro segue la lingua', () => {
    setLang('en');
    const txt = setAsText([{ type: 'mark', label: 'Break', dur: '15:00' }], [], {});
    expect(txt.split('\n')[0]).toBe('0 songs · 1 placeholders · 15:00');
  });
  test('i nomi di ripiego seguono la lingua', () => {
    setLang('en');
    const m = migrate({ bands: [{ id: 'b1', sets: [{ id: 's1' }] }] });
    expect(m.bands[0].name).toBe('Band 1');
    expect(m.bands[0].sets[0].name).toBe('Setlist 1');
  });
  /* I ruoli sono chiavi dei dati: tradurli nel database spezzerebbe le
     note già scritte. Si traducono solo a schermo. */
  test('il ruolo salvato resta la chiave italiana', () => {
    setLang('en');
    expect(migrate({ me: 'Basso' }).bands[0].me).toBe('Basso');
  });
});
