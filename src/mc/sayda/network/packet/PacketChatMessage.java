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

package mc.sayda.network.packet;

import mc.sayda.Color;
import mc.sayda.network.Packet;
import mc.sayda.network.PacketHandler;

/**
 * Server -> Client: Chat message to display
 */
public class PacketChatMessage extends Packet {
	private static final long serialVersionUID = 1L;

	public String message;
	public int colorRGB;  // Color serialized as RGB int

	public PacketChatMessage() {}

	public PacketChatMessage(String message, Color color) {
		this.message = message;
		this.colorRGB = color.toRGB();
	}

	@Override
	public int getPacketId() {
		return 4;
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handleChatMessage(this);
	}

	public Color getColor() {
		return Color.fromRGB(colorRGB);
	}
}
