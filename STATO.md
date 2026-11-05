# Stato dei lavori

Aggiornato al **5 novembre 2026**. Il `README.md` dice cosa fa l'app e come si usa; qui c'è a che punto siamo,
cosa è stato deciso e cosa resta da fare.

## Dove sta

| | |
|---|---|
| Cartella | `D:\Progetti\backline-android` |
| Repo | `github.com/RobyPisco/backline-android` |
| App web (specifica) | `D:\Progetti\backline` · `github.com/RobyPisco/backline` |
| Build | JDK **21** (`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`). Il JDK di Android Studio e quello di sistema sono ora il 25: il Kotlin del progetto non lo legge e Gradle fallisce con «25.0.3». |
| Test | `gradlew testDebugUnitTest` · `node tools\pagina-pc\test-pulizia.js` · da `tools\pagina-pc`: `python -m unittest test_identifica` e `python test_server.py` |

## Fatto il 5 novembre

- **Copia di sicurezza automatica** (`data/AutoBackup.kt`): una al giorno sul tablet (ultime 7), e una sul PC
  (`/api/backup`, ultime 10 in `tools\pagina-pc\backup\`) se il database è cambiato. `tools\backup.ps1` scarica
  quelle del tablet via cavo.
- **Pagina del PC**: lista «+ Aggiungi alla lista» per inviare più brani in un colpo; niente doppioni in coda;
  avviso se il brano è già nel telefono (si ricava dall'ultimo backup); storico di cosa il telefono ha scaricato;
  controllo degli accordi che non c'entrano con la tonalità (`suspectChords` in `pulizia.js`).
- **App**: `addPcSongs` salta i brani già presenti (stesso titolo e artista, senza maiuscole/accenti) e la
  conferma lo dice.
- **Pulsazione dei BPM** (Impostazioni, spenta di default): in live un puntino batte il tempo del brano, più
  grande ogni quattro battiti. Provato sul tablet.
- **Scaletta condivisa con la band** (`data/SetlistPackage.kt`): «Condividi» → «File Backline» manda un file con i
  brani e l'ordine; chi lo apre con Backline li aggiunge e ottiene la scaletta. Provato sul tablet, andata e ritorno
  (condivisione → chooser → Backline → conferma), con un brano già noto e uno nuovo. **Non provato** con un
  secondo telefono e con WhatsApp/Telegram veri.
- `server.py`: non va più in errore se l'output non è una console UTF-8.

## Da fare / da provare

1. **Prova a mano sul tablet** del giro completo con la Wi-Fi: la parte di rete è provata solo con il test del
   server, non con il telefono vero. Con il cavo è già provato (5 novembre): la finestra dice «1 è già nel
   repertorio». Una volta, con l'app aperta e il tablet appena svegliato, la finestra è uscita a 0×0; non si è più
   ripetuta in 3 prove.
2. Il tablet aveva l'app firmata con un'altra chiave di debug: è stata disinstallata e reinstallata, i dati
   ripristinati dalla copia (`backup-tablet\prima-dell-aggiornamento.json`, 6 brani del gruppo COPYCUT).
   D'ora in poi gli aggiornamenti con `adb install -r` funzionano da questo PC.
3. Idee non fatte: cerca da titolo+artista senza tab incollato, click sonoro dai BPM, riepilogo della serata
   (tempo reale contro previsto), Il timer per brano
   in live c'è già.
