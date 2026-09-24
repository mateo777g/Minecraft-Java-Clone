# Minecraft Java Clone

Clon educativo de Minecraft desarrollado en Java usando LWJGL 3, OpenGL y JOML. El proyecto genera un mundo voxel procedural y permite explorarlo e interactuar con sus bloques en primera persona.

> Proyecto en desarrollo. Su objetivo es experimentar con renderizado 3D, generación procedural, chunks y entrada de usuario en Java.

## Características

- Renderizado 3D con OpenGL.
- Ventana y entrada de usuario gestionadas con GLFW.
- Cámara en primera persona con movimiento de ratón.
- Movimiento del jugador con colisiones contra el terreno.
- Mundo voxel generado proceduralmente mediante ruido Perlin.
- Generación de terreno por chunks.
- Carga y actualización de chunks alrededor del jugador.
- Generación de terreno y mallas en hilos secundarios para reducir bloqueos.
- Mallas separadas para bloques opacos y transparentes.
- Agua y nubes con transparencia.
- Cuevas, ríos, océanos, árboles, cactus y minerales generados automáticamente.
- Colocación y destrucción de bloques.
- Hotbar visual con selección de bloques.
- Selección rápida de bloques con Pick Block.
- Menú de inicio con título y botones con texto.
- Pantalla *Crear mundo* con semilla: la misma semilla da siempre el mismo mundo (terreno, cuevas, árboles y minerales); vacía, una al azar.
- Menú de pausa (ESC) con el mundo desenfocado detrás y la semilla del mundo; se puede volver al juego o salir al menú y entrar a otro mundo.
- Fuente de píxeles propia con tildes, ñ, ¿ y ¡.
- Textura atlas incluida en el proyecto.
- Shaders GLSL para vértices y fragmentos.

## Bloques disponibles

La hotbar contiene seis bloques seleccionables:

1. Piedra
2. Roca madre
3. Tierra
4. Pasto
5. Tronco
6. Hojas

El mundo también utiliza agua, nubes, arena, cactus, pizarra profunda, carbón y hierro durante la generación procedural.

Los IDs de todos los bloques están en `world/Block.java` (en el mismo orden que `terrain_atlas.png`) y la lista de la hotbar en `config/Constants.java` (`BLOQUES_HOTBAR`).

## Controles

| Acción | Tecla o botón |
| --- | --- |
| Moverse hacia delante | `W` |
| Moverse hacia atrás | `S` |
| Moverse a la izquierda | `A` |
| Moverse a la derecha | `D` |
| Subir | `Espacio` |
| Bajar | `Shift izquierdo` |
| Mirar | Movimiento del ratón |
| Romper un bloque | Clic izquierdo |
| Colocar un bloque | Clic derecho |
| Seleccionar bloque 1-6 | Teclas `1` a `6` |
| Cambiar bloque seleccionado | Rueda del ratón |
| Copiar el bloque apuntado | Clic central |
| Pausa / volver al juego | `Esc` |

El juego empieza en el menú de inicio con el cursor libre. *Un jugador* abre *Crear mundo*, donde se puede escribir una semilla: un número se usa tal cual, un texto se convierte en número (como en Minecraft) y si se deja vacía sale una al azar. *Crear mundo* (o `Enter`) muestra "Generando mundo..." mientras se genera el terreno donde apareces, y al entrar el cursor se captura; *Cancelar* (o `Esc`) vuelve al menú. En el juego, `Esc` abre la pausa, con el cursor libre, los botones *Volver al juego* y *Salir al menú* y la semilla del mundo para anotarla; otro `Esc` vuelve al juego.

## Requisitos

- Java Development Kit (JDK) 21.
- Maven 3.8 o superior.
- OpenGL compatible con LWJGL 3.
- Sistema operativo compatible con las librerías nativas configuradas en `pom.xml`.

El proyecto está configurado actualmente para usar las librerías nativas de Windows de LWJGL.

## Ejecutar el proyecto

Clona el repositorio y entra en su carpeta:

```bash
git clone https://github.com/mateo777g/Minecraft-Java-Clone.git
cd Minecraft-Java-Clone
```

Compila el proyecto con Maven:

```bash
mvn clean package
```

Después, ejecuta la clase `com.minejava.Main.Launcher` desde tu IDE como una aplicación Java. Los shaders y la textura se cargan desde el classpath, así que no importa desde qué carpeta se ejecute.

## Estructura del proyecto

```text
src/main/java/com/minejava/
├── Main.java                     # Inicialización y ciclo principal del juego
├── EstadoJuego.java              # Pantalla actual: menú, crear mundo, generando mundo, jugando o pausa
├── Partida.java                  # Semilla, mundo, jugador y cámara de una partida
├── render/
│   ├── ChunkMeshBuilder.java     # Construcción de geometría voxel
│   ├── ShaderProgram.java        # Carga y gestión de shaders
│   └── Texture.java              # Carga del atlas de texturas
├── world/
│   ├── Block.java                # IDs de todos los bloques
│   ├── Chunk.java                # Datos y mallas de un chunk
│   ├── World.java                # Chunks, renderizado e interacción
│   └── gen/
│       ├── Biome.java            # Definición de biomas
│       ├── BiomeProvider.java    # Selección de biomas
│       ├── PerlinNoise.java      # Ruido para la generación del mundo (uno por semilla)
│       ├── Semilla.java          # Convierte el texto del campo Semilla en la semilla
│       └── WorldGenerator.java   # Generación procedural del terreno a partir de la semilla
├── player/
│   ├── Camera.java               # Cámara en primera persona
│   ├── Input.java                # Teclado y ratón
│   └── PlayerController.java     # Movimiento y colisiones
├── ui/
│   ├── Boton.java                # Botón del menú (se ilumina con el ratón encima)
│   ├── CampoTexto.java           # Campo de texto (el de la semilla)
│   ├── FondoDesenfocado.java     # Fondo de la pausa: el mundo desenfocado
│   ├── FondoTierra.java          # Fondo de tierra oscurecida de los menús
│   ├── Hud.java                  # Hotbar y mira
│   ├── MenuPausa.java            # Pausa: Volver al juego, Salir al menú y la semilla
│   ├── MenuPrincipal.java        # Menú de inicio: fondo, título y botones Un jugador y Salir
│   ├── PantallaCrearMundo.java   # Crear mundo: campo Semilla y botones Crear mundo y Cancelar
│   ├── PantallaGenerando.java    # Pantalla de "Generando mundo..."
│   └── Texto.java                # Dibuja texto con la fuente de píxeles
├── debug/
│   └── MedidorRendimiento.java   # Frames lentos, GC y chunks en la consola (Constants.MEDIR_RENDIMIENTO)
└── config/
    └── Constants.java            # Configuración general y bloques de la hotbar

src/main/resources/
├── shaders/                 # Vertex shader y fragment shader
└── textures/                # Atlas del terreno, fuente de píxeles y título del menú

herramientas/                # Generan fuente.png y titulo.png, y miden los chunks sin pantalla (ver docs/ARQUITECTURA.md)
docs/                        # Documentación del proyecto (ver abajo)
```

## Documentación

- [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md): cómo está organizado el código y cómo funciona cada parte (arranque, ciclo del juego, chunks, generación del terreno, bloques, render y controles), dónde cambiar cada cosa y una lista de cosas a revisar.
- [`docs/PLAN_MENU_INICIO.md`](docs/PLAN_MENU_INICIO.md): el plan por fases del menú de inicio y cómo quedó cada fase.
- [`docs/PLAN_OPTIMIZACION.md`](docs/PLAN_OPTIMIZACION.md): el plan por fases para quitar los tirones al cargar chunks (en curso), con cómo medir y las mediciones.
- [`docs/PLAN_REESTRUCTURACION.txt`](docs/PLAN_REESTRUCTURACION.txt): la reorganización de carpetas que ya se aplicó y qué cambió.
- [`CLAUDE.md`](CLAUDE.md): cómo trabaja Claude en el proyecto (una fase por conversación y `/clear` entre fases).

## Menú de inicio

El juego arranca en un menú de inicio: un fondo de tierra, el título y los botones *Un jugador* y *Salir*. *Un jugador* abre *Crear mundo*, donde se elige la semilla; después sale "Generando mundo..." hasta que el terreno donde apareces está listo y empieza la partida en la misma ventana. Con `Esc` se pausa y se puede salir al menú para entrar a un mundo nuevo. Las seis fases del plan están hechas; el plan está en [`docs/PLAN_MENU_INICIO.md`](docs/PLAN_MENU_INICIO.md).

## Limitaciones actuales

- Con cualquier semilla, el jugador aparece en el fondo de un río (ver "Cosas a revisar" en `docs/ARQUITECTURA.md`).
- La ventana tiene tamaño fijo (1280 × 720).
- No hay guardado ni carga de mundos: los bloques que rompes o pones se pierden al alejarte y volver.
- No hay enemigos, animales ni entidades dinámicas.
- No hay sistema de inventario completo.
- No hay multijugador.
- El soporte de ejecución está preparado principalmente para Windows por las dependencias nativas actuales de LWJGL.

## Créditos

- El título del menú está hecho con la tipografía [MINECRAFT PE](https://www.kiddiefonts.com) de SpideRaY, gratis para uso personal. El `.ttf` no está en el repo; solo la imagen `titulo.png`.
- La fuente de los botones es propia (`herramientas/fuente.txt`).

## Tecnologías

- Java 21
- Maven
- LWJGL 3.3.3
- JOML 1.10.5
- OpenGL
- GLFW
- GLSL
