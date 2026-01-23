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

package mc.sayda.mcraze.graphics;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.entity.LivingEntity;

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

	// private Sprite builderIcon;
	private Sprite minerIcon;
	private Sprite foregroundIcon; // Indicator for normal block placement
	private Sprite backdropIcon; // Indicator for backdrop placement
	private Sprite fullHeart;
	private Sprite halfHeart;
	private Sprite emptyHeart;
	private Sprite bubble;
	private Sprite emptyBubble;

	public UIRenderer() {
		SpriteStore ss = SpriteStore.get();

		// Load UI sprites
		// builderIcon = ss.getSprite("assets/sprites/other/builder.png");
		// minerIcon = ss.getSprite("assets/sprites/other/miner.png");
		foregroundIcon = ss.getSprite("assets/sprites/other/foreground.png"); // NEW
		backdropIcon = ss.getSprite("assets/sprites/other/backdrop.png"); // NEW
		fullHeart = ss.getSprite("assets/sprites/other/full_heart.png");
		halfHeart = ss.getSprite("assets/sprites/other/half_heart.png");
		emptyHeart = ss.getSprite("assets/sprites/other/empty_heart.png");
		bubble = ss.getSprite("assets/sprites/other/bubble.png");
		// halfBubble = ss.getSprite("assets/sprites/bubble_half.png");
		emptyBubble = ss.getSprite("assets/sprites/other/bubble_pop2.png");

	}

	/**
	 * Draw a sprite centered horizontally on the screen
	 * 
	 * @param g      Graphics handler
	 * @param s      Sprite to draw
	 * @param top    Y position (top of sprite)
	 * @param width  Width to render sprite
	 * @param height Height to render sprite
	 */
	public void drawCenteredX(GraphicsHandler g, Sprite s, int top, int width, int height) {
		s.draw(g, g.getScreenWidth() / 2 - width / 2, top, width, height);
	}

	/**
	 * Draw the mouse cursor as a white circle with black center
	 * 
	 * @param g   Graphics handler
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
	 * 
	 * @param g        Graphics handler
	 * @param sprite   Sprite to use as tile
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
	 * 
	 * @param g        Graphics handler
	 * @param player   Player entity
	 * @param cameraX  Camera X position
	 * @param cameraY  Camera Y position
	 * @param tileSize Size of tiles in pixels
	 */
	public void drawBuildMineIcons(GraphicsHandler g, Player player, float cameraX, float cameraY, int tileSize) {
		if (player.handTargetPos.x != -1) {
			Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, tileSize,
					tileSize, tileSize, player.handTargetPos.x, player.handTargetPos.y);

			// Show different icon based on placement mode
			if (player.backdropPlacementMode) {
				backdropIcon.draw(g, pos.x, pos.y, tileSize, tileSize);
			} else {
				foregroundIcon.draw(g, pos.x, pos.y, tileSize, tileSize);
			}
		}
	}

	/**
	 * Draw the health bar (hearts) at the bottom of the screen
	 * 
	 * @param g            Graphics handler
	 * @param player       Player entity
	 * @param screenWidth  Screen width in pixels
	 * @param screenHeight Screen height in pixels
	 */
	public void drawHealthBar(GraphicsHandler g, Player player, int screenWidth, int screenHeight) {
		int barWidth = 200;
		int barHeight = 20;
		int x = (screenWidth - barWidth) / 2;
		int y = screenHeight - 60;

		// Background (Black/Gray)
		g.setColor(new Color(50, 50, 50));
		g.fillRect(x, y, barWidth, barHeight);

		// Health Fill (Red)
		float healthPct = (float) player.hitPoints / player.getMaxHP();
		int fillWidth = (int) (barWidth * healthPct);

		g.setColor(new Color(200, 20, 20));
		g.fillRect(x, y, fillWidth, barHeight);

		// Border (White)
		g.setColor(Color.white); // Assuming Color.white exists or using new Color(255,255,255)
		g.drawRect(x, y, barWidth, barHeight);

		// Text Overlay (HP / MaxHP)
		String hpText = player.hitPoints + " / " + player.getMaxHP();
		g.drawString(hpText, x + barWidth / 2 - hpText.length() * 4, y + 15); // Approximate centering
	}

	public void drawManaBar(GraphicsHandler g, Player player, int screenWidth, int screenHeight) {
		if (player.maxMana <= 0)
			return; // Only draw if class uses mana

		int barWidth = 100;
		int barHeight = 15;
		int hotbarWidth = 270; // Reference from InventoryView.java
		int gap = 15;

		// Position: RIGHT of Hotbar
		int x = (screenWidth / 2) + (hotbarWidth / 2) + gap;
		int y = screenHeight - 35; // Center-aligned vertically with hotbar

		// Background
		g.setColor(new Color(50, 50, 50));
		g.fillRect(x, y, barWidth, barHeight);

		// Mana Fill (Blue)
		float manaPct = (float) player.mana / player.maxMana;
		int fillWidth = (int) (barWidth * manaPct);

		g.setColor(new Color(40, 40, 240)); // Mana Blue
		g.fillRect(x, y, fillWidth, barHeight);

		// Border
		g.setColor(Color.white);
		g.drawRect(x, y, barWidth, barHeight);

		// Text
		String manaText = player.mana + "";
		g.drawString(manaText, x + barWidth / 2 - manaText.length() * 4, y + 12);
	}

	public void drawEssenceBar(GraphicsHandler g, Player player, int screenWidth, int screenHeight) {
		if (player.maxEssence <= 0 && player.essence <= 0)
			return;

		int barWidth = 100;
		int barHeight = 15;
		int hotbarWidth = 270;
		int gap = 15;

		// Position: LEFT of Hotbar
		int x = (screenWidth / 2) - (hotbarWidth / 2) - barWidth - gap;
		int y = screenHeight - 35; // Center-aligned vertically with hotbar

		// Background
		g.setColor(new Color(50, 50, 50));
		g.fillRect(x, y, barWidth, barHeight);

		// Essence Fill (Purple)
		float essencePct = (float) player.essence / player.maxEssence;
		int fillWidth = (int) (barWidth * essencePct);

		g.setColor(new Color(180, 40, 240)); // Essence Purple
		g.fillRect(x, y, fillWidth, barHeight);

		// Border
		g.setColor(Color.white);
		g.drawRect(x, y, barWidth, barHeight);

		// Text
		String text = player.essence + "";
		g.drawString(text, x + barWidth / 2 - text.length() * 4, y + 12);
	}

	public void drawBowChargeBar(GraphicsHandler g, Player player, int screenWidth, int screenHeight) {
		if (player.bowCharge <= 0)
			return;

		int barWidth = 100;
		int barHeight = 6;
		int hotbarWidth = 270;
		int gap = 15;

		// Position: LEFT of Hotbar (below essence if visible, or taking its place?)
		// Let's stack it above Essence bar area.

		int x = (screenWidth / 2) - (hotbarWidth / 2) - barWidth - gap;
		int y = screenHeight - 35 - 20; // 20px above Essence bar

		// Background
		g.setColor(new Color(50, 50, 50));
		g.fillRect(x, y, barWidth, barHeight);

		// Charge Fill (White/Yellow)
		float pct = (float) player.bowCharge / player.maxBowCharge;
		if (pct > 1f)
			pct = 1f;
		int fillWidth = (int) (barWidth * pct);

		g.setColor(new Color(255, 255, 200)); // Pale Yellow
		g.fillRect(x, y, fillWidth, barHeight);

		// Border
		g.setColor(Color.white);
		g.drawRect(x, y, barWidth, barHeight);
	}

	public void drawAirBubbles(GraphicsHandler g, Player player, World world, int tileSize,
			int screenWidth, int screenHeight) {
		if (player.isHeadUnderWater(world, tileSize)) {
			int barWidth = 200;
			int barHeight = 15;
			int x = (screenWidth - barWidth) / 2;
			int y = screenHeight - 80; // Above health bar

			// Background
			g.setColor(new Color(50, 50, 50));
			g.fillRect(x, y, barWidth, barHeight);

			// Air Fill (Blue)
			int maxAir = LivingEntity.MAX_AIR_TICKS;
			float airPct = (float) player.airRemaining() / maxAir;
			int fillWidth = (int) (barWidth * airPct);

			g.setColor(new Color(50, 100, 255));
			g.fillRect(x, y, fillWidth, barHeight);

			// Border
			g.setColor(Color.white);
			g.drawRect(x, y, barWidth, barHeight);
		}
	}

	/**
	 * Draw FPS counter and memory usage in top-left corner
	 * 
	 * @param g      Graphics handler
	 * @param delta  Time delta in milliseconds
	 * @param client Client instance for debug info
	 */
	public void drawFPS(GraphicsHandler g, long delta) {
		String fps = "Fps: " + 1 / ((float) delta / 1000) + "("
				+ Runtime.getRuntime().freeMemory() / 1024 / 1024 + " / "
				+ Runtime.getRuntime().totalMemory() / 1024 / 1024 + ") Free MB";
		g.setColor(Color.white);
		g.drawString(fps, 10, 10);
	}
}
