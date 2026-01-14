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
	public String playerUUID;

	public PacketPlayerRespawn() {
	}

	public PacketPlayerRespawn(String playerUUID) {
		this.playerUUID = playerUUID;
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
		byte[] uuidBytes = playerUUID.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(2 + uuidBytes.length);
		buf.putShort((short) uuidBytes.length);
		buf.put(uuidBytes);
		return buf.array();
	}

	public static PacketPlayerRespawn decode(ByteBuffer buf) {
		short length = buf.getShort();
		byte[] uuidBytes = new byte[length];
		buf.get(uuidBytes);
		String playerUUID = new String(uuidBytes, java.nio.charset.StandardCharsets.UTF_8);
		return new PacketPlayerRespawn(playerUUID);
	}
}
