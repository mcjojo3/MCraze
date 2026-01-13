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
 * Client → Server: Block break/place action
 * Binary protocol: 2 ints + 1 char + 1 boolean = 11 bytes
 */
public class PacketBlockChange extends ClientPacket {
	public int x;
	public int y;
	public char newTileId; // 0 for break, tile ID for place
	public boolean isBreak;

	public PacketBlockChange() {
	}

	public PacketBlockChange(int x, int y, char newTileId, boolean isBreak) {
		this.x = x;
		this.y = y;
		this.newTileId = newTileId;
		this.isBreak = isBreak;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketBlockChange.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleBlockChange(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(11);
		buf.putInt(x);
		buf.putInt(y);
		buf.putChar(newTileId);
		buf.put((byte) (isBreak ? 1 : 0));
		return buf.array();
	}

	public static PacketBlockChange decode(ByteBuffer buf) {
		int x = buf.getInt();
		int y = buf.getInt();
		char newTileId = buf.getChar();
		boolean isBreak = buf.get() == 1;
		return new PacketBlockChange(x, y, newTileId, isBreak);
	}
}
