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

package mc.sayda.mcraze.ui.layout;

/**
 * Layout parameters for UI components.
 * Defines how a component is positioned and spaced on screen.
 *
 * Uses fluent API for easy configuration:
 * <pre>
 * LayoutParams params = new LayoutParams()
 *     .setAnchor(Anchor.CENTER)
 *     .setMargin(10)
 *     .setOffset(0, -20);
 * </pre>
 */
public class LayoutParams {
	/**
	 * Anchor point for positioning
	 */
	private Anchor anchor = Anchor.TOP_LEFT;

	/**
	 * X offset from anchor point (in pixels)
	 */
	private int offsetX = 0;

	/**
	 * Y offset from anchor point (in pixels)
	 */
	private int offsetY = 0;

	/**
	 * Margin from top edge (in pixels)
	 */
	private int marginTop = 0;

	/**
	 * Margin from right edge (in pixels)
	 */
	private int marginRight = 0;

	/**
	 * Margin from bottom edge (in pixels)
	 */
	private int marginBottom = 0;

	/**
	 * Margin from left edge (in pixels)
	 */
	private int marginLeft = 0;

	/**
	 * Create default layout params (top-left anchor, no margins)
	 */
	public LayoutParams() {
	}

	/**
	 * Create layout params with specific anchor
	 */
	public LayoutParams(Anchor anchor) {
		this.anchor = anchor;
	}

	// Getters
	public Anchor getAnchor() {
		return anchor;
	}

	public int getOffsetX() {
		return offsetX;
	}

	public int getOffsetY() {
		return offsetY;
	}

	public int getMarginTop() {
		return marginTop;
	}

	public int getMarginRight() {
		return marginRight;
	}

	public int getMarginBottom() {
		return marginBottom;
	}

	public int getMarginLeft() {
		return marginLeft;
	}

	// Fluent setters (return this for chaining)

	/**
	 * Set anchor point
	 */
	public LayoutParams setAnchor(Anchor anchor) {
		this.anchor = anchor;
		return this;
	}

	/**
	 * Set X and Y offset from anchor
	 */
	public LayoutParams setOffset(int offsetX, int offsetY) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		return this;
	}

	/**
	 * Set margin (same for all sides)
	 */
	public LayoutParams setMargin(int margin) {
		this.marginTop = margin;
		this.marginRight = margin;
		this.marginBottom = margin;
		this.marginLeft = margin;
		return this;
	}

	/**
	 * Set margin (vertical and horizontal)
	 */
	public LayoutParams setMargin(int vertical, int horizontal) {
		this.marginTop = vertical;
		this.marginBottom = vertical;
		this.marginLeft = horizontal;
		this.marginRight = horizontal;
		return this;
	}

	/**
	 * Set margin (individual sides)
	 */
	public LayoutParams setMargin(int top, int right, int bottom, int left) {
		this.marginTop = top;
		this.marginRight = right;
		this.marginBottom = bottom;
		this.marginLeft = left;
		return this;
	}

	/**
	 * Copy constructor
	 */
	public LayoutParams copy() {
		LayoutParams copy = new LayoutParams(this.anchor);
		copy.offsetX = this.offsetX;
		copy.offsetY = this.offsetY;
		copy.marginTop = this.marginTop;
		copy.marginRight = this.marginRight;
		copy.marginBottom = this.marginBottom;
		copy.marginLeft = this.marginLeft;
		return copy;
	}
}
