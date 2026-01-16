package mc.sayda.mcraze.server;

import mc.sayda.mcraze.logging.CrashReport;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.NetworkConnection;
import mc.sayda.mcraze.state.GameState;
import mc.sayda.mcraze.state.GameStateManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Dedicated server that listens for network connections
 * and runs the game server logic with SharedWorld for multiple players.
 */
public class DedicatedServer {
	private final GameLogger logger = GameLogger.get(); // Dedicated Server initialized in main(), safe.
	private SharedWorld sharedWorld;
	private ServerSocket serverSocket;
	private int port;
	private boolean running = true;
	private Thread acceptThread;
	private List<PlayerConnection> connectedPlayers;
	private int nextPlayerId = 1;

	// Phase 4: Dedicated server tick thread
	private ServerTickThread serverTickThread;
	private final GameStateManager stateManager = new GameStateManager();

	// Minimal Server object for CommandHandler
	private mc.sayda.mcraze.server.Server serverForCommands;

	public DedicatedServer(int port) {
		this.port = port;
		this.connectedPlayers = new ArrayList<>();
	}

	/**
	 * Start the dedicated server
	 */
	public void start(int worldWidth) throws IOException {
		logger.info("=== Starting Dedicated MCraze Server ===");
		logger.info("Binding to port " + port + "...");

		serverSocket = new ServerSocket(port);
		logger.info("Server listening on port " + port);

		// Try loading existing world from "./world/" directory
		java.nio.file.Path worldDir = java.nio.file.Paths.get("world");
		mc.sayda.mcraze.world.World loadedWorld = null;

		if (java.nio.file.Files.exists(worldDir)) {
			logger.info("Found existing world directory, attempting to load...");
			// Create temporary Server instance for loading
			mc.sayda.mcraze.server.Server tempServer = new mc.sayda.mcraze.server.Server(null);
			boolean loaded = mc.sayda.mcraze.world.WorldSaveManager.loadWorldFromDirectory(worldDir, tempServer);

			if (loaded && tempServer.world != null) {
				loadedWorld = tempServer.world;
				logger.info("Successfully loaded existing world from ./world/");

				// Load chest data from ./world/chests.dat
				java.util.Map<String, mc.sayda.mcraze.world.ChestData> loadedChests = mc.sayda.mcraze.world.WorldSaveManager
						.loadChestsFromDirectory(worldDir);
				if (loadedChests != null && !loadedChests.isEmpty()) {
					loadedWorld.setChests(loadedChests);
					logger.info("Loaded " + loadedChests.size() + " chests from ./world/chests.dat");
				}
			} else {
				logger.error("Failed to load world, will create new one");
			}
		} else {
			logger.info("No existing world found, will create new one");
		}

		// Create shared world (either with loaded world or generate new)
		String worldName = "ServerWorld"; // Dedicated server uses fixed world name
		if (loadedWorld != null) {
			sharedWorld = new SharedWorld(loadedWorld, new Random(), worldName, worldDir);
		} else {
			sharedWorld = new SharedWorld(worldWidth, new Random(), worldName, worldDir);
			// Save the newly generated world
			saveWorld();
		}

		logger.info("Shared world initialized (playerdata will be saved to ./world/playerdata/)");

		// Set up CommandHandler for dedicated server
		// Create minimal Server object with world reference for commands
		serverForCommands = new mc.sayda.mcraze.server.Server(null);
		serverForCommands.world = sharedWorld.getWorld();

		mc.sayda.mcraze.ui.CommandHandler commandHandler = new mc.sayda.mcraze.ui.CommandHandler(serverForCommands,
				sharedWorld, null);
		sharedWorld.setCommandHandler(commandHandler);
		logger.info("Command handler initialized");

		logger.info("Server ready - accepting connections");

		// Dedicated server is always IN_GAME (no menu state)
		// Use forceState because dedicated server has no GUI lifecycle
		stateManager.forceState(GameState.IN_GAME);

		// Start dedicated server tick thread (Phase 4)
		serverTickThread = new ServerTickThread(sharedWorld, stateManager);
		serverTickThread.start();
		logger.info("ServerTickThread started (target 60 TPS)");

		// Start accept thread for client connections
		startAcceptThread();

		// Run maintenance loop (cleanup disconnected players)
		runMaintenanceLoop();
	}

	/**
	 * Thread to accept client connections
	 */
	private void startAcceptThread() {
		acceptThread = new Thread(() -> {
			while (running) {
				try {
					Socket clientSocket = serverSocket.accept();
					logger.info("Client connected from: " + clientSocket.getRemoteSocketAddress());

					// Create network connection
					NetworkConnection connection = new NetworkConnection(clientSocket);

					// Wait for authentication packet (timeout after 5 seconds)
					// System.out.println("Waiting for authentication from client...");
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
							Thread.sleep(50); // Wait a bit before checking again
						}
					}

					if (authPacket == null) {
						logger.warn("Client did not send authentication packet - disconnecting");
						connection.disconnect();
						continue;
					}

					logger.info("Authentication request from: " + authPacket.username);

					try {
						// Try to add player with authentication
						PlayerConnection playerConnection = sharedWorld.addPlayer(
								connection, authPacket.username, authPacket.password);

						if (playerConnection == null) {
							// Authentication failed (duplicate or wrong password)
							logger.warn("Authentication failed for " + authPacket.username);
							mc.sayda.mcraze.network.packet.PacketAuthResponse response = new mc.sayda.mcraze.network.packet.PacketAuthResponse(
									false, "Authentication failed");
							connection.sendPacket(response);
							connection.flush(); // Flush immediately so client receives auth failure
							connection.disconnect();
						} else {
							// Authentication successful
							logger.info("Player " + authPacket.username + " authenticated successfully");
							mc.sayda.mcraze.network.packet.PacketAuthResponse response = new mc.sayda.mcraze.network.packet.PacketAuthResponse(
									true, "");
							connection.sendPacket(response);
							connection.flush(); // Flush immediately so client receives auth success

							connectedPlayers.add(playerConnection);
							logger.info("Player " + authPacket.username + " joined ("
									+ connectedPlayers.size() + " players online)");
						}
					} catch (Exception e) {
						logger.error("Error adding player " + authPacket.username + ": " + e.getMessage());
						e.printStackTrace();
						try {
							mc.sayda.mcraze.network.packet.PacketAuthResponse response = new mc.sayda.mcraze.network.packet.PacketAuthResponse(
									false, "Server error: " + e.getMessage());
							connection.sendPacket(response);
							connection.flush(); // Flush immediately so client receives error
							connection.disconnect();
						} catch (Exception disconnectError) {
							logger.error("Failed to send error response: " + disconnectError.getMessage());
						}
					}

				} catch (IOException e) {
					if (running) {
						logger.error("Error accepting client: " + e.getMessage());
					}
				} catch (InterruptedException e) {
					logger.warn("Accept thread interrupted");
				}
			}
		}, "ClientAccept");
		acceptThread.setDaemon(true);
		acceptThread.start();
	}

	/**
	 * Maintenance loop - handles disconnected player cleanup
	 * ServerTickThread handles actual game logic at 60 TPS
	 */
	private void runMaintenanceLoop() {
		while (running) {
			// Check for disconnected players (every second)
			Iterator<PlayerConnection> it = connectedPlayers.iterator();
			while (it.hasNext()) {
				PlayerConnection playerConnection = it.next();
				if (!playerConnection.isConnected()) {
					logger.info("Player " + playerConnection.getPlayerName() + " disconnected");
					sharedWorld.removePlayer(playerConnection);
					it.remove();
				}
			}

			// CRITICAL FIX: Check every 100ms for faster player cleanup (was 1 second)
			// This prevents ghost players from being visible after disconnect
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				logger.warn("Maintenance loop interrupted");
				break;
			}
		}
		logger.info("Server shutting down...");

		// Stop server tick thread
		if (serverTickThread != null && serverTickThread.isRunning()) {
			logger.info("Stopping ServerTickThread...");
			serverTickThread.shutdown();
			try {
				serverTickThread.join(2000);
				logger.info("ServerTickThread stopped");
			} catch (InterruptedException e) {
				logger.warn("Interrupted waiting for ServerTickThread");
			}
		}

		saveWorld();
		cleanup();
	}

	/**
	 * Stop the server
	 */
	public void stop() {
		running = false;
		if (sharedWorld != null) {
			sharedWorld.stop();
		}
		// Save world before shutting down
		saveWorld();
		cleanup();
	}

	/**
	 * Save the current world to ./world/ directory
	 */
	private void saveWorld() {
		if (sharedWorld == null || sharedWorld.getWorld() == null) {
			logger.error("Cannot save: world is null");
			return;
		}

		System.out.println("Saving world...");
		java.nio.file.Path worldDir = java.nio.file.Paths.get("world");

		// Create temporary Server instance with the world for saving
		mc.sayda.mcraze.server.Server tempServer = new mc.sayda.mcraze.server.Server(null);
		tempServer.world = sharedWorld.getWorld();
		tempServer.entities = new java.util.ArrayList<>(); // Empty for dedicated server

		boolean saved = mc.sayda.mcraze.world.WorldSaveManager.saveWorldToDirectory(worldDir, tempServer);
		if (saved) {
			logger.info("World saved successfully");
		} else {
			logger.error("Failed to save world");
		}

		// Save chest data to ./world/chests.dat
		java.util.Map<String, mc.sayda.mcraze.world.ChestData> chests = tempServer.world != null
				? tempServer.world.getAllChests()
				: new java.util.concurrent.ConcurrentHashMap<>();
		boolean chestsSaved = mc.sayda.mcraze.world.WorldSaveManager.saveChestsToDirectory(worldDir, chests);
		if (chestsSaved) {
			logger.info("Chests saved successfully (" + chests.size() + " chests)");
		} else {
			logger.error("Failed to save chests");
		}
	}

	/**
	 * Cleanup resources
	 */
	private void cleanup() {
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
			}
		} catch (IOException e) {
			logger.error("Error closing server socket: " + e.getMessage());
		}
	}

	/**
	 * Main entry point for dedicated server
	 */
	public static void main(String[] args) {
		int port = 25565; // Default Minecraft port
		int worldWidth = 512; // Default world size
		boolean debugMode = false;

		// Parse arguments
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-d":
				case "--debug":
					debugMode = true;
					break;
				case "-p":
				case "--port":
					if (i + 1 < args.length) {
						port = Integer.parseInt(args[++i]);
					}
					break;
				case "-w":
				case "--world-width":
					if (i + 1 < args.length) {
						worldWidth = Integer.parseInt(args[++i]);
					}
					break;
				case "-h":
				case "--help":
					System.out.println("MCraze Dedicated Server");
					System.out.println("Usage: java mc.sayda.server.DedicatedServer [options]");
					System.out.println("Options:");
					System.out.println("  -d, --debug               Enable debug logging");
					System.out.println("  -p, --port <port>         Server port (default: 25565)");
					System.out.println("  -w, --world-width <size>  World width (default: 512)");
					System.out.println("  -h, --help                Show this help message");
					return;
				default:
					System.err.println("Unknown argument: " + args[i]);
					return;
			}
		}

		try {
			// Initialize server logging
			GameLogger.initServer(debugMode);
			GameLogger logger = GameLogger.get();
			logger.info("=== MCraze Dedicated Server Starting ===");
			logger.info("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
			logger.info("Java: " + System.getProperty("java.version"));
			logger.info("Port: " + port);
			logger.info("World Width: " + worldWidth);
			if (debugMode) {
				logger.info("DEBUG MODE ENABLED - Extra logging active");
			}

			DedicatedServer server = new DedicatedServer(port);
			server.start(worldWidth);

			// Clean shutdown
			logger.info("=== Server Shutting Down ===");
			logger.close();

		} catch (Throwable t) {
			// Generate crash report
			CrashReport crash = new CrashReport("Unexpected exception in dedicated server", t);
			crash.addDetail("Server Port", port);
			crash.addDetail("World Width", worldWidth);

			crash.printToConsole();
			String crashFile = crash.saveServer();

			if (crashFile != null) {
				System.err.println("Crash report saved to: " + crashFile);
			}

			// Close logger if initialized
			if (GameLogger.get() != null) {
				GameLogger.get().error("Fatal server crash occurred", t);
				GameLogger.get().close();
			}

			System.exit(1);
		}
	}
}
