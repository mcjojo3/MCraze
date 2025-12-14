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

import mc.sayda.item.Item;
import mc.sayda.ui.Inventory;
import mc.sayda.world.World;

public abstract class LivingEntity extends Entity {
	private static final long serialVersionUID = 1L;
	protected static final int maxHP = 100;
	
	public int hitPoints;
	public boolean climbing = false;
	public boolean facingRight = true;
	public boolean dead = false;  // Track if entity has died to prevent multiple death triggers
	public Inventory inventory;
	
	protected final float walkSpeed = .1f;
	protected final float swimSpeed = .04f;
	protected float armLength = 4.5f;
	protected float moveDirection = 0;
	protected long ticksAlive = 0;
	protected int ticksUnderwater = 0;
	protected boolean jumping = false;
	
	public LivingEntity(boolean gravityApplies, float x, float y, int width, int height) {
		super(null, gravityApplies, x, y, width, height);
		this.hitPoints = maxHP;
		inventory = new Inventory(10, 4, 3);
	}

	public void giveItem(Item item, int count) {
		inventory.addItem(item, count);
	}
	
	public int airRemaining() {
		return Math.max(10 - (ticksUnderwater / 50), 0);
	}
	
	public void jump(World world, int tileSize) {
		if (jumping) {
			return;
		}

		if (!this.isInWater(world, tileSize)) {
			dy = -.3f;
			jumping = true;
		} else {
			dy = -swimUpVelocity;  // Use dedicated swim velocity for upward movement in water
		}
	}
	
	@Override
	public void updatePosition(World world, int tileSize) {
		ticksAlive++;
		boolean isSwimClimb = this.isInWaterOrClimbable(world, tileSize);
		if (isSwimClimb) {
			dx = moveDirection * swimSpeed;
		} else {
			dx = moveDirection * walkSpeed;
		}
		if (climbing) {
			if (isSwimClimb) {
				jumping = false;
				dy = -swimUpVelocity;  // Use dedicated swim velocity for climbing in water
			} else {
				jump(world, tileSize);
			}
		}
		super.updatePosition(world, tileSize);
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
		if (dead) {
			return;  // Don't take damage if already dead
		}

		this.hitPoints -= amount;

		// Clamp health to 0 minimum (don't go below 0)
		if (this.hitPoints < 0) {
			this.hitPoints = 0;
		}

		System.out.println("Took " + amount + " damage. Current health = " + this.hitPoints);

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
		int newHP = this.hitPoints + amount;
		this.hitPoints = (newHP > maxHP) ? maxHP : newHP;
		System.out.println("Healed " + amount + ". Current health = " + this.hitPoints);
	}
}