package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.SpriteStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Loading screen displayed during world generation/loading
 */
public class LoadingScreen {
	// Background sprite
	private static final Sprite BG_TILE = SpriteStore.get().getSprite("assets/sprites/tiles/dirt.png");

	// Loading messages
	private List<String> messages;
	private String currentStatus;
	private int targetProgress; // The actual progress set by the game (0-100)
	private float visualProgress; // The displayed progress for smooth animation
	private long ticksRunning = 0;
	private int maxMessages = 10;

	public LoadingScreen() {
		this.messages = new ArrayList<>();
		this.currentStatus = "Initializing...";
		this.targetProgress = 0;
		this.visualProgress = 0;
	}

	/**
	 * Add a message to the console output
	 */
	public void addMessage(String message) {
		messages.add(message);
		// Keep only the last N messages
		while (messages.size() > maxMessages) {
			messages.remove(0);
		}
	}

	/**
	 * Set the current status message
	 */
	public void setStatus(String status) {
		this.currentStatus = status;
		addMessage(status);
	}

	/**
	 * Set loading progress (0-100)
	 */
	public void setProgress(int progress) {
		this.targetProgress = Math.max(0, Math.min(100, progress));
	}

	/**
	 * Force loading progress (0-100) without interpolation/smoothing
	 */
	public void forceProgress(int progress) {
		this.targetProgress = Math.max(0, Math.min(100, progress));
		this.visualProgress = this.targetProgress;
	}

	/**
	 * Update animation
	 */
	public void tick() {
		ticksRunning++;

		// Smoothly interpolate visual progress towards target progress
		if (visualProgress < targetProgress) {
			visualProgress += 2.0f; // Speed of filling (faster than before to feel responsive but smooth)
			if (visualProgress > targetProgress)
				visualProgress = targetProgress;
		} else if (visualProgress > targetProgress) {
			visualProgress = targetProgress; // Snap if going backwards
		}
	}

	/**
	 * Draw the loading screen
	 */
	public void draw(GraphicsHandler g) {
		int screenWidth = g.getScreenWidth();
		int screenHeight = g.getScreenHeight();

		// Draw tiled background
		int tileSize = 32;
		for (int x = 0; x < screenWidth; x += tileSize) {
			for (int y = 0; y < screenHeight; y += tileSize) {
				g.drawImage(BG_TILE, x, y, tileSize, tileSize);
			}
		}

		// Dark overlay for better text visibility
		g.setColor(new Color(0, 0, 0, 180));
		g.fillRect(0, 0, screenWidth, screenHeight);

		// Draw title
		g.setColor(Color.white);
		String title = "MCraze - Loading";
		int titleX = screenWidth / 2 - g.getStringWidth(title) / 2;
		g.drawString(title, titleX, 100);

		// Draw current status with animated dots
		int dots = (int) ((ticksRunning / 10) % 4);
		String statusText = currentStatus;
		for (int i = 0; i < dots; i++) {
			statusText += ".";
		}
		int statusX = screenWidth / 2 - g.getStringWidth(statusText) / 2;
		g.drawString(statusText, statusX, 150);

		// Draw progress bar
		int barWidth = 400;
		int barHeight = 30;
		int barX = screenWidth / 2 - barWidth / 2;
		int barY = 200;

		// Progress bar background
		g.setColor(Color.darkGray);
		g.fillRect(barX, barY, barWidth, barHeight);

		// Progress bar fill (using smooth visualProgress)
		g.setColor(Color.green);
		int fillWidth = (int) ((barWidth * visualProgress) / 100);
		g.fillRect(barX, barY, fillWidth, barHeight);

		// Progress bar border
		g.setColor(Color.white);
		g.drawRect(barX, barY, barWidth, barHeight);

		// Progress percentage
		String progressText = (int) visualProgress + "%";
		int progressX = screenWidth / 2 - g.getStringWidth(progressText) / 2;
		g.drawString(progressText, progressX, barY + barHeight / 2 - 4);

		// Draw console messages
		g.setColor(Color.lightGray);
		int messageY = 280;
		for (String message : messages) {
			// Truncate long messages
			if (message.length() > 80) {
				message = message.substring(0, 77) + "...";
			}
			g.drawString(message, 50, messageY);
			messageY += 16;
		}
	}
}
