# StagePilot — proposta grafica

Prima revisione: 6 novembre 2026. Nome, identità e installazione separati da Backline.

## Provare le interfacce

Aprire `StagePilot-preview.cmd` su Windows (richiede Python 3), poi visitare http://127.0.0.1:8795.
In alternativa, aprire `preview/index.html` direttamente nel browser.
Usare Android / Studio web per cambiare ambiente. L'anteprima contiene dati dimostrativi: non è ancora l'applicazione connessa al database.

Sono navigabili Oggi, Repertorio, Scalette, Prove, Importazione e Palco. È possibile cercare e filtrare i brani, cambiare gli appunti, spostare le righe del set, provare una conversione di testo e accordi, segnare i brani pronti e vedere la trasposizione. Le modifiche dimostrative non vengono inviate a dispositivi o servizi esterni e si azzerano ricaricando.

## Direzione

- Grafite quasi nero, ambra per azioni e accordi, lilla per preparazione, verde per stato pronto. Stato sempre accompagnato da testo.
- Android: una schermata per compito, quattro voci principali, modalità palco separata.
- Web: barra laterale stabile, più spazio per incollare e verificare la conversione.
- Importazione in tre passaggi: incolla, controlla, salva. Non riscrivere parole, inventare accordi o trattare una tonalità stimata come certa.
- Palco: lettura prima di tutto, controlli essenziali, sezioni riconoscibili, prossimo brano visibile.
- La schermata iniziale deve aiutare a iniziare a suonare o preparare la prossima prova.

## Stato di implementazione

Il repository contiene la base nativa Kotlin/Compose derivata da Backline, ora con application ID `it.stagepilot.app`, e le prime fondamenta per SQLite e sincronizzazione. Il restyling mostrato nell'anteprima non è ancora portato nelle schermate Compose. La web app collegata, la sincronizzazione e la cronologia richiedono implementazione e verifiche ulteriori. Non distribuire l'APK preliminare come versione finale.

Le risorse della visualizzazione e i caratteri possono richiedere Internet per l'anteprima. Nell'app Android finale i caratteri e le funzioni da palco saranno locali.
