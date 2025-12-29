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

package mc.sayda.mcraze;

import mc.sayda.mcraze.client.Client;
import mc.sayda.mcraze.logging.CrashReport;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.LocalConnection;
import mc.sayda.mcraze.server.Server;
import mc.sayda.mcraze.server.ServerTickThread;
import mc.sayda.mcraze.state.GameState;
import mc.sayda.mcraze.state.GameStateManager;
import mc.sayda.mcraze.util.CredentialManager;
import mc.sayda.mcraze.util.SystemTimer;

/**
 * Main game coordinator - creates and runs integrated server + client
 */
public class Game {
	private Server server;
	private Client client;
	private ServerTickThread serverTickThread;  // Dedicated server tick thread (Phase 4)

	// State machine (replaces boolean flags)
	private final GameStateManager stateManager = new GameStateManager();
	private String currentWorldName;  // Track current world for saving

	// Authentication session
	private String loggedInUsername;
	private String loggedInPassword;
	private boolean isLoggedIn = false;
	private mc.sayda.mcraze.ui.LoginScreen loginScreen;

	/**
	 * Construct game with integrated server (singleplayer)
	 */
	public Game() {
		this(true);
	}

	/**
	 * Construct game with optional integrated server
	 * @param integratedServer true for singleplayer with integrated server, false for multiplayer client only
	 */
	private Game(boolean integratedServer) {
		if (integratedServer) {
			System.out.println("=== Starting MCraze with Integrated Server ===");

			// Create local connection pair
			LocalConnection[] connections = LocalConnection.createPair();
			LocalConnection serverConnection = connections[1];
			LocalConnection clientConnection = connections[0];

			// Create server and client
			server = new Server(serverConnection);
			client = new Client(clientConnection, server);

			// Connect systems
			server.setChat(client.chat);
			client.setGame(this);  // Set Game reference for UI components

			// Initialize login screen
			loginScreen = new mc.sayda.mcraze.ui.LoginScreen(this, client.getUIRenderer());

			System.out.println("Integrated server initialized");
		} else {
			System.out.println("=== Starting MCraze Client Only (Multiplayer) ===");

			// Client only, no integrated server
			server = null;
			client = null;  // Will be created when connecting
		}

		System.gc();
	}

	/**
	 * Connect to multiplayer server
	 */
	public void connectMultiplayer(String host, int port) {
		try {
			// Transition to LOADING state
			stateManager.transitionTo(GameState.LOADING);

			// Show loading screen
			if (client != null) {
				client.showLoadingScreen();
				client.setLoadingStatus("Connecting to server...");
				client.setLoadingProgress(10);
				client.addLoadingMessage("Connecting to " + host + ":" + port);
				client.render();  // Force render to display loading screen
			}

			System.out.println("Connecting to multiplayer server: " + host + ":" + port);

			// Create network connection
			mc.sayda.mcraze.network.NetworkConnection connection = mc.sayda.mcraze.network.NetworkConnection.connect(host, port);

			if (client != null) {
				client.setLoadingProgress(30);
				client.addLoadingMessage("Connection established");
				client.setLoadingStatus("Authenticating...");
			}

			// Send authentication request
			String username = getLoggedInUsername();
			String password = getLoggedInPassword();
			mc.sayda.mcraze.network.packet.PacketAuthRequest authPacket =
				new mc.sayda.mcraze.network.packet.PacketAuthRequest(username, password);
			connection.sendPacket(authPacket);

			if (client != null) {
				client.addLoadingMessage("Sent authentication request");
				client.setLoadingStatus("Waiting for authentication...");
			}

			// Create a local server to hold world state (synced from remote)
			// This server won't run tick(), it just holds the client's view of the world
			mc.sayda.mcraze.network.LocalConnection[] localConnections = mc.sayda.mcraze.network.LocalConnection.createPair();
			Server localWorldHolder = new Server(localConnections[1]);

			// Don't initialize world here - it will be created when PacketWorldInit is received
			// This prevents generating a random world that gets mixed with server's world

			// Close old integrated server if exists (was running for singleplayer)
			if (server != null) {
				server.stop();
				server = null;
			}

			// Switch the existing client to multiplayer mode (reuse same window)
			if (client != null) {
				client.switchToMultiplayer(connection, localWorldHolder);
				client.addLoadingMessage("Receiving world data...");
			}

			System.out.println("Connected to multiplayer server successfully");
			// Loading screen will be hidden automatically when world init is complete (in Client.java)
		} catch (java.io.IOException e) {
			System.err.println("Failed to connect to server: " + e.getMessage());
			e.printStackTrace();
			// Transition back to MENU state
			stateManager.transitionTo(GameState.MENU);
			// Hide loading screen on error
			if (client != null) {
				client.hideLoadingScreen();
			}
			// TODO: Show error message to user
		}
	}

	/**
	 * Start a new game or load existing save (singleplayer) with authentication
	 * @param worldName Name of the world (for saving/loading)
	 * @param load Whether to load existing world or create new
	 * @param width World width (for new worlds only)
	 * @param username Authenticated username
	 * @param password Authenticated password
	 */
	public void startGame(String worldName, boolean load, int width, String username, String password) {
		this.currentWorldName = worldName;

		// Transition to LOADING state
		stateManager.transitionTo(GameState.LOADING);

		// Show loading screen
		client.showLoadingScreen();
		client.setLoadingStatus("Initializing...");
		client.setLoadingProgress(10);
		client.render();  // Force render to display loading screen

		// If server is null (e.g., after returning from multiplayer), recreate integrated server
		if (server == null) {
			System.out.println("Recreating integrated server for singleplayer");
			client.addLoadingMessage("Creating server...");
			client.render();  // Update display

			// Create local connection pair
			mc.sayda.mcraze.network.LocalConnection[] connections = mc.sayda.mcraze.network.LocalConnection.createPair();
			mc.sayda.mcraze.network.LocalConnection serverConnection = connections[1];
			mc.sayda.mcraze.network.LocalConnection clientConnection = connections[0];

			// Create server and reconnect client
			server = new Server(serverConnection);
			server.setChat(client.chat);
			client.switchToMultiplayer(clientConnection, server);  // Reuse this to reconnect
		}

		client.setLoadingProgress(30);
		client.render();  // Update display

		// Load or generate world
		mc.sayda.mcraze.world.World loadedWorld = null;
		if (load) {
			// Try to load existing world
			client.setLoadingStatus("Loading world...");
			client.addLoadingMessage("Loading world '" + worldName + "'...");
			client.render();  // Update display
			loadedWorld = mc.sayda.mcraze.world.WorldSaveManager.loadWorldOnly(worldName);

			if (loadedWorld != null) {
				System.out.println("World '" + worldName + "' loaded");
				client.addLoadingMessage("World loaded successfully");
			} else {
				System.out.println("Failed to load world, will generate new one");
				client.addLoadingMessage("Load failed, generating new world...");
			}
			client.render();  // Update display
		}

		// Start game with loaded world (or null to generate new)
		if (loadedWorld == null) {
			client.setLoadingStatus("Generating world...");
			client.addLoadingMessage("Generating new world (" + width + " blocks wide)...");
		}
		client.setLoadingProgress(50);
		client.render();  // Update display

		server.startGame(width, worldName, username, password, loadedWorld);

		// Update client connection to use the new one from SharedWorld
		client.switchToMultiplayer(server.getHostClientConnection(), server);

		if (loadedWorld != null) {
			client.addLoadingMessage("Game started with loaded world");
		} else {
			System.out.println("Created new world: " + worldName);
			client.addLoadingMessage("World generated successfully");
		}
		client.setLoadingProgress(80);
		client.render();  // Update display

		client.setLoadingStatus("Starting game...");
		client.addLoadingMessage("Initializing client...");
		client.setLoadingProgress(90);
		client.render();  // Update display
		client.startGame();

		// Phase 5: Add state barriers to ensure proper initialization order
		// 1. Verify player exists (should already be true, but defensive check)
		if (server.player == null) {
			System.err.println("ERROR: Player is null after server.startGame()!");
			client.addLoadingMessage("Error: Player not created");
			client.render();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				// Ignore
			}
			throw new RuntimeException("Failed to create player entity");
		}

		// 2. Process initial packets from server (world init, entity updates)
		// Give client time to receive and process initial world state
		client.addLoadingMessage("Receiving world data...");
		client.render();
		for (int i = 0; i < 10; i++) {
			client.processPackets();  // Process any pending packets
			try {
				Thread.sleep(50);  // Total 500ms to receive initial packets
			} catch (InterruptedException e) {
				break;  // Interrupted, exit loop
			}
		}

		client.setLoadingProgress(100);
		client.addLoadingMessage("Done!");
		client.render();  // Update display

		// 3. ONLY NOW transition to IN_GAME (all initialization complete)
		stateManager.transitionTo(GameState.IN_GAME);

		// Start dedicated server tick thread (Phase 4 - Thread Separation)
		// CRITICAL: Start AFTER state is IN_GAME so ticking begins immediately
		if (server != null && server.getSharedWorld() != null) {
			serverTickThread = new ServerTickThread(server.getSharedWorld(), stateManager);
			serverTickThread.start();
			System.out.println("ServerTickThread started (target 60 TPS)");
		}

		// Hide loading screen after a short delay (so user can see 100%)
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// Ignore
		}
		client.hideLoadingScreen();
	}

	/**
	 * Legacy method - creates world with default name and credentials
	 * @deprecated Use startGame(String, boolean, int, String, String) instead
	 */
	@Deprecated
	public void startGame(boolean load, int width) {
		startGame("World1", load, width, "DefaultPlayer", "password");
	}

	/**
	 * Main game loop - runs server tick and client render
	 */
	public void gameLoop() {
		long lastLoopTime = System.currentTimeMillis();

		if (Constants.DEBUG) {
			// Auto-login in debug mode
			setLoggedInUser("DebugPlayer", "debug");
			showMainMenu();
			startGame("DebugWorld", false, 512, "DebugPlayer", "debug");
		}

		// Main loop - state-driven
		while (stateManager.getState() != GameState.SHUTDOWN) {
			long delta = SystemTimer.getTime() - lastLoopTime;
			lastLoopTime = SystemTimer.getTime();

			GameState currentState = stateManager.getState();

			// State-based rendering/logic
			switch (currentState) {
				case BOOT:
					// Initialization complete, transition to login or menu
					if (Constants.DEBUG || isLoggedIn) {
						stateManager.transitionTo(GameState.MENU);
					} else {
						// Check for saved credentials and auto-login
						CredentialManager.SavedCredentials saved = CredentialManager.loadCredentials();
						if (saved != null) {
							System.out.println("Auto-login with saved credentials: " + saved.username);
							setLoggedInUser(saved.username, saved.password);
							stateManager.transitionTo(GameState.MENU);
						} else {
							stateManager.transitionTo(GameState.LOGIN);
						}
					}
					break;

				case LOGIN:
					// Render login screen
					if (client != null && loginScreen != null) {
						client.render();  // Client handles login screen rendering
					}
					break;

				case MENU:
					// Render main menu
					if (client != null) {
						client.render();  // Client handles menu rendering
					}
					break;

				case LOADING:
					// Render loading screen, process packets
					if (client != null) {
						client.render();  // Client shows loading progress
					}
					break;

				case IN_GAME:
				case PAUSED:
					// Server tick now runs in ServerTickThread (Phase 4)
					// Main thread ONLY renders for maximum performance

					// Client render (graphics)
					if (client != null) {
						client.render();
					}
					break;

				case SHUTDOWN:
					// Exit loop
					break;
			}

			// Check if client or server stopped unexpectedly
			if (client != null && !client.isRunning()) {
				stateManager.transitionTo(GameState.SHUTDOWN);
			}
			if (server != null && !server.isRunning() && stateManager.getState().isServerActive()) {
				stateManager.transitionTo(GameState.SHUTDOWN);
			}

			// Sleep to maintain ~60 FPS (clamp to 0 if frame took longer than 16ms)
			long sleepTime = Math.max(0, lastLoopTime + 16 - SystemTimer.getTime());
			SystemTimer.sleep(sleepTime);
		}
	}

	/**
	 * Send current input state to server (for continuous movement in multiplayer/LAN)
	 */
	public void sendCurrentInputState() {
		// Access the event handler through the graphics handler
		mc.sayda.mcraze.GraphicsHandler graphicsHandler = mc.sayda.mcraze.GraphicsHandler.get();
		if (graphicsHandler instanceof mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler) {
			mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler awtHandler =
				(mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler) graphicsHandler;
			mc.sayda.mcraze.awtgraphics.AwtEventsHandler eventsHandler = awtHandler.getEventsHandler();
			if (eventsHandler != null) {
				eventsHandler.sendInputPacket();
			}
		}
	}

	/**
	 * Submit chat message/command to server
	 */
	public void submitChat() {
		if (client == null || client.chat == null) return;

		String input = client.chat.submit();
		if (input != null && !input.trim().isEmpty()) {
			// Send chat message to server via packet
			mc.sayda.mcraze.network.packet.PacketChatSend packet =
				new mc.sayda.mcraze.network.packet.PacketChatSend(input);
			client.connection.sendPacket(packet);
		}
		client.chat.setOpen(false);
	}

	/**
	 * Save the current game state (only works in singleplayer)
	 */
	public void saveGame() {
		// Only save in singleplayer mode (when we have an integrated server)
		if (server != null && server.world != null) {
			if (currentWorldName != null) {
				mc.sayda.mcraze.world.WorldSaveManager.saveWorld(currentWorldName, server);
				server.savePlayerData();  // Save playerdata
				System.out.println("Game saved");
			} else {
				System.err.println("Cannot save: no world name set");
			}
		} else {
			System.out.println("Cannot save in multiplayer mode");
		}
	}

	/**
	 * Return to main menu (saves game if in singleplayer)
	 */
	public void goToMainMenu() {
		// 1. Stop server tick thread FIRST (Phase 4 cleanup)
		if (serverTickThread != null && serverTickThread.isRunning()) {
			System.out.println("Stopping ServerTickThread...");
			serverTickThread.shutdown();
			try {
				serverTickThread.join(2000);  // Wait up to 2 seconds for clean shutdown
				if (serverTickThread.isAlive()) {
					System.err.println("Warning: ServerTickThread did not stop in time");
				} else {
					System.out.println("ServerTickThread stopped successfully");
				}
			} catch (InterruptedException e) {
				System.err.println("Interrupted while waiting for ServerTickThread shutdown");
			}
			serverTickThread = null;
		}

		// 2. Save game if in singleplayer
		if (server != null) {
			saveGame();
		}

		// 3. Disconnect from multiplayer if needed
		if (client != null && server == null) {
			client.disconnectMultiplayer();
		}

		// 4. Transition to MENU state
		stateManager.transitionTo(GameState.MENU);
	}

	/**
	 * Delegate methods for backward compatibility
	 */
	public void quit() {
		// Transition to SHUTDOWN state
		stateManager.transitionTo(GameState.SHUTDOWN);

		if (server != null) {
			server.stop();
		}
		if (client != null) {
			client.stop();
			try {
				client.musicPlayer.close();
			} catch (Exception e) {
				System.err.println("Error closing music player: " + e.getMessage());
			}
		}
		System.exit(0);
	}

	// Expose server/client/state for event handlers
	public Server getServer() {
		return server;
	}

	public Client getClient() {
		return client;
	}

	public GameStateManager getStateManager() {
		return stateManager;
	}

	public String getCurrentWorldName() {
		return currentWorldName;
	}

	public void setCurrentWorldName(String worldName) {
		this.currentWorldName = worldName;
	}

	// ===== Authentication Session Management =====

	/**
	 * Set logged in user credentials
	 */
	public void setLoggedInUser(String username, String password) {
		this.loggedInUsername = username;
		this.loggedInPassword = password;
		this.isLoggedIn = true;
	}

	/**
	 * Exit login screen and show main menu
	 */
	public void showMainMenu() {
		stateManager.transitionTo(GameState.MENU);
	}

	/**
	 * Logout - clear credentials and return to login screen
	 */
	public void logout() {
		// Delete saved credentials
		CredentialManager.deleteCredentials();

		// Clear login state
		this.loggedInUsername = null;
		this.loggedInPassword = null;
		this.isLoggedIn = false;

		// Return to login screen
		stateManager.transitionTo(GameState.LOGIN);

		System.out.println("Logged out successfully");
	}

	/**
	 * Check if login screen is showing
	 */
	public boolean isShowingLoginScreen() {
		return stateManager.getState() == GameState.LOGIN;
	}

	/**
	 * Get the login screen
	 */
	public mc.sayda.mcraze.ui.LoginScreen getLoginScreen() {
		return loginScreen;
	}

	/**
	 * Get logged in username
	 */
	public String getLoggedInUsername() {
		return loggedInUsername;
	}

	/**
	 * Get logged in password
	 */
	public String getLoggedInPassword() {
		return loggedInPassword;
	}

	/**
	 * Main entry point
	 */
	public static void main(String argv[]) {
		boolean dedicatedServer = false;
		int serverPort = 25565;
		int worldWidth = 512;
		String autoUsername = null;
		String autoPassword = null;

		// Parse arguments
		for (int i = 0; i < argv.length; i++) {
			String arg = argv[i];
			if (arg.equals("-d") || arg.equals("--debug")) {
				Constants.DEBUG = true;
			} else if (arg.equals("-s") || arg.equals("--server")) {
				dedicatedServer = true;
			} else if (arg.equals("-p") || arg.equals("--port")) {
				if (i + 1 < argv.length) {
					serverPort = Integer.parseInt(argv[++i]);
				}
			} else if (arg.equals("-w") || arg.equals("--world-width")) {
				if (i + 1 < argv.length) {
					worldWidth = Integer.parseInt(argv[++i]);
				}
			} else if (arg.equals("--username")) {
				if (i + 1 < argv.length) {
					autoUsername = argv[++i];
				}
			} else if (arg.equals("--password")) {
				if (i + 1 < argv.length) {
					autoPassword = argv[++i];
				}
			} else if (arg.equals("-h") || arg.equals("--help")) {
				System.out.println("MCraze - A 2D Minecraft-like game");
				System.out.println("Usage: java mc.sayda.Game [options]");
				System.out.println("Options:");
				System.out.println("  -d, --debug              Enable debug mode");
				System.out.println("  -s, --server             Start dedicated server (no client)");
				System.out.println("  -p, --port <port>        Server port (default: 25565)");
				System.out.println("  -w, --world-width <size> World width (default: 512)");
				System.out.println("  --username <name>        Auto-login with username");
				System.out.println("  --password <pass>        Auto-login with password");
				System.out.println("  -h, --help               Show this help message");
				return;
			} else {
				System.err.println("Unrecognized argument: " + arg);
				System.err.println("Use --help for usage information");
				return;
			}
		}

		try {
			// Initialize logging based on mode (server logs to current dir, client logs to AppData)
			if (dedicatedServer) {
				// Server mode - log to current directory
				GameLogger.initServer(Constants.DEBUG);
			} else {
				// Client mode - log to AppData
				GameLogger.initClient(Constants.DEBUG);
			}

			GameLogger logger = GameLogger.get();
			logger.info("=== MCraze Starting ===");
			logger.info("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
			logger.info("Java: " + System.getProperty("java.version"));
			if (Constants.DEBUG) {
				logger.info("DEBUG MODE ENABLED - Extra logging active");
			}

			// Start dedicated server or client
			if (dedicatedServer) {
				logger.warn("Dedicated server mode is deprecated. Use DedicatedServer.main() instead.");
				try {
					mc.sayda.mcraze.server.DedicatedServer server = new mc.sayda.mcraze.server.DedicatedServer(serverPort);
					server.start(worldWidth);
				} catch (java.io.IOException e) {
					logger.error("Failed to start server", e);
					throw e;
				}
			} else {
				// Create and start game client
				logger.info("Starting client with integrated server");
				Game g = new Game();

				// Auto-login if credentials provided
				if (autoUsername != null && autoPassword != null) {
					logger.info("Auto-login with username: " + autoUsername);
					g.setLoggedInUser(autoUsername, autoPassword);
					g.showMainMenu();
				}

				g.gameLoop();
			}

			// Clean shutdown
			logger.info("=== MCraze Shutting Down ===");
			logger.close();

		} catch (Throwable t) {
			// Generate crash report
			CrashReport crash = new CrashReport("Unexpected exception in game", t);
			crash.addDetail("Dedicated Server", dedicatedServer);
			crash.addDetail("Server Port", serverPort);
			crash.addDetail("World Width", worldWidth);

			crash.printToConsole();
			String crashFile = crash.saveClient();

			if (crashFile != null) {
				System.err.println("Crash report saved to: " + crashFile);
			}

			// Close logger if initialized
			if (GameLogger.get() != null) {
				GameLogger.get().error("Fatal crash occurred", t);
				GameLogger.get().close();
			}

			System.exit(1);
		}
	}
}
