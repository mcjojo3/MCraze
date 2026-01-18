/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.ui.screen;

import mc.sayda.mcraze.ui.component.*;
import mc.sayda.mcraze.graphics.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.player.data.*;
import mc.sayda.mcraze.ui.CommandHandler;

import java.util.ArrayList;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;

/**
 * Chat/Console UI for commands and future multiplayer chat
 */
public class Chat {
	private static final int MAX_MESSAGES = 100; // Increased from 10 to 100
	private static final int VISIBLE_MESSAGES = 10; // How many messages to show at once
	private static final int MESSAGE_DISPLAY_TIME = 300; // ticks (~5 seconds)

	private boolean open = false;
	private String currentInput = "";
	private int cursorPosition = 0; // CRITICAL FIX: Cursor position for editing
	private int selectionStart = -1; // Selection start (-1 if no selection)
	private int selectionEnd = -1; // Selection end (-1 if no selection)
	private ArrayList<ChatMessage> messages = new ArrayList<>();
	private long currentTick = 0;
	private int scrollOffset = 0; // How many messages scrolled up from bottom

	// Tab completion state
	private CommandHandler commandHandler; // Dynamically provides available commands
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
			cursorPosition = 0;
		}
	}

	public void toggle() {
		setOpen(!open);
	}

	/**
	 * Add a character to the input (CRITICAL FIX: Insert at cursor position, delete
	 * selection)
	 */
	public void typeChar(char c) {
		if (open && currentInput.length() < 100) {
			// Delete selection first if active
			if (hasSelection()) {
				deleteSelection();
			}

			currentInput = currentInput.substring(0, cursorPosition) + c +
					currentInput.substring(cursorPosition);
			cursorPosition++;
			resetTabCompletion();
		}
	}

	/**
	 * Remove character before cursor (backspace) - CRITICAL FIX: Delete at cursor
	 * position or selection
	 */
	public void backspace() {
		if (open) {
			if (hasSelection()) {
				deleteSelection();
			} else if (cursorPosition > 0) {
				currentInput = currentInput.substring(0, cursorPosition - 1) +
						currentInput.substring(cursorPosition);
				cursorPosition--;
			}
			resetTabCompletion();
		}
	}

	/**
	 * Remove character after cursor (delete key) or delete selection
	 */
	public void delete() {
		if (open) {
			if (hasSelection()) {
				deleteSelection();
			} else if (cursorPosition < currentInput.length()) {
				currentInput = currentInput.substring(0, cursorPosition) +
						currentInput.substring(cursorPosition + 1);
			}
			resetTabCompletion();
		}
	}

	/**
	 * Move cursor left (left arrow key, with optional selection)
	 */
	public void moveCursorLeft(boolean shiftPressed) {
		if (open) {
			if (shiftPressed) {
				moveCursorWithSelection(-1);
			} else {
				if (hasSelection()) {
					cursorPosition = Math.min(selectionStart, selectionEnd);
					clearSelection();
				} else if (cursorPosition > 0) {
					cursorPosition--;
				}
			}
			resetTabCompletion();
		}
	}

	/**
	 * Move cursor right (right arrow key, with optional selection)
	 */
	public void moveCursorRight(boolean shiftPressed) {
		if (open) {
			if (shiftPressed) {
				moveCursorWithSelection(1);
			} else {
				if (hasSelection()) {
					cursorPosition = Math.max(selectionStart, selectionEnd);
					clearSelection();
				} else if (cursorPosition < currentInput.length()) {
					cursorPosition++;
				}
			}
			resetTabCompletion();
		}
	}

	/**
	 * Move cursor to beginning (Home key, with optional selection)
	 */
	public void moveCursorHome(boolean shiftPressed) {
		if (open) {
			if (shiftPressed && cursorPosition > 0) {
				setSelection(0, cursorPosition);
			} else {
				clearSelection();
			}
			cursorPosition = 0;
			resetTabCompletion();
		}
	}

	/**
	 * Move cursor to end (End key, with optional selection)
	 */
	public void moveCursorEnd(boolean shiftPressed) {
		if (open) {
			if (shiftPressed && cursorPosition < currentInput.length()) {
				setSelection(cursorPosition, currentInput.length());
			} else {
				clearSelection();
			}
			cursorPosition = currentInput.length();
			resetTabCompletion();
		}
	}

	/**
	 * Move cursor with selection (shift+arrow)
	 */
	private void moveCursorWithSelection(int offset) {
		int newPos = Math.max(0, Math.min(currentInput.length(), cursorPosition + offset));

		if (!hasSelection()) {
			// Start new selection
			selectionStart = cursorPosition;
			selectionEnd = newPos;
		} else {
			// Extend existing selection
			if (cursorPosition == selectionStart) {
				selectionStart = newPos;
			} else {
				selectionEnd = newPos;
			}
		}

		cursorPosition = newPos;

		// Normalize selection (ensure start < end)
		if (selectionStart > selectionEnd) {
			int temp = selectionStart;
			selectionStart = selectionEnd;
			selectionEnd = temp;
		}

		// Clear selection if start == end
		if (selectionStart == selectionEnd) {
			clearSelection();
		}
	}

	/**
	 * Set selection range
	 */
	private void setSelection(int start, int end) {
		selectionStart = Math.min(start, end);
		selectionEnd = Math.max(start, end);
		if (selectionStart == selectionEnd) {
			clearSelection();
		}
	}

	/**
	 * Clear selection
	 */
	private void clearSelection() {
		selectionStart = -1;
		selectionEnd = -1;
	}

	/**
	 * Check if there's an active selection
	 */
	private boolean hasSelection() {
		return selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd;
	}

	/**
	 * Delete selected text
	 */
	private void deleteSelection() {
		if (!hasSelection())
			return;

		currentInput = currentInput.substring(0, selectionStart) +
				currentInput.substring(selectionEnd);
		cursorPosition = selectionStart;
		clearSelection();
	}

	/**
	 * Get selected text
	 */
	private String getSelectedText() {
		if (!hasSelection())
			return "";
		return currentInput.substring(selectionStart, selectionEnd);
	}

	/**
	 * Select all text (Ctrl+A)
	 */
	public void selectAll() {
		if (open && !currentInput.isEmpty()) {
			selectionStart = 0;
			selectionEnd = currentInput.length();
			cursorPosition = currentInput.length();
		}
	}

	/**
	 * Copy selection to clipboard (Ctrl+C)
	 */
	public void copyToClipboard() {
		if (open && hasSelection()) {
			try {
				java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				clipboard.setContents(new StringSelection(getSelectedText()), null);
			} catch (Exception e) {
				System.err.println("Failed to copy to clipboard: " + e.getMessage());
			}
		}
	}

	/**
	 * Paste from clipboard (Ctrl+V), replacing selection if active
	 */
	public void pasteFromClipboard() {
		if (open) {
			try {
				java.awt.datatransfer.Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				java.awt.datatransfer.Transferable contents = clipboard.getContents(null);
				if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
					String pastedText = (String) contents.getTransferData(DataFlavor.stringFlavor);
					// Insert pasted text at cursor position
					if (pastedText != null && !pastedText.isEmpty()) {
						// Delete selection first if active
						if (hasSelection()) {
							deleteSelection();
						}

						// Filter to single line and limit length
						pastedText = pastedText.replace("\n", "").replace("\r", "");
						int maxPaste = 100 - currentInput.length();
						if (pastedText.length() > maxPaste) {
							pastedText = pastedText.substring(0, maxPaste);
						}
						currentInput = currentInput.substring(0, cursorPosition) + pastedText +
								currentInput.substring(cursorPosition);
						cursorPosition += pastedText.length();
						resetTabCompletion();
					}
				}
			} catch (Exception e) {
				System.err.println("Failed to paste from clipboard: " + e.getMessage());
			}
		}
	}

	/**
	 * Handle tab completion - cycle through matching commands and arguments
	 */
	public void tabComplete() {
		if (!open || commandHandler == null)
			return;

		// Initialize tab completion on first tab press
		if (tabCompletionIndex == -1) {
			tabCompletionPrefix = currentInput.toLowerCase();
			tabMatches.clear();

			// Split input into parts to determine completion level
			String[] parts = currentInput.trim().split("\\s+", -1); // -1 to keep trailing empty string
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
						if (i > 0)
							base.append(" ");
						base.append(parts[i]);
					}

					for (String option : contextualOptions) {
						if (option.toLowerCase().startsWith(valuePrefix)) {
							tabMatches.add(base.toString() + " " + option);
						}
					}
				}
			}

			if (tabMatches.isEmpty()) {
				return; // No matches, do nothing
			}

			tabCompletionIndex = 0;
			currentInput = tabMatches.get(0);
		} else {
			// Cycle to next match
			tabCompletionIndex = (tabCompletionIndex + 1) % tabMatches.size();
			currentInput = tabMatches.get(tabCompletionIndex);
		}

		cursorPosition = currentInput.length(); // CRITICAL FIX: Move cursor to end after tab completion
	}

	/**
	 * Count spaces in a string
	 */
	private int countSpaces(String str) {
		int count = 0;
		for (char c : str.toCharArray()) {
			if (c == ' ')
				count++;
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
		cursorPosition = 0; // CRITICAL FIX: Reset cursor on submit
		resetTabCompletion();
		resetHistoryNavigation();
		return input;
	}

	/**
	 * Navigate to previous command in history (arrow up)
	 */
	public void historyUp() {
		if (!open || commandHistory.isEmpty())
			return;

		if (historyIndex == -1) {
			// First time pressing up - start from the end
			historyIndex = commandHistory.size() - 1;
			currentInput = commandHistory.get(historyIndex);
		} else if (historyIndex > 0) {
			// Go to older command
			historyIndex--;
			currentInput = commandHistory.get(historyIndex);
		}

		cursorPosition = currentInput.length(); // CRITICAL FIX: Move cursor to end
		resetTabCompletion();
	}

	/**
	 * Navigate to next command in history (arrow down)
	 */
	public void historyDown() {
		if (!open || historyIndex == -1)
			return;

		if (historyIndex < commandHistory.size() - 1) {
			// Go to newer command
			historyIndex++;
			currentInput = commandHistory.get(historyIndex);
		} else {
			// At the newest - clear input
			historyIndex = -1;
			currentInput = "";
		}

		cursorPosition = currentInput.length(); // CRITICAL FIX: Move cursor to end
		resetTabCompletion();
	}

	/**
	 * Scroll chat history up (view older messages)
	 */
	public void scrollUp() {
		if (!open)
			return;

		// Calculate max scroll (can't scroll beyond start of history)
		int maxScroll = Math.max(0, messages.size() - VISIBLE_MESSAGES);
		if (scrollOffset < maxScroll) {
			scrollOffset++;
		}
	}

	/**
	 * Scroll chat history down (view newer messages)
	 */
	public void scrollDown() {
		if (!open)
			return;

		if (scrollOffset > 0) {
			scrollOffset--;
		}
	}

	/**
	 * Reset scroll to bottom (most recent messages)
	 */
	public void resetScroll() {
		scrollOffset = 0;
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
		// Auto-scroll to bottom when new message arrives (if not manually scrolled up)
		if (scrollOffset == 0) {
			resetScroll();
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
		// Calculate visible range based on scroll offset
		int endIndex = messages.size() - scrollOffset;
		int startIndex = Math.max(0, endIndex - VISIBLE_MESSAGES);
		for (int i = startIndex; i < endIndex; i++) {
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

			// Draw selection highlight if active
			if (hasSelection()) {
				String prefix = "> " + currentInput.substring(0, selectionStart);
				String selected = currentInput.substring(selectionStart, selectionEnd);
				int prefixWidth = g.getStringWidth(prefix);
				int selectionWidth = g.getStringWidth(selected);

				g.setColor(new Color(50, 100, 200, 150)); // Blue highlight
				g.fillRect(8 + prefixWidth, y + 2, selectionWidth, 14);
			}

			// Input text with cursor at position (CRITICAL FIX: Show cursor at
			// cursorPosition + selection)
			g.setColor(Color.white);
			String beforeCursor = currentInput.substring(0, cursorPosition);
			String afterCursor = currentInput.substring(cursorPosition);
			String displayText = "> " + beforeCursor + "|" + afterCursor;
			g.drawString(displayText, 8, y + 12);
		}
	}
}
