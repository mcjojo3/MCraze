package mc.sayda.mcraze.player.specialization;

import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import java.util.List;

/**
 * Stat modifiers for the Engineer class.
 */
public class EngineerStats extends AbstractClassProvider {
    public EngineerStats(List<SpecializationPath> paths) {
        super(paths);
    }

    @Override
    public int getMaxHP(int base) {
        return base;
    }

    @Override
    public float getMeleeDamageMultiplier() {
        return 1.0f;
    }

    @Override
    public float getRangedDamageMultiplier() {
        float multiplier = 1.10f; // +10% base for Engineer

        if (paths.contains(SpecializationPath.MARKSMAN)) {
            multiplier += 0.30f; // +30% Ranged
        }

        return multiplier;
    }

    @Override
    public float getGatheringSpeedMultiplier(ClassStats.GatheringType type) {
        if (type == ClassStats.GatheringType.WOOD && paths.contains(SpecializationPath.LUMBERJACK)) {
            return 1.50f; // +50% Wood
        }
        return 1.0f;
    }

    @Override
    public int getMaxMana() {
        return 0;
    }

    @Override
    public float getManaRegen() {
        return 0;
    }

    @Override
    public float getTrapFallDamageMultiplier() {
        if (paths.contains(SpecializationPath.TRAP_MASTER)) {
            return 2.0f; // Double damage from spikes (Spike Specialist)
        }
        return 1.0f;
    }
}
