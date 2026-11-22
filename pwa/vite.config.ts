import { defineConfig } from 'vitest/config';
import { VitePWA } from 'vite-plugin-pwa';

/* `base: './'` perché la stessa build deve girare da una sottocartella
   (XAMPP, localhost/backline) e dentro Capacitor, che serve i file da
   un'origine sua: con percorsi assoluti si romperebbe uno dei due. */
export default defineConfig({
  base: './',
  plugins: [
    VitePWA({
      /* 'prompt' e non 'autoUpdate': la versione nuova si installa e poi
         ASPETTA. Il momento di passare lo sceglie chi suona, dall'avviso in
         fondo alla schermata — mai a metà pezzo. */
      registerType: 'prompt',
      injectRegister: false,
      manifest: false,
      includeAssets: ['manifest.webmanifest', 'icons/*.png'],
      workbox: {
        globPatterns: ['**/*.{js,css,html,woff2,png,webmanifest}'],
        navigateFallback: 'index.html'
      }
    })
  ],
  test: {
    include: ['tests/**/*.test.ts'],
    environment: 'node'
  }
});
