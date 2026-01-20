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
 * Client → Server: Player interaction with a block (e.g., crafting table,
 * chest)
 * Fixes the crafting table interaction bug by separating interaction from
 * placement.
 */
public class PacketInteract extends ClientPacket {
    public int blockX;
    public int blockY;
    public InteractionType type;

    public PacketInteract() {
    }

    public PacketInteract(int blockX, int blockY, InteractionType type) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.type = type;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketInteract.class);
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleInteract(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(9); // 4 + 4 + 1
        buf.putInt(blockX);
        buf.putInt(blockY);
        buf.put((byte) type.ordinal());
        return buf.array();
    }

    public static PacketInteract decode(ByteBuffer buf) {
        int blockX = buf.getInt();
        int blockY = buf.getInt();
        InteractionType type = InteractionType.values()[buf.get()];
        return new PacketInteract(blockX, blockY, type);
    }

    /**
     * Types of block interactions
     */
    public enum InteractionType {
        OPEN_CRAFTING, // Open 3x3 crafting table
        OPEN_CHEST, // Open chest inventory (future)
        OPEN_FURNACE, // Open furnace UI (future)
        TOGGLE_INVENTORY, // Toggle inventory open/closed (E key)
        INTERACT // Generic interaction (e.g. right-click trapdoor)
    }
}
