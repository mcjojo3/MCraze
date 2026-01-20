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
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Server → Client: Sync Wave Status (Day, Active, Difficulty)
 * Binary protocol: 14 bytes (int + byte + float + float)
 */
public class PacketWaveSync extends ServerPacket {
    public int waveDay;
    public boolean waveActive;
    public float diffMultHP;
    public float diffMultDmg;
    public float diffMultJump;

    public PacketWaveSync() {
    }

    public PacketWaveSync(int waveDay, boolean waveActive, float diffMultHP, float diffMultDmg, float diffMultJump) {
        this.waveDay = waveDay;
        this.waveActive = waveActive;
        this.diffMultHP = diffMultHP;
        this.diffMultDmg = diffMultDmg;
        this.diffMultJump = diffMultJump;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketWaveSync.class);
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handleWaveSync(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(18); // 14 -> 18 bytes
        buf.putInt(waveDay);
        buf.put((byte) (waveActive ? 1 : 0));
        buf.putFloat(diffMultHP);
        buf.putFloat(diffMultDmg);
        buf.putFloat(diffMultJump);
        return buf.array();
    }

    public static PacketWaveSync decode(ByteBuffer buf) {
        PacketWaveSync packet = new PacketWaveSync();
        packet.waveDay = buf.getInt();
        packet.waveActive = buf.get() == 1;
        packet.diffMultHP = buf.getFloat();
        packet.diffMultDmg = buf.getFloat();
        packet.diffMultJump = buf.getFloat();
        return packet;
    }
}
