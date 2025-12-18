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

import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketHandler;

/**
 * Server -> Client: Notify client that their player has died
 * Sent immediately on death for instant synchronization
 */
public class PacketPlayerDeath extends Packet {
	private static final long serialVersionUID = 1L;

	// No data needed - death is a simple event

	public PacketPlayerDeath() {}

	@Override
	public int getPacketId() {
		return 11;
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handlePlayerDeath(this);
	}
}
