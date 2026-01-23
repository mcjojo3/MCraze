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
	public boolean darkness = true;
	public int daylightSpeed = 40000;
	public boolean keepInventory = false;
	public boolean daylightCycle = true;
	public boolean mobGriefing = true;
	public boolean pvp = true;
	public boolean insomnia = false;

	public PacketGameruleUpdate() {
	}

	public PacketGameruleUpdate(boolean spelunking, boolean darkness, int daylightSpeed, boolean keepInventory,
			boolean daylightCycle, boolean mobGriefing,
			boolean pvp, boolean insomnia) {
		this.spelunking = spelunking;
		this.darkness = darkness;
		this.daylightSpeed = daylightSpeed;
		this.keepInventory = keepInventory;
		this.daylightCycle = daylightCycle;
		this.mobGriefing = mobGriefing;
		this.pvp = pvp;
		this.insomnia = insomnia;
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
		// 7 booleans (1 byte each) + 1 int (4 bytes) = 11 bytes
		ByteBuffer buf = ByteBuffer.allocate(11);
		buf.put((byte) (spelunking ? 1 : 0));
		buf.put((byte) (darkness ? 1 : 0));
		buf.putInt(daylightSpeed);
		buf.put((byte) (keepInventory ? 1 : 0));
		buf.put((byte) (daylightCycle ? 1 : 0));
		buf.put((byte) (mobGriefing ? 1 : 0));
		buf.put((byte) (pvp ? 1 : 0));
		buf.put((byte) (insomnia ? 1 : 0));
		return buf.array();
	}

	public static PacketGameruleUpdate decode(ByteBuffer buf) {
		PacketGameruleUpdate packet = new PacketGameruleUpdate();
		packet.spelunking = buf.get() == 1;
		packet.darkness = buf.get() == 1;
		packet.daylightSpeed = buf.getInt();
		packet.keepInventory = buf.get() == 1;
		packet.daylightCycle = buf.get() == 1;
		packet.mobGriefing = buf.get() == 1;
		packet.pvp = buf.get() == 1;
		packet.insomnia = buf.get() == 1;
		return packet;
	}
}
