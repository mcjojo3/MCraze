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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Centralized packet registry with non-conflicting IDs.
 * Handles packet ID assignment and deserialization.
 */
public class PacketRegistry {
    // Maps packet ID to decoder function
    private static final Map<Integer, Function<ByteBuffer, Packet>> DECODERS = new HashMap<>();

    // Maps packet class to ID
    private static final Map<Class<? extends Packet>, Integer> CLASS_TO_ID = new HashMap<>();

    static {
        // Client → Server packets (IDs 1-50)
        registerClientPacket(1, PacketPlayerInput.class, PacketPlayerInput::decode);
        registerClientPacket(2, PacketBlockChange.class, PacketBlockChange::decode);
        registerClientPacket(3, PacketChatSend.class, PacketChatSend::decode);
        registerClientPacket(4, PacketInventoryAction.class, PacketInventoryAction::decode);
        registerClientPacket(5, PacketAuthRequest.class, PacketAuthRequest::decode);
        registerClientPacket(6, PacketInteract.class, PacketInteract::decode);
        registerClientPacket(7, PacketItemToss.class, PacketItemToss::decode);
        registerClientPacket(8, PacketRespawn.class, PacketRespawn::decode);
        registerClientPacket(9, PacketToggleBackdropMode.class, PacketToggleBackdropMode::decode);
        registerClientPacket(10, PacketChestAction.class, PacketChestAction::decode);
        registerClientPacket(11, PacketEntityAttack.class, PacketEntityAttack::decode);
        registerClientPacket(12, PacketPing.class, PacketPing::decode);
        registerClientPacket(13, PacketFurnaceAction.class, PacketFurnaceAction::decode);

        // Server → Client packets (IDs 51-100)
        registerServerPacket(51, PacketWorldInit.class, PacketWorldInit::decode);
        registerServerPacket(52, PacketWorldUpdate.class, PacketWorldUpdate::decode);
        registerServerPacket(53, PacketEntityUpdate.class, PacketEntityUpdate::decode);
        registerServerPacket(54, PacketEntityRemove.class, PacketEntityRemove::decode);
        registerServerPacket(55, PacketInventoryUpdate.class, PacketInventoryUpdate::decode);
        registerServerPacket(56, PacketChatMessage.class, PacketChatMessage::decode);
        registerServerPacket(57, PacketPlayerDeath.class, PacketPlayerDeath::decode);
        registerServerPacket(58, PacketBreakingProgress.class, PacketBreakingProgress::decode);
        registerServerPacket(59, PacketAuthResponse.class, PacketAuthResponse::decode);
        registerServerPacket(60, PacketPlayerRespawn.class, PacketPlayerRespawn::decode);
        registerServerPacket(61, PacketBiomeData.class, PacketBiomeData::decode);
        registerServerPacket(62, PacketBackdropChange.class, PacketBackdropChange::decode);
        registerServerPacket(63, PacketChestOpen.class, PacketChestOpen::decode);
        registerServerPacket(64, PacketGameruleUpdate.class, PacketGameruleUpdate::decode);
        registerServerPacket(65, PacketBackdropBatch.class, PacketBackdropBatch::decode); // NEW - Batched backdrops
        registerServerPacket(66, PacketPong.class, PacketPong::decode);
        registerServerPacket(67, PacketFurnaceOpen.class, PacketFurnaceOpen::decode);
    }

    /**
     * Register a client packet (C→S)
     */
    private static void registerClientPacket(int id, Class<? extends Packet> clazz,
            Function<ByteBuffer, Packet> decoder) {
        register(id, clazz, decoder);
    }

    /**
     * Register a server packet (S→C)
     */
    private static void registerServerPacket(int id, Class<? extends Packet> clazz,
            Function<ByteBuffer, Packet> decoder) {
        register(id, clazz, decoder);
    }

    /**
     * Register a packet with ID and decoder
     */
    private static void register(int id, Class<? extends Packet> clazz, Function<ByteBuffer, Packet> decoder) {
        if (DECODERS.containsKey(id)) {
            throw new IllegalStateException("Duplicate packet ID: " + id + " for " + clazz.getSimpleName());
        }
        DECODERS.put(id, decoder);
        CLASS_TO_ID.put(clazz, id);
    }

    /**
     * Get packet ID for a packet class
     */
    public static int getId(Class<? extends Packet> clazz) {
        Integer id = CLASS_TO_ID.get(clazz);
        if (id == null) {
            throw new IllegalArgumentException("Unregistered packet class: " + clazz.getSimpleName());
        }
        return id;
    }

    /**
     * Decode a packet from binary data (without ID header)
     * 
     * @param id     Packet ID (read from stream)
     * @param buffer Data buffer positioned after ID
     * @return Decoded packet
     */
    public static Packet decode(int id, ByteBuffer buffer) {
        Function<ByteBuffer, Packet> decoder = DECODERS.get(id);
        if (decoder == null) {
            throw new IllegalArgumentException("Unknown packet ID: " + id);
        }
        return decoder.apply(buffer);
    }

    /**
     * Check if a packet ID is registered
     */
    public static boolean isRegistered(int id) {
        return DECODERS.containsKey(id);
    }
}
