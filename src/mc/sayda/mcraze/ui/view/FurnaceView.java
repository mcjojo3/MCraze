package mc.sayda.mcraze.ui.view;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.ui.container.FurnaceUI;
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.ui.component.Tooltip;

/**
 * View component for furnace UI - renders 3-slot furnace interface
 * Layout: [Input] [Progress→] [Output]
 * [Fuel] [Flame]
 */
public class FurnaceView {
    private static final int TILE_SIZE = 16;
    private static final int SEPARATION = 15;

    private FurnaceUI furnaceUI;
    private Tooltip tooltip;
    private int screenWidth;
    private int screenHeight;

    public FurnaceView(FurnaceUI ui) {
        this.furnaceUI = ui;
        this.tooltip = new Tooltip();
    }

    public void updateTooltip(int mouseX, int mouseY) {
        InventoryItem hoveredItem = getItemAtMouse(mouseX, mouseY);
        if (hoveredItem != null && !hoveredItem.isEmpty()) {
            tooltip.clearLines()
                    .addLine(hoveredItem.getItem().name)
                    .addLine("Count: " + hoveredItem.getCount())
                    .show(mouseX, mouseY);
        } else {
            tooltip.hide();
        }
    }

    private InventoryItem getItemAtMouse(int mouseX, int mouseY) {
        if (!furnaceUI.isVisible()) {
            return null;
        }

        int furnacePanelWidth = 3 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int furnacePanelHeight = 2 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelHeight = 4 * (TILE_SIZE + SEPARATION) + SEPARATION;

        int totalHeight = furnacePanelHeight + playerPanelHeight + SEPARATION;
        int furnaceX = screenWidth / 2 - furnacePanelWidth / 2;
        int furnaceY = screenHeight / 2 - totalHeight / 2;
        int playerX = screenWidth / 2 - playerPanelWidth / 2;
        int playerY = furnaceY + furnacePanelHeight + SEPARATION;

        // Check furnace panel
        if (mouseX >= furnaceX && mouseX <= furnaceX + furnacePanelWidth &&
                mouseY >= furnaceY && mouseY <= furnaceY + furnacePanelHeight) {
            mc.sayda.mcraze.util.Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
                    mouseX - furnaceX, mouseY - furnaceY,
                    TILE_SIZE, SEPARATION,
                    SEPARATION, SEPARATION,
                    3, 2);
            if (slot != null && slot.x < 3 && slot.y < 2) {
                return furnaceUI.getFurnaceItems()[slot.x][slot.y];
            }
        }

        // Check player panel
        if (mouseX >= playerX && mouseX <= playerX + playerPanelWidth &&
                mouseY >= playerY && mouseY <= playerY + playerPanelHeight) {
            mc.sayda.mcraze.util.Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
                    mouseX - playerX, mouseY - playerY,
                    TILE_SIZE, SEPARATION,
                    SEPARATION, SEPARATION,
                    10, 4);
            if (slot != null) {
                int invRow = (slot.y < 3) ? (slot.y + 3) : 6;
                Inventory inv = furnaceUI.getPlayerInventory();
                if (inv != null && slot.x < inv.inventoryItems.length) {
                    return inv.inventoryItems[slot.x][invRow];
                }
            }
        }

        return null;
    }

    public void render(GraphicsHandler g, int screenWidth, int screenHeight,
            Inventory playerInventory) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        if (!furnaceUI.isVisible()) {
            return;
        }

        // Calculate positions
        int furnacePanelWidth = 3 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int furnacePanelHeight = 2 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelHeight = 4 * (TILE_SIZE + SEPARATION) + SEPARATION;

        int totalHeight = furnacePanelHeight + playerPanelHeight + SEPARATION;
        int furnaceX = screenWidth / 2 - furnacePanelWidth / 2;
        int furnaceY = screenHeight / 2 - totalHeight / 2;
        int playerX = screenWidth / 2 - playerPanelWidth / 2;
        int playerY = furnaceY + furnacePanelHeight + SEPARATION;

        // 1. Render Furnace Panel
        g.setColor(Color.darkGray);
        g.fillRect(furnaceX, furnaceY, furnacePanelWidth, furnacePanelHeight);

        // Render furnace slots
        renderFurnaceSlots(g, furnaceX, furnaceY);

        // Render progress indicators
        renderProgress(g, furnaceX, furnaceY);

        // 2. Render Player Panel
        g.setColor(Color.gray);
        g.fillRect(playerX, playerY, playerPanelWidth, playerPanelHeight);

        // Render player inventory (same as ChestView)
        renderPlayerInventory(g, playerX, playerY, playerInventory);

        // 3. Render holding item
        if (playerInventory != null && playerInventory.holding != null && playerInventory.holding.count > 0) {
            playerInventory.holding.draw(g, playerInventory.holdingX, playerInventory.holdingY, TILE_SIZE);
        }

        // 4. Render tooltip
        if (tooltip != null) {
            tooltip.updateLayout(screenWidth, screenHeight);
            tooltip.draw(g);
        }
    }

    private void renderFurnaceSlots(GraphicsHandler g, int furnaceX, int furnaceY) {
        InventoryItem[][] furnaceItems = furnaceUI.getFurnaceItems();

        // Input slot (0,0) - top left
        int inputX = furnaceX + SEPARATION;
        int inputY = furnaceY + SEPARATION;
        renderSlot(g, inputX, inputY, furnaceItems[0][0]);

        // Fuel slot (0,1) - bottom left
        int fuelX = furnaceX + SEPARATION;
        int fuelY = furnaceY + SEPARATION + (TILE_SIZE + SEPARATION);
        renderSlot(g, fuelX, fuelY, furnaceItems[0][1]);

        // Output slot (2,0) - top right
        int outputX = furnaceX + SEPARATION + 2 * (TILE_SIZE + SEPARATION);
        int outputY = furnaceY + SEPARATION;
        renderSlot(g, outputX, outputY, furnaceItems[2][0]);
    }

    private void renderSlot(GraphicsHandler g, int x, int y, InventoryItem item) {
        // Slot border
        g.setColor(Color.darkGray);
        g.fillRect(x - 2, y - 2, TILE_SIZE + 4, TILE_SIZE + 4);
        g.setColor(Color.lightGray);
        g.fillRect(x, y, TILE_SIZE, TILE_SIZE);

        // Draw item if present
        if (item != null && item.item != null && item.count > 0) {
            item.draw(g, x, y, TILE_SIZE);
        }
    }

    private void renderProgress(GraphicsHandler g, int furnaceX, int furnaceY) {
        // Progress arrow (middle column, top row)
        int arrowX = furnaceX + SEPARATION + (TILE_SIZE + SEPARATION);
        int arrowY = furnaceY + SEPARATION;

        // Draw progress bar background
        g.setColor(Color.gray);
        g.fillRect(arrowX, arrowY + TILE_SIZE / 2 - 2, TILE_SIZE, 4);

        // Draw progress fill
        if (furnaceUI.getSmeltTimeTotal() > 0) {
            float progress = (float) furnaceUI.getSmeltProgress() / furnaceUI.getSmeltTimeTotal();
            int fillWidth = (int) (TILE_SIZE * progress);
            g.setColor(Color.orange);
            g.fillRect(arrowX, arrowY + TILE_SIZE / 2 - 2, fillWidth, 4);
        }

        // Flame icon (middle column, bottom row)
        int flameX = arrowX;
        int flameY = furnaceY + SEPARATION + (TILE_SIZE + SEPARATION);

        // Draw flame indicator
        if (furnaceUI.getFuelTimeRemaining() > 0) {
            g.setColor(Color.orange);
            g.fillRect(flameX + 4, flameY + 4, 8, 8); // Simple flame representation
        } else {
            g.setColor(Color.darkGray);
            g.fillRect(flameX + 4, flameY + 4, 8, 8); // Unlit
        }
    }

    private void renderPlayerInventory(GraphicsHandler g, int playerX, int playerY, Inventory playerInventory) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 10; j++) {
                int sx = playerX + SEPARATION + j * (TILE_SIZE + SEPARATION);
                int sy = playerY + SEPARATION + i * (TILE_SIZE + SEPARATION);

                // Map to player inventory rows (3-6)
                int invRow = (i < 3) ? (i + 3) : 6;

                // Highlight selected hotbar slot
                if (invRow == 6 && j == playerInventory.hotbarIdx) {
                    g.setColor(Color.blue);
                    g.fillRect(sx - 4, sy - 4, TILE_SIZE + 8, TILE_SIZE + 8);
                } else {
                    g.setColor(Color.darkGray);
                    g.fillRect(sx - 2, sy - 2, TILE_SIZE + 4, TILE_SIZE + 4);
                }
                g.setColor(Color.lightGray);
                g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

                // Draw item
                if (playerInventory != null && j < playerInventory.inventoryItems.length) {
                    InventoryItem item = playerInventory.inventoryItems[j][invRow];
                    if (item != null && item.item != null && item.count > 0) {
                        item.draw(g, sx, sy, TILE_SIZE);
                    }
                }
            }
        }
    }
}
