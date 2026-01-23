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

/**
 * All individual passive abilities in the class system.
 * Each class has 15 abilities (5 per thematic path).
 * Players spend skill points to unlock specific abilities.
 */
public enum PassiveEffectType {
    // ============== VANGUARD (Sentinel, Champion, Blacksmith) ==============
    // Sentinel Path
    UNBREAKABLE,
    IRON_SKIN,
    STEADFAST,
    SECOND_WIND,
    GUARDIANS_PRESENCE,

    // Champion Path
    BORN_WARRIOR,
    HEAVY_STRIKER,
    BATTLE_FRENZY,
    CLEAVE,
    BLOOD_RAGE,

    // Blacksmith Path
    MASTER_MINER,
    FORGE_MASTER,
    REINFORCED_CRAFT,
    SALVAGER,
    METALLURGY,

    // ============== ENGINEER (Marksman, Trap Master, Lumberjack) ==============
    // Marksman Path
    EAGLE_EYE,
    HEADSHOT_MASTER,
    SCAVENGER,
    PIERCING_SHOT,
    RAPID_FIRE,

    // Trap Master Path
    TRAP_MECHANISMS,
    SPIKE_SPECIALIST,
    OWNER_SECURITY,
    AUTO_DEPLOY,
    FIELD_CONTROL,

    // Lumberjack Path
    LUMBER_EXPERT,
    EFFICIENT_BUILDER,
    REINFORCED_STRUCTURES,
    BUILDERS_RANGE,
    FOREST_HARVEST,

    // ============== ARCANIST (Elementalist, Guardian Angel, Alchemist)
    // ==============
    // Elementalist Path
    ARCANE_INFUSION,
    MANA_SURGE,
    CRYSTAL_RESONANCE,
    MANA_ECHO,
    MANA_BURN,

    // Guardian Angel Path
    MYSTIC_REGENERATION,
    GUARDIANS_AURA,
    ARCANE_SHIELD,
    SHARED_VITALITY,
    DIVINE_INTERVENTION,

    // Alchemist Path
    ALCHEMIST_BLESSING,
    ESSENCE_COLLECTOR,
    POTION_MASTER,
    EFFICIENT_BREWING,
    TRANSMUTATION,

    // ============== DRUID (Cultivator, Beast Tamer, Chef) ==============
    // Cultivator Path
    GREEN_THUMB,
    MASTER_FARMER,
    IRRIGATED_SOIL,
    PHOTOSYNTHESIS,
    BOUNTIFUL_YIELD,

    // Beast Tamer Path
    SOUL_LINK,
    TAME_WOLVES,
    LOYAL_COMPANION,
    OWNER_LOYALTY,
    WILD_BOND,

    // Chef Path
    BOUNTIFUL_HARVEST,
    COMFORT_FOOD,
    FAST_METABOLISM,
    FEAST_PREPARATION,
    ANIMAL_WHISPERER;

    /**
     * Get the parent class for this passive.
     */
    public PlayerClass getParentClass() {
        int ordinal = this.ordinal();
        if (ordinal < 15)
            return PlayerClass.VANGUARD;
        if (ordinal < 30)
            return PlayerClass.ENGINEER;
        if (ordinal < 45)
            return PlayerClass.ARCANIST;
        return PlayerClass.DRUID;
    }

    /**
     * Get the parent path for this passive.
     */
    public SpecializationPath getParentPath() {
        int ordinal = this.ordinal();
        int pathIndex = (ordinal % 15) / 5;

        PlayerClass parentClass = getParentClass();
        SpecializationPath[] paths = SpecializationPath.values();
        for (SpecializationPath path : paths) {
            if (path.getParentClass() == parentClass) {
                if (pathIndex == 0)
                    return path;
                pathIndex--;
            }
        }
        return null;
    }
}
