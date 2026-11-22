/* Font in locale, non da Google Fonts: dentro l'app installata la prima
   apertura può avvenire senza rete, e sul palco spesso la rete non c'è. */
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import '@fontsource/ibm-plex-mono/600.css';
import '@fontsource/ibm-plex-sans-condensed/400.css';
import '@fontsource/ibm-plex-sans-condensed/500.css';
import '@fontsource/ibm-plex-sans-condensed/600.css';
import '@fontsource/ibm-plex-sans-condensed/700.css';
import './styles.css';

import { migrate } from './core/model';
import { seed } from './core/seed';
import { setLang, pickLang } from './i18n';
import { askPersist } from './storage/store';
import { applyI18n } from './ui/dom';
import { S, store, save, applyTheme } from './ui/state';
import { drawHome, drawSet, drawLib, drawPrac, drawGigs } from './ui/views';
import { bindEvents } from './ui/events';
import { initUpdates } from './ui/update';
import { LANGKEY, lsGet } from './ui/prefs';

async function boot(): Promise<void> {
  setLang(pickLang(lsGet(LANGKEY), navigator.language));
  applyI18n();
  const raw = await store.load();
  S.db = migrate(raw || seed());
  applyTheme();
  /* Se i dati arrivavano dal localStorage di prima, questo li porta in
     IndexedDB subito, invece di aspettare la prima modifica. */
  if (raw) await save();
  drawHome(); drawSet(); drawLib(); drawPrac(); drawGigs();
  bindEvents();
  void askPersist();
  void initUpdates();
}
void boot();
