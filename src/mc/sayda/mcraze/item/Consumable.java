package mc.sayda.mcraze.item;

import mc.sayda.mcraze.entity.buff.BuffType;

/**
 * Represents an item that can be consumed (potions, food).
 * Applies buffs and/or healing to the player.
 */
public class Consumable extends Item {
    private static final long serialVersionUID = 1L;

    public BuffType buffType;
    public int buffDuration; // In ticks
    public int buffAmplifier; // Level (0 = Level I)
    public int healingAmount; // HP restored on consumption

    public Consumable(String ref, int size, String itemId, String name, String[][] template, int templateCount,
            boolean shapeless) {
        super(ref, size, itemId, name, template, templateCount, shapeless);
    }

    @Override
    public Consumable clone() {
        Consumable cloned = (Consumable) super.clone();
        cloned.buffType = this.buffType;
        cloned.buffDuration = this.buffDuration;
        cloned.buffAmplifier = this.buffAmplifier;
        cloned.healingAmount = this.healingAmount;
        return cloned;
    }
}
