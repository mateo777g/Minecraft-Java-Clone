# Arquitectura del proyecto

Este documento explica cómo está organizado el código y cómo funciona cada parte del juego: qué pasa al arrancar, qué se hace en cada frame, cómo se generan y dibujan los chunks, qué bloques hay y dónde se cambia cada cosa.

## Paquetes

Todo el código vive en `src/main/java/com/minejava/`:

```text
com/minejava/
├── Main.java          Punto de entrada: ventana, OpenGL y ciclo principal
├── EstadoJuego.java   Qué pantalla se dibuja: menú o partida
├── Partida.java       Mundo, jugador y cámara de una partida
├── render/            Todo lo que habla con la GPU
├── world/             Datos del mundo: chunks, bloques, interacción
│   └── gen/           Generación procedural del terreno
├── player/            Jugador, cámara y entrada (teclado y ratón)
├── ui/                Interfaz 2D (menú de inicio, texto, hotbar y mira)
└── config/            Constantes de configuración
```

Fuera del código del juego está `herramientas/`, con dos programas que generan imágenes del juego (ver "Texto y título").

| Paquete | Clase | Qué hace |
| --- | --- | --- |
| `com.minejava` | `Main` | Lo que dura todo el programa: crea la ventana y el contexto de OpenGL, carga shaders, textura y fuente, guarda el estado actual y corre el ciclo del juego. Crea la `Partida` cuando se aprieta *Un jugador*. `Main.Launcher` tiene el `main()`. |
| `com.minejava` | `EstadoJuego` | `MENU_PRINCIPAL` o `JUGANDO`: le dice al ciclo qué dibujar en cada frame. |
| `com.minejava` | `Partida` | Lo que pertenece a un mundo: el `World`, el `PlayerController` y la `Camera`. Calcula el spawn, activa `Input`, y en cada frame mueve al jugador, carga chunks y dibuja el mundo y el HUD. |
| `render` | `ShaderProgram` | Compila y enlaza el vertex y el fragment shader. `readResource()` lee un `.glsl` del classpath. |
| `render` | `Texture` | Carga una imagen del classpath con STB y la sube a la GPU con filtro `GL_NEAREST` (pixelado). Guarda su tamaño (`getAncho()`, `getAlto()`). |
| `render` | `ChunkMeshBuilder` | Convierte los bloques de un chunk en una lista de vértices, dibujando solo las caras visibles. |
| `world` | `World` | Guarda los chunks activos, decide cuáles cargar y cuáles descargar, los dibuja y resuelve romper/poner bloques. |
| `world` | `Chunk` | Un pedazo de 48 × 200 × 48 bloques con sus mallas (opaca y transparente) en la GPU. |
| `world` | `Block` | Los IDs de todos los bloques y `isSolid()`. |
| `world.gen` | `WorldGenerator` | Llena un chunk: terreno, cuevas, ríos, minerales, árboles, cactus y nubes. |
| `world.gen` | `Biome` | Los biomas (llanura, desierto, océano) con su bloque de superficie y relleno. |
| `world.gen` | `BiomeProvider` | Elige el bioma de cada columna con ruido Perlin. |
| `world.gen` | `PerlinNoise` | Ruido Perlin 2D de 3 octavas con semilla fija (12345). |
| `player` | `PlayerController` | Movimiento del jugador y colisiones contra los bloques. |
| `player` | `Camera` | Posición y rotación de la cámara; calcula la matriz de vista y la dirección a la que miras. |
| `player` | `Input` | Callbacks de GLFW: ratón (mirar, romper, poner, pick block, rueda) y teclas 1–9 de la hotbar. Se registran al crear la `Partida`, así que en el menú no existen. |
| `ui` | `Hud` | Dibuja la hotbar (con los bloques en 3D) y la mira. |
| `ui` | `MenuPrincipal` | El menú de inicio: fondo de tierra oscurecida, el título (`titulo.png`) y los botones *Un jugador* y *Salir*. Lee el ratón cada frame y avisa cuándo se hizo clic en cada botón. |
| `ui` | `Boton` | Un rectángulo gris con texto que sabe si el ratón está encima. Cuando lo está, se aclara, le sale un borde blanco y el texto se pone amarillo claro. |
| `ui` | `Texto` | Dibuja texto con la fuente de píxeles `fuente.png`, con sombra como en Minecraft. Sabe medir un texto y centrarlo. |
| `config` | `Constants` | Tamaño y título de la ventana, sensibilidad del ratón, posición inicial y bloques de la hotbar. |

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
2. Deja el cursor visible y activa los botones "pegajosos" del ratón (`GLFW_STICKY_MOUSE_BUTTONS`), para que un clic muy rápido no se pierda entre dos frames del menú.
3. Crea el contexto de OpenGL y pone el color del cielo (`glClearColor`).
4. Compila los shaders, carga `terrain_atlas.png` y la fuente (`new Texto()`) y crea el `MenuPrincipal`, que carga `titulo.png`.
5. Crea la matriz de proyección (FOV de 70°, planos 0.1 y 1000).
6. Arranca en el estado `MENU_PRINCIPAL`. Todavía no hay mundo.

Al hacer clic en *Un jugador*, `iniciarPartida()` captura el cursor (`GLFW_CURSOR_DISABLED`), apaga los botones pegajosos (en la partida nadie los lee y un clic quedaría "pegado" para el próximo menú), hace `new Partida(window)` y pasa a `JUGANDO`. El constructor de `Partida` hace el resto:

1. Crea el `PlayerController` y la `Camera`.
2. Crea el `World` con una distancia de render de 4 chunks. Eso pide generar 9 × 9 = 81 chunks alrededor de (0, 0).
3. Busca la altura de la superficie en (0, 0) y pone al jugador encima, en (0.5, altura, 0.5).
4. Llama a `Input.init()`, que registra los callbacks y pone `firstMouse = true` para que la cámara no salte con el primer movimiento del ratón.

Al cerrar, `Main.cleanup()` llama a `partida.cleanup()` (que libera el mundo) y después libera el shader, las texturas (atlas, fuente y título) y la ventana.

## El ciclo de cada frame

`Main.loop()` repite esto hasta que se cierra la ventana. Lo que hace en cada frame depende del estado.

**`MENU_PRINCIPAL`:**

1. Limpia la pantalla.
2. `menu.update()`: lee la posición del ratón (`glfwGetCursorPos`) para iluminar el botón que está debajo, y el botón izquierdo (`glfwGetMouseButton`). El clic cuenta al **soltar** el botón, así mantenerlo apretado no cuenta como varios clics.
3. `menu.render()`: dibuja el fondo (la casilla de tierra del atlas repetida en baldosas de 64 px y oscurecida), el título y los dos botones con su texto, con `glOrtho` y `glBegin`/`glEnd` como el HUD.
4. Si se hizo clic en *Un jugador* llama a `iniciarPartida()`; si fue en *Salir*, marca la ventana para cerrarse.
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

## El mundo: chunks y generación

### Chunks

- Cada `Chunk` mide `CHUNK_SIZE` × `CHUNK_HEIGHT` × `CHUNK_SIZE` = 48 × 200 × 48 bloques.
- Los bloques se guardan en `int[x][y][z]`, un ID por bloque. Son unos 1.8 MB de RAM por chunk (~150 MB con 81 chunks).
- `World` guarda los chunks activos en un `ConcurrentHashMap<Long, Chunk>`. La clave junta `chunkX` y `chunkZ` en un `long`.
- Para pasar de coordenadas del mundo a coordenadas del chunk se usa `Math.floorDiv` y `Math.floorMod`, así funciona bien con coordenadas negativas.

### De "hace falta un chunk" a "se ve en pantalla"

```text
Hilo principal                         Hilos secundarios (núcleos - 1)
──────────────                         ───────────────────────────────
actualizarMundo()
  ├─ crea el Chunk vacío y lo guarda
  └─ manda la tarea al pool ─────────► generarTerrenoAsincrono()
                                          ├─ WorldGenerator.generateTerrain()  (solo la 1.ª vez)
                                          └─ ChunkMeshBuilder: malla opaca y transparente
                                       ◄── lo mete en la cola chunksListosParaGL
procesarMallasPendientes()
  └─ saca 1 chunk de la cola y sube sus vértices a la GPU (VAO/VBO)
render()
  └─ dibuja los chunks que ya están listos
```

OpenGL solo se puede usar desde el hilo principal. Por eso los hilos secundarios dejan los vértices en `float[]` y el hilo principal los sube después.

Los chunks que quedan a más de `renderDistance` del jugador se liberan (`chunk.cleanup()`) y se sacan del mapa.

Cuando rompes o pones un bloque, `setBlockGlobal()` cambia el ID y vuelve a mandar el chunk al pool. El terreno no se regenera (`terrenoGenerado` ya es `true`), solo se reconstruye la malla.

### Generación del terreno (`WorldGenerator`)

`generateTerrain()` trabaja en 5 pasos sobre el arreglo del chunk:

1. **Terreno.** Para cada columna calcula la altura con ruido Perlin (base de 74 a 94), la aplana cerca de los ríos y la hunde en los océanos (46 a 60). Rellena de abajo hacia arriba: roca madre en y = 0 (y con probabilidad hasta y = 4), pizarra profunda por debajo de 38, mezcla de pizarra y piedra entre 38 y 43, piedra más arriba, y en las últimas capas el bloque de relleno y de superficie del bioma (o arena en playas y fondos de agua). Lo que queda vacío por debajo de y = 68 (el nivel del agua) se llena de agua.
2. **Cuevas.** Cráteres esféricos con "gusanos" que suben, y a veces una entrada desde la superficie. No excavan cerca del agua para no inundar las cuevas.
3. **Ríos.** Vuelve a tallar los cauces sobre tierra firme y los llena de agua.
4. **Minerales, árboles y cactus.** Vetas de carbón (hasta y = 80) y de hierro (hasta y = 50) dentro de piedra o pizarra. Árboles con 10 % de probabilidad en pasto o tierra plana, solo en llanura y con al menos 3 bloques de separación. Cactus de 1 a 3 bloques en la arena del desierto.
5. **Nubes.** Bloques de nube en y = 150–151, agrupados en celdas de 12 × 12.

Los biomas los decide `BiomeProvider` con ruido a gran escala: océano si el valor es menor a 0.45, desierto si es mayor a 0.65 y llanura en el resto.

La **forma** del terreno siempre sale igual, porque `PerlinNoise` usa la semilla fija 12345. Las cuevas, minerales, árboles y cactus usan `Math.random()`, así que cambian cada vez que se genera un chunk (también al volver a una zona que se descargó).

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

## Render

- **Formato de vértice:** 5 floats (x, y, z, u, v). Cada cara visible son 2 triángulos, o sea 6 vértices. No se usan índices.
- **Caras visibles:** `ChunkMeshBuilder.shouldRenderFace()` consulta al vecino con `world.getBlockGlobal()`, que puede estar en otro chunk. Un bloque sólido solo dibuja las caras que dan al aire, al agua o a una nube. El agua no dibuja caras contra otra agua ni contra sólidos.
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

## Dónde cambiar cada cosa

| Qué | Dónde |
| --- | --- |
| Resolución y título de la ventana | `Constants.SCREEN_WIDTH`, `SCREEN_HEIGHT`, `WINDOW_TITLE` |
| Sensibilidad del ratón | `Constants.MOUSE_SENSITIVITY` |
| Bloques de la hotbar | `Constants.BLOQUES_HOTBAR` |
| Distancia de render (en chunks) | `Partida.RENDER_DISTANCE` (4 → 9 × 9 chunks) |
| Tamaño y altura del chunk | `Chunk.CHUNK_SIZE` (48) y `Chunk.CHUNK_HEIGHT` (200) |
| Nivel del agua | `WorldGenerator.generateTerrain()`, `nivelAgua = 68` |
| Semilla del terreno | `PerlinNoise`, `new Random(12345)` |
| Frecuencia de biomas | `BiomeProvider.BIOME_SCALE` y sus umbrales |
| Velocidad del jugador | `PlayerController.speed` |
| Campo de visión | `Main.init()`, `Math.toRadians(70.0f)` |
| Botones del menú (tamaño, posición y texto) | `MenuPrincipal`: `ANCHO_BOTON`, `ALTO_BOTON`, `SEPARACION` y el constructor |
| Color de los botones y tamaño de su texto | `Boton`: `GRIS` y `ESCALA_TEXTO` |
| Letras de la fuente | `herramientas/fuente.txt` y volver a correr `GenerarFuente` |
| Texto del título | `RENGLONES` en `herramientas/GenerarTitulo.java` y volver a correrlo con el `.ttf` |
| Posición del título | `MenuPrincipal.ESPACIO_TITULO` (distancia al primer botón) |
| Fondo del menú | `MenuPrincipal.dibujarFondo()` (bloque, oscurecido) y `TAM_BALDOSA` |
| Color del cielo | `Main.init()`, `glClearColor` |
| Color del agua y de las nubes | `shaders/fragment.glsl` |

`Constants.PLAYER_START_POSITION` casi no tiene efecto: en cuanto se crea el mundo, el jugador se mueve a la superficie en (0.5, altura, 0.5).

## Dependencias y plataforma

- Java 21, Maven, LWJGL 3.3.3 (GLFW, OpenGL, STB) y JOML 1.10.5.
- El `pom.xml` solo trae las librerías nativas de **Windows** (`natives-windows`). Para correrlo en Linux o macOS hay que agregar las dependencias con `natives-linux` o `natives-macos`.

## Cosas a revisar

Estas salen de leer el código y no las he probado en el juego. Conviene confirmarlas antes de arreglarlas:

1. **Posible desfase de medio bloque.** `ChunkMeshBuilder` dibuja cada bloque centrado en su coordenada entera (de x − 0.5 a x + 0.5), pero las colisiones y el rayo para romper/poner usan `Math.floor`, o sea que tratan al bloque como si ocupara de x a x + 1. Si es así, se notaría como que el jugador flota medio bloque sobre el suelo, o que al apuntar cerca de un borde se rompe o se pone el bloque de al lado.
2. **Posibles huecos en los bordes de chunk.** Un chunk nuevo empieza lleno de ceros, y 0 es `STONE`. Si la malla de un chunk se arma antes de que su vecino termine de generarse, las caras del borde se ocultan como si hubiera piedra al lado, y no se vuelven a calcular cuando el vecino ya está listo. Pasa algo parecido al romper un bloque justo en el borde, porque solo se reconstruye la malla de ese chunk y no la del vecino.
3. **La ventana es de tamaño fijo** (`GLFW_RESIZABLE` en falso) porque ni `glViewport`, ni la proyección, ni el HUD, ni los botones del menú se ajustan a otro tamaño: todos usan `SCREEN_WIDTH` y `SCREEN_HEIGHT`. Para poder redimensionarla habría que recalcular todo eso cuando cambia el tamaño.
4. **La velocidad depende de los FPS**, porque el movimiento se suma por frame y no por tiempo transcurrido.
5. **`Hud.cleanup()` nunca se llama.** Al cerrar el juego no importa, pero sí importará cuando se pueda salir al menú y volver a entrar.
6. **El spawn no espera a que el terreno exista.** El constructor de `Partida` calcula la altura de la superficie justo después de `new World()`, mientras el chunk (0, 0) todavía se está generando en otro hilo. Si aún no terminó, ese chunk está lleno de ceros (piedra), la "superficie" sale en y = 200 y el jugador aparece muy arriba, encima de las nubes. El menú de inicio es buen momento para arreglarlo con una pantalla de "Generando mundo…" (ver `PLAN_MENU_INICIO.md`).
