package mc.sayda.mcraze.server;

import mc.sayda.mcraze.entity.Player;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.PacketHandler;
import mc.sayda.mcraze.network.packet.*;

/**
 * Represents a single player connection to the SharedWorld.
 * Handles packet processing for one player.
 */
public class PlayerConnection implements PacketHandler {
	private final Connection connection;
	private final Player player;
	private final String playerName;
	private final String password;
	private final SharedWorld sharedWorld;

	public PlayerConnection(Connection connection, Player player, String playerName, String password, SharedWorld sharedWorld) {
		GameLogger logger = GameLogger.get();
		this.connection = connection;
		this.player = player;
		this.playerName = playerName;
		this.password = password;
		this.sharedWorld = sharedWorld;

		if (logger != null) {
			logger.debug("PlayerConnection.<init>: Created for player " + playerName + " - connection type: " + connection.getClass().getSimpleName());
		}
	}

	/**
	 * Process all incoming packets from this player
	 */
	private int packetProcessCount = 0;

	public void processPackets() {
		GameLogger logger = GameLogger.get();
		if (!connection.isConnected()) {
			if (logger != null && packetProcessCount == 0) {
				logger.warn("PlayerConnection.processPackets: Connection not connected for " + playerName);
			}
			return;
		}

		Packet[] packets = connection.receivePackets();
		if (packets.length > 0) {
			packetProcessCount++;
			if (logger != null && packetProcessCount <= 5) {
				logger.debug("PlayerConnection.processPackets: " + playerName + " - processing " + packets.length + " packets");
			}
		}

		for (Packet packet : packets) {
			if (logger != null && packetProcessCount <= 5) {
				logger.debug("PlayerConnection.processPackets: " + playerName + " - handling " + packet.getClass().getSimpleName());
			}
			packet.handle(this);
		}
	}

	// ===== Packet Handlers =====

	@Override
	public void handleWorldInit(PacketWorldInit packet) {
		// Players don't send world init (only receive it)
	}

	private int inputCount = 0;

	@Override
	public void handlePlayerInput(PacketPlayerInput packet) {
		GameLogger logger = GameLogger.get();
		if (player == null) {
			if (logger != null) logger.warn("PlayerConnection.handlePlayerInput: Player is null for " + playerName);
			return;
		}

		inputCount++;
		if (logger != null && inputCount <= 3) {
			logger.debug("PlayerConnection.handlePlayerInput: " + playerName + " - left=" + packet.moveLeft +
				", right=" + packet.moveRight + ", climb=" + packet.climb + ", hotbar=" + packet.hotbarSlot);
		}

		// Apply player input
		if (packet.moveLeft) {
			player.startLeft(false);
		} else {
			player.stopLeft();
		}

		if (packet.moveRight) {
			player.startRight(false);
		} else {
			player.stopRight();
		}

		if (packet.climb) {
			player.startClimb();
		} else {
			player.endClimb();
		}

		// Update hotbar selection
		if (packet.hotbarSlot >= 0 && packet.hotbarSlot < 10) {
			player.setHotbarItem(packet.hotbarSlot);
		}
	}

	@Override
	public void handleBlockChange(PacketBlockChange packet) {
		// Delegate to SharedWorld for processing and broadcasting
		sharedWorld.handleBlockChange(this, packet);
	}

	@Override
	public void handleChatSend(PacketChatSend packet) {
		GameLogger logger = GameLogger.get();
		if (packet.message == null || packet.message.trim().isEmpty()) {
			return;
		}

		// Check if it's a command
		if (packet.message.startsWith("/")) {
			// Handle command via CommandHandler (if available)
			mc.sayda.mcraze.ui.CommandHandler cmdHandler = sharedWorld.getCommandHandler();
			if (cmdHandler != null) {
				System.out.println("[" + playerName + "] Command: " + packet.message);
				if (logger != null) {
					logger.info("Command [" + playerName + "]: " + packet.message);
				}
				cmdHandler.executeCommand(packet.message);
			} else {
				// No command handler available (dedicated server)
				System.out.println("[" + playerName + "] Command not supported: " + packet.message);
			}
			return;  // Don't broadcast commands
		}

		// Regular chat message
		System.out.println("[" + playerName + "] " + packet.message);
		if (logger != null) {
			logger.info("Chat [" + playerName + "]: " + packet.message);
		}

		// Broadcast chat message to all players
		PacketChatMessage chatPacket = new PacketChatMessage(
			"<" + playerName + "> " + packet.message,
			mc.sayda.mcraze.Color.white
		);

		// Broadcast to all players via SharedWorld
		if (logger != null) logger.debug("PlayerConnection.handleChatSend: Broadcasting message from " + playerName);
		sharedWorld.broadcastPacket(chatPacket);
	}

	// Client-bound packet handlers (players don't send these)
	@Override
	public void handleWorldUpdate(PacketWorldUpdate packet) {
		// Players don't send world updates
	}

	@Override
	public void handleEntityUpdate(PacketEntityUpdate packet) {
		// Players don't send entity updates
	}

	@Override
	public void handleEntityRemove(PacketEntityRemove packet) {
		// Players don't send entity remove packets (server controls this)
	}

	@Override
	public void handleInventoryUpdate(PacketInventoryUpdate packet) {
		// Players don't send inventory updates
	}

	@Override
	public void handleChatMessage(PacketChatMessage packet) {
		// Players don't send chat messages (they send ChatSend)
	}

	@Override
	public void handlePlayerDeath(PacketPlayerDeath packet) {
		// Players don't send death packets (server detects death)
	}

	@Override
	public void handleBreakingProgress(PacketBreakingProgress packet) {
		// Players don't send breaking progress (server tracks it)
	}

	@Override
	public void handleAuthRequest(mc.sayda.mcraze.network.packet.PacketAuthRequest packet) {
		// Authentication handled at connection level (in SharedWorld/DedicatedServer)
	}

	@Override
	public void handleAuthResponse(mc.sayda.mcraze.network.packet.PacketAuthResponse packet) {
		// Players don't receive auth responses
	}

	// Getters
	public Connection getConnection() {
		return connection;
	}

	public Player getPlayer() {
		return player;
	}

	public String getPassword() {
		return password;
	}

	public String getPlayerName() {
		return playerName;
	}

	public boolean isConnected() {
		return connection.isConnected();
	}
}
