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

package mc.sayda.mcraze.ui.screen;

import mc.sayda.mcraze.ui.component.*;
import mc.sayda.mcraze.graphics.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.player.data.*;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.client.Client;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.server.Server;
import mc.sayda.mcraze.world.World;

/**
 * F3 debug overlay showing technical information
 */
public class DebugOverlay {
	private final mc.sayda.mcraze.logging.GameLogger logger = mc.sayda.mcraze.logging.GameLogger.get();
	public static final String GAME_VERSION = "Alpha 1.0";

	private Game game;
	private boolean visible = false;

	public DebugOverlay(Game game) {
		this.game = game;
	}

	/**
	 * Toggle debug overlay visibility
	 */
	public void toggle() {
		visible = !visible;
		if (logger != null)
			logger.info("Debug overlay (F3): " + (visible ? "Enabled" : "Disabled"));
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/**
	 * Check if overlay is visible
	 */
	public boolean isVisible() {
		return visible;
	}

	/**
	 * Draw the debug overlay
	 */
	public void draw(GraphicsHandler g, Client client, Server server) {
		if (!visible) {
			return;
		}

		// Semi-transparent background for readability
		g.setColor(new Color(0, 0, 0, 128));
		g.fillRect(2, 2, 300, 220);

		int y = 10;
		int lineHeight = 14;
		g.setColor(Color.white);

		// Game version
		g.drawString("MCraze " + GAME_VERSION, 5, y);
		y += lineHeight;

		// Separator line
		y += 4;

		// FPS (from client.getFrameDeltaMs())
		if (client != null) {
			long fps = client.getFrameDeltaMs() > 0 ? 1000 / client.getFrameDeltaMs() : 0;
			g.drawString("FPS: " + fps + " (" + client.getFrameDeltaMs() + "ms)", 5, y);
			y += lineHeight;
		}

		// Player coordinates and velocity
		Player player = null;
		if (client != null) {
			player = client.getLocalPlayer();
		}

		if (player != null) {
			g.drawString(String.format("XY: %.2f / %.2f", player.x, player.y), 5, y);
			y += lineHeight;

			g.drawString(String.format("Velocity: %.3f / %.3f", player.dx, player.dy), 5, y);
			y += lineHeight;

			// Player state
			String state = player.dead ? "Dead" : "Alive";
			g.drawString("State: " + state, 5, y);
			y += lineHeight;
		}

		// Separator line
		y += 4;

		// World info
		if (server != null && server.world != null) {
			World world = server.world;

			// World dimensions
			g.drawString("World: " + world.width + "x" + world.height, 5, y);
			y += lineHeight;

			// Seed
			long seed = world.getSeed();
			if (seed != 0) {
				g.drawString("Seed: " + seed, 5, y);
				y += lineHeight;
			}

			// Time of day
			long ticks = world.getTicksAlive();
			long day = ticks / 20000 + 1;
			long timeOfDay = ticks % 20000;
			String timeStr = formatTime(timeOfDay);
			g.drawString("Day " + day + ", " + timeStr, 5, y);
			y += lineHeight;

			// Biome at player location
			if (player != null) {
				try {
					int biomeX = (int) player.x;
					int biomeY = (int) player.y;
					String biomeName = world.getBiomeAt(biomeX, biomeY);
					g.drawString("Biome: " + biomeName, 5, y);
					y += lineHeight;
				} catch (Exception e) {
					// getBiomeAt() may not be implemented yet
					g.drawString("Biome: Unknown", 5, y);
					y += lineHeight;
				}
			}
		}

		// Separator line
		y += 4;

		// Connection info
		if (client != null) {
			if (server != null) {
				// Integrated server or LAN
				if (server.isLANEnabled()) {
					g.drawString("Mode: LAN (Port " + server.getLANPort() + ")", 5, y);
				} else {
					g.drawString("Mode: Integrated Server", 5, y);
				}
			} else {
				// Connected to dedicated server
				g.drawString("Mode: Multiplayer Client", 5, y);
			}
			y += lineHeight;
		}

		// Memory usage (optional, useful for debugging)
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
		long totalMemory = runtime.totalMemory() / 1024 / 1024;
		long maxMemory = runtime.maxMemory() / 1024 / 1024;
		g.drawString("Memory: " + usedMemory + "/" + totalMemory + " MB (Max: " + maxMemory + ")", 5, y);
		y += lineHeight;
	}

	/**
	 * Format time of day to HH:MM
	 */
	private String formatTime(long timeOfDay) {
		// 0 = dawn (06:00), 5000 = noon (12:00), 10000 = dusk (18:00), 15000 = midnight
		// (00:00)
		// 20000 ticks = 24 hours
		double hours = (timeOfDay / 20000.0) * 24.0 + 6.0; // Offset by 6 hours so 0 ticks = 06:00
		if (hours >= 24.0) {
			hours -= 24.0;
		}

		int hour = (int) hours;
		int minute = (int) ((hours - hour) * 60);

		return String.format("%02d:%02d", hour, minute);
	}
}
