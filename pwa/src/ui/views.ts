import { esc } from '../core/text';
import { mmss } from '../core/time';
import { setDuration, ordinals } from '../core/setlist';
import { sortSongs } from '../core/library';
import { ROLES } from '../core/model';
import { roleLabel } from '../core/sheet';
import { t } from '../i18n';
import { $, $in } from './dom';
import { S, curBand, curSet, setItems, byId, keyLabel, subLine, type ViewName } from './state';
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

/* ── vista oggi ──────────────────────────────────────────── */
export function drawHome(): void {
  const b = curBand(), s = curSet(), items = setItems();
  const nS = items.filter(i => i.type === 'song').length;
  const tot = setDuration(items, b.songs);
  const openTasks = b.practiceTasks.filter(p => !p.done).length;
  const nextGig = [...b.events].sort((a, x) => (a.date + a.time).localeCompare(x.date + x.time))[0];
  $('#bandnm').textContent = b.name;
  $('#homebody').innerHTML = `
    <div class="hhero">
      <div class="htoprow">
        <button class="btn act" data-hact="guide">${t('home.guide')}</button>
        <button class="btn" data-hact="new">${t('lib.new')}</button>
      </div>
      <div class="hlead">${t('home.title')}</div>
      <div class="hsub">${t('home.sub')}</div>
      <div class="hcard">
        <div class="hlbl">${nextGig ? esc([nextGig.date, nextGig.time, nextGig.location].filter(Boolean).join(' · ')) : t('home.nextGig')}</div>
        <div class="httl">${esc(nextGig ? nextGig.title : s.name)}</div>
        <div class="hmeta">${t('set.count', { n: nS })} · ${mmss(tot)}</div>
        <button class="btn key hgo" data-hact="go"${items.length ? '' : ' disabled'}>${t('home.goStage')}</button>
      </div>
      <div class="hstats">
        <div class="hstat"><b>${b.songs.length}</b><span>${t('home.statSongs')}</span></div>
        <div class="hstat"><b>${b.sets.length}</b><span>${t('home.statSets')}</span></div>
        <div class="hstat"><b>${openTasks}</b><span>${t('home.statTasks')}</span></div>
      </div>
      <button class="hprac" data-hact="prac">
        <div class="meta">
          <div class="ttl">${openTasks ? t('home.openTasks', { n: openTasks }) : t('home.prepAgenda')}</div>
          <div class="sub">${t('home.prepSub')}</div>
        </div>
        <span class="badge">&rsaquo;</span>
      </button>
    </div>`;
}

/* ── vista prove ─────────────────────────────────────────── */
export function drawPrac(): void {
  const b = curBand();
  const list = b.practiceTasks;
  $('#praclist').innerHTML = list.length ? list.map(p => {
    const song = byId(p.songId);
    return `
      <div class="row${p.done ? ' mk' : ''}">
        <button class="mini${p.done ? ' in' : ''}" data-ptog="${esc(p.id)}">${p.done ? '&#10003;' : '&#9675;'}</button>
        <button class="meta" data-pgo="${esc(p.songId)}" style="text-align:left">
          <div class="ttl">${esc(p.goal)}</div>
          <div class="sub">${esc(song ? song.title : '—')} &nbsp;·&nbsp; ${esc(p.section)} &nbsp;·&nbsp; ${esc(roleLabel(p.role))}</div>
        </button>
        <button class="mini" data-pdel="${esc(p.id)}">&#10005;</button>
      </div>`;
  }).join('')
  : `<div class="empty"><b>${t('prac.emptyTitle')}</b>${t('prac.emptyText')}</div>`;
}

/* ── vista concerti ──────────────────────────────────────── */
export function drawGigs(): void {
  const b = curBand();
  const list = [...b.events].sort((a, x) => (a.date + a.time).localeCompare(x.date + x.time));
  $('#giglist').innerHTML = list.length ? list.map(g => {
    const set = b.sets.find(s => s.id === g.setId) || b.sets[0];
    return `
      <div class="row">
        <div class="meta">
          <div class="ttl">${esc(g.title)}</div>
          <div class="sub">${esc([g.date, g.time, g.location, set?.name].filter(Boolean).join(' · ') || '—')}</div>
        </div>
        <button class="btn key" data-gplay="${esc(g.setId || set?.id || '')}">${t('gigs.openStage')}</button>
        <button class="mini" data-gdel="${esc(g.id)}">&#10005;</button>
      </div>`;
  }).join('')
  : `<div class="empty"><b>${t('gigs.emptyTitle')}</b>${t('gigs.emptyText')}</div>`;
}

/* Ridisegna tutto quello che dipende dalle opzioni. */
export function redrawAll(): void {
  drawHome(); drawSet(); drawLib(); drawPrac(); drawGigs();
  if (S.view === 'opts') drawOpts();
  if ($('#live').classList.contains('on')) paint();
}

export function showView(v: ViewName): void {
  S.view = v;
  document.querySelectorAll('.tab').forEach(x =>
    x.setAttribute('aria-selected', String((x as HTMLElement).dataset.v === v)));
  (['home', 'lib', 'set', 'prac', 'gigs', 'opts'] as const).forEach(k =>
    $(`#v-${k}`).classList.toggle('on', v === k));
  if (v === 'home') drawHome();
  else if (v === 'set') drawSet();
  else if (v === 'lib') drawLib();
  else if (v === 'prac') drawPrac();
  else if (v === 'gigs') drawGigs();
  else drawOpts();
}
