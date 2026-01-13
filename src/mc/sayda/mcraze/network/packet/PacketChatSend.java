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
 * Client → Server: Chat message or command
 * Binary protocol: 2-byte length + UTF-8 string
 */
public class PacketChatSend extends ClientPacket {
	public String message;

	public PacketChatSend() {
	}

	public PacketChatSend(String message) {
		this.message = message;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketChatSend.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleChatSend(this);
	}

	@Override
	public byte[] encode() {
		byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(2 + messageBytes.length);
		buf.putShort((short) messageBytes.length);
		buf.put(messageBytes);
		return buf.array();
	}

	public static PacketChatSend decode(ByteBuffer buf) {
		short length = buf.getShort();
		byte[] messageBytes = new byte[length];
		buf.get(messageBytes);
		String message = new String(messageBytes, StandardCharsets.UTF_8);
		return new PacketChatSend(message);
	}
}
