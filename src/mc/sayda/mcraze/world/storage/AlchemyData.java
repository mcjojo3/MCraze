package mc.sayda.mcraze.world.storage;

import mc.sayda.mcraze.item.InventoryItem;

/**
 * Stores the state of an Alchemy Table at a specific world position.
 * Layout: [0,0]=Input, [1,0]=ProgressIndicator, [2,0]=Output
 */
public class AlchemyData implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public final int x;
    public final int y;
    public InventoryItem[] items; // 5 slots (3 Ingredients, Bottle, Result)

    // Alchemy state
    public int brewProgress = 0;
    public int brewTimeTotal = 0;
    public String currentRecipe = null;

    public AlchemyData(int x, int y) {
        this.x = x;
        this.y = y;
        this.items = new InventoryItem[5];
        for (int i = 0; i < 5; i++) {
            items[i] = new InventoryItem(null);
        }
    }

    public InventoryItem getIngredientSlot(int index) {
        if (index >= 0 && index < 3)
            return items[index];
        return null;
    }

    public InventoryItem getBottleSlot() {
        return items[3];
    }

    public InventoryItem getResultSlot() {
        return items[4];
    }

    public boolean isBrewing() {
        return brewProgress > 0 && brewTimeTotal > 0;
    }

    public String getKey() {
        return x + "," + y;
    }

    public int getCurrentRecipeCost() {
        // Collect current ingredients
        java.util.List<String> inputs = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            InventoryItem item = items[i];
            if (item != null && !item.isEmpty() && item.getItem() != null) {
                inputs.add(item.getItem().itemId);
            }
        }

        if (inputs.isEmpty()) {
            return 0;
        }

        // Search for matching recipe
        for (mc.sayda.mcraze.item.ItemLoader.ItemDefinition def : mc.sayda.mcraze.item.ItemLoader
                .getAllItemDefinitions()) {
            if (def.alchemy != null && def.alchemy.ingredients != null) {
                // Check if ingredients match
                if (def.alchemy.ingredients.length != inputs.size()) {
                    continue;
                }

                // Check contents (order independent matching)
                java.util.List<String> recipeIngredients = new java.util.ArrayList<>();
                for (String s : def.alchemy.ingredients) {
                    recipeIngredients.add(s);
                }

                boolean match = true;
                for (String input : inputs) {
                    if (!recipeIngredients.remove(input)) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    return def.alchemy.essence;
                }
            }
        }

        return 0;
    }

    public static String makeKey(int x, int y) {
        return x + "," + y;
    }
}
