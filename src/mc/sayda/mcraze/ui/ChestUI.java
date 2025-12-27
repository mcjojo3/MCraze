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

package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketChestAction;
import mc.sayda.mcraze.util.Int2;

/**
 * UI for chest inventory - shows 9x3 chest grid above player inventory
 * No crafting functionality (simplified from Inventory)
 */
public class ChestUI {
	private boolean visible = false;
	private int chestX;
	private int chestY;
	private Inventory playerInventory;

	// Display grids (references to chest data and player inventory)
	private InventoryItem[][] chestItems = new InventoryItem[9][3];  // 9 wide, 3 tall

	public ChestUI() {
		// Initialize empty chest items
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 3; j++) {
				chestItems[i][j] = new InventoryItem(null);
			}
		}
	}

	/**
	 * Open the chest UI at the given position
	 */
	public void open(int chestX, int chestY, Inventory playerInventory) {
		this.chestX = chestX;
		this.chestY = chestY;
		this.playerInventory = playerInventory;
		this.visible = true;
	}

	/**
	 * Close the chest UI
	 */
	public void close() {
		this.visible = false;
	}

	/**
	 * Check if chest UI is visible
	 */
	public boolean isVisible() {
		return visible;
	}

	/**
	 * Get chest X position
	 */
	public int getChestX() {
		return chestX;
	}

	/**
	 * Get chest Y position
	 */
	public int getChestY() {
		return chestY;
	}

	/**
	 * Update chest inventory items from packet
	 */
	public void updateChestItems(InventoryItem[][] items) {
		if (items != null && items.length == 9 && items[0].length == 3) {
			this.chestItems = items;
		}
	}

	/**
	 * Handle mouse input for chest UI
	 * Returns true if mouse is over the chest UI
	 */
	public boolean update(int screenWidth, int screenHeight, Int2 mousePos,
	                      boolean leftClick, boolean rightClick, Connection connection) {
		if (!visible) {
			return false;
		}

		int tileSize = 16;
		int separation = 15;

		// Calculate chest panel dimensions (9 wide x 3 tall)
		int chestPanelWidth = 9 * (tileSize + separation) + separation;
		int chestPanelHeight = 3 * (tileSize + separation) + separation;

		// Calculate player inventory panel dimensions (9 wide x 4 tall)
		int playerPanelWidth = 9 * (tileSize + separation) + separation;
		int playerPanelHeight = 4 * (tileSize + separation) + separation;

		// Center chest panel at top, player panel below
		int totalHeight = chestPanelHeight + playerPanelHeight + separation;
		int chestX = screenWidth / 2 - chestPanelWidth / 2;
		int chestY = screenHeight / 2 - totalHeight / 2;
		int playerX = screenWidth / 2 - playerPanelWidth / 2;
		int playerY = chestY + chestPanelHeight + separation;

		// Check if mouse is over chest panel
		if (mousePos.x >= chestX && mousePos.x <= chestX + chestPanelWidth &&
		    mousePos.y >= chestY && mousePos.y <= chestY + chestPanelHeight) {

			if (leftClick || rightClick) {
				// Calculate clicked slot in chest
				Int2 slot = mouseToSlot(mousePos.x - chestX, mousePos.y - chestY, separation, tileSize);
				if (slot != null && slot.x < 9 && slot.y < 3) {
					// Send chest action packet (chest slot)
					if (connection != null) {
						PacketChestAction packet = new PacketChestAction(
							this.chestX, this.chestY, slot.x, slot.y, true, rightClick);
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
				if (slot != null && slot.x < 9 && slot.y < 4) {
					// Send chest action packet (player inventory slot)
					if (connection != null) {
						PacketChestAction packet = new PacketChestAction(
							this.chestX, this.chestY, slot.x, slot.y, false, rightClick);
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
	 */
	private Int2 mouseToSlot(int relX, int relY, int separation, int tileSize) {
		int slotX = relX / (separation + tileSize);
		int slotY = (relY / (separation + tileSize)) - 1;

		if (slotX < 0 || slotY < 0) {
			return null;
		}

		return new Int2(slotX, slotY);
	}

	/**
	 * Draw the chest UI
	 */
	public void draw(GraphicsHandler g, int screenWidth, int screenHeight) {
		if (!visible) {
			return;
		}

		int tileSize = 16;
		int separation = 15;

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
		int playerXPos = screenWidth / 2 - playerPanelWidth / 2;
		int playerYPos = chestYPos + chestPanelHeight + separation;

		// Draw chest panel background
		g.setColor(new Color(100, 80, 60));  // Brown background for chest
		g.fillRect(chestXPos, chestYPos, chestPanelWidth, chestPanelHeight);

		// Draw chest title
		g.setColor(Color.white);
		g.drawString("Chest", chestXPos + 10, chestYPos + 5);

		// Draw chest slots (9x3)
		int x = chestXPos + separation;
		int y = chestYPos + separation * 2;
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				// Draw slot background
				g.setColor(Color.LIGHT_GRAY);
				g.fillRect(x, y, tileSize, tileSize);

				// Draw item in slot
				if (chestItems[col][row] != null) {
					chestItems[col][row].draw(g, x, y, tileSize);
				}

				x += tileSize + separation;
			}
			x = chestXPos + separation;
			y += tileSize + separation;
		}

		// Draw player inventory panel background
		g.setColor(Color.gray);
		g.fillRect(playerXPos, playerYPos, playerPanelWidth, playerPanelHeight);

		// Draw player inventory title
		g.setColor(Color.white);
		g.drawString("Inventory", playerXPos + 10, playerYPos + 5);

		// Draw player inventory slots (9x4 - 3 rows + hotbar)
		if (playerInventory != null && playerInventory.inventoryItems != null) {
			x = playerXPos + separation;
			y = playerYPos + separation * 2;

			// Draw main inventory (3 rows)
			for (int row = 0; row < 3; row++) {
				for (int col = 0; col < 9; col++) {
					g.setColor(Color.LIGHT_GRAY);
					g.fillRect(x, y, tileSize, tileSize);

					// Draw item (from player's main inventory)
					if (col < playerInventory.inventoryItems.length &&
					    row < playerInventory.inventoryItems[col].length - 1) {
						playerInventory.inventoryItems[col][row].draw(g, x, y, tileSize);
					}

					x += tileSize + separation;
				}
				x = playerXPos + separation;
				y += tileSize + separation;
			}

			// Draw hotbar (bottom row, highlighted)
			int hotbarRow = playerInventory.inventoryItems[0].length - 1;
			for (int col = 0; col < 9; col++) {
				// Highlight selected hotbar slot
				if (playerInventory.hotbarIdx == col) {
					g.setColor(Color.blue);
					g.fillRect(x - 2, y - 2, tileSize + 4, tileSize + 4);
				}

				g.setColor(Color.LIGHT_GRAY);
				g.fillRect(x, y, tileSize, tileSize);

				// Draw item from hotbar
				if (col < playerInventory.inventoryItems.length) {
					playerInventory.inventoryItems[col][hotbarRow].draw(g, x, y, tileSize);
				}

				x += tileSize + separation;
			}
		}
	}
}
