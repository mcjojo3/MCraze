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
 * Layout engine for calculating component positions from anchors.
 * Handles all the math so you don't have to!
 *
 * No more manual centering calculations like:
 * <pre>
 * x = screenWidth / 2 - panelWidth / 2;  // Old way
 * </pre>
 *
 * Now just use:
 * <pre>
 * int x = LayoutEngine.calculateX(screenWidth, width, layoutParams);  // New way
 * </pre>
 */
public class LayoutEngine {
	/**
	 * Calculate X position for a component based on layout params.
	 *
	 * @param screenWidth Screen width in pixels
	 * @param componentWidth Component width in pixels
	 * @param params Layout parameters
	 * @return Calculated X position (top-left corner)
	 */
	public static int calculateX(int screenWidth, int componentWidth, LayoutParams params) {
		int baseX;
		Anchor anchor = params.getAnchor();

		// Calculate base X position from anchor
		switch (anchor) {
			case TOP_LEFT:
			case CENTER_LEFT:
			case BOTTOM_LEFT:
				baseX = params.getMarginLeft();
				break;

			case TOP_CENTER:
			case CENTER:
			case BOTTOM_CENTER:
				baseX = (screenWidth - componentWidth) / 2;
				break;

			case TOP_RIGHT:
			case CENTER_RIGHT:
			case BOTTOM_RIGHT:
				baseX = screenWidth - componentWidth - params.getMarginRight();
				break;

			default:
				baseX = 0;
				break;
		}

		// Apply offset
		return baseX + params.getOffsetX();
	}

	/**
	 * Calculate Y position for a component based on layout params.
	 *
	 * @param screenHeight Screen height in pixels
	 * @param componentHeight Component height in pixels
	 * @param params Layout parameters
	 * @return Calculated Y position (top-left corner)
	 */
	public static int calculateY(int screenHeight, int componentHeight, LayoutParams params) {
		int baseY;
		Anchor anchor = params.getAnchor();

		// Calculate base Y position from anchor
		switch (anchor) {
			case TOP_LEFT:
			case TOP_CENTER:
			case TOP_RIGHT:
				baseY = params.getMarginTop();
				break;

			case CENTER_LEFT:
			case CENTER:
			case CENTER_RIGHT:
				baseY = (screenHeight - componentHeight) / 2;
				break;

			case BOTTOM_LEFT:
			case BOTTOM_CENTER:
			case BOTTOM_RIGHT:
				baseY = screenHeight - componentHeight - params.getMarginBottom();
				break;

			default:
				baseY = 0;
				break;
		}

		// Apply offset
		return baseY + params.getOffsetY();
	}

	/**
	 * Calculate position for a component (convenience method).
	 *
	 * @param screenWidth Screen width in pixels
	 * @param screenHeight Screen height in pixels
	 * @param componentWidth Component width in pixels
	 * @param componentHeight Component height in pixels
	 * @param params Layout parameters
	 * @return Array [x, y] with calculated position
	 */
	public static int[] calculatePosition(int screenWidth, int screenHeight,
	                                      int componentWidth, int componentHeight,
	                                      LayoutParams params) {
		return new int[]{
			calculateX(screenWidth, componentWidth, params),
			calculateY(screenHeight, componentHeight, params)
		};
	}
}
