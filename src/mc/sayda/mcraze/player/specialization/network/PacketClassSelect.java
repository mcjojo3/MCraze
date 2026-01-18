package mc.sayda.mcraze.player.specialization.network;
import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import mc.sayda.mcraze.network.packet.ClientPacket;
import mc.sayda.mcraze.network.ServerPacketHandler;
import mc.sayda.mcraze.player.specialization.PlayerClass;
import mc.sayda.mcraze.player.specialization.SpecializationPath;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Packet sent from client to server to select a class and specialization paths.
 */
public class PacketClassSelect extends ClientPacket {
    private final PlayerClass selectedClass;
    private final SpecializationPath[] paths;

    public PacketClassSelect(PlayerClass selectedClass, SpecializationPath... paths) {
        this.selectedClass = selectedClass;
        this.paths = paths != null ? paths : new SpecializationPath[0];
    }

    public static PacketClassSelect decode(ByteBuffer buffer) {
        // Decode Class Name
        int classLen = buffer.getInt();
        byte[] classBytes = new byte[classLen];
        buffer.get(classBytes);
        PlayerClass pClass = PlayerClass.valueOf(new String(classBytes, StandardCharsets.UTF_8));

        // Decode Paths
        int pathCount = buffer.getInt();
        SpecializationPath[] paths = new SpecializationPath[pathCount];
        for (int i = 0; i < pathCount; i++) {
            int pathLen = buffer.getInt();
            byte[] pathBytes = new byte[pathLen];
            buffer.get(pathBytes);
            paths[i] = SpecializationPath.valueOf(new String(pathBytes, StandardCharsets.UTF_8));
        }

        return new PacketClassSelect(pClass, paths);
    }

    @Override
    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        // Write Class Name
        byte[] classBytes = selectedClass.name().getBytes(StandardCharsets.UTF_8);
        buffer.putInt(classBytes.length);
        buffer.put(classBytes);

        // Write Paths
        buffer.putInt(paths.length);
        for (SpecializationPath path : paths) {
            byte[] pathBytes = path.name().getBytes(StandardCharsets.UTF_8);
            buffer.putInt(pathBytes.length);
            buffer.put(pathBytes);
        }

        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleClassSelect(this);
    }

    @Override
    public int getPacketId() {
        return 17;
    }

    public PlayerClass getSelectedClass() {
        return selectedClass;
    }

    public SpecializationPath[] getPaths() {
        return paths;
    }
}
