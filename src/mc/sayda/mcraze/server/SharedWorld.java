package mc.sayda.mcraze.server;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.entity.Player;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketBlockChange;
import mc.sayda.mcraze.network.packet.PacketBreakingProgress;
import mc.sayda.mcraze.network.packet.PacketChestOpen;
import mc.sayda.mcraze.network.packet.PacketEntityUpdate;
import mc.sayda.mcraze.network.packet.PacketInteract;
import mc.sayda.mcraze.network.packet.PacketInventoryUpdate;
import mc.sayda.mcraze.network.packet.PacketPlayerDeath;
import mc.sayda.mcraze.network.packet.PacketWorldInit;
import mc.sayda.mcraze.network.packet.PacketWorldUpdate;
import mc.sayda.mcraze.world.Tile;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.world.WorldAccess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SharedWorld manages a single world instance shared by multiple players.
 * Handles world ticking, entity updates, and broadcasting to all connected clients.
 */
public class SharedWorld {
	private World world;
	private WorldAccess worldAccess;
	private List<PlayerConnection> players;
	private Random random;
	private boolean running = true;
	private long ticksRunning = 0;
	private boolean daylightCycle = true;
	private int tileSize = Constants.TILE_SIZE;

	// Per-player breaking state
	private HashMap<Player, BreakingState> playerBreakingState = new HashMap<>();

	// World entities (items, etc.) not associated with a specific player - thread-safe
	private final EntityManager entityManager = new EntityManager();

	// Authentication tracking
	private HashMap<String, PlayerConnection> playersByUsername = new HashMap<>();
	private String worldName;

	// Command handler (for integrated server only)
	private mc.sayda.mcraze.ui.CommandHandler commandHandler;

	public SharedWorld(int worldWidth, Random random, String worldName) {
		GameLogger logger = GameLogger.get();
		this.random = random;
		this.worldName = worldName;
		this.players = new CopyOnWriteArrayList<>();  // Thread-safe for concurrent access

		// Generate world
		System.out.println("SharedWorld: Generating world '" + worldName + "' (" + worldWidth + " wide)...");
		if (logger != null) {
			logger.info("SharedWorld: Generating world '" + worldName + "' (" + worldWidth + " wide)");
			logger.debug("SharedWorld.<init>: Using CopyOnWriteArrayList for thread-safe player list");
		}

		this.world = new World(worldWidth, worldWidth, random);
		this.worldAccess = new WorldAccess(world);
		System.out.println("SharedWorld: World generated");
		System.out.println("SharedWorld: Constructor - world instance: " + System.identityHashCode(world));
		System.out.println("SharedWorld: Constructor - world.tiles instance: " + System.identityHashCode(world.tiles));
		System.out.println("SharedWorld: Constructor - world.tiles.length: " + world.tiles.length);
		if (logger != null) {
			logger.info("SharedWorld: World generated successfully - " + worldWidth + "x" + worldWidth);
			logger.debug("SharedWorld.<init>: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
			logger.debug("SharedWorld.<init>: WorldAccess wrapper created for thread-safe tile access");
		}
	}

	/**
	 * Constructor for loading an existing world (used by dedicated server)
	 * @param world Pre-loaded world instance
	 * @param random Random instance for game logic
	 * @param worldName Name of the world (for playerdata)
	 */
	public SharedWorld(World world, Random random, String worldName) {
		GameLogger logger = GameLogger.get();
		this.random = random;
		this.worldName = worldName;
		this.players = new CopyOnWriteArrayList<>();
		this.world = world;
		this.worldAccess = new WorldAccess(world);

		System.out.println("SharedWorld: Loaded existing world '" + worldName + "' (" + world.width + "x" + world.height + ")");
		if (logger != null) {
			logger.info("SharedWorld: Loaded existing world '" + worldName + "' - " + world.width + "x" + world.height);
			logger.debug("SharedWorld.<init>: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
			logger.debug("SharedWorld.<init>: WorldAccess wrapper created for thread-safe tile access");
		}
	}

	/**
	 * Add a player to the shared world with authentication
	 * @return PlayerConnection if successful, null if authentication failed or duplicate username
	 */
	public PlayerConnection addPlayer(Connection connection, String username, String password) {
		GameLogger logger = GameLogger.get();
		System.out.println("SharedWorld: Adding player: " + username);
		if (logger != null) {
			logger.info("SharedWorld: Adding player: " + username);
			logger.debug("SharedWorld.addPlayer: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
		}

		// Check for duplicate username
		if (playersByUsername.containsKey(username)) {
			System.err.println("SharedWorld: Player " + username + " is already connected!");
			if (logger != null) logger.warn("SharedWorld: Rejected duplicate username: " + username);
			return null;
		}

		// Authenticate or auto-register
		mc.sayda.mcraze.world.PlayerData playerData =
			mc.sayda.mcraze.world.PlayerDataManager.authenticate(worldName, username, password);

		if (playerData == null && mc.sayda.mcraze.world.PlayerDataManager.exists(worldName, username)) {
			// Wrong password
			System.err.println("SharedWorld: Wrong password for " + username);
			if (logger != null) logger.warn("SharedWorld: Authentication failed for " + username);
			return null;
		}

		// Create player entity
		if (logger != null) logger.debug("SharedWorld.addPlayer: Creating player entity...");
		Player player = new Player(
			true,
			world.spawnLocation.x,
			world.spawnLocation.y,
			7 * 4,
			14 * 4
		);
		player.username = username;  // Set player's display name
		if (logger != null) logger.debug("SharedWorld.addPlayer: Player entity created for " + username);

		// Load or create playerdata
		if (playerData == null) {
			// Auto-register new player
			playerData = new mc.sayda.mcraze.world.PlayerData(
				username, password, world.spawnLocation.x, world.spawnLocation.y);
			mc.sayda.mcraze.world.PlayerDataManager.save(worldName, playerData);
			System.out.println("SharedWorld: Auto-registered new player: " + username);
			if (logger != null) logger.info("SharedWorld: Auto-registered new player: " + username);
		} else {
			// Apply existing playerdata
			mc.sayda.mcraze.world.PlayerDataManager.applyToPlayer(playerData, player);
			System.out.println("SharedWorld: Loaded playerdata for " + username);
			if (logger != null) logger.info("SharedWorld: Loaded playerdata for " + username);
		}

		// Create player connection
		if (logger != null) logger.debug("SharedWorld.addPlayer: Creating PlayerConnection...");
		PlayerConnection playerConnection = new PlayerConnection(connection, player, username, password, this);
		if (logger != null) logger.debug("SharedWorld.addPlayer: PlayerConnection created");

		// Add to tracking maps
		if (logger != null) logger.debug("SharedWorld.addPlayer: Adding to players list...");
		players.add(playerConnection);
		playersByUsername.put(username, playerConnection);
		if (logger != null) logger.debug("SharedWorld.addPlayer: Added to players list (size now: " + players.size() + ")");

		// Send initial world state to this player
		if (logger != null) logger.debug("SharedWorld.addPlayer: Sending initial world state...");
		sendInitialWorldState(playerConnection);
		if (logger != null) logger.debug("SharedWorld.addPlayer: Initial world state sent");

		System.out.println("SharedWorld: Player added (" + players.size() + " total players)");
		if (logger != null) logger.info("SharedWorld: Player " + username + " successfully added (" + players.size() + " total)");
		return playerConnection;
	}

	/**
	 * Remove a player from the shared world
	 */
	public void removePlayer(PlayerConnection playerConnection) {
		GameLogger logger = GameLogger.get();
		System.out.println("SharedWorld: Removing player: " + playerConnection.getPlayerName());
		if (logger != null) logger.info("SharedWorld: Removing player: " + playerConnection.getPlayerName());

		// Save playerdata before removing
		savePlayerData(playerConnection);

		// Remove player entity from entity manager
		Player playerEntity = playerConnection.getPlayer();
		if (playerEntity != null) {
			entityManager.remove(playerEntity);
			System.out.println("SharedWorld: Removed player entity for " + playerConnection.getPlayerName());
			if (logger != null) logger.info("SharedWorld: Removed player entity for " + playerConnection.getPlayerName());

			// Broadcast entity removal to all clients
			mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket =
				new mc.sayda.mcraze.network.packet.PacketEntityRemove(playerEntity.getUUID());
			broadcastPacket(removePacket);
			if (logger != null) logger.debug("SharedWorld: Broadcasted entity removal for UUID: " + playerEntity.getUUID());
		}

		// Remove from tracking
		players.remove(playerConnection);
		playersByUsername.remove(playerConnection.getPlayerName());

		System.out.println("SharedWorld: Player removed (" + players.size() + " remaining players)");
		if (logger != null) logger.info("SharedWorld: Player removed (" + players.size() + " remaining)");
	}

	/**
	 * Save playerdata for a specific player
	 */
	public void savePlayerData(PlayerConnection playerConnection) {
		if (worldName == null || playerConnection == null) return;

		String username = playerConnection.getPlayerName();
		String password = playerConnection.getPassword();
		Player player = playerConnection.getPlayer();

		if (player != null) {
			mc.sayda.mcraze.world.PlayerData data =
				mc.sayda.mcraze.world.PlayerDataManager.extractFromPlayer(username, password, player);
			boolean saved = mc.sayda.mcraze.world.PlayerDataManager.save(worldName, data);

			if (saved) {
				System.out.println("SharedWorld: Saved playerdata for " + username);
			} else {
				System.err.println("SharedWorld: Failed to save playerdata for " + username);
			}
		}
	}

	/**
	 * Send initial world state to a newly connected player
	 */
	private void sendInitialWorldState(PlayerConnection playerConnection) {
		GameLogger logger = GameLogger.get();
		System.out.println("SharedWorld: Sending initial world state to " + playerConnection.getPlayerName());
		if (logger != null) {
			logger.info("SharedWorld: Sending initial world state to " + playerConnection.getPlayerName());
			logger.debug("SharedWorld.sendInitialWorldState: World dimensions: " + world.width + "x" + world.height);
		}

		// Send all non-air tiles (count first to determine total packets)
		// Use WorldAccess for thread-safe tile iteration
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Collecting non-air tiles...");
		ArrayList<Integer> changedX = new ArrayList<>();
		ArrayList<Integer> changedY = new ArrayList<>();
		ArrayList<Character> changedTiles = new ArrayList<>();

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int tilesAdded = 0;

		int worldWidth = worldAccess.getWidth();
		int worldHeight = worldAccess.getHeight();

		for (int x = 0; x < worldWidth; x++) {
			for (int y = 0; y < worldHeight; y++) {
				Tile tile = worldAccess.getTile(x, y);
				if (tile != null && tile.type != null && tile.type.name != Constants.TileID.AIR) {
					changedX.add(x);
					changedY.add(y);
					changedTiles.add((char) tile.type.name.ordinal());

					tilesAdded++;

					// Track min/max coordinates
					if (x < minX) minX = x;
					if (x > maxX) maxX = x;
					if (y < minY) minY = y;
					if (y > maxY) maxY = y;
				}
			}
		}
		if (logger != null) {
			logger.debug("SharedWorld.sendInitialWorldState: Collected " + changedX.size() + " non-air tiles");
			logger.info("SharedWorld: Tile range - X:" + minX + "-" + maxX + ", Y:" + minY + "-" + maxY);
		}

		// Calculate total packets (world update chunks + biome data + entity update)
		int chunkSize = 1000;
		int numChunks = (int) Math.ceil((double) changedX.size() / chunkSize);
		int totalPackets = 1 + numChunks + 1;  // +1 biome, +numChunks world, +1 entity

		// Send world initialization packet with dimensions and spawn location
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sending PacketWorldInit...");

		// Get this player's UUID
		String playerUUID = playerConnection.getPlayer().getUUID();
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Player UUID: " + playerUUID);

		PacketWorldInit initPacket = new PacketWorldInit(
			world.width,
			world.height,
			0,
			world.spawnLocation.x,
			world.spawnLocation.y
		);
		initPacket.playerUUID = playerUUID;
		initPacket.totalPacketsExpected = totalPackets;
		// Set gamerules from world
		initPacket.spelunking = world.spelunking;
		initPacket.keepInventory = world.keepInventory;
		initPacket.daylightCycle = world.daylightCycle;
		playerConnection.getConnection().sendPacket(initPacket);
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: PacketWorldInit sent with playerUUID=" + playerUUID + ", totalPackets=" + totalPackets);

		// Send biome data immediately after world init
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sending PacketBiomeData...");
		mc.sayda.mcraze.network.packet.PacketBiomeData biomePacket =
			new mc.sayda.mcraze.network.packet.PacketBiomeData(world.getBiomeMap());
		playerConnection.getConnection().sendPacket(biomePacket);
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: PacketBiomeData sent (" +
			(world.getBiomeMap() != null ? world.getBiomeMap().length : 0) + " biomes)");

		// Send in chunks of 1000 tiles
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sending " + numChunks + " chunks of world data...");

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
			if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sent chunk " + chunkNum + "/" + numChunks + " (" + (end - i) + " tiles)");
		}
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: All world data chunks sent");

		// Send backdrop tiles
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Collecting backdrop tiles...");
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

		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Collected " + backdropX.size() + " backdrop tiles");

		// Send backdrops in chunks of 1000
		int backdropChunks = (int) Math.ceil((double) backdropX.size() / chunkSize);
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sending " + backdropChunks + " chunks of backdrop data...");

		for (int i = 0; i < backdropX.size(); i += chunkSize) {
			int end = Math.min(i + chunkSize, backdropX.size());
			int chunkNum = (i / chunkSize) + 1;

			// Send each backdrop tile individually (PacketBackdropChange is designed for single tiles)
			for (int j = i; j < end; j++) {
				mc.sayda.mcraze.network.packet.PacketBackdropChange backdropPacket =
					new mc.sayda.mcraze.network.packet.PacketBackdropChange(
						backdropX.get(j),
						backdropY.get(j),
						Constants.TileID.values()[backdropTiles.get(j)]
					);
				playerConnection.getConnection().sendPacket(backdropPacket);
			}

			if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sent backdrop chunk " + chunkNum + "/" + backdropChunks + " (" + (end - i) + " backdrops)");
		}

		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: All backdrop data sent (" + backdropX.size() + " backdrops)");

		// Send initial entity update DIRECTLY to this player (not broadcast)
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Sending initial entity update directly to " + playerConnection.getPlayerName());
		sendEntityUpdateToPlayer(playerConnection);
		if (logger != null) {
			logger.info("SharedWorld.sendInitialWorldState: Initial entity update sent to " + playerConnection.getPlayerName());
			logger.info("SharedWorld: Initial world state complete for " + playerConnection.getPlayerName() + " (" + changedX.size() + " tiles)");
		}
	}

	/**
	 * Send entity update to a specific player
	 */
	private void sendEntityUpdateToPlayer(PlayerConnection playerConnection) {
		GameLogger logger = GameLogger.get();
		List<Entity> allEntities = getAllEntities();
		if (allEntities.isEmpty()) {
			if (logger != null) logger.warn("SharedWorld.sendEntityUpdateToPlayer: No entities to send!");
			return;
		}

		if (logger != null) {
			logger.debug("SharedWorld.sendEntityUpdateToPlayer: Sending " + allEntities.size() + " entities to " + playerConnection.getPlayerName());
		}

		PacketEntityUpdate packet = new PacketEntityUpdate();

		int entityCount = allEntities.size();
		packet.entityIds = new int[entityCount];
		packet.entityTypes = new String[entityCount];
		packet.entityUUIDs = new String[entityCount];
		packet.entityX = new float[entityCount];
		packet.entityY = new float[entityCount];
		packet.entityDX = new float[entityCount];
		packet.entityDY = new float[entityCount];
		packet.entityHealth = new int[entityCount];
		packet.facingRight = new boolean[entityCount];
		packet.dead = new boolean[entityCount];
		packet.ticksAlive = new long[entityCount];
		packet.itemIds = new String[entityCount];
		packet.playerNames = new String[entityCount];

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
				packet.playerNames[i] = ((Player) entity).username;  // Send player name
			} else if (entity instanceof Item) {
				packet.entityTypes[i] = "Item";
				packet.itemIds[i] = ((Item) entity).itemId;
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
			} else {
				packet.entityHealth[i] = 0;
				packet.facingRight[i] = true;
				packet.dead[i] = false;
				packet.ticksAlive[i] = 0;
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
			logger.info("SharedWorld.sendEntityUpdateToPlayer: Packet sent successfully to " + playerConnection.getPlayerName());
		}
	}

	/**
	 * Main world tick - updates world and all entities
	 */
	public void tick() {
		GameLogger logger = GameLogger.get();
		if (!running) return;

		ticksRunning++;

		// Only log first few ticks in DEBUG mode
		if (logger != null && ticksRunning <= 10) {
			logger.debug("SharedWorld.tick: Tick " + ticksRunning + " - " + players.size() + " players");
		}

		// Process packets from all players
		if (logger != null && ticksRunning <= 5) {
			logger.debug("SharedWorld.tick: Processing packets from " + players.size() + " players...");
		}
		for (PlayerConnection playerConnection : players) {
			playerConnection.processPackets();
		}
		if (logger != null && ticksRunning <= 5) {
			logger.debug("SharedWorld.tick: Packets processed");
		}

		// Update world
		if (world != null) {
			world.chunkUpdate(daylightCycle);
		}

		// Update all player entities
		List<Entity> allEntities = getAllEntities();
		if (logger != null && ticksRunning <= 5) {
			logger.debug("SharedWorld.tick: Updating " + allEntities.size() + " entities...");
		}

		// Track entities to remove from worldEntities after iteration
		List<Entity> entitiesToRemove = new ArrayList<>();

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
							// Item fully picked up - mark for removal from worldEntities
							entitiesToRemove.add(entity);
							pickedUp = true;
							pickupPlayer = pc;
							if (logger != null && ticksRunning <= 10) {
								logger.debug("SharedWorld.tick: Player " + pc.getPlayerName() + " picked up " + item.itemId);
							}
							break;
						}
					}
				}
				if (pickedUp) {
					// Broadcast inventory update immediately after pickup
					broadcastInventoryUpdates();
					continue;  // Skip position update for removed item
				}
			}

			// Update entity
			entity.updatePosition(world, tileSize);

			// Check for death
			if (entity instanceof LivingEntity) {
				LivingEntity livingEntity = (LivingEntity) entity;
				if (livingEntity.hitPoints <= 0 && !livingEntity.dead) {
					livingEntity.dead = true;
					if (logger != null) logger.info("SharedWorld.tick: Entity died - type: " + entity.getClass().getSimpleName());

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
								System.out.println("Player " + deadPlayer.username + " died and dropped " + droppedItems.size() + " items");

								// Broadcast empty inventory after death
								broadcastInventoryUpdates();

								break;
							}
						}
					}
				}
			}
		}

		// Remove picked-up items using thread-safe EntityManager
		if (!entitiesToRemove.isEmpty()) {
			// Send entity removal packets immediately to all clients
			for (Entity entity : entitiesToRemove) {
				mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket =
					new mc.sayda.mcraze.network.packet.PacketEntityRemove();
				removePacket.entityUUID = entity.getUUID();
				broadcastPacket(removePacket);

				// Remove from EntityManager (thread-safe)
				entityManager.remove(entity);
			}
			if (logger != null && ticksRunning <= 10) {
				logger.debug("SharedWorld.tick: Removed " + entitiesToRemove.size() + " entities from world");
			}
		}

		if (logger != null && ticksRunning <= 5) {
			logger.debug("SharedWorld.tick: Entities updated");
		}

		// Broadcast entity updates every tick (60Hz) for smooth client-side rendering
		// This ensures clients see the same smooth motion as the host
		if (logger != null && ticksRunning <= 15) {
			logger.debug("SharedWorld.tick: Broadcasting entity update (tick " + ticksRunning + ")");
		}
		broadcastEntityUpdate();
		// NOTE: Inventory updates are NOT broadcast on timer - only when inventory actually changes
		// This prevents packet spam. Inventory is broadcast via handleBlockChange, handleInventoryAction, etc.

		// Broadcast world time every 20 ticks (~3 times per second) for smooth daylight updates
		// This ensures clients see daylight changes even without block updates
		if (ticksRunning % 20 == 0) {
			broadcastWorldTime();
		}

		// Auto-save all players every 6000 ticks (5 minutes at 20 TPS)
		if (ticksRunning % 6000 == 0) {
			System.out.println("SharedWorld: Auto-saving all players...");
			for (PlayerConnection pc : players) {
				savePlayerData(pc);
			}
			if (logger != null) logger.info("SharedWorld: Auto-saved " + players.size() + " players");
		}

		// FARMING: Random tick crop growth every 100 ticks
		// This checks a few random blocks for crop growth opportunities
		if (ticksRunning % 100 == 0 && world != null) {
			randomTickCropGrowth();
		}
	}

	/**
	 * FARMING: Random tick crop growth system
	 * Checks a few random blocks each tick for crop growth opportunities
	 * Currently only tracks wheat crops, but can be extended for other crops
	 */
	private void randomTickCropGrowth() {
		if (world == null || world.tiles == null) return;

		int worldWidth = world.width;
		int worldHeight = world.height;

		// Check 3 random blocks per random tick (adjustable for performance)
		int blocksToCheck = 3;

		for (int i = 0; i < blocksToCheck; i++) {
			// Pick random coordinates
			int x = random.nextInt(worldWidth);
			int y = random.nextInt(worldHeight);

			// Check if it's a wheat crop
			Tile tile = world.tiles[x][y];
			if (tile != null && tile.type != null && tile.type.name == Constants.TileID.WHEAT_CROP) {
				// Crop found! For now, crops are already fully grown when placed (only one sprite)
				// This is where you would implement growth stages:
				// - Check crop metadata for growth stage
				// - Advance to next stage with some probability
				// - Update tile sprite based on stage
				// - When fully grown, allow harvesting

				// Currently: Wheat crops are instantly "mature" when planted
				// No action needed, but this infrastructure is ready for growth stages
			}
		}
	}

	/**
	 * Get all entities from all players and world entities (dropped items, etc.)
	 * Returns a thread-safe snapshot using EntityManager
	 */
	public List<Entity> getAllEntities() {
		List<Entity> allEntities = new ArrayList<>();
		for (PlayerConnection playerConnection : players) {
			allEntities.add(playerConnection.getPlayer());
		}
		// Add world entities (dropped items, etc.) - thread-safe iteration
		allEntities.addAll(entityManager.getAll());
		return allEntities;
	}

	/**
	 * Broadcast entity update to all players
	 */
	public void broadcastEntityUpdate() {
		GameLogger logger = GameLogger.get();
		List<Entity> allEntities = getAllEntities();
		if (allEntities.isEmpty()) {
			// Don't spam when no entities
			return;
		}

		if (logger != null && ticksRunning <= 15) {
			logger.debug("SharedWorld.broadcastEntityUpdate: Broadcasting " + allEntities.size() + " entities");
		}

		PacketEntityUpdate packet = new PacketEntityUpdate();

		int entityCount = allEntities.size();
		packet.entityIds = new int[entityCount];
		packet.entityTypes = new String[entityCount];
		packet.entityUUIDs = new String[entityCount];
		packet.entityX = new float[entityCount];
		packet.entityY = new float[entityCount];
		packet.entityDX = new float[entityCount];
		packet.entityDY = new float[entityCount];
		packet.entityHealth = new int[entityCount];
		packet.facingRight = new boolean[entityCount];
		packet.dead = new boolean[entityCount];
		packet.ticksAlive = new long[entityCount];
		packet.itemIds = new String[entityCount];
		packet.playerNames = new String[entityCount];

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
				packet.playerNames[i] = ((Player) entity).username;  // Send player name
			} else if (entity instanceof Item) {
				packet.entityTypes[i] = "Item";
				packet.itemIds[i] = ((Item) entity).itemId;
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
			} else {
				packet.entityHealth[i] = 0;
				packet.facingRight[i] = true;
				packet.dead[i] = false;
				packet.ticksAlive[i] = 0;
			}

			// Log first entity details for first few broadcasts
			if (logger != null && ticksRunning <= 5 && i == 0) {
				logger.debug("SharedWorld.broadcastEntityUpdate: Entity 0 pos: " + entity.x + ", " + entity.y + " | vel: " + entity.dx + ", " + entity.dy);
			}
		}

		// Broadcast to all players
		if (logger != null && ticksRunning <= 15) {
			logger.debug("SharedWorld.broadcastEntityUpdate: Broadcasting to " + players.size() + " players");
		}
		broadcast(packet);
		if (logger != null && ticksRunning <= 15) {
			logger.debug("SharedWorld.broadcastEntityUpdate: Broadcast complete");
		}
	}

	/**
	 * Broadcast inventory updates for all players
	 */
	public void broadcastInventoryUpdates() {
		// Send each player's inventory to all clients
		for (PlayerConnection playerConnection : players) {
			Player player = playerConnection.getPlayer();
			if (player == null || player.inventory == null) {
				continue;
			}

			PacketInventoryUpdate packet = new PacketInventoryUpdate();

			// Set player UUID so clients know whose inventory this is
			packet.playerUUID = player.getUUID();

			mc.sayda.mcraze.ui.Inventory inv = player.inventory;
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

			// Broadcast to all players
			broadcast(packet);
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
		mc.sayda.mcraze.network.packet.PacketBackdropChange packet =
			new mc.sayda.mcraze.network.packet.PacketBackdropChange(x, y, backdropId);
		broadcast(packet);
	}

	/**
	 * Broadcast gamerule changes to all connected players
	 */
	public void broadcastGamerules() {
		mc.sayda.mcraze.network.packet.PacketGameruleUpdate packet =
			new mc.sayda.mcraze.network.packet.PacketGameruleUpdate(
				world.spelunking,
				world.keepInventory,
				world.daylightCycle
			);
		broadcast(packet);
	}

	/**
	 * Broadcast world time (for daylight cycle) without block changes
	 */
	private void broadcastWorldTime() {
		PacketWorldUpdate packet = new PacketWorldUpdate();
		packet.ticksAlive = world.getTicksAlive();
		packet.changedX = new int[0];  // No block changes
		packet.changedY = new int[0];
		packet.changedTiles = new char[0];

		broadcast(packet);
	}

	/**
	 * Broadcast a packet to all connected players
	 */
	private void broadcast(Object packet) {
		for (PlayerConnection playerConnection : players) {
			if (playerConnection.getConnection().isConnected()) {
				playerConnection.getConnection().sendPacket((mc.sayda.mcraze.network.Packet) packet);
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
	 * Get or create breaking state for a player
	 */
	private BreakingState getBreakingState(Player player) {
		return playerBreakingState.computeIfAbsent(player, p -> new BreakingState());
	}

	/**
	 * Break floating unstable blocks when the block below is removed.
	 * Unstable blocks (plants, torches, etc.) require specific support blocks below them.
	 * When a supporting block is removed, the unstable block above should break and drop items.
	 */
	private void breakFloatingPlants(int x, int y) {
		GameLogger logger = GameLogger.get();

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
							if (logger != null) logger.debug("SharedWorld: " + blockAbove + " broke at (" + x + ", " + aboveY + "), dropped item");
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
		if (unstableBlock == Constants.TileID.WHEAT_CROP) {
			return support == Constants.TileID.FARMLAND;
		}

		// Other unstable blocks (flowers, saplings, tall grass, torches) need dirt or grass
		return support == Constants.TileID.DIRT || support == Constants.TileID.GRASS;
	}

	/**
	 * Handle a block change from a player
	 */
	public void handleBlockChange(PlayerConnection playerConnection, PacketBlockChange packet) {
		GameLogger logger = GameLogger.get();
		Player player = playerConnection.getPlayer();

		if (player == null) {
			return;
		}

		// Dead players cannot break or place blocks
		if (player.dead) {
			return;
		}

		// Get per-player breaking state
		BreakingState breakingState = getBreakingState(player);

		if (packet.isBreak) {
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
				return;  // Don't break foreground
			}

			// FOREGROUND MODE: Normal block breaking (existing code)
			// Check if target is breakable (still safe - reads are thread-safe)
			if (!world.isBreakable(packet.x, packet.y)) {
				breakingState.reset();
				return;
			}

			// Track breaking progress
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

			// Send breaking progress to this player (ISSUE #3 fix)
			playerConnection.getConnection().sendPacket(new PacketBreakingProgress(packet.x, packet.y,
				breakingState.ticks, ticksNeeded));

			// Only break when enough ticks have passed
			if (breakingState.ticks < ticksNeeded) {
				return;  // Not ready to break yet
			}

			// Block is broken - reset counter and notify client
			breakingState.reset();
			playerConnection.getConnection().sendPacket(new PacketBreakingProgress(packet.x, packet.y, 0, 0));

			// Handle tool durability
			if (currentItem != null && currentItem instanceof Tool) {
				Tool tool = (Tool) currentItem;
				tool.uses++;
				if (tool.uses >= tool.totalUses) {
					player.inventory.selectedItem().setEmpty();
					// Immediately sync inventory after tool breaks (ISSUE #10 fix)
					broadcastInventoryUpdates();
				}
			}

			// Remove the tile
			Constants.TileID broken = world.removeTile(packet.x, packet.y);

			// Handle chest breaking - drop all items inside
			if (broken == Constants.TileID.CHEST) {
				mc.sayda.mcraze.world.ChestData chestData = world.getChest(packet.x, packet.y);
				if (chestData != null) {
					// Drop all items from the chest
					for (int cx = 0; cx < 9; cx++) {
						for (int cy = 0; cy < 3; cy++) {
							mc.sayda.mcraze.item.InventoryItem invItem = chestData.items[cx][cy];
							if (invItem != null && !invItem.isEmpty()) {
								// Drop all items from this stack
								for (int i = 0; i < invItem.getCount(); i++) {
									Item droppedItem = invItem.getItem().clone();
									droppedItem.x = packet.x + random.nextFloat() * 0.8f + 0.1f;
									droppedItem.y = packet.y + random.nextFloat() * 0.8f + 0.1f;
									droppedItem.dy = -0.05f - random.nextFloat() * 0.05f;
									entityManager.add(droppedItem);
								}
							}
						}
					}
					// Remove chest data from world
					world.removeChest(packet.x, packet.y);
					if (logger != null) {
						logger.debug("SharedWorld: Chest broken at (" + packet.x + ", " + packet.y + "), items dropped");
					}
				}
			}

			// Convert certain blocks when broken (like in BlockInteractionSystem)
			Constants.TileID dropId = broken;
			if (broken == Constants.TileID.GRASS) {
				dropId = Constants.TileID.DIRT;
			}
			if (broken == Constants.TileID.STONE) {
				dropId = Constants.TileID.COBBLE;
			}
			if (broken == Constants.TileID.LEAVES && random.nextDouble() < 0.1) {
				dropId = Constants.TileID.SAPLING;
			}

			// Drop items if block was broken - use thread-safe EntityManager
			if (dropId != null && dropId.itemDropId != null) {
				Item droppedItem = Constants.itemTypes.get(dropId.itemDropId);
				if (droppedItem != null) {
					Item item = droppedItem.clone();
					item.x = packet.x + random.nextFloat() * 0.5f;
					item.y = packet.y + random.nextFloat() * 0.5f;
					item.dy = -0.07f;  // Give it upward velocity
					entityManager.add(item);
					if (logger != null) logger.debug("SharedWorld: Dropped item " + item.itemId + " at (" + item.x + ", " + item.y + ")");
				}
			}

			// Broadcast change to all players
			broadcastBlockChange(packet.x, packet.y, null);

			// PLANT PHYSICS: Check if breaking this block causes plants above to break
			breakFloatingPlants(packet.x, packet.y);

		} else {
			// Check if clicking on a crafting table FIRST (before parsing packet data)
			if (world.isCraft(packet.x, packet.y)) {
				// Open inventory with 3x3 crafting
				player.inventory.tableSizeAvailable = 3;
				player.inventory.setVisible(true);
				broadcastInventoryUpdates();  // Immediately sync inventory state
				return;
			}

			// Check if clicking on a chest block
			Tile clickedTile = worldAccess.getTile(packet.x, packet.y);
			if (clickedTile != null && clickedTile.type != null &&
			    clickedTile.type.name == Constants.TileID.CHEST) {
				// Get or create chest data
				mc.sayda.mcraze.world.ChestData chestData = world.getOrCreateChest(packet.x, packet.y);

				// Send chest open packet to this player
				PacketChestOpen chestPacket = new PacketChestOpen(packet.x, packet.y, chestData.items);
				playerConnection.getConnection().sendPacket(chestPacket);

				if (logger != null) {
					logger.info("SharedWorld: Player " + player.username +
						" opened chest at (" + packet.x + ", " + packet.y + ")");
				}
				return;
			}

			// Block placing - validate and apply
			Constants.TileID tileId = null;
			if (packet.newTileId >= 0 && packet.newTileId < Constants.TileID.values().length) {
				tileId = Constants.TileID.values()[packet.newTileId];
			}

			if (tileId == null || tileId == Constants.TileID.AIR || tileId == Constants.TileID.NONE) {
				return;  // Cannot place air or invalid tiles
			}

			// FARMING: Special handling for hoes and seeds
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
									logger.debug("Player " + player.username + "'s hoe broke after " + hoe.uses + " uses");
								}
								// Broadcast inventory update to sync tool durability
								broadcastInventoryUpdates();
							}

							return;  // Don't proceed with normal placement
						}
					}
				}
				// SEED PLANTING: Place wheat_crop on farmland
				else if (selectedItem.itemId.equals("wheat_seeds")) {
					// Check if block below is farmland
					int belowY = packet.y + 1;  // Y increases downward
					if (belowY < world.height) {
						Tile belowTile = worldAccess.getTile(packet.x, belowY);
						if (belowTile != null && belowTile.type != null &&
						    belowTile.type.name == Constants.TileID.FARMLAND) {
							// Can only plant if position is empty
							if (!world.isBreakable(packet.x, packet.y)) {
								// Place wheat crop
								if (world.addTile(packet.x, packet.y, Constants.TileID.WHEAT_CROP)) {
									player.inventory.decreaseSelected(1);
									broadcastInventoryUpdates();
									broadcastBlockChange(packet.x, packet.y, Constants.TileID.WHEAT_CROP);
								}
							}
							return;  // Don't proceed with normal placement
						}
						// If not on farmland, seeds will deny placement
						//player.inventory.decreaseSelected(1);
						broadcastInventoryUpdates();
						return;
					}
				}
			}

			// BACKDROP MODE: Place backdrop instead of foreground
			if (player.backdropPlacementMode) {
				// Can only place backdrop where there's empty space (no solid block)
				if (!world.isBreakable(packet.x, packet.y)) {
					return;  // Cannot place backdrop on solid blocks
				}

				// Place backdrop
				if (world.setBackdrop(packet.x, packet.y, tileId)) {
					player.inventory.decreaseSelected(1);
					broadcastInventoryUpdates();
					broadcastBackdropChange(packet.x, packet.y, tileId);
				}
				return;  // Don't place foreground
			}

			// FOREGROUND MODE: Normal block placement (existing code)
			// Can only place if the target position is empty (not a solid block)
			if (world.isBreakable(packet.x, packet.y)) {
				return;  // Cannot place - there's already a block here
			}

			// Check if there's at least one adjacent block (up, down, left, right)
			boolean hasAdjacentBlock = world.isBreakable(packet.x - 1, packet.y) || // left
					world.isBreakable(packet.x + 1, packet.y) || // right
					world.isBreakable(packet.x, packet.y - 1) || // up
					world.isBreakable(packet.x, packet.y + 1);   // down

			if (!hasAdjacentBlock) {
				return;  // Cannot place - no adjacent block
			}

			// Check if the block would collide with the player
			boolean isPassable = Constants.tileTypes.get(tileId).type.passable;
			if (!isPassable && player.inBoundingBox(new mc.sayda.mcraze.util.Int2(packet.x, packet.y), tileSize)) {
				return;  // Cannot place - would collide with player
			}

			// Place the block
			if (world.addTile(packet.x, packet.y, tileId)) {
				// Successfully placed - decrease inventory
				player.inventory.decreaseSelected(1);
				// Immediately sync inventory after placing block (ISSUE #10 fix)
				broadcastInventoryUpdates();

				// Broadcast change to all players
				broadcastBlockChange(packet.x, packet.y, tileId);
			}
		}
	}

	/**
	 * Handle inventory action from client (slot clicks, crafting)
	 * Server-authoritative inventory manipulation
	 */
	public void handleInventoryAction(PlayerConnection playerConnection, mc.sayda.mcraze.network.packet.PacketInventoryAction packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot interact with inventory
		if (player.dead) {
			return;
		}

		// The player's inventory will handle the action server-side
		mc.sayda.mcraze.ui.Inventory inv = player.inventory;

		// DIRECT slot manipulation - packet coords map directly to array indices!
		// The client's mouseToCoor -1 offset compensates for panel padding, not array indexing
		// So: packet(x,y) -> inventoryItems[x][y] directly

		int slotX = packet.slotX;
		int slotY = packet.slotY;  // Direct mapping - no offset needed!
		int maxCount = 64;

		// DEBUG: Log the coordinate mapping
		GameLogger.get().info("InventoryAction: packet(" + packet.slotX + "," + packet.slotY +
			") -> array[" + slotX + "][" + slotY + "] | Array size: [" +
			inv.inventoryItems.length + "][" + inv.inventoryItems[0].length +
			"] | craftingHeight=" + inv.craftingHeight + " tableSizeAvailable=" + inv.tableSizeAvailable);

		// Validate array bounds
		if (slotX < 0 || slotX >= inv.inventoryItems.length ||
			slotY < 0 || slotY >= inv.inventoryItems[0].length) {
			GameLogger.get().warn("Invalid inventory slot: [" + slotX + "][" + slotY + "]");
			return;
		}

		// Handle craft output click with server-side crafting logic (resolution-independent)
		if (packet.craftClick) {
			GameLogger.get().info("Processing craft request for player " + playerConnection.getPlayerName());

			// Step 1: Compute current craft table from inventory
			String[][] currentTable;
			if (inv.tableSizeAvailable == 2) {
				currentTable = new String[2][2];
			} else {
				currentTable = new String[3][3];
			}

			for (int i = 0; i < inv.tableSizeAvailable; i++) {
				for (int j = 0; j < inv.tableSizeAvailable; j++) {
					mc.sayda.mcraze.item.Item item = inv.inventoryItems[i + inv.inventoryItems.length - inv.tableSizeAvailable][j].item;
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
					GameLogger.get().info("  Matched recipe: " + entry.itemId + " x" + craftedCount);
					break;
				}
			}

			if (craftedItem == null) {
				GameLogger.get().info("  No matching recipe found");
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
					GameLogger.get().info("  Cannot craft - holding incompatible item");
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
			GameLogger.get().info("  Crafted " + craftedItem.itemId + " x" + craftedCount);
		} else {
			// Direct slot manipulation for regular inventory clicks
			// This matches the logic from Inventory.updateInventory() lines 145-193

			GameLogger.get().info("  Slot item: " + (inv.inventoryItems[slotX][slotY].item != null ? inv.inventoryItems[slotX][slotY].item.itemId : "null") +
				" count=" + inv.inventoryItems[slotX][slotY].count);
			GameLogger.get().info("  Holding: " + (inv.holding.item != null ? inv.holding.item.itemId : "null") +
				" count=" + inv.holding.count + " isEmpty=" + inv.holding.isEmpty());

			if (inv.holding.isEmpty()) {
				// Picking up from slot
				GameLogger.get().info("  Action: PICKUP from slot");
				if (packet.leftClick || inv.inventoryItems[slotX][slotY].count <= 1) {
					// Left click or single item: pick up entire stack
					inv.holding.item = inv.inventoryItems[slotX][slotY].item;
					inv.holding.count = inv.inventoryItems[slotX][slotY].count;
					inv.inventoryItems[slotX][slotY].item = null;
					inv.inventoryItems[slotX][slotY].count = 0;
					GameLogger.get().info("  Result: Picked up " + (inv.holding.item != null ? inv.holding.item.itemId : "null") + " x" + inv.holding.count);
				} else {
					// Right click with multiple items: split stack
					inv.holding.item = inv.inventoryItems[slotX][slotY].item;
					inv.holding.count = (int) Math.ceil((double) inv.inventoryItems[slotX][slotY].count / 2);
					inv.inventoryItems[slotX][slotY].count = (int) Math.floor((double) inv.inventoryItems[slotX][slotY].count / 2);
					GameLogger.get().info("  Result: Split stack - holding " + inv.holding.count + ", slot has " + inv.inventoryItems[slotX][slotY].count);
				}
			} else if (inv.inventoryItems[slotX][slotY].item == null) {
				// Placing into empty slot
				if (!packet.leftClick) {
					// Right click: place one item
					inv.inventoryItems[slotX][slotY].item = inv.holding.item;
					inv.inventoryItems[slotX][slotY].count = 1;
					inv.holding.count--;
					if (inv.holding.count <= 0) {
						inv.holding.item = null;
					}
				} else {
					// Left click: place entire stack
					inv.inventoryItems[slotX][slotY].item = inv.holding.item;
					inv.inventoryItems[slotX][slotY].count = inv.holding.count;
					inv.holding.item = null;
					inv.holding.count = 0;
				}
			} else if (inv.holding.item.itemId.equals(inv.inventoryItems[slotX][slotY].item.itemId)
					&& inv.inventoryItems[slotX][slotY].count < maxCount) {
				// Merging stacks of same item
				if ((inv.holding.item.getClass() == mc.sayda.mcraze.item.Tool.class)
						|| (inv.inventoryItems[slotX][slotY].item.getClass() == mc.sayda.mcraze.item.Tool.class)) {
					// Tools don't stack - do nothing
				} else if (!packet.leftClick) {
					// Right click: add one item
					inv.inventoryItems[slotX][slotY].count++;
					inv.holding.count--;
					if (inv.holding.count <= 0) {
						inv.holding.item = null;
					}
				} else {
					// Left click: merge as much as possible
					int spaceInSlot = maxCount - inv.inventoryItems[slotX][slotY].count;
					int toTransfer = Math.min(spaceInSlot, inv.holding.count);
					inv.inventoryItems[slotX][slotY].count += toTransfer;
					inv.holding.count -= toTransfer;
					if (inv.holding.count <= 0) {
						inv.holding.item = null;
						inv.holding.count = 0;
					}
				}
			} else {
				// Different items: swap
				mc.sayda.mcraze.item.Item tempItem = inv.holding.item;
				int tempCount = inv.holding.count;
				inv.holding.item = inv.inventoryItems[slotX][slotY].item;
				inv.holding.count = inv.inventoryItems[slotX][slotY].count;
				inv.inventoryItems[slotX][slotY].item = tempItem;
				inv.inventoryItems[slotX][slotY].count = tempCount;
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
	private void updateCraftablePreview(mc.sayda.mcraze.ui.Inventory inv) {
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
				mc.sayda.mcraze.item.Item item = inv.inventoryItems[i + inv.inventoryItems.length - inv.tableSizeAvailable][j].item;
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
		}
		// Add more interaction types here (chests, furnaces, etc.)
	}

	/**
	 * Handle chest inventory actions (server-authoritative)
	 * Implements basic item transfer between chest and player inventory
	 */
	public void handleChestAction(PlayerConnection playerConnection, mc.sayda.mcraze.network.packet.PacketChestAction packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot interact with chests
		if (player.dead) {
			return;
		}

		GameLogger logger = GameLogger.get();
		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("SharedWorld.handleChestAction: Player " + player.username +
				" clicked chest at (" + packet.chestX + ", " + packet.chestY + ") slot (" +
				packet.slotX + ", " + packet.slotY + ") isChest=" + packet.isChestSlot +
				" rightClick=" + packet.isRightClick);
		}

		// Get the chest data
		mc.sayda.mcraze.world.ChestData chestData = world.getOrCreateChest(packet.chestX, packet.chestY);
		mc.sayda.mcraze.ui.Inventory inv = player.inventory;

		if (packet.isChestSlot) {
			// Clicked on chest slot - interact with chest
			if (packet.slotX >= 0 && packet.slotX < 9 && packet.slotY >= 0 && packet.slotY < 3) {
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
			// Chest UI shows 9x4 grid: 3 rows + hotbar
			if (packet.slotX >= 0 && packet.slotX < 9 && packet.slotY >= 0 && packet.slotY < 4) {
				int invX = packet.slotX;
				int invY = packet.slotY;

				// Validate bounds
				if (invX < inv.inventoryItems.length && invY < inv.inventoryItems[0].length) {
					// Use same slot interaction logic as inventory
					handleInventorySlotInteraction(inv, invX, invY, packet.isRightClick, logger);
				}
			}
		}

		// Broadcast chest state to all players (so others viewing same chest see updates)
		PacketChestOpen refreshPacket = new PacketChestOpen(packet.chestX, packet.chestY, chestData.items);
		broadcast(refreshPacket);

		// Also broadcast inventory updates (for player inventory changes)
		broadcastInventoryUpdates();
	}

	/**
	 * Handle inventory slot interaction (shared between chest UI and regular inventory)
	 */
	private void handleInventorySlotInteraction(mc.sayda.mcraze.ui.Inventory inv, int slotX, int slotY,
	                                             boolean rightClick, GameLogger logger) {
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
			if (!rightClick) {
				// Right click: place one item
				slot.setItem(inv.holding.getItem());
				slot.setCount(1);
				inv.holding.remove(1);
			} else {
				// Left click: place entire stack
				slot.setItem(inv.holding.getItem());
				slot.setCount(inv.holding.getCount());
				inv.holding.setEmpty();
			}
		} else if (inv.holding.getItem().itemId.equals(slot.getItem().itemId) && slot.getCount() < maxCount) {
			// Merging stacks of same item
			boolean isTool = (inv.holding.getItem() instanceof mc.sayda.mcraze.item.Tool) ||
			                 (slot.getItem() instanceof mc.sayda.mcraze.item.Tool);
			if (!isTool) {
				if (!rightClick) {
					// Right click: add one item
					slot.setCount(slot.getCount() + 1);
					inv.holding.remove(1);
				} else {
					// Left click: merge as much as possible
					int spaceInSlot = maxCount - slot.getCount();
					int toTransfer = Math.min(spaceInSlot, inv.holding.getCount());
					slot.setCount(slot.getCount() + toTransfer);
					inv.holding.remove(toTransfer);
				}
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
	public void handleItemToss(PlayerConnection playerConnection, mc.sayda.mcraze.network.packet.PacketItemToss packet) {
		Player player = playerConnection.getPlayer();
		if (player == null || player.inventory == null) {
			return;
		}

		// Dead players cannot toss items
		if (player.dead) {
			return;
		}

		GameLogger.get().info("Player " + playerConnection.getPlayerName() + " tossing item");

		// Use the existing tossSelectedItem logic from Player
		mc.sayda.mcraze.item.Item tossedItem = player.tossSelectedItem(random);
		if (tossedItem != null) {
			// Add item entity to world
			addEntity(tossedItem);
			GameLogger.get().info("  Tossed " + tossedItem.itemId);

			// Broadcast inventory update (item was removed from player inventory)
			broadcastInventoryUpdates();
		}
	}

	/**
	 * Respawn a player and broadcast to all clients
	 * Called when a player presses R after death
	 */
	public void respawnPlayer(PlayerConnection playerConn) {
		GameLogger logger = GameLogger.get();
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
		// This ensures all clients see respawn instantly (symmetric with PacketPlayerDeath)
		mc.sayda.mcraze.network.packet.PacketPlayerRespawn respawnPacket =
			new mc.sayda.mcraze.network.packet.PacketPlayerRespawn();
		broadcastPacket(respawnPacket);

		if (logger != null) {
			logger.info("SharedWorld.respawnPlayer: Broadcast respawn state for " + playerConn.getPlayerName());
		}

		System.out.println("Player " + playerConn.getPlayerName() + " respawned at spawn point");
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
}
