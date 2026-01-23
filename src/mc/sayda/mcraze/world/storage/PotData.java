package mc.sayda.mcraze.world.storage;

import mc.sayda.mcraze.item.Item;

/**
 * Stores data for a Flower Pot, including what is growing and its progress.
 */
public class PotData implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    public final int x;
    public final int y;

    public String plantId = null; // Item ID of the plant inside
    public int growthProgress = 0; // Current growth time
    public int growthTimeTotal = 0; // Total time needed to grow

    public PotData(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isEmpty() {
        return plantId == null;
    }

    public boolean isFinished() {
        return plantId != null && growthProgress >= growthTimeTotal;
    }

    public String getKey() {
        return x + "," + y;
    }

    public static String makeKey(int x, int y) {
        return x + "," + y;
    }
}
