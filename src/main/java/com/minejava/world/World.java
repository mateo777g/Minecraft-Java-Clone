package com.minejava.world;

import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.minejava.player.Camera;
import com.minejava.player.PlayerController;

public class World {
    private int renderDistance;
    private Map<Long, Chunk> chunksActivos;
    private ExecutorService chunkGenerators;
    private ConcurrentLinkedQueue<Chunk> chunksListosParaGL;
    // Se pone en true en cleanup(). Lo leen los hilos secundarios, por eso es volatile.
    private volatile boolean cerrado = false;

    public World(int renderDistance) {
        this.renderDistance = renderDistance;
        this.chunksActivos = new ConcurrentHashMap<>();
        this.chunksListosParaGL = new ConcurrentLinkedQueue<>();
        
        int hilosDisponibles = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        // Hilos "daemon": si se cierra el juego con chunks a medio generar, no impiden que el programa termine
        this.chunkGenerators = Executors.newFixedThreadPool(hilosDisponibles, tarea -> {
            Thread hilo = new Thread(tarea, "generador-chunks");
            hilo.setDaemon(true);
            return hilo;
        });
        
        actualizarMundo(0, 0); 
    }

    private long generarClave(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    public void actualizarMundo(float playerX, float playerZ) {
        int centroChunkX = Math.floorDiv(Math.round(playerX), Chunk.CHUNK_SIZE);
        int centroChunkZ = Math.floorDiv(Math.round(playerZ), Chunk.CHUNK_SIZE);

        List<int[]> faltantes = new ArrayList<>();
        for (int x = -renderDistance; x <= renderDistance; x++) {
            for (int z = -renderDistance; z <= renderDistance; z++) {
                if (!chunksActivos.containsKey(generarClave(centroChunkX + x, centroChunkZ + z))) {
                    faltantes.add(new int[] { x, z });
                }
            }
        }
        // Los más cercanos al jugador primero: el pool los genera en el orden en que llegan, así el chunk
        // donde aparece el jugador sale enseguida y la pantalla de "Generando mundo..." dura poco
        faltantes.sort(Comparator.comparingInt(d -> d[0] * d[0] + d[1] * d[1]));

        for (int[] d : faltantes) {
            int targetCX = centroChunkX + d[0];
            int targetCZ = centroChunkZ + d[1];
            Chunk nuevoChunk = new Chunk(this, targetCX, targetCZ);
            chunksActivos.put(generarClave(targetCX, targetCZ), nuevoChunk);

            chunkGenerators.submit(() -> {
                nuevoChunk.generarTerrenoAsincrono(); 
                chunksListosParaGL.add(nuevoChunk); 
            });
        }

        Iterator<Map.Entry<Long, Chunk>> iterator = chunksActivos.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            Chunk chunk = entry.getValue();
            
            int distX = Math.abs(chunk.getChunkX() - centroChunkX);
            int distZ = Math.abs(chunk.getChunkZ() - centroChunkZ);

            if (distX > renderDistance || distZ > renderDistance) {
                chunk.cleanup(); 
                iterator.remove(); 
            }
        }
    }

    public void procesarMallasPendientes() {
        if (!chunksListosParaGL.isEmpty()) {
            Chunk c = chunksListosParaGL.poll();
            if (c != null && chunksActivos.containsValue(c)) {
                c.cargarMallaEnOpenGL(); 
            }
        }
    }

    public void render() {
        GL11.glDisable(GL11.GL_BLEND); 
        GL11.glDepthMask(true);        
    
        for (Chunk chunk : chunksActivos.values()) {
            if (chunk != null && chunk.estaListoParaRenderizar()) chunk.renderOpaque();
        }

        GL11.glEnable(GL11.GL_BLEND); 
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA); 
        GL11.glDepthMask(false); 
    
        for (Chunk chunk : chunksActivos.values()) {
            if (chunk != null && chunk.estaListoParaRenderizar()) chunk.renderTransparent();
        }
    
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
    }

    public int getBlockGlobal(int x, int y, int z) {
        int cx = Math.floorDiv(x, Chunk.CHUNK_SIZE);
        int cz = Math.floorDiv(z, Chunk.CHUNK_SIZE);
        long clave = generarClave(cx, cz);
        if (chunksActivos.containsKey(clave)) {
            int lx = Math.floorMod(x, Chunk.CHUNK_SIZE);
            int lz = Math.floorMod(z, Chunk.CHUNK_SIZE);
            return chunksActivos.get(clave).getBlock(lx, y, lz);
        }
        return Block.AIR;
    }

    public void setBlockGlobal(int x, int y, int z, int blockType) {
        int cx = Math.floorDiv(x, Chunk.CHUNK_SIZE);
        int cz = Math.floorDiv(z, Chunk.CHUNK_SIZE);
        long clave = generarClave(cx, cz);
        if (chunksActivos.containsKey(clave)) {
            int lx = Math.floorMod(x, Chunk.CHUNK_SIZE);
            int lz = Math.floorMod(z, Chunk.CHUNK_SIZE);
            chunksActivos.get(clave).setBlock(lx, y, lz, blockType);
            
            Chunk modificado = chunksActivos.get(clave);
            chunkGenerators.submit(() -> {
                modificado.generarTerrenoAsincrono(); 
                chunksListosParaGL.add(modificado);
            });
        }
    }

    // Si el chunk que contiene la columna (x, z) del mundo ya tiene su terreno.
    // Lo usa la pantalla de "Generando mundo..." para saber cuándo se puede calcular el spawn.
    public boolean estaGenerado(int x, int z) {
        Chunk chunk = chunkEn(x, z);
        return chunk != null && chunk.estaGenerado();
    }

    // Si el chunk que contiene la columna (x, z) del mundo ya tiene su malla en la GPU y se dibuja
    public boolean estaListoParaRenderizar(int x, int z) {
        Chunk chunk = chunkEn(x, z);
        return chunk != null && chunk.estaListoParaRenderizar();
    }

    // El chunk que contiene la columna (x, z) del mundo, o null si no está cargado
    private Chunk chunkEn(int x, int z) {
        return chunksActivos.get(generarClave(Math.floorDiv(x, Chunk.CHUNK_SIZE), Math.floorDiv(z, Chunk.CHUNK_SIZE)));
    }

    public float getAlturaSuperficie(int x, int z) {
        for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            int block = getBlockGlobal(x, y, z);
            if (Block.isSolid(block)) {
                return y + 1.0f;
            }
        }
        return Chunk.CHUNK_HEIGHT / 2.0f;
    }

    public void interactuarConTerreno(Camera camara, boolean romper, int selectedBlockType, PlayerController jugador) {
        Vector3f rayOrigin = new Vector3f(camara.getPosition());
        Vector3f rayDirection = camara.getDirection();
        float maxDistance = 5.0f;
        float step = 0.03f; // Un paso un poco más pequeño para dar más precisión      
        Vector3f currentPos = new Vector3f(rayOrigin);
        Vector3f lastVacantPos = new Vector3f(rayOrigin);

        for (float d = 0; d < maxDistance; d += step) {
            currentPos.set(rayDirection).mul(d).add(rayOrigin);
            
            // ¡CORREGIDO!: Se usa Math.floor para saber exactamente en qué cubo de la rejilla estamos
            int blockX = (int) Math.floor(currentPos.x);
            int blockY = (int) Math.floor(currentPos.y);
            int blockZ = (int) Math.floor(currentPos.z);
            
            int hitBlock = getBlockGlobal(blockX, blockY, blockZ);
            
            if (Block.isSolid(hitBlock)) {
                if (romper) {
                    setBlockGlobal(blockX, blockY, blockZ, Block.AIR);
                } else {
                    // ¡CORREGIDO!: El bloque vacío adyacente también se calcula con floor
                    int placeX = (int) Math.floor(lastVacantPos.x);
                    int placeY = (int) Math.floor(lastVacantPos.y);
                    int placeZ = (int) Math.floor(lastVacantPos.z);
                    
                    if (getBlockGlobal(placeX, placeY, placeZ) == Block.AIR) {
                        if (jugador.intersectsBlock(placeX, placeY, placeZ)) {
                            System.out.println("¡Bloqueado! No puedes poner un bloque sobre ti mismo bro.");
                        } else {
                            setBlockGlobal(placeX, placeY, placeZ, selectedBlockType);
                        }
                    }
                }
                break;
            }
            lastVacantPos.set(currentPos);
        }
    }

    public int getBlockAtCrosshair(Camera camara) {
        Vector3f rayOrigin = new Vector3f(camara.getPosition());
        Vector3f rayDirection = camara.getDirection();
        float maxDistance = 5.0f;
        float step = 0.03f;      
        Vector3f currentPos = new Vector3f(rayOrigin);

        for (float d = 0; d < maxDistance; d += step) {
            currentPos.set(rayDirection).mul(d).add(rayOrigin);
            
            // ¡CORREGIDO!: También aquí cambiamos a floor
            int blockX = (int) Math.floor(currentPos.x);
            int blockY = (int) Math.floor(currentPos.y);
            int blockZ = (int) Math.floor(currentPos.z);
            
            int hitBlock = getBlockGlobal(blockX, blockY, blockZ);
            
            if (Block.isSolid(hitBlock)) {
                return hitBlock;
            }
        }
        return Block.AIR;
    }

    // Si ya se llamó a cleanup(). Los chunks que se estaban generando lo revisan para no armar su malla.
    public boolean estaCerrado() {
        return cerrado;
    }

    public void cleanup() {
        cerrado = true;
        // shutdownNow() descarta los chunks que esperan en la cola. Con shutdown() se generarían igual y,
        // como el mapa ya está vacío, cada malla saldría con todas las caras (sin vecinos) y tardaría muchísimo.
        // Los que ya se estaban generando terminan su terreno, pero con "cerrado" no arman la malla.
        chunkGenerators.shutdownNow();
        for (Chunk chunk : chunksActivos.values()) {
            if (chunk != null) chunk.cleanup();
        }
        chunksActivos.clear();
        chunksListosParaGL.clear();
    }
}