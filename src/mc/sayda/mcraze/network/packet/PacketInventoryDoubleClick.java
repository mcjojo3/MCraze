package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.ServerPacketHandler;
import java.nio.ByteBuffer;

/**
 * Client → Server: Double-click on inventory slot to collect matching items
 * Collects all matching items from entire visible UI into cursor
 */
public class PacketInventoryDoubleClick extends ClientPacket {
    public int slotX;
    public int slotY;
    public ContainerType containerType; // Which UI we're in

    public enum ContainerType {
        PLAYER_INVENTORY,
        CHEST,
        FURNACE
    }

    public PacketInventoryDoubleClick() {
    }

    public PacketInventoryDoubleClick(int slotX, int slotY, ContainerType containerType) {
        this.slotX = slotX;
        this.slotY = slotY;
        this.containerType = containerType;
    }

    @Override
    public int getPacketId() {
        return 16; // Next available client packet ID
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleInventoryDoubleClick(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(5); // 2 + 2 + 1
        buf.putShort((short) slotX);
        buf.putShort((short) slotY);
        buf.put((byte) containerType.ordinal());
        return buf.array();
    }

    public static PacketInventoryDoubleClick decode(ByteBuffer buf) {
        PacketInventoryDoubleClick packet = new PacketInventoryDoubleClick();
        packet.slotX = buf.getShort();
        packet.slotY = buf.getShort();
        packet.containerType = ContainerType.values()[buf.get()];
        return packet;
    }
}
