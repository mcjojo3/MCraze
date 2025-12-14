/*
 * Copyright 2025 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda;

import mc.sayda.client.Client;
import mc.sayda.network.LocalConnection;
import mc.sayda.server.Server;
import mc.sayda.util.SystemTimer;

/**
 * Main game coordinator - creates and runs integrated server + client
 */
public class Game {
	private Server server;
	private Client client;

	private boolean gameRunning = true;

	/**
	 * Construct game with integrated server
	 */
	public Game() {
		System.out.println("=== Starting MCraze with Integrated Server ===");

		// Create local connection pair
		LocalConnection[] connections = LocalConnection.createPair();
		LocalConnection serverConnection = connections[1];
		LocalConnection clientConnection = connections[0];

		// Create server and client
		server = new Server(serverConnection);
		client = new Client(clientConnection, server);

		// Connect systems
		server.setChat(client.chat);
		client.setGame(this);  // Set Game reference for UI components

		System.out.println("Integrated server initialized");
		System.gc();
	}

	/**
	 * Start a new game or load existing save
	 */
	public void startGame(boolean load, int width) {
		if (load) {
			// Try to load from save file
			boolean loaded = SaveLoad.doLoad(this);
			if (loaded) {
				System.out.println("Game loaded from save file");
			} else {
				// If load failed, start new game
				System.out.println("Failed to load save, starting new game");
				server.startGame(width);
			}
		} else {
			// Start new game
			server.startGame(width);
		}

		client.startGame();
	}

	/**
	 * Main game loop - runs server tick and client render
	 */
	public void gameLoop() {
		long lastLoopTime = System.currentTimeMillis();

		if (Constants.DEBUG) {
			startGame(false, 512);
		}

		// Main loop
		while (gameRunning && server.isRunning() && client.isRunning()) {
			long delta = SystemTimer.getTime() - lastLoopTime;
			lastLoopTime = SystemTimer.getTime();

			// Server tick (game logic)
			server.tick();

			// Client render (graphics)
			client.render();

			// Sleep to maintain ~60 FPS
			SystemTimer.sleep(lastLoopTime + 16 - SystemTimer.getTime());
		}
	}

	/**
	 * Submit chat message/command to server
	 */
	public void submitChat() {
		if (client == null || client.chat == null) return;

		String input = client.chat.submit();
		if (input != null && !input.trim().isEmpty()) {
			// Send chat message to server via packet
			mc.sayda.network.packet.PacketChatSend packet =
				new mc.sayda.network.packet.PacketChatSend(input);
			client.connection.sendPacket(packet);
		}
		client.chat.setOpen(false);
	}

	/**
	 * Save the current game state
	 */
	public void saveGame() {
		SaveLoad.doSave(this);
		System.out.println("Game saved");
	}

	/**
	 * Delegate methods for backward compatibility
	 */
	public void quit() {
		server.stop();
		client.stop();
		client.musicPlayer.close();
		System.exit(0);
	}

	// Expose server/client for event handlers
	public Server getServer() {
		return server;
	}

	public Client getClient() {
		return client;
	}

	/**
	 * Main entry point
	 */
	public static void main(String argv[]) {
		// Parse arguments
		for (String arg : argv) {
			if (arg.equals("-d") || arg.equals("--debug")) {
				Constants.DEBUG = true;
			} else {
				System.err.println("Unrecognized argument: " + arg);
			}
		}

		// Create and start game
		Game g = new Game();
		g.gameLoop();
	}
}
