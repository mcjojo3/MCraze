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

package mc.sayda.mcraze.ui.component;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.ui.layout.Anchor;
import mc.sayda.mcraze.ui.layout.LayoutEngine;
import mc.sayda.mcraze.ui.layout.LayoutParams;
import mc.sayda.mcraze.util.Int2;

/**
 * Grid of inventory slots with declarative positioning.
 *
 * This component replaces the manual coordinate calculations with a declarative approach:
 *
 * OLD WAY (Inventory.java:238-267):
 * <pre>
 * seperation = 15;
 * panelWidth = inventoryItems.length * (tileSize + seperation) + seperation;
 * x = screenWidth / 2 - panelWidth / 2;
 * for (...) {
 *     g.fillRect(x + seperation - 2, y + seperation - 2, tileSize + 4, tileSize + 4);
 *     current.draw(g, x + seperation, y + seperation, tileSize);
 *     x += tileSize + seperation;
 * }
 * </pre>
 *
 * NEW WAY:
 * <pre>
 * SlotGrid grid = new SlotGrid(inventoryData, 9, 4)
 *     .setSlotSize(18)
 *     .setGap(2)
 *     .setAnchor(Anchor.CENTER)
 *     .setMargin(10);
 * grid.updateLayout(screenWidth, screenHeight);
 * grid.draw(g);
 * </pre>
 *
 * DIMENSIONS:
 * - Slot size: 18x18 pixels (16px item sprite + 1px border each side)
 * - Gap: 2px between slots
 * - Total per slot: 20px (18px + 2px)
 * - Grid width: (columns * 20) + 10 (10px margin)
 * - Grid height: (rows * 20) + 10 (10px margin)
 *
 * CLIENT-SIDE ONLY: This component only renders. All slot interactions
 * must send packets to the server via SlotGridController.
 */
public class SlotGrid implements UIComponent {
	// Layout constants
	private static final int DEFAULT_SLOT_SIZE = 18;  // 16px item + 1px border each side
	private static final int DEFAULT_GAP = 2;          // Gap between slots
	private static final int DEFAULT_MARGIN = 10;      // Edge margin

	// Data reference (NOT owned - just reads)
	private InventoryItem[][] data;

	// Grid dimensions
	private int columns;
	private int rows;
	private int slotSize = DEFAULT_SLOT_SIZE;
	private int gap = DEFAULT_GAP;

	// Calculated layout
	private int x, y;
	private int width, height;
	private LayoutParams layoutParams = new LayoutParams();

	// Sprites (optional - fallback to solid colors if not set)
	private Sprite slotEmptySprite;
	private Sprite slotSelectedSprite;
	private Sprite slotHoverSprite;

	// State (CLIENT-SIDE ONLY - for rendering)
	private boolean visible = true;
	private Int2 selectedSlot = null;  // Which slot is selected (for highlighting)
	private Int2 hoverSlot = null;     // Which slot is hovered

	/**
	 * Create a slot grid that displays inventory items.
	 *
	 * @param data The inventory data to display (NOT copied - referenced)
	 * @param columns Number of columns
	 * @param rows Number of rows
	 */
	public SlotGrid(InventoryItem[][] data, int columns, int rows) {
		this.data = data;
		this.columns = columns;
		this.rows = rows;
		calculateDimensions();
	}

	/**
	 * Calculate grid dimensions from slot count and spacing.
	 */
	private void calculateDimensions() {
		// Width: (columns * slotSize) + ((columns - 1) * gap) + (2 * margin)
		// Height: (rows * slotSize) + ((rows - 1) * gap) + (2 * margin)
		// Simplified: (columns * (slotSize + gap)) - gap + (2 * margin)

		int slotPlusGap = slotSize + gap;
		this.width = (columns * slotPlusGap) - gap + (DEFAULT_MARGIN * 2);
		this.height = (rows * slotPlusGap) - gap + (DEFAULT_MARGIN * 2);
	}

	// ==================== FLUENT API ====================

	/**
	 * Set slot sprites for rendering.
	 *
	 * @param empty Sprite for empty/normal slots
	 * @param selected Sprite for selected slot
	 * @param hover Sprite for hovered slot (optional - can be null)
	 * @return This grid for chaining
	 */
	public SlotGrid setSlotSprites(Sprite empty, Sprite selected, Sprite hover) {
		this.slotEmptySprite = empty;
		this.slotSelectedSprite = selected;
		this.slotHoverSprite = hover;
		return this;
	}

	/**
	 * Set slot size (default: 18px).
	 */
	public SlotGrid setSlotSize(int slotSize) {
		this.slotSize = slotSize;
		calculateDimensions();
		return this;
	}

	/**
	 * Set gap between slots (default: 2px).
	 */
	public SlotGrid setGap(int gap) {
		this.gap = gap;
		calculateDimensions();
		return this;
	}

	/**
	 * Set anchor (fluent API).
	 */
	public SlotGrid setAnchor(Anchor anchor) {
		this.layoutParams.setAnchor(anchor);
		return this;
	}

	/**
	 * Set margin (fluent API).
	 */
	public SlotGrid setMargin(int margin) {
		this.layoutParams.setMargin(margin);
		return this;
	}

	/**
	 * Set layout params (fluent API).
	 */
	public SlotGrid setLayoutParams(LayoutParams params) {
		this.layoutParams = params;
		return this;
	}

	/**
	 * Set selected slot (CLIENT-SIDE ONLY - for rendering highlight).
	 *
	 * @param column Column index (0-based), or -1 to clear
	 * @param row Row index (0-based), or -1 to clear
	 */
	public SlotGrid setSelectedSlot(int column, int row) {
		if (column < 0 || row < 0) {
			this.selectedSlot = null;
		} else {
			this.selectedSlot = new Int2(column, row);
		}
		return this;
	}

	// ==================== UIComponent IMPLEMENTATION ====================

	@Override
	public void updateLayout(int screenWidth, int screenHeight) {
		// Calculate position using layout engine
		int[] pos = LayoutEngine.calculatePosition(screenWidth, screenHeight,
		                                           width, height, layoutParams);
		this.x = pos[0];
		this.y = pos[1];
	}

	@Override
	public void draw(GraphicsHandler g) {
		if (!visible) {
			return;
		}

		// Draw all slots
		int slotPlusGap = slotSize + gap;
		int slotX = x + DEFAULT_MARGIN;
		int slotY = y + DEFAULT_MARGIN;

		for (int row = 0; row < rows; row++) {
			slotX = x + DEFAULT_MARGIN;

			for (int col = 0; col < columns; col++) {
				// Determine slot state (selected > hover > normal)
				boolean isSelected = selectedSlot != null && selectedSlot.x == col && selectedSlot.y == row;
				boolean isHovered = hoverSlot != null && hoverSlot.x == col && hoverSlot.y == row;

				// Draw slot background
				if (isSelected && slotSelectedSprite != null) {
					slotSelectedSprite.draw(g, slotX, slotY, slotSize, slotSize);
				} else if (isHovered && slotHoverSprite != null) {
					slotHoverSprite.draw(g, slotX, slotY, slotSize, slotSize);
				} else if (slotEmptySprite != null) {
					slotEmptySprite.draw(g, slotX, slotY, slotSize, slotSize);
				} else {
					// Fallback: solid color rendering (matches old Inventory.java:260-261)
					// But add hover effect even without sprites
					if (isSelected) {
						g.setColor(Color.blue);
						g.fillRect(slotX - 2, slotY - 2, slotSize + 4, slotSize + 4);
					} else if (isHovered) {
						g.setColor(new Color(200, 200, 200));  // Lighter gray for hover
						g.fillRect(slotX - 2, slotY - 2, slotSize + 4, slotSize + 4);
					} else {
						g.setColor(Color.LIGHT_GRAY);
						g.fillRect(slotX - 2, slotY - 2, slotSize + 4, slotSize + 4);
					}
				}

				// Draw item if slot is within data bounds
				if (col < data.length && row < data[col].length) {
					InventoryItem item = data[col][row];
					if (item != null) {
						// Use existing InventoryItem.draw() method
						// Item size is slotSize - 2 (leave 1px border on each side)
						int itemSize = slotSize - 2;
						item.draw(g, slotX + 1, slotY + 1, itemSize);
					}
				}

				slotX += slotPlusGap;
			}

			slotY += slotPlusGap;
		}
	}

	@Override
	public boolean onMouseClick(int mouseX, int mouseY, boolean leftClick) {
		if (!visible) {
			return false;
		}

		// SlotGrid itself doesn't handle clicks - that's SlotGridController's job
		// This component is CLIENT-SIDE ONLY for rendering
		return false;
	}

	@Override
	public void onMouseMove(int mouseX, int mouseY) {
		if (!visible) {
			return;
		}

		// Update hover state
		Int2 slot = getSlotAt(mouseX, mouseY);
		this.hoverSlot = slot;
	}

	@Override
	public boolean contains(int x, int y) {
		return x >= this.x && x < this.x + width &&
		       y >= this.y && y < this.y + height;
	}

	/**
	 * Get which slot contains a point (for click detection).
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @return Int2(column, row) or null if outside grid
	 */
	public Int2 getSlotAt(int mouseX, int mouseY) {
		if (!contains(mouseX, mouseY)) {
			return null;
		}

		// Calculate slot indices
		int relX = mouseX - (x + DEFAULT_MARGIN);
		int relY = mouseY - (y + DEFAULT_MARGIN);

		int slotPlusGap = slotSize + gap;
		int col = relX / slotPlusGap;
		int row = relY / slotPlusGap;

		// Check if click is in gap (not on a slot)
		int slotLocalX = relX % slotPlusGap;
		int slotLocalY = relY % slotPlusGap;
		if (slotLocalX >= slotSize || slotLocalY >= slotSize) {
			return null;  // Clicked in gap
		}

		// Bounds check
		if (col < 0 || col >= columns || row < 0 || row >= rows) {
			return null;
		}

		return new Int2(col, row);
	}

	// ==================== GETTERS ====================

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public LayoutParams getLayoutParams() {
		return layoutParams;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/**
	 * Get grid columns.
	 */
	public int getColumns() {
		return columns;
	}

	/**
	 * Get grid rows.
	 */
	public int getRows() {
		return rows;
	}

	/**
	 * Get the data reference (NOT a copy).
	 */
	public InventoryItem[][] getData() {
		return data;
	}
}
