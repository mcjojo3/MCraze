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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Tool;
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.player.specialization.PlayerClass;
import mc.sayda.mcraze.player.specialization.SpecializationPath;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages player data persistence per world.
 *
 * File structure:
 * %APPDATA%/MCraze/saves/WorldName/playerdata/username.dat (JSON format)
 * %APPDATA%/MCraze/saves/WorldName/playerdata/username.dat.bak (backup)
 *
 * Each world maintains independent playerdata for each username.
 */
public class PlayerDataManager {
	private static final GameLogger logger = GameLogger.get();
	private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Get playerdata directory for a specific world (AppData location)
	 */
	private static Path getPlayerDataDirectory(String worldName) {
		String savesDir = WorldSaveManager.getSavesDirectory();
		String sanitizedWorldName = sanitizeWorldName(worldName);
		return Paths.get(savesDir, sanitizedWorldName, "playerdata");
	}

	/**
	 * Get playerdata directory for a specific world directory (dedicated server)
	 */
	private static Path getPlayerDataDirectory(Path worldDirectory) {
		return worldDirectory.resolve("playerdata");
	}

	/**
	 * Check if playerdata exists for a username in this world (AppData)
	 */
	public static boolean exists(String worldName, String username) {
		Path playerDataFile = getPlayerDataFile(worldName, username);
		return Files.exists(playerDataFile);
	}

	/**
	 * Check if playerdata exists for a username in this world directory (dedicated
	 * server)
	 */
	public static boolean exists(Path worldDirectory, String username) {
		Path playerDataFile = getPlayerDataDirectory(worldDirectory).resolve(sanitizeUsername(username) + ".dat");
		return Files.exists(playerDataFile);
	}

	/**
	 * Authenticate user: check if playerdata exists and password matches (AppData).
	 * Returns PlayerData if authentication successful, null otherwise.
	 * If playerdata doesn't exist, returns null for auto-register.
	 */
	public static PlayerData authenticate(String worldName, String username, String password) {
		if (!exists(worldName, username)) {
			// Auto-register: No playerdata exists, return null so caller can create new
			logger.info("PlayerDataManager: No existing playerdata for " + username + " in world " + worldName);
			return null;
		}

		try {
			PlayerData data = load(worldName, username);
			if (data != null && data.password.equals(password)) {
				logger.info("PlayerDataManager: Authentication successful for " + username);
				return data; // Authentication successful
			} else {
				logger.warn("PlayerDataManager: Wrong password for " + username);
				return null; // Wrong password
			}
		} catch (IOException e) {
			logger.error("Failed to authenticate " + username + ": " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Authenticate user in world directory (dedicated server).
	 * Returns PlayerData if authentication successful, null otherwise.
	 * If playerdata doesn't exist, returns null for auto-register.
	 */
	public static PlayerData authenticate(Path worldDirectory, String username, String password) {
		if (!exists(worldDirectory, username)) {
			// Auto-register: No playerdata exists, return null so caller can create new
			logger.info("PlayerDataManager: No existing playerdata for " + username + " in ./world/");
			return null;
		}

		try {
			PlayerData data = load(worldDirectory, username);
			if (data != null && data.password.equals(password)) {
				logger.info("PlayerDataManager: Authentication successful for " + username);
				return data; // Authentication successful
			} else {
				logger.warn("PlayerDataManager: Wrong password for " + username);
				return null; // Wrong password
			}
		} catch (IOException e) {
			logger.error("Failed to authenticate " + username + ": " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Load playerdata from disk (AppData)
	 */
	public static PlayerData load(String worldName, String username) throws IOException {
		Path playerDataFile = getPlayerDataFile(worldName, username);

		if (!Files.exists(playerDataFile)) {
			return null;
		}

		try {
			String json = new String(Files.readAllBytes(playerDataFile));
			PlayerData data = gson.fromJson(json, PlayerData.class);
			logger.info("Loaded playerdata for " + username + " in world " + worldName);
			return data;
		} catch (Exception e) {
			// Try backup if main file is corrupted
			Path backupFile = getPlayerDataDirectory(worldName).resolve(sanitizeUsername(username) + ".dat.bak");
			if (Files.exists(backupFile)) {
				logger.warn("Main playerdata file corrupted, trying backup...");
				String json = new String(Files.readAllBytes(backupFile));
				PlayerData data = gson.fromJson(json, PlayerData.class);
				logger.info("Loaded playerdata from backup for " + username);
				return data;
			}
			throw e;
		}
	}

	/**
	 * Load playerdata from world directory (dedicated server)
	 */
	public static PlayerData load(Path worldDirectory, String username) throws IOException {
		Path playerDataFile = getPlayerDataDirectory(worldDirectory).resolve(sanitizeUsername(username) + ".dat");

		if (!Files.exists(playerDataFile)) {
			return null;
		}

		try {
			String json = new String(Files.readAllBytes(playerDataFile));
			PlayerData data = gson.fromJson(json, PlayerData.class);
			logger.info("Loaded playerdata for " + username + " from ./world/");
			return data;
		} catch (Exception e) {
			// Try backup if main file is corrupted
			Path backupFile = getPlayerDataDirectory(worldDirectory).resolve(sanitizeUsername(username) + ".dat.bak");
			if (Files.exists(backupFile)) {
				logger.warn("Main playerdata file corrupted, trying backup...");
				String json = new String(Files.readAllBytes(backupFile));
				PlayerData data = gson.fromJson(json, PlayerData.class);
				logger.info("Loaded playerdata from backup for " + username);
				return data;
			}
			throw e;
		}
	}

	/**
	 * Save playerdata to disk (atomic with backup) - AppData
	 */
	public static boolean save(String worldName, PlayerData playerData) {
		try {
			// Create playerdata directory if it doesn't exist
			Path playerDataDir = getPlayerDataDirectory(worldName);
			Files.createDirectories(playerDataDir);

			// Write to temp file first (atomic save)
			Path tempFile = playerDataDir.resolve(playerData.username + ".dat.tmp");
			Path finalFile = playerDataDir.resolve(sanitizeUsername(playerData.username) + ".dat");

			// Update last play time
			playerData.lastPlayTime = System.currentTimeMillis();

			// Write JSON
			try (FileWriter writer = new FileWriter(tempFile.toFile())) {
				gson.toJson(playerData, writer);
			}

			// Backup old file if exists
			if (Files.exists(finalFile)) {
				Path backupFile = playerDataDir.resolve(sanitizeUsername(playerData.username) + ".dat.bak");
				Files.move(finalFile, backupFile,
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}

			// Atomic rename
			Files.move(tempFile, finalFile,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);

			logger.info("Saved playerdata for " + playerData.username + " in world " + worldName);
			return true;

		} catch (IOException e) {
			logger.error("Failed to save playerdata: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Save playerdata to world directory (atomic with backup) - dedicated server
	 */
	public static boolean save(Path worldDirectory, PlayerData playerData) {
		try {
			// Create playerdata directory if it doesn't exist
			Path playerDataDir = getPlayerDataDirectory(worldDirectory);
			Files.createDirectories(playerDataDir);

			// Write to temp file first (atomic save)
			Path tempFile = playerDataDir.resolve(playerData.username + ".dat.tmp");
			Path finalFile = playerDataDir.resolve(sanitizeUsername(playerData.username) + ".dat");

			// Update last play time
			playerData.lastPlayTime = System.currentTimeMillis();

			// Write JSON
			try (FileWriter writer = new FileWriter(tempFile.toFile())) {
				gson.toJson(playerData, writer);
			}

			// Backup old file if exists
			if (Files.exists(finalFile)) {
				Path backupFile = playerDataDir.resolve(sanitizeUsername(playerData.username) + ".dat.bak");
				Files.move(finalFile, backupFile,
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}

			// Atomic rename
			Files.move(tempFile, finalFile,
					java.nio.file.StandardCopyOption.ATOMIC_MOVE);

			logger.info("Saved playerdata for " + playerData.username + " to ./world/");
			return true;

		} catch (IOException e) {
			logger.error("Failed to save playerdata: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Apply PlayerData to a Player entity
	 */
	public static void applyToPlayer(PlayerData data, Player player) {
		player.x = data.x;
		player.y = data.y;
		player.dx = data.dx;
		player.dy = data.dy;
		player.facingRight = data.facingRight;
		player.spawnX = data.spawnX; // Restore spawn point
		player.spawnY = data.spawnY;
		player.mana = data.mana;
		player.maxMana = data.maxMana;
		player.essence = data.essence;
		player.maxEssence = data.maxEssence;

		// Restore Skill Points and Unlocked Passives (MUST happen before selectClass)
		player.skillPoints = data.skillPoints;
		player.unlockedPassives.clear();
		if (data.unlockedAbilityIds != null) {
			for (String abilityName : data.unlockedAbilityIds) {
				try {
					player.unlockedPassives
							.add(mc.sayda.mcraze.player.specialization.PassiveEffectType.valueOf(abilityName));
				} catch (IllegalArgumentException e) {
					logger.warn("Unknown passive ability in playerdata: " + abilityName);
				}
			}
		}

		// Restore Class System
		if (data.selectedClass != null && !data.selectedClass.equals("NONE")) {
			try {
				PlayerClass pClass = PlayerClass.valueOf(data.selectedClass);
				SpecializationPath[] pPaths = new SpecializationPath[data.selectedPaths.length];
				for (int i = 0; i < data.selectedPaths.length; i++) {
					pPaths[i] = SpecializationPath.valueOf(data.selectedPaths[i]);
				}
				player.selectClass(pClass, pPaths);
				// Re-apply HP/Mana/Essence after class selection potentially changed/capped
				// them
				player.hitPoints = data.health;
				player.mana = data.mana;
				player.essence = data.essence;
			} catch (IllegalArgumentException e) {
				logger.error("Failed to restore class from playerdata: " + data.selectedClass);
			}
		}

		// Restore inventory
		if (data.inventoryItemIds != null && player.inventory != null) {
			Inventory inv = player.inventory;
			int width = inv.inventoryItems.length;
			int height = inv.inventoryItems[0].length;
			int totalSlots = width * height;

			// Check array length compatibility (graceful degradation if older save format)
			boolean hasMastercraftedData = data.inventoryItemMastercrafted != null
					&& data.inventoryItemMastercrafted.length == totalSlots;

			if (data.inventoryItemIds.length == totalSlots) {
				inv.hotbarIdx = data.inventoryHotbarIdx;

				// Unflatten inventory array
				int index = 0;
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						String itemId = data.inventoryItemIds[index];
						int count = data.inventoryItemCounts[index];
						int uses = data.inventoryToolUses[index];
						boolean isMastercrafted = hasMastercraftedData && data.inventoryItemMastercrafted[index];

						InventoryItem slot = inv.inventoryItems[x][y];

						if (itemId == null || count == 0) {
							slot.setEmpty();
						} else {
							Item item = Constants.itemTypes.get(itemId);
							if (item != null) {
								Item clonedItem = item.clone();
								slot.setItem(clonedItem);
								slot.setCount(count);

								// [NEW] Restore mastercrafted status
								if (isMastercrafted) {
									clonedItem.isMastercrafted = true;
									if (clonedItem instanceof Tool) {
										Tool t = (Tool) clonedItem;
										t.totalUses = (int) (t.totalUses * 1.5f); // Restore boost
									}
								}

								if (clonedItem instanceof Tool) {
									((Tool) clonedItem).uses = uses;
								}
							} else {
								slot.setEmpty();
								logger.error("Unknown item ID in playerdata: " + itemId);
							}
						}
						index++;
					}
				}

				// Restore crafting grid size (2 = normal, 3 = workbench)
				inv.tableSizeAvailable = data.tableSizeAvailable;

				// Restore cursor item (item being held by mouse)
				if (data.holdingItemId != null && data.holdingItemCount > 0) {
					Item item = Constants.itemTypes.get(data.holdingItemId);
					if (item != null) {
						Item clonedItem = item.clone();
						inv.holding.setItem(clonedItem);
						inv.holding.setCount(data.holdingItemCount);

						// [NEW] Restore holding mastercrafted status
						if (data.holdingItemMastercrafted) {
							clonedItem.isMastercrafted = true;
							if (clonedItem instanceof Tool) {
								Tool t = (Tool) clonedItem;
								t.totalUses = (int) (t.totalUses * 1.5f);
							}
						}

						if (clonedItem instanceof Tool) {
							((Tool) clonedItem).uses = data.holdingToolUses;
						}
					}
				} else {
					// No cursor item - ensure it's empty
					inv.holding.setEmpty();
				}

				// [NEW] Restore equipment
				if (data.equipmentItemIds != null && data.equipmentItemIds.length == 10) {
					boolean hasEqMastercrafted = data.equipmentItemMastercrafted != null
							&& data.equipmentItemMastercrafted.length == 10;

					for (int i = 0; i < 10; i++) {
						String itemId = data.equipmentItemIds[i];
						int count = data.equipmentItemCounts[i];

						if (itemId != null && count > 0) {
							Item item = Constants.itemTypes.get(itemId);
							if (item != null) {
								Item clonedItem = item.clone();
								inv.equipment[i].setItem(clonedItem);
								inv.equipment[i].setCount(count);

								if (hasEqMastercrafted && data.equipmentItemMastercrafted[i]) {
									clonedItem.isMastercrafted = true;
									if (clonedItem instanceof Tool) {
										Tool t = (Tool) clonedItem;
										t.totalUses = (int) (t.totalUses * 1.5f);
									}
								}

								if (clonedItem instanceof Tool) {
									((Tool) clonedItem).uses = data.equipmentToolUses[i];
								}
							}
						} else {
							inv.equipment[i].setEmpty();
						}
					}
				}
			}
		}
	}

	/**
	 * Extract PlayerData from a Player entity
	 */
	public static PlayerData extractFromPlayer(String username, String password, Player player) {
		PlayerData data = new PlayerData();
		data.username = username;
		data.password = password;
		data.health = player.hitPoints;
		data.x = player.x;
		data.y = player.y;
		data.dx = player.dx;
		data.dy = player.dy;
		data.facingRight = player.facingRight;
		data.spawnX = player.spawnX; // Save spawn point
		data.spawnY = player.spawnY;
		data.mana = player.mana;
		data.maxMana = player.maxMana;
		data.essence = player.essence;
		data.maxEssence = player.maxEssence;

		// Extract Class System
		data.selectedClass = player.selectedClass.name();
		data.selectedPaths = new String[player.selectedPaths.length];
		for (int i = 0; i < player.selectedPaths.length; i++) {
			data.selectedPaths[i] = player.selectedPaths[i].name();
		}

		// Extract Skill Points and Unlocked Passives
		data.skillPoints = player.skillPoints;
		data.unlockedAbilityIds = new String[player.unlockedPassives.size()];
		for (int i = 0; i < player.unlockedPassives.size(); i++) {
			data.unlockedAbilityIds[i] = player.unlockedPassives.get(i).name();
		}

		// Extract inventory
		Inventory inv = player.inventory;
		int width = inv.inventoryItems.length;
		int height = inv.inventoryItems[0].length;
		int totalSlots = width * height;

		data.inventoryItemIds = new String[totalSlots];
		data.inventoryItemCounts = new int[totalSlots];
		data.inventoryToolUses = new int[totalSlots];
		data.inventoryItemMastercrafted = new boolean[totalSlots];
		data.inventoryHotbarIdx = inv.hotbarIdx;

		int index = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				InventoryItem slot = inv.inventoryItems[x][y];
				if (slot != null && slot.item != null && slot.count > 0) {
					data.inventoryItemIds[index] = slot.item.itemId;
					data.inventoryItemCounts[index] = slot.count;

					if (slot.item instanceof Tool) {
						data.inventoryToolUses[index] = ((Tool) slot.item).uses;
					}

					// [NEW] Save mastercrafted status
					data.inventoryItemMastercrafted[index] = slot.item.isMastercrafted;
				} else {
					data.inventoryItemIds[index] = null;
					data.inventoryItemCounts[index] = 0;
					data.inventoryToolUses[index] = 0;
					data.inventoryItemMastercrafted[index] = false;
				}
				index++;
			}
		}

		// Save crafting grid size (2 = normal, 3 = workbench)
		data.tableSizeAvailable = inv.tableSizeAvailable;

		// Save cursor item (item being held by mouse)
		if (inv.holding != null && !inv.holding.isEmpty()) {
			data.holdingItemId = inv.holding.getItem().itemId;
			data.holdingItemCount = inv.holding.getCount();
			// [NEW] Save holding mastercrafted status
			data.holdingItemMastercrafted = inv.holding.getItem().isMastercrafted;
			if (inv.holding.getItem() instanceof Tool) {
				data.holdingToolUses = ((Tool) inv.holding.getItem()).uses;
			} else {
				data.holdingToolUses = 0;
			}
		}

		// [NEW] Save equipment
		data.equipmentItemIds = new String[10];
		data.equipmentItemCounts = new int[10];
		data.equipmentToolUses = new int[10];
		data.equipmentItemMastercrafted = new boolean[10];

		for (int i = 0; i < 10; i++) {
			InventoryItem slot = inv.equipment[i];
			if (slot != null && !slot.isEmpty()) {
				data.equipmentItemIds[i] = slot.getItem().itemId;
				data.equipmentItemCounts[i] = slot.getCount();
				data.equipmentItemMastercrafted[i] = slot.getItem().isMastercrafted;

				if (slot.getItem() instanceof Tool) {
					data.equipmentToolUses[i] = ((Tool) slot.getItem()).uses;
				} else {
					data.equipmentToolUses[i] = 0;
				}
			} else {
				data.equipmentItemIds[i] = null;
				data.equipmentItemCounts[i] = 0;
				data.equipmentToolUses[i] = 0;
				data.equipmentItemMastercrafted[i] = false;
			}
		}

		data.lastPlayTime = System.currentTimeMillis();
		return data;
	}

	/**
	 * Delete playerdata for a user in a world
	 */
	public static boolean delete(String worldName, String username) {
		try {
			Path playerDataFile = getPlayerDataFile(worldName, username);
			Path backupFile = getPlayerDataDirectory(worldName).resolve(sanitizeUsername(username) + ".dat.bak");

			Files.deleteIfExists(playerDataFile);
			Files.deleteIfExists(backupFile);

			if (logger != null)
				logger.info("Deleted playerdata for " + username + " in world " + worldName);
			return true;
		} catch (IOException e) {
			if (logger != null)
				logger.error("Failed to delete playerdata: " + e.getMessage());
			return false;
		}
	}

	// Helper methods

	private static Path getPlayerDataFile(String worldName, String username) {
		return getPlayerDataDirectory(worldName).resolve(sanitizeUsername(username) + ".dat");
	}

	private static String sanitizeWorldName(String worldName) {
		return worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
	}

	private static String sanitizeUsername(String username) {
		return username.replaceAll("[^a-zA-Z0-9_-]", "_");
	}
}
