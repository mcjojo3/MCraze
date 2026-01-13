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

/**
 * Server → Client: Update gamerule values (sent when gamerules change)
 * Binary protocol: 3 bytes (one per gamerule)
 */
public class PacketGameruleUpdate extends ServerPacket {
	public boolean spelunking = false;
	public boolean keepInventory = false;
	public boolean daylightCycle = true;

	public PacketGameruleUpdate() {
	}

	public PacketGameruleUpdate(boolean spelunking, boolean keepInventory, boolean daylightCycle) {
		this.spelunking = spelunking;
		this.keepInventory = keepInventory;
		this.daylightCycle = daylightCycle;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketGameruleUpdate.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleGameruleUpdate(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(3);
		buf.put((byte) (spelunking ? 1 : 0));
		buf.put((byte) (keepInventory ? 1 : 0));
		buf.put((byte) (daylightCycle ? 1 : 0));
		return buf.array();
	}

	public static PacketGameruleUpdate decode(ByteBuffer buf) {
		PacketGameruleUpdate packet = new PacketGameruleUpdate();
		packet.spelunking = buf.get() == 1;
		packet.keepInventory = buf.get() == 1;
		packet.daylightCycle = buf.get() == 1;
		return packet;
	}
}
