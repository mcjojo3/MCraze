package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;

/**
 * Text input field for entering text (e.g., IP addresses, player names)
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
	 * Handle click event
	 */
	public void handleClick(int mouseX, int mouseY) {
		focused = contains(mouseX, mouseY);
	}

	/**
	 * Handle key typed event
	 */
	public void handleKeyTyped(char c) {
		if (!focused) return;

		if (c == '\b') {
			// Backspace
			if (text.length() > 0) {
				text.deleteCharAt(text.length() - 1);
			}
		} else if (c == '\n' || c == '\r') {
			// Enter - do nothing, let the form handle it
		} else if (text.length() < maxLength && isPrintable(c)) {
			text.append(c);
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
	}

	/**
	 * Clear the text
	 */
	public void clear() {
		text.setLength(0);
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

			g.setColor(textColor);
			// Scroll text if too long
			int textWidth = displayText.length() * 7;
			int displayX = x + 8;
			if (textWidth > width - 16) {
				// Scroll to show end of text
				displayX = x + width - textWidth - 8;
			}
			g.drawString(displayText, displayX, y + (height / 2) + 2);

			// Draw cursor when focused
			if (focused && cursorVisible) {
				int cursorX = displayX + displayText.length() * 7;
				g.fillRect(cursorX, y + 8, 2, height - 16);
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
