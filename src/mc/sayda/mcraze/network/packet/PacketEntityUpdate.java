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

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Server → Client: Entity state update (positions, health, etc.)
 * Binary protocol: count + per-entity data
 * Each entity: int + 3 strings + 4 floats + 1 int + 2 booleans
 */
public class PacketEntityUpdate extends ServerPacket {
	public int[] entityIds;
	public String[] entityTypes;  // Entity type: "Player", "Item", etc.
	public String[] entityUUIDs;  // Entity UUIDs for stable tracking across disconnects
	public float[] entityX;
	public float[] entityY;
	public float[] entityDX;
	public float[] entityDY;
	public int[] entityHealth;
	public boolean[] facingRight;  // Entity facing direction
	public boolean[] dead;  // Entity death state
	public long[] ticksAlive;  // Entity animation timing
	public String[] itemIds;  // Item ID (for Item entities only, null for others)
	public String[] playerNames;  // Player username (for Player entities only, null for others)

	public PacketEntityUpdate() {}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketEntityUpdate.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleEntityUpdate(this);
	}

	@Override
	public byte[] encode() {
		int count = (entityIds != null) ? entityIds.length : 0;

		// Calculate total size
		int totalSize = 4;  // Entity count
		for (int i = 0; i < count; i++) {
			totalSize += 4;  // entityId
			totalSize += 2 + entityTypes[i].getBytes(StandardCharsets.UTF_8).length;  // entityType
			totalSize += 2 + entityUUIDs[i].getBytes(StandardCharsets.UTF_8).length;  // entityUUID
			totalSize += 16;  // 4 floats (x, y, dx, dy)
			totalSize += 4;  // entityHealth
			totalSize += 2;  // 2 booleans (facingRight, dead)
			totalSize += 8;  // ticksAlive (long)
			String itemId = (itemIds[i] != null) ? itemIds[i] : "";
			totalSize += 2 + itemId.getBytes(StandardCharsets.UTF_8).length;  // itemId
			String playerName = (playerNames[i] != null) ? playerNames[i] : "";
			totalSize += 2 + playerName.getBytes(StandardCharsets.UTF_8).length;  // playerName
		}

		ByteBuffer buf = ByteBuffer.allocate(totalSize);

		// Write entity count
		buf.putInt(count);

		// Write each entity
		for (int i = 0; i < count; i++) {
			// Entity ID
			buf.putInt(entityIds[i]);

			// Entity type
			byte[] typeBytes = entityTypes[i].getBytes(StandardCharsets.UTF_8);
			buf.putShort((short) typeBytes.length);
			buf.put(typeBytes);

			// Entity UUID
			byte[] uuidBytes = entityUUIDs[i].getBytes(StandardCharsets.UTF_8);
			buf.putShort((short) uuidBytes.length);
			buf.put(uuidBytes);

			// Position and velocity
			buf.putFloat(entityX[i]);
			buf.putFloat(entityY[i]);
			buf.putFloat(entityDX[i]);
			buf.putFloat(entityDY[i]);

			// Health
			buf.putInt(entityHealth[i]);

			// Booleans
			buf.put((byte) (facingRight[i] ? 1 : 0));
			buf.put((byte) (dead[i] ? 1 : 0));

			// Animation timing
			buf.putLong(ticksAlive[i]);

			// Item ID (nullable)
			String itemId = (itemIds[i] != null) ? itemIds[i] : "";
			byte[] itemIdBytes = itemId.getBytes(StandardCharsets.UTF_8);
			buf.putShort((short) itemIdBytes.length);
			buf.put(itemIdBytes);

			// Player name (nullable)
			String playerName = (playerNames[i] != null) ? playerNames[i] : "";
			byte[] playerNameBytes = playerName.getBytes(StandardCharsets.UTF_8);
			buf.putShort((short) playerNameBytes.length);
			buf.put(playerNameBytes);
		}

		return buf.array();
	}

	public static PacketEntityUpdate decode(ByteBuffer buf) {
		PacketEntityUpdate packet = new PacketEntityUpdate();

		// Read entity count
		int count = buf.getInt();
		packet.entityIds = new int[count];
		packet.entityTypes = new String[count];
		packet.entityUUIDs = new String[count];
		packet.entityX = new float[count];
		packet.entityY = new float[count];
		packet.entityDX = new float[count];
		packet.entityDY = new float[count];
		packet.entityHealth = new int[count];
		packet.facingRight = new boolean[count];
		packet.dead = new boolean[count];
		packet.ticksAlive = new long[count];
		packet.itemIds = new String[count];
		packet.playerNames = new String[count];

		// Read each entity
		for (int i = 0; i < count; i++) {
			// Entity ID
			packet.entityIds[i] = buf.getInt();

			// Entity type
			short typeLen = buf.getShort();
			byte[] typeBytes = new byte[typeLen];
			buf.get(typeBytes);
			packet.entityTypes[i] = new String(typeBytes, StandardCharsets.UTF_8);

			// Entity UUID
			short uuidLen = buf.getShort();
			byte[] uuidBytes = new byte[uuidLen];
			buf.get(uuidBytes);
			packet.entityUUIDs[i] = new String(uuidBytes, StandardCharsets.UTF_8);

			// Position and velocity
			packet.entityX[i] = buf.getFloat();
			packet.entityY[i] = buf.getFloat();
			packet.entityDX[i] = buf.getFloat();
			packet.entityDY[i] = buf.getFloat();

			// Health
			packet.entityHealth[i] = buf.getInt();

			// Booleans
			packet.facingRight[i] = buf.get() == 1;
			packet.dead[i] = buf.get() == 1;

			// Animation timing
			packet.ticksAlive[i] = buf.getLong();

			// Item ID (nullable)
			short itemIdLen = buf.getShort();
			byte[] itemIdBytes = new byte[itemIdLen];
			buf.get(itemIdBytes);
			String itemId = new String(itemIdBytes, StandardCharsets.UTF_8);
			packet.itemIds[i] = itemId.isEmpty() ? null : itemId;

			// Player name (nullable)
			short playerNameLen = buf.getShort();
			byte[] playerNameBytes = new byte[playerNameLen];
			buf.get(playerNameBytes);
			String playerName = new String(playerNameBytes, StandardCharsets.UTF_8);
			packet.playerNames[i] = playerName.isEmpty() ? null : playerName;
		}

		return packet;
	}
}
