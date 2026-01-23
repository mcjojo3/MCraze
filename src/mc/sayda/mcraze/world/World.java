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

package mc.sayda.mcraze.world;

import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.gen.*;
import mc.sayda.mcraze.world.storage.*;

import java.util.Random;

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.Constants.TileID;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.system.LightingEngine;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;

public class World implements java.io.Serializable {
	private static final long serialVersionUID = 1L;

	public Tile[][] tiles;
	public int width;
	public int height;
	public Int2 spawnLocation;
	public Int2 flagLocation = null;

	private int chunkNeedsUpdate;
	private int chunkCount;
	private int chunkWidth = Constants.WORLD_CHUNK_SIZE;
	private volatile boolean chunkFillRight = true;
	private Random random;
	private static final mc.sayda.mcraze.logging.GameLogger logger = mc.sayda.mcraze.logging.GameLogger.get();

	// Performance: Reuse list for block changes instead of allocating every tick
	private final java.util.ArrayList<BlockChange> blockChangesCache = new java.util.ArrayList<>();
	private long seed; // World generation seed
	private Biome[] biomeMap; // One biome per X column
	private long ticksAlive = 0;
	private LightingEngine lightingEngineSun;
	private LightingEngine lightingEngineSourceBlocks;

	// Gamerules (synced to clients)
	public boolean spelunking = false; // Full light everywhere (no darkness at all)
	public boolean darkness = true; // Enable pitch-black nights (false = dimmer but not pitch black)
	public int daylightSpeed = 40000; // Day length in ticks (default: 40000 = ~33 min)
	public boolean keepInventory = false; // Keep items on death
	public boolean daylightCycle = true; // Enable day/night cycle
	public boolean mobGriefing = true; // Enable mob block destruction/pickup
	public boolean pvp = true; // Enable player-vs-player damage
	public boolean insomnia = false; // Enable bed usage (respawn/day-skip)

	// Wave Status (Synced from server for client display)
	public int waveDay = 0;
	public boolean waveActive = false;
	public float diffMultHP = 1.0f; // Current difficulty multiplier for HP
	public float diffMultDmg = 1.0f; // Current difficulty multiplier for Damage
	public float diffMultJump = 1.0f; // Current difficulty multiplier for Jump Height
	// Game Mode (SURVIVAL, CLASSIC, etc.)
	public GameMode gameMode = GameMode.CLASSIC;

	// Chest storage (position -> chest data)
	private java.util.Map<String, ChestData> chests = new java.util.HashMap<>();

	// Furnace storage (position -> furnace data)
	private java.util.Map<String, FurnaceData> furnaces = new java.util.HashMap<>();

	// Alchemy storage (position -> alchemy data)
	private java.util.Map<String, AlchemyData> alchemies = new java.util.HashMap<>();

	// Flower Pot storage (position -> pot data)
	private java.util.Map<String, PotData> pots = new java.util.HashMap<>();

	// Entity data loaded from world.dat (Version 4+), to be instantiated by
	// SharedWorld
	public java.util.ArrayList<mc.sayda.mcraze.world.storage.WorldSaveManager.EntityData> pendingEntities = new java.util.ArrayList<>();

	// PERFORMANCE: Pre-allocated Color objects for lighting tints
	// Indexed by light intensity (0-255), eliminates 72,000 allocations/sec
	private Color[] lightTintCache = new Color[256];
	private Color[] backdropTintCache = new Color[256];

	public long getTicksAlive() {
		return ticksAlive;
	}

	public void setTicksAlive(long ticksAlive) {
		this.ticksAlive = ticksAlive;
	}

	/**
	 * Get ticks to skip to reach the next occurrence of targetTime.
	 */
	public long getNextTime(int targetTime) {
		int currentInDay = (int) (ticksAlive % daylightSpeed);
		if (currentInDay < targetTime) {
			return targetTime - currentInDay;
		} else {
			return (daylightSpeed - currentInDay) + targetTime;
		}
	}

	public GameMode getGameMode() {
		return gameMode;
	}

	public long getSeed() {
		return seed;
	}

	public void setSeed(long seed) {
		this.seed = seed;
	}

	/**
	 * Get biome at a specific location
	 */
	public String getBiomeAt(int x, int y) {
		if (biomeMap == null || x < 0 || x >= width) {
			return "Unknown";
		}
		return biomeMap[x].getDisplayName();
	}

	/**
	 * Get biome at a specific X coordinate
	 */
	public Biome getBiome(int x) {
		if (biomeMap == null || x < 0 || x >= width) {
			return Biome.PLAINS;
		}
		return biomeMap[x];
	}

	/**
	 * Get the entire biome map
	 */
	public Biome[] getBiomeMap() {
		return biomeMap;
	}

	/**
	 * Set the biome map (used by clients receiving biome data from server)
	 */
	public void setBiomeMap(Biome[] biomeMap) {
		this.biomeMap = biomeMap;
	}

	// private int[] columnHeights;

	/**
	 * Get tile at the specified coordinates safely.
	 * Returns AIR tile if coordinates are out of bounds.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @return The tile at (x,y) or AIR if invalid
	 */
	public Tile getTile(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			// Return a dummy AIR tile to prevent NPEs
			// We create a new one to avoid modifying a shared static instance
			if (Constants.tileTypes.get(TileID.AIR) != null) {
				return new Tile(Constants.tileTypes.get(TileID.AIR).type);
			} else {
				return new Tile(new TileType("assets/sprites/tiles/air.png", TileID.AIR)); // Fallback
			}
		}
		if (tiles[x][y] == null) {
			// Should not happen as array is initialized with AIR, but safe guard
			if (Constants.tileTypes.get(TileID.AIR) != null) {
				return new Tile(Constants.tileTypes.get(TileID.AIR).type);
			} else {
				return new Tile(new TileType("assets/sprites/tiles/air.png", TileID.AIR));
			}
		}
		return tiles[x][y];
	}

	public void setTile(int x, int y, TileID id) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return;
		}
		if (id == null) {
			// If setting to null (AIR), use appropriate AIR tile
			id = TileID.AIR;
		}

		Tile template = Constants.tileTypes.get(id);
		if (template != null) {
			// CRITICAL FIX: Preserve existing backdrop
			TileType existingBackdrop = tiles[x][y] != null ? tiles[x][y].backdropType : null;
			tiles[x][y] = new Tile(template.type);
			tiles[x][y].backdropType = existingBackdrop;
		}

		// Trigger lighting updates immediately
		lightingEngineSun.addedTile(x, y);
		lightingEngineSourceBlocks.addedTile(x, y);
	}

	/**
	 * Set tile directly without triggering lighting updates.
	 * Used for batch processing from network packets.
	 */
	public void setTileNoUpdate(int x, int y, TileID id) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return;
		}
		if (id == null) {
			id = TileID.AIR;
		}

		Tile template = Constants.tileTypes.get(id);
		if (template != null) {
			// CRITICAL FIX: Preserve existing backdrop
			TileType existingBackdrop = tiles[x][y] != null ? tiles[x][y].backdropType : null;
			tiles[x][y] = new Tile(template.type);
			tiles[x][y].backdropType = existingBackdrop;
		}
	}

	/**
	 * Trigger lighting updates for a specific tile.
	 */
	public void updateLightingAt(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return;
		}
		lightingEngineSun.addedTile(x, y);
		lightingEngineSourceBlocks.addedTile(x, y);
	}

	public World(int width, int height, Random random, GameMode gameMode) {
		this.gameMode = gameMode;
		// Store seed for display in debug menu
		this.seed = random.nextLong();
		random.setSeed(this.seed); // Use the stored seed

		// Generate biome map before terrain generation
		this.biomeMap = WorldGenerator.generateBiomes(width, random);

		mc.sayda.mcraze.world.gen.WorldGenerator.GeneratorType genType = mc.sayda.mcraze.world.gen.WorldGenerator.GeneratorType.DEFAULT;
		if (gameMode == GameMode.SKYBLOCK) {
			genType = mc.sayda.mcraze.world.gen.WorldGenerator.GeneratorType.VOID;
		}

		TileID[][] generated = WorldGenerator.generate(width, height, random, this.biomeMap, this, genType);
		WorldGenerator.visibility = null;
		// Store suggested spawn from WorldGenerator
		Int2 suggestedSpawn = WorldGenerator.playerLocation;
		tiles = new Tile[width][height];
		// columnHeights = new int[width];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				Tile tile = Constants.tileTypes.get(generated[i][j]);
				if (tile == null) {
					// Create NEW tile instance (not shared reference)
					TileType airType = Constants.tileTypes.get(TileID.AIR).type;
					tiles[i][j] = new Tile(airType);
				} else {
					// Create NEW tile instance (not shared reference)
					tiles[i][j] = new Tile(tile.type);
				}

				// Apply backdrop from WorldGenerator (set during cave carving)
				if (WorldGenerator.backdrops != null && WorldGenerator.backdrops[i][j] != null) {
					Tile backdropTile = Constants.tileTypes.get(WorldGenerator.backdrops[i][j]);
					if (backdropTile != null) {
						tiles[i][j].backdropType = backdropTile.type;
					}
				}
			}
		}
		this.width = width;
		this.height = height;
		this.chunkCount = (int) Math.ceil((double) width / chunkWidth);
		this.chunkNeedsUpdate = 0;
		this.random = random;

		// Find a safe spawn location using the suggested spawn as starting point
		this.spawnLocation = findSafeSpawn(suggestedSpawn.x);
		if (logger != null)
			logger.info("World: Spawn location set to (" + spawnLocation.x + ", " + spawnLocation.y + ")");

		// PERFORMANCE: Pre-allocate all Color objects for lighting
		// Initialize cache before lighting engines to be safe/consistent
		for (int i = 0; i < 256; i++) {
			lightTintCache[i] = new Color(16, 16, 16, 255 - i);
			int backdropLight = (int) (i * 0.6);
			backdropTintCache[i] = new Color(16, 16, 16, 255 - backdropLight);
		}

		lightingEngineSun = new LightingEngine(width, height, tiles, true);
		lightingEngineSourceBlocks = new LightingEngine(width, height, tiles, false);
	}

	/**
	 * Create an empty world (all AIR) for multiplayer clients
	 * The server will populate this world with tile data
	 */
	public static World createEmpty(int width, int height, Random random) {
		if (logger != null)
			logger.info("World.createEmpty: Starting (" + width + "x" + height + ")");

		World world = new World();
		world.width = width;
		world.height = height;
		world.tiles = new Tile[width][height];
		world.random = random;

		if (logger != null && logger.isDebugEnabled())
			logger.debug("World.createEmpty: Filling with AIR tiles...");
		// Fill with AIR tiles (each position gets unique Tile instance)
		TileType airType = Constants.tileTypes.get(TileID.AIR).type;
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				world.tiles[i][j] = new Tile(airType);
			}
		}

		if (logger != null && logger.isDebugEnabled())
			logger.debug("World.createEmpty: Setting spawn location...");
		// Set default spawn location (will be updated by server)
		world.spawnLocation = new Int2(width / 2, height / 2);
		world.chunkCount = (int) Math.ceil((double) width / world.chunkWidth);
		world.chunkNeedsUpdate = 0;

		// PERFORMANCE: Pre-allocate all Color objects for lighting
		world.lightTintCache = new Color[256];
		world.backdropTintCache = new Color[256];
		for (int i = 0; i < 256; i++) {
			world.lightTintCache[i] = new Color(16, 16, 16, 255 - i);
			int backdropLight = (int) (i * 0.6);
			world.backdropTintCache[i] = new Color(16, 16, 16, 255 - backdropLight);
		}

		if (logger != null && logger.isDebugEnabled())
			logger.debug("World.createEmpty: Creating lighting engines...");
		world.lightingEngineSun = new LightingEngine(width, height, world.tiles, true);
		if (logger != null && logger.isDebugEnabled())
			logger.debug("World.createEmpty: Sun lighting engine created");
		world.lightingEngineSourceBlocks = new LightingEngine(width, height, world.tiles, false);
		if (logger != null && logger.isDebugEnabled())
			logger.debug("World.createEmpty: Block lighting engine created");

		if (logger != null)
			logger.info("World.createEmpty: Complete!");
		return world;
	}

	/**
	 * Private constructor for createEmpty factory method
	 */
	private World() {
		// Empty constructor for factory method
	}

	/**
	 * Constructor for loading from save data
	 */
	public World(TileID[][] tileData, Random random) {
		this.width = tileData.length;
		this.height = tileData[0].length;
		this.tiles = new Tile[width][height];
		this.random = random;

		// CRITICAL FIX: Initialize seed (defaults to 0 if not set by loader)
		// Loader should call setSeed() after construction to restore saved seed
		this.seed = 0;

		// Convert TileID array to Tile array
		// IMPORTANT: Create NEW Tile instances, don't reuse templates!
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				Tile templateTile = Constants.tileTypes.get(tileData[i][j]);
				if (templateTile == null) {
					templateTile = Constants.tileTypes.get(TileID.AIR);
				}
				// Create a new Tile with the template's TileType
				tiles[i][j] = new Tile(templateTile.type);
			}
		}

		// Find safe spawn location (solid ground with air above)
		this.spawnLocation = findSafeSpawn(width / 2);

		this.chunkCount = (int) Math.ceil((double) width / chunkWidth);
		this.chunkNeedsUpdate = 0;

		// PERFORMANCE: Pre-allocate all Color objects for lighting
		for (int i = 0; i < 256; i++) {
			lightTintCache[i] = new Color(16, 16, 16, 255 - i);
			int backdropLight = (int) (i * 0.6);
			backdropTintCache[i] = new Color(16, 16, 16, 255 - backdropLight);
		}

		lightingEngineSun = new LightingEngine(width, height, tiles, true);
		lightingEngineSourceBlocks = new LightingEngine(width, height, tiles, false);
	}

	/**
	 * Force a refresh of all lighting (call after loading a world)
	 */
	public void refreshLighting() {
		if (logger != null)
			logger.info("World.refreshLighting: Recreating lighting engines...");
		// Recreate lighting engines to force recalculation
		lightingEngineSun = new LightingEngine(width, height, tiles, true);
		if (logger != null)
			logger.info("World.refreshLighting: Sun lighting engine recreated");
		lightingEngineSourceBlocks = new LightingEngine(width, height, tiles, false);
		if (logger != null)
			logger.info("World.refreshLighting: Block lighting engine recreated");
	}

	// Methods for async saving (Deep copy)
	public Constants.TileID[][] cloneTiles() {
		Constants.TileID[][] clone = new Constants.TileID[width][height];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (tiles[i][j] != null && tiles[i][j].type != null) {
					clone[i][j] = tiles[i][j].type.name;
				} else {
					clone[i][j] = Constants.TileID.AIR;
				}
			}
		}
		return clone;
	}

	public Constants.TileID[][] cloneBackdrops() {
		Constants.TileID[][] clone = new Constants.TileID[width][height];
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (tiles[i][j] != null && tiles[i][j].backdropType != null) {
					clone[i][j] = tiles[i][j].backdropType.name;
				}
			}
		}
		return clone;
	}

	public java.util.Map<String, ChestData> cloneChests() {
		java.util.Map<String, ChestData> clone = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, ChestData> entry : chests.entrySet()) {
			// Manual deep copy of ChestData if clone() is not available
			ChestData original = entry.getValue();
			ChestData copy = new ChestData(original.x, original.y);
			// Copy items
			for (int i = 0; i < 10; i++) {
				for (int j = 0; j < 3; j++) {
					if (original.items[i][j] != null) {
						// InventoryItem copy constructor or manual copy?
						// Assuming InventoryItem is mutable, we need deep copy.
						// But Item is immutable (Type/ID).
						mc.sayda.mcraze.item.Item itemType = original.items[i][j].getItem();
						int count = original.items[i][j].getCount();
						copy.items[i][j] = new mc.sayda.mcraze.item.InventoryItem(itemType);
						copy.items[i][j].setCount(count);
					}
				}
			}
			clone.put(entry.getKey(), copy);
		}
		return clone;
	}

	public java.util.Map<String, FurnaceData> cloneFurnaces() {
		java.util.Map<String, FurnaceData> clone = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, FurnaceData> entry : furnaces.entrySet()) {
			FurnaceData original = entry.getValue();
			FurnaceData copy = new FurnaceData(original.x, original.y);

			// Copy smelting state
			copy.fuelTimeRemaining = original.fuelTimeRemaining;
			copy.smeltProgress = original.smeltProgress;
			copy.smeltTimeTotal = original.smeltTimeTotal;
			copy.currentRecipe = original.currentRecipe;

			// Copy items (3x2 grid)
			for (int i = 0; i < 3; i++) {
				for (int j = 0; j < 2; j++) {
					if (original.items[i][j] != null) {
						mc.sayda.mcraze.item.Item itemType = original.items[i][j].getItem();
						int count = original.items[i][j].getCount();
						copy.items[i][j] = new mc.sayda.mcraze.item.InventoryItem(itemType);
						copy.items[i][j].setCount(count);
					}
				}
			}
			clone.put(entry.getKey(), copy);
		}
		return clone;
	}

	public java.util.Map<String, AlchemyData> cloneAlchemies() {
		java.util.Map<String, AlchemyData> clone = new java.util.HashMap<>();
		for (java.util.Map.Entry<String, AlchemyData> entry : alchemies.entrySet()) {
			AlchemyData original = entry.getValue();
			AlchemyData copy = new AlchemyData(original.x, original.y);

			copy.brewProgress = original.brewProgress;
			copy.brewTimeTotal = original.brewTimeTotal;
			copy.currentRecipe = original.currentRecipe;

			for (int i = 0; i < 5; i++) {
				if (original.items[i] != null) {
					mc.sayda.mcraze.item.Item itemType = original.items[i].getItem();
					int count = original.items[i].getCount();
					copy.items[i] = new mc.sayda.mcraze.item.InventoryItem(itemType);
					copy.items[i].setCount(count);
				}
			}
			clone.put(entry.getKey(), copy);
		}
		return clone;
	}

	/**
	 * Simple class to track block changes for multiplayer broadcasting
	 */
	public static class BlockChange {
		public final int x;
		public final int y;
		public final Constants.TileID tileId;

		public BlockChange(int x, int y, Constants.TileID tileId) {
			this.x = x;
			this.y = y;
			this.tileId = tileId;
		}
	}

	public void chunkUpdate() {
		chunkUpdate(true); // Default: daylight cycle enabled
	}

	/**
	 * Update a chunk of the world (block physics, growth, etc.)
	 * Returns list of changed blocks for multiplayer broadcasting
	 */
	public java.util.List<BlockChange> chunkUpdate(boolean daylightCycle) {
		// Performance: Reuse cached list instead of allocating every tick (60 Hz = 60
		// allocs/sec!)
		blockChangesCache.clear();
		java.util.List<BlockChange> changes = blockChangesCache;

		if (daylightCycle) {
			ticksAlive++;
		}
		for (int i = 0; i < chunkWidth; i++) {
			boolean isDirectLight = true;
			for (int j = 0; j < height; j++) {
				int x = i + chunkWidth * chunkNeedsUpdate;
				if (x >= width || x < 0) {
					continue;
				}
				int y = j;
				// PERFORMANCE FIX: Removed directional alternation (chunkFillRight)
				// Alternating left→right then right→left caused directional lag
				// Players moving right during right→left sweep would be in the "hot zone"
				// Always update left→right for consistent performance
				if (isDirectLight && tiles[x][y].type.name == TileID.DIRT) {
					if (random.nextDouble() < .005) {
						tiles[x][y] = Constants.tileTypes.get(TileID.GRASS);
						changes.add(new BlockChange(x, y, TileID.GRASS));
					}
				} else if (tiles[x][y].type.name == TileID.GRASS) {
					// Grass turns to dirt when covered by non-passable (solid) blocks
					// Passable blocks (flowers, tall grass, cactus, leaves, etc.) allow grass to
					// persist
					if (y - 1 >= 0) {
						Tile blockAbove = tiles[x][y - 1];
						if (blockAbove.type.name != TileID.AIR && !blockAbove.type.passable) {
							// Solid block above - convert grass to dirt
							if (random.nextDouble() < .25) {
								tiles[x][y] = Constants.tileTypes.get(TileID.DIRT);
								changes.add(new BlockChange(x, y, TileID.DIRT));
							}
						}
					}
				} else if (tiles[x][y].type.name == TileID.SAND) {
					if (y + 1 < height) {
						if (isAir(x, y + 1) || isLiquid(x, y + 1)) {
							changeTile(x, y + 1, tiles[x][y]);
							changeTile(x, y, Constants.tileTypes.get(TileID.AIR));
							changes.add(new BlockChange(x, y + 1, TileID.SAND));
							changes.add(new BlockChange(x, y, TileID.AIR));
						}
					}
				} else if (tiles[x][y].type.name == TileID.SAPLING) {
					mc.sayda.mcraze.item.Item sapling = Constants.itemTypes.get("sapling");
					double growthChance = (sapling != null && sapling.growthTime > 0) ? 1.0 / sapling.growthTime : 0.01;
					if (random.nextDouble() < growthChance) {
						addTemplate(TileTemplate.tree, x, y);
						// CRITICAL FIX: Track all tree blocks placed by template for multiplayer sync
						TileTemplate tree = TileTemplate.tree;
						if (tree != null && tree.template != null) {
							for (int ti = 0; ti < tree.template.length; ti++) {
								for (int tj = 0; tj < tree.template[0].length; tj++) {
									if (tree.template[ti][tj] != TileID.NONE) {
										int blockX = x - tree.spawnY + ti;
										int blockY = y - tree.spawnX + tj;
										if (blockX >= 0 && blockX < width && blockY >= 0 && blockY < height) {
											changes.add(new BlockChange(blockX, blockY, tree.template[ti][tj]));
										}
									}
								}
							}
						}
					}
				} else if (tiles[x][y].type.name == TileID.WHEAT_SEEDS) {
					Tile ground = (y + 1 < height) ? tiles[x][y + 1] : null;
					if (ground != null && (ground.type.name == TileID.FARMLAND
							|| ground.type.name == TileID.IRRIGATED_FARMLAND)) {
						mc.sayda.mcraze.item.Item seeds = Constants.itemTypes.get("wheat_seeds");
						double baseChance = (seeds != null && seeds.growthTime > 0) ? 1.0 / seeds.growthTime : 0.001;
						double growthChance = baseChance;

						// IRRIGATED_FARMLAND: 2x growth speed
						if (ground.type.name == TileID.IRRIGATED_FARMLAND) {
							growthChance *= 2.0;
						}

						if (random.nextDouble() < growthChance) {
							changeTile(x, y,
									new Tile(Constants.tileTypes.get(TileID.WHEAT).type));
							changes.add(new BlockChange(x, y, TileID.WHEAT));
						}
					}
				} else if (tiles[x][y].type.name == TileID.CACTUS) {
					// Cactus Vertical Growth
					if (y - 1 >= 0 && tiles[x][y - 1].type.name == TileID.AIR) {
						// Check height (max 3)
						int heightCount = 1;
						if (y + 1 < height && tiles[x][y + 1].type.name == TileID.CACTUS) {
							heightCount++;
							if (y + 2 < height && tiles[x][y + 2].type.name == TileID.CACTUS) {
								heightCount++;
							}
						}

						if (heightCount < 3) {
							mc.sayda.mcraze.item.Item cactus = Constants.itemTypes.get("cactus");
							double growthChance = (cactus != null && cactus.growthTime > 0) ? 1.0 / cactus.growthTime
									: 0.0004;
							if (random.nextDouble() < growthChance) {
								tiles[x][y - 1] = Constants.tileTypes.get(TileID.CACTUS);
								changes.add(new BlockChange(x, y - 1, TileID.CACTUS));
							}
						}
					}
				} else if (tiles[x][y].type.name == TileID.FARMLAND) {
					// CRITICAL FIX: Farmland turns to dirt when obstructed (like grass)
					// Check if there's a non-passable block above
					if (y - 1 >= 0) {
						Tile blockAbove = tiles[x][y - 1];
						if (blockAbove.type.name != TileID.AIR && !blockAbove.type.passable) {
							// Solid block above - convert farmland to dirt instantly
							changeTile(x, y, Constants.tileTypes.get(TileID.DIRT));
							changes.add(new BlockChange(x, y, TileID.DIRT));
						}
					}
				} else if (tiles[x][y].type.liquid) {
					// ========================================
					// LIQUID PHYSICS OVERHAUL
					// ========================================
					// Metadata: 0 = Source (full block, no decay), 1-7 = Flowing (decays)
					// Water: 25% tick chance, max 7 block spread
					// Lava: 10% tick chance, max 3 block spread
					Tile thisTile = tiles[x][y];
					int level = thisTile.metadata;
					boolean isLava = thisTile.type.name == TileID.LAVA;
					int maxFlow = isLava ? 3 : 7; // Lava spreads shorter
					double tickChance = isLava ? 0.10 : 0.25; // Lava spreads slower

					// --- DECAY LOGIC (flowing blocks only) ---
					// Flowing blocks (metadata > 0) must have a source path or they evaporate
					if (level > 0) {
						if (!hasLiquidSupport(x, y, thisTile.type.name)) {
							// No source support - decay quickly (Minecraft-like)
							if (random.nextDouble() < 0.40) { // 40% chance to decay per tick (~0.5-1 sec)
								changeTile(x, y, Constants.tileTypes.get(TileID.AIR));
								changes.add(new BlockChange(x, y, TileID.AIR));
								continue; // Skip further processing for this tile
							}
						}
					}

					// --- WATER/LAVA INTERACTION ---
					// Check for opposite liquid nearby and convert appropriately
					if (checkLiquidInteraction(x, y, thisTile, changes)) {
						continue; // Interaction occurred, skip normal flow
					}

					// --- FLOW LOGIC ---
					if (random.nextDouble() < tickChance) {

						// 1. Flow Down (creates source at bottom)
						if (y + 1 < height) {
							Tile down = tiles[x][y + 1];
							if (down.type.name == TileID.AIR) {
								// Falling liquid becomes source at landing
								setLiquid(x, y + 1, thisTile.type, 0, changes);
							} else if (down.type.liquid && down.type.name == thisTile.type.name && down.metadata > 0) {
								// Falling into same liquid type - make it source
								setLiquid(x, y + 1, thisTile.type, 0, changes);
							}
						}

						// 2. Flow Horizontally (only sources and supported flowing)
						boolean grounded = (y + 1 < height)
								&& (!tiles[x][y + 1].type.passable || tiles[x][y + 1].type.liquid);

						if (grounded && level < maxFlow) {
							int nextLevel = level + 1;

							// Left
							if (x - 1 >= 0) {
								Tile left = tiles[x - 1][y];
								if (left.type.name == TileID.AIR) {
									setLiquid(x - 1, y, thisTile.type, nextLevel, changes);
								} else if (left.type.liquid && left.type.name == thisTile.type.name
										&& left.metadata > nextLevel) {
									// Strengthen weaker flowing block
									setLiquid(x - 1, y, thisTile.type, nextLevel, changes);
								}
							}

							// Right
							if (x + 1 < width) {
								Tile right = tiles[x + 1][y];
								if (right.type.name == TileID.AIR) {
									setLiquid(x + 1, y, thisTile.type, nextLevel, changes);
								} else if (right.type.liquid && right.type.name == thisTile.type.name
										&& right.metadata > nextLevel) {
									// Strengthen weaker flowing block
									setLiquid(x + 1, y, thisTile.type, nextLevel, changes);
								}
							}
						}
					}
				}
				if ((!tiles[x][y].type.passable || tiles[x][y].type.liquid)
						&& tiles[x][y].type.name != TileID.LEAVES) {
					isDirectLight = false;
				}
			}
		}
		chunkNeedsUpdate = (chunkNeedsUpdate + 1) % chunkCount;
		// REMOVED: chunkFillRight toggle (no longer using directional alternation)

		return changes;
	}

	private void addTemplate(TileTemplate tileTemplate, int x, int y) {
		for (int i = 0; i < tileTemplate.template.length; i++) {
			for (int j = 0; j < tileTemplate.template[0].length; j++) {
				if (tileTemplate.template[i][j] != TileID.NONE && x - tileTemplate.spawnY + i >= 0
						&& x - tileTemplate.spawnY + i < tiles.length
						&& y - tileTemplate.spawnX + j >= 0
						&& y - tileTemplate.spawnX + j < tiles[0].length) {
					addTile(x - tileTemplate.spawnY + i, y - tileTemplate.spawnX + j,
							tileTemplate.template[i][j]);
				}
			}
		}
	}

	public boolean placeTemplate(TileTemplate template, int x, int y) {
		if (template == null)
			return false;

		addTemplate(template, x, y);
		return true;
	}

	public boolean addTile(Int2 pos, TileID name) {
		return addTile(pos.x, pos.y, name);
	}

	private static int addTileCount = 0;

	public boolean addTile(int x, int y, TileID name) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		Tile tile = Constants.tileTypes.get(name);
		if (tile == null) {
			return false;
		}
		if (name == TileID.SAPLING && y + 1 < height) {
			if (tiles[x][y + 1].type.name != TileID.DIRT
					&& tiles[x][y + 1].type.name != TileID.GRASS
					&& tiles[x][y + 1].type.name != TileID.FLOWER_POT) {
				return false;
			}
		}
		// Preserve existing backdrop when placing a new foreground tile
		TileType existingBackdrop = tiles[x][y].backdropType;

		// Create NEW tile instance (not shared reference) to avoid backdrop sharing bug
		tiles[x][y] = new Tile(tile.type);

		// Restore the backdrop that was there before
		tiles[x][y].backdropType = existingBackdrop;

		lightingEngineSun.addedTile(x, y);
		lightingEngineSourceBlocks.addedTile(x, y);

		return true;
	}

	public TileID removeTile(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return TileID.NONE;
		}
		TileID name = tiles[x][y].type.name;

		// Preserve existing backdrop when removing foreground
		TileType existingBackdrop = tiles[x][y].backdropType;

		// Create NEW Tile instance to prevent backdrop hivemind bug
		// Using shared reference causes all removed tiles to share the same Tile object
		tiles[x][y] = new Tile(Constants.tileTypes.get(TileID.AIR).type);

		// Restore the backdrop that was there before
		tiles[x][y].backdropType = existingBackdrop;

		lightingEngineSun.removedTile(x, y);
		lightingEngineSourceBlocks.removedTile(x, y);
		return name;
	}

	/**
	 * Set tile directly without triggering updates (lighting, physics, etc).
	 * USE ONLY for bulk loading or generation!
	 */
	public void setTileNoUpdate(int x, int y, Tile tile) {
		if (x < 0 || x >= width || y < 0 || y >= height)
			return;

		// Ensure we don't put nulls in the array if possible, or handle it
		// The World expects non-null tiles usually.
		if (tile == null) {
			// Fallback to AIR if null is passed
			if (Constants.tileTypes.get(TileID.AIR) != null) {
				tile = new Tile(Constants.tileTypes.get(TileID.AIR).type);
			} else {
				// Absolute fallback
				tile = new Tile(new TileType("assets/sprites/tiles/air.png", TileID.AIR));
			}
		}

		tiles[x][y] = tile;
	}

	public void changeTile(int x, int y, Tile tile) {
		// CRITICAL FIX: Create NEW Tile instance to prevent backdrop hivemind bug
		// Directly assigning references causes multiple tiles to share the same object

		// Capture existing backdrop BEFORE overwriting
		TileType existingBackdrop = null;
		if (tiles[x][y] != null) {
			existingBackdrop = tiles[x][y].backdropType;
		}

		tiles[x][y] = new Tile(tile.type);

		// Preserve backdrop if it exists on the source tile OR the old tile
		if (tile.backdropType != null) {
			tiles[x][y].backdropType = tile.backdropType;
		} else if (existingBackdrop != null) {
			tiles[x][y].backdropType = existingBackdrop; // Restore old backdrop
		}

		if (tile.type.lightBlocking > 0) {
			lightingEngineSun.addedTile(x, y);
			lightingEngineSourceBlocks.addedTile(x, y);
		} else {
			lightingEngineSun.removedTile(x, y);
			// For source blocks, we need to check if it emits light too
			if (tile.type.lightEmitting > 0) {
				lightingEngineSourceBlocks.addedTile(x, y);
			} else {
				lightingEngineSourceBlocks.removedTile(x, y);
			}
		}
	}

	// Backdrop manipulation methods
	public boolean setBackdrop(int x, int y, TileID backdropId) {
		if (x < 0 || x >= width || y < 0 || y >= height)
			return false;
		Tile tile = Constants.tileTypes.get(backdropId);
		if (tile == null)
			return false;
		// Prevent placing backdrop over existing backdrop
		if (tiles[x][y].backdropType != null)
			return false;
		tiles[x][y].backdropType = tile.type;
		return true;
	}

	public TileID removeBackdrop(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height)
			return TileID.NONE;
		TileID removed = tiles[x][y].backdropType != null ? tiles[x][y].backdropType.name : TileID.NONE;
		tiles[x][y].backdropType = null;
		return removed;
	}

	public boolean hasBackdrop(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height)
			return false;
		return tiles[x][y].backdropType != null;
	}

	// Chest manipulation methods
	public ChestData getChest(int x, int y) {
		return chests.get(ChestData.makeKey(x, y));
	}

	public ChestData getOrCreateChest(int x, int y) {
		String key = ChestData.makeKey(x, y);
		ChestData chest = chests.get(key);
		if (chest == null) {
			chest = new ChestData(x, y);
			chests.put(key, chest);
		}
		return chest;
	}

	public void removeChest(int x, int y) {
		chests.remove(ChestData.makeKey(x, y));
	}

	public java.util.Map<String, ChestData> getAllChests() {
		return chests;
	}

	public void setChests(java.util.Map<String, ChestData> chests) {
		this.chests = chests;
	}

	public void setChest(int x, int y, ChestData chest) {
		chests.put(ChestData.makeKey(x, y), chest);
	}

	// Furnace manipulation methods
	public FurnaceData getFurnace(int x, int y) {
		return furnaces.get(FurnaceData.makeKey(x, y));
	}

	public FurnaceData getOrCreateFurnace(int x, int y) {
		String key = FurnaceData.makeKey(x, y);
		FurnaceData furnace = furnaces.get(key);
		if (furnace == null) {
			furnace = new FurnaceData(x, y);
			furnaces.put(key, furnace);
		}
		return furnace;
	}

	public void removeFurnace(int x, int y) {
		furnaces.remove(FurnaceData.makeKey(x, y));
	}

	public java.util.Map<String, FurnaceData> getAllFurnaces() {
		return furnaces;
	}

	public void setFurnace(int x, int y, FurnaceData furnace) {
		furnaces.put(FurnaceData.makeKey(x, y), furnace);
	}

	/**
	 * Provider interface for external logic to modify trap damage.
	 */
	public interface TrapMultiplierProvider extends java.io.Serializable {
		float getMultiplier(int x, int y);
	}

	private TrapMultiplierProvider trapMultiplierProvider = null;

	public void setTrapMultiplierProvider(TrapMultiplierProvider provider) {
		this.trapMultiplierProvider = provider;
	}

	/**
	 * Get the fall damage multiplier for a trap at (x, y).
	 * Overridden in SharedWorld to provide class-specific bonuses.
	 */
	public float getTrapFallDamageMultiplier(int x, int y) {
		if (trapMultiplierProvider != null) {
			return trapMultiplierProvider.getMultiplier(x, y);
		}
		return 1.0f; // Default: no multiplier
	}

	// Furnace management
	public void setFurnaces(java.util.Map<String, FurnaceData> furnaces) {
		this.furnaces = furnaces;
	}

	// Alchemy Table manipulation methods
	public AlchemyData getAlchemy(int x, int y) {
		return alchemies.get(AlchemyData.makeKey(x, y));
	}

	public AlchemyData getOrCreateAlchemy(int x, int y) {
		String key = AlchemyData.makeKey(x, y);
		AlchemyData alchemy = alchemies.get(key);
		if (alchemy == null) {
			alchemy = new AlchemyData(x, y);
			alchemies.put(key, alchemy);
		}
		return alchemy;
	}

	public void removeAlchemy(int x, int y) {
		alchemies.remove(AlchemyData.makeKey(x, y));
	}

	public java.util.Map<String, AlchemyData> getAllAlchemies() {
		return alchemies;
	}

	public void setAlchemies(java.util.Map<String, AlchemyData> alchemies) {
		this.alchemies = alchemies;
	}

	private TileID[] breakPlant = new TileID[] {
			TileID.TALL_GRASS, TileID.ROSE, TileID.DANDELION, TileID.WHEAT, TileID.WHEAT_SEEDS, TileID.SAPLING,
			TileID.LEAVES, // TileID.CACTUS
	};

	public int breakTicks(int x, int y, Item item) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return Integer.MAX_VALUE;
		}
		TileID currentName = tiles[x][y].type.name;

		// Check plant category first (almost instant break)
		for (TileID element : breakPlant) {
			if (element == currentName) {
				return 3; // Plants break almost instantly
			}
		}

		// Look up block properties from items.json
		Item blockItem = null;
		if (currentName.itemDropId != null) {
			blockItem = Constants.itemTypes.get(currentName.itemDropId);
		}

		// Determine required tool and power
		Tool.ToolType requiredTool = null;
		Tool.ToolPower requiredPower = null;

		if (blockItem != null) {
			requiredTool = blockItem.requiredToolType;
			requiredPower = blockItem.requiredToolPower;
		}

		// Calculate break time
		if (item == null || !(item instanceof Tool)) {
			// Hand breaking
			if (requiredTool == Tool.ToolType.Pick)
				return 500; // Cannot break stone/ore/metal by hand
			if (requiredTool == Tool.ToolType.Axe)
				return 75; // Slower for wood
			return 50; // Default hand speed (Dirt, Sand, etc)
		}

		Tool tool = (Tool) item;

		// If block has specific requirement
		if (requiredTool != null) {
			if (tool.toolType == requiredTool) {
				// Correct tool type!

				// Check power requirement
				boolean sufficientPower = true;
				if (requiredPower != null) {
					// Use ordinal comparison (Wood=0, Stone=1, etc)
					if (tool.toolPower.ordinal() < requiredPower.ordinal()) {
						sufficientPower = false;
					}
				}

				if (sufficientPower) {
					// Apply speed multiplier based on tool type
					double multiplier = 10;
					switch (tool.toolType) {
						case Axe:
							multiplier = 20;
							break;
						case Pick:
							multiplier = 25;
							break;
						case Shovel:
							multiplier = 15;
							break;
						case Hoe:
							multiplier = 5;
							break;
						default:
							multiplier = 10;
					}
					// Return ticks (base speed * multiplier)
					// Warning: this formula relies on getSpeed returning a small value (0.1 - 5)
					// Existing code used: speed * 20 or 25.
					return (int) (getSpeed(tool) * multiplier);
				} else {
					// Insufficient power (e.g. Wood Pick on Gold Ore)
					return 500; // Treat as impossible/very slow
				}
			} else {
				// Wrong tool type
				if (requiredTool == Tool.ToolType.Pick)
					return 500;
				if (requiredTool == Tool.ToolType.Axe)
					return 75;
				return 50;
			}
		}

		// No specific requirement defined?
		// Fallback for blocks not in items.json or without requirements
		if (tool.toolType == Tool.ToolType.Shovel)
			return (int) (getSpeed(tool) * 15);

		return 50;
	}

	private double getSpeed(Tool tool) {
		if (tool == null) {
			return 5;
		} else if (tool.toolPower == Tool.ToolPower.Wood) {
			return 3;
		} else if (tool.toolPower == Tool.ToolPower.Stone) {
			return 2;
		} else if (tool.toolPower == Tool.ToolPower.Iron) {
			return 1;
		} else if (tool.toolPower == Tool.ToolPower.Diamond) {
			return 0.5;
		} else {
			return 0.1;
		}
	}

	public void draw(GraphicsHandler g, int x, int y, int screenWidth, int screenHeight,
			float cameraX, float cameraY, int tileSize) {
		Int2 pos;

		pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth, screenHeight,
				tileSize, 0, height / 2);
		g.setColor(Color.darkGray);
		g.fillRect(pos.x, pos.y, width * tileSize, height * tileSize / 2);

		pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth, screenHeight,
				tileSize, 0, 0);
		g.setColor(getSkyColor());
		g.fillRect(pos.x, pos.y, width * tileSize, height * tileSize / 2 - 1);
		for (int i = 0; i < width; i++) {
			int posX = (int) ((i - cameraX) * tileSize);
			int posY = (int) ((height - cameraY) * tileSize);
			if (posX < 0 - tileSize || posX > screenWidth || posY < 0 - tileSize
					|| posY > screenHeight) {
				continue;
			}
			Constants.tileTypes.get(TileID.BEDROCK).type.sprite.draw(g, posX, posY, tileSize,
					tileSize);
		}

		for (int j = height / 2; j < height; j++) {
			int posX = (int) ((-1 - cameraX) * tileSize);
			int posY = (int) ((j - cameraY) * tileSize);
			if (!(posX < 0 - tileSize || posX > screenWidth || posY < 0 - tileSize || posY > screenHeight)) {
				Constants.tileTypes.get(TileID.BEDROCK).type.sprite.draw(g, posX, posY, tileSize,
						tileSize);
			}

			posX = (int) ((width - cameraX) * tileSize);
			if (!(posX < 0 - tileSize || posX > screenWidth)) {
				Constants.tileTypes.get(TileID.BEDROCK).type.sprite.draw(g, posX, posY, tileSize,
						tileSize);
			}
		}

		// PERFORMANCE FIX: Calculate visible tile range ONCE before loops
		// Instead of iterating ALL 131,072 tiles (512×256) every frame,
		// only iterate tiles visible on screen (~600 tiles)
		// This reduces from 15.7 million iterations/sec to ~36,000 iterations/sec (430x
		// speedup!)
		int startX = Math.max(0, (int) Math.floor(cameraX) - 1);
		int endX = Math.min(width, (int) Math.ceil(cameraX + (float) screenWidth / tileSize) + 2);
		int startY = Math.max(0, (int) Math.floor(cameraY) - 1);
		int endY = Math.min(height, (int) Math.ceil(cameraY + (float) screenHeight / tileSize) + 2);

		// PASS 1: Backdrop layer (60% brightness for depth effect)
		for (int i = startX; i < endX; i++) {
			for (int j = startY; j < endY; j++) {
				int posX = Math.round(((i - cameraX) * tileSize));
				int posY = Math.round(((j - cameraY) * tileSize));

				// Draw backdrop if it exists
				if (tiles[i][j].backdropType != null) {
					int lightIntensity = (int) (getLightValue(i, j) * 255);
					// PERFORMANCE: Use pre-allocated Color from cache (eliminates 36k allocs/sec)
					Color backdropTint = backdropTintCache[lightIntensity];

					tiles[i][j].backdropType.sprite.draw(g, posX, posY,
							tileSize, tileSize, backdropTint);
				}
			}
		}

		// PASS 2: Foreground layer
		for (int i = startX; i < endX; i++) {
			for (int j = startY; j < endY; j++) {
				int posX = Math.round(((i - cameraX) * tileSize));
				int posY = Math.round(((j - cameraY) * tileSize));

				int lightIntensity = (int) (getLightValue(i, j) * 255);
				// PERFORMANCE: Use pre-allocated Color from cache (eliminates 36k allocs/sec)
				Color tint = lightTintCache[lightIntensity];

				if (tiles[i][j].type.name != TileID.AIR) {
					tiles[i][j].type.sprite.draw(g, posX, posY, tileSize, tileSize, tint);
				} else {
					g.setColor(tint);
					g.fillRect(posX, posY, tileSize, tileSize);
				}
			}
		}
	}

	public boolean passable(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		return tiles[x][y].type == null || tiles[x][y].type.passable;
	}

	public boolean isLiquid(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		return tiles[x][y].type != null && tiles[x][y].type.liquid;
	}

	/**
	 * Check if position contains a SOURCE liquid block (metadata = 0).
	 * Used for drowning checks - only source water causes drowning.
	 */
	public boolean isSourceLiquid(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		return tiles[x][y].type != null && tiles[x][y].type.liquid && tiles[x][y].metadata == 0;
	}

	public boolean isAir(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		// Treat null tiles as air (empty)
		return tiles[x][y].type == null || tiles[x][y].type.name == TileID.AIR;
	}

	public boolean isBreakable(int x, int y) {
		return !(isAir(x, y) || isLiquid(x, y));
	}

	public boolean isClimbable(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		return tiles[x][y].type != null
				&& (tiles[x][y].type.name == TileID.LADDER || tiles[x][y].type.liquid);
	}

	public boolean isCraft(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return false;
		}
		return tiles[x][y].type != null && (tiles[x][y].type.name == TileID.WORKBENCH);
	}

	/**
	 * Find a safe spawn location with solid ground below and air above
	 * 
	 * @param startX Starting x coordinate (usually world center)
	 * @return Safe spawn location as Int2, or null if none found
	 */
	public Int2 findSafeSpawn(int startX) {
		// Search for SAFE spawn: Above sea level (y < height/2), solid ground, air
		// above.
		// Search outwards from startX (center) until a valid column is found.

		int maxRadius = width / 2;
		for (int r = 0; r < maxRadius; r++) {
			// Check left and right offsets (0, +1, -1, +2, -2...)
			// Alternate signs: 0, 1, -1, 2, -2
			// Actually simpler to just check both sides each radius step
			int[] offsets = (r == 0) ? new int[] { 0 } : new int[] { r, -r };

			for (int offset : offsets) {
				int x = startX + offset;
				if (x < 0 || x >= width)
					continue;

				// Search this column from top to bottom
				// Stop at sea level (height/2) because we want ABOVE sea level
				// NOTE: y=0 is sky, y=height is bedrock
				int seaLevel = height / 2;

				for (int y = 3; y < seaLevel; y++) {
					// Check if this position has solid ground
					if (!isBreakable(x, y)) {
						continue; // Not solid ground
					}
					// Check for 3 blocks of air above (for player clearance)
					if (!isAir(x, y - 1) || !isAir(x, y - 2) || !isAir(x, y - 3)) {
						continue; // Obstructed
					}

					// Found safe spawn ABOVE sea level!
					if (logger != null)
						logger.info("World.findSafeSpawn: Found safe surface spawn at (" + x + ", " + (y - 2) + ")");
					return new Int2(x, y - 2);
				}
			}
		}

		// Fallback 1: If no surface spawn found above sea level, try ANY valid surface
		// (even if low)
		// This handles worlds that might be entirely low (unlikely but possible)
		if (logger != null)
			logger.warn("World.findSafeSpawn: No high surface found, searching for ANY surface...");

		for (int y = 3; y < height - 5; y++) {
			if (isBreakable(startX, y) && isAir(startX, y - 1) && isAir(startX, y - 2) && isAir(startX, y - 3)) {
				return new Int2(startX, y - 2);
			}
		}

		// Fallback 2: Center
		if (logger != null)
			logger.error("World.findSafeSpawn: CRITICAL FAILURE, using default center fallback");
		return new Int2(startX, height / 2);
	}

	/**
	 * @return a light value [0,1]
	 **/
	public float getLightValue(int x, int y) {
		if (spelunking) // Full light mode - no darkness at all
			return 1;
		float daylight = getDaylight();

		// If darkness is disabled, provide a minimum light level at night
		if (!darkness && daylight < 0.3f) {
			daylight = 0.3f; // Minimum 30% light when darkness is disabled
		}

		float lightValueSun = ((float) lightingEngineSun.getLightValue(x, y))
				/ Constants.LIGHT_VALUE_SUN * daylight;
		float lightValueSourceBlocks = ((float) lightingEngineSourceBlocks.getLightValue(x, y))
				/ Constants.LIGHT_VALUE_SUN;
		if (lightValueSun >= lightValueSourceBlocks)
			return lightValueSun;
		return lightValueSourceBlocks;
	}

	public float getDaylight() {
		float timeOfDay = getTimeOfDay();
		if (timeOfDay > .4f && timeOfDay < .6f) {
			return 1 - StockMethods.smoothStep(.4f, .6f, timeOfDay);
		} else if (timeOfDay > .9) {
			return StockMethods.smoothStep(.9f, 1.1f, timeOfDay);
		} else if (timeOfDay < .1) {
			return StockMethods.smoothStep(-.1f, .1f, timeOfDay);
		} else if (timeOfDay > .5f) {
			return 0;
		} else {
			return 1;
		}

	}

	// returns a float in the range [0,1)
	// 0 is dawn, 0.25 is noon, 0.5 is dusk, 0.75 is midnight
	public float getTimeOfDay() {
		return ((float) (ticksAlive % daylightSpeed)) / daylightSpeed;
	}

	public boolean isNight() {
		return getTimeOfDay() > 0.5f;
	}

	public boolean isDay() {
		return !isNight();
	}

	static final Color dawnSky = new Color(255, 217, 92);
	static final Color noonSky = new Color(132, 210, 230);
	static final Color duskSky = new Color(245, 92, 32);
	static final Color midnightSky = new Color(0, 0, 0);

	public Color getSkyColor() {
		float time = getTimeOfDay();
		if (time < 0.25f) {
			return dawnSky.interpolateTo(noonSky, 4 * time);
		} else if (time < 0.5f) {
			return noonSky.interpolateTo(duskSky, 4 * (time - 0.25f));
		} else if (time < 0.75f) {
			return duskSky.interpolateTo(midnightSky, 4 * (time - 0.5f));
		} else {
			return midnightSky.interpolateTo(dawnSky, 4 * (time - 0.75f));
		}
	}

	public void setLiquid(int x, int y, TileType type, int metadata, java.util.List<BlockChange> changes) {
		Tile newTile = new Tile(type);
		newTile.metadata = metadata;
		tiles[x][y] = newTile; // Direct set to avoid recursion loop in changeTile logic if any

		changeTile(x, y, newTile);
		if (changes != null) {
			changes.add(new BlockChange(x, y, type.name));
		}
	}

	/**
	 * Checks if a flowing liquid block has source support (adjacent source or
	 * higher-level block).
	 * Used for decay logic - flowing blocks without support evaporate.
	 */
	private boolean hasLiquidSupport(int x, int y, TileID liquidType) {
		// Check block above (if liquid is falling from above, it's supported)
		if (y - 1 >= 0) {
			Tile above = tiles[x][y - 1];
			if (above.type.liquid && above.type.name == liquidType) {
				return true; // Supported by liquid above
			}
		}

		// Check adjacent blocks for source (metadata=0) or higher level (lower metadata
		// number)
		int currentLevel = tiles[x][y].metadata;

		// Left
		if (x - 1 >= 0) {
			Tile left = tiles[x - 1][y];
			if (left.type.liquid && left.type.name == liquidType && left.metadata < currentLevel) {
				return true;
			}
		}

		// Right
		if (x + 1 < width) {
			Tile right = tiles[x + 1][y];
			if (right.type.liquid && right.type.name == liquidType && right.metadata < currentLevel) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks for water/lava interaction and converts blocks appropriately.
	 * Flowing lava + Water -> Cobblestone
	 * Source lava + Water -> Stone
	 * Returns true if an interaction occurred.
	 */
	private boolean checkLiquidInteraction(int x, int y, Tile thisTile, java.util.List<BlockChange> changes) {
		boolean isLava = thisTile.type.name == TileID.LAVA;
		boolean isWater = thisTile.type.name == TileID.WATER;

		if (!isLava && !isWater)
			return false;

		TileID oppositeType = isLava ? TileID.WATER : TileID.LAVA;

		// Check adjacent blocks for opposite liquid type
		int[][] neighbors = { { x - 1, y }, { x + 1, y }, { x, y - 1 }, { x, y + 1 } };

		for (int[] pos : neighbors) {
			int nx = pos[0], ny = pos[1];
			if (nx < 0 || nx >= width || ny < 0 || ny >= height)
				continue;

			Tile neighbor = tiles[nx][ny];
			if (neighbor.type.liquid && neighbor.type.name == oppositeType) {
				// Interaction! Convert the LAVA block (not water)
				if (isLava) {
					// This block is lava touching water -> convert this lava
					TileID result = (thisTile.metadata == 0) ? TileID.STONE : TileID.COBBLE;
					changeTile(x, y, Constants.tileTypes.get(result));
					changes.add(new BlockChange(x, y, result));
					return true;
				} else {
					// This is water touching lava -> convert the lava neighbor
					TileID result = (neighbor.metadata == 0) ? TileID.STONE : TileID.COBBLE;
					changeTile(nx, ny, Constants.tileTypes.get(result));
					changes.add(new BlockChange(nx, ny, result));
					// Don't return true - water continues to exist
				}
			}
		}

		return false;
	}

	public java.util.Collection<PotData> getPots() {
		return pots.values();
	}

	public PotData getPot(int x, int y) {
		return pots.get(PotData.makeKey(x, y));
	}

	public PotData getOrCreatePot(int x, int y) {
		String key = PotData.makeKey(x, y);
		PotData data = pots.get(key);
		if (data == null) {
			data = new PotData(x, y);
			pots.put(key, data);
		}
		return data;
	}

	public void removePot(int x, int y) {
		pots.remove(PotData.makeKey(x, y));
	}

	public java.util.Map<String, PotData> clonePots() {
		return new java.util.HashMap<>(pots);
	}
}
