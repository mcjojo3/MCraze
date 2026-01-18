package mc.sayda.mcraze.player.specialization.network;

import mc.sayda.mcraze.network.packet.ClientPacket;
import mc.sayda.mcraze.network.ServerPacketHandler;
import mc.sayda.mcraze.player.specialization.PassiveEffectType;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Packet sent from client to server to unlock a passive skill.
 */
public class PacketSkillUpgrade extends ClientPacket {
    private final PassiveEffectType ability;

    public PacketSkillUpgrade(PassiveEffectType ability) {
        this.ability = ability;
    }

    public static PacketSkillUpgrade decode(ByteBuffer buffer) {
        // Decode Ability Name
        int len = buffer.getInt();
        byte[] bytes = new byte[len];
        buffer.get(bytes);
        String name = new String(bytes, StandardCharsets.UTF_8);
        return new PacketSkillUpgrade(PassiveEffectType.valueOf(name));
    }

    @Override
    public byte[] encode() {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        byte[] bytes = ability.name().getBytes(StandardCharsets.UTF_8);
        buffer.putInt(bytes.length);
        buffer.put(bytes);

        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        return data;
    }

    @Override
    public void handle(ServerPacketHandler handler) {
        handler.handleSkillUpgrade(this);
    }

    @Override
    public int getPacketId() {
        return 18; // ID 17 is ClassSelect, assume 18 is free
    }

    public PassiveEffectType getAbility() {
        return ability;
    }
}
