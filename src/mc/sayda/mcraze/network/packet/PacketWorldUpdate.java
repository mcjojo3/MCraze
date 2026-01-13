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

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Server → Client: World state update (block changes, time, etc.)
 * Binary protocol: 4-byte count + arrays + 8-byte long
 * Size: 4 + (count * 10) + 8 bytes
 */
public class PacketWorldUpdate extends ServerPacket {
	// Block changes
	public int[] changedX;
	public int[] changedY;
	public char[] changedTiles;

	// World time
	public long ticksAlive;

	public PacketWorldUpdate() {
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketWorldUpdate.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleWorldUpdate(this);
	}

	@Override
	public byte[] encode() {
		int count = (changedX != null) ? changedX.length : 0;
		// 4 bytes for count + (count * (4 + 4 + 2)) + 8 bytes for ticksAlive
		ByteBuffer buf = ByteBuffer.allocate(4 + (count * 10) + 8);

		buf.putInt(count);
		for (int i = 0; i < count; i++) {
			buf.putInt(changedX[i]);
			buf.putInt(changedY[i]);
			buf.putChar(changedTiles[i]);
		}
		buf.putLong(ticksAlive);

		return buf.array();
	}

	public static PacketWorldUpdate decode(ByteBuffer buf) {
		PacketWorldUpdate packet = new PacketWorldUpdate();

		int count = buf.getInt();
		packet.changedX = new int[count];
		packet.changedY = new int[count];
		packet.changedTiles = new char[count];

		for (int i = 0; i < count; i++) {
			packet.changedX[i] = buf.getInt();
			packet.changedY[i] = buf.getInt();
			packet.changedTiles[i] = buf.getChar();
		}

		packet.ticksAlive = buf.getLong();

		return packet;
	}
}
