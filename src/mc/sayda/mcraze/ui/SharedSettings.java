package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared settings logic used by both MainMenu OPTIONS and in-game SettingsMenu.
 * Centralizes settings UI rendering and interaction to ensure consistency.
 */
public class SharedSettings {
	private static final int BUTTON_WIDTH = 60;
	private static final int BUTTON_HEIGHT = 30;
	private static final int BUTTON_SPACING = 10;
	private static final int TOGGLE_BUTTON_WIDTH = 200;
	private static final int TOGGLE_BUTTON_HEIGHT = 40;

	private Game game;
	private UIRenderer uiRenderer;
	private List<Button> buttons;

	public SharedSettings(Game game, UIRenderer uiRenderer) {
		this.game = game;
		this.uiRenderer = uiRenderer;
		this.buttons = new ArrayList<>();
		buildSettingsButtons();
	}

	/**
	 * Build all settings buttons
	 */
	private void buildSettingsButtons() {
		buttons.clear();

		// Volume down button
		Button volumeDownBtn = new Button(
			"volume_down",
			"-",
			205,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::volumeDown);

		// Volume up button
		Button volumeUpBtn = new Button(
			"volume_up",
			"+",
			205,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		).setOnClick(this::volumeUp);

		buttons.add(volumeDownBtn);
		buttons.add(volumeUpBtn);

		// Music toggle button
		Button musicToggleBtn = new Button(
			"music_toggle",
			getMusicButtonText(),
			260,
			TOGGLE_BUTTON_WIDTH,
			TOGGLE_BUTTON_HEIGHT
		).setOnClick(this::toggleMusic);

		buttons.add(musicToggleBtn);

		// FPS toggle button
		Button fpsToggleBtn = new Button(
			"fps_toggle",
			getFPSButtonText(),
			310,
			TOGGLE_BUTTON_WIDTH,
			TOGGLE_BUTTON_HEIGHT
		).setOnClick(this::toggleFPS);

		buttons.add(fpsToggleBtn);
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
	 * Toggle music mute state (same as M keybind)
	 */
	private void toggleMusic() {
		if (game.getClient() != null && game.getClient().musicPlayer != null) {
			// Use the same method as the M keybind to ensure consistency
			game.getClient().musicPlayer.toggleSound();

			// Update button text (also updated on every render at line 207)
			if (buttons.size() >= 3) {
				buttons.get(2).setText(getMusicButtonText());
			}
		}
	}

	/**
	 * Toggle FPS display
	 */
	private void toggleFPS() {
		if (game.getClient() != null) {
			game.getClient().toggleFPS();

			// Update button text
			if (buttons.size() >= 4) {
				buttons.get(3).setText(getFPSButtonText());
			}
		}
	}

	/**
	 * Get music button text based on current state
	 */
	private String getMusicButtonText() {
		if (game.getClient() != null && game.getClient().musicPlayer != null) {
			return game.getClient().musicPlayer.isMuted() ? "Music: Off" : "Music: On";
		}
		return "Music: On";
	}

	/**
	 * Get FPS button text based on current state
	 */
	private String getFPSButtonText() {
		if (game.getClient() != null) {
			return game.getClient().isShowingFPS() ? "Show FPS: On" : "Show FPS: Off";
		}
		return "Show FPS: Off";
	}

	/**
	 * Render the settings UI (shared between MainMenu and SettingsMenu)
	 *
	 * @param g Graphics handler
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @param handleClicks Whether to process clicks (set to false if caller handles clicks separately)
	 */
	public void renderSettings(GraphicsHandler g, int mouseX, int mouseY, boolean handleClicks) {
		int screenWidth = g.getScreenWidth();

		// Draw volume label and value
		g.setColor(Color.white);
		String volumeLabel = "Music Volume:";
		int volumeLabelX = screenWidth / 2 - g.getStringWidth(volumeLabel) / 2;
		g.drawString(volumeLabel, volumeLabelX, 185);

		// Get and display current volume
		float currentVolume = 0.5f;
		if (game.getClient() != null && game.getClient().musicPlayer != null) {
			currentVolume = game.getClient().musicPlayer.getVolume();
		}
		int volumePercent = Math.round(currentVolume * 100);
		String volumeText = volumePercent + "%";
		int volumeTextX = screenWidth / 2 - g.getStringWidth(volumeText) / 2;
		g.drawString(volumeText, volumeTextX, 220);

		// Draw volume buttons (arranged horizontally)
		if (buttons.size() >= 2) {
			Button volumeDownBtn = buttons.get(0);
			Button volumeUpBtn = buttons.get(1);

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

		// Draw toggle buttons (music, FPS)
		if (buttons.size() >= 4) {
			Button musicToggleBtn = buttons.get(2);
			Button fpsToggleBtn = buttons.get(3);

			// Update button text to reflect current state
			musicToggleBtn.setText(getMusicButtonText());
			fpsToggleBtn.setText(getFPSButtonText());

			// Draw music toggle
			musicToggleBtn.updatePosition(screenWidth);
			musicToggleBtn.updateHover(mouseX, mouseY);
			musicToggleBtn.draw(g);

			// Draw FPS toggle
			fpsToggleBtn.updatePosition(screenWidth);
			fpsToggleBtn.updateHover(mouseX, mouseY);
			fpsToggleBtn.draw(g);
		}

		// Handle clicks if requested
		if (handleClicks && game.getClient() != null && game.getClient().leftClick) {
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

			// Handle toggle buttons clicks
			for (int i = 2; i < buttons.size(); i++) {
				if (buttons.get(i).handleClick(mouseX, mouseY)) {
					break;
				}
			}
		}
	}

	/**
	 * Handle clicks on settings buttons (for external click handling)
	 *
	 * @param mouseX Mouse X position
	 * @param mouseY Mouse Y position
	 * @param screenWidth Screen width for centering calculations
	 * @return true if a button was clicked
	 */
	public boolean handleClick(int mouseX, int mouseY, int screenWidth) {
		int centerX = screenWidth / 2;
		int btnY = 205;

		// Check volume down button click
		if (mouseX >= centerX - 100 && mouseX <= centerX - 100 + BUTTON_WIDTH &&
			mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT) {
			volumeDown();
			return true;
		}

		// Check volume up button click
		if (mouseX >= centerX + 40 && mouseX <= centerX + 40 + BUTTON_WIDTH &&
			mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT) {
			volumeUp();
			return true;
		}

		// Handle toggle buttons clicks
		for (int i = 2; i < buttons.size(); i++) {
			if (buttons.get(i).handleClick(mouseX, mouseY)) {
				return true;
			}
		}

		return false;
	}
}
