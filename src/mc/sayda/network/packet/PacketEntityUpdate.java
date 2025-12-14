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
 * Server -> Client: Entity state update (positions, health, etc.)
 */
public class PacketEntityUpdate extends Packet {
	private static final long serialVersionUID = 1L;

	public int[] entityIds;
	public float[] entityX;
	public float[] entityY;
	public float[] entityDX;
	public float[] entityDY;
	public int[] entityHealth;

	public PacketEntityUpdate() {}

	@Override
	public int getPacketId() {
		return 6;
	}

	@Override
	public void handle(PacketHandler handler) {
		handler.handleEntityUpdate(this);
	}
}
