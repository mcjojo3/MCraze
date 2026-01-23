/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

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
    protected final List<PassiveEffectType> passives;

    public AbstractClassProvider(List<SpecializationPath> paths, List<PassiveEffectType> passives) {
        this.paths = paths;
        this.passives = passives;
    }

    public abstract int getMaxHP(int base);

    public abstract float getMeleeDamageMultiplier();

    public abstract float getRangedDamageMultiplier();

    public abstract float getGatheringSpeedMultiplier(ClassStats.GatheringType type);

    public abstract int getMaxMana();

    public abstract float getManaRegen();

    public float getDurabilityMultiplier() {
        return 1.0f;
    }

    public float getTrapFallDamageMultiplier() {
        return 1.0f;
    }
}
