# StagePilot

**Prepara sul PC, prova con un obiettivo, suona dal tablet.** Android nativo e studio web locale, con librerie SQLite autonome e sincronizzazione esplicita. Tema grafite e azzurro, app separata da Backline (`it.stagepilot.app`).

## Funzioni del primo MVP

- **Importazione automatica:** incolla testo e accordi da Ultimate Guitar, oppure apri un file ChordPro/testo. La pulizia legge i metadati già presenti; il riconoscimento confronta le frasi con LRCLIB e propone titolo/artista, durata e BPM della registrazione Deezer. Mostra album, fonti e alternative, senza modificare le parole, gli accordi o le correzioni manuali. Se il catalogo non ha il BPM, lo dichiara e lascia il campo modificabile. Il web conserva la bozza nella scheda del browser anche dopo un ricaricamento. Su Android il riconoscimento online usa lo studio PC collegato nelle impostazioni; importazione e lettura restano disponibili offline.
- **Repertorio:** creazione, modifica, ricerca, tag, BPM, tonalità, durata, note condivise e per ruolo, stato di preparazione e appunto di prova.
- **Scalette:** più scalette per gruppo, duplicazione, riordino, pause e cambi strumento, somma delle durate e condivisione come file o elenco su Android.
- **Prova guidata:** agenda di passaggi specifici, scelta della sezione del brano, obiettivo, ruolo responsabile, decisioni condivise, stato da risolvere/risolto e filtro personale. Apertura del solo passaggio nel lettore, senza modificare il chart originale. Sessioni cronometrate con riepilogo nel registro.
- **Concerti:** data, orario, luogo, scaletta e appunti. Controlli sui brani ancora da preparare, sui passaggi aperti e sulle durate mancanti. Accesso alla prova filtrata per la scaletta della serata.
- **Palco Android:** Play avvia lo scorrimento del testo; l’attesa iniziale del brano mostra un conto alla rovescia e si può saltare con **Parti subito**. Se tutto il brano entra nello schermo viene indicato. Schermo acceso, trasposizione, notazione inglese/italiana, scorrimento, solo testo/struttura, note per ruolo, capotasto, blocco del chart, prossimo brano e metronomo. Regia del set con salto diretto ai brani, tempo rimanente stimato e richiesta fuori scaletta dal repertorio. Supporto ai tasti di girapagine Bluetooth.
- **Dati:** SQLite sul PC e sul tablet, backup JSON compatibili con Backline, copie locali Android, cronologia del PC con annullamento. La sincronizzazione unisce campi indipendenti, chiede una scelta per i conflitti e rifiuta conferme riferite a revisioni superate. Le preferenze personali di lettura non vengono imposte agli altri dispositivi.

## Avvio rapido su Windows

Per compilare dal repository servono **Node.js 24, Python 3, JDK 21 e Android SDK 36**. Sul PC usato per lo sviluppo il JDK 21 è quello incluso in Android Studio.

1. Esegui `Build-StagePilot.cmd`, oppure usa i comandi sotto.
2. Esegui `StagePilot.cmd` per aprire lo studio web.
3. Apri **Collega Android** nel browser: mostra l'indirizzo e un codice di sei cifre.
4. Al primo collegamento, sul tablet apri nome della band/ingranaggio → **Studio web** e inserisci indirizzo e codice. Poi premi il pulsante azzurro **Invia al tablet**, ben visibile nella barra superiore dello studio web: il tablet aperto su Oggi riceve la richiesta e il web mostra la conferma. È disponibile anche **Oggi → Sincronizza con il PC** sul tablet.

PC e tablet devono stare sulla stessa Wi-Fi. Il codice è creato sul PC al primo avvio e non è incluso nel repository. Lo studio web è locale: non servono account o abbonamenti. Sul palco Android legge la propria libreria senza dipendere dal PC.

```powershell
cd D:\progetti\StagePilot\web
npm ci
npm test
npm run build
cd ..
python studio\server.py
```

L'APK di prova è in `app/build/outputs/apk/debug/app-debug.apk`. Si installa accanto a Backline:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
New-Item -ItemType Directory -Force .tmp | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$((Resolve-Path .tmp).Path)"
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Per le sole prove via cavo si può usare `adb reverse tcp:8765 tcp:8765` e inserire `127.0.0.1:8765` nell'app. Il collegamento Wi-Fi è stato verificato direttamente sul Fire HD 8 e configurato con l'indirizzo del PC.

## Dati e recupero

- PC: `studio/data/stagepilot.sqlite`, escluso da Git; conserva fino a 40 revisioni precedenti.
- Android: `stagepilot.sqlite` nella memoria privata dell'app, con una cronologia locale; **Impostazioni → Backup** esporta/importa il formato JSON.
- La scrittura della libreria e della base di sincronizzazione avviene nella stessa transazione SQLite.
- Per migrare il repertorio di Backline, esporta il suo backup e importalo in StagePilot. Il progetto non modifica i dati dell'altra app.
- Un backup esportato è il modo più semplice per trasferire o conservare l'intera libreria. Disinstallare un'app Android rimuove la sua memoria privata.

## Verifiche

- JVM Android: test del motore musicale, importazione, normalizzazione, pacchetti, scalette e preparazione della serata.
- Web: test del convertitore distribuito, cancellazione con rimozione dei riferimenti, tempi/trasposizione e confronto delle bozze.
- Server: test SQLite, riapertura, annullamento, revisioni, autenticazione LAN, conflitti, passaggi di prova e richieste HTTP. Il riconoscimento ha test su testi non corrispondenti, cover/live, BPM mancanti, fonte della registrazione, cache, rete assente e limiti dei servizi.
- **Fire HD 8 / Android 10:** due test strumentati su conversione ICU e transazioni SQLite, incluso errore a metà scrittura. Provati anche importazione dal browser, persistenza dopo ricaricamento, trasferimento USB e Wi-Fi, conflitto con revisione superata e apertura del singolo ritornello sul tablet.

```powershell
cd studio
python -m unittest discover -v
```

Per i test sul dispositivo installare anche `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, poi:

```powershell
adb shell am instrument -r -w it.stagepilot.app.test/it.stagepilot.app.SmokeInstrumentation
```

## Piano da venerdì a domenica

- **Venerdì sera:** modello dati, progetto Android separato, interfaccia condivisa e SQLite.
- **Sabato mattina:** importazione, repertorio e scalette; sabato pomeriggio lettore da palco e studio web.
- **Sabato sera:** sincronizzazione, backup e test sul dispositivo.
- **Domenica mattina:** prova guidata, preparazione concerti e regia live; domenica pomeriggio prova completa della band, correzioni di leggibilità e confezionamento.

## Confini di questo MVP

La sincronizzazione è avviata esplicitamente da **Invia al tablet** sul web o da **Sincronizza con il PC** sul tablet; non è collaborazione in tempo reale. Per ricevere le richieste web il tablet deve avere StagePilot aperto e configurato, sulla stessa Wi-Fi del PC. Durante il palco o una modifica la richiesta aspetta il ritorno a Oggi; eventuali conflitti richiedono una scelta sul tablet. Le stime live dipendono dalle durate inserite. Non sono ancora inclusi PDF, registrazioni audio, multitraccia, MIDI, controllo simultaneo di più tablet, account/cloud e ricerca/importazione diretta da URL Ultimate Guitar. Il click audio e i pedali reali richiedono una prova musicale dedicata; i relativi controlli già esistono.

`preview/` conserva la proposta grafica approvata. L'app funzionante è in `app/` e `web/`; `tools/pagina-pc/` resta come riferimento dello strumento precedente. La conversione browser condivisa deriva da Backline ed è distribuita con il suo codice nel progetto.

Ispirazioni di prodotto: [MobileSheets](https://www.zubersoft.com/mobilesheets/), [BandHelper Practice](https://www.bandhelper.com/tutorials/practicing.html), [SongbookPro Manager](https://songbook-pro.com/docs/manager/) e [ChordPro](https://www.chordpro.org/chordpro/chordpro-directives/).

Il riconoscimento usa le API pubbliche di [LRCLIB](https://lrclib.net/docs) e [Deezer](https://developers.deezer.com/api/track). Le frasi candidate vengono cercate online; il testo completo recuperato per il confronto rimane in memoria temporanea e non sostituisce quello incollato. Le risposte positive sono memorizzate in una cache limitata in memoria per un’ora.
