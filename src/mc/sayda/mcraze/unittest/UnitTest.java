package mc.sayda.mcraze.unittest;

import mc.sayda.mcraze.network.packet.PacketEntityUpdate;
import java.nio.ByteBuffer;
import java.util.UUID;

public class UnitTest {
    public static void main(String[] args) {
        System.out.println("Starting PacketEntityUpdate test...");

        try {
            PacketEntityUpdate packet = new PacketEntityUpdate();
            packet.ensureCapacity(1);

            // Populate with dummy data
            packet.entityIds[0] = 123;
            packet.entityTypes[0] = "TestEntity";
            packet.entityUUIDs[0] = UUID.randomUUID().toString();
            packet.entityX[0] = 10.5f;
            packet.entityY[0] = 20.5f;
            packet.entityDX[0] = 0.1f;
            packet.entityDY[0] = 0.2f;
            packet.entityHealth[0] = 100;
            packet.facingRight[0] = true;
            packet.dead[0] = false;
            packet.ticksAlive[0] = 1000L;
            packet.ticksUnderwater[0] = 0;
            packet.itemIds[0] = "sword";
            packet.playerNames[0] = "PlayerOne";
            packet.flying[0] = false;
            packet.noclip[0] = false;
            packet.sneaking[0] = false;
            packet.climbing[0] = false;
            packet.jumping[0] = false;
            packet.speedMultiplier[0] = 1.0f;
            packet.backdropPlacementMode[0] = false;
            packet.handTargetX[0] = 100;
            packet.handTargetY[0] = 200;
            packet.hotbarIndex[0] = 1;
            packet.selectedItemId[0] = "bow";
            packet.selectedItemCount[0] = 1;
            packet.selectedItemDurability[0] = 50;
            packet.skillPoints[0] = 5;
            packet.isExploding[0] = false;
            packet.fuseTimer[0] = 0;
            packet.damageFlashTicks[0] = 0;
            packet.widthPX[0] = 32;
            packet.heightPX[0] = 32;
            packet.essence[0] = 10;
            packet.maxEssence[0] = 100;
            packet.mana[0] = 20;
            packet.maxMana[0] = 200;
            packet.bowCharge[0] = 5;
            packet.maxBowCharge[0] = 10;

            System.out.println("Encoding...");
            byte[] data = packet.encode();
            System.out.println("Encoded " + data.length + " bytes.");

            System.out.println("Decoding...");
            ByteBuffer buf = ByteBuffer.wrap(data);
            // Simulate PacketRegistry stripping header if needed?
            // PacketEntityUpdate.encode() includes "count" as Int at start.
            // PacketEntityUpdate.decode() reads "count" first.
            // So we can pass buffer directly.

            PacketEntityUpdate decoded = PacketEntityUpdate.decode(buf);
            System.out.println("Decoded successfully!");
            System.out.println("UUID: " + decoded.entityUUIDs[0]);
            System.out.println("BowCharge: " + decoded.bowCharge[0]);

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("TEST FAILED");
            System.exit(1);
        }
    }
}
