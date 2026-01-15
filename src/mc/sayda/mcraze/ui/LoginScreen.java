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

package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.SpriteStore;
import mc.sayda.mcraze.util.CredentialManager;

/**
 * Login screen - first screen shown when game starts
 * User enters username and password to access main menu
 */
public class LoginScreen {
	// Menu sprites
	// Menu sprites
	private static final Sprite MENU_BG_TILE = SpriteStore.get().getSprite("assets/sprites/tiles/dirt.png");
	private static final Sprite MENU_LOGO = SpriteStore.get().getSprite("assets/sprites/menus/title.png");

	// UI constants
	private static final int INPUT_WIDTH = 300;
	private static final int INPUT_HEIGHT = 40;
	private static final int INPUT_SPACING = 15;
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 40;

	private Game game;
	private UIRenderer uiRenderer;

	// Input fields
	private TextInput usernameInput;
	private TextInput passwordInput;
	private Button loginButton;
	private Checkbox rememberMeCheckbox;

	// Error handling
	private String errorMessage;
	private long errorStartTime;
	private static final long ERROR_DISPLAY_DURATION = 3000; // 3 seconds

	public LoginScreen(Game game, UIRenderer uiRenderer) {
		this.game = game;
		this.uiRenderer = uiRenderer;

		// Create username input
		usernameInput = new TextInput("username", 200, INPUT_WIDTH, INPUT_HEIGHT, 32);
		usernameInput.setPlaceholder("Username");
		usernameInput.setFocused(true);

		// Create password input
		passwordInput = new TextInput("password", 200 + INPUT_HEIGHT + INPUT_SPACING, INPUT_WIDTH, INPUT_HEIGHT, 32);
		passwordInput.setPlaceholder("Password");
		passwordInput.setPasswordMode(true);

		// Create remember me checkbox
		rememberMeCheckbox = new Checkbox(
				"remember_me",
				"Remember me",
				200 + (INPUT_HEIGHT + INPUT_SPACING) * 2 + 10,
				true);

		// Create login button
		loginButton = new Button(
				"login",
				"Login",
				200 + (INPUT_HEIGHT + INPUT_SPACING) * 2 + 40,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::attemptLogin);

		errorMessage = null;
		errorStartTime = 0;

		// Load saved credentials if available
		loadSavedCredentials();
	}

	/**
	 * Draw the login screen
	 */
	public void draw(GraphicsHandler g) {
		// Draw tiled background
		uiRenderer.drawTileBackground(g, MENU_BG_TILE, 32);

		// Draw logo
		uiRenderer.drawCenteredX(g, MENU_LOGO, 70, 397, 50);

		// Draw title
		g.setColor(Color.white);
		String title = "Login";
		int titleX = g.getScreenWidth() / 2 - g.getStringWidth(title) / 2;
		g.drawString(title, titleX, 150);

		// Update and draw input fields
		int screenWidth = g.getScreenWidth();
		usernameInput.updatePosition(screenWidth);
		passwordInput.updatePosition(screenWidth);
		rememberMeCheckbox.updatePosition(screenWidth);
		loginButton.updatePosition(screenWidth);

		// Draw labels
		g.setColor(Color.white);
		String usernameLabel = "Username:";
		int usernameLabelX = screenWidth / 2 - g.getStringWidth(usernameLabel) / 2;
		g.drawString(usernameLabel, usernameLabelX, usernameInput.getY() - 20);

		String passwordLabel = "Password:";
		int passwordLabelX = screenWidth / 2 - g.getStringWidth(passwordLabel) / 2;
		g.drawString(passwordLabel, passwordLabelX, passwordInput.getY() - 20);

		// Draw inputs
		usernameInput.draw(g);
		passwordInput.draw(g);

		// Draw checkbox
		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		rememberMeCheckbox.updateHover(mouseX, mouseY);
		rememberMeCheckbox.draw(g);

		// Draw button
		loginButton.updateHover(mouseX, mouseY);
		loginButton.draw(g);

		// Draw error message if present and not expired
		if (errorMessage != null) {
			long elapsed = System.currentTimeMillis() - errorStartTime;
			if (elapsed < ERROR_DISPLAY_DURATION) {
				g.setColor(new Color(255, 100, 100, 255)); // Red color for errors
				int errorX = screenWidth / 2 - g.getStringWidth(errorMessage) / 2;
				g.drawString(errorMessage, errorX, loginButton.getY() + loginButton.getHeight() + 30);
			} else {
				errorMessage = null; // Clear expired error
			}
		}
	}

	/**
	 * Handle mouse click
	 */
	public void handleClick(int mouseX, int mouseY) {
		// Handle input field clicks
		usernameInput.handleClick(mouseX, mouseY);
		passwordInput.handleClick(mouseX, mouseY);

		// Handle checkbox click
		rememberMeCheckbox.handleClick(mouseX, mouseY);

		// Handle button click
		loginButton.handleClick(mouseX, mouseY);
	}

	/**
	 * Handle key typed
	 */
	public void handleKeyTyped(char c) {
		// Handle ENTER key as login
		if (c == '\n' || c == '\r') {
			attemptLogin();
			return;
		}

		// Pass to input fields
		usernameInput.handleKeyTyped(c);
		passwordInput.handleKeyTyped(c);
	}

	/**
	 * Handle key pressed (for arrow keys, delete, ctrl+c/v, etc.)
	 */
	public void handleKeyPressed(int keyCode, boolean shiftPressed, boolean ctrlPressed) {
		// Handle TAB key to switch between inputs
		if (keyCode == 9) { // VK_TAB
			if (usernameInput.isFocused()) {
				usernameInput.setFocused(false);
				passwordInput.setFocused(true);
			} else {
				usernameInput.setFocused(true);
				passwordInput.setFocused(false);
			}
			return;
		}

		// Pass to input fields for cursor navigation and clipboard operations
		usernameInput.handleKeyPressed(keyCode, shiftPressed, ctrlPressed);
		passwordInput.handleKeyPressed(keyCode, shiftPressed, ctrlPressed);
	}

	/**
	 * Handle TAB key to switch between inputs
	 */
	public void handleTab() {
		if (usernameInput.isFocused()) {
			usernameInput.setFocused(false);
			passwordInput.setFocused(true);
		} else {
			usernameInput.setFocused(true);
			passwordInput.setFocused(false);
		}
	}

	/**
	 * Attempt to login with entered credentials
	 */
	private void attemptLogin() {
		String username = usernameInput.getText().trim();
		String password = passwordInput.getText();

		// Validate input
		if (username.isEmpty()) {
			showError("Please enter a username");
			return;
		}

		if (password.isEmpty()) {
			showError("Please enter a password");
			return;
		}

		// Basic username validation (alphanumeric, underscore, hyphen)
		if (!username.matches("[a-zA-Z0-9_-]+")) {
			showError("Username can only contain letters, numbers, - and _");
			return;
		}

		// Save credentials if "Remember me" is checked
		if (rememberMeCheckbox.isChecked()) {
			CredentialManager.saveCredentials(username, password);
		} else {
			// Clear saved credentials if unchecked
			CredentialManager.deleteCredentials();
		}

		// Login successful - store credentials and show main menu
		game.setLoggedInUser(username, password);
		game.showMainMenu();

		System.out.println("Logged in as: " + username);
	}

	/**
	 * Show error message for 3 seconds
	 */
	private void showError(String message) {
		this.errorMessage = message;
		this.errorStartTime = System.currentTimeMillis();
		System.err.println("Login error: " + message);
	}

	/**
	 * Load saved credentials and auto-fill the form
	 */
	private void loadSavedCredentials() {
		CredentialManager.SavedCredentials saved = CredentialManager.loadCredentials();
		if (saved != null) {
			usernameInput.setText(saved.username);
			passwordInput.setText(saved.password);
			rememberMeCheckbox.setChecked(true);
			System.out.println("Auto-filled credentials for: " + saved.username);
		}
	}
}
