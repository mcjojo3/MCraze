/*
 * Copyright 2025 SaydaGames (mc_jojo3)
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

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.ui.Inventory;
import mc.sayda.mcraze.world.World;

public abstract class LivingEntity extends Entity {
	private static final long serialVersionUID = 1L;
	protected static final int maxHP = 100;

	public int hitPoints;
	public boolean climbing = false;
	public boolean facingRight = true;
	public Inventory inventory;
	public boolean dead = false;
	public boolean flying = false;  // Flying mode (no gravity, free movement)
	public boolean noclip = false;  // Noclip mode (ghost through blocks when flying)
	public boolean sneaking = false;  // Sneaking mode (prevents falling off edges, lowers in fly mode)
	public float speedMultiplier = 1.0f;  // Speed multiplier (1.0 = normal speed)

	protected final float walkSpeed = .1f;
	protected final float swimSpeed = .04f;
	protected float armLength = Constants.ARM_LENGTH;
	protected float moveDirection = 0;
	protected long ticksAlive = 0;
	public int ticksUnderwater = 0;  // PUBLIC for network sync
	public boolean jumping = false;  // PUBLIC for network sync

	public LivingEntity(boolean gravityApplies, float x, float y, int width, int height) {
		super(null, gravityApplies, x, y, width, height);
		this.hitPoints = maxHP;
		inventory = new Inventory(10, 4, 3);
	}

	/**
	 * Give items to this entity's inventory.
	 * @param item Item to give
	 * @param count Number of items
	 * @return Number of items that couldn't fit (0 if all added successfully)
	 */
	public int giveItem(Item item, int count) {

        return inventory.addItem(item, count);
	}

	public int airRemaining() {

        return Math.max(10 - (ticksUnderwater / 50), 0);
	}

	/**
	 * Get the number of ticks this entity has been alive (for animation timing)
	 */
	public long getTicksAlive() {

        return ticksAlive;
	}

	/**
	 * Set the number of ticks this entity has been alive (used by client for animation sync)
	 */
	public void setTicksAlive(long ticksAlive) {

        this.ticksAlive = ticksAlive;
	}

    public int getMaxHP() {
        return maxHP;
    }


    public void jump(World world, int tileSize) {
		if (dead || jumping) {
			return;
		}

		if (!this.isInWater(world, tileSize)) {
			dy = -.3f;
			jumping = true;
		} else {
			dy = -maxWaterDY - .000001f;// BIG HACK
		}
	}

	@Override
	public void updatePosition(World world, int tileSize) {
		ticksAlive++;

		// Stop all movement when dead
		if (dead) {
			moveDirection = 0;
			climbing = false;
			dx = 0;
			super.updatePosition(world, tileSize);
			return;
		}

		// Flying mode: disable gravity and allow free movement
		if (flying) {
			dx = moveDirection * walkSpeed * speedMultiplier;
			if (climbing) {
				dy = -walkSpeed * speedMultiplier * 2;  // Upward movement
			} else if (sneaking) {
				dy = walkSpeed * speedMultiplier * 2;  // Downward movement when sneaking
			} else {
				dy = 0;  // No gravity when flying
			}
			jumping = false;

			// Temporarily disable gravity for flying
			boolean wasGravityApplies = gravityApplies;
			gravityApplies = false;
			super.updatePosition(world, tileSize);
			gravityApplies = wasGravityApplies;
		} else {
			// Normal movement (walking/swimming)
			boolean isSwimClimb = this.isInWaterOrClimbable(world, tileSize);
			if (isSwimClimb) {
				dx = moveDirection * swimSpeed * speedMultiplier;
			} else {
				dx = moveDirection * walkSpeed * speedMultiplier;
			}
			if (climbing) {
				if (isSwimClimb) {
					jumping = false;
					dy = -maxWaterDY - .000001f;// BIG HACK
				} else {
					jump(world, tileSize);
				}
			}

			// Edge prevention when sneaking: prevent falling off block edges
			if (sneaking && !jumping && dx != 0) {
				// Check if there's a solid block below the player at the next position
				float nextX = x + dx;
				float nextBottom = getBottom(tileSize);
				float nextLeft = nextX;
				float nextRight = nextX + (float) widthPX / tileSize;

				// Check one tile below the next position
				int checkY = (int) (nextBottom + 0.1f);  // Slightly below feet
				boolean hasGroundLeft = !world.passable((int) nextLeft, checkY);
				boolean hasGroundRight = !world.passable((int) nextRight, checkY);

				// If moving would cause player to walk off edge, prevent it
				if (!hasGroundLeft && !hasGroundRight) {
					dx = 0;  // Cancel horizontal movement
				}
			}

			super.updatePosition(world, tileSize);
		}
		if (this.dy == 0) {
			jumping = false;
		}
		if (this.isInWater(world, tileSize)) {
			jumping = false;
		}
		// drown check
		if (this.isHeadUnderWater(world, tileSize)) {
			ticksUnderwater++;
			if (this.airRemaining() == 0) {
				this.takeDamage(5);
				// back to about 4 bubbles' worth of air
				ticksUnderwater = 300;
			}
		} else {
			ticksUnderwater = 0;
		}
	}

	public void startLeft(boolean slow) {
		if (dead) return;  // Prevent movement when dead
		facingRight = false;
		if (slow) {
			moveDirection = -.2f;
		} else {
			moveDirection = -1;
		}
	}

	public void stopLeft() {
		if (moveDirection < 0) {
			moveDirection = 0;
		}
	}

	public void startRight(boolean slow) {
		if (dead) return;  // Prevent movement when dead
		facingRight = true;
		if (slow) {
			moveDirection = .2f;
		} else {
			moveDirection = 1;
		}
	}

	public void stopRight() {
		if (moveDirection > 0) {
			moveDirection = 0;
		}
	}

	public void startClimb() {
		if (dead) return;  // Prevent climbing when dead
		climbing = true;
	}

	public void endClimb() {
		climbing = false;
	}

	public float findIntersection(float rayOx, float rayOy, float m, float p1x, float p1y,
			float p2x, float p2y) {
		float freeVar = -1;
		if (p1x == p2x)// segment is vertical
		{
			freeVar = -m * (rayOx - p1x) + rayOy;// y1
			if ((freeVar < p1y && freeVar < p2y) || (freeVar > p1y && freeVar > p2y)) {
				return -1;
			}
		} else if (p1y == p2y)// segment is horizontal
		{
			freeVar = -(rayOy - p1y) / m + rayOx;// x1
			if ((freeVar < p1x && freeVar < p2x) || (freeVar > p1x && freeVar > p2x)) {
				return -1;
			}
		} else {
			System.err.println("Find intersection -- bad arguments");
		}
		return freeVar;
	}

	@Override
	public void takeDamage(int amount) {
		this.hitPoints -= amount;

		// Clamp health to 0 minimum (don't go below 0)
		if (this.hitPoints < 0) {
			this.hitPoints = 0;
		}

		// TODO: Add sound effect when sound system is implemented
		// Example: SoundPlayer.play("hit");

		// Trigger death immediately when health reaches exactly 0
		if (this.hitPoints == 0 && !dead) {
			dead = true;  // Mark as dead before calling onDeath
			onDeath();
		}
	}

	/**
	 * Check if this entity is dead (health <= 0)
	 * @return true if dead
	 */
	public boolean isDead() {
		return hitPoints <= 0;
	}

	/**
	 * Called when the entity dies. Subclasses can override for specific death behavior.
	 */
	protected void onDeath() {
		System.out.println(getClass().getSimpleName() + " has died!");
		// TODO: Add death sound effect when sound system is implemented
		// Example: SoundPlayer.play("death");
	}

    @Override
    public void heal(int amount) {
        if (amount <= 0 || dead) {
            return;
        }

        // Clamp healing to avoid overflow
        int healAmount = Math.min(amount, maxHP - this.hitPoints);
        if (healAmount <= 0) {
            return;
        }
        this.hitPoints += healAmount;
        System.out.println(
                "Healed " + healAmount + ". Current health = " + this.hitPoints
        );
    }
}
