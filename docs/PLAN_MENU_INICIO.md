# Plan: menú de inicio

**Estado:** pendiente. Es el siguiente paso ahora que el proyecto está organizado (ver `PLAN_REESTRUCTURACION.txt`).

## Cómo trabajamos: una fase por conversación y `/clear` entre fases

- Cada fase se hace en su propia conversación. Al terminarla: el proyecto compila, se actualiza la tabla de abajo, se hace commit y push, y **el usuario hace `/clear`** antes de pedir la siguiente.
- ¿Por qué? Para aprovechar mejor los créditos: cada mensaje vuelve a enviar toda la conversación, así que mientras más larga es, más cuesta cada mensaje. Con `/clear` la siguiente fase empieza limpia.
- No se pierde nada porque todo lo necesario está en el repo: este archivo, `ARQUITECTURA.md` y `CLAUDE.md` (que Claude lee solo al empezar).
- Para arrancar una fase basta con algo como: *"Empieza la fase 2 del menú (docs/PLAN_MENU_INICIO.md)"*.

## Estado de las fases

Antes de la fase 1 conviene abrir el juego en Windows desde la rama `claude/folder-redistribution-feedback-axvqj2` y confirmar que la reestructuración no rompió nada (paso 8 de `PLAN_REESTRUCTURACION.txt`).

| Fase | Estado | Commit | Qué falta probar en Windows |
| --- | --- | --- | --- |
| 1. Sacar la partida de `Main` | pendiente | | |
| 2. Estados y un menú mínimo | pendiente | | |
| 3. Texto | pendiente | | |
| 4. Pantalla de "Generando mundo…" | pendiente | | |
| 5. Pausa y volver al menú | pendiente | | |
| 6. Crear mundo con semilla (opcional) | pendiente | | |

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
| Mostrar algo antes del mundo | `Main.init()` crea el mundo apenas arranca. |
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

### Fase 4: pantalla de "Generando mundo…"

- Al darle a *Un jugador*, mostrar "Generando mundo…" hasta que el chunk donde aparece el jugador termine de generarse. Recién ahí se calcula el spawn y se pasa a `JUGANDO`.
- Para eso, `Chunk` tiene que avisar cuándo terminó: `terrenoGenerado` debe ser `volatile` (lo escribe un hilo secundario y lo lee el principal) y tener un getter. `World` puede exponer algo como `estaGenerado(x, z)`.
- Esto arregla el problema de aparecer encima de las nubes (punto 6 de "Cosas a revisar" en `ARQUITECTURA.md`).

**Lista cuando:** siempre apareces parado sobre el suelo, nunca en el cielo.

### Fase 5: pausa y volver al menú

- ESC durante la partida → estado `PAUSA`: el mundo se sigue viendo quieto, con una capa oscura encima y los botones *Volver al juego* y *Salir al menú*. El cursor queda libre.
- *Volver al juego*: captura el cursor otra vez y vuelve a `JUGANDO`.
- *Salir al menú*: `partida.cleanup()` (llama a `World.cleanup()`, que libera los chunks de la GPU y apaga los hilos), desactivar los callbacks de `Input` (ponerlos en `null` y liberar los anteriores con `.free()`), `partida = null` y estado `MENU_PRINCIPAL`.

**Lista cuando:** puedes entrar y salir del mundo varias veces seguidas sin que el juego se trabe, sin saltos de cámara al volver a entrar y sin que la memoria suba cada vez.

### Fase 6 (opcional): crear mundo con semilla

Hoy todos los mundos tienen la misma forma, porque `PerlinNoise` usa la semilla fija 12345. En cambio, las cuevas, los árboles y los minerales usan `Math.random()`, así que cambian cada vez y no se pueden repetir. Para que "crear mundo" dé mundos distintos y repetibles:

- `PerlinNoise` recibe la semilla (en lugar de fijarla en un bloque `static`). Lo más limpio es que deje de ser estático y que cada partida tenga el suyo.
- `WorldGenerator` usa un `Random` por chunk creado a partir de (semilla, chunkX, chunkZ) en lugar de `Math.random()`. Así la misma semilla da el mismo mundo, y un chunk sale igual si se descarga y se vuelve a cargar.
- Una pantalla *Crear mundo* con un campo de texto para la semilla (se escribe con `glfwSetCharCallback`).

## Fuera de este plan

- Guardar y cargar mundos, y la lista de mundos guardados como la de Minecraft. Para eso primero hace falta un sistema de guardado.
- Menú de opciones (distancia de render, sensibilidad, FOV).
- Multijugador.
