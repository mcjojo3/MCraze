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
 * Server → Client: Batched backdrop tile updates (for world initialization)
 * Reduces network overhead by batching backdrop changes into single packet
 * Binary protocol: 4-byte count + arrays of (int x, int y, char tileId)
 */
public class PacketBackdropBatch extends ServerPacket {
	public int[] x;
	public int[] y;
	public char[] backdropTileIds; // tile ordinal, 0 = remove

	public PacketBackdropBatch() {
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketBackdropBatch.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleBackdropBatch(this);
	}

	@Override
	public byte[] encode() {
		int count = (x != null) ? x.length : 0;
		// 4 bytes for count + (count * (4 + 4 + 2)) = 4 + count * 10
		ByteBuffer buf = ByteBuffer.allocate(4 + (count * 10));

		buf.putInt(count);
		for (int i = 0; i < count; i++) {
			buf.putInt(x[i]);
			buf.putInt(y[i]);
			buf.putChar(backdropTileIds[i]);
		}

		return buf.array();
	}

	public static PacketBackdropBatch decode(ByteBuffer buf) {
		PacketBackdropBatch packet = new PacketBackdropBatch();

		int count = buf.getInt();
		packet.x = new int[count];
		packet.y = new int[count];
		packet.backdropTileIds = new char[count];

		for (int i = 0; i < count; i++) {
			packet.x[i] = buf.getInt();
			packet.y[i] = buf.getInt();
			packet.backdropTileIds[i] = buf.getChar();
		}

		return packet;
	}
}
