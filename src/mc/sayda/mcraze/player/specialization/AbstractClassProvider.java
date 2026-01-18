package mc.sayda.mcraze.player.specialization;
import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import java.util.List;

/**
 * Base class for class-specific stat providers.
 * Makes it easier to find logic for a specific game class.
 */
public abstract class AbstractClassProvider {
    protected final List<SpecializationPath> paths;

    public AbstractClassProvider(List<SpecializationPath> paths) {
        this.paths = paths;
    }

    public abstract int getMaxHP(int base);

    public abstract float getMeleeDamageMultiplier();

    public abstract float getRangedDamageMultiplier();

    public abstract float getGatheringSpeedMultiplier(ClassStats.GatheringType type);

    public abstract int getMaxMana();

    public abstract float getManaRegen();
}
