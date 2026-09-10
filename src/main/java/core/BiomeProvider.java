package core;

public class BiomeProvider {
    // Escala controlada para que los biomas cambien cada pocos cientos de bloques
    private static final float BIOME_SCALE = 0.012f; 

    public static Biome getBiome(float globalX, float globalZ) {
        float ruidoBioma = utils.PerlinNoise.getNoise((globalX + 8000f) * BIOME_SCALE, (globalZ + 8000f) * BIOME_SCALE);

        // === DISTRIBUCIÓN DE BIOMAS AMPLIADA ===
        // Subimos el límite a 0.45f para generar océanos mucho más grandes
        if (ruidoBioma < 0.45f) {
            return Biome.OCEAN;
        }
        else if (ruidoBioma > 0.65f) {
            return Biome.DESERT;
        }
        return Biome.PLAINS;
    }
}