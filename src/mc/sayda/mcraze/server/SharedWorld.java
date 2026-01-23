package mc.sayda.mcraze.server;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.projectile.EntityBoulder;
import mc.sayda.mcraze.entity.projectile.EntityArrow;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketBlockChange;
import mc.sayda.mcraze.network.packet.PacketBreakingProgress;
import mc.sayda.mcraze.network.packet.PacketChatMessage;
import mc.sayda.mcraze.network.packet.PacketChestOpen;
import mc.sayda.mcraze.network.packet.PacketFurnaceOpen;
import mc.sayda.mcraze.network.packet.PacketEntityUpdate;
import mc.sayda.mcraze.network.packet.PacketInteract;
import mc.sayda.mcraze.network.packet.PacketInventoryUpdate;
import mc.sayda.mcraze.network.packet.PacketAlchemyOpen;
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.network.packet.PacketWorldInit;
import mc.sayda.mcraze.network.packet.PacketWorldUpdate;
import mc.sayda.mcraze.world.tile.Tile;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.world.storage.WorldAccess;
import mc.sayda.mcraze.world.storage.ChestData;
import mc.sayda.mcraze.world.storage.FurnaceData;
import mc.sayda.mcraze.network.packet.PacketWorldBulkData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SharedWorld manages a single world instance shared by multiple players.
 * Handles world ticking, entity updates, and broadcasting to all connected
 * clients.
 */
public class SharedWorld {
	private World world;
	private WorldAccess worldAccess;
	private List<PlayerConnection> players;
	private Random random;
	private boolean running = true;
	private long ticksRunning = 0;
	private int tileSize = Constants.TILE_SIZE;

	// Per-player breaking state
	private java.util.concurrent.ConcurrentHashMap<Player, BreakingState> playerBreakingState = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<String, Long> interactCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long INTERACT_COOLDOWN_MS = 200; // General interaction cooldown (doors, chests, workbenches)

	// World entities (items, etc.) not associated with a specific player -
	// thread-safe
	private final EntityManager entityManager = new EntityManager();

	// Performance: Reusable lists to avoid allocation in hot paths
	private List<Entity> entitiesToRemove = new ArrayList<>();
	private List<Entity> entitiesToAdd = new ArrayList<>();

	public boolean isDay() {
		return world != null && world.isDay();
	}

	public boolean isNight() {
		return world != null && world.isNight();
	}

	private final List<Entity> entitiesToRemoveCache = new ArrayList<>();
	private final List<Entity> despawnedItemsCache = new ArrayList<>();

	// Authentication tracking
	// CRITICAL FIX: Use ConcurrentHashMap for thread-safety (accessed from multiple
	// threads)
	private java.util.concurrent.ConcurrentHashMap<String, PlayerConnection> playersByUsername = new java.util.concurrent.ConcurrentHashMap<>();
	private String worldName;
	private java.nio.file.Path worldDirectory; // null for integrated server, ./world for dedicated server

	// Command handler (for integrated server only)
	private mc.sayda.mcraze.ui.CommandHandler commandHandler;

	// Logging
	private final GameLogger logger = GameLogger.get();

	// Spawning
	private MobSpawner mobSpawner;

	// Wave System
	private mc.sayda.mcraze.survival.WaveManager waveManager;

	// Flag Defense System
	// Moved to World.flagLocation

	public SharedWorld(int worldWidth, Random random, String worldName, mc.sayda.mcraze.world.GameMode gameMode) {
		this(worldWidth, random, worldName, null, gameMode); // Integrated server - no world directory
	}

	public SharedWorld(int worldWidth, Random random, String worldName) {
		this(worldWidth, random, worldName, null, mc.sayda.mcraze.world.GameMode.CLASSIC); // Default to CLASSIC for
																							// backward compatibility
	}

	public SharedWorld(int worldWidth, Random random, String worldName, java.nio.file.Path worldDirectory,
			mc.sayda.mcraze.world.GameMode gameMode) {
		this.random = random;
		this.worldName = worldName;
		this.worldDirectory = worldDirectory;
		this.players = new CopyOnWriteArrayList<>(); // Thread-safe for concurrent access
		this.mobSpawner = new MobSpawner(this);

		// Initialize Wave Manager
		this.waveManager = new mc.sayda.mcraze.survival.WaveManager(this);

		// Sheep rule: Weight 50, Group 1-3, Non-hostile, Biomes: Plains, Forest,
		// Mountain
		// Sheep rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntitySheep.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Pig rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityPig.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Cow rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityCow.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Wolf rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityWolf.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Zombie rule: 28x56 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityZombie.class, 20, 1, 2, true, 28, 56,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN, mc.sayda.mcraze.world.gen.Biome.DESERT,
				mc.sayda.mcraze.world.gen.Biome.SNOW));

		// Generate world
		if (logger != null) {
			logger.info("SharedWorld: Generating world '" + worldName + "' (" + worldWidth + " wide)");
			logger.debug("SharedWorld.<init>: Using CopyOnWriteArrayList for thread-safe player list");
		}

		this.world = new World(worldWidth, worldWidth, random, gameMode);
		this.worldAccess = new WorldAccess(world);

		// [NEW] Register Trap Multiplier Provider for class-specific trap bonuses
		this.world.setTrapMultiplierProvider((x, y) -> {
			mc.sayda.mcraze.world.tile.Tile tile = world.getTile(x, y);
			if (tile != null && tile.ownerUUID != null) {
				// Find player by UUID
				Entity owner = entityManager.findByUUID(tile.ownerUUID);
				if (owner instanceof Player) {
					Player player = (Player) owner;
					return player.classStats.getTrapFallDamageMultiplier();
				}
			}
			return 1.0f;
		});

		// System.out.println("SharedWorld: World generated");
		if (logger != null) {
			logger.info("SharedWorld: World generated successfully - " + worldWidth + "x" + worldWidth);
			logger.debug("SharedWorld.<init>: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
			logger.debug("SharedWorld.<init>: WorldAccess wrapper created for thread-safe tile access");
		}
	}

	/**
	 * Constructor for loading an existing world (used by dedicated server)
	 * 
	 * @param world          Pre-loaded world instance
	 * @param random         Random instance for game logic
	 * @param worldName      Name of the world (for playerdata)
	 * @param worldDirectory Directory where world is stored (null for integrated
	 *                       server)
	 */
	public SharedWorld(World world, Random random, String worldName, java.nio.file.Path worldDirectory) {
		this.random = random;
		this.worldName = worldName;
		this.worldDirectory = worldDirectory;
		this.players = new CopyOnWriteArrayList<>();
		this.world = world;
		this.worldAccess = new WorldAccess(world);

		// Initialize spawner with rules
		this.mobSpawner = new MobSpawner(this);

		// Initialize Wave Manager
		this.waveManager = new mc.sayda.mcraze.survival.WaveManager(this);
		// Sheep rule: Weight 50, Group 1-3, Non-hostile, Biomes: Plains, Forest,
		// Mountain
		// Sheep rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntitySheep.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Pig rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityPig.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Cow rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityCow.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Wolf rule: 32x32 dimensions
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityWolf.class, 50, 1, 3, false, 32, 32,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN));

		// Zombie rule: 28x56 dimensions (exact match to Player: 7*4, 14*4)
		this.mobSpawner.addRule(new SpawnRule(mc.sayda.mcraze.entity.mob.EntityZombie.class, 20, 1, 2, true, 28, 56,
				mc.sayda.mcraze.world.gen.Biome.PLAINS, mc.sayda.mcraze.world.gen.Biome.FOREST,
				mc.sayda.mcraze.world.gen.Biome.MOUNTAIN, mc.sayda.mcraze.world.gen.Biome.DESERT,
				mc.sayda.mcraze.world.gen.Biome.SNOW));

		// Load chest data from disk
		java.util.Map<String, mc.sayda.mcraze.world.storage.ChestData> loadedChests = mc.sayda.mcraze.world.storage.WorldSaveManager
				.loadChests(worldName);
		if (loadedChests != null && !loadedChests.isEmpty()) {
			world.setChests(loadedChests);
			if (logger != null) {
				logger.info("SharedWorld: Loaded " + loadedChests.size() + " chests");
			}
		}

		if (logger != null) {
			logger.info("SharedWorld: Loaded existing world '" + worldName + "' - " + world.width + "x" + world.height);
			logger.debug("SharedWorld.<init>: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
			logger.debug("SharedWorld.<init>: WorldAccess wrapper created for thread-safe tile access");
		}
	}

	/**
	 * Constructor for loading an existing world (backward compatibility -
	 * integrated server)
	 */
	public SharedWorld(World world, Random random, String worldName) {
		this(world, random, worldName, null); // Integrated server - no world directory
	}

	/**
	 * Add a player to the shared world with authentication
	 * 
	 * @return PlayerConnection if successful, null if authentication failed or
	 *         duplicate username
	 */
	public PlayerConnection addPlayer(Connection connection, String username, String password) {
		if (logger != null)
			logger.info("SharedWorld: Adding player: " + username);
		if (logger != null) {
			logger.info("SharedWorld: Adding player: " + username);
			logger.debug(
					"SharedWorld.addPlayer: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
		}

		// Check for duplicate username
		if (playersByUsername.containsKey(username)) {
			if (logger != null)
				logger.warn("SharedWorld: Rejected duplicate username: " + username);
			return null;
		}

		// Authenticate or auto-register
		mc.sayda.mcraze.player.data.PlayerData playerData;
		if (worldDirectory != null) {
			// Dedicated server - use world directory
			playerData = mc.sayda.mcraze.player.data.PlayerDataManager.authenticate(worldDirectory, username, password);
			if (playerData == null && mc.sayda.mcraze.player.data.PlayerDataManager.exists(worldDirectory, username)) {
				// Wrong password
				if (logger != null)
					logger.warn("SharedWorld: Authentication failed for " + username);
				return null;
			}
		} else {
			// Integrated server - use AppData
			playerData = mc.sayda.mcraze.player.data.PlayerDataManager.authenticate(worldName, username, password);
			if (playerData == null && mc.sayda.mcraze.player.data.PlayerDataManager.exists(worldName, username)) {
				// Wrong password
				if (logger != null)
					logger.warn("SharedWorld: Authentication failed for " + username);
				return null;
			}
		}

		// Create player entity
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Creating player entity...");
		Player player = new Player(
				true,
				world.spawnLocation.x,
				world.spawnLocation.y - 1.75f,
				7 * 4,
				56);
		player.username = username; // Set player's display name
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Player entity created for " + username);

		// Load or create playerdata
		if (playerData == null) {
			// Auto-register new player
			playerData = new mc.sayda.mcraze.player.data.PlayerData(
					username, password, world.spawnLocation.x, world.spawnLocation.y);
			if (worldDirectory != null) {
				mc.sayda.mcraze.player.data.PlayerDataManager.save(worldDirectory, playerData);
			} else {
				mc.sayda.mcraze.player.data.PlayerDataManager.save(worldName, playerData);
			}
			if (logger != null)
				logger.info("SharedWorld: Auto-registered new player: " + username);
			if (logger != null)
				logger.info("SharedWorld: Auto-registered new player: " + username);
		} else {
			// Apply existing playerdata
			mc.sayda.mcraze.player.data.PlayerDataManager.applyToPlayer(playerData, player);
			if (logger != null)
				logger.info("SharedWorld: Loaded playerdata for " + username);
			if (logger != null)
				logger.info("SharedWorld: Loaded playerdata for " + username);
		}

		// Create player connection
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Creating PlayerConnection...");
		PlayerConnection playerConnection = new PlayerConnection(connection, player, username, password, this);
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: PlayerConnection created");

		// Add to tracking maps
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Adding to players list...");
		players.add(playerConnection);
		playersByUsername.put(username, playerConnection);
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Added to players list (size now: " + players.size() + ")");

		// Send initial world state to this player
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Sending initial world state...");

		// FLAG SYSTEM: Give flag to the host (first player) if not already placed
		// Only give if flag is NOT placed in world (flagLocation == null)
		// AND gamemode is SURVIVAL or HORDE
		boolean isDefenseMode = (world.gameMode == mc.sayda.mcraze.world.GameMode.SURVIVAL
				|| world.gameMode == mc.sayda.mcraze.world.GameMode.HORDE);
		if (players.size() == 1 && world.flagLocation == null && isDefenseMode) {
			// Check if they already have it
			boolean hasFlag = false;
			for (int i = 0; i < player.inventory.inventoryItems.length; i++) {
				for (int j = 0; j < player.inventory.inventoryItems[i].length; j++) {
					mc.sayda.mcraze.item.InventoryItem item = player.inventory.inventoryItems[i][j];
					if (item != null && item.getItem() != null && "flag".equals(item.getItem().itemId)) {
						hasFlag = true;
						break;
					}
				}
				if (hasFlag)
					break;
			}

			if (!hasFlag) {
				mc.sayda.mcraze.item.Item flagItem = Constants.itemTypes.get("flag");
				if (flagItem != null) {
					player.inventory.addItem(flagItem, 1);
					logger.info("SharedWorld: Given Kingdom Flag to host " + username);
				} else {
					logger.error("SharedWorld: Could not find 'flag' item definition!");
				}
			}
		}

		sendInitialWorldState(playerConnection);
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Initial world state sent");

		// CRITICAL FIX: Immediately sync inventory to client on login
		// Without this, inventory only appears after hotbar scroll
		if (player.inventory != null && player.inventory.inventoryItems != null) {
			sendInventoryUpdate(playerConnection);

			// NEW: Send initial Wave Status
			broadcastWaveSync();

			if (logger != null) {
				logger.info("Sent initial inventory sync to " + username + " on login");
			}
		}

		// Update class stats and Force immediate entity update to sync stats
		// (Mana/Essence/Bow)
		player.updateClassStats();
		sendEntityUpdateToPlayer(playerConnection);

		if (logger != null)
			logger.info("SharedWorld: Player added (" + players.size() + " total players)");
		if (logger != null)
			logger.info("SharedWorld: Player " + username + " successfully added (" + players.size() + " total)");
		return playerConnection;
	}

	/**
	 * Remove a player from the shared world
	 */
	public void removePlayer(PlayerConnection playerConnection) {
		if (logger != null)
			logger.info("SharedWorld: Removing player: " + playerConnection.getPlayerName());
		if (logger != null)
			logger.info("SharedWorld: Removing player: " + playerConnection.getPlayerName());

		// Save playerdata before removing
		savePlayerData(playerConnection);

		// Remove player entity from entity manager
		Player playerEntity = playerConnection.getPlayer();
		if (playerEntity != null) {
			entityManager.remove(playerEntity);

			// Clean up per-player breaking state (Fix memory leak)
			playerBreakingState.remove(playerEntity);

			if (logger != null)
				logger.info("SharedWorld: Removed player entity for " + playerConnection.getPlayerName());
			if (logger != null)
				logger.info("SharedWorld: Removed player entity for " + playerConnection.getPlayerName());

			// Broadcast entity removal to all clients
			mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket = new mc.sayda.mcraze.network.packet.PacketEntityRemove(
					playerEntity.getUUID());
			broadcastPacket(removePacket);
			if (logger != null)
				logger.debug("SharedWorld: Broadcasted entity removal for UUID: " + playerEntity.getUUID());
		}

		// Remove from tracking
		players.remove(playerConnection);
		playersByUsername.remove(playerConnection.getPlayerName());

		if (logger != null)
			logger.info("SharedWorld: Player removed (" + players.size() + " remaining players)");
		if (logger != null)
			logger.info("SharedWorld: Player removed (" + players.size() + " remaining)");
	}

	/**
	 * Save playerdata for a specific player
	 */
	public void savePlayerData(PlayerConnection playerConnection) {
		if (worldName == null || playerConnection == null)
			return;

		String username = playerConnection.getPlayerName();
		String password = playerConnection.getPassword();
		Player player = playerConnection.getPlayer();

		if (player != null) {
			mc.sayda.mcraze.player.data.PlayerData data = mc.sayda.mcraze.player.data.PlayerDataManager
					.extractFromPlayer(username,
							password, player);

			boolean saved;
			if (worldDirectory != null) {
				// Dedicated server - save to world directory
				saved = mc.sayda.mcraze.player.data.PlayerDataManager.save(worldDirectory, data);
			} else {
				// Integrated server - save to AppData
				saved = mc.sayda.mcraze.player.data.PlayerDataManager.save(worldName, data);
			}

			if (saved) {
				if (logger != null)
					logger.info("SharedWorld: Saved playerdata for " + username);
			} else {
				if (logger != null)
					logger.error("SharedWorld: Failed to save playerdata for " + username);
			}
		}
	}

	/**
	 * Send initial world state to a newly connected player
	 */
	private void sendInitialWorldState(PlayerConnection playerConnection) {
		if (logger != null)
			logger.info("SharedWorld: Sending initial world state to " + playerConnection.getPlayerName());

		int worldWidth = world.width;
		int worldHeight = world.height;

		if (logger != null) {
			logger.debug("SharedWorld.sendInitialWorldState: World dimensions: " + worldWidth + "x" + worldHeight);
		}

		// Calculate total packets (Increase to 10 to cover extra sync packets like
		// Auth/Chat/Entities)
		int totalPackets = 10;

		// Send world initialization packet with dimensions and spawn location
		String playerUUID = playerConnection.getPlayer().getUUID();
		PacketWorldInit initPacket = new PacketWorldInit(
				world.width,
				world.height,
				0,
				world.spawnLocation.x,
				world.spawnLocation.y);
		initPacket.playerUUID = playerUUID;
		initPacket.totalPacketsExpected = totalPackets;
		initPacket.spelunking = world.spelunking;
		initPacket.keepInventory = world.keepInventory;
		initPacket.daylightCycle = world.daylightCycle;
		initPacket.mobGriefing = world.mobGriefing;
		initPacket.pvp = world.pvp;
		initPacket.insomnia = world.insomnia;
		playerConnection.getConnection().sendPacket(initPacket);

		// Send biome data
		mc.sayda.mcraze.network.packet.PacketBiomeData biomePacket = new mc.sayda.mcraze.network.packet.PacketBiomeData(
				world.getBiomeMap());
		playerConnection.getConnection().sendPacket(biomePacket);

		playerConnection.getConnection().flush(); // Ensure init packets arrive first

		// OPTIMIZATION: Send World Data as Bulk Compressed Packet
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Preparing Bulk World Packet...");

		byte[] tileData = new byte[worldWidth * worldHeight];
		byte[] metaData = new byte[worldWidth * worldHeight];
		byte[] backdropData = new byte[worldWidth * worldHeight];

		// Fill byte array (Column-Major order: X outer, Y inner)
		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = worldAccess.getTile(x, y);
				int index = x * worldHeight + y;

				// 1. Foreground & Metadata
				if (tile != null && tile.type != null && tile.type.name != Constants.TileID.AIR) {
					tileData[index] = (byte) tile.type.name.ordinal();
					metaData[index] = (byte) tile.metadata;
				} else {
					tileData[index] = 0;
					metaData[index] = 0;
				}

				// 2. Backdrops
				if (tile != null && tile.backdropType != null) {
					backdropData[index] = (byte) tile.backdropType.name.ordinal();
				} else {
					backdropData[index] = 0;
				}
			}
		}

		PacketWorldBulkData bulkPacket = new PacketWorldBulkData(0, 0, worldWidth, worldHeight, tileData, metaData,
				backdropData);
		playerConnection.getConnection().sendPacket(bulkPacket);
		playerConnection.getConnection().flush();

		if (logger != null)
			logger.info("SharedWorld.sendInitialWorldState: Sent compressed world data ("
					+ bulkPacket.compressedData.length + " bytes)");

		// Send initial entity update DIRECTLY to this player (not broadcast)
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Sending initial entity update directly to "
					+ playerConnection.getPlayerName());
		sendEntityUpdateToPlayer(playerConnection);
		if (logger != null) {
			logger.info("SharedWorld.sendInitialWorldState: Initial entity update sent to "
					+ playerConnection.getPlayerName());
			logger.info("SharedWorld: Initial world state complete for " + playerConnection.getPlayerName());
		}

		// CRITICAL: Flush all initial world data packets immediately
		// sendInitialWorldState() is called outside the normal tick loop (during player
		// join)
		// so flushAllConnections() won't run until next tick. Must flush manually here!
		playerConnection.getConnection().flush();
		if (logger != null) {
			logger.debug(
					"SharedWorld.sendInitialWorldState: Flushed connection for " + playerConnection.getPlayerName());
		}

		// Mark player as fully loaded - they can now receive live world updates
		playerConnection.setInitialWorldLoaded(true);
		if (logger != null) {
			logger.info("Player " + playerConnection.getPlayerName()
					+ " marked as fully loaded and ready for live updates");
		}
	}

	/**
	 * Send entity update to a specific player
	 */
	private void sendEntityUpdateToPlayer(PlayerConnection playerConnection) {
		List<Entity> allEntities = getAllEntities();
		if (allEntities.isEmpty()) {
			if (logger != null)
				logger.warn("SharedWorld.sendEntityUpdateToPlayer: No entities to send!");
			return;
		}

		if (logger != null) {
			logger.debug("SharedWorld.sendEntityUpdateToPlayer: Sending " + allEntities.size() + " entities to "
					+ playerConnection.getPlayerName());
		}

		// PERFORMANCE: Allocate packet with ensureCapacity (called rarely during player
		// join)
		PacketEntityUpdate packet = new PacketEntityUpdate();
		int entityCount = allEntities.size();
		packet.ensureCapacity(entityCount);

		for (int i = 0; i < entityCount; i++) {
			Entity entity = allEntities.get(i);
			packet.entityIds[i] = i;
			packet.entityUUIDs[i] = entity.getUUID();
			packet.entityX[i] = entity.x;
			packet.entityY[i] = entity.y;
			packet.entityDX[i] = entity.dx;
			packet.entityDY[i] = entity.dy;

			// Determine entity type
			if (entity instanceof Player) {
				packet.entityTypes[i] = "Player";
				packet.itemIds[i] = null;
				packet.playerNames[i] = ((Player) entity).username; // Send player name
			} else if (entity instanceof Item) {
				packet.entityTypes[i] = "Item";
				packet.itemIds[i] = ((Item) entity).itemId;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntitySheep) {
				packet.entityTypes[i] = "Sheep";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityPig) {
				packet.entityTypes[i] = "Pig";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityCow) {
				packet.entityTypes[i] = "Cow";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityZombie) {
				packet.entityTypes[i] = "Zombie";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityWolf) {
				packet.entityTypes[i] = "Wolf";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.projectile.EntityArrow) {
				packet.entityTypes[i] = "Arrow";
				packet.itemIds[i] = ((mc.sayda.mcraze.entity.projectile.EntityArrow) entity).getType().itemId;
				packet.playerNames[i] = null;
			} else if (entity instanceof mc.sayda.mcraze.entity.projectile.EntityBoulder) {
				packet.entityTypes[i] = "Boulder";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			} else {
				packet.entityTypes[i] = "Unknown";
				packet.itemIds[i] = null;
				packet.playerNames[i] = null;
			}

			if (entity instanceof LivingEntity) {
				LivingEntity livingEntity = (LivingEntity) entity;
				packet.entityHealth[i] = livingEntity.hitPoints;
				packet.facingRight[i] = livingEntity.facingRight;
				packet.dead[i] = livingEntity.dead;
				packet.ticksAlive[i] = livingEntity.getTicksAlive();
				packet.ticksUnderwater[i] = livingEntity.ticksUnderwater;
				packet.flying[i] = livingEntity.flying;
				packet.noclip[i] = livingEntity.noclip;
				packet.sneaking[i] = livingEntity.sneaking;
				packet.climbing[i] = livingEntity.climbing;
				packet.jumping[i] = livingEntity.jumping;
				packet.speedMultiplier[i] = livingEntity.speedMultiplier;

				// Player-specific fields
				if (entity instanceof Player) {
					Player player = (Player) entity;
					packet.backdropPlacementMode[i] = player.backdropPlacementMode;
					packet.handTargetX[i] = player.handTargetPos.x;
					packet.handTargetY[i] = player.handTargetPos.y;

					// CRITICAL FIX: Synchronize held item for remote players
					packet.hotbarIndex[i] = player.inventory.hotbarIdx;
					mc.sayda.mcraze.item.InventoryItem selectedSlot = player.inventory.selectedItem();
					if (selectedSlot != null && !selectedSlot.isEmpty()) {
						packet.selectedItemId[i] = selectedSlot.getItem().itemId;
						packet.selectedItemCount[i] = selectedSlot.getCount();
						if (selectedSlot.getItem() instanceof mc.sayda.mcraze.item.Tool) {
							packet.selectedItemDurability[i] = ((mc.sayda.mcraze.item.Tool) selectedSlot
									.getItem()).uses;
						} else {
							packet.selectedItemDurability[i] = 0;
						}
					} else {
						packet.selectedItemId[i] = null;
						packet.selectedItemCount[i] = 0;
						packet.selectedItemDurability[i] = 0;
					}

					// SYNC STATS
					packet.mana[i] = player.mana;
					packet.maxMana[i] = player.maxMana;
					packet.essence[i] = player.essence;
					packet.maxEssence[i] = player.maxEssence;
					packet.bowCharge[i] = player.bowCharge;
					packet.maxBowCharge[i] = player.maxBowCharge;
				} else {
					packet.backdropPlacementMode[i] = false;
					packet.handTargetX[i] = -1;
					packet.handTargetY[i] = -1;
					packet.hotbarIndex[i] = 0;
					packet.selectedItemId[i] = null;
					packet.selectedItemCount[i] = 0;
					packet.selectedItemDurability[i] = 0;
					packet.mana[i] = 0;
					packet.maxMana[i] = 0;
					packet.essence[i] = 0;
					packet.maxEssence[i] = 0;
					packet.bowCharge[i] = 0;
					packet.maxBowCharge[i] = 0;
				}
			} else {
				packet.entityHealth[i] = 0;
				packet.facingRight[i] = true;
				packet.dead[i] = false;
				packet.ticksAlive[i] = 0;
				packet.ticksUnderwater[i] = 0;
				packet.flying[i] = false;
				packet.noclip[i] = false;
				packet.sneaking[i] = false;
				packet.climbing[i] = false;
				packet.jumping[i] = false;
				packet.speedMultiplier[i] = 1.0f;
				packet.backdropPlacementMode[i] = false;
				packet.handTargetX[i] = -1;
				packet.handTargetY[i] = -1;
				packet.hotbarIndex[i] = 0;
				packet.selectedItemId[i] = null;
				packet.selectedItemCount[i] = 0;
				packet.selectedItemDurability[i] = 0;
			}

			if (logger != null && i == 0) {
				logger.debug("SharedWorld.sendEntityUpdateToPlayer: Entity 0 pos: " + entity.x + ", " + entity.y);
			}
		}

		// Send to specific player only
		if (logger != null) {
			logger.debug("SharedWorld.sendEntityUpdateToPlayer: Sending packet to " + playerConnection.getPlayerName());
		}
		playerConnection.getConnection().sendPacket(packet);
		if (logger != null) {
			logger.info("SharedWorld.sendEntityUpdateToPlayer: Packet sent successfully to "
					+ playerConnection.getPlayerName());
		}
	}

	/**
	 * Main world tick - updates world and all entities
	 */
	public long getTicksRunning() {
		return ticksRunning;
	}

	public void tick() {
		if (!running)
			return;

		ticksRunning++;

		// Update Wave Manager (Day/Night cycle)
		// Only run waves if NOT in CLASSIC mode
		if (waveManager != null && world.gameMode != mc.sayda.mcraze.world.GameMode.CLASSIC) {
			waveManager.tick();
			checkFlagStatus();
		}

		// Spawning logic (every 1 second = 60 ticks)
		if (mobSpawner != null && ticksRunning % 60 == 0) {
			mobSpawner.tick();
		}

		// Despawn logic (every 5 seconds = 100 ticks)
		if (mobSpawner != null && ticksRunning % 100 == 0) {
			mobSpawner.despawnTick();
		}

		// Only log first few ticks in DEBUG mode
		if (logger != null && ticksRunning <= 10) {
			logger.debug("SharedWorld.tick: Tick " + ticksRunning + " - " + players.size() + " players");
		}

		// Check for disconnected players and remove them
		// This fixes "Players leaving not removed in integrated server"
		for (PlayerConnection pc : players) {
			if (!pc.isConnected()) {
				removePlayer(pc);
			}
		}

		// Process packets from all players
		try {
			if (logger != null && ticksRunning <= 5) {
				logger.debug("SharedWorld.tick: Processing packets from " + players.size() + " players...");
			}
			for (PlayerConnection playerConnection : players) {
				playerConnection.processPackets();
			}
			if (logger != null && ticksRunning <= 5) {
				logger.debug("SharedWorld.tick: Packets processed");
			}
		} catch (Exception e) {
			if (logger != null)
				logger.error("SharedWorld.tick: Error processing packets", e);
		}

		// PROCESS BLOCK BREAKING (Auto-increment for continuous breaking)
		try {
			// This fixes issues with VNC/High Latency where packets arrive irregularly
			for (java.util.Map.Entry<Player, BreakingState> entry : playerBreakingState.entrySet()) {
				Player player = entry.getKey();
				BreakingState state = entry.getValue();

				// Skip if not currently breaking
				if (state.ticks <= 0)
					continue;

				// Check for timeout (player stopped breaking or disconnected)
				long timeoutTicks = 60; // 1 second timeout at 60Hz
				if (ticksRunning - state.lastUpdateTick > timeoutTicks) {
					if (state.ticks > 0) {
						// Timed out - reset
						state.reset();
						// Notify client to stop animation
						for (PlayerConnection pc : players) {
							if (pc.getPlayer() == player) {
								pc.getConnection().sendPacket(new PacketBreakingProgress(state.x, state.y, 0, 0));
								break;
							}
						}
					}
					continue;
				}

				// Normalize speed to 20 ticks/second standard (Server runs at 60Hz)
				if (ticksRunning % 3 != 0)
					continue;

				int increment = 1;

				// Increment progress
				state.ticks += increment;

				// Check if broken
				Item currentItem = player.inventory.selectedItem().getItem();
				int ticksNeeded = world.breakTicks(state.x, state.y, currentItem); // Calibrated for 20Hz

				// Apply Class Speed Bonuses
				if (currentItem instanceof mc.sayda.mcraze.item.Tool && player.classStats != null) {
					mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) currentItem;
					mc.sayda.mcraze.player.specialization.ClassStats.GatheringType type = null;

					if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Pick) {
						type = mc.sayda.mcraze.player.specialization.ClassStats.GatheringType.MINING;
					} else if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Axe) {
						type = mc.sayda.mcraze.player.specialization.ClassStats.GatheringType.WOOD;
					} else if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Hoe) {
						type = mc.sayda.mcraze.player.specialization.ClassStats.GatheringType.FARMING;
					}

					if (type != null) {
						float multiplier = player.classStats.getGatheringSpeedMultiplier(type);
						if (multiplier > 1.0f) {
							ticksNeeded = (int) (ticksNeeded / multiplier);
							if (ticksNeeded < 1)
								ticksNeeded = 1;
						}
					}
				}

				if (player.debugMode)
					ticksNeeded = 1;

				// Send progress update (to keep animation smooth)
				for (PlayerConnection pc : players) {
					if (pc.getPlayer() == player) {
						pc.getConnection().sendPacket(new PacketBreakingProgress(state.x, state.y,
								state.ticks, ticksNeeded));
						break;
					}
				}

				if (state.ticks >= ticksNeeded) {
					// Block broken!
					finishBreakingBlock(player, state.x, state.y);
				}
			}
		} catch (Exception e) {
			if (logger != null)
				logger.error("SharedWorld.tick: Error processing block breaking", e);
		}

		// Update world physics (grass growth, sand falling, sapling growth, wheat
		// growth, water spread, etc.)
		try {
			if (world != null) {
				java.util.List<mc.sayda.mcraze.world.World.BlockChange> blockChanges = world
						.chunkUpdate(world.daylightCycle);

				// CRITICAL FIX: Broadcast all world physics changes to multiplayer clients
				// This syncs falling sand, sapling→tree growth, wheat growth, water spread,
				// etc.
				if (blockChanges != null && !blockChanges.isEmpty()) {
					for (mc.sayda.mcraze.world.World.BlockChange change : blockChanges) {
						broadcastBlockChange(change.x, change.y, change.tileId);
					}
					if (logger != null && ticksRunning <= 10 && !blockChanges.isEmpty()) {
						logger.debug("SharedWorld.tick: Broadcast " + blockChanges.size() + " world physics changes");
					}
				}

				// Tick furnaces (smelting logic)
				tickFurnaces();
			}
		} catch (Exception e) {
			if (logger != null)
				logger.error("SharedWorld.tick: Error during world physics/furnaces", e);
			e.printStackTrace();
		}

		// Update all player entities
		try {
			List<Entity> allEntities = getAllEntities();
			if (logger != null && ticksRunning <= 5) {
				logger.debug("SharedWorld.tick: Updating " + allEntities.size() + " entities...");
			}

			// Track entities to remove from worldEntities after iteration
			// Performance: Reuse cached list instead of allocating new one every tick
			entitiesToRemoveCache.clear();
			List<Entity> entitiesToRemove = entitiesToRemoveCache;

			Iterator<Entity> it = allEntities.iterator();
			while (it.hasNext()) {
				Entity entity = it.next();
				if (entity == null) {
					it.remove();
					continue;
				}

				// Check for item pickup (before position update)
				if (entity instanceof mc.sayda.mcraze.item.Item || entity instanceof mc.sayda.mcraze.item.Tool) {
					// Check collision with all players
					boolean pickedUp = false;
					for (PlayerConnection pc : players) {
						Player player = pc.getPlayer();
						if (player != null && !player.dead && player.collidesWith(entity, tileSize)) {
							mc.sayda.mcraze.item.Item item = (mc.sayda.mcraze.item.Item) entity;
							int leftover = player.giveItem(item, 1);
							if (leftover == 0) {
								// FIX: Item fully picked up - mark for removal from worldEntities
								// Only remove if inventory had space (leftover == 0)
								entitiesToRemove.add(entity);
								pickedUp = true;

								if (logger != null && ticksRunning <= 10) {
									logger.debug(
											"SharedWorld.tick: Player " + pc.getPlayerName() + " picked up "
													+ item.itemId);
								}
								break;
							} else {
								// FIX: Inventory full (leftover > 0) - item stays on ground
								// Entity is NOT added to entitiesToRemove, so it remains in the world
								if (logger != null && ticksRunning <= 10) {
									logger.debug("SharedWorld.tick: Player " + pc.getPlayerName()
											+ " inventory full, item remains on ground");
								}
								// Don't break - let other players try to pick it up
							}
						}
					}
					if (pickedUp) {
						// Broadcast inventory update immediately after pickup
						broadcastInventoryUpdates();
						continue; // Skip position update for removed item
					}
				}

				if (entity.dead) {
					continue;
				}

				// Allow entities access to server context (e.g. for AI targeting / Essence
				// attraction)
				entity.tick(this);

				// Update entity
				entity.updatePosition(world, Constants.TILE_SIZE);

				// Check for death
				if (entity instanceof LivingEntity) {
					LivingEntity livingEntity = (LivingEntity) entity;
					// Robust removal: If entity is marked dead, ensure it stays in removal list
					// until tick finishes
					if (livingEntity.dead) {
						entitiesToRemove.add(entity);
						continue;
					}

					if (livingEntity.hitPoints <= 0) {
						livingEntity.dead = true;
						handleEntityDeath(livingEntity, livingEntity.lastAttacker);
						continue; // Already handled removal in handeEntityDeath
					}
				}
			}

			// Remove picked-up items using thread-safe EntityManager
			if (!entitiesToRemove.isEmpty())

			{
				// Send entity removal packets immediately to all clients
				for (Entity entity : entitiesToRemove) {
					mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket = new mc.sayda.mcraze.network.packet.PacketEntityRemove();
					removePacket.entityUUID = entity.getUUID();
					broadcastPacket(removePacket);

					// Remove from EntityManager (thread-safe)
					entityManager.remove(entity);
				}
				if (logger != null && ticksRunning <= 10) {
					logger.debug("SharedWorld.tick: Removed " + entitiesToRemove.size() + " entities from world");
				}
			}
		} catch (Exception e) {
			if (logger != null)
				logger.error("SharedWorld.tick: Error updating entities", e);
		}

		if (logger != null && ticksRunning <= 5) {
			logger.debug("SharedWorld.tick: Entities updated");
		}

		// Broadcast entity updates at reduced rate ONLY for dedicated servers
		// Integrated server (including LAN): 60 Hz - fast enough for local network
		// Dedicated server: 10 Hz - prevents client FPS drop from packet processing
		// overhead
		if (logger != null && ticksRunning <= 15) {
			logger.debug("SharedWorld.tick: Broadcasting entity update (tick " + ticksRunning + ")");
		}

		// Entity updates at reduced rate (20 Hz) -> REVERTED: Causes visual stutter
		// broadcasting at 60Hz for smoothness
		broadcastEntityUpdate();
		// NOTE: Inventory updates are NOT broadcast on timer - only when inventory
		// actually changes
		// This prevents packet spam. Inventory is broadcast via handleBlockChange,
		// handleInventoryAction, etc.

		// PERFORMANCE: Flush all connections once per tick to batch network I/O
		// This prevents the progressive lag from 180 individual flushes/sec
		// Now flushes 60 times/sec (once per tick) regardless of player count
		flushAllConnections();

		// PERFORMANCE FIX: Broadcast world time less frequently to reduce packet spam
		// Changed from every 20 ticks to every 100 ticks (once per 5 seconds at 20 TPS)
		// This still ensures clients see daylight changes, but with much less network
		// overhead
		if (ticksRunning % 100 == 0)

		{
			broadcastWorldTime();
		}

		// Auto-save all players every 6000 ticks (5 minutes at 20 TPS)
		if (ticksRunning % 6000 == 0) {
			// PERFORMANCE: Commented out console logging (use logger instead)
			// System.out.println("SharedWorld: Auto-saving all players...");
			for (PlayerConnection pc : players) {
				savePlayerData(pc);
			}
			if (logger != null)
				logger.info("SharedWorld: Auto-saved " + players.size() + " players");

			// Auto-save WORLD and CHESTS asynchronously
			if (worldName != null && world != null) {
				// Use async full world save which includes chests
				mc.sayda.mcraze.world.storage.WorldSaveManager
						.saveWorldAsync(worldName, world, getAllEntities())
						.thenAccept(success -> {
							if (success) {
								if (logger != null && logger.isDebugEnabled()) {
									logger.debug("SharedWorld: Async auto-save complete");
								}
							}
						});

				// Deprecated: Old synchronous chest save
				// mc.sayda.mcraze.world.storage.WorldSaveManager.saveChests(worldName,
				// world.getAllChests());
			}
		}

		// Remove despawned items (prevents packet size growth and lag over time)
		// Check every 100 ticks (5 seconds) to avoid excessive iteration
		if (ticksRunning % 100 == 0) {
			// Performance: Reuse cached list instead of allocating every 100 ticks
			despawnedItemsCache.clear();
			for (Entity entity : entityManager.getAll()) {
				// 1. Check if entity is explicitly marked dead (e.g. killed mob)
				if (entity.dead) {
					despawnedItemsCache.add(entity);
					continue;
				}

				// 2. Check for item despawning (time-based)
				if (entity instanceof mc.sayda.mcraze.item.Item) {
					mc.sayda.mcraze.item.Item item = (mc.sayda.mcraze.item.Item) entity;
					if (item.shouldDespawn()) {
						despawnedItemsCache.add(entity);
					}
				}
			}
			// BUG FIX: Send removal packets to clients BEFORE removing from server
			// Otherwise clients will have "phantom" items that were despawned on server
			for (Entity entity : despawnedItemsCache) {
				mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket = new mc.sayda.mcraze.network.packet.PacketEntityRemove();
				removePacket.entityUUID = entity.getUUID();
				broadcastPacket(removePacket);

				// Now remove from server
				entityManager.remove(entity);
			}
			if (!despawnedItemsCache.isEmpty() && logger != null) {
				logger.info("SharedWorld: Despawned " + despawnedItemsCache.size() + " items");
			}
		}

		// Tick alchemies
		tickAlchemies();

		// Tick pots
		tickPots();
	}

	/**
	 * Get all entities from all players and world entities (dropped items, etc.)
	 * Returns a thread-safe snapshot using EntityManager
	 * PERFORMANCE: Pre-allocates ArrayList with expected capacity to avoid resizing
	 */
	public List<Entity> getAllEntities() {
		// CRITICAL FIX: Must return NEW list for thread safety!
		// Render thread iterates this list while server tick thread modifies entities
		// Returning cached list causes ConcurrentModificationException
		// Performance: Pre-allocate with capacity to avoid resizing (better than
		// creating 60 lists/sec with default size)
		int expectedSize = players.size() + entityManager.size();
		List<Entity> allEntities = new ArrayList<>(expectedSize);

		for (PlayerConnection playerConnection : players) {
			Player player = playerConnection.getPlayer();
			// CRITICAL FIX: Null check to prevent NPE during disconnection
			if (player != null) {
				allEntities.add(player);
			}
		}
		// Add world entities (dropped items, etc.) - thread-safe iteration
		// (CopyOnWriteArrayList)
		allEntities.addAll(entityManager.getAll());
		return allEntities;
	}

	/**
	 * Get entities visible to a specific player based on their camera viewport
	 * Uses same bounds calculation as World.draw() for consistency
	 * PERFORMANCE FIX: Implements entity culling to reduce network bandwidth
	 * 
	 * @param viewer       Player whose viewport to check
	 * @param screenWidth  Screen width for camera calculation (defaults to 1280)
	 * @param screenHeight Screen height for camera calculation (defaults to 720)
	 * @param tileSize     Tile size for coordinate conversion
	 * @return List of entities visible to this player
	 */
	private List<Entity> getEntitiesVisibleToPlayer(Player viewer, int screenWidth, int screenHeight, int tileSize) {
		if (viewer == null) {
			return java.util.Collections.emptyList();
		}

		// Calculate camera position (same math as Client.java:290-291)
		float cameraX = viewer.x - screenWidth / tileSize / 2f;

		// Calculate visible bounds (same math as World.java:890-893)
		// FIXED: Use CLIENT_MIN_ZOOM to account for players zooming out
		// This ensures entities are sent even if the player is zoomed out (viewing
		// larger area)
		float minZoom = Constants.CLIENT_MIN_ZOOM;
		float zoomedWidth = screenWidth / minZoom;
		float zoomedHeight = screenHeight / minZoom;

		int startX = Math.max(0, (int) Math.floor(cameraX) - (int) ((zoomedWidth / tileSize) / 2) - 2);
		int endX = (int) Math.ceil(cameraX + (float) zoomedWidth / tileSize) + 2;

		// Let's use player center directly for safety:
		int centerViewWidthTiles = (int) (zoomedWidth / tileSize);
		int centerViewHeightTiles = (int) (zoomedHeight / tileSize);

		startX = Math.max(0, (int) viewer.x - centerViewWidthTiles / 2 - 4); // Extra buffer
		endX = Math.min(world.width, (int) viewer.x + centerViewWidthTiles / 2 + 4);

		int startY = Math.max(0, (int) viewer.y - centerViewHeightTiles / 2 - 4);
		int endY = Math.min(world.height, (int) viewer.y + centerViewHeightTiles / 2 + 4);

		// Filter entities within visible bounds
		List<Entity> allEntities = getAllEntities();
		List<Entity> visibleEntities = new ArrayList<>(allEntities.size());

		for (Entity entity : allEntities) {
			// Entity position is in tiles (world coordinates)
			int entityX = (int) Math.floor(entity.x);
			int entityY = (int) Math.floor(entity.y);

			// Include if within viewport OR if it's the viewing player themselves
			if ((entityX >= startX && entityX <= endX && entityY >= startY && entityY <= endY)
					|| entity.getUUID().equals(viewer.getUUID())) {
				visibleEntities.add(entity);
			}
		}

		return visibleEntities;
	}

	/**
	 * Broadcast entity update to all players WITH ENTITY CULLING
	 * PERFORMANCE FIX: Each player receives only entities visible on their screen
	 * This reduces bandwidth from ~486KB/sec to ~162KB/sec with 3 players (67%
	 * reduction)
	 */
	public void broadcastEntityUpdate() {

		// PERFORMANCE FIX: Per-player entity culling based on camera viewport
		// Each player receives only entities visible on their screen
		// Uses same bounds calculation as World.draw() for consistency

		// Default screen size for viewport calculation (1280x720 covers most use cases)
		int screenWidth = 1280;
		int screenHeight = 720;
		int tileSize = Constants.TILE_SIZE;

		for (PlayerConnection playerConnection : players) {
			Player viewer = playerConnection.getPlayer();
			if (viewer == null)
				continue;

			// Get entities visible to this specific player
			List<Entity> visibleEntities = getEntitiesVisibleToPlayer(
					viewer, screenWidth, screenHeight, tileSize);

			if (visibleEntities.isEmpty()) {
				continue; // Skip if no entities visible
			}

			if (logger != null && ticksRunning <= 15) {
				logger.debug("SharedWorld.broadcastEntityUpdate: Player " + viewer.username
						+ " sees " + visibleEntities.size() + " entities (culled from "
						+ getAllEntities().size() + ")");
			}

			// Build packet for this player's visible entities only
			PacketEntityUpdate packet = new PacketEntityUpdate();
			int entityCount = visibleEntities.size();
			packet.ensureCapacity(entityCount);

			for (int i = 0; i < entityCount; i++) {
				Entity entity = visibleEntities.get(i);
				packet.entityIds[i] = i;
				packet.entityUUIDs[i] = entity.getUUID();
				packet.entityX[i] = entity.x;
				packet.entityY[i] = entity.y;
				packet.entityDX[i] = entity.dx;
				packet.entityDY[i] = entity.dy;
				packet.widthPX[i] = entity.widthPX;
				packet.heightPX[i] = entity.heightPX;
				packet.damageFlashTicks[i] = entity.damageFlashTicks;

				// Determine entity type
				if (entity instanceof Player) {
					packet.entityTypes[i] = "Player";
					packet.itemIds[i] = null;
					packet.playerNames[i] = ((Player) entity).username;
				} else if (entity instanceof Item) {
					packet.entityTypes[i] = "Item";
					packet.itemIds[i] = ((Item) entity).itemId;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntitySheep) {
					packet.entityTypes[i] = "Sheep";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityPig) {
					packet.entityTypes[i] = "Pig";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityCow) {
					packet.entityTypes[i] = "Cow";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityBomber) {
					packet.entityTypes[i] = "Bomber";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityZombie) {
					packet.entityTypes[i] = "Zombie";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityWolf) {
					packet.entityTypes[i] = "Wolf";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof EntityBoulder) {
					packet.entityTypes[i] = "Boulder";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.EntityEssence) {
					packet.entityTypes[i] = "Essence";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof EntityArrow) {
					packet.entityTypes[i] = "Arrow";
					packet.itemIds[i] = ((EntityArrow) entity).getType().itemId;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.item.EntityPrimedTNT) {
					packet.entityTypes[i] = "PrimedTNT";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else {
					packet.entityTypes[i] = "Unknown";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				}

				if (entity instanceof mc.sayda.mcraze.entity.item.EntityPrimedTNT) {
					mc.sayda.mcraze.entity.item.EntityPrimedTNT tnt = (mc.sayda.mcraze.entity.item.EntityPrimedTNT) entity;
					packet.isExploding[i] = true;
					packet.fuseTimer[i] = tnt.getFuseTimer();
				}

				if (entity instanceof LivingEntity) {
					LivingEntity livingEntity = (LivingEntity) entity;
					packet.entityHealth[i] = livingEntity.hitPoints;
					packet.facingRight[i] = livingEntity.facingRight;
					packet.dead[i] = livingEntity.dead;
					packet.ticksAlive[i] = livingEntity.getTicksAlive();
					packet.ticksUnderwater[i] = livingEntity.ticksUnderwater;
					packet.flying[i] = livingEntity.flying;
					packet.noclip[i] = livingEntity.noclip;
					packet.sneaking[i] = livingEntity.sneaking;
					packet.climbing[i] = livingEntity.climbing;
					packet.jumping[i] = livingEntity.jumping;
					packet.speedMultiplier[i] = livingEntity.speedMultiplier;

					if (livingEntity instanceof mc.sayda.mcraze.entity.mob.EntityBomber) {
						mc.sayda.mcraze.entity.mob.EntityBomber bomber = (mc.sayda.mcraze.entity.mob.EntityBomber) livingEntity;
						packet.isExploding[i] = bomber.isExploding();
						packet.fuseTimer[i] = bomber.getFuseTimer();
					} else {
						packet.isExploding[i] = false;
						packet.fuseTimer[i] = 0;
					}

					// Player-specific fields
					if (entity instanceof Player) {
						Player player = (Player) entity;
						packet.backdropPlacementMode[i] = player.backdropPlacementMode;
						packet.handTargetX[i] = player.handTargetPos.x;
						packet.handTargetY[i] = player.handTargetPos.y;
						packet.skillPoints[i] = player.skillPoints;

						packet.hotbarIndex[i] = player.inventory.hotbarIdx;
						mc.sayda.mcraze.item.InventoryItem selectedSlot = player.inventory.selectedItem();
						if (selectedSlot != null && !selectedSlot.isEmpty()) {
							packet.selectedItemId[i] = selectedSlot.getItem().itemId;
							packet.selectedItemCount[i] = selectedSlot.getCount();
							if (selectedSlot.getItem() instanceof mc.sayda.mcraze.item.Tool) {
								packet.selectedItemDurability[i] = ((mc.sayda.mcraze.item.Tool) selectedSlot
										.getItem()).uses;
							} else {
								packet.selectedItemDurability[i] = 0;
							}
						} else {
							packet.selectedItemId[i] = null;
							packet.selectedItemCount[i] = 0;
							packet.selectedItemDurability[i] = 0;
						}

						// Stats sync (Always for players)
						packet.essence[i] = player.essence;
						packet.maxEssence[i] = player.maxEssence;
						packet.mana[i] = player.mana;
						packet.maxMana[i] = player.maxMana;
						packet.bowCharge[i] = player.bowCharge;
						packet.maxBowCharge[i] = player.maxBowCharge;
					}
				} else {
					packet.entityHealth[i] = 0;
					packet.facingRight[i] = true;
					packet.dead[i] = false;
					packet.ticksAlive[i] = 0;
					packet.ticksUnderwater[i] = 0;
					packet.flying[i] = false;
					packet.noclip[i] = false;
					packet.sneaking[i] = false;
					packet.climbing[i] = false;
					packet.jumping[i] = false;
					packet.speedMultiplier[i] = 1.0f;
					packet.backdropPlacementMode[i] = false;
					packet.handTargetX[i] = -1;
					packet.handTargetY[i] = -1;
					packet.hotbarIndex[i] = 0;
					packet.selectedItemId[i] = null;
					packet.selectedItemCount[i] = 0;
					packet.selectedItemDurability[i] = 0;
					packet.essence[i] = 0;
					packet.maxEssence[i] = 0;
					packet.mana[i] = 0;
					packet.maxMana[i] = 0;
				}
			}

			// Invalidate cache and send to this player only
			packet.invalidateCache();

			// Send to this player only (not broadcast)
			if (playerConnection.isInitialWorldLoaded()
					&& playerConnection.getConnection().isConnected()) {
				// Use batched flush (false) for performance
				playerConnection.getConnection().sendPacket(packet);
			}
		}

		if (logger != null && ticksRunning <= 15) {
			logger.debug("SharedWorld.broadcastEntityUpdate: Per-player culling complete");
		}
	}

	/**
	 * Build inventory packet for a specific player
	 * Extracted from broadcastInventoryUpdates for reuse
	 */
	private PacketInventoryUpdate buildInventoryPacket(Player player) {
		if (player == null || player.inventory == null) {
			return null;
		}

		PacketInventoryUpdate packet = new PacketInventoryUpdate();

		// Set player UUID so clients know whose inventory this is
		packet.playerUUID = player.getUUID();

		mc.sayda.mcraze.player.Inventory inv = player.inventory;
		int width = inv.inventoryItems.length;
		int height = inv.inventoryItems[0].length;
		int totalSlots = width * height;

		packet.itemIds = new String[totalSlots];
		packet.itemCounts = new int[totalSlots];
		packet.toolUses = new int[totalSlots];
		packet.itemMastercrafted = new boolean[totalSlots];
		packet.hotbarIndex = inv.hotbarIdx;

		// Inventory UI state
		packet.visible = inv.isVisible();
		packet.tableSizeAvailable = inv.tableSizeAvailable;

		// Craftable item preview
		mc.sayda.mcraze.item.InventoryItem craftable = inv.getCraftable();
		if (craftable != null && craftable.item != null && craftable.count > 0) {
			packet.craftableItemId = craftable.item.itemId;
			packet.craftableCount = craftable.count;
			packet.craftableMastercrafted = craftable.item.isMastercrafted;
			if (craftable.item instanceof Tool) {
				packet.craftableToolUse = ((Tool) craftable.item).uses;
			} else {
				packet.craftableToolUse = 0;
			}
		} else {
			packet.craftableItemId = null;
			packet.craftableCount = 0;
			packet.craftableToolUse = 0;
			packet.craftableMastercrafted = false;
		}

		// CRITICAL FIX: Synchronize cursor item (holding item) for all players
		// This ensures joined players see their own cursor item, not just the host
		mc.sayda.mcraze.item.InventoryItem holding = inv.holding;
		if (holding != null && holding.item != null && holding.count > 0) {
			packet.holdingItemId = holding.item.itemId;
			packet.holdingCount = holding.count;
			packet.holdingMastercrafted = holding.item.isMastercrafted;
			if (holding.item instanceof Tool) {
				packet.holdingToolUse = ((Tool) holding.item).uses;
			} else {
				packet.holdingToolUse = 0;
			}
		} else {
			packet.holdingItemId = null;
			packet.holdingCount = 0;
			packet.holdingToolUse = 0;
			packet.holdingMastercrafted = false;
		}

		// Populate Equipment Data
		int equipCount = (inv.equipment != null) ? inv.equipment.length : 0;
		packet.equipmentItemIds = new String[equipCount];
		packet.equipmentItemCounts = new int[equipCount];
		packet.equipmentToolUses = new int[equipCount];
		packet.equipmentMastercrafted = new boolean[equipCount];

		for (int i = 0; i < equipCount; i++) {
			mc.sayda.mcraze.item.InventoryItem slot = inv.equipment[i];
			if (slot != null && slot.item != null && slot.count > 0) {
				packet.equipmentItemIds[i] = slot.item.itemId;
				packet.equipmentItemCounts[i] = slot.count;
				packet.equipmentMastercrafted[i] = slot.item.isMastercrafted;
				if (slot.item instanceof Tool) {
					packet.equipmentToolUses[i] = ((Tool) slot.item).uses;
				} else {
					packet.equipmentToolUses[i] = 0;
				}
			} else {
				packet.equipmentItemIds[i] = null;
				packet.equipmentItemCounts[i] = 0;
				packet.equipmentToolUses[i] = 0;
				packet.equipmentMastercrafted[i] = false;
			}
		}

		// Flatten 2D inventory array
		int index = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				mc.sayda.mcraze.item.InventoryItem slot = inv.inventoryItems[x][y];
				if (slot != null && slot.item != null && slot.count > 0) {
					packet.itemIds[index] = slot.item.itemId;
					packet.itemCounts[index] = slot.count;
					packet.itemMastercrafted[index] = slot.item.isMastercrafted;

					// Track tool durability
					if (slot.item instanceof Tool) {
						Tool tool = (Tool) slot.item;
						packet.toolUses[index] = tool.uses;
					} else {
						packet.toolUses[index] = 0;
					}
				} else {
					packet.itemIds[index] = null;
					packet.itemCounts[index] = 0;
					packet.toolUses[index] = 0;
					packet.itemMastercrafted[index] = false;
				}
				index++;
			}
		}

		return packet;
	}

	/**
	 * Send inventory update to a specific player (targeted send)
	 * PERFORMANCE FIX: Changed from broadcast to targeted - each player only
	 * receives
	 * their own inventory
	 * This fixes O(n²) scaling that caused lag with 3+ players
	 */
	public void sendInventoryUpdate(PlayerConnection targetPlayer) {
		Player player = targetPlayer.getPlayer();
		PacketInventoryUpdate packet = buildInventoryPacket(player);

		if (packet != null && targetPlayer.isInitialWorldLoaded() && targetPlayer.getConnection().isConnected()) {
			targetPlayer.getConnection().sendPacket(packet);
		}
	}

	/**
	 * Broadcast inventory updates for all players
	 * PERFORMANCE FIX: Changed from O(n²) broadcast to O(n) targeted sends
	 * OLD: With 3 players, sent 9 packets (3 inventories × 3 recipients)
	 * NEW: With 3 players, sends 3 packets (each player gets only their own)
	 * This reduces packet count by 67% with 3 players, 75% with 4 players
	 */
	public void broadcastInventoryUpdates() {
		// Send each player ONLY their own inventory (targeted send)
		for (PlayerConnection playerConnection : players) {
			sendInventoryUpdate(playerConnection);
		}
	}

	/**
	 * Broadcast a block change to all players
	 */
	public void broadcastBlockChange(int x, int y, Constants.TileID tileId) {
		PacketWorldUpdate packet = new PacketWorldUpdate();
		packet.ticksAlive = world.getTicksAlive();
		packet.changedX = new int[] { x };
		packet.changedY = new int[] { y };
		packet.changedTiles = new char[] { tileId == null ? (char) 0 : (char) tileId.ordinal() };

		broadcast(packet);
	}

	/**
	 * Broadcast backdrop change to all connected players
	 */
	public void broadcastBackdropChange(int x, int y, Constants.TileID backdropId) {
		mc.sayda.mcraze.network.packet.PacketBackdropChange packet = new mc.sayda.mcraze.network.packet.PacketBackdropChange(
				x, y, backdropId);
		broadcast(packet);
	}

	/*
	 * Broadcast gamerule changes to all connected players
	 */
	public void broadcastGamerules() {
		mc.sayda.mcraze.network.packet.PacketGameruleUpdate packet = new mc.sayda.mcraze.network.packet.PacketGameruleUpdate(
				world.spelunking,
				world.darkness,
				world.daylightSpeed,
				world.keepInventory,
				world.daylightCycle,
				world.mobGriefing,
				world.pvp,
				world.insomnia);
		broadcast(packet);
	}

	/**
	 * Broadcast world time (for daylight cycle) without block changes
	 */
	private void broadcastWorldTime() {
		PacketWorldUpdate packet = new PacketWorldUpdate();
		packet.ticksAlive = world.getTicksAlive();
		packet.changedX = new int[0]; // No block changes
		packet.changedY = new int[0];
		packet.changedTiles = new char[0];

		broadcast(packet);
	}

	/**
	 * Broadcast a packet to all connected players
	 */
	private void broadcast(Object packet) {
		broadcast(packet, false); // Default: no immediate flush (batched)
	}

	/**
	 * Broadcast a sound effect to all players
	 */
	public void broadcastSound(String soundName) {
		mc.sayda.mcraze.network.packet.PacketPlaySound packet = new mc.sayda.mcraze.network.packet.PacketPlaySound(
				soundName);
		broadcast(packet);
	}

	public void broadcastSound(String soundName, float x, float y) {
		mc.sayda.mcraze.network.packet.PacketPlaySound packet = new mc.sayda.mcraze.network.packet.PacketPlaySound(
				soundName, x, y, 1.0f, 16.0f);
		broadcast(packet);
	}

	public void broadcastSound(String soundName, float x, float y, float volume, float maxDistance) {
		mc.sayda.mcraze.network.packet.PacketPlaySound packet = new mc.sayda.mcraze.network.packet.PacketPlaySound(
				soundName, x, y, volume, maxDistance);
		broadcast(packet);
	}

	/**
	 * Broadcast a packet to all connected players with optional immediate flush
	 * 
	 * @param packet         The packet to send
	 * @param immediateFlush If true, flush connection immediately after sending
	 *                       (for time-sensitive packets)
	 */
	private void broadcast(Object packet, boolean immediateFlush) {
		for (PlayerConnection playerConnection : players) {
			// Only send to fully loaded players to prevent ghost blocks during initial
			// world load
			if (playerConnection.isInitialWorldLoaded() && playerConnection.getConnection().isConnected()) {
				playerConnection.getConnection().sendPacket((mc.sayda.mcraze.network.Packet) packet);
				if (immediateFlush) {
					playerConnection.getConnection().flush();
				}
			}
		}
	}

	/**
	 * Broadcast a packet to all connected players (public for PlayerConnection)
	 */
	public void broadcastPacket(mc.sayda.mcraze.network.Packet packet) {
		broadcast(packet);
	}

	/**
	 * Flush all network connections to batch I/O operations.
	 * Called once per tick after all packets are sent.
	 * Reduces I/O syscall overhead dramatically on dedicated servers.
	 */
	private void flushAllConnections() {
		for (PlayerConnection playerConnection : players) {
			if (playerConnection.getConnection().isConnected()) {
				playerConnection.getConnection().flush();
			}
		}
	}

	/**
	 * Get or create breaking state for a player
	 */
	private BreakingState getBreakingState(Player player) {
		return playerBreakingState.computeIfAbsent(player, p -> new BreakingState());
	}

	/**
	 * Break floating unstable blocks when the block below is removed.
	 * Unstable blocks (plants, torches, etc.) require specific support blocks below
	 * them.
	 * When a supporting block is removed, the unstable block above should break and
	 * drop items.
	 */
	private void breakFloatingPlants(int x, int y) {

		// Check the block above (y-1, since Y increases downward)
		int aboveY = y - 1;

		// Check if there's an unstable block above that needs support
		if (aboveY >= 0 && world != null && world.tiles != null &&
				x >= 0 && x < world.tiles.length &&
				aboveY < world.tiles[0].length) {

			Tile tileAbove = world.tiles[x][aboveY];

			// Check if this block is unstable (needs ground support)
			if (tileAbove.type != null && !tileAbove.type.stable) {
				// Check if the block still has valid support
				// (The block we just broke at (x, y) was the support)
				// Since it's now removed, check what's there now
				Constants.TileID blockBelow = world.tiles[x][y].type.name;

				if (!isValidSupportFor(tileAbove.type.name, blockBelow)) {
					// Block has lost support - break it
					Constants.TileID blockAbove = tileAbove.type.name;
					world.removeTile(x, aboveY);

					// Drop block item
					if (blockAbove.itemDropId != null) {
						Item droppedItem = Constants.itemTypes.get(blockAbove.itemDropId);
						if (droppedItem != null) {
							Item item = droppedItem.clone();
							item.x = x + random.nextFloat() * 0.5f;
							item.y = aboveY + random.nextFloat() * 0.5f;
							item.dy = -0.07f;
							entityManager.add(item);
							if (logger != null)
								logger.debug("SharedWorld: " + blockAbove + " broke at (" + x + ", " + aboveY
										+ "), dropped item");
						}
					}

					// Broadcast the change
					broadcastBlockChange(x, aboveY, null);

					// Recursively check if there's another unstable block above this one
					breakFloatingPlants(x, aboveY);
				}
			}
		}
	}

	/**
	 * Check if a support block is valid for a given unstable block
	 */
	private boolean isValidSupportFor(Constants.TileID unstableBlock, Constants.TileID support) {
		// Cactus can only be on sand or another cactus
		if (unstableBlock == Constants.TileID.CACTUS) {
			return support == Constants.TileID.SAND || support == Constants.TileID.CACTUS;
		}

		// Wheat crops can only be on farmland
		if (unstableBlock == Constants.TileID.WHEAT_SEEDS || unstableBlock == Constants.TileID.WHEAT) {
			return support == Constants.TileID.FARMLAND || support == Constants.TileID.IRRIGATED_FARMLAND;
		}

		// Other unstable blocks (flowers, saplings, tall grass, torches) need dirt or
		// grass
		return support == Constants.TileID.DIRT || support == Constants.TileID.GRASS;
	}

	/**
	 * Handle a block change from a player
	 */
	public void handleBlockChange(PlayerConnection playerConnection, PacketBlockChange packet) {
		Player player = playerConnection.getPlayer();

		if (player == null) {
			return;
		}

		// SAFETY CHECKS (Sword & UI)
		mc.sayda.mcraze.item.Item held = player.inventory.selectedItem().getItem();

		// 1. UI Check - Cannot build/break while inventory is open
		if (player.inventory.isVisible()) {
			// Re-sync client to prevent ghost blocks
			Tile tile = worldAccess.getTile(packet.x, packet.y);
			char tileId = (char) (tile != null && tile.type != null ? tile.type.name.ordinal() : 0);
			playerConnection.getConnection().sendPacket(new PacketBlockChange(packet.x, packet.y, tileId, false));
			return;
		}

		// 2. Sword Check - Swords cannot break blocks (anti-grief / combat focus)
		if (packet.isBreak && held instanceof mc.sayda.mcraze.item.Tool) {
			mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) held;
			if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Sword) {
				// Correction packet
				playerConnection.getConnection().sendPacket(new PacketBreakingProgress(packet.x, packet.y, 0, 0));
				return;
			}
		}

		// CONSUMPTION SYSTEM (Right-click with food/potions)
		if (!packet.isBreak && held instanceof mc.sayda.mcraze.item.Consumable) {
			mc.sayda.mcraze.item.Consumable consumable = (mc.sayda.mcraze.item.Consumable) held;

			// Cooldown Check
			long currentTime = System.currentTimeMillis();
			Long lastEat = interactCooldowns.get(player.username + "_eat");
			int eatCooldown = 500;
			if (player.unlockedPassives
					.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.FAST_METABOLISM)) {
				eatCooldown = 250;
			}

			if (lastEat != null && currentTime - lastEat < eatCooldown) {
				return;
			}

			// Don't eat if full HP, unless it's a buff potion
			if (player.hitPoints < player.getMaxHP() || consumable.buffType != null) {
				interactCooldowns.put(player.username + "_eat", currentTime);

				// 1. Calculate Effects
				int healAmount = consumable.healingAmount;

				// Special cases for food items (RNG)
				if (held.itemId.equals("rotten_flesh")) {
					double roll = this.random != null ? this.random.nextDouble() : Math.random();
					if (roll < 0.5) {
						healAmount = 5;
					} else if (roll < 0.75) {
						healAmount = -5;
					} else {
						healAmount = 0;
					}
				}

				// Apply Healing/Damage
				if (healAmount > 0) {
					// Druid Core Bonus: +20% Food Healing
					if (player.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.DRUID) {
						healAmount = (int) (healAmount * 1.2f);
					}
					player.heal(healAmount);
				} else if (healAmount < 0) {
					player.takeDamage(-healAmount, mc.sayda.mcraze.entity.DamageType.MAGICAL);
				}

				// Apply Buffs
				if (consumable.buffType != null) {
					int duration = consumable.buffDuration;
					if (player.unlockedPassives
							.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.ALCHEMIST_BLESSING)) {
						duration *= 2;
					}
					player.addBuff(new mc.sayda.mcraze.entity.buff.Buff(consumable.buffType, duration,
							consumable.buffAmplifier));
				}

				// Well Fed Buff from cooked food
				if (player.unlockedPassives
						.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.COMFORT_FOOD)) {
					if (held.itemId.contains("cooked") || held.itemId.equals("golden_apple")
							|| held.itemId.equals("bread")) {
						player.addBuff(new mc.sayda.mcraze.entity.buff.Buff(
								mc.sayda.mcraze.entity.buff.BuffType.WELL_FED, 6000, 0));
					}
				}

				// 2. Feedback
				broadcastPacket(new mc.sayda.mcraze.network.packet.PacketPlaySound("assets/sounds/item/drink.wav",
						player.x, player.y));

				if (held.itemId.equals("bad_apple")) {
					playerConnection.getConnection()
							.sendPacket(new mc.sayda.mcraze.network.packet.PacketItemTrigger("bad_apple"));
				}

				String msg;
				mc.sayda.mcraze.graphics.Color color;
				if (healAmount > 0) {
					msg = "Yummers! Healed " + healAmount + " HP.";
					color = new mc.sayda.mcraze.graphics.Color(100, 255, 100);
				} else if (healAmount < 0) {
					msg = "Yikers! That tasted bad. Took " + (-healAmount) + " damage.";
					color = new mc.sayda.mcraze.graphics.Color(255, 100, 100);
				} else if (consumable.buffType != null) {
					String buffName = consumable.buffType.toString().toLowerCase().replace("_", " ");
					msg = "Gulp! Applied " + buffName + " effect.";
					color = new mc.sayda.mcraze.graphics.Color(180, 180, 255);
				} else {
					msg = "You feel nothing.";
					color = new mc.sayda.mcraze.graphics.Color(200, 200, 200);
				}
				playerConnection.getConnection().sendPacket(new PacketChatMessage(msg, color));

				// 3. Consume
				player.inventory.selectedItem().remove(1);

				// Return remains (e.g. empty bottle)
				if (consumable.remainsItemId != null) {
					mc.sayda.mcraze.item.Item remains = mc.sayda.mcraze.Constants.itemTypes
							.get(consumable.remainsItemId);
					if (remains != null) {
						player.inventory.addItem(remains.clone(), 1);
					}
				}

				broadcastInventoryUpdates();

				if (logger != null) {
					logger.info("SharedWorld: Player " + player.username + " consumed " + consumable.name);
				}
				return;
			}
		}

		// Dead players cannot break or place blocks
		if (player.dead) {
			return;
		}

		// Get per-player breaking state
		BreakingState breakingState = getBreakingState(player);

		if (packet.isBreak) {
			// CRITICAL FIX: Explicit STOP BREAKING signal
			// When client releases mouse, it sends x=-1 to cancels breaking immediately
			if (packet.x == -1) {
				breakingState.reset();
				playerConnection.getConnection().sendPacket(new PacketBreakingProgress(0, 0, 0, 0));
				return;
			}

			// BACKDROP MODE: Break backdrop instead of foreground
			if (player.backdropPlacementMode) {
				if (world.hasBackdrop(packet.x, packet.y)) {
					Constants.TileID backdropId = world.removeBackdrop(packet.x, packet.y);

					// Drop item if backdrop has drop
					if (backdropId != null && backdropId.itemDropId != null) {
						Item droppedItem = Constants.itemTypes.get(backdropId.itemDropId);
						if (droppedItem != null) {
							Item item = droppedItem.clone();
							item.x = packet.x + random.nextFloat() * 0.5f;
							item.y = packet.y + random.nextFloat() * 0.5f;
							item.dy = -0.07f;
							entityManager.add(item);
						}
					}

					// Broadcast backdrop removal to all clients
					broadcastBackdropChange(packet.x, packet.y, null);
				}
				return; // Don't break foreground
			}

			// FOREGROUND MODE: Normal block breaking (existing code)
			// Check if target is breakable (still safe - reads are thread-safe)
			if (!world.isBreakable(packet.x, packet.y)) {
				breakingState.reset();
				return;
			}

			// FLAG SYSTEM: Breaking Logic
			if (world.getTile(packet.x, packet.y).type.name == Constants.TileID.FLAG) {
				// Prevent moving flag at Night
				if (world.isNight()) {
					playerConnection.getConnection().sendPacket(new PacketChatMessage(
							"The Flag is locked in place by the darkness!",
							new mc.sayda.mcraze.graphics.Color(255, 50, 50)));
					breakingState.reset();
					playerConnection.getConnection().sendPacket(new PacketBreakingProgress(packet.x, packet.y, 0, 0));
					return;
				}
			}

			// Track breaking progress
			breakingState.lastUpdateTick = ticksRunning;
			if (breakingState.isBreaking(packet.x, packet.y)) {
				breakingState.ticks++;
			} else {
				// Started breaking a different block
				breakingState.ticks = 1;
				breakingState.x = packet.x;
				breakingState.y = packet.y;
			}

			// Get current tool to determine break time
			Item currentItem = player.inventory.selectedItem().getItem();
			int ticksNeeded = world.breakTicks(packet.x, packet.y, currentItem);

			// Master Miner & Lumber Expert: Class Speed Bonus
			if (currentItem instanceof mc.sayda.mcraze.item.Tool) {
				mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) currentItem;
				mc.sayda.mcraze.player.specialization.ClassStats.GatheringType type = null;

				if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Pick) {
					type = mc.sayda.mcraze.player.specialization.ClassStats.GatheringType.MINING;
				} else if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Axe) {
					type = mc.sayda.mcraze.player.specialization.ClassStats.GatheringType.WOOD;
				}

				if (type != null && player.classStats != null) {
					float multiplier = player.classStats.getGatheringSpeedMultiplier(type);
					if (multiplier > 1.0f) {
						// Apply multiplier (Speed increases -> Ticks decrease)
						ticksNeeded = (int) (ticksNeeded / multiplier);
						if (ticksNeeded < 1)
							ticksNeeded = 1;
					}
				}
			}

			// Debug mode: instant break (1 tick)
			if (player.debugMode) {
				ticksNeeded = 1;
			}

			// Send breaking progress to this player (ISSUE #3 fix)
			playerConnection.getConnection().sendPacket(new PacketBreakingProgress(packet.x, packet.y,
					breakingState.ticks, ticksNeeded));

			// Only break when enough ticks have passed
			if (breakingState.ticks < ticksNeeded) {
				return; // Not ready to break yet
			}

			// Block is broken - reset counter and notify client
			// Block is broken - finalize
			// FLAG SYSTEM: Update state when broken
			if (world.getTile(packet.x, packet.y).type.name == Constants.TileID.FLAG) {
				world.flagLocation = null;
				logger.info("SharedWorld: Flag broken by player " + player.username);
			}

			finishBreakingBlock(player, packet.x, packet.y);

		} else {
			// CRITICAL FIX: Explicitly stop breaking when isBreak=false (or on release)
			breakingState.reset();

			// BACKDROP MODE: Place backdrop instead of foreground
			// MOVED TO TOP to prevent interacting with foreground blocks when trying to
			// place backdrop
			if (player.backdropPlacementMode) {
				// FIXED: Allow backdrop placement anywhere (no restrictions - backdrops are
				// visual only)

				// Place backdrop
				// Validate tileId first
				Constants.TileID tileId = null;
				if (packet.newTileId >= 0 && packet.newTileId < Constants.TileID.values().length) {
					tileId = Constants.TileID.values()[packet.newTileId];
				}

				if (tileId != null) {
					if (world.setBackdrop(packet.x, packet.y, tileId)) {
						player.inventory.decreaseSelected(1);
						broadcastInventoryUpdates();
						broadcastBackdropChange(packet.x, packet.y, tileId);
					}
				}
				return; // Don't place foreground or interact
			}

			// Check if clicking on a crafting table FIRST (before parsing packet data)
			if (world.isCraft(packet.x, packet.y)) {
				// INTERACTION COOLDOWN CHECK
				long currentTime = System.currentTimeMillis();
				Long lastInteract = interactCooldowns.get(player.username);
				if (lastInteract != null && currentTime - lastInteract < INTERACT_COOLDOWN_MS) {
					return;
				}
				interactCooldowns.put(player.username, currentTime);

				// Open inventory with 3x3 crafting
				player.inventory.tableSizeAvailable = 3;
				player.inventory.setVisible(true);
				broadcastInventoryUpdates(); // Immediately sync inventory state
				return;
			}

			// Check if clicking on a chest block
			Tile clickedTile = worldAccess.getTile(packet.x, packet.y);
			if (clickedTile != null && clickedTile.type != null) {
				// Handled in handleInteract / handleTileInteraction via PacketInteract
				// Legacy block change handling removed
			}

			// Block placing - validate and apply
			Constants.TileID tileId = null;
			if (packet.newTileId >= 0 && packet.newTileId < Constants.TileID.values().length) {
				tileId = Constants.TileID.values()[packet.newTileId];
			}

			// FARMING: Special handling for hoes and seeds (processed BEFORE block
			// validation)
			// Get the item in the player's hand
			Item selectedItem = player.inventory.selectedItem().getItem();
			if (selectedItem != null && selectedItem.itemId != null) {
				// HOE USAGE: Convert dirt/grass to farmland
				if (selectedItem.itemId.endsWith("_hoe")) {
					Tile targetTile = worldAccess.getTile(packet.x, packet.y);
					if (targetTile != null && targetTile.type != null) {
						Constants.TileID targetType = targetTile.type.name;
						if (targetType == Constants.TileID.DIRT || targetType == Constants.TileID.GRASS
								|| targetType == Constants.TileID.FARMLAND) {
							// Convert to farmland
							// DRUID: Irrigated Soil Passive
							Constants.TileID farmlandType = Constants.TileID.FARMLAND;

							if (player.unlockedPassives
									.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.IRRIGATED_SOIL)) {
								farmlandType = Constants.TileID.IRRIGATED_FARMLAND;
							}

							// Ensure we don't convert to the same type (prevents infinite durability loss
							// on same block)
							if (targetType != farmlandType) {
								world.addTile(packet.x, packet.y, farmlandType);
								broadcastBlockChange(packet.x, packet.y, farmlandType);
							}

							// Handle hoe durability - each farmland created costs 1 durability
							if (selectedItem instanceof Tool) {
								Tool hoe = (Tool) selectedItem;
								hoe.uses++;
								if (hoe.uses >= hoe.totalUses) {
									// Hoe is broken - remove from inventory
									player.inventory.selectedItem().setEmpty();
									logger.debug(
											"Player " + player.username + "'s hoe broke after " + hoe.uses + " uses");
								}
								// Broadcast inventory update to sync tool durability
								broadcastInventoryUpdates();
							}

							return; // Don't proceed with normal placement
						}
					}
				}
				// SEED PLANTING: Place wheat on farmland
				else if (selectedItem.itemId.equals("wheat_seeds")) {
					// Check if block below is farmland
					int belowY = packet.y + 1; // Y increases downward
					if (belowY < world.height) {
						Tile belowTile = worldAccess.getTile(packet.x, belowY);
						if (belowTile != null && belowTile.type != null &&
								(belowTile.type.name == Constants.TileID.FARMLAND
										|| belowTile.type.name == Constants.TileID.IRRIGATED_FARMLAND)) {
							// Can only plant if position is empty
							if (!world.isBreakable(packet.x, packet.y)) {
								// Place wheat crop
								if (world.addTile(packet.x, packet.y, Constants.TileID.WHEAT_SEEDS)) {
									player.inventory.decreaseSelected(1);
									broadcastInventoryUpdates();
									broadcastBlockChange(packet.x, packet.y, Constants.TileID.WHEAT_SEEDS);
								}
							}
							return; // Don't proceed with normal placement
						}
						// If not on farmland, seeds will deny placement
						return;
					}
				}

			}

			// POTION/FOOD CONSUMPTION
			if (selectedItem instanceof mc.sayda.mcraze.item.Consumable) {
				mc.sayda.mcraze.item.Consumable consumable = (mc.sayda.mcraze.item.Consumable) selectedItem;

				// Apply Healing
				if (consumable.healingAmount > 0) {
					player.heal(consumable.healingAmount);
				}

				// Apply Buffs
				if (consumable.buffType != null) {
					int duration = consumable.buffDuration;
					// ARCANIST: Alchemist's Blessing (Path 3)
					if (player.unlockedPassives
							.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.ALCHEMIST_BLESSING)) {
						duration *= 2; // +100% Duration
					}

					player.addBuff(
							new mc.sayda.mcraze.entity.buff.Buff(consumable.buffType, duration,
									consumable.buffAmplifier));
				}

				// Feedback sound/effect
				broadcastSound("assets/sounds/item/drink.wav", player.x, player.y, 1.0f, 16.0f);

				// [NEW] Chat feedback prompt
				String msg = "";
				mc.sayda.mcraze.graphics.Color color = new mc.sayda.mcraze.graphics.Color(180, 180, 255); // Light blue
																											// for
																											// potions
				if (consumable.buffType != null) {
					String buffName = consumable.buffType.toString().toLowerCase().replace("_", " ");
					msg = "Gulp! Applied " + buffName + " effect.";
				} else if (consumable.healingAmount > 0) {
					msg = "Yummers! Healed " + consumable.healingAmount + " HP.";
					color = new mc.sayda.mcraze.graphics.Color(100, 255, 100); // Green for healing
				} else {
					msg = "You feel nothing.";
					color = new mc.sayda.mcraze.graphics.Color(200, 200, 200);
				}

				if (playerConnection != null) {
					playerConnection.getConnection()
							.sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(msg, color));
				}

				// Consume item
				player.inventory.decreaseSelected(1);
				broadcastInventoryUpdates();

				if (logger != null) {
					logger.info("SharedWorld: Player " + player.username + " consumed " + consumable.name);
				}
				return; // Handled click
			}

			// BUCKET LOGIC: Handling fluids
			if (selectedItem != null && selectedItem.itemId != null) {
				// Placing fluids
				boolean isWaterBucket = selectedItem.itemId.equals("water_bucket");
				boolean isLavaBucket = selectedItem.itemId.equals("lava_bucket");

				if (isWaterBucket || isLavaBucket) {
					// Check if target position is valid (only empty or fluid)
					// Cannot replace solid blocks
					mc.sayda.mcraze.world.tile.Tile target = worldAccess.getTile(packet.x, packet.y);
					boolean validTarget = false;
					if (target != null && target.type != null) {
						validTarget = target.type.name == Constants.TileID.AIR || target.type.liquid;
					}

					if (validTarget) {
						Constants.TileID fluidInfo = isWaterBucket ? Constants.TileID.WATER : Constants.TileID.LAVA;

						// Place fluid
						if (world.addTile(packet.x, packet.y, fluidInfo)) {
							// Consumed bucket -> Remains item (e.g. empty bucket)
							if (selectedItem.remainsItemId != null) {
								mc.sayda.mcraze.item.Item remains = Constants.itemTypes.get(selectedItem.remainsItemId);
								if (remains != null) {
									player.inventory.selectedItem().setItem(remains.clone());
									player.inventory.selectedItem().setCount(1);
								} else {
									player.inventory.decreaseSelected(1);
								}
							} else {
								player.inventory.decreaseSelected(1);
							}

							broadcastInventoryUpdates();
							broadcastBlockChange(packet.x, packet.y, fluidInfo);
						}
					}
					// If we placed it or not, we handled the click
					return;
				}

				// Picking up fluids
				if (selectedItem.itemId.equals("bucket")) {
					// Check target block
					Tile targetTile = worldAccess.getTile(packet.x, packet.y);
					if (targetTile != null && targetTile.type != null) {
						Constants.TileID targetId = targetTile.type.name;

						if (targetId == Constants.TileID.WATER || targetId == Constants.TileID.LAVA) {
							// LIQUID PHYSICS: Only pick up SOURCE blocks (metadata = 0)
							if (targetTile.metadata != 0) {
								// Flowing liquid - cannot be picked up with bucket
								return;
							}

							// Determine result item
							String resultId = (targetId == Constants.TileID.WATER) ? "water_bucket" : "lava_bucket";
							mc.sayda.mcraze.item.Item resultItem = Constants.itemTypes.get(resultId);

							if (resultItem != null) {
								// Remove fluid
								world.removeTile(packet.x, packet.y);
								broadcastBlockChange(packet.x, packet.y, null);

								// Fill bucket
								player.inventory.selectedItem().setItem(resultItem);
								player.inventory.selectedItem().setCount(1);
								broadcastInventoryUpdates();

								return;
							}
						}
					}
				}

				// BOTTLE LOGIC: Filling empty bottles with water
				if (selectedItem.itemId.equals("bottle")) {
					mc.sayda.mcraze.world.tile.Tile target = worldAccess.getTile(packet.x, packet.y);
					if (target != null && target.type != null && target.type.name == Constants.TileID.WATER) {
						mc.sayda.mcraze.item.Item waterBottle = Constants.itemTypes.get("water_bottle");
						if (waterBottle != null) {
							// Update selected item to water bottle (consume 1 empty bottle)
							if (player.inventory.selectedItem().getCount() > 1) {
								player.inventory.selectedItem()
										.setCount(player.inventory.selectedItem().getCount() - 1);
								// Try to add water bottle to inventory, or drop it if full
								if (player.inventory.addItem(waterBottle, 1) > 0) {
									dropItem("water_bottle", player.x, player.y, 1);
								}
							} else {
								player.inventory.selectedItem().setItem(waterBottle);
								player.inventory.selectedItem().setCount(1);
							}

							broadcastInventoryUpdates();
							// Sound: Water fill
							broadcastPacket(new mc.sayda.mcraze.network.packet.PacketPlaySound(
									"assets/sounds/item/bucket_fill.wav", packet.x, packet.y));
							return;
						}
					}
				}
			}

			// Block placing validation - check if tileId is valid for normal placement
			if (tileId == null || tileId == Constants.TileID.AIR || tileId == Constants.TileID.NONE) {
				// Correction: send current block state to client (likely AIR)
				Tile current = worldAccess.getTile(packet.x, packet.y);
				int currentId = (current != null && current.type != null) ? current.type.name.ordinal() : 0;
				playerConnection.getConnection()
						.sendPacket(new PacketBlockChange(packet.x, packet.y, (char) currentId, false));
				return; // Cannot place air or invalid tiles
			}

			// FOREGROUND MODE: Normal block placement (existing code)
			// Can only place if the target position is empty (not a solid block)
			if (world.isBreakable(packet.x, packet.y)) {
				// Correction: send current block state to client (which is the block causing
				// collision)
				Tile current = worldAccess.getTile(packet.x, packet.y);
				int currentId = (current != null && current.type != null) ? current.type.name.ordinal() : 0;
				playerConnection.getConnection()
						.sendPacket(new PacketBlockChange(packet.x, packet.y, (char) currentId, false));
				return; // Cannot place - there's already a block here
			}

			// Check if there's at least one adjacent block (up, down, left, right)
			boolean hasAdjacentBlock = world.isBreakable(packet.x - 1, packet.y) || // left
					world.isBreakable(packet.x + 1, packet.y) || // right
					world.isBreakable(packet.x, packet.y - 1) || // up
					world.isBreakable(packet.x, packet.y + 1); // down

			if (!hasAdjacentBlock) {
				// Correction: send current block state (likely AIR)
				Tile current = worldAccess.getTile(packet.x, packet.y);
				int currentId = (current != null && current.type != null) ? current.type.name.ordinal() : 0;
				playerConnection.getConnection()
						.sendPacket(new PacketBlockChange(packet.x, packet.y, (char) currentId, false));
				return; // Cannot place - no adjacent block
			}

			// Check if the block would collide with the player
			boolean isPassable = Constants.tileTypes.get(tileId).type.passable;
			if (!isPassable && player.inBoundingBox(new mc.sayda.mcraze.util.Int2(packet.x, packet.y), tileSize)) {
				// Correction: send current block state (likely AIR)
				Tile current = worldAccess.getTile(packet.x, packet.y);
				int currentId = (current != null && current.type != null) ? current.type.name.ordinal() : 0;
				playerConnection.getConnection()
						.sendPacket(new PacketBlockChange(packet.x, packet.y, (char) currentId, false));
				return; // Cannot place - would collide with player
			}

			// TRAP RESTRICTION
			boolean isTrap = (tileId == Constants.TileID.SPIKE_TRAP || tileId == Constants.TileID.BOULDER_TRAP);
			if (isTrap) {
				boolean hasTrapSkill = player.unlockedPassives
						.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.TRAP_MECHANISMS);

				if (!hasTrapSkill) {
					playerConnection.getConnection().sendPacket(new PacketChatMessage(
							"Only Trap Masters can place traps!",
							new mc.sayda.mcraze.graphics.Color(255, 100, 100)));

					// Revert ghost block
					Tile current = worldAccess.getTile(packet.x, packet.y);
					int currentId = (current != null && current.type != null) ? current.type.name.ordinal() : 0;
					playerConnection.getConnection()
							.sendPacket(new PacketBlockChange(packet.x, packet.y, (char) currentId, false));
					return;
				}
			}

			// Place the block
			if (world.addTile(packet.x, packet.y, tileId)) {
				// FLAG SYSTEM: Valid placement
				if (tileId == Constants.TileID.FLAG) {
					world.flagLocation = new mc.sayda.mcraze.util.Int2(packet.x, packet.y);
					logger.info("SharedWorld: Flag placed at " + packet.x + "," + packet.y);
				}

				// ENGINEER: Trap Ownership (Tile ownerUUID)
				if (tileId == Constants.TileID.SPIKE_TRAP || tileId == Constants.TileID.BOULDER_TRAP) {
					Tile tile = world.getTile(packet.x, packet.y);
					if (tile != null) {
						tile.ownerUUID = player.getUUID();
						if (logger != null) {
							logger.debug("SharedWorld: Trap (" + tileId + ") placed by " + player.username + " at ("
									+ packet.x + "," + packet.y + ")");
						}
					}
				}

				broadcastBlockChange(packet.x, packet.y, tileId);

				// Trapdoor placement
				if (tileId == Constants.TileID.TRAPDOOR) {
					// Just place it, no multi-block structure needed
				}

				// Successfully placed - decrease inventory
				boolean consume = true;
				if (player.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.ENGINEER &&
						player.unlockedPassives
								.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.EFFICIENT_BUILDER)) {
					// Check if tile is wood-based
					if (tileId == Constants.TileID.WOOD || tileId == Constants.TileID.PLANK ||
							tileId == Constants.TileID.DOOR_BOT_CLOSED || tileId == Constants.TileID.TRAPDOOR
							|| tileId == Constants.TileID.TRAPDOOR_CLOSED ||
							tileId == Constants.TileID.BED_LEFT || tileId == Constants.TileID.LADDER) {
						if (random.nextDouble() < 0.25) {
							consume = false;
							if (logger != null) {
								logger.info("Efficient Builder triggered! Wood block not consumed.");
							}
						}
					}
				}

				if (consume) {
					player.inventory.decreaseSelected(1);
				}
				// Immediately sync inventory after placing block (ISSUE #10 fix)
				broadcastInventoryUpdates();

				// Broadcast change to all players
				broadcastBlockChange(packet.x, packet.y, tileId);

				// DOOR SYSTEM: Auto-place door top when placing door bottom
				if (tileId == Constants.TileID.DOOR_BOT_CLOSED) {
					int topY = packet.y - 1;
					if (topY >= 0 && !world.isBreakable(packet.x, topY)) {
						world.addTile(packet.x, topY, Constants.TileID.DOOR_TOP_CLOSED);
						broadcastBlockChange(packet.x, topY, Constants.TileID.DOOR_TOP_CLOSED);
					} else {
						// Failed to place top -> Remove bottom
						world.removeTile(packet.x, packet.y);
						broadcastBlockChange(packet.x, packet.y, null);
						// Refund item
						player.inventory.selectedItem().setCount(player.inventory.selectedItem().getCount() + 1);
						broadcastInventoryUpdates();
					}
				}

				// BED SYSTEM: Auto-place bed right when placing bed left
				if (tileId == Constants.TileID.BED_LEFT) {
					int rightX = packet.x + 1;
					if (rightX < world.width && !world.isBreakable(rightX, packet.y)) {
						world.addTile(rightX, packet.y, Constants.TileID.BED_RIGHT);
						broadcastBlockChange(rightX, packet.y, Constants.TileID.BED_RIGHT);
					} else {
						// Fail
						world.removeTile(packet.x, packet.y);
						broadcastBlockChange(packet.x, packet.y, null);
						// Refund
						player.inventory.selectedItem().setCount(player.inventory.selectedItem().getCount() + 1);
						broadcastInventoryUpdates();
					}
				}
			}

		}
	}

	/**
	 * Handle inventory action from client (slot clicks, crafting)
	 * Server-authoritative inventory manipulation
	 */
	public void handleInventoryAction(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketInventoryAction packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot interact with inventory
		if (player.dead) {
			return;
		}

		// The player's inventory will handle the action server-side
		mc.sayda.mcraze.player.Inventory inv = player.inventory;

		// DIRECT slot manipulation - packet coords map directly to array indices!
		// The client's mouseToCoor -1 offset compensates for panel padding, not array
		// indexing
		// So: packet(x,y) -> inventoryItems[x][y] directly

		int slotX = packet.slotX;
		int slotY = packet.slotY; // Direct mapping - no offset needed!

		// DEBUG: Log the coordinate mapping
		logger.info("InventoryAction: packet(" + packet.slotX + "," + packet.slotY +
				") -> array[" + slotX + "][" + slotY + "] | Array size: [" +
				inv.inventoryItems.length + "][" + inv.inventoryItems[0].length +
				"] | craftingHeight=" + inv.craftingHeight + " tableSizeAvailable=" + inv.tableSizeAvailable);

		// Handle Item Dropping (Virtual X = -2)
		if (slotX == -2) {
			mc.sayda.mcraze.item.InventoryItem holding = inv.holding;
			if (!holding.isEmpty()) {
				int count = holding.getCount();
				mc.sayda.mcraze.item.Item itemDef = holding.getItem();
				holding.setEmpty();

				for (int i = 0; i < count; i++) {
					mc.sayda.mcraze.item.Item tossed = itemDef.clone();
					if (player.facingRight) {
						tossed.x = player.x + 1 + random.nextFloat();
					} else {
						tossed.x = player.x - 1 - random.nextFloat();
					}
					tossed.y = player.y;
					tossed.dy = -.1f;
					addEntity(tossed);
				}
				broadcastInventoryUpdates();
			}
			return;
		}

		// Handle Equipment Slots (Virtual X = -1)
		if (slotX == -1) {
			if (slotY >= 0 && slotY < inv.equipment.length) {
				handleEquipmentInteraction(playerConnection, inv, slotY, !packet.leftClick);
				return;
			}
		}

		// Validate array bounds
		if (slotX < 0 || slotX >= inv.inventoryItems.length ||
				slotY < 0 || slotY >= inv.inventoryItems[0].length) {
			logger.warn("Invalid inventory slot: [" + slotX + "][" + slotY + "]");
			return;
		}

		// Handle craft output click with server-side crafting logic
		// (resolution-independent)
		if (packet.craftClick) {
			logger.info("Processing craft request for player " + playerConnection.getPlayerName());

			// Step 1: Compute current craft table from inventory
			String[][] currentTable;
			if (inv.tableSizeAvailable == 2) {
				currentTable = new String[2][2];
			} else {
				currentTable = new String[3][3];
			}

			for (int i = 0; i < inv.tableSizeAvailable; i++) {
				for (int j = 0; j < inv.tableSizeAvailable; j++) {
					mc.sayda.mcraze.item.Item item = inv.inventoryItems[i + inv.inventoryItems.length
							- inv.tableSizeAvailable][j].item;
					if (item != null) {
						currentTable[j][i] = item.itemId;
					} else {
						currentTable[j][i] = null;
					}
				}
			}

			// Step 2: Find matching recipe
			mc.sayda.mcraze.item.Item craftedItem = null;
			int craftedCount = 0;

			for (mc.sayda.mcraze.item.Item entry : mc.sayda.mcraze.Constants.itemTypes.values()) {
				if (entry.template != null && entry.template.compare(currentTable)) {
					craftedItem = entry;
					craftedCount = entry.template.outCount;
					logger.info("  Matched recipe: " + entry.itemId + " x" + craftedCount);
					break;
				}
			}

			if (craftedItem == null) {
				logger.info("  No matching recipe found");
				// Broadcast inventory anyway to sync state
				broadcastInventoryUpdates();
				return;
			}

			// [NEW] Class Restriction Check
			if (craftedItem.requiredClass != null) {
				if (player.selectedClass != craftedItem.requiredClass) {
					logger.info(
							"  Cannot craft " + craftedItem.itemId + " - requires class " + craftedItem.requiredClass);
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Restricted! You must be a " + craftedItem.requiredClass.getDisplayName()
									+ " to craft this.",
							new mc.sayda.mcraze.graphics.Color(255, 50, 50) // Red
					));
					broadcastInventoryUpdates();
					return;
				}
			}

			// [NEW] Path/Subclass Restriction Check
			if (craftedItem.requiredPath != null) {
				boolean hasPath = false;
				if (player.selectedPaths != null) {
					for (mc.sayda.mcraze.player.specialization.SpecializationPath path : player.selectedPaths) {
						if (path == craftedItem.requiredPath) {
							hasPath = true;
							break;
						}
					}
				}

				if (!hasPath) {
					logger.info(
							"  Cannot craft " + craftedItem.itemId + " - requires path " + craftedItem.requiredPath);
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Restricted! You must follow the " + craftedItem.requiredPath.getDisplayName()
									+ " path to craft this.",
							new mc.sayda.mcraze.graphics.Color(255, 50, 50) // Red
					));
					broadcastInventoryUpdates();
					return;
				}
			}

			// Step 3: Check if holding is compatible
			// Step 3: Check if holding is compatible (Only relevant if NOT shift-clicking)
			// For shift-click, we move directly to inventory
			if (!packet.shiftClick) {
				if (!inv.holding.isEmpty()) {
					boolean isTool = craftedItem.getClass() == mc.sayda.mcraze.item.Tool.class ||
							inv.holding.item.getClass() == mc.sayda.mcraze.item.Tool.class;
					boolean differentItem = !craftedItem.itemId.equals(inv.holding.item.itemId);
					if (isTool || differentItem) {
						logger.info("  Cannot craft - holding incompatible item");
						broadcastInventoryUpdates();
						return;
					}

					int maxStack = isTool ? 1 : 64;
					if (inv.holding.count + craftedCount > maxStack) {
						logger.info("  Cannot craft - would exceed stack limit (" +
								inv.holding.count + " + " + craftedCount + " > " + maxStack + ")");
						broadcastInventoryUpdates();
						return;
					}
				}
			}

			// CRAFTING LOOP (Runs once for normal click, multiple for shift-click)
			int maxCrafts = 1;
			if (packet.shiftClick) {
				// Calculate maximum possible crafts based on input materials
				int minStack = 999;
				boolean emptyGrid = true;
				for (int i = 0; i < inv.tableSizeAvailable; i++) {
					for (int j = 0; j < inv.tableSizeAvailable; j++) {
						int gridX = i + inv.inventoryItems.length - inv.tableSizeAvailable;
						int gridY = j;
						if (inv.inventoryItems[gridX][gridY].item != null) {
							emptyGrid = false;
							if (inv.inventoryItems[gridX][gridY].count < minStack) {
								minStack = inv.inventoryItems[gridX][gridY].count;
							}
						}
					}
				}
				if (emptyGrid)
					minStack = 0;
				maxCrafts = minStack;
				logger.info("  Shift-crafting max: " + maxCrafts);
			}

			int actualCrafted = 0;
			for (int k = 0; k < maxCrafts; k++) {
				// 1. Create the item
				mc.sayda.mcraze.item.Item finalItem = (mc.sayda.mcraze.item.Item) craftedItem.clone();

				// Apply Class Durability Bonus
				if (finalItem instanceof mc.sayda.mcraze.item.Tool) {
					float durabilityMult = player.classStats.getDurabilityMultiplier();
					if (durabilityMult > 1.0f) {
						mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) finalItem;
						tool.totalUses = (int) (tool.totalUses * durabilityMult);
						tool.isMastercrafted = true;
					}
				}

				// 2. Add to destination
				if (packet.shiftClick) {
					// Add directly to main inventory/hotbar
					int leftover = inv.addItem(finalItem, craftedCount);
					if (leftover > 0) {
						// Inventory full, stop crafting
						// Since we haven't consumed inputs yet for this iteration, just break
						break;
					}
				} else {
					// Add to holding cursor
					inv.holding.add(finalItem, craftedCount);
				}

				// 3. Consume inputs (only if successful)
				for (int i = 0; i < inv.tableSizeAvailable; i++) {
					for (int j = 0; j < inv.tableSizeAvailable; j++) {
						int gridX = i + inv.inventoryItems.length - inv.tableSizeAvailable;
						int gridY = j;
						if (inv.inventoryItems[gridX][gridY].count > 0) {
							// ENGINEER: Resourceful Engineering Core Bonus (10% chance to not consume)
							boolean consume = true;
							if (player.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.ENGINEER) {
								if (random.nextDouble() < 0.10) {
									consume = false;
								}
							}

							if (consume) {
								inv.inventoryItems[gridX][gridY].count -= 1;
								if (inv.inventoryItems[gridX][gridY].count <= 0) {
									inv.inventoryItems[gridX][gridY].item = null;
									inv.inventoryItems[gridX][gridY].count = 0;
								}
							}
						}
					}
				}

				actualCrafted++;
			}

			logger.info("  Crafted " + craftedItem.itemId + " x" + (actualCrafted * craftedCount));
		} else {
			// Check if this is a mapped equipment slot (Top-left 5x2 area)
			if (slotY < 2 && slotX < 5) {
				// Map to equipment slots 0-9
				int equipIdx = slotY * 5 + slotX;
				handleEquipmentInteraction(playerConnection, inv, equipIdx, !packet.leftClick);
			} else {
				// Use common slot interaction logic for consistency across all UIs
				handleInventorySlotInteraction(inv, slotX, slotY, !packet.leftClick, logger);
			}
		}

		// Calculate craftable preview (what CAN be crafted from current grid)
		updateCraftablePreview(inv);

		// Broadcast updated inventory to all clients
		broadcastInventoryUpdates();

	}

	/**
	 * Calculate and update craftable preview based on current crafting grid
	 * This shows players what they CAN craft without actually crafting it
	 */
	private void updateCraftablePreview(mc.sayda.mcraze.player.Inventory inv) {
		mc.sayda.mcraze.item.InventoryItem craftable = inv.getCraftable();

		// Clear craftable preview
		craftable.setEmpty();

		// Build current craft table from inventory
		String[][] currentTable;
		if (inv.tableSizeAvailable == 2) {
			currentTable = new String[2][2];
		} else {
			currentTable = new String[3][3];
		}

		for (int i = 0; i < inv.tableSizeAvailable; i++) {
			for (int j = 0; j < inv.tableSizeAvailable; j++) {
				mc.sayda.mcraze.item.Item item = inv.inventoryItems[i + inv.inventoryItems.length
						- inv.tableSizeAvailable][j].item;
				if (item != null) {
					currentTable[j][i] = item.itemId;
				} else {
					currentTable[j][i] = null;
				}
			}
		}

		// Find matching recipe
		for (mc.sayda.mcraze.item.Item entry : mc.sayda.mcraze.Constants.itemTypes.values()) {
			if (entry.template != null && entry.template.compare(currentTable)) {
				// Found a match - set craftable preview
				mc.sayda.mcraze.item.Item clonedItem = (mc.sayda.mcraze.item.Item) entry.clone();
				craftable.setItem(clonedItem);
				craftable.setCount(entry.template.outCount);
				break;
			}
		}
	}

	/**
	 * Handle block interactions (crafting tables, chests, etc.)
	 */
	public void handleInteract(PlayerConnection playerConnection, PacketInteract packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot interact
		if (player.dead) {
			return;
		}

		// BOW USAGE
		if (packet.type == PacketInteract.InteractionType.INTERACT) {
			mc.sayda.mcraze.item.InventoryItem holding = player.inventory.selectedItem();
			if (holding != null && !holding.isEmpty() && holding.getItem() instanceof mc.sayda.mcraze.item.Tool
					&& ((mc.sayda.mcraze.item.Tool) holding
							.getItem()).toolType == mc.sayda.mcraze.item.Tool.ToolType.Bow) {
				// Search for arrows: Diamond > Iron > Stone
				mc.sayda.mcraze.item.InventoryItem arrowSlot = player.inventory.findItem("arrow_diamond");

				if (arrowSlot == null) {
					arrowSlot = player.inventory.findItem("arrow_iron");
				}
				if (arrowSlot == null) {
					arrowSlot = player.inventory.findItem("arrow_stone");
				}

				if (arrowSlot != null) {
					// Consume Arrow
					// Moved to Player.tick() for charge mechanic
					/*
					 * arrowSlot.count--;
					 * if (arrowSlot.count <= 0)
					 * arrowSlot.setEmpty();
					 * broadcastInventoryUpdates();
					 * 
					 * // Spawn Arrow
					 * // Velocity: Towards clicked point (packet.blockX, packet.blockY) treated as
					 * // target
					 * float dx = packet.blockX - player.x;
					 * float dy = packet.blockY - player.y;
					 * float len = (float) Math.sqrt(dx * dx + dy * dy);
					 * if (len < 0.01f)
					 * len = 1f;
					 * 
					 * float speed = 1.0f;
					 * 
					 * mc.sayda.mcraze.entity.projectile.EntityArrow arrow = new
					 * mc.sayda.mcraze.entity.projectile.EntityArrow(
					 * player.x, player.y + 0.2f, player, arrowType);
					 * arrow.dx = (dx / len) * speed;
					 * arrow.dy = (dy / len) * speed;
					 * // Arrow needs to look right? Rotation is handled by client/entity tick.
					 * // Just set velocity.
					 * addEntity(arrow);
					 */

					// Sound: Arrow hit
					broadcastSound("assets/sounds/hit.wav");

					return; // Stop further processing
				}
			}
		}

		// CRITICAL FIX: Handle E key inventory toggle (doesn't require tile validation)
		if (packet.type == PacketInteract.InteractionType.TOGGLE_INVENTORY) {
			// If player has a furnace or chest open, just close it (stop tracking) without
			// toggling inventory
			if (playerConnection.getOpenedFurnaceX() != -1 || playerConnection.getOpenedChestX() != -1
					|| playerConnection.getOpenedAlchemyX() != -1) {
				playerConnection.setOpenedFurnace(-1, -1);
				playerConnection.setOpenedChest(-1, -1);
				playerConnection.setOpenedAlchemy(-1, -1);
				// Ensure inventory is closed on server side logic
				player.inventory.setVisible(false);
			} else {
				// Normal E press: Toggle inventory visibility
				player.inventory.setVisible(!player.inventory.isVisible());
			}

			// When closing inventory, reset to 2x2 crafting grid
			if (!player.inventory.isVisible()) {
				player.inventory.tableSizeAvailable = 2;
			}

			// Broadcast inventory update to sync visibility state to all clients
			broadcastInventoryUpdates();
			return;
		}

		// Get the tile being interacted with - use thread-safe WorldAccess
		if (packet.blockX < 0 || packet.blockY < 0 ||
				packet.blockX >= worldAccess.getWidth() || packet.blockY >= worldAccess.getHeight()) {
			return;
		}

		Tile tile = worldAccess.getTile(packet.blockX, packet.blockY);
		if (tile == null || tile.type == null) {
			return;
		}

		// Handle different interaction types
		// TRAP/INTERACT LOGIC
		// Re-fetch from main world for interaction logic (ensures current state)
		Tile interactTile = world.getTile(packet.blockX, packet.blockY);

		if (logger != null)
			logger.info("SharedWorld: handleInteract at " + packet.blockX + "," + packet.blockY + " Type: "
					+ (interactTile != null && interactTile.type != null ? interactTile.type.name : "null"));

		// Unified Interaction Handling
		// Routes all block-based interactions to handleTileInteraction
		if (packet.type == PacketInteract.InteractionType.INTERACT ||
				packet.type == PacketInteract.InteractionType.OPEN_CRAFTING ||
				packet.type == PacketInteract.InteractionType.OPEN_FURNACE ||
				packet.type == PacketInteract.InteractionType.OPEN_CHEST ||
				packet.type == PacketInteract.InteractionType.OPEN_ALCHEMY) {

			// Use world.getTile() instead of worldAccess to ensure current state
			Tile targetTile = world.getTile(packet.blockX, packet.blockY);
			if (targetTile != null) {
				handleTileInteraction(playerConnection, packet.blockX, packet.blockY, targetTile);
			}
			return;
		}
	}

	/**
	 * Handle chest inventory actions (server-authoritative)
	 * Implements basic item transfer between chest and player inventory
	 */
	public void handleChestAction(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketChestAction packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot interact with chests
		if (player.dead) {
			return;
		}

		// SECURITY: Validate player has this chest open
		// Prevent players from interacting with chests they don't have open
		if (playerConnection.getOpenedChestX() != packet.chestX ||
				playerConnection.getOpenedChestY() != packet.chestY) {
			if (logger != null) {
				logger.warn("Player " + player.username + " tried to interact with chest at (" +
						packet.chestX + "," + packet.chestY + ") but has chest at (" +
						playerConnection.getOpenedChestX() + "," + playerConnection.getOpenedChestY() + ") open");
			}
			return;
		}

		// Get the chest data
		mc.sayda.mcraze.world.storage.ChestData chestData = world.getOrCreateChest(packet.chestX, packet.chestY);
		mc.sayda.mcraze.player.Inventory inv = player.inventory;

		if (packet.isChestSlot) {
			// Clicked on chest slot - interact with chest
			if (packet.slotX >= 0 && packet.slotX < 10 && packet.slotY >= 0 && packet.slotY < 3) {
				mc.sayda.mcraze.item.InventoryItem chestSlot = chestData.getInventoryItem(packet.slotX, packet.slotY);

				if (inv.holding.isEmpty()) {
					// Picking up from chest slot
					if (!chestSlot.isEmpty()) {
						if (packet.isRightClick && chestSlot.getCount() > 1) {
							// Right click - pick up half
							int halfCount = (chestSlot.getCount() + 1) / 2;
							inv.holding.setItem(chestSlot.getItem().clone());
							inv.holding.setCount(halfCount);
							chestSlot.setCount(chestSlot.getCount() - halfCount);
						} else {
							// Left click or single item - pick up all
							inv.holding.setItem(chestSlot.getItem());
							inv.holding.setCount(chestSlot.getCount());
							chestSlot.setEmpty();
						}
					}
				} else {
					// Placing into chest slot
					if (chestSlot.isEmpty()) {
						// Empty slot - place items
						if (packet.isRightClick) {
							// Right click - place one
							chestSlot.setItem(inv.holding.getItem().clone());
							chestSlot.setCount(1);
							inv.holding.remove(1);
						} else {
							// Left click - place all
							chestSlot.setItem(inv.holding.getItem().clone());
							chestSlot.setCount(inv.holding.getCount());
							inv.holding.setEmpty();
						}
					} else if (chestSlot.getItem().itemId.equals(inv.holding.getItem().itemId)) {
						// Same item type - try to stack
						if (packet.isRightClick) {
							// Right click - add one
							int added = chestSlot.add(inv.holding.getItem(), 1);
							if (added == 0) {
								inv.holding.remove(1);
							}
						} else {
							// Left click - add all
							int leftover = chestSlot.add(inv.holding.getItem(), inv.holding.getCount());
							inv.holding.setCount(leftover);
							if (leftover == 0) {
								inv.holding.setEmpty();
							}
						}
					} else {
						// Different item - swap
						mc.sayda.mcraze.item.InventoryItem temp = new mc.sayda.mcraze.item.InventoryItem(null);
						temp.setItem(inv.holding.getItem());
						temp.setCount(inv.holding.getCount());

						inv.holding.setItem(chestSlot.getItem());
						inv.holding.setCount(chestSlot.getCount());

						chestSlot.setItem(temp.getItem());
						chestSlot.setCount(temp.getCount());
					}
				}
			}
		} else {
			// Clicked on player inventory slot - use normal inventory interaction
			// Map chest UI player inventory coords to actual inventory coords
			// Chest UI shows 10x4 grid: 3 rows + hotbar
			if (packet.slotX >= 0 && packet.slotX < 10 && packet.slotY >= 0 && packet.slotY < 4) {
				int invX = packet.slotX;
				// CRITICAL FIX: Map chest UI row index (0-3) to actual player inventory row
				// index (3-6)
				// Rows 0-2 are crafting grid, rows 3-5 are main inventory, row 6 is hotbar
				int invY = packet.slotY + 3;

				// Validate bounds
				if (invX < inv.inventoryItems.length && invY < inv.inventoryItems[0].length) {
					// Use same slot interaction logic as inventory
					handleInventorySlotInteraction(inv, invX, invY, packet.isRightClick, logger);
					// CRITICAL FIX: Ensure craftable preview is updated if any inventory slot
					// changed
					// (even during container interaction)
					updateCraftablePreview(inv);
				}
			}
		}

		// Send chest update only to the acting player (not all players)
		// This prevents all players' chest UIs from opening when one player opens a
		// chest
		PacketChestOpen refreshPacket = new PacketChestOpen(packet.chestX, packet.chestY, chestData.items,
				player.getUUID());
		playerConnection.getConnection().sendPacket(refreshPacket);

		// Also broadcast inventory updates (for player inventory changes)
		broadcastInventoryUpdates();
	}

	/**
	 * Handle furnace inventory actions (server-authoritative)
	 * Implements item transfer for 3-slot furnace: input, fuel, output
	 */
	public void handleFurnaceAction(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketFurnaceAction packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot interact
		if (player.dead) {
			return;
		}

		// SECURITY: Validate player has this furnace open
		// Prevent players from interacting with furnaces they don't have open
		if (playerConnection.getOpenedFurnaceX() != packet.furnaceX ||
				playerConnection.getOpenedFurnaceY() != packet.furnaceY) {
			if (logger != null) {
				logger.warn("Player " + player.username + " tried to interact with furnace at (" +
						packet.furnaceX + "," + packet.furnaceY + ") but has furnace at (" +
						playerConnection.getOpenedFurnaceX() + "," + playerConnection.getOpenedFurnaceY() + ") open");
			}
			return;
		}

		// Get the furnace data
		mc.sayda.mcraze.world.storage.FurnaceData furnaceData = world.getOrCreateFurnace(packet.furnaceX,
				packet.furnaceY);
		mc.sayda.mcraze.player.Inventory inv = player.inventory;

		if (packet.isFurnaceSlot) {
			// Clicked on furnace slot
			if (packet.slotX >= 0 && packet.slotX < 3 && packet.slotY >= 0 && packet.slotY < 2) {
				mc.sayda.mcraze.item.InventoryItem furnaceSlot = furnaceData.getInventoryItem(packet.slotX,
						packet.slotY);

				// Input slot (0,0) or Fuel slot (0,1) or Output slot (2,0)

				boolean isFuelSlot = (packet.slotX == 0 && packet.slotY == 1);
				boolean isOutputSlot = (packet.slotX == 2 && packet.slotY == 0);

				if (inv.holding.isEmpty()) {
					// Picking up from furnace slot
					if (!furnaceSlot.isEmpty()) {
						if (packet.isRightClick && furnaceSlot.getCount() > 1 && !isOutputSlot) {
							// Right click - pick up half (not for output)
							int halfCount = (furnaceSlot.getCount() + 1) / 2;
							inv.holding.setItem(furnaceSlot.getItem().clone());
							inv.holding.setCount(halfCount);
							furnaceSlot.setCount(furnaceSlot.getCount() - halfCount);
						} else {
							// Left click or output slot - pick up all
							int countToTake = furnaceSlot.getCount();

							// [NEW] Metallurgy Passive (Vanguard)
							if (isOutputSlot
									&& player.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.VANGUARD
									&& player.unlockedPassives.contains(
											mc.sayda.mcraze.player.specialization.PassiveEffectType.METALLURGY)) {
								if (random.nextDouble() < 0.20) {
									countToTake *= 2;
									logger.info("Metallurgy triggered! Doubling output.");
								}
							}

							inv.holding.setItem(furnaceSlot.getItem());
							// Cap at 64 for holding, surplus stays in furnace
							int toHolding = Math.min(64, countToTake);
							inv.holding.setCount(toHolding);

							int surplus = countToTake - toHolding;
							if (surplus > 0) {
								furnaceSlot.setCount(surplus);
							} else {
								furnaceSlot.setEmpty();
							}
						}
					}
				} else {
					// Placing into furnace slot

					// Validate slot restrictions
					boolean canPlace = true;
					if (isFuelSlot) {
						// Fuel slot: only accept items with fuelBurnTime > 0
						canPlace = (inv.holding.getItem().fuelBurnTime > 0);
					} else if (isOutputSlot) {
						// Output slot: can only stack same items, not place new
						canPlace = !furnaceSlot.isEmpty() &&
								furnaceSlot.getItem().itemId.equals(inv.holding.getItem().itemId);
					}
					// Input slot accepts anything

					if (!canPlace) {
						return; // Invalid placement
					}

					if (furnaceSlot.isEmpty()) {
						// Empty slot - place items
						if (packet.isRightClick && !isOutputSlot) {
							// Right click - place one
							furnaceSlot.setItem(inv.holding.getItem().clone());
							furnaceSlot.setCount(1);
							inv.holding.remove(1);
						} else {
							// Left click - place all
							furnaceSlot.setItem(inv.holding.getItem().clone());
							furnaceSlot.setCount(inv.holding.getCount());
							inv.holding.setEmpty();
						}
					} else if (furnaceSlot.getItem().itemId.equals(inv.holding.getItem().itemId)) {
						// Same item type - try to stack
						if (packet.isRightClick) {
							// Right click - add one
							int added = furnaceSlot.add(inv.holding.getItem(), 1);
							if (added == 0) {
								inv.holding.remove(1);
							}
						} else {
							// Left click - add all
							int leftover = furnaceSlot.add(inv.holding.getItem(), inv.holding.getCount());
							inv.holding.setCount(leftover);
							if (leftover == 0) {
								inv.holding.setEmpty();
							}
						}
					} else {
						// Different item - swap (not for output slot)
						if (!isOutputSlot) {
							mc.sayda.mcraze.item.InventoryItem temp = new mc.sayda.mcraze.item.InventoryItem(null);
							temp.setItem(inv.holding.getItem());
							temp.setCount(inv.holding.getCount());

							inv.holding.setItem(furnaceSlot.getItem());
							inv.holding.setCount(furnaceSlot.getCount());

							furnaceSlot.setItem(temp.getItem());
							furnaceSlot.setCount(temp.getCount());
						}
					}
				}
			}
		} else {
			// Clicked on player inventory slot
			if (packet.slotX >= 0 && packet.slotX < 10 && packet.slotY >= 0 && packet.slotY < 4) {
				int invX = packet.slotX;
				int invY = packet.slotY + 3; // Map to inventory rows

				if (invX < inv.inventoryItems.length && invY < inv.inventoryItems[0].length) {
					handleInventorySlotInteraction(inv, invX, invY, packet.isRightClick, logger);
					// CRITICAL FIX: Ensure craftable preview is updated if any inventory slot
					// changed
					updateCraftablePreview(inv);
				}
			}
		}

		// Send furnace update to player
		PacketFurnaceOpen refreshPacket = new PacketFurnaceOpen(
				packet.furnaceX, packet.furnaceY,
				furnaceData.items,
				furnaceData.fuelTimeRemaining,
				furnaceData.smeltProgress,
				furnaceData.smeltTimeTotal,
				player.getUUID(),
				true);
		playerConnection.getConnection().sendPacket(refreshPacket);

		// Broadcast inventory updates
		broadcastInventoryUpdates();
	}

	/**
	 * Handle inventory slot interaction (shared between chest UI and regular
	 * inventory)
	 */
	/**
	 * Tick all furnaces - called every server tick
	 * Handles fuel consumption, smelting progress, and item transfer
	 */
	public void tickFurnaces() {
		for (mc.sayda.mcraze.world.storage.FurnaceData furnace : world.getAllFurnaces().values()) {
			tickFurnace(furnace);
		}
	}

	/**
	 * Tick a single furnace
	 */
	private void tickFurnace(mc.sayda.mcraze.world.storage.FurnaceData furnace) {
		// Valid bounds check to prevent crashes if save data is corrupted
		if (furnace.x < 0 || furnace.x >= world.width || furnace.y < 0 || furnace.y >= world.height) {
			if (logger != null && ticksRunning % 36000 == 0) { // Log extremely rarely (every 10 mins)
				logger.warn("SharedWorld.tickFurnace: Skipped invalid furnace at " + furnace.x + "," + furnace.y);
			}
			return;
		}

		mc.sayda.mcraze.item.InventoryItem inputSlot = furnace.getInputSlot();
		mc.sayda.mcraze.item.InventoryItem fuelSlot = furnace.getFuelSlot();
		mc.sayda.mcraze.item.InventoryItem outputSlot = furnace.getOutputSlot();

		// Check if input item has a smelting recipe
		String recipeResult = null;
		int recipeTime = 0;

		if (!inputSlot.isEmpty() && inputSlot.getItem() != null) {
			// Get smelting recipe from ItemLoader
			mc.sayda.mcraze.item.ItemLoader.ItemDefinition def = mc.sayda.mcraze.item.ItemLoader
					.getItemDefinition(inputSlot.getItem().itemId);
			if (def != null && def.smelting != null) {
				recipeResult = def.smelting.result;
				recipeTime = def.smelting.time;
			}
		}

		boolean hasRecipe = (recipeResult != null);
		boolean canSmelt = hasRecipe;

		// Check if output slot can accept result
		if (hasRecipe && !outputSlot.isEmpty()) {
			mc.sayda.mcraze.item.Item resultItem = mc.sayda.mcraze.Constants.itemTypes.get(recipeResult);
			if (resultItem == null || !outputSlot.getItem().itemId.equals(recipeResult)) {
				canSmelt = false; // Output blocked by different item
			} else if (outputSlot.getCount() >= 64) {
				canSmelt = false; // Output full
			}
		}

		// Fuel consumption
		if (furnace.fuelTimeRemaining > 0) {
			furnace.fuelTimeRemaining--;
		}

		// Try to consume new fuel if needed
		if (furnace.fuelTimeRemaining <= 0 && canSmelt && !fuelSlot.isEmpty()) {
			mc.sayda.mcraze.item.Item fuel = fuelSlot.getItem();
			if (fuel != null && fuel.fuelBurnTime > 0) {
				// Consume one fuel item
				furnace.fuelTimeRemaining = fuel.fuelBurnTime;
				fuelSlot.remove(1);
			}
		}

		// Smelting progress
		if (canSmelt && furnace.fuelTimeRemaining > 0) {
			// Start or continue smelting
			if (furnace.currentRecipe == null || !furnace.currentRecipe.equals(recipeResult)) {
				// New recipe, reset progress
				furnace.currentRecipe = recipeResult;
				furnace.smeltProgress = 0;
				furnace.smeltTimeTotal = recipeTime;
			}

			furnace.smeltProgress++;

			// Check if smelting is complete
			if (furnace.smeltProgress >= furnace.smeltTimeTotal) {
				// Transfer to output
				mc.sayda.mcraze.item.Item resultItem = mc.sayda.mcraze.Constants.itemTypes.get(recipeResult);
				if (resultItem != null) {
					if (outputSlot.isEmpty()) {
						outputSlot.setItem(resultItem.clone());
						outputSlot.setCount(1);
					} else {
						outputSlot.add(resultItem, 1);
					}

					// Consume input
					inputSlot.remove(1);

					// Reset progress
					furnace.smeltProgress = 0;
					furnace.currentRecipe = null;
					furnace.smeltTimeTotal = 0;
				}
			}
		} else {
			// No smelting happening, decay progress
			if (furnace.smeltProgress > 0) {
				furnace.smeltProgress--;
				if (furnace.smeltProgress <= 0) {
					furnace.currentRecipe = null;
					furnace.smeltTimeTotal = 0;
				}
			}
		}

		// Update Visual State (Lit/Unlit)
		if (furnace.fuelTimeRemaining > 0) {
			// Should be lit
			mc.sayda.mcraze.world.tile.Tile tile = world.tiles[furnace.x][furnace.y];
			if (tile != null && tile.type.name == mc.sayda.mcraze.Constants.TileID.FURNACE) {
				// Switch to lit
				world.changeTile(furnace.x, furnace.y, new mc.sayda.mcraze.world.tile.Tile(
						mc.sayda.mcraze.Constants.tileTypes.get(mc.sayda.mcraze.Constants.TileID.FURNACE_LIT).type));
				broadcastBlockChange(furnace.x, furnace.y, mc.sayda.mcraze.Constants.TileID.FURNACE_LIT);
			}
		} else {
			// Should be unlit
			mc.sayda.mcraze.world.tile.Tile tile = world.tiles[furnace.x][furnace.y];
			if (tile != null && tile.type.name == mc.sayda.mcraze.Constants.TileID.FURNACE_LIT) {
				// Switch to unlit
				world.changeTile(furnace.x, furnace.y, new mc.sayda.mcraze.world.tile.Tile(
						mc.sayda.mcraze.Constants.tileTypes.get(mc.sayda.mcraze.Constants.TileID.FURNACE).type));
				broadcastBlockChange(furnace.x, furnace.y, mc.sayda.mcraze.Constants.TileID.FURNACE);
			}
		}

		// Broadcast updates to players who have this furnace open
		for (PlayerConnection pc : players) {
			if (pc.getOpenedFurnaceX() == furnace.x && pc.getOpenedFurnaceY() == furnace.y) {
				mc.sayda.mcraze.network.packet.PacketFurnaceOpen updatePacket = new mc.sayda.mcraze.network.packet.PacketFurnaceOpen(
						furnace.x, furnace.y,
						furnace.items,
						furnace.fuelTimeRemaining,
						furnace.smeltProgress,
						furnace.smeltTimeTotal,
						pc.getPlayer().getUUID(),
						true);
				pc.getConnection().sendPacket(updatePacket);
			}
		}
	}

	/**
	 * Handle shift-click quick transfer (inventory QoL feature)
	 * Implements Minecraft-like quick transfer with smart stack merging
	 */
	public void handleShiftClick(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketShiftClick packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null || player.dead) {
			return;
		}

		mc.sayda.mcraze.player.Inventory playerInv = player.inventory;

		// Determine source and destination based on container type
		switch (packet.containerType) {
			case CHEST:
				handleChestShiftClick(playerConnection, packet, playerInv);
				break;
			case FURNACE:
				handleFurnaceShiftClick(playerConnection, packet, playerInv);
				break;
			case ALCHEMY:
				handleAlchemyShiftClick(playerConnection, packet, playerInv);
				break;
			case PLAYER_INVENTORY:
				handlePlayerInventoryShiftClick(playerConnection, packet, playerInv);
				break;
		}

		// Broadcast inventory update to the player
		sendInventoryUpdate(playerConnection);
	}

	/**
	 * Handle inventory drag (drag-to-split or drag-to-place-one)
	 * Left drag = distribute evenly, Right drag = place 1 per slot
	 */
	public void handleInventoryDrag(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketInventoryDrag packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null || player.dead) {
			return;
		}

		boolean debug = true;
		mc.sayda.mcraze.player.Inventory inv = player.inventory;
		if (debug && logger != null) {
			logger.info("SharedWorld received Drag Packet! Slots: " + packet.slots.size() + " Left: "
					+ packet.isEvenSplit + " Container: " + packet.containerType);
			logger.info("  Holding: isEmpty=" + inv.holding.isEmpty() +
					(inv.holding.isEmpty() ? ""
							: " item=" + inv.holding.getItem().itemId + " count=" + inv.holding.getCount()));
		}

		if (inv.holding.isEmpty() || packet.slots == null || packet.slots.isEmpty()) {
			if (debug && logger != null) {
				logger.info("  ABORTED: holding empty=" + inv.holding.isEmpty() + " slots null/empty="
						+ (packet.slots == null || packet.slots.isEmpty()));
			}
			return; // No items to distribute or no slots dragged
		}

		int itemsInHand = inv.holding.getCount();
		int slotsCount = packet.slots.size();

		if (debug && logger != null) {
			logger.info("  Starting distribution: itemsInHand=" + itemsInHand + " slotsCount=" + slotsCount
					+ " isEvenSplit=" + packet.isEvenSplit);
			for (int i = 0; i < packet.slots.size(); i++) {
				mc.sayda.mcraze.util.Int2 s = packet.slots.get(i);
				logger.info("    Slot[" + i + "]: " + s.x + "," + s.y);
			}
		}

		// RIGHT DRAG ENABLED - place 1 item per slot (skip first slot - that's the
		// source)
		// The client sends a CLICK packet for the first slot immediately, so we must
		// skip it here.
		if (!packet.isEvenSplit) {
			if (debug && logger != null) {
				logger.info("  RIGHT DRAG: placing 1 per slot (skipping source slot)");
			}
			for (int i = 1; i < packet.slots.size(); i++) {
				mc.sayda.mcraze.util.Int2 slot = packet.slots.get(i);
				if (itemsInHand <= 0)
					break;

				mc.sayda.mcraze.item.InventoryItem targetSlot = null;

				// Resolve target slot based on container type
				if (packet.containerType == mc.sayda.mcraze.network.packet.PacketInventoryDrag.ContainerType.CHEST) {
					ChestData chest = getChestAt(playerConnection.getOpenedChestX(),
							playerConnection.getOpenedChestY());
					if (chest == null)
						continue;

					if (slot.y < 3) {
						// Chest slot (rows 0-2)
						if (slot.x >= 0 && slot.x < 10) {
							targetSlot = chest.items[slot.x][slot.y];
						}
					} else {
						// Player slot (mapped Y 3-6 -> Inventory Row 3-6)
						// DIRECT MAPPING: Chest UI Y coordinate aligns perfectly with Inventory Rows
						// (Crafting 0-2, Storage 3-5, Hotbar 6)
						if (slot.y >= 3 && slot.y < 7 && slot.x >= 0 && slot.x < 10) {
							targetSlot = inv.inventoryItems[slot.x][slot.y];
						}
					}
				} else if (packet.containerType == mc.sayda.mcraze.network.packet.PacketInventoryDrag.ContainerType.FURNACE) {
					FurnaceData furnace = getOrCreateFurnace(playerConnection.getOpenedFurnaceX(),
							playerConnection.getOpenedFurnaceY());
					if (furnace == null)
						continue;

					if (slot.y < 2) {
						if (slot.x >= 0 && slot.x < 3) {
							targetSlot = furnace.items[slot.x][slot.y];
						}
					} else {
						// Player slot (mapped Y 3-6 -> Inventory Row 3-6)
						// DIRECT MAPPING: Furnace UI Y coordinate aligns perfectly with Inventory Rows
						// 3-6
						if (slot.y >= 3 && slot.y < 7 && slot.x >= 0 && slot.x < 10) {
							targetSlot = inv.inventoryItems[slot.x][slot.y];
						}
					}
				} else {
					// Player Inventory
					if (slot.x >= 0 && slot.x < 10 && slot.y >= 0 && slot.y < inv.inventoryItems[0].length) {
						targetSlot = inv.inventoryItems[slot.x][slot.y];
						// Validate visible crafting slots
						if (slot.y < 3
								&& (slot.x < (10 - inv.tableSizeAvailable) || slot.y >= inv.tableSizeAvailable)) {
							targetSlot = null; // Invisible slot
						}
					}
				}

				if (targetSlot == null)
					continue;

				// Only place in empty slots or matching item stacks
				if (targetSlot.isEmpty()) {
					targetSlot.setItem(inv.holding.getItem().clone());
					targetSlot.setCount(1);
					itemsInHand--;
				} else if (targetSlot.getItem().itemId.equals(inv.holding.getItem().itemId)) {
					int maxStack = 64;
					if (targetSlot.getCount() < maxStack) {
						targetSlot.setCount(targetSlot.getCount() + 1);
						itemsInHand--;
					}
				}
			}
		}

		// Update holding count
		if (itemsInHand <= 0) {
			inv.holding.setEmpty();
		} else {
			inv.holding.setCount(itemsInHand);
		}

		// Send inventory update (updates player inventory & holding)
		sendInventoryUpdate(playerConnection);

		// CRITICAL FIX: Send container update if we modified a chest or furnace
		// Without this, the client doesn't see the items placed in the container
		if (packet.containerType == mc.sayda.mcraze.network.packet.PacketInventoryDrag.ContainerType.CHEST) {
			mc.sayda.mcraze.world.storage.ChestData chest = getChestAt(playerConnection.getOpenedChestX(),
					playerConnection.getOpenedChestY());
			if (chest != null) {
				mc.sayda.mcraze.network.packet.PacketChestOpen refreshPacket = new mc.sayda.mcraze.network.packet.PacketChestOpen(
						playerConnection.getOpenedChestX(), playerConnection.getOpenedChestY(), chest.items,
						playerConnection.getPlayer().getUUID());
				playerConnection.getConnection().sendPacket(refreshPacket);
			}
		} else if (packet.containerType == mc.sayda.mcraze.network.packet.PacketInventoryDrag.ContainerType.FURNACE) {
			mc.sayda.mcraze.world.storage.FurnaceData furnace = getOrCreateFurnace(playerConnection.getOpenedFurnaceX(),
					playerConnection.getOpenedFurnaceY());
			if (furnace != null) {
				mc.sayda.mcraze.network.packet.PacketFurnaceOpen refreshPacket = new mc.sayda.mcraze.network.packet.PacketFurnaceOpen(
						playerConnection.getOpenedFurnaceX(), playerConnection.getOpenedFurnaceY(),
						furnace.items, furnace.fuelTimeRemaining, furnace.smeltProgress,
						furnace.smeltTimeTotal, playerConnection.getPlayer().getUUID(), false);
				playerConnection.getConnection().sendPacket(refreshPacket);
			}
		}
	}

	/**
	 * Handle double-click collect - collect all matching items from inventory into
	 * cursor
	 */
	public void handleInventoryDoubleClick(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketInventoryDoubleClick packet) {
		// Double-click feature DISABLED - feature caused unwanted behavior
		return;
		/*
		 * Player player = playerConnection.getPlayer();
		 * if (player == null || player.inventory == null || player.dead) {
		 * return;
		 * }
		 * 
		 * mc.sayda.mcraze.player.Inventory inv = player.inventory;
		 * 
		 * // Resolve clicked slot and container context
		 * mc.sayda.mcraze.item.InventoryItem clickedSlot = null;
		 * 
		 * if (packet.containerType ==
		 * mc.sayda.mcraze.network.packet.PacketInventoryDoubleClick.ContainerType.
		 * CHEST) {
		 * ChestData chest = getChestAt(playerConnection.getOpenedChestX(),
		 * playerConnection.getOpenedChestY());
		 * if (chest != null) {
		 * if (packet.slotY < 3) {
		 * if (packet.slotX >= 0 && packet.slotX < 10)
		 * clickedSlot = chest.items[packet.slotX][packet.slotY];
		 * } else {
		 * // ChestUI maps player inventory rows 0-3 to 3-6
		 * int playerRow = packet.slotY - 3;
		 * if (playerRow >= 0 && playerRow < 4) {
		 * if (packet.slotX >= 0 && packet.slotX < 10)
		 * clickedSlot = inv.inventoryItems[packet.slotX][playerRow];
		 * }
		 * }
		 * }
		 * } else if (packet.containerType ==
		 * mc.sayda.mcraze.network.packet.PacketInventoryDoubleClick.ContainerType.
		 * FURNACE) {
		 * FurnaceData furnace = getOrCreateFurnace(playerConnection.getOpenedChestX(),
		 * playerConnection.getOpenedChestY());
		 * if (furnace != null) {
		 * if (packet.slotY < 2) {
		 * if (packet.slotX >= 0 && packet.slotX < 3)
		 * clickedSlot = furnace.items[packet.slotX][packet.slotY];
		 * } else {
		 * // FurnaceUI maps player inventory rows 0-3 to 2-5
		 * int playerRow = packet.slotY - 2;
		 * if (playerRow >= 0 && playerRow < 4) {
		 * if (packet.slotX >= 0 && packet.slotX < 10)
		 * clickedSlot = inv.inventoryItems[packet.slotX][playerRow];
		 * }
		 * }
		 * }
		 * } else {
		 * // Player Inventory
		 * if (packet.slotX >= 0 && packet.slotX < inv.inventoryItems.length &&
		 * packet.slotY >= 0 && packet.slotY < inv.inventoryItems[0].length) {
		 * clickedSlot = inv.inventoryItems[packet.slotX][packet.slotY];
		 * }
		 * }
		 * 
		 * // Minecraft-like: Determine target item based on cursor state
		 * // If holding items -> collect more of what we're holding (ignore clicked
		 * slot)
		 * // If cursor empty -> pick up clicked slot and collect more of that type
		 * String targetItemId;
		 * int maxStack = 64;
		 * 
		 * if (!inv.holding.isEmpty()) {
		 * // Already holding items - collect more of the SAME type we're holding
		 * targetItemId = inv.holding.getItem().itemId;
		 * } else {
		 * // Cursor empty - must click on an item to start collecting
		 * if (clickedSlot == null || clickedSlot.isEmpty()) {
		 * return; // Nothing to collect
		 * }
		 * targetItemId = clickedSlot.getItem().itemId;
		 * // Pick up the clicked slot first
		 * inv.holding.setItem(clickedSlot.getItem());
		 * inv.holding.setCount(clickedSlot.getCount());
		 * clickedSlot.setEmpty();
		 * }
		 * 
		 * // Helper to collect from a grid
		 * java.util.function.Consumer<mc.sayda.mcraze.item.InventoryItem[][]>
		 * collectFromGrid = (grid) -> {
		 * for (int y = 0; y < grid[0].length; y++) {
		 * for (int x = 0; x < grid.length; x++) {
		 * if (inv.holding.getCount() >= maxStack)
		 * return;
		 * 
		 * mc.sayda.mcraze.item.InventoryItem slot = grid[x][y];
		 * if (slot != null && !slot.isEmpty() &&
		 * slot.getItem().itemId.equals(targetItemId)) {
		 * if (slot.getItem() instanceof mc.sayda.mcraze.item.Tool)
		 * continue;
		 * 
		 * int spaceLeft = maxStack - inv.holding.getCount();
		 * int takeCount = Math.min(slot.getCount(), spaceLeft);
		 * 
		 * inv.holding.setCount(inv.holding.getCount() + takeCount);
		 * slot.setCount(slot.getCount() - takeCount);
		 * 
		 * if (slot.getCount() <= 0)
		 * slot.setEmpty();
		 * }
		 * }
		 * }
		 * };
		 * 
		 * // Collect from Container FIRST if applicable
		 * if (packet.containerType ==
		 * mc.sayda.mcraze.network.packet.PacketInventoryDoubleClick.ContainerType.
		 * CHEST) {
		 * ChestData chest = getChestAt(playerConnection.getOpenedChestX(),
		 * playerConnection.getOpenedChestY());
		 * if (chest != null)
		 * collectFromGrid.accept(chest.items);
		 * } else if (packet.containerType ==
		 * mc.sayda.mcraze.network.packet.PacketInventoryDoubleClick.ContainerType.
		 * FURNACE) {
		 * FurnaceData furnace = getOrCreateFurnace(playerConnection.getOpenedChestX(),
		 * playerConnection.getOpenedChestY());
		 * if (furnace != null)
		 * collectFromGrid.accept(furnace.items);
		 * }
		 * 
		 * // Collect from Player Inventory
		 * collectFromGrid.accept(inv.inventoryItems);
		 * 
		 * // Send inventory update
		 * sendInventoryUpdate(playerConnection);
		 * 
		 * // Broadcast chest update if needed
		 * if (packet.containerType ==
		 * mc.sayda.mcraze.network.packet.PacketInventoryDoubleClick.ContainerType.
		 * CHEST) {
		 * ChestData chest = getChestAt(playerConnection.getOpenedChestX(),
		 * playerConnection.getOpenedChestY());
		 * if (chest != null) {
		 * for (PlayerConnection pc : players) {
		 * if (pc.getOpenedChestX() == playerConnection.getOpenedChestX()
		 * && pc.getOpenedChestY() == playerConnection.getOpenedChestY()) {
		 * pc.sendPacket(new
		 * mc.sayda.mcraze.network.packet.PacketChestOpen(pc.getOpenedChestX(),
		 * pc.getOpenedChestY(), chest.items, "Chest"));
		 * }
		 * }
		 * }
		 * }
		 */
	}

	/**
	 * Helper: Shift-click in chest UI
	 */
	private void handleChestShiftClick(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketShiftClick packet,
			mc.sayda.mcraze.player.Inventory playerInv) {
		// Validate chest is open
		if (playerConnection.getOpenedChestX() == -1 || playerConnection.getOpenedChestY() == -1) {
			return;
		}

		mc.sayda.mcraze.world.storage.ChestData chestData = world.getOrCreateChest(
				playerConnection.getOpenedChestX(), playerConnection.getOpenedChestY());

		if (packet.isContainerSlot) {
			// Shift-click chest slot → transfer to player inventory
			if (packet.slotX >= 0 && packet.slotX < 10 && packet.slotY >= 0 && packet.slotY < 3) {
				mc.sayda.mcraze.item.InventoryItem sourceSlot = chestData.getInventoryItem(packet.slotX, packet.slotY);
				if (!sourceSlot.isEmpty()) {
					quickTransferToPlayerInventory(sourceSlot, playerInv);
				}
			}
		} else {
			// Shift-click player inventory → transfer to chest
			// In ChestUI, player inv shows rows 0-3 which map to actual rows 3-6
			int playerRow = packet.slotY + 3; // UI 0→3, 1→4, 2→5, 3→6
			if (packet.slotX >= 0 && packet.slotX < 10 && playerRow >= 0
					&& playerRow < playerInv.inventoryItems[0].length) {
				mc.sayda.mcraze.item.InventoryItem sourceSlot = playerInv.inventoryItems[packet.slotX][playerRow];
				if (!sourceSlot.isEmpty()) {
					quickTransferToChest(sourceSlot, chestData);
				}
			}
		}

		// Refresh chest for all viewers by sending PacketChestOpen
		// This ensures the chest inventory is re-synced and items don't become
		// invisible
		for (PlayerConnection pc : players) {
			if (pc.getOpenedChestX() == playerConnection.getOpenedChestX() &&
					pc.getOpenedChestY() == playerConnection.getOpenedChestY()) {
				mc.sayda.mcraze.network.packet.PacketChestOpen updatePacket = new mc.sayda.mcraze.network.packet.PacketChestOpen(
						playerConnection.getOpenedChestX(),
						playerConnection.getOpenedChestY(),
						chestData.items,

						pc.getPlayer().getUUID());
				pc.getConnection().sendPacket(updatePacket);
			}
		}
	}

	/**
	 * Helper: Shift-click in furnace UI
	 */
	private void handleFurnaceShiftClick(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketShiftClick packet,
			mc.sayda.mcraze.player.Inventory playerInv) {
		// Validate furnace is open
		if (playerConnection.getOpenedFurnaceX() == -1 || playerConnection.getOpenedFurnaceY() == -1) {
			return;
		}

		mc.sayda.mcraze.world.storage.FurnaceData furnaceData = world.getOrCreateFurnace(
				playerConnection.getOpenedFurnaceX(), playerConnection.getOpenedFurnaceY());
		if (furnaceData == null)
			return;

		if (packet.isContainerSlot) {
			// Shift-click furnace slot → transfer to player inventory
			mc.sayda.mcraze.item.InventoryItem sourceSlot = null;
			if (packet.slotX == 0 && packet.slotY == 0) {
				sourceSlot = furnaceData.getInputSlot();
			} else if (packet.slotX == 0 && packet.slotY == 1) {
				sourceSlot = furnaceData.getFuelSlot();
			} else if (packet.slotX == 2 && packet.slotY == 0) {
				sourceSlot = furnaceData.getOutputSlot();
			}

			if (sourceSlot != null && !sourceSlot.isEmpty()) {
				quickTransferToPlayerInventory(sourceSlot, playerInv);
			}
		} else {
			// Shift-click player inventory → transfer to furnace (input or fuel)
			// In FurnaceUI, player inv shows rows 0-3 which map to actual rows 3-6
			int playerRow = packet.slotY + 3; // UI 0→3, 1→4, 2→5, 3→6
			if (packet.slotX >= 0 && packet.slotX < 10 && playerRow >= 0
					&& playerRow < playerInv.inventoryItems[0].length) {
				mc.sayda.mcraze.item.InventoryItem sourceSlot = playerInv.inventoryItems[packet.slotX][playerRow];
				if (!sourceSlot.isEmpty()) {
					quickTransferToFurnace(sourceSlot, furnaceData);
				}
			}
		}

		// Refresh furnace for all viewers (use existing update mechanism)
		// Furnace data is updated in-place, viewers will see changes on next UI refresh
	}

	/**
	 * Helper: Shift-click in regular player inventory (storage ↔ hotbar)
	 */
	private void handlePlayerInventoryShiftClick(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketShiftClick packet,
			mc.sayda.mcraze.player.Inventory playerInv) {

		if (logger != null) {
			logger.info("Player inventory shift-click: slot(" + packet.slotX + "," + packet.slotY
					+ "), tableSizeAvailable=" + packet.tableSizeAvailable);
		}

		int sourceRow = mapUIRowToInventoryRow(packet.slotY);
		if (packet.slotX < 0 || packet.slotX >= 10 || sourceRow < 0
				|| sourceRow >= playerInv.inventoryItems[0].length) {
			if (logger != null) {
				logger.warn("Invalid slot coordinates: (" + packet.slotX + "," + sourceRow + ")");
			}
			return;
		}

		// SECURITY: Validate that clicked slot is not in the invisible crafting area
		// In 2x2 mode (tableSizeAvailable=2), only columns 8-9 of rows 0-1 are visible
		// (crafting area)
		// In 3x3 mode (tableSizeAvailable=3), all of columns 7-9 of rows 0-2 are
		// visible
		// Rows 3-6 are always visible (storage + hotbar)
		if (sourceRow < 3) {
			// Clicking in crafting area - validate based on mode
			int craftingStartCol = 10 - packet.tableSizeAvailable;
			if (packet.slotX < craftingStartCol || sourceRow >= packet.tableSizeAvailable) {
				// Trying to click invisible crafting slot - reject
				if (logger != null)
					logger.warn("Player " + playerConnection.getPlayerName() +
							" tried to shift-click invisible crafting slot (" + packet.slotX + "," + sourceRow +
							") in " + packet.tableSizeAvailable + "x" + packet.tableSizeAvailable + " mode");
				return;
			}
		}

		mc.sayda.mcraze.item.InventoryItem sourceSlot = playerInv.inventoryItems[packet.slotX][sourceRow];
		if (sourceSlot.isEmpty()) {
			if (logger != null) {
				logger.info("Source slot is empty, nothing to transfer");
			}
			return;
		}

		// Determine if clicking hotbar or storage
		boolean isHotbar = (sourceRow == 6); // Row 6 is hotbar

		if (logger != null) {
			logger.info("Transferring from " + (isHotbar ? "hotbar" : "storage") + " row " + sourceRow);
		}

		if (sourceRow < 3) {
			// Crafting Grid -> Player Inventory (Storage + Hotbar)
			// Use the generic transfer method which tries to merge/place in rows 3-6
			quickTransferToPlayerInventory(sourceSlot, playerInv);
		} else if (isHotbar) {
			// Hotbar → Storage (rows 3-5)
			quickTransferToStorage(sourceSlot, playerInv);
		} else {
			// Storage → Hotbar (row 6)
			quickTransferToHotbar(sourceSlot, playerInv);
		}
	}

	/**
	 * Map UI row (0-3) to actual inventory row
	 * Inventory structure: rows 0-2 are crafting, rows 3-5 are storage, row 6 is
	 * hotbar
	 * UI displays rows 3-6 (storage + hotbar) as rows 0-3
	 * So UI row 0 = inventory row 3, UI row 1 = inv row 4, UI row 2 = inv row 5, UI
	 * row 3 = inv row 6
	 */
	private int mapUIRowToInventoryRow(int uiRow) {
		// UI sends actual inventory rows 0-6
		if (uiRow < 0 || uiRow > 6)
			return -1;
		return uiRow; // No offset needed
	}

	/**
	 * Quick transfer: chest/furnace → player inventory
	 * Try to merge with existing stacks first, then use empty slots
	 */
	private void quickTransferToPlayerInventory(mc.sayda.mcraze.item.InventoryItem sourceSlot,
			mc.sayda.mcraze.player.Inventory playerInv) {
		if (sourceSlot.isEmpty())
			return;

		int remaining = sourceSlot.getCount();
		mc.sayda.mcraze.item.Item item = sourceSlot.getItem();

		// Phase 1: Try to merge with existing stacks (hotbar first, then storage)
		for (int row = 6; row >= 3 && remaining > 0; row--) {
			for (int col = 0; col < 10 && remaining > 0; col++) {
				mc.sayda.mcraze.item.InventoryItem targetSlot = playerInv.inventoryItems[col][row];
				if (!targetSlot.isEmpty() && targetSlot.getItem().itemId.equals(item.itemId)) {
					int added = targetSlot.add(item, remaining);
					remaining -= (remaining - added);
				}
			}
		}

		// Phase 2: Fill empty slots if still have items
		for (int row = 6; row >= 3 && remaining > 0; row--) {
			for (int col = 0; col < 10 && remaining > 0; col++) {
				mc.sayda.mcraze.item.InventoryItem targetSlot = playerInv.inventoryItems[col][row];
				if (targetSlot.isEmpty()) {
					targetSlot.setItem(item.clone());
					targetSlot.setCount(remaining);
					remaining = 0;
					break;
				}
			}
		}

		// Update source slot
		if (remaining == 0) {
			sourceSlot.setEmpty();
		} else {
			sourceSlot.setCount(remaining);
		}
	}

	/**
	 * Quick transfer: player inventory → chest
	 */
	private void quickTransferToChest(mc.sayda.mcraze.item.InventoryItem sourceSlot,
			mc.sayda.mcraze.world.storage.ChestData chestData) {
		if (sourceSlot.isEmpty())
			return;

		int remaining = sourceSlot.getCount();
		mc.sayda.mcraze.item.Item item = sourceSlot.getItem();

		// Phase 1: Merge with existing stacks
		for (int row = 0; row < 3 && remaining > 0; row++) {
			for (int col = 0; col < 10 && remaining > 0; col++) {
				mc.sayda.mcraze.item.InventoryItem targetSlot = chestData.getInventoryItem(col, row);
				if (!targetSlot.isEmpty() && targetSlot.getItem().itemId.equals(item.itemId)) {
					int added = targetSlot.add(item, remaining);
					remaining -= (remaining - added);
				}
			}
		}

		// Phase 2: Fill empty slots
		for (int row = 0; row < 3 && remaining > 0; row++) {
			for (int col = 0; col < 10 && remaining > 0; col++) {
				mc.sayda.mcraze.item.InventoryItem targetSlot = chestData.getInventoryItem(col, row);
				if (targetSlot.isEmpty()) {
					targetSlot.setItem(item.clone());
					targetSlot.setCount(remaining);
					remaining = 0;
					break;
				}
			}
		}

		// Update source
		if (remaining == 0) {
			sourceSlot.setEmpty();
		} else {
			sourceSlot.setCount(remaining);
		}
	}

	/**
	 * Quick transfer: player inventory → furnace (tries input first, then fuel)
	 */
	private void quickTransferToFurnace(mc.sayda.mcraze.item.InventoryItem sourceSlot,
			mc.sayda.mcraze.world.storage.FurnaceData furnaceData) {
		if (sourceSlot.isEmpty())
			return;

		// Try input slot first, then fuel slot
		mc.sayda.mcraze.item.InventoryItem inputSlot = furnaceData.getInputSlot();
		mc.sayda.mcraze.item.InventoryItem fuelSlot = furnaceData.getFuelSlot();

		if (inputSlot.isEmpty()) {
			// Move to input slot
			inputSlot.setItem(sourceSlot.getItem().clone());
			inputSlot.setCount(sourceSlot.getCount());
			sourceSlot.setEmpty();
		} else if (inputSlot.getItem().itemId.equals(sourceSlot.getItem().itemId)) {
			// Merge with input
			int leftover = inputSlot.add(sourceSlot.getItem(), sourceSlot.getCount());
			if (leftover == 0) {
				sourceSlot.setEmpty();
			} else {
				sourceSlot.setCount(leftover);
			}
		} else if (fuelSlot.isEmpty()) {
			// Try fuel slot
			fuelSlot.setItem(sourceSlot.getItem().clone());
			fuelSlot.setCount(sourceSlot.getCount());
			sourceSlot.setEmpty();
		} else if (fuelSlot.getItem().itemId.equals(sourceSlot.getItem().itemId)) {
			// Merge with fuel
			int leftover = fuelSlot.add(sourceSlot.getItem(), sourceSlot.getCount());
			if (leftover == 0) {
				sourceSlot.setEmpty();
			} else {
				sourceSlot.setCount(leftover);
			}
		}
	}

	/**
	 * Quick transfer: storage → hotbar
	 */
	private void quickTransferToHotbar(mc.sayda.mcraze.item.InventoryItem sourceSlot,
			mc.sayda.mcraze.player.Inventory playerInv) {
		if (sourceSlot.isEmpty())
			return;

		int remaining = sourceSlot.getCount();
		mc.sayda.mcraze.item.Item item = sourceSlot.getItem();

		// Phase 1: Merge with existing stacks in hotbar (row 6)
		for (int col = 0; col < 10 && remaining > 0; col++) {
			mc.sayda.mcraze.item.InventoryItem targetSlot = playerInv.inventoryItems[col][6];
			if (!targetSlot.isEmpty() && targetSlot.getItem().itemId.equals(item.itemId)) {
				int added = targetSlot.add(item, remaining);
				remaining -= (remaining - added);
			}
		}

		// Phase 2: Fill empty hotbar slots
		for (int col = 0; col < 10 && remaining > 0; col++) {
			mc.sayda.mcraze.item.InventoryItem targetSlot = playerInv.inventoryItems[col][6];
			if (targetSlot.isEmpty()) {
				targetSlot.setItem(item.clone());
				targetSlot.setCount(remaining);
				remaining = 0;
				break;
			}
		}

		// Update source
		if (remaining == 0) {
			sourceSlot.setEmpty();
		} else {
			sourceSlot.setCount(remaining);
		}
	}

	/**
	 * Quick transfer: hotbar → storage
	 */
	private void quickTransferToStorage(mc.sayda.mcraze.item.InventoryItem sourceSlot,
			mc.sayda.mcraze.player.Inventory playerInv) {
		if (sourceSlot.isEmpty())
			return;

		int remaining = sourceSlot.getCount();
		mc.sayda.mcraze.item.Item item = sourceSlot.getItem();

		// Phase 1: Merge with existing stacks in storage (rows 3-5)
		for (int row = 3; row <= 5 && remaining > 0; row++) {
			for (int col = 0; col < 10 && remaining > 0; col++) {
				mc.sayda.mcraze.item.InventoryItem targetSlot = playerInv.inventoryItems[col][row];
				if (!targetSlot.isEmpty() && targetSlot.getItem().itemId.equals(item.itemId)) {
					int added = targetSlot.add(item, remaining);
					remaining -= (remaining - added);
				}
			}
		}

		// Phase 2: Fill empty storage slots
		for (int row = 3; row <= 5 && remaining > 0; row++) {
			for (int col = 0; col < 10 && remaining > 0; col++) {
				mc.sayda.mcraze.item.InventoryItem targetSlot = playerInv.inventoryItems[col][row];
				if (targetSlot.isEmpty()) {
					targetSlot.setItem(item.clone());
					targetSlot.setCount(remaining);
					remaining = 0;
					break;
				}
			}
		}

		// Update source
		if (remaining == 0) {
			sourceSlot.setEmpty();
		} else {
			sourceSlot.setCount(remaining);
		}
	}

	private void handleEquipmentInteraction(PlayerConnection playerConnection,
			mc.sayda.mcraze.player.Inventory inv, int slotIdx, boolean rightClick) {
		if (slotIdx < 0 || slotIdx >= inv.equipment.length)
			return;

		mc.sayda.mcraze.item.InventoryItem equipSlot = inv.equipment[slotIdx];
		mc.sayda.mcraze.item.InventoryItem holding = inv.holding;

		// Basic Swap
		mc.sayda.mcraze.item.Item tempItem = equipSlot.getItem();
		int tempCount = equipSlot.getCount();

		// Move holding -> equipment
		equipSlot.setItem(holding.getItem());
		equipSlot.setCount(holding.getCount()); // Usually 1 for equipment

		// Move old equipment -> holding
		holding.setItem(tempItem);
		holding.setCount(tempCount);

		if (equipSlot.getCount() <= 0)
			equipSlot.setEmpty();
		if (holding.getCount() <= 0)
			holding.setEmpty();

		// Broadcast update
		broadcastInventoryUpdates();
	}

	private void handleInventorySlotInteraction(mc.sayda.mcraze.player.Inventory inv, int slotX, int slotY,
			boolean rightClick, GameLogger logger) {

		// SECURITY: Validate crafting slot visibility (prevent clicking invisible 3x3
		// slots in 2x2 mode)
		if (slotY < 3) {
			// Clicking in crafting area - validate based on tableSizeAvailable
			int craftingStartCol = 10 - inv.tableSizeAvailable;
			if (slotX < craftingStartCol || slotY >= inv.tableSizeAvailable) {
				// Trying to click invisible crafting slot - reject silently
				if (logger != null) {
					logger.warn("Rejected click on invisible crafting slot (" + slotX + "," + slotY +
							") in " + inv.tableSizeAvailable + "x" + inv.tableSizeAvailable + " mode");
				}
				return;
			}
		}

		int maxCount = 64;
		mc.sayda.mcraze.item.InventoryItem slot = inv.inventoryItems[slotX][slotY];

		if (inv.holding.isEmpty()) {
			// Picking up from slot
			if (!rightClick || slot.getCount() <= 1) {
				// Left click or single item: pick up entire stack
				inv.holding.setItem(slot.getItem());
				inv.holding.setCount(slot.getCount());
				slot.setEmpty();
				if (logger != null && logger.isDebugEnabled()) {
					logger.debug("Picked up entire stack");
				}
			} else {
				// Right click with multiple items: split stack
				inv.holding.setItem(slot.getItem());
				inv.holding.setCount((int) Math.ceil((double) slot.getCount() / 2));
				slot.setCount((int) Math.floor((double) slot.getCount() / 2));
				if (logger != null && logger.isDebugEnabled()) {
					logger.debug("Split stack - holding " + inv.holding.getCount() + ", slot has " + slot.getCount());
				}
			}
		} else if (slot.isEmpty()) {
			// Placing into empty slot
			if (rightClick) {
				// FIXED: Right click: place one item
				slot.setItem(inv.holding.getItem());
				slot.setCount(1);
				inv.holding.remove(1);
			} else {
				// FIXED: Left click: place entire stack
				slot.setItem(inv.holding.getItem());
				slot.setCount(inv.holding.getCount());
				inv.holding.setEmpty();
			}
		} else if (inv.holding.getItem().itemId.equals(slot.getItem().itemId) && slot.getCount() < maxCount) {
			// Merging stacks of same item
			boolean isTool = (inv.holding.getItem() instanceof mc.sayda.mcraze.item.Tool) ||
					(slot.getItem() instanceof mc.sayda.mcraze.item.Tool);
			if (!isTool) {
				if (rightClick) {
					// FIXED: Right click: add one item
					slot.setCount(slot.getCount() + 1);
					inv.holding.remove(1);
				} else {
					// FIXED: Left click: merge as much as possible
					int spaceInSlot = maxCount - slot.getCount();
					int toTransfer = Math.min(spaceInSlot, inv.holding.getCount());
					slot.setCount(slot.getCount() + toTransfer);
					inv.holding.remove(toTransfer);
				}
			} else {
				// Tool case: Merge not possible, so swap instead (for better UX)
				// This matches how Minecraft handles "stacking" non-stackables by swapping
				Item tempItem = inv.holding.getItem();
				int tempCount = inv.holding.count;
				inv.holding.setItem(slot.getItem());
				inv.holding.setCount(slot.getCount());
				slot.setItem(tempItem);
				slot.setCount(tempCount);
			}
		} else {
			// Different items: swap
			Item tempItem = inv.holding.getItem();
			int tempCount = inv.holding.getCount();
			inv.holding.setItem(slot.getItem());
			inv.holding.setCount(slot.getCount());
			slot.setItem(tempItem);
			slot.setCount(tempCount);
		}
	}

	/**
	 * Handle item tossing from a player (server-authoritative)
	 */
	public void handleItemToss(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketItemToss packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot toss items
		if (player.dead) {
			return;
		}

		logger.info("Player " + playerConnection.getPlayerName() + " tossing item");

		// Use the existing tossSelectedItem logic from Player
		mc.sayda.mcraze.item.Item tossedItem = player.tossSelectedItem(random);
		if (tossedItem != null) {
			// Add item entity to world
			addEntity(tossedItem);
			logger.info("  Tossed " + tossedItem.itemId);

			// Broadcast inventory update (item was removed from player inventory)
			broadcastInventoryUpdates();
		}
	}

	/**
	 * Handle entity attack from a player (server-authoritative)
	 * Applies damage from player's held item to the target entity
	 */

	/**
	 * Handle entity interaction from a player (server-authoritative)
	 * For interactions like wolf taming.
	 */
	public void handleEntityInteract(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketEntityInteract packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.dead) {
			return; // Dead players can't interact
		}

		mc.sayda.mcraze.entity.Entity target = entityManager.findByUUID(packet.entityUUID);
		if (target == null) {
			return;
		}

		// RANGE VALIDATION: Prevent infinite reach
		float maxReach = mc.sayda.mcraze.Constants.PLAYER_REACH;
		float dx = player.x - target.x;
		float dy = player.y - target.y;
		float distSq = dx * dx + dy * dy;

		if (distSq > maxReach * maxReach) {
			return;
		}

		// DRUID: Beast Tamer Passive (Taming Wolves)
		if (target instanceof mc.sayda.mcraze.entity.mob.EntityWolf) {
			mc.sayda.mcraze.entity.mob.EntityWolf wolf = (mc.sayda.mcraze.entity.mob.EntityWolf) target;

			// Check if player is holding a bone
			mc.sayda.mcraze.item.Item held = player.inventory.selectedItem().getItem();
			if (held != null && "bone".equals(held.itemId)) {
				// Must have TAME_WOLVES passive (Beast Tamer path)
				boolean hasTamingSkill = player.unlockedPassives
						.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.TAME_WOLVES);

				if (hasTamingSkill && !wolf.isTamed()) {
					// Tame the wolf!
					wolf.setOwner(player.getUUID());
					// [NEW] Beast Tamer HP Boost
					wolf.maxHP = 30;
					wolf.heal(10);
					player.inventory.selectedItem().remove(1);
					broadcastInventoryUpdates();

					// Feedback
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Wolf tamed! It will now defend you.",
							new mc.sayda.mcraze.graphics.Color(100, 255, 100)));

					// Sync wolf taming state if necessary (usually handled by EntityUpdate)
					if (logger != null) {
						logger.info("Player " + player.username + " tamed a wolf.");
					}
				} else if (!hasTamingSkill) {
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"You need the 'Tame Wolves' skill to tame wild animals!",
							new mc.sayda.mcraze.graphics.Color(255, 100, 100)));
				}
			}
		}
	}

	// Unified entity damage handling with attacker tracking
	public void damageEntity(mc.sayda.mcraze.entity.LivingEntity victim, mc.sayda.mcraze.entity.Entity attacker,
			int amount, mc.sayda.mcraze.entity.DamageType type) {
		victim.takeDamage(amount, type, attacker);

		// If a player is attacked, alert their wolves
		if (victim instanceof Player && attacker != null) {
			alertWolvesOfTarget((Player) victim, attacker);
		}
	}

	/**
	 * Alert all tamed wolves of a specific owner to attack a target.
	 */
	public void alertWolvesOfTarget(Player owner, mc.sayda.mcraze.entity.Entity target) {
		if (owner == null || target == null || target.dead)
			return;

		for (mc.sayda.mcraze.entity.Entity e : entityManager.getAll()) {
			if (e instanceof mc.sayda.mcraze.entity.mob.EntityWolf) {
				mc.sayda.mcraze.entity.mob.EntityWolf wolf = (mc.sayda.mcraze.entity.mob.EntityWolf) e;
				if (wolf.isTamed() && owner.getUUID().equals(wolf.getOwner())) {
					wolf.alertTarget(target);
				}
			}
		}
	}

	public void handleEntityAttack(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketEntityAttack packet) {
		Player attacker = playerConnection.getPlayer();
		if (attacker == null || attacker.dead) {
			return; // Dead players can't attack
		}
		if (logger != null && logger.isDebugEnabled())
			logger.debug("handleEntityAttack: " + attacker.username + " attacking UUID " + packet.entityUUID);

		// Find the target entity by UUID (stable tracking across disconnects/list
		// shifts)
		mc.sayda.mcraze.entity.Entity target = entityManager.findByUUID(packet.entityUUID);

		if (target == null) {
			// Suppress warning - this is common when aiming at a dying mob
			logger.debug("Player " + playerConnection.getPlayerName()
					+ " tried to attack non-existent entity with UUID " + packet.entityUUID);
			return;
		}

		// RANGE VALIDATION: Prevent infinite reach
		float maxReach = mc.sayda.mcraze.Constants.PLAYER_REACH;
		float dx = attacker.x - target.x;
		float dy = attacker.y - target.y;
		float distSq = dx * dx + dy * dy;

		if (distSq > maxReach * maxReach) {
			logger.warn("Player " + playerConnection.getPlayerName()
					+ " attack rejected: target too far (dist^2=" + distSq + ", max^2=" + (maxReach * maxReach) + ")");
			return;
		}

		// SELF-ATTACK RESTRICTION: Do not allow attacking yourself
		if (attacker.getUUID() != null && target.getUUID() != null &&
				attacker.getUUID().equals(target.getUUID())) {
			logger.debug("Player " + playerConnection.getPlayerName() + " attack rejected: self-attack");
			return;
		}

		// Only living entities can take damage
		if (!(target instanceof mc.sayda.mcraze.entity.LivingEntity)) {
			return;
		}

		mc.sayda.mcraze.entity.LivingEntity livingTarget = (mc.sayda.mcraze.entity.LivingEntity) target;

		// PvP Check
		if (livingTarget instanceof Player) {
			if (logger != null && logger.isDebugEnabled())
				logger.debug("PvP Check: Target is Player. World PvP: " + world.pvp);
			if (!world.pvp) {
				if (logger != null && logger.isDebugEnabled())
					logger.debug("PvP Attack blocked: PvP is disabled in world");
				return; // PvP disabled
			}
		}

		// INVULNERABILITY CHECK: Respect damage cooldown
		if (livingTarget.invulnerabilityTicks > 0) {
			logger.debug("Player " + playerConnection.getPlayerName()
					+ " attack rejected: target invulnerable (" + livingTarget.invulnerabilityTicks + " ticks left)");
			return;
		}

		// Calculate damage from held item
		int damage = 1; // Default punch damage
		mc.sayda.mcraze.item.InventoryItem heldItem = attacker.inventory.selectedItem();
		if (heldItem != null && !heldItem.isEmpty() && heldItem.getItem() instanceof mc.sayda.mcraze.item.Tool) {
			mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) heldItem.getItem();
			damage = tool.attackDamage;

			// Apply Class Melee Damage Multiplier
			if (attacker.classStats != null) {
				damage = (int) (damage * attacker.classStats.getMeleeDamageMultiplier());

				// BLOOD_RAGE: +2% damage per 10% HP missing (Vanguard passive)
				if (attacker.classStats.provider instanceof mc.sayda.mcraze.player.specialization.VanguardStats) {
					mc.sayda.mcraze.player.specialization.VanguardStats vanguard = (mc.sayda.mcraze.player.specialization.VanguardStats) attacker.classStats.provider;
					float bloodRageBonus = vanguard.getBloodRageBonus(attacker.hitPoints, attacker.getMaxHP());
					if (bloodRageBonus > 0) {
						damage = (int) (damage * (1.0f + bloodRageBonus));
					}
				}
			}

			// Damage tool durability
			tool.uses++;
			if (tool.uses >= tool.totalUses) {
				// Tool broke
				heldItem.setEmpty();
				broadcastInventoryUpdates();
				broadcastSound("break.wav", target.x, target.y);
			}
		}

		// Apply Battle Frenzy (Add Buff)
		if (attacker instanceof mc.sayda.mcraze.player.Player)

		{
			mc.sayda.mcraze.player.Player p = (mc.sayda.mcraze.player.Player) attacker;
			if (p.unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.BATTLE_FRENZY)) {
				// Find existing Frenzy buff
				mc.sayda.mcraze.entity.buff.Buff frenzy = null;
				for (mc.sayda.mcraze.entity.buff.Buff b : p.activeBuffs) {
					if (b.getType() == mc.sayda.mcraze.entity.buff.BuffType.FRENZY) {
						frenzy = b;
						break;
					}
				}

				if (frenzy != null) {
					// Stack up to 3x (Amplifier 2)
					if (frenzy.getAmplifier() < 2) {
						frenzy.setAmplifier(frenzy.getAmplifier() + 1);
					}
					frenzy.setDuration(100); // Reset duration to 5s
				} else {
					p.addBuff(
							new mc.sayda.mcraze.entity.buff.Buff(mc.sayda.mcraze.entity.buff.BuffType.FRENZY, 100, 0));
				}
			}
		}

		// Apply Buff Modifiers (Strength / Weakness / Frenzy)
		int strengthLevel = attacker.getBuffAmplifier(mc.sayda.mcraze.entity.buff.BuffType.STRENGTH);
		if (strengthLevel >= 0) {
			damage += 3 * (strengthLevel + 1);
		}

		int weaknessLevel = attacker.getBuffAmplifier(mc.sayda.mcraze.entity.buff.BuffType.WEAKNESS);
		if (weaknessLevel >= 0) {
			damage -= 3 * (weaknessLevel + 1);
		}

		int frenzyLevel = attacker.getBuffAmplifier(mc.sayda.mcraze.entity.buff.BuffType.FRENZY);
		if (frenzyLevel >= 0) {
			// +5% damage per stack (min +1)
			int bonus = (int) Math.max(frenzyLevel + 1, damage * 0.05f * (frenzyLevel + 1));
			damage += bonus;
		}

		if (attacker.hasBuff(mc.sayda.mcraze.entity.buff.BuffType.WELL_FED)) {
			int bonus = (int) Math.max(1, damage * 0.10f); // +10% Damage
			damage += bonus;
		}

		if (damage < 1)
			damage = 1; // Minimum 1 damage

		// Apply damage to target
		if (logger != null && logger.isDebugEnabled())
			logger.debug("Applying " + damage + " damage to " + livingTarget.getClass().getSimpleName());

		// Use damageEntity to track attacker and alert wolves if needed
		damageEntity(livingTarget, attacker, damage, mc.sayda.mcraze.entity.DamageType.PHYSICAL);

		// Also alert wolves to help attack the player's target
		if (attacker instanceof Player) {
			alertWolvesOfTarget((Player) attacker, livingTarget);
		}

		// Play hit sound
		broadcastSound("hit.wav", target.x, target.y);

		// Apply knockback to target
		livingTarget.applyKnockback(attacker.x, 0.4f);

		// Broadcast entity update to show health change
		broadcastEntityUpdate();

		// Check if target died
		if (livingTarget.dead) {
			handleEntityDeath(livingTarget, attacker);
		}
	}

	/**
	 * Helper to spawn dropped items with random velocity
	 */
	private void dropItem(String itemId, float x, float y, int count) {
		mc.sayda.mcraze.item.Item template = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
		if (template != null) {
			for (int i = 0; i < count; i++) {
				mc.sayda.mcraze.item.Item drop = template.clone();
				drop.x = x;
				drop.y = y;
				drop.dx = (float) ((Math.random() - 0.5) * 0.05f);
				drop.dy = -0.1f - (float) (Math.random() * 0.05f);
				addEntity(drop);
			}
		}
	}

	/**
	 * Respawn a player and broadcast to all clients
	 * Called when a player presses R after death
	 */
	public void respawnPlayer(PlayerConnection playerConn) {
		Player player = playerConn.getPlayer();

		if (player == null || !player.dead) {
			if (logger != null) {
				logger.warn("SharedWorld.respawnPlayer: Cannot respawn " + playerConn.getPlayerName() +
						" (player null: " + (player == null) + ", dead: " + (player != null && player.dead) + ")");
			}
			return;
		}

		// Get spawn coordinates (Player specific or World default)
		float spawnX;
		float spawnY;

		if (player.spawnX != -1 && player.spawnY != -1) {
			spawnX = player.spawnX;
			spawnY = player.spawnY - 1.75f;
		} else {
			spawnX = world.spawnLocation.x;
			spawnY = world.spawnLocation.y - 1.75f;
		}

		if (logger != null) {
			logger.info("SharedWorld.respawnPlayer: Respawning " + playerConn.getPlayerName() +
					" at (" + spawnX + ", " + spawnY + ")");
		}

		// Respawn the player on the server
		player.respawn(spawnX, spawnY);

		// Broadcast immediate respawn state to all clients
		mc.sayda.mcraze.network.packet.PacketPlayerRespawn respawnPacket = new mc.sayda.mcraze.network.packet.PacketPlayerRespawn(
				player.getUUID());
		broadcastPacket(respawnPacket);

		if (logger != null) {
			logger.info("SharedWorld.respawnPlayer: Broadcast respawn state for " + playerConn.getPlayerName());
		}

		// PERFORMANCE: Commented out console logging (use logger instead)
		// System.out.println("Player " + playerConn.getPlayerName() + " respawned at
		// spawn point");
	}

	// Getters
	public World getWorld() {
		return world;
	}

	public List<PlayerConnection> getPlayers() {
		return players;
	}

	public mc.sayda.mcraze.survival.WaveManager getWaveManager() {
		return waveManager;
	}

	public MobSpawner getMobSpawner() {
		return mobSpawner;
	}

	private void handleEntityDeath(mc.sayda.mcraze.entity.LivingEntity entity, mc.sayda.mcraze.entity.Entity attacker) {
		logger.info(entity.getClass().getSimpleName() + " died from attack");
		if (logger != null && logger.isDebugEnabled())
			logger.debug("Entity died. UUID: " + entity.getUUID());

		if (entity instanceof mc.sayda.mcraze.player.Player) {
			mc.sayda.mcraze.player.Player deadPlayer = (mc.sayda.mcraze.player.Player) entity;
			if (logger != null)
				logger.info("Player died: " + deadPlayer.username);

			// 1. Drop Items (Server Side) - Respect keepInventory gamerule
			if (!world.keepInventory) {
				java.util.ArrayList<mc.sayda.mcraze.item.Item> dropped = deadPlayer.dropAllItems(random);
				if (dropped != null) {
					for (mc.sayda.mcraze.item.Item item : dropped) {
						addEntity(item);
					}
				}
				broadcastInventoryUpdates(); // Sync empty inventory
			} else {
				if (logger != null && logger.isDebugEnabled())
					logger.debug("keepInventory is ON. Items prevented from dropping.");
			}

			// 2. Find Connection and Send Death Packet (Unicast)
			for (PlayerConnection pc : players) {
				if (pc.getPlayer() != null && pc.getPlayer().getUUID().equals(deadPlayer.getUUID())) {
					if (logger != null && logger.isDebugEnabled())
						logger.debug("Sending PacketPlayerDeath to " + pc.getPlayerName());
					pc.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketPlayerDeath());
					break;
				}
			}
		} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntitySheep) {
			int count = 1 + (int) (Math.random() * 3);
			dropItem("white_wool", entity.x, entity.y, count);
		} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityPig) {
			int count = 1 + (int) (Math.random() * 2);
			dropItem("pork", entity.x, entity.y, count);
		} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityCow) {
			int count = 1 + (int) (Math.random() * 2);
			dropItem("beef", entity.x, entity.y, count);
			dropItem("leather", entity.x, entity.y, count);
		} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityZombie) {
			if (Math.random() < 0.2f) {
				dropItem("iron", entity.x, entity.y, 1);
			}
			if (Math.random() < 0.5f) {
				int boneCount = 1 + (int) (Math.random() * 2);
				dropItem("bone", entity.x, entity.y, boneCount);
			}
			int count = 1 + (int) (Math.random() * 2);
			dropItem("rotten_flesh", entity.x, entity.y, count);
		}

		// MAGE: Essence Drop System
		// Drop depends on killer (Arcanist/Druid) OR nearby Mage with ESSENCE_COLLECTOR
		Player killer = (attacker instanceof Player) ? (Player) attacker : null;
		boolean killerIsMage = killer != null
				&& (killer.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.ARCANIST
						|| killer.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.DRUID);

		Player nearbySupportMage = null;
		float rangeSq = 10 * 10 * mc.sayda.mcraze.Constants.TILE_SIZE * mc.sayda.mcraze.Constants.TILE_SIZE;

		for (mc.sayda.mcraze.server.PlayerConnection pc : players) {
			Player p = pc.getPlayer();
			if (p != null && !p.dead
					&& p.unlockedPassives
							.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.ESSENCE_COLLECTOR)) {
				// We only look for a support mage if the killer isn't a mage, or if we want to
				// boost drops
				float distSq = (p.x - entity.x) * (p.x - entity.x) + (p.y - entity.y) * (p.y - entity.y);
				if (distSq < rangeSq) {
					nearbySupportMage = p;
					break;
				}
			}
		}

		if (killerIsMage || nearbySupportMage != null) {
			float dropChance = killerIsMage ? 1.0f : 0.5f; // Mage killers always drop, teammates 50%
			int baseValue = 5 + (int) (Math.random() * 6); // 5-10 essence

			// Boost if someone has collector nearby (including the killer themselves)
			if (nearbySupportMage != null || (killerIsMage && killer.unlockedPassives
					.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.ESSENCE_COLLECTOR))) {
				baseValue *= 1.5; // 50% more essence
				dropChance = 1.0f; // collector makes it 100%
			}

			if (Math.random() < dropChance) {
				addEntity(new mc.sayda.mcraze.entity.EntityEssence(entity.x, entity.y, baseValue));
			}
		}

		// Remove entity
		entityManager.remove(entity);

		// Broadcast removal
		mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket = new mc.sayda.mcraze.network.packet.PacketEntityRemove(
				entity.getUUID());
		broadcastPacket(removePacket);
	}

	public long getTime() {
		if (world != null) {
			return world.getTicksAlive();
		}
		return 0;
	}

	public void broadcastSkillpointReward(int amount) {
		for (PlayerConnection pc : players) {
			Player p = pc.getPlayer();
			if (p != null) {
				p.addSkillPoint(amount);
				pc.getConnection()
						.sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
								"§aYou have been awarded " + amount + " Skillpoint for surviving 7 days!",
								new mc.sayda.mcraze.graphics.Color(50, 255, 50)));
			}
		}
	}

	/**
	 * Add an entity to the world (e.g., dropped items, tossed items)
	 * Thread-safe using EntityManager
	 */
	public void addEntity(Entity entity) {
		if (entity != null) {
			entityManager.add(entity);
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void stop() {
		running = false;
	}

	/**
	 * Set command handler for integrated server (singleplayer commands)
	 */
	public void setCommandHandler(mc.sayda.mcraze.ui.CommandHandler commandHandler) {
		this.commandHandler = commandHandler;
	}

	/**
	 * Get command handler (for PlayerConnection to execute commands)
	 */
	public mc.sayda.mcraze.ui.CommandHandler getCommandHandler() {
		return commandHandler;
	}

	public int getPlayerCount() {
		return players.size();
	}

	/**
	 * Get thread-safe EntityManager for rendering and iteration
	 */
	public EntityManager getEntityManager() {
		return entityManager;
	}

	/**
	 * Get thread-safe WorldAccess for tile access
	 */
	public WorldAccess getWorldAccess() {
		return worldAccess;
	}

	/**
	 * Finalize block breaking (called by handleBlockChange or tick)
	 */
	private void finishBreakingBlock(Player player, int x, int y) {
		BreakingState breakingState = getBreakingState(player);

		// Reset state first
		breakingState.reset();

		// Notify client to stop animation
		for (PlayerConnection pc : players) {
			if (pc.getPlayer() == player) {
				pc.getConnection().sendPacket(new PacketBreakingProgress(x, y, 0, 0));
				break;
			}
		}

		// Handle tool durability
		Item currentItem = player.inventory.selectedItem().getItem();
		if (currentItem != null && currentItem instanceof Tool) {
			Tool tool = (Tool) currentItem;
			tool.uses++;
			if (tool.uses >= tool.totalUses) {
				player.inventory.selectedItem().setEmpty();
				broadcastInventoryUpdates();
			}
		}

		// Special handling for backdrop mode
		if (player.backdropPlacementMode) {
			if (world.hasBackdrop(x, y)) {
				Constants.TileID backdropId = world.removeBackdrop(x, y);
				if (backdropId != null && backdropId.itemDropId != null) {
					Item droppedItem = Constants.itemTypes.get(backdropId.itemDropId);
					if (droppedItem != null) {
						Item item = droppedItem.clone();
						item.x = x + random.nextFloat() * 0.5f;
						item.y = y + random.nextFloat() * 0.5f;
						item.dy = -0.07f;
						entityManager.add(item);
					}
				}
				broadcastBackdropChange(x, y, null);
			}
			return;
		}

		Tile tile = world.getTile(x, y);
		if (tile != null && tile.type != null && tile.type.name == Constants.TileID.TNT) {
			// Remove block
			world.removeTile(x, y);
			broadcastBlockChange(x, y, null);

			// Spawn Primed TNT
			mc.sayda.mcraze.entity.item.EntityPrimedTNT tnt = new mc.sayda.mcraze.entity.item.EntityPrimedTNT(x, y);
			tnt.dx = (random.nextFloat() - 0.5f) * 0.1f;
			tnt.dy = -0.1f + (random.nextFloat() * 0.1f);
			addEntity(tnt);

			// Sound
			broadcastSound("fuse.wav", x, y);
			return;
		}

		// Remove the tile
		Constants.TileID broken = world.removeTile(x, y);

		// Handle pot breaking
		if (broken == Constants.TileID.FLOWER_POT) {
			mc.sayda.mcraze.world.storage.PotData potData = world.getPot(x, y);
			if (potData != null && potData.plantId != null) {
				dropItem(potData.plantId, x, y, 1);
			}
			world.removePot(x, y);
		}

		// Handle chest breaking
		if (broken == Constants.TileID.CHEST) {
			mc.sayda.mcraze.world.storage.ChestData chestData = world.getChest(x, y);
			if (chestData != null) {
				for (int cx = 0; cx < 10; cx++) { // FIXED: 10 slots wide
					for (int cy = 0; cy < 3; cy++) {
						mc.sayda.mcraze.item.InventoryItem invItem = chestData.items[cx][cy];
						if (invItem != null && !invItem.isEmpty()) {
							for (int i = 0; i < invItem.getCount(); i++) {
								Item droppedItem = invItem.getItem().clone();
								droppedItem.x = x + random.nextFloat() * 0.8f + 0.1f;
								droppedItem.y = y + random.nextFloat() * 0.8f + 0.1f;
								droppedItem.dy = -0.05f - random.nextFloat() * 0.05f;
								entityManager.add(droppedItem);
							}
						}
					}
				}
				world.removeChest(x, y);
				if (logger != null) {
					logger.debug("SharedWorld: Chest broken at (" + x + ", " + y + "), items dropped");
				}
			}
		}

		// Handle furnace breaking
		if (broken == Constants.TileID.FURNACE || broken == Constants.TileID.FURNACE_LIT) {
			mc.sayda.mcraze.world.storage.FurnaceData furnaceData = world.getFurnace(x, y);
			if (furnaceData != null) {
				for (mc.sayda.mcraze.item.InventoryItem[] row : furnaceData.items) {
					for (mc.sayda.mcraze.item.InventoryItem invItem : row) {
						if (invItem != null && !invItem.isEmpty()) {
							for (int i = 0; i < invItem.getCount(); i++) {
								Item droppedItem = invItem.getItem().clone();
								droppedItem.x = x + random.nextFloat() * 0.8f + 0.1f;
								droppedItem.y = y + random.nextFloat() * 0.8f + 0.1f;
								droppedItem.dy = -0.05f - random.nextFloat() * 0.05f;
								entityManager.add(droppedItem);
							}
						}
					}
				}
				world.removeFurnace(x, y);
				if (logger != null) {
					logger.debug("SharedWorld: Furnace broken at (" + x + ", " + y + "), items dropped");
				}
			}
		}

		// Handle alchemy table breaking
		if (broken == Constants.TileID.ALCHEMY_TABLE) {
			mc.sayda.mcraze.world.storage.AlchemyData alchemyData = world.getAlchemy(x, y);
			if (alchemyData != null) {
				for (mc.sayda.mcraze.item.InventoryItem invItem : alchemyData.items) {
					if (invItem != null && !invItem.isEmpty()) {
						for (int i = 0; i < invItem.getCount(); i++) {
							Item droppedItem = invItem.getItem().clone();
							droppedItem.x = x + random.nextFloat() * 0.8f + 0.1f;
							droppedItem.y = y + random.nextFloat() * 0.8f + 0.1f;
							droppedItem.dy = -0.05f - random.nextFloat() * 0.05f;
							entityManager.add(droppedItem);
						}
					}
				}
				world.removeAlchemy(x, y);
				if (logger != null) {
					logger.debug("SharedWorld: Alchemy Table broken at (" + x + ", " + y + "), items dropped");
				}
			}
		}

		// Door Breaking (Bot)
		if (broken == Constants.TileID.DOOR_BOT_CLOSED || broken == Constants.TileID.DOOR_BOT) {
			int topY = y - 1;
			if (topY >= 0) {
				Tile topTile = worldAccess.getTile(x, topY);
				if (topTile != null && topTile.type != null &&
						(topTile.type.name == Constants.TileID.DOOR_TOP_CLOSED
								|| topTile.type.name == Constants.TileID.DOOR_TOP)) {
					world.removeTile(x, topY);
					broadcastBlockChange(x, topY, null);
				}
			}
		} else if (broken == Constants.TileID.DOOR_TOP_CLOSED || broken == Constants.TileID.DOOR_TOP) {
			int botY = y + 1;
			if (botY < world.height) {
				Tile botTile = worldAccess.getTile(x, botY);
				if (botTile != null && botTile.type != null &&
						(botTile.type.name == Constants.TileID.DOOR_BOT_CLOSED
								|| botTile.type.name == Constants.TileID.DOOR_BOT)) {
					world.removeTile(x, botY);
					broadcastBlockChange(x, botY, null);
				}
			}
		}

		// Bed Breaking
		if (broken == Constants.TileID.BED_LEFT) {
			int rightX = x + 1;
			if (rightX < world.width) {
				Tile rightTile = worldAccess.getTile(rightX, y);
				if (rightTile != null && rightTile.type != null &&
						rightTile.type.name == Constants.TileID.BED_RIGHT) {
					world.removeTile(rightX, y);
					broadcastBlockChange(rightX, y, null);
				}
			}
		} else if (broken == Constants.TileID.BED_RIGHT) {
			int leftX = x - 1;
			if (leftX >= 0) {
				Tile leftTile = worldAccess.getTile(leftX, y);
				if (leftTile != null && leftTile.type != null &&
						leftTile.type.name == Constants.TileID.BED_LEFT) {
					world.removeTile(leftX, y);
					broadcastBlockChange(leftX, y, null);
				}
			}
		}

		// Item Drops
		Constants.TileID dropId = broken;
		if (broken == Constants.TileID.GRASS)
			dropId = Constants.TileID.DIRT;

		if (broken == Constants.TileID.STONE)
			dropId = Constants.TileID.COBBLE;

		if (broken == Constants.TileID.COAL_ORE) {
			Item coal = Constants.itemTypes.get("coal").clone();
			coal.x = x + random.nextFloat() * 0.5f;
			coal.y = y + random.nextFloat() * 0.5f;
			coal.dy = -0.07f;
			entityManager.add(coal);
		} else if (broken == Constants.TileID.DIAMOND_ORE) {
			Item diamond = Constants.itemTypes.get("diamond").clone();
			diamond.x = x + random.nextFloat() * 0.5f;
			diamond.y = y + random.nextFloat() * 0.5f;
			diamond.dy = -0.07f;
			entityManager.add(diamond);
		} else if (broken == Constants.TileID.RUBY_ORE) {
			Item ruby = Constants.itemTypes.get("ruby").clone();
			ruby.x = x + random.nextFloat() * 0.5f;
			ruby.y = y + random.nextFloat() * 0.5f;
			ruby.dy = -0.07f;
			entityManager.add(ruby);
		} else if (broken == Constants.TileID.LAPIS_ORE) {
			Item lapis = Constants.itemTypes.get("lapis").clone();
			lapis.x = x + random.nextFloat() * 0.5f;
			lapis.y = y + random.nextFloat() * 0.5f;
			lapis.dy = -0.07f;
			entityManager.add(lapis);
		}

		if (broken == Constants.TileID.LEAVES) {
			// FOREST_HARVEST: Higher drop rates + golden apple chance
			boolean hasForestHarvest = player != null && player.unlockedPassives
					.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.FOREST_HARVEST);
			double saplingChance = hasForestHarvest ? 0.25 : 0.1; // 25% vs 10%
			double appleChance = hasForestHarvest ? 0.12 : 0.05; // 12% vs 5%
			double goldenAppleChance = hasForestHarvest ? 0.02 : 0.0; // 2% vs 0%

			if (random.nextDouble() < saplingChance) {
				dropId = Constants.TileID.SAPLING;
			}
			// Apple drops (independent chance)
			if (random.nextDouble() < appleChance) {
				Item apple = Constants.itemTypes.get("apple").clone();
				apple.x = x + random.nextFloat() * 0.5f;
				apple.y = y + random.nextFloat() * 0.5f;
				apple.dy = -0.07f;
				entityManager.add(apple);
			}
			// Golden apple drops (FOREST_HARVEST only)
			if (random.nextDouble() < goldenAppleChance) {
				Item goldenApple = Constants.itemTypes.get("golden_apple");
				if (goldenApple != null) {
					Item ga = goldenApple.clone();
					ga.x = x + random.nextFloat() * 0.5f;
					ga.y = y + random.nextFloat() * 0.5f;
					ga.dy = -0.07f;
					entityManager.add(ga);
				}
			}
		}

		if (broken == Constants.TileID.TALL_GRASS) {
			if (random.nextDouble() < 0.75) {
				Item seedItem = Constants.itemTypes.get("wheat_seeds");
				if (seedItem != null) {
					Item seed = seedItem.clone();
					seed.x = x + random.nextFloat() * 0.5f;
					seed.y = y + random.nextFloat() * 0.5f;
					seed.dy = -0.07f;
					entityManager.add(seed);
				}
			}
		} else if (dropId != null && dropId.itemDropId != null) {
			Item droppedItem = Constants.itemTypes.get(dropId.itemDropId);
			if (droppedItem != null) {
				if (broken == Constants.TileID.WHEAT) {
					// Wheat drops (1 wheat + 1-3 seeds)
					Item wheat = droppedItem.clone();
					wheat.x = x + random.nextFloat() * 0.5f;
					wheat.y = y + random.nextFloat() * 0.5f;
					wheat.dy = -0.07f;
					entityManager.add(wheat);

					Item seedItem = Constants.itemTypes.get("wheat_seeds");
					if (seedItem != null) {
						int seedCount = 1 + random.nextInt(3);
						for (int i = 0; i < seedCount; i++) {
							Item seed = seedItem.clone();
							seed.x = x + random.nextFloat() * 0.5f;
							seed.y = y + random.nextFloat() * 0.5f;
							seed.dy = -0.07f;
							entityManager.add(seed);
						}
					}
				} else {
					// Standard drop
					Item item = droppedItem.clone();
					item.x = x + random.nextFloat() * 0.5f;
					item.y = y + random.nextFloat() * 0.5f;
					item.dy = -0.07f;
					entityManager.add(item);

					// Apply Class Extra Drops
					if (player.classStats != null) {
						// Lumber Expert: +2 extra wood when breaking trees
						if (broken == Constants.TileID.WOOD &&
								player.unlockedPassives.contains(
										mc.sayda.mcraze.player.specialization.PassiveEffectType.LUMBER_EXPERT)) {
							for (int i = 0; i < 2; i++) {
								Item extra = droppedItem.clone();
								extra.x = x + random.nextFloat() * 0.5f;
								extra.y = y + random.nextFloat() * 0.5f;
								extra.dy = -0.07f - (random.nextFloat() * 0.05f);
								entityManager.add(extra);
							}
						}

						// Bountiful Yield: +20% chance for extra crop yield (rounded up to +1 for now)
						if (broken == Constants.TileID.WHEAT &&
								player.unlockedPassives.contains(
										mc.sayda.mcraze.player.specialization.PassiveEffectType.BOUNTIFUL_YIELD)) {
							// Wheat already drops 1 wheat. Bountiful Yield adds another one 50% of the
							// time,
							// or just +1 always if simpler.
							// Design says "20% more items". Let's do 50% chance for +1.
							if (random.nextDouble() < 0.5) {
								Item extra = droppedItem.clone();
								extra.x = x + random.nextFloat() * 0.5f;
								extra.y = y + random.nextFloat() * 0.5f;
								extra.dy = -0.07f - (random.nextFloat() * 0.05f);
								entityManager.add(extra);
							}
						}
					}
				}
			}
		}

		// Broadcast removal
		broadcastBlockChange(x, y, null);

		// Plant Physics
		breakFloatingPlants(x, y);
	}

	// Helpers for container access
	private ChestData getChestAt(int x, int y) {
		if (world == null)
			return null;
		return world.getChest(x, y);
	}

	private FurnaceData getOrCreateFurnace(int x, int y) {
		if (world == null)
			return null;
		return world.getOrCreateFurnace(x, y);
	}

	/**
	 * Flag Defense Logic:
	 * - Checks if flag is placed during Night.
	 * - Applies "Curse" (Damage) if missing.
	 * - Lose condition logic (Flag destroyed) handled in break logic.
	 */
	private void checkFlagStatus() {
		if (world.isNight() && world.flagLocation == null) {
			if (ticksRunning % 100 == 0) { // Every 5 seconds
				// Warn or Damage players
				for (PlayerConnection pc : players) {
					Player p = pc.getPlayer();
					if (p != null && !p.dead) {
						p.takeDamage(1, mc.sayda.mcraze.entity.DamageType.MAGICAL); // 0.5 hearts damage (Curse)
						pc.getConnection().sendPacket(new PacketChatMessage(
								"The darkness consumes you! Place the Flag!",
								new mc.sayda.mcraze.graphics.Color(200, 0, 0)));
					}
				}
			}
		}
	}

	/**
	 * Get the Flag location (for zombie AI)
	 */
	public mc.sayda.mcraze.util.Int2 getFlagLocation() {
		return world != null ? world.flagLocation : null;
	}

	/**
	 * Allow mobs to break blocks (like the Flag)
	 */
	public void mobBreakBlock(int x, int y, mc.sayda.mcraze.entity.Entity source) {
		if (world == null)
			return;

		Tile tile = world.getTile(x, y);
		if (tile != null && tile.type != null && tile.type.name == Constants.TileID.TNT) {
			// Remove block
			world.removeTile(x, y);
			broadcastBlockChange(x, y, null);

			// Spawn Primed TNT
			mc.sayda.mcraze.entity.item.EntityPrimedTNT tnt = new mc.sayda.mcraze.entity.item.EntityPrimedTNT(x, y);
			tnt.dx = (random.nextFloat() - 0.5f) * 0.1f;
			tnt.dy = -0.1f;
			addEntity(tnt);
			broadcastSound("fuse.wav", x, y);
			return;
		}

		// Remove the tile
		Constants.TileID broken = world.removeTile(x, y);

		// Broadcast change
		broadcastBlockChange(x, y, null);

		// Handle Flag logic specifically
		if (broken == Constants.TileID.FLAG) {
			world.flagLocation = null;

			// Switch Gamemode to CLASSIC (Game Over for Horde Mode)
			world.gameMode = mc.sayda.mcraze.world.GameMode.CLASSIC;
			this.waveManager = null; // Disable wave manager effectively
			if (getMobSpawner() != null)
				getMobSpawner().setWaveMode(false);

			// Broadcast GAME OVER
			String killerName = source != null ? source.getClass().getSimpleName() : "Unknown Beast";

			logger.info("SharedWorld: Flag destroyed by " + killerName + "! Triggering Game Over.");
			broadcastPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage("GAME OVER! The Flag has fallen!",
					new mc.sayda.mcraze.graphics.Color(255, 0, 0)));
			broadcastPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage("Destroyed by: " + killerName,
					new mc.sayda.mcraze.graphics.Color(255, 50, 50)));
			broadcastPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage("The world reverts to Classic Mode.",
					new mc.sayda.mcraze.graphics.Color(255, 100, 100)));
		}

	}

	// Backwards compatibility overload
	public void mobBreakBlock(int x, int y) {
		mobBreakBlock(x, y, null);
	}

	public void broadcastWaveSync() {
		if (waveManager == null)
			return;

		mc.sayda.mcraze.network.packet.PacketWaveSync packet = new mc.sayda.mcraze.network.packet.PacketWaveSync(
				waveManager.getCurrentDay(),
				waveManager.isWaveActive(),
				waveManager.getCurrentHealthMultiplier(),
				waveManager.getCurrentDamageMultiplier(),
				waveManager.getCurrentDay());
		broadcast(packet);
	}

	public void handleAlchemyAction(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketAlchemyAction packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null || player.dead)
			return;

		mc.sayda.mcraze.world.storage.AlchemyData alchemyData = world.getOrCreateAlchemy(packet.blockX, packet.blockY);
		mc.sayda.mcraze.player.Inventory inv = player.inventory;

		if (packet.isDepositEssence) {
			// Deprecated: Essence is now drawn directly from player pool during brewing.
		} else if (packet.isAlchemySlot) {
			if (packet.slotIdx >= 0 && packet.slotIdx < 5) {
				mc.sayda.mcraze.item.InventoryItem alcSlot = alchemyData.items[packet.slotIdx];
				handleContainerSlotInteraction(inv, alcSlot, packet.isRightClick, packet.slotIdx == 4);
			}
		} else {
			if (packet.slotIdx >= 0 && packet.slotIdx < 40) {
				int invX = packet.slotIdx % 10;
				int invY = packet.slotIdx / 10 + 3;
				handleInventorySlotInteraction(inv, invX, invY, packet.isRightClick, logger);
			}
		}

		mc.sayda.mcraze.item.ItemLoader.ItemDefinition recipe = findAlchemyRecipe(alchemyData.items);
		int cost = (recipe != null) ? recipe.alchemy.essence : 0;

		PacketAlchemyOpen refreshPacket = new PacketAlchemyOpen(
				packet.blockX, packet.blockY,
				alchemyData.items,
				player.essence,
				alchemyData.brewProgress,
				alchemyData.brewTimeTotal,
				player.getUUID(),
				true,
				cost);
		playerConnection.getConnection().sendPacket(refreshPacket);
		broadcastInventoryUpdates();
	}

	private void handleContainerSlotInteraction(Inventory inv, mc.sayda.mcraze.item.InventoryItem containerSlot,
			boolean isRightClick, boolean isOutput) {
		if (inv.holding.isEmpty()) {
			if (!containerSlot.isEmpty()) {
				if (isRightClick && containerSlot.getCount() > 1 && !isOutput) {
					int pick = (containerSlot.getCount() + 1) / 2;
					inv.holding.setItem(containerSlot.getItem().clone());
					inv.holding.setCount(pick);
					containerSlot.setCount(containerSlot.getCount() - pick);
				} else {
					inv.holding.setItem(containerSlot.getItem());
					inv.holding.setCount(containerSlot.getCount());
					containerSlot.setEmpty();
				}
			}
		} else {
			if (isOutput) {
				if (!containerSlot.isEmpty() && containerSlot.getItem().itemId.equals(inv.holding.getItem().itemId)) {
					int leftover = inv.holding.add(containerSlot.getItem(), containerSlot.getCount());
					containerSlot.setCount(leftover);
					if (leftover == 0)
						containerSlot.setEmpty();
				}
				return;
			}

			if (containerSlot.isEmpty()) {
				if (isRightClick) {
					containerSlot.setItem(inv.holding.getItem().clone());
					containerSlot.setCount(1);
					inv.holding.remove(1);
				} else {
					containerSlot.setItem(inv.holding.getItem().clone());
					containerSlot.setCount(inv.holding.getCount());
					inv.holding.setEmpty();
				}
			} else if (containerSlot.getItem().itemId.equals(inv.holding.getItem().itemId)) {
				if (isRightClick) {
					if (containerSlot.add(inv.holding.getItem(), 1) == 0) {
						inv.holding.remove(1);
					}
				} else {
					int leftover = containerSlot.add(inv.holding.getItem(), inv.holding.getCount());
					inv.holding.setCount(leftover);
					if (leftover == 0)
						inv.holding.setEmpty();
				}
			} else {
				mc.sayda.mcraze.item.InventoryItem temp = new mc.sayda.mcraze.item.InventoryItem(null);
				temp.setItem(inv.holding.getItem());
				temp.setCount(inv.holding.getCount());
				inv.holding.setItem(containerSlot.getItem());
				inv.holding.setCount(containerSlot.getCount());
				containerSlot.setItem(temp.getItem());
				containerSlot.setCount(temp.getCount());
			}
		}
	}

	public void tickAlchemies() {
		for (mc.sayda.mcraze.world.storage.AlchemyData alchemy : world.getAllAlchemies().values()) {
			tickAlchemy(alchemy);
		}
	}

	private void tickAlchemy(mc.sayda.mcraze.world.storage.AlchemyData alchemy) {
		if (alchemy.x < 0 || alchemy.x >= world.width || alchemy.y < 0 || alchemy.y >= world.height)
			return;

		// CRITICAL FIX: Upgrade 3-slot alchemy tables to 5-slot to prevent crash on old
		// saves
		if (alchemy.items.length < 5) {
			mc.sayda.mcraze.item.InventoryItem[] newItems = new mc.sayda.mcraze.item.InventoryItem[5];
			for (int i = 0; i < 5; i++) {
				if (i < alchemy.items.length) {
					newItems[i] = alchemy.items[i];
				} else {
					newItems[i] = new mc.sayda.mcraze.item.InventoryItem(null);
				}
			}
			alchemy.items = newItems;
		}

		mc.sayda.mcraze.item.InventoryItem[] items = alchemy.items;
		mc.sayda.mcraze.item.ItemLoader.ItemDefinition recipe = findAlchemyRecipe(items);

		// Find a player who has this table open and has enough essence
		Player primaryUser = null;
		for (PlayerConnection pc : players) {
			if (pc.getOpenedAlchemyX() == alchemy.x && pc.getOpenedAlchemyY() == alchemy.y) {
				Player p = pc.getPlayer();
				if (recipe != null && p != null && p.essence >= recipe.alchemy.essence) {
					primaryUser = p;
					break;
				}
			}
		}

		boolean canBrew = (recipe != null && primaryUser != null);

		if (canBrew && !items[4].isEmpty()) {
			if (!items[4].getItem().itemId.equals(recipe.itemId) || items[4].getCount() >= 64) {
				canBrew = false;
			}
		}

		if (canBrew) {
			if (alchemy.currentRecipe == null || !alchemy.currentRecipe.equals(recipe.itemId)) {
				alchemy.currentRecipe = recipe.itemId;
				alchemy.brewProgress = 0;
				alchemy.brewTimeTotal = recipe.alchemy.time;
			}

			alchemy.brewProgress++;
			if (alchemy.brewProgress >= alchemy.brewTimeTotal) {
				// Deduct essence from the player who enabled this brew
				if (primaryUser != null) {
					primaryUser.essence -= recipe.alchemy.essence;
				}
				mc.sayda.mcraze.item.Item result = mc.sayda.mcraze.Constants.itemTypes.get(recipe.itemId);
				if (items[4].isEmpty()) {
					items[4].setItem(result.clone());
					items[4].setCount(recipe.alchemy.yield);
				} else {
					items[4].add(result, recipe.alchemy.yield);
				}

				for (int i = 0; i < 4; i++) {
					items[i].remove(1);
				}

				alchemy.brewProgress = 0;
				alchemy.currentRecipe = null;

				// Sound: Brewing Complete
				broadcastSound("assets/sounds/random/levelup.wav"); // Placeholder
			}
		} else {
			if (alchemy.brewProgress > 0) {
				alchemy.brewProgress--;
			}
			alchemy.currentRecipe = null;
		}

		for (PlayerConnection pc : players) {
			if (pc.getConnection().isConnected() && pc.getOpenedAlchemyX() == alchemy.x
					&& pc.getOpenedAlchemyY() == alchemy.y) {
				PacketAlchemyOpen syncPacket = new PacketAlchemyOpen(
						alchemy.x, alchemy.y,
						alchemy.items,
						pc.getPlayer().essence, // Send player essence as "stored" for UI display
						alchemy.brewProgress,
						alchemy.brewTimeTotal,
						pc.getPlayer().getUUID(),
						true,
						recipe != null ? recipe.alchemy.essence : 0);
				pc.getConnection().sendPacket(syncPacket);
			}
		}
	}

	private mc.sayda.mcraze.item.ItemLoader.ItemDefinition findAlchemyRecipe(
			mc.sayda.mcraze.item.InventoryItem[] slots) {
		if (slots[3].isEmpty())
			return null;

		for (mc.sayda.mcraze.item.Item itm : mc.sayda.mcraze.Constants.itemTypes.values()) {
			mc.sayda.mcraze.item.ItemLoader.ItemDefinition def = mc.sayda.mcraze.item.ItemLoader
					.getItemDefinition(itm.itemId);
			if (def != null && def.alchemy != null) {
				if (!slots[3].getItem().itemId.equals(def.alchemy.bottle))
					continue;

				java.util.List<String> needed = new java.util.ArrayList<>(
						java.util.Arrays.asList(def.alchemy.ingredients));
				boolean match = true;
				for (int i = 0; i < 3; i++) {
					String slotId = slots[i].isEmpty() ? "empty" : slots[i].getItem().itemId;
					if (!needed.remove(slotId)) {
						if (!slots[i].isEmpty()) {
							match = false;
							break;
						}
					}
				}
				if (match && needed.isEmpty())
					return def;
			}
		}
		return null;
	}

	private void handleAlchemyShiftClick(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketShiftClick packet,
			mc.sayda.mcraze.player.Inventory playerInv) {
		if (playerConnection.getOpenedAlchemyX() == -1 || playerConnection.getOpenedAlchemyY() == -1) {
			return;
		}

		mc.sayda.mcraze.world.storage.AlchemyData alchemyData = world.getOrCreateAlchemy(
				playerConnection.getOpenedAlchemyX(), playerConnection.getOpenedAlchemyY());
		if (alchemyData == null)
			return;

		if (packet.isContainerSlot) {
			if (packet.slotX >= 0 && packet.slotX < 5) {
				mc.sayda.mcraze.item.InventoryItem sourceSlot = alchemyData.items[packet.slotX];
				if (sourceSlot != null && !sourceSlot.isEmpty()) {
					quickTransferToPlayerInventory(sourceSlot, playerInv);
				}
			}
		} else {
			int playerRow = packet.slotY + 3;
			if (packet.slotX >= 0 && packet.slotX < 10 && playerRow >= 0
					&& playerRow < playerInv.inventoryItems[0].length) {
				mc.sayda.mcraze.item.InventoryItem sourceSlot = playerInv.inventoryItems[packet.slotX][playerRow];
				if (!sourceSlot.isEmpty()) {
					quickTransferToAlchemy(sourceSlot, alchemyData);
				}
			}
		}
	}

	private void quickTransferToAlchemy(mc.sayda.mcraze.item.InventoryItem source,
			mc.sayda.mcraze.world.storage.AlchemyData alchemy) {
		if (source.getItem().itemId.equals("water_bottle") || source.getItem().itemId.equals("bottle")) {
			if (alchemy.items[3].isEmpty() || alchemy.items[3].getItem().itemId.equals(source.getItem().itemId)) {
				int leftover = alchemy.items[3].add(source.getItem(), source.getCount());
				source.setCount(leftover);
				if (leftover == 0)
					source.setEmpty();
				if (source.isEmpty())
					return;
			}
		}

		for (int i = 0; i < 3; i++) {
			if (alchemy.items[i].isEmpty() || alchemy.items[i].getItem().itemId.equals(source.getItem().itemId)) {
				int leftover = alchemy.items[i].add(source.getItem(), source.getCount());
				source.setCount(leftover);
				if (leftover == 0)
					source.setEmpty();
				if (source.isEmpty())
					return;
			}
		}
	}

	private void tickPots() {
		for (mc.sayda.mcraze.world.storage.PotData pot : world.getPots()) {
			if (pot.plantId != null) {
				// Check tile above
				int aboveY = pot.y - 1;
				if (aboveY < 0)
					continue;

				mc.sayda.mcraze.world.tile.Tile aboveTile = world.getTile(pot.x, aboveY);
				// Pause if blocked (must be AIR to grow/place)

				boolean blocked = (aboveTile != null && aboveTile.type.name != Constants.TileID.AIR);

				if (blocked) {
					continue; // Paused
				}

				// Not blocked, so grow
				if (pot.growthProgress < pot.growthTimeTotal) {
					pot.growthProgress++;
				}

				// Check if ready to place
				if (pot.isFinished()) {
					// Place the plant above
					try {
						// Map plantId (item id) to TileID
						// e.g. "rose" -> ROSE, "wheat_seeds" -> WHEAT_SEEDS
						Constants.TileID plantTile = Constants.TileID.valueOf(pot.plantId.toUpperCase());
						world.addTile(pot.x, aboveY, plantTile);
						broadcastBlockChange(pot.x, aboveY, plantTile);

						// Sound effect
						broadcastSound("assets/sounds/random/pop.wav");

						// Reset progress but KEEP plantId (Continuous production)
						pot.growthProgress = 0;
					} catch (IllegalArgumentException e) {
						if (logger != null)
							logger.error("Failed to map pot plantId '" + pot.plantId + "' to TileID");
						pot.plantId = null; // Error recovery
						pot.growthProgress = 0;
					}
				}
			}
		}
	}

	private void handlePotAction(PlayerConnection playerConnection, int x, int y) {
		Player player = playerConnection.getPlayer();
		mc.sayda.mcraze.world.storage.PotData pot = world.getOrCreatePot(x, y);
		mc.sayda.mcraze.item.InventoryItem held = player.inventory.selectedItem();

		if (pot.isEmpty()) {
			// Try to plant
			if (!held.isEmpty()) {
				// Check if item is growable
				if (held.getItem().isGrowable) {
					// Plant it
					pot.plantId = held.getItem().itemId;
					pot.growthTimeTotal = held.getItem().growthTime;
					pot.growthProgress = 0;

					player.inventory.decreaseSelected(1);
					broadcastInventoryUpdates();

					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Planted " + held.getItem().name + ". Grows in "
									+ (pot.growthTimeTotal / 20) + "s.",
							new mc.sayda.mcraze.graphics.Color(100, 255, 100)));
				} else {
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"This cannot be planted.",
							new mc.sayda.mcraze.graphics.Color(255, 100, 100)));
				}
			} else {
				// Empty pot, empty hand
				playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
						"Empty Pot.",
						new mc.sayda.mcraze.graphics.Color(200, 200, 200)));
			}
		} else {
			// Pot has plant - Show Status Only (Continuous Production)
			mc.sayda.mcraze.item.Item plant = Constants.itemTypes.get(pot.plantId);
			String plantName = (plant != null ? plant.name : "Unknown");

			// Check for blockage
			int aboveY = y - 1;
			mc.sayda.mcraze.world.tile.Tile aboveTile = world.getTile(x, aboveY);
			boolean blocked = (aboveTile != null && aboveTile.type.name != Constants.TileID.AIR);

			if (blocked) {
				// Check if blocked by the plant itself (Harvest needed)
				boolean blockedByProduct = false;
				if (plant != null) {
					try {
						Constants.TileID productID = Constants.TileID.valueOf(pot.plantId.toUpperCase());
						if (aboveTile.type.name == productID) {
							blockedByProduct = true;
						}
					} catch (Exception e) {
					}
				}

				if (blockedByProduct) {
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Harvest the " + plantName + " above to continue!",
							new mc.sayda.mcraze.graphics.Color(255, 200, 100)));
				} else {
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Pot is blocked by " + aboveTile.type.name + "!",
							new mc.sayda.mcraze.graphics.Color(255, 100, 100)));
				}
			} else {
				// Growing status
				int percent = (int) ((float) pot.growthProgress / pot.growthTimeTotal * 100f);
				int secondsLeft = Math.max(0, (pot.growthTimeTotal - pot.growthProgress) / 20);
				playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
						"Growing " + plantName + ": " + percent + "% (" + secondsLeft
								+ "s left)",
						new mc.sayda.mcraze.graphics.Color(100, 255, 100)));
			}
		}
	}

	private boolean handleTileInteraction(PlayerConnection playerConnection, int x, int y,
			mc.sayda.mcraze.world.tile.Tile tile) {
		Player player = playerConnection.getPlayer();

		// Shared cooldown check for all interactions
		long currentTime = System.currentTimeMillis();
		Long lastInteract = interactCooldowns.get(player.username);
		if (lastInteract != null && currentTime - lastInteract < INTERACT_COOLDOWN_MS) {
			// Allow Spam?
		}

		switch (tile.type.name) {
			case WORKBENCH:
				interactCooldowns.put(player.username, currentTime);
				player.inventory.tableSizeAvailable = 3;
				player.inventory.setVisible(true);
				broadcastInventoryUpdates();
				return true;

			case CHEST:
				interactCooldowns.put(player.username, currentTime);
				mc.sayda.mcraze.world.storage.ChestData chestData = world.getOrCreateChest(x, y);
				playerConnection.setOpenedChest(x, y);
				player.inventory.setVisible(false);
				mc.sayda.mcraze.network.packet.PacketChestOpen chestPacket = new mc.sayda.mcraze.network.packet.PacketChestOpen(
						x, y,
						chestData.items,
						player.getUUID());
				playerConnection.getConnection().sendPacket(chestPacket);
				broadcastInventoryUpdates();
				if (logger != null)
					logger.info("Player " + player.username + " opened chest at " + x + "," + y);
				return true;

			case FURNACE:
			case FURNACE_LIT:
				interactCooldowns.put(player.username, currentTime);
				mc.sayda.mcraze.world.storage.FurnaceData furnaceData = world.getOrCreateFurnace(x, y);
				playerConnection.setOpenedFurnace(x, y);
				player.inventory.setVisible(false);
				mc.sayda.mcraze.network.packet.PacketFurnaceOpen furnacePacket = new mc.sayda.mcraze.network.packet.PacketFurnaceOpen(
						x, y,
						furnaceData.items,
						furnaceData.fuelTimeRemaining,
						furnaceData.smeltProgress,
						furnaceData.smeltTimeTotal,
						player.getUUID(),
						false);
				playerConnection.getConnection().sendPacket(furnacePacket);
				broadcastInventoryUpdates();
				if (logger != null)
					logger.info("Player " + player.username + " opened furnace at " + x + "," + y);
				return true;

			case ALCHEMY_TABLE:
				interactCooldowns.put(player.username, currentTime);
				playerConnection.setOpenedAlchemy(x, y);
				player.inventory.setVisible(false);
				mc.sayda.mcraze.world.storage.AlchemyData alchemyData = world.getOrCreateAlchemy(x, y);
				mc.sayda.mcraze.network.packet.PacketAlchemyOpen alchemyPacket = new mc.sayda.mcraze.network.packet.PacketAlchemyOpen(
						x, y,
						alchemyData.items,
						player.essence,
						alchemyData.brewProgress,
						alchemyData.brewTimeTotal,
						player.getUUID(),
						false,
						alchemyData.getCurrentRecipeCost());
				playerConnection.getConnection().sendPacket(alchemyPacket);
				broadcastInventoryUpdates();
				return true;
			case TRAPDOOR_CLOSED:
				// Toggle to OPEN
				world.addTile(x, y, Constants.TileID.TRAPDOOR);
				broadcastBlockChange(x, y, Constants.TileID.TRAPDOOR);
				broadcastSound("assets/sounds/hit.wav", x, y);
				return true;

			case TRAPDOOR:
				// Toggle to CLOSE
				world.addTile(x, y, Constants.TileID.TRAPDOOR_CLOSED);
				broadcastBlockChange(x, y, Constants.TileID.TRAPDOOR_CLOSED);
				broadcastSound("assets/sounds/hit.wav", x, y);
				return true;

			case DOOR_BOT:
			case DOOR_TOP:
			case DOOR_BOT_CLOSED:
			case DOOR_TOP_CLOSED:
				// Standard Door Toggle Logic
				boolean isBot = (tile.type.name == Constants.TileID.DOOR_BOT
						|| tile.type.name == Constants.TileID.DOOR_BOT_CLOSED);
				boolean isTop = (tile.type.name == Constants.TileID.DOOR_TOP
						|| tile.type.name == Constants.TileID.DOOR_TOP_CLOSED);

				if (!isBot && !isTop)
					return false;

				interactCooldowns.put(player.username, currentTime);

				// Determine new state
				Constants.TileID newBot, newTop;
				if (tile.type.name == Constants.TileID.DOOR_BOT_CLOSED) {
					newBot = Constants.TileID.DOOR_BOT;
					newTop = Constants.TileID.DOOR_TOP;
				} else if (tile.type.name == Constants.TileID.DOOR_BOT) {
					newBot = Constants.TileID.DOOR_BOT_CLOSED;
					newTop = Constants.TileID.DOOR_TOP_CLOSED;
				} else if (tile.type.name == Constants.TileID.DOOR_TOP_CLOSED) {
					newBot = Constants.TileID.DOOR_BOT;
					newTop = Constants.TileID.DOOR_TOP;
				} else { // DOOR_TOP
					newBot = Constants.TileID.DOOR_BOT_CLOSED;
					newTop = Constants.TileID.DOOR_TOP_CLOSED;
				}

				if (isBot) {
					// Clicked bottom part
					world.addTile(x, y, newBot);
					broadcastBlockChange(x, y, newBot);
					if (y > 0) { // Top part is at y-1
						world.addTile(x, y - 1, newTop);
						broadcastBlockChange(x, y - 1, newTop);
					}
				} else {
					// Clicked top part
					world.addTile(x, y, newTop);
					broadcastBlockChange(x, y, newTop);
					if (y < world.height - 1) { // Bottom part is at y+1
						world.addTile(x, y + 1, newBot);
						broadcastBlockChange(x, y + 1, newBot);
					}
				}
				broadcastSound("assets/sounds/hit.wav", x, y);
				return true;

			case BOULDER_TRAP:
				// Trigger Boulder
				world.removeTile(x, y);
				broadcastBlockChange(x, y, null);

				// Spawn Boulder
				boolean rollRight = player.x < x + 0.5f;
				mc.sayda.mcraze.entity.projectile.EntityBoulder boulder = new mc.sayda.mcraze.entity.projectile.EntityBoulder(
						x, y, rollRight);
				addEntity(boulder);
				return true;

			case FLOWER_POT:
				handlePotAction(playerConnection, x, y);
				return true;

			case BED_LEFT:
			case BED_RIGHT:
				// 1. Check Insomnia (True = Block Sleep)
				if (world.insomnia) {
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"Your insomnia is preventing you from sleeping...",
							new mc.sayda.mcraze.graphics.Color(255, 100, 100)));
					return true;
				}

				// 2. Check Time - Only allow sleep if it's night
				if (world.isNight()) {
					// Skip to dawn (06:00, which is tick 0 relative to daylightSpeed)
					long ticksToSkip = world.getNextTime(0);
					world.setTicksAlive(world.getTicksAlive() + ticksToSkip);

					player.heal(20);
					broadcastEntityUpdate(); // Sync health

					player.setSpawnPoint(x, y);
					broadcastWorldTime();
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"You sleep through the night... (+20 HP)",
							new mc.sayda.mcraze.graphics.Color(100, 255, 100)));
				} else {
					playerConnection.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
							"You can only sleep at night!",
							new mc.sayda.mcraze.graphics.Color(255, 100, 100)));
				}
				return true;

			default:
				return false;
		}
	}
}
