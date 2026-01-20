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

package mc.sayda.mcraze.player;

import mc.sayda.mcraze.entity.Entity;

import mc.sayda.mcraze.entity.LivingEntity;

import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.player.specialization.PlayerClass;
import mc.sayda.mcraze.player.specialization.SpecializationPath;
import mc.sayda.mcraze.player.specialization.ClassStats;
import mc.sayda.mcraze.player.specialization.PassiveEffectType;
import mc.sayda.mcraze.entity.LivingEntity;

public class Player extends LivingEntity {
	private static final long serialVersionUID = 1L;

	public String username = "Player"; // Player's display name

	public Int2 handTargetPos = new Int2(0, 0); // Unified position for breaking/placing
	public float handStartX;
	public float handStartY;
	public float handEndX;
	public float handEndY;

	// Class System
	public PlayerClass selectedClass = PlayerClass.NONE;
	public SpecializationPath[] selectedPaths = new SpecializationPath[0];
	public ClassStats classStats = new ClassStats(PlayerClass.NONE);
	public int mana = 0;
	public int maxMana = 0;

	// Skill Point System
	public int skillPoints = 0;
	public java.util.ArrayList<PassiveEffectType> unlockedPassives = new java.util.ArrayList<>();

	public boolean debugMode = false;
	public boolean godmode = false; // Invincibility mode (no damage taken)

	// Backdrop placement mode (server-authoritative)
	public boolean backdropPlacementMode = false;

	private Sprite leftFootSprite;
	private Sprite rightFootSprite;
	private Sprite sneakSprite;

	public Player(boolean gravityApplies, float x, float y, int width, int height) {
		super(gravityApplies, x, y, width, height);

		// 3-frame walking animation: right foot → still → left foot → still
		sprite = SpriteStore.get().getSprite("assets/sprites/entities/player.png"); // Standing (both feet together)
		rightFootSprite = SpriteStore.get().getSprite("assets/sprites/entities/player_right.png"); // Right foot forward
		leftFootSprite = SpriteStore.get().getSprite("assets/sprites/entities/player_left.png"); // Left foot forward
		sneakSprite = SpriteStore.get().getSprite("assets/sprites/entities/player_sneak.png"); // Sneaking
	}

	public void setHotbarItem(int hotbarIdx) {
		inventory.hotbarIdx = hotbarIdx;
	}

	/**
	 * Scroll the hotbar by the given number of slots.
	 * Clamps the result to valid range [0, 9].
	 * 
	 * @param scrollAmount Number of slots to scroll (positive or negative)
	 */
	public void scrollHotbar(int scrollAmount) {
		inventory.hotbarIdx += scrollAmount;
		inventory.hotbarIdx = Math.max(0, Math.min(9, inventory.hotbarIdx));
	}

	/**
	 * Override takeDamage to implement godmode (invincibility).
	 */
	@Override
	public void takeDamage(int amount) {
		// Godmode prevents all damage
		if (godmode) {
			return; // No damage taken
		}

		// Apply class damage reduction
		if (selectedPaths != null) {
			for (SpecializationPath path : selectedPaths) {
				if (path == SpecializationPath.SENTINEL) {
					amount = (int) (amount * 0.8f); // 20% reduction
					break;
				}
			}
		}

		// Normal damage handling
		super.takeDamage(amount);
	}

	@Override
	public int getMaxHP() {
		return classStats != null ? classStats.getMaxHP() : 100;
	}

	/**
	 * Select a class and initialize stats.
	 */
	public void selectClass(PlayerClass classType, SpecializationPath... paths) {
		this.selectedClass = classType;
		this.selectedPaths = paths;
		this.classStats = new ClassStats(classType, paths);
		this.maxHP = getMaxHP();
		this.maxMana = classStats.getMaxMana();
		this.mana = maxMana;

		// Set full HP on class change (or just current scaling)
		this.hitPoints = maxHP;

		System.out.println("Player " + username + " selected class: " + classType.getDisplayName());
	}

	/**
	 * Reset class selection, allowing the player to choose again.
	 * This clears the selected class, paths, and all unlocked passives.
	 * Skill points are preserved so they can be re-spent.
	 */
	public void resetClass() {
		this.selectedClass = PlayerClass.NONE;
		this.selectedPaths = new SpecializationPath[0];
		this.classStats = new ClassStats(PlayerClass.NONE);
		this.maxHP = getMaxHP();
		this.maxMana = 0;
		this.mana = 0;
		this.unlockedPassives.clear();
		// Note: skillPoints are NOT reset - they remain for re-spending

		System.out.println("Player " + username + " reset their class selection");
	}

	/**
	 * Toss the selected inventory item.
	 * Returns the item to be added to the world, or null if nothing to toss.
	 * 
	 * @param random Random number generator for positioning
	 * @return The item to add to entities, or null
	 */
	public Item tossSelectedItem(java.util.Random random) {
		InventoryItem inventoryItem = inventory.selectedItem();
		if (inventoryItem.isEmpty()) {
			return null;
		}

		Item newItem = inventoryItem.getItem();
		if (!(newItem instanceof mc.sayda.mcraze.item.Tool)) {
			newItem = (Item) newItem.clone();
		}
		inventoryItem.remove(1);

		// Position item near player based on facing direction
		if (facingRight) {
			newItem.x = x + 1 + random.nextFloat();
		} else {
			newItem.x = x - 1 - random.nextFloat();
		}
		newItem.y = y;
		newItem.dy = -.1f;

		return newItem;
	}

	/**
	 * Respawn the player at the given location
	 * 
	 * @param spawnX Spawn X coordinate
	 * @param spawnY Spawn Y coordinate
	 */
	public void respawn(float spawnX, float spawnY) {
		// Reset position
		this.x = spawnX;
		this.y = spawnY;

		// Reset health
		this.hitPoints = maxHP;

		// Reset death state
		this.dead = false;

		// Reset physics
		this.dx = 0;
		this.dy = 0;
		this.jumping = false;

		System.out.println("Player respawned at (" + spawnX + ", " + spawnY + ") with " + hitPoints + " HP");
	}

	/**
	 * Drop all items from inventory on death.
	 * 
	 * @param random Random number generator for scatter positioning
	 * @return List of items to add to the world
	 */
	public java.util.ArrayList<Item> dropAllItems(java.util.Random random) {
		java.util.ArrayList<Item> droppedItems = new java.util.ArrayList<>();

		// Iterate through all inventory slots
		for (int i = 0; i < inventory.inventoryItems.length; i++) {
			for (int j = 0; j < inventory.inventoryItems[i].length; j++) {
				InventoryItem invItem = inventory.inventoryItems[i][j];
				if (!invItem.isEmpty()) {
					Item item = invItem.getItem();
					int count = invItem.getCount();

					// Drop items (Tools aren't stackable so count = 1 for them)
					for (int k = 0; k < count; k++) {
						Item droppedItem;
						if (item instanceof mc.sayda.mcraze.item.Tool) {
							// Tools are unique instances, use directly
							droppedItem = item;
						} else {
							// Regular items can be cloned
							droppedItem = (Item) item.clone();
						}

						// Scatter items around player position
						droppedItem.x = x + (random.nextFloat() - 0.5f) * 2;
						droppedItem.y = y + (random.nextFloat() - 0.5f) * 2;
						droppedItem.dy = -0.1f - random.nextFloat() * 0.1f; // Pop up
						droppedItem.dx = (random.nextFloat() - 0.5f) * 0.2f; // Scatter horizontally
						droppedItems.add(droppedItem);
					}
				}
			}
		}

		// Clear entire inventory after dropping all items
		for (int i = 0; i < inventory.inventoryItems.length; i++) {
			for (int j = 0; j < inventory.inventoryItems[i].length; j++) {
				inventory.inventoryItems[i][j].setEmpty();
			}
		}

		return droppedItems;
	}

	/**
	 * Update hand target position based on mouse hover and distance.
	 * Simple approach: if mouse is over a block and it's within range, target it.
	 */
	public void updateHand(GraphicsHandler g, float cameraX, float cameraY, float mouseX,
			float mouseY, World world, int tileSize) {

		// Get player center position in world coordinates
		float playerX = this.getCenterX(tileSize);
		float playerY = this.getCenterY(tileSize);

		handStartX = playerX;
		handStartY = playerY;

		// Convert mouse position to block coordinates (mouseX/Y are already in world
		// coords)
		int targetBlockX = (int) Math.floor(mouseX);
		int targetBlockY = (int) Math.floor(mouseY);

		// Calculate distance from player to target block center
		float dx = (targetBlockX + 0.5f) - playerX;
		float dy = (targetBlockY + 0.5f) - playerY;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);

		// [NEW] Builder's Range Passive (Lumberjack)
		float effectiveReach = armLength;
		if (selectedPaths != null) {
			for (SpecializationPath path : selectedPaths) {
				if (path == SpecializationPath.LUMBERJACK) {
					effectiveReach += 3.0f; // +3 Block Range
					break;
				}
			}
		}

		// Check if block is within arm's reach
		if (distance <= effectiveReach) {
			handTargetPos.x = targetBlockX;
			handTargetPos.y = targetBlockY;
			handEndX = targetBlockX + 0.5f;
			handEndY = targetBlockY + 0.5f;
		} else {
			// Out of range
			handTargetPos.x = -1;
			handTargetPos.y = -1;
			handEndX = -1;
			handEndY = -1;
		}
	}

	@Override
	public void draw(GraphicsHandler g, float cameraX, float cameraY, int screenWidth,
			int screenHeight, int tileSize) {
		Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth,
				screenHeight, tileSize, x, y);
		if (StockMethods.onScreen) {

			// Scale dimensions based on zoom
			float scale = (float) tileSize / mc.sayda.mcraze.Constants.TILE_SIZE;
			int drawW = (int) (widthPX * scale);
			int drawH = (int) (heightPX * scale);

			// Use sneak sprite if sneaking
			if (sneaking) {
				if (facingRight) {
					sneakSprite.draw(g, pos.x, pos.y, drawW, drawH);
				} else {
					sneakSprite.draw(g, pos.x + drawW, pos.y, -drawW, drawH);
				}
			} else {
				// 3-frame walking animation: right foot → still → left foot → still
				boolean isMoving = (Math.abs(dx) > 0.001f); // Check if actually moving

				if (isMoving) {
					// 4-step cycle: right → still → left → still (each step = 8 ticks)
					int walkCycle = (int) (ticksAlive / 8) % 4;
					Sprite currentSprite;

					switch (walkCycle) {
						case 0:
							currentSprite = rightFootSprite;
							break; // Right foot forward
						case 1:
							currentSprite = sprite;
							break; // Standing
						case 2:
							currentSprite = leftFootSprite;
							break; // Left foot forward
						case 3:
							currentSprite = sprite;
							break; // Standing
						default:
							currentSprite = sprite;
							break;
					}

					if (facingRight) {
						currentSprite.draw(g, pos.x, pos.y, drawW, drawH);
					} else {
						// Flip horizontally when facing left
						currentSprite.draw(g, pos.x + drawW, pos.y, -drawW, drawH);
					}
				} else {
					// Standing still
					if (facingRight) {
						sprite.draw(g, pos.x, pos.y, drawW, drawH);
					} else {
						sprite.draw(g, pos.x + drawW, pos.y, -drawW, drawH);
					}
				}
			}

			// CRITICAL FIX: Draw held item for all players (local and remote)
			// Render the selected inventory item in the player's hand
			if (inventory != null) {
				InventoryItem selectedItem = inventory.selectedItem();
				if (selectedItem != null && !selectedItem.isEmpty()) {
					Item heldItem = selectedItem.getItem();
					if (heldItem != null && heldItem.sprite != null) {
						// Position held item near player's hand
						int itemSize = tileSize / 2; // Half tile size for held items
						int handOffsetX = facingRight ? widthPX / 2 : -itemSize;
						int handOffsetY = heightPX / 4; // Near center of player

						heldItem.sprite.draw(g, pos.x + handOffsetX, pos.y + handOffsetY, itemSize, itemSize);
					}
				}
			}
		}
	}

	@Override
	public Player clone() {
		try {
			Player cloned = (Player) super.clone();
			if (this.inventory != null) {
				cloned.inventory = this.inventory.clone();
			}
			if (this.handTargetPos != null) {
				cloned.handTargetPos = new Int2(this.handTargetPos.x, this.handTargetPos.y);
			}
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}
}
