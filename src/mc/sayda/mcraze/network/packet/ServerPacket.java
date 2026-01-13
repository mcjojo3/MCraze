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

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.Packet;

import java.nio.ByteBuffer;

/**
 * Base class for packets sent FROM server TO client.
 * All server packets must extend this class.
 */
public abstract class ServerPacket extends Packet {
    /**
     * Handle this packet on the client side
     * 
     * @param handler Client packet handler
     */
    public abstract void handle(ClientPacketHandler handler);

    /**
     * Encode packet data to binary format
     * 
     * @return Binary data (without packet ID header)
     */
    public abstract byte[] encode();

    /**
     * Get the packet ID for this packet type
     * Defined by PacketRegistry for each packet class
     */
    public abstract int getPacketId();
}
