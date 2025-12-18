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
 * Server -> Client: Initialize world with dimensions (sent before world data)
 */
public class PacketWorldInit extends Packet {
	private static final long serialVersionUID = 1L;

	public int worldWidth;
	public int worldHeight;
	public long seed;
	public float spawnX;
	public float spawnY;
	public String playerUUID;  // UUID of this client's player entity

	public PacketWorldInit() {}

	public PacketWorldInit(int width, int height, long seed) {
		this.worldWidth = width;
		this.worldHeight = height;
		this.seed = seed;
	}

	public PacketWorldInit(int width, int height, long seed, float spawnX, float spawnY) {
		this.worldWidth = width;
		this.worldHeight = height;
		this.seed = seed;
		this.spawnX = spawnX;
		this.spawnY = spawnY;
	}

	@Override
	public int getPacketId() {
		return 7;  // New packet ID
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handleWorldInit(this);
	}
}
