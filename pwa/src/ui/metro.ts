import { $ } from './dom';
import { S } from './state';

/* Metronomo visivo (+ click audio opzionale via Web Audio API).
   Sul palco il lampeggio discreto del chip BPM serve anche quando il click
   in cuffia non c'è; il suono resta spento di default. */
let timer: ReturnType<typeof setInterval> | 0 = 0;
let pulseT: ReturnType<typeof setTimeout> | 0 = 0;
let running = false;
let beat = 0;
let ctx: AudioContext | null = null;

function clickSound(accent: boolean): void {
  if (!S.db.opt.metroSound) return;
  try {
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;
    if (!ctx) ctx = new AudioCtx();
    if (ctx.state === 'suspended') void ctx.resume();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'triangle';
    osc.frequency.value = accent ? 1100 : 760;
    const now = ctx.currentTime;
    gain.gain.setValueAtTime(accent ? 0.22 : 0.14, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.045);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now);
    osc.stop(now + 0.05);
  } catch { /* audio bloccato o non disponibile: resta il visivo */ }
}

function pulse(): void {
  const el = $('#lbpm');
  const accent = beat % 4 === 0;
  beat++;
  el.classList.add('beat');
  clearTimeout(pulseT);
  pulseT = setTimeout(() => el.classList.remove('beat'), 110);
  clickSound(accent);
}

export const metroOn = (): boolean => running;

export function stopMetro(): void {
  running = false;
  clearInterval(timer);
  clearTimeout(pulseT);
  timer = 0;
  pulseT = 0;
  beat = 0;
  const el = $('#lbpm');
  el.classList.remove('metro', 'beat');
}

export function startMetro(bpm: number): void {
  stopMetro();
  if (!Number.isFinite(bpm) || bpm < 30 || bpm > 300) return;
  running = true;
  $('#lbpm').classList.add('metro');
  pulse();
  timer = setInterval(pulse, Math.round(60000 / bpm));
}

export function toggleMetro(bpm: number | ''): void {
  if (running) return stopMetro();
  if (typeof bpm === 'number' && bpm >= 30 && bpm <= 300) startMetro(bpm);
}
