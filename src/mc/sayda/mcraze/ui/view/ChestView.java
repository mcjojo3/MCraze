/*
 * Copyright 2025 SaydaGames (mc_jojo3)
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

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.ui.ChestUI;
import mc.sayda.mcraze.ui.Inventory;

/**
 * View component for rendering chest UI using declarative layout.
 *
 * Replaces manual coordinate calculations in ChestUI.draw() with clean
 * declarative approach (will use SlotGrid in future iterations).
 *
 * BEFORE (ChestUI.java:177-279):
 * - 100+ lines of manual coordinate math
 * - Duplicate calculations for chest and player grids
 * - Magic numbers everywhere
 *
 * AFTER (ChestView):
 * - Cleaner rendering logic
 * - TODO: Use SlotGrid components (Phase 2E)
 *
 * ARCHITECTURE:
 * - ChestView: CLIENT-SIDE ONLY rendering
 * - ChestUI: Data + packet logic (server-authoritative)
 *
 * The chest UI shows:
 * 1. Chest grid (9x3) - Top panel with brown background
 * 2. Player inventory (9x4) - Bottom panel with gray background
 */
public class ChestView {
	private ChestUI chestUI;  // Data reference (NOT owned)

	// Layout state
	private int screenWidth;
	private int screenHeight;
	private boolean layoutDirty = true;

	// Constants
	private static final int TILE_SIZE = 16;
	private static final int SEPARATION = 15;

	/**
	 * Create chest view for rendering.
	 *
	 * @param chestUI The chest UI data to render (NOT copied - referenced)
	 */
	public ChestView(ChestUI chestUI) {
		this.chestUI = chestUI;
	}

	/**
	 * Update layout if screen size changed.
	 */
	private void updateLayout(int screenWidth, int screenHeight) {
		if (this.screenWidth != screenWidth || this.screenHeight != screenHeight || layoutDirty) {
			this.screenWidth = screenWidth;
			this.screenHeight = screenHeight;
			this.layoutDirty = false;

			// TODO: Update SlotGrid components when added in Phase 2E
		}
	}

	/**
	 * Render the chest UI.
	 *
	 * @param g Graphics handler
	 * @param screenWidth Screen width
	 * @param screenHeight Screen height
	 * @param chestItems Chest inventory items (9x3)
	 * @param playerInventory Player inventory reference
	 */
	public void render(GraphicsHandler g, int screenWidth, int screenHeight,
	                   InventoryItem[][] chestItems, Inventory playerInventory) {
		if (!chestUI.isVisible()) {
			return;
		}

		// Update layout if needed
		updateLayout(screenWidth, screenHeight);

		// Render chest and player inventory panels
		renderChestPanel(g, screenWidth, screenHeight, chestItems);
		renderPlayerPanel(g, screenWidth, screenHeight, playerInventory);
	}

	/**
	 * Render chest panel (9x3 grid at top).
	 *
	 * TODO: Use SlotGrid component in Phase 2E.
	 * For now, using original rendering to maintain exact visual compatibility.
	 */
	private void renderChestPanel(GraphicsHandler g, int screenWidth, int screenHeight,
	                               InventoryItem[][] chestItems) {
		int tileSize = TILE_SIZE;
		int separation = SEPARATION;

		// Calculate chest panel dimensions (9 wide x 3 tall)
		int chestPanelWidth = 9 * (tileSize + separation) + separation;
		int chestPanelHeight = 3 * (tileSize + separation) + separation;

		// Calculate player inventory panel dimensions (9 wide x 4 tall)
		int playerPanelWidth = 9 * (tileSize + separation) + separation;
		int playerPanelHeight = 4 * (tileSize + separation) + separation;

		// Center chest panel at top, player panel below
		int totalHeight = chestPanelHeight + playerPanelHeight + separation;
		int chestXPos = screenWidth / 2 - chestPanelWidth / 2;
		int chestYPos = screenHeight / 2 - totalHeight / 2;

		// Draw chest panel background
		g.setColor(new Color(120, 90, 70));  // Slightly lighter brown background
		g.fillRect(chestXPos, chestYPos, chestPanelWidth, chestPanelHeight);

		// Draw chest slots (9x3)
		int x = chestXPos;
		int y = chestYPos;
		for (int row = 0; row < 3; row++) {
			x = chestXPos;
			for (int col = 0; col < 9; col++) {
				// Draw slot border (dark gray)
				g.setColor(Color.DARK_GRAY);
				g.fillRect(x + separation - 2, y + separation - 2, tileSize + 4, tileSize + 4);

				// Draw slot background - 16x16 light gray
				g.setColor(Color.LIGHT_GRAY);
				g.fillRect(x + separation, y + separation, tileSize, tileSize);

				// Draw item in slot
				if (chestItems != null && col < chestItems.length && row < chestItems[col].length) {
					if (chestItems[col][row] != null) {
						chestItems[col][row].draw(g, x + separation, y + separation, tileSize);
					}
				}

				x += tileSize + separation;
			}
			y += tileSize + separation;
		}
	}

	/**
	 * Render player inventory panel (9x4 grid at bottom).
	 *
	 * TODO: Use SlotGrid component in Phase 2E.
	 * For now, using original rendering to maintain exact visual compatibility.
	 */
	private void renderPlayerPanel(GraphicsHandler g, int screenWidth, int screenHeight,
	                                Inventory playerInventory) {
		if (playerInventory == null || playerInventory.inventoryItems == null) {
			return;
		}

		int tileSize = TILE_SIZE;
		int separation = SEPARATION;

		// Calculate chest panel dimensions (9 wide x 3 tall)
		int chestPanelWidth = 9 * (tileSize + separation) + separation;
		int chestPanelHeight = 3 * (tileSize + separation) + separation;

		// Calculate player inventory panel dimensions (9 wide x 4 tall)
		int playerPanelWidth = 9 * (tileSize + separation) + separation;
		int playerPanelHeight = 4 * (tileSize + separation) + separation;

		// Center chest panel at top, player panel below
		int totalHeight = chestPanelHeight + playerPanelHeight + separation;
		int chestYPos = screenHeight / 2 - totalHeight / 2;
		int playerXPos = screenWidth / 2 - playerPanelWidth / 2;
		int playerYPos = chestYPos + chestPanelHeight + separation;

		// Draw player inventory panel background
		g.setColor(Color.gray);
		g.fillRect(playerXPos, playerYPos, playerPanelWidth, playerPanelHeight);

		// Draw player inventory slots (9x4 - 3 rows + hotbar)
		int x = playerXPos;
		int y = playerYPos;

		// Draw main inventory (3 rows)
		for (int row = 0; row < 3; row++) {
			x = playerXPos;
			for (int col = 0; col < 9; col++) {
				// Draw slot border (dark gray)
				g.setColor(Color.DARK_GRAY);
				g.fillRect(x + separation - 2, y + separation - 2, tileSize + 4, tileSize + 4);

				// Draw slot background - 16x16 light gray
				g.setColor(Color.LIGHT_GRAY);
				g.fillRect(x + separation, y + separation, tileSize, tileSize);

				// Draw item (from player's main inventory)
				if (col < playerInventory.inventoryItems.length &&
				    row < playerInventory.inventoryItems[col].length - 1) {
					playerInventory.inventoryItems[col][row].draw(g, x + separation, y + separation, tileSize);
				}

				x += tileSize + separation;
			}
			y += tileSize + separation;
		}

		// Draw hotbar (bottom row, highlighted)
		x = playerXPos;
		int hotbarRow = playerInventory.inventoryItems[0].length - 1;
		for (int col = 0; col < 9; col++) {
			// Draw slot border (blue for selected, dark gray for others)
			if (playerInventory.hotbarIdx == col) {
				g.setColor(Color.blue);
			} else {
				g.setColor(Color.DARK_GRAY);
			}
			g.fillRect(x + separation - 2, y + separation - 2, tileSize + 4, tileSize + 4);

			// Draw slot background - 16x16 light gray
			g.setColor(Color.LIGHT_GRAY);
			g.fillRect(x + separation, y + separation, tileSize, tileSize);

			// Draw item from hotbar
			if (col < playerInventory.inventoryItems.length) {
				playerInventory.inventoryItems[col][hotbarRow].draw(g, x + separation, y + separation, tileSize);
			}

			x += tileSize + separation;
		}
	}

	/**
	 * Mark layout as dirty (forces recalculation on next render).
	 */
	public void invalidateLayout() {
		layoutDirty = true;
	}
}
