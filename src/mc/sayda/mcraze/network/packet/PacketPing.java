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

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Client → Server: Ping request with timestamp
 * Binary protocol: 8-byte timestamp (milliseconds since epoch)
 */
public class PacketPing extends ClientPacket {
	public long timestamp;  // Client timestamp when ping was sent

	public PacketPing() {}

	public PacketPing(long timestamp) {
		this.timestamp = timestamp;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketPing.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handlePing(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(8);
		buf.putLong(timestamp);
		return buf.array();
	}

	public static PacketPing decode(ByteBuffer buf) {
		long timestamp = buf.getLong();
		return new PacketPing(timestamp);
	}
}
