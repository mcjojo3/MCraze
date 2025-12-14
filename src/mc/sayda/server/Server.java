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

package mc.sayda.server;

import java.util.ArrayList;
import java.util.Random;

import mc.sayda.Constants;
import mc.sayda.entity.Entity;
import mc.sayda.entity.Player;
import mc.sayda.item.Item;
import mc.sayda.item.Tool;
import mc.sayda.network.Connection;
import mc.sayda.network.Packet;
import mc.sayda.network.PacketHandler;
import mc.sayda.network.packet.*;
import mc.sayda.system.BlockInteractionSystem;
import mc.sayda.ui.CommandHandler;
import mc.sayda.world.World;

/**
 * Server handles all game logic and world state.
 * In singleplayer, this runs locally. In multiplayer, this runs on the server.
 */
public class Server implements PacketHandler {
	// World state
	public World world;
	public ArrayList<Entity> entities = new ArrayList<>();
	public Player player;  // TODO: Support multiple players in multiplayer

	// Game settings
	private int worldWidth = 512;
	private int worldHeight = 256;
	public boolean keepInventory = false;
	public boolean daylightCycle = true;
	private float spawnX = 0;
	private float spawnY = 0;

	// Systems
	private BlockInteractionSystem blockInteractionSystem;
	private mc.sayda.ui.CommandHandler commandHandler;
	private mc.sayda.ui.Chat chat;  // Reference to client's chat for command output
	private Random random = new Random();
	private int tileSize = 32;

	// Network
	private Connection connection;

	// Game state
	private boolean running = true;
	public long ticksRunning = 0;
	private boolean deathHandled = false;

	public Server(Connection connection) {
		this.connection = connection;
		this.blockInteractionSystem = new BlockInteractionSystem(random);
	}

	/**
	 * Set chat reference for command output
	 */
	public void setChat(mc.sayda.ui.Chat chat) {
		this.chat = chat;
		this.commandHandler = new mc.sayda.ui.CommandHandler(this, chat);
		chat.setCommandHandler(commandHandler);
	}

	/**
	 * Start a new game
	 */
	public void startGame(int width) {
		worldWidth = width;
		entities.clear();
		deathHandled = false;

		// Create world and player
		world = new World(worldWidth, worldHeight, random);
		spawnX = world.spawnLocation.x;
		spawnY = world.spawnLocation.y;
		player = new Player(true, spawnX, spawnY, 7 * (tileSize / 8), 14 * (tileSize / 8));
		entities.add(player);

		// Debug items
		if (Constants.DEBUG) {
			player.giveItem(Constants.itemTypes.get((char) 175).clone(), 1);
			player.giveItem(Constants.itemTypes.get((char) 88).clone(), 1);
			player.giveItem(Constants.itemTypes.get((char) 106).clone(), 64);
		}

		System.out.println("Server: Game started, world size " + width);
	}

	/**
	 * Server tick - process packets and update game state
	 */
	public void tick() {
		if (!running) return;

		ticksRunning++;

		// Process incoming packets from client
		Packet[] packets = connection.receivePackets();
		for (Packet packet : packets) {
			packet.handle(this);
		}

		// Update world
		if (world != null) {
			world.chunkUpdate(daylightCycle);
		}

		// Check for player death
		if (player != null && player.dead && !deathHandled) {
			handlePlayerDeath();
			deathHandled = true;
		}

		// Update entities
		java.util.Iterator<Entity> it = entities.iterator();
		while (it.hasNext()) {
			Entity entity = it.next();

			// Check collision with player
			if (entity != player && player != null && player.collidesWith(entity, tileSize)) {
				if (entity instanceof Item || entity instanceof Tool) {
					player.giveItem((Item) entity, 1);
				}
				it.remove();
				continue;
			}

			// Update entity position
			if (world != null) {
				entity.updatePosition(world, tileSize);
			}
		}

		// TODO: Send state update packets to clients
	}

	/**
	 * Handle player death - drop items
	 */
	private void handlePlayerDeath() {
		if (!keepInventory && player != null) {
			ArrayList<Item> droppedItems = player.dropAllItems(random);
			entities.addAll(droppedItems);
			System.out.println("Server: Dropped " + droppedItems.size() + " items on death");
		} else {
			System.out.println("Server: Kept inventory on death (keepInventory = true)");
		}
		System.out.println("Server: Player died");
	}

	/**
	 * Respawn the player
	 */
	public void respawnPlayer() {
		if (player != null) {
			player.respawn(spawnX, spawnY);
			deathHandled = false;
			System.out.println("Server: Player respawned");
		}
	}

	/**
	 * Toss an item from player inventory
	 */
	public void tossItem() {
		if (player != null) {
			Item tossedItem = player.tossSelectedItem(random);
			if (tossedItem != null) {
				entities.add(tossedItem);
			}
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void stop() {
		running = false;
	}

	// ===== Packet Handlers =====

	@Override
	public void handlePlayerInput(PacketPlayerInput packet) {
		if (player == null) return;

		// Apply player input
		if (packet.moveLeft) {
			player.startLeft(false);  // TODO: Handle shift from packet
		} else {
			player.stopLeft();
		}

		if (packet.moveRight) {
			player.startRight(false);  // TODO: Handle shift from packet
		} else {
			player.stopRight();
		}

		if (packet.climb) {
			player.startClimb();
		} else {
			player.endClimb();
		}

		// Set hotbar slot
		player.setHotbarItem(packet.hotbarSlot);

		// Handle block interactions
		if (world != null) {
			float worldMouseX = packet.mouseX;
			float worldMouseY = packet.mouseY;

			// Block breaking
			if (packet.leftClick) {
				blockInteractionSystem.handleBlockBreaking(null, player, world, entities,
						0, 0, tileSize, true);  // TODO: Pass proper camera coords
			}

			// Block placing
			if (packet.rightClick) {
				blockInteractionSystem.handleBlockPlacing(player, world, tileSize);
			}
		}
	}

	@Override
	public void handleBlockChange(PacketBlockChange packet) {
		if (world == null) return;

		// Validate and apply block change
		if (packet.isBreak) {
			// TODO: Validate player can break this block
			world.removeTile(packet.x, packet.y);
		} else {
			// TODO: Validate player can place this block
			// Note: packet.newTileId is a char representing the TileID
			mc.sayda.Constants.TileID tileId = mc.sayda.Constants.TileID.values()[packet.newTileId];
			world.addTile(packet.x, packet.y, tileId);
		}
	}

	@Override
	public void handleChatSend(PacketChatSend packet) {
		System.out.println("Server: Received chat: " + packet.message);

		// Process through command handler
		if (commandHandler != null) {
			commandHandler.executeCommand(packet.message);
		} else {
			// Fallback: echo back to client
			connection.sendPacket(new PacketChatMessage(packet.message, mc.sayda.Color.white));
		}
	}

	// Client-bound packet handlers (server doesn't handle these)
	@Override
	public void handleWorldUpdate(PacketWorldUpdate packet) {}

	@Override
	public void handleEntityUpdate(PacketEntityUpdate packet) {}

	@Override
	public void handleChatMessage(PacketChatMessage packet) {}
}
