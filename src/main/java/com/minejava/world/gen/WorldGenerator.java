package com.minejava.world.gen;

import java.util.Random;

import com.minejava.world.Chunk;

import static com.minejava.world.Block.*;

// Genera el terreno de los chunks de UN mundo a partir de su semilla. Todo lo que es al azar (cuevas,
// minerales, árboles, cactus y la roca madre) sale de un Random propio de cada chunk, que depende solo de
// la semilla y de la posición del chunk: la misma semilla da el mismo mundo, y un chunk sale igual si se
// descarga y se vuelve a cargar, sin importar en qué orden ni en qué hilo se genere.
// Lo usan varios hilos a la vez: no guarda nada que cambie al generar.
// El seno y el coseno usan StrictMath, que da el mismo resultado en cualquier equipo: con Math.sin
// un solo bit distinto podría cambiar una nube o el camino de una cueva.
public class WorldGenerator {

    // Lo que queda vacío hasta esta altura se llena de agua
    private static final int NIVEL_AGUA = 68;
    // Hasta qué distancia de (0, 0), en bloques, busca buscarSpawn() tierra firme
    private static final int RADIO_BUSQUEDA_SPAWN = 2048;

    private final long semilla;
    private final PerlinNoise ruido;
    private final BiomeProvider biomas;
    // Para mezclar la posición del chunk con la semilla, como Minecraft (ver randomDelChunk())
    private final long multX;
    private final long multZ;

    public WorldGenerator(long semilla) {
        this.semilla = semilla;
        this.ruido = new PerlinNoise(semilla);
        this.biomas = new BiomeProvider(ruido);

        Random rand = new Random(semilla);
        // "| 1" los hace impares: multiplicar por un impar nunca junta dos coordenadas distintas en un mismo número
        this.multX = rand.nextLong() | 1L;
        this.multZ = rand.nextLong() | 1L;
    }

    // El Random de un chunk. Minecraft lo hace igual: cada coordenada se multiplica por un número
    // grande sacado de la semilla, así chunks vecinos no terminan con semillas parecidas.
    private Random randomDelChunk(int chunkX, int chunkZ) {
        return new Random((chunkX * multX + chunkZ * multZ) ^ semilla);
    }

    public void generateTerrain(int[][][] blocks, int chunkX, int chunkZ) {
        Random rand = randomDelChunk(chunkX, chunkZ);

        // =================================================================
        // PASO 1: GENERAR TERRENO 
        // =================================================================
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                
                float globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                float globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                
                Biome biome = biomas.getBiome(globalX, globalZ);
                float ruidoBioma = ruidoBioma(globalX, globalZ);
                float distanciaAlCanal = distanciaAlCanal(globalX, globalZ);
                boolean esCuerpoAgua = distanciaAlCanal < 0.04f; 
                int columnHeight = alturaColumna(globalX, globalZ, ruidoBioma, distanciaAlCanal);

                boolean usarArena = false;
                if ((esCuerpoAgua && columnHeight <= NIVEL_AGUA + 1) || ruidoBioma < 0.48f) {
                    usarArena = true; 
                }

                for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                    if (y >= columnHeight) {
                        if (y <= NIVEL_AGUA) blocks[x][y][z] = WATER;
                        else blocks[x][y][z] = AIR; 
                    } else if (y == 0) {
                        blocks[x][y][z] = BEDROCK;  
                    } else if (y <= 4 && rand.nextDouble() < (1.0f - (y * 0.2f))) {
                        blocks[x][y][z] = BEDROCK;  
                    } else if (y < columnHeight - 4) {
                        if (y < 38) {
                            blocks[x][y][z] = DEEPSLATE; 
                        } else if (y <= 43) {
                            float probabilidadPiedra = (y - 38) / 5.0f;
                            blocks[x][y][z] = (rand.nextDouble() < probabilidadPiedra) ? STONE : DEEPSLATE;
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
            if (rand.nextDouble() < 0.85) generarSistemaCuevas(blocks, rand);
        }

        // =================================================================
        // PASO 3: PARCHEO DE AGUA (RÍOS EN TIERRA)
        // =================================================================
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                
                float globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                float globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                
                Biome biome = biomas.getBiome(globalX, globalZ);
                float ruidoBioma = ruidoBioma(globalX, globalZ);
                float distanciaAlCanal = distanciaAlCanal(globalX, globalZ);
                
                if (distanciaAlCanal < 0.04f && ruidoBioma >= 0.50f) {
                    int alturaBase = 72; 
                    float factorPicada = (0.04f - distanciaAlCanal) / 0.04f; 
                    float ruidoRugoso = ruido.getNoise(globalX * 0.3f, globalZ * 0.3f) * 2.0f;
                    int columnHeight = alturaBase - (int)(factorPicada * 8) + (int)ruidoRugoso;
                    boolean usarArena = (columnHeight <= NIVEL_AGUA + 1);
                    
                    for (int y = columnHeight - 6; y < columnHeight; y++) {
                        if (y > 4) { 
                            if (y == columnHeight - 1) blocks[x][y][z] = usarArena ? SAND : biome.surfaceBlock;
                            else blocks[x][y][z] = usarArena ? SAND : biome.fillerBlock;
                        }
                    }
                    for (int y = columnHeight; y <= NIVEL_AGUA; y++) blocks[x][y][z] = WATER; 
                }
            }
        }

        // =================================================================
        // PASO 4: MINERALES, ÁRBOLES Y CACTUS
        // =================================================================
        generarVetas(blocks, rand, COAL_ORE, 40 * multiplicador, 80, 5, 10);
        generarVetas(blocks, rand, IRON_ORE, 35 * multiplicador, 50, 4, 8);
        generarArboles(blocks, rand, chunkX, chunkZ);
        generarCactus(blocks, rand, chunkX, chunkZ);

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
                double pseudoRuido = StrictMath.sin(celdaX * 12.9898 + celdaZ * 78.233) * 43758.5453;
                pseudoRuido = pseudoRuido - Math.floor(pseudoRuido);
                float controlDensidad = ruido.getNoise(celdaX * 0.05f, celdaZ * 0.05f);
                double umbralRequerido = 0.75 - (controlDensidad * 0.38);
                
                if (pseudoRuido > umbralRequerido && controlDensidad > -0.15f) {
                    for (int y = 150; y <= 151; y++) {
                        if (y < Chunk.CHUNK_HEIGHT && blocks[x][y][z] == AIR) blocks[x][y][z] = CLOUD;
                    }
                }
            }
        }
    }

    // =================================================================
    // FÓRMULAS DE UNA COLUMNA
    // Solo usan el ruido: dan lo mismo sin importar el chunk, el orden ni el hilo. Las usan
    // generateTerrain() y buscarSpawn(), así el spawn se decide con las mismas cuentas que el terreno.
    // =================================================================

    // Decide océano, playa o tierra: el ruido del bioma con una pequeña distorsión
    private float ruidoBioma(float globalX, float globalZ) {
        float ruidoBiomaPuro = ruido.getNoise((globalX + 8000f) * 0.012f, (globalZ + 8000f) * 0.012f);
        float distorsion = (ruido.getNoise(globalX * 0.1f, globalZ * 0.1f) - 0.5f) * 0.1f;
        return ruidoBiomaPuro + distorsion;
    }

    // Qué tan lejos está la columna del centro de un río: menos de 0.04 es agua y menos de 0.06, orilla
    private float distanciaAlCanal(float globalX, float globalZ) {
        float ruidoAgua = ruido.getNoise(globalX * 0.16f, globalZ * 0.16f);
        return Math.abs(ruidoAgua - 0.5f);
    }

    // Altura del terreno en la columna, antes de las cuevas y del paso 3: el primer y vacío (aire, o agua
    // si no pasa de NIVEL_AGUA). Aplana cerca de los ríos, cava su cauce y la hunde en los océanos.
    private int alturaColumna(float globalX, float globalZ, float ruidoBioma, float distanciaAlCanal) {
        float ruidoBase = ruido.getNoise(globalX * 0.02f, globalZ * 0.02f); 
        int alturaOriginal = (int)(ruidoBase * 20) + 74; 

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
            float ruidoRugoso = ruido.getNoise(globalX * 0.3f, globalZ * 0.3f) * 2.0f;
            columnHeight = alturaBase - (int)(factorPicada * 8) + (int)ruidoRugoso;
        }

        float ruidoOceanoProfundo = ruido.getNoise(globalX * 0.04f, globalZ * 0.04f);
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

        return columnHeight;
    }

    // Dónde aparece el jugador, como en Minecraft: la primera columna de tierra firme que encuentra
    // recorriendo en espiral desde (0, 0). Solo usa el ruido, así que no hace falta generar ningún chunk,
    // y la misma semilla siempre da el mismo spawn. Devuelve {x, z}; si no hay tierra firme hasta
    // RADIO_BUSQUEDA_SPAWN, (0, 0).
    public int[] buscarSpawn() {
        // Espiral cuadrada: 1 paso a +x, 1 a +z, 2 a -x, 2 a -z, 3 a +x... Pasa por cada columna una vez,
        // de la más cercana a la más lejana (por anillos)
        int x = 0, z = 0;
        int dx = 1, dz = 0;
        for (int largo = 1; largo <= 2 * RADIO_BUSQUEDA_SPAWN + 1; largo++) {
            for (int tramo = 0; tramo < 2; tramo++) {
                for (int paso = 0; paso < largo; paso++) {
                    if (esTierraFirme(x, z)) return new int[] { x, z };
                    x += dx;
                    z += dz;
                }
                // Gira 90°
                int giro = dx;
                dx = -dz;
                dz = giro;
            }
        }
        return new int[] { 0, 0 };
    }

    // Si en la columna se puede aparecer de pie: no es océano ni río, y el suelo queda por encima del agua
    private boolean esTierraFirme(float globalX, float globalZ) {
        // De lo más barato a lo más caro: la mayoría de las columnas descartadas son de océano
        if (biomas.getBiome(globalX, globalZ) == Biome.OCEAN) return false;
        float distanciaAlCanal = distanciaAlCanal(globalX, globalZ);
        if (distanciaAlCanal < 0.04f) return false; // río: el mismo umbral que esCuerpoAgua
        return alturaColumna(globalX, globalZ, ruidoBioma(globalX, globalZ), distanciaAlCanal) > NIVEL_AGUA;
    }

    // --- MÉTODOS AUXILIARES ---
    
    private void generarCactus(int[][][] blocks, Random rand, int chunkX, int chunkZ) {
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) {
            for (int z = 1; z < Chunk.CHUNK_SIZE - 1; z++) {
                float globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                float globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                Biome biome = biomas.getBiome(globalX, globalZ);

                if (biome != Biome.DESERT) continue;

                int topY = 0;
                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    int b = blocks[x][y][z];
                    if (b != AIR && b != CLOUD) {
                        topY = y;
                        break;
                    }
                }

                // REDUJE LA PROBABILIDAD DE 0.02f a 0.002f PARA QUE SE VEA COMO MINECRAFT
                if (topY > 0 && topY >= 69 && blocks[x][topY][z] == SAND && rand.nextDouble() < 0.002f) {
                    int alturaCactus = 1 + (int)(rand.nextDouble() * 3); 
                    boolean puedeCrecer = true;

                    for (int i = 1; i <= alturaCactus; i++) {
                        int checkY = topY + i;
                        if (checkY >= Chunk.CHUNK_HEIGHT) { puedeCrecer = false; break; }
                        
                        if (blocks[x][checkY][z] != AIR) { puedeCrecer = false; break; }

                        if (blocks[x+1][checkY][z] != AIR && blocks[x+1][checkY][z] != CLOUD) puedeCrecer = false;
                        if (blocks[x-1][checkY][z] != AIR && blocks[x-1][checkY][z] != CLOUD) puedeCrecer = false;
                        if (blocks[x][checkY][z+1] != AIR && blocks[x][checkY][z+1] != CLOUD) puedeCrecer = false;
                        if (blocks[x][checkY][z-1] != AIR && blocks[x][checkY][z-1] != CLOUD) puedeCrecer = false;
                        
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

    private static void generarSistemaCuevas(int[][][] blocks, Random rand) { 
        float craterX = (float)(rand.nextDouble() * Chunk.CHUNK_SIZE); 
        float craterY = 12 + (float)(rand.nextDouble() * 18); 
        float craterZ = (float)(rand.nextDouble() * Chunk.CHUNK_SIZE); 
        float radioCrater = 8.0f + (float)(rand.nextDouble() * 5.0f); 
        vaciarEsfera(blocks, craterX, craterY, craterZ, radioCrater); 
        vaciarEsfera(blocks, craterX + 3, craterY - 1, craterZ + 2, radioCrater * 0.8f); 
        
        int numGusanosAscendentes = 2 + (int)(rand.nextDouble() * 3); 
        for (int i = 0; i < numGusanosAscendentes; i++) { 
            float cx = craterX, cy = craterY, cz = craterZ; 
            float yaw = (float)(rand.nextDouble() * Math.PI * 2); 
            float pitch = 0.4f + (float)(rand.nextDouble() * 0.5f); 
            int longitud = 120 + (int)(rand.nextDouble() * 40); 
            float radioGusano = 2.0f + (float)rand.nextDouble() * 1.5f; 
            for (int paso = 0; paso < longitud; paso++) { 
                cx += StrictMath.cos(yaw) * StrictMath.cos(pitch); 
                cy += StrictMath.sin(pitch); 
                cz += StrictMath.sin(yaw) * StrictMath.cos(pitch); 
                yaw += (rand.nextDouble() - 0.5f) * 0.4f; 
                pitch += (rand.nextDouble() - 0.5f) * 0.2f; 
                pitch = Math.max(-0.1f, Math.min(1.2f, pitch)); 
                vaciarEsfera(blocks, cx, cy, cz, radioGusano); 
            } 
        } 
        
        if (rand.nextDouble() < 0.05) { 
            int scanX = 2 + (int)(rand.nextDouble() * (Chunk.CHUNK_SIZE - 4)); 
            int scanZ = 2 + (int)(rand.nextDouble() * (Chunk.CHUNK_SIZE - 4)); 
            int superficieY = 0; 
            for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) { 
                if (blocks[scanX][y][scanZ] == GRASS) { 
                    superficieY = y; 
                    break; 
                } 
            } 
            if (superficieY > 75) { 
                float cx = scanX, cy = superficieY, cz = scanZ; 
                float yaw = (float)(rand.nextDouble() * Math.PI * 2); 
                float pitch = -0.5f - (float)(rand.nextDouble() * 0.4f); 
                int longitudEntrada = 70 + (int)(rand.nextDouble() * 40); 
                float radioEntrada = 2.5f + (float)rand.nextDouble() * 1.5f; 
                for (int paso = 0; paso < longitudEntrada; paso++) { 
                    cx += StrictMath.cos(yaw) * StrictMath.cos(pitch); 
                    cy += StrictMath.sin(pitch); 
                    cz += StrictMath.sin(yaw) * StrictMath.cos(pitch); 
                    yaw += (rand.nextDouble() - 0.5f) * 0.4f; 
                    pitch += (rand.nextDouble() - 0.5f) * 0.2f; 
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
                                blocks[bx][by][bz] = AIR; 
                            } 
                        } 
                    } 
                } 
            } 
        } 
    }

    private static void generarVetas(int[][][] blocks, Random rand, int blockType, int intentos, int maxAltura, int minTam, int maxTam) { for (int i = 0; i < intentos; i++) { int x = (int)(rand.nextDouble() * Chunk.CHUNK_SIZE); int y = 5 + (int)(rand.nextDouble() * (maxAltura - 5)); int z = (int)(rand.nextDouble() * Chunk.CHUNK_SIZE); if (blocks[x][y][z] == STONE || blocks[x][y][z] == DEEPSLATE) { int tamaño = minTam + (int)(rand.nextDouble() * (maxTam - minTam)); for (int b = 0; b < tamaño; b++) { blocks[x][y][z] = blockType; x += (int)(rand.nextDouble() * 3) - 1; y += (int)(rand.nextDouble() * 3) - 1; z += (int)(rand.nextDouble() * 3) - 1; x = Math.max(0, Math.min(Chunk.CHUNK_SIZE - 1, x)); y = Math.max(5, Math.min(Chunk.CHUNK_HEIGHT - 1, y)); z = Math.max(0, Math.min(Chunk.CHUNK_SIZE - 1, z)); if (blocks[x][y][z] != STONE && blocks[x][y][z] != DEEPSLATE && blocks[x][y][z] != blockType) break; } } } }
    
    private void generarArboles(int[][][] blocks, Random rand, int chunkX, int chunkZ) { 
        for (int x = 1; x < Chunk.CHUNK_SIZE - 1; x++) { 
            for (int z = 1; z < Chunk.CHUNK_SIZE - 1; z++) {
                int globalX = x + (chunkX * Chunk.CHUNK_SIZE);
                int globalZ = z + (chunkZ * Chunk.CHUNK_SIZE);
                Biome biome = biomas.getBiome(globalX, globalZ);
                if (!biome.canSpawnTrees) continue;
                
                int topY = 0;
                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    int b = blocks[x][y][z];
                    if (b != AIR && b != WATER && b != CLOUD && b != LEAVES && b != WOOD && b != CACTUS) {
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
                    if (!hayArbolCerca && rand.nextDouble() < 0.10f) { 
                        blocks[x][topY][z] = DIRT; 
                        generarArbolClasico(blocks, rand, x, topY + 1, z); 
                    } 
                } 
            } 
        } 
    }
    
    private static void generarArbolClasico(int[][][] blocks, Random rand, int baseX, int baseY, int baseZ) { int alturaTronco = 4 + (int)(rand.nextDouble() * 2); for (int i = 0; i < alturaTronco; i++) setBlockEnGeneracion(blocks, baseX, baseY + i, baseZ, WOOD); int copaInicioY = baseY + (alturaTronco - 2); for (int y = copaInicioY; y < baseY + alturaTronco; y++) { for (int x = baseX - 2; x <= baseX + 2; x++) { for (int z = baseZ - 2; z <= baseZ + 2; z++) { if (x == baseX && z == baseZ && y < baseY + alturaTronco) continue; if ((Math.abs(x - baseX) == 2 && Math.abs(z - baseZ) == 2) && rand.nextDouble() > 0.7) continue; setBlockEnGeneracion(blocks, x, y, z, LEAVES); } } } int puntaY = baseY + alturaTronco; for (int x = baseX - 1; x <= baseX + 1; x++) { for (int z = baseZ - 1; z <= baseZ + 1; z++) setBlockEnGeneracion(blocks, x, puntaY, z, LEAVES); } setBlockEnGeneracion(blocks, baseX, puntaY + 1, baseZ, LEAVES); setBlockEnGeneracion(blocks, baseX + 1, puntaY + 1, baseZ, LEAVES); setBlockEnGeneracion(blocks, baseX - 1, puntaY + 1, baseZ, LEAVES); setBlockEnGeneracion(blocks, baseX, puntaY + 1, baseZ + 1, LEAVES); setBlockEnGeneracion(blocks, baseX, puntaY + 1, baseZ - 1, LEAVES); }
    private static void setBlockEnGeneracion(int[][][] blocks, int x, int y, int z, int blockType) { if (x >= 0 && x < Chunk.CHUNK_SIZE && y >= 0 && y < Chunk.CHUNK_HEIGHT && z >= 0 && z < Chunk.CHUNK_SIZE) { int bloqueActual = blocks[x][y][z]; if (bloqueActual == AIR || bloqueActual == LEAVES) blocks[x][y][z] = blockType; } }
}