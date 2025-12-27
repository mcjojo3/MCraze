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

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Server → Client: Sync block breaking progress for accurate client animation
 * ISSUE #3 fix: Server-authoritative breaking state
 * Binary protocol: 4 ints = 16 bytes
 */
public class PacketBreakingProgress extends ServerPacket {
	public int x;  // Block X position
	public int y;  // Block Y position
	public int progress;  // Breaking progress (0 = not breaking, 1-9 = breaking stages)
	public int ticksNeeded;  // Total ticks needed to break

	public PacketBreakingProgress() {}

	public PacketBreakingProgress(int x, int y, int progress, int ticksNeeded) {
		this.x = x;
		this.y = y;
		this.progress = progress;
		this.ticksNeeded = ticksNeeded;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketBreakingProgress.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleBreakingProgress(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(16);
		buf.putInt(x);
		buf.putInt(y);
		buf.putInt(progress);
		buf.putInt(ticksNeeded);
		return buf.array();
	}

	public static PacketBreakingProgress decode(ByteBuffer buf) {
		int x = buf.getInt();
		int y = buf.getInt();
		int progress = buf.getInt();
		int ticksNeeded = buf.getInt();
		return new PacketBreakingProgress(x, y, progress, ticksNeeded);
	}
}
