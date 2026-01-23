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

package mc.sayda.mcraze.ui.view;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.ui.container.AlchemyUI;
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.ui.component.Tooltip;
import mc.sayda.mcraze.util.Int2;

/**
 * View component for rendering Alchemy Table UI.
 */
public class AlchemyView {
    private AlchemyUI alchemyUI;
    private Tooltip tooltip;

    // Constants
    private static final int TILE_SIZE = 16;
    private static final int SEPARATION = 15;

    // Layout state
    private int screenWidth;
    private int screenHeight;

    public AlchemyView(AlchemyUI alchemyUI) {
        this.alchemyUI = alchemyUI;
        this.tooltip = new Tooltip();
    }

    public void render(GraphicsHandler g, int screenWidth, int screenHeight,
            InventoryItem[] alchemySlots, int essence, int progress, int maxProgress, Inventory playerInventory) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;

        if (!alchemyUI.isVisible()) {
            return;
        }

        // Calculate positions
        int alchemyPanelWidth = 5 * (TILE_SIZE + SEPARATION) + SEPARATION; // Enough for 3 inputs, gap, output
        int alchemyPanelHeight = 3 * (TILE_SIZE + SEPARATION) + SEPARATION; // Enough for 2 rows + essence text
        int playerPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelHeight = 4 * (TILE_SIZE + SEPARATION) + SEPARATION;

        int totalHeight = alchemyPanelHeight + playerPanelHeight + SEPARATION;
        int alchemyX = screenWidth / 2 - alchemyPanelWidth / 2;
        int alchemyY = screenHeight / 2 - totalHeight / 2;
        int playerX = screenWidth / 2 - playerPanelWidth / 2;
        int playerY = alchemyY + alchemyPanelHeight + SEPARATION;

        // 1. Render Alchemy Panel
        g.setColor(new Color(100, 50, 150)); // Purple-ish for Alchemy
        g.fillRect(alchemyX, alchemyY, alchemyPanelWidth, alchemyPanelHeight);

        // Draw "Alchemy Table" text
        // g.setColor(Color.white);
        // g.drawString("Alchemy Table", alchemyX + SEPARATION, alchemyY + SEPARATION -
        // 5);

        // Draw essence info
        // g.setColor(Color.white);
        // g.drawString("Pool: " + essence, alchemyX + SEPARATION, alchemyY +
        // alchemyPanelHeight - 20);

        if (alchemyUI.recipeCost > 0) {
            boolean canAfford = essence >= alchemyUI.recipeCost;
            g.setColor(canAfford ? Color.white : Color.red);
            g.drawString("Cost: " + alchemyUI.recipeCost, alchemyX + SEPARATION, alchemyY + alchemyPanelHeight - 12);
        } else {
            g.setColor(Color.lightGray);
            g.drawString("No Recipe", alchemyX + SEPARATION, alchemyY + alchemyPanelHeight - 12);
        }

        // Draw Progress Bar
        int barWidth = 48;
        int barHeight = 10;
        int barX = alchemyX + SEPARATION + 1 * (TILE_SIZE + SEPARATION);
        int barY = alchemyY + SEPARATION + TILE_SIZE + SEPARATION + 13;

        g.setColor(Color.darkGray);
        g.fillRect(barX, barY, barWidth, barHeight);
        if (maxProgress > 0) {
            int fillWidth = (int) ((progress / (float) maxProgress) * barWidth);
            g.setColor(Color.magenta);
            g.fillRect(barX, barY, fillWidth, barHeight);
        }

        // Draw Slots
        // 0, 1, 2 (Ingredients)
        for (int i = 0; i < 3; i++) {
            int sx = alchemyX + SEPARATION + i * (TILE_SIZE + SEPARATION);
            int sy = alchemyY + SEPARATION + 10;
            drawSlot(g, sx, sy, alchemySlots[i]);
        }

        // 3 (Fuel/Bottle) - Bottom Left
        int fX = alchemyX + SEPARATION;
        int fY = alchemyY + SEPARATION + TILE_SIZE + SEPARATION + 10;
        drawSlot(g, fX, fY, alchemySlots[3]);
        // g.setColor(new Color(255, 255, 255, 100));
        // g.drawString("Bottle", fX, fY + TILE_SIZE + 10);

        // 4 (Result) - Right side of progress bar
        int rX = alchemyX + alchemyPanelWidth - SEPARATION - TILE_SIZE;
        int rY = alchemyY + SEPARATION + TILE_SIZE / 2 + 10;
        drawSlot(g, rX, rY, alchemySlots[4]);

        // 2. Render Player Panel
        g.setColor(Color.gray);
        g.fillRect(playerX, playerY, playerPanelWidth, playerPanelHeight);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 10; j++) {
                int sx = playerX + SEPARATION + j * (TILE_SIZE + SEPARATION);
                int sy = playerY + SEPARATION + i * (TILE_SIZE + SEPARATION);

                int invRow = (i < 3) ? (i + 3) : 6;
                if (invRow == 6 && j == playerInventory.hotbarIdx) {
                    g.setColor(Color.blue);
                    g.fillRect(sx - 4, sy - 4, TILE_SIZE + 8, TILE_SIZE + 8);
                } else {
                    g.setColor(Color.darkGray);
                    g.fillRect(sx - 2, sy - 2, TILE_SIZE + 4, TILE_SIZE + 4);
                }
                g.setColor(Color.lightGray);
                g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

                if (playerInventory != null && j < playerInventory.inventoryItems.length) {
                    InventoryItem item = playerInventory.inventoryItems[j][invRow];
                    item.draw(g, sx, sy, TILE_SIZE);
                }
            }
        }

        // 3. Render holding item
        if (playerInventory != null && playerInventory.holding != null && !playerInventory.holding.isEmpty()) {
            playerInventory.holding.draw(g, playerInventory.holdingX, playerInventory.holdingY, TILE_SIZE);
        }

        // 4. Render Tooltip
        if (tooltip != null) {
            tooltip.updateLayout(screenWidth, screenHeight);
            tooltip.draw(g);
        }
    }

    private void drawSlot(GraphicsHandler g, int x, int y, InventoryItem item) {
        g.setColor(Color.darkGray);
        g.fillRect(x - 2, y - 2, TILE_SIZE + 4, TILE_SIZE + 4);
        g.setColor(Color.lightGray);
        g.fillRect(x, y, TILE_SIZE, TILE_SIZE);

        if (item != null && item.item != null && item.count > 0) {
            item.draw(g, x, y, TILE_SIZE);
        }
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
        // Logic similar to render for panel positions
        int alchemyPanelWidth = 5 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int alchemyPanelHeight = 3 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
        int playerPanelHeight = 4 * (TILE_SIZE + SEPARATION) + SEPARATION;

        int totalHeight = alchemyPanelHeight + playerPanelHeight + SEPARATION;
        int alchemyX = screenWidth / 2 - alchemyPanelWidth / 2;
        int alchemyY = screenHeight / 2 - totalHeight / 2;
        int playerX = screenWidth / 2 - playerPanelWidth / 2;
        int playerY = alchemyY + alchemyPanelHeight + SEPARATION;

        // Check Alchemy Slots
        InventoryItem[] slots = alchemyUI.getAlchemySlots();

        // Ingredients
        for (int i = 0; i < 3; i++) {
            int sx = alchemyX + SEPARATION + i * (TILE_SIZE + SEPARATION);
            int sy = alchemyY + SEPARATION + 10;
            if (mouseX >= sx && mouseX <= sx + TILE_SIZE && mouseY >= sy && mouseY <= sy + TILE_SIZE) {
                return slots[i];
            }
        }

        // Bottle
        int fX = alchemyX + SEPARATION;
        int fY = alchemyY + SEPARATION + TILE_SIZE + SEPARATION + 10;
        if (mouseX >= fX && mouseX <= fX + TILE_SIZE && mouseY >= fY && mouseY <= fY + TILE_SIZE) {
            return slots[3];
        }

        // Result
        int rX = alchemyX + alchemyPanelWidth - SEPARATION - TILE_SIZE;
        int rY = alchemyY + SEPARATION + TILE_SIZE / 2 + 10;
        if (mouseX >= rX && mouseX <= rX + TILE_SIZE && mouseY >= rY && mouseY <= rY + TILE_SIZE) {
            return slots[4];
        }

        // Check player panel
        if (mouseX >= playerX && mouseX <= playerX + playerPanelWidth && mouseY >= playerY
                && mouseY <= playerY + playerPanelHeight) {
            Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
                    mouseX - playerX, mouseY - playerY,
                    TILE_SIZE, SEPARATION,
                    SEPARATION, SEPARATION,
                    10, 4);
            if (slot != null) {
                int invRow = (slot.y < 3) ? (slot.y + 3) : 6;
                return alchemyUI.getPlayerInventory().inventoryItems[slot.x][invRow];
            }
        }

        return null;
    }
}
