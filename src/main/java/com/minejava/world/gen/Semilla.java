package com.minejava.world.gen;

import java.util.Random;

// Convierte lo que se escribe en el campo "Semilla" de la pantalla Crear mundo en la semilla del mundo
public class Semilla {

    // Como en Minecraft:
    //  - vacío (o solo espacios) → una semilla al azar
    //  - un número entero (también negativo) → ese mismo número
    //  - cualquier otro texto → su hashCode(), así "hola" siempre da el mismo mundo.
    //    Un número que no cabe en un long cuenta como texto.
    public static long desdeTexto(String texto) {
        String limpio = texto.trim();
        if (limpio.isEmpty()) return new Random().nextLong();
        try {
            return Long.parseLong(limpio);
        } catch (NumberFormatException e) {
            return limpio.hashCode();
        }
    }
}
