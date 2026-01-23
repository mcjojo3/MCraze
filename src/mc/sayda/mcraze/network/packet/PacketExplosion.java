package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.PacketRegistry;
import java.nio.ByteBuffer;

/**
 * Server-to-client packet to trigger explosion visual effects
 */
public class PacketExplosion extends ServerPacket {
    public float x;
    public float y;
    public float radius;

    public PacketExplosion() {
    }

    public PacketExplosion(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketExplosion.class);
    }

    @Override
    public void handle(ClientPacketHandler handler) {
        handler.handleExplosion(this);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buf = ByteBuffer.allocate(12); // 3 * 4 bytes float
        buf.putFloat(x);
        buf.putFloat(y);
        buf.putFloat(radius);
        return buf.array();
    }

    public static PacketExplosion decode(ByteBuffer buf) {
        PacketExplosion packet = new PacketExplosion();
        packet.x = buf.getFloat();
        packet.y = buf.getFloat();
        packet.radius = buf.getFloat();
        return packet;
    }
}
