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

package mc.sayda.mcraze.world;

import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.InventoryItem;

/**
 * Stores the inventory data for a chest at a specific world position
 */
public class ChestData implements java.io.Serializable {
	private static final long serialVersionUID = 2L; // Changed due to structure change

	public final int x;
	public final int y;
	public InventoryItem[][] items; // 10 wide x 3 tall grid with counts

	public ChestData(int x, int y) {
		this.x = x;
		this.y = y;
		this.items = new InventoryItem[10][3]; // 10 columns, 3 rows

		// Initialize all slots with empty InventoryItems
		for (int i = 0; i < 10; i++) {
			for (int j = 0; j < 3; j++) {
				items[i][j] = new InventoryItem(null);
			}
		}
	}

	/**
	 * Get item at chest slot
	 */
	public Item getItem(int slotX, int slotY) {
		if (slotX < 0 || slotX >= 10 || slotY < 0 || slotY >= 3)
			return null;
		InventoryItem invItem = items[slotX][slotY];
		return (invItem != null && !invItem.isEmpty()) ? invItem.getItem() : null;
	}

	/**
	 * Get inventory item at chest slot (with count)
	 */
	public InventoryItem getInventoryItem(int slotX, int slotY) {
		if (slotX < 0 || slotX >= 10 || slotY < 0 || slotY >= 3)
			return null;
		return items[slotX][slotY];
	}

	/**
	 * Set item at chest slot (backwards compatibility - sets count to 1)
	 */
	public void setItem(int slotX, int slotY, Item item) {
		if (slotX < 0 || slotX >= 10 || slotY < 0 || slotY >= 3)
			return;
		if (item == null) {
			items[slotX][slotY].setEmpty();
		} else {
			items[slotX][slotY].setItem(item);
			items[slotX][slotY].setCount(1);
		}
	}

	/**
	 * Set inventory item at chest slot (with count)
	 */
	public void setInventoryItem(int slotX, int slotY, InventoryItem invItem) {
		if (slotX < 0 || slotX >= 10 || slotY < 0 || slotY >= 3)
			return;
		items[slotX][slotY] = invItem;
	}

	/**
	 * Check if chest is empty
	 */
	public boolean isEmpty() {
		for (int x = 0; x < 10; x++) {
			for (int y = 0; y < 3; y++) {
				if (items[x][y] != null && !items[x][y].isEmpty()) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Get unique key for this chest position
	 */
	public String getKey() {
		return x + "," + y;
	}

	/**
	 * Create key from position
	 */
	public static String makeKey(int x, int y) {
		return x + "," + y;
	}
}
