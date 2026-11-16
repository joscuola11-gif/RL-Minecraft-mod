# Rocket League Fennec (NeoForge 1.21.1)

Una mod per Minecraft **NeoForge 1.21.1** che aggiunge una **Fennec di Rocket League** guidabile:
si muove, **salta** (e fa il flip), ha **100 di boost** e in basso a destra c'è il contatore del boost.

![Fennec](src/main/resources/assets/rlfennec/textures/item/fennec.png)

## Come si usa

1. Ottieni l'oggetto **Fennec**: scheda *Strumenti e utilità* del creativo, oppure `/give @s rlfennec:fennec`
   (ricetta in survival: 4 lingotti di ferro + colorante blu + blocco di redstone + 3 lana nera, vedi `data/rlfennec/recipe/fennec.json`).
2. Click destro su un blocco per appoggiare l'auto, poi **click destro sull'auto** per salire. **Shift** per scendere.
3. Guida:

| Tasto | Azione |
|---|---|
| `W` / `S` | accelera / frena e retromarcia |
| `A` / `D` | sterza (in aria: ruota l'auto) |
| `Spazio` | **salto**. Premilo ancora in aria (entro 1,5 s) per il **doppio salto**; con `W` `S` `A` `D` premuto fai un **flip** in quella direzione, con una spinta in più |
| `Ctrl sinistro` | **boost** (rimappabile in *Opzioni → Comandi → Rocket League Fennec*) |
| Mouse su/giù | in aria il muso dell'auto punta dove guardi: guarda in su e usa il boost per volare |

* Il **boost** ha 100 punti e se ne vanno a 33 al secondo (100 = 3 secondi, come in Rocket League). Con il boost si supera la velocità massima (22 → 34 blocchi/s) e si spinge nella direzione del muso, anche in aria.
* Il contatore è in basso a destra: anello arancione che si svuota, numero al centro.
* Il boost si ricarica da solo dopo 2 secondi di inattività (6 punti/s). Si può cambiare o spegnere (`0` = come in Rocket League, niente ricarica) in `config/rlfennec-common.toml`.
* La camera è sempre dietro l'auto (la visuale in prima persona è disattivata mentre guidi), il pilota non viene disegnato.

## Compilare

Serve JDK 21.

```bash
./gradlew build
```

Il jar è in `build/libs/rlfennec-1.0.0.jar`: va copiato nella cartella `mods` di NeoForge 1.21.1 (21.1.x). Per provarla direttamente: `./gradlew runClient`.

I test della fisica (`CarPhysics`, pura matematica senza Minecraft) girano con `./gradlew test`.

## Suoni: cosa c'è e cosa no

I **suoni originali di Rocket League (motore della Fennec, boost) non sono inclusi**: sono audio di Psyonix/Epic Games, coperto da copyright, e non li ho scaricati né ricampionati. Non ho trovato nessuna fonte che li distribuisca con una licenza che permetta di metterli in una mod.

Al loro posto ci sono suoni **sintetizzati da zero** (`tools/generate_sounds.py`): un motore a loop il cui tono sale con la velocità, un ruggito di boost in loop, l'accensione del boost, il salto e il flip.

Se vuoi i suoni veri (per uso personale, dalla tua copia del gioco) basta **sostituire questi file** in `src/main/resources/assets/rlfennec/sounds/fennec/` e ricompilare:

| File | Quando suona |
|---|---|
| `engine_loop.ogg` | in loop mentre qualcuno guida; l'altezza va da 0,5× a 2× con la velocità |
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
```

> Il modello e le texture della Fennec sono proprietà di Psyonix/Epic Games. Il codice di questa mod è libero (MIT), ma gli asset del modello non lo sono: tienili per uso personale e non ridistribuire il jar.

## Come funziona la rete (multiplayer)

Come per le barche, l'auto è simulata dal client di chi guida (sterzo senza ritardo) e Minecraft sincronizza la posizione. Un piccolo pacchetto (`CarControlPayload`) comunica al server il boost rimasto, se il boost è acceso e i salti, così gli altri giocatori vedono fiamme, flip e sentono i suoni. Di conseguenza il boost è "fidato" dal client: va bene tra amici, non è a prova di cheat.

## Cosa non c'è (ancora)

* Pad del boost sul terreno (ora il boost si ricarica da solo, vedi sopra).
* Palla e porte. È solo l'auto.
* Non è stato provato su un server dedicato né con due giocatori; è stato provato su client singleplayer.
