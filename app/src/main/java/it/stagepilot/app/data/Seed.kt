package it.stagepilot.app.data

/* Dati di partenza: un gruppo d'esempio con tre brani e due scalette, così
   chi apre l'app la prima volta vede subito come si usa. Sono quelli
   dell'app web. */
fun seed(): DB = DB(
    curBand = "b1",
    bands = listOf(
        Band(
            id = "b1",
            name = "COPYCUT",
            me = "Basso",
            cur = "set1",
            songs = listOf(
                Song(
                    id = "s1", title = "Ultimo Giro", artist = "COPYCUT", key = "Am", bpm = 142, dur = "3:20",
                    tags = listOf("originale", "apertura"), lead = 8,
                    notes = mapOf(
                        "Tutti" to "Attacco secco, senza conteggio.",
                        "Basso" to "Strofa sul I grado, niente quinte fino al ritornello.",
                    ),
                    body = """
# Intro — basso e batteria, 8 battute
[Am]        [F]        [C]        [G]

# Strofa
[Am]Ultimo giro, il [F]bicchiere è vuoto
[C]La sala chiude ma noi [G]siamo ancora qui
[Am]Nessuno guarda l'oro[F]logio stanotte
[C]Fuori piove e den[G]tro no

# Ritornello
[F]Suona ancora, [C]suona forte
[G]Tanto il giorno [Am]non arriva mai
[F]Suona ancora, [C]suona forte
[G]Ultimo giro e poi [Am]si va

# Stop-time — solo colpi sull'1
[F]  [C]  [G]  [Am]

# Finale
[Am]        [F]        [C]        [G]  [Am]
""".trimIndent(),
                ),
                Song(
                    id = "s2", title = "Still Burning", artist = "COPYCUT", key = "D", bpm = 72, dur = "4:10",
                    tags = listOf("originale", "lenta"), lead = 12,
                    body = """
# Intro — arpeggio pulito
[D]        [Bm]        [G]        [A]

# Strofa
[D]Ashes in the morn[Bm]ing light
[G]Everything we said we'd [A]never lose
[D]I can still feel it [Bm]burning
[G]Slow and low and [A]true

# Ritornello — entra tutto
[G]Still burning, [D]still burning
[A]After all this [Bm]time
[G]Still burning, [D]still burning
[A]And it's still [D]mine

# Assolo — 8 battute sul giro di strofa
[D]  [Bm]  [G]  [A]  [D]  [Bm]  [G]  [A]
""".trimIndent(),
                ),
                Song(
                    id = "s3", title = "Amore Disperato", artist = "Nada", key = "Em", bpm = 126, dur = "3:40",
                    tags = listOf("cover", "italiana"), lead = 6,
                    notes = mapOf("Tutti" to "Arrangiamento nostro, non la versione del disco."),
                    body = """
# Struttura concordata — arrangiamento COPYCUT

# Intro — basso e batteria, 8 battute
[Em]  [Em]  [C]  [D]

# Strofa — arrangiamento rado, basso avanti
[Em]  [Em]  [C]  [D]
[Em]  [Em]  [C]  [D]

# Ritornello — stop-time, colpi sull'1 e sul 3
[C]  [D]  [Em]  [Em]
[C]  [D]  [Em]  [Em]

# Bridge — breakdown, solo voce e basso
[Am]  [Am]  [Em]  [Em]

# Finale — ritornello x2, chiusura secca
""".trimIndent(),
                ),
            ),
            sets = listOf(
                SetList(
                    "set1", "Serata tipo",
                    listOf(
                        SetItem("song", id = "s3"),
                        SetItem("song", id = "s1"),
                        SetItem("mark", label = "Pausa", dur = "15:00"),
                        SetItem("song", id = "s2"),
                    ),
                ),
                SetList("set2", "Acustico", listOf(SetItem("song", id = "s2"))),
            ),
        ),
    ),
)
