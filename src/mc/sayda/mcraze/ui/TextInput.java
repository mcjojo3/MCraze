package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.IOException;

/**
 * Enhanced text input field with cursor navigation, selection, and copy/paste
 */
public class TextInput {
	private final String id;
	private int x;
	private final int y;
	private final int width;
	private final int height;
	private final boolean centered;

	private StringBuilder text;
	private final int maxLength;
	private boolean focused;
	private long lastBlinkTime;
	private boolean cursorVisible;
	private boolean passwordMode;
	private String placeholder;

	// Enhanced editing features
	private int cursorPosition;  // Current cursor position (0 to text.length())
	private int selectionStart;  // Selection start (-1 if no selection)
	private int selectionEnd;    // Selection end (-1 if no selection)

	/**
	 * Create a centered text input field
	 */
	public TextInput(String id, int y, int width, int height, int maxLength) {
		this.id = id;
		this.x = 0;  // Will be calculated during draw
		this.y = y;
		this.width = width;
		this.height = height;
		this.maxLength = maxLength;
		this.centered = true;
		this.text = new StringBuilder();
		this.focused = false;
		this.lastBlinkTime = System.currentTimeMillis();
		this.cursorVisible = true;
		this.passwordMode = false;
		this.placeholder = "";
		this.cursorPosition = 0;
		this.selectionStart = -1;
		this.selectionEnd = -1;
	}

	/**
	 * Update position based on screen width (for centered input)
	 */
	public void updatePosition(int screenWidth) {
		if (centered) {
			this.x = (screenWidth - width) / 2;
		}
	}

	/**
	 * Check if mouse is over this input field
	 */
	public boolean contains(int mouseX, int mouseY) {
		return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	/**
	 * Handle click event - position cursor at click location
	 */
	public void handleClick(int mouseX, int mouseY) {
		boolean wasFocused = focused;
		focused = contains(mouseX, mouseY);

		if (focused && wasFocused) {
			// Click inside text to position cursor
			int relativeX = mouseX - (x + 8);
			int charWidth = 7;
			int clickedPosition = Math.max(0, Math.min(text.length(), relativeX / charWidth));
			cursorPosition = clickedPosition;
			clearSelection();
		} else if (focused) {
			// Just gained focus - move cursor to end
			cursorPosition = text.length();
			clearSelection();
		}
	}

	/**
	 * Handle key typed event (printable characters)
	 */
	public void handleKeyTyped(char c) {
		if (!focused) return;

		if (c == '\b') {
			// Backspace - handled in handleKeyPressed for better control
			return;
		} else if (c == '\n' || c == '\r') {
			// Enter - do nothing, let the form handle it
		} else if (c == 127) {
			// Delete - handled in handleKeyPressed
			return;
		} else if (isPrintable(c)) {
			insertText(String.valueOf(c));
		}
	}

	/**
	 * Handle special key pressed events (arrows, delete, ctrl+c/v, etc.)
	 * Call this from your key event handler with the key code
	 */
	public void handleKeyPressed(int keyCode, boolean shiftPressed, boolean ctrlPressed) {
		if (!focused) return;

		// Reset cursor blink on any key
		lastBlinkTime = System.currentTimeMillis();
		cursorVisible = true;

		// Handle Ctrl combinations first
		if (ctrlPressed) {
			switch (keyCode) {
				case 65: // Ctrl+A - Select All
					selectAll();
					return;
				case 67: // Ctrl+C - Copy
					copySelection();
					return;
				case 86: // Ctrl+V - Paste
					paste();
					return;
				case 88: // Ctrl+X - Cut
					cutSelection();
					return;
			}
		}

		// Handle navigation and editing keys
		switch (keyCode) {
			case 8:   // Backspace
				if (hasSelection()) {
					deleteSelection();
				} else if (cursorPosition > 0) {
					text.deleteCharAt(cursorPosition - 1);
					cursorPosition--;
				}
				break;

			case 127: // Delete
				if (hasSelection()) {
					deleteSelection();
				} else if (cursorPosition < text.length()) {
					text.deleteCharAt(cursorPosition);
				}
				break;

			case 37:  // Left arrow
				if (shiftPressed) {
					moveCursorWithSelection(-1);
				} else {
					if (hasSelection() && !shiftPressed) {
						cursorPosition = Math.min(selectionStart, selectionEnd);
						clearSelection();
					} else {
						moveCursor(-1);
					}
				}
				break;

			case 39:  // Right arrow
				if (shiftPressed) {
					moveCursorWithSelection(1);
				} else {
					if (hasSelection() && !shiftPressed) {
						cursorPosition = Math.max(selectionStart, selectionEnd);
						clearSelection();
					} else {
						moveCursor(1);
					}
				}
				break;

			case 36:  // Home
				if (shiftPressed) {
					setSelection(0, cursorPosition);
				} else {
					clearSelection();
				}
				cursorPosition = 0;
				break;

			case 35:  // End
				if (shiftPressed) {
					setSelection(cursorPosition, text.length());
				} else {
					clearSelection();
				}
				cursorPosition = text.length();
				break;
		}
	}

	/**
	 * Insert text at cursor position (deletes selection if active)
	 */
	private void insertText(String str) {
		if (hasSelection()) {
			deleteSelection();
		}

		// Check length limit
		if (text.length() + str.length() <= maxLength) {
			text.insert(cursorPosition, str);
			cursorPosition += str.length();
		}
	}

	/**
	 * Move cursor by offset
	 */
	private void moveCursor(int offset) {
		cursorPosition = Math.max(0, Math.min(text.length(), cursorPosition + offset));
		clearSelection();
	}

	/**
	 * Move cursor with selection (shift+arrow)
	 */
	private void moveCursorWithSelection(int offset) {
		int newPos = Math.max(0, Math.min(text.length(), cursorPosition + offset));

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
		if (!hasSelection()) return;

		text.delete(selectionStart, selectionEnd);
		cursorPosition = selectionStart;
		clearSelection();
	}

	/**
	 * Get selected text
	 */
	private String getSelectedText() {
		if (!hasSelection()) return "";
		return text.substring(selectionStart, selectionEnd);
	}

	/**
	 * Select all text
	 */
	private void selectAll() {
		if (text.length() > 0) {
			selectionStart = 0;
			selectionEnd = text.length();
			cursorPosition = text.length();
		}
	}

	/**
	 * Copy selection to clipboard
	 */
	private void copySelection() {
		if (!hasSelection()) return;

		String selectedText = getSelectedText();
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(new StringSelection(selectedText), null);
		} catch (Exception e) {
			// Clipboard access might fail in some environments
			System.err.println("Failed to copy to clipboard: " + e.getMessage());
		}
	}

	/**
	 * Cut selection to clipboard
	 */
	private void cutSelection() {
		if (!hasSelection()) return;

		copySelection();
		deleteSelection();
	}

	/**
	 * Paste from clipboard
	 */
	private void paste() {
		try {
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			Transferable contents = clipboard.getContents(null);

			if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
				String pastedText = (String) contents.getTransferData(DataFlavor.stringFlavor);

				// Filter to only printable characters
				StringBuilder filtered = new StringBuilder();
				for (char c : pastedText.toCharArray()) {
					if (isPrintable(c) && c != '\n' && c != '\r' && c != '\t') {
						filtered.append(c);
					}
				}

				insertText(filtered.toString());
			}
		} catch (Exception e) {
			// Clipboard access might fail in some environments
			System.err.println("Failed to paste from clipboard: " + e.getMessage());
		}
	}

	/**
	 * Check if character is printable (for IP:PORT input)
	 */
	private boolean isPrintable(char c) {
		// Allow digits, dots, colons, and basic ASCII characters
		return c >= 32 && c <= 126;
	}

	/**
	 * Get the current text
	 */
	public String getText() {
		return text.toString();
	}

	/**
	 * Set the text
	 */
	public void setText(String text) {
		this.text = new StringBuilder(text.substring(0, Math.min(text.length(), maxLength)));
		this.cursorPosition = this.text.length();
		clearSelection();
	}

	/**
	 * Clear the text
	 */
	public void clear() {
		text.setLength(0);
		cursorPosition = 0;
		clearSelection();
	}

	/**
	 * Check if this input is focused
	 */
	public boolean isFocused() {
		return focused;
	}

	/**
	 * Set focus state
	 */
	public void setFocused(boolean focused) {
		this.focused = focused;
		if (!focused) {
			clearSelection();
		}
	}

	/**
	 * Enable password mode (display asterisks instead of text)
	 */
	public void setPasswordMode(boolean passwordMode) {
		this.passwordMode = passwordMode;
	}

	/**
	 * Set placeholder text (shown when empty and unfocused)
	 */
	public void setPlaceholder(String placeholder) {
		this.placeholder = placeholder;
	}

	/**
	 * Draw the text input
	 */
	public void draw(GraphicsHandler g) {
		// Update cursor blink
		long currentTime = System.currentTimeMillis();
		if (currentTime - lastBlinkTime > 500) {
			cursorVisible = !cursorVisible;
			lastBlinkTime = currentTime;
		}

		// Choose colors based on focus state
		Color bgColor = focused ? new Color(60, 60, 60, 200) : new Color(40, 40, 40, 200);
		Color borderColor = focused ? new Color(255, 255, 255, 255) : new Color(120, 120, 120, 255);
		Color textColor = Color.white;
		Color selectionColor = new Color(50, 100, 200, 150);

		// Draw background
		g.setColor(bgColor);
		g.fillRect(x, y, width, height);

		// Draw border
		g.setColor(borderColor);
		g.drawRect(x, y, width, height);
		if (focused) {
			g.drawRect(x + 1, y + 1, width - 2, height - 2);
		}

		// Draw text
		String displayText = text.toString();
		if (displayText.isEmpty() && !focused) {
			// Show placeholder
			if (!placeholder.isEmpty()) {
				g.setColor(new Color(150, 150, 150, 255));
				g.drawString(placeholder, x + 8, y + (height / 2) + 2);
			}
		} else {
			// Convert to asterisks if password mode
			if (passwordMode) {
				StringBuilder masked = new StringBuilder();
				for (int i = 0; i < displayText.length(); i++) {
					masked.append('*');
				}
				displayText = masked.toString();
			}

			// Calculate scroll offset to keep cursor visible
			int charWidth = 7;
			int visibleChars = (width - 16) / charWidth;
			int scrollOffset = 0;

			if (cursorPosition > visibleChars) {
				scrollOffset = cursorPosition - visibleChars + 2;
			}

			int displayX = x + 8 - (scrollOffset * charWidth);

			// Draw selection highlight
			if (hasSelection() && focused) {
				int selStart = Math.max(0, selectionStart - scrollOffset);
				int selEnd = Math.min(displayText.length() - scrollOffset, selectionEnd - scrollOffset);
				if (selEnd > selStart && selStart < visibleChars) {
					g.setColor(selectionColor);
					g.fillRect(displayX + selStart * charWidth, y + 8,
					          (selEnd - selStart) * charWidth, height - 16);
				}
			}

			// Draw text
			g.setColor(textColor);
			String visibleText = displayText.substring(Math.min(scrollOffset, displayText.length()));
			g.drawString(visibleText, displayX, y + (height / 2) + 2);

			// Draw cursor when focused
			if (focused && cursorVisible) {
				int cursorX = displayX + (cursorPosition - scrollOffset) * charWidth;
				if (cursorX >= x + 8 && cursorX <= x + width - 8) {
					g.fillRect(cursorX, y + 8, 2, height - 16);
				}
			}
		}
	}

	public String getId() {
		return id;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}
}
