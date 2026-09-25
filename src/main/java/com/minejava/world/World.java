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
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.minejava.debug.MedidorRendimiento;
import com.minejava.player.Camera;
import com.minejava.player.PlayerController;
import com.minejava.world.gen.WorldGenerator;

public class World {
    // Hasta dónde se arman y se dibujan las mallas, en chunks. El terreno se genera un anillo más allá
    // (renderDistance + 1): ese anillo no tiene malla, está para ser vecino. Así la malla de cada chunk se pide
    // recién cuando sus 4 vecinos tienen terreno, y las caras de sus bordes salen bien a la primera.
    private int renderDistance;
    private Map<Long, Chunk> chunksActivos;
    // Hilos generadores: arman terrenos y mallas. Las mallas van primero (ver Tarea).
    private ThreadPoolExecutor chunkGenerators;
    // Los hilos dejan aquí los chunks que terminaron su terreno, para que el hilo principal pida las mallas
    private final ConcurrentLinkedQueue<Chunk> terrenosListos = new ConcurrentLinkedQueue<>();
    // ... y aquí las mallas terminadas, hasta que el hilo principal las suba a la GPU
    private final ConcurrentLinkedQueue<Chunk.MallaArmada> mallasListas = new ConcurrentLinkedQueue<>();
    // Tareas mandadas al pool que no terminaron. Las herramientas sin pantalla esperan a que llegue a 0.
    private final AtomicInteger tareasEnCurso = new AtomicInteger();
    // Número de orden de la próxima tarea (solo el hilo principal)
    private long ordenTareas = 0;
    // El chunk del jugador en la última llamada a actualizarMundo()
    private int centroChunkX;
    private int centroChunkZ;
    // Genera los chunks de este mundo con su semilla. Se crea antes de mandar tareas al pool.
    private final WorldGenerator generador;
    // Se pone en true en cleanup(). Lo leen los hilos secundarios, por eso es volatile.
    private volatile boolean cerrado = false;

    public World(int renderDistance, long semilla) {
        this.renderDistance = renderDistance;
        this.generador = new WorldGenerator(semilla);
        this.chunksActivos = new ConcurrentHashMap<>();
        
        int hilosDisponibles = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        // Hilos "daemon": si se cierra el juego con chunks a medio generar, no impiden que el programa termine.
        // La cola ordena las tareas con Tarea.compareTo(): por eso se mandan con execute() y no con submit().
        this.chunkGenerators = new ThreadPoolExecutor(hilosDisponibles, hilosDisponibles, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(), tarea -> {
                    Thread hilo = new Thread(tarea, "generador-chunks");
                    hilo.setDaemon(true);
                    return hilo;
                });
        // Todavía no pide ningún chunk: Partida busca el spawn y llama a actualizarMundo() con él
    }

    // Una tarea del pool. Las mallas van antes que los terrenos: son lo que se ve y usan terrenos que ya
    // están hechos. Entre dos del mismo tipo va la que se mandó antes, y los terrenos se mandan del más
    // cercano al jugador al más lejano.
    private record Tarea(boolean esMalla, long orden, Runnable trabajo) implements Runnable, Comparable<Tarea> {
        @Override
        public void run() {
            trabajo.run();
        }

        @Override
        public int compareTo(Tarea otra) {
            if (esMalla != otra.esMalla) return esMalla ? -1 : 1;
            return Long.compare(orden, otra.orden);
        }
    }

    public WorldGenerator getGenerador() {
        return generador;
    }

    // Solo para herramientas/MedirChunks.java: mete un chunk en el mapa sin mandarlo a los hilos, así la
    // herramienta genera el terreno y arma las mallas cuando quiere y las mide
    public void agregarChunk(Chunk chunk) {
        chunksActivos.put(generarClave(chunk.getChunkX(), chunk.getChunkZ()), chunk);
    }

    // La clave de un chunk en chunksActivos: chunkX y chunkZ juntos en un long, mezclados con el paso final de
    // SplitMix64 (a claves distintas les da números distintos). Sin mezclar, el hashCode() del Long era
    // chunkX ^ chunkZ: las 81 claves caían en 16 cubetas y cada búsqueda era lenta y reservaba memoria.
    private static long generarClave(int cx, int cz) {
        long clave = (((long) cx) << 32) | (cz & 0xffffffffL);
        clave = (clave ^ (clave >>> 30)) * 0xbf58476d1ce4e5b9L;
        clave = (clave ^ (clave >>> 27)) * 0x94d049bb133111ebL;
        return clave ^ (clave >>> 31);
    }

    // El chunk (chunkX, chunkZ), o null si no está cargado. Lo usa ChunkMeshBuilder para leer los bordes de sus vecinos.
    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunksActivos.get(generarClave(chunkX, chunkZ));
    }

    public void actualizarMundo(float playerX, float playerZ) {
        centroChunkX = Math.floorDiv(Math.round(playerX), Chunk.CHUNK_SIZE);
        centroChunkZ = Math.floorDiv(Math.round(playerZ), Chunk.CHUNK_SIZE);
        int radioTerreno = renderDistance + 1;

        List<int[]> faltantes = new ArrayList<>();
        for (int x = -radioTerreno; x <= radioTerreno; x++) {
            for (int z = -radioTerreno; z <= radioTerreno; z++) {
                if (!chunksActivos.containsKey(generarClave(centroChunkX + x, centroChunkZ + z))) {
                    faltantes.add(new int[] { x, z });
                }
            }
        }
        // Los más cercanos al jugador primero: el pool los genera en el orden en que llegan, así el chunk
        // donde aparece el jugador y sus vecinos salen enseguida y la pantalla de "Generando mundo..." dura poco
        faltantes.sort(Comparator.comparingInt(d -> d[0] * d[0] + d[1] * d[1]));
        MedidorRendimiento.chunksPedidos(faltantes.size());

        for (int[] d : faltantes) {
            int targetCX = centroChunkX + d[0];
            int targetCZ = centroChunkZ + d[1];
            Chunk nuevoChunk = new Chunk(this, targetCX, targetCZ);
            chunksActivos.put(generarClave(targetCX, targetCZ), nuevoChunk);

            // La malla la pide el hilo principal cuando estén los vecinos (sacarMallaParaSubir())
            mandar(false, () -> {
                nuevoChunk.generarTerreno();
                terrenosListos.add(nuevoChunk);
            });
        }

        Iterator<Map.Entry<Long, Chunk>> iterator = chunksActivos.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            Chunk chunk = entry.getValue();
            
            int distX = Math.abs(chunk.getChunkX() - centroChunkX);
            int distZ = Math.abs(chunk.getChunkZ() - centroChunkZ);

            if (distX > radioTerreno || distZ > radioTerreno) {
                chunk.cleanup(); 
                iterator.remove(); 
            } else if ((distX > renderDistance || distZ > renderDistance) && chunk.tieneMallaPedida()) {
                // Quedó en el anillo de afuera: se queda con su terreno, para ser vecino, pero sin malla
                chunk.liberarMalla();
            }
        }

        // Los que volvieron a quedar cerca (por ejemplo, al caminar para atrás) y ya tienen a sus vecinos
        for (Chunk chunk : chunksActivos.values()) {
            pedirMallaSiEstaListo(chunk);
        }
    }

    // Manda una tarea al pool (hilo principal)
    private void mandar(boolean esMalla, Runnable trabajo) {
        if (cerrado) return;
        tareasEnCurso.incrementAndGet();
        chunkGenerators.execute(new Tarea(esMalla, ordenTareas++, () -> {
            try {
                trabajo.run();
            } finally {
                tareasEnCurso.decrementAndGet();
            }
        }));
    }

    // Pide la malla del chunk si le toca y todavía no la pidió: está a renderDistance o menos del jugador,
    // tiene su terreno y los 4 de al lado también. Hilo principal.
    private void pedirMallaSiEstaListo(Chunk chunk) {
        if (chunk == null || chunk.tieneMallaPedida() || !chunk.estaGenerado()) return;
        int cx = chunk.getChunkX();
        int cz = chunk.getChunkZ();
        if (Math.abs(cx - centroChunkX) > renderDistance || Math.abs(cz - centroChunkZ) > renderDistance) return;
        if (tieneTerreno(cx - 1, cz) && tieneTerreno(cx + 1, cz) && tieneTerreno(cx, cz - 1) && tieneTerreno(cx, cz + 1)) {
            pedirMalla(chunk);
        }
    }

    private boolean tieneTerreno(int chunkX, int chunkZ) {
        Chunk chunk = getChunk(chunkX, chunkZ);
        return chunk != null && chunk.estaGenerado();
    }

    // Manda a armar la malla del chunk con sus bloques de ahora. Si ya había una pedida, la vieja se tira
    // cuando llegue (hilo principal).
    private void pedirMalla(Chunk chunk) {
        int version = chunk.pedirVersionMalla();
        mandar(true, () -> {
            // Si se salió al menú, el mapa ya está vacío: sin vecinos, la malla saldría con todas las caras
            // y tardaría muchísimo, y nadie la va a dibujar
            if (cerrado) return;
            mallasListas.add(chunk.armarMalla(version));
        });
    }

    // Hilo principal, una vez por frame (desde procesarMallasPendientes()). Primero, por cada chunk que terminó
    // su terreno, pide las mallas que ahora se pueden armar: la suya y las de sus 4 vecinos, que pueden haber
    // estado esperándolo. Después devuelve la próxima malla terminada que hay que subir a la GPU, o null. Las
    // que ya no sirven (su chunk se alejó, se descargó o pidió una más nueva) se tiran.
    // Está separado de la subida para que las herramientas sin pantalla lo usen igual que el juego.
    public Chunk.MallaArmada sacarMallaParaSubir() {
        Chunk listo;
        while ((listo = terrenosListos.poll()) != null) {
            int cx = listo.getChunkX();
            int cz = listo.getChunkZ();
            if (getChunk(cx, cz) != listo) continue; // Se descargó mientras se generaba
            pedirMallaSiEstaListo(listo);
            pedirMallaSiEstaListo(getChunk(cx - 1, cz));
            pedirMallaSiEstaListo(getChunk(cx + 1, cz));
            pedirMallaSiEstaListo(getChunk(cx, cz - 1));
            pedirMallaSiEstaListo(getChunk(cx, cz + 1));
        }

        Chunk.MallaArmada malla;
        while ((malla = mallasListas.poll()) != null) {
            if (malla.chunk().esMallaVigente(malla)) return malla;
            MedidorRendimiento.mallaDescartada();
        }
        return null;
    }

    // Sube a la GPU una malla terminada por frame
    public void procesarMallasPendientes() {
        Chunk.MallaArmada malla = sacarMallaParaSubir();
        if (malla != null) malla.chunk().cargarMallaEnOpenGL(malla);
    }

    // Si los hilos todavía tienen trabajo o hay terrenos o mallas sin procesar. Solo para las herramientas sin
    // pantalla: para saber cuándo terminó de cargarse el mundo, llaman a sacarMallaParaSubir() hasta que esto
    // da false. La tarea descuenta tareasEnCurso después de dejar su resultado en la cola: si da 0, está ahí.
    public boolean estaTrabajando() {
        return tareasEnCurso.get() > 0 || !terrenosListos.isEmpty() || !mallasListas.isEmpty();
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

    // Una sola búsqueda en el mapa (antes eran dos, containsKey y get). Si el chunk no está cargado, es aire.
    public int getBlockGlobal(int x, int y, int z) {
        Chunk chunk = chunkEn(x, z);
        if (chunk == null) return Block.AIR;
        return chunk.getBlock(Math.floorMod(x, Chunk.CHUNK_SIZE), y, Math.floorMod(z, Chunk.CHUNK_SIZE));
    }

    // Cambia un bloque y vuelve a pedir la malla de su chunk. Si el bloque está en el borde, también la del
    // chunk de al lado, que dibuja (o esconde) la cara que da a este bloque. Los chunks que todavía no
    // pidieron su malla la arman después, ya con el bloque nuevo.
    public void setBlockGlobal(int x, int y, int z, int blockType) {
        Chunk modificado = chunkEn(x, z);
        // Sin terreno todavía, el generador lo pisaría
        if (modificado == null || !modificado.estaGenerado() || y < 0 || y >= Chunk.CHUNK_HEIGHT) return;

        int localX = Math.floorMod(x, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(z, Chunk.CHUNK_SIZE);
        modificado.setBlock(localX, y, localZ, blockType);

        int cx = modificado.getChunkX();
        int cz = modificado.getChunkZ();
        volverAPedirMalla(modificado);
        if (localX == 0) volverAPedirMalla(getChunk(cx - 1, cz));
        if (localX == Chunk.CHUNK_SIZE - 1) volverAPedirMalla(getChunk(cx + 1, cz));
        if (localZ == 0) volverAPedirMalla(getChunk(cx, cz - 1));
        if (localZ == Chunk.CHUNK_SIZE - 1) volverAPedirMalla(getChunk(cx, cz + 1));
    }

    private void volverAPedirMalla(Chunk chunk) {
        if (chunk != null && chunk.tieneMallaPedida()) pedirMalla(chunk);
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
        terrenosListos.clear();
        mallasListas.clear();
    }
}