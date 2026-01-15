package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Client → Server: Player performed an action in the furnace inventory
 */
public class PacketFurnaceAction extends ClientPacket {
    public int furnaceX;
    public int furnaceY;
    public int slotX;
    public int slotY;
    public boolean isFurnaceSlot; // true = furnace slot, false = player inventory slot
    public boolean isRightClick;

    public PacketFurnaceAction() {
    }

    public PacketFurnaceAction(int furnaceX, int furnaceY, int slotX, int slotY,
            boolean isFurnaceSlot, boolean isRightClick) {
        this.furnaceX = furnaceX;
        this.furnaceY = furnaceY;
        this.slotX = slotX;
        this.slotY = slotY;
        this.isFurnaceSlot = isFurnaceSlot;
        this.isRightClick = isRightClick;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketFurnaceAction.class);
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleFurnaceAction(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(14);
        buf.putInt(furnaceX);
        buf.putInt(furnaceY);
        buf.putShort((short) slotX);
        buf.putShort((short) slotY);
        buf.put((byte) (isFurnaceSlot ? 1 : 0));
        buf.put((byte) (isRightClick ? 1 : 0));
        return buf.array();
    }

    public static PacketFurnaceAction decode(ByteBuffer buf) {
        PacketFurnaceAction packet = new PacketFurnaceAction();
        packet.furnaceX = buf.getInt();
        packet.furnaceY = buf.getInt();
        packet.slotX = buf.getShort();
        packet.slotY = buf.getShort();
        packet.isFurnaceSlot = buf.get() == 1;
        packet.isRightClick = buf.get() == 1;
        return packet;
    }
}
