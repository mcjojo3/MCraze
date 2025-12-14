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

import mc.sayda.network.Packet;
import mc.sayda.network.PacketHandler;

/**
 * Server -> Client: World state update (block changes, time, etc.)
 */
public class PacketWorldUpdate extends Packet {
	private static final long serialVersionUID = 1L;

	// Block changes
	public int[] changedX;
	public int[] changedY;
	public char[] changedTiles;

	// World time
	public long ticksAlive;

	public PacketWorldUpdate() {}

	@Override
	public int getPacketId() {
		return 5;
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handleWorldUpdate(this);
	}
}
