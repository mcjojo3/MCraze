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

package mc.sayda.entity;

import mc.sayda.GraphicsHandler;
import mc.sayda.Sprite;
import mc.sayda.SpriteStore;
import mc.sayda.item.InventoryItem;
import mc.sayda.item.Item;
import mc.sayda.util.Int2;
import mc.sayda.util.StockMethods;
import mc.sayda.world.World;

public class Player extends LivingEntity {
	private static final long serialVersionUID = 1L;
	
	public Int2 handTargetPos = new Int2(0, 0);  // Unified position for breaking/placing
	public float handStartX;
	public float handStartY;
	public float handEndX;
	public float handEndY;
	
	private Sprite leftWalkSprite;
	private Sprite rightWalkSprite;
	
	public Player(boolean gravityApplies, float x, float y, int width, int height) {
		super(gravityApplies, x, y, width, height);
		
		leftWalkSprite = SpriteStore.get().getSprite("sprites/entities/left_man.png");
		rightWalkSprite = SpriteStore.get().getSprite("sprites/entities/right_man.png");
		sprite = SpriteStore.get().getSprite("sprites/entities/player.gif");
	}

	public void setHotbarItem(int hotbarIdx) {
		inventory.hotbarIdx = hotbarIdx;
	}

	/**
	 * Scroll the hotbar by the given number of slots.
	 * Clamps the result to valid range [0, 9].
	 * @param scrollAmount Number of slots to scroll (positive or negative)
	 */
	public void scrollHotbar(int scrollAmount) {
		inventory.hotbarIdx += scrollAmount;
		inventory.hotbarIdx = Math.max(0, Math.min(9, inventory.hotbarIdx));
	}

	/**
	 * Toss the selected inventory item.
	 * Returns the item to be added to the world, or null if nothing to toss.
	 * @param random Random number generator for positioning
	 * @return The item to add to entities, or null
	 */
	public Item tossSelectedItem(java.util.Random random) {
		InventoryItem inventoryItem = inventory.selectedItem();
		if (inventoryItem.isEmpty()) {
			return null;
		}

		Item newItem = inventoryItem.getItem();
		if (!(newItem instanceof mc.sayda.item.Tool)) {
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
						if (item instanceof mc.sayda.item.Tool) {
							// Tools are unique instances, use directly
							droppedItem = item;
						} else {
							// Regular items can be cloned
							droppedItem = (Item) item.clone();
						}

						// Scatter items around player position
						droppedItem.x = x + (random.nextFloat() - 0.5f) * 2;
						droppedItem.y = y + (random.nextFloat() - 0.5f) * 2;
						droppedItem.dy = -0.1f - random.nextFloat() * 0.1f;  // Pop up
						droppedItem.dx = (random.nextFloat() - 0.5f) * 0.2f;  // Scatter horizontally
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

		// Convert mouse position to block coordinates (mouseX/Y are already in world coords)
		int targetBlockX = (int) Math.floor(mouseX);
		int targetBlockY = (int) Math.floor(mouseY);

		// Calculate distance from player to target block center
		float dx = (targetBlockX + 0.5f) - playerX;
		float dy = (targetBlockY + 0.5f) - playerY;
		float distance = (float) Math.sqrt(dx * dx + dy * dy);

		// Check if block is within arm's reach
		if (distance <= armLength) {
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
			int frame = (int) x % 4;// (int) ((ticksAlive/20)%4);
			if (facingRight) {
				if (frame == 0 || frame == 2 || dx <= 0) {
					sprite.draw(g, pos.x, pos.y, widthPX, heightPX);
				} else if (frame == 1) {
					rightWalkSprite.draw(g, pos.x, pos.y, widthPX, heightPX);
				} else {
					leftWalkSprite.draw(g, pos.x, pos.y, widthPX, heightPX);
				}
			} else {
				if (frame == 0 || frame == 2 || dx >= 0) {
					sprite.draw(g, pos.x + widthPX, pos.y, -widthPX, heightPX);
				} else if (frame == 1) {
					rightWalkSprite.draw(g, pos.x + widthPX, pos.y, -widthPX, heightPX);
				} else {
					leftWalkSprite.draw(g, pos.x + widthPX, pos.y, -widthPX, heightPX);
				}
			}
		}
	}
}
