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

package mc.sayda.mcraze.client;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.player.specialization.ui.ClassSelectionUI;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.Constants.TileID;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.audio.MusicPlayer;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.ClientPacketHandler;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.packet.*;
import mc.sayda.mcraze.server.Server;
import mc.sayda.mcraze.state.GameState;
import mc.sayda.mcraze.ui.screen.Chat;
import mc.sayda.mcraze.ui.menu.MainMenu;
import mc.sayda.mcraze.graphics.UIRenderer;
import mc.sayda.mcraze.ui.view.InventoryView;
import mc.sayda.mcraze.ui.view.ChestView;
import mc.sayda.mcraze.ui.view.FurnaceView;

import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.world.gen.Biome;
import java.util.Random;

/**
 * Client handles rendering and input.
 * Communicates with server (local or remote) via packets.
 */
public class Client implements ClientPacketHandler {
	// Connection to server
	public Connection connection; // Public for Game to send packets
	private Server localServer; // For integrated server (singleplayer)
	private mc.sayda.mcraze.Game game; // Reference to Game for save/load
	Random random = new Random();

	// Reusable collections for performance
	private java.util.Map<String, mc.sayda.mcraze.entity.Entity> reusableEntityMap;

	// Client-side breaking animation (visual only)
	private int clientBreakingTicks = 0;
	private int clientBreakingX = -1;
	private int clientBreakingY = -1;
	private mc.sayda.mcraze.graphics.Sprite[] breakingSprites;

	// Client-side visual effects
	private static class ExplosionEffect {
		float x, y, radius;
		int ticks;

		ExplosionEffect(float x, float y, float radius) {
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.ticks = 0;
		}
	}

	private java.util.List<ExplosionEffect> activeExplosions = new java.util.ArrayList<>();

	// Rendering
	private UIRenderer uiRenderer;
	public boolean viewFPS = false;
	private int tileSize = Constants.TILE_SIZE;
	private float zoomLevel = 1.0f; // Zoom level (1.0 = normal, 0.5 = zoomed out 2x, 2.0 = zoomed in 2x)
	private static final float MIN_ZOOM = Constants.CLIENT_MIN_ZOOM; // Max zoom out (8x)
	private static final float MAX_ZOOM = Constants.CLIENT_MAX_ZOOM; // Max zoom in (8x)

	// Input state
	public boolean leftClick = false;
	private boolean wasLeftClick = false;
	public boolean rightClick = false;
	private boolean wasRightClick = false;
	public Int2 screenMousePos = new Int2(0, 0);

	// [NEW] Movement state (centralized in Client)
	public boolean moveLeft = false;
	public boolean moveRight = false;
	public boolean climb = false;
	public boolean sneak = false;

	// Interaction timing (to prevent double-actions/multi-placement)
	private long lastRightClickTime = 0;
	private long uiOpenTime = 0;
	private static final long INTERACT_COOLDOWN_MS = Constants.CLIENT_INTERACT_COOLDOWN_MS; // 8 cps limit (reduced from
																							// 250ms/4cps)
	private static final long UI_GRACE_PERIOD_MS = Constants.CLIENT_UI_GRACE_PERIOD_MS;

	// Reusable camera and world coordinates
	public float cameraX, cameraY; // Public for AwtEventsHandler coordination
	private float worldMouseX, worldMouseY;
	private int effectiveTileSize;
	private int screenWidth, screenHeight;

	// Frame timing for FPS calculation
	private long lastFrameTime = System.nanoTime();
	private long frameDeltaMs = 16; // Default to ~60 FPS

	// UI (state-driven, no boolean flags)
	public Chat chat;
	private MainMenu menu;
	private mc.sayda.mcraze.ui.menu.PauseMenu pauseMenu;
	private mc.sayda.mcraze.ui.menu.SettingsMenu settingsMenu;
	private mc.sayda.mcraze.ui.screen.DebugOverlay debugOverlay;
	private mc.sayda.mcraze.ui.screen.LoadingScreen loadingScreen;
	private mc.sayda.mcraze.ui.container.ChestUI chestUI;
	private mc.sayda.mcraze.ui.container.FurnaceUI furnaceUI;
	private mc.sayda.mcraze.ui.container.AlchemyUI alchemyUI; // [NEW] Alchemy UI
	private InventoryView inventoryView; // New declarative inventory view
	private ChestView chestView; // New declarative chest view
	private FurnaceView furnaceView; // New declarative furnace view
	private mc.sayda.mcraze.ui.view.AlchemyView alchemyView; // [NEW] Alchemy View
	private ClassSelectionUI classSelectionUI;
	private mc.sayda.mcraze.player.specialization.ui.SkillAssignmentUI skillAssignmentUI;
	private boolean inSettingsMenu = false; // Settings is a substate of PAUSED (not in GameState enum)

	// Audio
	private MusicPlayer musicPlayer;

	public MusicPlayer getMusicPlayer() {
		return musicPlayer;
	}

	// Logging
	private final GameLogger logger = GameLogger.get();

	// Bad Apple easter egg
	private mc.sayda.mcraze.ui.BadApplePlayer badApplePlayer;

	// Client state
	private boolean running = true;
	private String myPlayerUUID = null; // UUID of this client's player entity

	private mc.sayda.mcraze.player.Player cachedLocalPlayer = null; // Cached reference for multiplayer

	public mc.sayda.mcraze.player.Player getCachedLocalPlayer() {
		return cachedLocalPlayer;
	}

	public Client(Connection connection, Server localServer) {
		this.connection = connection;
		this.localServer = localServer;

		// Initialize systems (for integrated server (singleplayer)
		// Load breaking animation sprites
		final mc.sayda.mcraze.graphics.SpriteStore ss = mc.sayda.mcraze.graphics.SpriteStore.get();
		breakingSprites = new mc.sayda.mcraze.graphics.Sprite[8];
		for (int i = 0; i < 8; i++) {
			breakingSprites[i] = ss.getSprite("assets/sprites/tiles/break" + i + ".png");
		}

		// Initialize UI
		uiRenderer = new UIRenderer();
		chat = new Chat();
		chestUI = new mc.sayda.mcraze.ui.container.ChestUI();
		furnaceUI = new mc.sayda.mcraze.ui.container.FurnaceUI();
		alchemyUI = new mc.sayda.mcraze.ui.container.AlchemyUI(); // [NEW]
		badApplePlayer = new mc.sayda.mcraze.ui.BadApplePlayer();
		// Note: CommandHandler is created by Server after Client construction
		menu = new MainMenu(null, uiRenderer); // Game reference set later via setGame()
		pauseMenu = null; // Will be created when setGame() is called
		loadingScreen = new mc.sayda.mcraze.ui.screen.LoadingScreen();
		classSelectionUI = new ClassSelectionUI(null); // Game ref set later
	}

	/**
	 * Set Game reference (called after Game construction to avoid circular
	 * dependency)
	 */
	public void setGame(mc.sayda.mcraze.Game game) {
		this.game = game;
		menu.setGame(game);
		pauseMenu = new mc.sayda.mcraze.ui.menu.PauseMenu(game, uiRenderer);
		settingsMenu = new mc.sayda.mcraze.ui.menu.SettingsMenu(game, uiRenderer, pauseMenu);
		debugOverlay = new mc.sayda.mcraze.ui.screen.DebugOverlay(game);
		classSelectionUI = new ClassSelectionUI(game);
		skillAssignmentUI = new mc.sayda.mcraze.player.specialization.ui.SkillAssignmentUI(game);

		// Initialize music player
		musicPlayer = new MusicPlayer("");

		// Initialize graphics handler now that we have the Game reference
		GraphicsHandler.get().init(game);
		applyOptions();
	}

	/**
	 * Client rendering loop
	 */
	private long lastRenderLog = 0;
	private long lastBreakPacketTime = 0; // Track last break packet for continuous breaking
	private int lastTargetBlockX = -1;
	private int lastTargetBlockY = -1;

	public void render() {
		if (!running || localServer == null) {
			if (logger != null)
				logger.warn("Client.render: Skipped due to invalid state");
			return;
		}

		// Get current game state
		GameState currentState = (game != null) ? game.getStateManager().getState() : GameState.MENU;

		// 1. Process incoming packets (Sync State)
		if (currentState.isPacketProcessing()) {
			processPackets();
		}

		// 2. Update shared state for this frame based on newest information
		updateFrameState();

		// Send input packets in multiplayer
		if (isMultiplayerOrLAN() && currentState.isGameplayState()) {
			sendInputFromEventHandler();
		}

		GraphicsHandler g = GraphicsHandler.get();
		g.startDrawing();

		// State-based rendering
		if (renderNonGameplayState(g, currentState)) {
			g.finishDrawing();
			return;
		}

		// Beyond this point is gameplay rendering
		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null) {
			if (logger != null)
				logger.warn("Client.render: Player is null, skipping gameplay render");
			g.finishDrawing();
			return;
		}

		// Update music
		updateMusicContext(player);

		// Render World
		renderWorld(g);

		// Render Entities
		renderEntities(g);

		// Handle UI Interaction & Rendering
		renderGameplayUI(g, player);

		// Record input state for 'justPressed' detection in next frame
		wasLeftClick = leftClick;
		wasRightClick = rightClick;

		g.finishDrawing();
	}

	private void updateFrameState() {
		GraphicsHandler g = GraphicsHandler.get();
		screenWidth = g.getScreenWidth();
		screenHeight = g.getScreenHeight();
		effectiveTileSize = getEffectiveTileSize();

		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player != null) {
			cameraX = player.x - screenWidth / effectiveTileSize / 2f;
			cameraY = player.y - screenHeight / effectiveTileSize / 2f;
			computeWorldMouse(); // Ensure worldMouseX/Y are updated with new camera

			// Update hand targeting
			player.updateHand(g, cameraX, cameraY, worldMouseX, worldMouseY, localServer.world, effectiveTileSize);
		}

		// Update explosions
		for (int i = 0; i < activeExplosions.size(); i++) {
			ExplosionEffect e = activeExplosions.get(i);
			e.ticks++;
			if (e.ticks >= 15) {
				activeExplosions.remove(i);
				i--;
			}
		}
	}

	/**
	 * Recalculates worldMouseX/Y from current screenMousePos and camera
	 * Called whenever mouse or camera moves to ensure fresh coordinates for packets
	 */
	public void computeWorldMouse() {
		int ets = getEffectiveTileSize();
		worldMouseX = (cameraX * ets + screenMousePos.x) / (float) ets;
		worldMouseY = (cameraY * ets + screenMousePos.y) / (float) ets;
	}

	private boolean renderNonGameplayState(GraphicsHandler g, GameState state) {
		switch (state) {
			case LOGIN:
				if (game != null && game.getLoginScreen() != null) {
					game.getLoginScreen().draw(g);
					if (leftClick) {
						game.getLoginScreen().handleClick(g, screenMousePos.x, screenMousePos.y);
						leftClick = false;
					}
					uiRenderer.drawMouse(g, screenMousePos);
				}
				return true;

			case LOADING:
				if (loadingScreen != null) {
					loadingScreen.tick();
					loadingScreen.draw(g);
				}
				return true;

			case MENU:
				if (menu != null) {
					menu.draw(g);
					uiRenderer.drawMouse(g, screenMousePos);
				}
				return true;

			case IN_GAME:
			case PAUSED:
				// Bad Apple easter egg
				if (badApplePlayer != null && badApplePlayer.isPlaying()) {
					badApplePlayer.render(screenWidth, screenHeight);
					g.setColor(mc.sayda.mcraze.graphics.Color.white);
					String hint = "Press ESC to exit";
					g.drawString(hint, (screenWidth - g.getStringWidth(hint)) / 2, 20);
					return true;
				}
				return false;

			default:
				return true;
		}
	}

	private void updateMusicContext(mc.sayda.mcraze.player.Player player) {
		if (musicPlayer != null && localServer.world != null) {
			boolean isUnderground = player.y > Constants.CAVE_DEPTH;
			boolean isNight = localServer.world.isNight();
			String newContext = isUnderground ? "cave" : (isNight ? "night" : "day");
			musicPlayer.switchContext(newContext);
		}
	}

	private void renderWorld(GraphicsHandler g) {
		if (localServer.world != null) {
			mc.sayda.mcraze.world.storage.WorldAccess worldAccess = localServer.getWorldAccess();
			if (worldAccess != null) {
				worldAccess.renderWithLock(world -> world.draw(g, 0, 0, screenWidth, screenHeight, cameraX,
						cameraY, effectiveTileSize));
			} else {
				localServer.world.draw(g, 0, 0, screenWidth, screenHeight, cameraX, cameraY, effectiveTileSize);
			}
		}
	}

	private void renderEntities(GraphicsHandler g) {
		java.util.List<mc.sayda.mcraze.entity.Entity> entities = (localServer.getSharedWorld() != null)
				? localServer.getSharedWorld().getAllEntities()
				: localServer.entities;

		for (mc.sayda.mcraze.entity.Entity entity : entities) {
			if (entity != null) {
				entity.draw(g, cameraX, cameraY, screenWidth, screenHeight, effectiveTileSize);
				if (entity instanceof mc.sayda.mcraze.player.Player) {
					renderPlayerName(g, (mc.sayda.mcraze.player.Player) entity);
				}
			}
		}
	}

	private void renderPlayerName(GraphicsHandler g, mc.sayda.mcraze.player.Player p) {
		if (p.username != null && !p.username.isEmpty()) {
			mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
					cameraX, cameraY, screenWidth, screenHeight, effectiveTileSize, p.x, p.y);
			if (mc.sayda.mcraze.util.StockMethods.onScreen) {
				if (p.cachedNameWidth == -1) {
					p.cachedNameWidth = g.getStringWidth(p.username);
				}
				int textX = pos.x + (p.widthPX / 2) - (p.cachedNameWidth / 2);
				int textY = pos.y - 8;
				g.setColor(mc.sayda.mcraze.graphics.Color.white);
				g.drawString(p.username, textX, textY);
			}
		}
	}

	private void renderGameplayUI(GraphicsHandler g, mc.sayda.mcraze.player.Player player) {
		boolean chatOpen = chat.isOpen();
		long currentTime = System.currentTimeMillis();
		boolean canProcessUIClicks = (currentTime - uiOpenTime >= UI_GRACE_PERIOD_MS);

		// 1. Handle UI Interactivity (Containers, Inventory)
		boolean uiHasFocus = handleContainerInteractions(chatOpen, canProcessUIClicks);

		// 2. Handle Game World Interactivity (Breaking, Attacking)
		if (!uiHasFocus && !chatOpen && !isClassSelectionUIOpen()) {
			handleGameInteractions(currentTime);
		}

		// Update input state tracking
		wasLeftClick = leftClick;
		wasRightClick = rightClick;

		// 3. Render Overlays & HUD
		renderOverlays(g, player);
	}

	private boolean handleContainerInteractions(boolean chatOpen, boolean canProcess) {
		boolean justL = leftClick && !wasLeftClick && !chatOpen && canProcess;
		boolean justR = rightClick && !wasRightClick && !chatOpen && canProcess;
		boolean heldL = leftClick && !chatOpen;
		boolean heldR = rightClick && !chatOpen;

		boolean focus = false;

		// Chest
		if (chestUI != null && chestUI.isVisible()) {
			focus |= chestUI.update(screenWidth, screenHeight, screenMousePos, justL, justR, heldL, heldR, connection,
					isShiftPressed());
		}

		// Furnace
		if (!focus && furnaceUI != null && furnaceUI.isVisible()) {
			focus |= furnaceUI.update(screenWidth, screenHeight, screenMousePos, justL, justR, heldL, heldR, connection,
					isShiftPressed());
		}

		// Alchemy
		if (!focus && alchemyUI != null && alchemyUI.isVisible()) {
			focus |= alchemyUI.update(screenWidth, screenHeight, screenMousePos, justL, justR, heldL, heldR, connection,
					isShiftPressed());
		}

		// Inventory
		if (!focus && getLocalPlayer().inventory != null) {
			focus |= getLocalPlayer().inventory.updateInventory(screenWidth, screenHeight, screenMousePos, justL, justR,
					heldL, heldR, connection, isShiftPressed());
		}

		return focus;
	}

	private void handleGameInteractions(long currentTime) {
		boolean justL = leftClick && !wasLeftClick;
		boolean justR = rightClick && !wasRightClick;

		if (justL && connection != null) {
			String uuid = getEntityUUIDAtMouse();
			if (uuid != null) {
				connection.sendPacket(new mc.sayda.mcraze.network.packet.PacketEntityAttack(uuid));
			} else {
				sendBlockChangePacket(true);
				lastBreakPacketTime = currentTime;
			}
		} else if (leftClick && connection != null) {
			// Continuous breaking
			int tileX = (int) Math.floor(worldMouseX);
			int tileY = (int) Math.floor(worldMouseY);
			boolean targetChanged = (tileX != lastTargetBlockX || tileY != lastTargetBlockY);

			if (currentTime - lastBreakPacketTime >= 500 || targetChanged) {
				sendBlockChangePacket(true);
				lastBreakPacketTime = currentTime;
				lastTargetBlockX = tileX;
				lastTargetBlockY = tileY;
			}
		} else if (!leftClick && wasLeftClick && connection != null) {
			connection.sendPacket(new mc.sayda.mcraze.network.packet.PacketBlockChange(-1, -1, (char) 0, true));
			clientBreakingTicks = 0;
			clientBreakingX = -1;
			clientBreakingY = -1;
		}

		if (justR && connection != null) {
			String uuid = getEntityUUIDAtMouse();
			if (uuid != null) {
				connection.sendPacket(new mc.sayda.mcraze.network.packet.PacketEntityInteract(uuid));
			}
		}

		if (rightClick && connection != null && currentTime - lastRightClickTime >= INTERACT_COOLDOWN_MS) {
			lastRightClickTime = currentTime;
			sendBlockChangePacket(false);
		}
	}

	private void renderOverlays(GraphicsHandler g, mc.sayda.mcraze.player.Player player) {
		// Breaking animation
		renderBreakingAnimation(g, player);

		// Explosions
		renderExplosions(g);

		// HUD & Icons
		uiRenderer.drawBuildMineIcons(g, player, cameraX, cameraY, effectiveTileSize);

		// Inventory & Tooltips
		renderInventoryOverlay(g, player);

		// Container Overlays
		renderContainerOverlays(g);

		// Performance/Debug
		renderDebugInfo(g);

		// Vital Stats
		uiRenderer.drawHealthBar(g, player, screenWidth, screenHeight);
		uiRenderer.drawManaBar(g, player, screenWidth, screenHeight);
		uiRenderer.drawEssenceBar(g, player, screenWidth, screenHeight);
		uiRenderer.drawBowChargeBar(g, player, screenWidth, screenHeight);
		uiRenderer.drawAirBubbles(g, player, localServer.world, Constants.TILE_SIZE, screenWidth, screenHeight);

		// Buff List (top right corner)
		renderBuffList(g, player);

		// Menus (drawn on top)
		renderMenus(g);

		// Chat
		chat.draw(g, screenWidth, screenHeight);

		// Final mouse cursor
		mc.sayda.mcraze.util.Int2 mouseTest = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
				cameraX, cameraY, effectiveTileSize, effectiveTileSize, effectiveTileSize, worldMouseX, worldMouseY);
		uiRenderer.drawMouse(g, mouseTest);
	}

	/**
	 * Render active buffs in the top right corner of the screen.
	 * Displays buff name, level (if > 0), and remaining duration in seconds.
	 */
	private void renderBuffList(GraphicsHandler g, mc.sayda.mcraze.player.Player player) {
		java.util.List<mc.sayda.mcraze.entity.buff.Buff> buffs = player.activeBuffs;
		if (buffs == null || buffs.isEmpty()) {
			return;
		}

		int startX = screenWidth - 10; // Right aligned
		int startY = 10;
		int lineHeight = 14;

		g.setColor(mc.sayda.mcraze.graphics.Color.white);

		for (int i = 0; i < buffs.size(); i++) {
			mc.sayda.mcraze.entity.buff.Buff buff = buffs.get(i);
			String name = formatBuffName(buff.getType().name());
			int level = buff.getAmplifier();
			int durationTicks = buff.getDuration();
			int durationSeconds = durationTicks / 20; // 20 ticks per second

			// Format: "Regeneration II (15s)" or "Speed (30s)"
			StringBuilder sb = new StringBuilder();
			sb.append(name);
			if (level > 0) {
				sb.append(" ").append(toRoman(level + 1));
			}
			sb.append(" (").append(durationSeconds).append("s)");

			String text = sb.toString();
			int textWidth = g.getStringWidth(text);
			int x = startX - textWidth;
			int y = startY + (i * lineHeight);

			// Draw semi-transparent background
			g.setColor(new mc.sayda.mcraze.graphics.Color(0, 0, 0, 128));
			g.fillRect(x - 4, y - 2, textWidth + 8, lineHeight);

			// Draw text
			g.setColor(getBuffColor(buff.getType()));
			g.drawString(text, x, y);
		}
	}

	/**
	 * Format buff type name to be more readable (e.g., DAMAGE_RESISTANCE -> Damage
	 * Resistance)
	 */
	private String formatBuffName(String enumName) {
		StringBuilder sb = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : enumName.toCharArray()) {
			if (c == '_') {
				sb.append(' ');
				capitalizeNext = true;
			} else {
				if (capitalizeNext) {
					sb.append(Character.toUpperCase(c));
					capitalizeNext = false;
				} else {
					sb.append(Character.toLowerCase(c));
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Convert number to Roman numeral (for buff levels)
	 */
	private String toRoman(int num) {
		if (num <= 0 || num > 10)
			return String.valueOf(num);
		String[] romans = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
		return romans[num - 1];
	}

	/**
	 * Get color for buff type (positive = green/blue, negative = red/orange)
	 */
	private mc.sayda.mcraze.graphics.Color getBuffColor(mc.sayda.mcraze.entity.buff.BuffType type) {
		switch (type) {
			case REGENERATION:
				return new mc.sayda.mcraze.graphics.Color(255, 150, 150); // Light red/pink
			case SPEED:
				return new mc.sayda.mcraze.graphics.Color(150, 200, 255); // Light blue
			case SLOWNESS:
				return new mc.sayda.mcraze.graphics.Color(100, 100, 150); // Dark blue-gray
			case DAMAGE_RESISTANCE:
				return new mc.sayda.mcraze.graphics.Color(200, 200, 200); // Gray/silver
			case STRENGTH:
				return new mc.sayda.mcraze.graphics.Color(255, 100, 100); // Red
			case WEAKNESS:
				return new mc.sayda.mcraze.graphics.Color(150, 100, 100); // Dark red
			case STEADFAST:
				return new mc.sayda.mcraze.graphics.Color(200, 180, 100); // Gold
			case FRENZY:
				return new mc.sayda.mcraze.graphics.Color(255, 150, 50); // Orange
			case FOCUS:
				return new mc.sayda.mcraze.graphics.Color(150, 255, 150); // Light green
			case WELL_FED:
				return new mc.sayda.mcraze.graphics.Color(200, 150, 100); // Brown/tan
			default:
				return mc.sayda.mcraze.graphics.Color.white;
		}
	}

	private void renderBreakingAnimation(GraphicsHandler g, mc.sayda.mcraze.player.Player player) {
		if (clientBreakingTicks > 0 && clientBreakingX != -1 && clientBreakingY != -1 && player.inventory != null) {
			mc.sayda.mcraze.item.Item currentItem = player.inventory.selectedItem().getItem();
			int ticksNeeded = localServer.world.breakTicks(clientBreakingX, clientBreakingY, currentItem);
			int spriteIndex = (int) (Math.min(1.0, (double) clientBreakingTicks / ticksNeeded)
					* (breakingSprites.length - 1));

			mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
					cameraX, cameraY, effectiveTileSize, effectiveTileSize, effectiveTileSize, clientBreakingX,
					clientBreakingY);
			breakingSprites[spriteIndex].draw(g, pos.x, pos.y, effectiveTileSize, effectiveTileSize);
		}
	}

	private void renderExplosions(GraphicsHandler g) {
		for (ExplosionEffect e : activeExplosions) {
			float pct = (float) e.ticks / 15;
			float currentRadius = e.radius * (0.5f + pct * 0.5f);
			int alpha = (int) (255 * (1.0f - pct));

			mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(
					cameraX, cameraY, screenWidth, screenHeight, effectiveTileSize, e.x, e.y);

			if (StockMethods.onScreen) {
				g.setColor(new mc.sayda.mcraze.graphics.Color(255, 100, 0, alpha));
				int r = (int) (currentRadius * effectiveTileSize);
				g.fillOval(pos.x - r, pos.y - r, r * 2, r * 2);

				// Outer ring
				g.setColor(new mc.sayda.mcraze.graphics.Color(255, 200, 0, alpha / 2));
				int r2 = (int) (currentRadius * 1.2f * effectiveTileSize);
				g.fillOval(pos.x - r2, pos.y - r2, r2 * 2, r2 * 2);
			}
		}
	}

	private void renderInventoryOverlay(GraphicsHandler g, mc.sayda.mcraze.player.Player player) {
		if (player.inventory != null) {
			if (inventoryView == null || inventoryView.getInventory() != player.inventory) {
				inventoryView = new InventoryView(player.inventory);
			}
			inventoryView.updateTooltip(screenMousePos.x, screenMousePos.y);
			inventoryView.render(g, screenWidth, screenHeight);
		}
	}

	private void renderContainerOverlays(GraphicsHandler g) {
		if (chestUI != null && chestUI.isVisible()) {
			if (chestView == null)
				chestView = new ChestView(chestUI);
			chestView.updateTooltip(screenMousePos.x, screenMousePos.y);
			chestView.render(g, screenWidth, screenHeight, chestUI.getChestItems(), chestUI.getPlayerInventory());
		}

		if (furnaceUI != null && furnaceUI.isVisible()) {
			if (furnaceView == null)
				furnaceView = new FurnaceView(furnaceUI);
			furnaceView.updateTooltip(screenMousePos.x, screenMousePos.y);
			furnaceView.render(g, screenWidth, screenHeight, furnaceUI.getPlayerInventory());
		}

		if (alchemyUI != null && alchemyUI.isVisible()) {
			if (alchemyView == null)
				alchemyView = new mc.sayda.mcraze.ui.view.AlchemyView(alchemyUI);
			alchemyView.updateTooltip(screenMousePos.x, screenMousePos.y);
			alchemyView.render(g, screenWidth, screenHeight, alchemyUI.getAlchemySlots(),
					alchemyUI.storedEssence, alchemyUI.brewProgress, alchemyUI.brewTimeTotal,
					alchemyUI.getPlayerInventory());
		}
	}

	private void renderDebugInfo(GraphicsHandler g) {
		long currentNanoTime = System.nanoTime();
		frameDeltaMs = (currentNanoTime - lastFrameTime) / 1_000_000;
		lastFrameTime = currentNanoTime;

		if (isShowingFPS()) {
			uiRenderer.drawFPS(g, frameDeltaMs);
		}

		if (debugOverlay != null) {
			debugOverlay.draw(g, this, localServer);
		}
	}

	private void renderMenus(GraphicsHandler g) {
		GameState state = game.getStateManager().getState();

		if (inSettingsMenu && settingsMenu != null) {
			settingsMenu.draw(g);
		} else if (state == GameState.PAUSED && pauseMenu != null) {
			pauseMenu.draw(g);
		}

		if (getLocalPlayer().dead) {
			drawDeathScreen(g, screenWidth, screenHeight);
		}

		if (classSelectionUI != null && classSelectionUI.isVisible()) {
			classSelectionUI.draw(g);
		}

		if (skillAssignmentUI != null && skillAssignmentUI.isVisible()) {
			skillAssignmentUI.draw(g);
		}
	}

	/**
	 * Send input state to server
	 */
	public void sendInput() {
		if (localServer == null)
			return;

		// Ensure we use freshest world coordinates if mouse moved between frames
		computeWorldMouse();

		// Movement keys are tracked and sent in AwtEventsHandler.sendInputPacket()
		// This packet sending here is for click events only

		// Attack Logic: Check for entity hits on left click (requires fresh worldMouse)
		if (leftClick && !wasLeftClick) {
			checkEntityAttack();
		}

		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null || player.inventory == null)
			return;

		PacketPlayerInput inputPacket = new PacketPlayerInput(
				moveLeft, moveRight, climb, sneak,
				leftClick, rightClick,
				worldMouseX, worldMouseY,
				player.inventory.hotbarIdx);
		connection.sendPacket(inputPacket);
	}

	private void checkEntityAttack() {
		if (localServer == null)
			return;

		mc.sayda.mcraze.player.Player p = getLocalPlayer();
		if (p == null)
			return;

		// Use centralized worldMouseX/Y
		java.util.List<mc.sayda.mcraze.entity.Entity> entities = (localServer.getSharedWorld() != null)
				? localServer.getSharedWorld().getAllEntities()
				: localServer.entities;

		for (mc.sayda.mcraze.entity.Entity e : entities) {
			if (e == p)
				continue;
			if (e instanceof mc.sayda.mcraze.entity.LivingEntity) {
				float widthTiles = e.widthPX / (float) Constants.TILE_SIZE;
				float heightTiles = e.heightPX / (float) Constants.TILE_SIZE;

				if (worldMouseX >= e.x && worldMouseX < e.x + widthTiles &&
						worldMouseY >= e.y && worldMouseY < e.y + heightTiles) {

					float dx = e.x - p.x;
					float dy = e.y - p.y;
					if (dx * dx + dy * dy < 5.0f * 5.0f) { // 5 tiles reach
						connection.sendPacket(new mc.sayda.mcraze.network.packet.PacketEntityAttack(e.getUUID()));
						return;
					}
				}
			}
		}
	}

	/**
	 * Send block change packet to server (works for both integrated and remote
	 * servers)
	 */
	private void sendBlockChangePacket(boolean isBreak) {
		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null || connection == null) {
			return;
		}

		// BOW HANDLING (Right Click)
		// Deprecated: Bow charging is now handled via PacketPlayerInput rightClick
		// state
		// which allows for variable charge time.
		/*
		 * if (!isBreak) {
		 * mc.sayda.mcraze.item.InventoryItem holding = player.inventory.selectedItem();
		 * if (!holding.isEmpty() && holding.getItem() instanceof
		 * mc.sayda.mcraze.item.Tool) {
		 * mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool)
		 * holding.getItem();
		 * if (tool.toolType == mc.sayda.mcraze.item.Tool.ToolType.Bow) {
		 * // Don't send Place/Break packet for Bow
		 * return;
		 * }
		 * }
		 * }
		 */

		// Get the target block position from player
		int targetX = player.handTargetPos.x;
		int targetY = player.handTargetPos.y;

		if (targetX == -1 || targetY == -1) {
			return; // No valid target
		}

		// Determine the tile ID for placing
		char tileId = 0;
		if (!isBreak) {
			// CRITICAL FIX: Intercept interaction with interactable blocks (Furnace,
			// Workbench, Chest)
			// Instead of sending a Place/Change packet, send a dedicated INTERACT packet
			if (localServer != null && localServer.world != null) {
				mc.sayda.mcraze.world.tile.Tile targetTile = localServer.world.getTile(targetX, targetY);
				if (targetTile != null && targetTile.type != null) {
					mc.sayda.mcraze.Constants.TileID type = targetTile.type.name;

					if (type == mc.sayda.mcraze.Constants.TileID.FURNACE ||
							type == mc.sayda.mcraze.Constants.TileID.FURNACE_LIT) {

						mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
								targetX, targetY,
								mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.OPEN_FURNACE);
						connection.sendPacket(packet);
						return; // Stop processing block place
					} else if (type == mc.sayda.mcraze.Constants.TileID.WORKBENCH) {
						mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
								targetX, targetY,
								mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.OPEN_CRAFTING);
						connection.sendPacket(packet);
						return;
					} else if (type == mc.sayda.mcraze.Constants.TileID.CHEST) {
						mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
								targetX, targetY,
								mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.OPEN_CHEST);
						connection.sendPacket(packet);
						return;
					} else if (type == mc.sayda.mcraze.Constants.TileID.ALCHEMY_TABLE) {
						mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
								targetX, targetY,
								mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.OPEN_ALCHEMY);
						connection.sendPacket(packet);
						return;
					} else if (type == mc.sayda.mcraze.Constants.TileID.BED_LEFT ||
							type == mc.sayda.mcraze.Constants.TileID.BED_RIGHT ||
							type == mc.sayda.mcraze.Constants.TileID.FLOWER_POT ||
							type == mc.sayda.mcraze.Constants.TileID.TRAPDOOR ||
							type == mc.sayda.mcraze.Constants.TileID.TRAPDOOR_CLOSED ||
							type == mc.sayda.mcraze.Constants.TileID.BOULDER_TRAP ||
							type == mc.sayda.mcraze.Constants.TileID.DOOR_BOT ||
							type == mc.sayda.mcraze.Constants.TileID.DOOR_BOT_CLOSED ||
							type == mc.sayda.mcraze.Constants.TileID.DOOR_TOP ||
							type == mc.sayda.mcraze.Constants.TileID.DOOR_TOP_CLOSED) {
						// Generic Interact Packet for these tiles
						mc.sayda.mcraze.network.packet.PacketInteract packet = new mc.sayda.mcraze.network.packet.PacketInteract(
								targetX, targetY,
								mc.sayda.mcraze.network.packet.PacketInteract.InteractionType.INTERACT);
						connection.sendPacket(packet);
						return;
					}
					// Add other interactable blocks here (e.g. Buttons, Levers)
				}
			}

			// For placing/interacting, convert the held item to a tile ID
			mc.sayda.mcraze.item.InventoryItem currentItem = player.inventory.selectedItem();

			if (!currentItem.isEmpty()) {
				mc.sayda.mcraze.Constants.TileID itemAsTile = mc.sayda.mcraze.Constants.TileID
						.fromItemId(currentItem.getItem().itemId);
				if (itemAsTile != null) {
					tileId = (char) itemAsTile.ordinal();
				}
				// If item can't be placed, tileId stays 0 - still send packet for block
				// interactions (crafting table)
			}
			// tileId=0 will be sent for:
			// - Empty hand (for block interactions like crafting tables)
			// - Non-placeable items (tools, sticks, etc. - also for interactions)
		}

		// Send packet
		mc.sayda.mcraze.network.packet.PacketBlockChange packet = new mc.sayda.mcraze.network.packet.PacketBlockChange(
				targetX, targetY, tileId, isBreak);
		connection.sendPacket(packet);
	}

	/**
	 * Process packets from server (limited per frame to avoid blocking render)
	 * EXCEPT during world initialization when we need all data immediately
	 */
	// PERFORMANCE: Process ALL packets to prevent packet loss and stuttering
	// Dropping packets causes position desync and laggy gameplay on dedicated
	// servers
	// With packet pooling optimizations, server rarely sends >100 packets/frame
	// Increased from 200 to 1000 to handle burst scenarios without dropping packets
	private int worldInitPacketsRemaining = 0; // Unlimited packet processing for initial world load
	private int worldInitTotalPackets = 0; // Track total packets expected for progress calculation

	public void processPackets() {
		Packet[] packets = connection.receivePackets();
		if (logger != null && packets.length > 0) {
			logger.debug("Client.processPackets: Received " + packets.length + " packets");
		}

		// Process ALL packets to avoid packet loss and desync
		// Previously capped at 1000, which caused "Packet queue overflow" and entity
		// lag
		// receivingPackets() clears the queue, so any skipped packets here are lost
		// forever.

		for (Packet packet : packets) {
			// No limit check here - process everything we received

			// Decrement counter for world init packets and update loading progress
			if (worldInitPacketsRemaining > 0) {
				worldInitPacketsRemaining--;

				// Update loading screen progress (30% to 90% during packet reception)
				if (worldInitTotalPackets > 0) {
					int packetsReceived = worldInitTotalPackets - worldInitPacketsRemaining;
					int progress = 30 + (int) ((packetsReceived / (float) worldInitTotalPackets) * 60);
					setLoadingProgress(progress);

					// Update status message every 20 packets
					if (packetsReceived % 20 == 0) {
						setLoadingStatus(
								"Receiving world data... (" + packetsReceived + "/" + worldInitTotalPackets + ")");
					}
				}

				// When world init is complete, refresh lighting and hide loading screen
				if (worldInitPacketsRemaining == 0 && localServer != null && localServer.world != null) {
					setLoadingStatus("Initializing lighting...");
					setLoadingProgress(95);
					if (logger != null)
						logger.info("Client: World sync complete, refreshing lighting...");

					// PERFORMANCE FIX: Move lighting refresh to background thread to prevent render
					// blocking
					final mc.sayda.mcraze.world.World worldRef = localServer.world;
					new Thread(() -> {
						worldRef.refreshLighting();
						if (logger != null)
							logger.info("Client: Lighting refresh complete");
						// Hide loading screen now that world is ready
						setLoadingProgress(100);
						setLoadingStatus("Done!");
						hideLoadingScreen();
						if (logger != null)
							logger.info("Client: Loading screen hidden");
					}, "LightingRefreshThread").start();
				}
			}

			try {
				// All packets received by client are ServerPackets
				if (packet instanceof ServerPacket) {
					((ServerPacket) packet).handle(this);
				} else {
					if (logger != null)
						logger.warn("Client received non-ServerPacket: " + packet.getClass().getSimpleName());
				}
			} catch (Exception e) {
				if (logger != null)
					logger.error("Client.processPackets: Exception in " + packet.getClass().getSimpleName(), e);
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
	 * Returns the entity UUID or null if none
	 */
	private String getEntityUUIDAtMouse() {
		if (localServer == null) {
			return null;
		}

		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null) {
			return null;
		}

		// Use centralized worldMouseX/Y for consistency and performance
		java.util.List<mc.sayda.mcraze.entity.Entity> entities = (localServer.getSharedWorld() != null)
				? localServer.getSharedWorld().getAllEntities()
				: localServer.entities;

		for (mc.sayda.mcraze.entity.Entity entity : entities) {
			if (entity == null || (entity.getUUID() != null && entity.getUUID().equals(player.getUUID()))) {
				continue;
			}

			// Use actual visual dimensions for hitbox to match what user sees
			float entityWidth = (float) entity.widthPX / effectiveTileSize;
			float entityHeight = (float) entity.heightPX / effectiveTileSize;

			if (worldMouseX >= entity.x && worldMouseX <= entity.x + entityWidth &&
					worldMouseY >= entity.y && worldMouseY <= entity.y + entityHeight) {
				return entity.getUUID();
			}
		}

		return null;
	}

	// Input handling
	public void setMousePosition(int x, int y) {
		screenMousePos.x = x;
		screenMousePos.y = y;
	}

	/**
	 * Check if shift key is currently pressed (for inventory QoL features)
	 */
	private boolean isShiftPressed() {
		if (game == null)
			return false;
		GraphicsHandler graphicsHandler = GraphicsHandler.get();
		if (graphicsHandler instanceof mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler) {
			mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler awtHandler = (mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler) graphicsHandler;
			mc.sayda.mcraze.awtgraphics.AwtEventsHandler eventsHandler = awtHandler.getEventsHandler();
			if (eventsHandler != null) {
				return eventsHandler.isShiftPressed();
			}
		}
		return false;
	}

	public void setLeftClick(boolean pressed) {
		leftClick = pressed;
	}

	public void setRightClick(boolean pressed) {
		rightClick = pressed;
	}

	/**
	 * Zoom in (increase magnification)
	 */
	public void zoomIn() {
		zoomLevel = Math.min(MAX_ZOOM, zoomLevel * 1.25f);
		logger.info("Zoom: " + String.format("%.2f", zoomLevel) + "x");
	}

	/**
	 * Zoom out (decrease magnification)
	 */
	public void zoomOut() {
		zoomLevel = Math.max(MIN_ZOOM, zoomLevel / 1.25f);
		logger.info("Zoom: " + String.format("%.2f", zoomLevel) + "x");
	}

	/**
	 * Reset zoom to default (1.0x)
	 */
	public void resetZoom() {
		zoomLevel = 1.0f;
		logger.info("Zoom reset to 1.0x");
	}

	/**
	 * Get the current effective tile size based on zoom level
	 */
	public int getEffectiveTileSize() {
		return (int) (tileSize * zoomLevel);
	}

	public mc.sayda.mcraze.ui.screen.DebugOverlay getDebugOverlay() {
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

		if (logger != null)
			logger.info("Client: Multiplayer mode active");
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
			pauseMenu.refresh(); // Update button states
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
	 * Update loading screen progress immediately (skipping animation)
	 */
	public void setLoadingProgressImmediate(int progress) {
		if (loadingScreen != null) {
			loadingScreen.forceProgress(progress);
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

	/**
	 * Check if furnace UI is open
	 */
	public boolean isFurnaceUIOpen() {
		return furnaceUI != null && furnaceUI.isVisible();
	}

	/**
	 * Close furnace UI
	 */
	public void closeFurnaceUI() {
		if (furnaceUI != null) {
			furnaceUI.close();
		}
	}

	public boolean isAlchemyUIOpen() {
		return alchemyUI != null && alchemyUI.isVisible();
	}

	public void closeAlchemyUI() {
		if (alchemyUI != null) {
			alchemyUI.close();
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

	public MainMenu getMainMenu() {
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
	public mc.sayda.mcraze.player.Player getLocalPlayer() {
		if (myPlayerUUID == null)
			return null;

		// 1. Hot cache (Valid for the entire frame once found)
		if (cachedLocalPlayer != null && myPlayerUUID.equals(cachedLocalPlayer.getUUID())) {
			return cachedLocalPlayer;
		}

		// 2. Direct server access (Fastest fallback)
		if (localServer != null && localServer.player != null && myPlayerUUID.equals(localServer.player.getUUID())) {
			cachedLocalPlayer = localServer.player;
			return cachedLocalPlayer;
		}

		// 3. Search entities (Slowest path - only happens during init or after major
		// state change)
		if (localServer != null && localServer.entities != null) {
			for (int i = 0; i < localServer.entities.size(); i++) {
				mc.sayda.mcraze.entity.Entity e = localServer.entities.get(i);
				if (e instanceof mc.sayda.mcraze.player.Player && myPlayerUUID.equals(e.getUUID())) {
					cachedLocalPlayer = (mc.sayda.mcraze.player.Player) e;
					return cachedLocalPlayer;
				}
			}
		}

		// Log failure occasionally
		long now = System.currentTimeMillis();
		if (now - lastRenderLog > 5000) { // Reduced frequency to 5s
			lastRenderLog = now;
			if (logger != null) {
				logger.warn("Client: Local player not found yet (UUID=" + myPlayerUUID + ")");
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

		if (logger != null) {
			logger.info("Client: Received PacketWorldInit - dimensions: " + packet.worldWidth + "x" + packet.worldHeight
					+ ", seed: " + packet.seed);
		}

		// Store which player entity belongs to this client
		myPlayerUUID = packet.playerUUID;
		if (logger != null) {
			logger.info("Client: My player UUID is " + myPlayerUUID);
		}

		// Enable unlimited packet processing for initial world data
		int expectedPackets = (packet.totalPacketsExpected > 0) ? packet.totalPacketsExpected : 200;
		worldInitPacketsRemaining = expectedPackets;
		worldInitTotalPackets = expectedPackets;

		if (localServer != null) {
			try {
				// CRITICAL FIX: In integrated server mode, DON'T replace server.world!
				// The server already has a generated world with tiles. Creating an empty
				// world here would overwrite it and cause all tiles to become AIR.
				// Only create empty world for dedicated server (multiplayer) clients.

				// Check if this is integrated server or dedicated server
				// In integrated server mode, world is already generated by Server.startGame()
				// In multiplayer mode, localServer.world would be null or we'd be connecting
				// remotely
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
							new java.util.Random(packet.seed));

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
					localServer.world.darkness = packet.darkness;
					localServer.world.daylightSpeed = packet.daylightSpeed;
					localServer.world.keepInventory = packet.keepInventory;
					localServer.world.daylightCycle = packet.daylightCycle;
					localServer.world.mobGriefing = packet.mobGriefing;
					localServer.world.pvp = packet.pvp;
					localServer.world.insomnia = packet.insomnia;
					if (logger != null) {
						logger.info("Client: Applied gamerules - spelunking=" + packet.spelunking +
								", darkness=" + packet.darkness + ", daylightSpeed=" + packet.daylightSpeed +
								", keepInventory=" + packet.keepInventory + ", daylightCycle=" + packet.daylightCycle +
								", mobGriefing=" + packet.mobGriefing + ", pvp=" + packet.pvp +
								", insomnia=" + packet.insomnia);
					}
				}

				if (logger != null) {
					logger.debug("Client: World initialized at (" + packet.spawnX + ", " + packet.spawnY + ")");
				}
			} catch (Exception e) {
				if (logger != null)
					logger.error("Client.handleWorldInit: FATAL ERROR creating world", e);
				throw e;
			}
		} else {
			if (logger != null)
				logger.error("Client.handleWorldInit: localServer is NULL!");
		}
	}

	@Override
	public void handleWorldBulkData(mc.sayda.mcraze.network.packet.PacketWorldBulkData packet) {
		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("Client: handleWorldBulkData CALLED! PacketID=" + packet.getPacketId());
		}

		if (localServer == null || localServer.world == null) {
			if (logger != null)
				logger.warn("Client.handleWorldBulkData: World is null!");
			return;
		}

		// OPTIMIZATION & FIX: If we are running on Integrated Server (Host),
		// we already share the world object with the server. Skip redundant loading.
		boolean isHost = (connection instanceof mc.sayda.mcraze.network.LocalConnection);

		if (isHost) {
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client: Integrated server mode - skipping redundant bulk load");
			}
			if (localServer.world != null)
				localServer.world.refreshLighting();
			return;
		}

		mc.sayda.mcraze.world.storage.WorldAccess worldAccess = localServer.getWorldAccess();

		byte[] decompressed = packet.decompress();
		if (decompressed == null) {
			if (logger != null)
				logger.error("Client.handleWorldBulkData: Decompression failed!");
			return;
		}

		// Apply bulk data to world
		// Data format: [TileIDs (WxH)] + [Metadata (WxH)] + [Backdrops (WxH)]
		int width = packet.width;
		int height = packet.height;
		int layerSize = width * height; // Size of one layer

		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("Client: Received Bulk World Data (" + packet.compressedData.length + " bytes compressed, "
					+ decompressed.length + " bytes raw)");
			logger.debug(
					"Client: Parsing Bulk Data. Width=" + width + ", Height=" + height + ", LayerSize=" + layerSize);
		}

		if (decompressed.length < layerSize * 2) {
			if (logger != null)
				logger.warn("Client: Bulk data size mismatch! Expected at least " + (layerSize * 2) + " but got "
						+ decompressed.length);
		}

		// Apply bulk data to world
		if (worldAccess != null) {
			if (logger != null && logger.isDebugEnabled())
				logger.debug("Client: Applying bulk data with WorldAccess lock");
			worldAccess.updateWithLock(world -> {
				applyBulkToWorld(world, decompressed, layerSize, width, height, packet.x, packet.y);
			});
		} else {
			applyBulkToWorld(localServer.world, decompressed, layerSize, width, height, packet.x, packet.y);
		}

		if (logger != null && logger.isDebugEnabled())
			logger.debug("Client: World Bulk Data Applied successfully.");

		// Force full re-render
		localServer.world.refreshLighting();
	}

	@Override
	public void handleWorldUpdate(PacketWorldUpdate packet) {
		if (localServer == null) {
			if (logger != null)
				logger.warn("Client.handleWorldUpdate: localServer is NULL!");
			return;
		}

		mc.sayda.mcraze.world.storage.WorldAccess worldAccess = localServer.getWorldAccess();

		// Use WorldAccess if available, otherwise direct update
		if (worldAccess != null) {
			// Thread-safe update using write lock
			worldAccess.updateWithLock(world -> {
				applyWorldUpdateImpl(world, packet);
			});
		} else if (localServer.world != null) {
			applyWorldUpdateImpl(localServer.world, packet);
		}
	}

	private void applyWorldUpdateImpl(mc.sayda.mcraze.world.World world, PacketWorldUpdate packet) {
		world.setTicksAlive(packet.ticksAlive);

		// Apply block changes
		if (packet.changedX != null && packet.changedX.length > 0) {
			// PERFORMANCE FIX: Batch tiles FIRST, update lighting ONCE at the end
			// This prevents O(N^2) recursive lighting propagation lag (1 FPS fix)
			for (int i = 0; i < packet.changedX.length; i++) {
				if (packet.changedTiles[i] == 0) {
					// Use NoUpdate variant for performance
					world.setTileNoUpdate(packet.changedX[i], packet.changedY[i], TileID.AIR);
				} else {
					TileID tileId = TileID.values()[packet.changedTiles[i]];
					world.setTileNoUpdate(packet.changedX[i], packet.changedY[i], tileId);
				}
			}

			// Finalize lighting for all modified blocks in one pass
			// Propagate updates only after all tiles are in place
			for (int i = 0; i < packet.changedX.length; i++) {
				world.updateLightingAt(packet.changedX[i], packet.changedY[i]);
			}
		}
	}

	@Override
	public void handleEntityUpdate(PacketEntityUpdate packet) {
		if (localServer == null || packet.entityIds == null)
			return;

		if (reusableEntityMap == null) {
			reusableEntityMap = new java.util.HashMap<>();
		}

		// ERROR: Aggressive culling (removeIf) removed entities that were just out of
		// view
		// This caused shimmying and jitter as items/mobs were constantly re-added.
		// ONLY remove via PacketEntityRemove.

		// Update or create entities
		for (int i = 0; i < packet.entityIds.length; i++) {
			String uuid = packet.entityUUIDs[i];
			mc.sayda.mcraze.entity.Entity entity = reusableEntityMap.get(uuid);

			if (entity == null) {
				String type = (packet.entityTypes != null) ? packet.entityTypes[i] : "Player";
				String itemId = (packet.itemIds != null) ? packet.itemIds[i] : null;
				entity = createEntityFromPacket(type, itemId, packet.entityX[i], packet.entityY[i], uuid);

				if (entity != null) {
					entity.setUUID(uuid);
				}
			}

			if (entity == null)
				continue;

			// Assign player by UUID match
			if (uuid != null && uuid.equals(myPlayerUUID)) {
				if (localServer.player == null && entity instanceof mc.sayda.mcraze.player.Player) {
					localServer.player = (mc.sayda.mcraze.player.Player) entity;
				} else if (localServer.player != null && localServer.player != entity) {
					entity = localServer.player;
				}
			}

			// Update common state
			updateEntityCommonState(entity, packet, i);

			// Add to local list if new
			if (!reusableEntityMap.containsKey(uuid)) {
				reusableEntityMap.put(uuid, entity);
				localServer.entities.add(entity);
			}
		}

		if (logger != null && logger.isDebugEnabled()) {
			logger.debug("Client.handleEntityUpdate: " + localServer.entities.size() + " entities synced (player="
					+ (localServer.player != null ? "SET" : "NULL") + ")");
		}
	}

	private mc.sayda.mcraze.entity.Entity createEntityFromPacket(String type, String itemId, float x, float y,
			String uuid) {
		mc.sayda.mcraze.entity.Entity entity = null;

		if ("Item".equals(type)) {
			if (itemId != null) {
				mc.sayda.mcraze.item.Item template = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
				if (template != null)
					entity = template.clone();
			}
			if (entity == null)
				entity = new mc.sayda.mcraze.player.Player(true, x, y, 28, 56);
		} else if ("Sheep".equals(type) || "EntitySheep".equals(type)) {
			entity = new mc.sayda.mcraze.entity.mob.EntitySheep(x, y);
		} else if ("Wolf".equals(type) || "EntityWolf".equals(type)) {
			entity = new mc.sayda.mcraze.entity.mob.EntityWolf(true, x, y, 32, 32);
		} else if ("Pig".equals(type) || "EntityPig".equals(type)) {
			entity = new mc.sayda.mcraze.entity.mob.EntityPig(x, y);
		} else if ("Cow".equals(type) || "EntityCow".equals(type)) {
			entity = new mc.sayda.mcraze.entity.mob.EntityCow(x, y);
		} else if ("Zombie".equals(type) || "EntityZombie".equals(type)) {
			entity = new mc.sayda.mcraze.entity.mob.EntityZombie(true, x, y, 28, 56);
		} else if ("Bomber".equals(type) || "EntityBomber".equals(type)) {
			entity = new mc.sayda.mcraze.entity.mob.EntityBomber(true, x, y, 28, 56);
		} else if ("Arrow".equals(type)) {
			mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType arrowType = mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType.STONE;
			if (itemId != null) {
				for (mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType t : mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType
						.values()) {
					if (t.itemId.equals(itemId)) {
						arrowType = t;
						break;
					}
				}
			}
			entity = new mc.sayda.mcraze.entity.projectile.EntityArrow(x, y, null, arrowType);
		} else if ("Essence".equals(type) || "EntityEssence".equals(type)) {
			entity = new mc.sayda.mcraze.entity.EntityEssence(x, y, 1);
		} else if ("Boulder".equals(type)) {
			entity = new mc.sayda.mcraze.entity.projectile.EntityBoulder(x, y, true);
		} else if ("PrimedTNT".equals(type) || "EntityPrimedTNT".equals(type)) {
			entity = new mc.sayda.mcraze.entity.item.EntityPrimedTNT(x, y);
		} else {
			entity = new mc.sayda.mcraze.player.Player(true, x, y, 28, 56);
		}

		if (logger != null && entity != null) {
			logger.debug("Client: Created " + type + " (UUID: " + uuid + ")");
		}

		return entity;
	}

	private void updateEntityCommonState(mc.sayda.mcraze.entity.Entity entity, PacketEntityUpdate packet, int i) {
		entity.x = packet.entityX[i];
		entity.y = packet.entityY[i];
		entity.dx = packet.entityDX[i];
		entity.dy = packet.entityDY[i];
		entity.dead = packet.dead[i];
		entity.damageFlashTicks = packet.damageFlashTicks[i];
		entity.widthPX = packet.widthPX[i];
		entity.heightPX = packet.heightPX[i];
		entity.setTicksAlive(packet.ticksAlive[i]);

		if (entity instanceof mc.sayda.mcraze.entity.LivingEntity) {
			updateLivingEntityState((mc.sayda.mcraze.entity.LivingEntity) entity, packet, i);
		}

		if (entity instanceof mc.sayda.mcraze.player.Player) {
			updatePlayerEntityState((mc.sayda.mcraze.player.Player) entity, packet, i);
		}
	}

	private void updateLivingEntityState(mc.sayda.mcraze.entity.LivingEntity le, PacketEntityUpdate packet, int i) {
		le.hitPoints = packet.entityHealth[i];
		if (packet.entityHealth[i] <= 0 || packet.dead[i]) {
			le.hitPoints = 0;
			le.dead = true;
		} else {
			le.dead = false;
		}

		le.facingRight = packet.facingRight[i];
		le.ticksUnderwater = packet.ticksUnderwater[i];
		le.flying = packet.flying[i];
		le.noclip = packet.noclip[i];
		le.sneaking = packet.sneaking[i];
		le.climbing = packet.climbing[i];
		le.jumping = packet.jumping[i];
		le.speedMultiplier = packet.speedMultiplier[i];

		if (le instanceof mc.sayda.mcraze.entity.mob.EntityBomber) {
			mc.sayda.mcraze.entity.mob.EntityBomber bomber = (mc.sayda.mcraze.entity.mob.EntityBomber) le;
			bomber.setExploding(packet.isExploding[i]);
			bomber.setFuseTimer(packet.fuseTimer[i]);
		}

		// Sync to local player if UUID matches
		if (localServer.player != null && localServer.player.getUUID().equals(le.getUUID())) {
			localServer.player.dead = le.dead;
			localServer.player.hitPoints = le.hitPoints;
		}
	}

	private void updatePlayerEntityState(mc.sayda.mcraze.player.Player p, PacketEntityUpdate packet, int i) {
		if (packet.playerNames != null && i < packet.playerNames.length && packet.playerNames[i] != null) {
			if (!packet.playerNames[i].equals(p.username)) {
				p.username = packet.playerNames[i];
				p.cachedNameWidth = -1; // Reset cache when name changes
			}
		}

		p.backdropPlacementMode = packet.backdropPlacementMode[i];
		p.handTargetPos.x = packet.handTargetX[i];
		p.handTargetPos.y = packet.handTargetY[i];

		p.essence = packet.essence[i];
		p.maxEssence = packet.maxEssence[i];
		p.mana = packet.mana[i];
		p.maxMana = packet.maxMana[i];
		p.skillPoints = packet.skillPoints[i];

		p.inventory.hotbarIdx = packet.hotbarIndex[i];
		updatePlayerInventoryHeldItem(p, packet, i);
	}

	private void updatePlayerInventoryHeldItem(mc.sayda.mcraze.player.Player p, PacketEntityUpdate packet, int i) {
		String itemId = packet.selectedItemId[i];
		if (itemId != null && !itemId.isEmpty()) {
			mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
			if (item != null) {
				p.inventory.selectedItem().setItem(item);
				p.inventory.selectedItem().setCount(packet.selectedItemCount[i]);
				if (item instanceof mc.sayda.mcraze.item.Tool) {
					((mc.sayda.mcraze.item.Tool) p.inventory.selectedItem()
							.getItem()).uses = packet.selectedItemDurability[i];
				}
				return;
			}
		}
		p.inventory.selectedItem().setEmpty();
	}

	private void reconstructItem(mc.sayda.mcraze.item.InventoryItem slot, String itemId, int count, int uses,
			boolean mastercrafted) {
		if (itemId == null || count <= 0) {
			slot.setEmpty();
			return;
		}

		mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
		if (item != null) {
			mc.sayda.mcraze.item.Item clonedItem = item.clone();
			clonedItem.isMastercrafted = mastercrafted;
			slot.setItem(clonedItem);
			slot.setCount(count);

			if (clonedItem instanceof mc.sayda.mcraze.item.Tool) {
				mc.sayda.mcraze.item.Tool tool = (mc.sayda.mcraze.item.Tool) clonedItem;
				tool.uses = uses;
				// Apply mastercrafted durability boost for consistency
				if (mastercrafted) {
					tool.totalUses = (int) (tool.totalUses * 1.5f);
				}
			}
		} else {
			slot.setEmpty();
			if (logger != null) {
				logger.warn("Client.reconstructItem: Unknown item ID: " + itemId);
			}
		}
	}

	@Override
	public void handleEntityRemove(mc.sayda.mcraze.network.packet.PacketEntityRemove packet) {

		if (localServer == null || localServer.entities == null) {
			if (logger != null)
				logger.warn("Client.handleEntityRemove: localServer or entities is NULL!");
			return;
		}

		// Remove entity by UUID from local entity list
		boolean removed = localServer.entities
				.removeIf(e -> e != null && e.getUUID() != null && e.getUUID().equals(packet.entityUUID));

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

		// Update UI open time if inventory visibility changed to true
		// (This covers both 'E' key and server-forced opens like workbenches)
		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player != null && player.inventory != null) {
			if (!player.inventory.isVisible() && packet.visible) {
				uiOpenTime = System.currentTimeMillis();
			}
		}
		if (player == null || player.inventory == null) {
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug(
						"Client.handleInventoryUpdate: Player or inventory is NULL (player may not be initialized yet)");
			}
			return;
		}

		// Only apply inventory update if it's for THIS player
		String localPlayerUUID = player.getUUID();
		if (packet.playerUUID == null || !packet.playerUUID.equals(localPlayerUUID)) {
			// This inventory update is for a different player - ignore it
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client.handleInventoryUpdate: Ignoring inventory for different player (packet UUID: "
						+ packet.playerUUID + ", local UUID: " + localPlayerUUID + ")");
			}
			return;
		}

		mc.sayda.mcraze.player.Inventory inv = player.inventory;

		// Defensive null checks for inventoryItems array (can be null during
		// initialization)
		if (inv.inventoryItems == null || inv.inventoryItems.length == 0 || inv.inventoryItems[0] == null) {
			if (logger != null)
				logger.warn("Client.handleInventoryUpdate: Inventory items array is NULL or empty!");
			return;
		}

		int width = inv.inventoryItems.length;
		int height = inv.inventoryItems[0].length;
		int totalSlots = width * height;

		if (packet.itemIds == null || packet.itemIds.length != totalSlots) {
			if (logger != null)
				logger.warn("Client.handleInventoryUpdate: Invalid packet data!");
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
		reconstructItem(inv.getCraftable(), packet.craftableItemId, packet.craftableCount, packet.craftableToolUse,
				packet.craftableMastercrafted);

		// CRITICAL FIX: Restore cursor item (holding item) from packet
		reconstructItem(inv.holding, packet.holdingItemId, packet.holdingCount, packet.holdingToolUse,
				packet.holdingMastercrafted);

		// Unflatten inventory array
		int index = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				boolean mastercrafted = (packet.itemMastercrafted != null && packet.itemMastercrafted.length > index)
						? packet.itemMastercrafted[index]
						: false;
				reconstructItem(inv.inventoryItems[x][y], packet.itemIds[index], packet.itemCounts[index],
						packet.toolUses[index], mastercrafted);
				index++;
			}
		}

		// Update Equipment
		if (packet.equipmentItemIds != null && inv.equipment != null) {
			int count = Math.min(packet.equipmentItemIds.length, inv.equipment.length);
			for (int i = 0; i < count; i++) {
				boolean mastercrafted = (packet.equipmentMastercrafted != null
						&& packet.equipmentMastercrafted.length > i)
								? packet.equipmentMastercrafted[i]
								: false;
				reconstructItem(inv.equipment[i], packet.equipmentItemIds[i], packet.equipmentItemCounts[i],
						packet.equipmentToolUses[i], mastercrafted);
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
		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player != null) {
			player.dead = true;
			player.hitPoints = 0;
			// Player death packet received
		}
	}

	@Override
	public void handlePlayerRespawn(mc.sayda.mcraze.network.packet.PacketPlayerRespawn packet) {
		mc.sayda.mcraze.player.Player localPlayer = getLocalPlayer();

		// Case 1: Local Player Respawned
		if (localPlayer != null && packet.playerUUID.equals(localPlayer.getUUID())) {
			localPlayer.dead = false;
			localPlayer.hitPoints = localPlayer.getMaxHP(); // Full health
			if (logger != null) {
				logger.info("CLIENT: Local player respawn confirmed by server");
			}
			return;
		}

		// Case 2: Remote Player Respawned
		// Find the entity in local world and reset it
		if (localServer != null && localServer.entities != null) {
			for (mc.sayda.mcraze.entity.Entity e : localServer.entities) {
				if (e.getUUID() != null && e.getUUID().equals(packet.playerUUID)) {
					if (e instanceof mc.sayda.mcraze.entity.LivingEntity) {
						mc.sayda.mcraze.entity.LivingEntity le = (mc.sayda.mcraze.entity.LivingEntity) e;
						le.dead = false;
						le.hitPoints = le.getMaxHP();
						if (logger != null) {
							logger.info("CLIENT: Remote player respawned (UUID: " + packet.playerUUID + ")");
						}
					}
					return;
				}
			}
			if (logger != null) {
				logger.debug("CLIENT: Could not find respawned player entity (UUID: " + packet.playerUUID + ")");
			}
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
		if (logger != null) {
			logger.info("Client: Received authentication response - success=" + packet.success);
		}

		if (packet.success) {
			// Authentication successful - continue with world init
			// Authentication successful
			// World init will be sent by server automatically
		} else {
			// Authentication failed - show error and disconnect
			if (logger != null)
				logger.error("Authentication failed: " + packet.message);
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
		if (logger != null) {
			logger.info("Client: Received PacketBiomeData with " +
					(packet.biomeIndices != null ? packet.biomeIndices.length : 0) + " biomes");
		}

		if (localServer == null || localServer.world == null) {
			if (logger != null)
				logger.warn("Client.handleBiomeData: localServer or world is NULL!");
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
		if (localServer == null || localServer.world == null)
			return;

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
		if (localServer == null || localServer.world == null)
			return;
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
		uiOpenTime = System.currentTimeMillis(); // Set cooldown when opening UI

		if (chestUI == null || localServer == null || localServer.player == null) {
			if (logger != null)
				logger.warn("Client.handleChestOpen: chestUI or player is null");
			return;
		}

		// CRITICAL FIX: Ignore chest packets not consistent with our player UUID
		// (Prevents "all players open chest" bug when server broadcasts packet)
		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null)
			return;

		if (packet.targetPlayerUUID != null && !packet.targetPlayerUUID.equals(player.getUUID())) {
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client.handleChestOpen: Ignoring chest packet for different player (target: "
						+ packet.targetPlayerUUID + ")");
			}
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

		// Convert flattened item array to 10x3 grid with counts
		mc.sayda.mcraze.item.InventoryItem[][] chestItems = new mc.sayda.mcraze.item.InventoryItem[10][3];
		int idx = 0;
		for (int slotY = 0; slotY < 3; slotY++) {
			for (int slotX = 0; slotX < 10; slotX++) {
				chestItems[slotX][slotY] = new mc.sayda.mcraze.item.InventoryItem(null);
				reconstructItem(chestItems[slotX][slotY], packet.itemIds[idx], packet.itemCounts[idx],
						packet.toolUses[idx],
						false);
				idx++;
			}
		}

		// Update chest UI with items
		chestUI.updateChestItems(chestItems);

		// Open the chest UI if not already open
		if (!chestUI.isVisible()) {
			// player already retrieved and null-checked at line 1702
			if (player.inventory != null) {
				chestUI.open(packet.chestX, packet.chestY, player.inventory);
			}
		}
	}

	@Override
	public void handleFurnaceOpen(PacketFurnaceOpen packet) {
		// Only set grace period when first opening, not on sync updates
		if (furnaceUI == null || !furnaceUI.isVisible()) {
			uiOpenTime = System.currentTimeMillis();
		}

		if (furnaceUI == null || localServer == null || localServer.player == null) {
			if (logger != null)
				logger.warn("Client.handleFurnaceOpen: furnaceUI or player is null");
			return;
		}

		// Check player UUID match
		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null)
			return;

		if (packet.targetPlayerUUID != null && !packet.targetPlayerUUID.equals(player.getUUID())) {
			if (logger != null && logger.isDebugEnabled()) {
				logger.debug("Client.handleFurnaceOpen: Ignoring furnace packet for different player");
			}
			return;
		}

		if (packet.isUpdate && !furnaceUI.isVisible()) {
			return; // Ignore passive updates if UI is already closed
		}

		if (logger != null && !furnaceUI.isVisible()) {
			logger.info("Client: Opening furnace at (" + packet.furnaceX + ", " + packet.furnaceY + ")");
		}

		// Convert flattened item array to 3x2 grid
		mc.sayda.mcraze.item.InventoryItem[][] furnaceItems = new mc.sayda.mcraze.item.InventoryItem[3][2];
		int idx = 0;
		for (int slotY = 0; slotY < 2; slotY++) {
			for (int slotX = 0; slotX < 3; slotX++) {
				furnaceItems[slotX][slotY] = new mc.sayda.mcraze.item.InventoryItem(null);
				reconstructItem(furnaceItems[slotX][slotY], packet.itemIds[idx], packet.itemCounts[idx],
						packet.toolUses[idx], false);
				idx++;
			}
		}

		// Update furnace UI with reconstructed items and state
		mc.sayda.mcraze.item.InventoryItem[][] currentItems = furnaceUI.getFurnaceItems();
		for (int y = 0; y < 2; y++) {
			for (int x = 0; x < 3; x++) {
				currentItems[x][y] = furnaceItems[x][y];
			}
		}
		furnaceUI.setFuelTimeRemaining(packet.fuelTimeRemaining);
		furnaceUI.setSmeltProgress(packet.smeltProgress);
		furnaceUI.setSmeltTimeTotal(packet.smeltTimeTotal);

		// Open the furnace UI if not already open
		if (!furnaceUI.isVisible()) {
			furnaceUI.open(player.inventory, packet.furnaceX, packet.furnaceY);
		}
	}

	@Override
	public void handleAlchemyOpen(PacketAlchemyOpen packet) {
		if (alchemyUI == null || !alchemyUI.isVisible()) {
			uiOpenTime = System.currentTimeMillis();
		}

		if (alchemyUI == null || localServer == null || localServer.player == null) {
			if (logger != null)
				logger.warn("Client.handleAlchemyOpen: alchemyUI or player is null");
			return;
		}

		mc.sayda.mcraze.player.Player player = getLocalPlayer();
		if (player == null)
			return;

		if (packet.targetPlayerUUID != null && !packet.targetPlayerUUID.equals(player.getUUID())) {
			return;
		}

		if (packet.isUpdate && !alchemyUI.isVisible()) {
			return;
		}

		if (logger != null && !alchemyUI.isVisible()) {
			logger.info("Client: Opening alchemy table at (" + packet.blockX + ", " + packet.blockY + ")");
		}

		// Reconstruct alchemy items from packet
		mc.sayda.mcraze.item.InventoryItem[] alchemyItems = new mc.sayda.mcraze.item.InventoryItem[5];
		for (int i = 0; i < 5; i++) {
			alchemyItems[i] = new mc.sayda.mcraze.item.InventoryItem(null);
			reconstructItem(alchemyItems[i], packet.itemIds[i], packet.itemCounts[i], packet.toolUses[i], false);
		}

		// Update UI state
		alchemyUI.updateState(packet.storedEssence, packet.brewProgress, packet.brewTimeTotal, alchemyItems,
				packet.recipeCost);

		// Open UI if not already open
		if (!alchemyUI.isVisible()) {
			alchemyUI.open(packet.blockX, packet.blockY, player.inventory);
		}
	}

	@Override
	public void handleGameruleUpdate(PacketGameruleUpdate packet) {

		if (localServer != null && localServer.world != null) {
			localServer.world.spelunking = packet.spelunking;
			localServer.world.darkness = packet.darkness;
			localServer.world.daylightSpeed = packet.daylightSpeed;
			localServer.world.keepInventory = packet.keepInventory;
			localServer.world.daylightCycle = packet.daylightCycle;
			localServer.world.mobGriefing = packet.mobGriefing;
			localServer.world.pvp = packet.pvp;
			localServer.world.insomnia = packet.insomnia;

			if (logger != null) {
				logger.info("Client: Gamerules updated - spelunking=" + packet.spelunking +
						", darkness=" + packet.darkness + ", daylightSpeed=" + packet.daylightSpeed +
						", keepInventory=" + packet.keepInventory + ", daylightCycle=" + packet.daylightCycle +
						", mobGriefing=" + packet.mobGriefing + ", pvp=" + packet.pvp +
						", insomnia=" + packet.insomnia);
			}
		} else {
			if (logger != null)
				logger.warn("Client.handleGameruleUpdate: localServer or world is null");
		}
	}

	@Override
	public void handlePong(mc.sayda.mcraze.network.packet.PacketPong packet) {
		// Calculate round-trip time (latency)
		long now = System.currentTimeMillis();
		long rtt = now - packet.timestamp;

		// Display ping result in chat
		chat.addMessage("Pong! Latency: " + rtt + "ms", mc.sayda.mcraze.graphics.Color.orange);
	}

	/**
	 * Start Bad Apple playback and music
	 */
	public void startBadApple() {
		if (badApplePlayer != null) {
			badApplePlayer.play();
			// Play Bad Apple audio (saves current music state)
			if (musicPlayer != null) {
				musicPlayer.playOneTimeTrack("assets/sounds/music/bad_apple.wav");
			}
		}
	}

	/**
	 * Stop Bad Apple playback
	 */
	public void stopBadApple() {
		if (badApplePlayer != null && badApplePlayer.isPlaying()) {
			badApplePlayer.stop();
			// Restore normal music playback to previous context
			if (musicPlayer != null) {
				musicPlayer.restoreNormalPlayback();
			}
		}
	}

	/**
	 * Check if Bad Apple is currently playing
	 */
	public boolean isBadApplePlaying() {
		return badApplePlayer != null && badApplePlayer.isPlaying();
	}

	@Override
	public void handleItemTrigger(mc.sayda.mcraze.network.packet.PacketItemTrigger packet) {
		// Handle special item effects based on itemId
		if (packet.itemId.equals("bad_apple")) {
			startBadApple();
		}
	}

	@Override
	public void handlePlaySound(mc.sayda.mcraze.network.packet.PacketPlaySound packet) {
		String path = packet.soundName;
		if (!path.contains("/")) {
			path = "assets/sounds/" + path;
		}

		float volumeMult = 1.0f;

		// Regional sound logic
		if (!packet.isGlobal) {
			mc.sayda.mcraze.player.Player localPlayer = getLocalPlayer();
			if (localPlayer != null) {
				float dx = localPlayer.x - packet.x;
				float dy = localPlayer.y - packet.y;
				float dist = (float) Math.sqrt(dx * dx + dy * dy);

				if (dist > packet.maxDistance) {
					return; // Too far away to hear
				}

				// Linear falloff: 1.0 at source, 0.0 at maxDistance
				volumeMult = 1.0f - (dist / packet.maxDistance);
				// Apply exponential curve for more natural falloff
				volumeMult = volumeMult * volumeMult;
			}
		}

		// Apply SFX volume from settings (multiply packet volume by saved SFX volume
		// and distance mult)
		float sfxVolume = mc.sayda.mcraze.util.OptionsManager.get().getSfxVolume();
		float finalVolume = packet.volume * sfxVolume * volumeMult;

		if (finalVolume > 0.01f) {
			mc.sayda.mcraze.audio.SoundManager.playSound(path, finalVolume);
		}
	}

	@Override
	public void handleExplosion(PacketExplosion packet) {
		activeExplosions.add(new ExplosionEffect(packet.x, packet.y, packet.radius));
	}

	/**
	 * Apply persistent options to game systems
	 */
	public void applyOptions() {
		mc.sayda.mcraze.util.OptionsManager opts = mc.sayda.mcraze.util.OptionsManager.get();
		if (musicPlayer != null) {
			musicPlayer.setVolume(opts.getMusicVolume());
		}
		// DebugOverlay visibility is now independent (F3 toggles it)
		// FPS toggle only affects uiRenderer.drawFPS in render()
	}

	public void toggleFPS() {
		boolean newState = !mc.sayda.mcraze.util.OptionsManager.get().isShowFPS();
		mc.sayda.mcraze.util.OptionsManager.get().setShowFPS(newState);
		applyOptions();
	}

	public boolean isShowingFPS() {
		return mc.sayda.mcraze.util.OptionsManager.get().isShowFPS();
	}

	public ClassSelectionUI getClassSelectionUI() {
		return classSelectionUI;
	}

	public boolean isClassSelectionUIOpen() {
		return classSelectionUI != null && classSelectionUI.isVisible();
	}

	public void closeClassSelectionUI() {
		if (classSelectionUI != null) {
			classSelectionUI.setVisible(false);
		}
	}

	public mc.sayda.mcraze.player.specialization.ui.SkillAssignmentUI getSkillAssignmentUI() {
		return skillAssignmentUI;
	}

	public boolean isSkillUIOpen() {
		return skillAssignmentUI != null && skillAssignmentUI.isVisible();
	}

	public void toggleSkillUI() {
		if (skillAssignmentUI != null) {
			skillAssignmentUI.setVisible(!skillAssignmentUI.isVisible());
		}
	}

	public void closeSkillUI() {
		if (skillAssignmentUI != null) {
			skillAssignmentUI.setVisible(false);
		}
	}

	public mc.sayda.mcraze.server.Server getServer() {
		return localServer;
	}

	@Override
	public void handleWaveSync(mc.sayda.mcraze.network.packet.PacketWaveSync packet) {
		// Always update the logical world view for rendering/logic
		// Also update localServer world if it exists (Integrated Server case / LAN
		// Host)
		// This keeps the "client's server view" in sync if it exists locally
		if (localServer != null && localServer.world != null) {
			localServer.world.waveDay = packet.waveDay;
			localServer.world.waveActive = packet.waveActive;
			localServer.world.diffMultHP = packet.diffMultHP;
			localServer.world.diffMultDmg = packet.diffMultDmg;
			localServer.world.diffMultJump = packet.diffMultJump;
		}
	}

	/**
	 * Internal helper to apply decompressed bulk data to a world object
	 */
	private void applyBulkToWorld(mc.sayda.mcraze.world.World targetWorld, byte[] decompressed, int layerSize,
			int width,
			int height, int startX, int startY) {
		int index = 0;
		int debugCount = 0;
		boolean foundNonZero = false;

		for (int x = startX; x < startX + width; x++) {
			for (int y = startY; y < startY + height; y++) {
				if (index >= layerSize)
					break;

				// 1. Reconstruct Foreground
				int tileIdInt = decompressed[index] & 0xFF;

				if (tileIdInt != 0 && !foundNonZero) {
					foundNonZero = true;
					if (logger != null)
						logger.info("Client: Found VALID DATA at " + x + "," + y + " ID=" + tileIdInt);
				}

				if (debugCount < 5 && logger != null) {
					logger.info("Debug Tile [" + x + "," + y + "]: ID=" + tileIdInt);
					debugCount++;
				}

				// 2. Metadata
				int metaIndex = index + layerSize;
				int metadata = 0;
				if (metaIndex < decompressed.length) {
					metadata = decompressed[metaIndex] & 0xFF;
				}

				// 3. Backdrops (3rd layer)
				int backdropIndex = index + (layerSize * 2);
				int backdropIdInt = 0;
				if (backdropIndex < decompressed.length) {
					backdropIdInt = decompressed[backdropIndex] & 0xFF;
				}

				// Create Tile Object
				mc.sayda.mcraze.world.tile.Tile t = null;
				if (tileIdInt > 0 && tileIdInt < Constants.TileID.values().length) {
					Constants.TileID type = Constants.TileID.values()[tileIdInt];
					if (type != null && type != Constants.TileID.AIR) {
						mc.sayda.mcraze.world.tile.Tile template = mc.sayda.mcraze.Constants.tileTypes.get(type);
						if (template != null) {
							t = new mc.sayda.mcraze.world.tile.Tile(template.type);
							t.metadata = metadata;
						}
					}
				}

				// Handle Backdrop
				if (backdropIdInt > 0 && backdropIdInt < Constants.TileID.values().length) {
					Constants.TileID bgType = Constants.TileID.values()[backdropIdInt];
					if (bgType != null && bgType != Constants.TileID.AIR) {
						mc.sayda.mcraze.world.tile.Tile bgTemplate = Constants.tileTypes.get(bgType);
						if (bgTemplate != null) {
							// If we don't have a foreground tile yet, create an AIR tile to hold the
							// backdrop
							if (t == null) {
								mc.sayda.mcraze.world.tile.Tile airTemplate = Constants.tileTypes
										.get(Constants.TileID.AIR);
								if (airTemplate != null) {
									t = new mc.sayda.mcraze.world.tile.Tile(airTemplate.type);
								}
							}
							if (t != null) {
								t.backdropType = bgTemplate.type;
							}
						}
					}
				}

				// Apply to world
				targetWorld.setTileNoUpdate(x, y, t);
				index++;
			}
		}
	}
}
