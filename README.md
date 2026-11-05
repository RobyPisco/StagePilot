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

## Versione e APK da dare agli altri

La versione (ora **0.2.0**, build 2) si cambia in `app/build.gradle.kts` (`versionName`, `versionCode`) e si legge
in fondo a Impostazioni. Per salire di versione si alza sempre anche `versionCode`: serve ad Android per
aggiornare un'app già installata senza perdere i dati.

APK di rilascio (piccolo, ridotto con R8, firmato con la chiave di debug di questo PC):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat assembleRelease        # app\build\outputs\apk\release\app-release.apk
```

Una copia da distribuire sta in `dist\Backline-0.2.0.apk` (non è nel repo). Per installarla: consentire
«installa da fonti sconosciute» e aprire il file. Chi ha già una versione firmata con un'altra chiave deve prima
disinstallarla (e perde i dati: meglio un backup prima).

## Compilare e provare

Serve un JDK 21 (qui Eclipse Adoptium; il Java di sistema e quello di Android Studio, ora il 25, non vanno bene per Gradle):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat testDebugUnitTest assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n it.backline.app/.MainActivity
```

`minSdk 29` (Android 10). Provata su Fire HD 8 con LineageOS 17.1.

## Dare la scaletta agli altri

**Condividi** (nella scaletta) offre due strade:
- **File Backline**: un `.json` con i brani, gli accordi, le note e l'ordine (pause comprese). Si manda da qualunque
  app: WhatsApp, Telegram, posta, Quick Share. Chi lo riceve lo apre con Backline (dalla condivisione, o aprendo il
  file dal gestore di file): l'app mostra cosa contiene e chiede conferma, poi aggiunge i brani che mancano e crea
  una **scaletta nuova** accanto alle sue. I brani che ha già (stesso titolo e artista) non si duplicano; non si
  toccano trasposizione e velocità di scorrimento, che sono sue.
- **Solo elenco**: i titoli come testo, per chi non ha Backline.

## Portare un brano dentro Backline

Sul tablet spesso non si riesce a copiare il testo di un tab. Le strade, dalla più comoda:

0. **La pagina del PC** (`tools\pagina-pc\avvia.cmd`, serve Python). Si apre nel browser: incolli il tab,
   scrivi titolo, artista, BPM (c'è anche «Batti» per battere il tempo), durata, tag e nota, e vedi subito
   l'anteprima come si leggerà in live (la conversione è quella dell'app web, compilata con esbuild:
   `entry.ts` → `convert.bundle.js`). Poi due pulsanti:
   - **Invia (Wi-Fi):** il brano va in una coda sul PC. In Backline, *Impostazioni → Brani dal PC*,
     scrivi una volta sola indirizzo e codice (li mostra la pagina) e tocca Scarica; l'app poi controlla da sola
     a ogni apertura. Telefono e PC devono stare sulla stessa rete. La coda si legge solo con il codice.
   - **Via cavo (adb):** il brano arriva subito, con il telefono collegato.
   In entrambi i casi Backline chiede conferma prima di aggiungerlo.

   **Più brani insieme.** «+ Aggiungi alla lista» tiene da parte il brano e svuota il modulo; quando hai finito,
   «Invia» manda in un colpo solo tutta la lista (più quello che c'è nel modulo). La lista resta anche se
   ricarichi la pagina. Un brano già in coda non si mette due volte; uno già nel repertorio del telefono la
   pagina lo segnala, e l'app, se arriva lo stesso, lo salta. In fondo alla pagina si vede cosa il telefono ha
   già scaricato.

   **Controllo degli accordi.** Se un accordo non c'entra con la tonalità (il tab ha un errore, o la tonalità è
   sbagliata) la pagina lo elenca sopra l'anteprima. Si perdonano i prestiti comuni (bIII, bVI, bVII…).

   **Pulizia e riconoscimento automatici.** Incollando il tab così com'è da Ultimate Guitar, la pagina:
   - toglie la tablatura per chitarra (si può tenere con la casella), la testata «Tuning / Key / Capo»,
     i residui del sito («X», «Last update…») e le sezioni rimaste vuote;
   - legge la tonalità e il capotasto dalla testata, o deduce la tonalità dagli accordi;
   - riconosce la canzone: cerca su [LRCLIB](https://lrclib.net) le frasi più ripetute del testo (di solito una è
     il titolo) e conferma che il testo trovato coincida con quello incollato; a parità di testo tra cover e
     originale sceglie l'artista più noto su Deezer. Se sbaglia, ci sono le alternative e «Cerca di nuovo»
     con titolo e artista scritti a mano;
   - prende **BPM** e **durata** da [SongBPM](https://songbpm.com/) (`songbpm.py`: legge la pagina dei risultati,
    una richiesta per ricerca; la tonalità del sito non si usa, perché manca il maggiore/minore). Se il sito non
    risponde o non trova il brano, ripiega su [Deezer](https://developers.deezer.com/api); se Deezer non
     ha il BPM lo stima dall'anteprima audio di 30 secondi, con un piccolo componente opzionale
     (`miniaudio`, la pagina lo installa solo se tocchi «Installalo»). La stima è precisa di un paio di BPM,
     ma un brano lento può uscire al doppio: ci sono i tasti **½** e **×2** e **Batti**.
      Nelle righe a battute (`| D | % | % | G |`) il `%` diventa la battuta precedente, scritta per esteso.
   Fuori dal PC non esce mai il testo del brano: partono solo le frasi cercate, titolo e artista.
   I campi che scrivi a mano non vengono mai sovrascritti. Serve Internet; senza, si compila tutto a mano.
   Prove: `node tools\pagina-pc\test-pulizia.js` e `python -m unittest test_identifica` e `python test_server.py` (dalla cartella
   `tools\pagina-pc`); `node prova.js file.txt` mostra cosa viene tolto e i candidati per il titolo.
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

## Gestire il repertorio dal PC

La pagina del PC ha una seconda scheda, **Repertorio** (`http://localhost:8765/repertorio.html`). Mostra i brani e le
scalette del tablet, come stavano all'ultimo backup che il tablet ha mandato al PC (serve aver messo indirizzo e codice
in Impostazioni → Brani dal PC):

- **Correggere un brano:** titolo, artista, tonalità, BPM, durata, tag e testo ChordPro. «Cerca BPM e durata» li prende
  da SongBPM.
- **Eliminare** un brano dal repertorio (si può annullare finché non si invia).
- **Riordinare una scaletta:** trascinando le righe (o con le frecce), togliendo voci, aggiungendo brani e segnaposto
  (Pausa, Discorso…) nel punto che vuoi.
- **Inviare:** *Invia (Wi-Fi)* mette le modifiche in coda, *Via cavo (adb)* le manda subito. Con telefono e tablet
  collegati insieme, adb sceglie il dispositivo che ha Backline.

Sul tablet compare **«Modifiche dal PC»** con l'elenco di cosa cambia (le eliminazioni in rosso): non si applica niente
senza il tuo «Applica». Per prudenza una scaletta che sul tablet è cambiata dopo l'ultimo backup letto dal PC non si
sovrascrive, e un brano sparito nel frattempo si salta: l'app lo dice.

## Dati

Un solo file `db.json` nella memoria privata dell'app, scritto su un file temporaneo e poi rinominato.
Per leggerlo dal PC: `adb shell run-as it.backline.app cat files/db.json`.

**Copie di sicurezza automatiche.** Il database sparisce se disinstalli l'app o il tablet si rompe, quindi a ogni
apertura Backline:
- salva una copia al giorno (le ultime 7) in `Android/data/it.backline.app/files/backups`; dal PC, con il cavo,
  `.\tools\backup.ps1` le scarica in `backup-tablet\`;
- se «Brani dal PC» è configurato, manda una copia anche al PC (solo se il database è cambiato dall'ultima volta):
  la pagina le tiene in `tools\pagina-pc\backup\` (le ultime 10). Ripristino: Impostazioni → Importa → file
  → «Sostituisci tutto». Dall'ultima copia la pagina ricava anche i brani che hai già, per avvisarti dei doppioni.

## Cose da sapere

- I test unitari girano sulla JVM del PC. Il motore delle espressioni regolari di Android (ICU) è più
  severo: va scritto `\}` e preferito `\]`. Dopo aver toccato un'espressione regolare, provare l'app sul dispositivo.
- La logica in `core/` è portata dall'app web (`src/core`) e ne ripete i test.
- I caratteri IBM Plex (`res/font`) hanno licenza OFL.
