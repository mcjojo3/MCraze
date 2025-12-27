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

package mc.sayda.mcraze.ui;

import java.util.HashMap;
import java.util.Map;
import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.Game;

/**
 * Handles command parsing and execution
 */
public class CommandHandler {
	private mc.sayda.mcraze.server.Server server;
	private mc.sayda.mcraze.server.SharedWorld sharedWorld;  // For broadcasting command effects
	private Chat chat;

	// Command registry for tab completion
	private Map<String, String[]> commandArguments = new HashMap<>();

	public CommandHandler(mc.sayda.mcraze.server.Server server, mc.sayda.mcraze.server.SharedWorld sharedWorld, Chat chat) {
		this.server = server;
		this.sharedWorld = sharedWorld;
		this.chat = chat;
		registerCommands();
	}

	/**
	 * Set SharedWorld reference (called after SharedWorld is created)
	 */
	public void setSharedWorld(mc.sayda.mcraze.server.SharedWorld sharedWorld) {
		this.sharedWorld = sharedWorld;
	}

	/**
	 * Send a message to all players via broadcast
	 */
	private void sendMessage(String message, Color color) {
		if (sharedWorld != null) {
			// Broadcast to all players (message will come back to sender too)
			mc.sayda.mcraze.network.packet.PacketChatMessage packet =
				new mc.sayda.mcraze.network.packet.PacketChatMessage(message, color);
			sharedWorld.broadcastPacket(packet);
		} else if (chat != null) {
			// Fallback to chat if available (integrated server)
			chat.addMessage(message, color);
		} else {
			// No way to send message (dedicated server with no SharedWorld)
			System.err.println("WARNING: CommandHandler has no SharedWorld or Chat! Message: " + message);
		}
	}

	/**
	 * Register all available commands and their argument options
	 */
	private void registerCommands() {
		// Commands with arguments
		commandArguments.put("/gamerule", new String[]{"keepInventory", "daylightCycle", "spelunking"});
		commandArguments.put("/time", new String[]{"set", "add"});
		commandArguments.put("/give", new String[]{});  // Item names are too many to list
		commandArguments.put("/teleport", new String[]{});

		// Commands without arguments (empty array)
		commandArguments.put("/help", new String[]{});
		commandArguments.put("/kill", new String[]{});
		commandArguments.put("/noclip", new String[]{});
		commandArguments.put("/speed", new String[]{});
	}

	/**
	 * Get all available command names
	 */
	public String[] getAvailableCommands() {
		return commandArguments.keySet().toArray(new String[0]);
	}

	/**
	 * Get argument completion options for a specific command
	 */
	public String[] getCommandArguments(String command) {
		return commandArguments.get(command.toLowerCase());
	}

	/**
	 * Get completion options based on full command context
	 * For example: "/gamerule keepInventory " should suggest "true" or "false"
	 */
	public String[] getContextualCompletions(String[] parts) {
		if (parts.length < 2) {
			return null;
		}

		String command = parts[0].toLowerCase();

		switch (command) {
			case "/gamerule":
				// If we have the rule name, suggest true/false
				if (parts.length == 2) {
					return new String[]{"true", "false"};
				}
				break;

			case "/time":
				// If we have the action (set/add), suggest common time values
				if (parts.length == 2) {
					String action = parts[1].toLowerCase();
					if (action.equals("set")) {
						return new String[]{"0", "6000", "12000", "18000"};  // dawn, noon, dusk, midnight
					} else if (action.equals("add")) {
						return new String[]{"1000", "6000", "12000"};  // Common increments
					}
				}
				break;
		}

		return null;
	}

	/**
	 * Execute a command or send a chat message
	 * @param input The command string
	 * @param executingPlayer The player executing the command (null = use host player)
	 */
	public void executeCommand(String input, mc.sayda.mcraze.entity.Player executingPlayer) {
		if (input == null || input.trim().isEmpty()) {
			return;
		}

		// Default to host player if no player specified
		if (executingPlayer == null) {
			executingPlayer = server.player;
		}

		String[] parts = input.trim().split("\\s+");
		String command = parts[0].toLowerCase();

		if (!command.startsWith("/")) {
			// Regular chat message - broadcast to all players
			String username = executingPlayer != null ? executingPlayer.username : "Unknown";
			sendMessage("<" + username + "> " + input, Color.white);
			System.out.println("[CHAT] " + username + ": " + input);
			return;
		}

		// Echo the command - broadcast so executor sees it
		String username = executingPlayer != null ? executingPlayer.username : "Unknown";
		sendMessage("> [" + username + "] " + input, Color.LIGHT_GRAY);

		// Remove the "/" prefix
		command = command.substring(1);

		switch (command) {
			case "help":
				showHelp();
				break;
			case "gamerule":
				handleGamerule(parts);
				break;
			case "give":
				handleGive(parts, executingPlayer);
				break;
			case "teleport":
			case "tp":
				handleTeleport(parts, executingPlayer);
				break;
			case "time":
				handleTime(parts);
				break;
			case "kill":
				handleKill(executingPlayer);
				break;
			case "noclip":
				handleNoclip(executingPlayer);
				break;
			case "speed":
				handleSpeed(parts, executingPlayer);
				break;
			default:
				sendMessage("Unknown command: /" + command, new Color(255, 100, 100));
				sendMessage("Type /help for a list of commands", Color.gray);
		}
	}

	/**
	 * Execute a command with default player (host)
	 * @deprecated Use executeCommand(String, Player) instead
	 */
	@Deprecated
	public void executeCommand(String input) {
		executeCommand(input, null);
	}

	private void showHelp() {
		sendMessage("=== Available Commands ===", Color.orange);
		sendMessage("/gamerule <rule> [value] - Get/set game rules", Color.white);
		sendMessage("/give <item> [amount] - Give yourself items", Color.white);
		sendMessage("/teleport <x> <y> - Teleport to coordinates", Color.white);
		sendMessage("/time <set|add> <value> - Manage world time", Color.white);
		sendMessage("/noclip - Toggle noclip mode (fly + ghost through blocks)", Color.white);
		sendMessage("/speed <multiplier> - Set movement speed (1.0 = normal)", Color.white);
		sendMessage("/kill - Kill yourself (respawn)", Color.white);
		sendMessage("/help - Show this help", Color.white);
	}

	private void handleGamerule(String[] parts) {
		if (parts.length < 2) {
			sendMessage("Usage: /gamerule <rule> [value]", new Color(255, 200, 100));
			sendMessage("Available rules:", Color.gray);
			sendMessage("  keepInventory - Keep items on death (true/false)", Color.gray);
			sendMessage("  daylightCycle - Enable day/night cycle (true/false)", Color.gray);
            sendMessage("  spelunking - Disable darkness (true/false)", Color.gray);
			return;
		}

		String rule = parts[1].toLowerCase();

		// Get value
		if (parts.length == 2) {
			switch (rule) {
				case "keepinventory":
					sendMessage("keepInventory = " + server.world.keepInventory, Color.green);
					break;
				case "daylightcycle":
					sendMessage("daylightCycle = " + server.world.daylightCycle, Color.green);
					break;
                case "spelunking":
                    sendMessage("spelunking = " + server.world.spelunking, Color.green);
                    break;
				default:
					sendMessage("Unknown gamerule: " + rule, new Color(255, 100, 100));
			}
			return;
		}

		// Set value
		String value = parts[2].toLowerCase();
		boolean boolValue;

		if (value.equals("true") || value.equals("1")) {
			boolValue = true;
		} else if (value.equals("false") || value.equals("0")) {
			boolValue = false;
		} else {
			sendMessage("Invalid value. Use: true/false or 1/0", new Color(255, 100, 100));
			return;
		}

		switch (rule) {
			case "keepinventory":
				server.world.keepInventory = boolValue;
				sendMessage("Set keepInventory to " + boolValue, Color.green);
				if (sharedWorld != null) sharedWorld.broadcastGamerules();  // Sync to all clients
				break;
			case "daylightcycle":
				server.world.daylightCycle = boolValue;
				sendMessage("Set daylightCycle to " + boolValue, Color.green);
				if (sharedWorld != null) sharedWorld.broadcastGamerules();  // Sync to all clients
				break;
            case "spelunking":
                server.world.spelunking = boolValue;
                sendMessage("Set spelunking to " + boolValue, Color.green);
                if (sharedWorld != null) sharedWorld.broadcastGamerules();  // Sync to all clients
                break;
			default:
				sendMessage("Unknown gamerule: " + rule, new Color(255, 100, 100));
		}
	}

	private void handleTime(String[] parts) {
		if (parts.length < 3) {
			sendMessage("Usage: /time <set|add> <value>", new Color(255, 200, 100));
			sendMessage("Example: /time set 0 (dawn)", Color.gray);
			sendMessage("Example: /time set 6000 (noon)", Color.gray);
			sendMessage("Example: /time set 12000 (dusk)", Color.gray);
			return;
		}

		String action = parts[1].toLowerCase();

		try {
			long value = Long.parseLong(parts[2]);

			// Use SharedWorld to access world and broadcast changes
			mc.sayda.mcraze.world.World world = sharedWorld != null ? sharedWorld.getWorld() : server.world;

			if (world != null) {
				if (action.equals("set")) {
					world.setTicksAlive(value);
					sendMessage("Time set to " + value, Color.green);
				} else if (action.equals("add")) {
					world.setTicksAlive(world.getTicksAlive() + value);
					sendMessage("Added " + value + " to time", Color.green);
				} else {
					sendMessage("Unknown action: " + action, new Color(255, 100, 100));
					return;
				}

				// Broadcast world update to sync time change immediately
				if (sharedWorld != null) {
					// Time will be broadcast in next world update packet automatically
					// No need for manual broadcast - world updates include ticksAlive
				}
			}
		} catch (NumberFormatException e) {
			sendMessage("Invalid time value: " + parts[2], new Color(255, 100, 100));
		}
	}

	private void handleKill(mc.sayda.mcraze.entity.Player executingPlayer) {
		if (executingPlayer != null) {
			if (executingPlayer.dead) {
				sendMessage("You are already dead!", new Color(255, 100, 100));
			} else {
				executingPlayer.takeDamage(executingPlayer.hitPoints);
				// Force immediate death processing (don't wait for tick)
				if (executingPlayer.hitPoints <= 0 && !executingPlayer.dead) {
					executingPlayer.dead = true;
					// Find PlayerConnection and trigger death immediately
					for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
						if (pc.getPlayer() == executingPlayer) {
							pc.getConnection().sendPacket(new mc.sayda.mcraze.network.packet.PacketPlayerDeath());
							// Drop items
							java.util.ArrayList<mc.sayda.mcraze.item.Item> droppedItems = executingPlayer.dropAllItems(new java.util.Random());
							for (mc.sayda.mcraze.item.Item item : droppedItems) {
								sharedWorld.addEntity(item);
							}
							sharedWorld.broadcastInventoryUpdates();
							break;
						}
					}
				}
				sendMessage("Killed " + executingPlayer.username, Color.green);
			}
		}
	}

	private void handleGive(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
		if (parts.length < 2) {
			sendMessage("Usage: /give <item> [amount]", new Color(255, 200, 100));
			sendMessage("Example: /give dirt 64", Color.gray);
			sendMessage("Example: /give stone_pickaxe", Color.gray);
			return;
		}

		String itemId = parts[1].toLowerCase();
		int amount = 1;

		// Parse amount if provided
		if (parts.length >= 3) {
			try {
				amount = Integer.parseInt(parts[2]);
				if (amount <= 0) {
					sendMessage("Amount must be positive", new Color(255, 100, 100));
					return;
				}
			} catch (NumberFormatException e) {
				sendMessage("Invalid amount: " + parts[2], new Color(255, 100, 100));
				return;
			}
		}

		// Get the item from registry
		mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
		if (item == null) {
			sendMessage("Unknown item: " + itemId, new Color(255, 100, 100));
			sendMessage("Available items: dirt, stone, cobble, wood, plank, torch, etc.", Color.gray);
			return;
		}

		// Give the item to player
		if (executingPlayer != null && executingPlayer.inventory != null) {
			mc.sayda.mcraze.item.Item giveItem = item.clone();
			int remaining = executingPlayer.inventory.addItem(giveItem, amount);
			if (remaining == 0) {
				sendMessage("Gave " + amount + "x " + itemId + " to " + executingPlayer.username, Color.green);
			} else if (remaining < amount) {
				sendMessage("Gave " + (amount - remaining) + "x " + itemId + " to " + executingPlayer.username + " (inventory full)", new Color(255, 200, 100));
			} else {
				sendMessage("Inventory full! Could not give any items to " + executingPlayer.username, new Color(255, 100, 100));
			}

			// Broadcast inventory update to all clients
			if (sharedWorld != null) {
				sharedWorld.broadcastInventoryUpdates();
			}
		}
	}

	private void handleTeleport(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
		if (parts.length < 3) {
			sendMessage("Usage: /teleport <x> <y>", new Color(255, 200, 100));
			sendMessage("Example: /teleport 100 50", Color.gray);
			return;
		}

		try {
			float x = Float.parseFloat(parts[1]);
			float y = Float.parseFloat(parts[2]);

			if (executingPlayer != null) {
				// Check if coordinates are within world bounds
				if (server.world != null) {
					if (x < 0 || x >= server.world.width || y < 0 || y >= server.world.height) {
						sendMessage("Coordinates out of bounds! World size: " +
							server.world.width + "x" + server.world.height, new Color(255, 100, 100));
						return;
					}
				}

				executingPlayer.x = x;
				executingPlayer.y = y;
				executingPlayer.dx = 0;
				executingPlayer.dy = 0;
				// Broadcast entity update immediately to all clients
				sharedWorld.broadcastEntityUpdate();
				sendMessage("Teleported " + executingPlayer.username + " to (" + x + ", " + y + ")", Color.green);
			}
		} catch (NumberFormatException e) {
			sendMessage("Invalid coordinates", new Color(255, 100, 100));
		}
	}

	private void handleNoclip(mc.sayda.mcraze.entity.Player executingPlayer) {
		if (executingPlayer != null) {
			// Toggle flying and noclip together
			executingPlayer.flying = !executingPlayer.flying;
			executingPlayer.noclip = executingPlayer.flying;  // noclip follows flying state

			// Broadcast entity update immediately to all clients
			sharedWorld.broadcastEntityUpdate();

			if (executingPlayer.flying) {
				sendMessage("Noclip mode enabled for " + executingPlayer.username + " (ghost through blocks)", Color.green);
			} else {
				sendMessage("Noclip mode disabled for " + executingPlayer.username, Color.green);
			}
		}
	}

	private void handleSpeed(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
		if (parts.length < 2) {
			sendMessage("Usage: /speed <multiplier>", new Color(255, 200, 100));
			sendMessage("Example: /speed 2.0 (2x speed)", Color.gray);
			sendMessage("Example: /speed 1.0 (normal speed)", Color.gray);
			sendMessage("Example: /speed 0.5 (half speed)", Color.gray);
			return;
		}

		try {
			float multiplier = Float.parseFloat(parts[1]);
			if (multiplier <= 0) {
				sendMessage("Speed multiplier must be positive", new Color(255, 100, 100));
				return;
			}
			if (multiplier > 10) {
				sendMessage("Speed multiplier capped at 10x", new Color(255, 200, 100));
				multiplier = 10;
			}

			if (executingPlayer != null) {
				executingPlayer.speedMultiplier = multiplier;
				// Broadcast entity update immediately to all clients
				sharedWorld.broadcastEntityUpdate();
				sendMessage("Set speed to " + multiplier + "x for " + executingPlayer.username, Color.green);
			}
		} catch (NumberFormatException e) {
			sendMessage("Invalid speed multiplier: " + parts[1], new Color(255, 100, 100));
		}
	}
}
