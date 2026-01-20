package mc.sayda.mcraze.player.specialization;

import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import java.util.List;

/**
 * Stat modifiers for the Vanguard class.
 */
public class VanguardStats extends AbstractClassProvider {
    public VanguardStats(List<SpecializationPath> paths) {
        super(paths);
    }

    @Override
    public int getMaxHP(int base) {
        float multiplier = 1.10f; // +10% base for Vanguard

        if (paths.contains(SpecializationPath.SENTINEL)) {
            multiplier += 0.20f; // +20% Health (Tank path)
        }

        return (int) (base * multiplier);
    }

    @Override
    public float getMeleeDamageMultiplier() {
        float multiplier = 1.05f; // +5% base for Vanguard

        if (paths.contains(SpecializationPath.CHAMPION)) {
            multiplier += 0.30f; // +30% Melee
        }

        return multiplier;
    }

    @Override
    public float getRangedDamageMultiplier() {
        return 1.0f;
    }

    @Override
    public float getGatheringSpeedMultiplier(ClassStats.GatheringType type) {
        if (type == ClassStats.GatheringType.MINING && paths.contains(SpecializationPath.BLACKSMITH)) {
            return 1.40f; // +40% Mining
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
    public float getDurabilityMultiplier() {
        if (paths.contains(SpecializationPath.BLACKSMITH)) {
            return 1.50f; // +50% Durability (Reinforced Craft)
        }
        return 1.0f;
    }
}
