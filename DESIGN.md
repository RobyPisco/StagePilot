# StagePilot — proposta grafica

Prima revisione: 6 novembre 2026. Nome, identità e installazione separati da Backline.

## Provare le interfacce

Aprire `StagePilot-preview.cmd` su Windows (richiede Python 3), poi visitare http://127.0.0.1:8795.
In alternativa, aprire `preview/index.html` direttamente nel browser.
Usare Android / Studio web per cambiare ambiente. L'anteprima contiene dati dimostrativi: non è ancora l'applicazione connessa al database.

Sono navigabili Oggi, Repertorio, Scalette, Prove, Importazione e Palco. È possibile cercare e filtrare i brani, cambiare gli appunti, spostare le righe del set, provare una conversione di testo e accordi, segnare i brani pronti e vedere la trasposizione. Le modifiche dimostrative non vengono inviate a dispositivi o servizi esterni e si azzerano ricaricando.

## Direzione

- Grafite quasi nero, azzurro freddo per azioni e accordi, lilla per preparazione, verde per stato pronto. Stato sempre accompagnato da testo.
- Android: una schermata per compito, quattro voci principali, modalità palco separata.
- Web: barra laterale stabile, più spazio per incollare e verificare la conversione.
- Importazione in tre passaggi: incolla, controlla, salva. Non riscrivere parole, inventare accordi o trattare una tonalità stimata come certa.
- Palco: lettura prima di tutto, controlli essenziali, sezioni riconoscibili, prossimo brano visibile.
- La schermata iniziale deve aiutare a iniziare a suonare o preparare la prossima prova.

## Stato di implementazione — 7 novembre 2026

La direzione azzurra è stata approvata. Android e studio web ora contengono flussi reali collegati a SQLite: importazione, repertorio, scalette, prova guidata, concerti e sincronizzazione. Sul tablet sono presenti nuove schermate Oggi, Prove e Concerti; il Live ha una regia del set e richieste fuori scaletta.

La proposta in `preview/` è la revisione grafica iniziale: resta una dimostrazione e non contiene tutte le funzioni aggiunte. La versione funzionante si avvia da `StagePilot.cmd`, con istruzioni nel README.

Il progetto mantiene alcuni componenti di gestione derivati da Backline. Le differenze di prodotto sono l'agenda con obiettivi per singoli passaggi, decisioni e ruoli; la preparazione delle serate; il collegamento tra prove e concerti e la regia live.

Le risorse e i caratteri della dimostrazione possono richiedere Internet. I caratteri e il lettore Android sono locali; nello studio web i caratteri di sistema restano disponibili se la rete manca.
