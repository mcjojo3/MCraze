/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.ui.menu;

import mc.sayda.mcraze.ui.component.*;
import mc.sayda.mcraze.graphics.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.player.data.*;
import mc.sayda.mcraze.ui.SharedSettings;
import mc.sayda.mcraze.graphics.UIRenderer;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings menu with volume controls and other options
 * Uses SharedSettings for consistent settings UI across menus
 */
public class SettingsMenu {
	private Game game;
	private UIRenderer uiRenderer;
	private List<Button> buttons;
	private PauseMenu pauseMenu;
	private SharedSettings sharedSettings;

	public SettingsMenu(Game game, UIRenderer uiRenderer, PauseMenu pauseMenu) {
		this.game = game;
		this.uiRenderer = uiRenderer;
		this.pauseMenu = pauseMenu;
		this.buttons = new ArrayList<>();
		this.sharedSettings = new SharedSettings(game, uiRenderer);
		buildMenu();
	}

	/**
	 * Build the settings menu buttons (just the Back button - settings handled by
	 * SharedSettings)
	 */
	private void buildMenu() {
		buttons.clear();

		// Back button
		Button backBtn = new Button(
				"back",
				"Back",
				430, // Y position - moved down to accommodate SFX volume controls
				200,
				40).setOnClick(this::goBack);

		buttons.add(backBtn);
	}

	/**
	 * Go back to pause menu
	 */
	private void goBack() {
		if (game.getClient() != null) {
			game.getClient().closeSettingsMenu();
		}
	}

	/**
	 * Draw the settings menu
	 */
	public void draw(GraphicsHandler g) {
		// Semi-transparent overlay
		g.setColor(new Color(0, 0, 0, 150));
		g.fillRect(0, 0, g.getScreenWidth(), g.getScreenHeight());

		// Draw title
		g.setColor(Color.white);
		String title = "Settings";
		int titleX = g.getScreenWidth() / 2 - g.getStringWidth(title) / 2;
		g.drawString(title, titleX, 150);

		// Get mouse position
		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		int screenWidth = g.getScreenWidth();

		// Render shared settings UI (volume, music, FPS)
		sharedSettings.renderSettings(g, mouseX, mouseY, false);

		// Draw back button
		if (buttons.size() >= 1) {
			Button backBtn = buttons.get(0);
			backBtn.updatePosition(screenWidth);
			backBtn.updateHover(mouseX, mouseY);
			backBtn.draw(g);
		}

		// Handle clicks
		if (game.getClient().leftClick) {
			game.getClient().leftClick = false;

			// Handle settings clicks first
			boolean settingsClicked = sharedSettings.handleClick(mouseX, mouseY, screenWidth);

			// Handle back button click if settings weren't clicked
			if (!settingsClicked) {
				for (Button button : buttons) {
					if (button.handleClick(mouseX, mouseY)) {
						break;
					}
				}
			}
		}

		// Draw mouse cursor
		uiRenderer.drawMouse(g, game.getClient().screenMousePos);
	}
}
