package com.minejava.world.gen;

import java.util.Random;

public class PerlinNoise {
    private static final int[] p = new int[512];

    static {
        // Inicialización determinista basada en una semilla fija (Seed: 12345)
        Random rand = new Random(12345);
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
    }

    // El método principal que llamaremos desde el Chunk
    public static float getNoise(float x, float z) {
        float total = 0;
        float frequency = 0.05f; // Ajusta esto si quieres montañas más pegadas o separadas
        float amplitude = 1.0f;
        float maxValue = 0;  
        
        // 3 Octavas: combina ondas grandes con detalles pequeños para más realismo
        for (int i = 0; i < 3; i++) {
            total += noise(x * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= 0.5f;
            frequency *= 2.0f;
        }
        
        return (total / maxValue + 1.0f) / 2.0f; // Normaliza el resultado entre 0.0 y 1.0
    }

    private static float noise(float x, float y) {
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