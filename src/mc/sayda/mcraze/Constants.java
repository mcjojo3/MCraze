package mc.sayda.mcraze;

import java.util.HashMap;
import java.util.Map;

import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.ItemLoader;
import mc.sayda.mcraze.world.Tile;
import mc.sayda.mcraze.world.TileType;

public class Constants {

	public enum TileID {
		NONE(null),      // NONE/AIR should be first (ordinal 0) to avoid issues with default values
		AIR(null),
		DIRT("dirt"),
		GRASS(null),
		LEAVES(null),
		PLANK("plank"),
		WOOD("wood"),
		STONE("stone"),
		WATER(null),
        LAVA(null),
		SAND("sand"),
		IRON_ORE("iron_ore"),
		GOLD_ORE("gold_ore"),
		COAL_ORE("coal_ore"),
		DIAMOND_ORE("diamond_ore"),
		LAPIS_ORE("lapis_ore"),
		EMERALD_ORE("emerald_ore"),
		COBBLE("cobble"),
		WORKBENCH("workbench"),
		BEDROCK(null),
		SAPLING("sapling"),
		LADDER("ladder"),
		TORCH("torch"),
		ROSE("rose"),
		DANDELION("dandelion"),
		TALL_GRASS("wheat_seeds"),
        CACTUS("cactus"),
		MOSSY_COBBLE("mossy_cobble"),
		CHEST("chest"),           // Dungeon chest (no drop yet)
		SPAWNER(null),         // Monster spawner (no drop yet)
		FARMLAND("dirt"),        // Tilled soil for planting crops
		WHEAT_CROP("wheat");  // Growing wheat (drops wheat item when harvested)

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

			// Try matching by enum name (for tiles without itemDropId or with different names)
			try {
				// Convert item ID to uppercase and try to match enum name
				String enumName = itemId.toUpperCase().replace("_", "_");  // Keep underscores
				return TileID.valueOf(enumName);
			} catch (IllegalArgumentException e) {
				// Not a valid enum name, continue to special cases
			}

			// Special cases where item names don't match tile names
			if (itemId.equals("workbench")) return WORKBENCH;

			return null;
		}
	}

	public static Map<TileID, Tile> tileTypes = new HashMap<TileID, Tile>();

	static {
		tileTypes.put(TileID.DIRT, new Tile(new TileType("sprites/tiles/dirt.png", TileID.DIRT)));
		tileTypes.put(TileID.GRASS, new Tile(new TileType("sprites/tiles/grass_block.png",
				TileID.GRASS)));
		tileTypes.put(TileID.LEAVES, new Tile(new TileType("sprites/tiles/leaves.png",
				TileID.LEAVES, true, false, 1)));
		tileTypes
				.put(TileID.PLANK, new Tile(new TileType("sprites/tiles/plank.png", TileID.PLANK)));
		tileTypes.put(TileID.WOOD, new Tile(new TileType("sprites/tiles/wood.png", TileID.WOOD,
				true, false, 0)));
		tileTypes
				.put(TileID.STONE, new Tile(new TileType("sprites/tiles/stone.png", TileID.STONE)));
		tileTypes.put(TileID.AIR, new Tile(new TileType("sprites/tiles/air.png", TileID.AIR, true,
				false, 0)));
		tileTypes.put(TileID.WATER, new Tile(new TileType("sprites/tiles/water.png", TileID.WATER,
				true, true, 6)));
        tileTypes.put(TileID.LAVA, new Tile(new TileType("sprites/tiles/lava.png", TileID.LAVA,
                true, true, Constants.LIGHT_VALUE_LAVA)));
		tileTypes.put(TileID.SAND, new Tile(new TileType("sprites/tiles/sand.png", TileID.SAND)));
		tileTypes.put(TileID.IRON_ORE, new Tile(new TileType("sprites/tiles/iron_ore.png",
				TileID.IRON_ORE)));
		tileTypes.put(TileID.COAL_ORE, new Tile(new TileType("sprites/tiles/coal_ore.png",
				TileID.COAL_ORE)));
		tileTypes.put(TileID.DIAMOND_ORE, new Tile(new TileType("sprites/tiles/diamond_ore.png",
				TileID.DIAMOND_ORE)));
		tileTypes.put(TileID.GOLD_ORE, new Tile(new TileType("sprites/tiles/gold_ore.png",
				TileID.GOLD_ORE)));
		tileTypes.put(TileID.LAPIS_ORE, new Tile(new TileType("sprites/tiles/lapis_ore.png",
				TileID.LAPIS_ORE)));
		tileTypes.put(TileID.EMERALD_ORE, new Tile(new TileType("sprites/tiles/emerald_ore.png",
				TileID.EMERALD_ORE)));
		tileTypes.put(TileID.COBBLE, new Tile(new TileType("sprites/tiles/cobble.png",
				TileID.COBBLE)));
		tileTypes.put(TileID.WORKBENCH, new Tile(new TileType("sprites/tiles/workbench.png",
				TileID.WORKBENCH, true, false, 0)));
		tileTypes.put(TileID.BEDROCK, new Tile(new TileType("sprites/tiles/bedrock.png",
				TileID.BEDROCK)));
		tileTypes.put(TileID.SAPLING, new Tile(new TileType("sprites/tiles/sapling.png",
				TileID.SAPLING, true, false, 0, 0, false)));  // unstable - needs ground
		tileTypes.put(TileID.LADDER, new Tile(new TileType("sprites/tiles/ladder.png",
				TileID.LADDER, true, false, 0)));
		tileTypes.put(TileID.TORCH, new Tile(new TileType("sprites/tiles/torch.png", TileID.TORCH,
				true, false, 0, Constants.LIGHT_VALUE_TORCH, false)));  // unstable - needs ground
		tileTypes.put(TileID.ROSE, new Tile(new TileType("sprites/tiles/rose.png",
				TileID.ROSE, true, false, 0, 0, false)));  // unstable - needs ground
		tileTypes.put(TileID.DANDELION, new Tile(new TileType("sprites/tiles/dandelion.png",
				TileID.DANDELION, true, false, 0, 0, false)));  // unstable - needs ground
		tileTypes.put(TileID.TALL_GRASS, new Tile(new TileType("sprites/tiles/tall_grass.png",
				TileID.TALL_GRASS, true, false, 0, 0, false)));  // unstable - needs ground
        tileTypes.put(TileID.CACTUS, new Tile(new TileType("sprites/tiles/cactus.png",
                TileID.CACTUS, true, false, 0, 0, false)));  // unstable - needs ground
		tileTypes.put(TileID.MOSSY_COBBLE, new Tile(new TileType("sprites/tiles/mossy_cobble.png",
				TileID.MOSSY_COBBLE)));
		tileTypes.put(TileID.CHEST, new Tile(new TileType("sprites/tiles/chest.png",
				TileID.CHEST, true, false, 0)));
        tileTypes.put(TileID.SPAWNER, new Tile(new TileType("sprites/tiles/spawner.png",
				TileID.SPAWNER, true, false, 1)));
        tileTypes.put(TileID.FARMLAND, new Tile(new TileType("sprites/tiles/farmland.png",
				TileID.FARMLAND, false, false, 0)));
		tileTypes.put(TileID.WHEAT_CROP, new Tile(new TileType("sprites/tiles/wheat_crop.png",
				TileID.WHEAT_CROP, true, false, 0, 0, false)));  // Passable, unstable - needs farmland
	}

	public static Map<String, Item> itemTypes;
	static {
		itemTypes = ItemLoader.loadItems(16);
	}

	// Tile rendering size in pixels
	public static final int TILE_SIZE = 32;

	// Player reach distance (in tiles)
	public static final float ARM_LENGTH = 4.5f;

	public static final int LIGHT_VALUE_TORCH = 12;
	public static final int LIGHT_VALUE_LAVA = 8;
	public static final int LIGHT_VALUE_SUN = 15;
	// not final so that we can set it via command-line arg
	public static boolean DEBUG = false;
	// volatile for thread-safe access across client/server threads
	public static volatile boolean DEBUG_VISIBILITY_ON = false;
	public static final int LIGHT_VALUE_OPAQUE = 10000;
}
