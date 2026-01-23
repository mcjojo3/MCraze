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

import java.util.ArrayList;
import java.util.List;

/**
 * Manages skill point allocation and ability unlocking.
 * Provides utility methods for checking unlocked abilities and spending points.
 */
public class SkillPointManager {

    /**
     * Check if a player can unlock a specific ability.
     * 
     * @param playerClass      The player's selected class
     * @param unlockedPassives List of already unlocked passives
     * @param availablePoints  Points available to spend
     * @param target           The ability to unlock
     * @return true if the player can unlock this ability
     */
    public static boolean canUnlock(PlayerClass playerClass, List<PassiveEffectType> unlockedPassives,
            int availablePoints, PassiveEffectType target) {
        // Must have at least 1 point
        if (availablePoints < 1)
            return false;

        // Must not already be unlocked
        if (unlockedPassives.contains(target))
            return false;

        // Must belong to the player's class
        if (target.getParentClass() != playerClass)
            return false;

        return true;
    }

    /**
     * Get all abilities available to a specific class.
     * 
     * @param playerClass The class to get abilities for
     * @return List of all PassiveEffectType values for this class
     */
    public static List<PassiveEffectType> getAbilitiesForClass(PlayerClass playerClass) {
        List<PassiveEffectType> abilities = new ArrayList<>();
        for (PassiveEffectType passive : PassiveEffectType.values()) {
            if (passive.getParentClass() == playerClass) {
                abilities.add(passive);
            }
        }
        return abilities;
    }

    /**
     * Check if a player has a specific passive unlocked.
     * 
     * @param unlockedPassives List of unlocked passives
     * @param target           The passive to check
     * @return true if the player has this passive
     */
    public static boolean hasPassive(List<PassiveEffectType> unlockedPassives, PassiveEffectType target) {
        return unlockedPassives != null && unlockedPassives.contains(target);
    }
}
