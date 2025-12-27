package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Client -> Server: Player performed an action in the chest inventory
 * Reuses the same slot-clicking logic as regular inventory
 */
public class PacketChestAction extends ClientPacket {
	public int chestX;
	public int chestY;
	public int slotX;
	public int slotY;
	public boolean isChestSlot;  // true = chest slot, false = player inventory slot
	public boolean isRightClick;

	public PacketChestAction() {}

	public PacketChestAction(int chestX, int chestY, int slotX, int slotY, boolean isChestSlot, boolean isRightClick) {
		this.chestX = chestX;
		this.chestY = chestY;
		this.slotX = slotX;
		this.slotY = slotY;
		this.isChestSlot = isChestSlot;
		this.isRightClick = isRightClick;
	}

	@Override
	public int getPacketId() {
		return PacketRegistry.getId(PacketChestAction.class);
	}

	@Override
	public void handle(ServerPacketHandler handler) {
		handler.handleChestAction(this);
	}

	@Override
	public byte[] encode() {
		ByteBuffer buf = ByteBuffer.allocate(14);
		buf.putInt(chestX);
		buf.putInt(chestY);
		buf.putShort((short) slotX);
		buf.putShort((short) slotY);
		buf.put((byte) (isChestSlot ? 1 : 0));
		buf.put((byte) (isRightClick ? 1 : 0));
		return buf.array();
	}

	public static PacketChestAction decode(ByteBuffer buf) {
		PacketChestAction packet = new PacketChestAction();
		packet.chestX = buf.getInt();
		packet.chestY = buf.getInt();
		packet.slotX = buf.getShort();
		packet.slotY = buf.getShort();
		packet.isChestSlot = buf.get() == 1;
		packet.isRightClick = buf.get() == 1;
		return packet;
	}
}
