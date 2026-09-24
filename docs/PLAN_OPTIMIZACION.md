# Plan: optimización (tirones al cargar chunks)

**Estado:** pendiente. Es lo siguiente después del menú de inicio, que ya está terminado y probado (ver `PLAN_MENU_INICIO.md`).

## El problema

Al caminar, los chunks nuevos se generan bien, pero **el juego se traba un poco cada vez que carga chunks nuevos**. Lo reportó el usuario al probar en Windows el 2026-09-24.

## Cómo trabajamos

Igual que con el menú: una fase por conversación y `/clear` entre fases (ver `CLAUDE.md`). Además, en este plan:

- **Medir antes y después de cada fase** con lo que se hace en la fase 1, y anotar los números en la tabla. Sin números no sabemos si algo mejoró.
- **El mundo no puede cambiar.** Con la misma semilla deben salir los mismos bloques y las mismas mallas. La herramienta de la fase 1 lo comprueba.
- Para arrancar una fase basta con algo como: *"Empieza la fase 1 de optimización (docs/PLAN_OPTIMIZACION.md)"*.

## Estado de las fases

| Fase | Estado | Commit | Medición (antes → después) | Qué falta probar en Windows |
| --- | --- | --- | --- | --- |
| 1. Medir | pendiente | | | |
| 2. Mallas sin objetos `Float` | pendiente | | | |
| 3. Aliviar el hilo principal | pendiente | | | |
| 4. Hilos generadores | pendiente | | | |
| 5. Menos cosas que dibujar (opcional) | pendiente | | | |

## Qué pasa hoy al cruzar a otro chunk

Esto sale de leer el código; **todavía no está medido**. La fase 1 dice cuál de estos pesa de verdad.

1. **Mallas hechas con millones de objetos `Float` (el sospechoso principal).** `ChunkMeshBuilder` junta los vértices en una `List<Float>`: cada número es un objeto aparte en memoria, y además cada cara arma un `float[]` temporal. Un chunk de 48 × 200 × 48 tiene muchísimas caras, así que cada malla deja una montaña de basura. Para limpiarla, el recolector de basura (GC) de Java a veces tiene que pausar *todos* los hilos, incluido el que dibuja, y eso se ve como un tirón.
2. **Llegan 9 chunks de golpe.** Con `CHUNK_SIZE = 48` y distancia de render 4 (9 × 9 chunks), cruzar un borde pide una fila entera de 9 chunks grandes al mismo tiempo. Los hilos generadores son `núcleos − 1` y compiten con el hilo principal y con el GC por la CPU.
3. **Trabajo en el hilo principal.** `World.actualizarMundo()` crea los 9 `Chunk` en el hilo principal, y cada uno reserva un `int[48][200][48]` (unos 1,8 MB repartidos en ~9.600 arreglos chicos). Además, `Chunk.cargarMallaEnOpenGL()` sube una malla completa por frame: copia todo a un buffer nativo nuevo y lo manda con `glBufferData`.
4. **Trabajo que se tira.** Si el jugador camina rápido, se siguen generando chunks que ya quedaron fuera de rango, porque su tarea no se cancela.

## Fases

### Fase 1: medir (sin cambios visibles)

- **Medidor de frames en el juego.** Cada frame de `JUGANDO` mide cuánto tarda cada parte del ciclo: el jugador y la cámara, `actualizarMundo()`, `procesarMallasPendientes()`, el render y el swap. Si un frame tarda más de ~25 ms, imprime en consola el desglose y si hubo GC en ese frame (con `GarbageCollectorMXBean`: cuántas veces y cuánto tiempo). Cada ~5 s imprime un resumen: FPS promedio, el peor frame, cuántos frames lentos hubo y el GC. Se prende y se apaga con una constante en `Constants` (por ejemplo `MEDIR_RENDIMIENTO`).
- **Herramienta sin pantalla** (en `herramientas/`, como `GenerarTitulo.java`), para medir aquí sin GPU. Genera y arma las mallas de un bloque de chunks con una semilla fija, y reporta el tiempo y la memoria reservada por chunk (`ThreadMXBean.getThreadAllocatedBytes`). También guarda un resumen de cada malla (tamaño y hash) para comprobar en las fases siguientes que el mundo sale idéntico.
- **Prueba en Windows.** El usuario corre el juego, camina en línea recta durante ~1 minuto y pega la salida de la consola. Anotar los números en la tabla.

**Lista cuando:** hay números. Cuánto duran los tirones, en qué parte del ciclo caen y si coinciden con el GC.

### Fase 2: mallas sin objetos `Float`

- Cambiar la `List<Float>` por un `float[]` que crece solo (una clase chiquita con `add` y `toArray`). Escribir los 30 floats de cada cara directo, sin el `float[]` temporal y con el mismo orden de vértices.
- Con la herramienta de la fase 1: comprobar que las mallas salen idénticas (mismo hash) y cuánto bajaron la memoria y el tiempo por chunk.

**Lista cuando:** las mallas salen idénticas, cada chunk deja mucha menos basura y en Windows hay menos tirones.

### Fase 3: aliviar el hilo principal

Según lo que diga la medición:

- Reservar el arreglo de bloques en el hilo generador, no en `actualizarMundo()`. Pasar a un arreglo plano (`int[]` con un índice calculado) en vez de `int[][][]` es una mejora extra, pero toca `Chunk`, `WorldGenerator` y `ChunkMeshBuilder`. Si sale muy grande, dejarlo para después.
- Subida a la GPU:
  - Reutilizar un solo buffer nativo en vez de reservar uno por malla.
  - Usar `GL_STATIC_DRAW`.
  - Darle a cada frame un presupuesto de tiempo para subir mallas (por ejemplo 2–3 ms), en vez de "una malla por frame" sin importar su tamaño.

**Lista cuando:** la medición ya no muestra picos en `actualizarMundo()` ni en `procesarMallasPendientes()`.

### Fase 4: hilos generadores

- Usar `núcleos − 2` hilos (mínimo 1) con prioridad baja, para dejarle CPU al hilo principal.
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
