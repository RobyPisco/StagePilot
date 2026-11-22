import { describe, test, expect, beforeEach } from 'vitest';
import { IDBFactory } from 'fake-indexeddb';
import { createStore, KEY } from '../src/storage/store';

/* Un localStorage finto: una Map con la stessa interfaccia. */
const fakeLs = (init: Record<string, string> = {}) => {
  const m = new Map(Object.entries(init));
  return {
    getItem: (k: string) => m.get(k) ?? null,
    setItem: (k: string, v: string) => { m.set(k, v); },
    removeItem: (k: string) => { m.delete(k); },
    map: m
  };
};
const DATA = { v: 4, bands: [{ id: 'b1', name: 'X' }] };

describe('storage su IndexedDB', () => {
  let idb: IDBFactory;
  beforeEach(() => { idb = new IDBFactory(); });

  test('primo avvio: niente da nessuna parte', async () => {
    expect(await createStore(idb, fakeLs()).load()).toBe(null);
  });

  test('salva e rilegge da IndexedDB', async () => {
    const ls = fakeLs();
    const s = createStore(idb, ls);
    expect(await s.save(DATA)).toBe(true);
    expect(await createStore(idb, ls).load()).toEqual(DATA);
    expect(ls.map.size).toBe(0);           // non è finito in localStorage
  });

  /* Il passaggio dal localStorage di prima deve essere invisibile:
     chi aggiorna l'app ritrova tutto. */
  test('IndexedDB vuoto: si leggono i dati del localStorage di prima', async () => {
    const ls = fakeLs({ [KEY]: JSON.stringify(DATA) });
    expect(await createStore(idb, ls).load()).toEqual(DATA);
  });
  test('anche dalle chiavi dei nomi vecchi, Leggio e Scaletta', async () => {
    expect(await createStore(idb, fakeLs({ 'leggio:db': '{"v":3}' })).load()).toEqual({ v: 3 });
    expect(await createStore(new IDBFactory(), fakeLs({ 'scaletta:db': '{"v":1}' })).load()).toEqual({ v: 1 });
  });
  test('la chiave nuova vince sulle vecchie', async () => {
    const ls = fakeLs({ [KEY]: '{"v":4}', 'leggio:db': '{"v":3}' });
    expect(await createStore(idb, ls).load()).toEqual({ v: 4 });
  });
  test('una chiave rotta non blocca le altre', async () => {
    const ls = fakeLs({ [KEY]: '{rotto', 'leggio:db': '{"v":3}' });
    expect(await createStore(idb, ls).load()).toEqual({ v: 3 });
  });
  test('dopo il primo salvataggio vince IndexedDB, e la copia vecchia resta', async () => {
    const ls = fakeLs({ [KEY]: '{"v":"vecchio"}' });
    const s = createStore(idb, ls);
    await s.save(DATA);
    expect(await s.load()).toEqual(DATA);
    expect(ls.map.get(KEY)).toBe('{"v":"vecchio"}');
  });

  test('cancella tutto: IndexedDB e chiavi vecchie', async () => {
    const ls = fakeLs({ [KEY]: '{}', 'leggio:db': '{}', 'scaletta:db': '{}', 'backline:lang': 'en' });
    const s = createStore(idb, ls);
    await s.save(DATA);
    await s.wipe();
    expect(await s.load()).toBe(null);
    /* le preferenze del device non sono dati: restano */
    expect([...ls.map.keys()]).toEqual(['backline:lang']);
  });

  /* Navigazione privata o IndexedDB negato: meglio localStorage che niente. */
  test('senza IndexedDB si ripiega su localStorage', async () => {
    const ls = fakeLs();
    const s = createStore(undefined, ls);
    expect(await s.save(DATA)).toBe(true);
    expect(JSON.parse(ls.map.get(KEY)!)).toEqual(DATA);
    expect(await s.load()).toEqual(DATA);
  });
  test('senza nessuno dei due il salvataggio lo dice', async () => {
    const rotto = { getItem: () => null, setItem: () => { throw new Error('quota'); }, removeItem: () => {} };
    expect(await createStore(undefined, rotto).save(DATA)).toBe(false);
  });

  test('si salva una copia: modificare dopo non tocca il salvato', async () => {
    const s = createStore(idb, fakeLs());
    const d = structuredClone(DATA);
    await s.save(d);
    d.bands[0].name = 'cambiato';
    expect(await s.load()).toEqual(DATA);
  });
});
