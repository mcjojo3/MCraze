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

package mc.sayda.mcraze.util;

/**
 * Utility for gap-aware slot coordinate calculation.
 *
 * This replaces the buggy integer division approach used in Inventory.mouseToCoor()
 * and ChestUI.mouseToSlot() which incorrectly assigned gap pixels to adjacent slots.
 *
 * Based on the correct implementation from SlotGrid.getSlotAt() (SlotGrid.java:298-324).
 *
 * KEY FIX: Uses modulo operator to detect clicks in gaps between slots and returns
 * null instead of incorrectly selecting an adjacent slot.
 */
public class SlotCoordinateHelper {

	/**
	 * Calculate which slot contains a point, with gap detection.
	 *
	 * @param relX Relative X position within panel
	 * @param relY Relative Y position within panel
	 * @param slotSize Size of each slot in pixels (e.g., 16 or 18)
	 * @param gap Gap between slots in pixels (e.g., 2, 10, or 15)
	 * @param offsetX X offset from panel edge to first slot (usually margin)
	 * @param offsetY Y offset from panel edge to first slot (usually margin)
	 * @param columns Number of columns in the grid
	 * @param rows Number of rows in the grid
	 * @return Int2(column, row) or null if click is in gap or outside bounds
	 */
	public static Int2 getSlotAt(int relX, int relY, int slotSize, int gap,
	                              int offsetX, int offsetY, int columns, int rows) {
		// Calculate position relative to first slot
		int localX = relX - offsetX;
		int localY = relY - offsetY;

		// If before first slot, return null
		if (localX < 0 || localY < 0) {
			return null;
		}

		// Calculate which slot (including gaps)
		int slotPlusGap = slotSize + gap;
		int col = localX / slotPlusGap;
		int row = localY / slotPlusGap;

		// KEY FIX: Check if click is in the gap between slots
		// Use modulo to find position within the slot+gap cell
		int slotLocalX = localX % slotPlusGap;
		int slotLocalY = localY % slotPlusGap;

		// If we're in the gap portion (beyond slotSize), return null
		if (slotLocalX >= slotSize || slotLocalY >= slotSize) {
			return null;  // Clicked in gap between slots
		}

		// Bounds check - verify slot is within grid
		if (col < 0 || col >= columns || row < 0 || row >= rows) {
			return null;
		}

		return new Int2(col, row);
	}
}
