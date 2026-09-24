# Plan: menú de inicio

**Estado:** las seis fases están hechas. Falta probar la fase 6 en Windows (ver la tabla "Estado de las fases"). Después se hizo un arreglo aparte, el del spawn en un río (ver "Arreglos fuera de las fases").

## Cómo trabajamos: una fase por conversación y `/clear` entre fases

- Cada fase se hace en su propia conversación. Al terminarla: el proyecto compila, se actualiza la tabla de abajo, se hace commit y push, y **el usuario hace `/clear`** antes de pedir la siguiente.
- ¿Por qué? Para aprovechar mejor los créditos: cada mensaje vuelve a enviar toda la conversación, así que mientras más larga es, más cuesta cada mensaje. Con `/clear` la siguiente fase empieza limpia.
- No se pierde nada porque todo lo necesario está en el repo: este archivo, `ARQUITECTURA.md` y `CLAUDE.md` (que Claude lee solo al empezar).
- Para arrancar una fase basta con algo como: *"Empieza la fase 2 del menú (docs/PLAN_MENU_INICIO.md)"*.

## Estado de las fases

Antes de la fase 1 conviene abrir el juego en Windows desde la rama `claude/folder-redistribution-feedback-axvqj2` y confirmar que la reestructuración no rompió nada (paso 8 de `PLAN_REESTRUCTURACION.txt`).

| Fase | Estado | Commit | Qué falta probar en Windows |
| --- | --- | --- | --- |
| 1. Sacar la partida de `Main` | hecha (compila) | `35fc81b` | Que el juego se vea y se juegue igual que antes: moverse, romper y poner bloques, rueda y teclas 1–9 de la hotbar, pick block, que carguen chunks al caminar y que se cierre sin errores. |
| 2. Estados y un menú mínimo | hecha (compila) | `33c3d1b` | Que al abrir salga el menú (fondo de tierra oscura, botón verde *Jugar* y rojo *Salir*); que cada botón se aclare y tenga borde blanco al pasar el ratón; que *Jugar* entre al mundo sin salto de cámara y *Salir* cierre el juego; que el clic en *Jugar* no rompa ni ponga un bloque; que la ventana ya no se pueda agrandar. |
| 3. Texto | hecha y probada en Windows | `7a1844c` | Nada. El usuario lo probó el 2026-09-24: el título, los botones grises con *Un jugador* y *Salir* y el hover se ven bien; *Un jugador* entra al mundo y el HUD se ve igual que antes. |
| 4. Pantalla de "Generando mundo…" | hecha y probada en Windows | `5b4d141` | Nada. El usuario lo probó el 2026-09-24 y funciona bien. |
| 5. Pausa y volver al menú | hecha y probada en Windows | `a93fa50` | Nada. El usuario lo probó el 2026-09-24 y todo funciona bien: ESC abre y cierra la pausa, el fondo desenfocado con la capa oscura, los botones *Volver al juego* y *Salir al menú*, y entrar y salir del mundo varias veces. |
| 6. Crear mundo con semilla (opcional) | hecha (compila; probada sin GPU en Linux) | `0a69f5a` | Que *Un jugador* abra *Crear mundo* con el mismo estilo del menú (fondo de tierra, título, *Semilla*, el campo, la ayuda y los botones *Crear mundo* y *Cancelar*) y con el campo vacío. Que se pueda escribir con tildes y ñ, borrar (también manteniendo Borrar) y pegar con Ctrl+V. Que *Cancelar* y ESC vuelvan al menú sin abrir la pausa, y que Enter cree el mundo. Que dos mundos con la misma semilla salgan iguales (mismo lugar al aparecer, mismos árboles, cuevas y nubes) y que dos sin semilla salgan distintos. Que la pausa diga "Semilla: ..." con el número correcto (12345 → 12345, "hola" → 3208380). Que un chunk se vea igual después de alejarse y volver. |

### Arreglos fuera de las fases

| Arreglo | Estado | Commit | Qué falta probar en Windows |
| --- | --- | --- | --- |
| Spawn en tierra firme (siempre aparecías en un río) | hecho (compila; probado sin GPU en Linux) | `c955c1a` | Crear varios mundos (con semilla y sin ella) y ver que apareces de pie en tierra, no en un río ni en el océano. Que la consola diga "Spawn: x, z" y que la misma semilla dé el mismo spawn y el mismo mundo (12345 → spawn en (0, 0), sobre pasto; 1 → spawn en (346, −345), sobre arena). Que "Generando mundo..." siga durando poco aunque el spawn quede lejos de (0, 0), y que al caminar carguen los chunks normal. Detalles en `ARQUITECTURA.md`, "Dónde aparece el jugador". |

## La idea

Hoy, al abrir el juego, apareces directamente en el mundo. La idea es que primero salga un menú de inicio al estilo de Minecraft: el título del juego y botones como **Un jugador** y **Salir**. Al darle a *Un jugador* se crea el mundo y empieza la partida que ya tenemos.

## ¿El menú "ejecuta Main.java"?

No hace falta volver a ejecutar nada. `Main.java` es lo primero que corre cuando abres el juego y sigue corriendo hasta que lo cierras: tiene la ventana y el ciclo que dibuja cada frame. Lo que cambia es **qué dibuja ese ciclo en cada momento**.

- **Hoy:** `Main` arranca → crea el mundo → ciclo del juego.
- **Con menú:** `Main` arranca → ciclo que dibuja el menú → al hacer clic en *Un jugador* se crea el mundo → el mismo ciclo pasa a dibujar el juego.

Todo pasa en la misma ventana, como en Minecraft: el menú y el mundo son dos "pantallas" del mismo programa. Para saber cuál toca dibujar, `Main` guarda un **estado** (menú, jugando, pausa…).

```text
Main.run()
 ├─ init(): ventana, OpenGL, shaders y texturas        (ya NO crea el mundo)
 └─ loop(): cada frame, según el estado:
      MENU_PRINCIPAL → dibujar el menú y revisar clics
           clic en "Un jugador" → iniciarPartida(): crea el mundo y pone al jugador
                                  → estado = JUGANDO
           clic en "Salir"      → cerrar la ventana
      JUGANDO        → lo mismo que hace el ciclo hoy (moverse, chunks, render, HUD)
           ESC                  → estado = PAUSA
      PAUSA          → mundo congelado + botones "Volver al juego" / "Salir al menú"
           "Salir al menú"      → liberar el mundo → estado = MENU_PRINCIPAL
```

## Qué falta hoy para poder hacerlo

| Para el menú se necesita… | …y hoy pasa esto |
| --- | --- |
| Mostrar algo antes del mundo | `Main.init()` crea la `Partida` (y con ella el mundo) apenas arranca. |
| Un cursor visible para hacer clic en los botones | `Input.init()` captura el cursor siempre. |
| Que los clics en el menú no rompan bloques | Los callbacks de `Input` siempre actúan sobre el mundo. |
| Texto en los botones | No hay forma de dibujar texto. |
| Botones centrados | La ventana se puede redimensionar, pero nada se ajusta al nuevo tamaño. |
| Poner al jugador cuando el terreno ya existe | El spawn se calcula sin esperar a que el chunk (0, 0) termine de generarse (ver `ARQUITECTURA.md`, "Cosas a revisar"). |
| Salir de una partida | No hay tecla ESC ni forma de liberar el mundo y volver al menú. |

## Estructura propuesta

```text
com/minejava/
├── Main.java               (cambia) ventana, OpenGL, shader, textura y estado actual
├── EstadoJuego.java        (nuevo)  MENU_PRINCIPAL, JUGANDO, PAUSA
├── Partida.java            (nuevo)  mundo + jugador + cámara de UNA partida
├── ui/
│   ├── Hud.java
│   ├── Texto.java          (nuevo)  dibuja texto con una fuente de píxeles
│   ├── Boton.java          (nuevo)  rectángulo con texto; sabe si el ratón está encima
│   └── MenuPrincipal.java  (nuevo)  fondo, título y botones
└── player/
    └── Input.java          (cambia) se activa al entrar a una partida y se desactiva al salir

src/main/resources/textures/fuente.png   (nuevo) fuente de píxeles para el texto
```

La pieza clave es **`Partida`**: junta todo lo que hoy vive suelto en `Main` y que pertenece a *un* mundo (el `World`, el `PlayerController` y la `Camera`). Así, "crear mundo" es `new Partida(...)` y "salir al menú" es `partida.cleanup()` y olvidarse de ella. `Main` se queda solo con lo que dura todo el programa: la ventana, los shaders, las texturas y el estado.

Boceto de cómo quedaría el ciclo (no es código final):

```java
while (!GLFW.glfwWindowShouldClose(window)) {
    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

    switch (estado) {
        case MENU_PRINCIPAL -> {
            menu.update(window);                       // hover y clics de los botones
            menu.render();
            if (menu.clicEnJugar()) iniciarPartida();
            if (menu.clicEnSalir()) GLFW.glfwSetWindowShouldClose(window, true);
        }
        case JUGANDO -> {
            partida.update(window);                    // lo que hoy hace loop() antes de dibujar
            partida.render(shader, blockTexture, projectionMatrix);
        }
    }

    GLFW.glfwSwapBuffers(window);
    GLFW.glfwPollEvents();
}
```

## Fases

Cada fase deja el juego funcionando, así se puede probar y hacer commit antes de pasar a la siguiente.

### Fase 1: sacar la partida de `Main` (sin cambios visibles)

- Crear `Partida` y mover ahí la creación del mundo, el cálculo del spawn, `Input.init()` y la parte del ciclo que mueve al jugador, actualiza los chunks y dibuja el mundo y el HUD.
- `Main.init()` deja de crear el mundo. Al terminar llama a `iniciarPartida()` directamente, así que por ahora todavía se entra al mundo sin menú.

**Lista cuando:** el juego se ve y se juega exactamente igual que hoy.

### Fase 2: estados y un menú mínimo

- Agregar `EstadoJuego`. `Main` arranca en `MENU_PRINCIPAL`, con el cursor visible (`GLFW_CURSOR_NORMAL`).
- `MenuPrincipal` con un fondo y dos `Boton` (todavía sin texto; se pueden distinguir por color): *Jugar* y *Salir*. Cada botón se aclara cuando el ratón está encima, como en Minecraft.
- Para los clics, el menú puede leer el ratón cada frame con `glfwGetCursorPos` y `glfwGetMouseButton`, sin tocar los callbacks de `Input`. `glfwGetCursorPos` da píxeles con el origen arriba a la izquierda, igual que el `glOrtho` que usa `Hud`, así que se compara directo con el rectángulo del botón. Hay que reaccionar cuando el botón se **suelta**, no mientras está apretado, para que un clic no cuente varias veces.
- `iniciarPartida()`: crea la `Partida`, captura el cursor (`GLFW_CURSOR_DISABLED`) y cambia a `JUGANDO`. `Input.init()` se llama aquí, no al arrancar el programa, así en el menú no hay callbacks que rompan bloques. Al iniciar hay que poner `firstMouse = true` para que la cámara no pegue un salto.
- Por ahora conviene hacer la ventana de tamaño fijo (`GLFW_RESIZABLE` en `GLFW_FALSE`) para que los botones queden centrados con `SCREEN_WIDTH` y `SCREEN_HEIGHT`.
- Idea para el fondo: repetir la textura de tierra del atlas (ID 2) por toda la pantalla y oscurecerla, como el fondo de los menús de Minecraft.

**Lista cuando:** al abrir el juego sale el menú, los botones se iluminan al pasar el ratón, *Jugar* entra al mundo, *Salir* cierra el juego y hacer clic en el menú no rompe ni pone bloques.

### Fase 3: texto

- Agregar `fuente.png`: una imagen con los caracteres en una cuadrícula de 16 × 16. Si sigue el orden Latin-1 (ISO-8859-1), las 256 casillas alcanzan para á, é, ñ, ¿ y ¡.
- `Texto.dibujar(texto, x, y, escala)`: por cada carácter se calcula su casilla (columna = código % 16, fila = código / 16) con el mismo truco que `ChunkMeshBuilder.getUVs()` usa para el atlas, y se dibuja un cuadrado con esa parte de la imagen. Ojo: `Texture` voltea la imagen al cargarla, así que la fila se invierte igual que en `getUVs()`.
- Poner el título del juego y los textos de los botones: *Un jugador* y *Salir*.
- No hay que copiar la fuente de Minecraft porque tiene derechos de autor. Se puede dibujar una propia o usar una fuente de píxeles libre (CC0 u OFL).

**Lista cuando:** los botones dicen *Un jugador* y *Salir* y el título se lee bien.

**Cómo quedó:**

- `fuente.png` es una fuente de píxeles propia de 8 × 12 por casilla. Los glifos están dibujados con `#` y `.` en `herramientas/fuente.txt`, y `java herramientas/GenerarFuente.java` los convierte en la imagen. Cada letra mide hasta su última columna pintada, así que el ancho es variable como en Minecraft.
- El título no se escribe con `Texto`: es una imagen (`titulo.png`) hecha con la tipografía MINECRAFT PE, que trajo el usuario, rellena con la piedra del atlas por `herramientas/GenerarTitulo.java`. Esa tipografía es solo para uso personal, así que su `.ttf` no se sube al repo. `Minecraft.ttf` (CraftronGaming) no se usó porque no trae tildes ni ñ.
- Los botones pasaron a ser grises, como en Minecraft: ya no hace falta distinguirlos por color.
- Detalles en `ARQUITECTURA.md`, "Texto y título".

### Fase 4: pantalla de "Generando mundo…"

- Al darle a *Un jugador*, mostrar "Generando mundo..." hasta que el chunk donde aparece el jugador termine de generarse. Recién ahí se calcula el spawn y se pasa a `JUGANDO`.
- Para eso, `Chunk` tiene que avisar cuándo terminó: `terrenoGenerado` debe ser `volatile` (lo escribe un hilo secundario y lo lee el principal) y tener un getter. `World` puede exponer algo como `estaGenerado(x, z)`.
- El texto se dibuja con `Texto` (lo tiene `Main`). Hay que escribirlo con tres puntos: `…` no es Latin-1 y saldría como `?`.
- Esto arregla el problema de aparecer encima de las nubes (punto 6 de "Cosas a revisar" en `ARQUITECTURA.md`).

**Lista cuando:** siempre apareces parado sobre el suelo, nunca en el cielo.

**Cómo quedó:**

- Nuevo estado `GENERANDO_MUNDO`. *Un jugador* hace `new Partida()` (crea el mundo, sin poner al jugador) y pasa a ese estado. Cada frame se dibuja `PantallaGenerando` y se sube una malla terminada a la GPU. Cuando `partida.estaLista()`, `Main.empezarAJugar()` captura el cursor, llama a `partida.comenzar()` (spawn + `Input.init()`) y pasa a `JUGANDO`.
- `estaLista()` espera que el chunk del spawn tenga su terreno (`World.estaGenerado()`, con `terrenoGenerado` `volatile`) y también su malla en la GPU, así al entrar ya se ve el suelo.
- El fondo de tierra pasó de `MenuPrincipal` a `FondoTierra`, para usarlo en las dos pantallas.
- Dos cambios en `World` que salieron al probar sin pantalla, midiendo con un programa aparte en un equipo de 4 núcleos:
  - **Los chunks se generan de adentro hacia afuera.** Antes se mandaban por filas desde la esquina (−4, −4) y el del spawn era el número 41 de 81: tardaba 6,6 s en estar listo. Ahora es el primero: 50–110 ms.
  - **Cerrar a medio generar ya no deja el proceso vivo.** `World.cleanup()` usaba `shutdown()`, que deja terminar todas las tareas en cola. Con el mapa ya vacío, cada malla sale con todas las caras y tarda muchísimo: cerrar en "Generando mundo..." dejaba el proceso más de dos minutos trabajando. Ahora usa `shutdownNow()` y los hilos son *daemon*: termina en menos de medio segundo.
- La prueba sin pantalla también confirmó el problema original: sin esperar, el spawn salía en y = 200; esperando, en y = 59.

### Fase 5: pausa y volver al menú

- ESC durante la partida → estado `PAUSA`; ESC otra vez (o *Volver al juego*) vuelve a `JUGANDO`, como en Minecraft. Mantener ESC apretado no debe abrir y cerrar la pausa varias veces.
- El mundo se queda congelado detrás: no se llama a `partida.update()`, así que el jugador no se mueve y los chunks no se actualizan.
- Fondo: el mundo congelado **desenfocado** (blur) con una capa oscura encima. Como el mundo no se mueve en la pausa, el desenfoque se calcula una sola vez al entrar y se reutiliza esa imagen cada frame. El HUD (hotbar y mira) queda detrás del desenfoque, no encima. Las texturas del desenfoque no deben acumularse cada vez que se pausa.
- Título *Juego en pausa* y botones *Volver al juego* y *Salir al menú*, centrados y con el mismo estilo que el menú de inicio (se reusan `Boton` y `Texto`).
- El cursor queda libre. Mover el ratón no mueve la cámara, y los clics y la rueda no rompen ni ponen bloques ni cambian la hotbar.
- *Volver al juego*: captura el cursor otra vez, sin que la cámara pegue un salto, y vuelve a `JUGANDO`.
- *Salir al menú*: `partida.cleanup()` (llama a `World.cleanup()`, que libera los chunks de la GPU y apaga los hilos), desactivar los callbacks de `Input` (ponerlos en `null` y liberar los anteriores con `.free()`), `partida = null` y estado `MENU_PRINCIPAL`.
- Ojo: `shutdownNow()` descarta los chunks en cola, pero los que ya se estaban generando terminan igual. Como el mapa ya está vacío, su malla sale con todas las caras (mucha memoria y CPU un rato). Al cerrar el juego no importa porque los hilos son *daemon*; al volver al menú sí. Se puede arreglar haciendo que la tarea no arme la malla si el mundo ya se cerró.

**Lista cuando:** puedes entrar y salir del mundo varias veces seguidas sin que el juego se trabe, sin saltos de cámara al volver a entrar y sin que la memoria suba cada vez.

**Cómo quedó:**

- Clases nuevas: `MenuPausa` (título, botones y capa oscura al 50 %) y `FondoDesenfocado` (el desenfoque). Detalles en `ARQUITECTURA.md`, "Pausa y volver al menú".
- **ESC** se lee con un callback de teclado en `Main`, que solo cuenta `GLFW_PRESS`. Mantener la tecla manda `GLFW_REPEAT`, que no cuenta.
- **`pausar()`:** apaga `Input` (`Input.desactivar()` quita los callbacks del ratón y los libera), libera el cursor y lo pone en el centro, y dibuja el mundo una vez más para copiarlo y desenfocarlo. **`reanudar()`** captura el cursor y vuelve a llamar a `Input.init()`, que pone `firstMouse = true`.
- **El desenfoque** se hace en la CPU: `glReadPixels`, se achica a 320 × 180, desenfoque gaussiano y se sube a **una sola textura** que se crea al arrancar y se reutiliza en cada pausa. Tarda unos 10 ms; la primera pausa de cada sesión, más (en el equipo de prueba, 200 ms), porque Java todavía no optimizó ese código.
- **En la pausa, un clic solo cuenta si empieza y termina sobre el mismo botón**, así soltar un botón del ratón que venía apretado desde la partida no saca del mundo.
- **Se arregló el "Ojo" de arriba:** `World.cleanup()` pone `cerrado = true`; `Chunk` no empieza la malla y `ChunkMeshBuilder` la deja a la mitad si el mundo se cerró. Con solo lo primero, un hilo que ya estaba armando una malla tardó 2,1 s en terminar; con las dos, todos terminan en menos de 0,3 s.
- `Hud.cleanup()` ahora se llama al cerrar el juego (era el punto 5 de "Cosas a revisar"). El VAO del HUD es uno para todas las partidas, así que salir al menú no lo borra.
- `pom.xml` fija `project.build.sourceEncoding` en UTF-8: *Salir al menú* es el primer texto con tilde que se dibuja, y sin eso la ú podría salir mal según cómo compile el IDE.
- **Se probó sin GPU**, en Linux: el juego corrió en una pantalla virtual (Xvfb con OpenGL por software) y un programa con `java.awt.Robot` apretó teclas, movió el ratón, hizo clics y sacó capturas:
  - La pausa se ve con el mundo y el HUD desenfocados, la capa oscura, el título y los botones centrados, y la ú bien.
  - Durante la pausa se movió el ratón, se hicieron clics izquierdo y derecho mirando al suelo, se giró la rueda y se apretaron 1, 6, W y Espacio: al volver, la pantalla quedó idéntica píxel por píxel.
  - Mantener ESC 2,5 s (llegaron 1 PRESS y 46 REPEAT) cambió de estado una sola vez.
  - Al volver con ESC o con el botón, aunque el ratón se haya movido lejos en la pausa, la cámara no salta; después se puede girar, romper, usar la rueda y las teclas.
  - Entrando y saliendo del mundo 10 y 12 veces seguidas (saliendo a veces en plena generación), el heap de Java después de cada salida vuelve siempre a 2,6 MB (jugando usa 250–360 MB), no quedan hilos generando y la memoria del proceso queda pareja.
  - Cerrar la ventana en la pausa o en el menú después de salir termina el programa enseguida.

### Fase 6 (opcional): crear mundo con semilla

Hoy todos los mundos tienen la misma forma, porque `PerlinNoise` usa la semilla fija 12345. En cambio, las cuevas, los árboles y los minerales usan `Math.random()`, así que cambian cada vez y no se pueden repetir. Para que "crear mundo" dé mundos distintos y repetibles:

- `PerlinNoise` recibe la semilla (en lugar de fijarla en un bloque `static`). Lo más limpio es que deje de ser estático y que cada partida tenga el suyo.
- `WorldGenerator` usa un `Random` por chunk creado a partir de (semilla, chunkX, chunkZ) en lugar de `Math.random()`. Así la misma semilla da el mismo mundo, y un chunk sale igual si se descarga y se vuelve a cargar.
- Una pantalla *Crear mundo* con un campo de texto para la semilla (se escribe con `glfwSetCharCallback`).

**Lista cuando:** la misma semilla da el mismo mundo, sin semilla sale uno distinto cada vez y la semilla se ve en la pausa.

**Cómo quedó:**

- **Pantalla *Crear mundo*** (`PantallaCrearMundo`, estado `CREAR_MUNDO`): se abre con *Un jugador*. El fondo de tierra, el título, *Semilla* en gris, el campo de texto (`CampoTexto`, fondo negro y borde blanco como en Minecraft), la ayuda "Déjala vacía para una semilla al azar" y *Crear mundo* y *Cancelar* uno al lado del otro. El campo empieza vacío cada vez.
- **Teclado:** `Main` registra `glfwSetCharCallback` y, mientras el estado es `CREAR_MUNDO`, le pasa las letras y las teclas a la pantalla (cada ventana tiene un solo callback de teclado, y el de `Main` ya atendía ESC). El campo acepta solo las letras que tiene la fuente, hasta 32. Borrar quita la última letra (mantenida sigue borrando) y Ctrl+V pega. **ESC es *Cancelar*** (se resuelve en el mismo lugar que la pausa, así que no se cruzan) y **Enter es *Crear mundo***.
- **De texto a semilla** (`Semilla.desdeTexto()`): vacío → al azar, un número → ese número, otro texto → `hashCode()`, como Minecraft. Se ignoran los espacios del principio y del final.
- **Generación:** `PerlinNoise` dejó de ser estático: cada `World` tiene un `WorldGenerator` con su semilla, que tiene su propio `PerlinNoise` y `BiomeProvider`. Todos los `Math.random()` de `WorldGenerator` se cambiaron por un `Random` por chunk hecho con (semilla, chunkX, chunkZ), como Minecraft. Seno y coseno con `StrictMath`, que da lo mismo en cualquier equipo.
- **Pausa:** debajo de los botones dice "Semilla: ...". La semilla también se imprime en la consola al crear el mundo.
- **Se probó sin GPU**, en Linux: sin pantalla, los 81 chunks alrededor del spawn salen idénticos con la misma semilla (uno por uno o con 4 hilos en otro orden) y generar un chunk tarda lo mismo que antes. Con Xvfb y un programa con `java.awt.Robot`: dos mundos con la semilla 12345 dieron capturas idénticas píxel por píxel; uno sin semilla cambió el 96 % de la pantalla. Detalles en `ARQUITECTURA.md`, "Crear mundo y la semilla".
- **Se encontró otra cosa:** con cualquier semilla hay un río justo en el spawn (0, 0) y el jugador aparece en el fondo, bajo 4 a 16 bloques de agua. Pasaba igual con la semilla fija. No se arregló en esta fase porque no era parte de ella; se arregló después, aparte (ver "Arreglos fuera de las fases").

## Fuera de este plan

- Guardar y cargar mundos, y la lista de mundos guardados como la de Minecraft. Para eso primero hace falta un sistema de guardado.
- Menú de opciones (distancia de render, sensibilidad, FOV).
- Multijugador.
