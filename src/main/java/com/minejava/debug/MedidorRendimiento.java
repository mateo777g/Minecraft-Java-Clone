package com.minejava.debug;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.minejava.config.Constants;

// Mide cuánto tarda cada parte de los frames de la partida, si hubo GC y cuánto trabajo hacen los hilos
// generadores (fase 1 de docs/PLAN_OPTIMIZACION.md). Se prende con Constants.MEDIR_RENDIMIENTO; apagado,
// cada método vuelve enseguida.
//
// Cada frame se parte con marcar(): el tiempo desde la marca anterior va a esa parte. Imprime en la consola,
// con el prefijo [medidor]:
//  - cada frame que tarda más de FRAME_LENTO_MS, con lo que tardó cada parte y si hubo GC en ese frame
//  - cada RESUMEN_CADA_MS de juego, un resumen: FPS, el peor frame, los frames lentos, el GC y los chunks
//  - al terminar la partida (salir al menú o cerrar), el total
// Todo lo usa el hilo principal, salvo bytesReservadosHilo(), terrenoGenerado() y mallaArmada(), que llaman los
// hilos generadores.
// Lo que imprime va sin tildes ni ñ: según cómo se abra el juego, la consola de Windows las mostraría mal.
public final class MedidorRendimiento {

    // Las partes de un frame de JUGANDO, en el orden en que pasan
    public enum Parte {
        LIMPIAR("limpiar"),   // glClear y ESC: del inicio del frame hasta partida.update()
        JUGADOR("jugador"),   // Input.update(), el jugador y la cámara
        MUNDO("mundo"),       // actualizarMundo(), que solo corre al cambiar de chunk
        MALLAS("mallas"),     // procesarMallasPendientes(): sube una malla a la GPU
        RENDER("render"),     // el mundo y el HUD
        SWAP("swap");         // glfwSwapBuffers() y glfwPollEvents(): con V-Sync aquí se espera al monitor

        private final String nombre;

        Parte(String nombre) {
            this.nombre = nombre;
        }
    }

    private static final double FRAME_LENTO_MS = 25;
    private static final double RESUMEN_CADA_MS = 5000;
    // Si todos los frames son lentos, no llenar la consola: el resumen dice cuántos no se imprimieron
    private static final int MAX_LENTOS_POR_RESUMEN = 15;

    private static final Parte[] PARTES = Parte.values();
    private static final double MS = 1e6;   // Nanosegundos en un milisegundo
    private static final double MB = 1024.0 * 1024.0;

    // Los colectores de basura de la JVM. Los "Cycles" (ZGC, Shenandoah) miden trabajo concurrente, no pausas.
    private static final List<GarbageCollectorMXBean> COLECTORES = Constants.MEDIR_RENDIMIENTO
            ? ManagementFactory.getGarbageCollectorMXBeans().stream().filter(gc -> !gc.getName().contains("Cycles")).toList()
            : List.of();
    // Para saber cuánta memoria reserva cada hilo (null si esta JVM no lo sabe medir)
    private static final com.sun.management.ThreadMXBean HILOS = crearMedidorDeHilos();

    // ---- El frame actual ----
    private static long inicioFrame;
    private static long ultimaMarca;
    private static boolean frameDePartida;   // Si se llamó a marcar(): solo se miden los frames de JUGANDO
    private static final long[] parteFrame = new long[PARTES.length];
    private static long gcCuentaInicioFrame;
    private static long gcMsInicioFrame;
    private static int chunksPedidosFrame;
    private static int mallasSubidasFrame;
    private static long bytesSubidosFrame;

    // ---- Desde el último resumen ----
    private static long inicioMedicion;   // Primer frame medido (0 = todavía ninguno); el "t=" se cuenta desde aquí
    private static int frames;
    private static int lentos;
    private static int lentosImpresos;
    private static long sumaFrames;
    private static long peorFrame;
    private static final long[] sumaParte = new long[PARTES.length];
    private static final long[] maxParte = new long[PARTES.length];
    private static long gcCuenta;
    private static long gcMs;
    private static long gcMsPeorFrame;   // El frame con más GC: lo que el GC pudo trabar la pantalla de una vez
    private static int chunksPedidos;
    private static int mallasSubidas;
    private static int mallasDescartadas;
    private static long bytesSubidos;
    private static long bytesPrincipalInicio;

    // ---- Lo que escriben los hilos generadores (con el candado) ----
    private static final Object CANDADO = new Object();
    private static int terrenos;
    private static long nanosTerreno;
    private static long maxNanosTerreno;
    private static long bytesTerreno;
    private static int mallas;
    private static long nanosMallas;
    private static long maxNanosMalla;
    private static long bytesMallas;

    // ---- Total de la partida ----
    private static long totalFrames;
    private static int totalLentos;
    private static long totalSumaFrames;
    private static long totalPeorFrame;
    private static long totalGcCuenta;
    private static long totalGcMs;
    private static long totalGcMsPeorFrame;
    private static int totalChunksPedidos;

    private MedidorRendimiento() {
    }

    private static com.sun.management.ThreadMXBean crearMedidorDeHilos() {
        if (!Constants.MEDIR_RENDIMIENTO) return null;
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean hilos
                && hilos.isThreadAllocatedMemorySupported()) {
            hilos.setThreadAllocatedMemoryEnabled(true);
            return hilos;
        }
        return null;
    }

    // ========================================================================
    // Hilo principal: el ciclo de Main
    // ========================================================================

    // Al principio de cada frame, sea cual sea el estado
    public static void empezarFrame() {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        inicioFrame = System.nanoTime();
        ultimaMarca = inicioFrame;
        frameDePartida = false;
        Arrays.fill(parteFrame, 0);
        gcCuentaInicioFrame = gcCuentaTotal();
        gcMsInicioFrame = gcMsTotal();
        chunksPedidosFrame = 0;
        mallasSubidasFrame = 0;
        bytesSubidosFrame = 0;
    }

    // Terminó una parte del frame: se lleva el tiempo desde la marca anterior
    public static void marcar(Parte parte) {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        long ahora = System.nanoTime();
        parteFrame[parte.ordinal()] += ahora - ultimaMarca;
        ultimaMarca = ahora;
        frameDePartida = true;
    }

    // Después de glfwPollEvents(). El swap es lo que queda desde la última marca.
    // Si no fue un frame de la partida (menú, pausa, "Generando mundo..."), no cuenta.
    public static void terminarFrame() {
        if (!Constants.MEDIR_RENDIMIENTO || !frameDePartida) return;
        marcar(Parte.SWAP);
        long total = ultimaMarca - inicioFrame;
        long gcCuentaFrame = gcCuentaTotal() - gcCuentaInicioFrame;
        long gcMsFrame = gcMsTotal() - gcMsInicioFrame;

        if (inicioMedicion == 0) empezarMedicion();

        frames++;
        sumaFrames += total;
        peorFrame = Math.max(peorFrame, total);
        for (int i = 0; i < PARTES.length; i++) {
            sumaParte[i] += parteFrame[i];
            maxParte[i] = Math.max(maxParte[i], parteFrame[i]);
        }
        gcCuenta += gcCuentaFrame;
        gcMs += gcMsFrame;
        gcMsPeorFrame = Math.max(gcMsPeorFrame, gcMsFrame);

        if (total / MS > FRAME_LENTO_MS) {
            lentos++;
            if (lentosImpresos < MAX_LENTOS_POR_RESUMEN) {
                lentosImpresos++;
                imprimirFrameLento(total, gcCuentaFrame, gcMsFrame);
            }
        }

        if (sumaFrames / MS >= RESUMEN_CADA_MS) imprimirResumen();
    }

    // Al terminar la partida (salir al menú o cerrar el juego): lo que quedó sin resumir y el total
    public static void terminarPartida() {
        if (!Constants.MEDIR_RENDIMIENTO || inicioMedicion == 0) return;
        if (frames > 0) imprimirResumen();
        imprimir(String.format(Locale.ROOT,
                "==== total de la partida: %.1f s de juego, frames %d, %.1f FPS, peor frame %.1f ms, lentos (>%.0f ms) %d, "
                        + "GC: pausas %d, %d ms en total, el peor frame tuvo %d ms | chunks pedidos %d ====",
                totalSumaFrames / MS / 1000, totalFrames, fps(totalFrames, totalSumaFrames), totalPeorFrame / MS,
                FRAME_LENTO_MS, totalLentos, totalGcCuenta, totalGcMs, totalGcMsPeorFrame, totalChunksPedidos));
        inicioMedicion = 0;
        totalFrames = 0;
        totalLentos = 0;
        totalSumaFrames = 0;
        totalPeorFrame = 0;
        totalGcCuenta = 0;
        totalGcMs = 0;
        totalGcMsPeorFrame = 0;
        totalChunksPedidos = 0;
    }

    // ========================================================================
    // Lo que avisan World y Chunk
    // ========================================================================

    // actualizarMundo() creó estos chunks y mandó a generar su terreno (hilo principal)
    public static void chunksPedidos(int cantidad) {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        chunksPedidosFrame += cantidad;
        chunksPedidos += cantidad;
        totalChunksPedidos += cantidad;
    }

    // Se subió a la GPU una malla con tantos bytes de vértices (hilo principal)
    public static void mallaSubida(long bytes) {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        mallasSubidasFrame++;
        bytesSubidosFrame += bytes;
        mallasSubidas++;
        bytesSubidos += bytes;
    }

    // Una malla terminada no se subió porque su chunk ya salió del rango (hilo principal)
    public static void mallaDescartada() {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        mallasDescartadas++;
    }

    // Bytes que el hilo actual reservó desde que empezó; la diferencia entre dos llamadas es lo que se
    // reservó entre ellas. 0 si el medidor está apagado.
    public static long bytesReservadosHilo() {
        if (!Constants.MEDIR_RENDIMIENTO || HILOS == null) return 0;
        return HILOS.getCurrentThreadAllocatedBytes();
    }

    // Un hilo generador terminó el terreno de un chunk: lo que tardó y lo que reservó
    public static void terrenoGenerado(long nanos, long bytes) {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        synchronized (CANDADO) {
            terrenos++;
            nanosTerreno += nanos;
            maxNanosTerreno = Math.max(maxNanosTerreno, nanos);
            bytesTerreno += bytes;
        }
    }

    // Un hilo generador armó las dos mallas de un chunk (la opaca y la transparente)
    public static void mallaArmada(long nanos, long bytes) {
        if (!Constants.MEDIR_RENDIMIENTO) return;
        synchronized (CANDADO) {
            mallas++;
            nanosMallas += nanos;
            maxNanosMalla = Math.max(maxNanosMalla, nanos);
            bytesMallas += bytes;
        }
    }

    // ========================================================================
    // Consola
    // ========================================================================

    private static void empezarMedicion() {
        inicioMedicion = System.nanoTime();
        bytesPrincipalInicio = bytesReservadosHilo();
        Runtime rt = Runtime.getRuntime();
        String colectores = String.join(", ", COLECTORES.stream().map(GarbageCollectorMXBean::getName).toList());
        imprimir(String.format(Locale.ROOT,
                "prendido (Constants.MEDIR_RENDIMIENTO). Java %s, %d procesadores, heap max %.0f MB, GC: %s. "
                        + "Frame lento: de %.0f ms para arriba. Tiempos en ms.",
                System.getProperty("java.version"), rt.availableProcessors(), rt.maxMemory() / MB, colectores,
                FRAME_LENTO_MS));
    }

    private static void imprimirFrameLento(long total, long gcCuentaFrame, long gcMsFrame) {
        StringBuilder linea = new StringBuilder(String.format(Locale.ROOT, "t=%.1f s  frame lento %.1f ms:", segundos(), total / MS));
        for (int i = 0; i < PARTES.length; i++) {
            linea.append(String.format(Locale.ROOT, "%s %s %.1f", i == 0 ? "" : ",", PARTES[i].nombre, parteFrame[i] / MS));
        }
        linea.append(gcCuentaFrame == 0 ? " | sin GC"
                : String.format(Locale.ROOT, " | GC: pausas %d, %d ms", gcCuentaFrame, gcMsFrame));
        if (chunksPedidosFrame > 0) linea.append(" | chunks pedidos ").append(chunksPedidosFrame);
        if (mallasSubidasFrame > 0) {
            linea.append(String.format(Locale.ROOT, " | mallas subidas %d (%.1f MB)", mallasSubidasFrame, bytesSubidosFrame / MB));
        }
        imprimir(linea.toString());
    }

    private static void imprimirResumen() {
        imprimir(String.format(Locale.ROOT,
                "t=%.1f s  resumen de %.1f s de juego: frames %d, %.1f FPS, frame prom %.1f ms, peor %.1f ms, "
                        + "lentos (>%.0f ms) %d%s",
                segundos(), sumaFrames / MS / 1000, frames, fps(frames, sumaFrames), sumaFrames / MS / frames,
                peorFrame / MS, FRAME_LENTO_MS, lentos,
                lentos > lentosImpresos ? " (" + (lentos - lentosImpresos) + " sin imprimir)" : ""));

        StringBuilder partes = new StringBuilder("  partes, prom/max:");
        for (int i = 0; i < PARTES.length; i++) {
            partes.append(String.format(Locale.ROOT, "%s %s %.1f/%.1f", i == 0 ? "" : ",", PARTES[i].nombre,
                    sumaParte[i] / MS / frames, maxParte[i] / MS));
        }
        imprimir(partes.toString());

        int terrenosVentana, mallasVentana;
        long nanosTerrenoVentana, maxTerrenoVentana, bytesTerrenoVentana;
        long nanosMallasVentana, maxMallaVentana, bytesMallasVentana;
        synchronized (CANDADO) {
            terrenosVentana = terrenos;
            nanosTerrenoVentana = nanosTerreno;
            maxTerrenoVentana = maxNanosTerreno;
            bytesTerrenoVentana = bytesTerreno;
            mallasVentana = mallas;
            nanosMallasVentana = nanosMallas;
            maxMallaVentana = maxNanosMalla;
            bytesMallasVentana = bytesMallas;
            terrenos = 0;
            nanosTerreno = 0;
            maxNanosTerreno = 0;
            bytesTerreno = 0;
            mallas = 0;
            nanosMallas = 0;
            maxNanosMalla = 0;
            bytesMallas = 0;
        }

        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long bytesPrincipal = bytesReservadosHilo();
        imprimir(String.format(Locale.ROOT,
                "  GC: pausas %d, %d ms en total, el peor frame tuvo %d ms | heap: %.0f MB usados de %.0f (max %.0f) | "
                        + "el hilo principal reservo %.0f MB",
                gcCuenta, gcMs, gcMsPeorFrame, heap.getUsed() / MB, heap.getCommitted() / MB, heap.getMax() / MB,
                (bytesPrincipal - bytesPrincipalInicio) / MB));
        bytesPrincipalInicio = bytesPrincipal;

        // El terreno se genera un anillo más allá de las mallas: al cruzar un borde son 11 terrenos y 9 mallas
        StringBuilder chunks = new StringBuilder(String.format(Locale.ROOT, "  chunks: pedidos %d, terrenos %d", chunksPedidos, terrenosVentana));
        if (terrenosVentana > 0) {
            chunks.append(String.format(Locale.ROOT, " (prom. %.1f ms, %.1f MB reservados; el peor %.1f ms)",
                    nanosTerrenoVentana / MS / terrenosVentana, bytesTerrenoVentana / MB / terrenosVentana,
                    maxTerrenoVentana / MS));
        }
        chunks.append(String.format(Locale.ROOT, ", mallas armadas %d", mallasVentana));
        if (mallasVentana > 0) {
            chunks.append(String.format(Locale.ROOT, " (prom. %.1f ms, %.1f MB reservados; la peor %.1f ms)",
                    nanosMallasVentana / MS / mallasVentana, bytesMallasVentana / MB / mallasVentana,
                    maxMallaVentana / MS));
        }
        chunks.append(String.format(Locale.ROOT, ", subidas %d", mallasSubidas));
        if (mallasSubidas > 0) chunks.append(String.format(Locale.ROOT, " (%.1f MB prom.)", bytesSubidos / MB / mallasSubidas));
        chunks.append(", descartadas ").append(mallasDescartadas);
        imprimir(chunks.toString());

        totalFrames += frames;
        totalLentos += lentos;
        totalSumaFrames += sumaFrames;
        totalPeorFrame = Math.max(totalPeorFrame, peorFrame);
        totalGcCuenta += gcCuenta;
        totalGcMs += gcMs;
        totalGcMsPeorFrame = Math.max(totalGcMsPeorFrame, gcMsPeorFrame);

        frames = 0;
        lentos = 0;
        lentosImpresos = 0;
        sumaFrames = 0;
        peorFrame = 0;
        Arrays.fill(sumaParte, 0);
        Arrays.fill(maxParte, 0);
        gcCuenta = 0;
        gcMs = 0;
        gcMsPeorFrame = 0;
        chunksPedidos = 0;
        mallasSubidas = 0;
        mallasDescartadas = 0;
        bytesSubidos = 0;
    }

    private static void imprimir(String texto) {
        System.out.println("[medidor] " + texto);
    }

    private static double segundos() {
        return (System.nanoTime() - inicioMedicion) / MS / 1000;
    }

    private static double fps(long cuantosFrames, long nanos) {
        return nanos == 0 ? 0 : cuantosFrames / (nanos / MS / 1000);
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
