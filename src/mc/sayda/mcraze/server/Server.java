/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketHandler;
import mc.sayda.mcraze.network.packet.*;
import mc.sayda.mcraze.ui.CommandHandler;
import mc.sayda.mcraze.world.World;

/**
 * Server handles all game logic and world state.
 * In singleplayer, this runs locally. In multiplayer, this runs on the server.
 *
 * REFACTORED: Now uses SharedWorld internally for proper LAN support.
 * This makes integrated server identical to DedicatedServer in architecture.
 */
public class Server implements PacketHandler {
	// NEW: Multiplayer architecture (wraps SharedWorld)
	private SharedWorld sharedWorld;
	private PlayerConnection hostPlayerConnection; // The integrated client's connection
	private mc.sayda.mcraze.network.LocalConnection hostClientConnection; // Client end of connection
	private String hostUsername;
	private String hostPassword;

	// Delegates to SharedWorld for backwards compatibility
	public World world; // -> sharedWorld.getWorld()
	public Player player; // -> hostPlayerConnection.getPlayer()
	public ArrayList<Entity> entities = new ArrayList<>(); // -> sharedWorld.getAllEntities() (updated in tick())

	// Game settings
	private int worldWidth = 512;
	private int worldHeight = 256;
	// Gamerules moved to World class (world.keepInventory, world.daylightCycle,
	// world.spelunking)
	private float spawnX = 0;
	private float spawnY = 0;

	// Systems
	private mc.sayda.mcraze.ui.CommandHandler commandHandler;
	private mc.sayda.mcraze.ui.screen.Chat chat; // Reference to client's chat for command output
	private Random random = new Random();
	private int tileSize = Constants.TILE_SIZE;

	// Game state
	private boolean running = true;
	public long ticksRunning = 0;
	private boolean deathHandled = false;

	// World name tracking
	private String currentWorldName;

	// LAN server support
	private java.net.ServerSocket lanServerSocket;
	private Thread lanAcceptThread;
	private boolean lanEnabled = false;
	private int lanPort = 25565;

	// Logging
	private final GameLogger logger = GameLogger.get();

	// OLD: Authentication fields moved to hostUsername/hostPassword

	/**
	 * Constructor for integrated server (singleplayer/LAN)
	 * The connection parameter is kept for backwards compatibility but not used
	 * directly.
	 * Instead, we'll create a LocalConnection when startGame() is called.
	 */
	public Server(Connection connection) {
		// Connection will be created in startGame() via SharedWorld.addPlayer()
		// This constructor is kept for backwards compatibility
	}

	/**
	 * Set chat reference for command output
	 */
	public void setChat(mc.sayda.mcraze.ui.screen.Chat chat) {
		this.chat = chat;
		this.commandHandler = new mc.sayda.mcraze.ui.CommandHandler(this, null, chat);
		chat.setCommandHandler(commandHandler);
	}

	/**
	 * Start a new game with authentication (REFACTORED to use SharedWorld)
	 * Optionally load an existing world
	 */
	public void startGame(int width, String worldName, String username, String password, World loadedWorld,
			mc.sayda.mcraze.world.GameMode gameMode, boolean keepInventory, boolean daylightCycle,
			double noiseModifier) {
		// Set global noise modifier for next generation (if any)
		mc.sayda.mcraze.world.gen.WorldGenerator.noiseModifier = noiseModifier;

		worldWidth = width;
		deathHandled = false;
		this.hostUsername = username;
		this.hostPassword = password;
		this.currentWorldName = worldName;

		// Use loaded world if provided, otherwise generate new
		World gameWorld;
		if (loadedWorld != null) {
			gameWorld = loadedWorld;
			if (logger != null)
				logger.info("Using loaded world: " + worldName);
			// Loaded worlds use their saved GameMode/Rules (restored from metadata)
		} else {
			gameWorld = new World(worldWidth, worldHeight, random, gameMode);
			// Apply creation settings to NEW world
			gameWorld.gameMode = gameMode;
			gameWorld.keepInventory = keepInventory;
			gameWorld.daylightCycle = daylightCycle;

			if (logger != null)
				logger.info("Generated new world: " + worldName + " Mode: " + gameMode);
		}

		spawnX = gameWorld.spawnLocation.x;
		spawnY = gameWorld.spawnLocation.y;

		// Create SharedWorld (same as DedicatedServer)
		sharedWorld = new SharedWorld(gameWorld, random, worldName);
		world = sharedWorld.getWorld();

		// Set command handler for integrated server commands
		if (commandHandler != null) {
			sharedWorld.setCommandHandler(commandHandler);
			commandHandler.setSharedWorld(sharedWorld); // Give CommandHandler reference to SharedWorld
		}

		// Create LocalConnection pair for host player
		mc.sayda.mcraze.network.LocalConnection[] connectionPair = mc.sayda.mcraze.network.LocalConnection.createPair();
		hostClientConnection = connectionPair[0]; // Client end - for Game.java to give to Client
		mc.sayda.mcraze.network.LocalConnection serverConnection = connectionPair[1]; // Server end

		// Add host player to SharedWorld (handles authentication, playerdata, entity
		// creation)
		hostPlayerConnection = sharedWorld.addPlayer(serverConnection, username, password);

		if (hostPlayerConnection == null) {
			throw new RuntimeException("Failed to authenticate host player: " + username);
		}

		// Set backwards compatibility delegates
		player = hostPlayerConnection.getPlayer();

		if (logger != null)
			logger.info("Integrated server started for " + username + " in world " + worldName);

		// Debug items (apply to the player from SharedWorld)
		if (Constants.DEBUG) {
			player.giveItem(Constants.itemTypes.get("diamond_sword").clone(), 1);
			player.giveItem(Constants.itemTypes.get("diamond_pickaxe").clone(), 1);
			player.giveItem(Constants.itemTypes.get("diamond_axe").clone(), 1);
			player.giveItem(Constants.itemTypes.get("diamond_hoe").clone(), 1);
			player.giveItem(Constants.itemTypes.get("diamond_shovel").clone(), 1);
			player.giveItem(Constants.itemTypes.get("torch").clone(), 64);
			Constants.DEBUG_VISIBILITY_ON = true;
		}
	}

	/**
	 * Get the client connection for the host player.
	 * Game.java should call this after startGame() and give it to the Client.
	 */
	public Connection getHostClientConnection() {
		return hostClientConnection;
	}

	/**
	 * Start a new game without loading (generates new world)
	 */
	public void startGame(int width, String worldName, String username, String password) {
		startGame(width, worldName, username, password, null, mc.sayda.mcraze.world.GameMode.CLASSIC, false, true, 0.0);
	}

	/**
	 * Legacy method for backward compatibility
	 * 
	 * @deprecated Use startGame(int, String, String, String) instead
	 */
	@Deprecated
	public void startGame(int width) {
		startGame(width, "World1", "Player", "password", null, mc.sayda.mcraze.world.GameMode.CLASSIC, false, true,
				0.0);
	}

	/**
	 * REMOVED: sendInitialWorldState() is now handled by SharedWorld.addPlayer()
	 * SharedWorld automatically sends all initial packets when a player connects.
	 */

	/**
	 * Server tick - process packets and update game state (REFACTORED)
	 * Now delegates to SharedWorld which handles all the multiplayer logic.
	 */
	public void tick() {
		if (!running || sharedWorld == null)
			return;

		ticksRunning++;

		// CRITICAL FIX: Check for disconnected players ALWAYS (not just when LAN
		// enabled)
		// This fixes ghost players and E key inconsistency
		java.util.List<mc.sayda.mcraze.server.PlayerConnection> disconnectedPlayers = new java.util.ArrayList<>();

		for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
			// Skip host player (never disconnects - if they do, whole server stops)
			if (pc == hostPlayerConnection) {
				continue;
			}

			// Check if player is still connected (works for both LAN and singleplayer)
			if (!pc.isConnected()) {
				if (logger != null) {
					logger.info("Detected disconnected player: " + pc.getPlayerName());
				} else {
					// Fallback if logger not initialized
					// System.out.println("Player " + pc.getPlayerName() + " disconnected");
				}
				disconnectedPlayers.add(pc);
			}
		}

		// Remove disconnected players
		for (mc.sayda.mcraze.server.PlayerConnection pc : disconnectedPlayers) {
			sharedWorld.removePlayer(pc);
			if (logger != null) {
				logger.info("Removed player connection: " + pc.getPlayerName());
			} else {
				// System.out.println("Removed player: " + pc.getPlayerName());
			}
		}

		// Delegate all game logic to SharedWorld
		// SharedWorld handles:
		// - Processing packets from all players
		// - Updating world (chunk updates, daylight cycle)
		// - Updating entities (movement, collisions, item pickups)
		// - Broadcasting updates to all clients
		sharedWorld.tick();

		// Update backwards compatibility delegates
		world = sharedWorld.getWorld();
		if (hostPlayerConnection != null) {
			player = hostPlayerConnection.getPlayer();
		}

		// Update entities list for backwards compatibility
		entities.clear();
		// CRITICAL FIX: Deep clone entities for integrated server
		// This prevents the client from modifying live server objects (Host Shortcut
		// fix)
		// prev: for(mc.sayda.mcraze.entity.Entity e : sharedWorld.getAllEntities()) {
		for (mc.sayda.mcraze.entity.Entity e : sharedWorld.getAllEntities()) {
			try {
				Entity cloned = e.clone();
				// CRITICAL: Legay entities list (clones) should maintain the same UUID
				// so my new UUID-based entity tracking can correctly match them.
				cloned.setUUID(e.getUUID());
				entities.add(cloned);
			} catch (CloneNotSupportedException ex) {
				// Should not happen for Player/Item
				// Fallback to reference (better than crash)
				entities.add(e);
				if (logger != null)
					logger.error("Failed to clone entity: " + ex.getMessage());
			}
		}
	}

	/**
	 * REMOVED: Update methods are now handled by SharedWorld
	 * - sendWorldUpdate() - SharedWorld.tick() handles world time broadcasts
	 * - sendInventoryUpdate() - SharedWorld.broadcastInventoryUpdates() handles
	 * this
	 * - sendEntityUpdate() - SharedWorld.broadcastEntityUpdate() handles this
	 *
	 * All updates are automatically broadcast to all connected players (including
	 * LAN clients)
	 */

	/**
	 * REMOVED: handlePlayerDeath() is now handled by SharedWorld.tick()
	 * Player death detection and item dropping is managed by SharedWorld
	 * automatically.
	 */

	/**
	 * Respawn the player (REFACTORED to use hostUsername)
	 *
	 * @deprecated This method should NOT be used. All respawns must go through
	 *             the packet system (PacketRespawn → SharedWorld.respawnPlayer) to
	 *             ensure
	 *             consistent behavior whether LAN is enabled or not. This maintains
	 *             the
	 *             architecture principle that there is no "singleplayer" - only
	 *             integrated
	 *             servers with LAN disabled. ALL player actions must use packets.
	 */
	@Deprecated
	public void respawnPlayer() {
		// WARNING: This method is deprecated and should not be called
		if (logger != null) {
			logger.warn("WARNING: Server.respawnPlayer() called directly! This is deprecated.");
			logger.warn("         All respawns should use PacketRespawn -> SharedWorld.respawnPlayer()");
		}

		if (player != null && hostUsername != null && currentWorldName != null) {
			// Reload playerdata (reset to spawn with full health)
			try {
				mc.sayda.mcraze.player.data.PlayerData playerData = mc.sayda.mcraze.player.data.PlayerDataManager
						.load(currentWorldName, hostUsername);
				if (playerData != null) {
					// Reset to spawn location (not saved position)
					playerData.x = spawnX;
					playerData.y = spawnY;
					playerData.health = 100;
					mc.sayda.mcraze.player.data.PlayerDataManager.applyToPlayer(playerData, player);
				} else {
					// Fallback to simple respawn
					player.respawn(spawnX, spawnY);
				}
			} catch (java.io.IOException e) {
				if (logger != null)
					logger.error("Failed to reload playerdata on respawn: " + e.getMessage());
				player.respawn(spawnX, spawnY);
			}
			deathHandled = false;
		} else if (player != null) {
			// Fallback for legacy code
			player.respawn(spawnX, spawnY);
			deathHandled = false;
		}
	}

	/**
	 * Save playerdata for the authenticated player (REFACTORED to use hostUsername)
	 */
	public void savePlayerData() {
		if (player != null && hostUsername != null && hostPassword != null && currentWorldName != null) {
			mc.sayda.mcraze.player.data.PlayerData playerData = mc.sayda.mcraze.player.data.PlayerDataManager
					.extractFromPlayer(
							hostUsername,
							hostPassword,
							player);
			mc.sayda.mcraze.player.data.PlayerDataManager.save(currentWorldName, playerData);
			if (logger != null)
				logger.info("Saved playerdata for " + hostUsername);
		}
	}

	/**
	 * Load playerdata for the authenticated player (REFACTORED to use hostUsername)
	 * Used after loading a world
	 */
	public void loadPlayerData(String worldName, String username, String password) {
		this.hostUsername = username;
		this.hostPassword = password;
		this.currentWorldName = worldName;

		if (player != null) {
			// Try to load playerdata
			mc.sayda.mcraze.player.data.PlayerData playerData = mc.sayda.mcraze.player.data.PlayerDataManager
					.authenticate(worldName, username, password);

			if (playerData == null) {
				// Auto-register: Create new playerdata
				playerData = new mc.sayda.mcraze.player.data.PlayerData(username, password, spawnX, spawnY);
				mc.sayda.mcraze.player.data.PlayerDataManager.save(worldName, playerData);
				if (logger != null)
					logger.info("Auto-registered new player: " + username + " in loaded world");
			}

			// Apply playerdata to existing player
			mc.sayda.mcraze.player.data.PlayerDataManager.applyToPlayer(playerData, player);
			if (logger != null)
				logger.info("Loaded playerdata for " + username + " in world " + worldName);
		}
	}

	/**
	 * Toss an item from player inventory (REFACTORED to use SharedWorld)
	 */
	public void tossItem() {
		if (player != null && sharedWorld != null) {
			Item tossedItem = player.tossSelectedItem(random);
			if (tossedItem != null) {
				sharedWorld.addEntity(tossedItem);
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void stop() {
		running = false;
		// Stop LAN server if enabled
		if (lanEnabled) {
			disableLAN();
		}
	}

	/**
	 * Enable LAN server to allow other players to connect
	 */
	public boolean enableLAN(int port) {
		if (lanEnabled) {
			if (logger != null)
				logger.info("LAN server already running on port " + lanPort);
			return true;
		}

		if (sharedWorld == null) {
			if (logger != null)
				logger.error("Cannot enable LAN: SharedWorld not initialized");
			return false;
		}

		try {
			lanServerSocket = new java.net.ServerSocket(port);
			lanPort = port;
			lanEnabled = true;
			startLANAcceptThread();
			if (logger != null)
				logger.info("LAN server started on port " + port);
			return true;
		} catch (java.io.IOException e) {
			if (logger != null)
				logger.error("Failed to start LAN server: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Disable LAN server
	 */
	public void disableLAN() {
		if (!lanEnabled) {
			return;
		}

		if (logger != null)
			logger.info("Disabling LAN server...");
		lanEnabled = false;

		// Disconnect all LAN clients (not the host)
		if (sharedWorld != null) {
			java.util.List<mc.sayda.mcraze.server.PlayerConnection> lanClients = new java.util.ArrayList<>();

			// Find all LAN clients (players who are not the host)
			for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
				if (pc != hostPlayerConnection) {
					lanClients.add(pc);
				}
			}

			// Disconnect each LAN client
			for (mc.sayda.mcraze.server.PlayerConnection pc : lanClients) {
				if (logger != null)
					logger.info("Disconnecting LAN player: " + pc.getPlayerName());
				// Close the connection
				pc.getConnection().disconnect();
				// Remove player from world
				sharedWorld.removePlayer(pc);
			}

			if (logger != null)
				logger.info("Disconnected " + lanClients.size() + " LAN client(s)");
		}

		// Close server socket
		if (lanServerSocket != null) {
			try {
				lanServerSocket.close();
			} catch (java.io.IOException e) {
				if (logger != null)
					logger.error("Error closing LAN server socket: " + e.getMessage());
			}
			lanServerSocket = null;
		}

		// Wait for accept thread to finish
		if (lanAcceptThread != null && lanAcceptThread.isAlive()) {
			try {
				lanAcceptThread.join(1000);
			} catch (InterruptedException e) {
				// Ignore
			}
			lanAcceptThread = null;
		}

		if (logger != null)
			logger.info("LAN server stopped");
	}

	/**
	 * Start thread to accept LAN connections
	 */
	private void startLANAcceptThread() {
		lanAcceptThread = new Thread(() -> {
			if (logger != null)
				logger.info("LAN accept thread started");
			while (lanEnabled && running) {
				try {
					java.net.Socket clientSocket = lanServerSocket.accept();
					if (logger != null)
						logger.info("LAN client connected from: " + clientSocket.getRemoteSocketAddress());

					// Handle connection in a separate thread
					handleLANConnection(clientSocket);

				} catch (java.io.IOException e) {
					if (lanEnabled) {
						if (logger != null)
							logger.error("Error accepting LAN client: " + e.getMessage());
					}
				}
			}
			if (logger != null)
				logger.info("LAN accept thread stopped");
		}, "LANAccept");
		lanAcceptThread.setDaemon(true);
		lanAcceptThread.start();
	}

	/**
	 * Handle a LAN client connection (authentication and player creation)
	 */
	private void handleLANConnection(java.net.Socket clientSocket) {
		new Thread(() -> {
			try {
				// Create network connection
				mc.sayda.mcraze.network.NetworkConnection connection = new mc.sayda.mcraze.network.NetworkConnection(
						clientSocket);

				// Wait for authentication packet (timeout after 5 seconds)
				if (logger != null)
					logger.info("Waiting for authentication from LAN client...");
				long authStart = System.currentTimeMillis();
				mc.sayda.mcraze.network.packet.PacketAuthRequest authPacket = null;

				while (authPacket == null && System.currentTimeMillis() - authStart < 5000) {
					mc.sayda.mcraze.network.Packet[] packets = connection.receivePackets();
					for (mc.sayda.mcraze.network.Packet packet : packets) {
						if (packet instanceof mc.sayda.mcraze.network.packet.PacketAuthRequest) {
							authPacket = (mc.sayda.mcraze.network.packet.PacketAuthRequest) packet;
							break;
						}
					}
					if (authPacket == null) {
						Thread.sleep(50);
					}
				}

				if (authPacket == null) {
					if (logger != null)
						logger.error("LAN client did not send authentication packet - disconnecting");
					connection.disconnect();
					return;
				}

				if (logger != null)
					logger.info("LAN authentication request from: " + authPacket.username);

				// Try to add player with authentication
				PlayerConnection playerConnection = sharedWorld.addPlayer(
						connection, authPacket.username, authPacket.password);

				if (playerConnection == null) {
					// Authentication failed
					if (logger != null)
						logger.error("LAN authentication failed for " + authPacket.username);
					mc.sayda.mcraze.network.packet.PacketAuthResponse response = new mc.sayda.mcraze.network.packet.PacketAuthResponse(
							false, "Authentication failed");
					connection.sendPacket(response);
					connection.flush(); // Flush immediately so client receives auth failure
					connection.disconnect();
				} else {
					// Authentication successful
					if (logger != null)
						logger.info("LAN player " + authPacket.username + " authenticated successfully");
					mc.sayda.mcraze.network.packet.PacketAuthResponse response = new mc.sayda.mcraze.network.packet.PacketAuthResponse(
							true, "");
					connection.sendPacket(response);
					connection.flush(); // Flush immediately so client receives auth success

					if (logger != null)
						logger.info("LAN player " + authPacket.username + " joined the game");
				}

			} catch (Exception e) {
				if (logger != null)
					logger.error("Error handling LAN connection: " + e.getMessage());
				e.printStackTrace();
			}
		}, "LANConnectionHandler").start();
	}

	public boolean isLANEnabled() {
		return lanEnabled;
	}

	public int getLANPort() {
		return lanPort;
	}

	/**
	 * Set spawn location for player respawning
	 */
	public void setSpawnLocation(float x, float y) {
		this.spawnX = x;
		this.spawnY = y;
	}

	/**
	 * Get SharedWorld instance (for accessing multiplayer systems)
	 */
	public SharedWorld getSharedWorld() {
		return sharedWorld;
	}

	/**
	 * Get thread-safe EntityManager for rendering
	 */
	public EntityManager getEntityManager() {
		return sharedWorld != null ? sharedWorld.getEntityManager() : null;
	}

	/**
	 * Get thread-safe WorldAccess for tile rendering
	 */
	public mc.sayda.mcraze.world.storage.WorldAccess getWorldAccess() {
		return sharedWorld != null ? sharedWorld.getWorldAccess() : null;
	}

	/**
	 * REMOVED: getBreakingState() is no longer needed
	 * Block breaking state is now managed by SharedWorld per-player
	 */

	// ===== Packet Handlers =====

	@Override
	public void handleWorldInit(PacketWorldInit packet) {
		// Server doesn't handle world init (only sends it)
	}

	@Override
	public void handlePlayerInput(PacketPlayerInput packet) {
		// Delegate to host player's connection handler
		if (hostPlayerConnection != null) {
			hostPlayerConnection.handlePlayerInput(packet);
		}
	}

	@Override
	public void handleBlockChange(PacketBlockChange packet) {
		// Delegate to SharedWorld via host player connection
		// SharedWorld handles all the block breaking/placing logic and broadcasting
		if (hostPlayerConnection != null) {
			hostPlayerConnection.handleBlockChange(packet);
		}
	}

	@Override
	public void handleInventoryAction(PacketInventoryAction packet) {
		// Delegate to host player connection
		if (hostPlayerConnection != null) {
			hostPlayerConnection.handleInventoryAction(packet);
		}
	}

	@Override
	public void handleChatSend(PacketChatSend packet) {
		// Delegate ALL chat/commands to PlayerConnection (integrated server host's
		// connection)
		// This ensures consistent behavior - host is treated like any other player
		if (hostPlayerConnection != null) {
			hostPlayerConnection.handleChatSend(packet);
		}
	}

	// Client-bound packet handlers (server doesn't handle these)
	@Override
	public void handleWorldUpdate(PacketWorldUpdate packet) {
	}

	@Override
	public void handleEntityUpdate(PacketEntityUpdate packet) {
	}

	@Override
	public void handleEntityRemove(mc.sayda.mcraze.network.packet.PacketEntityRemove packet) {
	}

	@Override
	public void handleInventoryUpdate(PacketInventoryUpdate packet) {
	}

	@Override
	public void handleChatMessage(PacketChatMessage packet) {
	}

	@Override
	public void handlePlayerDeath(PacketPlayerDeath packet) {
	}

	@Override
	public void handleBreakingProgress(PacketBreakingProgress packet) {
	}

	@Override
	public void handleAuthRequest(mc.sayda.mcraze.network.packet.PacketAuthRequest packet) {
		// Server doesn't handle auth requests in singleplayer (only DedicatedServer
		// does)
	}

	@Override
	public void handleAuthResponse(mc.sayda.mcraze.network.packet.PacketAuthResponse packet) {
		// Server doesn't receive auth responses
	}
}
