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

package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketHandler;

/**
 * Client -> Server: Inventory click action
 * Sent when player clicks in their inventory to move/split/craft items
 */
public class PacketInventoryAction extends Packet {
	private static final long serialVersionUID = 1L;

	public int slotX;          // Clicked slot X coordinate
	public int slotY;          // Clicked slot Y coordinate
	public boolean leftClick;  // true = left click, false = right click
	public boolean craftClick; // true if clicking craft output slot

	public PacketInventoryAction() {}

	public PacketInventoryAction(int slotX, int slotY, boolean leftClick, boolean craftClick) {
		this.slotX = slotX;
		this.slotY = slotY;
		this.leftClick = leftClick;
		this.craftClick = craftClick;
	}

	@Override
	public int getPacketId() {
		return 11;  // New packet ID
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handleInventoryAction(this);
	}
}
