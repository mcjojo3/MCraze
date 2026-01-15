package mc.sayda.mcraze.network.packet;

import java.nio.ByteBuffer;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

/**
 * Packet sent from server to client to open furnace UI and sync furnace
 * contents
 */
public class PacketFurnaceOpen extends ServerPacket {
    public int furnaceX;
    public int furnaceY;
    public String[] itemIds; // Flattened 3x2 array (6 slots)
    public int[] itemCounts;
    public int[] toolUses; // Added tool durability sync
    public int fuelTimeRemaining;
    public int smeltProgress;
    public int smeltTimeTotal;
    public String targetPlayerUUID;
    public boolean isUpdate; // True if just updating data, false if should force open

    public PacketFurnaceOpen() {
    }

    public PacketFurnaceOpen(int x, int y, InventoryItem[][] items,
            int fuelTime, int smeltProg, int smeltTotal, String targetUUID, boolean isUpdate) {
        this.furnaceX = x;
        this.furnaceY = y;
        this.fuelTimeRemaining = fuelTime;
        this.smeltProgress = smeltProg;
        this.smeltTimeTotal = smeltTotal;
        this.targetPlayerUUID = targetUUID;
        this.isUpdate = isUpdate;

        // Flatten 3x2 furnace array (6 slots: input, fuel, output positions)
        this.itemIds = new String[6];
        this.itemCounts = new int[6];
        this.toolUses = new int[6];

        int idx = 0;
        for (int slotY = 0; slotY < 2; slotY++) {
            for (int slotX = 0; slotX < 3; slotX++) {
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
        return PacketRegistry.getId(PacketFurnaceOpen.class);
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handleFurnaceOpen(this);
    }

    @Override
    public byte[] encode() {
        int size = 8; // furnaceX + furnaceY
        for (String id : itemIds) {
            size += 2 + (id != null ? id.length() : 0);
        }
        size += 6 * 4; // 6 counts
        size += 6 * 4; // 6 tool uses
        size += 4 + 4 + 4; // fuel, progress, total
        size += 2 + (targetPlayerUUID != null ? targetPlayerUUID.length() : 0);
        size += 1; // isUpdate

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(furnaceX);
        buf.putInt(furnaceY);

        // Encode item IDs
        for (String id : itemIds) {
            if (id != null) {
                buf.putShort((short) id.length());
                buf.put(id.getBytes());
            } else {
                buf.putShort((short) 0);
            }
        }

        // Encode counts
        for (int count : itemCounts) {
            buf.putInt(count);
        }

        // Encode tool uses
        for (int uses : toolUses) {
            buf.putInt(uses);
        }

        // Encode state
        buf.putInt(fuelTimeRemaining);
        buf.putInt(smeltProgress);
        buf.putInt(smeltTimeTotal);

        // Encode UUID
        if (targetPlayerUUID != null) {
            buf.putShort((short) targetPlayerUUID.length());
            buf.put(targetPlayerUUID.getBytes());
        } else {
            buf.putShort((short) 0);
        }

        buf.put((byte) (isUpdate ? 1 : 0));

        return buf.array();
    }

    public static PacketFurnaceOpen decode(ByteBuffer buf) {
        PacketFurnaceOpen packet = new PacketFurnaceOpen();
        packet.furnaceX = buf.getInt();
        packet.furnaceY = buf.getInt();

        packet.itemIds = new String[6];
        for (int i = 0; i < 6; i++) {
            short length = buf.getShort();
            if (length > 0) {
                byte[] bytes = new byte[length];
                buf.get(bytes);
                packet.itemIds[i] = new String(bytes);
            } else {
                packet.itemIds[i] = null;
            }
        }

        packet.itemCounts = new int[6];
        for (int i = 0; i < 6; i++) {
            packet.itemCounts[i] = buf.getInt();
        }

        packet.toolUses = new int[6];
        for (int i = 0; i < 6; i++) {
            packet.toolUses[i] = buf.getInt();
        }

        packet.fuelTimeRemaining = buf.getInt();
        packet.smeltProgress = buf.getInt();
        packet.smeltTimeTotal = buf.getInt();

        short uuidLen = buf.getShort();
        if (uuidLen > 0) {
            byte[] bytes = new byte[uuidLen];
            buf.get(bytes);
            packet.targetPlayerUUID = new String(bytes);
        } else {
            packet.targetPlayerUUID = null;
        }

        packet.isUpdate = buf.get() == 1;

        return packet;
    }
}
