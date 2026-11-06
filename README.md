# StagePilot

Repertorio, prove e concerti. **Android nativo sul palco, studio web per preparare tutto dal PC.**

Progetto derivato da Backline, con identità e application ID separati: `it.stagepilot.app`.

## Prima revisione: le interfacce

La prima consegna è una **proposta grafica interattiva**, da valutare prima di portare il nuovo design nelle schermate native.

Su Windows avviare `StagePilot-preview.cmd` (serve Python 3) oppure aprire direttamente `preview/index.html` nel browser.

Nell'anteprima si può passare tra **Android** e **Studio web** e provare Oggi, Repertorio, Importazione, Scalette, Prove e Palco. Si possono cercare e filtrare i brani, modificare i promemoria, provare la pulizia di un testo con accordi, riordinare la scaletta, avviare il cronometro delle prove e vedere la trasposizione.

**Sono dati dimostrativi.** Le modifiche dell'anteprima si azzerano ricaricando; il collegamento Android–PC e lo scorrimento sono rappresentazioni dell'interfaccia, non funzioni già collegate alla nuova app.

Dettagli della proposta e limiti: [DESIGN.md](DESIGN.md).

## Base Android

`app/` contiene la base Kotlin / Jetpack Compose derivata da Backline. Il nuovo modello dati aggiunge preparazione dei brani, eventi e prove; è presente una prima persistenza SQLite transazionale. La grafica dell'APK preliminare è ancora quella della base, non quella della proposta.

Compilazione Windows con JDK 21 e Android SDK 36:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' # verificare che sia JDK 21
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
New-Item -ItemType Directory -Force .tmp | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$((Resolve-Path .tmp).Path)"
.\gradlew.bat testDebugUnitTest assembleDebug
```

La directory corta per i socket risolve l'errore `Unable to establish loopback connection` riscontrato sul PC di sviluppo. L'APK preliminare viene generato in `app/build/outputs/apk/debug/app-debug.apk`.

## Sviluppo successivo

1. Valutare il design Android e web.
2. Portare le schermate approvate in Compose e completare lo studio web.
3. Completare l'importazione dei backup Backline e la gestione di brani/scalette.
4. Completare scambio dati, gestione dei conflitti, backup e recupero.
5. Provare l'intero flusso su telefono e tablet reali, offline e con rete interrotta.

`studio/library.py` e `web/` sono fondamenta preliminari, non ancora un servizio web avviabile. `tools/pagina-pc/` conserva il precedente strumento PC di Backline come riferimento.

## Verifiche

La base Android rinominata compila e supera i **56 test JVM esistenti**. L'anteprima è stata verificata nel browser per navigazione, importazione dimostrativa, riordino e trasposizione; sono stati controllati i layout mobile e desktop. Le nuove funzioni di database/sincronizzazione richiedono ancora test dedicati e sul dispositivo.
