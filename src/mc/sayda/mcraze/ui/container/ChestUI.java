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

package mc.sayda.mcraze.ui.container;

import mc.sayda.mcraze.ui.component.*;
import mc.sayda.mcraze.graphics.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.player.data.*;
import mc.sayda.mcraze.ui.DragHandler;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketChestAction;
import mc.sayda.mcraze.util.Int2;

/**
 * UI for chest inventory - shows 10x3 chest grid above player inventory
 * No crafting functionality (simplified from Inventory)
 */
public class ChestUI {
	private boolean visible = false;
	private int chestX;
	private int chestY;
	private Inventory playerInventory;

	// Display grids (references to chest data and player inventory)
	private InventoryItem[][] chestItems = new InventoryItem[10][3]; // 10 wide, 3 tall

	// Drag handler (shared implementation with other UIs)
	private DragHandler dragHandler = new DragHandler(
			mc.sayda.mcraze.network.packet.PacketInventoryDrag.ContainerType.CHEST);

	public ChestUI() {
		// Initialize empty chest items
		for (int i = 0; i < 10; i++) {
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
		if (items != null && items.length == 10 && items[0].length == 3) {
			this.chestItems = items;
		}
	}

	/**
	 * Handle mouse input for chest UI
	 * Returns true if mouse is over the chest UI
	 */
	public boolean update(int screenWidth, int screenHeight, Int2 mousePos,
			boolean leftClicked, boolean rightClicked, boolean leftHeld, boolean rightHeld,
			Connection connection, boolean shiftPressed) {
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

		// Calculate chest panel dimensions (10 wide x 3 tall)
		int chestPanelWidth = 10 * (tileSize + separation) + separation;
		int chestPanelHeight = 3 * (tileSize + separation) + separation;

		// Calculate player inventory panel dimensions (10 wide x 4 tall)
		int playerPanelWidth = 10 * (tileSize + separation) + separation;
		int playerPanelHeight = 4 * (tileSize + separation) + separation;

		// Center chest panel at top, player panel below
		int totalHeight = chestPanelHeight + playerPanelHeight + separation;
		int chestX = screenWidth / 2 - chestPanelWidth / 2;
		int chestY = screenHeight / 2 - totalHeight / 2;
		int playerX = screenWidth / 2 - playerPanelWidth / 2;
		int playerY = chestY + chestPanelHeight + separation;

		// Determine which slot is hovered (Unified Coordinates: Y 0-2 Chest, Y 3-6
		// Player)
		Int2 unifiedSlot = null;

		// Check Chest Panel
		if (mousePos.x >= chestX && mousePos.x <= chestX + chestPanelWidth &&
				mousePos.y >= chestY && mousePos.y <= chestY + chestPanelHeight) {
			Int2 slot = mouseToSlot(mousePos.x - chestX, mousePos.y - chestY, separation, tileSize);
			if (slot != null && slot.x < 10 && slot.y < 3) {
				unifiedSlot = new Int2(slot.x, slot.y);
			}
		}
		// Check Player Panel
		else if (mousePos.x >= playerX && mousePos.x <= playerX + playerPanelWidth &&
				mousePos.y >= playerY && mousePos.y <= playerY + playerPanelHeight) {
			Int2 slot = mouseToSlot(mousePos.x - playerX, mousePos.y - playerY, separation, tileSize);
			if (slot != null && slot.x < 10 && slot.y < 4) {
				unifiedSlot = new Int2(slot.x, slot.y + 3); // Map 0-3 -> 3-6
			}
		}

		// Check bounds (UI covers screen area even if no slot hovered)
		boolean overUI = (mousePos.x >= chestX && mousePos.x <= chestX + chestPanelWidth &&
				mousePos.y >= chestY && mousePos.y <= chestY + totalHeight);

		if (!overUI) {
			dragHandler.cancelDrag(connection);
			return false;
		}

		// Handle drag using DragHandler
		if (playerInventory != null && dragHandler.update(unifiedSlot, leftHeld, rightHeld, playerInventory.holding)) {
			return true; // Drag consumed the event
		}

		// Check if drag should end
		if (dragHandler.checkEndDrag(leftHeld, rightHeld, connection)) {
			return true;
		}

		// Clear potential drag if released without dragging
		dragHandler.clearPotentialDrag(leftHeld, rightHeld);

		// Handle Valid Click (use CLICKED state) - double-click REMOVED
		if (unifiedSlot != null && !dragHandler.isDragging() && (leftClicked || rightClicked)) {
			if (connection != null) {
				// Check cooldown
				if (!dragHandler.isOnCooldown(unifiedSlot)) {
					dragHandler.recordAction(unifiedSlot);

					// Decode unified slot back to container context
					boolean isChestSlot = unifiedSlot.y < 3;
					int localY = isChestSlot ? unifiedSlot.y : unifiedSlot.y - 3;

					if (shiftPressed && leftClicked) {
						mc.sayda.mcraze.network.packet.PacketShiftClick packet = new mc.sayda.mcraze.network.packet.PacketShiftClick(
								unifiedSlot.x, localY,
								mc.sayda.mcraze.network.packet.PacketShiftClick.ContainerType.CHEST,
								isChestSlot, 0);
						connection.sendPacket(packet);
					} else {
						PacketChestAction packet = new PacketChestAction(
								this.chestX, this.chestY, unifiedSlot.x, localY, isChestSlot, rightClicked);
						connection.sendPacket(packet);
					}
				}
			}
			return true;
		}

		return overUI;
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
				10, 4); // ChestUI handles grids up to 10x4 (Chest 10x3, Player Inv 10x4)
	}

}
