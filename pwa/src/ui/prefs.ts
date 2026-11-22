/* Preferenze di questo device, fuori dal database: non finiscono mai
   nell'export/import né nella cancellazione totale, che riguardano solo
   repertorio e scalette.
   - token e id del Gist per la sincronizzazione: il token è un segreto
     e non deve viaggiare con i dati
   - la lingua: è di chi tiene in mano il device, non del repertorio */
export const TOKKEY = 'backline:sync-token';
export const GISTKEY = 'backline:sync-gist';
export const LANGKEY = 'backline:lang';

export const lsGet = (k: string): string | null => {
  try { return localStorage.getItem(k); } catch { return null; }
};
export const lsSet = (k: string, v: string): void => {
  try { localStorage.setItem(k, v); } catch { /* preferenza persa, non i dati */ }
};
export const lsDel = (k: string): void => {
  try { localStorage.removeItem(k); } catch { /* idem */ }
};
