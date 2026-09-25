import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

import com.minejava.render.ChunkMeshBuilder;
import com.minejava.world.Block;
import com.minejava.world.Chunk;
import com.minejava.world.World;

// Revisa sin pantalla las mallas de los bordes de chunk (las rayas del agua, fase 1 de docs/PLAN_AGUA_JUGADOR.md).
// Carga el mundo como el juego: busca el spawn, llama a World.actualizarMundo() y deja que los hilos del World
// generen los terrenos y armen las mallas. Hace lo mismo que procesarMallasPendientes() (sacarMallaParaSubir()),
// pero guarda cada malla aquí en vez de mandarla a la GPU, y "libera" las de los chunks que se alejan, como el
// juego. Revisa al empezar, después de avanzar hacia +x de a un chunk y después de romper un bloque y poner otro
// en el borde de un chunk. Cada vez compara la malla subida de cada chunk con la misma malla armada otra vez con
// todos los vecinos ya generados, que es la correcta:
//  - caras de agua que sobran: paredes de agua contra un chunk que sí está cargado (las rayas);
//  - caras opacas que faltan (huecos en los bordes) y que sobran (paredes escondidas en el terreno);
//  - paredes de agua contra el borde del mundo (un chunk sin cargar): la pared del horizonte;
//  - chunks a renderDistance o menos sin malla o con un vecino sin terreno, y chunks del anillo de afuera sin
//    terreno o con malla.
// Tiene que dar 0 en todo; si no, termina con error.
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
    // El chunk del jugador, calculado como World.actualizarMundo()
    private static int centroX, centroZ;
    // Todo lo que salió mal: si al final no es 0, la herramienta termina con error
    private static long errores = 0;
    // Cuándo salió la malla del chunk del spawn: lo que dura "Generando mundo..." (sin contar los frames)
    private static long inicioCarga, nanosMallaSpawn;
    private static Chunk chunkSpawn;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        long semilla = args.length > 0 ? Long.parseLong(args[0]) : 12345;
        int pasos = args.length > 1 ? Integer.parseInt(args[1]) : 5;

        World mundo = new World(RENDER_DISTANCE, semilla);
        int[] spawn = mundo.getGenerador().buscarSpawn();
        float x = spawn[0], z = spawn[1];
        System.out.printf(Locale.ROOT, "RevisarBordes: semilla %d, spawn (%d, %d), %d hilos generadores%n%n",
                semilla, spawn[0], spawn[1], Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

        inicioCarga = System.nanoTime();
        mover(mundo, x, z);
        chunkSpawn = mundo.getChunk(centroX, centroZ);
        esperar(mundo);
        System.out.printf(Locale.ROOT, "La malla del chunk del spawn salió a los %.0f ms y todo el mundo a los %.0f ms%n%n",
                nanosMallaSpawn / 1e6, (System.nanoTime() - inicioCarga) / 1e6);
        revisar(mundo, "Carga inicial");

        for (int i = 0; i < pasos; i++) {
            x += SIZE;
            mover(mundo, x, z);
            esperar(mundo);
        }
        revisar(mundo, "Después de avanzar " + pasos + " chunks hacia +x");

        romperYPonerEnBordes(mundo);
        revisar(mundo, "Después de romper y poner en los bordes");

        mundo.cleanup();
        if (errores == 0) {
            System.out.println("Todo bien: ninguna cara de más ni de menos, en el agua ni en los bloques opacos.");
        } else {
            System.out.printf(Locale.ROOT, "¡HAY %d PROBLEMAS! (ver arriba)%n", errores);
            System.exit(1);
        }
    }

    // Como Partida al cambiar de chunk. Las mallas de los chunks que quedaron más lejos que la distancia de render
    // se "liberan", como hace World: si alguno vuelve a quedar cerca sin que se le pida otra, se nota como "sin malla".
    private static void mover(World mundo, float x, float z) {
        centroX = Math.floorDiv(Math.round(x), SIZE);
        centroZ = Math.floorDiv(Math.round(z), SIZE);
        mundo.actualizarMundo(x, z);
        subidas.keySet().removeIf(c -> distancia(c.getChunkX(), c.getChunkZ()) > RENDER_DISTANCE);
    }

    // "Sube" las mallas en el orden en que salen, como procesarMallasPendientes() (una más nueva del mismo chunk
    // reemplaza a la anterior), hasta que los hilos del World no tienen nada más que hacer
    private static void esperar(World mundo) throws InterruptedException {
        while (true) {
            Chunk.MallaArmada malla = mundo.sacarMallaParaSubir();
            if (malla != null) {
                subidas.put(malla.chunk(), new float[][] { malla.opaca(), malla.transparente() });
                if (malla.chunk() == chunkSpawn && nanosMallaSpawn == 0) nanosMallaSpawn = System.nanoTime() - inicioCarga;
            } else if (mundo.estaTrabajando()) {
                Thread.sleep(2);
            } else {
                return;
            }
        }
    }

    private static void revisar(World mundo, String titulo) {
        int chunks = 0, caras = 0, aguaSobra = 0, aguaFalta = 0, opacaSobra = 0, opacaFalta = 0;
        int chunksConRayas = 0, chunksConHuecos = 0, paredesBordeMundo = 0, sinMalla = 0, vecinosSinTerreno = 0;
        int anillo = 0, anilloSinTerreno = 0, anilloConMalla = 0;
        for (int cx = centroX - RENDER_DISTANCE - 1; cx <= centroX + RENDER_DISTANCE + 1; cx++) {
            for (int cz = centroZ - RENDER_DISTANCE - 1; cz <= centroZ + RENDER_DISTANCE + 1; cz++) {
                Chunk c = mundo.getChunk(cx, cz);
                if (distancia(cx, cz) > RENDER_DISTANCE) {
                    // El anillo de afuera: solo terreno, para ser vecino
                    anillo++;
                    if (c == null || !c.estaGenerado()) anilloSinTerreno++;
                    if (c != null && subidas.containsKey(c)) anilloConMalla++;
                    continue;
                }
                chunks++;
                if (!tieneTerreno(mundo, cx - 1, cz) || !tieneTerreno(mundo, cx + 1, cz)
                        || !tieneTerreno(mundo, cx, cz - 1) || !tieneTerreno(mundo, cx, cz + 1)) {
                    vecinosSinTerreno++;
                }
                float[][] subida = c == null ? null : subidas.get(c);
                if (subida == null) {
                    sinMalla++;
                    continue;
                }
                float[] opaca = ChunkMeshBuilder.buildOpaqueMesh(mundo, c.getBlocks(), cx, cz);
                float[] agua = ChunkMeshBuilder.buildTransparentMesh(mundo, c.getBlocks(), cx, cz);
                caras += (opaca.length + agua.length) / FLOATS_CARA;

                int[] difAgua = diferencia(subida[1], agua);
                int[] difOpaca = diferencia(subida[0], opaca);
                aguaSobra += difAgua[0];
                aguaFalta += difAgua[1];
                opacaSobra += difOpaca[0];
                opacaFalta += difOpaca[1];
                if (difAgua[0] > 0) chunksConRayas++;
                if (difOpaca[1] > 0) chunksConHuecos++;
                // Con la malla subida: lo que se ve en la pantalla
                paredesBordeMundo += paredesContraNoCargado(mundo, c, subida[1]);
            }
        }
        System.out.printf(Locale.ROOT, "%s: %d chunks con malla, %d caras en total; %d en el anillo de afuera%n",
                titulo, chunks - sinMalla, caras, anillo);
        System.out.printf(Locale.ROOT, "   Agua: sobran %d caras (paredes contra un chunk cargado) en %d chunks, faltan %d%n",
                aguaSobra, chunksConRayas, aguaFalta);
        System.out.printf(Locale.ROOT, "   Opacas: faltan %d caras (huecos) en %d chunks, sobran %d%n", opacaFalta, chunksConHuecos, opacaSobra);
        System.out.printf(Locale.ROOT, "   Paredes de agua contra el borde del mundo (chunks sin cargar): %d%n", paredesBordeMundo);
        System.out.printf(Locale.ROOT, "   Chunks cercanos sin malla: %d, con un vecino sin terreno: %d. Anillo de afuera: sin terreno %d, con malla %d%n%n",
                sinMalla, vecinosSinTerreno, anilloSinTerreno, anilloConMalla);
        errores += aguaSobra + aguaFalta + opacaSobra + opacaFalta + paredesBordeMundo + sinMalla + vecinosSinTerreno
                + anilloSinTerreno + anilloConMalla;
    }

    // Rompe un bloque en el borde este del chunk del jugador, pegado a un bloque del vecino este (que tiene que
    // dibujar la cara que quedó al aire), y pone uno en el borde oeste, pegado a un bloque del vecino oeste (que
    // tiene que esconder la cara que quedó tapada). Como el juego, con World.setBlockGlobal().
    private static void romperYPonerEnBordes(World mundo) throws InterruptedException {
        int inicioX = centroX * SIZE, inicioZ = centroZ * SIZE;
        Chunk este = mundo.getChunk(centroX + 1, centroZ), oeste = mundo.getChunk(centroX - 1, centroZ);
        int[] romper = buscar(mundo, inicioX + SIZE - 1, inicioX + SIZE, inicioZ, true);
        int[] poner = buscar(mundo, inicioX, inicioX - 1, inicioZ, false);
        int carasEste = caras(subidas.get(este)), carasOeste = caras(subidas.get(oeste));

        mundo.setBlockGlobal(romper[0], romper[1], romper[2], Block.AIR);
        mundo.setBlockGlobal(poner[0], poner[1], poner[2], Block.PLANKS);
        esperar(mundo);

        int nuevasEste = caras(subidas.get(este)), nuevasOeste = caras(subidas.get(oeste));
        System.out.printf(Locale.ROOT, "Rompí (%d, %d, %d), en el borde este del chunk (%d, %d): la malla del vecino este pasó de %d a %d caras%n",
                romper[0], romper[1], romper[2], centroX, centroZ, carasEste, nuevasEste);
        System.out.printf(Locale.ROOT, "Puse (%d, %d, %d), en el borde oeste: la malla del vecino oeste pasó de %d a %d caras%n",
                poner[0], poner[1], poner[2], carasOeste, nuevasOeste);
        // Una cara más en el este (la que quedó al aire) y una menos en el oeste (la que quedó tapada)
        if (nuevasEste != carasEste + 1) {
            System.out.println("   ¡La malla del vecino este no se volvió a armar bien!");
            errores++;
        }
        if (nuevasOeste != carasOeste - 1) {
            System.out.println("   ¡La malla del vecino oeste no se volvió a armar bien!");
            errores++;
        }
        System.out.println();
    }

    // El bloque más alto de la columna x (para algún z del chunk, sin las esquinas) que es sólido (romper) o aire
    // (poner), con el bloque del vecino en xVecino, a la misma altura, sólido
    private static int[] buscar(World mundo, int x, int xVecino, int inicioZ, boolean romper) {
        for (int z = inicioZ + 1; z < inicioZ + SIZE - 1; z++) {
            for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                int bloque = mundo.getBlockGlobal(x, y, z);
                boolean sirve = romper ? Block.isSolid(bloque) : bloque == Block.AIR;
                if (sirve && Block.isSolid(mundo.getBlockGlobal(xVecino, y, z))) return new int[] { x, y, z };
            }
        }
        throw new IllegalStateException("No encontré dónde " + (romper ? "romper" : "poner") + " en x = " + x);
    }

    private static int caras(float[][] malla) {
        return (malla[0].length + malla[1].length) / FLOATS_CARA;
    }

    private static boolean tieneTerreno(World mundo, int cx, int cz) {
        Chunk c = mundo.getChunk(cx, cz);
        return c != null && c.estaGenerado();
    }

    private static int distancia(int cx, int cz) {
        return Math.max(Math.abs(cx - centroX), Math.abs(cz - centroZ));
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
        // Las caras verticales están en los límites entre bloques: hoy de -0.5 a 47.5 desde el inicio del chunk
        // (la malla dibuja cada bloque de x - 0.5 a x + 0.5) y, con la fase 2, de 0 a 48. Las dos formas
        // entran en estos cortes: el borde oeste queda por debajo de 0.25 y el este por encima de 47.25.
        float inicioX = c.getChunkX() * SIZE, inicioZ = c.getChunkZ() * SIZE;
        int n = 0;
        for (int i = 0; i < v.length; i += FLOATS_CARA) {
            boolean xFija = true, zFija = true;
            for (int k = 1; k < 6; k++) {
                if (v[i + k * 5] != v[i]) xFija = false;
                if (v[i + k * 5 + 2] != v[i + 2]) zFija = false;
            }
            float relX = v[i] - inicioX, relZ = v[i + 2] - inicioZ;
            int dx = 0, dz = 0;
            if (xFija && relX < 0.25f) dx = -1;
            else if (xFija && relX > SIZE - 0.75f) dx = 1;
            else if (zFija && relZ < 0.25f) dz = -1;
            else if (zFija && relZ > SIZE - 0.75f) dz = 1;
            else continue;
            if (mundo.getChunk(c.getChunkX() + dx, c.getChunkZ() + dz) == null) n++;
        }
        return n;
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
