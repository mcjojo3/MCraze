<<<<<<<< Updated upstream:src/com/github/jleahey/minicraft/Constants.java
package com.github.jleahey.minicraft;
========
package mc.sayda.mcraze;
>>>>>>>> Stashed changes:src/mc/sayda/mcraze/Constants.java

import java.util.HashMap;
import java.util.Map;

<<<<<<<< Updated upstream:src/com/github/jleahey/minicraft/Constants.java
========
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.ItemLoader;
import mc.sayda.mcraze.world.Tile;
import mc.sayda.mcraze.world.TileType;

>>>>>>>> Stashed changes:src/mc/sayda/mcraze/Constants.java
public class Constants {
	
	public enum TileID {
		DIRT("dirt"),
		GRASS(null),
		LEAVES(null),
		PLANK("plank"),
		WOOD("wood"),
		STONE("stone"),
		AIR(null),
		WATER(null),
		SAND("sand"),
		IRON_ORE("iron_ore"),
		GOLD_ORE("gold_ore"),
		COAL_ORE("coal_ore"),
		DIAMOND_ORE("diamond_ore"),
		COBBLE("cobble"),
		CRAFTING_BENCH("craft"),
		BEDROCK(null),
		SAPLING("sapling"),
		LADDER("ladder"),
		TORCH("torch"),
		NONE(null);

		// The string ID of the item this tile drops when broken (null = no drop)
		public final String itemDropId;

		TileID(String itemDropId) {
			this.itemDropId = itemDropId;
		}

		/**
		 * Get TileID from an item string ID.
		 * Used when placing blocks from inventory.
		 * @param itemId The item string ID
		 * @return Matching TileID or null if not placeable
		 */
		public static TileID fromItemId(String itemId) {
			if (itemId == null) return null;

			// Direct matches (most tiles match their item drops)
			for (TileID tileID : values()) {
				if (tileID.itemDropId != null && tileID.itemDropId.equals(itemId)) {
					return tileID;
				}
			}

			// Special cases where item names don't match tile names
			if (itemId.equals("craft")) return CRAFTING_BENCH;

			return null;
		}
	}
	
	public static Map<TileID, Tile> tileTypes = new HashMap<TileID, Tile>();

	static {
		tileTypes.put(TileID.DIRT, new Tile(new TileType("sprites/tiles/dirt.png", TileID.DIRT)));
		tileTypes.put(TileID.GRASS, new Tile(new TileType("sprites/tiles/grass_block.png",
				TileID.GRASS)));
		tileTypes.put(TileID.LEAVES, new Tile(new TileType("sprites/tiles/leaves.png",
				TileID.LEAVES, false, false, 1)));
		tileTypes
				.put(TileID.PLANK, new Tile(new TileType("sprites/tiles/plank.png", TileID.PLANK)));
		tileTypes.put(TileID.WOOD, new Tile(new TileType("sprites/tiles/wood.png", TileID.WOOD,
				true, false, 0)));
		tileTypes
				.put(TileID.STONE, new Tile(new TileType("sprites/tiles/stone.png", TileID.STONE)));
		tileTypes.put(TileID.AIR, new Tile(new TileType("sprites/tiles/air.png", TileID.AIR, true,
				false, 0)));
		tileTypes.put(TileID.WATER, new Tile(new TileType("sprites/tiles/water.png", TileID.WATER,
				true, true, 1)));
		tileTypes.put(TileID.SAND, new Tile(new TileType("sprites/tiles/sand.png", TileID.SAND)));
		tileTypes.put(TileID.IRON_ORE, new Tile(new TileType("sprites/tiles/iron_ore.png",
				TileID.IRON_ORE)));
		tileTypes.put(TileID.COAL_ORE, new Tile(new TileType("sprites/tiles/coal_ore.png",
				TileID.COAL_ORE)));
		tileTypes.put(TileID.DIAMOND_ORE, new Tile(new TileType("sprites/tiles/diamond_ore.png",
				TileID.DIAMOND_ORE)));
		tileTypes.put(TileID.COBBLE, new Tile(new TileType("sprites/tiles/cobble.png",
				TileID.COBBLE)));
		tileTypes.put(TileID.CRAFTING_BENCH, new Tile(new TileType("sprites/tiles/craft.png",
				TileID.CRAFTING_BENCH)));
		tileTypes.put(TileID.BEDROCK, new Tile(new TileType("sprites/tiles/bedrock.png",
				TileID.BEDROCK)));
		tileTypes.put(TileID.SAPLING, new Tile(new TileType("sprites/tiles/sapling.png",
				TileID.SAPLING, true, false, 0)));
		tileTypes.put(TileID.LADDER, new Tile(new TileType("sprites/tiles/ladder.png",
				TileID.LADDER, true, false, 0)));
		tileTypes.put(TileID.TORCH, new Tile(new TileType("sprites/tiles/torch.png", TileID.TORCH,
				true, false, 0, Constants.LIGHT_VALUE_TORCH)));
	}

	public static Map<String, Item> itemTypes;
	static {
		itemTypes = ItemLoader.loadItems(16);
	}
	
	// Tile rendering size in pixels
	public static final int TILE_SIZE = 32;

	// Player reach distance (in tiles)
	public static final float ARM_LENGTH = 4.5f;

	public static final int LIGHT_VALUE_TORCH = 13;
	public static final int LIGHT_VALUE_SUN = 15;
	// not final so that we can set it via command-line arg
	public static boolean DEBUG = false;
	// volatile for thread-safe access across client/server threads
	public static volatile boolean DEBUG_VISIBILITY_ON = false;
	public static final int LIGHT_VALUE_OPAQUE = 10000;
}
