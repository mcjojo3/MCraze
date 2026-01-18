package mc.sayda.mcraze.ui.component;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Sprite;

/**
 * Represents a clickable button with hover effects.
 * Supports both sprite-based buttons and text buttons with 9-patch rendering.
 */
public class Button {
	private final String id;
	private int x; // Made non-final to allow dynamic positioning
	private final int y;
	private final int width;
	private final int height;
	private final boolean centered; // Whether to center horizontally
	private int offsetX = 0; // Horizontal offset from center position

	// Sprite-based button
	private final Sprite spriteUp;
	private final Sprite spriteDown;

	// Text-based button (9-patch style)
	private String text; // Removed final to allow text updates
	private final boolean useNinePatch;

	private boolean hovered;
	private boolean enabled;
	private Runnable onClick;

	/**
	 * Create a sprite-based button
	 */
	public Button(String id, Sprite spriteUp, Sprite spriteDown, int x, int y, int width, int height) {
		this(id, spriteUp, spriteDown, x, y, width, height, false);
	}

	/**
	 * Create a sprite-based button with centering option
	 */
	public Button(String id, Sprite spriteUp, Sprite spriteDown, int x, int y, int width, int height,
			boolean centered) {
		this.id = id;
		this.spriteUp = spriteUp;
		this.spriteDown = spriteDown;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.centered = centered;
		this.text = null;
		this.useNinePatch = false;
		this.enabled = true;
		this.hovered = false;
	}

	/**
	 * Create a text-based button with 9-patch rendering (Minecraft-style)
	 * Automatically centered horizontally
	 */
	/**
	 * Create a text-based button with explicit position and centering option
	 */
	public Button(String id, String text, int x, int y, int width, int height, boolean centered) {
		this.id = id;
		this.text = text;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.centered = centered;
		this.spriteUp = null;
		this.spriteDown = null;
		this.useNinePatch = true;
		this.enabled = true;
		this.hovered = false;
	}

	/**
	 * Create a text-based button with explicit position (non-centered)
	 */
	public Button(String id, String text, int x, int y, int width, int height) {
		this(id, text, x, y, width, height, false);
	}

	/**
	 * Create a text-based button with 9-patch rendering (Minecraft-style)
	 * Automatically centered horizontally
	 */
	public Button(String id, String text, int y, int width, int height) {
		this(id, text, 0, y, width, height, true);
	}

	/**
	 * Set the click handler for this button
	 */
	public Button setOnClick(Runnable onClick) {
		this.onClick = onClick;
		return this;
	}

	/**
	 * Enable or disable this button
	 */
	public Button setEnabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	/**
	 * Update the button text (for text-based buttons only)
	 */
	public Button setText(String text) {
		if (useNinePatch) {
			this.text = text;
		}
		return this;
	}

	/**
	 * Set horizontal offset from center position
	 */
	public Button setOffsetX(int offsetX) {
		this.offsetX = offsetX;
		return this;
	}

	/**
	 * Update position based on screen width (for centered buttons)
	 */
	public void updatePosition(int screenWidth) {
		if (centered) {
			this.x = (screenWidth - width) / 2 + offsetX;
		}
	}

	/**
	 * Update hover state based on mouse position
	 */
	public void updateHover(int mouseX, int mouseY) {
		if (!enabled) {
			hovered = false;
			return;
		}
		hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
	}

	/**
	 * Handle click event
	 * 
	 * @return true if button was clicked
	 */
	public boolean handleClick(int mouseX, int mouseY) {
		if (!enabled) {
			return false;
		}

		if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
			if (onClick != null) {
				onClick.run();
			}
			return true;
		}
		return false;
	}

	/**
	 * Draw the button
	 */
	public void draw(GraphicsHandler g) {
		if (useNinePatch) {
			drawNinePatchButton(g);
		} else {
			drawSpriteButton(g);
		}
	}

	/**
	 * Draw sprite-based button
	 */
	private void drawSpriteButton(GraphicsHandler g) {
		if (!enabled) {
			spriteUp.draw(g, x, y, width, height);
			return;
		}

		if (hovered && spriteDown != null) {
			spriteDown.draw(g, x, y, width, height);
		} else {
			spriteUp.draw(g, x, y, width, height);
		}
	}

	/**
	 * Draw text button with 9-patch style (Minecraft-like)
	 * This creates a button with stretched edges and centered text
	 */
	private void drawNinePatchButton(GraphicsHandler g) {
		// Choose colors based on state
		Color bgColor, borderColor, textColor;

		if (!enabled) {
			bgColor = new Color(100, 100, 100, 200);
			borderColor = new Color(50, 50, 50, 255);
			textColor = new Color(160, 160, 160, 255);
		} else if (hovered) {
			bgColor = new Color(120, 180, 255, 200); // Light blue hover
			borderColor = new Color(255, 255, 255, 255);
			textColor = Color.white;
		} else {
			bgColor = new Color(80, 80, 80, 200); // Gray normal
			borderColor = new Color(160, 160, 160, 255);
			textColor = Color.white;
		}

		// Draw button background
		g.setColor(bgColor);
		g.fillRect(x, y, width, height);

		// Draw border (simple rectangle for now, can be enhanced with 9-patch texture)
		g.setColor(borderColor);
		g.drawRect(x, y, width, height);
		g.drawRect(x + 1, y + 1, width - 2, height - 2);

		// Draw highlight on top edge when hovered
		if (hovered && enabled) {
			g.setColor(new Color(255, 255, 255, 100));
			g.fillRect(x + 2, y + 2, width - 4, 2);
		}

		// Draw text centered
		if (text != null) {
			g.setColor(textColor);
			// Use actual font metrics for accurate centering
			int textWidth = g.getStringWidth(text);
			int textX = x + (width - textWidth) / 2;
			int textY = y + (height / 2) + 2; // Adjust vertical centering
			g.drawString(text, textX, textY);
		}
	}

	public String getId() {
		return id;
	}

	public boolean isHovered() {
		return hovered;
	}

	public boolean isEnabled() {
		return enabled;
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
