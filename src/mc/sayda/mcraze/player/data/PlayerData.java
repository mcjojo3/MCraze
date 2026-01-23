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

package mc.sayda.mcraze.player.data;

import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.world.*;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.storage.*;
import mc.sayda.mcraze.entity.*;
import mc.sayda.mcraze.graphics.*;
import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import java.io.Serializable;

/**
 * Represents all persistent data for a single player in a specific world.
 * Stored as JSON in saves/WorldName/playerdata/username.dat
 *
 * Each world maintains independent playerdata - same username in different
 * worlds
 * has separate character data (position, inventory, health, etc.)
 */
public class PlayerData implements Serializable {
	private static final long serialVersionUID = 1L;

	// Authentication
	public String username;
	public String password; // Plain text for now (no encryption yet)

	// Player state
	public int health;
	public float x;
	public float y;
	public float dx;
	public float dy;
	public boolean facingRight;
	public int spawnX = -1; // -1 means no spawn set
	public int spawnY = -1;

	// Stats
	public int mana;
	public int maxMana;
	public int essence;
	public int maxEssence;

	// Inventory data (flattened 2D array: 10 columns x 7 rows = 70 slots)
	public String[] inventoryItemIds; // Item IDs
	public int[] inventoryItemCounts; // Stack counts
	public int[] inventoryToolUses; // Tool durability
	public boolean[] inventoryItemMastercrafted; // [NEW] Mastercrafted status
	public int inventoryHotbarIdx; // Selected hotbar slot

	// Equipment data (Size 10: Head, Chest, Legs, Trinket, etc.)
	public String[] equipmentItemIds;
	public int[] equipmentItemCounts;
	public int[] equipmentToolUses;
	public boolean[] equipmentItemMastercrafted;

	// Crafting grid size (2 = normal, 3 = workbench)
	public int tableSizeAvailable = 2;

	// Class System
	public String selectedClass = "NONE";
	public String[] selectedPaths = new String[0];

	// Skill Point System (Persistence)
	public int skillPoints = 0;
	public String[] unlockedAbilityIds = new String[0]; // Passive effect type names

	// Cursor item (item being held by mouse)
	public String holdingItemId; // Item ID of held item (null if empty)
	public int holdingItemCount; // Stack count
	public int holdingToolUses; // Tool durability (0 if not a tool)
	public boolean holdingItemMastercrafted; // [NEW] Mastercrafted status

	// Metadata
	public long firstJoinTime;
	public long lastPlayTime;

	/**
	 * Default constructor for Gson
	 */
	public PlayerData() {
	}

	/**
	 * Create new playerdata for fresh spawn
	 * 
	 * @param username Player username
	 * @param password Player password (plain text)
	 * @param spawnX   Spawn X coordinate
	 * @param spawnY   Spawn Y coordinate
	 */
	public PlayerData(String username, String password, float spawnX, float spawnY) {
		this.username = username;
		this.password = password;
		this.health = 100; // maxHP
		this.x = spawnX;
		this.y = spawnY;
		this.dx = 0;
		this.dy = 0;
		this.facingRight = true;
		this.essence = 0;
		this.maxEssence = 100;
		this.mana = 0;
		this.maxMana = 0;

		// Initialize empty inventory (10x7 grid = 70 slots)
		int totalSlots = 70;
		this.inventoryItemIds = new String[totalSlots];
		this.inventoryItemCounts = new int[totalSlots];
		this.inventoryToolUses = new int[totalSlots];
		this.inventoryItemMastercrafted = new boolean[totalSlots]; // [NEW]
		this.inventoryHotbarIdx = 0;

		// Timestamps
		long now = System.currentTimeMillis();
		this.firstJoinTime = now;
		this.lastPlayTime = now;
	}
}
