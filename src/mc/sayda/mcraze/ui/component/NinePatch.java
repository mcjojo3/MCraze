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
import mc.sayda.mcraze.Sprite;

/**
 * Utility for rendering 9-patch sprites (expandable panel backgrounds).
 *
 * A 9-patch sprite is divided into 9 regions:
 * ┌─────┬─────┬─────┐
 * │ TL  │ Top │ TR  │  (Corners stay fixed size)
 * ├─────┼─────┼─────┤
 * │Left │Cen  │Right│  (Edges and center stretch/tile)
 * ├─────┼─────┼─────┤
 * │ BL  │Bot  │ BR  │  (Corners stay fixed size)
 * └─────┴─────┴─────┘
 *
 * For MCraze, we use 16x16 sprites with 4px corners and 8px edges.
 */
public class NinePatch {
	private final Sprite sprite;
	private final int cornerSize;
	private final int edgeSize;

	/**
	 * Create a 9-patch renderer for a sprite.
	 *
	 * @param sprite The sprite to use (should be square, e.g. 16x16)
	 * @param cornerSize Size of corners in pixels (e.g. 4)
	 */
	public NinePatch(Sprite sprite, int cornerSize) {
		this.sprite = sprite;
		this.cornerSize = cornerSize;
		this.edgeSize = sprite.getWidth() - (cornerSize * 2);
	}

	/**
	 * Draw the 9-patch sprite at a specific position and size.
	 *
	 * NOTE: Currently uses simple stretching. Full 9-patch implementation
	 * (with fixed corners and tiled edges) requires adding sub-region support
	 * to GraphicsHandler. This is a v1 implementation that will work for now.
	 *
	 * @param g Graphics handler
	 * @param x X position (top-left corner)
	 * @param y Y position (top-left corner)
	 * @param width Desired width
	 * @param height Desired height
	 */
	public void draw(GraphicsHandler g, int x, int y, int width, int height) {
		// For now, just stretch the entire sprite
		// TODO: Implement proper 9-patch rendering when GraphicsHandler
		// supports drawing sub-regions of sprites
		sprite.draw(g, x, y, width, height);
	}
}
