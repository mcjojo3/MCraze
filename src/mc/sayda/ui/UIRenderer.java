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

package mc.sayda.ui;

import mc.sayda.Color;
import mc.sayda.GraphicsHandler;
import mc.sayda.Sprite;
import mc.sayda.SpriteStore;
import mc.sayda.entity.Player;
import mc.sayda.util.Int2;
import mc.sayda.util.StockMethods;
import mc.sayda.world.World;

/**
 * UIRenderer handles all UI rendering logic including:
 * - Health bar (hearts)
 * - Air bubbles (when underwater)
 * - Builder/miner icons (block placement/breaking indicators)
 * - Mouse cursor
 * - FPS counter
 * - Background tiles
 */
public class UIRenderer {

	private Sprite builderIcon;
	private Sprite minerIcon;
	private Sprite fullHeart;
	private Sprite halfHeart;
	private Sprite emptyHeart;
	private Sprite bubble;
	private Sprite emptyBubble;

	public UIRenderer() {
		loadSprites();
	}

	/**
	 * Load all UI sprites from the sprite store
	 */
	private void loadSprites() {
		final SpriteStore ss = SpriteStore.get();
		builderIcon = ss.getSprite("sprites/other/builder.png");
		minerIcon = ss.getSprite("sprites/other/miner.png");
		fullHeart = ss.getSprite("sprites/other/full_heart.png");
		halfHeart = ss.getSprite("sprites/other/half_heart.png");
		emptyHeart = ss.getSprite("sprites/other/empty_heart.png");
		bubble = ss.getSprite("sprites/other/bubble.png");
		// there's no empty bubble image, so we'll just use this for now
		emptyBubble = ss.getSprite("sprites/other/bubble_pop2.png");
	}

	/**
	 * Draw a sprite centered horizontally on the screen
	 * @param g Graphics handler
	 * @param s Sprite to draw
	 * @param top Y position (top of sprite)
	 * @param width Width to render sprite
	 * @param height Height to render sprite
	 */
	public void drawCenteredX(GraphicsHandler g, Sprite s, int top, int width, int height) {
		s.draw(g, g.getScreenWidth() / 2 - width / 2, top, width, height);
	}

	/**
	 * Draw the mouse cursor as a white circle with black center
	 * @param g Graphics handler
	 * @param pos Position to draw the cursor
	 */
	public void drawMouse(GraphicsHandler g, Int2 pos) {
		g.setColor(Color.white);
		g.fillOval(pos.x - 4, pos.y - 4, 8, 8);
		g.setColor(Color.black);
		g.fillOval(pos.x - 3, pos.y - 3, 6, 6);
	}

	/**
	 * Fill the entire screen with a tiled sprite background
	 * @param g Graphics handler
	 * @param sprite Sprite to use as tile
	 * @param tileSize Size of each tile
	 */
	public void drawTileBackground(GraphicsHandler g, Sprite sprite, int tileSize) {
		for (int i = 0; i <= GraphicsHandler.get().getScreenWidth() / tileSize; i++) {
			for (int j = 0; j <= GraphicsHandler.get().getScreenHeight() / tileSize; j++) {
				sprite.draw(g, i * tileSize, j * tileSize, tileSize, tileSize);
			}
		}
	}

	/**
	 * Draw the builder and miner icons at the player's hand positions
	 * @param g Graphics handler
	 * @param player Player entity
	 * @param cameraX Camera X position
	 * @param cameraY Camera Y position
	 * @param tileSize Size of tiles in pixels
	 */
	public void drawBuildMineIcons(GraphicsHandler g, Player player, float cameraX, float cameraY, int tileSize) {
		if (player.handTargetPos.x != -1) {
			Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, tileSize,
					tileSize, tileSize, player.handTargetPos.x, player.handTargetPos.y);
			// Show miner icon as the unified target indicator
			minerIcon.draw(g, pos.x, pos.y, tileSize, tileSize);
		}
	}

	/**
	 * Draw the health bar (hearts) at the bottom of the screen
	 * @param g Graphics handler
	 * @param player Player entity
	 * @param screenWidth Screen width in pixels
	 * @param screenHeight Screen height in pixels
	 */
	public void drawHealthBar(GraphicsHandler g, Player player, int screenWidth, int screenHeight) {
		int heartX = (screenWidth - 250) / 2;
		int heartY = screenHeight - 50;
		for (int heartIdx = 1; heartIdx <= 10; ++heartIdx) {
			int hpDiff = player.hitPoints - heartIdx * 10;
			if (hpDiff >= 0) {
				fullHeart.draw(g, heartX, heartY, 10, 10);
			} else if (hpDiff >= -5) {
				halfHeart.draw(g, heartX, heartY, 10, 10);
			} else {
				emptyHeart.draw(g, heartX, heartY, 10, 10);
			}
			heartX += 15;
		}
	}

	/**
	 * Draw air bubbles (oxygen indicator) when player is underwater
	 * @param g Graphics handler
	 * @param player Player entity
	 * @param world Game world
	 * @param tileSize Size of tiles in pixels
	 * @param screenWidth Screen width in pixels
	 * @param screenHeight Screen height in pixels
	 */
	public void drawAirBubbles(GraphicsHandler g, Player player, World world, int tileSize,
			int screenWidth, int screenHeight) {
		if (player.isHeadUnderWater(world, tileSize)) {
			int bubbleX = (screenWidth + 50) / 2;
			int bubbleY = screenHeight - 50;
			int numBubbles = player.airRemaining();
			for (int bubbleIdx = 1; bubbleIdx <= 10; ++bubbleIdx) {
				if (bubbleIdx <= numBubbles) {
					bubble.draw(g, bubbleX, bubbleY, 10, 10);
				} else {
					emptyBubble.draw(g, bubbleX, bubbleY, 10, 10);
				}
				bubbleX += 15;
			}
		}
	}

	/**
	 * Draw FPS counter and memory usage in top-left corner
	 * @param g Graphics handler
	 * @param delta Time delta in milliseconds
	 */
	public void drawFPS(GraphicsHandler g, long delta) {
		String fps = "Fps: " + 1 / ((float) delta / 1000) + "("
				+ Runtime.getRuntime().freeMemory() / 1024 / 1024 + " / "
				+ Runtime.getRuntime().totalMemory() / 1024 / 1024 + ") Free MB";
		g.setColor(Color.white);
		g.drawString(fps, 10, 10);
	}
}
