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

import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;

/**
 * Base class for packets sent FROM client TO server.
 * All client packets must extend this class.
 */
public abstract class ClientPacket extends Packet {
    /**
     * Handle this packet on the server side
     * @param handler Server packet handler
     */
    public abstract void handle(ServerPacketHandler handler);

    /**
     * Encode packet data to binary format
     * @return Binary data (without packet ID header)
     */
    public abstract byte[] encode();

    /**
     * Get the packet ID for this packet type
     * Defined by PacketRegistry for each packet class
     */
    public abstract int getPacketId();
}
