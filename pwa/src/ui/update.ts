import { $ } from './dom';
import { isNative } from './native';

/* ── aggiornamenti ───────────────────────────────────────── */
/* Il service worker nuovo si installa accanto a quello in uso e resta in
   attesa: il momento di passare lo sceglie chi suona. L'avviso però non
   compare mai in vista live — un banner che spunta a metà pezzo è
   esattamente quello che non deve succedere sul palco. Se l'aggiornamento
   arriva mentre stai suonando aspetta, e lo trovi chiudendo il brano.
   "Dopo" lo mette a tacere per questa sessione: alla riapertura ritorna,
   perché la versione nuova resta lì in attesa comunque.
   Il service worker lo genera vite-plugin-pwa a ogni build, con l'elenco
   dei file e la loro impronta: non c'è più una VERSION da alzare a mano. */
let pronto = false, zitto = false;
let aggiorna: ((reload?: boolean) => Promise<void>) | null = null;

export function drawUpd(): void {
  $('#upd').classList.toggle('off', !pronto || zitto || $('#live').classList.contains('on'));
}
export const updNow = (): void => { void aggiorna?.(true); };
export const updLater = (): void => { zitto = true; drawUpd(); };

export async function initUpdates(): Promise<void> {
  /* In sviluppo e dentro Capacitor il service worker non serve: nell'app
     nativa i file stanno già nel pacchetto e gli aggiornamenti arrivano
     dallo store. */
  if (import.meta.env.DEV || isNative() || !('serviceWorker' in navigator)) return;
  try {
    const { registerSW } = await import('virtual:pwa-register');
    aggiorna = registerSW({
      onNeedRefresh() { pronto = true; drawUpd(); }
    });
  } catch { /* senza service worker l'app gira lo stesso, solo non offline */ }
}
