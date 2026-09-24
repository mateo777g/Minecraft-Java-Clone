# Plan: optimización (tirones al cargar chunks)

**Estado:** en curso. La fase 1 (medir) está hecha y medida aquí; falta la medición en Windows. Viene después del menú de inicio, que ya está terminado y probado (ver `PLAN_MENU_INICIO.md`).

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
| 1. Medir | hecha (compila; medida aquí y en el juego con OpenGL por software) | `d264a19` | Aquí: la malla opaca de un chunk reserva **141 MB** y tarda 165–410 ms; cruzar un borde reserva **905 MB** y trae pausas de GC de **100–540 ms**. Ver "Mediciones". | Correr el juego con la semilla 12345, caminar ~1 minuto en línea recta y pegar la salida de la consola (ver "Prueba en Windows"). |
| 2. Mallas sin basura (`Float` y vecinos) | pendiente | | | |
| 3. Aliviar el hilo principal | pendiente | | | |
| 4. Hilos generadores | pendiente | | | |
| 5. Menos cosas que dibujar (opcional) | pendiente | | | |

## Qué pasa hoy al cruzar a otro chunk

Medido en la fase 1 (ver "Mediciones"). Lo que traba la pantalla son **pausas del GC** mientras los hilos generadores arman las mallas: en esas pausas se detienen todos los hilos, también el que dibuja. Lo demás pesa mucho menos.

1. **Buscar los vecinos de cada bloque en el mapa de chunks (lo más pesado, no estaba en el plan).** Para decidir qué caras se ven, `ChunkMeshBuilder` pregunta por los 6 vecinos de cada bloque sólido con `world.getBlockGlobal()`, también cuando el vecino está en el mismo chunk: unas 800.000 veces por malla. Cada vez busca el chunk en el `ConcurrentHashMap<Long, Chunk>` y eso reserva memoria:
   - la clave `long` se convierte en un objeto `Long`, dos veces (`containsKey` y `get`);
   - el `hashCode()` de esa clave es `chunkX ^ chunkZ`, así que las 81 claves caen en **solo 16 cubetas** (hasta 9 en una). Las cubetas tan llenas se vuelven árboles y buscar en ellos llama a `getGenericInterfaces()`, que crea arreglos nuevos cada vez.

   En total son 48 a 112 bytes y ~0,1 µs por vecino: **unos 86 de los 141 MB** de la malla opaca y más o menos la mitad de su tiempo.
2. **Mallas hechas con objetos `Float`.** `ChunkMeshBuilder` junta los vértices en una `List<Float>`: cada número es un objeto aparte y cada cara arma un `float[]` temporal. Son unos 35 MB por malla (35.800 caras, más de un millón de `Float`). Pesa menos que los vecinos, pero es lo que **alarga las pausas**: el log del GC muestra que casi toda la pausa es copiar objetos vivos ("Object Copy"), y mientras una malla se arma su lista está viva. Además, cada vez que la lista crece pide un arreglo de más de 1 MB ("humongous" para G1), y eso dispara GC extra.
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

### Prueba en Windows

1. Traer los cambios de la rama y abrir el juego como siempre (`com.minejava.Main.Launcher` desde el IDE).
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

## Fases

### Fase 1: medir (sin cambios visibles) — hecha

- Medidor de frames en el juego (`debug/MedidorRendimiento`, prendido con `Constants.MEDIR_RENDIMIENTO`).
- `herramientas/MedirChunks.java` y la referencia `herramientas/referencia_mallas.txt`.
- Para que la herramienta pueda armar mallas sin los hilos del juego, `World` tiene `agregarChunk()`, que solo usa ella.
- Falta la prueba en Windows (ver arriba).

**Lista cuando:** hay números. Cuánto duran los tirones, en qué parte del ciclo caen y si coinciden con el GC.

### Fase 2: mallas sin basura (`Float` y vecinos)

La medición dice que esto es lo que más pesa. Todo tiene que dar **exactamente** las mismas mallas.

- **Vecinos:** si el vecino está dentro del chunk, leerlo directo del arreglo `blocks`. Para los bloques del borde, buscar los 4 chunks vecinos **una sola vez** al empezar la malla, no una vez por bloque. Ojo, para que salga igual hay que respetar lo que hace hoy `getBlockGlobal()`: si el chunk vecino no está en el mapa, es aire; si está, se lee su bloque aunque todavía no tenga terreno (ceros = piedra).
- **`Float`:** cambiar la `List<Float>` por un `float[]` que crece solo (una clase chiquita con `add` y `toArray`). Escribir los 30 floats de cada cara directo, sin el `float[]` temporal y con el mismo orden de vértices.
- **`getUVs()`:** calcular las coordenadas de cada tipo de bloque una vez (una tabla), en vez de un `float[4]` nuevo por bloque. Es poco (4 MB), pero es fácil.
- Opcional: arreglar el hash de las claves de `World` (`chunkX ^ chunkZ` choca muchísimo), que también usan las colisiones y el rayo de romper/poner en el hilo principal.
- Con `MedirChunks`: que diga "El mundo no cambió" y anotar cuánto bajaron la memoria y el tiempo por chunk y las pausas del GC.

**Lista cuando:** las mallas salen idénticas, cada chunk deja mucha menos basura y en Windows hay menos tirones.

### Fase 3: aliviar el hilo principal

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
