package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketFurnaceAction;
import mc.sayda.mcraze.util.Int2;

/**
 * Furnace UI - manages furnace state and inventory
 * 
 * Slot layout:
 * [0,0] = Input (item to smelt)
 * [0,1] = Fuel (coal)
 * [2,0] = Output (smelted result)
 */
public class FurnaceUI {
    private boolean visible = false;
    private Inventory playerInventory;
    private InventoryItem[][] furnaceItems = new InventoryItem[3][2]; // 3 columns, 2 rows

    // Furnace state
    private int fuelTimeRemaining = 0;
    private int smeltProgress = 0;
    private int smeltTimeTotal = 0;
    private String currentRecipe = null; // itemId being smelted

    // Furnace position (for server-side tracking)
    private int furnaceX;
    private int furnaceY;

    public FurnaceUI() {
        // Initialize empty slots
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 2; j++) {
                furnaceItems[i][j] = new InventoryItem(null);
            }
        }
    }

    public void open(Inventory playerInv, int x, int y) {
        this.playerInventory = playerInv;
        this.furnaceX = x;
        this.furnaceY = y;
        this.visible = true;
    }

    public void close() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public InventoryItem[][] getFurnaceItems() {
        return furnaceItems;
    }

    public Inventory getPlayerInventory() {
        return playerInventory;
    }

    // Furnace-specific getters/setters
    public int getFuelTimeRemaining() {
        return fuelTimeRemaining;
    }

    public void setFuelTimeRemaining(int time) {
        this.fuelTimeRemaining = time;
    }

    public int getSmeltProgress() {
        return smeltProgress;
    }

    public void setSmeltProgress(int progress) {
        this.smeltProgress = progress;
    }

    public int getSmeltTimeTotal() {
        return smeltTimeTotal;
    }

    public void setSmeltTimeTotal(int total) {
        this.smeltTimeTotal = total;
    }

    public String getCurrentRecipe() {
        return currentRecipe;
    }

    public void setCurrentRecipe(String recipe) {
        this.currentRecipe = recipe;
    }

    public int getFurnaceX() {
        return furnaceX;
    }

    public int getFurnaceY() {
        return furnaceY;
    }

    // Slot accessors
    public InventoryItem getInputSlot() {
        return furnaceItems[0][0];
    }

    public InventoryItem getFuelSlot() {
        return furnaceItems[0][1];
    }

    public InventoryItem getOutputSlot() {
        return furnaceItems[2][0];
    }

    /**
     * Handle mouse input for furnace UI
     * Returns true if mouse is over the furnace UI
     */
    public boolean update(int screenWidth, int screenHeight, Int2 mousePos,
            boolean leftClick, boolean rightClick, Connection connection) {
        if (!visible) {
            return false;
        }

        int tileSize = 16;
        int separation = 15;

        // Update holding item position for cursor rendering
        if (playerInventory != null) {
            playerInventory.holdingX = mousePos.x - tileSize / 2;
            playerInventory.holdingY = mousePos.y - tileSize / 2;
        }

        // Calculate furnace panel dimensions (3 wide x 2 tall)
        int furnacePanelWidth = 3 * (tileSize + separation) + separation;
        int furnacePanelHeight = 2 * (tileSize + separation) + separation;

        // Calculate player inventory panel dimensions (10 wide x 4 tall)
        int playerPanelWidth = 10 * (tileSize + separation) + separation;
        int playerPanelHeight = 4 * (tileSize + separation) + separation;

        // Center furnace panel at top, player panel below
        int totalHeight = furnacePanelHeight + playerPanelHeight + separation;
        int furnaceX = screenWidth / 2 - furnacePanelWidth / 2;
        int furnaceY = screenHeight / 2 - totalHeight / 2;
        int playerX = screenWidth / 2 - playerPanelWidth / 2;
        int playerY = furnaceY + furnacePanelHeight + separation;

        // Check if mouse is over furnace panel
        if (mousePos.x >= furnaceX && mousePos.x <= furnaceX + furnacePanelWidth &&
                mousePos.y >= furnaceY && mousePos.y <= furnaceY + furnacePanelHeight) {

            if (leftClick || rightClick) {
                // Calculate clicked slot in furnace
                Int2 slot = mouseToSlot(mousePos.x - furnaceX, mousePos.y - furnaceY, separation, tileSize);
                if (slot != null && slot.x < 3 && slot.y < 2) {
                    // Send furnace action packet (furnace slot)
                    if (connection != null) {
                        PacketFurnaceAction packet = new PacketFurnaceAction(
                                this.furnaceX, this.furnaceY, slot.x, slot.y, true, rightClick);
                        connection.sendPacket(packet);
                    }
                }
            }
            return true;
        }

        // Check if mouse is over player inventory panel
        if (mousePos.x >= playerX && mousePos.x <= playerX + playerPanelWidth &&
                mousePos.y >= playerY && mousePos.y <= playerY + playerPanelHeight) {

            if (leftClick || rightClick) {
                // Calculate clicked slot in player inventory
                Int2 slot = mouseToSlot(mousePos.x - playerX, mousePos.y - playerY, separation, tileSize);
                if (slot != null && slot.x < 10 && slot.y < 4) {
                    // Send furnace action packet (player inventory slot)
                    if (connection != null) {
                        PacketFurnaceAction packet = new PacketFurnaceAction(
                                this.furnaceX, this.furnaceY, slot.x, slot.y, false, rightClick);
                        connection.sendPacket(packet);
                    }
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Convert mouse position to slot coordinates
     * Uses unified SlotCoordinateHelper for gap-aware hit detection.
     */
    private Int2 mouseToSlot(int relX, int relY, int separation, int tileSize) {
        return mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
                relX, relY,
                tileSize, separation,
                separation, separation,
                10, 4); // FurnaceUI handles grids up to 10x4 (Furnace 3x2, Player Inv 10x4)
    }
}
