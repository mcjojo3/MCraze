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

package mc.sayda.client;

import mc.sayda.Color;
import mc.sayda.GraphicsHandler;
import mc.sayda.MusicPlayer;
import mc.sayda.network.Connection;
import mc.sayda.network.Packet;
import mc.sayda.network.PacketHandler;
import mc.sayda.network.packet.*;
import mc.sayda.server.Server;
import mc.sayda.ui.Chat;
import mc.sayda.ui.CommandHandler;
import mc.sayda.ui.MainMenu;
import mc.sayda.ui.UIRenderer;
import mc.sayda.util.Int2;
import mc.sayda.util.StockMethods;
import mc.sayda.util.SystemTimer;

/**
 * Client handles rendering and input.
 * Communicates with server (local or remote) via packets.
 */
public class Client implements PacketHandler {
	// Connection to server
	public Connection connection;  // Public for Game to send packets
	private Server localServer;  // For integrated server (singleplayer)
	private mc.sayda.Game game;  // Reference to Game for save/load

	// Systems (for integrated server)
	private mc.sayda.system.BlockInteractionSystem blockInteractionSystem;

	// Rendering
	private UIRenderer uiRenderer;
	public boolean viewFPS = false;
	private int tileSize = 32;

	// Input state
	public boolean leftClick = false;
	public boolean rightClick = false;
	public Int2 screenMousePos = new Int2(0, 0);

	// UI
	public Chat chat;
	private MainMenu menu;
	private boolean inMenu = true;

	// Audio
	public MusicPlayer musicPlayer = new MusicPlayer("sounds/music.ogg");

	// Client state
	private boolean running = true;

	public Client(Connection connection, Server localServer) {
		this.connection = connection;
		this.localServer = localServer;

		// Initialize systems (for integrated server)
		blockInteractionSystem = new mc.sayda.system.BlockInteractionSystem(new java.util.Random());

		// Initialize UI
		uiRenderer = new UIRenderer();
		chat = new Chat();
		// Note: CommandHandler is created by Server after Client construction
		menu = new MainMenu(null, uiRenderer);  // Game reference set later via setGame()

		System.out.println("Client: Initialized");
	}

	/**
	 * Set Game reference (called after Game construction to avoid circular dependency)
	 */
	public void setGame(mc.sayda.Game game) {
		this.game = game;
		menu.setGame(game);
		// Initialize graphics handler now that we have the Game reference
		GraphicsHandler.get().init(game);
	}

	/**
	 * Client rendering loop
	 */
	public void render() {
		if (!running || localServer == null || localServer.player == null) return;

		GraphicsHandler g = GraphicsHandler.get();
		g.startDrawing();

		// Render menu
		if (inMenu) {
			menu.draw(g);
			uiRenderer.drawMouse(g, screenMousePos);
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
		}

		// Render entities
		for (mc.sayda.entity.Entity entity : localServer.entities) {
			entity.draw(g, cameraX, cameraY, screenWidth, screenHeight, tileSize);
		}

		// Don't process game input if chat is open
		boolean chatOpen = chat.isOpen();

		// Update inventory UI
		boolean inventoryFocus = localServer.player.inventory.updateInventory(screenWidth, screenHeight,
				screenMousePos, leftClick && !chatOpen, rightClick && !chatOpen);
		if (inventoryFocus || chatOpen) {
			leftClick = false;
			rightClick = false;
		}

		// Handle block interactions (for integrated server - direct access)
		// Don't reset leftClick/rightClick here - let them persist while mouse is held down
		if (leftClick && localServer.world != null) {
			// Block breaking - called every frame while mouse is held down
			// Note: This is a direct call for integrated server; in multiplayer this would be via packets
			blockInteractionSystem.handleBlockBreaking(g, localServer.player, localServer.world, localServer.entities,
					cameraX, cameraY, tileSize, true);
		}

		if (rightClick && localServer.world != null) {
			// Block placing - uses persistent blockInteractionSystem
			blockInteractionSystem.handleBlockPlacing(localServer.player, localServer.world, tileSize);
		}

		// Update player hand visual
		localServer.player.updateHand(g, cameraX, cameraY, worldMouseX, worldMouseY, localServer.world, tileSize);

		// Render UI
		if (viewFPS) {
			uiRenderer.drawFPS(g, 16);  // TODO: Calculate actual delta
		}

		uiRenderer.drawBuildMineIcons(g, localServer.player, cameraX, cameraY, tileSize);
		localServer.player.inventory.draw(g, screenWidth, screenHeight);

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

		// For now, we're accessing server state directly (integrated server)
		// TODO: Send proper input packets

		// Send player input
		PacketPlayerInput inputPacket = new PacketPlayerInput(
				false, false, false,  // TODO: Track movement keys
				leftClick, rightClick,
				screenMousePos.x, screenMousePos.y,
				localServer.player.inventory.hotbarIdx
		);
		connection.sendPacket(inputPacket);
	}

	/**
	 * Process packets from server
	 */
	public void processPackets() {
		Packet[] packets = connection.receivePackets();
		for (Packet packet : packets) {
			packet.handle(this);
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

	public void goToMainMenu() {
		// Save the game before returning to menu
		if (game != null && localServer != null && localServer.world != null) {
			game.saveGame();
		}
		inMenu = true;
		musicPlayer.pause();
	}

	public boolean isRunning() {
		return running;
	}

	public void stop() {
		running = false;
	}

	// ===== Packet Handlers =====

	@Override
	public void handleWorldUpdate(PacketWorldUpdate packet) {
		// Update local world state from server
		if (localServer != null && localServer.world != null) {
			localServer.world.setTicksAlive(packet.ticksAlive);

			// Apply block changes
			if (packet.changedX != null) {
				for (int i = 0; i < packet.changedX.length; i++) {
					// Convert char to TileID and add/remove tile
					if (packet.changedTiles[i] == 0) {
						localServer.world.removeTile(packet.changedX[i], packet.changedY[i]);
					} else {
						mc.sayda.Constants.TileID tileId = mc.sayda.Constants.TileID.values()[packet.changedTiles[i]];
						localServer.world.addTile(packet.changedX[i], packet.changedY[i], tileId);
					}
				}
			}
		}
	}

	@Override
	public void handleEntityUpdate(PacketEntityUpdate packet) {
		// Update entity positions from server
		// TODO: Implement when we have proper entity sync
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
	public void handleChatSend(PacketChatSend packet) {}
}
