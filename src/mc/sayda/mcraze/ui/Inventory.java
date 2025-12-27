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

import java.util.Map;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.util.Int2;

import java.util.Map;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.util.Int2;

public class Inventory implements java.io.Serializable {
	private static final long serialVersionUID = 1L;
	
	public InventoryItem[][] inventoryItems;
	public int tableSizeAvailable = 2;
	public int hotbarIdx = 0;
	
	private int maxCount = 64;
	private int playerRow;
	private boolean visible = false;
	public InventoryItem holding = new InventoryItem(null);
	private int holdingX;
	private int holdingY;
	private Int2 clickPos = new Int2(0, 0);;
	public int craftingHeight;
	private InventoryItem craftable = new InventoryItem(null);
	
	public Inventory(int width, int height, int craftingHeight) {
		inventoryItems = new InventoryItem[width][height + craftingHeight];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height + craftingHeight; j++) {
				inventoryItems[i][j] = new InventoryItem(null);
			}
		}
		hotbarIdx = 0;
		playerRow = height + craftingHeight - 1;
		this.craftingHeight = craftingHeight;
	}
	
	/**
	 * Add items to inventory. Returns number of items that couldn't fit.
	 * @param item Item to add
	 * @param count Number of items to add
	 * @return Number of items that couldn't be added (0 if all added successfully)
	 */
	public int addItem(Item item, int count) {
		int itemsToGo = count;

		// Try hotbar first
		for (int i = 0; i < inventoryItems.length && itemsToGo > 0; i++) {
			itemsToGo = inventoryItems[i][playerRow].add(item, itemsToGo);
		}

		// Try rest of inventory (excluding crafting grid)
		for (int i = 0; i < inventoryItems.length && itemsToGo > 0; i++) {
			for (int j = 0; j < inventoryItems[0].length - 1 && itemsToGo > 0; j++) {
				// Skip crafting grid slots
				if ((j < craftingHeight && i < inventoryItems.length - tableSizeAvailable)
						|| (craftingHeight != tableSizeAvailable && j == tableSizeAvailable)) {
					continue;
				}
				itemsToGo = inventoryItems[i][j].add(item, itemsToGo);
			}
		}

		return itemsToGo;
	}
	
	public void decreaseSelected(int count) {
		inventoryItems[hotbarIdx][playerRow].remove(count);
	}
	
	public InventoryItem selectedItem() {
		return inventoryItems[hotbarIdx][playerRow];
	}
	
	// returns true if the mouse hit in the inventory
	public boolean updateInventory(int screenWidth, int screenHeight,
			Int2 mousePos, boolean leftClick, boolean rightClick) {
		return updateInventory(screenWidth, screenHeight, mousePos, leftClick, rightClick, null);
	}

	// Version with connection for packet sending
	public boolean updateInventory(int screenWidth, int screenHeight,
			Int2 mousePos, boolean leftClick, boolean rightClick, mc.sayda.mcraze.network.Connection connection) {
		if (!visible) {
			return false;
		}
		
		int tileSize = 16;
		int seperation = 15;
		
		int panelWidth = inventoryItems.length * (tileSize + seperation) + seperation;
		int panelHeight = inventoryItems[0].length * (tileSize + seperation) + seperation;
		int x = screenWidth / 2 - panelWidth / 2;
		int y = screenHeight / 2 - panelHeight / 2;
		
		if (mousePos.x < x || mousePos.x > x + panelWidth
				|| mousePos.y < y || mousePos.y > y + panelHeight) {
			return false;
		}
		
		holdingX = mousePos.x;
		holdingY = mousePos.y - tileSize;
		if (!leftClick && !rightClick) {
			return true;
		}
		
		Int2 position = mouseToCoor(mousePos.x - x, mousePos.y - y, seperation, tileSize);
		if (position != null) {
			// Send packet to server for inventory action (ALL modes - integrated or dedicated server)
			if (connection == null) {
				System.err.println("ERROR: Inventory has no connection! This should NEVER happen - even integrated servers use LocalConnection.");
				return true;
			}

			mc.sayda.mcraze.network.packet.PacketInventoryAction packet =
				new mc.sayda.mcraze.network.packet.PacketInventoryAction(
					position.x, position.y, leftClick, false);
			connection.sendPacket(packet);
			// Let server handle the inventory manipulation (whether integrated or dedicated)
			// The server will broadcast the updated inventory back to us
			// This prevents client-server desync and ensures consistent behavior
			return true;
		}
		
		x = screenWidth / 2 - panelWidth / 2;
		y = screenHeight / 2 - panelHeight / 2;
		x = x + (inventoryItems.length - tableSizeAvailable - 1) * (tileSize + seperation) - 5;
		y = y + seperation * 2 + tileSize - 5;
		
		// Check if clicking on craft output slot
		if (mousePos.x >= x && mousePos.x <= x + tileSize + 10 && mousePos.y >= y
				&& mousePos.y <= y + tileSize * 2 + 10) {

			// Send craft packet to server (ALL modes - integrated or dedicated server)
			if (connection == null) {
				System.err.println("ERROR: Inventory has no connection! This should NEVER happen - even integrated servers use LocalConnection.");
				return true;
			}

			mc.sayda.mcraze.network.packet.PacketInventoryAction packet =
				new mc.sayda.mcraze.network.packet.PacketInventoryAction(
					0, 0, leftClick, true);  // craftClick = true
			connection.sendPacket(packet);
			// Let server handle crafting (whether integrated or dedicated)
			// Server will broadcast the updated inventory back to us
			// This prevents client-server desync and ensures consistent behavior
			return true;
		}

		// NOTE: Crafting preview calculation is done SERVER-SIDE for all modes
		// The server calculates what CAN be crafted and sends it via PacketInventoryUpdate
		// Client just displays what the server tells it

		return true;
	}

	// relative x/y in px
	private Int2 mouseToCoor(int x, int y, int seperation, int tileSize) {
		clickPos.x = x / (seperation + tileSize);
		clickPos.y = y / (seperation + tileSize) - 1;
		if (clickPos.x < 0
				|| clickPos.y < 0
				|| clickPos.x >= inventoryItems.length
				|| clickPos.y >= inventoryItems[0].length
				|| ((clickPos.y < craftingHeight && clickPos.x < inventoryItems.length
						- tableSizeAvailable) || (craftingHeight != tableSizeAvailable && clickPos.y == tableSizeAvailable))) {
			return null;
		}
		return clickPos;
	}
	
	public void draw(GraphicsHandler g, int screenWidth, int screenHeight) {
		int tileSize = 16;
		int seperation = 10;
		
		int panelWidth = inventoryItems.length * (tileSize + seperation) + seperation;
		int panelHeight = tileSize + seperation * 2;
		int x = screenWidth / 2 - panelWidth / 2;
		int y = screenHeight - panelHeight - seperation;
		
		g.setColor(Color.gray);
		g.fillRect(x, y, panelWidth, panelHeight);
		
		for (int j = 0; j < inventoryItems.length; j++) {
			InventoryItem current = inventoryItems[j][playerRow];
			if (hotbarIdx == j) {
				g.setColor(Color.blue);
				g.fillRect(x + seperation - 2, y + seperation - 2, tileSize + 4, tileSize + 4);
			}
			g.setColor(Color.LIGHT_GRAY);
			g.fillRect(x + seperation, y + seperation, tileSize, tileSize);
			// if(hotbarIdx == j)
			// {
			//
			// if(current.item == null)
			// }
			// else
			// {
			// g.setColor(Color.LIGHT_GRAY);
			// g.fillRect(x+seperation-2, y+seperation-2, tileSize+4, tileSize+4);
			// }
			
			current.draw(g, x + seperation, y + seperation, tileSize);
			x += tileSize + seperation;
		}
		if (!isVisible()) {
			return;
		}
		
		seperation = 15;
		
		panelWidth = inventoryItems.length * (tileSize + seperation) + seperation;
		panelHeight = inventoryItems[0].length * (tileSize + seperation) + seperation;
		x = screenWidth / 2 - panelWidth / 2;
		y = screenHeight / 2 - panelHeight / 2;
		
		g.setColor(Color.gray);
		g.fillRect(x, y, panelWidth, panelHeight);
		
		g.setColor(Color.DARK_GRAY);
		g.fillRect(x + panelWidth - tableSizeAvailable * (tileSize + seperation) - seperation, y,
				tableSizeAvailable * (tileSize + seperation) + seperation, tableSizeAvailable
						* (tileSize + seperation) + seperation);
		
		for (int i = 0; i < inventoryItems[0].length; i++) {
			x = screenWidth / 2 - panelWidth / 2;
			for (int j = 0; j < inventoryItems.length; j++) {
				if ((i < craftingHeight && j < inventoryItems.length - tableSizeAvailable)
						|| (craftingHeight != tableSizeAvailable && i == tableSizeAvailable)) {
					x += tileSize + seperation;
					continue;
				}
				
				g.setColor(Color.LIGHT_GRAY);
				g.fillRect(x + seperation - 2, y + seperation - 2, tileSize + 4, tileSize + 4);
				InventoryItem current = inventoryItems[j][i];
				current.draw(g, x + seperation, y + seperation, tileSize);
				x += tileSize + seperation;
			}
			y += tileSize + seperation;
		}
		
		x = screenWidth / 2 - panelWidth / 2;
		y = screenHeight / 2 - panelHeight / 2;
		g.setColor(Color.orange);
		x = x + (inventoryItems.length - tableSizeAvailable - 1) * (tileSize + seperation);
		y = y + seperation * 2 + tileSize;
		
		g.fillRect(x - 5, y - 5, tileSize + 10, tileSize + 10);
		
		craftable.draw(g, x, y, tileSize);
		holding.draw(g, holdingX - tileSize / 2, holdingY - tileSize / 2, tileSize);
	}
	
	public void setVisible(boolean visible) {
		if (visible == false) {
			tableSizeAvailable = 2;
		}
		this.visible = visible;
	}
	
	public boolean isVisible() {
		return visible;
	}

	public InventoryItem getCraftable() {
		return craftable;
	}
}
