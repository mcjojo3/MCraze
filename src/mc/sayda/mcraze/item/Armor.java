package mc.sayda.mcraze.item;

public class Armor extends Item {
    private static final long serialVersionUID = 1L;

    public EquipmentSlot slot;
    public int defense; // Armor points (e.g. 1-5)
    public int magicDefense; // Magic resistance points (e.g. 0-5)

    public Armor(String ref, int size, String itemId, String name, String[][] template, int templateCount,
            boolean shapeless, EquipmentSlot slot, int defense, int magicDefense) {
        super(ref, size, itemId, name, template, templateCount, shapeless);
        this.slot = slot;
        this.defense = defense;
        this.magicDefense = magicDefense;
    }

    @Override
    public Item clone() {
        Armor cloned = (Armor) super.clone();
        cloned.slot = this.slot;
        cloned.defense = this.defense;
        cloned.magicDefense = this.magicDefense;
        return cloned;
    }
}
