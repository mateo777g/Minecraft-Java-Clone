package com.minejava.config;

import org.joml.Vector3f;

import com.minejava.world.Block;

public class Constants {
    // Configuraciones de pantalla
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;
    public static final String WINDOW_TITLE = "Minecraft Java Clone";

    // Configuraciones del juego
    public static final float MOUSE_SENSITIVITY = 0.15f;
    public static final Vector3f PLAYER_START_POSITION = new Vector3f(0.0f, 5.0f, 0.0f);

    // Hotbar: un ID de bloque por casilla (teclas 1 a 9 como máximo)
    public static final int[] BLOQUES_HOTBAR = {
        Block.STONE,
        Block.BEDROCK,
        Block.DIRT,
        Block.GRASS,
        Block.WOOD,
        Block.LEAVES
    };
}
