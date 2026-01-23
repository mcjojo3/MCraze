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

public class Player extends LivingEntity {
	private static final long serialVersionUID = 1L;

	public String username = "Player"; // Player's display name
	public int cachedNameWidth = -1; // Cached width of username for rendering optimization

	public Int2 handTargetPos = new Int2(0, 0); // Unified position for breaking/placing
	public float handStartX;
	public float handStartY;
	public float handEndX;
	public float handEndY;

	public int spawnX = -1;
	public int spawnY = -1;

	public void setSpawnPoint(int x, int y) {
		this.spawnX = x;
		this.spawnY = y;
	}

	// Class System
	public PlayerClass selectedClass = PlayerClass.NONE;
	public SpecializationPath[] selectedPaths = new SpecializationPath[0];
	public ClassStats classStats = new ClassStats(PlayerClass.NONE, null, null);
	public int mana = 0;
	public int maxMana = 0;
	public int essence = 0;
	public int maxEssence = 100;

	// Skill Point System
	public int skillPoints = 1;

	// Skillpoint Cap (Disabled for now)
	// private static final int MAX_SKILL_POINTS = 100;

	public void addSkillPoint(int amount) {
		this.skillPoints += amount;
		// if (this.skillPoints > MAX_SKILL_POINTS) this.skillPoints = MAX_SKILL_POINTS;
	}

	public java.util.ArrayList<PassiveEffectType> unlockedPassives = new java.util.ArrayList<>();
	public int consecutiveBowHits = 0;

	// Bow Charge System
	public int bowCharge = 0;
	public int maxBowCharge = 40; // 2 seconds to full charge
	public boolean holdingBow = false;
	// For Headshot Master tracking

	public boolean debugMode = false;
	public boolean godmode = false; // Invincibility mode (no damage taken)

	// Backdrop placement mode (server-authoritative)
	public boolean backdropPlacementMode = false;

	// Input State
	public boolean rightClick = false;
	public int itemUseTicks = 0;

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
	public void takeDamage(int amount) {
		takeDamage(amount, mc.sayda.mcraze.entity.DamageType.PHYSICAL);
	}

	@Override
	public void takeDamage(int amount, mc.sayda.mcraze.entity.DamageType type) {
		// Godmode prevents all damage
		if (godmode) {
			return; // No damage taken
		}

		// Apply class damage reduction (Iron Skin - Sentinel path)
		if (type != mc.sayda.mcraze.entity.DamageType.TRUE_DAMAGE &&
				unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.IRON_SKIN)) {
			amount = (int) (amount * 0.8f); // 20% reduction
		}

		// Normal damage handling with type
		super.takeDamage(amount, type);

		// Steadfast Trigger (Sentinel)
		if (amount > 0
				&& unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.STEADFAST)) {
			// Add Steadfast buff (Duration 100 ticks = 5s, Amp 0)
			addBuff(new mc.sayda.mcraze.entity.buff.Buff(mc.sayda.mcraze.entity.buff.BuffType.STEADFAST, 100, 0));
		}
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
		updateClassStats(); // Initialize or re-initialize stats
		this.hitPoints = maxHP; // Set full HP on initial class selection
		// logger.info("Player " + username + " selected class: " +
		// classType.getDisplayName());
	}

	/**
	 * Re-calculate stats based on current class, paths, and unlocked passives.
	 */
	public void updateClassStats() {
		this.classStats = new ClassStats(selectedClass, java.util.Arrays.asList(selectedPaths), unlockedPassives);
		this.maxHP = getMaxHP();
		this.maxMana = classStats.getMaxMana();

		// Set maxEssence based on class (Only Arcanist uses essence)
		if (selectedClass == PlayerClass.ARCANIST) {
			this.maxEssence = 100;
		} else {
			this.maxEssence = 0;
		}

		// RAPID_FIRE: 30% faster bow draw (reduce maxBowCharge)
		if (unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.RAPID_FIRE)) {
			this.maxBowCharge = 28; // 40 * 0.7 = 28 ticks (1.4 seconds instead of 2)
		} else {
			this.maxBowCharge = 40; // Default 2 seconds
		}

		// Ensure current HP/Mana/Essence don't exceed new maximums
		if (this.hitPoints > maxHP)
			this.hitPoints = maxHP;
		if (this.mana > maxMana)
			this.mana = maxMana;
		if (this.essence > maxEssence)
			this.essence = maxEssence;
	}

	/**
	 * Spend mana if available. Returns true if successful.
	 * Handles Mana Echo passive (25% chance to not spend).
	 */
	public boolean spendMana(int amount) {
		if (mana < amount)
			return false;

		// [NEW] Mana Echo Passive (Arcanist)
		if (unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.MANA_ECHO)) {
			if (new java.util.Random().nextDouble() < 0.25) {
				return true;
			}
		}

		mana -= amount;
		return true;
	}

	/**
	 * Reset class selection, allowing the player to choose again.
	 * This clears the selected class, paths, and all unlocked passives.
	 * Skill points are preserved so they can be re-spent.
	 */
	public void resetClass() {
		this.selectedClass = PlayerClass.NONE;
		this.selectedPaths = new SpecializationPath[0];
		this.classStats = new ClassStats(PlayerClass.NONE, null, null);
		this.maxHP = getMaxHP();
		this.maxMana = 0;
		this.mana = 0;
		this.unlockedPassives.clear();
		// Note: skillPoints are NOT reset - they remain for re-spending

		// logger.info("Player " + username + " reset their class selection");
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

		// logger.info("Player respawned at (" + spawnX + ", " + spawnY + ") with " +
		// hitPoints + " HP");
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
		if (unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.BUILDERS_RANGE)) {
			effectiveReach += 3.0f; // +3 Block Range
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

			// Draw Equipment Overlays
			if (inventory != null && inventory.equipment != null) {
				for (mc.sayda.mcraze.item.InventoryItem slot : inventory.equipment) {
					if (slot != null && !slot.isEmpty() && slot.getItem() instanceof mc.sayda.mcraze.item.Armor) {
						mc.sayda.mcraze.item.Armor armor = (mc.sayda.mcraze.item.Armor) slot.getItem();
						if (armor.sprite != null) {
							if (facingRight) {
								armor.sprite.draw(g, pos.x, pos.y, drawW, drawH);
							} else {
								armor.sprite.draw(g, pos.x + drawW, pos.y, -drawW, drawH);
							}
						}
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

	@Override
	public void tick(mc.sayda.mcraze.server.SharedWorld sharedWorld) {
		super.tick(sharedWorld);
		updateStatsFromEquipment();

		// Mana Regeneration
		if (maxMana > 0 && mana < maxMana) {
			float regenRate = classStats != null ? classStats.getManaRegen() : 0;
			// Regen is per second, tick is 20 per second
			// Accumulate partial regen or just simplistic logic
			// Simple logic: regen 1 mana every X ticks based on rate
			if (regenRate > 0) {
				int ticksPerRegen = (int) (20 / regenRate);
				if (ticksPerRegen > 0 && sharedWorld.getTicksRunning() % ticksPerRegen == 0) {
					mana++;
					if (mana > maxMana)
						mana = maxMana;
				}
			}
		}

		// [NEW] Passive: Photosynthesis (Druid) - Regen during day
		if (unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.PHOTOSYNTHESIS)) {
			if (!dead && sharedWorld.isDay()) {
				// Regen 1 HP and 1 Mana every 2 seconds (40 ticks)
				if (sharedWorld.getTicksRunning() % 40 == 0) {
					if (hitPoints < getMaxHP())
						heal(1);
					if (mana < maxMana)
						mana++;
				}
			}
		}

		// [NEW] Passive: Fast Metabolism (Druid) - +1 HP/sec regen
		if (unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.FAST_METABOLISM)) {
			if (hitPoints < getMaxHP() && !dead && sharedWorld.getTicksRunning() % 20 == 0) {
				heal(1);
			}
		}

		// Item Consumption & Bow Charge Logic
		if (rightClick) {
			InventoryItem held = inventory.selectedItem();
			if (held != null && !held.isEmpty()) {
				if (held.getItem() instanceof mc.sayda.mcraze.item.Consumable) {
					itemUseTicks++;
					if (itemUseTicks >= 32) { // 1.6 seconds to drink
						consumeItem(held, sharedWorld);
						itemUseTicks = 0;
					}
				} else if (held.getItem() instanceof mc.sayda.mcraze.item.Tool &&
						((mc.sayda.mcraze.item.Tool) held
								.getItem()).toolType == mc.sayda.mcraze.item.Tool.ToolType.Bow) {
					holdingBow = true;
					if (bowCharge < maxBowCharge) {
						int drawSpeed = 1;
						if (unlockedPassives
								.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.RAPID_FIRE)) {
							// +50% speed: 1.5 per tick (approx: 2 every 2 ticks, 1 every 2 ticks?)
							// Simplest: 2 every other tick, 1 every other tick
							drawSpeed = (sharedWorld.getTicksRunning() % 2 == 0) ? 2 : 1;
						}
						bowCharge = Math.min(bowCharge + drawSpeed, maxBowCharge);
					}
					itemUseTicks = 0;
				} else {
					holdingBow = false;
					bowCharge = 0;
					itemUseTicks = 0;
				}
			} else {
				holdingBow = false;
				bowCharge = 0;
				itemUseTicks = 0;
			}
		} else {
			// [FIX] Fire bow on release
			if (holdingBow && bowCharge >= 10) {
				// Fire arrow towards hand target
				fireArrow(sharedWorld, handEndX, handEndY, (float) bowCharge / maxBowCharge);
			}

			itemUseTicks = 0;
			holdingBow = false;
			bowCharge = 0;
		}
	}

	public void fireArrow(mc.sayda.mcraze.server.SharedWorld world, float targetX, float targetY, float chargePct) {
		// Search for arrows: Diamond > Iron > Stone
		mc.sayda.mcraze.item.InventoryItem arrowSlot = inventory.findItem("arrow_diamond");
		mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType arrowType = mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType.DIAMOND;

		if (arrowSlot == null) {
			arrowSlot = inventory.findItem("arrow_iron");
			arrowType = mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType.IRON;
		}
		if (arrowSlot == null) {
			arrowSlot = inventory.findItem("arrow_stone");
			arrowType = mc.sayda.mcraze.entity.projectile.EntityArrow.ArrowType.STONE;
		}

		if (arrowSlot != null) {
			// Consume Arrow
			arrowSlot.count--;
			if (arrowSlot.count <= 0)
				arrowSlot.setEmpty();
			world.broadcastInventoryUpdates();

			float dx = targetX - x;
			float dy = targetY - y;
			float len = (float) Math.sqrt(dx * dx + dy * dy);
			if (len < 0.01f)
				len = 1f;

			// Min speed 0.5, Max speed 1.5 based on charge
			float speed = 0.5f + (chargePct * 1.0f);

			mc.sayda.mcraze.entity.projectile.EntityArrow arrow = new mc.sayda.mcraze.entity.projectile.EntityArrow(
					x, y + 0.2f, this, arrowType);
			arrow.dx = (dx / len) * speed;
			arrow.dy = (dy / len) * speed;

			// Increase damage based on charge
			arrow.damage = (int) (arrow.getType().damage * (0.5f + chargePct));

			world.addEntity(arrow);

			// Sound
			world.broadcastSound("assets/sounds/random/bow_fire.wav");
		}
	}

	private void consumeItem(InventoryItem slot, mc.sayda.mcraze.server.SharedWorld world) {
		mc.sayda.mcraze.item.Consumable consumable = (mc.sayda.mcraze.item.Consumable) slot.getItem();

		// Apply Buff
		if (consumable.buffType != null) {
			addBuff(new mc.sayda.mcraze.entity.buff.Buff(consumable.buffType, consumable.buffDuration,
					consumable.buffAmplifier));
		}

		// Heal
		if (consumable.healingAmount > 0) {
			int finalHeal = consumable.healingAmount;

			// Passive: Comfort Food (Druid) - +20% healing from consumables
			if (unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.COMFORT_FOOD)) {
				finalHeal = (int) (finalHeal * 1.2f);
			}

			heal(finalHeal);
		}

		// Reduce count
		slot.remove(1);

		// Return empty bottle (simplified: just add to inventory or drop)
		// For now, if slot becomes empty, replace with bottle.
		// If stack > 0, we should try to give a bottle.
		if (slot.isEmpty()) {
			mc.sayda.mcraze.item.Item bottle = mc.sayda.mcraze.Constants.itemTypes.get("bottle");
			if (bottle != null) {
				slot.setItem(bottle.clone());
				slot.setCount(1);
			}
		} else {
			// Add bottle to inventory
			mc.sayda.mcraze.item.Item bottle = mc.sayda.mcraze.Constants.itemTypes.get("bottle");
			if (bottle != null) {
				inventory.addItem(bottle.clone(), 1); // Simplification: auto-add
			}
		}

		world.broadcastInventoryUpdates();

		// Play drink sound
		world.broadcastSound("assets/sounds/random/drink.wav"); // Requires asset
	}

	/**
	 * Calculate stats (Armor, Magic Resist) based on equipped items.
	 */
	private void updateStatsFromEquipment() {
		int totalArmor = 0;
		int totalMagicResist = 0;

		if (inventory != null && inventory.equipment != null) {
			for (InventoryItem slot : inventory.equipment) {
				if (slot != null && !slot.isEmpty() && slot.getItem() instanceof mc.sayda.mcraze.item.Armor) {
					mc.sayda.mcraze.item.Armor armorItem = (mc.sayda.mcraze.item.Armor) slot.getItem();
					totalArmor += armorItem.defense;
					totalMagicResist += armorItem.magicDefense;
				}
			}
		}

		// Update LivingEntity fields
		this.armor = totalArmor;
		this.magicResistance = totalMagicResist;
	}
}
