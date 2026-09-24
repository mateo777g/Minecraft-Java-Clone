package com.minejava.world.gen;

import com.minejava.world.Block;

public enum Biome {
    PLAINS(Block.GRASS, Block.DIRT, true),
    DESERT(Block.SAND, Block.SAND, false),
    OCEAN(Block.SAND, Block.SAND, false);  // Fondo del mar

    public final int surfaceBlock;
    public final int fillerBlock;
    public final boolean canSpawnTrees;

    Biome(int surfaceBlock, int fillerBlock, boolean canSpawnTrees) {
        this.surfaceBlock = surfaceBlock;
        this.fillerBlock = fillerBlock;
        this.canSpawnTrees = canSpawnTrees;
    }
}