/*
 * Copyright 2012 Jonathan Leahey
 * 
 * This file is part of Minicraft
 * 
 * Minicraft is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Minicraft is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Minicraft. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda;

import java.util.ArrayList;
import java.util.Random;

import mc.sayda.Constants.TileID;
import mc.sayda.entity.Entity;
import mc.sayda.entity.Player;
import mc.sayda.item.InventoryItem;
import mc.sayda.item.Item;
import mc.sayda.item.Tool;
import mc.sayda.system.BlockInteractionSystem;
import mc.sayda.ui.MainMenu;
import mc.sayda.ui.UIRenderer;
import mc.sayda.util.Int2;
import mc.sayda.util.StockMethods;
import mc.sayda.util.SystemTimer;
import mc.sayda.world.World;

public class Game {
	
	private int worldWidth = 512;
	private int worldHeight = 256;
	private boolean gameRunning = true;
	public boolean leftClick = false;
	public boolean rightClick = false;
	public boolean paused = true;
	
	public ArrayList<Entity> entities = new ArrayList<Entity>();
	
	private int tileSize = 32;

	private UIRenderer uiRenderer;
	private BlockInteractionSystem blockInteractionSystem;
	
	public boolean viewFPS = false;
	private boolean inMenu = true;
	private MainMenu menu;
	public long ticksRunning;
	private Random random = new Random();
	
	public Player player;
	public World world;
	
	public MusicPlayer musicPlayer = new MusicPlayer("sounds/music.ogg");
	public Int2 screenMousePos = new Int2(0, 0);
	
	/**
	 * Construct our game and set it running.
	 */
	public Game() {
		uiRenderer = new UIRenderer();
		blockInteractionSystem = new BlockInteractionSystem(random);
		menu = new MainMenu(this, uiRenderer);
		GraphicsHandler.get().init(this);
		System.gc();
	}
	
	/**
	 * Start a fresh game, this should clear out any old data and
	 * create a new set.
	 */
	public void startGame(boolean load, int width) {
		inMenu = false;
		if (load) {
			System.out.println("Loading world, width: " + worldWidth);
		} else {
			System.out.println("Creating world, width: " + width);
			worldWidth = width;
		}
		
		entities.clear();
		if (load) {
			// check to see loading is possible (and if so load)
			load = SaveLoad.doLoad(this);
		}
		
		if (load) {
			for (Entity entity : entities) {
				if (entity instanceof Player) {
					player = (Player) entity;
					player.widthPX = 7 * (tileSize / 8);
					player.heightPX = 14 * (tileSize / 8);
				}
			}
		}
		if (!load) {
			// make a new world and player
			world = new World(worldWidth, worldHeight, random);
			player = new Player(true, world.spawnLocation.x, world.spawnLocation.y,
					7 * (tileSize / 8), 14 * (tileSize / 8));
			entities.add(player);
			if (Constants.DEBUG) {
				player.giveItem(Constants.itemTypes.get((char) 175).clone(), 1);
				player.giveItem(Constants.itemTypes.get((char) 88).clone(), 1);
				player.giveItem(Constants.itemTypes.get((char) 106).clone(), 64);
			}
		}

		musicPlayer.play();
		System.gc();
	}

	public void gameLoop() {
		long lastLoopTime = System.currentTimeMillis();
		
		if (Constants.DEBUG) {
			startGame(false, 512);
		}
		
		// keep looping round till the game ends
		while (gameRunning) {
			ticksRunning++;
			long delta = SystemTimer.getTime() - lastLoopTime;
			lastLoopTime = SystemTimer.getTime();
			
			GraphicsHandler g = GraphicsHandler.get();
			g.startDrawing();

			if (inMenu) {
				menu.draw(g);
				uiRenderer.drawMouse(g, screenMousePos);
				g.finishDrawing();
				
				SystemTimer.sleep(lastLoopTime + 16 - SystemTimer.getTime());
				continue;
			}
			final int screenWidth = g.getScreenWidth();
			final int screenHeight = g.getScreenHeight();
			float cameraX = player.x - screenWidth / tileSize / 2;
			float cameraY = player.y - screenHeight / tileSize / 2;
			float worldMouseX = (cameraX * tileSize + screenMousePos.x) / tileSize;
			float worldMouseY = (cameraY * tileSize + screenMousePos.y) / tileSize - .5f;
			
			world.chunkUpdate();
			world.draw(g, 0, 0, screenWidth, screenHeight, cameraX, cameraY, tileSize);
			
			boolean inventoryFocus = player.inventory.updateInventory(screenWidth, screenHeight,
					screenMousePos, leftClick, rightClick);
			if (inventoryFocus) {
				leftClick = false;
				rightClick = false;
			}

			// Handle block breaking
			blockInteractionSystem.handleBlockBreaking(g, player, world, entities,
					cameraX, cameraY, tileSize, leftClick);

			// Handle block placing
			if (rightClick) {
				if (blockInteractionSystem.handleBlockPlacing(player, world, tileSize)) {
					rightClick = false;
				}
			}
			
			player.updateHand(g, cameraX, cameraY, worldMouseX, worldMouseY, world, tileSize);
			
			java.util.Iterator<Entity> it = entities.iterator();
			while (it.hasNext()) {
				Entity entity = it.next();
				if (entity != player && player.collidesWith(entity, tileSize)) {
					if (entity instanceof Item || entity instanceof Tool) {
						player.giveItem((Item) entity, 1);
					}
					it.remove();
					continue;
				}
				entity.updatePosition(world, tileSize);
				entity.draw(g, cameraX, cameraY, screenWidth, screenHeight, tileSize);
			}

			if (viewFPS) {
				uiRenderer.drawFPS(g, delta);
			}

			// Draw the UI
			uiRenderer.drawBuildMineIcons(g, player, cameraX, cameraY, tileSize);
			
			// draw the hotbar, and optionally the inventory screen
			player.inventory.draw(g, screenWidth, screenHeight);

			// draw the mouse
			Int2 mouseTest = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, tileSize,
					tileSize, tileSize, worldMouseX, worldMouseY);
			uiRenderer.drawMouse(g, mouseTest);

			// Draw health bar and air bubbles
			uiRenderer.drawHealthBar(g, player, screenWidth, screenHeight);
			uiRenderer.drawAirBubbles(g, player, world, tileSize, screenWidth, screenHeight);
			
			g.finishDrawing();
			
			SystemTimer.sleep(lastLoopTime + 16 - SystemTimer.getTime());
		}
	}

	public void zoom(int level) {
		if (level == 0) {
			if (tileSize < 32) {
				zoom(1);
				zoom(0);
			}
			if (tileSize > 32) {
				zoom(-1);
				zoom(0);
			}
		} else if (level == 1) {
			if (tileSize < 128) {
				tileSize = tileSize * 2;
				for (Entity entity : entities) {
					entity.widthPX *= 2;
					entity.heightPX *= 2;
				}
				for (Item item : Constants.itemTypes.values()) {
					item.widthPX *= 2;
					item.heightPX *= 2;
				}
			}
		} else if (level == -1) {
			if (tileSize > 8) {
				tileSize = tileSize / 2;
				for (Entity entity : entities) {
					entity.widthPX /= 2;
					entity.heightPX /= 2;
				}
				for (Item item : Constants.itemTypes.values()) {
					item.widthPX /= 2;
					item.heightPX /= 2;
				}
			}
		}
	}
	
	public void tossItem() {
		// TODO: move this into Player
		InventoryItem inventoryItem = player.inventory.selectedItem();
		if (!inventoryItem.isEmpty()) {
			Item newItem = inventoryItem.getItem();
			if (!(newItem instanceof Tool)) {
				newItem = (Item) newItem.clone();
			}
			inventoryItem.remove(1);
			if (player.facingRight) {
				newItem.x = player.x + 1 + random.nextFloat();
			} else {
				newItem.x = player.x - 1 - random.nextFloat();
			}
			newItem.y = player.y;
			newItem.dy = -.1f;
			entities.add(newItem);
		}
	}
	
	public void goToMainMenu() {
		zoom(0);
		SaveLoad.doSave(this);
		musicPlayer.pause();
		inMenu = true; // go back to the main menu
	}
	
	public void quit() {
		musicPlayer.close();
		System.exit(0);
	}
	
	/**
	 * The entry point into the game. We'll simply create an
	 * instance of class which will start the display and game
	 * loop.
	 * 
	 * @param argv
	 *            The arguments that are passed into our game
	 */
	public static void main(String argv[]) {
		// really simple argument parsing
		for (String arg : argv) {
			if (arg.equals("-d") || arg.equals("--debug")) {
				Constants.DEBUG = true;
			} else {
				System.err.println("Unrecognized argument: "+arg);
			}
		}
		// initialize the game state
		Game g = new Game();
		
		// Start the main game loop, note: this method will not
		// return until the game has finished running. Hence we are
		// using the actual main thread to run the game.
		g.gameLoop();
	}
}
