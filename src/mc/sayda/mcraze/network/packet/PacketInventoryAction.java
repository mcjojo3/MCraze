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

package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Client → Server: Inventory click action
 * Sent when player clicks in their inventory to move/split/craft items
 * Binary protocol: 2 ints + 2 booleans = 10 bytes
 */
public class PacketInventoryAction extends ClientPacket {
	public int slotX; // Clicked slot X coordinate
	public int slotY; // Clicked slot Y coordinate
	public boolean leftClick; // true = left click, false = right click
	public boolean craftClick; // true if clicking craft output slot

	public PacketInventoryAction() {
	}

	public PacketInventoryAction(int slotX, int slotY, boolean leftClick, boolean craftClick) {
		this.slotX = slotX;
		this.slotY = slotY;
		this.leftClick = leftClick;
		this.craftClick = craftClick;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketInventoryAction.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleInventoryAction(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(10);
		buf.putInt(slotX);
		buf.putInt(slotY);
		buf.put((byte) (leftClick ? 1 : 0));
		buf.put((byte) (craftClick ? 1 : 0));
		return buf.array();
	}

	public static PacketInventoryAction decode(ByteBuffer buf) {
		int slotX = buf.getInt();
		int slotY = buf.getInt();
		boolean leftClick = buf.get() == 1;
		boolean craftClick = buf.get() == 1;
		return new PacketInventoryAction(slotX, slotY, leftClick, craftClick);
	}
}
