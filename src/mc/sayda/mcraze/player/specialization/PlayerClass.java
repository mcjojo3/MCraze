package mc.sayda.mcraze.player.specialization;
import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

/**
 * Base classes for the MCraze survival game.
 */
public enum PlayerClass {
    NONE("None", "No specialization selected."),
    VANGUARD("Vanguard", "Frontline defender - tanky and resourceful."),
    ENGINEER("Engineer", "Tactical genius - traps and ranged combat."),
    ARCANIST("Arcanist", "Scholastic mystic - magic and support."),
    DRUID("Druid", "Nature master - farming and companions.");

    private final String displayName;
    private final String description;

    PlayerClass(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
