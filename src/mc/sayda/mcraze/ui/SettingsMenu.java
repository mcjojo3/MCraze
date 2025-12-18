package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings menu with volume controls and other options
 */
public class SettingsMenu {
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = 30;
	private static final int BUTTON_SPACING = 10;

	private Game game;
	private UIRenderer uiRenderer;
	private List<Button> buttons;
	private PauseMenu pauseMenu;

	public SettingsMenu(Game game, UIRenderer uiRenderer, PauseMenu pauseMenu) {
		this.game = game;
		this.uiRenderer = uiRenderer;
		this.pauseMenu = pauseMenu;
		this.buttons = new ArrayList<>();
		buildMenu();
	}

	/**
	 * Build the settings menu buttons
	 */
	private void buildMenu() {
		buttons.clear();

		int startY = 200;
		int buttonIndex = 0;

		// Volume down button
		Button volumeDownBtn = new Button(
			"volume_down",
			"-",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::volumeDown);

		// Volume up button
		Button volumeUpBtn = new Button(
			"volume_up",
			"+",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * buttonIndex++,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::volumeUp);

		buttons.add(volumeDownBtn);
		buttons.add(volumeUpBtn);

		// Back button
		Button backBtn = new Button(
			"back",
			"Back",
			startY + (BUTTON_HEIGHT + BUTTON_SPACING) * (buttonIndex + 2),
			200,
			40
		).setOnClick(this::goBack);

		buttons.add(backBtn);
	}

	/**
	 * Decrease volume by 10%
	 */
	private void volumeDown() {
		if (game.getClient() != null && game.getClient().musicPlayer != null) {
			float currentVolume = game.getClient().musicPlayer.getVolume();
			game.getClient().musicPlayer.setVolume(currentVolume - 0.1f);
		}
	}

	/**
	 * Increase volume by 10%
	 */
	private void volumeUp() {
		if (game.getClient() != null && game.getClient().musicPlayer != null) {
			float currentVolume = game.getClient().musicPlayer.getVolume();
			game.getClient().musicPlayer.setVolume(currentVolume + 0.1f);
		}
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
		int titleX = g.getScreenWidth() / 2 - (title.length() * 8) / 2;
		g.drawString(title, titleX, 150);

		// Draw volume label and value
		String volumeLabel = "Music Volume:";
		int volumeLabelX = g.getScreenWidth() / 2 - (volumeLabel.length() * 8) / 2;
		g.drawString(volumeLabel, volumeLabelX, 185);

		// Get and display current volume
		float currentVolume = 0.5f;
		if (game.getClient() != null && game.getClient().musicPlayer != null) {
			currentVolume = game.getClient().musicPlayer.getVolume();
		}
		int volumePercent = Math.round(currentVolume * 100);
		String volumeText = volumePercent + "%";
		int volumeTextX = g.getScreenWidth() / 2 - (volumeText.length() * 8) / 2;
		g.drawString(volumeText, volumeTextX, 220);

		// Update button positions and draw them
		int mouseX = game.getClient().screenMousePos.x;
		int mouseY = game.getClient().screenMousePos.y;
		int screenWidth = g.getScreenWidth();

		// Draw volume buttons (arranged horizontally)
		if (buttons.size() >= 2) {
			Button volumeDownBtn = buttons.get(0);
			Button volumeUpBtn = buttons.get(1);

			// Manually draw buttons at specific positions
			int centerX = screenWidth / 2;
			int btnY = 205;

			// Draw volume down button
			volumeDownBtn.updateHover(mouseX - (centerX - 100), mouseY - btnY);
			g.setColor(Color.DARK_GRAY);
			g.fillRect(centerX - 100, btnY, BUTTON_WIDTH, BUTTON_HEIGHT);
			g.setColor(Color.white);
			g.drawString("-", centerX - 100 + (BUTTON_WIDTH / 2) - 4, btnY + 15);

			// Draw volume up button
			volumeUpBtn.updateHover(mouseX - (centerX + 40), mouseY - btnY);
			g.setColor(Color.DARK_GRAY);
			g.fillRect(centerX + 40, btnY, BUTTON_WIDTH, BUTTON_HEIGHT);
			g.setColor(Color.white);
			g.drawString("+", centerX + 40 + (BUTTON_WIDTH / 2) - 4, btnY + 15);
		}

		// Draw back button (centered)
		if (buttons.size() >= 3) {
			Button backBtn = buttons.get(2);
			backBtn.updatePosition(screenWidth);
			backBtn.updateHover(mouseX, mouseY);
			backBtn.draw(g);
		}

		// Handle clicks manually for volume buttons
		if (game.getClient().leftClick) {
			game.getClient().leftClick = false;

			int centerX = screenWidth / 2;
			int btnY = 205;

			// Check volume down button click
			if (mouseX >= centerX - 100 && mouseX <= centerX - 100 + BUTTON_WIDTH &&
				mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT) {
				volumeDown();
			}

			// Check volume up button click
			if (mouseX >= centerX + 40 && mouseX <= centerX + 40 + BUTTON_WIDTH &&
				mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT) {
				volumeUp();
			}

			// Handle back button click normally
			if (buttons.size() >= 3) {
				buttons.get(2).handleClick(mouseX, mouseY);
			}
		}

		// Draw mouse cursor
		uiRenderer.drawMouse(g, game.getClient().screenMousePos);
	}
}
