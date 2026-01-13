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
 * Server → Client: Entity state update (positions, health, etc.)
 * Binary protocol: count + per-entity data
 * Each entity: int + 3 strings + 4 floats + 1 int + 2 booleans
 */
public class PacketEntityUpdate extends ServerPacket {
	public int[] entityIds;
	public String[] entityTypes; // Entity type: "Player", "Item", etc.
	public String[] entityUUIDs; // Entity UUIDs for stable tracking across disconnects
	public float[] entityX;
	public float[] entityY;
	public float[] entityDX;
	public float[] entityDY;
	public int[] entityHealth;
	public boolean[] facingRight; // Entity facing direction
	public boolean[] dead; // Entity death state
	public long[] ticksAlive; // Entity animation timing
	public int[] ticksUnderwater; // Oxygen/drowning state (for LivingEntity)
	public String[] itemIds; // Item ID (for Item entities only, null for others)
	public String[] playerNames; // Player username (for Player entities only, null for others)
	public int[] damageFlashTicks; // Red flash duration (sync for all entities)

	// Movement states (LivingEntity fields)
	public boolean[] flying;
	public boolean[] noclip;
	public boolean[] sneaking;
	public boolean[] climbing;
	public boolean[] jumping;

	// Command effects (LivingEntity)
	public float[] speedMultiplier;

	// Player-specific fields
	public boolean[] backdropPlacementMode;

	// Hand targeting (Player fields)
	public int[] handTargetX;
	public int[] handTargetY;

	// Held item synchronization (Player fields) - FIX for invisible held items
	public int[] hotbarIndex; // Selected hotbar slot (0-8)
	public String[] selectedItemId; // Item ID of held item (null if empty)
	public int[] selectedItemCount; // Stack count
	public int[] selectedItemDurability; // Tool uses (0 if not a tool)

	// PERFORMANCE: Reusable ByteBuffer to avoid allocation in encode()
	// This is thread-safe because each SharedWorld instance has its own
	// cachedEntityPacket
	private ByteBuffer encodeBuffer = null;

	// CACHE: Cached encoded byte array to reuse across multiple connection sends
	// Invalidated when packet is modified (via ensureCapacity/reuse)
	private byte[] cachedData = null;

	public PacketEntityUpdate() {
	}

	/**
	 * PERFORMANCE: Ensure arrays have sufficient capacity, reusing existing arrays
	 * when possible
	 * This avoids allocating new arrays on every broadcast (60 Hz = 1560
	 * arrays/sec)
	 */
	public void ensureCapacity(int size) {
		// Reuse arrays if they exist and are large enough, otherwise allocate new ones
		if (entityIds == null || entityIds.length < size) {
			entityIds = new int[size];
			entityTypes = new String[size];
			entityUUIDs = new String[size];
			entityX = new float[size];
			entityY = new float[size];
			entityDX = new float[size];
			entityDY = new float[size];
			entityHealth = new int[size];
			facingRight = new boolean[size];
			dead = new boolean[size];
			ticksAlive = new long[size];
			ticksUnderwater = new int[size];
			itemIds = new String[size];
			playerNames = new String[size];
			flying = new boolean[size];
			noclip = new boolean[size];
			sneaking = new boolean[size];
			climbing = new boolean[size];
			jumping = new boolean[size];
			speedMultiplier = new float[size];
			backdropPlacementMode = new boolean[size];
			handTargetX = new int[size];
			handTargetY = new int[size];
			hotbarIndex = new int[size];
			selectedItemId = new String[size];
			selectedItemCount = new int[size];
			selectedItemDurability = new int[size];
			damageFlashTicks = new int[size];
		}
		// If arrays exist and are large enough, we reuse them (no allocation!)

		// Invalidate cache since we're about to modify the packet
		cachedData = null;
	}

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
		// Return cached data if available (avoid re-encoding for every client)
		if (cachedData != null) {
			return cachedData;
		}

		int count = (entityIds != null) ? entityIds.length : 0;

		// PERFORMANCE FIX: Eliminate double iteration by using estimated size
		// Old code iterated twice: once to calculate size, once to write
		// New code: Pre-allocate reasonable buffer size to avoid most resizing
		// For 10 entities: ~150 bytes per entity = ~1500 bytes total
		int estimatedSize = 4 + (count * 150); // Entity count + estimated per-entity size
		ByteBuffer buf = (encodeBuffer != null && encodeBuffer.capacity() >= estimatedSize)
				? encodeBuffer
				: ByteBuffer.allocate(Math.max(estimatedSize, 2048));
		buf.clear(); // Reset position to 0
		encodeBuffer = buf; // Cache for next encode()

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

			// Oxygen state
			buf.putInt(ticksUnderwater[i]);

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

			// Movement states (LivingEntity)
			buf.put((byte) (flying[i] ? 1 : 0));
			buf.put((byte) (noclip[i] ? 1 : 0));
			buf.put((byte) (sneaking[i] ? 1 : 0));
			buf.put((byte) (climbing[i] ? 1 : 0));
			buf.put((byte) (jumping[i] ? 1 : 0));

			// Command effects
			buf.putFloat(speedMultiplier[i]);

			// Player-specific
			buf.put((byte) (backdropPlacementMode[i] ? 1 : 0));

			// Hand targeting
			buf.putInt(handTargetX[i]);
			buf.putInt(handTargetY[i]);

			// Held item synchronization (FIX for invisible held items)
			buf.putInt(hotbarIndex[i]);
			String selectedItem = (selectedItemId[i] != null) ? selectedItemId[i] : "";
			byte[] selectedItemBytes = selectedItem.getBytes(StandardCharsets.UTF_8);
			buf.putShort((short) selectedItemBytes.length);
			buf.put(selectedItemBytes);
			buf.putInt(selectedItemCount[i]);
			buf.putInt(selectedItemDurability[i]);
			buf.putInt(damageFlashTicks[i]);
		}

		// PERFORMANCE: Return right-sized array (buffer may be larger than actual data)
		int actualSize = buf.position();
		if (actualSize == buf.capacity()) {
			byte[] result = buf.array(); // Perfect fit, no copy needed
			cachedData = result; // Cache the result
			return result;
		} else {
			// Buffer was larger than needed, create right-sized array
			byte[] result = new byte[actualSize];
			System.arraycopy(buf.array(), 0, result, 0, actualSize);
			System.arraycopy(buf.array(), 0, result, 0, actualSize);
			cachedData = result; // Cache the result
			return result;
		}
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
		packet.ticksUnderwater = new int[count];
		packet.itemIds = new String[count];
		packet.playerNames = new String[count];
		packet.flying = new boolean[count];
		packet.noclip = new boolean[count];
		packet.sneaking = new boolean[count];
		packet.climbing = new boolean[count];
		packet.jumping = new boolean[count];
		packet.speedMultiplier = new float[count];
		packet.backdropPlacementMode = new boolean[count];
		packet.handTargetX = new int[count];
		packet.handTargetY = new int[count];
		packet.hotbarIndex = new int[count];
		packet.selectedItemId = new String[count];
		packet.selectedItemCount = new int[count];
		packet.selectedItemDurability = new int[count];
		packet.damageFlashTicks = new int[count];

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

			// Oxygen state
			packet.ticksUnderwater[i] = buf.getInt();

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

			// Movement states (LivingEntity)
			packet.flying[i] = buf.get() == 1;
			packet.noclip[i] = buf.get() == 1;
			packet.sneaking[i] = buf.get() == 1;
			packet.climbing[i] = buf.get() == 1;
			packet.jumping[i] = buf.get() == 1;

			// Command effects
			packet.speedMultiplier[i] = buf.getFloat();

			// Player-specific
			packet.backdropPlacementMode[i] = buf.get() == 1;

			// Hand targeting
			packet.handTargetX[i] = buf.getInt();
			packet.handTargetY[i] = buf.getInt();

			// Held item synchronization (FIX for invisible held items)
			packet.hotbarIndex[i] = buf.getInt();
			short selectedItemLen = buf.getShort();
			byte[] selectedItemBytes = new byte[selectedItemLen];
			buf.get(selectedItemBytes);
			String selectedItem = new String(selectedItemBytes, StandardCharsets.UTF_8);
			packet.selectedItemId[i] = selectedItem.isEmpty() ? null : selectedItem;
			packet.selectedItemCount[i] = buf.getInt();
			packet.selectedItemDurability[i] = buf.getInt();
			packet.damageFlashTicks[i] = buf.getInt();
		}

		return packet;
	}
}
