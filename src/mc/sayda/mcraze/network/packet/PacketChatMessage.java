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

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Server → Client: Chat message to display
 * Binary protocol: 2-byte length + UTF-8 string + 4-byte RGB color
 */
public class PacketChatMessage extends ServerPacket {
	public String message;
	public int colorRGB; // Color serialized as RGB int

	public PacketChatMessage() {
	}

	public PacketChatMessage(String message, Color color) {
		this.message = message;
		this.colorRGB = color.toRGB();
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketChatMessage.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleChatMessage(this);
	}

	@Override
	public byte[] encode() {
		byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(6 + messageBytes.length);
		buf.putShort((short) messageBytes.length);
		buf.put(messageBytes);
		buf.putInt(colorRGB);
		return buf.array();
	}

	public static PacketChatMessage decode(ByteBuffer buf) {
		short messageLen = buf.getShort();
		byte[] messageBytes = new byte[messageLen];
		buf.get(messageBytes);
		String message = new String(messageBytes, StandardCharsets.UTF_8);
		int colorRGB = buf.getInt();

		PacketChatMessage packet = new PacketChatMessage();
		packet.message = message;
		packet.colorRGB = colorRGB;
		return packet;
	}

	public Color getColor() {
		return Color.fromRGB(colorRGB);
	}
}
