package mc.sayda.mcraze.player.specialization;

import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Calculates and stores stat modifiers based on player class and specialization
 * paths.
 */
public class ClassStats {
    private final PlayerClass playerClass;
    private final List<SpecializationPath> paths;
    private final AbstractClassProvider provider;

    public ClassStats(PlayerClass playerClass, SpecializationPath... chosenPaths) {
        this.playerClass = playerClass != null ? playerClass : PlayerClass.NONE;
        this.paths = chosenPaths != null ? Arrays.asList(chosenPaths) : new ArrayList<>();
        this.provider = createProvider(this.playerClass, this.paths);
    }

    private AbstractClassProvider createProvider(PlayerClass pClass, List<SpecializationPath> paths) {
        switch (pClass) {
            case VANGUARD:
                return new VanguardStats(paths);
            case ENGINEER:
                return new EngineerStats(paths);
            case ARCANIST:
                return new ArcanistStats(paths);
            case DRUID:
                return new DruidStats(paths);
            case NONE:
            default:
                return null;
        }
    }

    /**
     * Get the final Max HP for a player with these stats.
     * Base: 100
     */
    public int getMaxHP() {
        int base = 100;
        if (provider == null)
            return base;
        return provider.getMaxHP(base);
    }

    /**
     * Get melee damage multiplier.
     */
    public float getMeleeDamageMultiplier() {
        if (provider == null)
            return 1.0f;
        return provider.getMeleeDamageMultiplier();
    }

    /**
     * Get ranged damage multiplier.
     */
    public float getRangedDamageMultiplier() {
        if (provider == null)
            return 1.0f;
        return provider.getRangedDamageMultiplier();
    }

    /**
     * Get gathering speed multiplier for specific tools.
     */
    public float getGatheringSpeedMultiplier(GatheringType type) {
        if (provider == null)
            return 1.0f;
        return provider.getGatheringSpeedMultiplier(type);
    }

    /**
     * Get max mana pool.
     */
    public int getMaxMana() {
        if (provider == null)
            return 0;
        return provider.getMaxMana();
    }

    /**
     * Get mana regeneration per second.
     */
    public float getManaRegen() {
        if (provider == null)
            return 0;
        return provider.getManaRegen();
    }

    /**
     * Get durability multiplier for crafted tools.
     */
    public float getDurabilityMultiplier() {
        if (provider == null)
            return 1.0f;
        return provider.getDurabilityMultiplier();
    }

    /**
     * Get fall damage multiplier for traps.
     */
    public float getTrapFallDamageMultiplier() {
        if (provider == null)
            return 1.0f;
        return provider.getTrapFallDamageMultiplier();
    }

    public enum GatheringType {
        MINING, WOOD, FARMING
    }
}
