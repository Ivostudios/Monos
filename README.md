# Monos - Open Music Player

Un player musicale per Android per chi ascolta i **propri brani locali**: niente account, niente abbonamenti, niente tracciamento. Interfaccia pulita e minimale, testi sincronizzati, controlli separati (o uniti) di velocità e pitch, e una dashboard con le tue statistiche d'ascolto.

L'interfaccia segue un design realizzato in Figma ed è scritta da zero, senza componenti Material 3.

<!-- Aggiungi qui gli screenshot, ad esempio in docs/screenshots -->
<!-- ![Home](docs/screenshots/home.png) ![Player](docs/screenshots/player.png) ![Libreria](docs/screenshots/library.png) -->

**Versione attuale: 1.25** · APK: `monos.apk` · Android 8.0 o successivo

---

## Caratteristiche

### Ascolto
- Legge i brani già presenti sul telefono (titolo, artista, album, copertina) tramite MediaStore.
- Riproduzione in background con notifica e controlli dalla schermata di blocco e dalle cuffie.
- **Velocità e pitch** regolabili separatamente oppure uniti in un unico controllo (tasto *Unisci / Separa*).
- Casuale, ripeti brano o tutti, coda di riproduzione con i prossimi brani.
- Cartelle da includere (whitelist) o escludere (blacklist) dalla libreria.

### Il player
- Schermo intero con **sfondo sfocato e colorato** costruito sul colore della copertina.
- Tre pagine da scorrere con un gesto: **Informazioni**, **Copertina** e **Testi**. La pagina Informazioni compare solo per i brani di un album e mostra la posizione del brano ("Traccia 3 di 14"), gli artisti e i dati audio.
- Si apre trascinando il mini player verso l'alto e si chiude trascinandolo verso il basso, con la pagina che segue il bordo del player.
- Due stili per mini player e barra: **Tutto in uno** (la fascia scura prende il colore della copertina) o **Pillola**.

### Testi sincronizzati
- Le righe scorrono a tempo: quella corrente è in evidenza, le altre sfumano e si sfocano. Un tocco su una riga porta il brano a quel punto.
- Allineamento **a sinistra o al centro**.
- I testi arrivano da [LRCLIB](https://lrclib.net), un database gratuito e collaborativo: si scaricano **da soli alla prima apertura** di un brano oppure **tutti insieme** dalle impostazioni, con un popup che mostra l'avanzamento e permette di interrompere.
- Restano salvati nell'app. Puoi anche importare a mano un file `.lrc`.

### Libreria
- **Home**: album suggeriti, *album del giorno*, *artista del giorno* con alcuni brani e brani suggeriti. Le proposte del giorno cambiano ogni giorno e nascono dai metadati dei tuoi brani.
- **Libreria**, con selettore in alto tra **Brani**, **Album** e **Artisti**, in griglia o in elenco.
- **Album**: tieni premuto un album e trascinalo su un altro per unirli in una **cartella unita**.
- **Artisti**: pagina dedicata con album, brani e ascolti. Puoi segnare gli artisti preferiti (stella) e cambiare nome e immagine.
- **Playlist**: i brani preferiti e le tue playlist, create da un popup dal basso con nome, descrizione, copertina dalla galleria e scelta dei brani.
- Ricerca su brani, album e artisti.

### Menu rapidi
Tenendo premuto o dai tre puntini di brani, album, playlist e artisti si apre un menu a comparsa con le azioni avanzate:
- aggiungi ai preferiti, aggiungi a una playlist, vedi i dettagli;
- modifica le informazioni di una playlist o di un artista, aggiungi brani a una playlist;
- nascondi un album (si ritrova da *Impostazioni → Dati*);
- elimina dal dispositivo (Android chiede sempre una conferma di sistema).

### Dashboard
Statistiche calcolate **solo sul telefono**, a partire dal giorno in cui installi l'app:
- giorni di ascolto consecutivi, con la settimana in corso;
- ore totali di ascolto e brani ascoltati (un brano conta dopo 20 secondi);
- album più ascoltato di sempre;
- cronologia degli ascolti recenti.

### Personalizzazione
- Tema chiaro, scuro o del sistema, con modalità **AMOLED**.
- Colore principale: monocromatico (nero/bianco) oppure blu, viola, verde, rosso.
- Fascia colorata sulla copertina attivabile o meno, ritaglio 1:1 delle copertine opzionale.
- Pagine della barra di navigazione attivabili o nascoste.
- Icona dell'app adattiva, compatibile con i **colori dinamici** di Android.

### Impostazioni
Stile dell'app · Testi · Navigazione · Dati (spazio usato e pulizia della cache) · Cartelle · Versione dell'app · Pagina GitHub · Resetta tutto.

---

## Privacy
- Nessun account, nessuna pubblicità, nessun servizio di analisi.
- Preferiti, playlist, cartelle e statistiche restano nella memoria dell'app.
- La connessione a internet serve **solo** per scaricare i testi da LRCLIB.
- Se l'app si chiude per un errore, alla riapertura mostra il testo dell'errore con un pulsante per copiarlo. Non viene inviato nulla.

## Permessi
| Permesso | A cosa serve |
|---|---|
| Lettura dei file audio | Trovare i brani sul telefono |
| Notifiche | Controlli di riproduzione nella notifica |
| Internet | Scaricare i testi da LRCLIB |
| Scrittura (solo Android 9 e precedenti) | Eliminare un brano dal dispositivo su sistemi vecchi |

## Requisiti
- Android 8.0 (API 26) o successivo. La sfocatura vera dello sfondo richiede Android 12 o successivo; sulle versioni precedenti si usa un gradiente.

---

## Tecnologie
- **Kotlin** e **Jetpack Compose**, con componenti grafici scritti da zero
- **Media3** (ExoPlayer e MediaSession) per la riproduzione
- **DataStore** per preferenze, preferiti, playlist, cartelle e statistiche
- **MediaStore** per la libreria locale

## Crediti
- Testi: [LRCLIB](https://lrclib.net)
- Font: [Unna](https://fonts.google.com/specimen/Unna) e [Urbanist](https://fonts.google.com/specimen/Urbanist), distribuiti con licenza SIL Open Font License
- Icone: Material Icons (licenza Apache 2.0)
- Design: file Figma realizzato per questo progetto

I testi appartengono ai rispettivi autori e vanno usati solo per ascolto personale.

## Prossimamente
- Layout per tablet e schermi larghi
- Popup "Opzioni" con cronologia ascolti in pagina propria
- Classifica dei 5 brani più ascoltati di sempre nella dashboard
- Integrazione con Jellyfin (profilo e uscita dal profilo)
- APK firmato per l'installazione senza avvisi

## Licenza
Da definire.
