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

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.io.InputStream;

/**
 * Main menu system with dynamic button support
 */
public class MainMenu {
	private final mc.sayda.mcraze.logging.GameLogger logger = mc.sayda.mcraze.logging.GameLogger.get();

	// Menu states
	private enum MenuState {
		MAIN,
		SINGLEPLAYER,
		WORLD_SELECT,
		WORLD_CREATE,
		MULTIPLAYER,
		OPTIONS
	}

	// Menu sprites
	private static final Sprite MENU_BG_TILE = SpriteStore.get().getSprite("assets/sprites/tiles/dirt.png");
	private static final Sprite MENU_LOGO = SpriteStore.get().getSprite("assets/sprites/menus/title.png");
	// private static final Sprite MENU_TAG =
	// SpriteStore.get().getSprite("assets/sprites/menus/tag.png"); // Removed in
	// favor of text

	// Button constants
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 40;
	private static final int BUTTON_SPACING = 10;

	// World size constants
	private static final int WORLD_SIZE_TINY = 128;
	private static final int WORLD_SIZE_SMALL = 256;
	private static final int WORLD_SIZE_MEDIUM = 512;
	private static final int WORLD_SIZE_LARGE = 1024;
	private static final int WORLD_SIZE_HUGE = 2048;

	private Game game;
	private UIRenderer uiRenderer;
	private MenuState currentState;
	private List<Button> currentButtons;
	private TextInput ipInput; // For multiplayer connection
	private TextInput worldNameInput; // For world creation
	private TextInput noiseModifierInput; // For noise override
	private ScrollableList<String> gamemodeList;
	private ScrollableList<String> rulesList;
	private double selectedNoiseModifier = 0.0;
	private long ticksRunning = 0;
	private SharedSettings sharedSettings; // Shared settings component
	private String currentSplash; // Dynamic splash text

	// Selected options for new world
	private mc.sayda.mcraze.world.GameMode selectedGameMode = mc.sayda.mcraze.world.GameMode.CLASSIC; // Default CLASSIC
	private boolean selectedKeepInventory = false;
	private boolean selectedDaylightCycle = true;
	private boolean selectedSpelunking = false;
	private boolean selectedMobGriefing = true;
	private int selectedWorldSize = WORLD_SIZE_MEDIUM; // Default Medium

	// World selection state
	private ScrollableList<mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata> worldList;
	private List<Button> worldActionButtons; // Load, Rename, Delete buttons

	public MainMenu(Game g, UIRenderer uiRenderer) {
		this.game = g;
		this.uiRenderer = uiRenderer;
		this.currentState = MenuState.MAIN;
		this.currentButtons = new ArrayList<>();
		loadSplashes(); // Load random splash
		buildMainMenu();
	}

	private void loadSplashes() {
		List<String> splashes = new ArrayList<>();
		try {
			InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("assets/splashes.txt");
			if (in != null) {
				try (Scanner scanner = new Scanner(in)) {
					while (scanner.hasNextLine()) {
						String line = scanner.nextLine().trim();
						if (!line.isEmpty()) {
							splashes.add(line);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.error("Failed to load splashes: " + e.getMessage());
		}

		if (splashes.isEmpty()) {
			currentSplash = "Now in 2D!";
		} else {
			currentSplash = splashes.get(new Random().nextInt(splashes.size()));
		}
	}

	/**
	 * Initialize shared settings (called after Game has a client)
	 */
	public void initializeSharedSettings() {
		if (sharedSettings == null) {
			sharedSettings = new SharedSettings(game, uiRenderer);
		}
	}

	/**
	 * Set Game reference (called after construction to avoid circular dependency)
	 */
	public void setGame(Game g) {
		this.game = g;
	}

	/**
	 * Reset to main menu root (public interface for ESC key behavior)
	 */
	public void resetToRoot() {
		buildMainMenu();
	}

	/**
	 * Build the main menu buttons
	 */
	private void buildMainMenu() {
		currentButtons.clear();
		currentState = MenuState.MAIN;

		int startY = 200;

		// Singleplayer button
		Button singleplayerBtn = new Button(
				"singleplayer",
				"Singleplayer",
				startY,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::showSingleplayerMenu);

		// Multiplayer button
		Button multiplayerBtn = new Button(
				"multiplayer",
				"Multiplayer",
				startY + BUTTON_HEIGHT + BUTTON_SPACING,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::showMultiplayerMenu);

		// Options button
		Button optionsBtn = new Button(
				"options",
				"Options",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::showOptionsMenu);

		// Logout button
		Button logoutBtn = new Button(
				"logout",
				"Logout",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(() -> game.logout());

		// Exit button
		Button exitBtn = new Button(
				"exit",
				"Exit Game",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 4,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(() -> game.quit());

		currentButtons.add(singleplayerBtn);
		currentButtons.add(multiplayerBtn);
		currentButtons.add(optionsBtn);
		currentButtons.add(logoutBtn);
		currentButtons.add(exitBtn);
	}

	/**
	 * Build the singleplayer menu with Create/Load options
	 */
	private void showSingleplayerMenu() {
		currentButtons.clear();
		currentState = MenuState.SINGLEPLAYER;

		int startY = 200;

		// Create New World button
		Button createBtn = new Button(
				"create",
				"Create New World",
				startY,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::showWorldCreationMenu);

		// Load World button
		Button loadBtn = new Button(
				"load",
				"Load World",
				startY + BUTTON_HEIGHT + BUTTON_SPACING,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::showWorldSelectionMenu);

		// Back button
		Button backBtn = new Button(
				"back",
				"Back",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::buildMainMenu);

		currentButtons.add(createBtn);
		currentButtons.add(loadBtn);
		currentButtons.add(backBtn);
	}

	/**
	 * Show world creation menu (2-column layout)
	 */
	private void showWorldCreationMenu() {
		currentButtons.clear();
		currentState = MenuState.WORLD_CREATE;

		int startY = 170; // Start below logo (ends at ~148)
		int colWidth = BUTTON_WIDTH;
		int spacing = 65; // Keeping good vertical spacing

		// Center-relative offsets for two-column layout (moved closer)
		int leftOffset = -colWidth / 2 - 20; // 40px total gap
		int rightOffset = colWidth / 2 + 20;

		// Preserve typed values
		String defaultName = "New World";
		if (worldNameInput != null)
			defaultName = worldNameInput.getText();

		String defaultNoise = "0.0";
		if (noiseModifierInput != null)
			defaultNoise = noiseModifierInput.getText();

		// 1. World Name (Top Left)
		worldNameInput = new TextInput("world_name", startY, colWidth, BUTTON_HEIGHT, 20);
		worldNameInput.setText(defaultName);
		worldNameInput.setOffsetX(leftOffset);

		// 2. World Size (Below Name)
		String sizeLabel = "Size: Medium";
		if (selectedWorldSize == WORLD_SIZE_TINY)
			sizeLabel = "Size: Tiny";
		else if (selectedWorldSize == WORLD_SIZE_SMALL)
			sizeLabel = "Size: Small";
		else if (selectedWorldSize == WORLD_SIZE_MEDIUM)
			sizeLabel = "Size: Medium";
		else if (selectedWorldSize == WORLD_SIZE_LARGE)
			sizeLabel = "Size: Large";
		else if (selectedWorldSize == WORLD_SIZE_HUGE)
			sizeLabel = "Size: Huge";

		Button sizeBtn = new Button(
				"size",
				sizeLabel,
				startY + spacing,
				colWidth,
				BUTTON_HEIGHT).setOnClick(() -> {
					// Cycle sizes
					if (selectedWorldSize == WORLD_SIZE_TINY)
						selectedWorldSize = WORLD_SIZE_SMALL;
					else if (selectedWorldSize == WORLD_SIZE_SMALL)
						selectedWorldSize = WORLD_SIZE_MEDIUM;
					else if (selectedWorldSize == WORLD_SIZE_MEDIUM)
						selectedWorldSize = WORLD_SIZE_LARGE;
					else if (selectedWorldSize == WORLD_SIZE_LARGE)
						selectedWorldSize = WORLD_SIZE_HUGE;
					else
						selectedWorldSize = WORLD_SIZE_TINY;
					showWorldCreationMenu(); // Refresh label
				});
		sizeBtn.setOffsetX(leftOffset);
		currentButtons.add(sizeBtn);

		// 3. Noise Modifier (Below Size)
		noiseModifierInput = new TextInput("noise_mod", startY + spacing * 2, colWidth, BUTTON_HEIGHT, 10);
		noiseModifierInput.setText(defaultNoise);
		noiseModifierInput.setOffsetX(leftOffset);

		// 4. Create World Button (Bottom of Column 1)
		Button createBtn = new Button("create", "Create World", startY + spacing * 3, colWidth, BUTTON_HEIGHT)
				.setOnClick(() -> createNewWorld());
		createBtn.setOffsetX(leftOffset);
		currentButtons.add(createBtn);

		// 5. Back Button (Below Create)
		Button backBtn = new Button("back", "Back", startY + spacing * 4, colWidth, BUTTON_HEIGHT)
				.setOnClick(() -> showSingleplayerMenu());
		backBtn.setOffsetX(leftOffset);
		currentButtons.add(backBtn);

		// Right Column: Scrollable Lists

		// Right Column: Scrollable Lists (Aligned with Name/Size and Noise
		// respectively)

		// 6. GameMode List (Top Right)
		if (gamemodeList == null) {
			gamemodeList = new ScrollableList<>(0, startY, colWidth, 90, 22);
			gamemodeList.setOffsetX(rightOffset);
			updateGameModeList();
		} else {
			gamemodeList = new ScrollableList<>(0, startY, colWidth, 90, 22);
			gamemodeList.setOffsetX(rightOffset);
			updateGameModeList();
		}

		// 7. Rules List (Below Mode)
		if (rulesList == null) {
			rulesList = new ScrollableList<>(0, startY + spacing * 2, colWidth, 120, 22);
			rulesList.setOffsetX(rightOffset);
			updateRulesList();
		} else {
			rulesList = new ScrollableList<>(0, startY + spacing * 2, colWidth, 120, 22);
			rulesList.setOffsetX(rightOffset);
			updateRulesList();
		}
	}

	private void updateGameModeList() {
		if (gamemodeList == null)
			return;
		List<String> modes = new ArrayList<>();
		List<String> names = new ArrayList<>();
		int index = 0;
		int selection = -1;
		for (mc.sayda.mcraze.world.GameMode mode : mc.sayda.mcraze.world.GameMode.values()) {
			modes.add(mode.name());
			names.add(mode.name());
			if (mode == selectedGameMode) {
				selection = index;
			}
			index++;
		}
		gamemodeList.setItems(modes, names);
		gamemodeList.setSelectedIndex(selection);
	}

	private void updateRulesList() {
		if (rulesList == null)
			return;
		List<String> keys = new ArrayList<>();
		List<String> names = new ArrayList<>();

		keys.add("keepInventory");
		names.add("keepInventory: " + (selectedKeepInventory ? "TRUE" : "FALSE"));

		keys.add("daylightCycle");
		names.add("daylightCycle: " + (selectedDaylightCycle ? "TRUE" : "FALSE"));

		keys.add("spelunking");
		names.add("spelunking: " + (selectedSpelunking ? "TRUE" : "FALSE"));

		keys.add("mobGriefing");
		names.add("mobGriefing: " + (selectedMobGriefing ? "TRUE" : "FALSE"));

		rulesList.setItems(keys, names);
	}

	/**
	 * Show world selection menu (list of saved worlds)
	 */
	private void showWorldSelectionMenu() {
		currentButtons.clear();
		currentState = MenuState.WORLD_SELECT;
		worldNameInput = null;

		// Get available worlds
		List<mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata> availableWorlds = mc.sayda.mcraze.world.storage.WorldSaveManager
				.getAvailableWorlds();

		// Create scrollable list positioned to the left
		int listWidth = 400;
		int listHeight = 200;
		int listY = 200; // Centered vertically
		// Position list offset to the left, centered as a group with buttons
		worldList = new ScrollableList<>(-100, listY, listWidth, listHeight, 35);

		// Populate list
		List<String> displayNames = new ArrayList<>();
		for (mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata world : availableWorlds) {
			displayNames.add(world.worldName);
		}
		worldList.setItems(availableWorlds, displayNames);

		// Create action buttons
		buildWorldActionButtons();
	}

	/**
	 * Build world action buttons (Load, Rename, Delete, Back)
	 * Positioned to the right of the world list
	 */
	private void buildWorldActionButtons() {
		worldActionButtons = new ArrayList<>();

		// Position buttons to the right of the list with minimal gap
		// List is at x=-100 (offset from center), width=400
		// List right edge is at: -100 + 400 = 300 from center
		// Buttons centered as a group with the list
		int buttonOffsetX = 210; // Positions group center at screen center
		int startY = 200; // Same Y as list for vertical centering
		int buttonWidth = 180;

		boolean hasSelection = (worldList != null && worldList.getSelectedIndex() >= 0);

		Button loadBtn = new Button(
				"load_world",
				"Load World",
				startY,
				buttonWidth,
				BUTTON_HEIGHT).setOnClick(this::loadSelectedWorld)
				.setEnabled(hasSelection)
				.setOffsetX(buttonOffsetX);

		Button renameBtn = new Button(
				"rename_world",
				"Rename World",
				startY + BUTTON_HEIGHT + BUTTON_SPACING,
				buttonWidth,
				BUTTON_HEIGHT).setOnClick(this::renameSelectedWorld)
				.setEnabled(hasSelection)
				.setOffsetX(buttonOffsetX);

		Button deleteBtn = new Button(
				"delete_world",
				"Delete World",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2,
				buttonWidth,
				BUTTON_HEIGHT).setOnClick(this::deleteSelectedWorld)
				.setEnabled(hasSelection)
				.setOffsetX(buttonOffsetX);

		Button backBtn = new Button(
				"back",
				"Back",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3,
				buttonWidth,
				BUTTON_HEIGHT).setOnClick(this::showSingleplayerMenu)
				.setOffsetX(buttonOffsetX);

		worldActionButtons.add(loadBtn);
		worldActionButtons.add(renameBtn);
		worldActionButtons.add(deleteBtn);
		worldActionButtons.add(backBtn);
	}

	/**
	 * Load the selected world
	 */
	private void loadSelectedWorld() {
		if (worldList != null) {
			mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata world = worldList.getSelectedItem();
			if (world != null) {
				loadExistingWorld(world.worldName);
			}
		}
	}

	/**
	 * Rename the selected world
	 */
	private void renameSelectedWorld() {
		if (worldList != null) {
			mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata world = worldList.getSelectedItem();
			if (world != null) {
				// TODO: Implement rename dialog
				System.out.println("Rename world: " + world.worldName);
			}
		}
	}

	/**
	 * Delete the selected world
	 */
	private void deleteSelectedWorld() {
		if (worldList != null) {
			mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata world = worldList.getSelectedItem();
			if (world != null) {
				// Delete the world
				boolean deleted = mc.sayda.mcraze.world.storage.WorldSaveManager.deleteWorld(world.worldName);
				if (deleted) {
					System.out.println("Deleted world: " + world.worldName);
					// Refresh world list
					showWorldSelectionMenu();
				} else {
					System.err.println("Failed to delete world: " + world.worldName);
				}
			}
		}
	}

	/**
	 * Build the multiplayer connection menu
	 */
	private void showMultiplayerMenu() {
		currentButtons.clear();
		currentState = MenuState.MULTIPLAYER;

		int startY = 200;

		// Create IP input field and load last used IP
		ipInput = new TextInput("ip_input", startY, BUTTON_WIDTH, BUTTON_HEIGHT, 32);
		String lastIP = mc.sayda.mcraze.util.OptionsManager.get().getLastServerIP();
		ipInput.setText(lastIP != null ? lastIP : "localhost:25565"); // Use last IP or default

		// Connect button
		Button connectBtn = new Button(
				"connect",
				"Connect",
				startY + BUTTON_HEIGHT + BUTTON_SPACING * 2,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::connectToServer);

		// Back button
		Button backBtn = new Button(
				"back",
				"Back",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 4,
				BUTTON_WIDTH,
				BUTTON_HEIGHT).setOnClick(this::buildMainMenu);

		currentButtons.add(connectBtn);
		currentButtons.add(backBtn);
	}

	/**
	 * Build the options menu
	 */
	private void showOptionsMenu() {
		currentButtons.clear();
		currentState = MenuState.OPTIONS;
		ipInput = null; // Clear IP input when leaving multiplayer menu
		worldNameInput = null;

		// Initialize shared settings if not already done
		initializeSharedSettings();

		// Back button (positioned below settings UI)
		Button backBtn = new Button(
				"back",
				"Back",
				430, // Y position - moved down to accommodate SFX volume controls
				200,
				40).setOnClick(this::buildMainMenu);

		currentButtons.add(backBtn);
	}

	/**
	 * Create a new world with user-specified name and size
	 */
	private void createNewWorld() {
		if (worldNameInput == null) {
			System.err.println("World name input is null");
			return;
		}

		String worldName = worldNameInput.getText().trim();
		if (worldName.isEmpty()) {
			worldName = "New World";
		}

		// Check if world already exists
		List<mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata> worlds = mc.sayda.mcraze.world.storage.WorldSaveManager
				.getAvailableWorlds();
		for (mc.sayda.mcraze.world.storage.WorldSaveManager.WorldMetadata world : worlds) {
			if (world.worldName.equalsIgnoreCase(worldName)) {
				System.err.println("World '" + worldName + "' already exists!");
				return;
			}
		}

		// Create the world
		System.out.println("Creating new world: " + worldName + " (size: " + selectedWorldSize + ")");

		// Get logged-in credentials
		String username = game.getLoggedInUsername();
		String password = game.getLoggedInPassword();

		double noiseMod = 0.0;
		try {
			if (noiseModifierInput != null) {
				noiseMod = Double.parseDouble(noiseModifierInput.getText());
			}
		} catch (Exception e) {
		}

		game.startGame(worldName, false, selectedWorldSize, username, password,
				selectedGameMode, selectedKeepInventory, selectedDaylightCycle, noiseMod);
	}

	/**
	 * Load an existing world by name
	 */
	private void loadExistingWorld(String worldName) {
		System.out.println("Loading world: " + worldName);

		// Get logged-in credentials
		String username = game.getLoggedInUsername();
		String password = game.getLoggedInPassword();

		// Pre-validate: Check if playerdata exists and password matches
		if (mc.sayda.mcraze.player.data.PlayerDataManager.exists(worldName, username)) {
			mc.sayda.mcraze.player.data.PlayerData data = mc.sayda.mcraze.player.data.PlayerDataManager.authenticate(
					worldName,
					username, password);
			if (data == null) {
				// Wrong password
				System.err.println("Wrong password for world '" + worldName + "'");
				return;
			}
		}
		// If playerdata doesn't exist, it will auto-register

		game.startGame(worldName, true, WORLD_SIZE_MEDIUM, username, password); // Size doesn't matter for loading
	}

	/**
	 * Start a new game with the specified world size (legacy method)
	 * 
	 * @deprecated Use createNewWorld() instead
	 */
	@Deprecated
	private void startNewGame(int worldSize) {
		String worldName = "World1";
		String username = game.getLoggedInUsername();
		String password = game.getLoggedInPassword();
		game.startGame(worldName, false, worldSize, username, password);
	}

	/**
	 * Load an existing world (legacy method)
	 * 
	 * @deprecated Use loadExistingWorld() instead
	 */
	@Deprecated
	private void loadWorld() {
		String worldName = "World1";
		String username = game.getLoggedInUsername();
		String password = game.getLoggedInPassword();
		game.startGame(worldName, true, WORLD_SIZE_MEDIUM, username, password);
	}

	/**
	 * Connect to multiplayer server
	 */
	private void connectToServer() {
		if (ipInput == null)
			return;

		String address = ipInput.getText().trim();
		if (address.isEmpty()) {
			System.err.println("Please enter a server address");
			return;
		}

		// Parse IP:PORT
		String host = "localhost";
		int port = 25565;

		try {
			if (address.contains(":")) {
				String[] parts = address.split(":");
				host = parts[0];
				port = Integer.parseInt(parts[1]);
			} else {
				host = address;
			}

			System.out.println("Connecting to " + host + ":" + port);

			// Save the IP address for next time (using OptionsManager)
			mc.sayda.mcraze.util.OptionsManager.get().setLastServerIP(address);
			// Legacy fallback for now (optional)
			// mc.sayda.mcraze.util.CredentialManager.saveLastIP(address);

			game.connectMultiplayer(host, port);
		} catch (NumberFormatException e) {
			System.err.println("Invalid port number: " + e.getMessage());
		} catch (Exception e) {
			System.err.println("Failed to connect: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Draw the menu
	 */
	public void draw(GraphicsHandler g) {
		ticksRunning++;
		int screenWidth = g.getScreenWidth();

		// Draw tiled background
		uiRenderer.drawTileBackground(g, MENU_BG_TILE, 32);

		// Draw logo
		int logoX = (screenWidth - 349) / 2;
		int logoY = 70;
		uiRenderer.drawCenteredX(g, MENU_LOGO, logoY, 349, 78);

		// Draw splash text
		if (currentSplash != null) {
			g.setColor(new mc.sayda.mcraze.graphics.Color(255, 255, 0)); // Yellow
			g.setFont("Dialog", GraphicsHandler.FONT_BOLD, 20); // Bold and slightly larger

			// Pulse and simple animation (time-based for smoothness)
			// Cycle every 1.5 seconds (1500ms)
			double timeScale = (System.currentTimeMillis() % 1500L) / 1500.0 * Math.PI * 2;
			float scale = 1.0f + (float) Math.sin(timeScale) * 0.1f;
			int splashWidth = g.getStringWidth(currentSplash);

			// Position relative to logo (center point for rotation)
			// Moved slightly more to the right (+300 instead of +270)
			int centerX = logoX + 300;
			int centerY = logoY + 60;

			g.pushState();

			// Translate to center, rotate, scale, then translate back
			g.translate(centerX, centerY);
			g.rotate(Math.toRadians(-20), 0, 0);
			g.scale(scale, scale);

			// Draw centered on current local origin
			g.drawString(currentSplash, -splashWidth / 2, 0);

			g.popState();

			g.setFont("Dialog", GraphicsHandler.FONT_PLAIN, 12);
		}

		// Draw menu title based on state
		g.setColor(mc.sayda.mcraze.graphics.Color.white);
		String menuTitle = getMenuTitle();
		if (menuTitle != null) {
			int titleX = screenWidth / 2 - g.getStringWidth(menuTitle) / 2;
			g.drawString(menuTitle, titleX, 150);
		}

		// Update button positions and draw them
		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;

		// Draw IP input if in multiplayer menu
		if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
			ipInput.updatePosition(screenWidth);
			ipInput.draw(g);
		}

		// Draw world name input if in world creation menu
		if (currentState == MenuState.WORLD_CREATE) {
			// Draw menu title just below logo

			if (worldNameInput != null) {
				worldNameInput.updatePosition(screenWidth);
				worldNameInput.draw(g);

				// Draw label above input
				g.setColor(mc.sayda.mcraze.graphics.Color.white);
				String label = "World Name:";
				g.drawString(label, worldNameInput.getX(), worldNameInput.getY() - 4);
			}

			// Draw Size Label (Button is at offset leftOffset)
			g.setColor(mc.sayda.mcraze.graphics.Color.white);
			int leftX = (screenWidth - BUTTON_WIDTH) / 2 + (-BUTTON_WIDTH / 2 - 20);
			g.drawString("World Size:", leftX, 170 + 65 - 4); // startY + spacing - 4

			if (noiseModifierInput != null) {
				noiseModifierInput.updatePosition(screenWidth);
				noiseModifierInput.draw(g);

				// Draw label
				g.setColor(mc.sayda.mcraze.graphics.Color.white);
				String label = "Noise Modifier:";
				g.drawString(label, noiseModifierInput.getX(), noiseModifierInput.getY() - 4);
			}

			if (gamemodeList != null) {
				gamemodeList.updatePosition(screenWidth);
				gamemodeList.draw(g);

				// Label
				g.setColor(mc.sayda.mcraze.graphics.Color.white);
				String label = "Game Mode:";
				g.drawString(label, gamemodeList.getX(), gamemodeList.getY() - 4);
			}

			if (rulesList != null) {
				rulesList.updatePosition(screenWidth);
				rulesList.draw(g);

				// Label
				g.setColor(mc.sayda.mcraze.graphics.Color.white);
				String label = "Game Rules:";
				g.drawString(label, rulesList.getX(), rulesList.getY() - 4);
			}
		}

		// Draw world selection list if in world select menu
		if (currentState == MenuState.WORLD_SELECT && worldList != null) {
			worldList.updatePosition(screenWidth);
			worldList.draw(g);

			// Draw world action buttons
			if (worldActionButtons != null) {
				for (Button button : worldActionButtons) {
					button.updatePosition(screenWidth);
					button.updateHover(mouseX, mouseY);
					button.draw(g);
				}
			}
		} else {
			// Draw regular buttons for other menus
			for (Button button : currentButtons) {
				button.updatePosition(screenWidth); // Center buttons dynamically
				button.updateHover(mouseX, mouseY);
				button.draw(g);
			}
		}

		// Render shared settings if in OPTIONS menu
		if (currentState == MenuState.OPTIONS && sharedSettings != null) {
			sharedSettings.renderSettings(g, mouseX, mouseY, false);
		}

		// Handle clicks
		if (game.getClient().leftClick) {
			game.getClient().leftClick = false;
			boolean settingsClicked = false;

			// Check IP input click first
			if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
				ipInput.handleClick(g, mouseX, mouseY);
			}

			// Check world name input click
			if (currentState == MenuState.WORLD_CREATE) {
				if (worldNameInput != null)
					worldNameInput.handleClick(g, mouseX, mouseY);
				if (noiseModifierInput != null)
					noiseModifierInput.handleClick(g, mouseX, mouseY);

				if (gamemodeList != null && gamemodeList.handleClick(mouseX, mouseY)) {
					String modeName = gamemodeList.getSelectedItem();
					if (modeName != null) {
						try {
							selectedGameMode = mc.sayda.mcraze.world.GameMode.valueOf(modeName);
							updateGameModeList();
						} catch (Exception e) {
						}
					}
					settingsClicked = true;
				}

				if (rulesList != null && rulesList.handleClick(mouseX, mouseY)) {
					String key = rulesList.getSelectedItem();
					if (key != null) {
						switch (key) {
							case "keepInventory":
								selectedKeepInventory = !selectedKeepInventory;
								break;
							case "daylightCycle":
								selectedDaylightCycle = !selectedDaylightCycle;
								break;
							case "spelunking":
								selectedSpelunking = !selectedSpelunking;
								break;
							case "mobgriefing":
								selectedMobGriefing = !selectedMobGriefing;
								break;
						}
						updateRulesList();
						rulesList.clearSelection();
					}
					settingsClicked = true;
				}
			}

			// Handle settings clicks in OPTIONS menu
			if (currentState == MenuState.OPTIONS && sharedSettings != null) {
				settingsClicked = sharedSettings.handleClick(mouseX, mouseY, screenWidth);
			}

			// Handle world list clicks in WORLD_SELECT menu
			if (currentState == MenuState.WORLD_SELECT && worldList != null) {
				if (worldList.handleClick(mouseX, mouseY)) {
					// Selection changed - rebuild action buttons to update enabled state
					buildWorldActionButtons();
					// Don't handle other clicks
					settingsClicked = true;
				}

				// Check world action button clicks
				if (!settingsClicked && worldActionButtons != null) {
					for (Button button : worldActionButtons) {
						if (button.handleClick(mouseX, mouseY)) {
							settingsClicked = true;
							break;
						}
					}
				}
			}

			// Then check regular button clicks (only if nothing else was clicked)
			if (!settingsClicked) {
				for (Button button : currentButtons) {
					if (button.handleClick(mouseX, mouseY)) {
						break; // Only handle one click
					}
				}
			}
		}
	}

	/**
	 * Handle keyboard input (for text input fields)
	 */
	public void handleKeyTyped(char c) {
		if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
			ipInput.handleKeyTyped(c);
		} else if (currentState == MenuState.WORLD_CREATE) {
			if (worldNameInput != null)
				worldNameInput.handleKeyTyped(c);
			if (noiseModifierInput != null)
				noiseModifierInput.handleKeyTyped(c);
		}
	}

	/**
	 * Handle key pressed (for arrow keys, delete, ctrl+c/v, etc.)
	 */
	public void handleKeyPressed(int keyCode, boolean shiftPressed, boolean ctrlPressed) {
		if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
			ipInput.handleKeyPressed(keyCode, shiftPressed, ctrlPressed);
		} else if (currentState == MenuState.WORLD_CREATE) {
			if (worldNameInput != null)
				worldNameInput.handleKeyPressed(keyCode, shiftPressed, ctrlPressed);
			if (noiseModifierInput != null)
				noiseModifierInput.handleKeyPressed(keyCode, shiftPressed, ctrlPressed);
		}
	}

	/**
	 * Handle mouse wheel scroll
	 */
	public void handleMouseWheel(int mouseX, int mouseY, int wheelRotation) {
		if (currentState == MenuState.WORLD_SELECT && worldList != null) {
			worldList.handleScroll(mouseX, mouseY, wheelRotation);
		} else if (currentState == MenuState.WORLD_CREATE) {
			if (gamemodeList != null) {
				gamemodeList.handleScroll(mouseX, mouseY, wheelRotation);
			}
			if (rulesList != null) {
				rulesList.handleScroll(mouseX, mouseY, wheelRotation);
			}
		}
	}

	/**
	 * Get the title for the current menu state
	 */
	private String getMenuTitle() {
		switch (currentState) {
			case MAIN:
				return null; // No title for main menu (logo is shown)
			case SINGLEPLAYER:
				return "Singleplayer";
			case WORLD_CREATE:
				return "Create New World";
			case WORLD_SELECT:
				return "Select World";
			case MULTIPLAYER:
				return "Connect to Server";
			case OPTIONS:
				return "Options";
			default:
				return null;
		}
	}
}
