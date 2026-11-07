# StagePilot — punto di ripartenza

Salvato il 7 novembre 2026. Versione Android 0.2.0, installata sul Fire HD 8; studio web locale disponibile da StagePilot.cmd.

## Stato completato

- Android nativo e studio web grafite/azzurro, separati da Backline.
- SQLite, repertorio, scalette e riordino, lettore palco, regia del set e richieste fuori scaletta.
- Prove guidate per passaggio, ruoli, obiettivi, decisioni e registro; preparazione concerti.
- Sincronizzazione esplicita PC/tablet, conflitti, backup e recupero.
- Importazione automatica aggiornata: lettura dei metadati presenti, riconoscimento del testo tramite LRCLIB e proposta di registrazione da Deezer con titolo/artista, durata, BPM, album, fonti e alternative.
- Il web conserva la bozza nella scheda del browser dopo il ricaricamento. Le risposte online non sovrascrivono le correzioni manuali. Sul tablet il riconoscimento passa attraverso lo studio PC configurato.

## Verifica dell’ultimo aggiornamento

65 test Android, 32 server e 9 web passati. Verificati riconoscimento online, campi compilati nel browser e sul tablet, conservazione delle correzioni, recupero della bozza e avvio del pacchetto Windows estratto.

## Da riprendere insieme

1. Provare l’importazione del brano reale dell’utente, incollato senza intestazioni, e controllare la versione proposta.
2. Provare altri brani italiani e inglesi, cover e versioni live; raccogliere i casi non riconosciuti.
3. Collaudare in sala leggibilità, scorrimento, click audio ed eventuale pedale Bluetooth.

Il catalogo non contiene sempre i BPM: in quel caso il campo rimane modificabile. La tonalità del proprio arrangiamento non viene sovrascritta con quella di una registrazione. Non sono ancora inclusi importazione diretta da URL Ultimate Guitar, registrazioni audio, PDF, MIDI o collaborazione cloud.

## Ripartenza su Windows

Aprire StagePilot.cmd nella cartella del progetto e poi Importa nello studio web. Se nel browser è ancora aperta la vecchia pagina, copiare il testo prima di ricaricarla: solo la nuova versione conserva le bozze.

Repository privato: https://github.com/RobyPisco/StagePilot
Il codice dell’importatore è nel commit 569e0cd. Dati runtime e codice di collegamento sono esclusi da Git. Il backup JSON della libreria PC è salvato separatamente tra i file consegnati, senza il codice di collegamento.

## Correzione sincronizzazione e Play

Aggiunto il pulsante Sincronizza con il PC in Oggi. Play mostra il conto alla rovescia dell’attesa iniziale (Ultimo Giro: 8 secondi) con Parti subito; segnala quando il chart è interamente visibile. Lo scorrimento riprende dopo un trascinamento manuale e Play a fine chart riparte dall’inizio.

## Invio dal web e verifica finale

Pulsante azzurro Invia al tablet nella barra superiore web. Richieste autenticate e temporanee, conferma ricevuta dal tablet, attesa durante palco/modifica, gestione conflitti. La libreria continua a usare il normale merge SQLite. Provato dal pulsante reale del browser sul Fire HD 8 via Wi-Fi: quinto brano e scaletta completa presenti. Verificati conto alla rovescia e Parti subito su Ultimo Giro, con movimento del testo misurato sul dispositivo. Versione Android installata: 0.1.2.

### Nota per il prossimo collaudo

Nel primo invio osservato il nuovo brano era nel repertorio ma mancava la sua ultima voce nella scaletta. La voce è stata recuperata dalla cronologia del PC e il secondo invio ha trasferito correttamente tutte le 6 voci (5 brani e una pausa). Il caso non si riproduce nel merge isolato: aggiunto un test di tre sincronizzazioni consecutive con normalizzazione Android dei campi opzionali. Ricontrollare domani con un nuovo brano aggiunto dal web prima di considerare concluso il collaudo della sincronizzazione. Backup finale completo disponibile tra i file consegnati.

## Identità StagePilot — icona 0.1.3

Creata e integrata l’icona originale S azzurra con Play nello spazio negativo su fondo grafite. Master e prompt in branding/. Launcher Android adattivo, testata dell’app, logo web e favicon aggiornati; asset locali e PNG esportabile. Compilazioni Android e web riuscite; versione 0.1.3 installata sul Fire HD 8 e icona verificata nella testata dell’app. Asset web e favicon serviti con risposta HTTP 200. Nessuna modifica ai dati musicali o alla sincronizzazione in questo aggiornamento.


## Versione 0.2.0 — Band Live, dossier e guida

- Band Live Android autonomo, senza PC: un tablet espone la scaletta sulla Wi-Fi con codice temporaneo, gli altri seguono cambio brano, Play/Pausa/Stop e salti alle sezioni. Snapshot della sessione separato dalla libreria personale, lettura libera, cache offline e riconnessione. La sessione va tenuta aperta sul capofila.
- Dossier concerto offline su Android e web: orari, indirizzo, contatti, checklist assegnate, schema palco e lista ingressi. Editor web con recupero bozza, merge per campo e stampa/PDF. Modifiche concorrenti non sovrascritte in silenzio.
- Invio dal web protetto contro una cancellazione o un riordino presente solo sul tablet: richiede confronto prima di riscrivere il PC. Riprodotta e coperta dai test questa categoria di rischio; non è dimostrato che fosse l'esatta causa dell'episodio storico.
- Guida pratica italiana inclusa nel menu Oggi Android, nella barra laterale web e in HTML/Markdown nel kit. Installazione, trasferimenti, importazione, palco, Band Live, dossier, backup e problemi comuni.

Verifiche concluse: 70 test JVM Android, 37 Python, 12 web e 4 strumentati sul Fire HD 8. Browser: dossier salvato/riletto, orari, checklist, posizione con tastiera, ingresso mixer, recupero bozza dopo reload, guida e ancore. Tablet reale: stessa scheda concerto ricevuta via sync, orari/contatto/checklist/schema/ingresso verificati; guida HTML offline visualizzata. Capofila reale: cambio brano, Play, Parti subito e salto Ritornello rilevati da client Wi-Fi. Test nativi capofila/seguace con cache, perdita e ripristino del collegamento passati sullo stesso Fire.

Resta da collaudare Band Live con due tablet fisici distinti in sala e da valutare leggibilità/click/pedale con la band. Non promette sincronizzazione audio o metronomo in fase.

Libreria reale conservata: 5 brani, scalette Serata tipo (6 voci) e Acustico (1). Dossier compilato solo nel concerto esplicitamente dimostrativo, con indicazioni “da confermare” e nessun numero telefonico inventato. Sessione di collaudo Band Live terminata; tablet aggiornato e tornato a Oggi. Backup personale separato dal kit, escluso da Git.

Consegna: StagePilot-0.2.0-kit.zip con APK debug, studio Windows precompilato, guida e avvisi dipendenze. Per una futura pubblicazione stabile preparare una firma release e conservarla. Repository ancora privato: nessuna pubblicazione pubblica effettuata.
