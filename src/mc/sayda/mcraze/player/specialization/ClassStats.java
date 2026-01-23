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
    private final List<PassiveEffectType> passives;
    /**
     * Provider for class-specific stat calculations. Public for advanced passive
     * checks.
     */
    public final AbstractClassProvider provider;

    public ClassStats(PlayerClass playerClass, List<SpecializationPath> chosenPaths,
            List<PassiveEffectType> unlockedPassives) {
        this.playerClass = playerClass != null ? playerClass : PlayerClass.NONE;
        this.paths = chosenPaths != null ? chosenPaths : new ArrayList<>();
        this.passives = unlockedPassives != null ? unlockedPassives : new ArrayList<>();
        this.provider = createProvider(this.playerClass, this.paths, this.passives);
    }

    private AbstractClassProvider createProvider(PlayerClass pClass, List<SpecializationPath> paths,
            List<PassiveEffectType> passives) {
        switch (pClass) {
            case VANGUARD:
                return new VanguardStats(paths, passives);
            case ENGINEER:
                return new EngineerStats(paths, passives);
            case ARCANIST:
                return new ArcanistStats(paths, passives);
            case DRUID:
                return new DruidStats(paths, passives);
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
