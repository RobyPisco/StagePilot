# Backline (Android)

App da palco per musicisti: scalette, chart con gli accordi sopra le parole, vista live
con scorrimento automatico. Nativa Android — Kotlin e Jetpack Compose, niente WebView.
Per uso personale e della band: non è pensata per lo store.

L'app web da cui nasce, con la specifica delle funzioni, sta in `D:\progetti\backline`.

## Cosa fa

- **Scaletta** con segnaposto (pausa, cambio chitarra), più scalette per gruppo, modifica,
  condivisione come testo.
- **Repertorio** con ricerca, **editor** del brano, **importazione** di tab "accordi sopra il testo"
  (Ultimate Guitar e simili) e di ChordPro, con anteprima.
- **Vista live**: schermo sempre acceso, trasposizione, Do Re Mi, scorrimento automatico (la velocità
  discende dalla durata del brano e si corregge per brano), solo struttura, blocco touch, note per ruolo,
  capotasto, tasti da girapagine Bluetooth (frecce, PagSu/PagGiù, Spazio, Esc).
- **Gruppi e impostazioni**: ruolo, alterazioni, corpo del testo, solo fondamentali, solo testo.
- **Backup** JSON, lo stesso formato dell'app web: esporta, importa, sostituisci o aggiungi come gruppi nuovi.

## Compilare e provare

Serve il JDK 21 di Android Studio (il Java di sistema, se più recente, non va bene per Gradle):

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n it.backline.app/.MainActivity
```

`minSdk 29` (Android 10). Provata su Fire HD 8 con LineageOS 17.1.

## Portare un brano dentro Backline

Sul tablet spesso non si riesce a copiare il testo di un tab. Le strade, dalla più comoda:

1. **Dal PC, in un comando.** Copia il tab sul PC (o salvalo in un `.txt`) e, con il tablet collegato via cavo:
   ```powershell
   .\tools\invia-brano.ps1                  # prende il testo dagli appunti
   .\tools\invia-brano.ps1 -File brano.txt  # oppure da un file
   ```
   Backline si apre sull'importazione, con l'anteprima. Si tocca Continua, si controllano titolo, BPM e
   durata e si salva. La tonalità e il capotasto si leggono dalla riga "Tuning / Key / Capo" dei siti di tab.
2. **Condividi con Backline.** Da qualunque app Android che condivide testo.
3. **Apri file** nella schermata di importazione, per un `.txt` mandato sul tablet (cavo, cloud, posta).
4. **Backup JSON** dell'app web: Impostazioni → Importa.

Le righe di tablatura per chitarra (`e|---0---|`) restano com'erano, a spaziatura fissa; chi canta, con
"Solo testo", non le vede.

## Dati

Un solo file `db.json` nella memoria privata dell'app, scritto su un file temporaneo e poi rinominato.
Per leggerlo dal PC: `adb shell run-as it.backline.app cat files/db.json`.

## Cose da sapere

- I test unitari girano sulla JVM del PC. Il motore delle espressioni regolari di Android (ICU) è più
  severo: va scritto `\}` e preferito `\]`. Dopo aver toccato un'espressione regolare, provare l'app sul dispositivo.
- La logica in `core/` è portata dall'app web (`src/core`) e ne ripete i test.
- I caratteri IBM Plex (`res/font`) hanno licenza OFL.
