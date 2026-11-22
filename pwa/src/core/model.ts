import type { Accid } from './chords';
import { t } from '../i18n';

/* Ruoli fissi. "Tutti" è la nota di arrangiamento che vede la band,
   gli altri sono le note che ognuno scrive per sé. I nomi sono anche le
   chiavi delle note salvate sul brano, quindi restano in italiano in
   qualunque lingua: a schermo si traducono con `role.<nome>`. */
export const ROLES = ['Tutti', 'Voce', 'Chitarra', 'Basso', 'Tastiere', 'Batteria', 'Fiati'] as const;
export type Role = typeof ROLES[number];

export interface Song {
  id: string;
  title: string;
  artist?: string;
  key?: string;
  bpm: number | '';
  dur?: string;
  tags: string[];
  notes: Partial<Record<string, string>>;
  tr: number;
  capo: number;
  scroll: number;
  lead: number;
  body?: string;
}
export type SetItem =
  | { type: 'song'; id: string }
  | { type: 'mark'; label: string; dur: string };
export interface SetList { id: string; name: string; items: SetItem[] }
export interface Band {
  id: string;
  name: string;
  me: Role;
  songs: Song[];
  sets: SetList[];
  cur: string;
}
export const SORTS = ['add', 'title', 'artist', 'key', 'bpm'] as const;
export type Sort = typeof SORTS[number];
export type Theme = 'dark' | 'light';
export type PedalMode = 'song' | 'page';
export interface Options {
  theme: Theme;
  notation: 'en' | 'it';
  accid: Accid;
  fs: number;
  bar: boolean;
  timer: boolean;
  caps: boolean;
  roots: boolean;
  plain: boolean;
  pedal: PedalMode;
  metroSound: boolean;
  sort: Sort;
}
export interface DB { v: 4; bands: Band[]; curBand: string; opt: Options }

/* ── normalizzazione del database ────────────────────────── */
export const parseTags = (s: unknown): string[] =>
  String(s || '').split(',').map(t => t.trim().toLowerCase()).filter(Boolean)
    .filter((t, i, a) => a.indexOf(t) === i);
export const parseBpm = (v: unknown): number | '' => {
  const n = parseInt(String(v), 10);
  return Number.isFinite(n) ? n : '';
};
export const clamp = (v: unknown, lo: number, hi: number, dflt: number): number => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.min(hi, Math.max(lo, n)) : dflt;
};
const isRole = (r: unknown): r is Role => (ROLES as readonly unknown[]).includes(r);

/* Quello che arriva da fuori — localStorage, IndexedDB, un file — non ha
   una forma garantita: si legge come `any` e si esce di qui tipizzati. */

/* Un gruppo: repertorio, scalette e ruolo di chi lo suona. */
export function migrateBand(raw: any, i?: number): Band {
  raw = raw || {};
  const songs: Song[] = (Array.isArray(raw.songs) ? raw.songs : []).filter((s: any) => s && s.id).map((s: any) => ({
    ...s,
    bpm: parseBpm(s.bpm),
    tags: Array.isArray(s.tags) ? parseTags(s.tags.join(',')) : parseTags(s.tags),
    notes: (s.notes && typeof s.notes === 'object' && !Array.isArray(s.notes)) ? s.notes : {},
    tr: Math.round(clamp(s.tr, -11, 11, 0)),
    capo: Math.round(clamp(s.capo, 0, 11, 0)),
    scroll: clamp(s.scroll, 0.5, 4, 1),
    lead: Math.round(clamp(s.lead, 0, 60, 0))
  }));
  const known = (id: unknown) => songs.some(s => s.id === id);
  /* Le voci di scaletta erano id di brani, adesso sono oggetti: serviva
     per infilarci i segnaposto, che brani non sono. */
  const toItems = (set: any): SetItem[] => {
    if (Array.isArray(set.items)) return set.items.filter((it: any) => it && (
      it.type === 'mark' ? !!it.label : known(it.id)
    )).map((it: any): SetItem => it.type === 'mark'
      ? { type: 'mark', label: String(it.label), dur: String(it.dur || '') }
      : { type: 'song', id: it.id });
    return (Array.isArray(set.songs) ? set.songs : []).filter(known).map((id: string) => ({ type: 'song', id }));
  };
  let sets: SetList[] = [];
  if (Array.isArray(raw.sets) && raw.sets.length) {
    sets = raw.sets.filter((s: any) => s && s.id).map((s: any, j: number) => ({
      id: s.id, name: s.name || t('set.defaultN', { n: j + 1 }), items: toItems(s)
    }));
  }
  if (!sets.length) {
    sets = [{ id: 'set1', name: t('set.default'),
              items: (Array.isArray(raw.set) ? raw.set : []).filter(known).map((id: string) => ({ type: 'song', id })) }];
  }
  return {
    id: raw.id || 'b' + Date.now() + (i || 0),
    name: raw.name || raw.band || t('band.defaultN', { n: (i || 0) + 1 }),
    me: isRole(raw.me) ? raw.me : 'Tutti',
    songs,
    sets,
    cur: sets.some(s => s.id === raw.cur) ? raw.cur : sets[0].id
  };
}

/* Regge quattro formati: v4 (con opzioni), v3 (gruppi), v2 (un gruppo
   solo, `sets` in cima) e v1 (`set`, una scaletta sola). Passa di qui
   tutto quello che arriva da fuori: storage e import JSON. Aggiungendo un
   campo al brano va aggiunto qui. */
export function migrate(raw: any): DB {
  raw = raw || {};
  const bands = Array.isArray(raw.bands) && raw.bands.length
    ? raw.bands.filter(Boolean).map(migrateBand)
    : [migrateBand(raw, 0)];
  const o = raw.opt || {};
  const ui = raw.ui || {};
  return {
    v: 4,
    bands,
    curBand: bands.some((b: Band) => b.id === raw.curBand) ? raw.curBand : bands[0].id,
    opt: {
      theme: o.theme === 'light' ? 'light' : 'dark',
      notation: o.notation === 'it' ? 'it' : 'en',
      accid: ['auto', 'sharp', 'flat'].includes(o.accid) ? o.accid : 'auto',
      fs: Math.round(clamp(o.fs, 14, 48, 22)),
      bar: (o.bar === false || ui.bar === false) ? false : true,
      timer: o.timer === false ? false : true,
      /* maiuscolo spento di suo: chi lo vuole lo accende, chi ha un
         database di prima non se lo ritrova acceso senza averlo chiesto */
      caps: o.caps === true,
      /* solo fondamentali: scelta di chi legge, spenta se non richiesta */
      roots: o.roots === true,
      /* senza accordi: come sopra, è il foglio di chi canta */
      plain: o.plain === true,
      pedal: o.pedal === 'page' ? 'page' : 'song',
      metroSound: o.metroSound === true,
      sort: (SORTS as readonly unknown[]).includes(o.sort) ? o.sort : 'add'
    }
  };
}
