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

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;

/**
 * Simple checkbox UI component with label
 */
public class Checkbox {
	private final String id;
	private String label;
	private int x;
	private int y;
	private boolean checked;
	private boolean hovered;
	private final int size = 16;  // Checkbox box size
	private final boolean centered;  // Whether to center horizontally
	private int cachedLabelWidth = 0;  // Cached actual label width from font metrics

	/**
	 * Create a checkbox
	 * @param id Unique identifier
	 * @param label Text label displayed next to checkbox
	 * @param y Y position
	 * @param centered Whether to center horizontally
	 */
	public Checkbox(String id, String label, int y, boolean centered) {
		this.id = id;
		this.label = label;
		this.x = 0;  // Will be calculated during draw/update
		this.y = y;
		this.checked = false;
		this.hovered = false;
		this.centered = centered;
	}

	/**
	 * Get checkbox ID
	 */
	public String getId() {
		return id;
	}

	/**
	 * Check if checkbox is checked
	 */
	public boolean isChecked() {
		return checked;
	}

	/**
	 * Set checkbox checked state
	 */
	public void setChecked(boolean checked) {
		this.checked = checked;
	}

	/**
	 * Toggle checkbox state
	 */
	public void toggle() {
		this.checked = !this.checked;
	}

	/**
	 * Update position based on screen width (for centering)
	 */
	public void updatePosition(int screenWidth) {
		if (centered) {
			// Use cached width from draw(), or estimate if not yet available
			int labelWidth = (cachedLabelWidth > 0) ? cachedLabelWidth : label.length() * 7;
			int totalWidth = size + 5 + labelWidth;
			this.x = screenWidth / 2 - totalWidth / 2;
		}
	}

	/**
	 * Update hover state
	 */
	public void updateHover(int mouseX, int mouseY) {
		// Use cached width from draw(), or estimate if not yet available
		int labelWidth = (cachedLabelWidth > 0) ? cachedLabelWidth : label.length() * 7;
		int totalWidth = size + 5 + labelWidth;
		this.hovered = mouseX >= x && mouseX <= x + totalWidth &&
		               mouseY >= y && mouseY <= y + size;
	}

	/**
	 * Handle mouse click
	 * @return true if click was handled
	 */
	public boolean handleClick(int mouseX, int mouseY) {
		// Use cached width from draw(), or estimate if not yet available
		int labelWidth = (cachedLabelWidth > 0) ? cachedLabelWidth : label.length() * 7;
		int totalWidth = size + 5 + labelWidth;

		if (mouseX >= x && mouseX <= x + totalWidth &&
		    mouseY >= y && mouseY <= y + size) {
			toggle();
			return true;
		}
		return false;
	}

	/**
	 * Draw the checkbox
	 */
	public void draw(GraphicsHandler g) {
		// Cache actual label width for accurate positioning and hit detection
		cachedLabelWidth = g.getStringWidth(label);

		// Draw checkbox box
		g.setColor(hovered ? Color.LIGHT_GRAY : Color.white);
		g.fillRect(x, y, size, size);

		// Draw border
		g.setColor(Color.darkGray);
		g.drawRect(x, y, size, size);

		// Draw checkmark if checked
		if (checked) {
			g.setColor(new Color(50, 200, 50, 255));  // Green checkmark
			// Draw a simple filled rectangle checkmark
			g.fillRect(x + 4, y + 4, size - 8, size - 8);
		}

		// Draw label
		g.setColor(Color.white);
		g.drawString(label, x + size + 5, y + (size / 2) - 4);
	}

	/**
	 * Get Y position
	 */
	public int getY() {
		return y;
	}

	/**
	 * Set label text
	 */
	public void setLabel(String label) {
		this.label = label;
	}
}
