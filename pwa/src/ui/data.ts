import { migrate } from '../core/model';
import { seed } from '../core/seed';
import { t } from '../i18n';
import { $in } from './dom';
import { S, save, store, curBand, applyTheme } from './state';
import { redrawAll, showView } from './views';
import { TOKKEY, GISTKEY, lsSet } from './prefs';
import { toast, uiConfirm } from './dialog';

const closeOpts = () => showView('set');
const resetFilters = () => { S.libQ = ''; S.libTag = ''; $in('#q').value = ''; };

/* Si esporta il gruppo attivo, non tutto: il file è quello che passi
   alla band, e le altre band non c'entrano niente.
   Due esportazioni, non una con la domanda: una domanda che ricompare a
   ogni click si preme a occhi chiusi dopo tre giorni, e da lì in poi non
   protegge più niente.
   La versione "per la band" toglie ruolo e note — le uniche due cose
   personali — e lascia tutto il resto. Trasposizione, capo, scorrimento e
   attesa restano: sono la taratura del pezzo, non roba privata, e a
   toglierle chi riceve si ritroverebbe l'autoscroll da rifare su ogni
   brano. */
export function exportBand(perBand: boolean): void {
  const gruppo: Record<string, unknown> & { name: string; id: string; songs: Record<string, unknown>[] } =
    structuredClone(curBand()) as never;
  if (perBand) {
    delete gruppo.me;
    gruppo.songs.forEach(s => { delete s.notes; });
  }
  const one = { v: 4, bands: [gruppo], curBand: gruppo.id, opt: S.db.opt };
  const blob = new Blob([JSON.stringify(one, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'stagepilot-' + (gruppo.name || 'gruppo').toLowerCase().replace(/\s+/g, '-')
             + (perBand ? '-band' : '') + '.json';
  a.click(); URL.revokeObjectURL(a.href);
}

/* L'import aggiunge, non sostituisce: un file di un gruppo non deve
   poter cancellare gli altri. */
export function importFile(): void {
  const inp = document.createElement('input'); inp.type = 'file'; inp.accept = '.json,application/json';
  inp.onchange = async () => {
    try {
      const parsed = JSON.parse(await inp.files![0].text());
      if (!Array.isArray(parsed.songs) && !Array.isArray(parsed.bands)) throw 0;
      const inc = migrate(parsed);
      const names = inc.bands.map(b => b.name).join(', ');
      if (!(await uiConfirm(t('imp.confirm', { names })))) return;
      /* L'importazione sostituiva il gruppo per intero, e con lui buttava
         via due cose che appartengono a chi riceve e non al repertorio:
         il ruolo e gli appunti personali. Chi sta dall'altra parte
         riceveva un aggiornamento dei testi e perdeva i propri "qui
         prendo fiato" — ogni volta, in silenzio. */
      for (const b of inc.bands) {
        const i = S.db.bands.findIndex(x => x.id === b.id);
        if (i < 0) { S.db.bands.push(b); continue; }
        const mio = S.db.bands[i];
        /* Il ruolo dice chi sei tu in questo gruppo: non arriva da fuori. */
        b.me = mio.me;
        /* Le note si fondono per ruolo: quelle in arrivo vincono dove ci
           sono, le tue restano dove il file non dice niente. Con un file
           "per la band", che di note non ne porta nessuna, questo vuol
           dire che le tue sopravvivono tutte. */
        const prima = new Map(mio.songs.map(s => [s.id, s.notes || {}]));
        b.songs.forEach(s => {
          const vecchie = prima.get(s.id);
          if (vecchie) s.notes = { ...vecchie, ...(s.notes || {}) };
        });
        S.db.bands[i] = b;
      }
      S.db.curBand = inc.bands[0].id; resetFilters();
      await save(); closeOpts(); redrawAll();
    } catch { toast(t('imp.bad'), true); }
  };
  inp.click();
}

/* ── sincronizzazione via Gist ───────────────────────────── */
/* Resta finché non arriva il cloud con l'account (fase 4 del piano). */
export async function syncPush(): Promise<void> {
  const tok = $in('#o-tok').value.trim();
  const gid = $in('#o-gist').value.trim();
  if (!tok) { toast(t('sync.needTok'), true); return; }
  lsSet(TOKKEY, tok);
  try {
    const res = await fetch(gid ? `https://api.github.com/gists/${gid}` : 'https://api.github.com/gists', {
      method: gid ? 'PATCH' : 'POST',
      headers: { 'Authorization': 'token ' + tok, 'Accept': 'application/vnd.github+json' },
      /* Gist "secret": non compare nel profilo e non finisce nelle ricerche
         né sui motori. Non è però una cassaforte — chi ha l'id lo legge
         senza token. Vale per un repertorio, non per una password.
         Attenzione: GitHub guarda questo campo solo alla creazione, sulle
         PATCH lo ignora. Un Gist nato pubblico resta pubblico per sempre:
         l'unico modo è cancellarlo e ripubblicare da zero. */
      body: JSON.stringify({ description: 'Backline — backup', public: false,
                             files: { 'backline.json': { content: JSON.stringify(S.db) } } })
    });
    if (!res.ok) throw new Error(String(res.status));
    const out = await res.json();
    $in('#o-gist').value = out.id; lsSet(GISTKEY, out.id);
    toast(t('sync.pushed'));
  } catch { toast(t('sync.pushFail'), true); }
}

export async function syncPull(): Promise<void> {
  const gid = $in('#o-gist').value.trim();
  if (!gid) { toast(t('sync.needGist'), true); return; }
  lsSet(GISTKEY, gid);
  if (!(await uiConfirm(t('sync.pullConfirm')))) return;
  try {
    const res = await fetch(`https://api.github.com/gists/${gid}`,
      { headers: { 'Accept': 'application/vnd.github+json' } });
    if (!res.ok) throw new Error(String(res.status));
    const out = await res.json();
    const file = out.files && out.files['backline.json'];
    if (!file) throw new Error('missing file');
    S.db = migrate(JSON.parse(file.content));
    applyTheme();
    await save(); closeOpts(); redrawAll();
  } catch { toast(t('sync.pullFail'), true); }
}

export async function wipeAll(): Promise<void> {
  if (!(await uiConfirm(t('wipe.confirm1'), { danger: true }))) return;
  if (!(await uiConfirm(t('wipe.confirm2'), { danger: true }))) return;
  await store.wipe();
  S.db = migrate(seed());
  applyTheme();
  resetFilters();
  await save(); closeOpts(); redrawAll();
}
