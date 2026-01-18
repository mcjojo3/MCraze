package mc.sayda.mcraze.player.specialization;
import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import java.util.List;

/**
 * Stat modifiers for the Arcanist class.
 */
public class ArcanistStats extends AbstractClassProvider {
    public ArcanistStats(List<SpecializationPath> paths) {
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
        return 1.0f;
    }

    @Override
    public float getGatheringSpeedMultiplier(ClassStats.GatheringType type) {
        return 1.0f;
    }

    @Override
    public int getMaxMana() {
        int base = 50;
        if (paths.contains(SpecializationPath.ELEMENTALIST)) {
            base += 50; // +50 Mana
        }
        return base;
    }

    @Override
    public float getManaRegen() {
        float base = 3.0f; // 3 mana/sec
        if (paths.contains(SpecializationPath.ELEMENTALIST)) {
            base = 7.0f; // 7 mana/sec
        }
        return base;
    }
}
