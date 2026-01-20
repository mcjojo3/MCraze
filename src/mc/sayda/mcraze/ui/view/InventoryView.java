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
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.ui.component.Tooltip;
import mc.sayda.mcraze.util.Int2;

/**
 * Legacy view component for rendering inventory UI using manual loops.
 */
public class InventoryView {
	private Inventory inventory;

	// Tooltip support
	private Tooltip tooltip;

	// Constants
	private static final int TILE_SIZE = 16;
	private static final int SEPARATION = 15;

	// Layout state
	private int screenWidth;
	private int screenHeight;
	private boolean layoutDirty = true;

	public InventoryView(Inventory inventory) {
		this.inventory = inventory;
		this.tooltip = new Tooltip();
	}

	public Inventory getInventory() {
		return inventory;
	}

	public void render(GraphicsHandler g, int screenWidth, int screenHeight) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;

		// 1. Render hotbar (always visible)
		renderHotbar(g, screenWidth, screenHeight);

		// 2. Render full inventory (if visible)
		if (inventory.isVisible()) {
			renderFullInventory(g, screenWidth, screenHeight);
		}

		// 3. Render holding item (follows cursor)
		renderHoldingItem(g);

		// 4. Render tooltip
		if (tooltip != null) {
			tooltip.updateLayout(screenWidth, screenHeight);
			tooltip.draw(g);
		}
	}

	private void renderHotbar(GraphicsHandler g, int screenWidth, int screenHeight) {
		int separation = 10;
		int panelWidth = inventory.inventoryItems.length * (TILE_SIZE + separation) + separation;
		int panelHeight = TILE_SIZE + separation * 2;
		int x = screenWidth / 2 - panelWidth / 2;
		int y = screenHeight - panelHeight - separation;

		// Draw hotbar background panel
		g.setColor(Color.gray);
		g.fillRect(x, y, panelWidth, panelHeight);

		// Draw hotbar slots
		int playerRow = inventory.inventoryItems[0].length - 1;
		for (int j = 0; j < inventory.inventoryItems.length; j++) {
			InventoryItem current = inventory.inventoryItems[j][playerRow];

			// Draw slot border (blue for selected, dark gray for unselected)
			if (inventory.hotbarIdx == j) {
				g.setColor(Color.blue);
				g.fillRect(x + separation - 4, y + separation - 4, TILE_SIZE + 8, TILE_SIZE + 8);
			} else {
				g.setColor(Color.darkGray);
				g.fillRect(x + separation - 2, y + separation - 2, TILE_SIZE + 4, TILE_SIZE + 4);
			}

			// Draw slot background
			g.setColor(Color.lightGray);
			g.fillRect(x + separation, y + separation, TILE_SIZE, TILE_SIZE);

			// Draw item
			current.draw(g, x + separation, y + separation, TILE_SIZE);
			x += TILE_SIZE + separation;
		}
	}

	private void renderFullInventory(GraphicsHandler g, int screenWidth, int screenHeight) {
		int panelWidth = inventory.inventoryItems.length * (TILE_SIZE + SEPARATION) + SEPARATION;
		int panelHeight = inventory.inventoryItems[0].length * (TILE_SIZE + SEPARATION) + SEPARATION;
		int centerX = screenWidth / 2 - panelWidth / 2;
		int centerY = screenHeight / 2 - panelHeight / 2;

		// Draw main panel background
		g.setColor(Color.gray);
		g.fillRect(centerX, centerY, panelWidth, panelHeight);

		// Draw crafting area background (darker)
		g.setColor(Color.darkGray);
		g.fillRect(centerX + panelWidth - inventory.tableSizeAvailable * (TILE_SIZE + SEPARATION) - SEPARATION, centerY,
				inventory.tableSizeAvailable * (TILE_SIZE + SEPARATION) + SEPARATION,
				inventory.tableSizeAvailable * (TILE_SIZE + SEPARATION) + SEPARATION);

		// Determine hotbar row index
		int hotbarRowIndex = inventory.inventoryItems[0].length - 1;

		// Draw all inventory slots
		for (int i = 0; i < inventory.inventoryItems[0].length; i++) {
			int x = centerX;
			int y = centerY + i * (TILE_SIZE + SEPARATION);

			for (int j = 0; j < inventory.inventoryItems.length; j++) {
				// Skip crafting grid slots and gap
				if ((i < inventory.craftingHeight && j < inventory.inventoryItems.length - inventory.tableSizeAvailable)
						|| (inventory.craftingHeight != inventory.tableSizeAvailable
								&& i == inventory.tableSizeAvailable)) {
					x += TILE_SIZE + SEPARATION;
					continue;
				}

				// Draw slot border (blue for selected hotbar, dark gray for others)
				if (i == hotbarRowIndex && j == inventory.hotbarIdx) {
					g.setColor(Color.blue);
					g.fillRect(x + SEPARATION - 4, y + SEPARATION - 4, TILE_SIZE + 8, TILE_SIZE + 8);
				} else {
					g.setColor(Color.darkGray);
					g.fillRect(x + SEPARATION - 2, y + SEPARATION - 2, TILE_SIZE + 4, TILE_SIZE + 4);
				}

				// Draw slot background
				g.setColor(Color.lightGray);
				g.fillRect(x + SEPARATION, y + SEPARATION, TILE_SIZE, TILE_SIZE);

				// Draw item
				InventoryItem current = inventory.inventoryItems[j][i];
				current.draw(g, x + SEPARATION, y + SEPARATION, TILE_SIZE);
				x += TILE_SIZE + SEPARATION;
			}
		}

		// Draw crafting output slot (orange highlight)
		int outputX = centerX
				+ (inventory.inventoryItems.length - inventory.tableSizeAvailable - 1) * (TILE_SIZE + SEPARATION);
		int outputY = centerY + SEPARATION * 2 + TILE_SIZE;

		g.setColor(Color.orange);
		g.fillRect(outputX - 5, outputY - 5, TILE_SIZE + 10, TILE_SIZE + 10);
		g.setColor(Color.lightGray);
		g.fillRect(outputX, outputY, TILE_SIZE, TILE_SIZE);

		// Draw craftable item
		inventory.getCraftable().draw(g, outputX, outputY, TILE_SIZE);
	}

	private void renderHoldingItem(GraphicsHandler g) {
		if (inventory.holding != null && !inventory.holding.isEmpty()) {
			inventory.holding.draw(g, inventory.holdingX, inventory.holdingY, TILE_SIZE);
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

	public InventoryItem getItemAtMouse(int mouseX, int mouseY) {
		// Check hotbar
		int separation = 10;
		int panelWidth = inventory.inventoryItems.length * (TILE_SIZE + separation) + separation;
		int panelHeight = TILE_SIZE + separation * 2;
		int xStart = screenWidth / 2 - panelWidth / 2;
		int yStart = screenHeight - panelHeight - separation;

		if (mouseX >= xStart && mouseX <= xStart + panelWidth && mouseY >= yStart && mouseY <= yStart + panelHeight) {
			Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
					mouseX - xStart, mouseY - yStart,
					TILE_SIZE, separation,
					separation, separation,
					inventory.inventoryItems.length, 1);
			if (slot != null) {
				int playerRow = inventory.inventoryItems[0].length - 1;
				return inventory.inventoryItems[slot.x][playerRow];
			}
		}

		// Check full inventory
		if (inventory.isVisible()) {
			panelWidth = inventory.inventoryItems.length * (TILE_SIZE + SEPARATION) + SEPARATION;
			panelHeight = inventory.inventoryItems[0].length * (TILE_SIZE + SEPARATION) + SEPARATION;
			xStart = screenWidth / 2 - panelWidth / 2;
			yStart = screenHeight / 2 - panelHeight / 2;

			if (mouseX >= xStart && mouseX <= xStart + panelWidth && mouseY >= yStart
					&& mouseY <= yStart + panelHeight) {
				Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
						mouseX - xStart, mouseY - yStart,
						TILE_SIZE, SEPARATION,
						SEPARATION, SEPARATION,
						inventory.inventoryItems.length, inventory.inventoryItems[0].length);
				if (slot != null) {
					// Area checks for gaps/crafting
					if ((slot.y < inventory.craftingHeight
							&& slot.x < inventory.inventoryItems.length - inventory.tableSizeAvailable)
							|| (inventory.craftingHeight != inventory.tableSizeAvailable
									&& slot.y == inventory.tableSizeAvailable)) {
						return null;
					}
					return inventory.inventoryItems[slot.x][slot.y];
				}
			}
		}

		return null;
	}

	public void invalidateLayout() {
		layoutDirty = true;
	}
}
