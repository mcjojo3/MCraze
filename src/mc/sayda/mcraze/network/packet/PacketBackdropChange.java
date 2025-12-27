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

import mc.sayda.mcraze.Constants.TileID;
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Server → Client: Backdrop tile change notification
 * Binary protocol: 2 ints + 1 char = 10 bytes
 */
public class PacketBackdropChange extends ServerPacket {
	public int x;
	public int y;
	public char backdropTileId;  // 0 = remove, else tile ordinal

	public PacketBackdropChange() {}

	public PacketBackdropChange(int x, int y, TileID backdropId) {
		this.x = x;
		this.y = y;
		this.backdropTileId = backdropId == null ?
			(char) 0 : (char) backdropId.ordinal();
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketBackdropChange.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleBackdropChange(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(10);
		buf.putInt(x);
		buf.putInt(y);
		buf.putChar(backdropTileId);
		return buf.array();
	}

	public static PacketBackdropChange decode(ByteBuffer buf) {
		int x = buf.getInt();
		int y = buf.getInt();
		char backdropTileId = buf.getChar();

		PacketBackdropChange packet = new PacketBackdropChange();
		packet.x = x;
		packet.y = y;
		packet.backdropTileId = backdropTileId;
		return packet;
	}
}
