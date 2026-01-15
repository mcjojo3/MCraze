package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Packet sent from server to client to trigger special item effects
 */
public class PacketItemTrigger extends ServerPacket {
    public String itemId;

    public PacketItemTrigger() {
        // Default constructor for decoding
    }

    public PacketItemTrigger(String itemId) {
        this.itemId = itemId;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketItemTrigger.class);
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handleItemTrigger(this);
    }

    @Override
    public byte[] encode() {
        byte[] itemIdBytes = itemId.getBytes();
        ByteBuffer buf = ByteBuffer.allocate(2 + itemIdBytes.length);
        buf.putShort((short) itemIdBytes.length);
        buf.put(itemIdBytes);
        return buf.array();
    }

    public static PacketItemTrigger decode(ByteBuffer buf) {
        PacketItemTrigger packet = new PacketItemTrigger();
        short length = buf.getShort();
        if (length > 0) {
            byte[] bytes = new byte[length];
            buf.get(bytes);
            packet.itemId = new String(bytes);
        }
        return packet;
    }
}
