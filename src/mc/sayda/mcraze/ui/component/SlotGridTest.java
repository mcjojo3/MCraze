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

import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.ui.layout.Anchor;

/**
 * Simple test/demo for SlotGrid component.
 *
 * Usage in Client.java or main menu:
 * <pre>
 * // Create test instance
 * SlotGridTest test = new SlotGridTest();
 *
 * // In render loop
 * test.render(g, screenWidth, screenHeight);
 * </pre>
 *
 * This demonstrates:
 * - SlotGrid with declarative positioning (centered)
 * - Automatic layout calculation
 * - Dummy inventory data rendering
 * - Fallback to solid color slots (no sprites yet)
 *
 * Expected result:
 * - 9x4 grid centered on screen
 * - First 5 slots have items (dirt, stone, wood, etc.)
 * - Light gray slot backgrounds
 * - Grid dimensions: 200px wide × 100px tall
 *
 * TODO: Remove this test file once UI redesign is complete and integrated.
 */
public class SlotGridTest {
	private SlotGrid slotGrid;
	private InventoryItem[][] dummyData;

	/**
	 * Create a test instance with dummy inventory data.
	 */
	public SlotGridTest() {
		// Create dummy 9x4 inventory grid
		dummyData = new InventoryItem[9][4];

		// Fill with empty slots (just test rendering, not item functionality)
		for (int col = 0; col < 9; col++) {
			for (int row = 0; row < 4; row++) {
				dummyData[col][row] = new InventoryItem(null);
			}
		}

		// TODO: Add actual test items when Item registry system is available
		// For now, empty slots are sufficient to verify grid rendering and layout

		// Create SlotGrid with declarative positioning
		slotGrid = new SlotGrid(dummyData, 9, 4)
			.setAnchor(Anchor.CENTER)  // Center on screen
			.setSelectedSlot(0, 0);    // Highlight first slot for demo
	}

	/**
	 * Render the test grid.
	 *
	 * @param g Graphics handler
	 * @param screenWidth Screen width
	 * @param screenHeight Screen height
	 */
	public void render(GraphicsHandler g, int screenWidth, int screenHeight) {
		// Update layout (recalculates position if screen size changed)
		slotGrid.updateLayout(screenWidth, screenHeight);

		// Draw the grid
		slotGrid.draw(g);

		// Draw debug info
		g.setColor(mc.sayda.mcraze.Color.white);
		g.drawString("SlotGrid Test - 9x4 Grid", 10, 20);
		g.drawString("Grid position: (" + slotGrid.getX() + ", " + slotGrid.getY() + ")", 10, 35);
		g.drawString("Grid size: " + slotGrid.getWidth() + "x" + slotGrid.getHeight(), 10, 50);
		g.drawString("Expected: 200x100 centered", 10, 65);
	}

	/**
	 * Test mouse click on grid.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @return Clicked slot or null
	 */
	public mc.sayda.mcraze.util.Int2 testClick(int mouseX, int mouseY) {
		mc.sayda.mcraze.util.Int2 slot = slotGrid.getSlotAt(mouseX, mouseY);
		if (slot != null) {
			slotGrid.setSelectedSlot(slot.x, slot.y);
			System.out.println("Clicked slot: (" + slot.x + ", " + slot.y + ")");
		}
		return slot;
	}

	/**
	 * Test mouse move for hover effect.
	 */
	public void testMouseMove(int mouseX, int mouseY) {
		slotGrid.onMouseMove(mouseX, mouseY);
	}

	/**
	 * Get the test grid (for inspection).
	 */
	public SlotGrid getSlotGrid() {
		return slotGrid;
	}
}
