package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;
import java.util.UUID;

public class PacketAlchemyOpen extends ServerPacket {
    public int blockX, blockY;
    public String[] itemIds; // 5 slots
    public int[] itemCounts;
    public int[] toolUses;
    public int storedEssence;
    public int recipeCost; // [NEW] Cost of the current recipe
    public int brewProgress;
    public int brewTimeTotal;
    public String targetPlayerUUID;
    public boolean isUpdate;

    public PacketAlchemyOpen() {
    }

    public PacketAlchemyOpen(int blockX, int blockY, InventoryItem[] items, int essence, int progress, int total,
            String targetPlayerUUID, boolean isUpdate, int recipeCost) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.storedEssence = essence;
        this.recipeCost = recipeCost;
        this.brewProgress = progress;
        this.brewTimeTotal = total;
        this.targetPlayerUUID = targetPlayerUUID;
        this.isUpdate = isUpdate;

        this.itemIds = new String[5];
        this.itemCounts = new int[5];
        this.toolUses = new int[5];

        for (int i = 0; i < 5; i++) {
            InventoryItem invItem = items[i];
            if (invItem != null && !invItem.isEmpty()) {
                itemIds[i] = invItem.getItem().itemId;
                itemCounts[i] = invItem.getCount();
                if (invItem.getItem() instanceof mc.sayda.mcraze.item.Tool) {
                    toolUses[i] = ((mc.sayda.mcraze.item.Tool) invItem.getItem()).uses;
                }
            } else {
                itemIds[i] = null;
                itemCounts[i] = 0;
            }
        }
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketAlchemyOpen.class);
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handleAlchemyOpen(this);
    }

    @Override
    public byte[] encode() {
        // Calculate size
        int size = 4 + 4 + 4 + 4 + 4 + 4; // blockX, blockY, essence, cost, progress, total
        size += (targetPlayerUUID != null ? targetPlayerUUID.length() + 2 : 2); // UUID string + len
        size += 1; // isUpdate boolean

        for (String id : itemIds) {
            size += 2 + (id != null ? id.length() : 0);
        }
        size += 5 * 4; // counts
        size += 5 * 4; // tool uses

        ByteBuffer buf = ByteBuffer.allocate(size);
        buf.putInt(blockX);
        buf.putInt(blockY);
        buf.putInt(storedEssence);
        buf.putInt(recipeCost);
        buf.putInt(brewProgress);
        buf.putInt(brewTimeTotal);

        if (targetPlayerUUID != null) {
            buf.putShort((short) targetPlayerUUID.length());
            buf.put(targetPlayerUUID.getBytes());
        } else {
            buf.putShort((short) 0);
        }

        buf.put((byte) (isUpdate ? 1 : 0));

        for (String id : itemIds) {
            if (id != null) {
                buf.putShort((short) id.length());
                buf.put(id.getBytes());
            } else {
                buf.putShort((short) 0);
            }
        }

        for (int count : itemCounts)
            buf.putInt(count);
        for (int uses : toolUses)
            buf.putInt(uses);

        return buf.array();
    }

    public static PacketAlchemyOpen decode(ByteBuffer buf) {
        PacketAlchemyOpen packet = new PacketAlchemyOpen();
        packet.blockX = buf.getInt();
        packet.blockY = buf.getInt();
        packet.storedEssence = buf.getInt();
        packet.recipeCost = buf.getInt();
        packet.brewProgress = buf.getInt();
        packet.brewTimeTotal = buf.getInt();

        short uuidLen = buf.getShort();
        if (uuidLen > 0) {
            byte[] b = new byte[uuidLen];
            buf.get(b);
            packet.targetPlayerUUID = new String(b);
        }

        packet.isUpdate = buf.get() == 1;

        packet.itemIds = new String[5];
        for (int i = 0; i < 5; i++) {
            short len = buf.getShort();
            if (len > 0) {
                byte[] bytes = new byte[len];
                buf.get(bytes);
                packet.itemIds[i] = new String(bytes);
            }
        }

        packet.itemCounts = new int[5];
        for (int i = 0; i < 5; i++)
            packet.itemCounts[i] = buf.getInt();
        packet.toolUses = new int[5];
        for (int i = 0; i < 5; i++)
            packet.toolUses[i] = buf.getInt();

        return packet;
    }
}
