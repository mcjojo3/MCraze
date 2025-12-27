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
import java.nio.charset.StandardCharsets;

/**
 * Server → Client: Authentication response (success or error)
 * Binary protocol: 1 boolean + 2-byte length + UTF-8 string
 */
public class PacketAuthResponse extends ServerPacket {
	public boolean success;
	public String message;  // Error message if failed, empty if success

	public PacketAuthResponse() {}

	public PacketAuthResponse(boolean success, String message) {
		this.success = success;
		this.message = message;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketAuthResponse.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleAuthResponse(this);
	}

	@Override
	public byte[] encode() {
		byte[] messageBytes = (message != null ? message : "").getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(3 + messageBytes.length);
		buf.put((byte) (success ? 1 : 0));
		buf.putShort((short) messageBytes.length);
		buf.put(messageBytes);
		return buf.array();
	}

	public static PacketAuthResponse decode(ByteBuffer buf) {
		boolean success = buf.get() == 1;
		short messageLen = buf.getShort();
		byte[] messageBytes = new byte[messageLen];
		buf.get(messageBytes);
		String message = new String(messageBytes, StandardCharsets.UTF_8);
		return new PacketAuthResponse(success, message);
	}
}
