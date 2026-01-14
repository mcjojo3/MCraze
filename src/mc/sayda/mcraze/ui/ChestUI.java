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
	private InventoryItem[][] chestItems = new InventoryItem[9][3]; // 9 wide, 3 tall

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
	 * Get chest items array (for rendering)
	 */
	public InventoryItem[][] getChestItems() {
		return chestItems;
	}

	/**
	 * Get player inventory reference
	 */
	public Inventory getPlayerInventory() {
		return playerInventory;
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

		// Update holding item position for cursor rendering
		if (playerInventory != null) {
			playerInventory.holdingX = mousePos.x - tileSize / 2;
			playerInventory.holdingY = mousePos.y - tileSize / 2;
		}

		// Calculate chest panel dimensions (9 wide x 3 tall)
		int chestPanelWidth = 10 * (tileSize + separation) + separation;
		int chestPanelHeight = 3 * (tileSize + separation) + separation;

		// Calculate player inventory panel dimensions (9 wide x 4 tall)
		int playerPanelWidth = 10 * (tileSize + separation) + separation;
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
				if (slot != null && slot.x < 10 && slot.y < 4) {
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
	 * Uses unified SlotCoordinateHelper for gap-aware hit detection.
	 */
	private Int2 mouseToSlot(int relX, int relY, int separation, int tileSize) {
		return mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
				relX, relY,
				tileSize, separation,
				separation, separation,
				10, 4); // ChestUI handles grids up to 9x4 (Chest 9x3, Player Inv 9x4)
	}

}
