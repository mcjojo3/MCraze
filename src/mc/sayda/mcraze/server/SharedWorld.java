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
import mc.sayda.mcraze.network.packet.PacketEntityUpdate;
import mc.sayda.mcraze.network.packet.PacketInventoryUpdate;
import mc.sayda.mcraze.network.packet.PacketPlayerDeath;
import mc.sayda.mcraze.network.packet.PacketWorldInit;
import mc.sayda.mcraze.network.packet.PacketWorldUpdate;
import mc.sayda.mcraze.world.World;

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
	private List<PlayerConnection> players;
	private Random random;
	private boolean running = true;
	private long ticksRunning = 0;
	private boolean daylightCycle = true;
	private int tileSize = Constants.TILE_SIZE;

	// Per-player breaking state
	private HashMap<Player, BreakingState> playerBreakingState = new HashMap<>();

	// World entities (items, etc.) not associated with a specific player
	private List<Entity> worldEntities = new ArrayList<>();

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
		System.out.println("SharedWorld: World generated");
		System.out.println("SharedWorld: Constructor - world instance: " + System.identityHashCode(world));
		System.out.println("SharedWorld: Constructor - world.tiles instance: " + System.identityHashCode(world.tiles));
		System.out.println("SharedWorld: Constructor - world.tiles.length: " + world.tiles.length);
		if (logger != null) {
			logger.info("SharedWorld: World generated successfully - " + worldWidth + "x" + worldWidth);
			logger.debug("SharedWorld.<init>: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
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

		System.out.println("SharedWorld: Loaded existing world '" + worldName + "' (" + world.width + "x" + world.height + ")");
		if (logger != null) {
			logger.info("SharedWorld: Loaded existing world '" + worldName + "' - " + world.width + "x" + world.height);
			logger.debug("SharedWorld.<init>: Spawn location: " + world.spawnLocation.x + ", " + world.spawnLocation.y);
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

		// Remove player entity from worldEntities list
		Player playerEntity = playerConnection.getPlayer();
		if (playerEntity != null) {
			worldEntities.remove(playerEntity);
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
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: Collecting non-air tiles...");
		ArrayList<Integer> changedX = new ArrayList<>();
		ArrayList<Integer> changedY = new ArrayList<>();
		ArrayList<Character> changedTiles = new ArrayList<>();

		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		int tilesAdded = 0;

		for (int x = 0; x < world.width; x++) {
			for (int y = 0; y < world.height; y++) {
				if (world.tiles[x][y] != null && world.tiles[x][y].type != null
					&& world.tiles[x][y].type.name != Constants.TileID.AIR) {
					changedX.add(x);
					changedY.add(y);
					changedTiles.add((char) world.tiles[x][y].type.name.ordinal());

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

		// Calculate total packets (world update chunks + 1 entity update)
		int chunkSize = 1000;
		int numChunks = (int) Math.ceil((double) changedX.size() / chunkSize);
		int totalPackets = numChunks + 1;  // +1 for entity update

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
		playerConnection.getConnection().sendPacket(initPacket);
		if (logger != null) logger.debug("SharedWorld.sendInitialWorldState: PacketWorldInit sent with playerUUID=" + playerUUID + ", totalPackets=" + totalPackets);

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
			} else {
				packet.entityHealth[i] = 0;
				packet.facingRight[i] = true;
				packet.dead[i] = false;
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
				for (PlayerConnection pc : players) {
					Player player = pc.getPlayer();
					if (player != null && !player.dead && player.collidesWith(entity, tileSize)) {
						mc.sayda.mcraze.item.Item item = (mc.sayda.mcraze.item.Item) entity;
						int leftover = player.giveItem(item, 1);
						if (leftover == 0) {
							// Item fully picked up - mark for removal from worldEntities
							entitiesToRemove.add(entity);
							pickedUp = true;
							if (logger != null && ticksRunning <= 10) {
								logger.debug("SharedWorld.tick: Player " + pc.getPlayerName() + " picked up " + item.itemId);
							}
							break;
						}
					}
				}
				if (pickedUp) {
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

								// Drop all items from inventory
								java.util.ArrayList<Item> droppedItems = deadPlayer.dropAllItems(random);
								for (Item item : droppedItems) {
									worldEntities.add(item);
								}
								System.out.println("Player " + deadPlayer.username + " died and dropped " + droppedItems.size() + " items");

								break;
							}
						}
					}
				}
			}
		}

		// Remove picked-up items from worldEntities
		if (!entitiesToRemove.isEmpty()) {
			// Send entity removal packets immediately to all clients
			for (Entity entity : entitiesToRemove) {
				mc.sayda.mcraze.network.packet.PacketEntityRemove removePacket =
					new mc.sayda.mcraze.network.packet.PacketEntityRemove();
				removePacket.entityUUID = entity.getUUID();
				broadcastPacket(removePacket);
			}

			worldEntities.removeAll(entitiesToRemove);
			if (logger != null && ticksRunning <= 10) {
				logger.debug("SharedWorld.tick: Removed " + entitiesToRemove.size() + " entities from world");
			}
		}

		if (logger != null && ticksRunning <= 5) {
			logger.debug("SharedWorld.tick: Entities updated");
		}

		// Broadcast entity updates every 3 ticks (20Hz)
		if (ticksRunning % 3 == 0) {
			if (logger != null && ticksRunning <= 15) {
				logger.debug("SharedWorld.tick: Broadcasting entity update (tick " + ticksRunning + ")");
			}
			broadcastEntityUpdate();
			broadcastInventoryUpdates();
		}

		// Auto-save all players every 6000 ticks (5 minutes at 20 TPS)
		if (ticksRunning % 6000 == 0) {
			System.out.println("SharedWorld: Auto-saving all players...");
			for (PlayerConnection pc : players) {
				savePlayerData(pc);
			}
			if (logger != null) logger.info("SharedWorld: Auto-saved " + players.size() + " players");
		}
	}

	/**
	 * Get all entities from all players and world entities (dropped items, etc.)
	 */
	public List<Entity> getAllEntities() {
		List<Entity> allEntities = new ArrayList<>();
		for (PlayerConnection playerConnection : players) {
			allEntities.add(playerConnection.getPlayer());
		}
		// Add world entities (dropped items, etc.)
		allEntities.addAll(worldEntities);
		return allEntities;
	}

	/**
	 * Broadcast entity update to all players
	 */
	private void broadcastEntityUpdate() {
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
			} else {
				packet.entityHealth[i] = 0;
				packet.facingRight[i] = true;
				packet.dead[i] = false;
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
	private void broadcastInventoryUpdates() {
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
			// Check if target is breakable
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

			// Drop items if block was broken
			if (dropId != null && dropId.itemDropId != null) {
				Item droppedItem = Constants.itemTypes.get(dropId.itemDropId);
				if (droppedItem != null) {
					Item item = droppedItem.clone();
					item.x = packet.x + random.nextFloat() * 0.5f;
					item.y = packet.y + random.nextFloat() * 0.5f;
					item.dy = -0.07f;  // Give it upward velocity
					worldEntities.add(item);
					if (logger != null) logger.debug("SharedWorld: Dropped item " + item.itemId + " at (" + item.x + ", " + item.y + ")");
				}
			}

			// Broadcast change to all players
			broadcastBlockChange(packet.x, packet.y, null);

		} else {
			// Check if clicking on a crafting table FIRST (before parsing packet data)
			if (world.isCraft(packet.x, packet.y)) {
				// Open inventory with 3x3 crafting
				player.inventory.tableSizeAvailable = 3;
				player.inventory.setVisible(true);
				broadcastInventoryUpdates();  // Immediately sync inventory state
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

		// Construct fake screen dimensions and mouse position to call updateInventory()
		// This allows us to reuse the existing inventory manipulation logic
		int fakeScreenWidth = 800;
		int fakeScreenHeight = 600;

		// Calculate mouse position for the clicked slot
		// Based on Inventory.draw() layout calculations
		int tileSize = 16;
		int separation = 15;
		int panelWidth = inv.inventoryItems.length * (tileSize + separation) + separation;
		int panelHeight = inv.inventoryItems[0].length * (tileSize + separation) + separation;
		int panelX = fakeScreenWidth / 2 - panelWidth / 2;
		int panelY = fakeScreenHeight / 2 - panelHeight / 2;

		// Calculate click position for the slot
		int mouseX = panelX + packet.slotX * (tileSize + separation) + separation + tileSize/2;
		int mouseY = panelY + (packet.slotY + 1) * (tileSize + separation) + separation + tileSize/2;

		// Handle craft clicks differently
		if (packet.craftClick) {
			// Craft output slot position
			mouseX = panelX + (inv.inventoryItems.length - inv.tableSizeAvailable - 1) * (tileSize + separation);
			mouseY = panelY + separation * 2 + tileSize + tileSize/2;
		}

		mc.sayda.mcraze.util.Int2 mousePos = new mc.sayda.mcraze.util.Int2(mouseX, mouseY);

		// Apply the inventory action server-side by calling updateInventory
		// Pass null for connection to avoid sending packets from server
		inv.updateInventory(fakeScreenWidth, fakeScreenHeight, mousePos,
			packet.leftClick, !packet.leftClick, null);

		// Broadcast updated inventory to all clients
		broadcastInventoryUpdates();
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
	 */
	public void addEntity(Entity entity) {
		if (entity != null) {
			worldEntities.add(entity);
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
}
