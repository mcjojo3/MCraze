package mc.sayda.mcraze.world.storage;

import mc.sayda.mcraze.world.*;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.gen.*;
import mc.sayda.mcraze.world.storage.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.graphics.*;

import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Item;

/**
 * Server-side furnace data storage
 * Layout: [0,0]=Input, [0,1]=Fuel, [2,0]=Output
 */
public class FurnaceData implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public final int x;
    public final int y;
    public InventoryItem[][] items; // 3 wide x 2 tall grid

    // Smelting state
    public int fuelTimeRemaining = 0;
    public int smeltProgress = 0;
    public int smeltTimeTotal = 0;
    public String currentRecipe = null; // itemId being smelted

    public FurnaceData(int x, int y) {
        this.x = x;
        this.y = y;
        this.items = new InventoryItem[3][2]; // 3 columns, 2 rows

        // Initialize all slots with empty InventoryItems
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                items[i][j] = new InventoryItem(null);
            }
        }
    }

    /**
     * Get inventory item at furnace slot
     */
    public InventoryItem getInventoryItem(int slotX, int slotY) {
        if (slotX < 0 || slotX >= 3 || slotY < 0 || slotY >= 2)
            return null;
        return items[slotX][slotY];
    }

    /**
     * Convenience getters for specific slots
     */
    public InventoryItem getInputSlot() {
        return items[0][0];
    }

    public InventoryItem getFuelSlot() {
        return items[0][1];
    }

    public InventoryItem getOutputSlot() {
        return items[2][0];
    }

    /**
     * Check if furnace is empty
     */
    public boolean isEmpty() {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 2; y++) {
                if (items[x][y] != null && !items[x][y].isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get unique key for this furnace position
     */
    public String getKey() {
        return x + "," + y;
    }

    /**
     * Create key from position
     */
    public static String makeKey(int x, int y) {
        return x + "," + y;
    }

    /**
     * Check if furnace is currently smelting
     */
    public boolean isSmelting() {
        return smeltProgress > 0 && smeltTimeTotal > 0;
    }

    /**
     * Check if furnace has fuel
     */
    public boolean hasFuel() {
        return fuelTimeRemaining > 0;
    }
}
