/*
 * Copyright 2025 SaydaGames (mc_jojo3)
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
 * Client → Server: Player attacks an entity
 * Server determines damage from player's held item
 */
public class PacketEntityAttack extends ClientPacket {
    public int entityId;  // The entity being attacked

    public PacketEntityAttack() {}

    public PacketEntityAttack(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketEntityAttack.class);
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleEntityAttack(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(entityId);
        return buf.array();
    }

    public static PacketEntityAttack decode(ByteBuffer buf) {
        int entityId = buf.getInt();
        return new PacketEntityAttack(entityId);
    }
}
