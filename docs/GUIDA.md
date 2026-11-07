# StagePilot — guida pratica

Versione 0.2.0 · Testi, accordi, prove e concerti. Android e studio web locale.

## Da dove comincio?

Puoi usare il tablet da solo per leggere e preparare i brani. Il PC rende più comodo incollare testi, costruire scalette e preparare il dossier di una serata. Band Live collega direttamente i tablet: il PC non è necessario.

1. Installa StagePilot sul tablet.
2. Prova un brano dimostrativo, poi importa il tuo primo brano.
3. Aggiungilo a una scaletta e apri Vai sul palco.
4. Se usi anche il PC, collega lo studio web e premi Invia al tablet dopo le modifiche.

## Installare Android

Serve Android 10 o una versione successiva. Apri il file StagePilot-0.2.0-debug.apk sul dispositivo e segui la procedura di installazione. Android può chiederti di autorizzare l'installazione dall'app con cui hai aperto il file: concedi l'autorizzazione solo per il pacchetto StagePilot che intendi installare.

Per aggiornare, installa il nuovo APK sopra la versione precedente, senza disinstallarla. Perché l'aggiornamento conservi i dati, il pacchetto deve avere la stessa firma. Prima di cambiare dispositivo o disinstallare, esporta un backup.

Il pacchetto attuale è una versione di prova da installare manualmente. Non è un'app pubblicata sul Play Store.

Al primo avvio trovi dati dimostrativi. Tocca il nome della band o l'ingranaggio per rinominare il gruppo e scegliere il tuo ruolo. I dati già presenti su un tablet aggiornato vengono conservati.

## Aprire lo studio web su Windows

Lo studio web è facoltativo e gira sul tuo PC. Non richiede un account.

1. Installa Python 3.10 o successivo dal [sito ufficiale Python](https://www.python.org/downloads/windows/), includendo il launcher Python.
2. Estrai StagePilot-Studio-Windows.zip in una cartella: non avviare il programma dall'interno dello ZIP.
3. Apri StagePilot.cmd. Si apre lo studio nel browser del PC, all'indirizzo http://127.0.0.1:8765.
4. Lascia aperta la finestra di StagePilot mentre usi lo studio o trasferisci i dati.

Il pacchetto contiene già la pagina compilata: per usarlo non servono Node.js o Android Studio. Alla chiusura del programma i dati restano salvati nella cartella studio/data. Per spostare lo studio, conserva l'intera cartella oppure esporta il backup.

## Collegare PC e tablet

PC e tablet devono essere sulla stessa rete Wi-Fi. L'indirizzo 127.0.0.1 indica il dispositivo su cui lo stai usando: sul tablet devi inserire l'indirizzo del PC mostrato dallo studio.

1. Sul web premi Collega Android, oppure Come collegarlo nel riquadro di trasferimento.
2. Sul tablet apri nome della band/ingranaggio → Studio web.
3. Copia l'indirizzo del PC e il codice di sei cifre mostrati nel browser.
4. Torna a Oggi sul tablet.
5. Dopo aver salvato brani o scalette sul web, premi il grande pulsante Invia al tablet in alto. Attendi Tablet aggiornato.

In alternativa premi Oggi → Sincronizza con il PC sul tablet. Il trasferimento include repertorio, scalette, prove e dossier dei concerti. Le modifiche indipendenti vengono unite. Quando la stessa informazione cambia da entrambe le parti, StagePilot chiede quale versione tenere: leggi il confronto prima di confermare.

Durante l'uso sul palco o una modifica, il trasferimento può attendere: torna a Oggi per completarlo. Il pulsante Invio non significa che il tablet abbia già ricevuto: controlla la conferma finale.

## Importare testo e accordi

Sul web apri Repertorio → Importa. Incolla il testo con gli accordi sopra le parole, oppure apri un file TXT o ChordPro. Dopo una breve pausa parte la preparazione; puoi anche premere Pulisci e riconosci.

StagePilot elimina le righe di servizio riconosciute e prepara sezioni, testo e accordi. Legge i metadati presenti nel testo e, quando mancano, cerca una corrispondenza online con LRCLIB e Deezer. Controlla titolo, artista, album e registrazione proposta: una versione live o una cover può avere durata e tempo differenti.

- Titolo e artista possono essere corretti a mano. Cerca di nuovo usa anche queste correzioni.
- BPM e durata sono valori della registrazione proposta, non una misura della tua esecuzione. Se il catalogo non ha un dato, puoi aggiungerlo.
- La tonalità del tuo arrangiamento resta separata dai dati della registrazione.
- Mantieni tablatura per chitarra conserva le righe di tablatura quando ti servono.
- Controlla sempre l'anteprima prima di aggiungere il brano al repertorio.

La ricerca richiede Internet. Sul tablet usa lo studio PC configurato come collegamento al servizio; la pulizia e la compilazione manuale funzionano anche offline. Non c'è ancora importazione diretta da un URL Ultimate Guitar: incolla il testo che vuoi preparare.

La bozza dell'importatore web resta nella stessa scheda del browser anche dopo un ricaricamento. Salva comunque il brano prima di chiudere il lavoro.

## Costruire una scaletta

In Scalette scegli una serata o creane una nuova. Aggiungi i brani dal repertorio e riordinali con i comandi disponibili; sul web puoi anche trascinarli. Puoi inserire pause o cambi di strumento con una durata.

Il tempo totale usa le durate inserite, comprese le pause. Correggi le durate se la tua versione è più corta o più lunga della registrazione. Un brano nel repertorio non entra automaticamente in tutte le scalette: aggiungilo a quella che vuoi suonare.

Prima del concerto verifica il nome della scaletta e l'ordine completo, quindi trasferisci gli aggiornamenti ai dispositivi interessati.

## Usare il palco

Apri Vai sul palco o un brano della scaletta. Il lettore usa i dati salvati sul tablet: per leggere non servono PC o Internet.

- Play avvia lo scorrimento del testo. Non riproduce una registrazione della canzone.
- Se il brano ha un'attesa iniziale, compare il conto alla rovescia. Parti subito salta quell'attesa.
- Pausa ferma lo scorrimento; Stop lo ferma e torna all'inizio.
- Opzioni permette di cambiare dimensione, notazione, trasposizione e velocità di scorrimento.
- Se tutto il chart entra nello schermo, non c'è nulla da scorrere. Aumenta il testo se preferisci una lettura più grande.
- Regia permette di scegliere direttamente un brano della scaletta. Nella lettura normale puoi anche cercare una richiesta fuori scaletta.
- Sezioni salta a una sezione nominata del chart, ad esempio Strofa o Ritornello.
- Blocca protegge il chart dai tocchi accidentali. I comandi restano disponibili.

Il metronomo è un click separato: quando abilitato nelle impostazioni, si attiva dall'indicatore del battito nel lettore. Prova click, scorrimento ed eventuale girapagine Bluetooth prima della serata.

## Band Live senza PC

Tutti i tablet devono avere una versione compatibile di StagePilot ed essere collegati alla stessa rete locale. Non serve accesso a Internet. Lascia StagePilot aperto sui dispositivi durante la sessione.

### Sul tablet capofila

1. Da Oggi apri Band Live · collega i tablet.
2. Scegli la scaletta e premi Apri sessione come capofila.
3. Comunica agli altri musicisti l'indirizzo e il codice di sei cifre visualizzati.
4. Premi Apri il palco e guida.

### Sugli altri tablet

1. Apri Band Live · collega i tablet da Oggi.
2. Inserisci indirizzo del capofila e codice della sessione.
3. Premi Entra nella band, poi Apri il palco.
4. Controlla che compaia Segui il capofila.

Il capofila guida il cambio di brano, i salti alle sezioni e i comandi Play, Pausa e Stop. Ogni lettore mantiene le proprie impostazioni di visualizzazione. La sessione porta una copia della scaletta al lettore: non sostituisce il repertorio personale del musicista.

Lettura libera permette di consultare il chart e usare i comandi localmente. Segui capofila riattiva il collegamento ai comandi della band. Anche una navigazione locale può passare alla lettura libera.

Se la rete si interrompe, il chart già ricevuto rimane disponibile e StagePilot prova a ricollegarsi alla stessa sessione. Puoi anche riaprire l'ultima sessione offline dal menu Band Live. Se il capofila chiude e ricrea la sessione, rientra con il nuovo codice.

La scaletta è fissata quando il capofila apre la sessione. Per cambiarne il contenuto, termina la sessione, aggiorna la scaletta e aprine una nuova. Band Live sincronizza comandi di lettura: non è un sistema di sincronizzazione audio o di click di precisione.

## Preparare il dossier del concerto

In Concerti crea la serata, collega la scaletta e apri Dossier del concerto. Sul tablet il pulsante si chiama Dossier.

- La giornata: indirizzo, arrivo e scarico, soundcheck, apertura porte, inizio e fine prevista.
- Contatti utili: nomi, ruoli e telefoni dei referenti del locale e dell'audio.
- Da portare e da fare: attività, attrezzatura, responsabile e stato completato.
- Disposizione sul palco: musicisti e strumenti, con il fondo palco in alto e il pubblico in basso.
- Ingressi del mixer: canale, sorgente, microfono o DI e note tecniche.

Premi Modifica dossier, completa le informazioni e salva. Sul web puoi trascinare i musicisti o spostarli con le frecce della tastiera. Sul tablet scegli posizione e profondità dai comandi. Puoi spuntare la checklist anche senza aprire la modifica completa.

Stampa / PDF nel browser apre la stampa del dossier: scegli una stampante o Salva come PDF. Dopo il trasferimento al tablet il dossier si consulta anche senza rete. Le modifiche fatte offline restano sul dispositivo finché non sincronizzi.

Se arriva una modifica durante la preparazione di una bozza, StagePilot evita di sovrascriverla alla cieca: conserva la bozza e chiede un confronto.

## Preparare una prova

In Prove aggiungi un passaggio: scegli brano e sezione, scrivi l'obiettivo e indica il ruolo responsabile. Apri il passaggio mostra solo quella sezione, senza cambiare il chart completo.

Annota la decisione della band e segna il passaggio risolto quando il risultato è raggiunto. Il timer della sessione permette di conservare un riepilogo del lavoro nel registro.

## Backup e cambio dispositivo

Esporta regolarmente il backup JSON: sul tablet da Impostazioni → Backup → Esporta; sul web dal pulsante Esporta backup nella barra superiore. Il file contiene la libreria: conservalo in un posto sicuro e riconoscibile.

Per ripristinare o migrare, importa il backup e controlla gruppi e scalette prima del concerto. Puoi importare il formato compatibile di Backline; StagePilot rimane un'app separata.

Disinstallare l'app rimuove la memoria privata. Prima di cambiare telefono, firma dell'APK o cartella dello studio, prepara un backup verificato. Annulla ultima modifica può recuperare lo stato precedente nelle operazioni supportate, ma non sostituisce una copia esportata.

## Problemi comuni

### Il tablet non riceve dal PC

Controlla che lo studio sia aperto, che indirizzo e codice corrispondano a quelli attuali, e che i due dispositivi siano sulla stessa Wi-Fi. Torna a Oggi e riprova Invia al tablet. Se compare un confronto, completalo sul tablet. Una rete ospiti può impedire ai dispositivi di comunicare fra loro.

### Play cambia in Pausa ma non vedo movimento

Leggi il messaggio sopra i comandi: potrebbe esserci l'attesa iniziale, oppure il chart potrebbe essere già tutto visibile. Prova Parti subito o aumenta la dimensione del testo. Verifica anche di non essere già alla fine del brano.

### Band Live non entra nella sessione

Usa l'indirizzo del tablet capofila, non quello del PC. Controlla il nuovo codice se la sessione è stata riaperta. Tieni StagePilot aperto sul capofila. Se la rete isola i dispositivi, usa un'altra rete locale che consenta il collegamento fra tablet.

### Titolo, artista o BPM non sono riconosciuti

Controlla la connessione Internet del PC. Scrivi titolo e artista a mano e premi Cerca di nuovo. Verifica le versioni alternative. Se il dato non è nel catalogo, completalo manualmente: lo scorrimento può usare anche la durata o una velocità fissa.

## Condividere StagePilot

Per far provare l'app, distribuisci l'APK aggiornato e questa guida. Per lo studio PC distribuisci lo ZIP già compilato. Non includere i tuoi backup, la cartella studio/data o il codice di collegamento personale.

Questa è una versione di prova: fate una prova completa in sala prima di usarla in concerto, soprattutto con più tablet e con gli accessori della band. Il comportamento può dipendere dalla rete e dai dispositivi.

Buona musica!
