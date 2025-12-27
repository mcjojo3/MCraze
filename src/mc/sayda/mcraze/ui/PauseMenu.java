package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.SpriteStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Pause menu system with Continue, Save, and Exit buttons
 */
public class PauseMenu {
	// Menu sprites
	private static final Sprite MENU_BG_TILE = SpriteStore.get().getSprite("sprites/tiles/dirt.png");

	// Button constants
	private static final int BUTTON_WIDTH = 200;
	private static final int BUTTON_HEIGHT = 40;
	private static final int BUTTON_SPACING = 10;

	private Game game;
	private UIRenderer uiRenderer;
	private List<Button> buttons;

	public PauseMenu(Game game, UIRenderer uiRenderer) {
		this.game = game;
		this.uiRenderer = uiRenderer;
		this.buttons = new ArrayList<>();
		buildMenu();
	}

	/**
	 * Build the pause menu buttons
	 */
	private void buildMenu() {
		buttons.clear();

		int startY = 200;
		int buttonIndex = 0;

		// Continue button
		Button continueBtn = new Button(
			"continue",
			"Continue",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex++,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::resumeGame);

		// Settings button
		Button settingsBtn = new Button(
			"settings",
			"Settings",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex++,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::openSettings);

		buttons.add(continueBtn);
		buttons.add(settingsBtn);

		// Open to LAN button (only for integrated servers - not available on remote servers)
		// Disabled if connected as client (no local server)
		boolean isHost = (game.getServer() != null);
		if (isHost) {
			Button lanBtn = new Button(
				"lan",
				getLANButtonText(),
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex++,
				BUTTON_WIDTH,
				BUTTON_HEIGHT
			).setOnClick(this::toggleLAN)
			 .setEnabled(true);  // Always enabled for hosts
			buttons.add(lanBtn);
		} else {
			// Show disabled LAN button for clients
			Button lanBtn = new Button(
				"lan",
				"Open to LAN (Host Only)",
				startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex++,
				BUTTON_WIDTH,
				BUTTON_HEIGHT
			).setEnabled(false);  // Grayed out for clients
			buttons.add(lanBtn);
		}

		// Exit button
		Button exitBtn = new Button(
			"exit",
			"Exit to Main Menu",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex++,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::exitToMainMenu);

		buttons.add(exitBtn);
	}

	/**
	 * Resume the game (close pause menu)
	 */
	private void resumeGame() {
		if (game.getClient() != null) {
			game.getClient().closePauseMenu();
		}
	}

	/**
	 * Open settings menu
	 */
	private void openSettings() {
		if (game.getClient() != null) {
			game.getClient().openSettingsMenu();
		}
	}

	/**
	 * Get the text for the LAN button based on current state
	 */
	private String getLANButtonText() {
		if (game.getServer() != null && game.getServer().isLANEnabled()) {
			return "LAN: Enabled (Port " + game.getServer().getLANPort() + ")";
		}
		return "Open to LAN";
	}

	/**
	 * Toggle LAN server on/off
	 */
	private void toggleLAN() {
		if (game.getServer() == null) {
			return;
		}

		if (game.getServer().isLANEnabled()) {
			// Disable LAN
			game.getServer().disableLAN();
			System.out.println("LAN server disabled");
			if (game.getClient() != null && game.getClient().chat != null) {
				game.getClient().chat.addMessage("LAN server disabled", mc.sayda.mcraze.Color.orange);
			}
		} else {
			// Enable LAN on default port
			int port = 25565;
			boolean success = game.getServer().enableLAN(port);
			if (success) {
				System.out.println("LAN server enabled on port " + port);
				if (game.getClient() != null && game.getClient().chat != null) {
					game.getClient().chat.addMessage("LAN server opened on port " + port, mc.sayda.mcraze.Color.green);
					game.getClient().chat.addMessage("Other players can now connect!", mc.sayda.mcraze.Color.green);
				}
			} else {
				System.err.println("Failed to enable LAN server");
				if (game.getClient() != null && game.getClient().chat != null) {
					game.getClient().chat.addMessage("Failed to open LAN server", new mc.sayda.mcraze.Color(255, 100, 100));
				}
			}
		}

		// Rebuild menu to update button text
		buildMenu();
	}

	/**
	 * Exit to main menu
	 */
	private void exitToMainMenu() {
		if (game.getClient() != null) {
			// Disconnect from multiplayer if connected
			if (game.getServer() == null) {
				game.getClient().disconnectMultiplayer();
			}
			game.getClient().closePauseMenu();
			game.getClient().goToMainMenu();
		}
	}

	/**
	 * Draw the pause menu
	 */
	public void draw(GraphicsHandler g) {
		// Semi-transparent overlay to darken the game behind
		g.setColor(new mc.sayda.mcraze.Color(0, 0, 0, 150));
		g.fillRect(0, 0, g.getScreenWidth(), g.getScreenHeight());

		// Draw title
		g.setColor(mc.sayda.mcraze.Color.white);
		String title = "Game Paused";
		int titleX = g.getScreenWidth() / 2 - (title.length() * 8) / 2;
		g.drawString(title, titleX, 150);

		// Update button positions and draw them
		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		int screenWidth = g.getScreenWidth();

		for (Button button : buttons) {
			button.updatePosition(screenWidth);
			button.updateHover(mouseX, mouseY);
			button.draw(g);
		}

		// Handle clicks
		if (game.getClient().leftClick) {
			game.getClient().leftClick = false;

			for (Button button : buttons) {
				if (button.handleClick(mouseX, mouseY)) {
					break;  // Only handle one click
				}
			}
		}

		// Draw mouse cursor
		uiRenderer.drawMouse(g, game.getClient().screenMousePos);
	}

	/**
	 * Refresh menu state (update button enabled states)
	 */
	public void refresh() {
		// No buttons need conditional enabling anymore
	}
}
