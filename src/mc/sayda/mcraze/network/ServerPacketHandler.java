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
 * Handler for packets received by the SERVER (from client).
 * Server/PlayerConnection implements this interface to handle client-bound packets.
 */
public interface ServerPacketHandler {
    // Client → Server packets
    void handlePlayerInput(PacketPlayerInput packet);
    void handleBlockChange(PacketBlockChange packet);
    void handleInventoryAction(PacketInventoryAction packet);
    void handleChatSend(PacketChatSend packet);
    void handleAuthRequest(PacketAuthRequest packet);
    void handleInteract(PacketInteract packet);  // NEW: For crafting table interaction
    void handleItemToss(PacketItemToss packet);  // NEW: For item tossing
    void handleRespawn(PacketRespawn packet);    // NEW: For player respawning
    void handleToggleBackdropMode(PacketToggleBackdropMode packet);  // NEW: For backdrop mode toggle
    void handleChestAction(PacketChestAction packet);  // NEW: For chest inventory actions
}
