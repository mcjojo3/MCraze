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

    public PacketPlaySound() {
    }

    public PacketPlaySound(String soundName) {
        this(soundName, 1.0f, 1.0f);
    }

    public PacketPlaySound(String soundName, float volume, float pitch) {
        this.soundName = soundName;
        this.volume = volume;
        this.pitch = pitch;
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
        ByteBuffer buf = ByteBuffer.allocate(2 + nameBytes.length + 8); // string + 2 floats
        buf.putShort((short) nameBytes.length);
        buf.put(nameBytes);
        buf.putFloat(volume);
        buf.putFloat(pitch);
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
        return packet;
    }
}
