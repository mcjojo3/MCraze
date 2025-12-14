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

package mc.sayda.network;

import java.io.Serializable;

/**
 * Base class for all network packets sent between client and server
 */
public abstract class Packet implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Get the packet type ID for deserialization
	 */
	public abstract int getPacketId();

	/**
	 * Handle this packet on the receiving end
	 */
	public abstract void handle(PacketHandler handler);
}
