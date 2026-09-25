# Arquitectura del proyecto

Este documento explica cómo está organizado el código y cómo funciona cada parte del juego: qué pasa al arrancar, qué se hace en cada frame, cómo se generan y dibujan los chunks, qué bloques hay y dónde se cambia cada cosa.

## Paquetes

Todo el código vive en `src/main/java/com/minejava/`:

```text
com/minejava/
├── Main.java          Punto de entrada: ventana, OpenGL y ciclo principal
├── EstadoJuego.java   Qué pantalla se dibuja: menú, "Crear mundo", "Generando mundo...", partida o pausa
├── Partida.java       Semilla, mundo, jugador y cámara de una partida
├── render/            Todo lo que habla con la GPU
├── world/             Datos del mundo: chunks, bloques, interacción
│   └── gen/           Generación procedural del terreno
├── player/            Jugador, cámara y entrada (teclado y ratón)
├── ui/                Interfaz 2D (menú de inicio, crear mundo, pantalla de carga, pausa, texto, hotbar y mira)
├── debug/             Medidor de rendimiento: frames lentos, GC y chunks en la consola
└── config/            Constantes de configuración
```

Fuera del código del juego está `herramientas/`, con dos programas que generan imágenes del juego (ver "Texto y título"), uno que mide los chunks sin pantalla (ver "Medir el rendimiento") y dos que revisan sin pantalla los bordes de los chunks y al jugador (ver "Cómo medir" en `PLAN_AGUA_JUGADOR.md`).

| Paquete | Clase | Qué hace |
| --- | --- | --- |
| `com.minejava` | `Main` | Lo que dura todo el programa: crea la ventana y el contexto de OpenGL, carga shaders, textura y fuente, guarda el estado actual y corre el ciclo del juego. *Un jugador* abre *Crear mundo*; *Crear mundo* crea la `Partida` con la semilla y la empieza cuando el chunk del spawn está listo. Con ESC pausa y reanuda (en *Crear mundo*, ESC es Cancelar); *Salir al menú* libera la partida. Sus callbacks del teclado y de las letras le pasan a *Crear mundo* lo que se escribe. `Main.Launcher` tiene el `main()`. |
| `com.minejava` | `EstadoJuego` | `MENU_PRINCIPAL`, `CREAR_MUNDO`, `GENERANDO_MUNDO`, `JUGANDO` o `PAUSA`: le dice al ciclo qué dibujar en cada frame. |
| `com.minejava` | `Partida` | Lo que pertenece a un mundo: su semilla, el `World`, el `PlayerController` y la `Camera`. Avisa cuándo el chunk del spawn está listo (`estaLista()`); entonces calcula el spawn y activa `Input` (`comenzar()`). En cada frame mueve al jugador, carga chunks y dibuja el mundo y el HUD. `pausar()` y `reanudar()` apagan y prenden `Input`; `cleanup()` apaga `Input` y libera el mundo. |
| `render` | `ShaderProgram` | Compila y enlaza el vertex y el fragment shader. `readResource()` lee un `.glsl` del classpath. |
| `render` | `Texture` | Carga una imagen del classpath con STB y la sube a la GPU con filtro `GL_NEAREST` (pixelado). Guarda su tamaño (`getAncho()`, `getAlto()`). |
| `render` | `ChunkMeshBuilder` | Convierte los bloques de un chunk en un `float[]` de vértices, dibujando solo las caras visibles. Lee los vecinos de los arreglos de bloques (el suyo y los de los 4 chunks de al lado) y junta los vértices en una lista por hilo que se reutiliza, así casi no deja basura. |
| `world` | `World` | Guarda los chunks activos, decide cuáles cargar (los más cercanos primero) y cuáles descargar, los dibuja y resuelve romper/poner bloques. `estaGenerado(x, z)` dice si el terreno de un chunk ya existe. `estaCerrado()` avisa a los hilos que el mundo ya se liberó. `getChunk(x, z)` da un chunk por sus coordenadas de chunk. Tiene el `WorldGenerator` de su semilla. `agregarChunk()` solo lo usa `herramientas/MedirChunks.java`. |
| `world` | `Chunk` | Un pedazo de 48 × 200 × 48 bloques con sus mallas (opaca y transparente) en la GPU. |
| `world` | `Block` | Los IDs de todos los bloques y `isSolid()`. |
| `world.gen` | `WorldGenerator` | Llena un chunk: terreno, cuevas, ríos, minerales, árboles, cactus y nubes. Hay uno por mundo, hecho con su semilla; lo que es al azar sale de un `Random` propio de cada chunk (ver "Crear mundo y la semilla"). |
| `world.gen` | `Biome` | Los biomas (llanura, desierto, océano) con su bloque de superficie y relleno. |
| `world.gen` | `BiomeProvider` | Elige el bioma de cada columna con el ruido Perlin del mundo. |
| `world.gen` | `PerlinNoise` | Ruido Perlin 2D de 3 octavas. Cada mundo tiene el suyo: su tabla se mezcla con la semilla. |
| `world.gen` | `Semilla` | Convierte lo que se escribe en el campo *Semilla* en la semilla del mundo: vacío → una al azar, un número → ese número, otro texto → su `hashCode()`. |
| `player` | `PlayerController` | Movimiento del jugador y colisiones contra los bloques. |
| `player` | `Camera` | Posición y rotación de la cámara; calcula la matriz de vista y la dirección a la que miras. |
| `player` | `Input` | Callbacks de GLFW: ratón (mirar, romper, poner, pick block, rueda) y teclas 1–9 de la hotbar. `init()` los registra al empezar la partida y al volver de la pausa; `desactivar()` los quita y los libera al pausar y al salir, así que en el menú y en la pausa no existen. |
| `ui` | `Hud` | Dibuja la hotbar (con los bloques en 3D) y la mira. |
| `ui` | `MenuPrincipal` | El menú de inicio: fondo de tierra (`FondoTierra`), el título (`titulo.png`) y los botones *Un jugador* y *Salir*. Lee el ratón cada frame y avisa cuándo se hizo clic en cada botón. |
| `ui` | `PantallaCrearMundo` | La pantalla *Crear mundo*: el fondo de tierra, el título, el campo *Semilla* con una ayuda debajo y los botones *Crear mundo* y *Cancelar*. Lee el ratón cada frame como `MenuPausa`; el teclado se lo pasa `Main`. |
| `ui` | `CampoTexto` | Un campo de texto de una línea como los de Minecraft: fondo negro, borde blanco y un `_` que parpadea. Se escribe y se borra al final, y se puede pegar con Ctrl+V. |
| `ui` | `PantallaGenerando` | La pantalla de "Generando mundo...": el fondo de tierra con ese texto en el centro. |
| `ui` | `MenuPausa` | La pausa: el mundo desenfocado (`FondoDesenfocado`) con una capa oscura, el título *Juego en pausa*, los botones *Volver al juego* y *Salir al menú* y la semilla del mundo debajo. Lee el ratón cada frame como `MenuPrincipal`. |
| `ui` | `FondoDesenfocado` | Copia lo que se acaba de dibujar (`glReadPixels`), lo desenfoca en la CPU y lo guarda en una textura que se reutiliza en cada pausa. |
| `ui` | `FondoTierra` | El fondo de las pantallas de menú: la casilla de tierra del atlas repetida por toda la pantalla y oscurecida. |
| `ui` | `Boton` | Un rectángulo gris con texto que sabe si el ratón está encima. Cuando lo está, se aclara, le sale un borde blanco y el texto se pone amarillo claro. |
| `ui` | `Texto` | Dibuja texto con la fuente de píxeles `fuente.png`, con sombra como en Minecraft. Sabe medir un texto, centrarlo y decir si una letra está en la fuente. |
| `debug` | `MedidorRendimiento` | Mide cuánto tarda cada parte de los frames de la partida, el GC y el trabajo de los hilos generadores, y lo imprime en la consola (ver "Medir el rendimiento"). |
| `config` | `Constants` | Tamaño y título de la ventana, sensibilidad del ratón, posición inicial, bloques de la hotbar y si el medidor de rendimiento está prendido (`MEDIR_RENDIMIENTO`). |

Recursos en `src/main/resources/`:

| Archivo | Uso |
| --- | --- |
| `shaders/vertex.glsl` | Aplica `projection * view * model` a cada vértice. |
| `shaders/fragment.glsl` | Pinta con la textura; si la coordenada de textura es negativa pinta agua (azul, 60 % opaca) o nube (blanco). |
| `textures/terrain_atlas.png` | Atlas de 4 × 4 texturas de bloques. |
| `textures/fuente.png` | Fuente de píxeles propia: 16 × 16 casillas de 8 × 12, una por carácter (Latin-1). Sale de `herramientas/GenerarFuente.java`. |
| `textures/titulo.png` | El título del menú, hecho con la tipografía MINECRAFT PE. Sale de `herramientas/GenerarTitulo.java`. |

Los recursos se leen del classpath con `getResourceAsStream` (por ejemplo `"/shaders/vertex.glsl"`), así que funcionan desde el IDE, desde cualquier carpeta y dentro del `.jar`.

## Cómo arranca el juego

`Main.Launcher.main()` → `new Main().run()` → `init()`, `loop()` y `cleanup()`.

`init()` hace esto, en orden:

1. Inicializa GLFW y crea la ventana de 1280 × 720, de tamaño fijo, con V-Sync (`glfwSwapInterval(1)`).
2. Deja el cursor visible y activa los botones "pegajosos" del ratón (`GLFW_STICKY_MOUSE_BUTTONS`), para que un clic muy rápido no se pierda entre dos frames del menú. Registra el callback del teclado, que avisa cuando se aprieta ESC y le pasa las demás teclas a *Crear mundo*, y el de las letras (`glfwSetCharCallback`), que solo usa el campo de la semilla.
3. Crea el contexto de OpenGL y pone el color del cielo (`glClearColor`).
4. Compila los shaders, carga `terrain_atlas.png` y la fuente (`new Texto()`) y crea el `MenuPrincipal`, que carga `titulo.png`, la `PantallaCrearMundo` y el `MenuPausa`, que crea la textura del fondo desenfocado.
5. Crea la matriz de proyección (FOV de 70°, planos 0.1 y 1000).
6. Arranca en el estado `MENU_PRINCIPAL`. Todavía no hay mundo.

Al hacer clic en *Un jugador* se abre *Crear mundo* (ver "Crear mundo y la semilla"). Con *Crear mundo* (o Enter), `iniciarPartida(semilla)` hace `new Partida(semilla)` y pasa a `GENERANDO_MUNDO`. El constructor de `Partida` imprime la semilla en la consola y crea el `PlayerController`, la `Camera` y el `World` con una distancia de render de 4 chunks y esa semilla. El `World` todavía no pide ningún chunk: `Partida` busca primero el spawn (`buscarSpawn()`, ver "Dónde aparece el jugador"), lo imprime en la consola ("Spawn: x, z") y llama a `actualizarMundo()` con él. Eso pide generar 9 × 9 = 81 chunks alrededor del spawn, empezando por el suyo.

Mientras tanto se ve "Generando mundo..." (con el cursor todavía visible). Cuando el chunk del spawn, el que contiene su columna, ya tiene su terreno y su malla en la GPU (`partida.estaLista()`), `empezarAJugar()`:

1. Captura el cursor (`GLFW_CURSOR_DISABLED`) y apaga los botones pegajosos (en la partida nadie los lee y un clic quedaría "pegado" para el próximo menú).
2. Llama a `partida.comenzar()`, que busca la altura de la superficie en la columna del spawn (x, z), pone al jugador encima, en (x + 0.5, altura + 1, z + 0.5), y llama a `Input.init()`. `Input.init()` registra los callbacks y pone `firstMouse = true` para que la cámara no salte con el primer movimiento del ratón.
3. Pasa a `JUGANDO`.

Hay que esperar porque, hasta que su terreno se genera, el chunk está lleno de ceros (piedra): la "superficie" saldría en y = 200 y el jugador aparecería encima de las nubes. Esperar también a la malla hace que al entrar ya se vea el suelo.

Al cerrar, `Main.cleanup()` llama a `partida.cleanup()` si hay una partida (apaga `Input` y libera el mundo) y después libera el shader, las texturas (atlas, fuente, título y fondo de la pausa), el VAO del HUD, los callbacks del teclado y de las letras y la ventana.

## El ciclo de cada frame

`Main.loop()` repite esto hasta que se cierra la ventana. Lo que hace en cada frame depende del estado.

Antes de mirar el estado revisa si se apretó ESC desde el frame anterior: en `JUGANDO` llama a `pausar()`, en `PAUSA` a `reanudar()` (ver "Pausa y volver al menú") y en `CREAR_MUNDO` vuelve al menú, como *Cancelar*. Así la pantalla nueva ya se dibuja en ese mismo frame. En el menú y en "Generando mundo..." ESC no hace nada.

**`MENU_PRINCIPAL`:**

1. Limpia la pantalla.
2. `menu.update()`: lee la posición del ratón (`glfwGetCursorPos`) para iluminar el botón que está debajo, y el botón izquierdo (`glfwGetMouseButton`). El clic cuenta al **soltar** el botón, así mantenerlo apretado no cuenta como varios clics.
3. `menu.render()`: dibuja el fondo (la casilla de tierra del atlas repetida en baldosas de 64 px y oscurecida), el título y los dos botones con su texto, con `glOrtho` y `glBegin`/`glEnd` como el HUD.
4. Si se hizo clic en *Un jugador* abre *Crear mundo* (`abrirCrearMundo()`); si fue en *Salir*, marca la ventana para cerrarse.
5. Intercambia buffers y procesa eventos.

**`CREAR_MUNDO`:**

1. Limpia la pantalla.
2. `crearMundo.update()`: hover y clics como en la pausa (el clic cuenta si se aprieta y se suelta sobre el mismo botón). Un Enter que llegó por el callback del teclado cuenta como clic en *Crear mundo*.
3. `crearMundo.render()`: el fondo de tierra, el título, *Semilla*, el campo con lo escrito, la ayuda y los dos botones.
4. Si se hizo clic en *Crear mundo* llama a `iniciarPartida()` con la semilla del campo; si fue en *Cancelar*, vuelve a `MENU_PRINCIPAL`.
5. Intercambia buffers y procesa eventos. Lo que se escribe llega por los callbacks durante `glfwPollEvents`.

**`GENERANDO_MUNDO`:**

1. Limpia la pantalla.
2. `partida.updateGenerando()`: sube a la GPU una malla terminada, igual que en la partida, así al entrar ya se ve buena parte del mundo.
3. `PantallaGenerando.render()`: el fondo de tierra y "Generando mundo..." en el centro.
4. Si `partida.estaLista()`, llama a `empezarAJugar()`.
5. Intercambia buffers y procesa eventos.

**`JUGANDO`:** los pasos 2 a 6 están en `Partida.update()` y los pasos 7 y 8 en `Partida.render()`:

1. Limpia la pantalla.
2. `Input.update()`: revisa las teclas 1–9 de la hotbar.
3. `jugador.update()`: mueve al jugador según WASD, Espacio y Shift, y resuelve colisiones.
4. `camara.updatePosition()`: pone la cámara a la altura de los ojos.
5. Si el jugador cambió de chunk, llama a `mundo.actualizarMundo()` para cargar y descargar chunks.
6. `mundo.procesarMallasPendientes()`: sube a la GPU **una** malla terminada por frame.
7. Pasa las matrices al shader, activa la textura y llama a `mundo.render()`.
8. `Hud.render()`: dibuja la hotbar y la mira encima de todo.
9. Intercambia buffers y procesa eventos (`glfwPollEvents`). Los callbacks del ratón corren aquí.

Entre estos pasos, `Main` y `Partida` llaman a `MedidorRendimiento.marcar()` para medir cuánto tarda cada uno (ver "Medir el rendimiento"). Con el medidor apagado no hace nada.

**`PAUSA`:** no se llama a `partida.update()`, así que el jugador no se mueve, no se cargan chunks y no se suben mallas.

1. Limpia la pantalla.
2. `menuPausa.update()`: igual que el menú principal, hover y clics leyendo el ratón.
3. `menuPausa.render()`: la imagen desenfocada que se guardó al pausar, una capa negra al 50 %, el título, los dos botones y "Semilla: ..." debajo.
4. Si se hizo clic en *Volver al juego* llama a `reanudar()`; si fue en *Salir al menú*, a `salirAlMenu()`.
5. Intercambia buffers y procesa eventos.

## Pausa y volver al menú

- **ESC.** Un callback de teclado que registra `Main` pone `escApretado` en `true` solo cuando la tecla se **aprieta** (`GLFW_PRESS`). Mantenerla apretada manda `GLFW_REPEAT`, que no cuenta, así la pausa no se abre y se cierra sola. El ciclo lee y borra ese aviso una vez por frame. Se usa un callback y no `glfwGetKey` porque así no se pierde un toque más corto que un frame.
- **`pausar()`:**
  1. `partida.pausar()` quita los callbacks de `Input` (`Input.desactivar()`): mover el ratón no gira la cámara y los clics y la rueda no rompen, ponen ni cambian la hotbar. Las teclas 1–9 y WASD se leen en `partida.update()`, que en la pausa no se llama.
  2. Libera el cursor (`GLFW_CURSOR_NORMAL`), lo pone en el centro de la ventana, como Minecraft, y vuelve a activar los botones pegajosos para el menú. Esto va antes del paso 3, así si el ratón se mueve mientras se copia la pantalla no vuelve al centro.
  3. Dibuja el mundo y el HUD una vez más, sin moverlos, y `menuPausa.abrir(semilla)` los copia y los desenfoca (`FondoDesenfocado.capturar()`) y guarda el texto "Semilla: ..." de la partida. El HUD queda adentro de esa imagen, detrás del desenfoque.
- **`reanudar()`** (ESC o *Volver al juego*): captura el cursor, apaga los botones pegajosos y llama a `partida.reanudar()`, que vuelve a llamar a `Input.init()`. Este pone `firstMouse = true`, así la cámara no salta aunque el ratón se haya movido durante la pausa.
- **`salirAlMenu()`:** `partida.cleanup()` quita los callbacks de `Input` y llama a `World.cleanup()`, que libera los chunks de la GPU y apaga los hilos. Después `partida = null` y vuelve a `MENU_PRINCIPAL`. El cursor ya está libre desde la pausa.
- **Callbacks de `Input`.** Cada `glfwSet...Callback` devuelve el callback anterior, que hay que liberar con `.free()`. Si no, sigue guardando la cámara y el mundo, y el mundo de cada partida quedaría en memoria para siempre. `Input.desactivar()` los pone en `null` y libera los anteriores; `Input.init()` también libera los que hubiera antes de poner los nuevos.
- **Clics en la pausa.** Un clic solo cuenta si se aprieta y se suelta sobre el mismo botón. Así, si el botón del ratón venía apretado desde la partida, soltarlo sobre *Salir al menú* no saca del mundo.

### El fondo desenfocado (`FondoDesenfocado`)

Como el mundo no se mueve en la pausa, el desenfoque se calcula una sola vez, al entrar, y cada frame solo se dibuja esa imagen:

1. `glReadPixels` copia la pantalla (1280 × 720) antes de `glfwSwapBuffers`.
2. Se achica a 1/4 (320 × 180) promediando cada cuadro de 4 × 4 píxeles.
3. Desenfoque gaussiano separable (primero las filas, después las columnas) con `SIGMA` = 2 píxeles de la copia chica, o sea unos 8 píxeles de la pantalla. En los bordes repite el último píxel.
4. Se sube a una textura con filtro lineal (no `GL_NEAREST` como los bloques) y `GL_CLAMP_TO_EDGE`. Al estirarla a toda la pantalla queda suave.

Hay **una sola textura** para todas las pausas: se crea con el `MenuPausa` y cada `capturar()` reemplaza su imagen, así no se acumulan. Se hace en la CPU y no con un shader porque es una vez por pausa: con el código ya optimizado por Java tarda unos 10 ms; la primera pausa de cada sesión, un poco más.

## Crear mundo y la semilla

### La pantalla (`PantallaCrearMundo` y `CampoTexto`)

- Se abre con *Un jugador*. Tiene el fondo de tierra del menú, el título *Crear mundo*, la etiqueta *Semilla* en gris, el campo de texto, la ayuda "Déjala vacía para una semilla al azar" y, abajo, *Crear mundo* y *Cancelar* uno al lado del otro, como en Minecraft. Todo se centra junto en la pantalla.
- Cada vez que se abre, el campo empieza vacío.
- **Teclado.** Cada ventana tiene un solo callback de teclado y es el de `Main` (el de ESC), así que `Main` le pasa a la pantalla las demás teclas (`tecla()`) y las letras de `glfwSetCharCallback` (`escribir()`), pero solo mientras el estado es `CREAR_MUNDO`. `glfwSetCharCallback` da la letra ya lista (mayúsculas, tildes y ñ según el teclado del sistema), algo que no se puede sacar de las teclas sueltas.
- **El campo** acepta solo las letras que están en la fuente (`Texto.tieneLetra()`): lo que se ve es exactamente lo que se usa para la semilla. Hasta 32 letras, como en Minecraft. Borrar quita la última (manteniéndola apretada sigue borrando, porque también cuenta `GLFW_REPEAT`) y Ctrl+V pega el portapapeles, saltándose los saltos de línea y las letras que no están en la fuente. Si el texto no entra, se ve el final. El `_` parpadea cada 0,3 s y se ve siempre justo después de escribir o borrar.
- **ESC** es *Cancelar* (lo resuelve `Main.loop()`, igual que la pausa) y **Enter** es *Crear mundo*.

### De texto a semilla (`Semilla.desdeTexto()`)

Como Minecraft, sin contar los espacios del principio y del final:

- Vacío → una semilla al azar (`new Random().nextLong()`).
- Un número entero, también negativo → ese mismo número. `0` también vale.
- Cualquier otro texto → su `hashCode()`. Por ejemplo, "hola" siempre da 3208380. Un número que no cabe en un `long` (más de 19 cifras) cuenta como texto.

La semilla se ve en la pausa ("Semilla: 12345") y se imprime en la consola al crear el mundo.

### Cómo la semilla hace el mundo

- **Forma del terreno y biomas.** Cada `World` crea un `WorldGenerator` con su semilla, y este un `PerlinNoise` propio: la semilla decide cómo se mezcla su tabla de permutación y cuánto se corre cada octava (ver "Dónde aparece el jugador"). `BiomeProvider` usa ese mismo ruido.
- **Cuevas, minerales, árboles, cactus y la roca madre.** Antes usaban `Math.random()`, que cambia cada vez. Ahora cada chunk usa su propio `Random`, creado con la semilla y la posición del chunk, igual que Minecraft: `new Random((chunkX * a + chunkZ * b) ^ semilla)`, donde `a` y `b` son dos números impares que salen de `new Random(semilla)`. Así chunks vecinos no quedan con semillas parecidas.
- Por eso **la misma semilla da el mismo mundo** y **un chunk sale igual si se descarga y se vuelve a cargar**: lo que sale depende solo de la semilla y de la posición del chunk, no de en qué orden ni en qué hilo se genera. Los bloques que rompiste o pusiste sí se pierden al descargar el chunk, porque todavía no hay guardado.
- El seno y el coseno de las cuevas y las nubes usan `StrictMath`, que da exactamente el mismo resultado en cualquier equipo (con `Math` podría cambiar el último bit, y eso basta para mover una nube).
- Con la semilla 12345 la forma del terreno era la misma que cuando estaba fija. Desde el arreglo del spawn ya no: el desplazamiento de las octavas cambia la forma de todos los mundos, también la de 12345.
- `WorldGenerator` lo usan varios hilos a la vez: no guarda nada que cambie al generar.

**Probado sin pantalla:**

- Con la misma semilla, los 81 chunks alrededor de (0, 0) salen idénticos generándolos uno por uno o con 4 hilos en otro orden, y un chunk generado otra vez sale idéntico.
- Con semillas distintas, el chunk (0, 0) cambia entre el 14 y el 22 % de sus bloques.
- Generar un chunk tarda lo mismo que antes, unos 15 ms.
- En Linux con una pantalla virtual (Xvfb) y OpenGL por software: dos mundos con la semilla 12345, esperando a que carguen todos los chunks, dieron capturas idénticas píxel por píxel; uno sin semilla cambió el 96 % de la pantalla. *Cancelar*, ESC (también mantenida) y Enter funcionan, el campo empieza vacío cada vez, pegar filtra las letras que no están en la fuente y corta en 32, y la pausa muestra la semilla (12345, 3208380 para "hola", −5).

## El mundo: chunks y generación

### Chunks

- Cada `Chunk` mide `CHUNK_SIZE` × `CHUNK_HEIGHT` × `CHUNK_SIZE` = 48 × 200 × 48 bloques.
- Los bloques se guardan en `int[x][y][z]`, un ID por bloque. Son unos 1.8 MB de RAM por chunk (~150 MB con 81 chunks).
- `World` guarda los chunks activos en un `ConcurrentHashMap<Long, Chunk>`. La clave junta `chunkX` y `chunkZ` en un `long` y lo mezcla con el paso final de SplitMix64, que a claves distintas les da números distintos. Sin mezclar, el `hashCode()` de ese `Long` era `chunkX ^ chunkZ`: las 81 claves caían en 16 cubetas y buscar un chunk era lento y reservaba memoria (ver la fase 2 de `PLAN_OPTIMIZACION.md`).
- Para pasar de coordenadas del mundo a coordenadas del chunk se usa `Math.floorDiv` y `Math.floorMod`, así funciona bien con coordenadas negativas.

### De "hace falta un chunk" a "se ve en pantalla"

```text
Hilo principal                         Hilos secundarios (núcleos - 1)
──────────────                         ───────────────────────────────
actualizarMundo()
  ├─ crea el Chunk vacío y lo guarda
  └─ manda la tarea al pool ─────────► generarTerrenoAsincrono()
     (los más cercanos primero)
                                          ├─ WorldGenerator.generateTerrain()  (solo la 1.ª vez)
                                          └─ ChunkMeshBuilder: malla opaca y transparente
                                       ◄── lo mete en la cola chunksListosParaGL
procesarMallasPendientes()
  └─ saca 1 chunk de la cola y sube sus vértices a la GPU (VAO/VBO)
render()
  └─ dibuja los chunks que ya están listos
```

OpenGL solo se puede usar desde el hilo principal. Por eso los hilos secundarios dejan los vértices en `float[]` y el hilo principal los sube después.

- `actualizarMundo()` ordena los chunks que faltan por distancia al jugador antes de mandarlos al pool, que los atiende en el orden en que llegan. Así el chunk donde está el jugador sale primero: al empezar una partida es el del spawn, y la pantalla de "Generando mundo..." dura una fracción de segundo en vez de esperar a que se generen la mitad de los 81.
- `Chunk.terrenoGenerado` es `volatile`: lo escribe un hilo secundario y lo lee el principal (`World.estaGenerado()`). Así, cuando el hilo principal lo ve en `true`, también ve los bloques que se escribieron antes.
- Los hilos del pool son *daemon* y `World.cleanup()` usa `shutdownNow()`, que descarta los chunks que esperan en la cola. Con `shutdown()` se generarían igual después de vaciar el mapa y, como ya no tienen vecinos, cada malla saldría con todas las caras: cerrar el juego en "Generando mundo..." dejaba el proceso varios minutos vivo.
- Los chunks que ya se estaban generando no se pueden descartar. Por eso `World.cleanup()` también pone `cerrado = true` (`volatile`): `Chunk` no empieza la malla si el mundo ya se cerró, y `ChunkMeshBuilder` deja de armarla a la mitad (lo revisa en cada columna x). Sin esto, al salir al menú los hilos seguían varios segundos armando mallas con todas las caras.

Los chunks que quedan a más de `renderDistance` del jugador se liberan (`chunk.cleanup()`) y se sacan del mapa.

Cuando rompes o pones un bloque, `setBlockGlobal()` cambia el ID y vuelve a mandar el chunk al pool. El terreno no se regenera (`terrenoGenerado` ya es `true`), solo se reconstruye la malla.

### Generación del terreno (`WorldGenerator`)

`generateTerrain()` trabaja en 5 pasos sobre el arreglo del chunk:

1. **Terreno.** Para cada columna calcula la altura con ruido Perlin (base de 74 a 94), la aplana cerca de los ríos y la hunde en los océanos (46 a 60). Las cuentas de una columna están en `ruidoBioma()`, `distanciaAlCanal()` y `alturaColumna()`, que también usa `buscarSpawn()`. Rellena de abajo hacia arriba: roca madre en y = 0 (y con probabilidad hasta y = 4), pizarra profunda por debajo de 38, mezcla de pizarra y piedra entre 38 y 43, piedra más arriba, y en las últimas capas el bloque de relleno y de superficie del bioma (o arena en playas y fondos de agua). Lo que queda vacío por debajo de y = 68 (`NIVEL_AGUA`) se llena de agua.
2. **Cuevas.** Cráteres esféricos con "gusanos" que suben, y a veces una entrada desde la superficie. No excavan cerca del agua para no inundar las cuevas.
3. **Ríos.** Vuelve a tallar los cauces sobre tierra firme y los llena de agua.
4. **Minerales, árboles y cactus.** Vetas de carbón (hasta y = 80) y de hierro (hasta y = 50) dentro de piedra o pizarra. Árboles con 10 % de probabilidad en pasto o tierra plana, solo en llanura y con al menos 3 bloques de separación. Cactus de 1 a 3 bloques en la arena del desierto.
5. **Nubes.** Bloques de nube en y = 150–151, agrupados en celdas de 12 × 12.

Los biomas los decide `BiomeProvider` con ruido a gran escala: océano si el valor es menor a 0.45, desierto si es mayor a 0.65 y llanura en el resto.

Todo sale de la semilla del mundo: la forma del terreno y los biomas del ruido Perlin, y las cuevas, minerales, árboles, cactus y la roca madre del `Random` de cada chunk. La misma semilla siempre da el mismo mundo (ver "Crear mundo y la semilla").

### Dónde aparece el jugador (`buscarSpawn()`)

**El problema.** Siempre aparecías en un río. El ruido Perlin vale 0 en los puntos enteros de su cuadrícula, así que `PerlinNoise.getNoise(0, 0)` daba 0.5 con cualquier semilla. `WorldGenerator` pone el centro de un río donde el ruido del agua está cerca de 0.5 (`distanciaAlCanal < 0.04`), y el spawn estaba fijo en (0, 0).

**El arreglo** tiene dos partes. La lógica del terreno (umbrales, ríos, biomas, océanos, cuevas y árboles) no cambió.

1. **La semilla corre el ruido.** `PerlinNoise` saca de la semilla un desplazamiento por octava, con decimales y distinto en x y en z, y se lo suma a las coordenadas antes de calcular el ruido. Se sacan del mismo `Random` justo después de mezclar la tabla, así la tabla sale igual que antes. Van de 0 a 256 porque el ruido se repite cada 256. Así (0, 0) deja de ser especial y cada semilla tiene otro terreno ahí.
2. **Spawn en tierra firme, como Minecraft.** `WorldGenerator.buscarSpawn()` recorre las columnas en espiral cuadrada desde (0, 0) (1 paso a +x, 1 a +z, 2 a −x, 2 a −z, 3 a +x…) y se queda con la primera donde `esTierraFirme()`: el bioma no es océano, no es río (`distanciaAlCanal` de 0.04 o más, el mismo umbral que el cauce) y `alturaColumna()` queda por encima de `NIVEL_AGUA`. Usa las mismas cuentas que `generateTerrain()` (las fórmulas de la columna se sacaron a métodos, no se copiaron), solo con el ruido: no genera ningún chunk. Si no hay tierra firme hasta `RADIO_BUSQUEDA_SPAWN` (2048 bloques), devuelve (0, 0).

`Partida` lo usa para todo: el `World` ya no pide chunks alrededor de (0, 0) al crearse, sino que `Partida` busca el spawn y llama a `actualizarMundo()` con él; `estaLista()` espera al chunk del spawn y `comenzar()` pone al jugador en esa columna. La misma semilla siempre da el mismo spawn.

**Probado sin pantalla** (con un programa aparte, en Linux):

- Antes: `getNoise(0, 0)` daba exactamente 0.5 en 1000 de 1000 semillas. Ahora, en 509 semillas, va de 0.24 a 0.79 y no es 0.5 en ninguna. En (0, 0) sigue habiendo río en un 30 % de las semillas, pero ahí la espiral sigue buscando.
- En 509 semillas, generando de verdad el chunk del spawn: ninguna columna del spawn tiene agua. En el 46 % el spawn queda en (0, 0) mismo; la mediana está a 2 bloques y el más lejano a 571. Con 5000 semillas, el más lejano a 734.
- La espiral no se salta nada: para los spawns a menos de 300 bloques se revisó que ninguna columna más cercana (por anillos) fuera tierra firme.
- La misma semilla da el mismo spawn y los mismos chunks.
- `buscarSpawn()` tarda 0.01 ms la mitad de las veces y como mucho unos 160 ms (en 5000 semillas), cuando el spawn queda a cientos de bloques. Se hace una vez, al darle a *Crear mundo*.
- Sacar las fórmulas a métodos no cambió nada: antes de tocar `PerlinNoise`, 7 semillas × 25 chunks dieron exactamente los mismos bloques que el código anterior.

**Lo que puede pasar todavía:**

- **Aparecer en un hoyo de cueva.** Los "gusanos" de las cuevas que suben a veces llegan a la superficie: pasa en un 3.8 % de todas las columnas de tierra firme, no solo en el spawn. El ruido no sabe de cuevas (salen del `Random` de cada chunk), así que `buscarSpawn()` no lo ve. En 6 de 509 semillas el spawn quedó en el fondo de uno de esos hoyos, sin agua. Ver el punto 7 de "Cosas a revisar".
- **Aparecer encima de un árbol** (13 % de las semillas): `getAlturaSuperficie()` cuenta las hojas como suelo.
- Muchas veces el spawn queda en la orilla de un río, porque es la primera columna fuera del cauce. Es tierra firme, a pocos bloques por encima del agua.

## Bloques

Todos los IDs están en `world/Block.java`. Del 0 al 14, el ID también dice qué casilla del atlas usa: columna = ID % 4, fila = ID / 4, contando desde arriba a la izquierda.

| ID | Constante | Bloque | ¿Se genera? | Casilla de la hotbar |
| --- | --- | --- | --- | --- |
| -1 | `AIR` | Aire | Sí | – |
| 0 | `STONE` | Piedra | Sí | 1 |
| 1 | `BEDROCK` | Roca madre | Sí (y ≤ 4) | 2 |
| 2 | `DIRT` | Tierra | Sí | 3 |
| 3 | `GRASS` | Pasto | Sí | 4 |
| 4 | `WOOD` | Tronco | Sí (árboles) | 5 |
| 5 | `LEAVES` | Hojas | Sí (árboles) | 6 |
| 6 | `PLANKS` | Tablones | No | – |
| 7 | `BRICKS` | Ladrillos | No | – |
| 8 | `CACTUS` | Cactus | Sí (desierto) | – |
| 9 | `COBBLESTONE` | Adoquín | No | – |
| 10 | `OBSIDIAN` | Obsidiana | No | – |
| 11 | `IRON_ORE` | Mineral de hierro | Sí | – |
| 12 | `COAL_ORE` | Mineral de carbón | Sí | – |
| 13 | `DEEPSLATE` | Pizarra profunda | Sí | – |
| 14 | `SAND` | Arena | Sí | – |
| 15 | `WATER` | Agua | Sí | – |
| 16 | `CLOUD` | Nube | Sí | – |

- El agua y las nubes no usan el atlas. `ChunkMeshBuilder` les pone coordenadas de textura negativas (−1 y −2) y el fragment shader las pinta con un color fijo. La casilla 15 del atlas existe, pero no se usa.
- `Block.isSolid(id)` es `true` para todo menos aire, agua y nube. Los bloques sólidos chocan con el jugador y se pueden apuntar para romper o poner.

### Cambiar la hotbar

La hotbar es la lista `Constants.BLOQUES_HOTBAR`. Se puede agregar, quitar o reordenar bloques ahí, hasta 9 (las teclas 1–9). El HUD, la rueda, las teclas y el pick block se ajustan solos. Por ejemplo, para poder poner tablones, agrega `Block.PLANKS` a la lista.

### Agregar un bloque con textura nueva

El atlas de 4 × 4 ya está lleno. Para un bloque nuevo hay que:

1. Hacer un atlas más grande (por ejemplo 8 × 8) conservando las casillas actuales en el mismo orden.
2. Cambiar `atlasSize` y `columnas` en `ChunkMeshBuilder.getUVs()` y en `Hud.buildSingleCube()` (el cálculo está repetido en los dos).
3. Agregar la constante en `Block` con un ID a partir de 17, porque el 15 y el 16 ya son agua y nube.
4. Agrandar la tabla `UVS` de `ChunkMeshBuilder`: tiene una casilla por ID hasta `Block.CLOUD`.

## Render

- **Formato de vértice:** 5 floats (x, y, z, u, v). Cada cara visible son 2 triángulos, o sea 6 vértices. No se usan índices.
- **Caras visibles:** `ChunkMeshBuilder.shouldRenderFace()` mira al bloque vecino. Si está en el mismo chunk, lo lee de su arreglo; si está en uno de los 4 chunks de al lado, del arreglo de ese chunk, que se busca una sola vez por malla con `world.getChunk()`. Si ese chunk no está cargado, el vecino cuenta como aire, y si está cargado pero todavía sin terreno, como piedra (ceros). Arriba del todo (y = 199) la cara de arriba se ve; la de abajo de y = 0, no. Un bloque sólido solo dibuja las caras que dan al aire, al agua o a una nube. El agua no dibuja caras contra otra agua ni contra sólidos.
- **Sin basura:** cada cara escribe sus 6 vértices directo en `ListaFloats`, un `float[]` que crece solo. Hay una por hilo (`ThreadLocal`) y se reutiliza de malla en malla; al final se devuelve una copia del tamaño justo. Las coordenadas de textura de cada bloque salen de la tabla `UVS`, calculada una vez con `getUVs()`. Antes, con una `List<Float>` y buscando cada vecino en el mapa de chunks, una malla reservaba 141 MB y ahora 4 MB (ver la fase 2 de `PLAN_OPTIMIZACION.md`).
- **Dos pasadas:** `World.render()` dibuja primero la malla opaca de todos los chunks (sin blending) y luego la transparente, que solo tiene el agua (con blending y sin escribir profundidad). Las nubes van en la malla opaca.
- **HUD:** `Hud` dibuja el fondo, los marcos y la mira con OpenGL antiguo (`glOrtho` + `glBegin`/`glEnd`). Después dibuja un cubo 3D girado por casilla usando el mismo shader con una proyección ortográfica. Funciona porque el contexto que crea GLFW no es "core profile".

## Texto y título

### La fuente (`Texto` y `fuente.png`)

- `fuente.png` mide 128 × 192: 16 × 16 casillas de 8 × 12 píxeles, blancas sobre transparente. La casilla de cada carácter es su código Latin-1 (columna = código % 16, fila = código / 16), así que entran á, é, í, ó, ú, ñ, ü, ¿ y ¡ con sus mayúsculas.
- Dentro de la casilla, las mayúsculas y los números ocupan las filas 3 a 9, las minúsculas las filas 5 a 9 (con las astas desde la 3), las colas de g, j, p, q, y bajan hasta la 11 y los acentos de las mayúsculas van en las filas 0 y 1.
- Al cargarla, `Texto` lee la imagen otra vez con `ImageIO` para medir cada letra hasta su última columna pintada, como Minecraft: la "i" ocupa menos que la "m". Entre letra y letra deja una columna libre; el espacio mide 3.
- Para las coordenadas de textura usa el mismo cálculo que `ChunkMeshBuilder.getUVs()`, con la fila invertida porque `Texture` voltea la imagen.
- `dibujar(texto, x, y, escala)` pone la esquina de arriba a la izquierda en (x, y). La escala es entera (2 en los botones) para que los píxeles salgan parejos. Primero dibuja una sombra un píxel de la fuente más abajo y a la derecha, cuatro veces más oscura.
- `dibujarCentrado()` centra el texto en horizontal y la altura de las mayúsculas en vertical. `ancho()` mide un texto.
- Se encarga solo del blending (lo guarda y lo deja como estaba con `glPushAttrib`/`glPopAttrib`), pero necesita que ya esté puesto el `glOrtho` de la pantalla.
- Un carácter que no está en la fuente se dibuja como `?`. Ojo con `…` (puntos suspensivos): no es Latin-1, hay que escribir `...`.

Para cambiar o agregar letras se edita `herramientas/fuente.txt`, que tiene los glifos dibujados con `#` y `.` (el formato está explicado al principio del archivo), y se corre desde la carpeta del proyecto:

```text
java herramientas/GenerarFuente.java
```

Eso vuelve a escribir `src/main/resources/textures/fuente.png`. La fuente es dibujada para este proyecto, así que no tiene problemas de derechos de autor.

### El título (`titulo.png`)

- Es una imagen de 590 × 143 con "MINECRAFT" y debajo "JAVA CLONE", hecha con la tipografía **MINECRAFT PE** (SpideRaY, kiddiefonts.com). `MenuPrincipal` la dibuja a su tamaño real, centrada, 56 px arriba del primer botón.
- Las letras de esa tipografía son solo el contorno. `GenerarTitulo` rellena el interior con la casilla de piedra del atlas, reducida a 16 × 16 y ampliada ×3, como el logo de Minecraft.
- MINECRAFT PE es gratis **solo para uso personal** y tiene todos los derechos reservados. Por eso el `.ttf` no está en el repo (`.gitignore` ignora los `.ttf`): solo la imagen que sale de él. Si algún día el proyecto fuera comercial, habría que pedir una licencia o cambiar de tipografía.
- Para cambiar el texto o el tamaño se editan `RENGLONES` y `TAMANOS` en `herramientas/GenerarTitulo.java` y se corre con la ruta al `.ttf`:

```text
java herramientas/GenerarTitulo.java ruta/a/MINECRAFT_PE.ttf
```

La tipografía solo tiene mayúsculas (las minúsculas salen como mayúsculas) y no trae tildes ni ñ.

## Jugador y controles

- **Movimiento:** vuelo libre, sin gravedad. WASD según hacia dónde miras, Espacio sube y Shift baja. La velocidad es de 0.12 bloques **por frame**, así que depende de los FPS (con V-Sync a 60 Hz son ~7 bloques por segundo).
- **Colisiones:** caja de 0.56 de ancho (radio 0.28) y 1.8 de alto. El movimiento se prueba eje por eje, así el jugador se desliza por las paredes en vez de quedarse pegado.
- **Cámara:** los ojos están a 1.62 sobre los pies. La inclinación está limitada a ±89°.
- **Romper y poner:** `World.interactuarConTerreno()` avanza un rayo desde la cámara en pasos de 0.03 hasta 5 bloques. Romper borra el primer bloque sólido que encuentra. Poner coloca el bloque seleccionado en la última posición vacía antes de él, salvo que choque con el jugador.
- **Pick block (clic central):** si el bloque que miras está en la hotbar, selecciona esa casilla.
- **Hotbar:** `Input` guarda la **casilla** seleccionada (empieza en la 4, el pasto). `getSelectedBlockType()` traduce esa casilla al ID del bloque con `Constants.BLOQUES_HOTBAR`.

## Medir el rendimiento

Es la fase 1 de `PLAN_OPTIMIZACION.md`, que tiene los detalles y las mediciones.

- **En el juego:** `debug/MedidorRendimiento`, prendido con `Constants.MEDIR_RENDIMIENTO`. Solo mide los frames de `JUGANDO`. Cada frame se parte en `limpiar`, `jugador`, `mundo` (`actualizarMundo()`), `mallas` (`procesarMallasPendientes()`), `render` y `swap` (con V-Sync, la espera al monitor). Imprime en la consola, con el prefijo `[medidor]`, cada frame de 25 ms o más con su desglose y si hubo GC; cada 5 s de juego un resumen (FPS, peor frame, GC, heap y chunks pedidos, armados, subidos y descartados); y al terminar la partida el total. Además, `Chunk.generarTerrenoAsincrono()` le avisa cuánto tardó cada chunk y cuánta memoria reservó su hilo, y `World` y `Chunk` cuántos chunks se pidieron, subieron o descartaron. Lo que imprime va sin tildes, para que se vea bien en cualquier consola de Windows.
- **Sin pantalla:** `herramientas/MedirChunks.java` mide un chunk en un solo hilo (tiempo y memoria de cada paso), simula cruzar un borde con los hilos generadores (GC y un hilo "sonda" que hace de hilo principal) y comprueba que el mundo no cambió comparando los bloques y las mallas con `herramientas/referencia_mallas.txt`. Necesita las clases del juego compiladas:

```text
mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp "target/classes:$(cat target/classpath.txt)" herramientas/MedirChunks.java
```

En Windows (PowerShell) el separador es `;`: `java -cp "target/classes;$(Get-Content target/classpath.txt)" herramientas/MedirChunks.java`.

## Dónde cambiar cada cosa

| Qué | Dónde |
| --- | --- |
| Resolución y título de la ventana | `Constants.SCREEN_WIDTH`, `SCREEN_HEIGHT`, `WINDOW_TITLE` |
| Sensibilidad del ratón | `Constants.MOUSE_SENSITIVITY` |
| Bloques de la hotbar | `Constants.BLOQUES_HOTBAR` |
| Distancia de render (en chunks) | `Partida.RENDER_DISTANCE` (4 → 9 × 9 chunks) |
| Tamaño y altura del chunk | `Chunk.CHUNK_SIZE` (48) y `Chunk.CHUNK_HEIGHT` (200) |
| Nivel del agua | `WorldGenerator.NIVEL_AGUA` (68) |
| Cómo se convierte el texto en semilla | `Semilla.desdeTexto()` |
| Pantalla *Crear mundo* (textos, tamaños, distancias y largo máximo de la semilla) | `PantallaCrearMundo`: `TITULO`, `ETIQUETA`, `AYUDA`, `MAX_LETRAS`, `ANCHO` y las distancias de arriba abajo |
| Aspecto del campo de texto (borde, margen, parpadeo del `_`) | `CampoTexto`: `BORDE`, `MARGEN`, `COLOR_TEXTO` y `PARPADEO` |
| Frecuencia de biomas | `BiomeProvider.BIOME_SCALE` y sus umbrales |
| Velocidad del jugador | `PlayerController.speed` |
| Campo de visión | `Main.init()`, `Math.toRadians(70.0f)` |
| Botones del menú (tamaño, posición y texto) | `MenuPrincipal`: `ANCHO_BOTON`, `ALTO_BOTON`, `SEPARACION` y el constructor |
| Color de los botones y tamaño de su texto | `Boton`: `GRIS` y `ESCALA_TEXTO` |
| Letras de la fuente | `herramientas/fuente.txt` y volver a correr `GenerarFuente` |
| Texto del título | `RENGLONES` en `herramientas/GenerarTitulo.java` y volver a correrlo con el `.ttf` |
| Posición del título | `MenuPrincipal.ESPACIO_TITULO` (distancia al primer botón) |
| Fondo del menú y de "Generando mundo..." | `FondoTierra`: el bloque en `dibujar()`, `BRILLO` y `TAM_BALDOSA` |
| Título, botones, semilla y posición de la pausa | `MenuPausa`: `TITULO`, `ESCALA_TITULO`, `ANCHO_BOTON`, `ALTO_BOTON`, `SEPARACION`, `ESPACIO_TITULO`, `ESPACIO_SEMILLA`, `GRIS_SEMILLA` y el constructor |
| Qué tan oscura es la capa de la pausa | `MenuPausa.OSCURIDAD` (0 = nada, 1 = negro) |
| Cuánto se desenfoca el fondo de la pausa | `FondoDesenfocado.SIGMA` (y `REDUCCION`, cuánto se achica antes) |
| Texto de la pantalla de carga | `PantallaGenerando.TEXTO` |
| Dónde aparece el jugador | `WorldGenerator.buscarSpawn()` (la espiral), `esTierraFirme()` (qué cuenta como tierra firme) y `RADIO_BUSQUEDA_SPAWN` (hasta dónde busca) |
| Color del cielo | `Main.init()`, `glClearColor` |
| Color del agua y de las nubes | `shaders/fragment.glsl` |
| Prender o apagar el medidor de rendimiento | `Constants.MEDIR_RENDIMIENTO` |
| Desde cuántos ms un frame es lento y cada cuánto sale el resumen | `MedidorRendimiento.FRAME_LENTO_MS` y `RESUMEN_CADA_MS` |

`Constants.PLAYER_START_POSITION` casi no tiene efecto: en cuanto el chunk del spawn está listo, el jugador se mueve a la superficie del spawn, en (x + 0.5, altura + 1, z + 0.5).

## Dependencias y plataforma

- Java 21, Maven, LWJGL 3.3.3 (GLFW, OpenGL, STB) y JOML 1.10.5.
- El `pom.xml` solo trae las librerías nativas de **Windows** (`natives-windows`). Para correrlo en Linux o macOS hay que agregar las dependencias con `natives-linux` o `natives-macos`.

## Cosas a revisar

Estas salen de leer el código. Las que no dicen "confirmado" no las he probado en el juego: conviene confirmarlas antes de arreglarlas.

1. **Desfase de medio bloque (confirmado sin pantalla).** `ChunkMeshBuilder` dibuja cada bloque centrado en su coordenada entera (de x − 0.5 a x + 0.5), pero las colisiones y el rayo para romper/poner usan `Math.floor`, o sea que tratan al bloque como si ocupara de x a x + 1. Por eso el jugador flota medio bloque sobre el suelo, se mete en los bloques por algunas caras y choca antes por otras, y el 68–76 % de las veces rompe otro bloque que el que está bajo la mira (`herramientas/RevisarJugador.java`). El usuario lo notó en Windows. Se arregla en la fase 2 de `PLAN_AGUA_JUGADOR.md`, junto con el spawn un bloque más arriba (`Partida.comenzar()`).
2. **Huecos en los bordes de chunk (confirmado sin pantalla).** Un chunk nuevo empieza lleno de ceros, y 0 es `STONE`. Si la malla de un chunk se arma antes de que su vecino termine de generarse, las caras del borde se ocultan como si hubiera piedra al lado, y no se vuelven a calcular cuando el vecino ya está listo. Pasa algo parecido al romper un bloque justo en el borde, porque solo se reconstruye la malla de ese chunk y no la del vecino. Como los chunks se generan de adentro hacia afuera, el del spawn siempre arma su malla antes que sus vecinos. Un programa aparte que carga el mundo como el juego (semilla 12345, 3 hilos) encontró que faltan ~79.000 de 2,95 millones de caras, en 77 de los 81 chunks, antes y después de la fase 2 de la optimización (ver "Las caras de los bordes" en `PLAN_OPTIMIZACION.md`). Muchas están bajo tierra, pero donde el terreno sube justo en un borde tendría que verse un hueco. Con el agua es peor: las paredes de agua que se dibujan contra un chunk que todavía no está cargado se quedan cuando llega, y se ven como rayas oscuras en los ríos y el mar (el usuario lo vio en Windows; `herramientas/RevisarBordes.java`). El arreglo está en la fase 1 de `PLAN_AGUA_JUGADOR.md`.
3. **La ventana es de tamaño fijo** (`GLFW_RESIZABLE` en falso) porque ni `glViewport`, ni la proyección, ni el HUD, ni los botones del menú, ni el fondo de la pausa se ajustan a otro tamaño: todos usan `SCREEN_WIDTH` y `SCREEN_HEIGHT`. Para poder redimensionarla habría que recalcular todo eso cuando cambia el tamaño.
4. **La velocidad depende de los FPS**, porque el movimiento se suma por frame y no por tiempo transcurrido. A los ~226 FPS del equipo del usuario son ~27 bloques por segundo. Se arregla en la fase 3 de `PLAN_AGUA_JUGADOR.md`.
5. **Las teclas 1–9 se leen una vez por frame** con `glfwGetKey`. Con pocos FPS, un toque más corto que un frame se puede perder. Se notó al probar con OpenGL por software (unos 10 FPS); con V-Sync a 60 FPS no debería pasar. Si pasa, se arregla igual que ESC: con un callback de teclado.
6. **~~Todos los mundos tienen agua justo donde aparece el jugador.~~ Arreglado.** El ruido Perlin valía 0,5 en (0, 0) con cualquier semilla, justo el centro de un río, y el jugador aparecía en el fondo, bajo 4 a 16 bloques de agua. Ahora la semilla corre el ruido y el spawn se busca en espiral hasta encontrar tierra firme (ver "Dónde aparece el jugador").
7. **Las cuevas abren hoyos en la superficie.** Los "gusanos" de las cuevas suben 120 a 160 pasos desde y = 12–30 y muchas veces atraviesan el suelo: en un 3.8 % de las columnas de tierra firme la superficie queda más abajo de lo que dice el ruido (medido sin pantalla, 20 semillas × 25 chunks). Solo se protegen las columnas con agua o arena. A veces el spawn cae en uno de esos hoyos (6 de 509 semillas). Si molesta, se podría revisar la columna del spawn después de generar su chunk y, si quedó más abajo que `alturaColumna()`, seguir la espiral; o no dejar que los gusanos pasen de cierta altura (eso sí cambia las cuevas).
