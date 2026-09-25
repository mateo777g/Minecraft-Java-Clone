import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;

import org.joml.Vector3f;

import com.minejava.render.ChunkMeshBuilder;
import com.minejava.world.Block;
import com.minejava.world.Chunk;
import com.minejava.world.World;

// Revisa sin pantalla si lo que el juego hace con el jugador coincide con lo que se ve (fase 2 de
// docs/PLAN_AGUA_JUGADOR.md):
//  1. A qué altura quedan los ojos sobre el suelo que se ve, al aparecer y bajando con Shift hasta chocar.
//  2. Rayos al azar desde los ojos: si el bloque que rompería el juego es el que está bajo la mira en la
//     pantalla, y si el que pondría va pegado a la cara que se ve.
// "Lo que se ve" sale de la malla: la herramienta mide dónde dibuja ChunkMeshBuilder cada bloque (hoy, el
// bloque (x, y, z) va de x - 0.5 a x + 0.5) y recorre esa rejilla de forma exacta. "Lo que hace el juego" es
// una copia de World.interactuarConTerreno() y de Partida.comenzar(): si la fase 2 los cambia, hay que
// cambiar también las copias de aquí (o llamar a los métodos nuevos).
//
// Usa las clases del juego, así que primero hay que compilarlo. Desde la carpeta del proyecto:
//     mvn -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
//     java -cp "target/classes:$(cat target/classpath.txt)" herramientas/RevisarJugador.java [semilla]
public class RevisarJugador {

    private static final int RAYOS = 20000;
    private static final float ALCANCE = 5.0f;   // como World
    private static final float OJOS = 1.62f;     // PlayerController.cameraHeight

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        long semilla = args.length > 0 ? Long.parseLong(args[0]) : 12345;

        // El chunk del spawn y sus 8 vecinos, con su terreno
        World mundo = new World(4, semilla);
        int[] spawn = mundo.getGenerador().buscarSpawn();
        int sx = spawn[0], sz = spawn[1];
        int ccx = Math.floorDiv(sx, Chunk.CHUNK_SIZE), ccz = Math.floorDiv(sz, Chunk.CHUNK_SIZE);
        for (int cx = ccx - 1; cx <= ccx + 1; cx++) {
            for (int cz = ccz - 1; cz <= ccz + 1; cz++) {
                Chunk chunk = new Chunk(mundo, cx, cz);
                mundo.getGenerador().generateTerrain(chunk.getBlocks(), cx, cz);
                mundo.agregarChunk(chunk);
            }
        }

        // Dónde dibuja la malla: el vértice más al oeste de un chunk es el borde oeste de sus bloques x = 0
        float desfase = desfaseMalla(mundo, ccx, ccz);
        System.out.printf(Locale.ROOT, "RevisarJugador: semilla %d, spawn (%d, %d). La malla dibuja el bloque (x, y, z) de x %+.1f a x %+.1f%n%n",
                semilla, sx, sz, desfase, desfase + 1);

        // 1. Altura de los ojos
        float alturaSuperficie = mundo.getAlturaSuperficie(sx, sz); // y del bloque de más arriba + 1
        float sueloVisto = alturaSuperficie + desfase;               // donde se dibuja su cara de arriba
        float piesAlAparecer = alturaSuperficie + 1.0f;              // como Partida.comenzar()
        float piesEnElSuelo = alturaSuperficie;                      // bajando con Shift hasta chocar
        System.out.printf(Locale.ROOT, "1) Ojos sobre el suelo que se ve (en Minecraft, 1.62):%n");
        System.out.printf(Locale.ROOT, "   al aparecer: %.2f%n", piesAlAparecer + OJOS - sueloVisto);
        System.out.printf(Locale.ROOT, "   bajando con Shift hasta chocar: %.2f%n%n", piesEnElSuelo + OJOS - sueloVisto);

        // 2. Rayos al azar, parado en el suelo cerca del spawn y mirando hacia abajo
        Random azar = new Random(1);
        int apuntados = 0, rompeOtro = 0, noRompe = 0, poneOtroLugar = 0, poneDiagonal = 0;
        for (int i = 0; i < RAYOS; i++) {
            float ox = sx + 0.5f + (azar.nextFloat() - 0.5f) * 6;
            float oz = sz + 0.5f + (azar.nextFloat() - 0.5f) * 6;
            float oy = mundo.getAlturaSuperficie((int) Math.floor(ox), (int) Math.floor(oz)) + OJOS;
            Vector3f origen = new Vector3f(ox, oy, oz);
            Vector3f direccion = direccion(azar.nextFloat() * 360, 5 + azar.nextFloat() * 80);

            int[] visto = rayoVisto(mundo, origen, direccion, desfase);
            if (visto == null) continue;
            apuntados++;
            int[][] juego = rayoJuego(mundo, origen, direccion);
            if (juego == null) {
                noRompe++;
                continue;
            }
            if (!Arrays.equals(Arrays.copyOf(visto, 3), juego[0])) rompeOtro++;
            int[] ponerBien = { visto[0] + visto[3], visto[1] + visto[4], visto[2] + visto[5] };
            if (!Arrays.equals(ponerBien, juego[1])) poneOtroLugar++;
            int distancia = Math.abs(juego[1][0] - juego[0][0]) + Math.abs(juego[1][1] - juego[0][1]) + Math.abs(juego[1][2] - juego[0][2]);
            if (distancia != 1) poneDiagonal++;
        }
        System.out.printf(Locale.ROOT, "2) %d rayos que apuntan a un bloque que se ve a menos de %.0f bloques:%n", apuntados, ALCANCE);
        System.out.printf(Locale.ROOT, "   rompe otro bloque:                 %5.1f %%%n", 100.0 * rompeOtro / apuntados);
        System.out.printf(Locale.ROOT, "   no rompe nada:                     %5.1f %%%n", 100.0 * noRompe / apuntados);
        System.out.printf(Locale.ROOT, "   pone el bloque en otro lugar:      %5.1f %%%n", 100.0 * poneOtroLugar / apuntados);
        System.out.printf(Locale.ROOT, "   lo pone en diagonal (no en una cara del que rompería): %.1f %%%n", 100.0 * poneDiagonal / apuntados);
        mundo.cleanup();
    }

    // Cuánto corre la malla a cada bloque respecto de [x, x + 1]: hoy -0.5
    private static float desfaseMalla(World mundo, int cx, int cz) {
        float[] v = ChunkMeshBuilder.buildOpaqueMesh(mundo, mundo.getChunk(cx, cz).getBlocks(), cx, cz);
        float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        for (int i = 0; i < v.length; i += 5) {
            minX = Math.min(minX, v[i]);
            minZ = Math.min(minZ, v[i + 2]);
        }
        float dx = minX - cx * Chunk.CHUNK_SIZE, dz = minZ - cz * Chunk.CHUNK_SIZE;
        if (dx != dz) throw new IllegalStateException("La malla está corrida distinto en x (" + dx + ") y en z (" + dz + ")");
        return dx;
    }

    // Como Camera.getDirection()
    private static Vector3f direccion(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vector3f((float) (Math.sin(y) * Math.cos(p)), (float) -Math.sin(p), (float) (-Math.cos(y) * Math.cos(p))).normalize();
    }

    // Copia de World.interactuarConTerreno(): {bloque que rompe, dónde pone}, o null si no llega a ninguno
    private static int[][] rayoJuego(World mundo, Vector3f origen, Vector3f direccion) {
        Vector3f actual = new Vector3f(origen), ultimaVacia = new Vector3f(origen);
        for (float d = 0; d < ALCANCE; d += 0.03f) {
            actual.set(direccion).mul(d).add(origen);
            int bx = (int) Math.floor(actual.x), by = (int) Math.floor(actual.y), bz = (int) Math.floor(actual.z);
            if (Block.isSolid(mundo.getBlockGlobal(bx, by, bz))) {
                int[] poner = { (int) Math.floor(ultimaVacia.x), (int) Math.floor(ultimaVacia.y), (int) Math.floor(ultimaVacia.z) };
                return new int[][] { { bx, by, bz }, poner };
            }
            ultimaVacia.set(actual);
        }
        return null;
    }

    // El bloque sólido que se ve bajo la mira, recorriendo exacto la rejilla que dibuja la malla (cada bloque
    // corrido "desfase"). Devuelve {x, y, z, nx, ny, nz}, con la normal de la cara por la que entra el rayo,
    // o null si no hay ninguno a menos de ALCANCE.
    private static int[] rayoVisto(World mundo, Vector3f origen, Vector3f d, float desfase) {
        double px = origen.x - desfase, py = origen.y - desfase, pz = origen.z - desfase;
        int x = (int) Math.floor(px), y = (int) Math.floor(py), z = (int) Math.floor(pz);
        int pasoX = d.x > 0 ? 1 : -1, pasoY = d.y > 0 ? 1 : -1, pasoZ = d.z > 0 ? 1 : -1;
        double deltaX = Math.abs(1 / d.x), deltaY = Math.abs(1 / d.y), deltaZ = Math.abs(1 / d.z);
        double tX = (d.x > 0 ? x + 1 - px : px - x) * deltaX;
        double tY = (d.y > 0 ? y + 1 - py : py - y) * deltaY;
        double tZ = (d.z > 0 ? z + 1 - pz : pz - z) * deltaZ;
        int nx = 0, ny = 0, nz = 0;
        double t = 0;
        while (t <= ALCANCE) {
            if (Block.isSolid(mundo.getBlockGlobal(x, y, z))) return new int[] { x, y, z, nx, ny, nz };
            if (tX < tY && tX < tZ) {
                x += pasoX; t = tX; tX += deltaX; nx = -pasoX; ny = 0; nz = 0;
            } else if (tY < tZ) {
                y += pasoY; t = tY; tY += deltaY; nx = 0; ny = -pasoY; nz = 0;
            } else {
                z += pasoZ; t = tZ; tZ += deltaZ; nx = 0; ny = 0; nz = -pasoZ;
            }
        }
        return null;
    }
}
