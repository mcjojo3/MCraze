package mc.sayda.mcraze.server;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.world.Biome;

public class SpawnRule {
    public final Class<? extends Entity> entityClass;
    public final int weight;
    public final int groupMin;
    public final int groupMax;
    public final Biome[] biomes;
    public final boolean hostile;

    public final int width;
    public final int height;

    public SpawnRule(Class<? extends Entity> entityClass, int weight, int groupMin, int groupMax, boolean hostile,
            int width, int height,
            Biome... biomes) {
        this.entityClass = entityClass;
        this.weight = weight;
        this.groupMin = groupMin;
        this.groupMax = groupMax;
        this.hostile = hostile;
        this.width = width;
        this.height = height;
        this.biomes = biomes;
    }
}
