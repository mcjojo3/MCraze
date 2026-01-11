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

package mc.sayda.mcraze.network;

/**
 * Abstract connection between client and server.
 * Can be either local (integrated server) or remote (network).
 */
public interface Connection {
	/**
	 * Send a packet through this connection
	 */
	void sendPacket(Packet packet);

	/**
	 * Receive all pending packets
	 */
	Packet[] receivePackets();

	/**
	 * Check if the connection is still active
	 */
	boolean isConnected();

	/**
	 * Flush pending outbound packets to the network.
	 * For NetworkConnection: flushes BufferedOutputStream to TCP socket.
	 * For LocalConnection: no-op (no buffering).
	 * Should be called once per server tick to batch I/O operations.
	 */
	default void flush() {
		// Default: no-op (for LocalConnection)
	}

	/**
	 * Close the connection
	 */
	void disconnect();
}
