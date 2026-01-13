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

package mc.sayda.mcraze.ui.component;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Tooltip component for showing item information on hover.
 *
 * Features:
 * - Multiple lines of text
 * - Semi-transparent background
 * - Following mouse cursor or anchors to position
 * - Automatic sizing based on content
 */
public class Tooltip {
	private List<String> lines = new ArrayList<>();
	private int x, y;
	private int width, height;
	private boolean visible = false;

	// Background
	private Sprite backgroundSprite;
	private Color backgroundColor = new Color(0, 0, 0, 200); // Semi-transparent black
	private Color borderColor = new Color(100, 100, 255, 200); // Light blue border
	private Color textColor = Color.white;

	private static final int PADDING = 5;
	private static final int LINE_HEIGHT = 12;

	// Hover delay
	private long hoverStartTime = 0;
	private static final long HOVER_DELAY_MS = 500; // 500ms delay before showing
	private boolean delayElapsed = false;

	/**
	 * Create an empty tooltip.
	 */
	public Tooltip() {
	}

	/**
	 * Add a line of text to the tooltip.
	 *
	 * @param text Text to add
	 * @return This tooltip for chaining
	 */
	public Tooltip addLine(String text) {
		lines.add(text);
		calculateDimensions();
		return this;
	}

	/**
	 * Clear all lines.
	 */
	public Tooltip clearLines() {
		lines.clear();
		calculateDimensions();
		return this;
	}

	/**
	 * Set background sprite.
	 *
	 * @param sprite Background sprite
	 * @return This tooltip for chaining
	 */
	public Tooltip setBackground(Sprite sprite) {
		this.backgroundSprite = sprite;
		return this;
	}

	/**
	 * Set colors.
	 *
	 * @param background Background color
	 * @param border     Border color
	 * @param text       Text color
	 * @return This tooltip for chaining
	 */
	public Tooltip setColors(Color background, Color border, Color text) {
		this.backgroundColor = background;
		this.borderColor = border;
		this.textColor = text;
		return this;
	}

	/**
	 * Show tooltip at mouse position.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @return This tooltip for chaining
	 */
	public Tooltip show(int mouseX, int mouseY) {
		// Start hover delay if not already started
		if (hoverStartTime == 0) {
			hoverStartTime = System.currentTimeMillis();
			delayElapsed = false;
		}

		// Check if delay has elapsed
		long elapsed = System.currentTimeMillis() - hoverStartTime;
		if (elapsed >= HOVER_DELAY_MS) {
			delayElapsed = true;
		}

		// Only show if delay elapsed
		if (delayElapsed && !lines.isEmpty()) {
			this.visible = true;
			// Position tooltip
			this.x = mouseX + 15;
			this.y = mouseY + 10;
		}

		return this;
	}

	/**
	 * Hide tooltip and reset hover delay.
	 */
	public Tooltip hide() {
		this.visible = false;
		this.hoverStartTime = 0;
		this.delayElapsed = false;
		return this;
	}

	/**
	 * Calculate dimensions from text content.
	 */
	private void calculateDimensions() {
		if (lines.isEmpty()) {
			width = 0;
			height = 0;
			return;
		}

		// Calculate max width from lines (estimate 6px per character)
		int maxWidth = 0;
		for (String line : lines) {
			int lineWidth = line.length() * 6;
			if (lineWidth > maxWidth) {
				maxWidth = lineWidth;
			}
		}

		// Add padding
		width = maxWidth + (PADDING * 2);
		height = (lines.size() * LINE_HEIGHT) + (PADDING * 2);
	}

	/**
	 * Update the component's layout based on screen dimensions.
	 *
	 * @param screenWidth  Screen width in pixels
	 * @param screenHeight Screen height in pixels
	 */
	public void updateLayout(int screenWidth, int screenHeight) {
		// Ensure tooltip stays on screen
		if (x + width > screenWidth) {
			x = screenWidth - width - 5;
		}
		if (y + height > screenHeight) {
			y = screenHeight - height - 5;
		}
		if (x < 0) {
			x = 5;
		}
		if (y < 0) {
			y = 5;
		}
	}

	/**
	 * Render the component.
	 *
	 * @param g Graphics handler for rendering
	 */
	public void draw(GraphicsHandler g) {
		if (!visible || lines.isEmpty()) {
			return;
		}

		// Calculate actual max width using font metrics
		int maxWidth = 0;
		for (String line : lines) {
			int lineWidth = g.getStringWidth(line);
			if (lineWidth > maxWidth) {
				maxWidth = lineWidth;
			}
		}
		width = maxWidth + (PADDING * 2);

		// Draw background
		if (backgroundSprite != null) {
			// Simple stretch render as current NinePatch was just a wrapper for this
			backgroundSprite.draw(g, x, y, width, height);
		} else {
			// Solid color background with border
			g.setColor(backgroundColor);
			g.fillRect(x, y, width, height);

			g.setColor(borderColor);
			g.drawRect(x, y, width, height);
		}

		// Draw text lines
		g.setColor(textColor);
		int textY = y + PADDING + LINE_HEIGHT - 2; // Align to baseline
		for (String line : lines) {
			g.drawString(line, x + PADDING, textY);
			textY += LINE_HEIGHT;
		}
	}

	/**
	 * Handle mouse move event.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 */
	public void onMouseMove(int mouseX, int mouseY) {
		// Update position to follow mouse
		if (visible) {
			this.x = mouseX + 15;
			this.y = mouseY + 35;
		}
	}

	/**
	 * Check if point is within component bounds.
	 *
	 * @param x X position
	 * @param y Y position
	 * @return True if point is inside component
	 */
	public boolean contains(int x, int y) {
		return x >= this.x && x < this.x + width &&
				y >= this.y && y < this.y + height;
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

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
		if (!visible) {
			hide();
		}
	}
}
