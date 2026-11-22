import { esc } from '../core/text';
import { mmss } from '../core/time';
import { setDuration, ordinals } from '../core/setlist';
import { sortSongs } from '../core/library';
import { ROLES } from '../core/model';
import { roleLabel } from '../core/sheet';
import { t } from '../i18n';
import { $, $in } from './dom';
import { S, curBand, curSet, setItems, byId, keyLabel, subLine } from './state';
import { paint } from './live';
import { LANGKEY, TOKKEY, GISTKEY, lsGet } from './prefs';

/* ── vista scaletta ──────────────────────────────────────── */
export function drawSet(): void {
  $('#bandnm').textContent = curBand().name;
  const cur = curSet();
  $('#setsel').innerHTML = curBand().sets.map(s =>
    `<option value="${esc(s.id)}"${s.id === cur.id ? ' selected' : ''}>${esc(s.name)} · ${s.items.length}</option>`
  ).join('');
  $<HTMLButtonElement>('#sdel').disabled = curBand().sets.length < 2;

  const items = setItems(), ord = ordinals(items);
  const tot = setDuration(items, curBand().songs);
  const nS = items.filter(i => i.type === 'song').length, nM = items.length - nS;
  $('#setlist').innerHTML = `
    <div class="tot">
      <span>${t('set.count', { n: nS })}${nM ? ' · ' + t('set.marks', { n: nM }) : ''}</span>
      <span>${t('set.duration')} <b>${mmss(tot)}</b></span>
    </div>
    ${items.length ? items.map((it, i) => {
      const grip = `<span class="grip" title="${esc(t('set.drag'))}">&#10247;</span>`;
      const rm = `<button class="mini" data-rm="${i}">&#10005;</button>`;
      if (it.type === 'mark') return `
        <div class="row mk">
          ${grip}<span class="num">&mdash;</span>
          <span class="meta">
            <div class="ttl">${esc(it.label)}</div>
            <div class="sub">${t('mark.word')}${it.dur ? ' &nbsp;·&nbsp; ' + esc(it.dur) : ''}</div>
          </span>${rm}
        </div>`;
      const s = byId(it.id); if (!s) return '';
      return `
        <div class="row">
          ${grip}<span class="num">${String(ord[i]).padStart(2, '0')}</span>
          <button class="meta" data-play="${i}" style="text-align:left">
            <div class="ttl">${esc(s.title)}</div>
            <div class="sub">${subLine(s)}</div>
          </button>
          <span class="badge">${esc(keyLabel(s))}</span>${rm}
        </div>`;
    }).join('')
    : `<div class="empty"><b>${t('set.emptyTitle', { name: esc(cur.name) })}</b>${t('set.emptyText')}</div>`}
  `;
}

/* ── vista repertorio ────────────────────────────────────── */
/* La barra di ricerca sta nel markup e non viene mai ridisegnata:
   così il cursore resta dov'è mentre scrivi. Qui si ridisegnano
   solo i tag e la lista. */
export function drawLib(): void {
  const songs = curBand().songs;
  const tags = [...new Set(songs.flatMap(s => s.tags))].sort();
  if (S.libTag && !tags.includes(S.libTag)) S.libTag = '';
  $('#tagbar').innerHTML = tags.map(tg =>
    `<button class="tag${tg === S.libTag ? ' on' : ''}" data-tag="${esc(tg)}">#${esc(tg)}</button>`
  ).join('');
  $<HTMLSelectElement>('#sortsel').value = S.db.opt.sort;

  const q = S.libQ.toLowerCase();
  const list = sortSongs(songs.filter(s =>
    (s.title + ' ' + (s.artist || '')).toLowerCase().includes(q) &&
    (!S.libTag || s.tags.includes(S.libTag))
  ), S.db.opt.sort);
  const inSet = new Set(setItems().flatMap(i => i.type === 'song' ? [i.id] : []));
  $('#liblist').innerHTML = list.length ? list.map(s => `
      <div class="row">
        <button class="meta" data-ed="${s.id}" style="text-align:left">
          <div class="ttl">${esc(s.title)}</div>
          <div class="sub">${subLine(s)}</div>
        </button>
        <span class="badge">${esc(keyLabel(s))}</span>
        <button class="mini${inSet.has(s.id) ? ' in' : ''}" data-add="${s.id}"
          title="${esc(t(inSet.has(s.id) ? 'lib.removeFrom' : 'lib.addTo', { name: curSet().name }))}">${inSet.has(s.id) ? '&#10003;' : '+'}</button>
      </div>`).join('')
    : `<div class="empty"><b>${t('lib.emptyTitle')}</b>${
        S.libQ || S.libTag ? t('lib.emptyFiltered') : t('lib.emptyText')}</div>`;
}

/* ── gruppi ──────────────────────────────────────────────── */
const roleOptions = (sel: string) =>
  ROLES.map(r => `<option value="${r}"${r === sel ? ' selected' : ''}>${esc(roleLabel(r))}</option>`).join('');

export function drawBands(): void {
  const cur = curBand();
  $('#blist').innerHTML = S.db.bands.map(b => `
    <button class="row" data-band="${esc(b.id)}">
      <span class="meta">
        <div class="ttl">${esc(b.name)}</div>
        <div class="sub">${t('bands.sub', { songs: b.songs.length, sets: b.sets.length })} &nbsp;·&nbsp; ${esc(roleLabel(b.me))}</div>
      </span>
      ${b.id === cur.id ? '<span class="badge">&#10003;</span>' : ''}
    </button>`).join('');
  $('#b-role').innerHTML = roleOptions(cur.me);
  $<HTMLButtonElement>('#bdel').disabled = S.db.bands.length < 2;
}

/* ── opzioni ─────────────────────────────────────────────── */
const tog = (id: string, on: boolean, yes: Parameters<typeof t>[0], no: Parameters<typeof t>[0]) => {
  const el = $(id);
  el.classList.toggle('on', on);
  el.textContent = t(on ? yes : no);
};
export function drawOpts(): void {
  const o = S.db.opt;
  $<HTMLSelectElement>('#o-theme').value = o.theme;
  $<HTMLSelectElement>('#o-lang').value = lsGet(LANGKEY) || 'auto';
  $<HTMLSelectElement>('#o-notation').value = o.notation;
  $<HTMLSelectElement>('#o-accid').value = o.accid;
  $<HTMLSelectElement>('#o-pedal').value = o.pedal;
  $('#o-fsval').textContent = String(o.fs);
  tog('#o-bar', o.bar, 'opts.show', 'opts.hide');
  tog('#o-timer', o.timer, 'opts.show', 'opts.hide');
  tog('#o-metro', o.metroSound, 'opts.metroOn', 'opts.metroOff');
  tog('#o-caps', o.caps, 'opts.capsOn', 'opts.capsOff');
  tog('#o-roots', o.roots, 'opts.rootsOn', 'opts.rootsOff');
  tog('#o-plain', o.plain, 'opts.plainOn', 'opts.plainOff');
  /* Senza accordi, ridurli alla fondamentale non vuol dire niente. */
  $<HTMLButtonElement>('#o-roots').disabled = o.plain;
  $in('#o-tok').value = lsGet(TOKKEY) || '';
  $in('#o-gist').value = lsGet(GISTKEY) || '';
}

/* Scaletta da palco pronta da stampare in A4 e fissare col nastro. */
export function printSetlist(): void {
  const band = curBand(), set = curSet(), items = setItems(), ord = ordinals(items);
  const tot = setDuration(items, band.songs);
  $('#print-sheet').innerHTML = `
    <div class="phead">
      <div class="pband">${esc(band.name)}</div>
      <div class="ptitle">${esc(set.name)}</div>
      <div class="pmeta">${t('set.count', { n: items.filter(i => i.type === 'song').length })} · ${mmss(tot)}</div>
    </div>
    <div class="plist">
      ${items.map((it, i) => {
        if (it.type === 'mark') return `
          <div class="prow pmk">
            <span class="pnum">—</span>
            <span class="pname">${esc(it.label)}</span>
            <span class="pkey">${esc(it.dur || '')}</span>
          </div>`;
        const s = byId(it.id); if (!s) return '';
        return `
          <div class="prow">
            <span class="pnum">${String(ord[i]).padStart(2, '0')}</span>
            <span class="pname">${esc(s.title)}</span>
            <span class="pkey">${esc(keyLabel(s))}${s.bpm ? ` · ${s.bpm}` : ''}</span>
          </div>`;
      }).join('')}
    </div>`;
  window.print();
}

/* Ridisegna tutto quello che dipende dalle opzioni. */
export function redrawAll(): void {
  drawSet(); drawLib();
  if (S.view === 'opts') drawOpts();
  if ($('#live').classList.contains('on')) paint();
}

export function showView(v: 'set' | 'lib' | 'opts'): void {
  S.view = v;
  document.querySelectorAll('.tab').forEach(x =>
    x.setAttribute('aria-selected', String((x as HTMLElement).dataset.v === v)));
  $('#v-set').classList.toggle('on', v === 'set');
  $('#v-lib').classList.toggle('on', v === 'lib');
  $('#v-opts').classList.toggle('on', v === 'opts');
  if (v === 'set') drawSet();
  else if (v === 'lib') drawLib();
  else drawOpts();
}
