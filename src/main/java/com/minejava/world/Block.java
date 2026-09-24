package com.minejava.world;

// IDs de todos los bloques del juego.
// Del 0 al 14 cada ID es también la casilla del terrain_atlas.png (4x4),
// contando de izquierda a derecha y de arriba hacia abajo.
public final class Block {

    public static final int AIR = -1;

    public static final int STONE = 0;
    public static final int BEDROCK = 1;
    public static final int DIRT = 2;
    public static final int GRASS = 3;
    public static final int WOOD = 4;        // Tronco
    public static final int LEAVES = 5;
    public static final int PLANKS = 6;      // En el atlas, todavía no se genera
    public static final int BRICKS = 7;      // En el atlas, todavía no se genera
    public static final int CACTUS = 8;
    public static final int COBBLESTONE = 9; // En el atlas, todavía no se genera
    public static final int OBSIDIAN = 10;   // En el atlas, todavía no se genera
    public static final int IRON_ORE = 11;
    public static final int COAL_ORE = 12;
    public static final int DEEPSLATE = 13;
    public static final int SAND = 14;

    // Sin textura: el fragment shader los pinta con un color fijo
    public static final int WATER = 15;
    public static final int CLOUD = 16;

    private Block() {}

    // Sólido = choca con el jugador y se puede apuntar para romper o poner bloques
    public static boolean isSolid(int id) {
        return id != AIR && id != WATER && id != CLOUD;
    }
}
