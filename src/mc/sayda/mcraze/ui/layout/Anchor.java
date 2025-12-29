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
 * Defines anchor points for UI component positioning.
 * Similar to Unity's Anchor system or CSS positioning.
 */
public enum Anchor {
	/**
	 * Anchor to top-left corner of screen
	 */
	TOP_LEFT,

	/**
	 * Anchor to top-center of screen
	 */
	TOP_CENTER,

	/**
	 * Anchor to top-right corner of screen
	 */
	TOP_RIGHT,

	/**
	 * Anchor to center-left of screen
	 */
	CENTER_LEFT,

	/**
	 * Anchor to center of screen (both X and Y)
	 */
	CENTER,

	/**
	 * Anchor to center-right of screen
	 */
	CENTER_RIGHT,

	/**
	 * Anchor to bottom-left corner of screen
	 */
	BOTTOM_LEFT,

	/**
	 * Anchor to bottom-center of screen
	 */
	BOTTOM_CENTER,

	/**
	 * Anchor to bottom-right corner of screen
	 */
	BOTTOM_RIGHT
}
