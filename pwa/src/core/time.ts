/* ── tempo ───────────────────────────────────────────────── */
export const secs = (d: unknown): number => {
  const m = /^(\d+):(\d{1,2})$/.exec(String(d || '').trim());
  return m ? +m[1] * 60 + +m[2] : 0;
};
export const mmss = (t: number): string =>
  Math.floor(t / 60) + ':' + String(Math.floor(t % 60)).padStart(2, '0');
export const signed = (t: number): string => (t < 0 ? '−' : '+') + mmss(Math.abs(t));

/* Tap tempo: calcola i BPM dalla media degli ultimi intervalli validi.
   Una pausa oltre 2,5 secondi azzera la sequenza. */
export function bpmFromTaps(taps: number[]): number | null {
  if (taps.length < 2) return null;
  const recent: number[] = [taps[taps.length - 1]];
  for (let i = taps.length - 2; i >= 0 && recent.length < 9; i--) {
    if (recent[0] - taps[i] > 2500) break;
    recent.unshift(taps[i]);
  }
  if (recent.length < 2) return null;
  const avgMs = (recent[recent.length - 1] - recent[0]) / (recent.length - 1);
  if (avgMs <= 0) return null;
  const bpm = Math.round(60000 / avgMs);
  return Math.max(30, Math.min(300, bpm));
}
