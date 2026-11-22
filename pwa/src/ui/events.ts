import { migrateBand, parseBpm, parseTags, ROLES, type Role, type Sort, type Theme, type PedalMode } from '../core/model';
import { toEnglishLine, normalizeBody } from '../core/chords';
import { tabToChordPro, looksRawTab } from '../core/convert';
import { setAsText, moveItem } from '../core/setlist';
import { t, setLang, pickLang } from '../i18n';
import { $, $in, flashBtn, applyI18n } from './dom';
import { S, live, save, saveSoon, applyTheme, curBand, curSet, setItems, byId, entries, liveEntry, keyLabel, type ViewName } from './state';
import { drawHome, drawSet, drawLib, drawPrac, drawGigs, drawBands, drawOpts, redrawAll, printSetlist, showView } from './views';
import { openLive, closeLive, takeWake, paint, drawBar, drawTimer, measure, setPlay, liveOn, openJump, closeJump, drawJump, jumpToSong, scrollHalfPage } from './live';
import { openEdit, editDirty, drawSetChips, drawRoles, applySetChips, toggleEditorPreview, tapBpm } from './editor';
import { exportBand, importFile, syncPush, syncPull, wipeAll } from './data';
import { updNow, updLater } from './update';
import { LANGKEY, lsSet, lsDel } from './prefs';
import { toast, uiConfirm, uiPrompt, uiMarkPrompt } from './dialog';
import { toggleMetro } from './metro';
import { bindNativeBack } from './native';

const area = (s: string) => $<HTMLTextAreaElement>(s);
const resetFilters = () => { S.libQ = ''; S.libTag = ''; $in('#q').value = ''; };
const clampN = (v: number, lo: number, hi: number) => Math.max(lo, Math.min(hi, v));

/* ── click ───────────────────────────────────────────────── */
/* Un gestore solo per tutti i tasti: il markup si ridisegna di continuo,
   e agganciare i gestori ai singoli elementi vorrebbe dire riagganciarli
   a ogni disegno. */
async function onClick(e: MouseEvent): Promise<void> {
  /* un trascinamento appena finito genera un click: va ignorato, se no
     lasciare la riga fa partire il brano */
  if (Date.now() - S.dragTs < 300) return;
  const b = (e.target as Element).closest('button'); if (!b) return;
  const d = b.dataset;

  if (b.classList.contains('tab')) {
    showView(d.v as ViewName);
    return;
  }

  // oggi, guida, prove, concerti
  if (d.hact === 'go' && setItems().length) return openLive(0);
  if (d.hact === 'new') return openEdit(null);
  if (d.hact === 'prac') { showView('prac'); return; }
  if (d.hact === 'guide' || b.id === 'oguide') { $('#guide').classList.add('on'); return; }
  if (b.id === 'gclose') { $('#guide').classList.remove('on'); return; }
  if (b.id === 'pnew') {
    const bnd = curBand();
    if (!bnd.songs.length) { showView('lib'); return openEdit(null); }
    const goal = ((await uiPrompt(t('prac.promptGoal'))) || '').trim();
    if (!goal) return;
    bnd.practiceTasks.push({
      id: 'p' + Date.now(), songId: bnd.songs[0].id,
      section: 'Brano intero', goal, role: bnd.me, done: false
    });
    await save(); drawPrac(); drawHome(); return;
  }
  if (d.ptog) {
    const p = curBand().practiceTasks.find(x => x.id === d.ptog);
    if (p) { p.done = !p.done; await save(); drawPrac(); drawHome(); }
    return;
  }
  if (d.pdel) {
    curBand().practiceTasks = curBand().practiceTasks.filter(x => x.id !== d.pdel);
    await save(); drawPrac(); drawHome(); return;
  }
  if (d.pgo) {
    const idx = entries().findIndex(x => x.type === 'song' && x.song.id === d.pgo);
    if (idx >= 0) return openLive(idx);
    live.offSong = d.pgo;
    return openLive(0);
  }
  if (b.id === 'gnew') {
    const title = ((await uiPrompt(t('gigs.promptTitle'))) || '').trim();
    if (!title) return;
    const today = new Date().toISOString().slice(0, 10);
    curBand().events.push({
      id: 'g' + Date.now(), title, date: today, time: '21:00',
      location: '', setId: curSet().id, notes: ''
    });
    await save(); drawGigs(); drawHome(); return;
  }
  if (d.gplay) {
    if (d.gplay && curBand().sets.some(s => s.id === d.gplay)) curBand().cur = d.gplay;
    if (setItems().length) return openLive(0);
    showView('set'); return;
  }
  if (d.gdel) {
    curBand().events = curBand().events.filter(x => x.id !== d.gdel);
    await save(); drawGigs(); drawHome(); return;
  }

  // tema rapido
  if (b.id === 'themebtn') {
    S.db.opt.theme = S.db.opt.theme === 'dark' ? 'light' : 'dark';
    applyTheme();
    await save();
    drawOpts();
    return;
  }

  // opzioni
  if (d.ofs) {
    S.db.opt.fs = clampN(S.db.opt.fs + +d.ofs, 14, 48);
    await save(); drawOpts(); return;
  }
  if (b.id === 'o-bar') { S.db.opt.bar = !S.db.opt.bar; await save(); drawOpts(); drawBar(); return; }
  if (b.id === 'o-metro') { S.db.opt.metroSound = !S.db.opt.metroSound; await save(); drawOpts(); return; }
  /* La classe si applica a mano invece di ripassare da paint(): quello
     rimanderebbe il brano a inizio pagina, e girare un interruttore non
     deve farti perdere il segno mentre stai suonando. */
  if (b.id === 'o-caps') {
    S.db.opt.caps = !S.db.opt.caps;
    $('#sheet').classList.toggle('caps', S.db.opt.caps);
    await save(); drawOpts(); return;
  }
  /* Le fondamentali cambiano il testo degli accordi, quindi qui il chart va
     ridisegnato per forza. paint() però rimanda a inizio pagina: il punto in
     cui si stava leggendo si salva e si rimette, se no cambiare opzione a
     metà pezzo fa perdere il segno. */
  if (b.id === 'o-roots' || b.id === 'o-plain') {
    if (b.id === 'o-roots') S.db.opt.roots = !S.db.opt.roots;
    else S.db.opt.plain = !S.db.opt.plain;
    await save(); drawOpts();
    if (liveOn()) {
      const sh = $('#sheet'), y = sh.scrollTop;
      paint(); sh.scrollTop = y;
    }
    return;
  }
  if (b.id === 'o-timer') {
    S.db.opt.timer = !S.db.opt.timer; await save(); drawOpts();
    $('#ltime').classList.toggle('off', !S.db.opt.timer); return;
  }
  if (b.id === 'o-push') return syncPush();
  if (b.id === 'o-pull') return syncPull();
  if (b.id === 'o-wipe') return wipeAll();

  // gruppi
  if (b.id === 'bandbtn') { drawBands(); $('#bands').classList.add('on'); return; }
  if (b.id === 'bclose') { $('#bands').classList.remove('on'); return; }
  if (d.band) {
    S.db.curBand = d.band; resetFilters();
    await save(); drawBands(); drawSet(); drawLib(); return;
  }
  if (b.id === 'bnew') {
    const name = ((await uiPrompt(t('bands.newPrompt'))) || '').trim();
    if (!name) return;
    const nb = migrateBand({ id: 'b' + Date.now(), name }, S.db.bands.length);
    S.db.bands.push(nb); S.db.curBand = nb.id; resetFilters();
    await save(); drawBands(); drawSet(); drawLib(); return;
  }
  if (b.id === 'bren') {
    const name = ((await uiPrompt(t('prompt.rename'), curBand().name)) || '').trim();
    if (!name) return;
    curBand().name = name; await save(); drawBands(); drawSet(); return;
  }
  if (b.id === 'bdel') {
    if (S.db.bands.length < 2) return;
    if (!(await uiConfirm(t('bands.delConfirm', { name: curBand().name }), { danger: true }))) return;
    S.db.bands = S.db.bands.filter(x => x.id !== S.db.curBand);
    S.db.curBand = S.db.bands[0].id; resetFilters();
    await save(); drawBands(); drawSet(); drawLib(); return;
  }

  // scalette
  if (b.id === 'snew') {
    const name = ((await uiPrompt(t('set.newPrompt'))) || '').trim();
    if (!name) return;
    const s = { id: 'set' + Date.now(), name, items: [] };
    curBand().sets.push(s); curBand().cur = s.id;
    await save(); return drawSet();
  }
  if (b.id === 'sren') {
    const name = ((await uiPrompt(t('prompt.rename'), curSet().name)) || '').trim();
    if (!name) return;
    curSet().name = name; await save(); return drawSet();
  }
  /* Negli appunti, non in un file: quello che serve è incollarla in chat.
     `navigator.clipboard` vuole un contesto sicuro e può essere negato, e in
     quel caso la textarea con `execCommand` funziona ancora quasi ovunque —
     brutta ma è l'unica strada che regge un browser vecchio o un permesso
     rifiutato. Se cade anche quella lo si dice, invece di far finta. */
  if (b.id === 'scopy') {
    const txt = setAsText(setItems(), curBand().songs,
      { band: curBand().name, name: curSet().name, keyOf: s => keyLabel(s as never) });
    let fatto = false;
    try { await navigator.clipboard.writeText(txt); fatto = true; } catch { /* si prova l'altra strada */ }
    if (!fatto) try {
      const ta = document.createElement('textarea');
      ta.value = txt;
      ta.style.cssText = 'position:fixed;top:-1000px;opacity:0';
      document.body.appendChild(ta); ta.select();
      fatto = document.execCommand('copy');
      ta.remove();
    } catch { /* fallito anche questo: lo si dice sotto */ }
    flashBtn(b, fatto ? '✓' : '✗');
    toast(t(fatto ? 'set.copied' : 'set.copyFail'), !fatto);
    return;
  }
  if (b.id === 'sprint') { printSetlist(); return; }
  if (b.id === 'sdup') {
    const s = { id: 'set' + Date.now(), name: t('copyOf', { name: curSet().name }),
                items: curSet().items.map(it => ({ ...it })) };
    curBand().sets.push(s); curBand().cur = s.id;
    await save(); return drawSet();
  }
  if (b.id === 'sdel') {
    if (curBand().sets.length < 2) return;
    if (!(await uiConfirm(t('set.delConfirm', { name: curSet().name }), { danger: true }))) return;
    const band = curBand();
    band.sets = band.sets.filter(s => s.id !== band.cur);
    band.cur = band.sets[0].id;
    await save(); return drawSet();
  }
  if (b.id === 'smark') {
    const mk = await uiMarkPrompt();
    if (!mk) return;
    setItems().push({ type: 'mark', label: mk.label, dur: mk.dur });
    await save(); return drawSet();
  }

  if (b.id === 'go' && setItems().length) return openLive(0);
  if (d.play !== undefined) return openLive(+d.play);
  if (d.rm !== undefined) { setItems().splice(+d.rm, 1); await save(); return drawSet(); }
  if (d.add) {
    const a = setItems(), i = a.findIndex(x => x.type === 'song' && x.id === d.add);
    i < 0 ? a.push({ type: 'song', id: d.add }) : a.splice(i, 1);
    await save(); return drawLib();
  }
  if (d.sid) {
    S.editSets.has(d.sid) ? S.editSets.delete(d.sid) : S.editSets.add(d.sid);
    return drawSetChips();
  }
  if (d.tag !== undefined) { S.libTag = (S.libTag === d.tag) ? '' : d.tag; return drawLib(); }
  if (d.ed) return openEdit(d.ed);
  if (b.id === 'new') return openEdit(null);

  if (b.id === 'exp' || b.id === 'expb') return exportBand(b.id === 'expb');
  if (b.id === 'imp') return importFile();

  // aggiornamenti
  if (b.id === 'updgo') return updNow();
  if (b.id === 'updlater') return updLater();

  // live
  if (b.id === 'lclose') return closeLive();
  if (b.id === 'ljump') return openJump();
  if (b.id === 'lpick-close') return closeJump();
  if (d.jump) return jumpToSong(d.jump);
  if (b.id === 'lbpm') {
    const en = liveEntry();
    if (en && en.type === 'song') toggleMetro(en.song.bpm);
    return;
  }
  /* Toccare l'avviso è il modo di riprovare: spesso il permesso arriva al
     secondo colpo, perché la prima richiesta era partita in un momento in
     cui il sistema non era disposto a concederlo. */
  if (b.id === 'lwake') { await takeWake(); return; }
  if (b.id === 'lgear' || b.id === 'lkey') { $('#drawer').classList.toggle('on'); return; }
  if (b.id === 'ltime') { live.t0 = Date.now(); return drawTimer(); }
  if (b.id === 'kprev') {
    if (live.offSong) { live.offSong = null; paint(); return; }
    if (live.i > 0) { live.i--; paint(); }
    return;
  }
  if (b.id === 'knext' || b.id === 'nextbtn') {
    if (live.offSong) { live.offSong = null; paint(); return; }
    if (live.i < entries().length - 1) { live.i++; paint(); } else closeLive();
    return;
  }
  if (b.id === 'kplay') return setPlay(!live.play);
  if (b.id === 'kstop') { setPlay(false); $('#sheet').scrollTop = 0; return; }
  if (b.id === 'kstruct') { live.struct = !live.struct; return paint(); }
  if (b.id === 'klock') { live.lock = !live.lock; return drawBar(); }
  if (d.tr || d.capo || d.lead) {
    const en = liveEntry(); if (!en || en.type !== 'song') return;
    const s = en.song;
    if (d.tr)   s.tr   = clampN((s.tr || 0) + +d.tr, -11, 11);
    if (d.capo) s.capo = clampN((s.capo || 0) + +d.capo, 0, 11);
    if (d.lead) s.lead = clampN((s.lead || 0) + +d.lead, 0, 60);
    await save(); return paint();
  }
  if (d.fs) {
    live.fs = clampN(live.fs + +d.fs, 14, 48);
    S.db.opt.fs = live.fs; saveSoon();
    return paint();
  }

  // editor
  if (b.id === 'eclose') {
    /* La X buttava via tutto in silenzio: chi converte un tab e poi chiude
       invece di salvare non aveva modo di accorgersene. */
    if (editDirty() && !(await uiConfirm(t('ed.closeConfirm'), { danger: true }))) return;
    $('#edit').classList.remove('on');
    return;
  }
  if (b.id === 'etap') { tapBpm(); return; }
  if (b.id === 'ebprev') { toggleEditorPreview(); return; }
  if (b.id === 'eug') { area('#f-ug').value = ''; $('#ug').classList.add('on'); area('#f-ug').focus(); return; }
  if (b.id === 'ugclose') { $('#ug').classList.remove('on'); return; }
  if (b.id === 'ugconv') {
    area('#f-body').value = tabToChordPro(area('#f-ug').value);
    $('#ug').classList.remove('on');
    return;
  }
  /* Converte quello che è già nel campo. Serve a chi incolla il tab lì
     dentro invece di passare dalla finestra: era la strada che tutti
     prendevano. Rilanciarlo su un testo già a posto non cambia niente,
     ma senza un segnale sembra che il tasto sia rotto: da qui il fiato
     di conferma. */
  if (b.id === 'ebconv') {
    const el = area('#f-body'), prima = el.value;
    el.value = tabToChordPro(prima);
    flashBtn(b, t(el.value === prima ? 'ed.convertSame' : 'ed.convertDone'));
    return;
  }
  if (b.id === 'esave') return saveSong();
  /* Si duplica un brano per averlo due volte diverso: la stessa canzone in
     due tonalità per due voci, o la versione acustica con le sue note. Si
     copia quello SALVATO, non quello che c'è nei campi: se avessi modifiche
     aperte, duplicarle in sordina sarebbe una sorpresa. La copia si apre
     subito in modifica, perché si duplica per cambiare qualcosa. */
  if (b.id === 'edup') {
    if (!S.editing) return;
    const orig = byId(S.editing);
    if (!orig) return;
    if (editDirty() && !(await uiConfirm(t('ed.dupConfirm')))) return;
    const copia = { ...structuredClone(orig), id: 's' + Date.now(), title: t('copyOf', { name: orig.title }) };
    curBand().songs.push(copia);
    await save();
    S.view === 'lib' ? drawLib() : drawSet();
    return openEdit(copia.id);
  }
  if (b.id === 'edel') {
    if (!(await uiConfirm(t('ed.delConfirm'), { danger: true }))) return;
    const band = curBand();
    band.songs = band.songs.filter(s => s.id !== S.editing);
    band.sets.forEach(s => s.items = s.items.filter(it => it.type !== 'song' || it.id !== S.editing));
    await save(); $('#edit').classList.remove('on'); drawLib(); return;
  }
}

async function saveSong(): Promise<void> {
  const title = $in('#f-title').value.trim();
  if (!title) { $in('#f-title').focus(); return; }
  /* Ultima rete: se il corpo è ancora un tab grezzo lo dico adesso e mi
     offro di convertirlo. In ogni caso si salva: nessuna strada qui
     deve far perdere quello che uno ha scritto. */
  if (looksRawTab(area('#f-body').value) && (await uiConfirm(t('ed.rawConfirm'))))
    area('#f-body').value = tabToChordPro(area('#f-body').value);
  S.editNotes[curBand().me] = area('#f-note').value.trim();
  const notes: Record<string, string> = {};
  for (const r of ROLES) if (S.editNotes[r]) notes[r] = S.editNotes[r];
  const rec = {
    title, artist: $in('#f-artist').value.trim(),
    /* Do Re Mi in ingresso, C D E nel file: così resta scambiabile */
    key: toEnglishLine($in('#f-key').value.trim()),
    bpm: parseBpm($in('#f-bpm').value), dur: $in('#f-dur').value.trim(),
    tags: parseTags($in('#f-tags').value), notes, body: normalizeBody(area('#f-body').value)
  };
  // tr, capo, scroll e lead non sono in rec: si regolano in live e non vanno persi qui
  /* L'id serve subito dopo per le scalette: un brano nuovo ce l'ha solo
     adesso, ed è il motivo per cui le pastiglie si applicano qui e non
     quando le premi. */
  let id = S.editing;
  const old = id ? byId(id) : undefined;
  if (old) Object.assign(old, rec);
  else {
    id = 's' + Date.now();
    curBand().songs.push({ id, tr: 0, capo: 0, scroll: 1, lead: 0, ...rec });
  }
  applySetChips(id!);
  await save(); $('#edit').classList.remove('on');
  S.view === 'lib' ? drawLib() : drawSet();
  toast(t('ed.saved'));
}

/* ── riordino a trascinamento ────────────────────────────── */
/* La maniglia esiste per due motivi: non ruba il tocco al titolo, e ha
   touch-action:none, che è l'unico modo perché sul telefono il gesto non
   se lo prenda lo scorrimento della pagina. */
let drag: { from: number; to: number; rows: HTMLElement[]; h: number; y0: number; el: HTMLElement } | null = null;

function dragStart(e: PointerEvent): void {
  const grip = (e.target as Element).closest('.grip'); if (!grip) return;
  const row = grip.closest<HTMLElement>('.row')!;
  const rows = [...$('#setlist').querySelectorAll<HTMLElement>('.row')];
  const from = rows.indexOf(row);
  if (from < 0 || rows.length < 2) return;
  e.preventDefault();
  drag = { from, to: from, rows, h: row.offsetHeight || 52, y0: e.clientY, el: row };
  row.classList.add('lift');
  document.addEventListener('pointermove', dragMove, { passive: false });
  document.addEventListener('pointerup', dragDrop);
  document.addEventListener('pointercancel', dragDrop);
}
function dragMove(e: PointerEvent): void {
  if (!drag) return;
  e.preventDefault();
  const dy = e.clientY - drag.y0;
  drag.el.style.transform = `translateY(${dy}px)`;
  const to = clampN(drag.from + Math.round(dy / drag.h), 0, drag.rows.length - 1);
  if (to === drag.to) return;
  drag.to = to;
  /* le altre righe si fanno da parte, così si vede dove finirà */
  const { from, h, el } = drag;
  drag.rows.forEach((r, j) => {
    if (r === el) return;
    const sh = (from < to && j > from && j <= to) ? -h
             : (from > to && j >= to && j < from) ? h : 0;
    r.style.transform = sh ? `translateY(${sh}px)` : '';
  });
}
async function dragDrop(): Promise<void> {
  document.removeEventListener('pointermove', dragMove);
  document.removeEventListener('pointerup', dragDrop);
  document.removeEventListener('pointercancel', dragDrop);
  if (!drag) return;
  const { from, to, rows } = drag;
  rows.forEach(r => { r.style.transform = ''; r.classList.remove('lift'); });
  drag = null; S.dragTs = Date.now();
  if (to !== from) {
    curSet().items = moveItem(setItems(), from, to);
    await save();
  }
  drawSet();
}

export function bindEvents(): void {
  document.addEventListener('click', e => { void onClick(e); });

  $in('#q').oninput = e => { S.libQ = (e.target as HTMLInputElement).value; drawLib(); };
  $in('#lpick-q').oninput = e => { drawJump((e.target as HTMLInputElement).value); };
  const sel = (id: string, fn: (v: string) => unknown) => {
    $<HTMLSelectElement>(id).onchange = e => { void fn((e.target as HTMLSelectElement).value); };
  };
  sel('#setsel', async v => { curBand().cur = v; await save(); drawSet(); });
  sel('#sortsel', async v => { S.db.opt.sort = v as Sort; await save(); drawLib(); });
  sel('#o-theme', async v => { S.db.opt.theme = v as Theme; applyTheme(); await save(); });
  sel('#o-pedal', async v => { S.db.opt.pedal = v as PedalMode; await save(); });
  sel('#o-notation', async v => { S.db.opt.notation = v === 'it' ? 'it' : 'en'; await save(); redrawAll(); });
  sel('#o-accid', async v => { S.db.opt.accid = v as typeof S.db.opt.accid; await save(); redrawAll(); });
  /* La lingua è del device, non del repertorio: sta fuori dal database.
     "Automatica" vuol dire seguire quella del sistema. */
  sel('#o-lang', v => {
    v === 'auto' ? lsDel(LANGKEY) : lsSet(LANGKEY, v);
    setLang(pickLang(v, navigator.language));
    applyI18n(); drawOpts(); redrawAll();
    if (liveOn()) drawBar();
  });

  /* Lo slider non è più px/s ma una correzione sulla velocità che discende
     dalla durata: si registra in prova e resta sul brano. */
  $in('#spd').oninput = e => {
    const en = liveEntry(); if (!en || en.type !== 'song') return;
    const v = (e.target as HTMLInputElement).value;
    en.song.scroll = +v / 100;
    $('#spdval').textContent = v + '%';
    measure();
    if (live.play) setPlay(true);
    saveSoon();
  };

  /* Cambiare ruolo qui dice all'app chi sei in questo gruppo, non solo
     quale nota stai scrivendo: la vista live userà lo stesso ruolo. */
  sel('#f-role', async v => {
    S.editNotes[curBand().me] = area('#f-note').value.trim();
    curBand().me = v as Role;
    await save();
    drawRoles();
  });
  sel('#b-role', async v => {
    curBand().me = v as Role;
    await save(); drawBands();
  });

  $('#setlist').addEventListener('pointerdown', dragStart);

  document.addEventListener('keydown', e => {
    if (!liveOn()) return;
    if ($('#lpick').classList.contains('on')) {
      if (e.key === 'Escape') { e.preventDefault(); closeJump(); }
      return;
    }
    if (e.key === 'ArrowRight' || e.key === 'PageDown') {
      e.preventDefault();
      if (S.db.opt.pedal === 'page') scrollHalfPage(1);
      else $('#knext').click();
    }
    if (e.key === 'ArrowLeft' || e.key === 'PageUp') {
      e.preventDefault();
      if (S.db.opt.pedal === 'page') scrollHalfPage(-1);
      else $('#kprev').click();
    }
    if (e.key === 'ArrowDown') { e.preventDefault(); scrollHalfPage(1); }
    if (e.key === 'ArrowUp') { e.preventDefault(); scrollHalfPage(-1); }
    if (e.key === ' ') { e.preventDefault(); setPlay(!live.play); }
    if (e.key === 'Escape') closeLive();
  });

  /* Ruotare il telefono o cambiare corpo del testo cambia quanto c'è da
     scorrere, quindi la velocità va rifatta. */
  addEventListener('resize', () => {
    if (!liveOn()) return;
    measure();
    if (live.play) setPlay(true);
  });

  document.addEventListener('visibilitychange', () => {
    /* Tornando all'app il lock è quasi sempre da riprendere: il sistema lo
       rilascia da solo quando la pagina va in secondo piano. */
    if (document.visibilityState === 'visible' && liveOn() && !live.wake) void takeWake();
  });

  bindNativeBack(handleBack);
}

/* Chiude l'overlay più in alto (usato dal tasto Indietro di Android). */
function handleBack(): boolean {
  if ($('#dlg').classList.contains('on')) { $('#dlg-cancel').click(); return true; }
  if ($('#lpick').classList.contains('on')) { closeJump(); return true; }
  if ($('#drawer').classList.contains('on')) { $('#drawer').classList.remove('on'); return true; }
  if ($('#ug').classList.contains('on')) { $('#ug').classList.remove('on'); return true; }
  if ($('#edit').classList.contains('on')) { $('#eclose').click(); return true; }
  if ($('#bands').classList.contains('on')) { $('#bands').classList.remove('on'); return true; }
  if (liveOn()) {
    if (live.offSong) { live.offSong = null; paint(); return true; }
    closeLive();
    return true;
  }
  if (S.view !== 'set') { showView('set'); return true; }
  return false;
}
