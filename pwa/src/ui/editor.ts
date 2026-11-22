import { esc } from '../core/text';
import { ROLES } from '../core/model';
import { bpmFromTaps } from '../core/time';
import { toEnglishLine, flatFor } from '../core/chords';
import { roleLabel, renderSheet } from '../core/sheet';
import { t } from '../i18n';
import { $, $in } from './dom';
import { S, curBand, byId, isIt } from './state';

const area = (s: string) => $<HTMLTextAreaElement>(s);
let taps: number[] = [];
let previewOn = false;

export function setEditorPreview(on: boolean): void {
  previewOn = on;
  const body = area('#f-body');
  const prev = $('#f-prev');
  const btn = $('#ebprev');
  body.classList.toggle('off', on);
  prev.classList.toggle('off', !on);
  btn.classList.toggle('on', on);
  btn.textContent = t(on ? 'ed.editMode' : 'ed.preview');
  if (on) {
    const o = S.db.opt;
    const k = toEnglishLine($in('#f-key').value.trim()) || 'C';
    const flat = flatFor(k, o.accid);
    prev.classList.toggle('caps', !!o.caps);
    prev.innerHTML = renderSheet(body.value, {
      tr: 0, struct: false, it: isIt(), flat, roots: o.roots, plain: o.plain
    });
  }
}

export function toggleEditorPreview(): void {
  setEditorPreview(!previewOn);
}

export function tapBpm(): void {
  const now = performance.now();
  if (taps.length && now - taps[taps.length - 1] > 2500) taps = [];
  taps.push(now);
  if (taps.length > 9) taps.shift();
  const bpm = bpmFromTaps(taps);
  if (bpm !== null) $in('#f-bpm').value = String(bpm);
}

/* Impronta dei campi dell'editor. Serve a una domanda sola: alla chiusura,
   c'è qualcosa che andrebbe perso? Confronta i valori ripuliti come li
   ripulisce il salvataggio, se no un solo spazio di troppo farebbe scattare
   l'avviso a vuoto. Le note stanno per ruolo, e quella a schermo appartiene
   al ruolo corrente: va rimessa al suo posto prima di confrontare. */
export function editSnap(): string {
  const notes: Record<string, string> = { ...S.editNotes, [curBand().me]: area('#f-note').value.trim() };
  const puliti: Record<string, string> = {};
  Object.keys(notes).sort().forEach(r => { if (notes[r]) puliti[r] = notes[r]; });
  return JSON.stringify([
    $in('#f-title').value.trim(), $in('#f-artist').value.trim(), $in('#f-key').value.trim(),
    String($in('#f-bpm').value).trim(), $in('#f-dur').value.trim(), $in('#f-tags').value.trim(),
    area('#f-body').value, puliti,
    /* anche le scalette: spegnere una pastiglia e poi chiudere con la ✕ è
       una modifica che si perde come le altre, e va avvisata come le altre */
    [...S.editSets].sort()
  ]);
}
export const editDirty = (): boolean => editSnap() !== S.editMark;

export function openEdit(id: string | null): void {
  S.editing = id;
  const s = (id && byId(id)) || { title: '', artist: '', key: '', bpm: '', dur: '', body: '', tags: [], notes: {} };
  S.editNotes = { ...(s.notes || {}) } as Record<string, string>;
  $in('#f-title').value = s.title; $in('#f-artist').value = s.artist || '';
  $in('#f-key').value = s.key || ''; $in('#f-bpm').value = String(s.bpm || ''); $in('#f-dur').value = s.dur || '';
  $in('#f-tags').value = (s.tags || []).join(', ');
  area('#f-body').value = s.body || '';
  drawRoles();
  drawArtisti();
  /* In quali scalette sta adesso. La selezione si tiene qui e si applica
     solo al salvataggio, come le note: chiudendo senza salvare non deve
     cambiare niente, né il brano né le scalette. Un brano nuovo parte da
     nessuna, perché un id ancora non ce l'ha. */
  S.editSets = new Set(id
    ? curBand().sets.filter(st => st.items.some(it => it.type === 'song' && it.id === id)).map(st => st.id)
    : []);
  drawSetChips();
  /* Su un brano che non esiste ancora non c'è niente da eliminare né da
     duplicare: i due tasti compaiono solo in modifica. */
  $('#edel').style.display = id ? '' : 'none';
  $('#edup').style.display = id ? '' : 'none';
  taps = [];
  setEditorPreview(false);
  $('#edit').classList.add('on');
  S.editMark = editSnap();   // com'era all'apertura: il metro per la chiusura
}

/* Le scalette del gruppo come pastiglie: accesa vuol dire che il pezzo ci
   sta dentro. Aprendo un brano dice a colpo d'occhio dove sta finito, cosa
   che altrimenti si saprebbe solo aprendo le scalette una per una. */
export function drawSetChips(): void {
  $('#f-sets').innerHTML = curBand().sets.map(s =>
    `<button type="button" class="tag${S.editSets.has(s.id) ? ' on' : ''}" data-sid="${esc(s.id)}">${esc(s.name)}</button>`
  ).join('');
}

/* Allinea le scalette alla selezione. Accesa e non c'era: si accoda in
   fondo — la posizione precisa resta roba da trascinamento. Spenta e
   c'era: esce dalla scaletta ma RESTA nel repertorio, che è la stessa
   distinzione che fa la ✕ nella lista. */
export function applySetChips(id: string): void {
  for (const s of curBand().sets) {
    const dentro = s.items.some(it => it.type === 'song' && it.id === id);
    const voluto = S.editSets.has(s.id);
    if (voluto && !dentro) s.items.push({ type: 'song', id });
    else if (!voluto && dentro) s.items = s.items.filter(it => !(it.type === 'song' && it.id === id));
  }
}

/* Gli artisti già in repertorio, per completare il campo mentre si scrive.
   Solo quelli del gruppo attivo: i gruppi sono isolati apposta, e vedersi
   suggerire i nomi di un'altra band sarebbe una crepa in quel confine.
   `localeCompare` con l'italiano perché l'ordine di default mette De André
   dopo Zucchero, e in una tendina si nota subito. */
function drawArtisti(): void {
  const nomi = [...new Set(
    curBand().songs.map(s => String(s.artist || '').trim()).filter(Boolean)
  )].sort((a, b) => a.localeCompare(b, 'it'));
  $('#artisti').innerHTML = nomi.map(a => `<option value="${esc(a)}"></option>`).join('');
}

/* Il pallino segnala i ruoli che hanno già una nota su questo brano:
   senza, l'unico modo di scoprirlo sarebbe aprirli uno per uno. */
export function drawRoles(): void {
  const me = curBand().me;
  $('#f-role').innerHTML = ROLES.map(r =>
    `<option value="${r}"${r === me ? ' selected' : ''}>${esc(roleLabel(r))}${S.editNotes[r] ? ' •' : ''}</option>`
  ).join('');
  area('#f-note').value = S.editNotes[me] || '';
}
