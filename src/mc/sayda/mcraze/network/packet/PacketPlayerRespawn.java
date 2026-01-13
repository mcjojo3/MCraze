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
 * Server → Client: Notify client that their player has respawned
 * Sent immediately on respawn for instant synchronization (symmetric with
 * PacketPlayerDeath)
 * Binary protocol: 0 bytes (signal packet, no payload)
 */
public class PacketPlayerRespawn extends ServerPacket {
	// No data needed - respawn is a simple event

	public PacketPlayerRespawn() {
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketPlayerRespawn.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handlePlayerRespawn(this);
	}

	@Override
	public byte[] encode() {
		// Empty packet - just a signal
		return new byte[0];
	}

	public static PacketPlayerRespawn decode(ByteBuffer buf) {
		// No data to decode
		return new PacketPlayerRespawn();
	}
}
