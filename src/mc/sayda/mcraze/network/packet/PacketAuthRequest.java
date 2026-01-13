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
import java.nio.charset.StandardCharsets;

/**
 * Client → Server: Authentication request with username and password
 * Binary protocol: 2 strings (each with 2-byte length prefix)
 */
public class PacketAuthRequest extends ClientPacket {
	public String username;
	public String password;

	public PacketAuthRequest() {
	}

	public PacketAuthRequest(String username, String password) {
		this.username = username;
		this.password = password;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketAuthRequest.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleAuthRequest(this);
	}

	@Override
	public boolean requiresImmediateFlush() {
		return true; // CRITICAL: Auth packets must be flushed immediately
	}

	@Override
	public byte[] encode() {
		byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
		byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(4 + usernameBytes.length + passwordBytes.length);
		buf.putShort((short) usernameBytes.length);
		buf.put(usernameBytes);
		buf.putShort((short) passwordBytes.length);
		buf.put(passwordBytes);
		return buf.array();
	}

	public static PacketAuthRequest decode(ByteBuffer buf) {
		short usernameLen = buf.getShort();
		byte[] usernameBytes = new byte[usernameLen];
		buf.get(usernameBytes);
		String username = new String(usernameBytes, StandardCharsets.UTF_8);

		short passwordLen = buf.getShort();
		byte[] passwordBytes = new byte[passwordLen];
		buf.get(passwordBytes);
		String password = new String(passwordBytes, StandardCharsets.UTF_8);

		return new PacketAuthRequest(username, password);
	}
}
