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
 * Client → Server: Player pressed Q to toss an item
 * This packet is sent when the player wants to drop the currently selected
 * item.
 * The server will handle removing the item from inventory and spawning it in
 * the world.
 */
public class PacketItemToss extends ClientPacket {
	// No payload needed - server knows which player sent it and which item is
	// selected

	public PacketItemToss() {
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketItemToss.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleItemToss(this);
	}

	@Override
	public byte[] encode() {
		// Empty packet - no data needed
		return new byte[0];
	}

	public static PacketItemToss decode(ByteBuffer buf) {
		// No data to decode
		return new PacketItemToss();
	}
}
