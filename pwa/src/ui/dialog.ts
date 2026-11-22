import { t } from '../i18n';
import { $, $in } from './dom';

let toastT: ReturnType<typeof setTimeout> | 0 = 0;

export function toast(msg: string, isErr = false): void {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.toggle('err', isErr);
  el.classList.add('on');
  clearTimeout(toastT);
  toastT = setTimeout(() => { el.classList.remove('on'); }, isErr ? 3600 : 2400);
}

interface DialogOpts {
  msg: string;
  mode: 'confirm' | 'prompt' | 'mark';
  val1?: string;
  ph1?: string;
  val2?: string;
  ph2?: string;
  okLabel?: string;
  danger?: boolean;
}

function openDialog(opts: DialogOpts): Promise<{ ok: boolean; v1: string; v2: string }> {
  return new Promise(resolve => {
    const dlg = $('#dlg');
    const in1 = $in('#dlg-in1');
    const in2 = $in('#dlg-in2');
    const okBtn = $<HTMLButtonElement>('#dlg-ok');
    const cancelBtn = $<HTMLButtonElement>('#dlg-cancel');

    $('#dlg-msg').textContent = opts.msg;
    in1.classList.toggle('off', opts.mode === 'confirm');
    in2.classList.toggle('off', opts.mode !== 'mark');
    in1.value = opts.val1 || '';
    in1.placeholder = opts.ph1 || '';
    in2.value = opts.val2 || '';
    in2.placeholder = opts.ph2 || '';

    okBtn.textContent = opts.okLabel || t(opts.danger ? 'dlg.delete' : 'dlg.ok');
    okBtn.classList.toggle('danger', !!opts.danger);
    okBtn.classList.toggle('key', !opts.danger);
    cancelBtn.textContent = t('dlg.cancel');

    const close = (ok: boolean) => {
      dlg.classList.remove('on');
      okBtn.onclick = null;
      cancelBtn.onclick = null;
      dlg.onclick = null;
      document.removeEventListener('keydown', onKey);
      resolve({ ok, v1: in1.value, v2: in2.value });
    };

    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); close(false); }
      if (e.key === 'Enter' && (opts.mode === 'confirm' || document.activeElement === in1 || document.activeElement === in2)) {
        e.preventDefault();
        close(true);
      }
    };

    okBtn.onclick = () => close(true);
    cancelBtn.onclick = () => close(false);
    dlg.onclick = e => { if (e.target === dlg) close(false); };
    document.addEventListener('keydown', onKey);

    dlg.classList.add('on');
    if (opts.mode === 'confirm') okBtn.focus();
    else { in1.focus(); in1.select(); }
  });
}

export async function uiConfirm(msg: string, opts?: { ok?: string; danger?: boolean }): Promise<boolean> {
  const res = await openDialog({
    msg,
    mode: 'confirm',
    okLabel: opts?.ok || t(opts?.danger ? 'dlg.delete' : 'dlg.confirm'),
    danger: opts?.danger
  });
  return res.ok;
}

export async function uiPrompt(msg: string, value = '', placeholder = ''): Promise<string | null> {
  const res = await openDialog({ msg, mode: 'prompt', val1: value, ph1: placeholder });
  return res.ok ? res.v1 : null;
}

export async function uiMarkPrompt(): Promise<{ label: string; dur: string } | null> {
  const res = await openDialog({
    msg: t('mark.title'),
    mode: 'mark',
    ph1: t('mark.labelPh'),
    val2: '15:00',
    ph2: t('mark.durPh')
  });
  if (!res.ok) return null;
  const label = res.v1.trim();
  if (!label) return null;
  return { label, dur: res.v2.trim() };
}
