package mc.sayda.mcraze.unittest;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class UnitTestShim {

    public static void main(String[] args) {
        try {
            System.out.println("Testing PacketEntityUpdate logic...");

            // 1. Create packet and populate
            Packet packet = new Packet();
            packet.ensureCapacity(1);

            packet.entityIds[0] = 1;
            packet.entityTypes[0] = "Player";
            packet.entityUUIDs[0] = "uuid-1234";
            packet.entityX[0] = 10f;
            packet.entityY[0] = 10f;
            packet.entityDX[0] = 0f;
            packet.entityDY[0] = 0f;
            packet.entityHealth[0] = 100;
            packet.facingRight[0] = true;
            packet.dead[0] = false;
            packet.ticksAlive[0] = 100;
            packet.ticksUnderwater[0] = 0;
            packet.itemIds[0] = "item";
            packet.playerNames[0] = "Steve";
            packet.flying[0] = false;
            packet.noclip[0] = false;
            packet.sneaking[0] = false;
            packet.climbing[0] = false;
            packet.jumping[0] = false;
            packet.speedMultiplier[0] = 1f;
            packet.backdropPlacementMode[0] = false;
            packet.handTargetX[0] = 0;
            packet.handTargetY[0] = 0;
            packet.hotbarIndex[0] = 0;
            packet.selectedItemId[0] = "sword";
            packet.selectedItemCount[0] = 1;
            packet.selectedItemDurability[0] = 100;
            packet.skillPoints[0] = 0;
            packet.isExploding[0] = false;
            packet.fuseTimer[0] = 0;
            packet.damageFlashTicks[0] = 0;
            packet.widthPX[0] = 32;
            packet.heightPX[0] = 32;
            packet.essence[0] = 10;
            packet.maxEssence[0] = 10;
            packet.mana[0] = 10;
            packet.maxMana[0] = 10;
            packet.bowCharge[0] = 5;
            packet.maxBowCharge[0] = 10;

            // 2. Encode
            byte[] data = packet.encode();
            System.out.println("Encoded size: " + data.length);

            // 3. Decode
            Packet decoded = Packet.decode(ByteBuffer.wrap(data));
            System.out.println("Decoded successfully. BowCharge: " + decoded.bowCharge[0]);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class Packet {
        public int count = 0;
        public int[] entityIds;
        public String[] entityTypes;
        public String[] entityUUIDs;
        public float[] entityX;
        public float[] entityY;
        public float[] entityDX;
        public float[] entityDY;
        public int[] entityHealth;
        public boolean[] facingRight;
        public boolean[] dead;
        public long[] ticksAlive;
        public int[] ticksUnderwater;
        public String[] itemIds;
        public String[] playerNames;
        public boolean[] flying;
        public boolean[] noclip;
        public boolean[] sneaking;
        public boolean[] climbing;
        public boolean[] jumping;
        public float[] speedMultiplier;
        public boolean[] backdropPlacementMode;
        public int[] handTargetX;
        public int[] handTargetY;
        public int[] hotbarIndex;
        public String[] selectedItemId;
        public int[] selectedItemCount;
        public int[] selectedItemDurability;
        public int[] skillPoints;
        public boolean[] isExploding;
        public int[] fuseTimer;
        public int[] damageFlashTicks;
        public int[] widthPX;
        public int[] heightPX;
        public int[] essence;
        public int[] maxEssence;
        public int[] mana;
        public int[] maxMana;
        public int[] bowCharge;
        public int[] maxBowCharge;

        public void ensureCapacity(int size) {
            this.count = size;
            entityIds = new int[size];
            entityTypes = new String[size];
            entityUUIDs = new String[size];
            entityX = new float[size];
            entityY = new float[size];
            entityDX = new float[size];
            entityDY = new float[size];
            entityHealth = new int[size];
            facingRight = new boolean[size];
            dead = new boolean[size];
            ticksAlive = new long[size];
            ticksUnderwater = new int[size];
            itemIds = new String[size];
            playerNames = new String[size];
            flying = new boolean[size];
            noclip = new boolean[size];
            sneaking = new boolean[size];
            climbing = new boolean[size];
            jumping = new boolean[size];
            speedMultiplier = new float[size];
            backdropPlacementMode = new boolean[size];
            handTargetX = new int[size];
            handTargetY = new int[size];
            hotbarIndex = new int[size];
            selectedItemId = new String[size];
            selectedItemCount = new int[size];
            selectedItemDurability = new int[size];
            skillPoints = new int[size];
            isExploding = new boolean[size];
            fuseTimer = new int[size];
            damageFlashTicks = new int[size];
            widthPX = new int[size];
            heightPX = new int[size];
            essence = new int[size];
            maxEssence = new int[size];
            mana = new int[size];
            maxMana = new int[size];
            bowCharge = new int[size];
            maxBowCharge = new int[size];
        }

        public byte[] encode() {
            int estimatedSize = 4 + (count * 400);
            ByteBuffer buf = ByteBuffer.allocate(Math.max(estimatedSize, 2048));
            buf.putInt(count);

            for (int i = 0; i < count; i++) {
                buf.putInt(entityIds[i]);

                byte[] typeBytes = entityTypes[i].getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) typeBytes.length);
                buf.put(typeBytes);

                byte[] uuidBytes = entityUUIDs[i].getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) uuidBytes.length);
                buf.put(uuidBytes);

                buf.putFloat(entityX[i]);
                buf.putFloat(entityY[i]);
                buf.putFloat(entityDX[i]);
                buf.putFloat(entityDY[i]);

                buf.putInt(entityHealth[i]);
                buf.put((byte) (facingRight[i] ? 1 : 0));
                buf.put((byte) (dead[i] ? 1 : 0));

                buf.putLong(ticksAlive[i]);
                buf.putInt(ticksUnderwater[i]);

                String itemId = (itemIds[i] != null) ? itemIds[i] : "";
                byte[] itemIdBytes = itemId.getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) itemIdBytes.length);
                buf.put(itemIdBytes);

                String playerName = (playerNames[i] != null) ? playerNames[i] : "";
                byte[] playerNameBytes = playerName.getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) playerNameBytes.length);
                buf.put(playerNameBytes);

                buf.put((byte) (flying[i] ? 1 : 0));
                buf.put((byte) (noclip[i] ? 1 : 0));
                buf.put((byte) (sneaking[i] ? 1 : 0));
                buf.put((byte) (climbing[i] ? 1 : 0));
                buf.put((byte) (jumping[i] ? 1 : 0));

                buf.putFloat(speedMultiplier[i]);

                buf.put((byte) (backdropPlacementMode[i] ? 1 : 0));

                buf.putInt(handTargetX[i]);
                buf.putInt(handTargetY[i]);

                buf.putInt(hotbarIndex[i]);
                String selectedItem = (selectedItemId[i] != null) ? selectedItemId[i] : "";
                byte[] selectedItemBytes = selectedItem.getBytes(StandardCharsets.UTF_8);
                buf.putShort((short) selectedItemBytes.length);
                buf.put(selectedItemBytes);
                buf.putInt(selectedItemCount[i]);
                buf.putInt(selectedItemDurability[i]);
                buf.putInt(skillPoints[i]);
                buf.put((byte) (isExploding[i] ? 1 : 0));
                buf.putInt(fuseTimer[i]);
                buf.putInt(damageFlashTicks[i]);
                buf.putInt(widthPX[i]);
                buf.putInt(heightPX[i]);

                buf.putInt(essence[i]);
                buf.putInt(maxEssence[i]);
                buf.putInt(mana[i]);
                buf.putInt(maxMana[i]);

                buf.putInt(bowCharge[i]);
                buf.putInt(maxBowCharge[i]);
            }
            int actualSize = buf.position();
            byte[] result = new byte[actualSize];
            System.arraycopy(buf.array(), 0, result, 0, actualSize);
            return result;
        }

        public static Packet decode(ByteBuffer buf) {
            Packet packet = new Packet();
            int count = buf.getInt();
            packet.ensureCapacity(count);

            for (int i = 0; i < count; i++) {
                packet.entityIds[i] = buf.getInt();

                short typeLen = buf.getShort();
                byte[] typeBytes = new byte[typeLen];
                buf.get(typeBytes);
                packet.entityTypes[i] = new String(typeBytes, StandardCharsets.UTF_8);

                short uuidLen = buf.getShort();
                byte[] uuidBytes = new byte[uuidLen];
                buf.get(uuidBytes); // FAIL POINT
                packet.entityUUIDs[i] = new String(uuidBytes, StandardCharsets.UTF_8);

                packet.entityX[i] = buf.getFloat();
                packet.entityY[i] = buf.getFloat();
                packet.entityDX[i] = buf.getFloat();
                packet.entityDY[i] = buf.getFloat();

                packet.entityHealth[i] = buf.getInt();
                packet.facingRight[i] = buf.get() == 1;
                packet.dead[i] = buf.get() == 1;

                packet.ticksAlive[i] = buf.getLong();
                packet.ticksUnderwater[i] = buf.getInt();

                short itemIdLen = buf.getShort();
                byte[] itemIdBytes = new byte[itemIdLen];
                buf.get(itemIdBytes);
                packet.itemIds[i] = new String(itemIdBytes, StandardCharsets.UTF_8);

                short playerNameLen = buf.getShort();
                byte[] playerNameBytes = new byte[playerNameLen];
                buf.get(playerNameBytes);
                packet.playerNames[i] = new String(playerNameBytes, StandardCharsets.UTF_8);

                packet.flying[i] = buf.get() == 1;
                packet.noclip[i] = buf.get() == 1;
                packet.sneaking[i] = buf.get() == 1;
                packet.climbing[i] = buf.get() == 1;
                packet.jumping[i] = buf.get() == 1;

                packet.speedMultiplier[i] = buf.getFloat();

                packet.backdropPlacementMode[i] = buf.get() == 1;

                packet.handTargetX[i] = buf.getInt();
                packet.handTargetY[i] = buf.getInt();

                packet.hotbarIndex[i] = buf.getInt();
                short selectedItemLen = buf.getShort();
                byte[] selectedItemBytes = new byte[selectedItemLen];
                buf.get(selectedItemBytes);
                packet.selectedItemId[i] = new String(selectedItemBytes, StandardCharsets.UTF_8);
                packet.selectedItemCount[i] = buf.getInt();
                packet.selectedItemDurability[i] = buf.getInt();
                packet.skillPoints[i] = buf.getInt();
                packet.isExploding[i] = buf.get() == 1;
                packet.fuseTimer[i] = buf.getInt();
                packet.damageFlashTicks[i] = buf.getInt();
                packet.widthPX[i] = buf.getInt();
                packet.heightPX[i] = buf.getInt();

                packet.essence[i] = buf.getInt();
                packet.maxEssence[i] = buf.getInt();
                packet.mana[i] = buf.getInt();
                packet.maxMana[i] = buf.getInt();

                packet.bowCharge[i] = buf.getInt();
                packet.maxBowCharge[i] = buf.getInt();
            }
            return packet;
        }
    }
}
