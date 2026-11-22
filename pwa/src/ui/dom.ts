import { t, type Key } from '../i18n';

/* Il markup è scritto a mano in index.html: chi chiede un id sa che
   elemento è, e lo dice col parametro di tipo. Il controllo che l'id
   esista davvero lo fa tests/static.test.ts. */
export const $ = <T extends HTMLElement = HTMLElement>(s: string): T => document.querySelector(s) as T;
export const $in = (s: string) => $<HTMLInputElement>(s);

/* Scritta di conferma sul tasto stesso, poi torna com'era. Per le azioni
   che possono non cambiare niente: senza un segnale sembrano rotte. */
export function flashBtn(btn: HTMLElement, msg: string): void {
  if (!btn.dataset.lab) btn.dataset.lab = btn.textContent || '';
  clearTimeout(+(btn.dataset.timer || 0));
  btn.textContent = msg;
  btn.dataset.timer = String(setTimeout(() => { btn.textContent = btn.dataset.lab || ''; }, 1500));
}

/* Riempie i testi statici del markup dalla lingua corrente. Si richiama
   quando si cambia lingua: le parti disegnate dal JS si ridisegnano per
   conto loro. */
export function applyI18n(root: ParentNode = document): void {
  root.querySelectorAll<HTMLElement>('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n as Key); });
  root.querySelectorAll<HTMLElement>('[data-i18n-html]').forEach(el => { el.innerHTML = t(el.dataset.i18nHtml as Key); });
  root.querySelectorAll<HTMLElement>('[data-i18n-title]').forEach(el => { el.title = t(el.dataset.i18nTitle as Key); });
  root.querySelectorAll<HTMLElement>('[data-i18n-aria]').forEach(el => { el.setAttribute('aria-label', t(el.dataset.i18nAria as Key)); });
  root.querySelectorAll<HTMLInputElement>('[data-i18n-placeholder]').forEach(el => { el.placeholder = t(el.dataset.i18nPlaceholder as Key); });
  document.documentElement.lang = t('meta.lang');
}
