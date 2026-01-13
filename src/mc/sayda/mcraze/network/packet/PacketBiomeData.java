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
import mc.sayda.mcraze.world.Biome;

import java.nio.ByteBuffer;

/**
 * Server → Client: Send biome data for the entire world
 * Binary protocol: 4-byte count + byte array
 * Sent once during world initialization
 * Size: 4 + worldWidth bytes
 */
public class PacketBiomeData extends ServerPacket {
	public byte[] biomeIndices; // Biome ordinal values (one byte per X column)

	public PacketBiomeData() {
	}

	public PacketBiomeData(Biome[] biomes) {
		if (biomes != null) {
			this.biomeIndices = new byte[biomes.length];
			for (int i = 0; i < biomes.length; i++) {
				this.biomeIndices[i] = (byte) biomes[i].ordinal();
			}
		} else {
			this.biomeIndices = new byte[0];
		}
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketBiomeData.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleBiomeData(this);
	}

	@Override
	public byte[] encode() {
		int count = (biomeIndices != null) ? biomeIndices.length : 0;
		ByteBuffer buf = ByteBuffer.allocate(4 + count);

		buf.putInt(count);
		if (count > 0) {
			buf.put(biomeIndices);
		}

		return buf.array();
	}

	public static PacketBiomeData decode(ByteBuffer buf) {
		PacketBiomeData packet = new PacketBiomeData();

		int count = buf.getInt();
		packet.biomeIndices = new byte[count];

		if (count > 0) {
			buf.get(packet.biomeIndices);
		}

		return packet;
	}

	/**
	 * Convert biome indices to Biome array
	 */
	public Biome[] toBiomeArray() {
		if (biomeIndices == null || biomeIndices.length == 0) {
			return new Biome[0];
		}

		Biome[] biomes = new Biome[biomeIndices.length];
		Biome[] allBiomes = Biome.values();

		for (int i = 0; i < biomeIndices.length; i++) {
			int ordinal = biomeIndices[i] & 0xFF; // Convert to unsigned
			if (ordinal >= 0 && ordinal < allBiomes.length) {
				biomes[i] = allBiomes[ordinal];
			} else {
				biomes[i] = Biome.PLAINS; // Fallback to plains for invalid indices
			}
		}

		return biomes;
	}
}
