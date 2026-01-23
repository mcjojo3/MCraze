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

package mc.sayda.mcraze.item;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;

import mc.sayda.mcraze.util.StockMethods;

import mc.sayda.mcraze.logging.GameLogger;

public class ItemLoader {
	private static final GameLogger logger = GameLogger.get();
	private static ItemDefinition[] itemDefs;
	private static java.util.Map<String, ItemDefinition> itemDefMap = new java.util.HashMap<>();

	public static ItemDefinition getItemDefinition(String itemId) {
		return itemDefMap.get(itemId);
	}

	public static java.util.Collection<ItemDefinition> getAllItemDefinitions() {
		return itemDefMap.values();
	}

	private static final Gson gson = new Gson();

	public static HashMap<String, Item> loadItems(int size) {
		ToolDefinition[] tools = null;
		ItemDefinition[] items = null;
		ItemDefinition[] classItems = null; // [NEW] Class items
		ItemDefinition[] potions = null; // [NEW] Potions

		// TODO: use the streaming API: https://sites.google.com/site/gson/streaming
		try {
			tools = gson.fromJson(StockMethods.readFile("items/tools.json"), ToolDefinition[].class);
			items = gson.fromJson(StockMethods.readFile("items/items.json"), ItemDefinition[].class);

			// [NEW] Load class items
			String classItemsJson = StockMethods.readFile("items/class.json");
			if (classItemsJson != null) {
				classItems = gson.fromJson(classItemsJson, ItemDefinition[].class);
			}

			// [NEW] Load potions
			String potionsJson = StockMethods.readFile("items/potions.json");
			if (potionsJson != null) {
				potions = gson.fromJson(potionsJson, ItemDefinition[].class);
			}
		} catch (IOException e) {
			logger.warn("Failed to load some item definition files: " + e.getMessage());
		}

		// Store definitions for furnace recipe lookup (merge regular and class items)
		// We need to merge arrays or just iterate both
		int totalItems = (items != null ? items.length : 0) + (classItems != null ? classItems.length : 0)
				+ (potions != null ? potions.length : 0);
		itemDefs = new ItemDefinition[totalItems];

		int idx = 0;
		if (items != null) {
			for (ItemDefinition def : items) {
				itemDefs[idx++] = def;
				if (def.itemId != null)
					itemDefMap.put(def.itemId, def);
			}
		}
		if (classItems != null) {
			for (ItemDefinition def : classItems) {
				itemDefs[idx++] = def;
				if (def.itemId != null)
					itemDefMap.put(def.itemId, def);
			}
		}
		if (potions != null) {
			for (ItemDefinition def : potions) {
				itemDefs[idx++] = def;
				if (def.itemId != null)
					itemDefMap.put(def.itemId, def);
			}
		}

		if (tools == null || items == null) {
			logger.error("Failed to load core item files (tools.json or items.json).");
			// System.exit(5);
		}

		HashMap<String, Item> itemTypes = new HashMap<String, Item>();
		for (ToolDefinition td : tools) {
			itemTypes.put(td.itemId, td.makeTool(size));
		}
		for (ItemDefinition id : items) {
			itemTypes.put(id.itemId, id.makeItem(size));
		}
		// [NEW] Register class items
		if (classItems != null) {
			for (ItemDefinition id : classItems) {
				itemTypes.put(id.itemId, id.makeItem(size));
			}
		}
		// [NEW] Register potions
		if (potions != null) {
			for (ItemDefinition id : potions) {
				itemTypes.put(id.itemId, id.makeItem(size));
			}
		}
		return itemTypes;
	}

	public static class ItemDefinition {
		public String itemId;
		public String name;
		public String spriteRef;
		public RecipeDefinition recipe;
		public SmeltingDefinition smelting;
		public Integer fuel; // Burn time in ticks (null if not fuel)
		public String requiredTool;
		public String requiredPower;
		public String requiredClass; // [NEW] Class restriction
		public String requiredPath; // [NEW] Subclass/Path restriction

		public UsageDefinition usage; // [NEW] Nested usage/consumable properties

		public String hexColor; // [NEW] Optional hex color
		public LayerDefinition[] layers; // [NEW] Multi-layer sprite support
		public AlchemyDefinition alchemy; // [NEW] Alchemy recipe

		// No-arg constructor for Gson
		public ItemDefinition() {
		}

		public Item makeItem(int size) {
			int yield = recipe != null ? recipe.yield : 0;
			boolean shapeless = recipe != null ? recipe.shapeless : false;
			String[][] pattern = recipe != null ? recipe.pattern : null;

			Item item;
			if (usage != null && (usage.buffType != null || usage.healingAmount != null)) {
				Consumable cons = new Consumable(spriteRef, size, itemId, name, pattern, yield, shapeless);
				if (usage.buffType != null) {
					try {
						cons.buffType = mc.sayda.mcraze.entity.buff.BuffType.valueOf(usage.buffType.toUpperCase());
					} catch (IllegalArgumentException e) {
						if (logger != null)
							logger.error("Invalid buffType: " + usage.buffType + " for " + itemId);
					}
				}
				cons.buffDuration = (usage.buffDuration != null) ? usage.buffDuration : 0;
				cons.buffAmplifier = (usage.buffAmplifier != null) ? usage.buffAmplifier : 0;
				cons.healingAmount = (usage.healingAmount != null) ? usage.healingAmount : 0;
				item = cons;
			} else {
				item = new Item(spriteRef, size, itemId, name, pattern, yield, shapeless);
			}

			// Set remainsItemId if present in usage block
			if (usage != null) {
				if (usage.remains != null) {
					item.remainsItemId = usage.remains;
				}
				if (usage.growthTime != null) {
					item.isGrowable = true;
					item.growthTime = usage.growthTime;
				}
			}

			if (requiredTool != null) {
				try {
					item.requiredToolType = Tool.ToolType.valueOf(requiredTool);
				} catch (IllegalArgumentException e) {
					if (logger != null)
						logger.error("Invalid requiredTool: " + requiredTool + " for " + itemId);
				}
			}
			if (requiredPower != null) {
				try {
					item.requiredToolPower = Tool.ToolPower.valueOf(requiredPower);
				} catch (IllegalArgumentException e) {
					if (logger != null)
						logger.error("Invalid requiredPower: " + requiredPower + " for " + itemId);
				}
			}
			// Set fuel burn time (0 if not fuel)
			item.fuelBurnTime = (fuel != null) ? fuel : 0;

			// [NEW] Set required class
			if (requiredClass != null) {
				try {
					item.requiredClass = mc.sayda.mcraze.player.specialization.PlayerClass
							.valueOf(requiredClass.toUpperCase());
				} catch (IllegalArgumentException e) {
					if (logger != null)
						logger.error("Invalid requiredClass: " + requiredClass + " for " + itemId);
				}
			}

			// [NEW] Set required path
			if (requiredPath != null) {
				try {
					item.requiredPath = mc.sayda.mcraze.player.specialization.SpecializationPath
							.valueOf(requiredPath.toUpperCase());
				} catch (IllegalArgumentException e) {
					if (logger != null)
						logger.error("Invalid requiredPath: " + requiredPath + " for " + itemId);
				}
			}

			// [NEW] Set layered sprites
			if (layers != null && layers.length > 0) {
				for (LayerDefinition layerDef : layers) {
					item.layers.add(new Item.SpriteLayer(layerDef.spriteRef,
							mc.sayda.mcraze.graphics.Color.fromHex(layerDef.hexColor)));
				}
			} else {
				// Fallback to single spriteRef/hexColor if no layers defined
				item.layers.add(new Item.SpriteLayer(spriteRef,
						mc.sayda.mcraze.graphics.Color.fromHex(hexColor)));
			}

			return item;
		}
	}

	public static class LayerDefinition {
		public String spriteRef;
		public String hexColor;
	}

	public static class ToolDefinition extends ItemDefinition {
		public String type;
		public String power;

		public Tool makeTool(int size) {
			String[][] pattern = recipe != null ? recipe.pattern : null;
			int yield = recipe != null ? recipe.yield : 0;
			Tool.ToolType t = null;
			Tool.ToolPower p = null;
			boolean shapeless = recipe != null ? recipe.shapeless : false;
			try {
				t = Tool.ToolType.valueOf(type);
				p = Tool.ToolPower.valueOf(power);
			} catch (IllegalArgumentException e) {
				System.err.println("Invalid tool type or power: " + type + ", " + power);
			}

			Tool tool = new Tool(spriteRef, size, itemId, name, pattern, yield, t, p, shapeless);

			// [NEW] Set required class for tools
			if (requiredClass != null) {
				try {
					tool.requiredClass = mc.sayda.mcraze.player.specialization.PlayerClass
							.valueOf(requiredClass.toUpperCase());
				} catch (IllegalArgumentException e) {
					logger.error("Invalid requiredClass: " + requiredClass + " for tool " + itemId);
				}
			}

			// [NEW] Set required path for tools
			if (requiredPath != null) {
				try {
					tool.requiredPath = mc.sayda.mcraze.player.specialization.SpecializationPath
							.valueOf(requiredPath.toUpperCase());
				} catch (IllegalArgumentException e) {
					logger.error("Invalid requiredPath: " + requiredPath + " for tool " + itemId);
				}
			}

			return tool;
		}
	}

	// Helper classes for nested JSON deserialization
	public static class RecipeDefinition {
		public String[][] pattern;
		public int yield;
		public boolean shapeless;
	}

	public static class UsageDefinition {
		public Integer healingAmount;
		public String buffType;
		public Integer buffDuration;
		public Integer buffAmplifier;
		public String remains;
		public Integer growthTime; // [NEW] Total growth ticks in pot (null = not growable)
	}

	public static class SmeltingDefinition {
		public String result;
		public int time;
		public int yield;
	}

	public static class AlchemyDefinition {
		public String[] ingredients;
		public String bottle;
		public int essence;
		public int time;
		public int yield;
	}
}
