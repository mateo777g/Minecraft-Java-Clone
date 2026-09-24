package com.minejava.world;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import com.minejava.render.ChunkMeshBuilder;
import com.minejava.world.gen.WorldGenerator;

public class Chunk {
    
    public static final int CHUNK_SIZE = 48; 
    public static final int CHUNK_HEIGHT = 200; 

    private World world;
    private int chunkX;
    private int chunkZ;
    private int[][][] blocks; 
    private boolean terrenoGenerado = false; // Para no recalcular montañas si solo rompemos un bloque

    // Capa Opaca (Terreno)
    private int opaqueVaoId = 0;
    private int opaqueVboId = 0;
    private int opaqueVertexCount = 0;

    // Capa Transparente (Agua y Nubes)
    private int transparentVaoId = 0;
    private int transparentVboId = 0;
    private int transparentVertexCount = 0;

    // === VARIABLES MULTITHREADING ===
    // Aquí los hilos secundarios guardan el resultado hasta que OpenGL esté listo
    private float[] pendingOpaqueVertices = null;
    private float[] pendingTransparentVertices = null;
    private volatile boolean readyToRender = false; 

    public Chunk(World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        // Solo inicializamos el array, el trabajo pesado lo hará el hilo secundario
        this.blocks = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
    }

    // ========================================================================
    // 1. TRABAJO DE CPU (HILO SECUNDARIO) - ¡Aquí no se puede usar OpenGL!
    // ========================================================================
    public void generarTerrenoAsincrono() {
        // Solo generamos el Perlin Noise la primera vez
        if (!terrenoGenerado) {
            WorldGenerator.generateTerrain(this.blocks, this.chunkX, this.chunkZ);
            terrenoGenerado = true;
        }

        // Construimos las mallas y las dejamos en "bandeja de espera"
        this.pendingOpaqueVertices = ChunkMeshBuilder.buildOpaqueMesh(world, blocks, chunkX, chunkZ);
        this.pendingTransparentVertices = ChunkMeshBuilder.buildTransparentMesh(world, blocks, chunkX, chunkZ);
    }

    // ========================================================================
    // 2. TRABAJO DE GPU (HILO PRINCIPAL) - Llamado por procesarMallasPendientes()
    // ========================================================================
    public void cargarMallaEnOpenGL() {
        // ---- CARGAR CAPA OPACA ----
        if (pendingOpaqueVertices != null) {
            this.opaqueVertexCount = pendingOpaqueVertices.length / 5;
            
            if (opaqueVertexCount > 0) {
                FloatBuffer buffer = MemoryUtil.memAllocFloat(pendingOpaqueVertices.length);
                buffer.put(pendingOpaqueVertices).flip();

                if (opaqueVaoId == 0) {
                    opaqueVaoId = GL30.glGenVertexArrays();
                    opaqueVboId = GL15.glGenBuffers();
                }

                GL30.glBindVertexArray(opaqueVaoId);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, opaqueVboId);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
                MemoryUtil.memFree(buffer);
            }
            pendingOpaqueVertices = null; // Liberamos memoria RAM
        } else {
            this.opaqueVertexCount = 0;
        }

        // ---- CARGAR CAPA TRANSPARENTE ----
        if (pendingTransparentVertices != null) {
            this.transparentVertexCount = pendingTransparentVertices.length / 5;
            
            if (transparentVertexCount > 0) {
                FloatBuffer buffer = MemoryUtil.memAllocFloat(pendingTransparentVertices.length);
                buffer.put(pendingTransparentVertices).flip();

                if (transparentVaoId == 0) {
                    transparentVaoId = GL30.glGenVertexArrays();
                    transparentVboId = GL15.glGenBuffers();
                }

                GL30.glBindVertexArray(transparentVaoId);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, transparentVboId);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_DYNAMIC_DRAW);
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
                GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
                MemoryUtil.memFree(buffer);
            }
            pendingTransparentVertices = null; // Liberamos memoria RAM
        } else {
            this.transparentVertexCount = 0;
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        
        // ¡Listo! Ya podemos decirle al World que nos dibuje
        this.readyToRender = true;
    }

    // ========================================================================
    // MÉTODOS DE RENDER Y LÓGICA
    // ========================================================================
    
    public boolean estaListoParaRenderizar() {
        return readyToRender;
    }

    public void renderOpaque() {
        if (!readyToRender || opaqueVertexCount == 0) return;
        GL30.glBindVertexArray(opaqueVaoId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, opaqueVertexCount);
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
    }

    public void renderTransparent() {
        if (!readyToRender || transparentVertexCount == 0) return;
        GL30.glBindVertexArray(transparentVaoId);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, transparentVertexCount);
        GL20.glDisableVertexAttribArray(0);
        GL20.glDisableVertexAttribArray(1);
    }

    public void setBlock(int x, int y, int z, int blockType) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blocks[x][y][z] = blockType;
            // IMPORTANTE: Ya no llamamos a generateMesh() aquí. 
            // El World.java se encarga de enviarnos al Hilo Secundario cuando rompemos un bloque.
        }
    }

    public int getBlock(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return blocks[x][y][z];
        }
        return -1; 
    }

    public int[][][] getBlocks() { return blocks; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    public void cleanup() {
        if (opaqueVboId != 0) GL15.glDeleteBuffers(opaqueVboId);
        if (opaqueVaoId != 0) GL30.glDeleteVertexArrays(opaqueVaoId);
        if (transparentVboId != 0) GL15.glDeleteBuffers(transparentVboId);
        if (transparentVaoId != 0) GL30.glDeleteVertexArrays(transparentVaoId);
    }
}