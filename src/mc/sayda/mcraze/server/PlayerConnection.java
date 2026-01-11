package mc.sayda.mcraze.server;

import mc.sayda.mcraze.entity.Player;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.ServerPacketHandler;
import mc.sayda.mcraze.network.packet.*;

/**
 * Represents a single player connection to the SharedWorld.
 * Handles packet processing for one player.
 */
public class PlayerConnection implements ServerPacketHandler {
	private final Connection connection;
	private final Player player;
	private final String playerName;
	private final String password;
	private final SharedWorld sharedWorld;
	private boolean initialWorldLoaded = false;  // Track if player has finished loading initial world data

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
			// All packets received by server are ClientPackets
			if (packet instanceof ClientPacket) {
				((ClientPacket) packet).handle(this);
			} else {
				if (logger != null) logger.warn("Server received non-ClientPacket: " + packet.getClass().getSimpleName());
			}
		}
	}

	// ===== Packet Handlers =====

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

		// Apply sneaking state
		player.sneaking = packet.sneak;

		// CRITICAL FIX: Update hand target position from world mouse coordinates
		// This fixes the bug where remote players could only interact at (0,0)
		// Convert world mouse coordinates to block coordinates
		int targetBlockX = (int) Math.floor(packet.worldMouseX);
		int targetBlockY = (int) Math.floor(packet.worldMouseY);

		// Calculate distance from player center to target block center
		float playerCenterX = player.getCenterX(mc.sayda.mcraze.Constants.TILE_SIZE);
		float playerCenterY = player.getCenterY(mc.sayda.mcraze.Constants.TILE_SIZE);
		float targetCenterX = targetBlockX + 0.5f;
		float targetCenterY = targetBlockY + 0.5f;
		float dx = targetCenterX - playerCenterX;
		float dy = targetCenterY - playerCenterY;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);

		// Check if within arm's reach
		if (distance <= mc.sayda.mcraze.Constants.ARM_LENGTH) {
			player.handTargetPos.x = targetBlockX;
			player.handTargetPos.y = targetBlockY;
		} else {
			// Out of reach - set to invalid position
			player.handTargetPos.x = -1;
			player.handTargetPos.y = -1;
		}

		// Update hotbar selection
		if (packet.hotbarSlot >= 0 && packet.hotbarSlot < 10) {
			player.setHotbarItem(packet.hotbarSlot);
			// Broadcast hotbar change immediately to all clients
			sharedWorld.broadcastInventoryUpdates();
		}
	}

	@Override
	public void handleBlockChange(PacketBlockChange packet) {
		// Delegate to SharedWorld for processing and broadcasting
		sharedWorld.handleBlockChange(this, packet);
	}

	@Override
	public void handleInventoryAction(PacketInventoryAction packet) {
		// Delegate to SharedWorld for processing and broadcasting
		sharedWorld.handleInventoryAction(this, packet);
	}

	@Override
	public void handleInteract(PacketInteract packet) {
		// Handle block interactions (crafting table, chests, etc.)
		// Delegate to SharedWorld for processing
		sharedWorld.handleInteract(this, packet);
	}

	@Override
	public void handleItemToss(mc.sayda.mcraze.network.packet.PacketItemToss packet) {
		// Handle item tossing - delegate to SharedWorld for processing
		sharedWorld.handleItemToss(this, packet);
	}

	@Override
	public void handleRespawn(mc.sayda.mcraze.network.packet.PacketRespawn packet) {
		GameLogger logger = GameLogger.get();
		if (player == null || !player.dead) {
			if (logger != null) {
				logger.warn("PlayerConnection.handleRespawn: Player " + playerName +
					" tried to respawn but is not dead (player null: " + (player == null) + ")");
			}
			return;  // Can't respawn if not dead
		}

		if (logger != null) {
			logger.info("PlayerConnection.handleRespawn: Respawning player " + playerName);
		}

		// Respawn the player on the server and broadcast to all clients
		sharedWorld.respawnPlayer(this);
	}

	@Override
	public void handleToggleBackdropMode(mc.sayda.mcraze.network.packet.PacketToggleBackdropMode packet) {
		if (player != null) {
			// Toggle backdrop placement mode
			player.backdropPlacementMode = !player.backdropPlacementMode;

			// Send chat feedback to client (yellow color)
			String message = "Backdrop Mode: " + (player.backdropPlacementMode ? "ON" : "OFF");
			mc.sayda.mcraze.network.packet.PacketChatMessage chatPacket =
				new mc.sayda.mcraze.network.packet.PacketChatMessage(message, new mc.sayda.mcraze.Color(255, 255, 0));
			connection.sendPacket(chatPacket);

			GameLogger logger = GameLogger.get();
			if (logger != null) {
				logger.info("PlayerConnection.handleToggleBackdropMode: Player " + playerName +
					" toggled backdrop mode to " + player.backdropPlacementMode);
			}
		}
	}

	@Override
	public void handleChestAction(mc.sayda.mcraze.network.packet.PacketChestAction packet) {
		// TODO: Handle chest inventory interactions
		// Delegate to SharedWorld for processing
		GameLogger logger = GameLogger.get();
		if (logger != null) {
			logger.info("PlayerConnection.handleChestAction: Player " + playerName +
				" interacted with chest at (" + packet.chestX + ", " + packet.chestY + ")");
		}
		sharedWorld.handleChestAction(this, packet);
	}

	@Override
	public void handleEntityAttack(mc.sayda.mcraze.network.packet.PacketEntityAttack packet) {
		// Handle entity attack - delegate to SharedWorld for processing
		sharedWorld.handleEntityAttack(this, packet);
	}

	@Override
	public void handlePing(mc.sayda.mcraze.network.packet.PacketPing packet) {
		// Respond to ping request with pong containing original timestamp
		mc.sayda.mcraze.network.packet.PacketPong pong = new mc.sayda.mcraze.network.packet.PacketPong(packet.timestamp);
		connection.sendPacket(pong);
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
				// PERFORMANCE: Commented out console logging (use logger instead)
				// System.out.println("[" + playerName + "] Command: " + packet.message);
				if (logger != null) {
					logger.info("Command [" + playerName + "]: " + packet.message);
				}
				// Pass the player who sent the command
				cmdHandler.executeCommand(packet.message, this.player);
			} else {
				// No command handler available (dedicated server)
				// PERFORMANCE: Commented out console logging
				// System.out.println("[" + playerName + "] Command not supported: " + packet.message);
			}
			return;  // Don't broadcast commands
		}

		// Regular chat message
		// PERFORMANCE: Commented out console logging (use logger instead)
		// System.out.println("[" + playerName + "] " + packet.message);
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

	@Override
	public void handleAuthRequest(mc.sayda.mcraze.network.packet.PacketAuthRequest packet) {
		// Authentication handled at connection level (in SharedWorld/DedicatedServer)
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

	public void setInitialWorldLoaded(boolean loaded) {
		this.initialWorldLoaded = loaded;
	}

	public boolean isInitialWorldLoaded() {
		return initialWorldLoaded;
	}
}
