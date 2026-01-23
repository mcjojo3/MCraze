package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketRegistry;

import java.nio.ByteBuffer;

/**
 * Packet to trigger sound effects on the client
 */
public class PacketPlaySound extends ServerPacket {
    public String soundName;
    public float volume = 1.0f;
    public float pitch = 1.0f;
    public float x;
    public float y;
    public float maxDistance = 16.0f;
    public boolean isGlobal = true; // Default to global for backward compatibility in constructor

    public PacketPlaySound() {
    }

    public PacketPlaySound(String soundName) {
        this(soundName, 1.0f, 1.0f);
    }

    public PacketPlaySound(String soundName, float volume, float pitch) {
        this.soundName = soundName;
        this.volume = volume;
        this.pitch = pitch;
        this.isGlobal = true;
    }

    public PacketPlaySound(String soundName, float x, float y, float volume, float maxDistance) {
        this.soundName = soundName;
        this.x = x;
        this.y = y;
        this.volume = volume;
        this.maxDistance = maxDistance;
        this.isGlobal = false;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketPlaySound.class);
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handlePlaySound(this);
    }

    @Override
    public byte[] encode() {
        byte[] nameBytes = soundName.getBytes();
        // string (2+len) + 5 floats (20) + 1 byte (bool) = 23 + len
        ByteBuffer buf = ByteBuffer.allocate(2 + nameBytes.length + 21);
        buf.putShort((short) nameBytes.length);
        buf.put(nameBytes);
        buf.putFloat(volume);
        buf.putFloat(pitch);
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(maxDistance);
        buf.put((byte) (isGlobal ? 1 : 0));
        return buf.array();
    }

    public static PacketPlaySound decode(ByteBuffer buf) {
        PacketPlaySound packet = new PacketPlaySound();
        short len = buf.getShort();
        byte[] bytes = new byte[len];
        buf.get(bytes);
        packet.soundName = new String(bytes);
        if (buf.remaining() >= 8) {
            packet.volume = buf.getFloat();
            packet.pitch = buf.getFloat();
        }
        if (buf.remaining() >= 13) {
            packet.x = buf.getFloat();
            packet.y = buf.getFloat();
            packet.maxDistance = buf.getFloat();
            packet.isGlobal = buf.get() == 1;
        }
        return packet;
    }
}
