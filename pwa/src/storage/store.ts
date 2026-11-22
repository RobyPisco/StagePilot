/* ═══════════════════════════════════════════════════════════
   STORAGE
   Il database vive in IndexedDB. Prima stava in localStorage, che dentro
   un'app installata il sistema può svuotare quando manca spazio: per un
   repertorio di anni non è accettabile. In più IndexedDB non ha il tetto
   dei ~5 MB, e il giorno che entrano i PDF il posto c'è già.

   Il passaggio è silenzioso: se IndexedDB è vuoto si leggono le chiavi
   localStorage di prima, e al primo salvataggio i dati finiscono in
   IndexedDB. Le chiavi vecchie NON si cancellano: restano lì come copia
   di sicurezza, finché non lo chiede "Cancella tutti i dati".
   ═══════════════════════════════════════════════════════════ */

export const KEY = 'backline:db';
/* L'app si è chiamata Scaletta e poi Leggio: le chiavi vecchie si leggono
   ancora, in ordine. Chi aveva già i suoi dati non deve accorgersi del
   cambio di nome. */
export const OLD_KEYS = ['leggio:db', 'scaletta:db'];

const DB_NAME = 'backline', OBJ = 'kv', DOC = 'db';

type KV = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
export interface Store {
  load(): Promise<unknown | null>;
  /* false = non si è riusciti a salvare da nessuna parte */
  save(data: unknown): Promise<boolean>;
  wipe(): Promise<void>;
}

/* Le dipendenze arrivano da fuori così i test possono passare un
   IndexedDB finto e un localStorage fatto con una Map. */
export function createStore(idb: IDBFactory | undefined, ls: KV | undefined): Store {
  let conn: Promise<IDBDatabase | null> | null = null;
  const open = (): Promise<IDBDatabase | null> => conn ??= new Promise(res => {
    if (!idb) return res(null);
    try {
      const req = idb.open(DB_NAME, 1);
      req.onupgradeneeded = () => req.result.createObjectStore(OBJ);
      req.onsuccess = () => res(req.result);
      /* navigazione privata su certi browser, quote a zero, ecc.: si
         ripiega su localStorage invece di restare senza dati */
      req.onerror = () => res(null);
      req.onblocked = () => res(null);
    } catch { res(null); }
  });
  const run = <T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>) =>
    open().then(db => db && new Promise<T | undefined>((res, rej) => {
      const tx = db.transaction(OBJ, mode);
      const req = fn(tx.objectStore(OBJ));
      tx.oncomplete = () => res(req.result);
      tx.onerror = () => rej(tx.error);
      tx.onabort = () => rej(tx.error);
    }));

  const fromLocal = (): unknown | null => {
    for (const k of [KEY, ...OLD_KEYS]) {
      try { const s = ls?.getItem(k); if (s) return JSON.parse(s); } catch { /* chiave rotta: si prova la prossima */ }
    }
    return null;
  };

  return {
    async load() {
      try {
        const v = await run('readonly', s => s.get(DOC));
        if (v) return v;
      } catch { /* si ripiega */ }
      return fromLocal();
    },
    async save(data) {
      /* Si salva una copia pulita: IndexedDB clona l'oggetto, e un campo
         non clonabile finito per sbaglio nel database farebbe fallire
         tutto il salvataggio. Il JSON è anche il formato dell'export,
         quindi quello che c'è dentro è per forza esportabile. */
      const plain = JSON.parse(JSON.stringify(data));
      try {
        const db = await open();
        if (db) { await run('readwrite', s => s.put(plain, DOC)); return true; }
      } catch { /* si ripiega */ }
      try { ls?.setItem(KEY, JSON.stringify(plain)); return true; } catch { return false; }
    },
    async wipe() {
      try { await run('readwrite', s => s.delete(DOC)); } catch { /* niente da cancellare */ }
      for (const k of [KEY, ...OLD_KEYS]) { try { ls?.removeItem(k); } catch { /* idem */ } }
    }
  };
}

/* Chiede al browser di non sfrattare i dati quando lo spazio scarseggia.
   Può dire di no, e non è un errore: si chiede e basta. */
export const askPersist = async (): Promise<void> => {
  try { await navigator.storage?.persist?.(); } catch { /* niente */ }
};
