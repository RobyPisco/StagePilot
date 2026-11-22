/* ── dati di partenza ────────────────────────────────────────
   Un gruppo d'esempio con tre brani e due scalette: chi apre l'app la
   prima volta vede subito come si usa, invece di una lista vuota.
   È una funzione e non una costante perché ogni chiamata deve dare una
   copia nuova: il database si modifica sul posto. */
export const seed = () => (
{
  v:4, curBand:'b1',
  opt:{ notation:'en', accid:'auto', fs:22, bar:true, timer:true, caps:false, roots:false, plain:false, sort:'add' },
  bands:[{
    id:'b1', name:'COPYCUT', me:'Basso',
    songs:[
      { id:'s1', title:'Ultimo Giro', artist:'COPYCUT', key:'Am', bpm:142, dur:'3:20',
        tags:['originale','apertura'], tr:0, capo:0, scroll:1, lead:8,
        notes:{ Tutti:'Attacco secco, senza conteggio.', Basso:'Strofa sul I grado, niente quinte fino al ritornello.' },
        body:`# Intro — basso e batteria, 8 battute
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
[Am]        [F]        [C]        [G]  [Am]` },

      { id:'s2', title:'Still Burning', artist:'COPYCUT', key:'D', bpm:72, dur:'4:10',
        tags:['originale','lenta'], tr:0, capo:0, scroll:1, lead:12, notes:{},
        body:`# Intro — arpeggio pulito
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
[D]  [Bm]  [G]  [A]  [D]  [Bm]  [G]  [A]` },

      { id:'s3', title:'Amore Disperato', artist:'Nada', key:'Em', bpm:126, dur:'3:40',
        tags:['cover','italiana'], tr:0, capo:0, scroll:1, lead:6,
        notes:{ Tutti:'Arrangiamento nostro, non la versione del disco.' },
        body:`# Struttura concordata — arrangiamento COPYCUT

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

# Finale — ritornello x2, chiusura secca` }
    ],
    sets:[
      { id:'set1', name:'Serata tipo', items:[
        {type:'song', id:'s3'},
        {type:'song', id:'s1'},
        {type:'mark', label:'Pausa', dur:'15:00'},
        {type:'song', id:'s2'}
      ]},
      { id:'set2', name:'Acustico', items:[{type:'song', id:'s2'}] }
    ],
    cur:'set1'
  }]
});
