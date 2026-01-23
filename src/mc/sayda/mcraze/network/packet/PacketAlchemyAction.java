package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.ServerPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

public class PacketAlchemyAction extends ClientPacket {
    public int blockX, blockY;
    public int slotIdx;
    public boolean isAlchemySlot; // true = table, false = player inv
    public boolean isRightClick;
    public boolean isDepositEssence; // Special action to deposit essence

    public PacketAlchemyAction() {
    }

    public PacketAlchemyAction(int bx, int by, int slot, boolean alchemy, boolean right, boolean deposit) {
        this.blockX = bx;
        this.blockY = by;
        this.slotIdx = slot;
        this.isAlchemySlot = alchemy;
        this.isRightClick = right;
        this.isDepositEssence = deposit;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketAlchemyAction.class);
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleAlchemyAction(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(4 + 4 + 4 + 1 + 1 + 1);
        buf.putInt(blockX);
        buf.putInt(blockY);
        buf.putInt(slotIdx);
        buf.put((byte) (isAlchemySlot ? 1 : 0));
        buf.put((byte) (isRightClick ? 1 : 0));
        buf.put((byte) (isDepositEssence ? 1 : 0));
        return buf.array();
    }

    public static PacketAlchemyAction decode(ByteBuffer buf) {
        PacketAlchemyAction packet = new PacketAlchemyAction();
        packet.blockX = buf.getInt();
        packet.blockY = buf.getInt();
        packet.slotIdx = buf.getInt();
        packet.isAlchemySlot = buf.get() == 1;
        packet.isRightClick = buf.get() == 1;
        packet.isDepositEssence = buf.get() == 1;
        return packet;
    }
}
