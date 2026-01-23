/*
 * Copyright 2026 SaydaGames (mc_jojo3)
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

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Client → Server: Shift-click quick transfer
 * Sent when player shift-clicks a slot to quick transfer items between
 * inventories
 * Binary protocol: 4 ints + 1 byte = 17 bytes
 */
public class PacketShiftClick extends ClientPacket {
    public int slotX;
    public int slotY;
    public ContainerType containerType;
    public boolean isContainerSlot; // true = container slot, false = player inventory slot
    public int tableSizeAvailable; // 2 for regular inventory, 3 for crafting table (PLAYER_INVENTORY only)

    public enum ContainerType {
        PLAYER_INVENTORY, // Regular inventory (no container open)
        CHEST,
        FURNACE,
        ALCHEMY
    }

    public PacketShiftClick() {
        // Required for deserialization
    }

    public PacketShiftClick(int slotX, int slotY, ContainerType containerType, boolean isContainerSlot,
            int tableSizeAvailable) {
        this.slotX = slotX;
        this.slotY = slotY;
        this.tableSizeAvailable = tableSizeAvailable;
        this.containerType = containerType;
        this.isContainerSlot = isContainerSlot;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketShiftClick.class);
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleShiftClick(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(21); // 4 ints + 1 byte = 17, +4 for tableSizeAvailable = 21
        buf.putInt(slotX);
        buf.putInt(slotY);
        buf.putInt(containerType.ordinal());
        buf.put((byte) (isContainerSlot ? 1 : 0));
        buf.putInt(tableSizeAvailable);
        return buf.array();
    }

    public static PacketShiftClick decode(ByteBuffer buf) {
        int slotX = buf.getInt();
        int slotY = buf.getInt();
        ContainerType containerType = ContainerType.values()[buf.getInt()];
        boolean isContainerSlot = buf.get() == 1;
        int tableSizeAvailable = buf.getInt();
        return new PacketShiftClick(slotX, slotY, containerType, isContainerSlot, tableSizeAvailable);
    }
}
