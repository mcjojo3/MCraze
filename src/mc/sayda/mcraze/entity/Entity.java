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

package mc.sayda.mcraze.entity;

import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.item.Item;
import java.util.UUID;

public abstract class Entity implements java.io.Serializable {
	private static final long serialVersionUID = 1L;

	protected static final float gravityAcceleration = Constants.PHYSICS_GRAVITY;
	protected static final float waterAcceleration = Constants.PHYSICS_WATER_ACCEL;
	protected static final float maxWaterDY = Constants.PHYSICS_MAX_WATER_DY;
	protected static final float swimUpVelocity = Constants.PHYSICS_SWIM_VELOCITY; // Slightly higher than maxWaterDY
																					// for active swimming

	// Unique identifier for network entity tracking
	private String uuid;

	public float x;
	public float y;
	public float dx;
	public float dy;

	public transient Sprite sprite;
	protected boolean gravityApplies;
	public int widthPX;
	public int heightPX;

	public boolean dead = false;
	protected long ticksAlive = 0;

	public Entity(String ref, boolean gravityApplies, float x, float y, int width, int height) {
		if (ref != null) {
			this.sprite = SpriteStore.get().getSprite(ref);
		}
		this.gravityApplies = gravityApplies;
		this.x = x;
		this.y = y;
		this.widthPX = width;
		this.heightPX = height;
		this.dx = this.dy = 0;
		// Generate unique UUID for network entity tracking
		this.uuid = UUID.randomUUID().toString();
	}

	/**
	 * Get the number of ticks this entity has been alive (for animation timing)
	 */
	public long getTicksAlive() {
		return ticksAlive;
	}

	/**
	 * Set the number of ticks this entity has been alive (used by client for
	 * animation sync)
	 */
	public void setTicksAlive(long ticksAlive) {
		this.ticksAlive = ticksAlive;
	}

	@Override
	public Entity clone() throws CloneNotSupportedException {
		Entity cloned = (Entity) super.clone();
		// Reset UUID for cloned instances so they behave as distinct entities
		cloned.uuid = UUID.randomUUID().toString();
		return cloned;
	}

	public void updatePosition(World world, int tileSize) {
		// Noclip mode: ghost through blocks (no collision detection)
		if (this instanceof mc.sayda.mcraze.entity.LivingEntity) {
			mc.sayda.mcraze.entity.LivingEntity living = (mc.sayda.mcraze.entity.LivingEntity) this;
			if (living.noclip) {
				// Directly update position without collision checks
				x += dx;
				y += dy;
				return;
			}
		}

		int pixels = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) * tileSize);

		boolean favorVertical = (Math.abs(dy) > Math.abs(dx));
		boolean hitTop = false;
		boolean hitBottom = false;

		float left = this.getLeft(tileSize);
		float right = this.getRight(tileSize);
		float top = this.getTop(tileSize);
		float bottom = this.getBottom(tileSize);

		boolean topLeft = true;
		boolean topRight = true;
		boolean bottomLeft = true;
		boolean bottomRight = true;
		boolean middleLeft = true;
		boolean middleRight = true;

		float scale = 1.f / pixels;

		if (favorVertical) {
			for (int i = 1; i <= pixels && topLeft && topRight && bottomLeft && bottomRight; i++) {
				top = top + dy * scale;
				bottom = bottom + dy * scale;

				topLeft = world.passable((int) left, (int) top);
				topRight = world.passable((int) right, (int) top);
				bottomLeft = world.passable((int) left, (int) bottom);
				bottomRight = world.passable((int) right, (int) bottom);
				middleLeft = world.passable((int) left, (int) (top + (bottom - top) / 2));
				middleRight = world.passable((int) right, (int) (top + (bottom - top) / 2));

				if (!(topLeft && topRight && bottomLeft && bottomRight && middleLeft && middleRight)) {
					hitTop |= !topLeft || !topRight;
					hitBottom |= !bottomLeft || !bottomRight;
					top = top - dy * scale;
					bottom = bottom - dy * scale;
				}
			}
			for (int i = 1; i <= pixels && topLeft && topRight && bottomLeft && bottomRight; i++) {
				left = left + dx * scale;
				right = right + dx * scale;

				topLeft = world.passable((int) left, (int) top);
				topRight = world.passable((int) right, (int) top);
				bottomLeft = world.passable((int) left, (int) bottom);
				bottomRight = world.passable((int) right, (int) bottom);
				middleLeft = world.passable((int) left, (int) (top + (bottom - top) / 2));
				middleRight = world.passable((int) right, (int) (top + (bottom - top) / 2));

				if (!(topLeft && topRight && bottomLeft && bottomRight && middleLeft && middleRight)) {
					left = left - dx * scale;
					right = right - dx * scale;
				}
			}
		} else {
			for (int i = 1; i <= pixels && topLeft && topRight && bottomLeft && bottomRight; i++) {
				left = left + dx * scale;
				right = right + dx * scale;

				topLeft = world.passable((int) left, (int) top);
				topRight = world.passable((int) right, (int) top);
				bottomLeft = world.passable((int) left, (int) bottom);
				bottomRight = world.passable((int) right, (int) bottom);
				middleLeft = world.passable((int) left, (int) (top + (bottom - top) / 2));
				middleRight = world.passable((int) right, (int) (top + (bottom - top) / 2));

				if (!(topLeft && topRight && bottomLeft && bottomRight && middleLeft && middleRight)) {
					left = left - dx * scale;
					right = right - dx * scale;
				}
			}
			for (int i = 1; i <= pixels && topLeft && topRight && bottomLeft && bottomRight; i++) {
				top = top + dy * scale;
				bottom = bottom + dy * scale;

				topLeft = world.passable((int) left, (int) top);
				topRight = world.passable((int) right, (int) top);
				bottomLeft = world.passable((int) left, (int) bottom);
				bottomRight = world.passable((int) right, (int) bottom);
				middleLeft = world.passable((int) left, (int) (top + (bottom - top) / 2));
				middleRight = world.passable((int) right, (int) (top + (bottom - top) / 2));

				if (!(topLeft && topRight && bottomLeft && bottomRight && middleLeft && middleRight)) {
					hitTop |= !topLeft || !topRight;
					hitBottom |= !bottomLeft || !bottomRight;
					top = top - dy * scale;
					bottom = bottom - dy * scale;
				}
			}
		}

		if (gravityApplies) {
			if (world.isClimbable((int) left, (int) top)
					|| world.isClimbable((int) right, (int) top)
					|| world.isClimbable((int) left, (int) bottom)
					|| world.isClimbable((int) right, (int) bottom)) {
				dy += waterAcceleration;
				if (dy > 0) {
					dy = Math.min(maxWaterDY, dy);
				} else {
					dy = Math.max(-maxWaterDY, dy);
				}
			} else {
				dy += gravityAcceleration;
			}
			if (hitBottom) {
				// mathemagically derived to mimic the damage from
				// counting the number of meters dropped
				int dmg = ((int) (114 * dy)) - 60;

				// Check for Spike Trap
				// Get tile at center bottom (using NEW position)
				int tx = (int) ((left + right) / 2.0f);
				int ty = (int) (bottom + 0.01f); // Look slightly into the block below

				// Fallback safe check for world bounds
				if (tx >= 0 && tx < world.width && ty >= 0 && ty < world.height) {
					if (world.tiles[tx][ty] != null && world.tiles[tx][ty].type.name == Constants.TileID.SPIKE_TRAP) {
						float mult = world.getTrapFallDamageMultiplier(tx, ty);
						dmg *= mult; // Apply class-specific trap damage multiplier
					}
				}

				if (dmg > 0) {
					if (this instanceof mc.sayda.mcraze.entity.LivingEntity) {
						// Apply damage
						// Use FALL damage type
						((mc.sayda.mcraze.entity.LivingEntity) this).takeDamage(dmg,
								mc.sayda.mcraze.entity.DamageType.FALL);
					}
				}
				dx *= 0.9; // loss of energy due to friction
			}
		}
		if (hitTop) {
			dy = 0.0000001f;
		} else if (hitBottom) {
			dy = 0;
		}

		x = left;
		y = top;
	}

	public float getCenterY(int tileSize) {
		return y + (float) heightPX / (2 * tileSize);
	}

	public float getCenterX(int tileSize) {
		return x + (float) widthPX / (2 * tileSize);
	}

	public float getTop(int tileSize) {
		return y;
	}

	public float getBottom(int tileSize) {
		return (y + (float) (heightPX) / tileSize);
	}

	public float getLeft(int tileSize) {
		return x;
	}

	public float getRight(int tileSize) {
		return x + (float) (widthPX) / tileSize;
	}

	public boolean isInWater(World world, int tileSize) {
		int left = (int) this.getLeft(tileSize);
		int right = (int) this.getRight(tileSize);
		int top = (int) this.getTop(tileSize);
		int bottom = (int) this.getBottom(tileSize);
		return (world.isLiquid(left, top) || world.isLiquid(right, top)
				|| world.isLiquid(left, bottom) || world.isLiquid(right, bottom));
	}

	public boolean isHeadUnderWater(World world, int tileSize) {
		int top = (int) this.getTop(tileSize);
		int centerX = (int) this.getCenterX(tileSize);
		return world.isLiquid(centerX, top);
	}

	public boolean isInWaterOrClimbable(World world, int tileSize) {
		int left = (int) this.getLeft(tileSize);
		int right = (int) this.getRight(tileSize);
		int top = (int) this.getTop(tileSize);
		int bottom = (int) this.getBottom(tileSize);
		return (world.isLiquid(left, top) || world.isLiquid(right, top)
				|| world.isLiquid(left, bottom) || world.isLiquid(right, bottom)
				|| world.isClimbable(left, top) || world.isClimbable(right, top)
				|| world.isClimbable(left, bottom) || world.isClimbable(right, bottom));
	}

	public boolean collidesWith(Entity entity, int tileSize) {
		float left1, left2;
		float right1, right2;
		float top1, top2;
		float bottom1, bottom2;

		left1 = this.x;
		left2 = entity.x;
		right1 = this.getRight(tileSize);
		right2 = entity.getRight(tileSize);
		top1 = this.y;
		top2 = entity.y;
		bottom1 = this.getBottom(tileSize);
		bottom2 = entity.getBottom(tileSize);

		return !(bottom1 < top2 || top1 > bottom2 || right1 < left2 || left1 > right2);
	}

	public boolean inBoundingBox(Int2 pos, int tileSize) {
		int left = (int) this.getLeft(tileSize);
		int right = (int) this.getRight(tileSize);
		int top = (int) this.getTop(tileSize);
		int bottom = (int) this.getBottom(tileSize);

		return pos.x >= left && pos.x <= right && pos.y >= top && pos.y <= bottom;
	}

	public int damageFlashTicks = 0; // Ticks remaining for red damage flash

	public void tick(SharedWorld world) {
		// Base entity logic (if any)
		ticksAlive++;
	}

	public void draw(GraphicsHandler g, float cameraX, float cameraY, int screenWidth,
			int screenHeight, int tileSize) {
		Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth,
				screenHeight, tileSize, x, y);
		if (StockMethods.onScreen) {
			// Scale width/height based on zoom level (tileSize)
			float scale = (float) tileSize / mc.sayda.mcraze.Constants.TILE_SIZE;
			int drawW = (int) (widthPX * scale);
			int drawH = (int) (heightPX * scale);

			mc.sayda.mcraze.graphics.Color tint = null;
			if (damageFlashTicks > 0) {
				// Apply red tint for damage indication (50% opacity red)
				tint = new mc.sayda.mcraze.graphics.Color(255, 0, 0, 128);
			}

			if (this instanceof Item) {
				((Item) this).drawLayers(g, pos.x, pos.y, drawW, drawH, tint);
			} else {
				if (tint != null) {
					g.drawImage(sprite, pos.x, pos.y, drawW, drawH, tint);
				} else {
					sprite.draw(g, pos.x, pos.y, drawW, drawH);
				}
			}
		}
	}

	/**
	 * Get the unique UUID for this entity (used for network entity tracking)
	 */
	public String getUUID() {
		return uuid;
	}

	/**
	 * Set the UUID for this entity (used when deserializing from network)
	 */
	public void setUUID(String uuid) {
		this.uuid = uuid;
	}

	// Only living entities have hitpoints; they should override these methods.

	public void takeDamage(int amount, mc.sayda.mcraze.entity.DamageType type) {
	}

	public void heal(int amount) {
	}
}
