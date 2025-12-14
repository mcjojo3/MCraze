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

package mc.sayda.ui;

import java.util.HashMap;
import java.util.Map;
import mc.sayda.Color;
import mc.sayda.Game;

/**
 * Handles command parsing and execution
 */
public class CommandHandler {
	private mc.sayda.server.Server server;
	private Chat chat;

	// Command registry for tab completion
	private Map<String, String[]> commandArguments = new HashMap<>();

	public CommandHandler(mc.sayda.server.Server server, Chat chat) {
		this.server = server;
		this.chat = chat;
		registerCommands();
	}

	/**
	 * Register all available commands and their argument options
	 */
	private void registerCommands() {
		// Commands with arguments
		commandArguments.put("/gamerule", new String[]{"keepInventory", "daylightCycle"});
		commandArguments.put("/time", new String[]{"set", "add"});

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
	 */
	public void executeCommand(String input) {
		if (input == null || input.trim().isEmpty()) {
			return;
		}

		String[] parts = input.trim().split("\\s+");
		String command = parts[0].toLowerCase();

		if (!command.startsWith("/")) {
			// Regular chat message (for future multiplayer)
			chat.addMessage("<Player> " + input, Color.white);
			System.out.println("[CHAT] Player: " + input);
			// TODO: In multiplayer, send this message to the server
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
			case "time":
				handleTime(parts);
				break;
			case "kill":
				handleKill();
				break;
			default:
				chat.addMessage("Unknown command: /" + command, new Color(255, 100, 100));
				chat.addMessage("Type /help for a list of commands", Color.gray);
		}
	}

	private void showHelp() {
		chat.addMessage("=== Available Commands ===", Color.orange);
		chat.addMessage("/gamerule <rule> [value] - Get/set game rules", Color.white);
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

	private void handleKill() {
		if (server.player != null) {
			if (server.player.dead) {
				chat.addMessage("You are already dead!", new Color(255, 100, 100));
			} else {
				server.player.takeDamage(server.player.hitPoints);
				chat.addMessage("Killed player", Color.green);
			}
		}
	}
}
