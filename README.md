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

El cursor se captura automáticamente al iniciar el juego.

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
│       ├── PerlinNoise.java      # Ruido para la generación del mundo
│       └── WorldGenerator.java   # Generación procedural del terreno
├── player/
│   ├── Camera.java               # Cámara en primera persona
│   ├── Input.java                # Teclado y ratón
│   └── PlayerController.java     # Movimiento y colisiones
├── ui/
│   └── Hud.java                  # Hotbar y mira
└── config/
    └── Constants.java            # Configuración general y bloques de la hotbar

src/main/resources/
├── shaders/                 # Vertex shader y fragment shader
└── textures/                # Atlas de texturas del terreno
```

## Limitaciones actuales

- No hay guardado ni carga de mundos.
- No hay enemigos, animales ni entidades dinámicas.
- No hay sistema de inventario completo.
- No hay multijugador.
- El soporte de ejecución está preparado principalmente para Windows por las dependencias nativas actuales de LWJGL.

## Tecnologías

- Java 21
- Maven
- LWJGL 3.3.3
- JOML 1.10.5
- OpenGL
- GLFW
- GLSL
