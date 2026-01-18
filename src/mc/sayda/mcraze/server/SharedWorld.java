package mc.sayda.mcraze.server;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.entity.Entity;
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
import mc.sayda.mcraze.network.packet.PacketPlayerDeath;
import mc.sayda.mcraze.network.packet.PacketWorldInit;
import mc.sayda.mcraze.network.packet.PacketWorldUpdate;
import mc.sayda.mcraze.world.tile.Tile;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.world.storage.WorldAccess;
import mc.sayda.mcraze.world.storage.ChestData;
import mc.sayda.mcraze.world.storage.FurnaceData;

import java.util.ArrayList;
import java.util.HashMap;
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
	private HashMap<Player, BreakingState> playerBreakingState = new HashMap<>();
	private final java.util.concurrent.ConcurrentHashMap<String, Long> doorCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long DOOR_COOLDOWN_MS = 250;
	private final java.util.concurrent.ConcurrentHashMap<String, Long> bedCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long BED_COOLDOWN_MS = 500; // Bed message cooldown (0.5 seconds)
	private final java.util.concurrent.ConcurrentHashMap<String, Long> interactCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long INTERACT_COOLDOWN_MS = 200; // General interaction cooldown (doors, chests, workbenches)

	// World entities (items, etc.) not associated with a specific player -
	// thread-safe
	private final EntityManager entityManager = new EntityManager();

	// Performance: Reusable lists to avoid allocation in hot paths
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

	public SharedWorld(int worldWidth, Random random, String worldName) {
		this(worldWidth, random, worldName, null); // Integrated server - no world directory
	}

	public SharedWorld(int worldWidth, Random random, String worldName, java.nio.file.Path worldDirectory) {
		this.random = random;
		this.worldName = worldName;
		this.worldDirectory = worldDirectory;
		this.players = new CopyOnWriteArrayList<>(); // Thread-safe for concurrent access

		// Initialize spawner with rules
		this.mobSpawner = new MobSpawner(this);
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

		this.world = new World(worldWidth, worldWidth, random);
		this.worldAccess = new WorldAccess(world);
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
				world.spawnLocation.y,
				7 * 4,
				14 * 4);
		player.username = username; // Set player's display name
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Player entity created for " + username);

		// Load or create playerdata
		if (playerData == null) {
			// Auto-register new player
			playerData = new mc.sayda.mcraze.player.data.PlayerData(
					username, password, world.spawnLocation.x, world.spawnLocation.y);
			mc.sayda.mcraze.player.data.PlayerDataManager.save(worldName, playerData);
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
		sendInitialWorldState(playerConnection);
		if (logger != null)
			logger.debug("SharedWorld.addPlayer: Initial world state sent");

		// CRITICAL FIX: Immediately sync inventory to client on login
		// Without this, inventory only appears after hotbar scroll
		if (player.inventory != null && player.inventory.inventoryItems != null) {
			sendInventoryUpdate(playerConnection);
			if (logger != null) {
				logger.info("Sent initial inventory sync to " + username + " on login");
			}
		}

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
		if (logger != null) {
			logger.info("SharedWorld: Sending initial world state to " + playerConnection.getPlayerName());
			logger.debug("SharedWorld.sendInitialWorldState: World dimensions: " + world.width + "x" + world.height);
		}

		// Send all non-air tiles (count first to determine total packets)
		// Use WorldAccess for thread-safe tile iteration
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Collecting non-air tiles...");
		ArrayList<Integer> changedX = new ArrayList<>();
		ArrayList<Integer> changedY = new ArrayList<>();
		ArrayList<Character> changedTiles = new ArrayList<>();

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

		int worldWidth = worldAccess.getWidth();
		int worldHeight = worldAccess.getHeight();

		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = worldAccess.getTile(x, y);
				if (tile != null && tile.type != null && tile.type.name != Constants.TileID.AIR) {
					changedX.add(x);
					changedY.add(y);
					changedTiles.add((char) tile.type.name.ordinal());

					// Track min/max coordinates
					if (x < minX)
						minX = x;
					if (x > maxX)
						maxX = x;
					if (y < minY)
						minY = y;
					if (y > maxY)
						maxY = y;
				}
			}
		}
		if (logger != null) {
			logger.debug("SharedWorld.sendInitialWorldState: Collected " + changedX.size() + " non-air tiles");
			logger.info("SharedWorld: Tile range - X:" + minX + "-" + maxX + ", Y:" + minY + "-" + maxY);
		}

		// Calculate total packets (world update chunks + biome data + entity update)
		// PERFORMANCE FIX: Increased chunk size from 1000 to 5000 tiles
		// This reduces packet count from ~65 to ~13 for a 256x256 world, improving load
		// times
		int chunkSize = 5000;
		int numChunks = (int) Math.ceil((double) changedX.size() / chunkSize);
		int totalPackets = 1 + numChunks + 1; // +1 biome, +numChunks world, +1 entity

		// Send world initialization packet with dimensions and spawn location
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Sending PacketWorldInit...");

		// Get this player's UUID
		String playerUUID = playerConnection.getPlayer().getUUID();
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Player UUID: " + playerUUID);

		PacketWorldInit initPacket = new PacketWorldInit(
				world.width,
				world.height,
				0,
				world.spawnLocation.x,
				world.spawnLocation.y);
		initPacket.playerUUID = playerUUID;
		initPacket.totalPacketsExpected = totalPackets;
		// Set gamerules from world
		initPacket.spelunking = world.spelunking;
		initPacket.keepInventory = world.keepInventory;
		initPacket.daylightCycle = world.daylightCycle;
		playerConnection.getConnection().sendPacket(initPacket);
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: PacketWorldInit sent with playerUUID=" + playerUUID
					+ ", totalPackets=" + totalPackets);

		// Send biome data immediately after world init
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Sending PacketBiomeData...");
		mc.sayda.mcraze.network.packet.PacketBiomeData biomePacket = new mc.sayda.mcraze.network.packet.PacketBiomeData(
				world.getBiomeMap());
		playerConnection.getConnection().sendPacket(biomePacket);
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: PacketBiomeData sent (" +
					(world.getBiomeMap() != null ? world.getBiomeMap().length : 0) + " biomes)");

		// CRITICAL FIX: Flush initial packets (WorldInit + BiomeData) before sending
		// massive world data
		// This prevents BufferedOutputStream from accumulating ALL packets before
		// flushing
		playerConnection.getConnection().flush();
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Flushed initial packets (WorldInit + BiomeData)");

		// Send in chunks of 1000 tiles
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Sending " + numChunks + " chunks of world data...");

		for (int i = 0; i < changedX.size(); i += chunkSize) {
			int end = Math.min(i + chunkSize, changedX.size());
			int chunkNum = (i / chunkSize) + 1;

			PacketWorldUpdate packet = new PacketWorldUpdate();
			packet.ticksAlive = world.getTicksAlive();
			packet.changedX = new int[end - i];
			packet.changedY = new int[end - i];
			packet.changedTiles = new char[end - i];

			for (int j = 0; j < end - i; j++) {
				packet.changedX[j] = changedX.get(i + j);
				packet.changedY[j] = changedY.get(i + j);
				packet.changedTiles[j] = changedTiles.get(i + j);
			}

			playerConnection.getConnection().sendPacket(packet);
			if (logger != null)
				logger.debug("SharedWorld.sendInitialWorldState: Sent chunk " + chunkNum + "/" + numChunks + " ("
						+ (end - i) + " tiles)");

			// CRITICAL FIX: Flush every 2 chunks (~10,000 tiles) to prevent packet
			// overflow
			// Without this, all packets accumulate in BufferedOutputStream and flush as
			// massive burst
			if (chunkNum % 2 == 0 || chunkNum == numChunks) {
				playerConnection.getConnection().flush();
				if (logger != null)
					logger.debug(
							"SharedWorld.sendInitialWorldState: Flushed after chunk " + chunkNum + "/" + numChunks);
			}
		}
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: All world data chunks sent");

		// Send backdrop tiles
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Collecting backdrop tiles...");
		ArrayList<Integer> backdropX = new ArrayList<>();
		ArrayList<Integer> backdropY = new ArrayList<>();
		ArrayList<Character> backdropTiles = new ArrayList<>();

		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = worldAccess.getTile(x, y);
				if (tile != null && tile.backdropType != null) {
					backdropX.add(x);
					backdropY.add(y);
					backdropTiles.add((char) tile.backdropType.name.ordinal());
				}
			}
		}

		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Collected " + backdropX.size() + " backdrop tiles");

		// Send backdrops in chunks of 1000
		int backdropChunks = (int) Math.ceil((double) backdropX.size() / chunkSize);
		if (logger != null)
			logger.debug(
					"SharedWorld.sendInitialWorldState: Sending " + backdropChunks + " chunks of backdrop data...");

		for (int i = 0; i < backdropX.size(); i += chunkSize) {
			int end = Math.min(i + chunkSize, backdropX.size());
			int chunkNum = (i / chunkSize) + 1;

			// Create batched packet (reduces 1000 packets to 1 packet)
			mc.sayda.mcraze.network.packet.PacketBackdropBatch batchPacket = new mc.sayda.mcraze.network.packet.PacketBackdropBatch();
			batchPacket.x = new int[end - i];
			batchPacket.y = new int[end - i];
			batchPacket.backdropTileIds = new char[end - i];

			for (int j = 0; j < end - i; j++) {
				batchPacket.x[j] = backdropX.get(i + j);
				batchPacket.y[j] = backdropY.get(i + j);
				batchPacket.backdropTileIds[j] = backdropTiles.get(i + j);
			}

			playerConnection.getConnection().sendPacket(batchPacket);

			if (logger != null)
				logger.debug("SharedWorld.sendInitialWorldState: Sent backdrop batch " + chunkNum + "/" + backdropChunks
						+ " (" + (end - i) + " backdrops)");
		}

		if (logger != null)
			logger.debug(
					"SharedWorld.sendInitialWorldState: All backdrop data sent (" + backdropX.size() + " backdrops)");

		// CRITICAL FIX: Flush after backdrop chunks to prevent packet overflow
		playerConnection.getConnection().flush();
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Flushed after backdrop data");

		// Send initial entity update DIRECTLY to this player (not broadcast)
		if (logger != null)
			logger.debug("SharedWorld.sendInitialWorldState: Sending initial entity update directly to "
					+ playerConnection.getPlayerName());
		sendEntityUpdateToPlayer(playerConnection);
		if (logger != null) {
			logger.info("SharedWorld.sendInitialWorldState: Initial entity update sent to "
					+ playerConnection.getPlayerName());
			logger.info("SharedWorld: Initial world state complete for " + playerConnection.getPlayerName() + " ("
					+ changedX.size() + " tiles)");
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
				} else {
					packet.backdropPlacementMode[i] = false;
					packet.handTargetX[i] = -1;
					packet.handTargetY[i] = -1;
					packet.hotbarIndex[i] = 0;
					packet.selectedItemId[i] = null;
					packet.selectedItemCount[i] = 0;
					packet.selectedItemDurability[i] = 0;
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
	public void tick() {
		if (!running)
			return;

		ticksRunning++;

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
				long timeoutTicks = (worldDirectory == null) ? 60 : 10; // 1 second timeout
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

				// Normalize speed to 20 ticks/second standard
				int increment = 1;
				if (worldDirectory == null) {
					// Integrated Server (60Hz): Throttle to 20Hz
					if (ticksRunning % 3 != 0)
						continue;
				} else {
					// Dedicated Server (10Hz): Boost to 20Hz equivalent
					increment = 2;
				}

				// Increment progress
				state.ticks += increment;

				// Check if broken
				Item currentItem = player.inventory.selectedItem().getItem();
				int ticksNeeded = world.breakTicks(state.x, state.y, currentItem); // Calibrated for 20Hz

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
					PlayerConnection pickupPlayer = null;
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

				// Allow entities access to server context (e.g. for AI targeting)
				if (entity instanceof LivingEntity) {
					((LivingEntity) entity).tick(this);
				}

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
						if (logger != null)
							logger.info("SharedWorld.tick: Entity died - type: " + entity.getClass().getSimpleName());

						// Send immediate death packet to the player (ISSUE #13 fix)
						if (entity instanceof Player) {
							Player deadPlayer = (Player) entity;
							for (PlayerConnection pc : players) {
								if (pc.getPlayer() == deadPlayer) {
									pc.getConnection().sendPacket(new PacketPlayerDeath());

									// Drop all items from inventory - use thread-safe EntityManager
									java.util.ArrayList<Item> droppedItems = deadPlayer.dropAllItems(random);
									for (Item item : droppedItems) {
										entityManager.add(item);
									}
									// TODO: Replace with GameLogger
									if (logger != null)
										logger.info("Player " + deadPlayer.username + " died and dropped "
												+ droppedItems.size() + " items");

									// Broadcast empty inventory after death
									broadcastInventoryUpdates();

									break;
								}
							}
						}
						// Ensure dead entities are removed from world and broadcast to clients
						entitiesToRemove.add(entity);
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

		// Tick furnaces
		tickFurnaces();
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
		float cameraY = viewer.y - screenHeight / tileSize / 2f;

		// Calculate visible bounds (same math as World.java:890-893)
		// +2 buffer on each side ensures entities at edge of screen are included
		int startX = Math.max(0, (int) Math.floor(cameraX) - 2);
		int endX = (int) Math.ceil(cameraX + (float) screenWidth / tileSize) + 2;
		int startY = Math.max(0, (int) Math.floor(cameraY) - 2);
		int endY = (int) Math.ceil(cameraY + (float) screenHeight / tileSize) + 2;

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
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityZombie) {
					packet.entityTypes[i] = "Zombie";
					packet.itemIds[i] = null;
					packet.playerNames[i] = null;
				} else if (entity instanceof mc.sayda.mcraze.entity.mob.EntityWolf) {
					packet.entityTypes[i] = "Wolf";
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
					} else {
						packet.backdropPlacementMode[i] = false;
						packet.handTargetX[i] = -1;
						packet.handTargetY[i] = -1;
						packet.hotbarIndex[i] = 0;
						packet.selectedItemId[i] = null;
						packet.selectedItemCount[i] = 0;
						packet.selectedItemDurability[i] = 0;
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
		packet.hotbarIndex = inv.hotbarIdx;

		// Inventory UI state
		packet.visible = inv.isVisible();
		packet.tableSizeAvailable = inv.tableSizeAvailable;

		// Craftable item preview
		mc.sayda.mcraze.item.InventoryItem craftable = inv.getCraftable();
		if (craftable != null && craftable.item != null && craftable.count > 0) {
			packet.craftableItemId = craftable.item.itemId;
			packet.craftableCount = craftable.count;
			if (craftable.item instanceof Tool) {
				packet.craftableToolUse = ((Tool) craftable.item).uses;
			} else {
				packet.craftableToolUse = 0;
			}
		} else {
			packet.craftableItemId = null;
			packet.craftableCount = 0;
			packet.craftableToolUse = 0;
		}

		// CRITICAL FIX: Synchronize cursor item (holding item) for all players
		// This ensures joined players see their own cursor item, not just the host
		mc.sayda.mcraze.item.InventoryItem holding = inv.holding;
		if (holding != null && holding.item != null && holding.count > 0) {
			packet.holdingItemId = holding.item.itemId;
			packet.holdingCount = holding.count;
			if (holding.item instanceof Tool) {
				packet.holdingToolUse = ((Tool) holding.item).uses;
			} else {
				packet.holdingToolUse = 0;
			}
		} else {
			packet.holdingItemId = null;
			packet.holdingCount = 0;
			packet.holdingToolUse = 0;
		}

		// Flatten 2D inventory array
		int index = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				mc.sayda.mcraze.item.InventoryItem slot = inv.inventoryItems[x][y];
				if (slot != null && slot.item != null && slot.count > 0) {
					packet.itemIds[index] = slot.item.itemId;
					packet.itemCounts[index] = slot.count;

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

	/**
	 * Broadcast gamerule changes to all connected players
	 */
	public void broadcastGamerules() {
		mc.sayda.mcraze.network.packet.PacketGameruleUpdate packet = new mc.sayda.mcraze.network.packet.PacketGameruleUpdate(
				world.spelunking,
				world.keepInventory,
				world.daylightCycle);
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
			return support == Constants.TileID.FARMLAND;
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

		// HEALING / EATING SYSTEM (Right-click with food)
		if (!packet.isBreak && held != null && held.itemId != null) {
			int healAmount = 0;
			boolean isFood = false;

			if (held.itemId.equals("bread")) {
				healAmount = 20; // 2 Hearts
				isFood = true;
			} else if (held.itemId.equals("apple")) {
				healAmount = 5; // Half Heart
				isFood = true;
			} else if (held.itemId.equals("golden_apple")) {
				healAmount = 100; // Full heal (max HP)
				isFood = true;
			} else if (held.itemId.equals("pork")) {
				healAmount = 5; // Half Heart
				isFood = true;
			} else if (held.itemId.equals("beef")) {
				healAmount = 5; // Half Heart
				isFood = true;
			} else if (held.itemId.equals("cooked_pork")) {
				healAmount = 30; // 3 Hearts
				isFood = true;
			} else if (held.itemId.equals("cooked_beef")) {
				healAmount = 30; // 3 Hearts
				isFood = true;
			} else if (held.itemId.equals("rotten_flesh")) {
				// 50% Heal 5, 25% Damage 5, 25% Nothing (Changed to test RNG distribution)
				double roll = this.random != null ? this.random.nextDouble() : Math.random();
				if (roll < 0.5) {
					healAmount = 5;
				} else if (roll < 0.75) {
					healAmount = -5;
				} else {
					healAmount = 0;
				}
				if (logger != null)
					logger.debug("Rotten Flesh Roll: " + roll + " -> Heal: " + healAmount);
				isFood = true;
			} else if (held.itemId.equals("bad_apple")) {
				healAmount = 10; // 1 Heart
				isFood = true;

				// Trigger Bad Apple video on client via packet
				playerConnection.getConnection().sendPacket(
						new mc.sayda.mcraze.network.packet.PacketItemTrigger("bad_apple"));
			}

			if (isFood) {
				// EATING COOLDOWN CHECK
				long currentTime = System.currentTimeMillis();
				Long lastEat = interactCooldowns.get(player.username + "_eat"); // Separate key for eating
				if (lastEat != null && currentTime - lastEat < 500) { // 500ms cooldown
					return;
				}

				if (player.hitPoints < player.getMaxHP()) {
					// Apply heal or damage
					if (healAmount > 0) {
						player.heal(healAmount);
					} else if (healAmount < 0) {
						player.takeDamage(-healAmount);
					}
					// else 0: nothing happens but item consumed

					interactCooldowns.put(player.username + "_eat", currentTime);

					// Consume item
					player.inventory.selectedItem().remove(1);
					broadcastInventoryUpdates();

					// Feedback
					String msg;
					mc.sayda.mcraze.graphics.Color color;
					if (healAmount > 0) {
						msg = "Yummers! Healed " + healAmount + " HP.";
						color = new mc.sayda.mcraze.graphics.Color(100, 255, 100);
					} else if (healAmount < 0) {
						msg = "Yikers! That tasted bad. Took " + (-healAmount) + " damage.";
						color = new mc.sayda.mcraze.graphics.Color(255, 100, 100);
					} else {
						msg = "You feel nothing.";
						color = new mc.sayda.mcraze.graphics.Color(200, 200, 200);
					}

					playerConnection.getConnection().sendPacket(new PacketChatMessage(msg, color));
					return; // Consumed food, don't place block
				}
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
			finishBreakingBlock(player, packet.x, packet.y);

		} else {
			// CRITICAL FIX: Explicitly stop breaking when isBreak=false (or on release)
			breakingState.reset();

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
			if (clickedTile != null && clickedTile.type != null &&
					clickedTile.type.name == Constants.TileID.CHEST) {
				// Get or create chest data
				mc.sayda.mcraze.world.storage.ChestData chestData = world.getOrCreateChest(packet.x, packet.y);

				// INTERACTION COOLDOWN CHECK
				long currentTime = System.currentTimeMillis();
				Long lastInteract = interactCooldowns.get(player.username);
				if (lastInteract != null && currentTime - lastInteract < INTERACT_COOLDOWN_MS) {
					return;
				}
				interactCooldowns.put(player.username, currentTime);

				// CRITICAL FIX: Track that this player has this chest open
				// This is required for server-side validation of chest actions
				playerConnection.setOpenedChest(packet.x, packet.y);

				// CRITICAL FIX: Hide regular inventory when opening container
				// This prevents dual input handling conflict
				player.inventory.setVisible(false);

				// Send chest open packet to this player (including UUID for client-side
				// filtering)
				PacketChestOpen chestPacket = new PacketChestOpen(packet.x, packet.y, chestData.items,
						player.getUUID());
				playerConnection.getConnection().sendPacket(chestPacket);

				// Broadcast inventory state change to sync visibility
				broadcastInventoryUpdates();

				if (logger != null) {
					logger.info("SharedWorld: Player " + player.username +
							" opened chest at (" + packet.x + ", " + packet.y + ")");
				}
				return;
			}

			// DOOR SYSTEM
			// Check if clicking on a furnace block
			if (clickedTile != null && clickedTile.type != null &&
					clickedTile.type.name == Constants.TileID.FURNACE) {
				// Get or create furnace data
				mc.sayda.mcraze.world.storage.FurnaceData furnaceData = world.getOrCreateFurnace(packet.x, packet.y);

				// INTERACTION COOLDOWN CHECK
				long currentTime = System.currentTimeMillis();
				Long lastInteract = interactCooldowns.get(player.username);
				if (lastInteract != null && currentTime - lastInteract < INTERACT_COOLDOWN_MS) {
					return;
				}
				interactCooldowns.put(player.username, currentTime);

				// CRITICAL FIX: Track that this player has this furnace open
				// This is required for server-side validation of furnace actions
				playerConnection.setOpenedFurnace(packet.x, packet.y);

				// CRITICAL FIX: Hide regular inventory when opening container
				// This prevents dual input handling conflict
				player.inventory.setVisible(false);

				// Send furnace open packet to this player
				PacketFurnaceOpen furnacePacket = new PacketFurnaceOpen(
						packet.x, packet.y,
						furnaceData.items,
						furnaceData.fuelTimeRemaining,
						furnaceData.smeltProgress,
						furnaceData.smeltTimeTotal,
						player.getUUID(),
						false);
				playerConnection.getConnection().sendPacket(furnacePacket);

				// Broadcast inventory state change to sync visibility
				broadcastInventoryUpdates();

				if (logger != null) {
					logger.info("SharedWorld: Player " + player.username +
							" opened furnace at (" + packet.x + ", " + packet.y + ")");
				}
				return;
			}
			// DOOR SYSTEM: Check if clicking on a door to toggle open/closed
			Tile doorTile = worldAccess.getTile(packet.x, packet.y);
			if (doorTile != null && doorTile.type != null) {
				Constants.TileID doorType = doorTile.type.name;
				boolean isDoorBot = (doorType == Constants.TileID.DOOR_BOT_CLOSED
						|| doorType == Constants.TileID.DOOR_BOT);
				boolean isDoorTop = (doorType == Constants.TileID.DOOR_TOP_CLOSED
						|| doorType == Constants.TileID.DOOR_TOP);

				if (isDoorBot || isDoorTop) {
					// DOOR COOLDOWN CHECK
					long currentTime = System.currentTimeMillis();
					Long lastInteract = doorCooldowns.get(player.username);
					if (lastInteract != null && currentTime - lastInteract < DOOR_COOLDOWN_MS) {
						return; // Too fast!
					}
					doorCooldowns.put(player.username, currentTime);

					// Determine the new state (toggle open/closed)
					Constants.TileID newBotState, newTopState;
					if (doorType == Constants.TileID.DOOR_BOT_CLOSED) {
						newBotState = Constants.TileID.DOOR_BOT;
						newTopState = Constants.TileID.DOOR_TOP;
					} else if (doorType == Constants.TileID.DOOR_BOT) {
						newBotState = Constants.TileID.DOOR_BOT_CLOSED;
						newTopState = Constants.TileID.DOOR_TOP_CLOSED;
					} else if (doorType == Constants.TileID.DOOR_TOP_CLOSED) {
						newBotState = Constants.TileID.DOOR_BOT;
						newTopState = Constants.TileID.DOOR_TOP;
					} else { // DOOR_TOP
						newBotState = Constants.TileID.DOOR_BOT_CLOSED;
						newTopState = Constants.TileID.DOOR_TOP_CLOSED;
					}

					// Toggle the clicked door part and its counterpart
					if (isDoorBot) {
						// Clicked on bottom - toggle both parts
						world.addTile(packet.x, packet.y, newBotState);
						broadcastBlockChange(packet.x, packet.y, newBotState);
						int topY = packet.y - 1;
						if (topY >= 0) {
							world.addTile(packet.x, topY, newTopState);
							broadcastBlockChange(packet.x, topY, newTopState);
						}
					} else {
						// Clicked on top - toggle both parts
						world.addTile(packet.x, packet.y, newTopState);
						broadcastBlockChange(packet.x, packet.y, newTopState);
						int botY = packet.y + 1;
						if (botY < world.height) {
							world.addTile(packet.x, botY, newBotState);
							broadcastBlockChange(packet.x, botY, newBotState);
						}
					}

					if (logger != null) {
						logger.debug("SharedWorld: Player " + player.username +
								" toggled door at (" + packet.x + ", " + packet.y + ")");
					}
					return; // Don't proceed with block placement
				}
			}

			// BED SYSTEM: Check if clicking on a bed to skip night
			Tile bedTile = worldAccess.getTile(packet.x, packet.y);
			if (bedTile != null && bedTile.type != null) {
				Constants.TileID bedType = bedTile.type.name;
				boolean isBed = (bedType == Constants.TileID.BED_LEFT || bedType == Constants.TileID.BED_RIGHT);

				if (isBed) {
					// Check if it's night time
					if (world.isNight()) {
						// Skip to morning (next day at time 0.0)
						long currentTicks = world.getTicksAlive();
						int dayLength = 20000; // From World.java
						// Calculate the next morning (skip to next day start)
						long nextMorning = ((currentTicks / dayLength) + 1) * dayLength;
						world.setTicksAlive(nextMorning);

						// Broadcast new time to all players
						broadcastWorldTime();

						// Heal player for sleeping
						player.heal(20);

						if (logger != null) {
							logger.info("SharedWorld: Player " + player.username +
									" used bed to skip night at (" + packet.x + ", " + packet.y + ")");
						}

						// Feedback
						playerConnection.getConnection().sendPacket(
								new PacketChatMessage("You slept and feel refreshed (+20 HP).",
										new mc.sayda.mcraze.graphics.Color(100, 255, 100)));
					} else {
						// Can only sleep at night - add cooldown to prevent message spam
						long currentTime = System.currentTimeMillis();
						Long lastBedMessage = bedCooldowns.get(player.username);

						// Only send message if cooldown has expired
						if (lastBedMessage == null || currentTime - lastBedMessage >= BED_COOLDOWN_MS) {
							bedCooldowns.put(player.username, currentTime);

							if (logger != null) {
								logger.debug("SharedWorld: Player " + player.username +
										" tried to use bed during day at (" + packet.x + ", " + packet.y + ")");
							}

							// Send feedback message to player
							PacketChatMessage msg = new PacketChatMessage(
									"You can only sleep at night",
									new mc.sayda.mcraze.graphics.Color(255, 100, 100)); // Red color
							playerConnection.getConnection().sendPacket(msg);
						}
						// Else: Cooldown active, silently ignore click
					}
					return; // Don't proceed with block placement
				}
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
						if (targetType == Constants.TileID.DIRT || targetType == Constants.TileID.GRASS) {
							// Convert to farmland
							world.addTile(packet.x, packet.y, Constants.TileID.FARMLAND);
							broadcastBlockChange(packet.x, packet.y, Constants.TileID.FARMLAND);

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
								belowTile.type.name == Constants.TileID.FARMLAND) {
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

			// Block placing validation - check if tileId is valid for normal placement
			if (tileId == null || tileId == Constants.TileID.AIR || tileId == Constants.TileID.NONE) {
				// Correction: send current block state to client (likely AIR)
				Tile current = worldAccess.getTile(packet.x, packet.y);
				int currentId = (current != null && current.type != null) ? current.type.name.ordinal() : 0;
				playerConnection.getConnection()
						.sendPacket(new PacketBlockChange(packet.x, packet.y, (char) currentId, false));
				return; // Cannot place air or invalid tiles
			}

			// BACKDROP MODE: Place backdrop instead of foreground
			if (player.backdropPlacementMode) {
				// FIXED: Allow backdrop placement anywhere (no restrictions - backdrops are
				// visual only)

				// Place backdrop
				if (world.setBackdrop(packet.x, packet.y, tileId)) {
					player.inventory.decreaseSelected(1);
					broadcastInventoryUpdates();
					broadcastBackdropChange(packet.x, packet.y, tileId);
				}
				return; // Don't place foreground
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

			// Place the block
			if (world.addTile(packet.x, packet.y, tileId)) {
				// Successfully placed - decrease inventory
				player.inventory.decreaseSelected(1);
				// Immediately sync inventory after placing block (ISSUE #10 fix)
				broadcastInventoryUpdates();

				// Broadcast change to all players
				broadcastBlockChange(packet.x, packet.y, tileId);

				// DOOR SYSTEM: Auto-place door top when placing door bottom
				if (tileId == Constants.TileID.DOOR_BOT_CLOSED) {
					int topY = packet.y - 1; // Y increases downward, so door top is above (y-1)
					// Check if space above is empty and within world bounds
					if (topY >= 0 && !world.isBreakable(packet.x, topY)) {
						// Place door top
						world.addTile(packet.x, topY, Constants.TileID.DOOR_TOP_CLOSED);
						broadcastBlockChange(packet.x, topY, Constants.TileID.DOOR_TOP_CLOSED);
						if (logger != null) {
							logger.debug("SharedWorld: Auto-placed door top at (" + packet.x + ", " + topY + ")");
						}
					}
				}

				// BED SYSTEM: Auto-place bed right when placing bed left
				if (tileId == Constants.TileID.BED_LEFT) {
					int rightX = packet.x + 1; // Bed right is to the right (x+1)
					// Check if space to the right is empty and within world bounds
					if (rightX < world.width && !world.isBreakable(rightX, packet.y)) {
						// Place bed right
						world.addTile(rightX, packet.y, Constants.TileID.BED_RIGHT);
						broadcastBlockChange(rightX, packet.y, Constants.TileID.BED_RIGHT);
						if (logger != null) {
							logger.debug("SharedWorld: Auto-placed bed right at (" + rightX + ", " + packet.y + ")");
						}
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
						currentTable[j][i] = "0";
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

			// Step 3: Check if holding is compatible
			if (!inv.holding.isEmpty()) {
				boolean isTool = craftedItem.getClass() == mc.sayda.mcraze.item.Tool.class ||
						inv.holding.item.getClass() == mc.sayda.mcraze.item.Tool.class;
				boolean differentItem = !craftedItem.itemId.equals(inv.holding.item.itemId);
				if (isTool || differentItem) {
					logger.info("  Cannot craft - holding incompatible item");
					broadcastInventoryUpdates();
					return;
				}

				// CRITICAL FIX: Check if crafted items would fit in holding stack
				int maxStack = isTool ? 1 : 64;
				if (inv.holding.count + craftedCount > maxStack) {
					logger.info("  Cannot craft - would exceed stack limit (" +
							inv.holding.count + " + " + craftedCount + " > " + maxStack + ")");
					broadcastInventoryUpdates();
					return;
				}
			}

			// Step 4: Remove items from crafting grid
			for (int i = 0; i < inv.tableSizeAvailable; i++) {
				for (int j = 0; j < inv.tableSizeAvailable; j++) {
					int gridX = i + inv.inventoryItems.length - inv.tableSizeAvailable;
					int gridY = j;
					inv.inventoryItems[gridX][gridY].count -= 1;
					if (inv.inventoryItems[gridX][gridY].count <= 0) {
						inv.inventoryItems[gridX][gridY].item = null;
						inv.inventoryItems[gridX][gridY].count = 0;
					}
				}
			}

			// Step 5: Add crafted item to holding
			inv.holding.add((mc.sayda.mcraze.item.Item) craftedItem.clone(), craftedCount);
			logger.info("  Crafted " + craftedItem.itemId + " x" + craftedCount);
		} else {
			// Use common slot interaction logic for consistency across all UIs
			handleInventorySlotInteraction(inv, slotX, slotY, !packet.leftClick, logger);
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
					currentTable[j][i] = "0";
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

		// CRITICAL FIX: Handle E key inventory toggle (doesn't require tile validation)
		if (packet.type == PacketInteract.InteractionType.TOGGLE_INVENTORY) {
			// If player has a furnace or chest open, just close it (stop tracking) without
			// toggling inventory
			if (playerConnection.getOpenedFurnaceX() != -1 || playerConnection.getOpenedChestX() != -1) {
				playerConnection.setOpenedFurnace(-1, -1);
				playerConnection.setOpenedChest(-1, -1);
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
		if (packet.type == PacketInteract.InteractionType.OPEN_CRAFTING) {
			// Check if the tile is actually a crafting table
			if (tile.type.name != Constants.TileID.WORKBENCH) {
				return;
			}

			// Enable 3x3 crafting grid for this player
			player.inventory.tableSizeAvailable = 3;

			// Open the inventory UI
			player.inventory.setVisible(true);

			// Broadcast inventory update with crafting UI to all clients
			broadcastInventoryUpdates();
		} else if (packet.type == PacketInteract.InteractionType.OPEN_FURNACE) {
			// Check if tile is a furnace
			if (tile.type.name != Constants.TileID.FURNACE && tile.type.name != Constants.TileID.FURNACE_LIT) {
				return;
			}

			// Track that this player has this furnace open
			playerConnection.setOpenedFurnace(packet.blockX, packet.blockY);

			// Hide regular inventory when opening container
			player.inventory.setVisible(false);

			// Send initial furnace data
			mc.sayda.mcraze.world.storage.FurnaceData furnaceData = world.getOrCreateFurnace(packet.blockX,
					packet.blockY);
			mc.sayda.mcraze.network.packet.PacketFurnaceOpen refreshPacket = new mc.sayda.mcraze.network.packet.PacketFurnaceOpen(
					packet.blockX, packet.blockY,
					furnaceData.items,
					furnaceData.fuelTimeRemaining,
					furnaceData.smeltProgress,
					furnaceData.smeltTimeTotal,
					player.getUUID(),
					false);
			playerConnection.getConnection().sendPacket(refreshPacket);

			// Broadcast inventory state change
			broadcastInventoryUpdates();
		} else if (packet.type == PacketInteract.InteractionType.OPEN_CHEST) {
			// Check if tile is a chest
			if (tile.type.name != Constants.TileID.CHEST) {
				return;
			}

			// Track that this player has this chest open
			playerConnection.setOpenedChest(packet.blockX, packet.blockY);

			// Hide regular inventory when opening container
			player.inventory.setVisible(false);

			// Send initial chest data
			mc.sayda.mcraze.world.storage.ChestData chestData = world.getOrCreateChest(packet.blockX, packet.blockY);
			mc.sayda.mcraze.network.packet.PacketChestOpen refreshPacket = new mc.sayda.mcraze.network.packet.PacketChestOpen(
					packet.blockX, packet.blockY,
					chestData.items,
					player.getUUID());
			playerConnection.getConnection().sendPacket(refreshPacket);

			// Broadcast inventory state change
			broadcastInventoryUpdates();
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
							inv.holding.setItem(furnaceSlot.getItem());
							inv.holding.setCount(furnaceSlot.getCount());
							furnaceSlot.setEmpty();
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

		if (isHotbar) {
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
	public void handleEntityAttack(PlayerConnection playerConnection,
			mc.sayda.mcraze.network.packet.PacketEntityAttack packet) {
		Player attacker = playerConnection.getPlayer();
		if (attacker == null || attacker.dead) {
			return; // Dead players can't attack
		}

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
		// Using PLAYER_REACH tiles as a generous melee range (adjust as needed)
		float maxReach = mc.sayda.mcraze.Constants.PLAYER_REACH;
		float dx = attacker.x - target.x;
		float dy = attacker.y - target.y;
		float distSq = dx * dx + dy * dy;

		// Convert pixel coordinates to tiles?
		// Entity coords are typically in tiles or normalized units.
		// World tiles are ints, Entity x/y are floats.
		// Player.java uses 28/56 pixels which implies x/y might be in tiles.
		// Assuming x,y are in tile units (based on other code like World.addTile(x,y)).
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

		// INVULNERABILITY CHECK: Respect damage cooldown
		if (livingTarget.invulnerabilityTicks > 0) {
			logger.debug("Player " + playerConnection.getPlayerName()
					+ " attack rejected: target invulnerable (" + livingTarget.invulnerabilityTicks + " ticks left)");
			return;
		}

		// Calculate damage from held item
		int damage = 0; // Default: 0 damage (prevent attack unless sword)
		mc.sayda.mcraze.item.InventoryItem heldItem = attacker.inventory.selectedItem();
		boolean isSword = false;

		if (heldItem != null && !heldItem.isEmpty() && heldItem.getItem() instanceof mc.sayda.mcraze.item.Tool) {
			mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) heldItem.getItem();

			// Add tool damage if it's a weapon (Sword)
			if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Sword) {
				damage = tool.attackDamage;
				isSword = true;

				// Damage sword durability (swords lose 1 durability per hit)
				tool.uses++;
				if (tool.uses >= tool.totalUses) {
					// Sword broke
					heldItem.setEmpty();
					broadcastInventoryUpdates();
				}
			}
		}

		// Only allow attacks with Swords
		if (!isSword) {
			// Reject attack
			return;
		}

		// Only apply damage if holding a valid weapon or fist (damage > 0)
		if (damage <= 0) {
			logger
					.debug("Player " + playerConnection.getPlayerName() + " attack rejected: damage=0");
			return;
		}

		// DEBUG: Trace attack
		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("Processing attack from " + playerConnection.getPlayerName() +
					" on " + target.getClass().getSimpleName() + " (" + target.getUUID() + ")");
			logger.debug("Target stats - HP: " + livingTarget.hitPoints +
					", Dead: " + livingTarget.dead +
					", Invuln: " + livingTarget.invulnerabilityTicks);
		}

		// Apply damage to target
		logger.info("Player " + playerConnection.getPlayerName() + " attacking " +
				target.getClass().getSimpleName() + " (UUID: " + target.getUUID() + ") with damage " + damage);
		livingTarget.takeDamage(damage);

		// Play hit sound
		broadcastPacket(new mc.sayda.mcraze.network.packet.PacketPlaySound("hit.wav"));

		// Apply knockback to target (away from attacker)
		// Attacker already defined in scope
		float kbDx = livingTarget.x - attacker.x;
		float kbDy = livingTarget.y - attacker.y;
		float kbDistSq = kbDx * kbDx + kbDy * kbDy;

		// Normalize roughly
		float kbLen = (float) Math.sqrt(kbDistSq);
		if (kbLen > 0) {
			kbDx /= kbLen;
			kbDy /= kbLen;
		} else {
			// Overlapping? push randomly or x-axis
			kbDx = 1.0f;
			kbDy = 0.0f;
		}

		// Apply impulse
		livingTarget.dx += kbDx * 1.2f;
		// livingTarget.dy = -0.3f; // Removed upward knockback

		if (logger != null && logger.isDebugEnabled())
			logger.debug("Post-damage - HP: " + livingTarget.hitPoints + ", Dead: " + livingTarget.dead);

		logger.info("Player " + playerConnection.getPlayerName() + " attacked " +
				target.getClass().getSimpleName() + " for " + damage + " damage");

		// Broadcast entity update to show health change
		broadcastEntityUpdate();

		// Check if target died
		if (livingTarget.dead) {
			logger.info(target.getClass().getSimpleName() + " died from attack");
			if (logger != null && logger.isDebugEnabled())
				logger.debug("Entity died. UUID: " + livingTarget.getUUID());

			if (livingTarget instanceof mc.sayda.mcraze.player.Player) {
				mc.sayda.mcraze.player.Player deadPlayer = (mc.sayda.mcraze.player.Player) livingTarget;
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
					// CRITICAL FIX: Use UUID matching instead of reference equality
					// Ensures we find the correct connection even if entity objects differ
					if (pc.getPlayer() != null && pc.getPlayer().getUUID().equals(deadPlayer.getUUID())) {
						if (logger != null && logger.isDebugEnabled())
							logger.debug("Sending PacketPlayerDeath to " + pc.getPlayerName());
						pc.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketPlayerDeath());
						break;
					}
				}
			} else if (livingTarget instanceof mc.sayda.mcraze.entity.mob.EntitySheep) {
				// Sheep drops white wool (1-3 drops)
				int count = 1 + (int) (Math.random() * 3);
				dropItem("white_wool", livingTarget.x, livingTarget.y, count);
			} else if (livingTarget instanceof mc.sayda.mcraze.entity.mob.EntityPig) {
				// Pig drops pork (1-2 drops)
				int count = 1 + (int) (Math.random() * 2);
				dropItem("pork", livingTarget.x, livingTarget.y, count);
			} else if (livingTarget instanceof mc.sayda.mcraze.entity.mob.EntityCow) {
				// Cow drops beef and leather (1-2 drops)
				int count = 1 + (int) (Math.random() * 2);
				dropItem("beef", livingTarget.x, livingTarget.y, count);
				dropItem("leather", livingTarget.x, livingTarget.y, count);
			} else if (livingTarget instanceof mc.sayda.mcraze.entity.mob.EntityZombie) {
				// Zombie Drop 1: Iron (20% chance)
				if (Math.random() < 0.2f) {
					dropItem("iron", livingTarget.x, livingTarget.y, 1);
				}
				// Zombie Drop 2: Rotten Flesh (Guaranteed 1-2 drops)
				int count = 1 + (int) (Math.random() * 2); // 1-2
				dropItem("rotten_flesh", livingTarget.x, livingTarget.y, count);
			}

			// Remove entity
			entityManager.remove(livingTarget);

			// Broadcast removal
			mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket = new mc.sayda.mcraze.network.packet.PacketEntityRemove(
					livingTarget.getUUID());
			broadcastPacket(removePacket);
			if (logger != null)
				logger.info("Entity " + livingTarget.getClass().getSimpleName() + " died and was removed");
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

		// Get spawn coordinates from world
		float spawnX = world.spawnLocation.x;
		float spawnY = world.spawnLocation.y;

		if (logger != null) {
			logger.info("SharedWorld.respawnPlayer: Respawning " + playerConn.getPlayerName() +
					" at (" + spawnX + ", " + spawnY + ")");
		}

		// Respawn the player on the server
		player.respawn(spawnX, spawnY);

		// Broadcast immediate respawn state to all clients
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

		// Remove the tile
		Constants.TileID broken = world.removeTile(x, y);

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
			if (random.nextDouble() < 0.1) {
				dropId = Constants.TileID.SAPLING;
			}
			// Apple drops (independent chance)
			if (random.nextDouble() < 0.05) { // 5% chance
				Item apple = Constants.itemTypes.get("apple").clone();
				apple.x = x + random.nextFloat() * 0.5f;
				apple.y = y + random.nextFloat() * 0.5f;
				apple.dy = -0.07f;
				entityManager.add(apple);
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
}
