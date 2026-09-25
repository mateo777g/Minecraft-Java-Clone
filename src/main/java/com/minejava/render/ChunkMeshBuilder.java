package com.minejava.render;

import java.util.Arrays;

import com.minejava.world.Block;
import com.minejava.world.Chunk;
import com.minejava.world.World;

// Convierte los bloques de un chunk en vértices (x, y, z, u, v), solo con las caras que se ven.
// Lo usan los hilos generadores. No reserva memoria por bloque ni por cara: con 35.000 caras por malla,
// eso era la basura que alargaba las pausas del GC (fase 2 de docs/PLAN_OPTIMIZACION.md).
public class ChunkMeshBuilder {

    private static final int SIZE = Chunk.CHUNK_SIZE;
    private static final int HEIGHT = Chunk.CHUNK_HEIGHT;

    // {uMin, uMax, vMin, vMax} de cada bloque (índice = ID), calculados una sola vez
    private static final float[][] UVS = new float[Block.CLOUD + 1][];
    static {
        for (int id = 0; id < UVS.length; id++) UVS[id] = getUVs(id);
    }

    // Donde se juntan los vértices. Hay una por hilo y se reutiliza de malla en malla: así crece una sola
    // vez por hilo y no en cada malla (cada vez que crece reserva un arreglo de varios MB)
    private static final ThreadLocal<ListaFloats> VERTICES = ThreadLocal.withInitial(ListaFloats::new);

    private final int[][][] blocks;
    // Los bloques de los 4 chunks de al lado, o null si no están cargados
    private final int[][][] oeste, este, norte, sur;
    private final ListaFloats vertices;

    // Genera la malla solo para bloques sólidos (Tierra, Arena, Piedra, Nubes, etc.)
    public static float[] buildOpaqueMesh(World world, int[][][] blocks, int chunkX, int chunkZ) {
        return new ChunkMeshBuilder(world, blocks, chunkX, chunkZ).armar(world, chunkX, chunkZ, false);
    }

    // Genera la malla solo para el agua
    public static float[] buildTransparentMesh(World world, int[][][] blocks, int chunkX, int chunkZ) {
        return new ChunkMeshBuilder(world, blocks, chunkX, chunkZ).armar(world, chunkX, chunkZ, true);
    }

    // Los chunks de al lado se buscan una sola vez, no una vez por bloque. Como hacía getBlockGlobal():
    // si no está en el mapa, es aire; si está, se lee su arreglo aunque todavía no tenga terreno (ceros = piedra).
    // En el juego no pasa ninguna de las dos: World pide la malla recién cuando los 4 ya tienen su terreno.
    private ChunkMeshBuilder(World world, int[][][] blocks, int chunkX, int chunkZ) {
        this.blocks = blocks;
        this.oeste = bloquesDe(world.getChunk(chunkX - 1, chunkZ));
        this.este = bloquesDe(world.getChunk(chunkX + 1, chunkZ));
        this.norte = bloquesDe(world.getChunk(chunkX, chunkZ - 1));
        this.sur = bloquesDe(world.getChunk(chunkX, chunkZ + 1));
        this.vertices = VERTICES.get();
    }

    private static int[][][] bloquesDe(Chunk chunk) {
        return chunk == null ? null : chunk.getBlocks();
    }

    private float[] armar(World world, int chunkX, int chunkZ, boolean transparentPass) {
        vertices.vaciar();

        int startX = chunkX * SIZE;
        int startZ = chunkZ * SIZE;

        for (int x = 0; x < SIZE; x++) {

            // Si se salió al menú a medio armar, los vecinos ya no están: el resto saldría con todas las caras
            if (world.estaCerrado()) return new float[0];

            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {

                    int blockType = blocks[x][y][z];
                    if (blockType == Block.AIR) continue;

                    // === SOLO EL AGUA VA AL PASE TRANSPARENTE ===
                    boolean isTransparentBlock = (blockType == Block.WATER);
                    if (transparentPass != isTransparentBlock) continue;

                    // Posición en el mundo para el renderizado
                    float realX = x + startX;
                    float realZ = z + startZ;
                    float[] uvs = UVS[blockType];

                    // Arriba del todo la cara se ve; debajo del fondo, no
                    if (y + 1 >= HEIGHT || shouldRenderFace(blockType, blocks[x][y + 1][z])) addTopFace(realX, y, realZ, uvs);
                    if (y - 1 >= 0 && shouldRenderFace(blockType, blocks[x][y - 1][z])) addBottomFace(realX, y, realZ, uvs);
                    if (shouldRenderFace(blockType, bloqueEn(x, y, z - 1))) addNorthFace(realX, y, realZ, uvs);
                    if (shouldRenderFace(blockType, bloqueEn(x, y, z + 1))) addSouthFace(realX, y, realZ, uvs);
                    if (shouldRenderFace(blockType, bloqueEn(x - 1, y, z))) addWestFace(realX, y, realZ, uvs);
                    if (shouldRenderFace(blockType, bloqueEn(x + 1, y, z))) addEastFace(realX, y, realZ, uvs);
                }
            }
        }

        return vertices.toArray();
    }

    // El bloque en (x, y, z), en coordenadas de este chunk. x o z pueden salirse un bloque: entonces se lee
    // el chunk de al lado, o aire si no está cargado.
    private int bloqueEn(int x, int y, int z) {
        if (x < 0) return oeste == null ? Block.AIR : oeste[SIZE - 1][y][z];
        if (x >= SIZE) return este == null ? Block.AIR : este[0][y][z];
        if (z < 0) return norte == null ? Block.AIR : norte[x][y][SIZE - 1];
        if (z >= SIZE) return sur == null ? Block.AIR : sur[x][y][0];
        return blocks[x][y][z];
    }

    // Si la cara de un bloque que da a su vecino se ve
    private static boolean shouldRenderFace(int currentID, int neighborID) {

        // Si el vecino es aire/vacío, la cara obligatoriamente se ve
        if (neighborID == Block.AIR) return true;

        // Caso 1: El bloque evaluado es AGUA
        if (currentID == Block.WATER) {
            if (neighborID == Block.WATER) return false; // Si chocamos con agua vecina (del mismo u otro chunk), ocultar pared.
            if (neighborID == Block.CLOUD) return true;  // Si hay una nube abajo, dejar ver el agua
            return false; // No renderizar caras internas del agua que colisionen contra arena, tierra o piedra profunda.
        }

        // Caso 2: El bloque evaluado es NUBE
        if (currentID == Block.CLOUD) {
            return neighborID != Block.CLOUD; // Ocultar caras internas compartidas entre bloques de nubes
        }

        // Caso 3: El bloque evaluado es SÓLIDO (Tierra, arena, piedra, madera, etc.)
        // Un bloque sólido dibuja su cara si su vecino es Agua, Nube o Aire.
        return (neighborID == Block.WATER || neighborID == Block.CLOUD);
    }

    // {uMin, uMax, vMin, vMax} de la casilla del atlas de ese bloque. También lo usa el fondo del menú.
    public static float[] getUVs(int blockType) {
        if (blockType == Block.WATER) return new float[]{-1.0f, -1.0f, -1.0f, -1.0f};
        if (blockType == Block.CLOUD) return new float[]{-2.0f, -2.0f, -2.0f, -2.0f};

        float atlasSize = 4.0f;
        int columnas = 4;

        int xCoord = blockType % columnas;
        int yCoord = blockType / columnas;
        int invertedY = (columnas - 1) - yCoord;

        float uMin = (float)xCoord / atlasSize;
        float uMax = ((float)xCoord + 1.0f) / atlasSize;
        float vMin = (float)invertedY / atlasSize;
        float vMax = ((float)invertedY + 1.0f) / atlasSize;

        return new float[]{uMin, uMax, vMin, vMax};
    }

    // Cada cara son 2 triángulos: 6 vértices de 5 floats (x, y, z, u, v)

    private void addNorthFace(float x, float y, float z, float[] uvs) {
        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];
        vertices.add(x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin);
        vertices.add(x - 0.5f, y - 0.5f, z - 0.5f,  uMin, vMax);
        vertices.add(x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax);
        vertices.add(x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin);
        vertices.add(x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax);
        vertices.add(x + 0.5f, y + 0.5f, z - 0.5f,  uMax, vMin);
    }

    private void addSouthFace(float x, float y, float z, float[] uvs) {
        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];
        vertices.add(x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin);
        vertices.add(x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMax);
        vertices.add(x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax);
        vertices.add(x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin);
        vertices.add(x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax);
        vertices.add(x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMin);
    }

    private void addEastFace(float x, float y, float z, float[] uvs) {
        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];
        vertices.add(x + 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin);
        vertices.add(x + 0.5f, y - 0.5f, z - 0.5f,  uMin, vMax);
        vertices.add(x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax);
        vertices.add(x + 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin);
        vertices.add(x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax);
        vertices.add(x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMin);
    }

    private void addWestFace(float x, float y, float z, float[] uvs) {
        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];
        vertices.add(x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin);
        vertices.add(x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMax);
        vertices.add(x - 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax);
        vertices.add(x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin);
        vertices.add(x - 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax);
        vertices.add(x - 0.5f, y + 0.5f, z - 0.5f,  uMax, vMin);
    }

    private void addTopFace(float x, float y, float z, float[] uvs) {
        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];
        vertices.add(x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin);
        vertices.add(x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMax);
        vertices.add(x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMax);
        vertices.add(x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin);
        vertices.add(x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMax);
        vertices.add(x + 0.5f, y + 0.5f, z - 0.5f,  uMax, vMin);
    }

    private void addBottomFace(float x, float y, float z, float[] uvs) {
        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];
        vertices.add(x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMin);
        vertices.add(x - 0.5f, y - 0.5f, z - 0.5f,  uMin, vMax);
        vertices.add(x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax);
        vertices.add(x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMin);
        vertices.add(x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax);
        vertices.add(x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMin);
    }

    // Un float[] que crece solo: hace lo mismo que una List<Float>, pero sin un objeto Float por cada número
    private static final class ListaFloats {
        // Una malla opaca tiene ~1 millón de floats: empezando así, crece unas pocas veces en cada hilo
        private float[] datos = new float[1 << 16];
        private int tamano = 0;

        void vaciar() {
            tamano = 0;
        }

        // Un vértice: posición y coordenadas de textura
        void add(float x, float y, float z, float u, float v) {
            if (tamano + 5 > datos.length) datos = Arrays.copyOf(datos, datos.length * 2);
            datos[tamano] = x;
            datos[tamano + 1] = y;
            datos[tamano + 2] = z;
            datos[tamano + 3] = u;
            datos[tamano + 4] = v;
            tamano += 5;
        }

        // Una copia justa de lo que se juntó: la lista se reutiliza en la próxima malla
        float[] toArray() {
            return Arrays.copyOf(datos, tamano);
        }
    }
}
