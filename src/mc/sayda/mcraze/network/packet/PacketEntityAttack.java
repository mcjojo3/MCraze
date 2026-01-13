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
 * Client → Server: Player attacks an entity
 * Server determines damage from player's held item
 */
public class PacketEntityAttack extends ClientPacket {
    public String entityUUID; // The entity being attacked

    public PacketEntityAttack() {
    }

    public PacketEntityAttack(String entityUUID) {
        this.entityUUID = entityUUID;
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
        if (entityUUID == null)
            entityUUID = "";
        byte[] uuidBytes = entityUUID.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(2 + uuidBytes.length);
        buf.putShort((short) uuidBytes.length);
        buf.put(uuidBytes);
        return buf.array();
    }

    public static PacketEntityAttack decode(ByteBuffer buf) {
        short len = buf.getShort();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        String entityUUID = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return new PacketEntityAttack(entityUUID);
    }
}
