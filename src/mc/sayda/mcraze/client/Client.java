/*
 * Copyright 2025 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.client;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.MusicPlayer;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketHandler;
import mc.sayda.mcraze.network.packet.*;
import mc.sayda.mcraze.server.Server;
import mc.sayda.mcraze.ui.Chat;
import mc.sayda.mcraze.ui.CommandHandler;
import mc.sayda.mcraze.ui.MainMenu;
import mc.sayda.mcraze.ui.UIRenderer;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.util.SystemTimer;
import java.util.Random;

/**
 * Client handles rendering and input.
 * Communicates with server (local or remote) via packets.
 */
public class Client implements PacketHandler {
	// Connection to server
	public Connection connection;  // Public for Game to send packets
	private Server localServer;  // For integrated server (singleplayer)
	private mc.sayda.mcraze.Game game;  // Reference to Game for save/load
    Random random = new Random();

	// Client-side breaking animation (visual only)
	private int clientBreakingTicks = 0;
	private int clientBreakingX = -1;
	private int clientBreakingY = -1;
	private mc.sayda.mcraze.Sprite[] breakingSprites;

	// Rendering
	private UIRenderer uiRenderer;
	public boolean viewFPS = false;
	private int tileSize = Constants.TILE_SIZE;

	// Input state
	public boolean leftClick = false;
	public boolean rightClick = false;
	public Int2 screenMousePos = new Int2(0, 0);

	// Frame timing for FPS calculation
	private long lastFrameTime = System.nanoTime();
	private long frameDeltaMs = 16;  // Default to ~60 FPS

	// UI
	public Chat chat;
	private MainMenu menu;
	private boolean inMenu = true;
	private mc.sayda.mcraze.ui.PauseMenu pauseMenu;
	private boolean inPauseMenu = false;
	private mc.sayda.mcraze.ui.SettingsMenu settingsMenu;
	private boolean inSettingsMenu = false;
	private mc.sayda.mcraze.ui.LoadingScreen loadingScreen;
	private boolean inLoadingScreen = false;

    // Audio
    String song = random.nextBoolean() ? "music" : "music2";
    public MusicPlayer musicPlayer = new MusicPlayer("sounds/" + song + ".ogg");


    // Client state
	private boolean running = true;
	private String myPlayerUUID = null;  // UUID of this client's player entity

	public Client(Connection connection, Server localServer) {
		this.connection = connection;
		this.localServer = localServer;

		// Initialize systems (for integrated server)
		// Load breaking animation sprites
		final mc.sayda.mcraze.SpriteStore ss = mc.sayda.mcraze.SpriteStore.get();
		breakingSprites = new mc.sayda.mcraze.Sprite[8];
		for (int i = 0; i < 8; i++) {
			breakingSprites[i] = ss.getSprite("sprites/tiles/break" + i + ".png");
		}

		// Initialize UI
		uiRenderer = new UIRenderer();
		chat = new Chat();
		// Note: CommandHandler is created by Server after Client construction
		menu = new MainMenu(null, uiRenderer);  // Game reference set later via setGame()
		pauseMenu = null;  // Will be created when setGame() is called
		loadingScreen = new mc.sayda.mcraze.ui.LoadingScreen();
	}

	/**
	 * Set Game reference (called after Game construction to avoid circular dependency)
	 */
	public void setGame(mc.sayda.mcraze.Game game) {
		this.game = game;
		menu.setGame(game);
		pauseMenu = new mc.sayda.mcraze.ui.PauseMenu(game, uiRenderer);
		settingsMenu = new mc.sayda.mcraze.ui.SettingsMenu(game, uiRenderer, pauseMenu);
		// Initialize graphics handler now that we have the Game reference
		GraphicsHandler.get().init(game);
	}

	/**
	 * Client rendering loop
	 */
	private long lastRenderLog = 0;

	public void render() {
		GameLogger logger = GameLogger.get();

		if (!running || localServer == null) {
			if (logger != null) logger.warn("Client.render: Skipped due to invalid state");
			return;
		}

		// Process incoming packets from server (for multiplayer)
		processPackets();

		// Send input packets regularly in multiplayer/LAN mode (for continuous movement)
		if (isMultiplayerOrLAN()) {
			sendInputFromEventHandler();
		}

		GraphicsHandler g = GraphicsHandler.get();
		g.startDrawing();

		// Render login screen (highest priority)
		if (game != null && game.isShowingLoginScreen()) {
			mc.sayda.mcraze.ui.LoginScreen loginScreen = game.getLoginScreen();
			if (loginScreen != null) {
				loginScreen.draw(g);

				// Handle input for login screen
				if (leftClick) {
					loginScreen.handleClick(screenMousePos.x, screenMousePos.y);
					leftClick = false;
				}

				uiRenderer.drawMouse(g, screenMousePos);
				g.finishDrawing();
				return;
			}
		}

		// Render loading screen (takes priority over everything else)
		if (inLoadingScreen && loadingScreen != null) {
			loadingScreen.tick();
			loadingScreen.draw(g);
			g.finishDrawing();
			return;
		}

		// Render menu (menu can render even if player is null)
		if (inMenu) {
			menu.draw(g);
			uiRenderer.drawMouse(g, screenMousePos);
			g.finishDrawing();
			return;
		}

		// From here on, we need the player for gameplay
		if (localServer.player == null) {
			if (logger != null) logger.warn("Client.render: Player is null, skipping gameplay render");
			g.finishDrawing();
			return;
		}

		// Get screen dimensions
		final int screenWidth = g.getScreenWidth();
		final int screenHeight = g.getScreenHeight();

		// Calculate camera position
		float cameraX = localServer.player.x - screenWidth / tileSize / 2;
		float cameraY = localServer.player.y - screenHeight / tileSize / 2;
		float worldMouseX = (cameraX * tileSize + screenMousePos.x) / tileSize;
		float worldMouseY = (cameraY * tileSize + screenMousePos.y) / tileSize - .5f;

		// Render world
		if (localServer.world != null) {
			localServer.world.draw(g, 0, 0, screenWidth, screenHeight, cameraX, cameraY, tileSize);
		} else {
			if (logger != null) logger.warn("Client.render: World is null, skipping world render");
		}

		// Render entities
		for (mc.sayda.mcraze.entity.Entity entity : localServer.entities) {
			if (entity != null) {
				entity.draw(g, cameraX, cameraY, screenWidth, screenHeight, tileSize);

				// Draw username above player heads
				if (entity instanceof mc.sayda.mcraze.entity.Player) {
					mc.sayda.mcraze.entity.Player player = (mc.sayda.mcraze.entity.Player) entity;
					if (player.username != null && !player.username.isEmpty()) {
						// Calculate screen position above player's head
						mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
							cameraX, cameraY, screenWidth, screenHeight, tileSize, entity.x, entity.y
						);

						if (mc.sayda.mcraze.util.StockMethods.onScreen) {
							// Draw username centered above player (offset by entity height + 8 pixels)
							int textX = pos.x + (entity.widthPX / 2) - (player.username.length() * 4);
							int textY = pos.y - 8;
							g.setColor(mc.sayda.mcraze.Color.white);
							g.drawString(player.username, textX, textY);
						}
					}
				}
			}
		}

		// Don't process game input if chat is open
		boolean chatOpen = chat.isOpen();

		// Update inventory UI (with connection for packet sending)
		boolean inventoryFocus = localServer.player.inventory.updateInventory(screenWidth, screenHeight,
				screenMousePos, leftClick && !chatOpen, rightClick && !chatOpen, connection);
		if (inventoryFocus || chatOpen) {
			leftClick = false;
			rightClick = false;
		}

		// Handle block interactions
		// Always use packet-based communication with server (integrated or remote)
		// This ensures identical behavior in singleplayer and multiplayer
		if (leftClick && localServer.world != null) {
			sendBlockChangePacket(true);

			// Track breaking progress for animation (client-side visual only)
			int targetX = localServer.player.handTargetPos.x;
			int targetY = localServer.player.handTargetPos.y;

			if (targetX != -1 && targetY != -1 && localServer.world.isBreakable(targetX, targetY)) {
				if (targetX == clientBreakingX && targetY == clientBreakingY) {
					clientBreakingTicks++;
				} else {
					clientBreakingTicks = 1;
					clientBreakingX = targetX;
					clientBreakingY = targetY;
				}
			} else {
				clientBreakingTicks = 0;
			}
		} else {
			clientBreakingTicks = 0;
		}

		if (rightClick && localServer.world != null) {
			sendBlockChangePacket(false);
		}

		// Render breaking animation overlay
		if (clientBreakingTicks > 0 && clientBreakingX != -1 && clientBreakingY != -1) {
			mc.sayda.mcraze.item.Item currentItem = localServer.player.inventory.selectedItem().getItem();
			int ticksNeeded = localServer.world.breakTicks(clientBreakingX, clientBreakingY, currentItem);
			int spriteIndex = (int) (Math.min(1.0, (double) clientBreakingTicks / ticksNeeded) * (breakingSprites.length - 1));

			mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
				cameraX, cameraY, tileSize, tileSize, tileSize, clientBreakingX, clientBreakingY);
			breakingSprites[spriteIndex].draw(g, pos.x, pos.y, tileSize, tileSize);
		}

		// Update player hand visual
		localServer.player.updateHand(g, cameraX, cameraY, worldMouseX, worldMouseY, localServer.world, tileSize);

		// Render UI
		if (viewFPS) {
			// Calculate frame delta for FPS display
			long currentTime = System.nanoTime();
			frameDeltaMs = (currentTime - lastFrameTime) / 1_000_000;  // Convert to milliseconds
			lastFrameTime = currentTime;

			uiRenderer.drawFPS(g, frameDeltaMs);
		}

		uiRenderer.drawBuildMineIcons(g, localServer.player, cameraX, cameraY, tileSize);
		localServer.player.inventory.draw(g, screenWidth, screenHeight);

		// Settings menu (drawn over pause menu if open)
		if (inSettingsMenu && settingsMenu != null) {
			settingsMenu.draw(g);
			g.finishDrawing();
			return;
		}

		// Pause menu (drawn over everything else)
		if (inPauseMenu && pauseMenu != null) {
			pauseMenu.draw(g);
			g.finishDrawing();
			return;
		}

		// Death screen
		if (localServer.player.dead) {
			drawDeathScreen(g, screenWidth, screenHeight);
		}

		// Chat
		chat.draw(g, screenWidth, screenHeight);

		// Mouse cursor
		Int2 mouseTest = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, tileSize,
				tileSize, tileSize, worldMouseX, worldMouseY);
		uiRenderer.drawMouse(g, mouseTest);

		// Health and air
		uiRenderer.drawHealthBar(g, localServer.player, screenWidth, screenHeight);
		uiRenderer.drawAirBubbles(g, localServer.player, localServer.world, tileSize, screenWidth, screenHeight);

		g.finishDrawing();
	}

	/**
	 * Send input state to server
	 */
	public void sendInput() {
		if (localServer == null || localServer.player == null) return;

		// Movement keys are tracked and sent in AwtEventsHandler.sendInputPacket()
		// This packet sending here is for click events only

		// Send player input (clicks and mouse position)
		PacketPlayerInput inputPacket = new PacketPlayerInput(
				false, false, false,  // Movement keys handled by AwtEventsHandler
				leftClick, rightClick,
				screenMousePos.x, screenMousePos.y,
				localServer.player.inventory.hotbarIdx
		);
		connection.sendPacket(inputPacket);
	}

	/**
	 * Check if we're in multiplayer mode (no integrated server in Game)
	 */
	private boolean isMultiplayerMode() {
		return game != null && game.getServer() == null;
	}

	/**
	 * Send block change packet to server (works for both integrated and remote servers)
	 */
	private void sendBlockChangePacket(boolean isBreak) {
		if (localServer == null || localServer.player == null) {
			return;
		}

		// Get the target block position from player
		int targetX = localServer.player.handTargetPos.x;
		int targetY = localServer.player.handTargetPos.y;

		if (targetX == -1 || targetY == -1) {
			return;  // No valid target
		}

		// Determine the tile ID for placing
		char tileId = 0;
		if (!isBreak) {
			// For placing/interacting, convert the held item to a tile ID
			mc.sayda.mcraze.item.InventoryItem currentItem = localServer.player.inventory.selectedItem();

			if (!currentItem.isEmpty()) {
				mc.sayda.mcraze.Constants.TileID itemAsTile = mc.sayda.mcraze.Constants.TileID.fromItemId(currentItem.getItem().itemId);
				if (itemAsTile != null) {
					tileId = (char) itemAsTile.ordinal();
				}
				// If item can't be placed, tileId stays 0 - still send packet for block interactions (crafting table)
			}
			// tileId=0 will be sent for:
			//   - Empty hand (for block interactions like crafting tables)
			//   - Non-placeable items (tools, sticks, etc. - also for interactions)
		}

		// Send packet
		mc.sayda.mcraze.network.packet.PacketBlockChange packet =
			new mc.sayda.mcraze.network.packet.PacketBlockChange(targetX, targetY, tileId, isBreak);
		connection.sendPacket(packet);
	}

	/**
	 * Process packets from server (limited per frame to avoid blocking render)
	 * EXCEPT during world initialization when we need all data immediately
	 */
	// Increased from 10 to prevent packet drops during high load
	private static final int MAX_PACKETS_PER_FRAME = 100;
	private int worldInitPacketsRemaining = 0;  // Unlimited packet processing for initial world load
	private int worldInitTotalPackets = 0;  // Track total packets expected for progress calculation

	public void processPackets() {
		GameLogger logger = GameLogger.get();
		Packet[] packets = connection.receivePackets();
		if (logger != null && packets.length > 0) {
			logger.debug("Client.processPackets: Received " + packets.length + " packets");
		}

		// During world initialization, process packets in batches to keep UI responsive
		// Process up to 50 packets per frame to allow loading screen updates
		int maxPackets = (worldInitPacketsRemaining > 0) ? 50 : MAX_PACKETS_PER_FRAME;

		int packetsProcessed = 0;
		for (Packet packet : packets) {
			if (packetsProcessed >= maxPackets) {
				if (logger != null && worldInitPacketsRemaining <= 5) {
					logger.debug("Client.processPackets: Processed batch of " + maxPackets + ", " + worldInitPacketsRemaining + " world init packets remaining");
				}
				break;
			}

			// Decrement counter for world init packets and update loading progress
			if (worldInitPacketsRemaining > 0) {
				worldInitPacketsRemaining--;

				// Update loading screen progress (30% to 90% during packet reception)
				if (worldInitTotalPackets > 0) {
					int packetsReceived = worldInitTotalPackets - worldInitPacketsRemaining;
					int progress = 30 + (int)((packetsReceived / (float)worldInitTotalPackets) * 60);
					setLoadingProgress(progress);

					// Update status message every 20 packets
					if (packetsReceived % 20 == 0) {
						setLoadingStatus("Receiving world data... (" + packetsReceived + "/" + worldInitTotalPackets + ")");
					}
				}

				// When world init is complete, refresh lighting and hide loading screen
				if (worldInitPacketsRemaining == 0 && localServer != null && localServer.world != null) {
					setLoadingStatus("Initializing lighting...");
					setLoadingProgress(95);
					if (logger != null) logger.info("Client: World sync complete, refreshing lighting...");
					localServer.world.refreshLighting();
					if (logger != null) logger.info("Client: Lighting refresh complete");
					// Hide loading screen now that world is ready
					setLoadingProgress(100);
					setLoadingStatus("Done!");
					hideLoadingScreen();
					if (logger != null) logger.info("Client: Loading screen hidden");
				}
			}

			try {
				packet.handle(this);
				packetsProcessed++;
			} catch (Exception e) {
				if (logger != null) logger.error("Client.processPackets: Exception in " + packet.getClass().getSimpleName(), e);
				packetsProcessed++;
			}
		}
	}

	/**
	 * Draw death screen overlay
	 */
	private void drawDeathScreen(GraphicsHandler g, int screenWidth, int screenHeight) {
		g.setColor(new Color(0, 0, 0, 180));
		g.fillRect(0, 0, screenWidth, screenHeight);

		g.setColor(Color.white);
		String deathText = "YOU DIED!";
		g.drawString(deathText, screenWidth / 2 - 50, screenHeight / 2 - 40);

		String respawnText = "Press 'R' to Respawn";
		g.drawString(respawnText, screenWidth / 2 - 80, screenHeight / 2 + 10);
	}

	// Input handling
	public void setMousePosition(int x, int y) {
		screenMousePos.x = x;
		screenMousePos.y = y;
	}

	public void setLeftClick(boolean pressed) {
		leftClick = pressed;
	}

	public void setRightClick(boolean pressed) {
		rightClick = pressed;
	}

	public void toggleFPS() {
		viewFPS = !viewFPS;
	}

	public void startGame() {
		inMenu = false;
		musicPlayer.play();
	}

	/**
	 * Switch to multiplayer mode (reuse existing window/graphics)
	 */
	public void switchToMultiplayer(Connection newConnection, Server newLocalServer) {
		GameLogger logger = GameLogger.get();

		if (logger != null) {
			logger.info("Client: Switching to multiplayer mode");
		}

		// Switch to new connection and local server
		this.connection = newConnection;
		this.localServer = newLocalServer;

		// Set chat reference
		newLocalServer.setChat(chat);

		// Start game (exit menu)
		startGame();

		if (logger != null) logger.info("Client: Multiplayer mode active - inMenu=" + inMenu);
	}

	/**
	 * Disconnect from multiplayer server
	 */
	public void disconnectMultiplayer() {
		// Close network connection
		if (connection != null) {
			connection.disconnect();
		}

		// Stop local world holder
		if (localServer != null) {
			localServer.stop();
		}

		// Note: Don't set localServer to null here, as we'll need to recreate
		// the integrated server when returning to singleplayer
		// That will be handled by Game class
	}

	public void goToMainMenu() {
		// Save the game before returning to menu (only in singleplayer)
		if (game != null && game.getServer() != null && game.getServer().world != null) {
			game.saveGame();
		}
		inMenu = true;
		inPauseMenu = false;  // Close pause menu if open
		musicPlayer.pause();

		// Reset main menu to root state
		if (menu != null) {
			menu.resetToRoot();
		}
	}

	/**
	 * Open the pause menu
	 */
	public void openPauseMenu() {
		if (pauseMenu != null) {
			pauseMenu.refresh();  // Update button states
			inPauseMenu = true;
		}
	}

	/**
	 * Close the pause menu
	 */
	public void closePauseMenu() {
		inPauseMenu = false;
	}

	/**
	 * Open the settings menu
	 */
	public void openSettingsMenu() {
		inSettingsMenu = true;
		inPauseMenu = false;  // Hide pause menu when showing settings
	}

	/**
	 * Close the settings menu (returns to pause menu)
	 */
	public void closeSettingsMenu() {
		inSettingsMenu = false;
		inPauseMenu = true;  // Show pause menu again
	}

	/**
	 * Show the loading screen
	 */
	public void showLoadingScreen() {
		inLoadingScreen = true;
		inMenu = false;  // Hide main menu while loading
		if (loadingScreen != null) {
			loadingScreen.setStatus("Loading...");
			loadingScreen.setProgress(0);
		}
	}

	/**
	 * Hide the loading screen
	 */
	public void hideLoadingScreen() {
		inLoadingScreen = false;
	}

	/**
	 * Update loading screen status
	 */
	public void setLoadingStatus(String status) {
		if (loadingScreen != null) {
			loadingScreen.setStatus(status);
		}
	}

	/**
	 * Update loading screen progress (0-100)
	 */
	public void setLoadingProgress(int progress) {
		if (loadingScreen != null) {
			loadingScreen.setProgress(progress);
		}
	}

	/**
	 * Add a message to the loading screen console
	 */
	public void addLoadingMessage(String message) {
		if (loadingScreen != null) {
			loadingScreen.addMessage(message);
		}
	}

	/**
	 * Check if pause menu is open
	 */
	public boolean isInPauseMenu() {
		return inPauseMenu;
	}

	public boolean isRunning() {
		return running;
	}

	/**
	 * Check if we're in multiplayer or LAN mode (need continuous input packets)
	 */
	private boolean isMultiplayerOrLAN() {
		// NetworkConnection = multiplayer client
		// LocalConnection + game has LAN enabled = LAN mode (host or client)
		return connection instanceof mc.sayda.mcraze.network.NetworkConnection ||
		       (game != null && game.getServer() != null && game.getServer().isLANEnabled());
	}

	/**
	 * Called by render loop to send input packets from AwtEventsHandler state
	 * This requests AwtEventsHandler to send the current input state
	 */
	private void sendInputFromEventHandler() {
		// Request AwtEventsHandler to send current input state via Game
		if (game != null) {
			game.sendCurrentInputState();
		}
	}

	public boolean isInMenu() {
		return inMenu;
	}

	public MainMenu getMenu() {
		return menu;
	}

	public UIRenderer getUIRenderer() {
		return uiRenderer;
	}

	/**
	 * Get the local player entity (works in both singleplayer and multiplayer)
	 */
	public mc.sayda.mcraze.entity.Player getLocalPlayer() {
		if (localServer != null) {
			return localServer.player;
		}
		return null;
	}

	public void stop() {
		running = false;
	}

	// ===== Packet Handlers =====

	@Override
	public void handleWorldInit(PacketWorldInit packet) {
		GameLogger logger = GameLogger.get();

		if (logger != null) {
			logger.info("Client: Received PacketWorldInit - dimensions: " + packet.worldWidth + "x" + packet.worldHeight + ", seed: " + packet.seed);
		}

		// Store which player entity belongs to this client
		myPlayerUUID = packet.playerUUID;
		if (logger != null) {
			logger.info("Client: My player UUID is " + myPlayerUUID);
		}

		// Enable unlimited packet processing for initial world data
		// Use server's expected packet count (or fallback to 200 for old servers)
		int expectedPackets = (packet.totalPacketsExpected > 0) ? packet.totalPacketsExpected : 200;
		worldInitPacketsRemaining = expectedPackets;
		worldInitTotalPackets = expectedPackets;
		if (logger != null) {
			logger.info("Client: Expecting " + expectedPackets + " world initialization packets");
		}

		if (localServer != null) {
			try {
				// Create empty world that will be populated by server
				localServer.world = mc.sayda.mcraze.world.World.createEmpty(
					packet.worldWidth,
					packet.worldHeight,
					new java.util.Random(packet.seed)
				);

				// Set spawn location for respawning
				localServer.setSpawnLocation(packet.spawnX, packet.spawnY);

				if (logger != null) {
					logger.info("Client: Empty world created successfully - " + localServer.world.width + "x" + localServer.world.height);
					logger.info("Client: Spawn location set to (" + packet.spawnX + ", " + packet.spawnY + ")");
				}
			} catch (Exception e) {
				if (logger != null) logger.error("Client.handleWorldInit: FATAL ERROR creating world", e);
				throw e;
			}
		} else {
			if (logger != null) logger.error("Client.handleWorldInit: localServer is NULL!");
		}
	}

	@Override
	public void handleWorldUpdate(PacketWorldUpdate packet) {
		GameLogger logger = GameLogger.get();

		// Update local world state from server
		if (localServer != null && localServer.world != null) {
			localServer.world.setTicksAlive(packet.ticksAlive);

			// Apply block changes
			if (packet.changedX != null && packet.changedX.length > 0) {
				for (int i = 0; i < packet.changedX.length; i++) {
					// Convert char to TileID and add/remove tile
					if (packet.changedTiles[i] == 0) {
						localServer.world.removeTile(packet.changedX[i], packet.changedY[i]);
					} else {
						mc.sayda.mcraze.Constants.TileID tileId = mc.sayda.mcraze.Constants.TileID.values()[packet.changedTiles[i]];
						localServer.world.addTile(packet.changedX[i], packet.changedY[i], tileId);
					}
				}
			}
		} else {
			if (logger != null) logger.warn("Client.handleWorldUpdate: localServer or world is NULL!");
		}
	}

	@Override
	public void handleEntityUpdate(PacketEntityUpdate packet) {
		GameLogger logger = GameLogger.get();

		// Update entity positions from server
		if (localServer == null || packet.entityIds == null) {
			if (logger != null) logger.warn("Client.handleEntityUpdate: localServer or entityIds is NULL!");
			return;
		}

		// Ensure we have the right number of entities
		// Note: Entities may be null temporarily during sync - this is intentional
		while (localServer.entities.size() < packet.entityIds.length) {
			localServer.entities.add(null);
		}

		// Update entity positions and velocities
		for (int i = 0; i < packet.entityIds.length; i++) {
			mc.sayda.mcraze.entity.Entity entity = null;

			// Get existing entity or create new one
			if (i < localServer.entities.size()) {
				entity = localServer.entities.get(i);
			}

			// Create entity if it doesn't exist
			if (entity == null) {
				// Create entity based on type from packet
				String entityType = (packet.entityTypes != null && i < packet.entityTypes.length)
					? packet.entityTypes[i] : "Player";

				if ("Item".equals(entityType)) {
					// Create Item entity
					String itemId = (packet.itemIds != null && i < packet.itemIds.length)
						? packet.itemIds[i] : null;

					if (itemId != null) {
						mc.sayda.mcraze.item.Item itemTemplate = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
						if (itemTemplate != null) {
							entity = itemTemplate.clone();
							entity.x = packet.entityX[i];
							entity.y = packet.entityY[i];
							if (logger != null) {
								logger.debug("Client: Created Item entity: " + itemId);
							}
						}
					}

					// If item creation failed, create a fallback
					if (entity == null) {
						if (logger != null) {
							logger.warn("Client: Failed to create Item entity, using fallback");
						}
						entity = new mc.sayda.mcraze.entity.Player(true, packet.entityX[i], packet.entityY[i], 7 * 4, 14 * 4);
					}
				} else {
					// Create Player entity
					entity = new mc.sayda.mcraze.entity.Player(
						true,
						packet.entityX[i],
						packet.entityY[i],
						7 * 4,  // width
						14 * 4  // height
					);

					// Set player username
					if (packet.playerNames != null && i < packet.playerNames.length && packet.playerNames[i] != null) {
						((mc.sayda.mcraze.entity.Player) entity).username = packet.playerNames[i];
					}
				}

				localServer.entities.set(i, entity);

				// Assign player by UUID match (not index)
				String entityUUID = packet.entityUUIDs[i];
				entity.setUUID(entityUUID);  // Set UUID from packet

				if (entityUUID != null && entityUUID.equals(myPlayerUUID) && localServer.player == null) {
					if (entity instanceof mc.sayda.mcraze.entity.Player) {
						localServer.player = (mc.sayda.mcraze.entity.Player) entity;
						if (logger != null) {
							logger.info("Client: Player entity created and assigned to localServer.player! (UUID: " + myPlayerUUID + ")");
						}
					}
				}
			}

			// Update entity state from packet
			entity.x = packet.entityX[i];
			entity.y = packet.entityY[i];
			entity.dx = packet.entityDX[i];
			entity.dy = packet.entityDY[i];

			// Update living entity specific properties
			if (entity instanceof mc.sayda.mcraze.entity.LivingEntity) {
				mc.sayda.mcraze.entity.LivingEntity livingEntity = (mc.sayda.mcraze.entity.LivingEntity) entity;
				livingEntity.hitPoints = packet.entityHealth[i];

				// Update facingRight if packet has it
				if (packet.facingRight != null && i < packet.facingRight.length) {
					livingEntity.facingRight = packet.facingRight[i];
				}

				// Update dead status if packet has it
				if (packet.dead != null && i < packet.dead.length) {
					livingEntity.dead = packet.dead[i];
				}
			}
		}

		// Log only at DEBUG level (controlled by logger configuration)
		if (logger != null) {
			logger.debug("Client.handleEntityUpdate: All entities processed, player=" + (localServer.player != null ? "SET" : "NULL"));
		}
	}

	@Override
	public void handleEntityRemove(mc.sayda.mcraze.network.packet.PacketEntityRemove packet) {
		GameLogger logger = GameLogger.get();

		if (localServer == null || localServer.entities == null) {
			if (logger != null) logger.warn("Client.handleEntityRemove: localServer or entities is NULL!");
			return;
		}

		// Remove entity by UUID from local entity list
		boolean removed = localServer.entities.removeIf(e -> e != null && e.getUUID() != null && e.getUUID().equals(packet.entityUUID));

		if (logger != null) {
			if (removed) {
				logger.info("Client: Removed entity with UUID: " + packet.entityUUID);
			} else {
				logger.debug("Client: Entity with UUID " + packet.entityUUID + " not found in local entity list");
			}
		}
	}

	@Override
	public void handleInventoryUpdate(PacketInventoryUpdate packet) {
		GameLogger logger = GameLogger.get();

		if (localServer == null || localServer.player == null || localServer.player.inventory == null) {
			if (logger != null) logger.warn("Client.handleInventoryUpdate: localServer, player, or inventory is NULL!");
			return;
		}

		// Only apply inventory update if it's for THIS player
		String localPlayerUUID = localServer.player.getUUID();
		if (packet.playerUUID == null || !packet.playerUUID.equals(localPlayerUUID)) {
			// This inventory update is for a different player - ignore it
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client.handleInventoryUpdate: Ignoring inventory for different player (packet UUID: " + packet.playerUUID + ", local UUID: " + localPlayerUUID + ")");
			}
			return;
		}

		mc.sayda.mcraze.ui.Inventory inv = localServer.player.inventory;
		int width = inv.inventoryItems.length;
		int height = inv.inventoryItems[0].length;
		int totalSlots = width * height;

		if (packet.itemIds == null || packet.itemIds.length != totalSlots) {
			if (logger != null) logger.warn("Client.handleInventoryUpdate: Invalid packet data!");
			return;
		}

		// Update inventory from packet
		inv.hotbarIdx = packet.hotbarIndex;

		// Update inventory UI state
		// NOTE: Do NOT sync visibility - it's client-side UI state
		// inv.setVisible(packet.visible);  // This would close the inventory immediately!
		inv.tableSizeAvailable = packet.tableSizeAvailable;

		// Unflatten inventory array
		int index = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				String itemId = packet.itemIds[index];
				int count = packet.itemCounts[index];
				int uses = packet.toolUses[index];

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
						if (logger != null) logger.warn("Client.handleInventoryUpdate: Unknown item ID: " + itemId);
					}
				}

				index++;
			}
		}
	}

	@Override
	public void handleChatMessage(PacketChatMessage packet) {
		// Display chat message
		chat.addMessage(packet.message, packet.getColor());
	}

	// Server-bound packet handlers (client doesn't handle these)
	@Override
	public void handlePlayerInput(PacketPlayerInput packet) {}

	@Override
	public void handleBlockChange(PacketBlockChange packet) {}

	@Override
	public void handleInventoryAction(PacketInventoryAction packet) {}

	@Override
	public void handleChatSend(PacketChatSend packet) {}

	@Override
	public void handlePlayerDeath(PacketPlayerDeath packet) {
		// Immediate death synchronization from server
		if (localServer != null && localServer.player != null) {
			localServer.player.dead = true;
			localServer.player.hitPoints = 0;
			System.out.println("CLIENT: Player death received from server");
		}
	}

	@Override
	public void handleBreakingProgress(PacketBreakingProgress packet) {
		// Server-authoritative breaking progress (ISSUE #3 fix)
		if (packet.progress > 0) {
			// Update client breaking animation
			clientBreakingX = packet.x;
			clientBreakingY = packet.y;
			clientBreakingTicks = packet.progress;
		} else {
			// Reset breaking animation
			clientBreakingX = -1;
			clientBreakingY = -1;
			clientBreakingTicks = 0;
		}
	}

	@Override
	public void handleAuthRequest(PacketAuthRequest packet) {
		// Client doesn't handle auth requests (only server does)
	}

	@Override
	public void handleAuthResponse(PacketAuthResponse packet) {
		GameLogger logger = GameLogger.get();
		if (logger != null) {
			logger.info("Client: Received authentication response - success=" + packet.success);
		}

		if (packet.success) {
			// Authentication successful - continue with world init
			System.out.println("Authentication successful");
			// World init will be sent by server automatically
		} else {
			// Authentication failed - show error and disconnect
			System.err.println("Authentication failed: " + packet.message);
			// Disconnect from server
			connection.disconnect();
			hideLoadingScreen();
			// Return to main menu
			if (game != null) {
				game.getClient().goToMainMenu();
			}
		}
	}
}
