package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.SpriteStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Main menu system with dynamic button support
 */
public class MainMenu {
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
	private static final Sprite MENU_BG_TILE = SpriteStore.get().getSprite("sprites/tiles/dirt.png");
	private static final Sprite MENU_LOGO = SpriteStore.get().getSprite("sprites/menus/title.png");
	private static final Sprite MENU_TAG = SpriteStore.get().getSprite("sprites/menus/tag.png");

	// Button constants
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 40;
	private static final int BUTTON_SPACING = 10;

	// World size constants
	private static final int WORLD_SIZE_SMALL = 256;
	private static final int WORLD_SIZE_MEDIUM = 512;
	private static final int WORLD_SIZE_LARGE = 1024;

	private Game game;
	private UIRenderer uiRenderer;
	private MenuState currentState;
	private List<Button> currentButtons;
	private TextInput ipInput;  // For multiplayer connection
	private TextInput worldNameInput;  // For world creation
	private int selectedWorldSize = WORLD_SIZE_MEDIUM;  // Default world size
	private long ticksRunning = 0;

	public MainMenu(Game g, UIRenderer uiRenderer) {
		this.game = g;
		this.uiRenderer = uiRenderer;
		this.currentState = MenuState.MAIN;
		this.currentButtons = new ArrayList<>();
		buildMainMenu();
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
			BUTTON_HEIGHT
		).setOnClick(this::showSingleplayerMenu);

		// Multiplayer button
		Button multiplayerBtn = new Button(
			"multiplayer",
			"Multiplayer",
			startY + BUTTON_HEIGHT + BUTTON_SPACING,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::showMultiplayerMenu);

		// Options button
		Button optionsBtn = new Button(
			"options",
			"Options",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::showOptionsMenu);

		// Exit button
		Button exitBtn = new Button(
			"exit",
			"Exit Game",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(() -> game.quit());

		currentButtons.add(singleplayerBtn);
		currentButtons.add(multiplayerBtn);
		currentButtons.add(optionsBtn);
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
			BUTTON_HEIGHT
		).setOnClick(this::showWorldCreationMenu);

		// Load World button
		Button loadBtn = new Button(
			"load",
			"Load World",
			startY + BUTTON_HEIGHT + BUTTON_SPACING,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::showWorldSelectionMenu);

		// Back button
		Button backBtn = new Button(
			"back",
			"Back",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::buildMainMenu);

		currentButtons.add(createBtn);
		currentButtons.add(loadBtn);
		currentButtons.add(backBtn);
	}

	/**
	 * Show world creation menu (name input + size selection)
	 */
	private void showWorldCreationMenu() {
		currentButtons.clear();
		currentState = MenuState.WORLD_CREATE;

		int startY = 200;

		// Create world name input
		worldNameInput = new TextInput("world_name", startY, BUTTON_WIDTH, BUTTON_HEIGHT, 20);
		worldNameInput.setText("New World");

		// World size buttons
		Button smallBtn = new Button(
			"small",
			"Small (256x256)",
			startY + BUTTON_HEIGHT + BUTTON_SPACING * 3,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(() -> {
			selectedWorldSize = WORLD_SIZE_SMALL;
			createNewWorld();
		});

		Button mediumBtn = new Button(
			"medium",
			"Medium (512x512)",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 2 + BUTTON_SPACING * 2,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(() -> {
			selectedWorldSize = WORLD_SIZE_MEDIUM;
			createNewWorld();
		});

		Button largeBtn = new Button(
			"large",
			"Large (1024x1024)",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 3 + BUTTON_SPACING * 2,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(() -> {
			selectedWorldSize = WORLD_SIZE_LARGE;
			createNewWorld();
		});

		// Back button
		Button backBtn = new Button(
			"back",
			"Back",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 5,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::showSingleplayerMenu);

		currentButtons.add(smallBtn);
		currentButtons.add(mediumBtn);
		currentButtons.add(largeBtn);
		currentButtons.add(backBtn);
	}

	/**
	 * Show world selection menu (list of saved worlds)
	 */
	private void showWorldSelectionMenu() {
		currentButtons.clear();
		currentState = MenuState.WORLD_SELECT;
		worldNameInput = null;

		int startY = 180;

		// Get available worlds
		List<mc.sayda.mcraze.world.WorldSaveManager.WorldMetadata> worlds =
			mc.sayda.mcraze.world.WorldSaveManager.getAvailableWorlds();

		if (worlds.isEmpty()) {
			// No worlds found, show message
			Button backBtn = new Button(
				"back",
				"Back",
				startY + BUTTON_HEIGHT + BUTTON_SPACING,
				BUTTON_WIDTH,
				BUTTON_HEIGHT
			).setOnClick(this::showSingleplayerMenu);

			currentButtons.add(backBtn);
		} else {
			// Show world buttons (max 5 visible at a time)
			int maxVisible = Math.min(5, worlds.size());
			for (int i = 0; i < maxVisible; i++) {
				final mc.sayda.mcraze.world.WorldSaveManager.WorldMetadata world = worlds.get(i);

				Button worldBtn = new Button(
					"world_" + i,
					world.worldName,
					startY + i * (BUTTON_HEIGHT + BUTTON_SPACING),
					BUTTON_WIDTH,
					BUTTON_HEIGHT
				).setOnClick(() -> loadExistingWorld(world.worldName));

				currentButtons.add(worldBtn);
			}

			// Back button below world list
			Button backBtn = new Button(
				"back",
				"Back",
				startY + maxVisible * (BUTTON_HEIGHT + BUTTON_SPACING) + BUTTON_SPACING,
				BUTTON_WIDTH,
				BUTTON_HEIGHT
			).setOnClick(this::showSingleplayerMenu);

			currentButtons.add(backBtn);
		}
	}

	/**
	 * Build the multiplayer connection menu
	 */
	private void showMultiplayerMenu() {
		currentButtons.clear();
		currentState = MenuState.MULTIPLAYER;

		int startY = 200;

		// Create IP input field
		ipInput = new TextInput("ip_input", startY, BUTTON_WIDTH, BUTTON_HEIGHT, 32);
		ipInput.setText("localhost:25565");  // Default

		// Connect button
		Button connectBtn = new Button(
			"connect",
			"Connect",
			startY + BUTTON_HEIGHT + BUTTON_SPACING * 2,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::connectToServer);

		// Back button
		Button backBtn = new Button(
			"back",
			"Back",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 4,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::buildMainMenu);

		currentButtons.add(connectBtn);
		currentButtons.add(backBtn);
	}

	/**
	 * Build the options menu
	 */
	private void showOptionsMenu() {
		currentButtons.clear();
		currentState = MenuState.OPTIONS;
		ipInput = null;  // Clear IP input when leaving multiplayer menu

		int startY = 200;

		// TODO: Add actual options here (sound, graphics, controls, etc.)

		// Placeholder text - options would go here

		// Back button
		Button backBtn = new Button(
			"back",
			"Back",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * 4,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::buildMainMenu);

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
		List<mc.sayda.mcraze.world.WorldSaveManager.WorldMetadata> worlds =
			mc.sayda.mcraze.world.WorldSaveManager.getAvailableWorlds();
		for (mc.sayda.mcraze.world.WorldSaveManager.WorldMetadata world : worlds) {
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

		game.startGame(worldName, false, selectedWorldSize, username, password);
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
		if (mc.sayda.mcraze.world.PlayerDataManager.exists(worldName, username)) {
			mc.sayda.mcraze.world.PlayerData data =
				mc.sayda.mcraze.world.PlayerDataManager.authenticate(worldName, username, password);
			if (data == null) {
				// Wrong password
				System.err.println("Wrong password for world '" + worldName + "'");
				return;
			}
		}
		// If playerdata doesn't exist, it will auto-register

		game.startGame(worldName, true, WORLD_SIZE_MEDIUM, username, password);  // Size doesn't matter for loading
	}

	/**
	 * Start a new game with the specified world size (legacy method)
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
		if (ipInput == null) return;

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

		// Draw tiled background
		uiRenderer.drawTileBackground(g, MENU_BG_TILE, 32);

		// Draw logo
		uiRenderer.drawCenteredX(g, MENU_LOGO, 70, 397, 50);

		// Draw animated tag
		float tagScale = ((float) Math.abs((ticksRunning % 100) - 50)) / 50 + 1;
		MENU_TAG.draw(g, 450, 70, (int) (60 * tagScale), (int) (37 * tagScale));

		// Draw menu title based on state
		g.setColor(mc.sayda.mcraze.Color.white);
		String menuTitle = getMenuTitle();
		if (menuTitle != null) {
			int titleX = g.getScreenWidth() / 2 - (menuTitle.length() * 7) / 2;
			g.drawString(menuTitle, titleX, 150);
		}

		// Update button positions and draw them
		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		int screenWidth = g.getScreenWidth();

		// Draw IP input if in multiplayer menu
		if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
			ipInput.updatePosition(screenWidth);
			ipInput.draw(g);
		}

		// Draw world name input if in world creation menu
		if (currentState == MenuState.WORLD_CREATE && worldNameInput != null) {
			worldNameInput.updatePosition(screenWidth);
			worldNameInput.draw(g);

			// Draw label above input
			g.setColor(mc.sayda.mcraze.Color.white);
			String label = "World Name:";
			int labelX = screenWidth / 2 - (label.length() * 7) / 2;
			worldNameInput.updatePosition(screenWidth);
			g.drawString(label, labelX, worldNameInput.getY() - 20);
		}

		// Draw "No worlds found" message if in world select with no worlds
		if (currentState == MenuState.WORLD_SELECT && currentButtons.size() <= 1) {
			g.setColor(mc.sayda.mcraze.Color.white);
			String text = "No saved worlds found";
			int textX = screenWidth / 2 - (text.length() * 8) / 2;
			g.drawString(text, textX, 200);
		}

		for (Button button : currentButtons) {
			button.updatePosition(screenWidth);  // Center buttons dynamically
			button.updateHover(mouseX, mouseY);
			button.draw(g);
		}

		// Handle clicks
		if (game.getClient().leftClick) {
			game.getClient().leftClick = false;

			// Check IP input click first
			if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
				ipInput.handleClick(mouseX, mouseY);
			}

			// Check world name input click
			if (currentState == MenuState.WORLD_CREATE && worldNameInput != null) {
				worldNameInput.handleClick(mouseX, mouseY);
			}

			// Then check button clicks
			for (Button button : currentButtons) {
				if (button.handleClick(mouseX, mouseY)) {
					break;  // Only handle one click
				}
			}
		}

		// Draw options placeholder text
		if (currentState == MenuState.OPTIONS) {
			g.setColor(mc.sayda.mcraze.Color.white);
			String text = "Options menu - Coming soon!";
			int textX = g.getScreenWidth() / 2 - (text.length() * 8) / 2;
			g.drawString(text, textX, 250);
		}
	}

	/**
	 * Handle keyboard input (for text input fields)
	 */
	public void handleKeyTyped(char c) {
		if (currentState == MenuState.MULTIPLAYER && ipInput != null) {
			ipInput.handleKeyTyped(c);
		} else if (currentState == MenuState.WORLD_CREATE && worldNameInput != null) {
			worldNameInput.handleKeyTyped(c);
		}
	}

	/**
	 * Get the title for the current menu state
	 */
	private String getMenuTitle() {
		switch (currentState) {
			case MAIN:
				return null;  // No title for main menu (logo is shown)
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
