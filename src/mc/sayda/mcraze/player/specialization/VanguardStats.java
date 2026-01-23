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
 * Stat modifiers for the Vanguard class.
 */
public class VanguardStats extends AbstractClassProvider {
    public VanguardStats(List<SpecializationPath> paths, List<PassiveEffectType> passives) {
        super(paths, passives);
    }

    @Override
    public int getMaxHP(int base) {
        float multiplier = 1.10f; // +10% base for Vanguard

        if (passives.contains(PassiveEffectType.UNBREAKABLE)) {
            multiplier += 0.20f; // +20% Health (Tank path)
        }

        return (int) (base * multiplier);
    }

    @Override
    public float getMeleeDamageMultiplier() {
        float multiplier = 1.05f; // +5% base for Vanguard

        if (passives.contains(PassiveEffectType.BORN_WARRIOR)) {
            multiplier += 0.30f; // +30% Melee
        }

        return multiplier;
    }

    /**
     * Blood Rage: +2% damage for every 10% HP missing.
     * Must be called with current HP and max HP to calculate bonus.
     */
    public float getBloodRageBonus(int currentHP, int maxHP) {
        if (!passives.contains(PassiveEffectType.BLOOD_RAGE) || maxHP <= 0) {
            return 0f;
        }
        float missingPercent = 1.0f - ((float) currentHP / maxHP);
        // +2% per 10% missing = 0.2% per 1% missing = 0.002 per 1%
        return missingPercent * 0.2f; // e.g., 50% missing = +10% damage
    }

    @Override
    public float getRangedDamageMultiplier() {
        return 1.0f;
    }

    @Override
    public float getGatheringSpeedMultiplier(ClassStats.GatheringType type) {
        if (type == ClassStats.GatheringType.MINING && passives.contains(PassiveEffectType.MASTER_MINER)) {
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
        if (passives.contains(PassiveEffectType.REINFORCED_CRAFT)) {
            return 1.50f; // +50% Durability (Reinforced Craft)
        }
        return 1.0f;
    }
}
