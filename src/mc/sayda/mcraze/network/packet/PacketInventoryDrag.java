package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.ServerPacketHandler;
import mc.sayda.mcraze.util.Int2;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Client → Server: Drag items across multiple slots
 * Used for drag-to-split (left drag) and drag-to-place-one (right drag)
 */
public class PacketInventoryDrag extends ClientPacket {
    public List<Int2> slots; // Slots that were dragged over
    public boolean isEvenSplit; // true = left drag (distribute), false = right drag (1 per slot)
    public ContainerType containerType; // Which UI we're in

    public enum ContainerType {
        PLAYER_INVENTORY,
        CHEST,
        FURNACE
    }

    public PacketInventoryDrag() {
        this.slots = new ArrayList<>();
    }

    public PacketInventoryDrag(List<Int2> slots, boolean isEvenSplit, ContainerType containerType) {
        this.slots = new ArrayList<>(slots); // Clone list to prevent modification issues
        this.isEvenSplit = isEvenSplit;
        this.containerType = containerType;
    }

    @Override
    public int getPacketId() {
        return 15; // Next available client packet ID
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleInventoryDrag(this);
    }

    @Override
    public byte[] encode() {
        // Calculate size: 1 (boolean) + 1 (container type) + 2 (slot count) + slots (4
        // bytes each)
        int size = 1 + 1 + 2 + (slots.size() * 4);
        ByteBuffer buf = ByteBuffer.allocate(size);

        buf.put((byte) (isEvenSplit ? 1 : 0));
        buf.put((byte) containerType.ordinal());
        buf.putShort((short) slots.size());

        for (Int2 slot : slots) {
            buf.putShort((short) slot.x);
            buf.putShort((short) slot.y);
        }

        return buf.array();
    }

    public static PacketInventoryDrag decode(ByteBuffer buf) {
        PacketInventoryDrag packet = new PacketInventoryDrag();

        packet.isEvenSplit = (buf.get() == 1);
        packet.containerType = ContainerType.values()[buf.get()];

        short slotCount = buf.getShort();
        packet.slots = new ArrayList<>(slotCount);

        for (int i = 0; i < slotCount; i++) {
            int x = buf.getShort();
            int y = buf.getShort();
            packet.slots.add(new Int2(x, y));
        }

        return packet;
    }
}
