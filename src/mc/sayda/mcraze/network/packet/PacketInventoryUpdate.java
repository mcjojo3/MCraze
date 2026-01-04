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
 * Server → Client: Inventory state synchronization
 * Sends inventory data (item IDs, counts, hotbar index) and UI state for the player
 * Binary protocol: UUID string + count + item arrays + metadata
 */
public class PacketInventoryUpdate extends ServerPacket {
	// Player identification
	public String playerUUID;  // UUID of the player this inventory belongs to

	// Flattened inventory grid: inventoryWidth * inventoryHeight
	public String[] itemIds;  // Item ID strings (null for empty slots)
	public int[] itemCounts;  // Item counts
	public int[] toolUses;    // Tool durability (0 for non-tools)
	public int hotbarIndex;   // Current hotbar selection

	// Inventory UI state
	public boolean visible;           // Whether inventory is open
	public int tableSizeAvailable;    // 0 = no crafting, 2 = 2x2, 3 = 3x3

	// Crafting preview (craftable item)
	public String craftableItemId;    // Item ID of craftable result (null if none)
	public int craftableCount;        // Stack count of craftable result
	public int craftableToolUse;      // Tool durability of craftable result

	// Cursor item (holding item) synchronization
	public String holdingItemId;      // Item ID of item on cursor (null if empty)
	public int holdingCount;          // Stack count of cursor item
	public int holdingToolUse;        // Tool durability (0 if not a tool)

	public PacketInventoryUpdate() {}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketInventoryUpdate.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleInventoryUpdate(this);
	}

	@Override
	public byte[] encode() {
		byte[] uuidBytes = playerUUID.getBytes(StandardCharsets.UTF_8);
		int count = (itemIds != null) ? itemIds.length : 0;

		// Calculate total size
		int totalSize = 2 + uuidBytes.length + 4;  // UUID + count
		for (int i = 0; i < count; i++) {
			String itemId = (itemIds[i] != null) ? itemIds[i] : "";
			totalSize += 2 + itemId.getBytes(StandardCharsets.UTF_8).length;  // Each item ID
			totalSize += 4;  // itemCount
			totalSize += 4;  // toolUse
		}
		totalSize += 4 + 1 + 4;  // hotbarIndex + visible + tableSizeAvailable

		// Add craftable item size
		String craftableId = (craftableItemId != null) ? craftableItemId : "";
		totalSize += 2 + craftableId.getBytes(StandardCharsets.UTF_8).length;  // craftableItemId
		totalSize += 4;  // craftableCount
		totalSize += 4;  // craftableToolUse

		// Add holding item size
		String holdingId = (holdingItemId != null) ? holdingItemId : "";
		totalSize += 2 + holdingId.getBytes(StandardCharsets.UTF_8).length;  // holdingItemId
		totalSize += 4;  // holdingCount
		totalSize += 4;  // holdingToolUse

		ByteBuffer buf = ByteBuffer.allocate(totalSize);

		// Write UUID
		buf.putShort((short) uuidBytes.length);
		buf.put(uuidBytes);

		// Write inventory array count
		buf.putInt(count);

		// Write each inventory slot
		for (int i = 0; i < count; i++) {
			String itemId = (itemIds[i] != null) ? itemIds[i] : "";
			byte[] itemIdBytes = itemId.getBytes(StandardCharsets.UTF_8);
			buf.putShort((short) itemIdBytes.length);
			buf.put(itemIdBytes);
			buf.putInt(itemCounts[i]);
			buf.putInt(toolUses[i]);
		}

		// Write metadata
		buf.putInt(hotbarIndex);
		buf.put((byte) (visible ? 1 : 0));
		buf.putInt(tableSizeAvailable);

		// Write craftable item
		byte[] craftableIdBytes = craftableId.getBytes(StandardCharsets.UTF_8);
		buf.putShort((short) craftableIdBytes.length);
		buf.put(craftableIdBytes);
		buf.putInt(craftableCount);
		buf.putInt(craftableToolUse);

		// Write holding item (cursor item)
		byte[] holdingIdBytes = holdingId.getBytes(StandardCharsets.UTF_8);
		buf.putShort((short) holdingIdBytes.length);
		buf.put(holdingIdBytes);
		buf.putInt(holdingCount);
		buf.putInt(holdingToolUse);

		return buf.array();
	}

	public static PacketInventoryUpdate decode(ByteBuffer buf) {
		PacketInventoryUpdate packet = new PacketInventoryUpdate();

		// Read UUID
		short uuidLen = buf.getShort();
		byte[] uuidBytes = new byte[uuidLen];
		buf.get(uuidBytes);
		packet.playerUUID = new String(uuidBytes, StandardCharsets.UTF_8);

		// Read inventory array count
		int count = buf.getInt();
		packet.itemIds = new String[count];
		packet.itemCounts = new int[count];
		packet.toolUses = new int[count];

		// Read each inventory slot
		for (int i = 0; i < count; i++) {
			short itemIdLen = buf.getShort();
			byte[] itemIdBytes = new byte[itemIdLen];
			buf.get(itemIdBytes);
			String itemId = new String(itemIdBytes, StandardCharsets.UTF_8);
			packet.itemIds[i] = itemId.isEmpty() ? null : itemId;
			packet.itemCounts[i] = buf.getInt();
			packet.toolUses[i] = buf.getInt();
		}

		// Read metadata
		packet.hotbarIndex = buf.getInt();
		packet.visible = buf.get() == 1;
		packet.tableSizeAvailable = buf.getInt();

		// Read craftable item
		short craftableIdLen = buf.getShort();
		byte[] craftableIdBytes = new byte[craftableIdLen];
		buf.get(craftableIdBytes);
		String craftableId = new String(craftableIdBytes, StandardCharsets.UTF_8);
		packet.craftableItemId = craftableId.isEmpty() ? null : craftableId;
		packet.craftableCount = buf.getInt();
		packet.craftableToolUse = buf.getInt();

		// Read holding item (cursor item)
		short holdingIdLen = buf.getShort();
		byte[] holdingIdBytes = new byte[holdingIdLen];
		buf.get(holdingIdBytes);
		String holdingId = new String(holdingIdBytes, StandardCharsets.UTF_8);
		packet.holdingItemId = holdingId.isEmpty() ? null : holdingId;
		packet.holdingCount = buf.getInt();
		packet.holdingToolUse = buf.getInt();

		return packet;
	}
}
