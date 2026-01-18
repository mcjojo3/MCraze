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
