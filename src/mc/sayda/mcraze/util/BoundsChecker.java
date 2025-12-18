/*
 * Copyright 2012 Jonathan Leahey
 *
 * This file is part of Minicraft
 *
 * Minicraft is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Minicraft is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Minicraft. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.util;

/**
 * BoundsChecker provides utility methods for checking if coordinates are within bounds.
 * This reduces code duplication throughout the codebase where boundary checking is needed.
 */
public class BoundsChecker {

	/**
	 * Check if a coordinate is within bounds
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param width Maximum width (exclusive)
	 * @param height Maximum height (exclusive)
	 * @return true if within bounds, false otherwise
	 */
	public static boolean isInBounds(int x, int y, int width, int height) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}

	/**
	 * Check if an X coordinate is within bounds
	 * @param x X coordinate
	 * @param width Maximum width (exclusive)
	 * @return true if within bounds, false otherwise
	 */
	public static boolean isXInBounds(int x, int width) {
		return x >= 0 && x < width;
	}

	/**
	 * Check if a Y coordinate is within bounds
	 * @param y Y coordinate
	 * @param height Maximum height (exclusive)
	 * @return true if within bounds, false otherwise
	 */
	public static boolean isYInBounds(int y, int height) {
		return y >= 0 && y < height;
	}
}
