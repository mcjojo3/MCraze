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

package mc.sayda.mcraze.network;

import mc.sayda.mcraze.network.packet.*;

/**
 * Handler for packets received by the CLIENT (from server).
 * Client implements this interface to handle server-bound packets.
 */
public interface ClientPacketHandler {
    // Server → Client packets
    void handleWorldInit(PacketWorldInit packet);
    void handleWorldUpdate(PacketWorldUpdate packet);
    void handleEntityUpdate(PacketEntityUpdate packet);
    void handleEntityRemove(PacketEntityRemove packet);
    void handleInventoryUpdate(PacketInventoryUpdate packet);
    void handleChatMessage(PacketChatMessage packet);
    void handlePlayerDeath(PacketPlayerDeath packet);
    void handlePlayerRespawn(PacketPlayerRespawn packet);
    void handleBreakingProgress(PacketBreakingProgress packet);
    void handleAuthResponse(PacketAuthResponse packet);
    void handleBiomeData(PacketBiomeData packet);
    void handleBackdropChange(PacketBackdropChange packet);  // NEW: For backdrop sync
    void handleBackdropBatch(PacketBackdropBatch packet);  // NEW: For batched backdrop sync
    void handleChestOpen(PacketChestOpen packet);  // NEW: For opening chest UI
    void handleGameruleUpdate(PacketGameruleUpdate packet);  // NEW: For syncing gamerule changes
    void handlePong(PacketPong packet);  // NEW: For latency measurement
}
