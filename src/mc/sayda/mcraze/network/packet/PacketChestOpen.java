package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Server -> Client: Open a chest UI with the chest's inventory
 */
public class PacketChestOpen extends ServerPacket {
	public int chestX;
	public int chestY;
	public String[] itemIds; // Flattened 10x3 array (30 items)
	public int[] itemCounts; // Flattened 10x3 array (30 counts)
	public int[] toolUses; // Flattened 10x3 array (30 durability values)
	public String targetPlayerUUID; // Fix for broadcasting issue

	public PacketChestOpen() {
	}

	public PacketChestOpen(int x, int y, InventoryItem[][] items, String targetPlayerUUID) {
		this.chestX = x;
		this.chestY = y;
		this.targetPlayerUUID = targetPlayerUUID;

		// Flatten 10x3 array into 30-element arrays
		this.itemIds = new String[30];
		this.itemCounts = new int[30];
		this.toolUses = new int[30];

		int idx = 0;
		for (int slotY = 0; slotY < 3; slotY++) {
			for (int slotX = 0; slotX < 10; slotX++) {
				InventoryItem invItem = items[slotX][slotY];
				if (invItem != null && !invItem.isEmpty()) {
					itemIds[idx] = invItem.getItem().itemId;
					itemCounts[idx] = invItem.getCount();
					if (invItem.getItem() instanceof mc.sayda.mcraze.item.Tool) {
						toolUses[idx] = ((mc.sayda.mcraze.item.Tool) invItem.getItem()).uses;
					} else {
						toolUses[idx] = 0;
					}
				} else {
					itemIds[idx] = null;
					itemCounts[idx] = 0;
					toolUses[idx] = 0;
				}
				idx++;
			}
		}
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketChestOpen.class);
	}

	@Override
	public void handle(ClientPacketHandler handler) {
		handler.handleChestOpen(this);
	}

	@Override
	public byte[] encode() {
		// Calculate size: 4 (x) + 4 (y) + itemIds encoding + itemCounts (4 bytes each)
		// + toolUses (4 bytes each) + UUID field
		int size = 8;
		for (String id : itemIds) {
			size += 2 + (id != null ? id.length() : 0); // 2 bytes length + string
		}
		size += 30 * 4; // 30 counts, 4 bytes each
		size += 30 * 4; // 30 tool uses, 4 bytes each

		// Add size for UUID
		size += 2 + (targetPlayerUUID != null ? targetPlayerUUID.length() : 0);

		ByteBuffer buf = ByteBuffer.allocate(size);
		buf.putInt(chestX);
		buf.putInt(chestY);

		// Encode item IDs
		for (String id : itemIds) {
			if (id != null) {
				buf.putShort((short) id.length());
				buf.put(id.getBytes());
			} else {
				buf.putShort((short) 0);
			}
		}

		// Encode item counts
		for (int count : itemCounts) {
			buf.putInt(count);
		}

		// Encode tool uses
		for (int uses : toolUses) {
			buf.putInt(uses);
		}

		// Encode UUID
		if (targetPlayerUUID != null) {
			buf.putShort((short) targetPlayerUUID.length());
			buf.put(targetPlayerUUID.getBytes());
		} else {
			buf.putShort((short) 0);
		}

		return buf.array();
	}

	public static PacketChestOpen decode(ByteBuffer buf) {
		PacketChestOpen packet = new PacketChestOpen();
		packet.chestX = buf.getInt();
		packet.chestY = buf.getInt();

		packet.itemIds = new String[30];

		// Decode item IDs
		for (int i = 0; i < 30; i++) {
			short length = buf.getShort();
			if (length > 0) {
				byte[] bytes = new byte[length];
				buf.get(bytes);
				packet.itemIds[i] = new String(bytes);
			} else {
				packet.itemIds[i] = null;
			}
		}

		// Decode item counts
		packet.itemCounts = new int[30];
		for (int i = 0; i < 30; i++) {
			packet.itemCounts[i] = buf.getInt();
		}

		// Decode tool uses
		packet.toolUses = new int[30];
		for (int i = 0; i < 30; i++) {
			packet.toolUses[i] = buf.getInt();
		}

		// Decode UUID
		short uuidLen = buf.getShort();
		if (uuidLen > 0) {
			byte[] bytes = new byte[uuidLen];
			buf.get(bytes);
			packet.targetPlayerUUID = new String(bytes);
		} else {
			packet.targetPlayerUUID = null;
		}

		return packet;
	}
}
