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

    void handleBackdropChange(PacketBackdropChange packet);

    void handleBackdropBatch(PacketBackdropBatch packet);

    void handleChestOpen(PacketChestOpen packet);

    void handleFurnaceOpen(PacketFurnaceOpen packet);

    void handleGameruleUpdate(PacketGameruleUpdate packet);

    void handlePong(PacketPong packet);

    void handleItemTrigger(PacketItemTrigger packet); // NEW: For special item effects

    void handlePlaySound(PacketPlaySound packet); // NEW: Play sound effects

    void handleWaveSync(PacketWaveSync packet); // NEW: Sync wave status

    void handleWorldBulkData(PacketWorldBulkData packet); // NEW: Compressed world loading

    void handleAlchemyOpen(PacketAlchemyOpen packet);

    void handleExplosion(PacketExplosion packet);
}
