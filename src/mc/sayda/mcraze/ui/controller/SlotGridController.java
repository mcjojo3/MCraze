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

package mc.sayda.mcraze.ui.controller;

import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketInventoryAction;
import mc.sayda.mcraze.ui.component.SlotGrid;
import mc.sayda.mcraze.util.Int2;

/**
 * Controller for handling SlotGrid input and sending packets to server.
 *
 * Separation of concerns:
 * - SlotGrid: CLIENT-SIDE ONLY rendering
 * - SlotGridController: Input handling + packet sending
 * - Server: Authoritative state management
 *
 * OLD WAY (Inventory.java:102-180):
 * <pre>
 * public boolean updateInventory(..., Connection connection) {
 *     // 80 lines of manual coordinate math + packet sending mixed together
 *     Int2 position = mouseToCoor(mousePos.x - x, mousePos.y - y, seperation, tileSize);
 *     if (position != null) {
 *         PacketInventoryAction packet = new PacketInventoryAction(position.x, position.y, leftClick, false);
 *         connection.sendPacket(packet);
 *     }
 * }
 * </pre>
 *
 * NEW WAY:
 * <pre>
 * SlotGridController controller = new SlotGridController(slotGrid, connection);
 * boolean handled = controller.handleClick(mouseX, mouseY, leftClick);
 * </pre>
 *
 * CRITICAL: All inventory state changes MUST go through server packets.
 * This ensures dedicated servers and joining players stay in sync.
 */
public class SlotGridController {
	private SlotGrid slotGrid;
	private Connection connection;

	/**
	 * Create a controller for a slot grid.
	 *
	 * @param slotGrid The grid to control
	 * @param connection Server connection for sending packets
	 */
	public SlotGridController(SlotGrid slotGrid, Connection connection) {
		this.slotGrid = slotGrid;
		this.connection = connection;
	}

	/**
	 * Handle a mouse click on the slot grid.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @param leftClick True if left click, false if right click
	 * @return True if click was handled
	 */
	public boolean handleClick(int mouseX, int mouseY, boolean leftClick) {
		if (!slotGrid.isVisible()) {
			return false;
		}

		// Get clicked slot
		Int2 slot = slotGrid.getSlotAt(mouseX, mouseY);
		if (slot == null) {
			return false;  // Clicked outside grid or in gap
		}

		// Validate connection (should NEVER be null - even integrated servers use LocalConnection)
		if (connection == null) {
			System.err.println("ERROR: SlotGridController has no connection! This should NEVER happen - even integrated servers use LocalConnection.");
			return true;
		}

		// Send packet to server (ALL modes - integrated or dedicated server)
		PacketInventoryAction packet = new PacketInventoryAction(
			slot.x, slot.y, leftClick, false  // craftClick = false (regular slot)
		);
		connection.sendPacket(packet);

		// Update selected slot for visual feedback
		slotGrid.setSelectedSlot(slot.x, slot.y);

		// Let server handle the inventory manipulation (whether integrated or dedicated)
		// The server will broadcast the updated inventory back to us via PacketInventoryUpdate
		// This prevents client-server desync and ensures consistent behavior
		return true;
	}

	/**
	 * Handle mouse move over the slot grid.
	 * Updates hover state for visual feedback.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 */
	public void handleMouseMove(int mouseX, int mouseY) {
		if (!slotGrid.isVisible()) {
			return;
		}

		// SlotGrid.onMouseMove() already handles hover state update
		slotGrid.onMouseMove(mouseX, mouseY);
	}

	/**
	 * Check if a point is within the slot grid bounds.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @return True if point is inside grid
	 */
	public boolean contains(int mouseX, int mouseY) {
		return slotGrid.contains(mouseX, mouseY);
	}

	/**
	 * Update the connection (for when it changes).
	 *
	 * @param connection New connection
	 */
	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	/**
	 * Get the controlled slot grid.
	 */
	public SlotGrid getSlotGrid() {
		return slotGrid;
	}
}
