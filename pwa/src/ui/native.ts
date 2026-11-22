import type { Theme } from '../core/model';

/* Versione PWA pura (iPadOS / iOS / browser): nessuna dipendenza Capacitor. */
export const isNative = (): boolean => false;
export async function nativeKeepAwake(_on: boolean): Promise<boolean> { return false; }
export async function syncStatusBar(_theme: Theme): Promise<void> {}
export function bindNativeBack(_onBack: () => boolean): void {}
