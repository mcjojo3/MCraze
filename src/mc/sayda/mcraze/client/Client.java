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
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.packet.*;
import mc.sayda.mcraze.server.Server;
import mc.sayda.mcraze.state.GameState;
import mc.sayda.mcraze.ui.Chat;
import mc.sayda.mcraze.ui.CommandHandler;
import mc.sayda.mcraze.ui.MainMenu;
import mc.sayda.mcraze.ui.UIRenderer;
import mc.sayda.mcraze.ui.view.InventoryView;
import mc.sayda.mcraze.ui.view.ChestView;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.util.SystemTimer;
import mc.sayda.mcraze.world.Biome;
import java.util.Random;

/**
 * Client handles rendering and input.
 * Communicates with server (local or remote) via packets.
 */
public class Client implements ClientPacketHandler {
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
	private float zoomLevel = 1.0f;  // Zoom level (1.0 = normal, 0.5 = zoomed out 2x, 2.0 = zoomed in 2x)
	private static final float MIN_ZOOM = 0.125f;  // Max zoom out (8x)
	private static final float MAX_ZOOM = 8.0f;   // Max zoom in (8x)

	// Input state
	public boolean leftClick = false;
	public boolean rightClick = false;
	public Int2 screenMousePos = new Int2(0, 0);

	// Frame timing for FPS calculation
	private long lastFrameTime = System.nanoTime();
	private long frameDeltaMs = 16;  // Default to ~60 FPS

	// UI (state-driven, no boolean flags)
	public Chat chat;
	private MainMenu menu;
	private mc.sayda.mcraze.ui.PauseMenu pauseMenu;
	private mc.sayda.mcraze.ui.SettingsMenu settingsMenu;
	private mc.sayda.mcraze.ui.DebugOverlay debugOverlay;
	private mc.sayda.mcraze.ui.LoadingScreen loadingScreen;
	private mc.sayda.mcraze.ui.ChestUI chestUI;
	private InventoryView inventoryView;  // New declarative inventory view
	private ChestView chestView;  // New declarative chest view
	private boolean inSettingsMenu = false;  // Settings is a substate of PAUSED (not in GameState enum)

    // Audio
    public MusicPlayer musicPlayer = new MusicPlayer("sounds/music.ogg");

    // Client state
	private boolean running = true;
	private String myPlayerUUID = null;  // UUID of this client's player entity
	private mc.sayda.mcraze.entity.Player cachedLocalPlayer = null;  // Cached reference for multiplayer

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
		chestUI = new mc.sayda.mcraze.ui.ChestUI();
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
		debugOverlay = new mc.sayda.mcraze.ui.DebugOverlay(game);
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

		// Get current game state
		GameState currentState = (game != null) ? game.getStateManager().getState() : GameState.MENU;

		// Process incoming packets from server (for multiplayer) when appropriate
		if (currentState.isPacketProcessing()) {
			processPackets();
		}

		// Send input packets regularly in multiplayer/LAN mode (for continuous movement)
		if (isMultiplayerOrLAN() && currentState.isGameplayState()) {
			sendInputFromEventHandler();
		}

		GraphicsHandler g = GraphicsHandler.get();
		g.startDrawing();

		// State-based rendering
		switch (currentState) {
			case LOGIN:
				// Render login screen
				if (game != null) {
					mc.sayda.mcraze.ui.LoginScreen loginScreen = game.getLoginScreen();
					if (loginScreen != null) {
						loginScreen.draw(g);

						// Handle input for login screen
						if (leftClick) {
							loginScreen.handleClick(screenMousePos.x, screenMousePos.y);
							leftClick = false;
						}

						uiRenderer.drawMouse(g, screenMousePos);
					}
				}
				g.finishDrawing();
				return;

			case LOADING:
				// Render loading screen
				if (loadingScreen != null) {
					loadingScreen.tick();
					loadingScreen.draw(g);
				}
				g.finishDrawing();
				return;

			case MENU:
				// Render main menu
				if (menu != null) {
					menu.draw(g);
					uiRenderer.drawMouse(g, screenMousePos);
				}
				g.finishDrawing();
				return;

			case IN_GAME:
			case PAUSED:
				// Render gameplay (continue below)
				break;

			default:
				// Unknown state, skip rendering
				g.finishDrawing();
				return;
		}

		// From here on, we need the player for gameplay
		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		if (player == null) {
			if (logger != null) logger.warn("Client.render: Player is null, skipping gameplay render");
			g.finishDrawing();
			return;
		}

		// Update music context based on player position and time
		if (musicPlayer != null && localServer.world != null) {
			String newContext;
			// Determine context: cave (deep underground), night (surface at night), or day (surface during day)
			// Note: Y=0 is sky, Y=128 is sea level, Y=256 is bedrock. Surface is typically Y=100-130.
			final int CAVE_DEPTH = 140;  // Y position where cave music starts (20-30 blocks below surface)
			boolean isUnderground = player.y > CAVE_DEPTH;
			boolean isNight = localServer.world.isNight();

			if (isUnderground) {
				newContext = "cave";
			} else if (isNight) {
				newContext = "night";
			} else {
				newContext = "day";
			}

			musicPlayer.switchContext(newContext);
		}

		// Get screen dimensions
		final int screenWidth = g.getScreenWidth();
		final int screenHeight = g.getScreenHeight();

		// Get effective tile size based on zoom level
		final int effectiveTileSize = getEffectiveTileSize();

		// Calculate camera position
		float cameraX = player.x - screenWidth / effectiveTileSize / 2;
		float cameraY = player.y - screenHeight / effectiveTileSize / 2;
		float worldMouseX = (cameraX * effectiveTileSize + screenMousePos.x) / effectiveTileSize;
		float worldMouseY = (cameraY * effectiveTileSize + screenMousePos.y) / effectiveTileSize - .5f;

		// CRITICAL FIX: Update player hand target BEFORE handling click events
		// This ensures handTargetPos is current for the frame, not from the previous frame
		// Fixes inconsistent block breaking/targeting ("sometimes have to doubleclick, targets wrong block")
		player.updateHand(g, cameraX, cameraY, worldMouseX, worldMouseY, localServer.world, effectiveTileSize);

		// Render world - thread-safe using WorldAccess
		if (localServer.world != null) {
			mc.sayda.mcraze.world.WorldAccess worldAccess = localServer.getWorldAccess();
			if (worldAccess != null) {
				// Use renderWithLock to ensure consistent view during rendering
				final float finalCameraX = cameraX;
				final float finalCameraY = cameraY;
				final int finalTileSize = effectiveTileSize;
				worldAccess.renderWithLock(world ->
					world.draw(g, 0, 0, screenWidth, screenHeight, finalCameraX, finalCameraY, finalTileSize)
				);
			} else {
				// Fallback to direct rendering (backward compatibility)
				localServer.world.draw(g, 0, 0, screenWidth, screenHeight, cameraX, cameraY, effectiveTileSize);
			}
		} else {
			if (logger != null) logger.warn("Client.render: World is null, skipping world render");
		}

		// Render entities - thread-safe using SharedWorld.getAllEntities()
		// CRITICAL: getAllEntities() returns BOTH players AND world entities (items)
		// EntityManager alone only contains world entities, not players!
		java.util.List<mc.sayda.mcraze.entity.Entity> entities;
		if (localServer.getSharedWorld() != null) {
			entities = localServer.getSharedWorld().getAllEntities();  // Thread-safe, includes players!
		} else {
			entities = localServer.entities;  // Fallback to old list
		}

		for (mc.sayda.mcraze.entity.Entity entity : entities) {
			if (entity != null) {
				entity.draw(g, cameraX, cameraY, screenWidth, screenHeight, effectiveTileSize);

				// Draw username above player heads
				if (entity instanceof mc.sayda.mcraze.entity.Player) {
					mc.sayda.mcraze.entity.Player entityPlayer = (mc.sayda.mcraze.entity.Player) entity;
					if (entityPlayer.username != null && !entityPlayer.username.isEmpty()) {
						// Calculate screen position above player's head
						mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
							cameraX, cameraY, screenWidth, screenHeight, effectiveTileSize, entity.x, entity.y
						);

						if (mc.sayda.mcraze.util.StockMethods.onScreen) {
							// Draw username centered above player (offset by entity height + 8 pixels)
							int textWidth = g.getStringWidth(entityPlayer.username);
							int textX = pos.x + (entity.widthPX / 2) - (textWidth / 2);
							int textY = pos.y - 8;
							g.setColor(mc.sayda.mcraze.Color.white);
							g.drawString(entityPlayer.username, textX, textY);
						}
					}
				}
			}
		}

		// Don't process game input if chat is open
		boolean chatOpen = chat.isOpen();

		// Update chest UI first (takes priority over inventory)
		boolean chestFocus = false;
		if (chestUI != null && chestUI.isVisible()) {
			chestFocus = chestUI.update(screenWidth, screenHeight, screenMousePos,
				leftClick && !chatOpen, rightClick && !chatOpen, connection);
			if (chestFocus) {
				leftClick = false;
				rightClick = false;
			}
		}

		// Update inventory UI (with connection for packet sending)
		boolean inventoryFocus = false;
		if (player.inventory != null && player.inventory.inventoryItems != null) {
			inventoryFocus = player.inventory.updateInventory(screenWidth, screenHeight,
					screenMousePos, leftClick && !chatOpen, rightClick && !chatOpen, connection);
		}
		if (inventoryFocus || chatOpen) {
			leftClick = false;
			rightClick = false;
		}

		// Handle block interactions (not if chest is open)
		// Always use packet-based communication with server (integrated or remote)
		// This ensures identical behavior in singleplayer and multiplayer
		if (leftClick && connection != null && !chestFocus) {
			// Check if clicking on an entity first
			boolean attackedEntity = false;
			int clickedEntityId = getEntityIdAtMouse();
			if (clickedEntityId >= 0) {
				// CRITICAL FIX: Use localServer.entities directly for multiplayer compatibility
				java.util.List<mc.sayda.mcraze.entity.Entity> allEntities = localServer.entities;
				if (clickedEntityId < allEntities.size()) {
					mc.sayda.mcraze.entity.Entity entity = allEntities.get(clickedEntityId);
					if (entity instanceof mc.sayda.mcraze.entity.LivingEntity) {
						// Send attack packet for ANY item (server calculates damage)
						// Fists = 1 damage, Tools have attackDamage field, Swords have tiered damage
						mc.sayda.mcraze.network.packet.PacketEntityAttack attackPacket =
							new mc.sayda.mcraze.network.packet.PacketEntityAttack(clickedEntityId);
						connection.sendPacket(attackPacket);
						attackedEntity = true;
					}
				}
			}

			// Only break blocks if we didn't attack an entity
			if (!attackedEntity) {
				sendBlockChangePacket(true);
				// Server will send PacketBreakingProgress with authoritative breaking animation
				// No client-side tracking needed - eliminates duplicate state and visual jitter
			}
		}

		if (rightClick && connection != null && !chestFocus) {
			sendBlockChangePacket(false);
		}

		// Render breaking animation overlay
		if (clientBreakingTicks > 0 && clientBreakingX != -1 && clientBreakingY != -1 &&
			player.inventory != null && player.inventory.inventoryItems != null) {
			mc.sayda.mcraze.item.Item currentItem = player.inventory.selectedItem().getItem();
			int ticksNeeded = localServer.world.breakTicks(clientBreakingX, clientBreakingY, currentItem);
			int spriteIndex = (int) (Math.min(1.0, (double) clientBreakingTicks / ticksNeeded) * (breakingSprites.length - 1));

			mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
				cameraX, cameraY, effectiveTileSize, effectiveTileSize, effectiveTileSize, clientBreakingX, clientBreakingY);
			breakingSprites[spriteIndex].draw(g, pos.x, pos.y, effectiveTileSize, effectiveTileSize);
		}

		// Calculate frame delta for FPS display (always, not just when viewFPS is true)
		long currentTime = System.nanoTime();
		frameDeltaMs = (currentTime - lastFrameTime) / 1_000_000;  // Convert to milliseconds
		lastFrameTime = currentTime;

		// Render UI
		if (viewFPS) {
			uiRenderer.drawFPS(g, frameDeltaMs);
		}

		uiRenderer.drawBuildMineIcons(g, player, cameraX, cameraY, effectiveTileSize);
		if (player.inventory != null && player.inventory.inventoryItems != null) {
			// Initialize InventoryView lazily (when player inventory is available)
			// CRITICAL FIX: Recreate InventoryView if player changed (after rejoin)
			if (inventoryView == null || inventoryView.getInventory() != player.inventory) {
				inventoryView = new InventoryView(player.inventory);
			}

			// Update tooltip based on mouse position
			inventoryView.updateTooltip(screenMousePos.x, screenMousePos.y);

			// Render inventory using new declarative view
			inventoryView.render(g, screenWidth, screenHeight);
		}

		// Settings menu (drawn over pause menu if open)
		if (inSettingsMenu && settingsMenu != null) {
			settingsMenu.draw(g);
			g.finishDrawing();
			return;
		}

		// Pause menu (drawn over everything else when in PAUSED state)
		if (currentState == GameState.PAUSED && pauseMenu != null) {
			pauseMenu.draw(g);
			g.finishDrawing();
			return;
		}

		// Death screen
		if (player.dead) {
			drawDeathScreen(g, screenWidth, screenHeight);
		}

		// Chat
		chat.draw(g, screenWidth, screenHeight);

		// Chest UI (drawn over inventory)
		if (chestUI != null && chestUI.isVisible()) {
			// Initialize ChestView lazily (when chest UI is available)
			if (chestView == null) {
				chestView = new ChestView(chestUI);
			}

			// Render chest using new declarative view
			chestView.render(g, screenWidth, screenHeight,
				chestUI.getChestItems(), chestUI.getPlayerInventory());
		}

		// Holding item (drawn on top of inventory/chest UI)
		if (player != null && player.inventory != null && player.inventory.holding != null && !player.inventory.holding.isEmpty()) {
			int tileSize = 16;
			player.inventory.holding.draw(g,
				screenMousePos.x - tileSize / 2,
				screenMousePos.y - tileSize / 2 - tileSize,
				tileSize);
		}

		// Debug overlay (F3)
		if (debugOverlay != null) {
			debugOverlay.draw(g, this, localServer);
		}

		// Mouse cursor
		Int2 mouseTest = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, effectiveTileSize,
				effectiveTileSize, effectiveTileSize, worldMouseX, worldMouseY);
		uiRenderer.drawMouse(g, mouseTest);

		// Health and air
		uiRenderer.drawHealthBar(g, player, screenWidth, screenHeight);
		uiRenderer.drawAirBubbles(g, player, localServer.world, effectiveTileSize, screenWidth, screenHeight);

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
				false, false, false, false,  // Movement keys (and sneak) handled by AwtEventsHandler
				leftClick, rightClick,
				screenMousePos.x, screenMousePos.y,
				getLocalPlayer().inventory.hotbarIdx
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
		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		if (player == null || connection == null) {
			return;
		}

		// Get the target block position from player
		int targetX = player.handTargetPos.x;
		int targetY = player.handTargetPos.y;

		if (targetX == -1 || targetY == -1) {
			return;  // No valid target
		}

		// Determine the tile ID for placing
		char tileId = 0;
		if (!isBreak) {
			// For placing/interacting, convert the held item to a tile ID
			mc.sayda.mcraze.item.InventoryItem currentItem = player.inventory.selectedItem();

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
	// PERFORMANCE: Process ALL packets to prevent packet loss and stuttering
	// Dropping packets causes position desync and laggy gameplay on dedicated servers
	// With packet pooling optimizations, server rarely sends >100 packets/frame
	// Increased from 200 to 1000 to handle burst scenarios without dropping packets
	private static final int MAX_PACKETS_PER_FRAME = 1000;
	private int worldInitPacketsRemaining = 0;  // Unlimited packet processing for initial world load
	private int worldInitTotalPackets = 0;  // Track total packets expected for progress calculation

	public void processPackets() {
		GameLogger logger = GameLogger.get();
		Packet[] packets = connection.receivePackets();
		if (logger != null && packets.length > 0) {
			logger.debug("Client.processPackets: Received " + packets.length + " packets");
		}

		// During world initialization, process ALL packets to avoid packet loss
		// BUG FIX: receivePackets() clears the queue, so we must process all packets!
		// Normal gameplay limits to MAX_PACKETS_PER_FRAME to avoid frame drops
		int maxPackets = (worldInitPacketsRemaining > 0) ? packets.length : MAX_PACKETS_PER_FRAME;

		int packetsProcessed = 0;
		for (Packet packet : packets) {
			if (packetsProcessed >= maxPackets) {
				if (logger != null) {
					logger.warn("Client.processPackets: Hit packet limit! Dropping " + (packets.length - packetsProcessed) + " packets");
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

					// PERFORMANCE FIX: Move lighting refresh to background thread to prevent render blocking
					final mc.sayda.mcraze.world.World worldRef = localServer.world;
					new Thread(() -> {
						worldRef.refreshLighting();
						if (logger != null) logger.info("Client: Lighting refresh complete");
						// Hide loading screen now that world is ready
						setLoadingProgress(100);
						setLoadingStatus("Done!");
						hideLoadingScreen();
						if (logger != null) logger.info("Client: Loading screen hidden");
					}, "LightingRefreshThread").start();
				}
			}

			try {
				// All packets received by client are ServerPackets
				if (packet instanceof ServerPacket) {
					((ServerPacket) packet).handle(this);
				} else {
					if (logger != null) logger.warn("Client received non-ServerPacket: " + packet.getClass().getSimpleName());
				}
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

	/**
	 * Check if the mouse cursor is over an entity
	 * Returns the entity ID (index in getAllEntities list) or -1 if none
	 */
	private int getEntityIdAtMouse() {
		// CRITICAL FIX: Use localServer.entities directly instead of SharedWorld.getAllEntities()
		// For multiplayer clients, localServer doesn't have a SharedWorld, but entities list is synced from packets
		if (localServer == null || localServer.entities == null) {
			return -1;
		}

		// Convert screen coordinates to world coordinates
		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		if (player == null) {
			return -1;
		}

		int effectiveTileSize = getEffectiveTileSize();
		int screenWidth = mc.sayda.mcraze.GraphicsHandler.get().getScreenWidth();
		int screenHeight = mc.sayda.mcraze.GraphicsHandler.get().getScreenHeight();

		// Calculate camera position (same as rendering)
		float cameraX = player.x - screenWidth / effectiveTileSize / 2;
		float cameraY = player.y - screenHeight / effectiveTileSize / 2;

		// Convert mouse screen position to world position
		float worldX = (cameraX * effectiveTileSize + screenMousePos.x) / effectiveTileSize;
		float worldY = (cameraY * effectiveTileSize + screenMousePos.y) / effectiveTileSize;

		// Check all entities in the world (use entities list directly for multiplayer compatibility)
		java.util.List<mc.sayda.mcraze.entity.Entity> allEntitiesList = localServer.entities;
		for (int i = 0; i < allEntitiesList.size(); i++) {
			mc.sayda.mcraze.entity.Entity entity = allEntitiesList.get(i);

			// Skip null entities (can happen during sync)
			if (entity == null) {
				continue;
			}

			// CRITICAL FIX: Skip the local player (can't attack yourself)
			// Use UUID comparison instead of equals() to handle multiplayer correctly
			if (entity.getUUID() != null && entity.getUUID().equals(player.getUUID())) {
				continue;
			}

			// Check if mouse is within entity bounding box
			// Entity bounding box is typically 1x2 tiles (width x height)
			float entityWidth = 1.0f;
			float entityHeight = 2.0f;
			if (worldX >= entity.x && worldX <= entity.x + entityWidth &&
			    worldY >= entity.y && worldY <= entity.y + entityHeight) {
				return i;  // Return the entity ID (index in list)
			}
		}

		return -1;  // No entity found
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

	public boolean isShowingFPS() {
		return viewFPS;
	}

	/**
	 * Zoom in (increase magnification)
	 */
	public void zoomIn() {
		zoomLevel = Math.min(MAX_ZOOM, zoomLevel * 1.25f);
		GameLogger.get().info("Zoom: " + String.format("%.2f", zoomLevel) + "x");
	}

	/**
	 * Zoom out (decrease magnification)
	 */
	public void zoomOut() {
		zoomLevel = Math.max(MIN_ZOOM, zoomLevel / 1.25f);
		GameLogger.get().info("Zoom: " + String.format("%.2f", zoomLevel) + "x");
	}

	/**
	 * Reset zoom to default (1.0x)
	 */
	public void resetZoom() {
		zoomLevel = 1.0f;
		GameLogger.get().info("Zoom reset to 1.0x");
	}

	/**
	 * Get the current effective tile size based on zoom level
	 */
	public int getEffectiveTileSize() {
		return (int)(tileSize * zoomLevel);
	}

	public mc.sayda.mcraze.ui.DebugOverlay getDebugOverlay() {
		return debugOverlay;
	}

	public void startGame() {
		// State transition is handled by Game.java (LOADING → IN_GAME)
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

		if (logger != null) logger.info("Client: Multiplayer mode active");
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
		// Delegate to Game.java which handles state transition to MENU
		if (game != null) {
			game.goToMainMenu();
		}

		// Switch to menu music
		if (musicPlayer != null) {
			musicPlayer.switchContext("menu");
		}

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
		}
		// Transition to PAUSED state
		if (game != null) {
			game.getStateManager().transitionTo(GameState.PAUSED);
		}
	}

	/**
	 * Close the pause menu
	 */
	public void closePauseMenu() {
		// Transition back to IN_GAME state
		if (game != null) {
			game.getStateManager().transitionTo(GameState.IN_GAME);
		}
	}

	/**
	 * Open the settings menu (substate within PAUSED)
	 */
	public void openSettingsMenu() {
		inSettingsMenu = true;
		// Settings is a substate within PAUSED, state remains PAUSED
	}

	/**
	 * Close the settings menu (returns to pause menu)
	 */
	public void closeSettingsMenu() {
		inSettingsMenu = false;
		// Returns to PAUSED state (which shows pause menu)
	}

	/**
	 * Show the loading screen
	 */
	public void showLoadingScreen() {
		// State transition to LOADING is handled by Game.java
		if (loadingScreen != null) {
			loadingScreen.setStatus("Loading...");
			loadingScreen.setProgress(0);
		}
	}

	/**
	 * Hide the loading screen and transition to IN_GAME state
	 */
	public void hideLoadingScreen() {
		// Transition from LOADING to IN_GAME
		if (game != null && game.getStateManager() != null) {
			game.getStateManager().transitionTo(mc.sayda.mcraze.state.GameState.IN_GAME);

			// Switch to in-game music
			if (musicPlayer != null) {
				musicPlayer.switchContext("day");
			}
		}
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
		return game != null && game.getStateManager().getState() == GameState.PAUSED;
	}

	/**
	 * Check if settings menu is open
	 */
	public boolean isInSettingsMenu() {
		return inSettingsMenu;
	}

	/**
	 * Check if chest UI is open
	 */
	public boolean isChestUIOpen() {
		return chestUI != null && chestUI.isVisible();
	}

	/**
	 * Close chest UI
	 */
	public void closeChestUI() {
		if (chestUI != null) {
			chestUI.close();
		}
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
		return game != null && game.getStateManager().getState() == GameState.MENU;
	}

	public MainMenu getMenu() {
		return menu;
	}

	public UIRenderer getUIRenderer() {
		return uiRenderer;
	}

	/**
	 * Get the frame delta time in milliseconds (for FPS calculation)
	 */
	public long getFrameDeltaMs() {
		return frameDeltaMs;
	}

	/**
	 * Get the local player entity (works in both singleplayer and multiplayer)
	 */
	public mc.sayda.mcraze.entity.Player getLocalPlayer() {
		// Always find player by UUID (works for both integrated and dedicated servers)
		// This ensures each client gets their own player entity, not the host's player

		// Multiplayer: find player by UUID
		if (myPlayerUUID == null) {
			return null;  // No UUID assigned yet
		}

		// Check cache first
		if (cachedLocalPlayer != null &&
		    cachedLocalPlayer.getUUID() != null &&
		    cachedLocalPlayer.getUUID().equals(myPlayerUUID)) {
			return cachedLocalPlayer;
		}

		// Search entities for our player
		if (localServer != null && localServer.entities != null) {
			for (mc.sayda.mcraze.entity.Entity e : localServer.entities) {
				if (e instanceof mc.sayda.mcraze.entity.Player &&
				    e.getUUID() != null &&
				    e.getUUID().equals(myPlayerUUID)) {
					cachedLocalPlayer = (mc.sayda.mcraze.entity.Player) e;
					return cachedLocalPlayer;
				}
			}
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
				// CRITICAL FIX: In integrated server mode, DON'T replace server.world!
				// The server already has a generated world with tiles. Creating an empty
				// world here would overwrite it and cause all tiles to become AIR.
				// Only create empty world for dedicated server (multiplayer) clients.

				// Check if this is integrated server or dedicated server
				// In integrated server mode, world is already generated by Server.startGame()
				// In multiplayer mode, localServer.world would be null or we'd be connecting remotely
				boolean isIntegratedServer = (localServer.world != null &&
				                              localServer.world.tiles != null &&
				                              localServer.world.tiles.length > 0);

				if (isIntegratedServer) {
					// Integrated server: use the existing world from server
					// Don't replace it! The server already generated it with tiles.
					if (logger != null) {
						logger.info("Client: Integrated server mode - using server's world (" +
							localServer.world.width + "x" + localServer.world.height + ")");
					}
				} else {
					// Dedicated server (multiplayer): create empty world to be populated
					localServer.world = mc.sayda.mcraze.world.World.createEmpty(
						packet.worldWidth,
						packet.worldHeight,
						new java.util.Random(packet.seed)
					);

					if (logger != null) {
						logger.info("Client: Multiplayer mode - empty world created successfully - " +
							localServer.world.width + "x" + localServer.world.height);
					}
				}

				// Set spawn location for respawning
				localServer.setSpawnLocation(packet.spawnX, packet.spawnY);

				// Apply gamerules from server
				if (localServer.world != null) {
					localServer.world.spelunking = packet.spelunking;
					localServer.world.keepInventory = packet.keepInventory;
					localServer.world.daylightCycle = packet.daylightCycle;
					if (logger != null) {
						logger.info("Client: Applied gamerules - spelunking=" + packet.spelunking +
							", keepInventory=" + packet.keepInventory + ", daylightCycle=" + packet.daylightCycle);
					}
				}

				if (logger != null) {
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

		if (localServer == null) {
			if (logger != null) logger.warn("Client.handleWorldUpdate: localServer is NULL!");
			return;
		}

		// Use WorldAccess for thread-safe updates (prevents race conditions with rendering)
		mc.sayda.mcraze.world.WorldAccess worldAccess = localServer.getWorldAccess();
		if (worldAccess == null) {
			// Fallback to direct update (backward compatibility)
			if (localServer.world != null) {
				applyWorldUpdateDirect(packet, localServer.world);
			} else {
				if (logger != null) logger.warn("Client.handleWorldUpdate: world is NULL!");
			}
			return;
		}

		// Thread-safe update using write lock
		worldAccess.updateWithLock(world -> {
			world.setTicksAlive(packet.ticksAlive);

			// Apply block changes
			if (packet.changedX != null && packet.changedX.length > 0) {
				for (int i = 0; i < packet.changedX.length; i++) {
					// Convert char to TileID and add/remove tile
					if (packet.changedTiles[i] == 0) {
						world.removeTile(packet.changedX[i], packet.changedY[i]);
					} else {
						mc.sayda.mcraze.Constants.TileID tileId = mc.sayda.mcraze.Constants.TileID.values()[packet.changedTiles[i]];
						world.addTile(packet.changedX[i], packet.changedY[i], tileId);
					}
				}
			}
		});
	}

	/**
	 * Apply world update directly without locking (fallback for backward compatibility)
	 */
	private void applyWorldUpdateDirect(PacketWorldUpdate packet, mc.sayda.mcraze.world.World world) {
		world.setTicksAlive(packet.ticksAlive);

		if (packet.changedX != null && packet.changedX.length > 0) {
			for (int i = 0; i < packet.changedX.length; i++) {
				if (packet.changedTiles[i] == 0) {
					world.removeTile(packet.changedX[i], packet.changedY[i]);
				} else {
					mc.sayda.mcraze.Constants.TileID tileId = mc.sayda.mcraze.Constants.TileID.values()[packet.changedTiles[i]];
					world.addTile(packet.changedX[i], packet.changedY[i], tileId);
				}
			}
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

			// Update entity state from packet - pure server authority
			// Server controls all entity positions for consistent gameplay
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

				// Update ticksAlive for sprite animation sync
				if (packet.ticksAlive != null && i < packet.ticksAlive.length) {
					livingEntity.setTicksAlive(packet.ticksAlive[i]);
				}

				// Update oxygen state (FIX: drowning now works in multiplayer)
				if (packet.ticksUnderwater != null && i < packet.ticksUnderwater.length) {
					livingEntity.ticksUnderwater = packet.ticksUnderwater[i];
				}

				// Update movement states
				if (packet.flying != null && i < packet.flying.length) {
					livingEntity.flying = packet.flying[i];
				}
				if (packet.noclip != null && i < packet.noclip.length) {
					livingEntity.noclip = packet.noclip[i];
				}
				if (packet.sneaking != null && i < packet.sneaking.length) {
					livingEntity.sneaking = packet.sneaking[i];
				}
				if (packet.climbing != null && i < packet.climbing.length) {
					livingEntity.climbing = packet.climbing[i];
				}
				if (packet.jumping != null && i < packet.jumping.length) {
					livingEntity.jumping = packet.jumping[i];
				}

				// Update command effects
				if (packet.speedMultiplier != null && i < packet.speedMultiplier.length) {
					livingEntity.speedMultiplier = packet.speedMultiplier[i];
				}

				// Update Player-specific fields
				if (entity instanceof mc.sayda.mcraze.entity.Player) {
					mc.sayda.mcraze.entity.Player player = (mc.sayda.mcraze.entity.Player) entity;
					if (packet.backdropPlacementMode != null && i < packet.backdropPlacementMode.length) {
						player.backdropPlacementMode = packet.backdropPlacementMode[i];
					}
					if (packet.handTargetX != null && i < packet.handTargetX.length) {
						player.handTargetPos.x = packet.handTargetX[i];
					}
					if (packet.handTargetY != null && i < packet.handTargetY.length) {
						player.handTargetPos.y = packet.handTargetY[i];
					}

					// Synchronize held item from server - pure server authority
					// All players (host and joined) receive held item updates from server
					if (packet.hotbarIndex != null && i < packet.hotbarIndex.length) {
						player.inventory.hotbarIdx = packet.hotbarIndex[i];
					}

					// Update selected slot with item from packet
					mc.sayda.mcraze.item.InventoryItem selectedSlot = player.inventory.selectedItem();
					if (packet.selectedItemId != null && i < packet.selectedItemId.length && packet.selectedItemId[i] != null) {
						mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(packet.selectedItemId[i]);
						if (item != null) {
							mc.sayda.mcraze.item.Item clonedItem = item.clone();
							selectedSlot.setItem(clonedItem);
							selectedSlot.setCount(packet.selectedItemCount[i]);
							if (clonedItem instanceof mc.sayda.mcraze.item.Tool) {
								((mc.sayda.mcraze.item.Tool) clonedItem).uses = packet.selectedItemDurability[i];
							}
						}
					} else {
						selectedSlot.setEmpty();
					}
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

		// Get local player (works for both integrated server and multiplayer)
		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		if (player == null || player.inventory == null) {
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client.handleInventoryUpdate: Player or inventory is NULL (player may not be initialized yet)");
			}
			return;
		}

		// Only apply inventory update if it's for THIS player
		String localPlayerUUID = player.getUUID();
		if (packet.playerUUID == null || !packet.playerUUID.equals(localPlayerUUID)) {
			// This inventory update is for a different player - ignore it
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client.handleInventoryUpdate: Ignoring inventory for different player (packet UUID: " + packet.playerUUID + ", local UUID: " + localPlayerUUID + ")");
			}
			return;
		}

		mc.sayda.mcraze.ui.Inventory inv = player.inventory;

		// Defensive null checks for inventoryItems array (can be null during initialization)
		if (inv.inventoryItems == null || inv.inventoryItems.length == 0 || inv.inventoryItems[0] == null) {
			if (logger != null) logger.warn("Client.handleInventoryUpdate: Inventory items array is NULL or empty!");
			return;
		}

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
		// CRITICAL FIX: Server controls visibility to prevent workbench desync
		// When server opens workbench, it sets visible=true + tableSizeAvailable=3
		// Client must respect this to keep inventory open with 3x3 grid
		inv.setVisible(packet.visible);
		inv.tableSizeAvailable = packet.tableSizeAvailable;

		// Update craftable item preview
		mc.sayda.mcraze.item.InventoryItem craftable = inv.getCraftable();
		if (packet.craftableItemId == null || packet.craftableCount == 0) {
			// No craftable item
			craftable.setEmpty();
		} else {
			// Get item from registry
			mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(packet.craftableItemId);
			if (item != null) {
				// Clone item and set count
				mc.sayda.mcraze.item.Item clonedItem = item.clone();
				craftable.setItem(clonedItem);
				craftable.setCount(packet.craftableCount);

				// Restore tool durability
				if (clonedItem instanceof mc.sayda.mcraze.item.Tool) {
					mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) clonedItem;
					tool.uses = packet.craftableToolUse;
				}
			} else {
				// Item not found in registry
				craftable.setEmpty();
				if (logger != null) logger.warn("Client.handleInventoryUpdate: Unknown craftable item ID: " + packet.craftableItemId);
			}
		}

		// CRITICAL FIX: Restore cursor item (holding item) from packet
		// This ensures joined players see their own cursor item after inventory actions
		if (packet.holdingItemId == null || packet.holdingCount == 0) {
			// No holding item
			inv.holding.setEmpty();
		} else {
			// Get item from registry
			mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(packet.holdingItemId);
			if (item != null) {
				// Clone item and set count
				mc.sayda.mcraze.item.Item clonedItem = item.clone();
				inv.holding.setItem(clonedItem);
				inv.holding.setCount(packet.holdingCount);

				// Restore tool durability
				if (clonedItem instanceof mc.sayda.mcraze.item.Tool) {
					mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) clonedItem;
					tool.uses = packet.holdingToolUse;
				}
			} else {
				// Item not found in registry
				inv.holding.setEmpty();
				if (logger != null) logger.warn("Client.handleInventoryUpdate: Unknown holding item ID: " + packet.holdingItemId);
			}
		}

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

	@Override
	public void handlePlayerDeath(PacketPlayerDeath packet) {
		// Immediate death synchronization from server
		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		if (player != null) {
			player.dead = true;
			player.hitPoints = 0;
			System.out.println("CLIENT: Player death received from server");
		}
	}

	@Override
	public void handlePlayerRespawn(mc.sayda.mcraze.network.packet.PacketPlayerRespawn packet) {
		// Immediate respawn synchronization from server (symmetric with handlePlayerDeath)
		mc.sayda.mcraze.entity.Player player = getLocalPlayer();
		if (player != null) {
			player.dead = false;
			player.hitPoints = 100;  // Full health on respawn
			System.out.println("CLIENT: Player respawn received from server");
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

	@Override
	public void handleBiomeData(PacketBiomeData packet) {
		GameLogger logger = GameLogger.get();
		if (logger != null) {
			logger.info("Client: Received PacketBiomeData with " +
				(packet.biomeIndices != null ? packet.biomeIndices.length : 0) + " biomes");
		}

		if (localServer == null || localServer.world == null) {
			if (logger != null) logger.warn("Client.handleBiomeData: localServer or world is NULL!");
			return;
		}

		Biome[] biomeMap = packet.toBiomeArray();
		localServer.world.setBiomeMap(biomeMap);

		if (logger != null) {
			logger.info("Client: Biome map applied to world (" + biomeMap.length + " columns)");
		}
	}

	@Override
	public void handleBackdropChange(mc.sayda.mcraze.network.packet.PacketBackdropChange packet) {
		if (localServer == null || localServer.world == null) return;

		if (packet.backdropTileId == 0) {
			// Remove backdrop
			localServer.world.removeBackdrop(packet.x, packet.y);
		} else {
			// Place backdrop
			Constants.TileID backdropId = Constants.TileID.values()[packet.backdropTileId];
			localServer.world.setBackdrop(packet.x, packet.y, backdropId);
		}
	}

	@Override
	public void handleBackdropBatch(mc.sayda.mcraze.network.packet.PacketBackdropBatch packet) {
		// NEW - Batch backdrop handler (reduces join lag from 2-5 seconds to <500ms)
		if (localServer == null || localServer.world == null) return;

		GameLogger logger = GameLogger.get();
		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("Client.handleBackdropBatch: Processing " +
				(packet.x != null ? packet.x.length : 0) + " backdrop tiles");
		}

		int count = (packet.x != null) ? packet.x.length : 0;
		for (int i = 0; i < count; i++) {
			if (packet.backdropTileIds[i] == 0) {
				localServer.world.removeBackdrop(packet.x[i], packet.y[i]);
			} else {
				Constants.TileID backdropId = Constants.TileID.values()[packet.backdropTileIds[i]];
				localServer.world.setBackdrop(packet.x[i], packet.y[i], backdropId);
			}
		}

		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("Client.handleBackdropBatch: Completed batch processing");
		}
	}

	@Override
	public void handleChestOpen(PacketChestOpen packet) {
		GameLogger logger = GameLogger.get();

		if (chestUI == null || localServer == null || localServer.player == null) {
			if (logger != null) logger.warn("Client.handleChestOpen: chestUI or player is null");
			return;
		}

		// Only process if:
		// 1. Chest UI is not open (initial open)
		// 2. Chest UI is open and this is the same chest (sync update)
		if (chestUI.isVisible() &&
		    (chestUI.getChestX() != packet.chestX || chestUI.getChestY() != packet.chestY)) {
			// Different chest is open, ignore this update
			return;
		}

		if (logger != null && !chestUI.isVisible()) {
			logger.info("Client: Opening chest at (" + packet.chestX + ", " + packet.chestY + ")");
		}

		// Convert flattened item array to 9x3 grid with counts
		mc.sayda.mcraze.item.InventoryItem[][] chestItems = new mc.sayda.mcraze.item.InventoryItem[9][3];
		int idx = 0;
		for (int slotY = 0; slotY < 3; slotY++) {
			for (int slotX = 0; slotX < 9; slotX++) {
				String itemId = packet.itemIds[idx];
				int itemCount = packet.itemCounts[idx];
				chestItems[slotX][slotY] = new mc.sayda.mcraze.item.InventoryItem(null);

				if (itemId != null && itemCount > 0) {
					// Get item from registry
					mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
					if (item != null) {
						mc.sayda.mcraze.item.Item clonedItem = item.clone();
						chestItems[slotX][slotY].setItem(clonedItem);
						chestItems[slotX][slotY].setCount(itemCount);
					}
				}
				idx++;
			}
		}

		// Update chest UI with items
		chestUI.updateChestItems(chestItems);

		// Open the chest UI if not already open
		if (!chestUI.isVisible()) {
			mc.sayda.mcraze.entity.Player player = getLocalPlayer();
			if (player != null && player.inventory != null) {
				chestUI.open(packet.chestX, packet.chestY, player.inventory);
			}
		}
	}

	@Override
	public void handleGameruleUpdate(PacketGameruleUpdate packet) {
		GameLogger logger = GameLogger.get();

		if (localServer != null && localServer.world != null) {
			localServer.world.spelunking = packet.spelunking;
			localServer.world.keepInventory = packet.keepInventory;
			localServer.world.daylightCycle = packet.daylightCycle;

			if (logger != null) {
				logger.info("Client: Gamerules updated - spelunking=" + packet.spelunking +
					", keepInventory=" + packet.keepInventory + ", daylightCycle=" + packet.daylightCycle);
			}
		} else {
			if (logger != null) logger.warn("Client.handleGameruleUpdate: localServer or world is null");
		}
	}

	@Override
	public void handlePong(mc.sayda.mcraze.network.packet.PacketPong packet) {
		// Calculate round-trip time (latency)
		long now = System.currentTimeMillis();
		long rtt = now - packet.timestamp;

		// Display ping result in chat
		chat.addMessage("Pong! Latency: " + rtt + "ms", mc.sayda.mcraze.Color.orange);
	}
}
