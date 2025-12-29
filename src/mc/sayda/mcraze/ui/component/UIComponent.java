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

package mc.sayda.mcraze.ui.component;

import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.ui.layout.Anchor;
import mc.sayda.mcraze.ui.layout.LayoutParams;

/**
 * Base interface for all UI components in the declarative UI system.
 *
 * Components are responsible for:
 * - Rendering themselves (draw)
 * - Calculating their bounds (updateLayout)
 * - Handling input events (onMouseClick, onMouseMove)
 *
 * Layout is handled automatically based on anchor and layout parameters.
 *
 * NOTE: UI components are CLIENT-SIDE ONLY for rendering.
 * All state changes must go through packets to the server.
 */
public interface UIComponent {
	/**
	 * Update the component's layout based on screen dimensions.
	 * Called when screen size changes or layout params change.
	 *
	 * @param screenWidth Screen width in pixels
	 * @param screenHeight Screen height in pixels
	 */
	void updateLayout(int screenWidth, int screenHeight);

	/**
	 * Render the component.
	 *
	 * @param g Graphics handler for rendering
	 */
	void draw(GraphicsHandler g);

	/**
	 * Handle mouse click event.
	 * Returns true if event was consumed (stop propagation).
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @param leftClick True if left click, false if right click
	 * @return True if event was handled
	 */
	boolean onMouseClick(int mouseX, int mouseY, boolean leftClick);

	/**
	 * Handle mouse move event.
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 */
	void onMouseMove(int mouseX, int mouseY);

	/**
	 * Check if point is within component bounds.
	 *
	 * @param x X position
	 * @param y Y position
	 * @return True if point is inside component
	 */
	boolean contains(int x, int y);

	/**
	 * Get component's X position (top-left corner).
	 */
	int getX();

	/**
	 * Get component's Y position (top-left corner).
	 */
	int getY();

	/**
	 * Get component's width in pixels.
	 */
	int getWidth();

	/**
	 * Get component's height in pixels.
	 */
	int getHeight();

	/**
	 * Get component's layout parameters.
	 */
	LayoutParams getLayoutParams();

	/**
	 * Get component's visibility.
	 */
	boolean isVisible();

	/**
	 * Set component's visibility.
	 */
	void setVisible(boolean visible);
}
