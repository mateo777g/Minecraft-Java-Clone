# Plan: el agua y el jugador

**Estado:** la fase 1 está hecha (2026-09-25; falta probarla en Windows). La siguiente es la 2. `PLAN_OPTIMIZACION.md` sigue en pausa hasta terminar este.

## Lo que reportó el usuario

1. **Agua.** Al entrar al mundo el agua se ve bien, pero en los chunks que se cargan al caminar, los ríos y los mares tienen rayas azul oscuro, rectas y paralelas, que parecen las separaciones entre chunks. En el horizonte también se ve una pared de agua. Tiene que verse azul parejo.
2. **Jugador.** "Mide como 3 bloques" y no pasa por una puerta de 2 bloques de alto. Al construir, en algunos lugares no deja poner bloques y en otros no deja quitar uno que está justo enfrente. Además, según la cara del bloque, atraviesa medio bloque y se ve por dentro.

## Cómo trabajamos

Igual que en los planes anteriores: una fase por conversación y `/clear` entre fases (ver `CLAUDE.md`). Además, en este plan:

- **Medir antes y después** con las dos herramientas sin pantalla que se hicieron para armar este plan (ver "Cómo medir") y anotar los números en la tabla.
- **Los bloques no cambian.** Con la misma semilla tienen que salir los mismos bloques. `herramientas/MedirChunks.java` lo comprueba ("El mundo no cambió"). La fase 2 cambia las mallas **a propósito** (corre todos los vértices medio bloque): ahí hay que comprobar que ese corrimiento sea lo único que cambió y guardar la referencia nueva (ver la fase 2).
- Las fases 1 y 2 no dependen una de la otra: si el usuario prefiere empezar por el jugador, se puede hacer la 2 primero.
- Para arrancar una fase basta con algo como: *"Empieza la fase 1 de agua y jugador (docs/PLAN_AGUA_JUGADOR.md)"*.

## Estado de las fases

| Fase | Estado | Commit | Medición (antes → después) | Qué falta probar en Windows |
| --- | --- | --- | --- | --- |
| 1. Agua sin rayas: bordes de chunk bien armados | hecha (compila; `RevisarBordes` da 0 en las semillas 12345, 7 y 99; "El mundo no cambió"; probada en el juego con OpenGL por software) | `d05e430` | `RevisarBordes`, semilla 12345, avanzando 5 chunks: caras de agua de más **1.217 → 0**; caras opacas que faltan **66.220 → 0** y que sobran 100.464 → 0; paredes de agua contra el borde del mundo 1.561 → 0. Al romper o poner en un borde, la malla del vecino gana o pierde su cara. `MedirChunks`, al cruzar un borde: 11 terrenos y 9 mallas en 89–96 ms (antes 9 chunks en 105–191 ms), 48 MB reservados (antes ~58), hilo principal 5–23 ms y 21 MB (antes 4–12 ms y 17 MB). ~75 MB más de bloques en memoria. Ver "Mediciones de la fase 1". | Semilla 12345: caminar ~1 minuto cruzando agua; el agua tiene que verse pareja, sin rayas, y sin pared de agua en el horizonte. Romper y poner bloques justo en el borde de un chunk. Pegar la salida del medidor (ver la fase 1). |
| 2. El jugador y los bloques en el mismo lugar | pendiente | | Antes (`RevisarJugador`, semillas 12345, 7 y 99): ojos a **3,12** bloques del suelo que se ve al aparecer (2,12 bajando con Shift; en Minecraft, 1,62). Apuntando a un bloque: rompe **otro** el 68–76 % de las veces, no rompe nada el 7–12 % y pone el bloque en otro lugar el 69–76 %. | |
| 3. Velocidad por segundo, no por frame | pendiente | | Antes: 0,12 bloques por frame, o sea **~27 bloques/s** a los 226 FPS de la medición en Windows (7,2 a 60 FPS). | |

## Qué causa cada cosa

### Las rayas del agua

1. **Cada malla decide sus bordes una sola vez.** Para saber qué caras dibujar, `ChunkMeshBuilder` mira el bloque de al lado. En el borde del chunk, ese bloque es del chunk vecino, y si el vecino **no está cargado** cuenta como aire (`bloqueEn()`). Contra el aire, el agua dibuja una pared vertical: es la pared de agua que se ve en el horizonte, en el borde del mundo cargado.
2. **Al caminar, esas paredes se quedan.** Llegan los chunks nuevos al lado de los que estaban en el borde, pero la malla de estos **no se vuelve a armar**: sus paredes siguen ahí, ahora entre dos chunks cargados. El agua se dibuja al 60 % de opacidad y sin escribir profundidad, así que desde arriba esas paredes se ven a través de la superficie como bandas más oscuras que bajan hasta el fondo, una por cada borde de chunk (cada 48 bloques). Esas son las rayas.
3. **Al empezar no pasa** porque los 81 chunks se meten al mapa de golpe: cuando se arma cada malla, sus vecinos ya están en el mapa (aunque sea todavía vacíos, es decir, llenos de piedra) y el agua no dibuja paredes contra la piedra. Solo quedan las del borde del mundo.

Medido con `RevisarBordes` (carga el mundo como el juego y compara cada malla con la correcta, armada con todos los vecinos ya generados):

| Semilla 12345 | Carga inicial | Después de avanzar 5 chunks hacia +x |
| --- | --- | --- |
| Caras de agua de más contra un chunk cargado (las rayas) | **0** | **1.217**, en 23 chunks |
| Caras opacas que faltan (huecos) | 79.627, en 77 chunks | 66.220, en 63 chunks |
| Caras opacas de más (paredes escondidas dentro del terreno) | 2.232 | 100.464 |
| Paredes de agua contra el borde del mundo | 672 | 1.561 |

Con la semilla 7 da lo mismo: 0 caras de agua de más al empezar y 756 (en 26 chunks) después de avanzar.

Es **la misma causa** que el punto 2 de "Cosas a revisar" en `ARQUITECTURA.md` (los huecos en los bordes): el borde de cada malla se calcula una vez con lo que había al lado en ese momento (nada, un vecino todavía vacío o a medio generar) y no se vuelve a calcular cuando eso cambia. Con el agua se ve como rayas; con los bloques sólidos, como caras que faltan donde el terreno sube justo en un borde. También pasa al romper o poner un bloque en el borde de un chunk: solo se vuelve a armar la malla de ese chunk, no la del vecino, y puede quedar un hueco por el que se ve a través del bloque de al lado.

### El jugador

1. **Todo está corrido medio bloque** (punto 1 de "Cosas a revisar", ahora confirmado). `ChunkMeshBuilder` dibuja el bloque (x, y, z) de x − 0.5 a x + 0.5, pero las colisiones, el rayo de romper/poner y el spawn lo tratan como si ocupara de x a x + 1 (usan `Math.floor`). Lo que el juego "siente" está medio bloque más allá, en x, en y y en z, de lo que se ve:
   - **Altura:** los pies quedan medio bloque por encima del suelo que se ve, así que los ojos quedan a 2,12 bloques y no a 1,62.
   - **Paredes:** desde un lado, la pared de verdad está medio bloque adentro del bloque que se ve, así que el jugador se mete en él y la cámara ve por dentro. Desde el lado contrario choca medio bloque antes, contra una pared invisible. Por eso "depende de la cara".
   - **Puertas:** el hueco de verdad está medio bloque al costado y medio bloque más arriba que el que se ve. Para pasar hay que ir pegado a un lado del hueco que se ve, casi metido en la pared, y con los ojos a 2,12 la cámara se mete en el bloque de arriba de la puerta.
   - **Romper y poner:** el rayo recorre la rejilla de las colisiones, no la que se ve. Medido con `RevisarJugador`: rompe **otro** bloque que el que está bajo la mira el 68–76 % de las veces, no rompe nada aunque el bloque esté a menos de 5 el 7–12 %, y pone el bloque en otro lugar el 69–76 %. Poner también falla cuando el jugador "choca" con el bloque nuevo (`intersectsBlock()`), y eso se calcula con el jugador corrido medio bloque.
2. **El jugador aparece un bloque más arriba.** `Partida.comenzar()` pone los pies en `getAlturaSuperficie() + 1`, pero `getAlturaSuperficie()` ya devuelve la cara de arriba del bloque (y + 1). Los pies quedan flotando un bloque sobre el suelo y, sumado al medio bloque de arriba, los ojos quedan a **3,12 bloques** del suelo que se ve: el "mide como 3 bloques". Como no hay gravedad, se queda a esa altura hasta que se baja con Shift, y así la cabeza choca con el bloque de arriba de una puerta de 2.
3. **El rayo avanza a saltos de 0,03.** Aun en la rejilla de las colisiones, en ~1 % de los casos agarra otro bloque y en ~2 % pone el bloque pegado en diagonal (tocando una arista, no una cara), porque "la última posición vacía" puede estar al lado de una arista.
4. **No se puede poner un bloque en el agua.** `World.interactuarConTerreno()` solo pone el bloque si el lugar es aire. El agua no lo es, así que bajo el agua o en un río el clic no hace nada, sin avisar.
5. **La velocidad va por frame** (punto 4 de "Cosas a revisar"): 0,12 bloques por frame. El monitor del usuario va a 240 Hz y en la medición de Windows el juego iba a 226 FPS, o sea **~27 bloques por segundo** en vez de los ~7 que daba a 60 FPS. Así cuesta frenar enfrente de una puerta o acomodarse para construir.

## Cómo medir

Las dos herramientas cargan el mundo con las clases del juego, sin pantalla. Primero hay que compilar:

```text
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" herramientas/RevisarBordes.java [semilla] [chunks]
java -cp "target/classes:$(cat target/classpath.txt)" herramientas/RevisarJugador.java [semilla]
```

- **`RevisarBordes`** (fase 1): busca el spawn, carga el mundo con `World.actualizarMundo()` y sus hilos, "sube" las mallas con `World.sacarMallaParaSubir()`, lo mismo que usa `procesarMallasPendientes()` (guardándolas en vez de mandarlas a la GPU), y avanza hacia +x de a un chunk (5 por defecto). Después rompe un bloque en el borde este del chunk del jugador y pone uno en el borde oeste, con `setBlockGlobal()`. Al empezar, después de avanzar y después de romper y poner, compara cada malla subida con la correcta: caras de agua de más (las rayas), caras opacas que faltan (huecos) y que sobran, paredes de agua contra el borde del mundo, chunks cercanos sin malla y el anillo de afuera (con terreno y sin malla). Tiene que dar 0 en todo; si no, termina con error. También dice a los cuántos ms salió la malla del chunk del spawn. Tarda unos 15 s. Desde la fase 1 ya no lee campos privados: usa `sacarMallaParaSubir()` y `estaTrabajando()`.
- **`RevisarJugador`** (fase 2): genera el chunk del spawn y sus vecinos, mide a qué altura quedan los ojos sobre el suelo que se ve, y tira 20.000 rayos al azar desde los ojos comparando el bloque que rompería y el lugar donde pondría el juego con lo que está bajo la mira en la pantalla. "Lo que se ve" lo mide de la malla (dónde dibuja `ChunkMeshBuilder` cada bloque), así que sirve antes y después de corregirla. "Lo que hace el juego" es una **copia** de `World.interactuarConTerreno()` y de `Partida.comenzar()`: la fase 2 tiene que actualizar esas copias (o llamar a los métodos nuevos).

La prueba en Windows es jugar: se abre `com.minejava.Main.Launcher` desde el IDE, después de `git pull`.

## Fases

### Fase 1: agua sin rayas (bordes de chunk bien armados) — hecha

**La idea**, como en Minecraft: armar la malla de un chunk **solo cuando sus 4 vecinos ya tienen su terreno**. Así se arma una vez y bien, y nunca hay que corregir un borde.

- **Terreno un anillo más allá.** Generar el terreno hasta `renderDistance + 1` (11 × 11 = 121 chunks) y armar y dibujar las mallas solo hasta `renderDistance` (9 × 9 = 81, como hoy). El anillo de afuera solo tiene terreno, sin malla: está para ser vecino.
- **Pedir las mallas cuando están los vecinos.** Cuando un hilo termina un terreno, avisa al hilo principal (por ejemplo, con una cola como `chunksListosParaGL`). El hilo principal, en `procesarMallasPendientes()` o parecido, revisa ese chunk y sus 4 vecinos: cada uno que está dentro de `renderDistance`, todavía no tiene malla pedida y ya tiene a sus 4 vecinos generados, la pide. Decidirlo en el hilo principal evita carreras entre hilos.
- **Descargar.** Más allá de `renderDistance` se libera la malla (GPU); más allá de `renderDistance + 1`, el chunk entero. Si un chunk que perdió su malla vuelve a quedar dentro, se le pide otra.
- **Romper y poner.** Volver a armar la malla del chunk y, si el bloque está en su borde (x o z en 0 o en 47), también la del vecino de ese lado.
- **Una sola malla válida por chunk.** Hoy dos tareas del mismo chunk (por ejemplo, romper dos bloques seguidos) escriben los mismos campos (`pendingOpaqueVertices`, `pendingTransparentVertices`) y puede ganar la más vieja. Que cada tarea devuelva su resultado con un número de versión y que solo se suba la más nueva.
- **"Generando mundo..."** espera la malla del chunk del spawn, que ahora necesita el terreno de sus 4 vecinos. Como todo se pide por distancia, son los primeros 5 terrenos: debería seguir durando muy poco.
- `ChunkMeshBuilder` no debería cambiar: con los vecinos ya generados, arma lo mismo que hoy arma `MedirChunks` en su parte 1.
- **Herramientas.**
  - `RevisarBordes`: ajustarla al flujo nuevo y dejarla como la prueba de esta fase. Tiene que dar **0** caras de más y de menos, de agua y opacas, al empezar y después de avanzar. También agregarle un bloque roto y uno puesto en un borde, y revisar la malla del vecino.
  - `MedirChunks`: la parte 1 tiene que seguir diciendo "El mundo no cambió". La parte 2 (cruzar un borde) simula el flujo del juego con su propio pool: pasarla al flujo nuevo (11 terrenos y 9 mallas por borde) y medir antes y después.
- **Lo que cuesta:** 40 terrenos más en memoria (1,8 MB cada uno, unos 75 MB) y, al cruzar un borde, 11 terrenos y 9 mallas en vez de 9 de cada uno. El terreno tarda ~15 ms por chunk en otro hilo. Medirlo con `MedirChunks` y con el medidor del juego.
- Con esto se cierra el punto 2 de "Cosas a revisar" en `ARQUITECTURA.md`: actualizarlo, junto con "De 'hace falta un chunk' a 'se ve en pantalla'" y "Render". Las caras de los bordes eran parte de la fase 5 de `PLAN_OPTIMIZACION.md`, que ya apunta aquí.
- **Descartado:** solo no dibujar las paredes de agua contra chunks sin cargar. Quita las rayas con una línea, pero deja los huecos de los bloques sólidos, los bordes que quedan mal al romper o poner y las mallas que leen a un vecino a medio generar.

**Probar en Windows:** semilla 12345, caminar ~1 minuto sobre el mar o un río cruzando varios chunks: el agua se ve pareja, sin rayas, y en el horizonte ya no hay pared de agua. Romper y poner bloques justo en el borde de un chunk no deja huecos. Pegar el último resumen del medidor para ver cuánto cuesta el anillo extra.

**Lista cuando:** `RevisarBordes` da 0 caras de más y de menos, `MedirChunks` dice "El mundo no cambió" y en Windows no hay rayas.

#### Lo que se hizo

Como estaba planeado, con estos detalles:

- `World.actualizarMundo()` pide el terreno hasta `renderDistance + 1`. Cuando un hilo termina un terreno, deja el chunk en la cola `terrenosListos`. En cada frame, `procesarMallasPendientes()` llama a `sacarMallaParaSubir()`, que por cada terreno listo revisa ese chunk y sus 4 vecinos y pide las mallas que ya se pueden armar; después saca de la cola `mallasListas` la próxima malla que sirve. `procesarMallasPendientes()` la sube a la GPU. Así las herramientas usan `sacarMallaParaSubir()` sin GPU, igual que el juego.
- `Chunk.generarTerrenoAsincrono()` se partió en `generarTerreno()` y `armarMalla(versión)`, que devuelve un `Chunk.MallaArmada` (el chunk, la versión y los dos `float[]`). Ya no están `pendingOpaqueVertices` ni `pendingTransparentVertices`. `Chunk.liberarMalla()` borra el VAO y el VBO sin descargar el chunk.
- **Las mallas pasan adelante de los terrenos en el pool** (`PriorityBlockingQueue` y `World.Tarea`). No estaba en el plan: sin esto, la malla del spawn quedaba en la cola detrás de los 121 terrenos. Entre dos tareas del mismo tipo va la que se mandó antes, así los terrenos siguen yendo del más cercano al más lejano.
- Una malla se sube solo si es la última versión que se pidió para su chunk y el chunk todavía la quiere (no se alejó ni se descargó). Las demás cuentan como descartadas en el medidor.
- `setBlockGlobal()` vuelve a pedir la malla del chunk y, si el bloque está en su borde, la del vecino de ese lado. Si el chunk todavía no tiene terreno, no hace nada.
- `World.estaTrabajando()` (solo para las herramientas) dice si queda trabajo en los hilos o en las colas: cada tarea descuenta su contador después de dejar su resultado.
- El medidor separa el terreno de las mallas: la línea `chunks:` ahora dice `pedidos` (terrenos pedidos), `terrenos` y `mallas armadas` (cada uno con su tiempo y memoria promedio y el peor), `subidas` y `descartadas`.
- `ChunkMeshBuilder` no cambió (solo un comentario). `MedirChunks` sigue diciendo "El mundo no cambió", y su parte 2 ahora usa el `World` del juego en vez de su propio pool.

#### Mediciones de la fase 1

Aquí (Linux, 4 núcleos, 3 hilos generadores, Java 21).

**`RevisarBordes`**, antes y después:

| Semilla 12345 | Antes: carga inicial | Antes: después de avanzar 5 chunks | Ahora (carga inicial, después de avanzar y después de romper y poner) |
| --- | --- | --- | --- |
| Caras de agua de más (las rayas) | 0 | 1.217, en 23 chunks (faltaban 216) | **0** |
| Caras opacas que faltan (huecos) | 79.561, en 77 chunks | 64.831, en 63 chunks | **0** |
| Caras opacas de más | 2.232 | 100.410 | **0** |
| Paredes de agua contra el borde del mundo | 672 | 1.561 | **0** |

Con las semillas 7 y 99 también da 0 en todo. Al romper un bloque en el borde este, la malla del vecino este gana 1 cara (la que quedó al aire), y al poner uno en el borde oeste, la del vecino oeste pierde 1 (la que quedó tapada). Para comprobar que la prueba sirve, se desactivó un momento el rearmado del vecino: la herramienta lo detectó (1 cara que falta, 1 que sobra, "¡la malla del vecino no se volvió a armar bien!") y terminó con error.

**Lo que tarda "Generando mundo..."** (la malla del chunk del spawn, con la JVM en frío): antes 250–600 ms, ahora 390–640 ms. Ahora necesita el terreno de 5 chunks (el suyo y sus 4 vecinos) antes de la malla; con 3 hilos son dos tandas de terreno. En el equipo del usuario, con 31 hilos, los 5 salen en la misma tanda, así que no debería notarse. Todo el mundo (121 terrenos y 81 mallas) queda listo en 1,7–2,6 s.

**`MedirChunks`**: "El mundo no cambió". Parte 2, cruzar un borde (3 corridas; los tiempos varían mucho en este equipo):

| | Antes (pool propio, 9 chunks con terreno y malla; 4 corridas) | Ahora (el `World`, 11 terrenos y 9 mallas) |
| --- | --- | --- |
| Tiempo por borde | 105–191 ms | 89–96 ms |
| Memoria reservada por los generadores | ~58 MB por borde (98 MB en el primer paso, con la lista de vértices de cada hilo todavía creciendo; 47–49 en los demás) | 48 MB por borde |
| GC | 6–22 ms por borde, la pausa más larga 12–68 ms | 0–3 ms por borde, la pausa más larga 0–14 ms |
| Hilo principal (`actualizarMundo()`) | 4–12 ms, 17 MB | 5–23 ms, 21 MB |

Que ahora tarde menos no quiere decir que sea más rápido: antes el primer paso incluía agrandar las listas de vértices de un pool nuevo, y ahora el `World` ya viene de cargar el mundo. Lo que sí se ve es que los 2 terrenos de más casi no se notan en el total. En el hilo principal, `actualizarMundo()` crea 11 chunks en vez de 9 (1,8 MB cada uno): es lo que ataca la fase 3 de `PLAN_OPTIMIZACION.md`. En memoria, los 40 chunks del anillo son ~75 MB más de bloques (~220 MB en total).

**El juego, con Xvfb y Mesa (OpenGL por software, 2–3 FPS, así que los FPS no sirven).** Manejado con `java.awt.Robot`: *Un jugador*, semilla 12345, esperar la carga, caminar con A hasta cruzar un borde y volver con D. Sin errores. Al empezar: `pedidos 121, terrenos 121, mallas armadas 81`; en cada borde: `pedidos 11, terrenos 11` (~15 ms y 1,2 MB cada uno), `mallas armadas 9` (~4 MB cada una), `subidas 9, descartadas 0`, y `mundo` 6,6 ms. Al volver, las mallas liberadas se pidieron otra vez y se subieron a VAO nuevos.

Para ver las rayas se llevó al jugador, de a un chunk, hasta el chunk 7 y se miró desde arriba el mar que cruza el borde x = 384 (entre los chunks 7 y 8, z ≈ −165). Con el código de antes se ven bandas azul oscuro, rectas, en ese borde y en el de z = −192; con el nuevo, el agua es pareja. En las dos capturas hay unas pocas líneas de 1 píxel sueltas, iguales antes y después y que no siguen los bordes de los chunks: no son de esta fase (probablemente del render por software).

**Prueba en Windows:** `git pull`, abrir `com.minejava.Main.Launcher`, *Un jugador*, semilla **12345**. Esperar a que un resumen diga `terrenos 0, mallas armadas 0` y `subidas 0`. Caminar en línea recta ~1 minuto y, al pasar sobre un río o el mar, mirar hacia abajo: el agua tiene que ser pareja, sin rayas, y en el horizonte no tiene que haber una pared de agua. Romper y poner bloques justo en el borde de un chunk: no tienen que quedar huecos. Pegar la salida del medidor desde `Semilla del mundo: 12345` hasta el total, para ver cuánto cuestan los terrenos de más. Lo del jugador (la altura, la puerta, romper y poner en otro lugar) sigue igual hasta la fase 2.

### Fase 2: el jugador y los bloques en el mismo lugar

- **Correr la malla medio bloque.** En `ChunkMeshBuilder`, cada cara del bloque (x, y, z) va de x a x + 1 (no de x − 0.5 a x + 0.5), y lo mismo en y y en z. Todo lo demás (colisiones, rayo, spawn, generación) ya usa esa convención, que es la de Minecraft, por eso se corrige la malla y no lo demás. El cubo de la hotbar (`Hud`) no cambia.
- **Spawn:** los pies en `getAlturaSuperficie()`, sin el + 1.
- **Rayo exacto.** Cambiar los saltos de 0,03 por un recorrido exacto de la rejilla (el algoritmo de Amanatides y Woo, "DDA"): da el bloque exacto y la cara por la que entra el rayo, y el bloque nuevo va del otro lado de esa cara. Un solo método para romper, poner y el pick block (`getBlockAtCrosshair()`).
- **Poner en el agua:** el lugar puede ser aire o agua, como en Minecraft.
- **El chunk del jugador:** `World.actualizarMundo()` y `Partida.update()` usan `Math.round` para saber en qué chunk está, que cuadra con la malla de hoy. Con la malla corregida corresponde `Math.floor`; con `Math.round` cambiaría de chunk medio bloque antes del borde.
- **Herramientas.**
  - `RevisarJugador`: actualizar sus copias del spawn y del rayo (o llamar al método nuevo). Tiene que dar ojos a **1,62** del suelo que se ve y **0 %** en romper otro bloque, no romper nada, poner en otro lugar y poner en diagonal.
  - `MedirChunks`: las mallas cambian a propósito. Comprobar que la única diferencia sea que todos los vértices se corrieron + 0.5 en x, y y z (las coordenadas de textura, iguales), y guardar la referencia nueva con `--guardar-referencia`. Los bloques no cambian.
- Actualizar en `ARQUITECTURA.md` "Jugador y controles", "Render" y el punto 1 de "Cosas a revisar".

**Probar en Windows:** al aparecer, los ojos a la altura de Minecraft (un poco menos de 2 bloques); pasar por una puerta de 2 de alto; acercarse a un bloque desde las 4 caras, desde arriba y desde abajo y no meterse en él ni chocar antes; romper y poner bloques mirando a distintas caras y bordes: siempre el que está bajo la mira y pegado a la cara que se mira; poner bloques bajo el agua.

**Lista cuando:** `RevisarJugador` da 1,62 y 0 %, `MedirChunks` solo cambió por el corrimiento, y en Windows se construye bien.

### Fase 3: velocidad por segundo, no por frame

- `Main` mide cuánto duró el frame (`glfwGetTime()`) y se lo pasa a `Partida.update()` y a `PlayerController.update()`.
- La velocidad pasa a bloques por segundo: 7,2 (lo que daba a 60 FPS), en una constante fácil de cambiar.
- Limitar el tiempo de un frame (por ejemplo a 0,05 s): si un frame tarda mucho (una pausa del GC), el jugador no avanza de golpe más de lo que las colisiones pueden revisar. Si hace falta, partir el movimiento en pasos más cortos que un bloque.
- Cerrar el punto 4 de "Cosas a revisar" y actualizar "Jugador y controles" en `ARQUITECTURA.md`.

**Probar en Windows:** moverse con WASD, Espacio y Shift: ~7 bloques por segundo y parejo (antes, a 240 Hz, era ~4 veces más rápido). Decir si se siente lento o rápido para ajustar la constante.

**Lista cuando:** la velocidad es la misma a cualquier FPS.

## Fuera de este plan

- **Gravedad, salto y caminar** (hoy el jugador vuela libre, sin gravedad). Lo de "mide 3 bloques" y la puerta se arreglan en la fase 2 sin esto. Si el usuario lo quiere, es un plan o una fase aparte.
- **Un contorno en el bloque que se apunta**, como en Minecraft. Ayudaría a construir, pero es algo nuevo, no un arreglo.
- Guardar los bloques rotos o puestos al descargar un chunk (hoy se pierden: todavía no hay guardado).
