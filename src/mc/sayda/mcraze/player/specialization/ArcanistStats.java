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
 * Stat modifiers for the Arcanist class.
 */
public class ArcanistStats extends AbstractClassProvider {
    public ArcanistStats(List<SpecializationPath> paths, List<PassiveEffectType> passives) {
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
        return 1.0f;
    }

    @Override
    public int getMaxMana() {
        int base = 50;
        if (passives.contains(PassiveEffectType.MANA_SURGE)) {
            base += 50; // +50 Mana
        }
        return base;
    }

    @Override
    public float getManaRegen() {
        float base = 3.0f; // 3 mana/sec
        if (passives.contains(PassiveEffectType.MANA_SURGE)) {
            base = 7.0f; // 7 mana/sec
        }
        return base;
    }
}
