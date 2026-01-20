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
import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Server → Client: Bulk world data (compressed)
 * Replaces thousands of PacketWorldUpdate packets for initial load.
 * Uses Zlib compression.
 */
public class PacketWorldBulkData extends ServerPacket {
    public int x;
    public int y;
    public int width;
    public int height;
    public byte[] compressedData;
    public int originalSize; // Size of uncompressed data for allocation

    public PacketWorldBulkData() {
    }

    public PacketWorldBulkData(int x, int y, int width, int height, byte... rawData) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.originalSize = rawData.length;
        this.compressedData = compress(rawData);
    }

    public PacketWorldBulkData(int x, int y, int width, int height, byte[] tileIds, byte[] metadata) {
        this(x, y, width, height, tileIds, metadata, new byte[tileIds.length]);
    }

    public PacketWorldBulkData(int x, int y, int width, int height, byte[] tileIds, byte[] metadata, byte[] backdrops) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        // Merge arrays: [Tiles] + [Meta] + [Backdrops]
        int len = tileIds.length;
        byte[] combined = new byte[len * 3];

        System.arraycopy(tileIds, 0, combined, 0, len);
        System.arraycopy(metadata, 0, combined, len, len);
        System.arraycopy(backdrops, 0, combined, len * 2, len);

        this.originalSize = combined.length;
        this.compressedData = compress(combined);
    }

    @Override
    public int getPacketId() {
        return 71; // Manually assigned ID
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handleWorldBulkData(this);
    }

    @Override
    public byte[] encode() {
        // x(4) + y(4) + w(4) + h(4) + origSize(4) + dataLen(4) + data
        ByteBuffer buf = ByteBuffer.allocate(24 + compressedData.length);
        buf.putInt(x);
        buf.putInt(y);
        buf.putInt(width);
        buf.putInt(height);
        buf.putInt(originalSize);
        buf.putInt(compressedData.length);
        buf.put(compressedData);
        return buf.array();
    }

    public static PacketWorldBulkData decode(ByteBuffer buf) {
        PacketWorldBulkData packet = new PacketWorldBulkData();
        packet.x = buf.getInt();
        packet.y = buf.getInt();
        packet.width = buf.getInt();
        packet.height = buf.getInt();
        packet.originalSize = buf.getInt();

        int dataLen = buf.getInt();
        packet.compressedData = new byte[dataLen];
        buf.get(packet.compressedData);

        return packet;
    }

    // --- Compression Utils ---

    private byte[] compress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        try {
            outputStream.close();
        } catch (java.io.IOException e) {
            // Should not happen with ByteArrayOutputStream
            e.printStackTrace();
        }
        return outputStream.toByteArray();
    }

    public byte[] decompress() {
        Inflater inflater = new Inflater();
        inflater.setInput(compressedData);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(originalSize);
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return outputStream.toByteArray();
    }
}
