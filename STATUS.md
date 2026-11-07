# StagePilot — punto di ripartenza

Salvato il 7 novembre 2026. Versione Android 0.1.1, installata sul Fire HD 8; studio web locale disponibile da StagePilot.cmd.

## Stato completato

- Android nativo e studio web grafite/azzurro, separati da Backline.
- SQLite, repertorio, scalette e riordino, lettore palco, regia del set e richieste fuori scaletta.
- Prove guidate per passaggio, ruoli, obiettivi, decisioni e registro; preparazione concerti.
- Sincronizzazione esplicita PC/tablet, conflitti, backup e recupero.
- Importazione automatica aggiornata: lettura dei metadati presenti, riconoscimento del testo tramite LRCLIB e proposta di registrazione da Deezer con titolo/artista, durata, BPM, album, fonti e alternative.
- Il web conserva la bozza nella scheda del browser dopo il ricaricamento. Le risposte online non sovrascrivono le correzioni manuali. Sul tablet il riconoscimento passa attraverso lo studio PC configurato.

## Verifica dell’ultimo aggiornamento

65 test Android, 23 server e 9 web passati. Verificati riconoscimento online, campi compilati nel browser e sul tablet, conservazione delle correzioni, recupero della bozza e avvio del pacchetto Windows estratto.

## Da riprendere insieme

1. Provare l’importazione del brano reale dell’utente, incollato senza intestazioni, e controllare la versione proposta.
2. Provare altri brani italiani e inglesi, cover e versioni live; raccogliere i casi non riconosciuti.
3. Collaudare in sala leggibilità, scorrimento, click audio ed eventuale pedale Bluetooth.

Il catalogo non contiene sempre i BPM: in quel caso il campo rimane modificabile. La tonalità del proprio arrangiamento non viene sovrascritta con quella di una registrazione. Non sono ancora inclusi importazione diretta da URL Ultimate Guitar, registrazioni audio, PDF, MIDI o collaborazione cloud.

## Ripartenza su Windows

Aprire StagePilot.cmd nella cartella del progetto e poi Importa nello studio web. Se nel browser è ancora aperta la vecchia pagina, copiare il testo prima di ricaricarla: solo la nuova versione conserva le bozze.

Repository privato: https://github.com/RobyPisco/StagePilot
Il codice dell’importatore è nel commit 569e0cd. Dati runtime e codice di collegamento sono esclusi da Git. Il backup JSON della libreria PC è salvato separatamente tra i file consegnati, senza il codice di collegamento.
