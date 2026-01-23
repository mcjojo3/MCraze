package mc.sayda.mcraze.survival;

/**
 * Configuration for the Wave System difficulty scaling.
 */
public class WaveConfig {

    // Cycle Lengths
    public static final int DAYS_PER_BOSS = 7;
    public static final int DAYS_PER_MONTH = 28;

    // Scaling Multipliers (per day)
    // Formula: Base * (1 + (Day / FACTOR))
    public static final float HP_SCALING_FACTOR = 128.0f; // +100% HP every 128 days
    public static final float DAMAGE_SCALING_FACTOR = 28.0f; // +100% Damage every 28 days
    public static final float SPAWN_RATE_FACTOR = 14.0f; // +100% Spawn Rate every 14 days (capped?)

    // Settlement Safety
    public static final int SAFE_ZONE_RADIUS = 32; // Radius in blocks around Flag where mobs won't spawn

    // AI / Physics Scaling
    public static final float ZOMBIE_BASE_JUMP = -0.3f; // Base jump velocity (negative for up)
    public static final float JUMP_SCALING_FACTOR = 0.025f; // +2.5% jump height per day

    /**
     * Calculate health multiplier for a given day
     */
    public static float getHealthMultiplier(int day) {
        // Day 1 = 1.0 (Base)
        // Day 14 = 2.0 (+100%)
        return 1.0f + ((float) day / HP_SCALING_FACTOR);
    }

    /**
     * Calculate damage multiplier for a given day
     */
    public static float getDamageMultiplier(int day) {
        // Day 1 = 1.0 (Base)
        // Day 21 = 2.0 (+100%)
        return 1.0f + ((float) day / DAMAGE_SCALING_FACTOR);
    }

    /**
     * Calculate spawn count multiplier for a given day
     */
    public static float getSpawnCountMultiplier(int day) {
        // Day 1 = 1.0 (Base)
        // Day 7 = 2.0 (+100%)
        return 1.0f + ((float) day / SPAWN_RATE_FACTOR);
    }

    /**
     * Calculate zombie jump height for a given day
     */
    public static float getZombieJumpHeight(int day) {
        // Linear increase: +2.5% jump height per day
        return ZOMBIE_BASE_JUMP * (1.0f + ((day - 1) * JUMP_SCALING_FACTOR));
    }
}
