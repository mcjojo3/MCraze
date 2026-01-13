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
 * Client → Server: Player pressed 'B' to toggle backdrop placement mode
 * This packet is sent when the player wants to switch between foreground and
 * backdrop placement.
 * The server will toggle the player's backdropPlacementMode boolean and send
 * chat feedback.
 */
public class PacketToggleBackdropMode extends ClientPacket {
	// No payload needed - server knows which player sent it

	public PacketToggleBackdropMode() {
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketToggleBackdropMode.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleToggleBackdropMode(this);
	}

	@Override
	public byte[] encode() {
		// Empty packet - no data needed
		return new byte[0];
	}

	public static PacketToggleBackdropMode decode(ByteBuffer buf) {
		// No data to decode
		return new PacketToggleBackdropMode();
	}
}
