package mc.sayda.mcraze.survival;

/**
 * Configuration for the Wave System difficulty scaling.
 */
public class WaveConfig {

    // Day Phase Constants (Ticks)
    // Total day length: 24000 ticks (20 minutes at 20 TPS)
    // Day Phase Constants (Ticks)
    // Total day length: 20000 ticks (matches Constants.WORLD_DAY_LENGTH_TICKS)
    public static final int DAY_LENGTH = 20000;
    public static final int DAWN_TIME = 0; // 06:00
    public static final int NOON_TIME = 5000; // 12:00
    public static final int DUSK_TIME = 10000; // 18:00 (Night/Wave Start)
    public static final int MIDNIGHT = 15000; // 00:00

    // Cycle Lengths
    public static final int DAYS_PER_BOSS = 7;
    public static final int DAYS_PER_MONTH = 28;

    // Scaling Multipliers (per day)
    // Formula: Base * (1 + (Day / FACTOR))
    public static final float HP_SCALING_FACTOR = 28.0f; // +100% HP every 28 days (halved scaling)
    public static final float DAMAGE_SCALING_FACTOR = 28.0f; // +100% Damage every 28 days
    public static final float SPAWN_RATE_FACTOR = 14.0f; // +100% Spawn Rate every 14 days (capped?)

    // Settlement Safety
    public static final int SAFE_ZONE_RADIUS = 32; // Radius in blocks around Flag where mobs won't spawn

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
}
