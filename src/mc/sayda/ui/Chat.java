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

import java.util.ArrayList;
import mc.sayda.Color;
import mc.sayda.GraphicsHandler;

/**
 * Chat/Console UI for commands and future multiplayer chat
 */
public class Chat {
	private static final int MAX_MESSAGES = 10;
	private static final int MESSAGE_DISPLAY_TIME = 300; // ticks (~5 seconds)

	private boolean open = false;
	private String currentInput = "";
	private ArrayList<ChatMessage> messages = new ArrayList<>();
	private long currentTick = 0;

	// Tab completion state
	private CommandHandler commandHandler;  // Dynamically provides available commands
	private int tabCompletionIndex = -1;
	private String tabCompletionPrefix = "";
	private ArrayList<String> tabMatches = new ArrayList<>();

	// Command history
	private ArrayList<String> commandHistory = new ArrayList<>();
	private int historyIndex = -1;

	public Chat() {
		addMessage("Press 'T' to open chat. Type /help for commands.", Color.gray);
	}

	/**
	 * Set the command handler for dynamic tab completion
	 */
	public void setCommandHandler(CommandHandler handler) {
		this.commandHandler = handler;
	}

	/**
	 * Message with timestamp for auto-hiding
	 */
	private static class ChatMessage {
		String text;
		Color color;
		long timestamp;

		ChatMessage(String text, Color color, long timestamp) {
			this.text = text;
			this.color = color;
			this.timestamp = timestamp;
		}
	}

	public boolean isOpen() {
		return open;
	}

	public void setOpen(boolean open) {
		this.open = open;
		if (!open) {
			currentInput = "";
		}
	}

	public void toggle() {
		setOpen(!open);
	}

	/**
	 * Add a character to the input
	 */
	public void typeChar(char c) {
		if (open && currentInput.length() < 100) {
			currentInput += c;
			resetTabCompletion();
		}
	}

	/**
	 * Remove last character (backspace)
	 */
	public void backspace() {
		if (open && currentInput.length() > 0) {
			currentInput = currentInput.substring(0, currentInput.length() - 1);
			resetTabCompletion();
		}
	}

	/**
	 * Handle tab completion - cycle through matching commands and arguments
	 */
	public void tabComplete() {
		if (!open || commandHandler == null) return;

		System.out.println("Tab completion triggered. Current input: '" + currentInput + "'");

		// Initialize tab completion on first tab press
		if (tabCompletionIndex == -1) {
			tabCompletionPrefix = currentInput.toLowerCase();
			tabMatches.clear();

			// Split input into parts to determine completion level
			String[] parts = currentInput.trim().split("\\s+", -1);  // -1 to keep trailing empty string
			int numSpaces = countSpaces(currentInput);

			if (numSpaces == 0) {
				// No space - completing main command
				String[] availableCommands = commandHandler.getAvailableCommands();
				for (String cmd : availableCommands) {
					if (cmd.toLowerCase().startsWith(tabCompletionPrefix)) {
						tabMatches.add(cmd);
					}
				}
			} else if (numSpaces == 1) {
				// One space - completing first argument (e.g., gamerule name, time action)
				String command = parts[0].toLowerCase();
				String argPrefix = parts.length > 1 ? parts[1].toLowerCase() : "";

				String[] options = commandHandler.getCommandArguments(command);
				if (options != null && options.length > 0) {
					for (String option : options) {
						if (option.toLowerCase().startsWith(argPrefix)) {
							tabMatches.add(command + " " + option);
						}
					}
				}
			} else {
				// Multiple spaces - completing values (e.g., true/false, time values)
				String[] contextualOptions = commandHandler.getContextualCompletions(parts);
				if (contextualOptions != null && contextualOptions.length > 0) {
					String valuePrefix = parts.length > 0 ? parts[parts.length - 1].toLowerCase() : "";

					// Rebuild the base (everything except the last part)
					StringBuilder base = new StringBuilder();
					for (int i = 0; i < parts.length - 1; i++) {
						if (i > 0) base.append(" ");
						base.append(parts[i]);
					}

					for (String option : contextualOptions) {
						if (option.toLowerCase().startsWith(valuePrefix)) {
							tabMatches.add(base.toString() + " " + option);
						}
					}
				}
			}

			System.out.println("Found " + tabMatches.size() + " matches for prefix '" + tabCompletionPrefix + "'");

			if (tabMatches.isEmpty()) {
				return;  // No matches, do nothing
			}

			tabCompletionIndex = 0;
			currentInput = tabMatches.get(0);
			System.out.println("Set input to: " + currentInput);
		} else {
			// Cycle to next match
			tabCompletionIndex = (tabCompletionIndex + 1) % tabMatches.size();
			currentInput = tabMatches.get(tabCompletionIndex);
			System.out.println("Cycled to: " + currentInput);
		}
	}

	/**
	 * Count spaces in a string
	 */
	private int countSpaces(String str) {
		int count = 0;
		for (char c : str.toCharArray()) {
			if (c == ' ') count++;
		}
		return count;
	}

	/**
	 * Reset tab completion state
	 */
	private void resetTabCompletion() {
		tabCompletionIndex = -1;
		tabCompletionPrefix = "";
		tabMatches.clear();
	}

	/**
	 * Reset command history navigation
	 */
	private void resetHistoryNavigation() {
		historyIndex = -1;
	}

	/**
	 * Get the current input and clear it
	 */
	public String submit() {
		String input = currentInput;

		// Save to history if not empty
		if (!input.trim().isEmpty()) {
			commandHistory.add(input);
			// Keep history limited to last 50 commands
			if (commandHistory.size() > 50) {
				commandHistory.remove(0);
			}
		}

		currentInput = "";
		resetTabCompletion();
		resetHistoryNavigation();
		return input;
	}

	/**
	 * Navigate to previous command in history (arrow up)
	 */
	public void historyUp() {
		if (!open || commandHistory.isEmpty()) return;

		if (historyIndex == -1) {
			// First time pressing up - start from the end
			historyIndex = commandHistory.size() - 1;
			currentInput = commandHistory.get(historyIndex);
		} else if (historyIndex > 0) {
			// Go to older command
			historyIndex--;
			currentInput = commandHistory.get(historyIndex);
		}

		resetTabCompletion();
	}

	/**
	 * Navigate to next command in history (arrow down)
	 */
	public void historyDown() {
		if (!open || historyIndex == -1) return;

		if (historyIndex < commandHistory.size() - 1) {
			// Go to newer command
			historyIndex++;
			currentInput = commandHistory.get(historyIndex);
		} else {
			// At the newest - clear input
			historyIndex = -1;
			currentInput = "";
		}

		resetTabCompletion();
	}

	/**
	 * Add a message to chat
	 */
	public void addMessage(String text, Color color) {
		messages.add(new ChatMessage(text, color, currentTick));
		// Keep only recent messages
		while (messages.size() > MAX_MESSAGES) {
			messages.remove(0);
		}
	}

	public void addMessage(String text) {
		addMessage(text, Color.white);
	}

	/**
	 * Draw the chat UI
	 */
	public void draw(GraphicsHandler g, int screenWidth, int screenHeight) {
		currentTick++;

		int y = screenHeight - 180;

		// Draw messages (visible when chat is open or recently sent)
		for (int i = Math.max(0, messages.size() - 10); i < messages.size(); i++) {
			ChatMessage msg = messages.get(i);

			// Only show if chat is open or message is recent
			if (open || (currentTick - msg.timestamp) < MESSAGE_DISPLAY_TIME) {
				// Semi-transparent background
				g.setColor(new Color(0, 0, 0, 128));
				g.fillRect(5, y - 2, screenWidth - 10, 14);

				// Message text
				g.setColor(msg.color);
				g.drawString(msg.text, 8, y + 10);
				y += 16;
			}
		}

		// Draw input box if chat is open
		if (open) {
			y += 5;
			// Input background
			g.setColor(new Color(0, 0, 0, 180));
			g.fillRect(5, y - 2, screenWidth - 10, 18);

			// Input text
			g.setColor(Color.white);
			String displayText = "> " + currentInput + "_";
			g.drawString(displayText, 8, y + 12);
		}
	}
}
