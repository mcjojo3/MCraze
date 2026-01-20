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

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Server → Client: Initialize world with dimensions (sent before world data)
 * Binary protocol: 2 ints + 1 long + 2 floats + 1 string + 1 int = ~30+ bytes
 */
public class PacketWorldInit extends ServerPacket {
	public int worldWidth;
	public int worldHeight;
	public long seed;
	public float spawnX;
	public float spawnY;
	public String playerUUID; // UUID of this client's player entity
	public int totalPacketsExpected; // Total number of world update + entity packets to expect

	// Gamerules
	public boolean spelunking = false;
	public boolean keepInventory = false;
	public boolean daylightCycle = true;
	public boolean mobGriefing = true;
	public boolean pvp = true;

	public PacketWorldInit() {
	}

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
		return PacketRegistry.getId(PacketWorldInit.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleWorldInit(this);
	}

	@Override
	public byte[] encode() {
		byte[] uuidBytes = (playerUUID != null ? playerUUID : "").getBytes(StandardCharsets.UTF_8);
		ByteBuffer buf = ByteBuffer.allocate(35 + uuidBytes.length); // +5 bytes for gamerules
		buf.putInt(worldWidth);
		buf.putInt(worldHeight);
		buf.putLong(seed);
		buf.putFloat(spawnX);
		buf.putFloat(spawnY);
		buf.putShort((short) uuidBytes.length);
		buf.put(uuidBytes);
		buf.putInt(totalPacketsExpected);
		// Gamerules
		buf.put((byte) (spelunking ? 1 : 0));
		buf.put((byte) (keepInventory ? 1 : 0));
		buf.put((byte) (daylightCycle ? 1 : 0));
		buf.put((byte) (mobGriefing ? 1 : 0));
		buf.put((byte) (pvp ? 1 : 0));
		return buf.array();
	}

	public static PacketWorldInit decode(ByteBuffer buf) {
		PacketWorldInit packet = new PacketWorldInit();
		packet.worldWidth = buf.getInt();
		packet.worldHeight = buf.getInt();
		packet.seed = buf.getLong();
		packet.spawnX = buf.getFloat();
		packet.spawnY = buf.getFloat();

		short uuidLen = buf.getShort();
		byte[] uuidBytes = new byte[uuidLen];
		buf.get(uuidBytes);
		packet.playerUUID = new String(uuidBytes, StandardCharsets.UTF_8);

		packet.totalPacketsExpected = buf.getInt();

		// Gamerules
		packet.spelunking = buf.get() == 1;
		packet.keepInventory = buf.get() == 1;
		packet.daylightCycle = buf.get() == 1;
		packet.mobGriefing = buf.get() == 1;
		packet.pvp = buf.get() == 1;

		return packet;
	}
}
