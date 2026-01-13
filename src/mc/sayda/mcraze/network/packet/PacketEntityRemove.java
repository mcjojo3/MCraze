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
import java.nio.charset.StandardCharsets;

/**
 * Server → Client: Remove entity from client's entity list (e.g., when player
 * disconnects)
 * Binary protocol: 2-byte length + UTF-8 UUID string
 */
public class PacketEntityRemove extends ServerPacket {
	public String entityUUID; // UUID of entity to remove

	public PacketEntityRemove() {
	}

	public PacketEntityRemove(String entityUUID) {
		this.entityUUID = entityUUID;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketEntityRemove.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleEntityRemove(this);
	}

	@Override
	public byte[] encode() {
		byte[] uuidBytes = entityUUID.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(2 + uuidBytes.length);
		buf.putShort((short) uuidBytes.length);
		buf.put(uuidBytes);
		return buf.array();
	}

	public static PacketEntityRemove decode(ByteBuffer buf) {
		short uuidLen = buf.getShort();
		byte[] uuidBytes = new byte[uuidLen];
		buf.get(uuidBytes);
		String entityUUID = new String(uuidBytes, StandardCharsets.UTF_8);
		return new PacketEntityRemove(entityUUID);
	}
}
