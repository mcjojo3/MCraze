package mc.sayda.mcraze.server;

import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.player.data.*;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.gen.*;
import mc.sayda.mcraze.world.storage.*;
import mc.sayda.mcraze.entity.mob.*;
import mc.sayda.mcraze.ui.menu.*;
import mc.sayda.mcraze.ui.screen.*;
import mc.sayda.mcraze.ui.component.*;
import mc.sayda.mcraze.ui.container.*;
import mc.sayda.mcraze.audio.*;
import mc.sayda.mcraze.graphics.*;

import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.Packet;
import mc.sayda.mcraze.network.ServerPacketHandler;
import mc.sayda.mcraze.network.packet.*;
import mc.sayda.mcraze.player.specialization.network.PacketClassSelect;
import mc.sayda.mcraze.player.specialization.network.PacketSkillUpgrade;
import mc.sayda.mcraze.player.specialization.PlayerClass;

/**
 * Represents a single player connection to the SharedWorld.
 * Handles packet processing for one player.
 */
public class PlayerConnection implements ServerPacketHandler {
	private final GameLogger logger = GameLogger.get();

	private final Connection connection;
	private final Player player;
	private final String playerName;
	private final String password;
	private final SharedWorld sharedWorld;
	private boolean initialWorldLoaded = false; // Track if player has finished loading initial world data

	public PlayerConnection(Connection connection, Player player, String playerName, String password,
			SharedWorld sharedWorld) {
		this.connection = connection;
		this.player = player;
		this.playerName = playerName;
		this.password = password;
		this.sharedWorld = sharedWorld;

		if (logger != null) {
			logger.debug("PlayerConnection.<init>: Created for player " + playerName + " - connection type: "
					+ connection.getClass().getSimpleName());
		}
	}

	/**
	 * Process all incoming packets from this player
	 */
	private int packetProcessCount = 0;

	public void processPackets() {
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
				logger.debug("PlayerConnection.processPackets: " + playerName + " - processing " + packets.length
						+ " packets");
			}
		}

		for (Packet packet : packets) {
			if (logger != null && packetProcessCount <= 5) {
				logger.debug("PlayerConnection.processPackets: " + playerName + " - handling "
						+ packet.getClass().getSimpleName());
			}
			// All packets received by server are ClientPackets
			if (packet instanceof ClientPacket) {
				((ClientPacket) packet).handle(this);
			} else {
				if (logger != null)
					logger.warn("Server received non-ClientPacket: " + packet.getClass().getSimpleName());
			}
		}
	}

	// ===== Packet Handlers =====

	private int inputCount = 0;

	public void sendPacket(mc.sayda.mcraze.network.Packet packet) {
		if (connection != null) {
			connection.sendPacket(packet);
		}
	}

	public void handlePlayerInput(PacketPlayerInput packet) {
		if (player == null) {
			if (logger != null)
				logger.warn("PlayerConnection.handlePlayerInput: Player is null for " + playerName);
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
		if (distance <= mc.sayda.mcraze.Constants.PLAYER_REACH) {
			player.handTargetPos.x = targetBlockX;
			player.handTargetPos.y = targetBlockY;
		} else {
			// Out of reach - set to invalid position
			player.handTargetPos.x = -1;
			player.handTargetPos.y = -1;
		}

		// Update hotbar selection
		if (packet.hotbarSlot >= 0 && packet.hotbarSlot < 10) {
			// PERFORMANCE FIX: Only broadcast if hotbar actually changed
			// Previously this was broadcasting on EVERY input packet (60+ times/sec)
			// causing severe lag during swimming/climbing
			int previousHotbar = player.inventory.hotbarIdx;
			player.setHotbarItem(packet.hotbarSlot);

			// Only broadcast if the value actually changed
			if (player.inventory.hotbarIdx != previousHotbar) {
				sharedWorld.broadcastInventoryUpdates();
			}
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
			return; // Can't respawn if not dead
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
			mc.sayda.mcraze.network.packet.PacketChatMessage chatPacket = new mc.sayda.mcraze.network.packet.PacketChatMessage(
					message, new mc.sayda.mcraze.graphics.Color(255, 255, 0));
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
		// Delegate to SharedWorld for processing
		if (logger != null) {
			logger.info("PlayerConnection.handleChestAction: Player " + playerName +
					" interacted with chest at (" + packet.chestX + ", " + packet.chestY + ")");
		}
		sharedWorld.handleChestAction(this, packet);
	}

	@Override
	public void handleFurnaceAction(mc.sayda.mcraze.network.packet.PacketFurnaceAction packet) {
		sharedWorld.handleFurnaceAction(this, packet);
	}

	@Override
	public void handleEntityAttack(mc.sayda.mcraze.network.packet.PacketEntityAttack packet) {
		// Handle entity attack - delegate to SharedWorld for processing
		sharedWorld.handleEntityAttack(this, packet);
	}

	@Override
	public void handlePing(mc.sayda.mcraze.network.packet.PacketPing packet) {
		// Respond to ping request with pong containing original timestamp
		mc.sayda.mcraze.network.packet.PacketPong pong = new mc.sayda.mcraze.network.packet.PacketPong(
				packet.timestamp);
		connection.sendPacket(pong);
	}

	@Override
	public void handleShiftClick(mc.sayda.mcraze.network.packet.PacketShiftClick packet) {
		// Delegate to SharedWorld for shift-click handling
		sharedWorld.handleShiftClick(this, packet);
	}

	@Override
	public void handleInventoryDrag(mc.sayda.mcraze.network.packet.PacketInventoryDrag packet) {
		sharedWorld.handleInventoryDrag(this, packet);
	}

	@Override
	public void handleInventoryDoubleClick(PacketInventoryDoubleClick packet) {
		sharedWorld.handleInventoryDoubleClick(this, packet);
	}

	@Override
	public void handleClassSelect(PacketClassSelect packet) {
		if (player == null)
			return;

		// Only allow class selection if player doesn't have one
		if (player.selectedClass == PlayerClass.NONE) {
			player.selectClass(packet.getSelectedClass(), packet.getPaths());

			// Broadcast inventory update because stats (like health) might have changed
			sharedWorld.broadcastInventoryUpdates();

			if (logger != null) {
				logger.info("Player " + playerName + " successfully specialized as " + packet.getSelectedClass());
			}
		} else {
			if (logger != null) {
				logger.warn("Player " + playerName + " attempted to re-specialize as " + packet.getSelectedClass());
			}
		}
	}

	@Override
	public void handleSkillUpgrade(PacketSkillUpgrade packet) {
		if (player == null)
			return;

		mc.sayda.mcraze.player.specialization.PassiveEffectType ability = packet.getAbility();

		// Validate: Check points and if already unlocked
		if (player.skillPoints > 0 && !player.unlockedPassives.contains(ability)) {
			// Verify class compatibility (optional security check, but good to have)
			// For now, trust client but verify points

			player.unlockedPassives.add(ability);
			player.skillPoints--;

			if (logger != null) {
				logger.info("Player " + playerName + " unlocked skill: " + ability.name());
			}

			// Sync changes to client
			// We broadcast inventory updates which currently carries basic stats
			sharedWorld.broadcastInventoryUpdates();
		} else {
			if (logger != null) {
				logger.warn("Player " + playerName + " tried to unlock " + ability.name()
						+ " but failed validation (Points: " + player.skillPoints + ", Unlocked: "
						+ player.unlockedPassives.contains(ability) + ")");
			}
		}
	}

	@Override
	public void handleChatSend(PacketChatSend packet) {
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
				// System.out.println("[" + playerName + "] Command not supported: " +
				// packet.message);
			}
			return; // Don't broadcast commands
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
				mc.sayda.mcraze.graphics.Color.white);

		// Broadcast to all players via SharedWorld
		if (logger != null)
			logger.debug("PlayerConnection.handleChatSend: Broadcasting message from " + playerName);
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

	// Track open furnace location
	private int openedFurnaceX = -1;
	private int openedFurnaceY = -1;

	public void setOpenedFurnace(int x, int y) {
		this.openedFurnaceX = x;
		this.openedFurnaceY = y;
	}

	public int getOpenedFurnaceX() {
		return openedFurnaceX;
	}

	public int getOpenedFurnaceY() {
		return openedFurnaceY;
	}

	// Track open chest location
	private int openedChestX = -1;
	private int openedChestY = -1;

	public void setOpenedChest(int x, int y) {
		this.openedChestX = x;
		this.openedChestY = y;
	}

	public int getOpenedChestX() {
		return openedChestX;
	}

	public int getOpenedChestY() {
		return openedChestY;
	}
}
