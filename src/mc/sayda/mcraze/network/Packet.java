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

package mc.sayda.mcraze.network;

/**
 * Base class for all network packets sent between client and server.
 * Uses binary protocol instead of Java serialization for efficiency.
 */
public abstract class Packet {
	/**
	 * Get the packet type ID for deserialization
	 */
	public abstract int getPacketId();

	/**
	 * Encode this packet to binary format
	 */
	public abstract byte[] encode();

	/**
	 * Whether this packet requires immediate flush (for critical packets like auth,
	 * world init)
	 * Default: false (batched for performance)
	 * Override to true for time-sensitive packets that must be sent immediately
	 */
	public boolean requiresImmediateFlush() {
		return false; // Default: no flush, better performance
	}
}
