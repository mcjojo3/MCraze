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

import mc.sayda.Game;
import mc.sayda.GraphicsHandler;
import mc.sayda.Sprite;
import mc.sayda.SpriteStore;

public class MainMenu {

	/* menu sprites */
	private static final Sprite menu_bgTile = SpriteStore.get().getSprite("sprites/tiles/dirt.png");
	private static final Sprite menu_logo = SpriteStore.get().getSprite("sprites/menus/title.png");
	private static final Sprite menu_newUp = SpriteStore.get().getSprite("sprites/menus/new_up.png");
	private static final Sprite menu_newDown = SpriteStore.get().getSprite("sprites/menus/new_down.png");
	private static final Sprite menu_loadUp = SpriteStore.get().getSprite("sprites/menus/load_up.png");
	private static final Sprite menu_loadDown = SpriteStore.get().getSprite("sprites/menus/load_down.png");
	private static final Sprite menu_quitUp = SpriteStore.get().getSprite("sprites/menus/quit_up.png");
	private static final Sprite menu_quitDown = SpriteStore.get().getSprite("sprites/menus/quit_down.png");
	private static final Sprite menu_miniUp = SpriteStore.get().getSprite("sprites/menus/mini_up.png");
	private static final Sprite menu_mediumUp = SpriteStore.get().getSprite("sprites/menus/med_up.png");
	private static final Sprite menu_bigUp = SpriteStore.get().getSprite("sprites/menus/big_up.png");
	private static final Sprite menu_miniDown = SpriteStore.get().getSprite("sprites/menus/mini_down.png");
	private static final Sprite menu_mediumDown = SpriteStore.get().getSprite("sprites/menus/med_down.png");
	private static final Sprite menu_bigDown = SpriteStore.get().getSprite("sprites/menus/big_down.png");
	private static final Sprite menu_tag = SpriteStore.get().getSprite("sprites/menus/tag.png");
	private static final int menu_miniWidth = 256;
	private static final int menu_mediumWidth = 512;
	private static final int menu_bigWidth = 1024;

	private boolean newGame = false;
	private Game game;
	private UIRenderer uiRenderer;
	private long ticksRunning = 0;  // For UI animation

	public MainMenu(Game g, UIRenderer uiRenderer) {
		this.game = g;
		this.uiRenderer = uiRenderer;
	}

	/**
	 * Set Game reference (called after construction to avoid circular dependency)
	 */
	public void setGame(Game g) {
		this.game = g;
	}

	public void draw(GraphicsHandler g) {
		ticksRunning++;  // Increment for animation
		uiRenderer.drawTileBackground(g, menu_bgTile, 32);
		uiRenderer.drawCenteredX(g, menu_logo, 70, 397, 50);
		float tagScale = ((float) Math.abs((ticksRunning % 100) - 50)) / 50 + 1;
		menu_tag.draw(g, 450, 70, (int) (60 * tagScale), (int) (37 * tagScale));

		if (newGame) {
			drawNewMenu(g);
		} else {
			drawStartMenu(g);
		}
	}
	
	private void drawStartMenu(GraphicsHandler g) {
		final int buttonWidth = 160;
		final int buttonHeight = 64;
		final int centerX = g.getScreenWidth() / 2;
		final int buttonLeft = centerX - buttonWidth / 2;
		final int buttonRight = centerX + buttonWidth / 2;

		uiRenderer.drawCenteredX(g, menu_newUp, 150, buttonWidth, buttonHeight);
		uiRenderer.drawCenteredX(g, menu_loadUp, 250, buttonWidth, buttonHeight);
		uiRenderer.drawCenteredX(g, menu_quitUp, 350, buttonWidth, buttonHeight);

		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		boolean mouseInButtonX = mouseX >= buttonLeft && mouseX <= buttonRight;

		if (mouseInButtonX && mouseY >= 350 && mouseY <= 350 + buttonHeight) {
			uiRenderer.drawCenteredX(g, menu_quitDown, 350, buttonWidth, buttonHeight);
		} else if (mouseInButtonX && mouseY >= 250 && mouseY <= 250 + buttonHeight) {
			uiRenderer.drawCenteredX(g, menu_loadDown, 250, buttonWidth, buttonHeight);
		} else if (mouseInButtonX && mouseY >= 150 && mouseY <= 150 + buttonHeight) {
			uiRenderer.drawCenteredX(g, menu_newDown, 150, buttonWidth, buttonHeight);
		}

		if (!game.getClient().leftClick) {
			return;
		}
		game.getClient().leftClick = false;

		if (mouseInButtonX && mouseY >= 350 && mouseY <= 350 + buttonHeight) {
			game.quit();  // "quit" button
		} else if (mouseInButtonX && mouseY >= 250 && mouseY <= 250 + buttonHeight) {
			game.startGame(true, menu_mediumWidth);  // "load" button
		} else if (mouseInButtonX && mouseY >= 150 && mouseY <= 150 + buttonHeight) {
			newGame = true;  // "new" button
		}
	}
	
	private void drawNewMenu(GraphicsHandler g) {
		final int buttonWidth = 160;
		final int buttonHeight = 64;
		final int centerX = g.getScreenWidth() / 2;
		final int buttonLeft = centerX - buttonWidth / 2;
		final int buttonRight = centerX + buttonWidth / 2;

		uiRenderer.drawCenteredX(g, menu_miniUp, 150, buttonWidth, buttonHeight);
		uiRenderer.drawCenteredX(g, menu_mediumUp, 250, buttonWidth, buttonHeight);
		uiRenderer.drawCenteredX(g, menu_bigUp, 350, buttonWidth, buttonHeight);

		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		boolean mouseInButtonX = mouseX >= buttonLeft && mouseX <= buttonRight;

		if (mouseInButtonX && mouseY >= 350 && mouseY <= 350 + buttonHeight) {
			uiRenderer.drawCenteredX(g, menu_bigDown, 350, buttonWidth, buttonHeight);
		} else if (mouseInButtonX && mouseY >= 250 && mouseY <= 250 + buttonHeight) {
			uiRenderer.drawCenteredX(g, menu_mediumDown, 250, buttonWidth, buttonHeight);
		} else if (mouseInButtonX && mouseY >= 150 && mouseY <= 150 + buttonHeight) {
			uiRenderer.drawCenteredX(g, menu_miniDown, 150, buttonWidth, buttonHeight);
		}

		if (!game.getClient().leftClick) {
			return;
		}
		game.getClient().leftClick = false;
		newGame = false;

		if (mouseInButtonX && mouseY >= 350 && mouseY <= 350 + buttonHeight) {
			game.startGame(false, menu_bigWidth);
		} else if (mouseInButtonX && mouseY >= 250 && mouseY <= 250 + buttonHeight) {
			game.startGame(false, menu_mediumWidth);
		} else if (mouseInButtonX && mouseY >= 150 && mouseY <= 150 + buttonHeight) {
			game.startGame(false, menu_miniWidth);
		}
	}
}
