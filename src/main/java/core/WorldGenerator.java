package core;

public class WorldGenerator {

    // === IDs de Bloques ===
    private static final int STONE = 0;
    private static final int BEDROCK = 1;
    private static final int DIRT = 2;
    private static final int GRASS = 3;
    private static final int WOOD = 4; 
    private static final int LEAVES = 5;
    // 6 
    // 7 
    private static final int CACTUS = 8; 
    // 9
    // 10
    private static final int IRON_ORE = 11;
    private static final int COAL_ORE = 12;
    private static final int DEEPSLATE = 13; 
    private static final int SAND = 14;
    private static final int WATER = 15; 
    private static final int CLOUD = 16;      

    public static void generateTerrain(int[][][] blocks, int chunkX, int chunkZ) {
        
        int nivelAgua = 68; 

        // =================================================================
        // PASO 1: GENERAR TERRENO 
        // =================================================================
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                
                float globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                float globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                
                Biome biome = BiomeProvider.getBiome(globalX, globalZ);
                float ruidoBiomaPuro = utils.PerlinNoise.getNoise((globalX + 8000f) * 0.012f, (globalZ + 8000f) * 0.012f);
                
                float distorsion = (utils.PerlinNoise.getNoise(globalX * 0.1f, globalZ * 0.1f) - 0.5f) * 0.1f;
                float ruidoBioma = ruidoBiomaPuro + distorsion; 
                
                float ruidoBase = utils.PerlinNoise.getNoise(globalX * 0.02f, globalZ * 0.02f); 
                int alturaOriginal = (int)(ruidoBase * 20) + 74; 

                float ruidoAgua = utils.PerlinNoise.getNoise(globalX * 0.16f, globalZ * 0.16f);
                float distanciaAlCanal = Math.abs(ruidoAgua - 0.5f); 

                int alturaBase = alturaOriginal;
                float umbralOrilla = 0.06f; 
                
                if (distanciaAlCanal < umbralOrilla) {
                    int alturaPlana = 72; 
                    float t = (umbralOrilla - distanciaAlCanal) / (umbralOrilla - 0.04f);
                    if (t > 1.0f) t = 1.0f; 
                    float curva = t * t * (3.0f - 2.0f * t); 
                    alturaBase = (int)(alturaOriginal * (1.0f - curva) + alturaPlana * curva);
                }
                
                boolean esCuerpoAgua = distanciaAlCanal < 0.04f; 
                int columnHeight = alturaBase;

                if (esCuerpoAgua) {
                    float factorPicada = (0.04f - distanciaAlCanal) / 0.04f; 
                    float ruidoRugoso = utils.PerlinNoise.getNoise(globalX * 0.3f, globalZ * 0.3f) * 2.0f;
                    columnHeight = alturaBase - (int)(factorPicada * 8) + (int)ruidoRugoso;
                }

                float ruidoOceanoProfundo = utils.PerlinNoise.getNoise(globalX * 0.04f, globalZ * 0.04f);
                int alturaOceano = 46 + (int)(ruidoOceanoProfundo * 14); 

                if (ruidoBioma < 0.52f) {
                    if (ruidoBioma <= 0.35f) {
                        columnHeight = alturaOceano; 
                    } else {
                        float tOcean = (ruidoBioma - 0.35f) / (0.52f - 0.35f); 
                        float curvaOcean = tOcean * tOcean * (3.0f - 2.0f * tOcean);
                        columnHeight = (int)(alturaOceano * (1.0f - curvaOcean) + columnHeight * curvaOcean);
                    }
                }

                boolean usarArena = false;
                if ((esCuerpoAgua && columnHeight <= nivelAgua + 1) || ruidoBioma < 0.48f) {
                    usarArena = true; 
                }

                for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                    if (y >= columnHeight) {
                        if (y <= nivelAgua) blocks[x][y][z] = WATER;
                        else blocks[x][y][z] = -1; 
                    } else if (y == 0) {
                        blocks[x][y][z] = BEDROCK;  
                    } else if (y <= 4 && Math.random() < (1.0f - (y * 0.2f))) {
                        blocks[x][y][z] = BEDROCK;  
                    } else if (y < columnHeight - 4) {
                        if (y < 38) {
                            blocks[x][y][z] = DEEPSLATE; 
                        } else if (y <= 43) {
                            float probabilidadPiedra = (y - 38) / 5.0f;
                            blocks[x][y][z] = (Math.random() < probabilidadPiedra) ? STONE : DEEPSLATE;
                        } else {
                            blocks[x][y][z] = STONE;   
                        }
                    } else if (y < columnHeight - 1) {
                        blocks[x][y][z] = usarArena ? SAND : biome.fillerBlock;
                    } else {
                        blocks[x][y][z] = usarArena ? SAND : biome.surfaceBlock;
                    }
                }
            }
        }

        // =================================================================
        // PASO 2: CUEVAS
        // =================================================================
        int areaChunk = Chunk.CHUNK_SIZE * Chunk.CHUNK_SIZE;
        int multiplicador = Math.max(1, areaChunk / 256); 
        int intentosCuevas = multiplicador * 2; 
        for(int i = 0; i < intentosCuevas; i++) {
            if (Math.random() < 0.85) generarSistemaCuevas(blocks);
        }

        // =================================================================
        // PASO 3: PARCHEO DE AGUA (RÍOS EN TIERRA)
        // =================================================================
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                
                float globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                float globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                
                Biome biome = BiomeProvider.getBiome(globalX, globalZ);
                float ruidoBiomaPuro = utils.PerlinNoise.getNoise((globalX + 8000f) * 0.012f, (globalZ + 8000f) * 0.012f);
                float distorsion = (utils.PerlinNoise.getNoise(globalX * 0.1f, globalZ * 0.1f) - 0.5f) * 0.1f;
                float ruidoBioma = ruidoBiomaPuro + distorsion; 

                float ruidoAgua = utils.PerlinNoise.getNoise(globalX * 0.16f, globalZ * 0.16f);
                float distanciaAlCanal = Math.abs(ruidoAgua - 0.5f); 
                
                if (distanciaAlCanal < 0.04f && ruidoBioma >= 0.50f) {
                    int alturaBase = 72; 
                    float factorPicada = (0.04f - distanciaAlCanal) / 0.04f; 
                    float ruidoRugoso = utils.PerlinNoise.getNoise(globalX * 0.3f, globalZ * 0.3f) * 2.0f;
                    int columnHeight = alturaBase - (int)(factorPicada * 8) + (int)ruidoRugoso;
                    boolean usarArena = (columnHeight <= nivelAgua + 1);
                    
                    for (int y = columnHeight - 6; y < columnHeight; y++) {
                        if (y > 4) { 
                            if (y == columnHeight - 1) blocks[x][y][z] = usarArena ? SAND : biome.surfaceBlock;
                            else blocks[x][y][z] = usarArena ? SAND : biome.fillerBlock;
                        }
                    }
                    for (int y = columnHeight; y <= nivelAgua; y++) blocks[x][y][z] = WATER; 
                }
            }
        }

        // =================================================================
        // PASO 4: MINERALES, ÁRBOLES Y CACTUS
        // =================================================================
        generarVetas(blocks, COAL_ORE, 40 * multiplicador, 80, 5, 10);
        generarVetas(blocks, IRON_ORE, 35 * multiplicador, 50, 4, 8);
        generarArboles(blocks, chunkX, chunkZ);
        generarCactus(blocks, chunkX, chunkZ);

        // =================================================================
        // PASO 5: NUBES
        // =================================================================
        int escalaNube = 12; 
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                int globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                int celdaX = (int) Math.floor((double) globalX / escalaNube);
                int celdaZ = (int) Math.floor((double) globalZ / escalaNube);
                double pseudoRuido = Math.sin(celdaX * 12.9898 + celdaZ * 78.233) * 43758.5453;
                pseudoRuido = pseudoRuido - Math.floor(pseudoRuido);
                float controlDensidad = utils.PerlinNoise.getNoise(celdaX * 0.05f, celdaZ * 0.05f);
                double umbralRequerido = 0.75 - (controlDensidad * 0.38);
                
                if (pseudoRuido > umbralRequerido && controlDensidad > -0.15f) {
                    for (int y = 150; y <= 151; y++) {
                        if (y < Chunk.CHUNK_HEIGHT && blocks[x][y][z] == -1) blocks[x][y][z] = CLOUD;
                    }
                }
            }
        }
    }

    // --- MÉTODOS AUXILIARES ---
    
    private static void generarCactus(int[][][] blocks, int chunkX, int chunkZ) {
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int z = 1; z < Chunk.CHUNK_SIZE - 1; z++) {
                float globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                float globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                Biome biome = BiomeProvider.getBiome(globalX, globalZ);

                if (biome != Biome.DESERT) continue;

                int topY = 0;
                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    int b = blocks[x][y][z];
                    if (b != -1 && b != CLOUD) {
                        topY = y;
                        break;
                    }
                }

                // REDUJE LA PROBABILIDAD DE 0.02f a 0.002f PARA QUE SE VEA COMO MINECRAFT
                if (topY > 0 && topY >= 69 && blocks[x][topY][z] == SAND && Math.random() < 0.002f) {
                    int alturaCactus = 1 + (int)(Math.random() * 3); 
                    boolean puedeCrecer = true;

                    for (int i = 1; i <= alturaCactus; i++) {
                        int checkY = topY + i;
                        if (checkY >= Chunk.CHUNK_HEIGHT) { puedeCrecer = false; break; }
                        
                        if (blocks[x][checkY][z] != -1) { puedeCrecer = false; break; }

                        if (blocks[x+1][checkY][z] != -1 && blocks[x+1][checkY][z] != CLOUD) puedeCrecer = false;
                        if (blocks[x-1][checkY][z] != -1 && blocks[x-1][checkY][z] != CLOUD) puedeCrecer = false;
                        if (blocks[x][checkY][z+1] != -1 && blocks[x][checkY][z+1] != CLOUD) puedeCrecer = false;
                        if (blocks[x][checkY][z-1] != -1 && blocks[x][checkY][z-1] != CLOUD) puedeCrecer = false;
                        
                        if (!puedeCrecer) break;
                    }

                    if (puedeCrecer) {
                        for (int i = 1; i <= alturaCactus; i++) {
                            blocks[x][topY + i][z] = CACTUS;
                        }
                    }
                }
            }
        }
    }

    private static void generarSistemaCuevas(int[][][] blocks) { 
        float craterX = (float)(Math.random() * Chunk.CHUNK_SIZE); 
        float craterY = 12 + (float)(Math.random() * 18); 
        float craterZ = (float)(Math.random() * Chunk.CHUNK_SIZE); 
        float radioCrater = 8.0f + (float)(Math.random() * 5.0f); 
        vaciarEsfera(blocks, craterX, craterY, craterZ, radioCrater); 
        vaciarEsfera(blocks, craterX + 3, craterY - 1, craterZ + 2, radioCrater * 0.8f); 
        
        int numGusanosAscendentes = 2 + (int)(Math.random() * 3); 
        for (int i = 0; i < numGusanosAscendentes; i++) { 
            float cx = craterX, cy = craterY, cz = craterZ; 
            float yaw = (float)(Math.random() * Math.PI * 2); 
            float pitch = 0.4f + (float)(Math.random() * 0.5f); 
            int longitud = 120 + (int)(Math.random() * 40); 
            float radioGusano = 2.0f + (float)Math.random() * 1.5f; 
            for (int paso = 0; paso < longitud; paso++) { 
                cx += Math.cos(yaw) * Math.cos(pitch); 
                cy += Math.sin(pitch); 
                cz += Math.sin(yaw) * Math.cos(pitch); 
                yaw += (Math.random() - 0.5f) * 0.4f; 
                pitch += (Math.random() - 0.5f) * 0.2f; 
                pitch = Math.max(-0.1f, Math.min(1.2f, pitch)); 
                vaciarEsfera(blocks, cx, cy, cz, radioGusano); 
            } 
        } 
        
        if (Math.random() < 0.05) { 
            int scanX = 2 + (int)(Math.random() * (Chunk.CHUNK_SIZE - 4)); 
            int scanZ = 2 + (int)(Math.random() * (Chunk.CHUNK_SIZE - 4)); 
            int superficieY = 0; 
            for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) { 
                if (blocks[scanX][y][scanZ] == GRASS) { 
                    superficieY = y; 
                    break; 
                } 
            } 
            if (superficieY > 75) { 
                float cx = scanX, cy = superficieY, cz = scanZ; 
                float yaw = (float)(Math.random() * Math.PI * 2); 
                float pitch = -0.5f - (float)(Math.random() * 0.4f); 
                int longitudEntrada = 70 + (int)(Math.random() * 40); 
                float radioEntrada = 2.5f + (float)Math.random() * 1.5f; 
                for (int paso = 0; paso < longitudEntrada; paso++) { 
                    cx += Math.cos(yaw) * Math.cos(pitch); 
                    cy += Math.sin(pitch); 
                    cz += Math.sin(yaw) * Math.cos(pitch); 
                    yaw += (Math.random() - 0.5f) * 0.4f; 
                    pitch += (Math.random() - 0.5f) * 0.2f; 
                    pitch = Math.max(-1.2f, Math.min(0.0f, pitch)); 
                    vaciarEsfera(blocks, cx, cy, cz, radioEntrada); 
                } 
            } 
        } 
    }
    
    private static void vaciarEsfera(int[][][] blocks, float cx, float cy, float cz, float radio) { 
        int r = (int)Math.ceil(radio); 
        for (int dx = -r; dx <= r; dx++) { 
            for (int dy = -r; dy <= r; dy++) { 
                for (int dz = -r; dz <= r; dz++) { 
                    if (dx*dx + dy*dy + dz*dz <= radio*radio) { 
                        int bx = (int)cx + dx; 
                        int by = (int)cy + dy; 
                        int bz = (int)cz + dz; 
                        if (bx >= 0 && bx < Chunk.CHUNK_SIZE && by > 4 && by < Chunk.CHUNK_HEIGHT && bz >= 0 && bz < Chunk.CHUNK_SIZE) { 
                            
                            boolean zonaProtegida = false;
                            if (by >= 35) {
                                for (int yTest = by; yTest <= 76; yTest++) {
                                    if (yTest < Chunk.CHUNK_HEIGHT) {
                                        int bTest = blocks[bx][yTest][bz];
                                        if (bTest == WATER || (bTest == SAND && yTest <= 75)) {
                                            zonaProtegida = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            
                            if (zonaProtegida) continue;

                            int bloque = blocks[bx][by][bz]; 
                            if (bloque == STONE || bloque == DEEPSLATE || bloque == DIRT || bloque == GRASS || bloque == SAND || bloque == IRON_ORE || bloque == COAL_ORE) { 
                                blocks[bx][by][bz] = -1; 
                            } 
                        } 
                    } 
                } 
            } 
        } 
    }

    private static void generarVetas(int[][][] blocks, int blockType, int intentos, int maxAltura, int minTam, int maxTam) { for (int i = 0; i < intentos; i++) { int x = (int)(Math.random() * Chunk.CHUNK_SIZE); int y = 5 + (int)(Math.random() * (maxAltura - 5)); int z = (int)(Math.random() * Chunk.CHUNK_SIZE); if (blocks[x][y][z] == STONE || blocks[x][y][z] == DEEPSLATE) { int tamaño = minTam + (int)(Math.random() * (maxTam - minTam)); for (int b = 0; b < tamaño; b++) { blocks[x][y][z] = blockType; x += (int)(Math.random() * 3) - 1; y += (int)(Math.random() * 3) - 1; z += (int)(Math.random() * 3) - 1; x = Math.max(0, Math.min(Chunk.CHUNK_SIZE - 1, x)); y = Math.max(5, Math.min(Chunk.CHUNK_HEIGHT - 1, y)); z = Math.max(0, Math.min(Chunk.CHUNK_SIZE - 1, z)); if (blocks[x][y][z] != STONE && blocks[x][y][z] != DEEPSLATE && blocks[x][y][z] != blockType) break; } } } }
    
    private static void generarArboles(int[][][] blocks, int chunkX, int chunkZ) { 
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) { 
            for (int z = 1; z < Chunk.CHUNK_SIZE - 1; z++) {
                int globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                int globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                Biome biome = BiomeProvider.getBiome(globalX, globalZ);
                if (!biome.canSpawnTrees) continue;
                
                int topY = 0;
                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    int b = blocks[x][y][z];
                    if (b != -1 && b != WATER && b != CLOUD && b != LEAVES && b != WOOD && b != CACTUS) {
                        topY = y;
                        break;
                    }
                } 
                
                if (topY <= 0) continue;
                int bloqueSuperficie = blocks[x][topY][z];
                if (bloqueSuperficie != GRASS && bloqueSuperficie != DIRT) continue;

                if (topY < Chunk.CHUNK_HEIGHT - 6) { 
                    boolean esPendiente = false; 
                    int[][] vecinos = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; 
                    for (int[] v : vecinos) { 
                        int nx = x + v[0]; 
                        int nz = z + v[1]; 
                        int nTopY = 0; 
                        for (int ny = Chunk.CHUNK_HEIGHT - 1; ny >= 0; ny--) { 
                            int b = blocks[nx][ny][nz]; 
                            if (b == GRASS || b == DIRT || b == SAND || b == STONE || b == DEEPSLATE) { 
                                nTopY = ny; 
                                break; 
                            } 
                        } 
                        if (Math.abs(nTopY - topY) > 1) { 
                            esPendiente = true; 
                            break; 
                        } 
                    } 
                    if (esPendiente) continue; 
                    boolean hayArbolCerca = false; 
                    for (int rx = x - 3; rx <= x + 3; rx++) { 
                        for (int rz = z - 3; rz <= z + 3; rz++) { 
                            if (rx >= 0 && rx < Chunk.CHUNK_SIZE && rz >= 0 && rz < Chunk.CHUNK_SIZE) { 
                                for (int ry = topY; ry <= topY + 5; ry++) { 
                                    if (ry < Chunk.CHUNK_HEIGHT && blocks[rx][ry][rz] == WOOD) { 
                                        hayArbolCerca = true; 
                                        break; 
                                    } 
                                } 
                            } 
                            if (hayArbolCerca) break; 
                        } 
                        if (hayArbolCerca) break; 
                    } 
                    if (!hayArbolCerca && Math.random() < 0.10f) { 
                        blocks[x][topY][z] = DIRT; 
                        generarArbolClasico(blocks, x, topY + 1, z); 
                    } 
                } 
            } 
        } 
    }
    
    private static void generarArbolClasico(int[][][] blocks, int baseX, int baseY, int baseZ) { int alturaTronco = 4 + (int)(Math.random() * 2); for (int i = 0; i < alturaTronco; i++) setBlockEnGeneracion(blocks, baseX, baseY + i, baseZ, WOOD); int copaInicioY = baseY + (alturaTronco - 2); for (int y = copaInicioY; y < baseY + alturaTronco; y++) { for (int x = baseX - 2; x <= baseX + 2; x++) { for (int z = baseZ - 2; z <= baseZ + 2; z++) { if (x == baseX && z == baseZ && y < baseY + alturaTronco) continue; if ((Math.abs(x - baseX) == 2 && Math.abs(z - baseZ) == 2) && Math.random() > 0.7) continue; setBlockEnGeneracion(blocks, x, y, z, LEAVES); } } } int puntaY = baseY + alturaTronco; for (int x = baseX - 1; x <= baseX + 1; x++) { for (int z = baseZ - 1; z <= baseZ + 1; z++) setBlockEnGeneracion(blocks, x, puntaY, z, LEAVES); } setBlockEnGeneracion(blocks, baseX, puntaY + 1, baseZ, LEAVES); setBlockEnGeneracion(blocks, baseX + 1, puntaY + 1, baseZ, LEAVES); setBlockEnGeneracion(blocks, baseX - 1, puntaY + 1, baseZ, LEAVES); setBlockEnGeneracion(blocks, baseX, puntaY + 1, baseZ + 1, LEAVES); setBlockEnGeneracion(blocks, baseX, puntaY + 1, baseZ - 1, LEAVES); }
    private static void setBlockEnGeneracion(int[][][] blocks, int x, int y, int z, int blockType) { if (x >= 0 && x < Chunk.CHUNK_SIZE && y >= 0 && y < Chunk.CHUNK_HEIGHT && z >= 0 && z < Chunk.CHUNK_SIZE) { int bloqueActual = blocks[x][y][z]; if (bloqueActual == -1 || bloqueActual == LEAVES) blocks[x][y][z] = blockType; } }
}