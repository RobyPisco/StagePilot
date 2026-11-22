import { esc } from '../core/text';
import { secs, mmss, signed } from '../core/time';
import { spellLine, flatFor, roman } from '../core/chords';
import { renderSheet, renderNotes } from '../core/sheet';
import { speedFor, setDuration, ordinals } from '../core/setlist';
import { sortSongs } from '../core/library';
import { t } from '../i18n';
import { $, $in } from './dom';
import { S, live, entries, liveEntry, setItems, curBand, isIt, keyLabel, readTr, readKey, subLine } from './state';
import { drawUpd } from './update';
import { stopMetro } from './metro';
import { isNative, nativeKeepAwake } from './native';

export const liveOn = (): boolean => $('#live').classList.contains('on');

export async function openLive(i: number): Promise<void> {
  live.i = i; live.offSong = null; live.play = false; live.lock = false; live.t0 = Date.now();
  live.fs = S.db.opt.fs;
  $('#live').classList.add('on');
  paint();
  clearInterval(live.tmr); live.tmr = setInterval(drawTimer, 1000);
  drawUpd();                    // se c'era un avviso, sparisce: si sta suonando
  await takeWake();
}

/* Il wake lock è la cosa più critica di un'app da palco ed era l'unica che
   falliva in silenzio: `catch` vuoto, nessun segno, e lo scopri quando lo
   schermo si spegne in mezzo a un pezzo. Fallisce per motivi normalissimi —
   un browser che non lo supporta, la batteria in risparmio energetico, il
   sistema che se lo riprende — e in tutti quei casi in testata compare un
   avviso, che si può toccare per riprovare. */
let nativeWake = false;

export async function takeWake(): Promise<boolean> {
  if (isNative()) {
    nativeWake = await nativeKeepAwake(true);
    drawWake();
    return nativeWake;
  }
  try {
    live.wake = await navigator.wakeLock.request('screen');
    /* il sistema può revocarlo per conto suo: quando succede l'avviso torna */
    live.wake.addEventListener('release', () => { live.wake = null; drawWake(); });
  } catch { live.wake = null; }
  drawWake();
  return !!live.wake;
}
const drawWake = () => $('#lwake').classList.toggle('off', nativeWake || !!live.wake);

export function closeLive(): void {
  setPlay(false);
  stopMetro();
  closeJump();
  clearInterval(live.tmr); live.tmr = 0;
  live.lock = false;
  live.offSong = null;
  $('#live').classList.remove('on', 'lock');
  if (nativeWake) { nativeWake = false; void nativeKeepAwake(false); }
  try { live.wake?.release(); live.wake = null; } catch { /* già rilasciato */ }
  drawUpd();                    // l'avviso arrivato mentre suonavi si vede adesso
}

export function openJump(): void {
  $in('#lpick-q').value = '';
  drawJump('');
  $('#lpick').classList.add('on');
  $in('#lpick-q').focus();
}

export function closeJump(): void {
  $('#lpick').classList.remove('on');
}

export function drawJump(query: string): void {
  const q = query.trim().toLowerCase();
  const songs = sortSongs(curBand().songs.filter(s =>
    (s.title + ' ' + (s.artist || '')).toLowerCase().includes(q)
  ), 'title');
  $('#lpick-list').innerHTML = songs.length ? songs.map(s => `
    <button class="row" data-jump="${esc(s.id)}">
      <span class="meta">
        <div class="ttl">${esc(s.title)}</div>
        <div class="sub">${subLine(s)}</div>
      </span>
      <span class="badge">${esc(keyLabel(s))}</span>
    </button>`).join('')
    : `<div class="empty"><b>${t('lib.emptyTitle')}</b>${t('lib.emptyFiltered')}</div>`;
}

export function jumpToSong(id: string): void {
  live.offSong = id;
  closeJump();
  paint();
}

export function scrollHalfPage(dir: 1 | -1): void {
  const sh = $('#sheet');
  const step = Math.max(120, Math.round(sh.clientHeight * 0.55));
  sh.scrollBy({ top: dir * step, behavior: 'smooth' });
}

export function paint(): void {
  const list = entries();
  const e = liveEntry(); if (!e) return closeLive();
  const nx = list[live.i + 1];
  const ord = ordinals(setItems());
  const sheet = $('#sheet');
  const o = S.db.opt;

  setPlay(false);
  stopMetro();
  $('#lnum').textContent = live.offSong ? '★' : e.type === 'mark' ? '—' : String(ord[live.i]).padStart(2, '0');
  $('#ltitle').textContent = e.type === 'mark' ? e.label : e.song.title;
  $('#lkey').classList.toggle('off', e.type === 'mark');
  $('#lbpm').classList.toggle('off', e.type === 'mark');
  $('#ltime').classList.toggle('off', !o.timer);
  sheet.classList.toggle('mk', e.type === 'mark');
  sheet.classList.toggle('struct', live.struct);
  sheet.classList.toggle('caps', !!o.caps);
  sheet.style.setProperty('--fs', String(live.fs));

  const spd = $<HTMLInputElement>('#spd');
  if (e.type === 'mark') {
    sheet.innerHTML = `<div class="mkl">${esc(e.label)}</div>`
      + (e.dur ? `<div class="mkd">${esc(e.dur)}</div>` : '');
    $('#lcapo').classList.add('off');
    $('#trval').textContent = '0'; $('#capoval').textContent = '0';
    $('#leadval').textContent = '0s';
    spd.value = '100'; $('#spdval').textContent = '100%';
  } else {
    const s = e.song, tr = readTr(s), rk = readKey(s);
    const flat = flatFor(rk, o.accid), it = isIt();
    $('#lkey').innerHTML = `<b>${esc(spellLine(rk, it, flat))}</b>`
      + (s.tr ? `<sup>${s.tr > 0 ? '+' : '−'}${Math.abs(s.tr)}</sup>` : '');
    $('#lcapo').classList.toggle('off', !s.capo);
    $('#lcapo').textContent = s.capo ? t('live.capoChip', { n: roman(s.capo) }) : '';
    $('#lbpm').innerHTML = `<b>${s.bpm || '—'}</b> bpm`;
    $('#trval').textContent = s.tr > 0 ? '+' + s.tr : String(s.tr || 0);
    $('#capoval').textContent = s.capo ? roman(s.capo) : '0';
    $('#leadval').textContent = (s.lead || 0) + 's';
    const pct = Math.round((s.scroll || 1) * 100);
    spd.value = String(pct); $('#spdval').textContent = pct + '%';
    sheet.innerHTML = renderNotes(s.notes, curBand().me)
      + renderSheet(s.body, { tr, struct: live.struct, it, flat, roots: o.roots, plain: o.plain });
  }

  sheet.scrollTop = 0;
  $('#fsval').textContent = String(live.fs);
  if (live.offSong) {
    $('#nextlbl').textContent = t('live.offSet');
    const cur = list[live.i];
    const back = cur ? (cur.type === 'mark' ? cur.label : cur.song.title) : '';
    $('#nextnm').innerHTML = `${esc(t('live.backToSet'))}${back ? ` <span>· ${esc(back)}</span>` : ''}`;
  } else {
    $('#nextlbl').textContent = t(nx ? 'live.next' : 'live.end');
    $('#nextnm').innerHTML = nx
      ? (nx.type === 'mark'
          ? `<span>&mdash;</span> ${esc(nx.label)}${nx.dur ? ` <span>· ${esc(nx.dur)}</span>` : ''}`
          : `<span>${String(ord[live.i + 1]).padStart(2, '0')}</span> ${esc(nx.song.title)} <span>· ${esc(keyLabel(nx.song))}</span>`)
      : esc(t('live.endText'));
  }
  drawTimer();
  drawBar();
  /* la velocità si può calcolare solo a impaginazione fatta */
  requestAnimationFrame(measure);
}

export function drawBar(): void {
  $('#lbar').classList.toggle('off', !S.db.opt.bar);
  $<HTMLButtonElement>('#kprev').disabled = !live.offSong && live.i === 0;
  $('#kplay').classList.toggle('on', live.play);
  /* durante l'attesa il tasto diventa il conto alla rovescia */
  const w = live.play && live.wait > 0;
  $('#kplay').classList.toggle('wait', w);
  $('#kplayg').innerHTML = w ? String(Math.ceil(live.wait))
                             : live.play ? '&#10073;&#10073;' : '&#9654;&#65038;';
  $('#kplayc').textContent = t(w ? 'k6.wait' : live.play ? 'k6.pause' : 'k6.play');
  $('#kstruct').classList.toggle('on', live.struct);
  $('#klock').classList.toggle('on', live.lock);
  $('#klockc').textContent = t(live.lock ? 'k6.unlock' : 'k6.lock');
  $('#live').classList.toggle('lock', live.lock);
}

export function drawTimer(): void {
  if (!live.t0) return;
  const el = $('#ltime');
  const elapsed = Math.floor((Date.now() - live.t0) / 1000);
  const atteso = setDuration(setItems().slice(0, live.i), curBand().songs);
  const d = elapsed - atteso;
  el.innerHTML = `<b>${mmss(elapsed)}</b> ${signed(d)}`;
  el.classList.toggle('late', d > 60);
}

/* Leggere scrollHeight costringe il browser a rifare il layout: si fa
   una volta per brano e a ogni cambio che sposta il testo, non a ogni
   fotogramma. */
export function measure(): void {
  const el = $('#sheet'), e = liveEntry();
  live.pps = (e && e.type === 'song')
    ? speedFor(el.scrollHeight - el.clientHeight, secs(e.song.dur), e.song.scroll || 1, e.song.lead || 0)
    : 0;
}

export function setPlay(on: boolean): void {
  live.play = !!on && live.pps > 0;
  cancelAnimationFrame(live.raf); live.raf = 0;
  if (live.play) {
    const e = liveEntry();
    /* l'attesa vale solo se parti dall'inizio: se hai messo in pausa a
       metà brano, riprendere deve riprendere e basta */
    live.wait = ($('#sheet').scrollTop < 2 && e && e.type === 'song') ? (e.song.lead || 0) : 0;
    live.last = performance.now(); live.acc = 0;
    live.raf = requestAnimationFrame(tick);
  } else {
    live.wait = 0;
  }
  drawBar();
}

function tick(now: number): void {
  if (!live.play) return;
  const el = $('#sheet');
  const dt = (now - live.last) / 1000;
  live.last = now;
  if (live.wait > 0) {
    const before = Math.ceil(live.wait);
    live.wait -= dt;
    if (Math.ceil(Math.max(live.wait, 0)) !== before) drawBar();
    if (live.wait > 0) { live.raf = requestAnimationFrame(tick); return; }
    live.wait = 0; drawBar();
  }
  live.acc += dt * live.pps;
  if (live.acc >= 1) { const px = Math.floor(live.acc); el.scrollTop += px; live.acc -= px; }
  /* arrivati in fondo il brano è finito: si ferma da solo */
  if (el.scrollTop >= el.scrollHeight - el.clientHeight - 1) return setPlay(false);
  live.raf = requestAnimationFrame(tick);
}
