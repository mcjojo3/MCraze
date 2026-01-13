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
 * Server → Client: Pong response with original timestamp
 * Binary protocol: 8-byte timestamp (original client timestamp)
 */
public class PacketPong extends ServerPacket {
	public long timestamp; // Original client timestamp (echoed back)

	public PacketPong() {
	}

	public PacketPong(long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketPong.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handlePong(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(8);
		buf.putLong(timestamp);
		return buf.array();
	}

	public static PacketPong decode(ByteBuffer buf) {
		long timestamp = buf.getLong();
		return new PacketPong(timestamp);
	}
}
