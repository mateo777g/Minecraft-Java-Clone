package com.minejava.world.gen;

import java.util.Random;

// Ruido Perlin 2D. Cada mundo tiene el suyo: la semilla decide cómo se mezcla la tabla de permutación,
// así la misma semilla siempre da el mismo ruido (y el mismo terreno) y otra semilla da otro.
public class PerlinNoise {
    // Cuántas ondas se suman en getNoise(): una grande y otras más chicas para el detalle
    private static final int OCTAVAS = 3;

    private final int[] p = new int[512];
    // Cuánto se corre cada octava, sacado de la semilla. El ruido Perlin vale 0 en los puntos enteros de su
    // cuadrícula, así que sin esto getNoise(0, 0) daba 0.5 con cualquier semilla y la columna (0, 0) era
    // igual en todos los mundos: siempre el centro de un río. Tiene decimales para no caer en otro punto
    // entero, y es distinto en cada octava para que tampoco coincidan entre ellas.
    private final float[] desplazamientoX = new float[OCTAVAS];
    private final float[] desplazamientoZ = new float[OCTAVAS];

    public PerlinNoise(long semilla) {
        Random rand = new Random(semilla);
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        // Mezclamos la tabla usando Fisher-Yates
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }
        // Duplicamos la tabla para evitar desbordamientos de índice
        for (int i = 0; i < 256; i++) {
            p[256 + i] = p[i];
        }
        // Después de mezclar, así la tabla sale igual que antes para la misma semilla.
        // Entre 0 y 256 alcanza: el ruido se repite cada 256 (por el "& 255" de noise()).
        for (int i = 0; i < OCTAVAS; i++) {
            desplazamientoX[i] = rand.nextFloat() * 256;
            desplazamientoZ[i] = rand.nextFloat() * 256;
        }
    }

    // El método principal que llamaremos desde el Chunk
    public float getNoise(float x, float z) {
        float total = 0;
        float frequency = 0.05f; // Ajusta esto si quieres montañas más pegadas o separadas
        float amplitude = 1.0f;
        float maxValue = 0;  
        
        // 3 Octavas: combina ondas grandes con detalles pequeños para más realismo
        for (int i = 0; i < OCTAVAS; i++) {
            total += noise(x * frequency + desplazamientoX[i], z * frequency + desplazamientoZ[i]) * amplitude;
            maxValue += amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        
        return (total / maxValue + 1.0f) / 2.0f; // Normaliza el resultado entre 0.0 y 1.0
    }

    private float noise(float x, float y) {
        int X = (int)Math.floor(x) & 255;
        int Y = (int)Math.floor(y) & 255;
        x -= Math.floor(x);
        y -= Math.floor(y);
        float u = fade(x);
        float v = fade(y);
        int A = p[X]+Y, B = p[X+1]+Y;
        return lerp(v, lerp(u, grad(p[A], x, y), grad(p[B], x-1, y)),
                       lerp(u, grad(p[A+1], x, y-1), grad(p[B+1], x-1, y-1)));
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static float lerp(float t, float a, float b) { return a + t * (b - a); }
    private static float grad(int hash, float x, float y) {
        int h = hash & 7;
        float u = h < 4 ? x : y;
        float v = h < 4 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}