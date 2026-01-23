package mc.sayda.mcraze.tools;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.*;
import java.util.*;

/**
 * Parses world.dat binary file for the World Editor tool.
 * Creates tree nodes representing the world structure.
 */
public class WorldDataParser {

    public static class WorldData {
        public int width;
        public int height;
        public long ticksAlive;
        public List<ChestEntry> chests = new ArrayList<>();
        public List<FurnaceEntry> furnaces = new ArrayList<>();
        public List<AlchemyEntry> alchemies = new ArrayList<>();
        public List<PotEntry> pots = new ArrayList<>();
        public List<EntityEntry> entities = new ArrayList<>(); // [NEW] Embedded entities
        public int biomeCount;
        public int tileCount;
        public int backdropCount;
    }

    public static class EntityEntry {
        public String type;
        public float x, y;
        public float dx, dy;
        public int hitPoints;
        public String itemId;
        public boolean isMastercrafted;
        public int toolUses;
        public String[] inventoryItemIds;
        public int[] inventoryItemCounts;
        public int[] inventoryToolUses;
        public int inventoryHotbarIdx;
        public String ownerUUID;
        public boolean isSitting;
    }

    public static class ChestEntry {
        public int x, y;
        public List<ItemSlot> items = new ArrayList<>();
    }

    public static class FurnaceEntry {
        public int x, y;
        public int fuelTimeRemaining;
        public int smeltProgress;
        public int smeltTimeTotal;
        public String currentRecipe;
        public List<ItemSlot> items = new ArrayList<>();
    }

    public static class AlchemyEntry {
        public int x, y;
        public int brewProgress;
        public int brewTimeTotal;
        public String currentRecipe;
        public List<ItemSlot> items = new ArrayList<>();
    }

    public static class PotEntry {
        public int x, y;
        public String plantId;
        public int growthProgress;
        public int growthTimeTotal;
    }

    public static class ItemSlot {
        public int slotX, slotY;
        public String itemId;
        public int count;
        public boolean isMastercrafted; // [NEW] Mastercrafted status
        public int toolUses; // [NEW] Tool durability
    }

    /**
     * Parse world.dat and return structured data
     */
    public static WorldData parse(File worldDatFile) throws IOException {
        WorldData data = new WorldData();

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(worldDatFile)))) {

            // [NEW] Version detection logic
            int saveVersion = 1;
            in.mark(8);
            int magic = in.readInt();
            if (magic == 0x4D43525A) {
                saveVersion = in.readInt();
            } else {
                in.reset();
            }

            // Read dimensions
            data.width = in.readInt();
            data.height = in.readInt();

            // Read biome map
            data.biomeCount = in.readInt();
            for (int i = 0; i < data.biomeCount; i++) {
                in.readInt(); // Skip biome ordinal
            }

            // Skip tile data (width * height * 2 bytes per tile)
            data.tileCount = data.width * data.height;
            in.skipBytes(data.tileCount * 2);

            // Read backdrop flag and skip if present
            try {
                boolean hasBackdrop = in.readBoolean();
                if (hasBackdrop) {
                    data.backdropCount = data.width * data.height;
                    in.skipBytes(data.backdropCount * 2);
                }
            } catch (EOFException e) {
                // Old format without backdrop
            }

            // Read world time
            try {
                data.ticksAlive = in.readLong();
            } catch (EOFException e) {
                data.ticksAlive = 0;
            }

            // Read chests
            try {
                int chestCount = in.readInt();
                for (int i = 0; i < chestCount; i++) {
                    ChestEntry chest = new ChestEntry();
                    chest.x = in.readInt();
                    chest.y = in.readInt();

                    // Read 10x3 grid of items (or 9x3 for legacy versions)
                    int chestWidth = saveVersion < 3 ? 9 : 10;
                    for (int x = 0; x < chestWidth; x++) {
                        for (int y = 0; y < 3; y++) {
                            boolean hasItem = in.readBoolean();
                            if (hasItem) {
                                ItemSlot slot = new ItemSlot();
                                slot.slotX = x;
                                slot.slotY = y;
                                slot.itemId = in.readUTF();
                                slot.count = in.readInt();
                                if (saveVersion >= 3) {
                                    slot.isMastercrafted = in.readBoolean();
                                    slot.toolUses = in.readInt();
                                }
                                chest.items.add(slot);
                            }
                        }
                    }
                    data.chests.add(chest);
                }
            } catch (EOFException e) {
                // No chest data
            }

            // Read furnaces
            try {
                int furnaceCount = in.readInt();
                for (int i = 0; i < furnaceCount; i++) {
                    FurnaceEntry furnace = new FurnaceEntry();
                    furnace.x = in.readInt();
                    furnace.y = in.readInt();
                    furnace.fuelTimeRemaining = in.readInt();
                    furnace.smeltProgress = in.readInt();
                    furnace.smeltTimeTotal = in.readInt();

                    if (in.readBoolean()) {
                        furnace.currentRecipe = in.readUTF();
                    }

                    // Read 3x2 grid of items
                    for (int x = 0; x < 3; x++) {
                        for (int y = 0; y < 2; y++) {
                            boolean hasItem = in.readBoolean();
                            if (hasItem) {
                                ItemSlot slot = new ItemSlot();
                                slot.slotX = x;
                                slot.slotY = y;
                                slot.itemId = in.readUTF();
                                slot.count = in.readInt();
                                if (saveVersion >= 3) {
                                    slot.isMastercrafted = in.readBoolean();
                                    slot.toolUses = in.readInt();
                                }
                                furnace.items.add(slot);
                            }
                        }
                    }
                    data.furnaces.add(furnace);
                }
            } catch (EOFException e) {
                // No furnace data
            }

            // Read alchemies
            try {
                int alchemyCount = in.readInt();
                for (int i = 0; i < alchemyCount; i++) {
                    AlchemyEntry alchemy = new AlchemyEntry();
                    alchemy.x = in.readInt();
                    alchemy.y = in.readInt();
                    alchemy.brewProgress = in.readInt();
                    alchemy.brewTimeTotal = in.readInt();

                    if (in.readBoolean()) {
                        alchemy.currentRecipe = in.readUTF();
                    }

                    // Read 5 slots
                    for (int j = 0; j < 5; j++) {
                        boolean hasItem = in.readBoolean();
                        if (hasItem) {
                            ItemSlot slot = new ItemSlot();
                            slot.slotX = j;
                            slot.slotY = 0;
                            slot.itemId = in.readUTF();
                            slot.count = in.readInt();
                            if (saveVersion >= 3) {
                                slot.isMastercrafted = in.readBoolean();
                                slot.toolUses = in.readInt();
                            }
                            alchemy.items.add(slot);
                        }
                    }
                    data.alchemies.add(alchemy);
                }
            } catch (EOFException e) {
                // No alchemy data
            }

            // Read pots
            try {
                int potCount = in.readInt();
                for (int i = 0; i < potCount; i++) {
                    PotEntry pot = new PotEntry();
                    pot.x = in.readInt();
                    pot.y = in.readInt();

                    if (in.readBoolean()) {
                        pot.plantId = in.readUTF();
                    }
                    pot.growthProgress = in.readInt();
                    pot.growthTimeTotal = in.readInt();
                    data.pots.add(pot);
                }
            } catch (EOFException e) {
                // No pot data
            }

            // Read entities (Version 4+)
            if (saveVersion >= 4) {
                try {
                    int entityCount = in.readInt();
                    for (int i = 0; i < entityCount; i++) {
                        EntityEntry entity = new EntityEntry();
                        entity.type = in.readUTF();
                        entity.x = in.readFloat();
                        entity.y = in.readFloat();
                        entity.dx = in.readFloat();
                        entity.dy = in.readFloat();
                        entity.hitPoints = in.readInt();

                        // Item data
                        if (in.readBoolean()) {
                            entity.itemId = in.readUTF();
                            entity.isMastercrafted = in.readBoolean();
                            entity.toolUses = in.readInt();
                        }

                        // Wolf data
                        if (in.readBoolean()) {
                            entity.ownerUUID = in.readUTF();
                        }
                        entity.isSitting = in.readBoolean();
                        data.entities.add(entity);
                    }
                } catch (EOFException e) {
                    // No entity data
                }
            }
        }

        return data;
    }

    /**
     * Build simplified tree nodes - individual items are leaves (no children).
     * Used by the simplified WorldEditor for JSON-only viewing.
     */
    public static void populateTreeNodeSimple(DefaultMutableTreeNode worldNode, WorldData data) {
        // World Info node (leaf)
        worldNode.add(new DefaultMutableTreeNode("📊 World Info"));

        // Chests folder with leaf children
        DefaultMutableTreeNode chestsNode = new DefaultMutableTreeNode("📦 Chests (" + data.chests.size() + ")");
        for (ChestEntry chest : data.chests) {
            chestsNode.add(new DefaultMutableTreeNode("Chest @ (" + chest.x + ", " + chest.y + ")"));
        }
        worldNode.add(chestsNode);

        // Furnaces folder with leaf children
        DefaultMutableTreeNode furnacesNode = new DefaultMutableTreeNode("🔥 Furnaces (" + data.furnaces.size() + ")");
        for (FurnaceEntry furnace : data.furnaces) {
            furnacesNode.add(new DefaultMutableTreeNode("Furnace @ (" + furnace.x + ", " + furnace.y + ")"));
        }
        worldNode.add(furnacesNode);

        // Alchemy Tables folder with leaf children
        DefaultMutableTreeNode alchemyNode = new DefaultMutableTreeNode(
                "⚗️ Alchemy Tables (" + data.alchemies.size() + ")");
        for (AlchemyEntry alchemy : data.alchemies) {
            alchemyNode.add(new DefaultMutableTreeNode("Alchemy @ (" + alchemy.x + ", " + alchemy.y + ")"));
        }
        worldNode.add(alchemyNode);

        // Flower Pots folder with leaf children
        DefaultMutableTreeNode potsNode = new DefaultMutableTreeNode("🌻 Flower Pots (" + data.pots.size() + ")");
        for (PotEntry pot : data.pots) {
            String label = "Pot @ (" + pot.x + ", " + pot.y + ")";
            if (pot.plantId != null) {
                label += " - " + pot.plantId;
            }
            potsNode.add(new DefaultMutableTreeNode(label));
        }
        worldNode.add(potsNode);

        // Entities folder
        DefaultMutableTreeNode entitiesNode = new DefaultMutableTreeNode("👾 Entities (" + data.entities.size() + ")");
        for (EntityEntry entity : data.entities) {
            entitiesNode.add(
                    new DefaultMutableTreeNode(entity.type + " @ (" + (int) entity.x + ", " + (int) entity.y + ")"));
        }
        worldNode.add(entitiesNode);
    }

    /**
     * Save modified storage blocks back to world.dat
     * Note: This preserves the original tile data and only updates storage blocks
     */
    public static void save(File worldDatFile, WorldData data) throws IOException {
        // Read the original file to preserve tile data
        byte[] originalBytes = java.nio.file.Files.readAllBytes(worldDatFile.toPath());

        // Create backup
        File backup = new File(worldDatFile.getParent(), "world.dat.bak");
        java.nio.file.Files.copy(worldDatFile.toPath(), backup.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(worldDatFile)))) {

            // [NEW] Versioning header
            out.writeInt(0x4D43525A); // Magic Number "MCRZ"
            out.writeInt(4); // Save Version 4

            // Write dimensions
            out.writeInt(data.width);
            out.writeInt(data.height);

            // We need to read and copy biome/tile/backdrop data from original
            try (DataInputStream in = new DataInputStream(
                    new BufferedInputStream(new java.io.ByteArrayInputStream(originalBytes)))) {

                in.mark(8);
                int magic = in.readInt();
                if (magic == 0x4D43525A) {
                    in.readInt(); // skip version
                } else {
                    in.reset();
                }

                in.readInt(); // skip width
                in.readInt(); // skip height

                // Copy biome data
                int biomeCount = in.readInt();
                out.writeInt(biomeCount);
                for (int i = 0; i < biomeCount; i++) {
                    out.writeInt(in.readInt());
                }

                // Copy tile data
                int tileBytes = data.width * data.height * 2;
                byte[] tileData = new byte[tileBytes];
                in.readFully(tileData);
                out.write(tileData);

                // Copy backdrop
                boolean hasBackdrop = in.readBoolean();
                out.writeBoolean(hasBackdrop);
                if (hasBackdrop) {
                    byte[] backdropData = new byte[tileBytes];
                    in.readFully(backdropData);
                    out.write(backdropData);
                }
            }

            // Write world time
            out.writeLong(data.ticksAlive);

            // Write chests
            out.writeInt(data.chests.size());
            for (ChestEntry chest : data.chests) {
                out.writeInt(chest.x);
                out.writeInt(chest.y);

                // Create item grid (10x3)
                ItemSlot[][] grid = new ItemSlot[10][3];
                for (ItemSlot slot : chest.items) {
                    grid[slot.slotX][slot.slotY] = slot;
                }

                for (int x = 0; x < 10; x++) {
                    for (int y = 0; y < 3; y++) {
                        ItemSlot slot = grid[x][y];
                        if (slot != null && slot.itemId != null) {
                            out.writeBoolean(true);
                            out.writeUTF(slot.itemId);
                            out.writeInt(slot.count);
                            // [NEW] Save Mastercrafted/Tool data (Version 3)
                            out.writeBoolean(slot.isMastercrafted);
                            out.writeInt(slot.toolUses);
                        } else {
                            out.writeBoolean(false);
                        }
                    }
                }
            }

            // Write furnaces
            out.writeInt(data.furnaces.size());
            for (FurnaceEntry furnace : data.furnaces) {
                out.writeInt(furnace.x);
                out.writeInt(furnace.y);
                out.writeInt(furnace.fuelTimeRemaining);
                out.writeInt(furnace.smeltProgress);
                out.writeInt(furnace.smeltTimeTotal);

                out.writeBoolean(furnace.currentRecipe != null);
                if (furnace.currentRecipe != null) {
                    out.writeUTF(furnace.currentRecipe);
                }

                // Create item grid (3x2)
                ItemSlot[][] grid = new ItemSlot[3][2];
                for (ItemSlot slot : furnace.items) {
                    grid[slot.slotX][slot.slotY] = slot;
                }

                for (int x = 0; x < 3; x++) {
                    for (int y = 0; y < 2; y++) {
                        ItemSlot slot = grid[x][y];
                        if (slot != null && slot.itemId != null) {
                            out.writeBoolean(true);
                            out.writeUTF(slot.itemId);
                            out.writeInt(slot.count);
                            // [NEW] Save Mastercrafted/Tool data (Version 3)
                            out.writeBoolean(slot.isMastercrafted);
                            out.writeInt(slot.toolUses);
                        } else {
                            out.writeBoolean(false);
                        }
                    }
                }
            }

            // Write alchemies
            out.writeInt(data.alchemies.size());
            for (AlchemyEntry alchemy : data.alchemies) {
                out.writeInt(alchemy.x);
                out.writeInt(alchemy.y);
                out.writeInt(alchemy.brewProgress);
                out.writeInt(alchemy.brewTimeTotal);

                out.writeBoolean(alchemy.currentRecipe != null);
                if (alchemy.currentRecipe != null) {
                    out.writeUTF(alchemy.currentRecipe);
                }

                // Create item array (5 slots)
                ItemSlot[] slots = new ItemSlot[5];
                for (ItemSlot slot : alchemy.items) {
                    slots[slot.slotX] = slot;
                }

                for (int i = 0; i < 5; i++) {
                    ItemSlot slot = slots[i];
                    if (slot != null && slot.itemId != null) {
                        out.writeBoolean(true);
                        out.writeUTF(slot.itemId);
                        out.writeInt(slot.count);
                        // [NEW] Save Mastercrafted/Tool data (Version 3)
                        out.writeBoolean(slot.isMastercrafted);
                        out.writeInt(slot.toolUses);
                    } else {
                        out.writeBoolean(false);
                    }
                }
            }

            // Write pots
            out.writeInt(data.pots.size());
            for (PotEntry pot : data.pots) {
                out.writeInt(pot.x);
                out.writeInt(pot.y);

                out.writeBoolean(pot.plantId != null);
                if (pot.plantId != null) {
                    out.writeUTF(pot.plantId);
                }
                out.writeInt(pot.growthProgress);
                out.writeInt(pot.growthTimeTotal);
            }

            // Write entities (Version 4+)
            out.writeInt(data.entities.size());
            for (EntityEntry entity : data.entities) {
                out.writeUTF(entity.type != null ? entity.type : "Unknown");
                out.writeFloat(entity.x);
                out.writeFloat(entity.y);
                out.writeFloat(entity.dx);
                out.writeFloat(entity.dy);
                out.writeInt(entity.hitPoints);

                // Item data
                out.writeBoolean(entity.itemId != null);
                if (entity.itemId != null) {
                    out.writeUTF(entity.itemId);
                    out.writeBoolean(entity.isMastercrafted);
                    out.writeInt(entity.toolUses);
                }

                // Wolf data
                out.writeBoolean(entity.ownerUUID != null);
                if (entity.ownerUUID != null) {
                    out.writeUTF(entity.ownerUUID);
                }
                out.writeBoolean(entity.isSitting);
            }
        }
    }
}
