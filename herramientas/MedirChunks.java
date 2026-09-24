import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;

import com.minejava.render.ChunkMeshBuilder;
import com.minejava.world.Chunk;
import com.minejava.world.World;
import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.ThreadMXBean;

// Mide sin pantalla ni GPU lo que cuesta un chunk (fase 1 de docs/PLAN_OPTIMIZACION.md), con la semilla 12345:
//  1. Un chunk en un solo hilo: tiempo y memoria reservada de cada paso (crear el Chunk, generar el terreno,
//     armar la malla opaca y la transparente).
//  2. Cruzar un borde: 9 chunks nuevos a la vez en núcleos − 1 hilos, como World. Cuánto tardan, el GC y
//     cuánto se retrasa un hilo "sonda" que hace de hilo principal (se despierta cada 1 ms).
//  3. El mundo no cambió: compara los bloques y las mallas de los chunks de la parte 1 (tamaño y hash) con
//     herramientas/referencia_mallas.txt. Si ese archivo no existe, lo crea.
//
// Usa las clases del juego, así que primero hay que compilarlo. Desde la carpeta del proyecto:
//     mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
//     java -cp "target/classes:$(cat target/classpath.txt)" herramientas/MedirChunks.java
// En Windows (PowerShell), con ; en vez de :
//     java -cp "target/classes;$(Get-Content target/classpath.txt)" herramientas/MedirChunks.java
// Con --guardar-referencia vuelve a escribir la referencia (solo si el mundo tenía que cambiar a propósito).
public class MedirChunks {

    private static final long SEMILLA = 12345;
    // Parte 1: las mallas de (2·RADIO + 1)² chunks alrededor de (0, 0), con un anillo más de terreno alrededor
    // para que todas tengan sus vecinos. Son los que se comparan con la referencia.
    private static final int RADIO = 2;
    // Parte 2: como en Partida, 4 → 9 × 9 chunks; el jugador avanza PASOS_BORDE chunks hacia +x
    private static final int RENDER_DISTANCE = 4;
    private static final int PASOS_BORDE = 5;
    private static final long SONDA_CADA_NS = 1_000_000;

    private static final Path REFERENCIA = Path.of("herramientas/referencia_mallas.txt");
    private static final double MS = 1e6;
    private static final double MB = 1024.0 * 1024.0;

    private static final ThreadMXBean HILOS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final List<GarbageCollectorMXBean> COLECTORES = ManagementFactory.getGarbageCollectorMXBeans()
            .stream().filter(gc -> !gc.getName().contains("Cycles")).toList();
    // Cada pausa de GC mientras se graba (las avisa otro hilo de la JVM)
    private static final List<Long> PAUSAS = new ArrayList<>();
    private static volatile boolean grabandoPausas = false;

    public static void main(String[] args) throws Exception {
        // En UTF-8, así las tildes se ven bien en la terminal
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        boolean guardarReferencia = List.of(args).contains("--guardar-referencia");
        escucharPausas();

        Runtime rt = Runtime.getRuntime();
        System.out.printf(Locale.ROOT, "MedirChunks: semilla %d, chunks de %d × %d × %d. Java %s, %d núcleos, heap máx %.0f MB, GC: %s%n%n",
                SEMILLA, Chunk.CHUNK_SIZE, Chunk.CHUNK_HEIGHT, Chunk.CHUNK_SIZE, System.getProperty("java.version"),
                rt.availableProcessors(), rt.maxMemory() / MB,
                String.join(", ", COLECTORES.stream().map(GarbageCollectorMXBean::getName).toList()));

        // Lejos de los chunks que se miden: al principio la JVM interpreta el código y todavía no lo optimizó
        medirArea(1000, 1000, 1, null);

        Map<String, String> resumen = new LinkedHashMap<>();
        medirArea(0, 0, RADIO, resumen);
        cruzarBordes();
        compararConReferencia(resumen, guardarReferencia);
    }

    // ========================================================================
    // Parte 1: un chunk en un solo hilo
    // ========================================================================

    // Lo que tardó un paso y lo que reservó, sumado sobre todos los chunks
    private static class Paso {
        final String nombre;
        int veces;
        long nanos, maxNanos, bytes, maxBytes;

        Paso(String nombre) {
            this.nombre = nombre;
        }

        <T> T medir(Supplier<T> trabajo) {
            long bytesInicio = HILOS.getCurrentThreadAllocatedBytes();
            long inicio = System.nanoTime();
            T resultado = trabajo.get();
            long duracion = System.nanoTime() - inicio;
            long reservado = HILOS.getCurrentThreadAllocatedBytes() - bytesInicio;
            veces++;
            nanos += duracion;
            maxNanos = Math.max(maxNanos, duracion);
            bytes += reservado;
            maxBytes = Math.max(maxBytes, reservado);
            return resultado;
        }

        double promMs() {
            return nanos / MS / veces;
        }

        double promMb() {
            return bytes / MB / veces;
        }

        void imprimir() {
            System.out.printf(Locale.ROOT, "   %-32s %8.1f %8.1f %10.1f %8.1f%n", nombre, promMs(), maxNanos / MS, promMb(), maxBytes / MB);
        }
    }

    // Los mismos pasos que World.actualizarMundo() y Chunk.generarTerrenoAsincrono(), pero por separado para
    // medir cada uno. Primero el terreno de todos (con un anillo más) y después las mallas: así las mallas
    // salen siempre iguales, porque todos sus vecinos ya están generados. Con resumen == null solo calienta.
    private static void medirArea(int centroX, int centroZ, int radio, Map<String, String> resumen) throws Exception {
        World mundo = new World(RENDER_DISTANCE, SEMILLA);
        Paso crear = new Paso("new Chunk (arreglo de bloques)");
        Paso terreno = new Paso("generar terreno");
        Paso opaca = new Paso("malla opaca");
        Paso transparente = new Paso("malla transparente");
        long floatsOpaca = 0, floatsTransparente = 0;

        int conAnillo = radio + 1;
        Map<Long, Chunk> chunks = new LinkedHashMap<>();
        for (int cx = centroX - conAnillo; cx <= centroX + conAnillo; cx++) {
            for (int cz = centroZ - conAnillo; cz <= centroZ + conAnillo; cz++) {
                int x = cx, z = cz;
                Chunk chunk = crear.medir(() -> new Chunk(mundo, x, z));
                mundo.agregarChunk(chunk);
                chunks.put(clave(cx, cz), chunk);
            }
        }
        for (Chunk chunk : chunks.values()) {
            terreno.medir(() -> {
                mundo.getGenerador().generateTerrain(chunk.getBlocks(), chunk.getChunkX(), chunk.getChunkZ());
                return null;
            });
        }
        for (int cx = centroX - radio; cx <= centroX + radio; cx++) {
            for (int cz = centroZ - radio; cz <= centroZ + radio; cz++) {
                Chunk chunk = chunks.get(clave(cx, cz));
                int x = cx, z = cz;
                float[] verticesOpaca = opaca.medir(() -> ChunkMeshBuilder.buildOpaqueMesh(mundo, chunk.getBlocks(), x, z));
                float[] verticesTransp = transparente.medir(() -> ChunkMeshBuilder.buildTransparentMesh(mundo, chunk.getBlocks(), x, z));
                floatsOpaca += verticesOpaca.length;
                floatsTransparente += verticesTransp.length;
                if (resumen != null) {
                    resumen.put(cx + " " + cz, String.join(" ", hashBloques(chunk),
                            String.valueOf(verticesOpaca.length), hash(verticesOpaca),
                            String.valueOf(verticesTransp.length), hash(verticesTransp)));
                }
            }
        }
        mundo.cleanup();
        if (resumen == null) return;

        int lado = 2 * radio + 1;
        System.out.printf(Locale.ROOT, "1) Un chunk, en un solo hilo: %d mallas (%d × %d chunks alrededor de (0, 0)) y %d terrenos%n",
                opaca.veces, lado, lado, terreno.veces);
        System.out.printf(Locale.ROOT, "   %-32s %8s %8s %10s %8s%n", "paso", "prom ms", "máx ms", "prom MB", "máx MB");
        crear.imprimir();
        terreno.imprimir();
        opaca.imprimir();
        transparente.imprimir();
        System.out.printf(Locale.ROOT, "   %-32s %8.1f %8s %10.1f%n", "un chunk completo",
                crear.promMs() + terreno.promMs() + opaca.promMs() + transparente.promMs(), "",
                crear.promMb() + terreno.promMb() + opaca.promMb() + transparente.promMb());
        // 30 floats por cara: 6 vértices de 5 floats (x, y, z, u, v)
        System.out.printf(Locale.ROOT, "   Caras por malla, en promedio: opaca %.0f (%.1f MB de vértices), transparente %.0f (%.1f MB)%n",
                floatsOpaca / 30.0 / opaca.veces, floatsOpaca * 4.0 / MB / opaca.veces,
                floatsTransparente / 30.0 / transparente.veces, floatsTransparente * 4.0 / MB / transparente.veces);
        System.out.printf(Locale.ROOT, "   (MB = memoria que el paso reservó en el heap: casi toda es basura que después limpia el GC)%n%n");
    }

    // ========================================================================
    // Parte 2: cruzar un borde
    // ========================================================================

    // Un hilo que hace de hilo principal: se duerme 1 ms una y otra vez y anota cuánto se despierta tarde.
    // Las pausas del GC y la pelea por los núcleos con los generadores se ven como retrasos.
    private static class Sonda extends Thread {
        private volatile boolean seguir = true;
        private long maxRetraso;
        private int retrasosDe5ms;

        Sonda() {
            super("sonda");
            setDaemon(true);
        }

        @Override
        public void run() {
            while (seguir) {
                long inicio = System.nanoTime();
                LockSupport.parkNanos(SONDA_CADA_NS);
                long retraso = System.nanoTime() - inicio - SONDA_CADA_NS;
                maxRetraso = Math.max(maxRetraso, retraso);
                if (retraso > 5 * MS) retrasosDe5ms++;
            }
        }

        void terminar() throws InterruptedException {
            seguir = false;
            join();
        }
    }

    // Como en el juego: el mundo cargado (9 × 9 chunks con terreno) y el jugador cruza PASOS_BORDE bordes hacia +x.
    // En cada paso, el "hilo principal" crea los 9 chunks de la columna nueva y los manda a núcleos − 1 hilos,
    // que corren Chunk.generarTerrenoAsincrono(), lo mismo que en el juego.
    private static void cruzarBordes() throws Exception {
        World mundo = new World(RENDER_DISTANCE, SEMILLA);
        for (int cx = -RENDER_DISTANCE; cx <= RENDER_DISTANCE; cx++) {
            for (int cz = -RENDER_DISTANCE; cz <= RENDER_DISTANCE; cz++) {
                Chunk chunk = new Chunk(mundo, cx, cz);
                mundo.agregarChunk(chunk);
                mundo.getGenerador().generateTerrain(chunk.getBlocks(), cx, cz);
            }
        }

        int cuantosHilos = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);   // Igual que World
        List<Thread> generadores = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(cuantosHilos, tarea -> {
            Thread hilo = new Thread(tarea, "generador-chunks");
            hilo.setDaemon(true);
            synchronized (generadores) {
                generadores.add(hilo);
            }
            return hilo;
        });

        int lado = 2 * RENDER_DISTANCE + 1;
        System.out.printf(Locale.ROOT, "2) Cruzar un borde: %d chunks nuevos en %d hilos generadores, %d veces%n", lado, cuantosHilos, PASOS_BORDE);
        double sumaMs = 0, sumaGcMs = 0, sumaMb = 0;
        long peorPausa = 0, peorRetraso = 0;
        for (int paso = 1; paso <= PASOS_BORDE; paso++) {
            int nuevaX = paso + RENDER_DISTANCE;

            Thread.sleep(200);   // Que no se mezcle el GC del paso anterior
            Sonda sonda = new Sonda();
            sonda.start();
            long gcCuenta = gcCuentaTotal(), gcMs = gcMsTotal();
            long bytesGeneradores = bytesDe(generadores);
            synchronized (PAUSAS) {
                PAUSAS.clear();
            }
            grabandoPausas = true;

            // Lo que hace actualizarMundo() en el hilo principal: crear los chunks (del más cercano al más lejano)
            long bytesInicio = HILOS.getCurrentThreadAllocatedBytes();
            long inicio = System.nanoTime();
            List<Future<?>> tareas = new ArrayList<>();
            for (int d = 0; d <= RENDER_DISTANCE; d++) {
                for (int cz : d == 0 ? new int[] { 0 } : new int[] { -d, d }) {
                    Chunk chunk = new Chunk(mundo, nuevaX, cz);
                    mundo.agregarChunk(chunk);
                    tareas.add(pool.submit(chunk::generarTerrenoAsincrono));
                }
            }
            long nanosCrear = System.nanoTime() - inicio;
            long bytesCrear = HILOS.getCurrentThreadAllocatedBytes() - bytesInicio;
            for (Future<?> tarea : tareas) tarea.get();
            long nanosTotal = System.nanoTime() - inicio;

            sonda.terminar();
            Thread.sleep(50);   // Los avisos del GC llegan un poco después
            grabandoPausas = false;
            long pausaMax;
            synchronized (PAUSAS) {
                pausaMax = PAUSAS.stream().mapToLong(Long::longValue).max().orElse(0);
            }
            long gcMsPaso = gcMsTotal() - gcMs;
            double mbGeneradores = (bytesDe(generadores) - bytesGeneradores) / MB;

            System.out.printf(Locale.ROOT, "   paso %d: %4.0f ms | hilo principal: crear los chunks %.1f ms (%.0f MB) | los generadores reservaron %.0f MB"
                    + " | GC: pausas %d, %d ms, la más larga %d ms | sonda: retraso máx %.1f ms, más de 5 ms: %d%n",
                    paso, nanosTotal / MS, nanosCrear / MS, bytesCrear / MB, mbGeneradores,
                    gcCuentaTotal() - gcCuenta, gcMsPaso, pausaMax, sonda.maxRetraso / MS, sonda.retrasosDe5ms);
            sumaMs += nanosTotal / MS;
            sumaGcMs += gcMsPaso;
            sumaMb += mbGeneradores;
            peorPausa = Math.max(peorPausa, pausaMax);
            peorRetraso = Math.max(peorRetraso, sonda.maxRetraso);
        }
        System.out.printf(Locale.ROOT, "   promedio: %.0f ms por borde, %.0f MB reservados (%.0f MB por chunk), GC %.0f ms por borde;"
                + " pausa más larga %d ms, retraso máx de la sonda %.1f ms%n%n",
                sumaMs / PASOS_BORDE, sumaMb / PASOS_BORDE, sumaMb / PASOS_BORDE / lado, sumaGcMs / PASOS_BORDE,
                peorPausa, peorRetraso / MS);
        pool.shutdownNow();
        mundo.cleanup();
    }

    // ========================================================================
    // Parte 3: el mundo no cambió
    // ========================================================================

    private static void compararConReferencia(Map<String, String> resumen, boolean guardar) throws Exception {
        if (guardar || !Files.exists(REFERENCIA)) {
            List<String> lineas = new ArrayList<>();
            lineas.add("# Bloques y mallas de herramientas/MedirChunks.java: semilla " + SEMILLA + ", " + (2 * RADIO + 1) + " × "
                    + (2 * RADIO + 1) + " chunks alrededor de (0, 0).");
            lineas.add("# Sirve para comprobar que una optimización no cambia el mundo: la herramienta compara lo que sale con esto.");
            lineas.add("# chunkX chunkZ hashBloques floatsOpaca hashOpaca floatsTransparente hashTransparente");
            resumen.forEach((chunk, datos) -> lineas.add(chunk + " " + datos));
            Files.write(REFERENCIA, lineas, StandardCharsets.UTF_8);
            System.out.printf("3) Guardé la referencia en %s (%d chunks). Las próximas veces se compara con ella.%n", REFERENCIA, resumen.size());
            return;
        }

        Map<String, String> referencia = new LinkedHashMap<>();
        for (String linea : Files.readAllLines(REFERENCIA, StandardCharsets.UTF_8)) {
            if (linea.isBlank() || linea.startsWith("#")) continue;
            String[] partes = linea.split(" ", 3);
            referencia.put(partes[0] + " " + partes[1], partes[2]);
        }
        List<String> distintos = new ArrayList<>();
        for (Map.Entry<String, String> e : resumen.entrySet()) {
            String esperado = referencia.get(e.getKey());
            if (esperado == null) {
                distintos.add("(" + e.getKey() + ") no está en la referencia");
                continue;
            }
            String[] a = esperado.split(" "), b = e.getValue().split(" ");
            List<String> que = new ArrayList<>();
            if (!a[0].equals(b[0])) que.add("bloques");
            if (!a[1].equals(b[1]) || !a[2].equals(b[2])) que.add("malla opaca (" + a[1] + " → " + b[1] + " floats)");
            if (!a[3].equals(b[3]) || !a[4].equals(b[4])) que.add("malla transparente (" + a[3] + " → " + b[3] + " floats)");
            if (!que.isEmpty()) distintos.add("(" + e.getKey() + "): " + String.join(", ", que));
        }
        if (distintos.isEmpty() && referencia.size() == resumen.size()) {
            System.out.printf("3) El mundo no cambió: los %d chunks tienen los mismos bloques y las mismas mallas que %s.%n", resumen.size(), REFERENCIA);
        } else {
            System.out.printf("3) ¡EL MUNDO CAMBIÓ! %d de %d chunks son distintos a %s:%n", distintos.size(), resumen.size(), REFERENCIA);
            for (String d : distintos) System.out.println("   " + d);
            System.exit(1);
        }
    }

    // ========================================================================
    // Ayudas
    // ========================================================================

    private static long clave(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xffffffffL);
    }

    // Con getBlock() y no con el arreglo: así el hash no cambia si algún día los bloques se guardan de otra forma
    private static String hashBloques(Chunk chunk) throws Exception {
        ByteBuffer datos = ByteBuffer.allocate(Chunk.CHUNK_SIZE * Chunk.CHUNK_HEIGHT * Chunk.CHUNK_SIZE * Integer.BYTES);
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++)
            for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++)
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++)
                    datos.putInt(chunk.getBlock(x, y, z));
        return sha(datos.array());
    }

    // Los bits exactos de cada float, en orden: cualquier vértice distinto o en otro orden cambia el hash
    private static String hash(float[] vertices) throws Exception {
        ByteBuffer datos = ByteBuffer.allocate(vertices.length * Float.BYTES);
        for (float v : vertices) datos.putInt(Float.floatToRawIntBits(v));
        return sha(datos.array());
    }

    private static String sha(byte[] datos) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos), 0, 8);
    }

    private static long bytesDe(List<Thread> hilos) {
        long total = 0;
        synchronized (hilos) {
            for (Thread hilo : hilos) total += Math.max(0, HILOS.getThreadAllocatedBytes(hilo.threadId()));
        }
        return total;
    }

    private static void escucharPausas() {
        for (GarbageCollectorMXBean gc : COLECTORES) {
            ((NotificationEmitter) gc).addNotificationListener((aviso, datos) -> {
                if (!grabandoPausas) return;
                var info = GarbageCollectionNotificationInfo.from((CompositeData) aviso.getUserData());
                synchronized (PAUSAS) {
                    PAUSAS.add(info.getGcInfo().getDuration());
                }
            }, aviso -> aviso.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION), null);
        }
    }

    private static long gcCuentaTotal() {
        long cuenta = 0;
        for (GarbageCollectorMXBean gc : COLECTORES) cuenta += Math.max(0, gc.getCollectionCount());
        return cuenta;
    }

    private static long gcMsTotal() {
        long ms = 0;
        for (GarbageCollectorMXBean gc : COLECTORES) ms += Math.max(0, gc.getCollectionTime());
        return ms;
    }
}
