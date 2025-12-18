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
 * Handler interface for processing received packets.
 * Implemented by both Client and Server to handle their respective packet types.
 */
public interface PacketHandler {
	// Client-bound packets (sent by server, handled by client)
	void handleWorldInit(PacketWorldInit packet);
	void handleWorldUpdate(PacketWorldUpdate packet);
	void handleEntityUpdate(PacketEntityUpdate packet);
	void handleEntityRemove(PacketEntityRemove packet);
	void handleInventoryUpdate(PacketInventoryUpdate packet);
	void handleChatMessage(PacketChatMessage packet);
	void handlePlayerDeath(PacketPlayerDeath packet);
	void handleBreakingProgress(PacketBreakingProgress packet);

	// Server-bound packets (sent by client, handled by server)
	void handlePlayerInput(PacketPlayerInput packet);
	void handleBlockChange(PacketBlockChange packet);
	void handleChatSend(PacketChatSend packet);
	void handleAuthRequest(PacketAuthRequest packet);

	// Bidirectional packets
	void handleAuthResponse(PacketAuthResponse packet);
}
