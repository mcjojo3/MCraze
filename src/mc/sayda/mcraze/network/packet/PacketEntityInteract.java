package mc.sayda.mcraze.network.packet;

import mc.sayda.mcraze.network.PacketRegistry;
import mc.sayda.mcraze.network.ServerPacketHandler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Client → Server: Interaction with an entity (e.g. taming).
 */
public class PacketEntityInteract extends ClientPacket {
    public String entityUUID;

    public PacketEntityInteract() {
    }

    public PacketEntityInteract(String entityUUID) {
        this.entityUUID = entityUUID;
    }

    @Override
    public int getPacketId() {
        return PacketRegistry.getId(PacketEntityInteract.class);
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleEntityInteract(this);
    }

    @Override
    public byte[] encode() {
        byte[] uuidBytes = entityUUID.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + uuidBytes.length);
        buf.putInt(uuidBytes.length);
        buf.put(uuidBytes);
        return buf.array();
    }

    public static PacketEntityInteract decode(ByteBuffer buf) {
        int length = buf.getInt();
        byte[] uuidBytes = new byte[length];
        buf.get(uuidBytes);
        String uuid = new String(uuidBytes, StandardCharsets.UTF_8);
        return new PacketEntityInteract(uuid);
    }
}
