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

package mc.sayda.mcraze.world.gen;

import mc.sayda.mcraze.world.*;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.gen.*;
import mc.sayda.mcraze.world.storage.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.graphics.*;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Loot table system for generating random chest contents.
 *
 * Used for dungeon chests, future structures, etc.
 */
public class LootTable {

	/**
	 * A single loot entry with item, count range, and spawn chance.
	 */
	public static class LootEntry {
		public Item item;
		public int minCount;
		public int maxCount;
		public double chance; // 0.0-1.0 (0% to 100%)

		public LootEntry(Item item, int minCount, int maxCount, double chance) {
			this.item = item;
			this.minCount = minCount;
			this.maxCount = maxCount;
			this.chance = chance;
		}
	}

	private List<LootEntry> entries = new ArrayList<>();

	/**
	 * Add a loot entry to this table (fluent API).
	 *
	 * @param item     Item to add
	 * @param minCount Minimum count
	 * @param maxCount Maximum count
	 * @param chance   Chance to spawn (0.0 = 0%, 1.0 = 100%)
	 * @return This loot table for chaining
	 */
	public LootTable add(Item item, int minCount, int maxCount, double chance) {
		entries.add(new LootEntry(item, minCount, maxCount, chance));
		return this;
	}

	/**
	 * Generate random loot items based on this table.
	 *
	 * @param random Random generator to use
	 * @return List of items to add to chest (max 27 items for 10x3 grid)
	 */
	public List<InventoryItem> generate(Random random) {
		List<InventoryItem> loot = new ArrayList<>();

		for (LootEntry entry : entries) {
			// Roll for this entry
			if (random.nextDouble() < entry.chance) {
				// Determine count within range
				int count = entry.minCount;
				if (entry.maxCount > entry.minCount) {
					count += random.nextInt(entry.maxCount - entry.minCount + 1);
				}

				// Add to loot (create InventoryItem and set count)
				InventoryItem item = new InventoryItem(entry.item);
				item.setCount(count);
				loot.add(item);
			}
		}

		return loot;
	}

	// ==================== PRE-DEFINED LOOT TABLES ====================

	/**
	 * Loot table for dungeon chests.
	 *
	 * Contains basic resources (coal, iron), rare ores (gold, diamond),
	 * and utility items (torches).
	 */
	public static final LootTable DUNGEON = new LootTable()
			.add(Constants.itemTypes.get("coal_ore"), 3, 8, 0.8) // 80% chance: 3-8 coal ore
			.add(Constants.itemTypes.get("iron_ore"), 1, 4, 0.5) // 50% chance: 1-4 iron ore
			.add(Constants.itemTypes.get("gold_ore"), 1, 3, 0.3) // 30% chance: 1-3 gold ore
			.add(Constants.itemTypes.get("diamond_ore"), 1, 2, 0.1) // 10% chance: 1-2 diamonds
			.add(Constants.itemTypes.get("torch"), 4, 12, 0.7) // 70% chance: 4-12 torches
			.add(Constants.itemTypes.get("wood"), 5, 15, 0.6); // 60% chance: 5-15 wood
}
