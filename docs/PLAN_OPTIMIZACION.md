# Plan: optimización (tirones al cargar chunks)

**Estado:** en pausa (2026-09-25), a pedido del usuario, para arreglar primero unas cosas del agua y del jugador. Las fases 1 (medir) y 2 (mallas sin basura, el arreglo grande) están hechas y medidas aquí. Al retomar: primero repetir en Windows la prueba de la fase 2 con el código actualizado (`git pull`), porque la que se hizo corrió sin la fase 2. Si ya no hay tirones, las fases 3 y 4 pueden no hacer falta.

## El problema

Al caminar, los chunks nuevos se generan bien, pero **el juego se traba un poco cada vez que carga chunks nuevos**. Lo reportó el usuario al probar en Windows el 2026-09-24.

## Cómo trabajamos

Igual que con el menú: una fase por conversación y `/clear` entre fases (ver `CLAUDE.md`). Además, en este plan:

- **Medir antes y después de cada fase** con lo que se hizo en la fase 1 (ver "Cómo medir"), y anotar los números en la tabla. Sin números no sabemos si algo mejoró.
- **El mundo no puede cambiar.** Con la misma semilla deben salir los mismos bloques y las mismas mallas. `herramientas/MedirChunks.java` lo comprueba: tiene que terminar con "El mundo no cambió".
- Para arrancar una fase basta con algo como: *"Empieza la fase 2 de optimización (docs/PLAN_OPTIMIZACION.md)"*.

## Estado de las fases

| Fase | Estado | Commit | Medición (antes → después) | Qué falta probar en Windows |
| --- | --- | --- | --- | --- |
| 1. Medir | hecha y medida en Windows | `d264a19` | Aquí: la malla opaca de un chunk reserva **141 MB** y tarda 165–410 ms; cruzar un borde reserva **905 MB** y trae pausas de GC de **100–540 ms**. Ver "Mediciones". En Windows (antes de la fase 2): 40 frames lentos en 1 minuto (el peor, 186 ms), 37 de ellos con pausa de GC; 60 pausas de GC que suman 3,2 s; 78–133 MB reservados por chunk. | Nada. El usuario lo midió el 2026-09-25 (ver "Mediciones"). |
| 2. Mallas sin basura (`Float` y vecinos) | hecha (compila; medida aquí y en el juego con OpenGL por software; "El mundo no cambió") | `48d2024` | Aquí: la malla opaca reserva **141 → 4,1 MB** y tarda 357 → 6–15 ms; cruzar un borde reserva **904 → 58 MB**, el GC pasa de 378 a **4–12 ms** por borde y la pausa más larga de 354 a **17 ms** (59 ms en una corrida). `getBlockGlobal()`: 160 → 21–37 ns. Ver "Mediciones". | Repetir la prueba de la fase 1 (semilla 12345, ~1 minuto en línea recta, pegar la salida): la del 2026-09-25 corrió **sin la fase 2** (133 MB por chunk, como antes; ver "Segunda prueba en Windows"). Debería haber muy pocas pausas de GC al cruzar bordes y `armados` con unos 5–7 MB por chunk. Mirar también `mundo` en los frames que piden chunks (ver "Fase 2, el juego en Linux"). |
| 3. Aliviar el hilo principal | pendiente: antes, la prueba de la fase 2 en Windows (ver "Fase 3") | | En Windows, sin la fase 2: `mundo` 4–11 ms y `mallas` 2–11 ms por borde (ver "Segunda prueba en Windows"). | |
| 4. Hilos generadores | pendiente | | | |
| 5. Menos cosas que dibujar (opcional) | pendiente | | | |

## Qué pasa hoy al cruzar a otro chunk

Medido en la fase 1 (ver "Mediciones"). Lo que traba la pantalla son **pausas del GC** mientras los hilos generadores arman las mallas: en esas pausas se detienen todos los hilos, también el que dibuja. Lo demás pesa mucho menos.

La fase 2 arregló los puntos 1 y 2: la basura por chunk bajó de ~100 MB a ~6 MB y las pausas del GC al cruzar un borde, de 100–540 ms a menos de 20 ms casi siempre. Quedan los puntos 3 a 5.

1. **(Arreglado en la fase 2.) Buscar los vecinos de cada bloque en el mapa de chunks (lo más pesado, no estaba en el plan).** Para decidir qué caras se ven, `ChunkMeshBuilder` pregunta por los 6 vecinos de cada bloque sólido con `world.getBlockGlobal()`, también cuando el vecino está en el mismo chunk: unas 800.000 veces por malla. Cada vez busca el chunk en el `ConcurrentHashMap<Long, Chunk>` y eso reserva memoria:
   - la clave `long` se convierte en un objeto `Long`, dos veces (`containsKey` y `get`);
   - el `hashCode()` de esa clave es `chunkX ^ chunkZ`, así que las 81 claves caen en **solo 16 cubetas** (hasta 9 en una). Las cubetas tan llenas se vuelven árboles y buscar en ellos llama a `getGenericInterfaces()`, que crea arreglos nuevos cada vez.

   En total son 48 a 112 bytes y ~0,1 µs por vecino: **unos 86 de los 141 MB** de la malla opaca y más o menos la mitad de su tiempo.
2. **(Arreglado en la fase 2.) Mallas hechas con objetos `Float`.** `ChunkMeshBuilder` junta los vértices en una `List<Float>`: cada número es un objeto aparte y cada cara arma un `float[]` temporal. Son unos 35 MB por malla (35.800 caras, más de un millón de `Float`). Pesa menos que los vecinos, pero es lo que **alarga las pausas**: el log del GC muestra que casi toda la pausa es copiar objetos vivos ("Object Copy"), y mientras una malla se arma su lista está viva. Además, cada vez que la lista crece pide un arreglo de más de 1 MB ("humongous" para G1), y eso dispara GC extra.
3. **Trabajo en el hilo principal.** `World.actualizarMundo()` crea los 9 `Chunk` en el hilo principal: 4–12 ms y 17 MB (a veces ~100 ms, cuando justo cae un GC). Subir una malla (~4 MB) con `Chunk.cargarMallaEnOpenGL()` tardó 2–47 ms con OpenGL por software; con una GPU de verdad hay que medirlo en Windows.
4. **Trabajo que se tira.** Si el jugador camina rápido, se siguen generando chunks que ya quedaron fuera de rango. En el juego con OpenGL por software (2–3 FPS) se descartaron 4 mallas en un cruce; a 60 FPS debería pasar poco.
5. **Llegan 9 chunks de golpe** y los hilos generadores (`núcleos − 1`) compiten con el hilo principal. Aquí no se vio: el retraso del hilo "sonda" (ver "Cómo medir") coincide con la pausa del GC más larga, no con la pelea por los núcleos.

## Cómo medir

### En el juego: el medidor de frames

`debug/MedidorRendimiento` se prende con `Constants.MEDIR_RENDIMIENTO` (queda en `true` mientras dure este plan). Solo mide los frames de la partida (`JUGANDO`), no los menús ni la pausa. Cada frame se parte en:

| Parte | Qué es |
| --- | --- |
| `limpiar` | `glClear` y revisar ESC |
| `jugador` | `Input.update()`, mover al jugador y la cámara |
| `mundo` | `actualizarMundo()` (solo en el frame en que se cambia de chunk) |
| `mallas` | `procesarMallasPendientes()`: subir una malla a la GPU |
| `render` | dibujar el mundo y el HUD |
| `swap` | `glfwSwapBuffers()` y `glfwPollEvents()`; con V-Sync aquí se espera al monitor, así que en un frame normal es casi todo el frame |

En la consola, con el prefijo `[medidor]`:

- **Cada frame de 25 ms o más:** cuánto tardó cada parte, si hubo GC en ese frame (cuántas pausas y cuántos ms), y si pidió chunks o subió una malla. Como mucho 15 por resumen, para no llenar la consola.
- **Cada 5 s de juego, un resumen:** frames, FPS, frame promedio y peor, frames lentos; promedio y máximo de cada parte; GC (pausas, ms en total y el frame con más GC), el heap y lo que reservó el hilo principal; y los chunks: pedidos, armados por los hilos generadores (con lo que tardaron el terreno y las mallas y la memoria que reservaron, en promedio), subidos a la GPU y descartados.
- **Al salir al menú o cerrar el juego:** el total de la partida.

### Aquí, sin pantalla: `herramientas/MedirChunks.java`

```text
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" herramientas/MedirChunks.java
```

Tarda unos 25 s y tiene tres partes:

1. **Un chunk en un solo hilo**, con la semilla 12345: tiempo y memoria reservada de cada paso (`new Chunk`, terreno, malla opaca y transparente), en 25 mallas alrededor de (0, 0), después de calentar la JVM.
2. **Cruzar un borde** 5 veces: 9 chunks nuevos en `núcleos − 1` hilos que corren `Chunk.generarTerrenoAsincrono()`, como el juego. Mide cuánto tardan, la memoria, las pausas del GC y un hilo "sonda" que hace de hilo principal: se duerme 1 ms una y otra vez y anota cuánto se despierta tarde.
3. **El mundo no cambió:** compara los bloques y las mallas de esos 25 chunks (cantidad de floats y hash) con `herramientas/referencia_mallas.txt`. Si algo cambió, dice qué chunk y qué malla, y termina con error.

**Los tiempos varían mucho de una corrida a otra** (este equipo es compartido): comparar con varias corridas. **La memoria reservada sale exacta** y es el mejor número para comparar fases.

**Ojo con la memoria nueva en este equipo.** Es una máquina virtual y, la primera vez que el heap crece y usa memoria que nunca se tocó, el hilo que la reserva puede quedarse esperando unos 100 ms sin usar CPU. Se ve como `crear los chunks` de ~110 ms en la herramienta y `mundo` de 159 ms en el juego (ver "Fase 2"). Antes de la fase 2 no se notaba porque la basura hacía crecer el heap al principio. Para saber si un número así es del código o del equipo, correr la herramienta también con el heap ya tocado:

```text
java -Xms3g -XX:+AlwaysPreTouch -cp "target/classes:$(cat target/classpath.txt)" herramientas/MedirChunks.java
```

Con eso `crear los chunks` vuelve a 4–6 ms. En Windows tocar memoria nueva es rápido, así que allá no debería pasar.

### Prueba en Windows

1. Traer los cambios de la rama (`git pull`), recompilar y abrir el juego como siempre (`com.minejava.Main.Launcher` desde el IDE). Para saber si corre el código nuevo: desde la fase 2, los resúmenes dicen unos 5–15 MB reservados por chunk en `armados`; si dicen ~100 MB, es el juego de antes de la fase 2.
2. *Un jugador* → escribir la semilla **12345** → *Crear mundo*. Así medimos siempre el mismo mundo.
3. Al entrar, **no moverse unos 15 s**, hasta que un resumen diga `armados 0` y `subidos 0` (terminó de cargar el mundo).
4. **Mantener W unos 60 s** sin mover el ratón (el jugador vuela en línea recta).
5. ESC → *Salir al menú*: imprime el total.
6. Copiar de la consola todo desde `Semilla del mundo: 12345` hasta la línea `==== total de la partida ... ====` y pegarlo en la conversación.

## Mediciones

### Fase 1, aquí (Linux, 4 núcleos, Java 21, G1, heap máx 3,4 GB)

`MedirChunks`, cinco corridas:

| Paso (un chunk, un hilo) | Tiempo prom. | Memoria reservada prom. |
| --- | --- | --- |
| `new Chunk` (arreglo de bloques) | 0,5–10 ms | 1,9 MB |
| Generar terreno | 15–22 ms | 1,3 MB |
| Malla opaca (35.800 caras, 4,1 MB de vértices) | 165–410 ms | **140,8 MB** |
| Malla transparente | 3–10 ms | 0,9 MB |

| Cruzar un borde (9 chunks, 3 hilos) | Resultado |
| --- | --- |
| Hasta tener los 9 chunks | 780–1040 ms en promedio (cada cruce, de 300 a 1800 ms) |
| Memoria reservada | **905 MB** (101 MB por chunk) |
| GC | 235–370 ms por borde; la pausa más larga, 190–540 ms |
| Retraso máx. de la sonda | igual a la pausa más larga: lo que traba es el GC |
| Hilo principal, crear los 9 chunks | 4–12 ms y 17 MB (a veces 60–100 ms, si justo cae un GC) |

De dónde salen los 141 MB de la malla opaca (medido con un programa aparte en el chunk (0, 0), que reserva 125 MB): **~86 MB buscar vecinos**, ~35 MB la `List<Float>` y los `float[]` de cada cara, 4 MB `getUVs()` (un `float[4]` nuevo por bloque).

### Fase 1, el juego en Linux con OpenGL por software

Con Xvfb y Mesa, semilla 12345, esperando la carga y caminando 15 s. No sirve para los FPS (dibujar tarda 250–400 ms por frame sin GPU), pero confirma que el medidor funciona y muestra frames con **pausas de GC de 70–436 ms**, `mundo` de 8,5 ms al pedir 9 chunks y subir una malla en 2–47 ms.

### Fase 1, en Windows (antes de la fase 2)

La prueba de "Prueba en Windows", hecha por el usuario el 2026-09-25 con el código de la fase 1: semilla 12345, 60 s de juego. Su equipo: 32 procesadores (31 hilos generadores), Java 21, G1, heap máx 8 GB.

| Qué | Resultado |
| --- | --- |
| FPS | 226 en promedio, 4,4 ms por frame: dibujar no es el problema (render 0,1–0,2 ms) |
| Frames lentos (>25 ms) | **40**, el peor de **186 ms**. **37 de los 40 coinciden con una pausa de GC**; los otros 3 son subidas de mallas de 24–29 ms en la carga inicial |
| GC | **60 pausas, 3,2 s en total**; las peores de 130–173 ms |
| Chunks al caminar | malla de 109–187 ms en promedio y **78–108 MB reservados por chunk**; terreno de 9–38 ms |
| Carga inicial (81 chunks a la vez) | malla de 845 ms en promedio, 133 MB por chunk, el peor de 2,9 s |
| Hilo principal | reservó 25–114 MB cada 5 s; una vez `mundo` tardó 140 ms al pedir 9 chunks, con una pausa de GC de 135 ms en ese frame |

El usuario notó que algunas cargas de chunks van fluidas y otras dan un tirón: cuadra con el GC, que solo pausa cuando se llena la memoria nueva, no en cada borde.

### Fase 2, aquí (mismo equipo)

`MedirChunks`: una corrida antes de los cambios (igual a las de la fase 1) y tres después, todas con "El mundo no cambió":

| Paso (un chunk, un hilo) | Antes: tiempo / memoria | Después: tiempo / memoria |
| --- | --- | --- |
| Malla opaca | 357 ms / **140,8 MB** | 6–15 ms / **4,1 MB** (son los vértices que devuelve) |
| Malla transparente | 12 ms / 0,9 MB | 2 ms / 0,0 MB |
| Un chunk completo (con `new Chunk` y el terreno) | 397 ms / 145 MB | 27–40 ms / 7,3 MB |

| Cruzar un borde (9 chunks, 3 hilos) | Antes | Después |
| --- | --- | --- |
| Hasta tener los 9 chunks | 923 ms en promedio | 123–194 ms (96–100 ms con el heap ya tocado) |
| Memoria reservada | 904 MB (100 MB por chunk) | **58 MB** (6 MB por chunk; el primer cruce, ~95 MB, porque cada hilo agranda su lista de vértices) |
| GC por borde | 378 ms | **4–12 ms** |
| Pausa más larga | 354 ms | **17 ms** en dos corridas y 59 ms en otra (12–16 ms con el heap ya tocado) |
| Hilo principal, crear los 9 chunks | 4–6 ms (una vez 49 ms) | 4–7 ms en una corrida; 84–219 ms en las otras dos, esperando memoria nueva de la VM (ver "Cómo medir") |

Con un programa aparte, `getBlockGlobal()` con 81 chunks cargados (lo usan las colisiones, el rayo de romper/poner y `getAlturaSuperficie()`, en el hilo principal): **~160 ns y 132 bytes → 21–37 ns y 24 bytes** por llamada (queda el `Long` de la clave).

### Fase 2, el juego en Linux con OpenGL por software

Semilla 12345, con Xvfb y Mesa (2–6 FPS, así que los FPS no sirven). El jugador apareció al lado de un tronco, así que caminó con A (hacia −x) y cruzó un borde:

- Carga inicial: 81 chunks armados con **6 MB reservados por chunk** en promedio (antes ~100 MB). Hubo 5 pausas de GC (84–99 ms en total, la peor de 62–63 ms) mientras se subían las mallas; después, ninguna.
- Al cruzar el borde: 9 chunks armados con 5 MB cada uno y **ninguna pausa de GC**. Pero `mundo` tardó **159 ms** sin GC: es crear los 9 chunks (18 MB) justo cuando el heap creció (de 634 a 660 MB), o sea la espera por memoria nueva de la VM. Esa reserva es lo primero de la fase 3. En Windows hay que mirar cuánto da `mundo`.

### Segunda prueba en Windows: corrió sin la fase 2

El usuario repitió la prueba el 2026-09-25 para medir la fase 2 (semilla 12345, ~15 s quieto y ~80 s caminando). Salió igual que la de la fase 1, y la memoria por chunk muestra que **el juego no tenía la fase 2**: con ella cada chunk reserva ~6 MB (hasta ~12 MB en la carga inicial, mientras cada uno de los 31 hilos agranda su lista una vez), y aquí siguió en 59–133 MB. Lo más probable es que faltara traer la rama o que el IDE corriera clases viejas. **Hay que repetirla.** Ese mismo día, `MedirChunks` sobre el último commit de la rama dio 6 MB por chunk y "El mundo no cambió".

| | Fase 1 en Windows | Esta prueba (tampoco tenía la fase 2) |
| --- | --- | --- |
| Frames lentos (>25 ms) | 40, el peor de 186 ms; 37 con GC | 43, el peor de 162 ms; **42 con GC** (el otro, de 29 ms, es subir una malla de 4,6 MB en la carga inicial) |
| GC | 60 pausas, 3,2 s | 68 pausas, 3,3 s; la peor de 160 ms |
| Chunks al caminar | malla de 109–187 ms, 78–108 MB por chunk | malla de 61–211 ms, **59–124 MB** por chunk; terreno de 7–50 ms |
| Carga inicial (81 chunks) | malla de 845 ms, 133 MB por chunk, el peor de 2,9 s | malla de 883 ms, **133 MB** por chunk, el peor de 2,75 s |
| Hilo principal | 25–114 MB cada 5 s | 43–96 MB cada 5 s caminando (7–9 MB quieto) |

Lo que sí sirve de esta prueba, porque la fase 2 no lo cambia, es **cuánto tarda en Windows lo que ataca la fase 3**:

- `mundo` (crear los 9 chunks al cruzar un borde): 4–11 ms como máximo en cada resumen. Una vez 89 ms, con una pausa de GC de 85 ms en ese frame.
- `mallas` (subir una malla, 2,5–4,6 MB): 2–11 ms como máximo al caminar, y 27 ms una vez en la carga inicial. Las de 40 y 91 ms tuvieron GC.
- El monitor va a 240 Hz (4,2 ms por frame), así que eso es perder 1 o 2 frames al cruzar un borde: mucho menos que las pausas de GC de 30–160 ms que había en casi todos los frames lentos.

### Las caras de los bordes (punto 2 de "Cosas a revisar" en `ARQUITECTURA.md`)

Las mallas ahora tardan ~10 ms en vez de ~350, así que el chunk de al lado tiene menos tiempo para generarse antes de que se lea su borde. Para ver si eso empeoraba los huecos en los bordes, un programa aparte hizo lo mismo que el juego (3 hilos, del chunk más cercano al más lejano, cada malla apenas está su terreno) y comparó cada malla con la correcta, armada con todos los vecinos ya generados. Tres corridas de cada una:

| | Código de la fase 1 | Código de la fase 2 |
| --- | --- | --- |
| Carga inicial: caras que faltan (de 2.954.230) | 79.500–79.600, en 77 de 81 chunks | 78.700–79.300, en 76–77 de 81 chunks |
| Cruzar un borde: caras que faltan (de 343.971) | 1.000–1.100, en 5–6 de 9 chunks | 1.000–1.700, en 4–7 de 9 chunks |

La fase 2 casi no lo cambia. Lo que sí muestra es que **los huecos existen**: al cargar el mundo falta ~1 de cada 37 caras, todas en los bordes de los chunks. Se arregla en la fase 5.

## Fases

### Fase 1: medir (sin cambios visibles) — hecha

- Medidor de frames en el juego (`debug/MedidorRendimiento`, prendido con `Constants.MEDIR_RENDIMIENTO`).
- `herramientas/MedirChunks.java` y la referencia `herramientas/referencia_mallas.txt`.
- Para que la herramienta pueda armar mallas sin los hilos del juego, `World` tiene `agregarChunk()`, que solo usa ella.
- Falta la prueba en Windows (ver arriba).

**Lista cuando:** hay números. Cuánto duran los tirones, en qué parte del ciclo caen y si coinciden con el GC.

### Fase 2: mallas sin basura (`Float` y vecinos) — hecha

La medición dice que esto es lo que más pesa. Todo tiene que dar **exactamente** las mismas mallas.

**Lo que se hizo** (en `ChunkMeshBuilder` y `World`):

- Los vecinos dentro del chunk se leen de su arreglo. Los 4 chunks de al lado se buscan una vez por malla con `World.getChunk()`, y sus bordes se leen de sus arreglos.
- Los vértices se juntan en `ListaFloats`, un `float[]` que crece solo, y cada cara escribe sus 6 vértices directo, en el mismo orden que antes. Hay una lista por hilo (`ThreadLocal`) que se reutiliza de malla en malla: así crece una sola vez por hilo (hasta ~8 MB) y no en cada malla. Solo se reserva la copia justa que se devuelve (4,1 MB en promedio). Las listas se liberan cuando terminan los hilos, al salir del mundo.
- Las coordenadas de textura de cada bloque se calculan una vez, en la tabla `UVS`. `getUVs()` sigue igual para el fondo del menú.
- La clave de los chunks en `World` se mezcla (el paso final de SplitMix64), así su `hashCode()` ya no choca. `getBlockGlobal()` y `setBlockGlobal()` buscan el chunk una vez y no dos (antes `containsKey` y `get`).
- Un detalle: antes, los bloques del propio chunk se buscaban en el mapa. Si el chunk salía del rango mientras se armaba su malla, esta salía con todas las caras (y se tiraba igual). Ahora se leen de su arreglo.

**Falta** la prueba en Windows (ver la tabla).

Lo que se había planeado:

- **Vecinos:** si el vecino está dentro del chunk, leerlo directo del arreglo `blocks`. Para los bloques del borde, buscar los 4 chunks vecinos **una sola vez** al empezar la malla, no una vez por bloque. Ojo, para que salga igual hay que respetar lo que hace hoy `getBlockGlobal()`: si el chunk vecino no está en el mapa, es aire; si está, se lee su bloque aunque todavía no tenga terreno (ceros = piedra).
- **`Float`:** cambiar la `List<Float>` por un `float[]` que crece solo (una clase chiquita con `add` y `toArray`). Escribir los 30 floats de cada cara directo, sin el `float[]` temporal y con el mismo orden de vértices.
- **`getUVs()`:** calcular las coordenadas de cada tipo de bloque una vez (una tabla), en vez de un `float[4]` nuevo por bloque. Es poco (4 MB), pero es fácil.
- Opcional: arreglar el hash de las claves de `World` (`chunkX ^ chunkZ` choca muchísimo), que también usan las colisiones y el rayo de romper/poner en el hilo principal.
- Con `MedirChunks`: que diga "El mundo no cambió" y anotar cuánto bajaron la memoria y el tiempo por chunk y las pausas del GC.

**Lista cuando:** las mallas salen idénticas, cada chunk deja mucha menos basura y en Windows hay menos tirones.

### Fase 3: aliviar el hilo principal

**Antes de empezarla**, repetir la prueba de la fase 2 en Windows. Si ya casi no hay frames lentos al caminar, lo que queda en el hilo principal son esos 4–11 ms por borde (ver "Segunda prueba en Windows"): esta fase pasa a ser un retoque y se puede achicar (por ejemplo, solo reservar el arreglo de bloques en el hilo generador) o saltar.

Según lo que diga la medición en Windows:

- Reservar el arreglo de bloques en el hilo generador, no en `actualizarMundo()` (hoy 4–12 ms y 17 MB en el hilo principal por cruce). Pasar a un arreglo plano (`int[]` con un índice calculado) en vez de `int[][][]` es una mejora extra, pero toca `Chunk`, `WorldGenerator` y `ChunkMeshBuilder`. Si sale muy grande, dejarlo para después. `MedirChunks` crea los chunks y genera el terreno por su cuenta: habrá que ajustarla.
- Subida a la GPU:
  - Reutilizar un solo buffer nativo en vez de reservar uno por malla.
  - Usar `GL_STATIC_DRAW`.
  - Darle a cada frame un presupuesto de tiempo para subir mallas (por ejemplo 2–3 ms), en vez de "una malla por frame" sin importar su tamaño.

**Lista cuando:** la medición ya no muestra picos en `mundo` ni en `mallas`.

### Fase 4: hilos generadores

- Usar `núcleos − 2` hilos (mínimo 1) con prioridad baja, para dejarle CPU al hilo principal. `MedirChunks` usa su propio pool con `núcleos − 1`, igual que `World`: cambiarlo también.
- Saltarse los chunks que ya quedaron fuera de rango cuando les toca empezar, y no subir sus mallas.
- Opcional: no pedir los 9 chunks de golpe, sino primero los que están en la dirección en que camina el jugador.

**Lista cuando:** caminar en línea recta ya no da tirones visibles.

### Fase 5 (opcional): menos cosas que dibujar

Esto no quita tirones, pero sube los FPS en general:

- No dibujar los chunks que quedan fuera de la vista de la cámara (*frustum culling*, con `FrustumIntersection` de JOML).
- Arreglar las caras de los bordes de chunk (punto 2 de "Cosas a revisar" en `ARQUITECTURA.md`): cuando llega un chunk, volver a armar la malla de los vecinos que ya estaban cargados.

## Fuera de este plan

- Cambiar `CHUNK_SIZE` de 48 a 16. Repartiría mejor el trabajo, pero cambia la generación (el `Random` es por chunk), así que las semillas darían otros mundos. Solo si lo anterior no alcanza.
- *Greedy meshing* (juntar caras iguales en rectángulos grandes), niveles de detalle y guardar chunks en disco.
- Cambiar de GC (por ejemplo ZGC) o darle más memoria a la JVM: esconde el problema en vez de arreglarlo, y el usuario abre el juego desde el IDE.
