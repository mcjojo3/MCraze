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

public class ItemLoader {
	private static ItemDefinition[] itemDefs;
	private static java.util.Map<String, ItemDefinition> itemDefMap = new java.util.HashMap<>();

	public static ItemDefinition getItemDefinition(String itemId) {
		return itemDefMap.get(itemId);
	}

	private static final Gson gson = new Gson();

	public static HashMap<String, Item> loadItems(int size) {
		ToolDefinition[] tools = null;
		ItemDefinition[] items = null;
		// TODO: use the streaming API: https://sites.google.com/site/gson/streaming
		try {
			tools = gson
					.fromJson(StockMethods.readFile("items/tools.json"), ToolDefinition[].class);
			items = gson
					.fromJson(StockMethods.readFile("items/items.json"), ItemDefinition[].class);
		} catch (IOException e) {
		}

		// Store definitions for furnace recipe lookup
		itemDefs = items;
		for (ItemDefinition def : items) {
			if (def.itemId != null) {
				itemDefMap.put(def.itemId, def);
			}
		}

		if (tools == null || items == null) {
			System.err.println("Failed to load items from json.");
			System.exit(5);
		}

		HashMap<String, Item> itemTypes = new HashMap<String, Item>();
		for (ToolDefinition td : tools) {
			itemTypes.put(td.itemId, td.makeTool(size));
		}
		for (ItemDefinition id : items) {
			itemTypes.put(id.itemId, id.makeItem(size));
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

		// No-arg constructor for Gson
		public ItemDefinition() {
		}

		public Item makeItem(int size) {
			String[][] pattern = recipe != null ? recipe.pattern : null;
			int yield = recipe != null ? recipe.yield : 0;
			boolean shapeless = recipe != null ? recipe.shapeless : false;
			Item item = new Item(spriteRef, size, itemId, name, pattern, yield, shapeless);
			if (requiredTool != null) {
				try {
					item.requiredToolType = Tool.ToolType.valueOf(requiredTool);
				} catch (IllegalArgumentException e) {
					System.err.println("Invalid requiredTool: " + requiredTool + " for " + itemId);
				}
			}
			if (requiredPower != null) {
				try {
					item.requiredToolPower = Tool.ToolPower.valueOf(requiredPower);
				} catch (IllegalArgumentException e) {
					System.err.println("Invalid requiredPower: " + requiredPower + " for " + itemId);
				}
			}
			// Set fuel burn time (0 if not fuel)
			item.fuelBurnTime = (fuel != null) ? fuel : 0;
			return item;
		}
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
			return new Tool(spriteRef, size, itemId, name, pattern, yield, t, p, shapeless);
		}
	}

	// Helper classes for nested JSON deserialization
	public static class RecipeDefinition {
		public String[][] pattern;
		public int yield;
		public boolean shapeless;
	}

	public static class SmeltingDefinition {
		public String result;
		public int time;
		public int yield;
	}
}
