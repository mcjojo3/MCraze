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

import java.util.ArrayList;
import java.util.List;

/**
 * Local (in-process) connection between client and integrated server.
 * Used for singleplayer mode where both client and server run in the same JVM.
 */
public class LocalConnection implements Connection {
	private List<Packet> packetQueue = new ArrayList<>();
	private LocalConnection otherEnd;
	private boolean connected = true;

	/**
	 * Create a pair of connected LocalConnections (client and server)
	 */
	public static LocalConnection[] createPair() {
		LocalConnection client = new LocalConnection();
		LocalConnection server = new LocalConnection();

		client.otherEnd = server;
		server.otherEnd = client;

		return new LocalConnection[] { client, server };
	}

	private LocalConnection() {}

	@Override
	public synchronized void sendPacket(Packet packet) {
		if (!connected || otherEnd == null) {
			return;
		}

		// Add packet to the other end's queue
		synchronized (otherEnd.packetQueue) {
			otherEnd.packetQueue.add(packet);
		}
	}

	@Override
	public synchronized Packet[] receivePackets() {
		synchronized (packetQueue) {
			if (packetQueue.isEmpty()) {
				return new Packet[0];
			}

			Packet[] packets = packetQueue.toArray(new Packet[0]);
			packetQueue.clear();
			return packets;
		}
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Override
	public synchronized void disconnect() {
		connected = false;
		if (otherEnd != null) {
			otherEnd.connected = false;
		}
	}
}
