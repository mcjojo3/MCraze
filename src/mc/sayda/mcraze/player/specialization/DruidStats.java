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
 * Stat modifiers for the Druid class.
 */
public class DruidStats extends AbstractClassProvider {
    public DruidStats(List<SpecializationPath> paths, List<PassiveEffectType> passives) {
        super(paths, passives);
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
        if (type == ClassStats.GatheringType.FARMING && passives.contains(PassiveEffectType.BOUNTIFUL_YIELD)) {
            return 1.35f; // +35% Farm speed (Bountiful Yield)
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
}
