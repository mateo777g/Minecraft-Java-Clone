import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ThreadPoolExecutor;

import com.minejava.render.ChunkMeshBuilder;
import com.minejava.world.Chunk;
import com.minejava.world.World;

// Revisa sin pantalla las mallas de los bordes de chunk (las rayas del agua, fase 1 de docs/PLAN_AGUA_JUGADOR.md).
// Carga el mundo como el juego: busca el spawn, llama a World.actualizarMundo() y deja que los hilos del World
// generen y armen los chunks. Cada malla terminada la "sube" como procesarMallasPendientes(), pero guardándola
// aquí en vez de mandarla a la GPU. Después avanza hacia +x de a un chunk y, al final, compara cada malla subida
// con la misma malla armada otra vez con todos los vecinos ya generados, que es la correcta:
//  - caras de agua que sobran: paredes de agua contra un chunk que sí está cargado (las rayas);
//  - caras opacas que faltan (huecos en los bordes) y que sobran (paredes escondidas en el terreno).
// Lee campos privados de World y Chunk (el pool, la cola de mallas y las mallas pendientes): si la fase 1 los
// cambia, hay que ajustar esta herramienta.
//
// Usa las clases del juego, así que primero hay que compilarlo. Desde la carpeta del proyecto:
//     mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
//     java -cp "target/classes:$(cat target/classpath.txt)" herramientas/RevisarBordes.java [semilla] [chunks]
// Por defecto, semilla 12345 y 5 chunks.
public class RevisarBordes {

    private static final int SIZE = Chunk.CHUNK_SIZE;
    private static final int RENDER_DISTANCE = 4; // como Partida
    // 30 floats por cara: 6 vértices de 5 floats (x, y, z, u, v)
    private static final int FLOATS_CARA = 30;

    // Lo que "subió" a la GPU cada chunk: su malla opaca y la transparente
    private static final Map<Chunk, float[][]> subidas = new IdentityHashMap<>();

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        long semilla = args.length > 0 ? Long.parseLong(args[0]) : 12345;
        int pasos = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        World mundo = new World(RENDER_DISTANCE, semilla);
        int[] spawn = mundo.getGenerador().buscarSpawn();
        float x = spawn[0], z = spawn[1];
        System.out.printf(Locale.ROOT, "RevisarBordes: semilla %d, spawn (%d, %d), %d hilos generadores%n%n",
                semilla, spawn[0], spawn[1], ((ThreadPoolExecutor) campo(mundo, "chunkGenerators")).getCorePoolSize());

        mundo.actualizarMundo(x, z);
        esperar(mundo);
        revisar(mundo, "Carga inicial");

        for (int i = 0; i < pasos; i++) {
            x += SIZE;
            mundo.actualizarMundo(x, z);
            esperar(mundo);
        }
        revisar(mundo, "Después de avanzar " + pasos + " chunks hacia +x");
        mundo.cleanup();
    }

    // Espera a que los hilos terminen y "sube" las mallas en el orden en que llegaron, como el juego
    // (una más nueva del mismo chunk reemplaza a la anterior)
    @SuppressWarnings("unchecked")
    private static void esperar(World mundo) throws Exception {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) campo(mundo, "chunkGenerators");
        Queue<Chunk> cola = (Queue<Chunk>) campo(mundo, "chunksListosParaGL");
        Map<Long, Chunk> activos = (Map<Long, Chunk>) campo(mundo, "chunksActivos");
        while (true) {
            Chunk c;
            while ((c = cola.poll()) != null) {
                if (!activos.containsValue(c)) continue; // procesarMallasPendientes() la descarta
                subidas.put(c, new float[][] {
                        (float[]) campo(c, "pendingOpaqueVertices"),
                        (float[]) campo(c, "pendingTransparentVertices") });
            }
            if (pool.getActiveCount() == 0 && pool.getQueue().isEmpty() && cola.isEmpty()) break;
            Thread.sleep(5);
        }
        subidas.keySet().retainAll(activos.values());
    }

    @SuppressWarnings("unchecked")
    private static void revisar(World mundo, String titulo) throws Exception {
        Map<Long, Chunk> activos = (Map<Long, Chunk>) campo(mundo, "chunksActivos");
        int caras = 0, aguaSobra = 0, aguaFalta = 0, opacaSobra = 0, opacaFalta = 0;
        int chunksConRayas = 0, chunksConHuecos = 0, paredesBordeMundo = 0;
        for (Chunk c : activos.values()) {
            float[][] subida = subidas.get(c);
            if (subida == null) continue;
            float[] opaca = ChunkMeshBuilder.buildOpaqueMesh(mundo, c.getBlocks(), c.getChunkX(), c.getChunkZ());
            float[] agua = ChunkMeshBuilder.buildTransparentMesh(mundo, c.getBlocks(), c.getChunkX(), c.getChunkZ());
            caras += (opaca.length + agua.length) / FLOATS_CARA;

            int[] difAgua = diferencia(subida[1], agua);
            int[] difOpaca = diferencia(subida[0], opaca);
            aguaSobra += difAgua[0];
            aguaFalta += difAgua[1];
            opacaSobra += difOpaca[0];
            opacaFalta += difOpaca[1];
            if (difAgua[0] > 0) chunksConRayas++;
            if (difOpaca[1] > 0) chunksConHuecos++;
            paredesBordeMundo += paredesContraNoCargado(mundo, c, agua);
        }
        System.out.printf(Locale.ROOT, "%s: %d chunks, %d caras en total%n", titulo, activos.size(), caras);
        System.out.printf(Locale.ROOT, "   Agua: sobran %d caras (paredes contra un chunk cargado) en %d chunks, faltan %d%n",
                aguaSobra, chunksConRayas, aguaFalta);
        System.out.printf(Locale.ROOT, "   Opacas: faltan %d caras (huecos) en %d chunks, sobran %d%n", opacaFalta, chunksConHuecos, opacaSobra);
        System.out.printf(Locale.ROOT, "   Paredes de agua contra el borde del mundo (chunks sin cargar): %d%n%n", paredesBordeMundo);
    }

    // {caras que están en "subida" y no en "correcta", caras que están en "correcta" y no en "subida"}
    private static int[] diferencia(float[] subida, float[] correcta) {
        Map<Cara, Integer> cuenta = new HashMap<>();
        for (int i = 0; i < correcta.length; i += FLOATS_CARA) cuenta.merge(new Cara(correcta, i), 1, Integer::sum);
        int sobran = 0;
        for (int i = 0; i < subida.length; i += FLOATS_CARA) {
            Cara cara = new Cara(subida, i);
            Integer n = cuenta.get(cara);
            if (n == null) sobran++;
            else if (n == 1) cuenta.remove(cara);
            else cuenta.put(cara, n - 1);
        }
        int faltan = 0;
        for (int n : cuenta.values()) faltan += n;
        return new int[] { sobran, faltan };
    }

    // Caras verticales de agua justo en el borde del chunk que dan a un chunk que no está cargado
    private static int paredesContraNoCargado(World mundo, Chunk c, float[] v) {
        // La malla dibuja cada bloque de x - 0.5 a x + 0.5: el borde oeste está en startX - 0.5
        float oeste = c.getChunkX() * SIZE - 0.5f, este = oeste + SIZE;
        float norte = c.getChunkZ() * SIZE - 0.5f, sur = norte + SIZE;
        int n = 0;
        for (int i = 0; i < v.length; i += FLOATS_CARA) {
            boolean xFija = true, zFija = true;
            for (int k = 1; k < 6; k++) {
                if (v[i + k * 5] != v[i]) xFija = false;
                if (v[i + k * 5 + 2] != v[i + 2]) zFija = false;
            }
            int dx = 0, dz = 0;
            if (xFija && v[i] == oeste) dx = -1;
            else if (xFija && v[i] == este) dx = 1;
            else if (zFija && v[i + 2] == norte) dz = -1;
            else if (zFija && v[i + 2] == sur) dz = 1;
            else continue;
            if (mundo.getChunk(c.getChunkX() + dx, c.getChunkZ() + dz) == null) n++;
        }
        return n;
    }

    private static Object campo(Object objeto, String nombre) throws Exception {
        Field f = objeto.getClass().getDeclaredField(nombre);
        f.setAccessible(true);
        return f.get(objeto);
    }

    // Una cara: sus 30 floats (posición y textura de los 6 vértices)
    private static final class Cara {
        private final float[] datos;
        private final int hash;

        Cara(float[] v, int inicio) {
            datos = Arrays.copyOfRange(v, inicio, inicio + FLOATS_CARA);
            hash = Arrays.hashCode(datos);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Cara otra && Arrays.equals(datos, otra.datos);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
