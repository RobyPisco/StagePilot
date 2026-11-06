# Stato dei lavori

Aggiornato al **6 novembre 2026**. Il `README.md` dice cosa fa l'app e come si usa; qui c'è a che punto siamo,
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
- **Lampeggio dei BPM**: vedi sotto (6 novembre, è stato rifatto).
- **Scaletta condivisa con la band** (`data/SetlistPackage.kt`): «Condividi» → «File Backline» manda un file con i
  brani e l'ordine; chi lo apre con Backline li aggiunge e ottiene la scaletta. Provato sul tablet, andata e ritorno
  (condivisione → chooser → Backline → conferma), con un brano già noto e uno nuovo. **Non provato** con un
  secondo telefono e con WhatsApp/Telegram veri.
- **Versione 0.2.0** (build 2), visibile in fondo a Impostazioni. APK di rilascio in `dist\Backline-0.2.0.apk`,
  firmato con la chiave di debug (scelta: per ora basta; chi cambia PC dovrà reinstallare).
- `server.py`: non va più in errore se l'output non è una console UTF-8.

## Fatto il 6 novembre

- **Grafica in stile iOS** (scaletta, repertorio, impostazioni): barra in basso con tre schede (Scaletta,
  Repertorio, Impostazioni), titoli grandi, liste a scheda, selettori a segmenti, bottoni tinti. Il Live non è
  stato toccato. Editor del brano e importazione hanno solo i bottoni nuovi.
- **Repertorio**: swipe di lato su una riga per eliminare il brano (con conferma).
- **Scaletta**: in Modifica si trascina la riga dalla maniglia a destra; il «+» fra due righe inserisce una pausa o
  un brano nel punto scelto (scorciatoie: Pausa 5/15 min, Discorso, Cambio chitarra); toccando una pausa si
  cambiano nome e durata. Il tasto «fatto» della tastiera salva (in orizzontale la tastiera copre «Salva»).
- **Schermo sempre acceso** finché l'app è davanti.
- **Live, battito**: cerchio in testata che batte a tempo (bianco sul primo di quattro). Prima era spento di
  default e piccolo; ora l'impostazione è «Lampeggio dei BPM», accesa di default per le installazioni nuove (sul
  tablet è stata accesa a mano). La striscia orizzontale è stata tolta su richiesta.
- **Live, click del metronomo** (`ui/Clicker.kt`): si abilita nelle Impostazioni e si accende toccando il cerchio
  in Live; parte sempre spento a ogni brano. Un thread scrive di continuo nell'AudioTrack in streaming e conta i
  campioni (un AudioTrack statico dà errore -22 su questo tablet; con le pause fra i colpi il flusso si bloccava
  e i colpi uscivano tutti insieme in ritardo). **Da provare a orecchio**: l'audio l'ho controllato solo dal
  sistema (flusso attivo, nessun buco).
- **Pagina PC, BPM e durata da SongBPM** (`songbpm.py`): una richiesta per ricerca; ripiega su Deezer. La
  tonalità del sito non si usa (manca il maggiore/minore e viene dalla registrazione).
- **Pagina PC, scheda «Repertorio»** (`repertorio.html`, `/api/library`, `/api/edits`): mostra brani e scalette
  dall'ultimo backup del tablet; si correggono i dati, si eliminano brani, si riordinano le scalette; si invia con
  Wi-Fi (coda) o via cavo. Sul tablet compare «Modifiche dal PC» e non si applica niente senza «Applica»; una
  scaletta cambiata sul tablet dopo l'ultimo backup letto dal PC non si sovrascrive. Con più dispositivi
  collegati, adb sceglie quello che ha Backline. Provato nel browser e fino alla finestra di conferma sul
  tablet; **«Applica» non provato sul tablet** (solo test automatici).

## Da fare / da provare

1. **Prova a mano sul tablet** del giro completo con la Wi-Fi: la parte di rete è provata solo con il test del
   server, non con il telefono vero. Con il cavo è già provato (5 novembre): la finestra dice «1 è già nel
   repertorio». Una volta, con l'app aperta e il tablet appena svegliato, la finestra è uscita a 0×0; non si è più
   ripetuta in 3 prove.
2. **Attenzione**: dopo il 5 novembre il repertorio del tablet è cambiato (altri brani aggiunti). La copia
   `backup-tablet\prima-dell-aggiornamento.json` è vecchia: non rimetterla sul tablet.
3. Il tablet aveva l'app firmata con un'altra chiave di debug: è stata disinstallata e reinstallata, i dati
   ripristinati dalla copia (`backup-tablet\prima-dell-aggiornamento.json`, 6 brani del gruppo COPYCUT).
   D'ora in poi gli aggiornamenti con `adb install -r` funzionano da questo PC.
4. **Provare a casa con la Wi-Fi**: «Applica» delle modifiche dal PC, invio e scarico via Wi-Fi, backup sul PC.
5. **Provare il click a orecchio** (a tempo? ritardo accettabile? volume?).
6. **APK di rilascio 0.3.0** (build 3): `dist\Backline-0.3.0.apk`, allegato alla release `v0.3.0` su GitHub (repo
   privato). Istruzioni di installazione nel README. Resta da provare la scaletta condivisa con un secondo telefono.
7. Idee non fatte: pedale Bluetooth (tasti freccia) per girare pagina, riepilogo a fine set (tempo reale contro
   previsto), importare più file insieme sulla pagina PC, anteprima come in Live nel Repertorio, trasposizione prima
   dell'invio, testo senza accordi da LRCLIB, tonalità dedotta migliore (Blondie: F#m invece di D), scaletta in PDF,
   rifare nello stile iOS editor e importazione. Il timer per brano in live c'è già, come lo scorrimento automatico
   con velocità per brano.
8. Il tablet ora è usato anche in orizzontale: il layout regge, ma vale la pena riguardarlo.
