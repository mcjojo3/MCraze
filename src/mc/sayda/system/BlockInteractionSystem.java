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

package mc.sayda.system;

import java.util.ArrayList;
import java.util.Random;

import mc.sayda.Constants;
import mc.sayda.Constants.TileID;
import mc.sayda.GraphicsHandler;
import mc.sayda.Sprite;
import mc.sayda.SpriteStore;
import mc.sayda.entity.Entity;
import mc.sayda.entity.Player;
import mc.sayda.item.InventoryItem;
import mc.sayda.item.Item;
import mc.sayda.item.Tool;
import mc.sayda.util.Int2;
import mc.sayda.util.StockMethods;
import mc.sayda.world.World;

/**
 * BlockInteractionSystem handles all block breaking and placing logic.
 * This includes:
 * - Tracking breaking progress
 * - Rendering break animations
 * - Tool durability
 * - Block drops
 * - Block placement validation
 * - Crafting table interaction
 */
public class BlockInteractionSystem {

	private int breakingTicks;
	private Int2 breakingPos;
	private Sprite[] breakingSprites;
	private Random random;

	public BlockInteractionSystem(Random random) {
		this.random = random;
		breakingPos = new Int2(-1, -1);
		loadSprites();
	}

	/**
	 * Load breaking animation sprites
	 */
	private void loadSprites() {
		final SpriteStore ss = SpriteStore.get();
		breakingSprites = new Sprite[8];
		for (int i = 0; i < 8; i++) {
			breakingSprites[i] = ss.getSprite("sprites/tiles/break" + i + ".png");
		}
	}

	/**
	 * Handle block breaking logic
	 * @param g Graphics handler for rendering
	 * @param player Player entity
	 * @param world Game world
	 * @param entities List of all entities
	 * @param cameraX Camera X position
	 * @param cameraY Camera Y position
	 * @param tileSize Size of tiles in pixels
	 * @param isBreaking Whether left mouse button is pressed
	 */
	public void handleBlockBreaking(GraphicsHandler g, Player player, World world,
			ArrayList<Entity> entities, float cameraX, float cameraY, int tileSize, boolean isBreaking) {

		if (isBreaking && player.handTargetPos.x != -1) {
			// Only allow breaking if there's actually a block to break
			if (!world.isBreakable(player.handTargetPos.x, player.handTargetPos.y)) {
				breakingTicks = 0;
				return;
			}

			if (player.handTargetPos.equals(breakingPos)) {
				breakingTicks++;
			} else {
				breakingTicks = 0;
			}
			breakingPos = player.handTargetPos;

			InventoryItem inventoryItem = player.inventory.selectedItem();
			Item item = inventoryItem.getItem();
			int ticksNeeded = world.breakTicks(breakingPos.x, breakingPos.y, item);

			Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, tileSize,
					tileSize, tileSize, breakingPos.x, breakingPos.y);
			int sprite_index = (int) (Math.min(1, (double) breakingTicks / ticksNeeded) * (breakingSprites.length - 1));
			breakingSprites[sprite_index].draw(g, pos.x, pos.y, tileSize, tileSize);

			if (breakingTicks >= ticksNeeded) {
				// Block is broken - handle tool durability
				if (item != null && item.getClass() == Tool.class) {
					Tool tool = (Tool) item;
					tool.uses++;
					if (tool.uses >= tool.totalUses) {
						inventoryItem.setEmpty();
					}
				}

				breakingTicks = 0;
				TileID name = world.removeTile(player.handTargetPos.x, player.handTargetPos.y);

				// Convert certain blocks when broken
				if (name == TileID.GRASS) {
					name = TileID.DIRT;
				}
				if (name == TileID.STONE) {
					name = TileID.COBBLE;
				}
				if (name == TileID.LEAVES && random.nextDouble() < .1) {
					name = TileID.SAPLING;
				}

				// Spawn dropped item
				Item newItem = Constants.itemTypes.get((char) name.breaksInto);
				if (newItem != null) {
					newItem = (Item) newItem.clone();
					newItem.x = player.handTargetPos.x + random.nextFloat()
							* (1 - (float) newItem.widthPX / tileSize);
					newItem.y = player.handTargetPos.y + random.nextFloat()
							* (1 - (float) newItem.widthPX / tileSize);
					newItem.dy = -.07f;
					entities.add(newItem);
				}
			}
		} else {
			breakingTicks = 0;
		}
	}

	/**
	 * Handle block placing logic at the targeted position.
	 * Only allows placing if the target position is empty and has an adjacent block.
	 *
	 * @param player Player entity
	 * @param world Game world
	 * @param tileSize Size of tiles in pixels
	 * @return true if right-click was consumed, false otherwise
	 */
	public boolean handleBlockPlacing(Player player, World world, int tileSize) {
		// Check if clicking on a crafting table
		if (world.isCraft(player.handTargetPos.x, player.handTargetPos.y)) {
			player.inventory.tableSizeAvailable = 3;
			player.inventory.setVisible(true);
			return true;
		}

		// Placing a block
		InventoryItem current = player.inventory.selectedItem();
		if (!current.isEmpty()) {
			TileID itemID = Constants.tileIDs.get(current.getItem().item_id);

			// Can only place if the target position is empty (not a solid block)
			if (world.isBreakable(player.handTargetPos.x, player.handTargetPos.y)) {
				return false;  // Cannot place - there's already a block here
			}

			// Check if there's at least one adjacent block (up, down, left, right)
			int x = player.handTargetPos.x;
			int y = player.handTargetPos.y;
			boolean hasAdjacentBlock = world.isBreakable(x - 1, y) || // left
					world.isBreakable(x + 1, y) || // right
					world.isBreakable(x, y - 1) || // up
					world.isBreakable(x, y + 1);   // down

			if (!hasAdjacentBlock) {
				return false;  // Cannot place - no adjacent block
			}

			// Check if the block would collide with the player
			boolean isPassable = Constants.tileTypes.get(itemID).type.passable;
			if (isPassable || !player.inBoundingBox(player.handTargetPos, tileSize)) {
				if (world.addTile(player.handTargetPos, itemID)) {
					// Placed successfully
					player.inventory.decreaseSelected(1);
					return true;
				}
			}
		}
		return false;
	}
}
