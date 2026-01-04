package mc.sayda.mcraze.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.Player;
import mc.sayda.mcraze.server.Server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Modern save system with multiple world support
 *
 * Structure:
 * Windows: %APPDATA%/MCraze/saves/
 * Linux: ~/.mcraze/saves/
 * Mac: ~/Library/Application Support/MCraze/saves/
 *
 * Each world:
 *   WorldName/
 *     level.dat     (JSON metadata)
 *     world.dat     (binary tile data)
 *     entities.dat  (JSON entity data)
 */
public class WorldSaveManager {
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Get the OS-specific save directory
	 */
	public static String getSavesDirectory() {
		String os = System.getProperty("os.name").toLowerCase();
		String userHome = System.getProperty("user.home");
		String saveDir;

		if (os.contains("win")) {
			// Windows: %APPDATA%/MCraze/saves
			String appdata = System.getenv("APPDATA");
			if (appdata != null) {
				saveDir = appdata + File.separator + "MCraze" + File.separator + "saves";
			} else {
				saveDir = userHome + File.separator + "MCraze" + File.separator + "saves";
			}
		} else if (os.contains("mac")) {
			// Mac: ~/Library/Application Support/MCraze/saves
			saveDir = userHome + File.separator + "Library" + File.separator +
					"Application Support" + File.separator + "MCraze" + File.separator + "saves";
		} else {
			// Linux/Unix: ~/.mcraze/saves
			saveDir = userHome + File.separator + ".mcraze" + File.separator + "saves";
		}

		return saveDir;
	}

	/**
	 * Metadata for a saved world
	 */
	public static class WorldMetadata {
		public String worldName;
		public long seed;
		public int worldWidth;
		public int worldHeight;
		public long createdTime;
		public long lastPlayedTime;
		public String gameVersion = "0.1.0";

		public WorldMetadata() {}

		public WorldMetadata(String worldName, long seed, int width, int height) {
			this.worldName = worldName;
			this.seed = seed;
			this.worldWidth = width;
			this.worldHeight = height;
			this.createdTime = Instant.now().toEpochMilli();
			this.lastPlayedTime = this.createdTime;
		}
	}

	/**
	 * Entity save data
	 */
	public static class EntityData {
		public String type;  // Player, Item, Tool, etc.
		public float x;
		public float y;
		public float dx;
		public float dy;
		public int hitPoints;

		// Inventory data (for Player entities)
		public String[] inventoryItemIds;
		public int[] inventoryItemCounts;
		public int[] inventoryToolUses;
		public int inventoryHotbarIdx;

		public EntityData() {}
	}

	/**
	 * Get list of all available saved worlds
	 */
	public static List<WorldMetadata> getAvailableWorlds() {
		List<WorldMetadata> worlds = new ArrayList<>();
		File savesDir = new File(getSavesDirectory());

		if (!savesDir.exists()) {
			return worlds;
		}

		File[] worldDirs = savesDir.listFiles(File::isDirectory);
		if (worldDirs == null) return worlds;

		for (File worldDir : worldDirs) {
			File levelFile = new File(worldDir, "level.dat");
			if (levelFile.exists()) {
				try {
					String json = new String(Files.readAllBytes(levelFile.toPath()));
					WorldMetadata metadata = gson.fromJson(json, WorldMetadata.class);
					worlds.add(metadata);
				} catch (IOException e) {
					System.err.println("Failed to read world metadata: " + worldDir.getName());
				}
			}
		}

		return worlds;
	}

	/**
	 * Save a world atomically to prevent corruption
	 * ISSUE #6 fix: Atomic saves with backup system
	 */
	public static boolean saveWorld(String worldName, Server server) {
		if (server.world == null) {
			System.err.println("Cannot save: world is null");
			return false;
		}

		try {
			// Create world directory
			Path worldPath = Paths.get(getSavesDirectory(), sanitizeWorldName(worldName));
			Files.createDirectories(worldPath);

			// --- ATOMIC SAVE: Write to temp files first ---
			Path levelTemp = worldPath.resolve("level.dat.tmp");
			Path worldTemp = worldPath.resolve("world.dat.tmp");
			Path entitiesTemp = worldPath.resolve("entities.dat.tmp");

			// Save metadata to temp file
			WorldMetadata metadata = new WorldMetadata(
				worldName,
				server.world.getSeed(),  // CRITICAL FIX: Save actual world seed instead of 0
				server.world.width,
				server.world.height
			);
			metadata.lastPlayedTime = Instant.now().toEpochMilli();

			try (FileWriter writer = new FileWriter(levelTemp.toFile())) {
				gson.toJson(metadata, writer);
			}

			// Save world data to temp file (binary format)
			saveWorldData(worldTemp.toFile(), server.world);

			// Save entities to temp file (JSON format)
			saveEntities(entitiesTemp.toFile(), server.entities);

			// --- ATOMIC COMMIT: Backup old files, then rename new ones ---
			Path levelFinal = worldPath.resolve("level.dat");
			Path worldFinal = worldPath.resolve("world.dat");
			Path entitiesFinal = worldPath.resolve("entities.dat");

			// Backup existing files (keep one backup)
			if (Files.exists(levelFinal)) {
				Files.move(levelFinal, worldPath.resolve("level.dat.bak"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.exists(worldFinal)) {
				Files.move(worldFinal, worldPath.resolve("world.dat.bak"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.exists(entitiesFinal)) {
				Files.move(entitiesFinal, worldPath.resolve("entities.dat.bak"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}

			// Atomic rename: temp -> final
			Files.move(levelTemp, levelFinal, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			Files.move(worldTemp, worldFinal, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			Files.move(entitiesTemp, entitiesFinal, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

			System.out.println("World '" + worldName + "' saved atomically with backup");
			return true;

		} catch (IOException e) {
			System.err.println("Failed to save world: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Load a world with automatic backup recovery
	 * ISSUE #6 fix: Try backup if main files corrupted
	 */
	/**
	 * Load just the World object (for SharedWorld architecture)
	 * Returns null if world doesn't exist or fails to load
	 */
	public static World loadWorldOnly(String worldName) {
		Path worldPath = Paths.get(getSavesDirectory(), sanitizeWorldName(worldName));

		if (!Files.exists(worldPath)) {
			System.err.println("World '" + worldName + "' does not exist");
			return null;
		}

		// Try loading from main files first
		try {
			return loadWorldDataFromPath(worldPath, false);
		} catch (Exception e) {
			System.err.println("Failed to load world from main files: " + e.getMessage());

			// Try backup files
			System.out.println("Attempting to restore from backup...");
			try {
				World world = loadWorldDataFromPath(worldPath, true);
				if (world != null) {
					System.out.println("Successfully restored world from backup!");
				}
				return world;
			} catch (Exception backupError) {
				System.err.println("Backup restoration also failed: " + backupError.getMessage());
				backupError.printStackTrace();
				return null;
			}
		}
	}

	/**
	 * Helper method to load World data from path
	 */
	private static World loadWorldDataFromPath(Path worldPath, boolean useBackup) throws IOException {
		String levelName = useBackup ? "level.dat.bak" : "level.dat";
		String worldDataName = useBackup ? "world.dat.bak" : "world.dat";

		// Load metadata
		File levelFile = worldPath.resolve(levelName).toFile();
		String json = new String(Files.readAllBytes(levelFile.toPath()));
		WorldMetadata metadata = gson.fromJson(json, WorldMetadata.class);

		// Load world data
		World world = loadWorldData(worldPath.resolve(worldDataName).toFile(),
			metadata.worldWidth, metadata.worldHeight);

		// CRITICAL FIX: Restore world seed from metadata
		if (world != null) {
			world.setSeed(metadata.seed);
		}

		return world;
	}

	public static boolean loadWorld(String worldName, Server server) {
		Path worldPath = Paths.get(getSavesDirectory(), sanitizeWorldName(worldName));

		if (!Files.exists(worldPath)) {
			System.err.println("World '" + worldName + "' does not exist");
			return false;
		}

		// Try loading from main files first
		try {
			return loadWorldFromPath(worldName, server, worldPath, false);
		} catch (Exception e) {
			System.err.println("Failed to load world from main files: " + e.getMessage());

			// Try backup files
			System.out.println("Attempting to restore from backup...");
			try {
				boolean success = loadWorldFromPath(worldName, server, worldPath, true);
				if (success) {
					System.out.println("Successfully restored world from backup!");
				}
				return success;
			} catch (Exception backupError) {
				System.err.println("Backup restoration also failed: " + backupError.getMessage());
				backupError.printStackTrace();
				return false;
			}
		}
	}

	/**
	 * Helper to load world from specific file set (main or backup)
	 */
	private static boolean loadWorldFromPath(String worldName, Server server,
			Path worldPath, boolean useBackup) throws IOException {
		// Determine file names
		String levelName = useBackup ? "level.dat.bak" : "level.dat";
		String worldDataName = useBackup ? "world.dat.bak" : "world.dat";
		String entitiesName = useBackup ? "entities.dat.bak" : "entities.dat";

		// Load metadata
		File levelFile = worldPath.resolve(levelName).toFile();
		String json = new String(Files.readAllBytes(levelFile.toPath()));
		WorldMetadata metadata = gson.fromJson(json, WorldMetadata.class);

		// Load world data
		server.world = loadWorldData(worldPath.resolve(worldDataName).toFile(),
			metadata.worldWidth, metadata.worldHeight);

		// CRITICAL FIX: Restore world seed from metadata
		if (server.world != null) {
			server.world.setSeed(metadata.seed);
		}

		// Set spawn location from loaded world
		if (server.world != null && server.world.spawnLocation != null) {
			server.setSpawnLocation(server.world.spawnLocation.x, server.world.spawnLocation.y);
		}

		// Load entities
		server.entities = loadEntities(worldPath.resolve(entitiesName).toFile());

		// Find player
		server.player = null;
		for (Entity entity : server.entities) {
			if (entity instanceof Player) {
				server.player = (Player) entity;
				break;
			}
		}

		String source = useBackup ? " from backup" : "";
		System.out.println("World '" + worldName + "' loaded successfully" + source);
		return true;
	}

	/**
	 * Delete a world
	 */
	public static boolean deleteWorld(String worldName) {
		try {
			Path worldPath = Paths.get(getSavesDirectory(), sanitizeWorldName(worldName));
			deleteDirectory(worldPath.toFile());
			System.out.println("World '" + worldName + "' deleted");
			return true;
		} catch (IOException e) {
			System.err.println("Failed to delete world: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Save world tile data in binary format
	 */
	private static void saveWorldData(File file, World world) throws IOException {
		try (DataOutputStream out = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(file)))) {

			// Write dimensions
			out.writeInt(world.width);
			out.writeInt(world.height);

			// Write biome map
			mc.sayda.mcraze.world.Biome[] biomes = world.getBiomeMap();
			if (biomes != null) {
				out.writeInt(biomes.length);
				for (mc.sayda.mcraze.world.Biome biome : biomes) {
					out.writeInt(biome.ordinal());
				}
			} else {
				out.writeInt(0);
			}

			// Write tile data with counting for debugging
			int dirtCount = 0, airCount = 0, noneCount = 0, otherCount = 0, nullCount = 0;
			for (int x = 0; x < world.width; x++) {
				for (int y = 0; y < world.height; y++) {
					if (world.tiles[x][y] != null && world.tiles[x][y].type != null) {
						int ordinal = world.tiles[x][y].type.name.ordinal();
						out.writeShort(ordinal);  // Use writeShort instead of writeChar for clarity

						// Count tiles for debugging
						if (ordinal == 0) noneCount++;
						else if (ordinal == 1) airCount++;
						else if (world.tiles[x][y].type.name == mc.sayda.mcraze.Constants.TileID.DIRT) dirtCount++;
						else otherCount++;
					} else {
						out.writeShort(0);  // NONE
						nullCount++;
					}
				}
			}

			System.out.println("WorldSaveManager.saveWorldData: Saved " + noneCount + " NONE, " + airCount + " AIR, " +
				dirtCount + " DIRT, " + otherCount + " other, " + nullCount + " null tiles");

			// Write backdrop data (version 2 feature)
			// First write a flag to indicate backdrop data presence (for backwards compatibility)
			out.writeBoolean(true);  // Backdrop data follows
			int backdropCount = 0;
			for (int x = 0; x < world.width; x++) {
				for (int y = 0; y < world.height; y++) {
					if (world.tiles[x][y] != null && world.tiles[x][y].backdropType != null) {
						out.writeShort(world.tiles[x][y].backdropType.name.ordinal());
						backdropCount++;
					} else {
						out.writeShort(0);  // No backdrop (NONE)
					}
				}
			}
			System.out.println("WorldSaveManager.saveWorldData: Saved " + backdropCount + " backdrop tiles");

			// Write world time
			out.writeLong(world.getTicksAlive());

			// Write chest data
			java.util.Map<String, mc.sayda.mcraze.world.ChestData> chests = world.getAllChests();
			out.writeInt(chests.size());  // Number of chests
			int chestCount = 0;
			for (mc.sayda.mcraze.world.ChestData chest : chests.values()) {
				// Write chest position
				out.writeInt(chest.x);
				out.writeInt(chest.y);

				// Write chest items (9x3 grid)
				for (int x = 0; x < 9; x++) {
					for (int y = 0; y < 3; y++) {
						mc.sayda.mcraze.item.InventoryItem invItem = chest.items[x][y];
						if (invItem != null && !invItem.isEmpty()) {
							out.writeBoolean(true);  // Item exists
							out.writeUTF(invItem.getItem().itemId);  // Item ID
							out.writeInt(invItem.getCount());  // Item count
						} else {
							out.writeBoolean(false);  // Empty slot
						}
					}
				}
				chestCount++;
			}
			System.out.println("WorldSaveManager.saveWorldData: Saved " + chestCount + " chests");
		}
	}

	/**
	 * Load world tile data from binary format
	 */
	private static World loadWorldData(File file, int width, int height) throws IOException {
		try (DataInputStream in = new DataInputStream(
				new BufferedInputStream(new FileInputStream(file)))) {

			// Read dimensions
			int savedWidth = in.readInt();
			int savedHeight = in.readInt();

			// Read biome map
			int biomeCount = in.readInt();
			mc.sayda.mcraze.world.Biome[] biomeMap = null;
			if (biomeCount > 0) {
				biomeMap = new mc.sayda.mcraze.world.Biome[biomeCount];
				for (int i = 0; i < biomeCount; i++) {
					int biomeOrdinal = in.readInt();
					if (biomeOrdinal >= 0 && biomeOrdinal < mc.sayda.mcraze.world.Biome.values().length) {
						biomeMap[i] = mc.sayda.mcraze.world.Biome.values()[biomeOrdinal];
					} else {
						biomeMap[i] = mc.sayda.mcraze.world.Biome.PLAINS;
					}
				}
			}

			// Create world with proper dimensions
			// Note: We'll need to modify World constructor to support loading
			mc.sayda.mcraze.Constants.TileID[][] tileData = new mc.sayda.mcraze.Constants.TileID[savedWidth][savedHeight];

			// Read tile data
			int dirtCount = 0, airCount = 0, noneCount = 0, otherCount = 0;
			for (int x = 0; x < savedWidth; x++) {
				for (int y = 0; y < savedHeight; y++) {
					short tileOrdinal = in.readShort();  // Use readShort to match writeShort
					if (tileOrdinal >= 0 && tileOrdinal < mc.sayda.mcraze.Constants.TileID.values().length) {
						tileData[x][y] = mc.sayda.mcraze.Constants.TileID.values()[tileOrdinal];
						// Count tile types
						if (tileOrdinal == 0) noneCount++;
						else if (tileOrdinal == 1) airCount++;
						else if (tileData[x][y] == mc.sayda.mcraze.Constants.TileID.DIRT) dirtCount++;
						else otherCount++;
					} else {
						tileData[x][y] = mc.sayda.mcraze.Constants.TileID.NONE;
						noneCount++;
					}
				}
			}
			System.out.println("WorldSaveManager.loadWorldData: Loaded " + noneCount + " NONE, " + airCount + " AIR, " +
				dirtCount + " DIRT, " + otherCount + " other tiles");

			// Read backdrop data (version 2 feature, may not exist in old saves)
			mc.sayda.mcraze.Constants.TileID[][] backdropData = null;
			boolean hasBackdropData = false;
			try {
				hasBackdropData = in.readBoolean();
				if (hasBackdropData) {
					backdropData = new mc.sayda.mcraze.Constants.TileID[savedWidth][savedHeight];
					int backdropCount = 0;
					for (int x = 0; x < savedWidth; x++) {
						for (int y = 0; y < savedHeight; y++) {
							short backdropOrdinal = in.readShort();
							if (backdropOrdinal > 0 && backdropOrdinal < mc.sayda.mcraze.Constants.TileID.values().length) {
								backdropData[x][y] = mc.sayda.mcraze.Constants.TileID.values()[backdropOrdinal];
								backdropCount++;
							} else {
								backdropData[x][y] = null;  // No backdrop
							}
						}
					}
					System.out.println("WorldSaveManager.loadWorldData: Loaded " + backdropCount + " backdrop tiles");
				}
			} catch (java.io.EOFException e) {
				// Old save file without backdrop data - this is expected
				System.out.println("WorldSaveManager.loadWorldData: No backdrop data (old save format)");
			}

			// Read world time
			long ticksAlive = in.readLong();

			// Read chest data (may not exist in old saves)
			java.util.Map<String, mc.sayda.mcraze.world.ChestData> chestData = new java.util.HashMap<>();
			try {
				int chestCount = in.readInt();
				for (int i = 0; i < chestCount; i++) {
					// Read chest position
					int chestX = in.readInt();
					int chestY = in.readInt();

					mc.sayda.mcraze.world.ChestData chest = new mc.sayda.mcraze.world.ChestData(chestX, chestY);

					// Read chest items (9x3 grid)
					for (int x = 0; x < 9; x++) {
						for (int y = 0; y < 3; y++) {
							boolean hasItem = in.readBoolean();
							if (hasItem) {
								String itemId = in.readUTF();
								int itemCount = in.readInt();
								mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
								if (item != null) {
									chest.items[x][y].setItem(item.clone());
									chest.items[x][y].setCount(itemCount);
								}
							}
						}
					}

					chestData.put(chest.getKey(), chest);
				}
				System.out.println("WorldSaveManager.loadWorldData: Loaded " + chestCount + " chests");
			} catch (java.io.EOFException e) {
				// Old save file without chest data - this is expected
				System.out.println("WorldSaveManager.loadWorldData: No chest data (old save format)");
			}

			// Create world from loaded data
			World world = new World(tileData, new java.util.Random());
			world.setTicksAlive(ticksAlive);

			// Apply backdrop data if it exists
			if (backdropData != null) {
				for (int x = 0; x < savedWidth; x++) {
					for (int y = 0; y < savedHeight; y++) {
						if (backdropData[x][y] != null) {
							mc.sayda.mcraze.world.Tile backdropTile = mc.sayda.mcraze.Constants.tileTypes.get(backdropData[x][y]);
							if (backdropTile != null && world.tiles[x][y] != null) {
								world.tiles[x][y].backdropType = backdropTile.type;
							}
						}
					}
				}
			}

			// Apply chest data
			for (mc.sayda.mcraze.world.ChestData chest : chestData.values()) {
				world.setChest(chest.x, chest.y, chest);
			}

			// Set biome map
			if (biomeMap != null) {
				world.setBiomeMap(biomeMap);
			}

			world.refreshLighting();  // Force lighting recalculation for torches

			return world;
		}
	}

	/**
	 * Save entities to JSON
	 */
	private static void saveEntities(File file, ArrayList<Entity> entities) throws IOException {
		List<EntityData> entityDataList = new ArrayList<>();

		for (Entity entity : entities) {
			EntityData data = new EntityData();
			data.type = entity.getClass().getSimpleName();
			data.x = entity.x;
			data.y = entity.y;
			data.dx = entity.dx;
			data.dy = entity.dy;

			if (entity instanceof mc.sayda.mcraze.entity.LivingEntity) {
				data.hitPoints = ((mc.sayda.mcraze.entity.LivingEntity) entity).hitPoints;
			}

			// Save player inventory
			if (entity instanceof mc.sayda.mcraze.entity.Player) {
				mc.sayda.mcraze.entity.Player player = (mc.sayda.mcraze.entity.Player) entity;
				mc.sayda.mcraze.ui.Inventory inv = player.inventory;

				if (inv != null && inv.inventoryItems != null) {
					int width = inv.inventoryItems.length;
					int height = inv.inventoryItems[0].length;
					int totalSlots = width * height;

					data.inventoryItemIds = new String[totalSlots];
					data.inventoryItemCounts = new int[totalSlots];
					data.inventoryToolUses = new int[totalSlots];
					data.inventoryHotbarIdx = inv.hotbarIdx;

					// Flatten 2D inventory array
					int index = 0;
					for (int y = 0; y < height; y++) {
						for (int x = 0; x < width; x++) {
							mc.sayda.mcraze.item.InventoryItem slot = inv.inventoryItems[x][y];
							if (slot != null && slot.item != null && slot.count > 0) {
								data.inventoryItemIds[index] = slot.item.itemId;
								data.inventoryItemCounts[index] = slot.count;

								if (slot.item instanceof mc.sayda.mcraze.item.Tool) {
									mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) slot.item;
									data.inventoryToolUses[index] = tool.uses;
								} else {
									data.inventoryToolUses[index] = 0;
								}
							} else {
								data.inventoryItemIds[index] = null;
								data.inventoryItemCounts[index] = 0;
								data.inventoryToolUses[index] = 0;
							}
							index++;
						}
					}
				}
			}

			entityDataList.add(data);
		}

		try (FileWriter writer = new FileWriter(file)) {
			gson.toJson(entityDataList, writer);
		}
	}

	/**
	 * Load entities from JSON
	 */
	private static ArrayList<Entity> loadEntities(File file) throws IOException {
		ArrayList<Entity> entities = new ArrayList<>();

		String json = new String(Files.readAllBytes(file.toPath()));
		EntityData[] entityDataArray = gson.fromJson(json, EntityData[].class);

		for (EntityData data : entityDataArray) {
			// Reconstruct entities based on type
			if ("Player".equals(data.type)) {
				Player player = new Player(true, data.x, data.y, 7 * 4, 14 * 4);
				player.hitPoints = data.hitPoints;

				// Restore player inventory
				if (data.inventoryItemIds != null && player.inventory != null) {
					mc.sayda.mcraze.ui.Inventory inv = player.inventory;
					int width = inv.inventoryItems.length;
					int height = inv.inventoryItems[0].length;
					int totalSlots = width * height;

					if (data.inventoryItemIds.length == totalSlots) {
						inv.hotbarIdx = data.inventoryHotbarIdx;

						// Unflatten inventory array
						int index = 0;
						for (int y = 0; y < height; y++) {
							for (int x = 0; x < width; x++) {
								String itemId = data.inventoryItemIds[index];
								int count = data.inventoryItemCounts[index];
								int uses = data.inventoryToolUses[index];

								mc.sayda.mcraze.item.InventoryItem slot = inv.inventoryItems[x][y];

								if (itemId == null || count == 0) {
									// Empty slot
									slot.setEmpty();
								} else {
									// Get item from registry
									mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
									if (item != null) {
										// Clone item and set count
										mc.sayda.mcraze.item.Item clonedItem = item.clone();
										slot.setItem(clonedItem);
										slot.setCount(count);

										// Restore tool durability
										if (clonedItem instanceof mc.sayda.mcraze.item.Tool) {
											mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) clonedItem;
											tool.uses = uses;
										}
									} else {
										// Item not found in registry
										slot.setEmpty();
										System.err.println("WorldSaveManager.loadEntities: Unknown item ID: " + itemId);
									}
								}

								index++;
							}
						}
					}
				}

				entities.add(player);
			}
			// TODO: Add other entity types (Item, Tool, etc.)
		}

		return entities;
	}

	/**
	 * Save world to a specific directory (for dedicated server)
	 * @param directory The directory to save to (e.g., "./world")
	 * @param server The server instance to save
	 * @return true if save succeeded
	 */
	public static boolean saveWorldToDirectory(Path directory, Server server) {
		if (server.world == null) {
			System.err.println("Cannot save: world is null");
			return false;
		}

		try {
			// Create directory if it doesn't exist
			Files.createDirectories(directory);

			// Save metadata (including world seed for regeneration)
			WorldMetadata metadata = new WorldMetadata(
				"world",
				server.world.getSeed(),  // CRITICAL FIX: Save actual world seed
				server.world.width,
				server.world.height
			);
			metadata.createdTime = Instant.now().toEpochMilli();
			metadata.lastPlayedTime = Instant.now().toEpochMilli();

			// Write to temp files first (atomic save)
			Path levelTemp = directory.resolve("level.dat.tmp");
			Path worldTemp = directory.resolve("world.dat.tmp");
			Path entitiesTemp = directory.resolve("entities.dat.tmp");

			// Save metadata
			try (FileWriter writer = new FileWriter(levelTemp.toFile())) {
				gson.toJson(metadata, writer);
			}

			// Save world data
			saveWorldData(worldTemp.toFile(), server.world);

			// Save entities
			saveEntities(entitiesTemp.toFile(), server.entities);

			// Atomic commit: backup old files, then rename new ones
			Path levelFinal = directory.resolve("level.dat");
			Path worldFinal = directory.resolve("world.dat");
			Path entitiesFinal = directory.resolve("entities.dat");

			if (Files.exists(levelFinal)) {
				Files.move(levelFinal, directory.resolve("level.dat.bak"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.exists(worldFinal)) {
				Files.move(worldFinal, directory.resolve("world.dat.bak"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			if (Files.exists(entitiesFinal)) {
				Files.move(entitiesFinal, directory.resolve("entities.dat.bak"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}

			// Atomic rename: temp -> final
			Files.move(levelTemp, levelFinal, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			Files.move(worldTemp, worldFinal, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
			Files.move(entitiesTemp, entitiesFinal, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

			System.out.println("World saved to " + directory.toAbsolutePath());
			return true;

		} catch (IOException e) {
			System.err.println("Failed to save world: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Load world from a specific directory (for dedicated server)
	 * @param directory The directory to load from (e.g., "./world")
	 * @param server The server instance to load into
	 * @return true if load succeeded
	 */
	public static boolean loadWorldFromDirectory(Path directory, Server server) {
		if (!Files.exists(directory)) {
			System.err.println("World directory does not exist: " + directory.toAbsolutePath());
			return false;
		}

		// Try loading from main files first
		try {
			return loadWorldFromPath("world", server, directory, false);
		} catch (Exception e) {
			System.err.println("Failed to load world from main files: " + e.getMessage());

			// Try backup files
			System.out.println("Attempting to restore from backup...");
			try {
				boolean success = loadWorldFromPath("world", server, directory, true);
				if (success) {
					System.out.println("Successfully restored world from backup!");
				}
				return success;
			} catch (Exception backupError) {
				System.err.println("Backup restoration also failed: " + backupError.getMessage());
				backupError.printStackTrace();
				return false;
			}
		}
	}

	/**
	 * Sanitize world name for use as directory name
	 */
	private static String sanitizeWorldName(String worldName) {
		return worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
	}

	/**
	 * Delete directory recursively
	 */
	private static void deleteDirectory(File directory) throws IOException {
		if (directory.exists()) {
			File[] files = directory.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isDirectory()) {
						deleteDirectory(file);
					} else {
						file.delete();
					}
				}
			}
			directory.delete();
		}
	}

	// ==================== CHEST PERSISTENCE ====================

	/**
	 * Save chest data to disk (JSON format)
	 * @param worldName Name of the world
	 * @param chestDataMap Map of chest data (key: "x,y", value: ChestData)
	 * @return true if save succeeded
	 */
	public static boolean saveChests(String worldName, java.util.Map<String, ChestData> chestDataMap) {
		if (chestDataMap == null) {
			return false;
		}

		Path worldPath = Paths.get(getSavesDirectory(), sanitizeWorldName(worldName));
		return saveChestsToDirectory(worldPath, chestDataMap);
	}

	/**
	 * Save chest data to a specific directory (for dedicated servers)
	 * @param directory The directory to save to (e.g., "./world")
	 * @param chestDataMap Map of chest data
	 * @return true if save succeeded
	 */
	public static boolean saveChestsToDirectory(Path directory, java.util.Map<String, ChestData> chestDataMap) {
		if (chestDataMap == null) {
			return false;
		}

		try {
			// Create directory if needed
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
			}

			Path chestsFile = directory.resolve("chests.dat");
			Path chestsBackup = directory.resolve("chests.dat.bak");

			// Backup existing file
			if (Files.exists(chestsFile)) {
				Files.copy(chestsFile, chestsBackup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}

			// Convert chest map to JSON
			String json = gson.toJson(chestDataMap);

			// Write atomically
			Path tempFile = directory.resolve("chests.dat.tmp");
			Files.write(tempFile, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			Files.move(tempFile, chestsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
			           java.nio.file.StandardCopyOption.ATOMIC_MOVE);

			return true;

		} catch (IOException e) {
			System.err.println("Failed to save chests: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Load chest data from disk (JSON format)
	 * @param worldName Name of the world
	 * @return Map of chest data, or empty map if file doesn't exist
	 */
	public static java.util.Map<String, ChestData> loadChests(String worldName) {
		Path worldPath = Paths.get(getSavesDirectory(), sanitizeWorldName(worldName));
		return loadChestsFromDirectory(worldPath);
	}

	/**
	 * Load chest data from a specific directory (for dedicated servers)
	 * @param directory The directory to load from (e.g., "./world")
	 * @return Map of chest data, or empty map if file doesn't exist
	 */
	public static java.util.Map<String, ChestData> loadChestsFromDirectory(Path directory) {
		Path chestsFile = directory.resolve("chests.dat");

		// If no chest file exists, return empty map (new world or no chests placed yet)
		if (!Files.exists(chestsFile)) {
			return new java.util.concurrent.ConcurrentHashMap<>();
		}

		try {
			// Try loading from main file
			String json = new String(Files.readAllBytes(chestsFile), java.nio.charset.StandardCharsets.UTF_8);

			// Deserialize JSON to Map<String, ChestData>
			java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, ChestData>>(){}.getType();
			java.util.Map<String, ChestData> chestMap = gson.fromJson(json, type);

			if (chestMap == null) {
				return new java.util.concurrent.ConcurrentHashMap<>();
			}

			// Convert to ConcurrentHashMap for thread safety
			return new java.util.concurrent.ConcurrentHashMap<>(chestMap);

		} catch (Exception e) {
			System.err.println("Failed to load chests from main file: " + e.getMessage());

			// Try backup file
			Path chestsBackup = directory.resolve("chests.dat.bak");
			if (Files.exists(chestsBackup)) {
				System.out.println("Attempting to restore chests from backup...");
				try {
					String json = new String(Files.readAllBytes(chestsBackup), java.nio.charset.StandardCharsets.UTF_8);
					java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, ChestData>>(){}.getType();
					java.util.Map<String, ChestData> chestMap = gson.fromJson(json, type);

					if (chestMap != null) {
						System.out.println("Successfully restored chests from backup!");
						return new java.util.concurrent.ConcurrentHashMap<>(chestMap);
					}
				} catch (Exception backupError) {
					System.err.println("Backup restoration also failed: " + backupError.getMessage());
				}
			}

			// Return empty map on failure
			return new java.util.concurrent.ConcurrentHashMap<>();
		}
	}
}
