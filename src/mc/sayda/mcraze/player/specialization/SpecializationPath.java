package mc.sayda.mcraze.player.specialization;

import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

/**
 * Specialization paths (skill trees) available to each class.
 * Each class has 3 paths with 5 abilities each.
 * Players spend skill points on individual abilities from any path.
 */
public enum SpecializationPath {
    // Vanguard Paths
    SENTINEL(PlayerClass.VANGUARD, "Sentinel", "Maximum defense and team protection."),
    CHAMPION(PlayerClass.VANGUARD, "Champion", "High melee damage and attack speed."),
    BLACKSMITH(PlayerClass.VANGUARD, "Blacksmith", "Expert crafting and resource efficiency."),

    // Engineer Paths
    MARKSMAN(PlayerClass.ENGINEER, "Marksman", "Deadly ranged precision and crits."),
    TRAP_MASTER(PlayerClass.ENGINEER, "Trap Master", "Advanced trap mechanisms and area control."),
    LUMBERJACK(PlayerClass.ENGINEER, "Lumberjack", "Wood gathering and structural reinforcement."),

    // Arcanist Paths
    ELEMENTALIST(PlayerClass.ARCANIST, "Elementalist", "Offensive magic and elemental AoE."),
    GUARDIAN_ANGEL(PlayerClass.ARCANIST, "Guardian Angel", "Healing infusions and protective auras."),
    ALCHEMIST(PlayerClass.ARCANIST, "Alchemist", "Enhanced potions and resource transmutation."),

    // Druid Paths
    CULTIVATOR(PlayerClass.DRUID, "Cultivator", "Advanced farming and growth acceleration."),
    BEAST_TAMER(PlayerClass.DRUID, "Beast Tamer", "Taming and buffing wolf companions."),
    CHEF(PlayerClass.DRUID, "Chef", "Superior food effects and group feasts.");

    private final PlayerClass parentClass;
    private final String displayName;
    private final String description;

    SpecializationPath(PlayerClass parentClass, String displayName, String description) {
        this.parentClass = parentClass;
        this.displayName = displayName;
        this.description = description;
    }

    public PlayerClass getParentClass() {
        return parentClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
