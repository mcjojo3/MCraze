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
	private Chat chat;

	// Command registry for tab completion
	private Map<String, String[]> commandArguments = new HashMap<>();

	public CommandHandler(mc.sayda.mcraze.server.Server server, Chat chat) {
		this.server = server;
		this.chat = chat;
		registerCommands();
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
			// Regular chat message (for future multiplayer)
			chat.addMessage("<Player> " + input, Color.white);
			System.out.println("[CHAT] Player: " + input);
			return;
		}

		// Echo the command
		chat.addMessage("> " + input, Color.LIGHT_GRAY);

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
			default:
				chat.addMessage("Unknown command: /" + command, new Color(255, 100, 100));
				chat.addMessage("Type /help for a list of commands", Color.gray);
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
		chat.addMessage("=== Available Commands ===", Color.orange);
		chat.addMessage("/gamerule <rule> [value] - Get/set game rules", Color.white);
		chat.addMessage("/give <item> [amount] - Give yourself items", Color.white);
		chat.addMessage("/teleport <x> <y> - Teleport to coordinates", Color.white);
		chat.addMessage("/time <set|add> <value> - Manage world time", Color.white);
		chat.addMessage("/kill - Kill yourself (respawn)", Color.white);
		chat.addMessage("/help - Show this help", Color.white);
	}

	private void handleGamerule(String[] parts) {
		if (parts.length < 2) {
			chat.addMessage("Usage: /gamerule <rule> [value]", new Color(255, 200, 100));
			chat.addMessage("Available rules:", Color.gray);
			chat.addMessage("  keepInventory - Keep items on death (true/false)", Color.gray);
			chat.addMessage("  daylightCycle - Enable day/night cycle (true/false)", Color.gray);
            chat.addMessage("  spelunking - Disable darkness (true/false)", Color.gray);
			return;
		}

		String rule = parts[1].toLowerCase();

		// Get value
		if (parts.length == 2) {
			switch (rule) {
				case "keepinventory":
					chat.addMessage("keepInventory = " + server.keepInventory, Color.green);
					break;
				case "daylightcycle":
					chat.addMessage("daylightCycle = " + server.daylightCycle, Color.green);
					break;
                case "spelunking":
                    chat.addMessage("spelunking = " + mc.sayda.mcraze.Constants.DEBUG_VISIBILITY_ON, Color.green);
                    break;
				default:
					chat.addMessage("Unknown gamerule: " + rule, new Color(255, 100, 100));
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
			chat.addMessage("Invalid value. Use: true/false or 1/0", new Color(255, 100, 100));
			return;
		}

		switch (rule) {
			case "keepinventory":
				server.keepInventory = boolValue;
				chat.addMessage("Set keepInventory to " + boolValue, Color.green);
				break;
			case "daylightcycle":
				server.daylightCycle = boolValue;
				chat.addMessage("Set daylightCycle to " + boolValue, Color.green);
				break;
            case "spelunking":
                mc.sayda.mcraze.Constants.DEBUG_VISIBILITY_ON = boolValue;
                chat.addMessage("Set spelunking to " + boolValue, Color.green);
                break;
			default:
				chat.addMessage("Unknown gamerule: " + rule, new Color(255, 100, 100));
		}
	}

	private void handleTime(String[] parts) {
		if (parts.length < 3) {
			chat.addMessage("Usage: /time <set|add> <value>", new Color(255, 200, 100));
			chat.addMessage("Example: /time set 0 (dawn)", Color.gray);
			chat.addMessage("Example: /time set 6000 (noon)", Color.gray);
			chat.addMessage("Example: /time set 12000 (dusk)", Color.gray);
			return;
		}

		String action = parts[1].toLowerCase();

		try {
			long value = Long.parseLong(parts[2]);

			if (server.world != null) {
				if (action.equals("set")) {
					server.world.setTicksAlive(value);
					chat.addMessage("Time set to " + value, Color.green);
				} else if (action.equals("add")) {
					server.world.setTicksAlive(server.world.getTicksAlive() + value);
					chat.addMessage("Added " + value + " to time", Color.green);
				} else {
					chat.addMessage("Unknown action: " + action, new Color(255, 100, 100));
				}
			}
		} catch (NumberFormatException e) {
			chat.addMessage("Invalid time value: " + parts[2], new Color(255, 100, 100));
		}
	}

	private void handleKill(mc.sayda.mcraze.entity.Player executingPlayer) {
		if (executingPlayer != null) {
			if (executingPlayer.dead) {
				chat.addMessage("You are already dead!", new Color(255, 100, 100));
			} else {
				executingPlayer.takeDamage(executingPlayer.hitPoints);
				chat.addMessage("Killed " + executingPlayer.username, Color.green);
			}
		}
	}

	private void handleGive(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
		if (parts.length < 2) {
			chat.addMessage("Usage: /give <item> [amount]", new Color(255, 200, 100));
			chat.addMessage("Example: /give dirt 64", Color.gray);
			chat.addMessage("Example: /give stone_pickaxe", Color.gray);
			return;
		}

		String itemId = parts[1].toLowerCase();
		int amount = 1;

		// Parse amount if provided
		if (parts.length >= 3) {
			try {
				amount = Integer.parseInt(parts[2]);
				if (amount <= 0) {
					chat.addMessage("Amount must be positive", new Color(255, 100, 100));
					return;
				}
			} catch (NumberFormatException e) {
				chat.addMessage("Invalid amount: " + parts[2], new Color(255, 100, 100));
				return;
			}
		}

		// Get the item from registry
		mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(itemId);
		if (item == null) {
			chat.addMessage("Unknown item: " + itemId, new Color(255, 100, 100));
			chat.addMessage("Available items: dirt, stone, cobble, wood, plank, torch, etc.", Color.gray);
			return;
		}

		// Give the item to player
		if (executingPlayer != null && executingPlayer.inventory != null) {
			mc.sayda.mcraze.item.Item giveItem = item.clone();
			int remaining = executingPlayer.inventory.addItem(giveItem, amount);
			if (remaining == 0) {
				chat.addMessage("Gave " + amount + "x " + itemId + " to " + executingPlayer.username, Color.green);
			} else if (remaining < amount) {
				chat.addMessage("Gave " + (amount - remaining) + "x " + itemId + " to " + executingPlayer.username + " (inventory full)", new Color(255, 200, 100));
			} else {
				chat.addMessage("Inventory full! Could not give any items to " + executingPlayer.username, new Color(255, 100, 100));
			}
		}
	}

	private void handleTeleport(String[] parts, mc.sayda.mcraze.entity.Player executingPlayer) {
		if (parts.length < 3) {
			chat.addMessage("Usage: /teleport <x> <y>", new Color(255, 200, 100));
			chat.addMessage("Example: /teleport 100 50", Color.gray);
			return;
		}

		try {
			float x = Float.parseFloat(parts[1]);
			float y = Float.parseFloat(parts[2]);

			if (executingPlayer != null) {
				// Check if coordinates are within world bounds
				if (server.world != null) {
					if (x < 0 || x >= server.world.width || y < 0 || y >= server.world.height) {
						chat.addMessage("Coordinates out of bounds! World size: " +
							server.world.width + "x" + server.world.height, new Color(255, 100, 100));
						return;
					}
				}

				executingPlayer.x = x;
				executingPlayer.y = y;
				executingPlayer.dx = 0;
				executingPlayer.dy = 0;
				chat.addMessage("Teleported " + executingPlayer.username + " to (" + x + ", " + y + ")", Color.green);
			}
		} catch (NumberFormatException e) {
			chat.addMessage("Invalid coordinates", new Color(255, 100, 100));
		}
	}
}
