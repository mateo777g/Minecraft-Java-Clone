package com.minejava.world;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import com.minejava.debug.MedidorRendimiento;
import com.minejava.render.ChunkMeshBuilder;

public class Chunk {
    
    public static final int CHUNK_SIZE = 48; 
    public static final int CHUNK_HEIGHT = 200; 

    // Las dos mallas que armó un hilo generador, con los vértices (x, y, z, u, v) de la capa opaca y de la
    // transparente, y la versión con que se pidieron (ver versionMalla)
    public record MallaArmada(Chunk chunk, int version, float[] opaca, float[] transparente) {}

    private World world;
    private int chunkX;
    private int chunkZ;
    private int[][][] blocks; 
    // Es volatile porque lo escribe un hilo secundario y lo lee el principal: al verlo en true, el hilo
    // principal también ve los bloques que se escribieron antes.
    private volatile boolean terrenoGenerado = false;

    // Capa Opaca (Terreno)
    private int opaqueVaoId = 0;
    private int opaqueVboId = 0;
    private int opaqueVertexCount = 0;

    // Capa Transparente (Agua y Nubes)
    private int transparentVaoId = 0;
    private int transparentVboId = 0;
    private int transparentVertexCount = 0;

    private volatile boolean readyToRender = false; 

    // === LA MALLA (solo el hilo principal, desde World) ===
    // Si al chunk le toca tener malla: se pidió y no se liberó
    private boolean mallaPedida = false;
    // Cuántas veces se pidió la malla. Cada MallaArmada lleva el número con que se pidió y solo se sube la de
    // la última: si se rompen dos bloques seguidos, la malla del primero puede terminar después y se tira.
    private int versionMalla = 0;

    public Chunk(World world, int chunkX, int chunkZ) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        // Solo inicializamos el array, el trabajo pesado lo hará el hilo secundario
        this.blocks = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
    }

    // ========================================================================
    // 1. TRABAJO DE CPU (HILOS SECUNDARIOS) - ¡Aquí no se puede usar OpenGL!
    // ========================================================================

    // El terreno, una sola vez por chunk. Con la semilla del mundo: siempre sale igual.
    public void generarTerreno() {
        // Para el medidor: cuánto tarda y cuánta memoria reserva este hilo
        long inicio = System.nanoTime();
        long bytesInicio = MedidorRendimiento.bytesReservadosHilo();

        world.getGenerador().generateTerrain(this.blocks, this.chunkX, this.chunkZ);
        terrenoGenerado = true;

        MedidorRendimiento.terrenoGenerado(System.nanoTime() - inicio, MedidorRendimiento.bytesReservadosHilo() - bytesInicio);
    }

    // Las dos mallas, con los bloques que hay ahora. World la pide solo cuando los 4 chunks de al lado ya
    // tienen su terreno: así las caras de los bordes salen bien a la primera.
    public MallaArmada armarMalla(int version) {
        long inicio = System.nanoTime();
        long bytesInicio = MedidorRendimiento.bytesReservadosHilo();

        float[] opaca = ChunkMeshBuilder.buildOpaqueMesh(world, blocks, chunkX, chunkZ);
        float[] transparente = ChunkMeshBuilder.buildTransparentMesh(world, blocks, chunkX, chunkZ);

        MedidorRendimiento.mallaArmada(System.nanoTime() - inicio, MedidorRendimiento.bytesReservadosHilo() - bytesInicio);
        return new MallaArmada(this, version, opaca, transparente);
    }

    // ========================================================================
    // 2. TRABAJO DE GPU (HILO PRINCIPAL) - Llamado por World.procesarMallasPendientes()
    // ========================================================================
    public void cargarMallaEnOpenGL(MallaArmada malla) {
        // ---- CARGAR CAPA OPACA ----
        float[] opaca = malla.opaca();
        this.opaqueVertexCount = opaca.length / 5;

        if (opaqueVertexCount > 0) {
            FloatBuffer buffer = MemoryUtil.memAllocFloat(opaca.length);
            buffer.put(opaca).flip();

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

        // ---- CARGAR CAPA TRANSPARENTE ----
        float[] transparente = malla.transparente();
        this.transparentVertexCount = transparente.length / 5;

        if (transparentVertexCount > 0) {
            FloatBuffer buffer = MemoryUtil.memAllocFloat(transparente.length);
            buffer.put(transparente).flip();

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

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
        MedidorRendimiento.mallaSubida((long) (opaqueVertexCount + transparentVertexCount) * 5 * Float.BYTES);
        
        // ¡Listo! Ya podemos decirle al World que nos dibuje
        this.readyToRender = true;
    }

    // Borra la malla de la GPU y deja de pedirla: el chunk quedó más lejos que la distancia de render (sigue
    // como vecino, con su terreno) o se descarga entero. Si vuelve a quedar cerca, World le pide otra.
    public void liberarMalla() {
        if (opaqueVboId != 0) GL15.glDeleteBuffers(opaqueVboId);
        if (opaqueVaoId != 0) GL30.glDeleteVertexArrays(opaqueVaoId);
        if (transparentVboId != 0) GL15.glDeleteBuffers(transparentVboId);
        if (transparentVaoId != 0) GL30.glDeleteVertexArrays(transparentVaoId);
        opaqueVaoId = opaqueVboId = transparentVaoId = transparentVboId = 0;
        opaqueVertexCount = transparentVertexCount = 0;
        readyToRender = false;
        mallaPedida = false;
    }

    // ========================================================================
    // LA MALLA: LO QUE DECIDE WORLD (HILO PRINCIPAL)
    // ========================================================================

    boolean tieneMallaPedida() {
        return mallaPedida;
    }

    // Pide una malla nueva: devuelve su versión, con la que la arma el hilo generador
    int pedirVersionMalla() {
        mallaPedida = true;
        return ++versionMalla;
    }

    // Si esta malla es la última que se pidió y el chunk todavía la quiere (no se alejó ni se descargó)
    boolean esMallaVigente(MallaArmada malla) {
        return mallaPedida && malla.version() == versionMalla;
    }

    // ========================================================================
    // MÉTODOS DE RENDER Y LÓGICA
    // ========================================================================
    
    public boolean estaListoParaRenderizar() {
        return readyToRender;
    }

    // Antes de esto el arreglo de bloques está lleno de ceros, o sea de piedra
    public boolean estaGenerado() {
        return terrenoGenerado;
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
            // IMPORTANTE: Ya no llamamos a generateMesh() aquí. World.setBlockGlobal() vuelve a pedir
            // la malla de este chunk (y la del vecino, si el bloque está en el borde).
        }
    }

    public int getBlock(int x, int y, int z) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            return blocks[x][y][z];
        }
        return Block.AIR;
    }

    public int[][][] getBlocks() { return blocks; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }

    public void cleanup() {
        liberarMalla();
    }
}