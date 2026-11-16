# Rocket League Fennec (NeoForge 1.21.1)

Una mod per Minecraft **NeoForge 1.21.1** che aggiunge una **Fennec di Rocket League** guidabile:
si muove, **salta** (e fa il flip), ha **100 di boost** e in basso a destra c'è il contatore del boost.

![Fennec](src/main/resources/assets/rlfennec/textures/item/fennec.png)

## Come si usa

1. Ottieni l'oggetto **Fennec**: scheda *Strumenti e utilità* del creativo, oppure `/give @s rlfennec:fennec`
   (ricetta in survival: 4 lingotti di ferro + colorante blu + blocco di redstone + 3 lana nera, vedi `data/rlfennec/recipe/fennec.json`).
2. Click destro su un blocco per appoggiare l'auto, poi **click destro sull'auto** per salire. **Shift** per scendere.
3. Guida:

| Tastiera | Azione |
|---|---|
| `W` / `S` | a terra: accelera / frena e retromarcia. **In aria: `W` abbassa il muso, `S` lo alza** (come spingere la leva in avanti in Rocket League) e in più danno una **spinta lenta** in avanti / indietro (vedi sotto) |
| `A` / `D` | a terra: sterza. In aria: **imbardata** (gira il muso a sinistra / destra) |
| `Q` / `E` | **air roll** a sinistra / destra (in aria) |
| `Alt sinistro` (tenuto) | **air roll libero**: finché lo tieni premuto `A` / `D` fanno rollare l'auto invece di girarla, come il pulsante "Air Roll" di Rocket League |
| `Spazio` | **salto**. Premilo ancora in aria (entro 1,5 s): senza direzione è un **doppio salto**, con una direzione premuta (`W` `S` `A` `D`, anche in diagonale) è un **flip** in quella direzione (vedi sotto). Con l'auto **ribaltata** a terra serve a **girarla** (vedi "Schianto") |
| `Ctrl sinistro` | **boost** |
| Mouse su/giù | solo la visuale. **Non punta più l'auto**: il muso si controlla con `W`/`S` |
| `Shift` | scendi (vedi sotto) |

### Cambiare i comandi

**Tastiera e mouse**: ogni azione ha il suo tasto in *Opzioni → Comandi → Tasti → Rocket League Fennec*: accelera, frena, sterza a sinistra/destra, muso giù/su, salto, boost, air roll sinistra/destra, air roll libero, scendi. Si può usare anche un tasto del mouse (sconsigliato `clic sinistro/destro`: Minecraft li usa comunque per colpire e piazzare).
* **Qualunque comando si può lasciare vuoto** ("Non assegnato"): un comando vuoto non fa niente. Se li svuoti tutti puoi anche dare all'auto tasti tutti suoi, per esempio le frecce per il muso e `WASD` per il resto.
* Mentre guidi **Furtività non fa più scendere**: si scende con il tasto "Scendi" (di default `Shift sinistro`) o con `Y` del controller. Se li lasci vuoti tutti e due, torna a valere Furtività, così non resti mai bloccato nell'auto.
* Minecraft segnala in rosso i tasti che hanno in comune con altre azioni (`W` `A` `S` `D` `Spazio` `Shift` `Q` `E` `Ctrl` sono anche movimento, "Butta oggetto", "Inventario" e "Corri"): è voluto, mentre guidi quelle azioni di Minecraft sono disattivate (non butti l'oggetto in mano e non si apre l'inventario), vale per qualsiasi tasto che condividi con un'azione dell'auto.
* In `config/rlfennec-common.toml` c'è `invertAirPitch` per invertire `W`/`S` in aria.

### Velocità di sterzata

Quattro regolatori (da 0,2 a 3,0, 1,0 è la velocità normale): sterzata a terra, beccheggio in aria, imbardata in aria e air roll. Si cambiano dal bottone **"Controller e impostazioni Fennec..."** in *Opzioni → Comandi → Tasti*, poi **"Sterzata e altre opzioni..."** (oppure *Mods → Rocket League Fennec → Config*), sezione `handling`. Sono nel file `config/rlfennec-client.toml` e valgono per te, non per gli altri giocatori.

### Salto, flip, flip cancel, half flip e wave dash

* **Altezza del salto**: un tocco su salto è un saltino (circa 1 blocco), tenendolo premuto (fino a 0,3 s) il salto sale fino al massimo (circa 3,4 blocchi), come in Rocket League.
* **Spinta lenta in aria**: senza boost, tenendo premuto accelera (`W`, o il grilletto) l'auto prende velocità in avanti nella direzione in cui è girata, fino a circa 12 blocchi al secondo; con frena (`S`) va indietro, fino a circa 8 blocchi al secondo. Con un grilletto premuto a metà la spinta è dimezzata. Non rallenta mai un'auto già più veloce che va dritta, non funziona col boost, durante un flip e con l'auto a ruote in su.
* **Flip (dodge)**: dopo il primo salto, premi di nuovo salto **spingendo una direzione**: avanti, indietro, sinistra, destra o diagonale (tastiera o levetta). L'auto fa un giro completo in quella direzione (0,6 s) e prende una spinta di velocità. Senza direzione è solo un doppio salto. **Funziona anche tenendo premuto l'air roll libero** (nel tuo caso ZL).
* **Flip cancel**: durante il flip tieni la direzione **opposta**: il giro si ferma e la spinta di velocità resta. **Non si ferma di colpo**: l'auto continua a girare per un attimo (circa un quinto di secondo, al massimo 35° in più) e rallenta fino a fermarsi, quindi il cancel va dato un po' **in anticipo**. Se lo dai subito il flip non parte quasi per niente (resta una piccola inclinazione). Per qualche istante dopo il cancel quel tasto non fa anche girare l'auto dall'altra parte.
* **Half flip**: vai in retromarcia, salto, flip all'indietro, **prima** di metà giro (quando l'auto è a circa 110-120° dal tetto in su) fai il cancel: l'auto finisce di girarsi da sola e si ferma capovolta, poi con l'air roll (`Q`/`E`, o l'air roll libero) rimetti le ruote in basso: l'auto ora guarda nella direzione in cui stava andando.
* **Wave dash**: fai il flip quando sei **vicino a terra** (per esempio con un saltino) e atterra con le ruote verso il basso entro circa mezzo secondo: il flip viene annullato dal terreno, l'auto riparte con un impulso di velocità sopra il normale (e lo tiene per un po') senza usare boost. Più in alto il flip ha più tempo e finisce il giro. Se invece atterri sul tetto o sul muso è uno schianto (vedi sotto).
* Il flip è una rotazione vera, non un'animazione: durante il giro il boost spinge dove punta il muso in quel momento. Gli altri giocatori vedono la stessa rotazione.

### Schianto e ribaltamento

* Se tocchi terra **sul tetto, su un fianco o di muso** (più di circa 63° fuori dalle ruote) l'auto **non si rimette in piedi da sola**: **rimbalza** (di più o di meno secondo la velocità di caduta, fino a due o tre rimbalzi), poi si ferma scivolando e, se stava in piedi sul muso, cade sul tetto o su un fianco. Un atterraggio lento non rimbalza.
* Durante i rimbalzi hai ancora il controllo in aria: se riesci a rimettere le ruote in basso con l'air roll prima dell'ultimo contatto, atterri normalmente e non c'è nessuno schianto.
* Quando l'auto è ferma ribaltata compare in basso a destra, **sotto il contatore del boost**, il suggerimento **"Premi [Spazio] per girarti"** (con il tasto che hai scelto per il salto e, se hai un controller collegato, anche il suo pulsante). Premi salto e l'auto **si gira in esattamente 1 secondo**, piano all'inizio e alla fine, con il muso verso dove guardava. Se è sulla schiena puoi scegliere da che parte girare tenendo sinistra o destra mentre premi (di default a destra).
* Finché l'auto è ribaltata non si guida, non sterza, non salta e non usa il boost. Se scendi, o scendi e sale qualcun altro, l'auto si rimette in piedi da sola; anche l'acqua la rimette in piedi.
* Il flip che finisce con l'atterraggio entro mezzo secondo (wave dash) non è uno schianto, anche se l'auto è ancora inclinata: viene raddrizzata come prima.

## Compilare

Serve JDK 21.

```bash
./gradlew build
```

Il jar è in `build/libs/rlfennec-1.0.0.jar`: va copiato nella cartella `mods` di NeoForge 1.21.1 (21.1.x). Per provarla direttamente: `./gradlew runClient`.

I test della fisica (`CarPhysics`, pura matematica senza Minecraft) girano con `./gradlew test`.

## Suoni: cosa c'è e cosa no

I **suoni originali di Rocket League (motore della Fennec, boost) non sono inclusi**: sono audio di Psyonix/Epic Games, coperto da copyright, e non li ho scaricati né ricampionati. Non ho trovato nessuna fonte che li distribuisca con una licenza che permetta di metterli in una mod.

**Il motore** viene dalla registrazione che mi hai dato, tagliata in tre pezzi (`tools/prepare_engine_sound.py`): l'accensione quando sali, il loop del motore acceso mentre guidi, lo spegnimento quando scendi. Il loop è fermo al minimo quando l'auto sta ferma; sale di tono e di volume con la velocità, ha un colpo di volume quando dai gas, e quando molli scende lentamente come un motore vero.

Il resto è **sintetizzato da zero** (`tools/generate_sounds.py`): il ruggito del boost in loop, l'accensione del boost, il salto e il flip.

Se vuoi i suoni veri (per uso personale, dalla tua copia del gioco) basta **sostituire questi file** in `src/main/resources/assets/rlfennec/sounds/fennec/` e ricompilare:

| File | Quando suona |
|---|---|
| `engine_start.ogg` | una volta quando qualcuno sale |
| `engine_loop.ogg` | in loop mentre qualcuno guida; l'altezza va da 0,5× a 2× con la velocità |
| `engine_stop.ogg` | una volta quando il pilota scende |
| `boost_loop.ogg` | in loop finché tieni premuto il boost |
| `boost_start.ogg` | una volta all'accensione del boost |
| `jump.ogg`, `flip.ogg` | salto e flip |

Devono essere **OGG Vorbis mono**; i due loop devono chiudersi senza click:

```bash
ffmpeg -i mio_motore.wav -ac 1 -ar 44100 -c:a libvorbis -q:a 4 engine_loop.ogg
```

## Il modello 3D

Il modello è quello del file che mi hai dato (`fennec_-_rocket_league_car.obj`, 58 851 vertici / 84 823 triangoli, texture incluse). `tools/convert_fennec_model.py` lo converte in `assets/rlfennec/models/entity/fennec.bin`: scala 0,025 (l'auto è lunga 3,5 blocchi), le quattro ruote sono parti separate così girano e sterzano. Viene caricato una sola volta nella GPU e disegnato con lo shader delle entità di Minecraft con texture trilineari, quindi costa poco anche con tante auto.

```bash
python3 tools/convert_fennec_model.py percorso/fennec_-_rocket_league_car.obj
python3 tools/render_icon.py      # rigenera l'icona dell'oggetto
python3 tools/generate_hull.py    # rigenera il contorno usato per appoggiare l'auto ribaltata a terra
```

> Il modello e le texture della Fennec sono proprietà di Psyonix/Epic Games. Il codice di questa mod è libero (MIT), ma gli asset del modello non lo sono: tienili per uso personale e non ridistribuire il jar.

## Come funziona la rete (multiplayer)

Come per le barche, l'auto è simulata dal client di chi guida (sterzo senza ritardo) e Minecraft sincronizza la posizione. Un piccolo pacchetto (`CarControlPayload`) comunica al server il boost rimasto, se il boost è acceso e i salti, così gli altri giocatori vedono fiamme, flip e sentono i suoni. Di conseguenza il boost è "fidato" dal client: va bene tra amici, non è a prova di cheat.

## Cosa non c'è (ancora)

* Pad del boost sul terreno (ora il boost si ricarica da solo, vedi sopra).
* Palla e porte. È solo l'auto.
* Non è stato provato su un server dedicato né con due giocatori.
* Il controller è provato solo a livello di logica (la mappatura ha i test, la lettura di GLFW non dà errori senza pad collegato) ma non con un pad fisico. La tastiera è stata provata con veri eventi di tasto in un client Minecraft reale.
