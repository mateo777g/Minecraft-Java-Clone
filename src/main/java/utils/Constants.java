package utils;

import org.joml.Vector3f;

public class Constants {
    // Configuraciones de pantalla
    public static final int SCREEN_WIDTH = 1280;
    public static final int SCREEN_HEIGHT = 720;
    public static final String WINDOW_TITLE = "Minecraft Java Clone";

    // Configuraciones del juego
    public static final float MOUSE_SENSITIVITY = 0.15f;
    public static final Vector3f PLAYER_START_POSITION = new Vector3f(0.0f, 5.0f, 0.0f);
    public static final int ALTURA_MINIMA = -5;
    public static final int CHUNK_SIZE = 16;

    // Hotbar (Usando nuestro nuevo Enum de bloques)
    public static final BlockType[] BLOQUES_HOTBAR = {
        BlockType.PASTO, 
        BlockType.TIERRA, 
        BlockType.PIEDRA, 
        BlockType.ROCA, 
        BlockType.TRONCO, 
        BlockType.MADERA, 
        BlockType.ROCA_MADRE
    };
}