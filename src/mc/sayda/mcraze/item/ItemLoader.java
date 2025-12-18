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

<<<<<<<< Updated upstream:src/com/github/jleahey/minicraft/ItemLoader.java
package com.github.jleahey.minicraft;
========
package mc.sayda.mcraze.item;
>>>>>>>> Stashed changes:src/mc/sayda/mcraze/item/ItemLoader.java

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.Gson;

<<<<<<<< Updated upstream:src/com/github/jleahey/minicraft/ItemLoader.java
========
import mc.sayda.mcraze.util.StockMethods;

>>>>>>>> Stashed changes:src/mc/sayda/mcraze/item/ItemLoader.java
public class ItemLoader {
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
}

class ItemDefinition {
	String itemId;
	String name;
	String spriteRef;
	String[][] recipe;
	int yield;

	public ItemDefinition(String id, String n, String s, String[][] t, int y) {
		itemId = id;
		name = n;
		spriteRef = s;
		recipe = t;
		yield = y;
	}

	public Item makeItem(int size) {
		return new Item(spriteRef, size, itemId, name, recipe, yield);
	}
}

class ToolDefinition extends ItemDefinition {
	Tool.ToolType type;
	Tool.ToolPower power;

	public ToolDefinition(String id, String n, String s, String[][] t, int y, Tool.ToolType tt,
			Tool.ToolPower tp) {
		super(id, n, s, t, y);
		type = tt;
		power = tp;
	}

	public Tool makeTool(int size) {
		return new Tool(spriteRef, size, itemId, name, recipe, yield, type, power);
	}
}
