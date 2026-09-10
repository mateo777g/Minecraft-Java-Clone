package core;

public enum Biome {
    PLAINS(3, 2, true),    // ID Pasto, ID Tierra, Árboles Sí
    DESERT(14, 14, false), // ID Arena, ID Arena, Árboles No
    OCEAN(14, 14, false);  // ID Arena, ID Arena, Árboles No (Fondo del mar)

    public final int surfaceBlock;
    public final int fillerBlock;
    public final boolean canSpawnTrees;

    Biome(int surfaceBlock, int fillerBlock, boolean canSpawnTrees) {
        this.surfaceBlock = surfaceBlock;
        this.fillerBlock = fillerBlock;
        this.canSpawnTrees = canSpawnTrees;
    }
}