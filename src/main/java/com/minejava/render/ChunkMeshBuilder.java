package com.minejava.render;



import java.util.ArrayList;

import java.util.List;

import com.minejava.world.Block;
import com.minejava.world.Chunk;
import com.minejava.world.World;



public class ChunkMeshBuilder {



    // Genera la malla solo para bloques sólidos (Tierra, Arena, Piedra, etc.)

    // ¡AHORA RECIBE EL WORLD!

    public static float[] buildOpaqueMesh(World world, int[][][] blocks, int chunkX, int chunkZ) {

        return buildMeshFiltered(world, blocks, chunkX, chunkZ, false);

    }



    // Genera la malla solo para bloques con transparencia (Agua y Nubes)

    // ¡AHORA RECIBE EL WORLD!

    public static float[] buildTransparentMesh(World world, int[][][] blocks, int chunkX, int chunkZ) {

        return buildMeshFiltered(world, blocks, chunkX, chunkZ, true);

    }



    private static float[] buildMeshFiltered(World world, int[][][] blocks, int chunkX, int chunkZ, boolean transparentPass) {

        List<Float> verticesList = new ArrayList<>();



        int startX = chunkX * Chunk.CHUNK_SIZE;

        int startZ = chunkZ * Chunk.CHUNK_SIZE;



        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {

            for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {

                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {

               

                    int blockType = blocks[x][y][z];

                    if (blockType == Block.AIR) continue;



                    // === SOLO EL AGUA VA AL PASE TRANSPARENTE ===

                    boolean isTransparentBlock = (blockType == Block.WATER);

                 

                    if (transparentPass != isTransparentBlock) continue;



                    // Posición flotante para el renderizado

                    float realX = x + startX;

                    float realZ = z + startZ;



                    // Coordenadas enteras globales absolutas en el mundo entero

                    int globalX = x + startX;

                    int globalY = y;

                    int globalZ = z + startZ;

               

                    float[] uvs = getUVs(blockType);



                    // === FACE CULLING INTELIGENTE MULTI-CHUNK ===

                    if (shouldRenderFace(world, blockType, globalX, globalY + 1, globalZ)) addTopFace(verticesList, realX, y, realZ, uvs);

                    if (shouldRenderFace(world, blockType, globalX, globalY - 1, globalZ)) addBottomFace(verticesList, realX, y, realZ, uvs);

                    if (shouldRenderFace(world, blockType, globalX, globalY, globalZ - 1)) addNorthFace(verticesList, realX, y, realZ, uvs);

                    if (shouldRenderFace(world, blockType, globalX, globalY, globalZ + 1)) addSouthFace(verticesList, realX, y, realZ, uvs);

                    if (shouldRenderFace(world, blockType, globalX - 1, globalY, globalZ)) addWestFace(verticesList, realX, y, realZ, uvs);

                    if (shouldRenderFace(world, blockType, globalX + 1, globalY, globalZ)) addEastFace(verticesList, realX, y, realZ, uvs);

                }

            }

        }



        float[] vertices = new float[verticesList.size()];

        for (int i = 0; i < verticesList.size(); i++) {

            vertices[i] = verticesList.get(i);

        }

        return vertices;

    }  



    // Reemplaza por completo tu antigua lógica "isTransparent" local

    private static boolean shouldRenderFace(World world, int currentID, int globalX, int globalY, int globalZ) {

        // Controlar límites verticales físicos del mapa

        if (globalY < 0 || globalY >= Chunk.CHUNK_HEIGHT) {

            return globalY >= Chunk.CHUNK_HEIGHT;

        }



        // Consultar de forma global al mapa de chunks activos usando la genial matemática de tu World

        int neighborID = world.getBlockGlobal(globalX, globalY, globalZ);

       

        // Si el vecino es aire/vacío, la cara obligatoriamente se ve

        if (neighborID == Block.AIR) return true;



        // Caso 1: El bloque evaluado es AGUA

        if (currentID == Block.WATER) {

            if (neighborID == Block.WATER) return false; // SOLUCIÓN: Si chocamos con agua vecina (del mismo u otro chunk), ocultar pared.

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



    private static void addNorthFace(List<Float> list, float x, float y, float z, float[] uvs) {

        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];

        float[] vertices = {

            x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin,

            x - 0.5f, y - 0.5f, z - 0.5f,  uMin, vMax,

            x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax,

            x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin,

            x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax,

            x + 0.5f, y + 0.5f, z - 0.5f,  uMax, vMin

        };

        for (float v : vertices) list.add(v);

    }



    private static void addSouthFace(List<Float> list, float x, float y, float z, float[] uvs) {

        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];

        float[] vertices = {

            x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin,

            x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMax,

            x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax,

            x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin,

            x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax,

            x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMin

        };

        for (float v : vertices) list.add(v);

    }



    private static void addEastFace(List<Float> list, float x, float y, float z, float[] uvs) {

        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];

        float[] vertices = {

            x + 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin,

            x + 0.5f, y - 0.5f, z - 0.5f,  uMin, vMax,

            x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax,

            x + 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin,

            x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMax,

            x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMin

        };

        for (float v : vertices) list.add(v);

    }



    private static void addWestFace(List<Float> list, float x, float y, float z, float[] uvs) {

        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];

        float[] vertices = {

            x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin,

            x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMax,

            x - 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax,

            x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMin,

            x - 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax,

            x - 0.5f, y + 0.5f, z - 0.5f,  uMax, vMin

        };

        for (float v : vertices) list.add(v);

    }



    private static void addTopFace(List<Float> list, float x, float y, float z, float[] uvs) {

        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];

        float[] vertices = {

            x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin,

            x - 0.5f, y + 0.5f, z + 0.5f,  uMin, vMax,

            x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMax,

            x - 0.5f, y + 0.5f, z - 0.5f,  uMin, vMin,

            x + 0.5f, y + 0.5f, z + 0.5f,  uMax, vMax,

            x + 0.5f, y + 0.5f, z - 0.5f,  uMax, vMin

        };

        for (float v : vertices) list.add(v);

    }



    private static void addBottomFace(List<Float> list, float x, float y, float z, float[] uvs) {

        float uMin = uvs[0], uMax = uvs[1], vMin = uvs[2], vMax = uvs[3];

        float[] vertices = {

            x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMin,

            x - 0.5f, y - 0.5f, z - 0.5f,  uMin, vMax,

            x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax,

            x - 0.5f, y - 0.5f, z + 0.5f,  uMin, vMin,

            x + 0.5f, y - 0.5f, z - 0.5f,  uMax, vMax,

            x + 0.5f, y - 0.5f, z + 0.5f,  uMax, vMin

        };

        for (float v : vertices) list.add(v);

    }

} 

