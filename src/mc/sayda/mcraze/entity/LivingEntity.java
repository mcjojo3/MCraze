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

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.item.Item;
import mc.sayda.mcraze.player.Inventory;
import mc.sayda.mcraze.world.World;

public abstract class LivingEntity extends Entity {
	private static final long serialVersionUID = 1L;
	public int maxHP = 100;

	public int hitPoints;
	public int magicResistance = 0; // % reduction for magic damage (0-100)
	public int armor = 0; // % reduction for physical damage

	// Let's add basic fields here for mobs.
	public boolean climbing = false;
	public boolean facingRight = true;
	public Inventory inventory;
	public boolean flying = false; // Flying mode (no gravity, free movement)
	public boolean noclip = false; // Noclip mode (ghost through blocks when flying)
	public boolean sneaking = false; // Sneaking mode (prevents falling off edges, lowers in fly mode)
	public float speedMultiplier = 1.0f; // Speed multiplier (1.0 = normal speed)
	public float jumpMultiplier = 1.0f; // Jump height multiplier

	protected final float walkSpeed = .1f;
	protected final float swimSpeed = .04f;
	protected float armLength = Constants.PLAYER_REACH;
	protected float moveDirection = 0;
	public int ticksUnderwater = 0; // PUBLIC for network sync
	public boolean jumping = false; // PUBLIC for network sync
	public int invulnerabilityTicks = 0; // Cooldown between taking damage (approx 0.5s at 20fps)
	public java.util.List<mc.sayda.mcraze.entity.buff.Buff> activeBuffs = new java.util.ArrayList<>();
	public mc.sayda.mcraze.entity.Entity lastAttacker = null; // Who dealt the last bit of damage
	private boolean lastDead = false;

	public void addBuff(mc.sayda.mcraze.entity.buff.Buff buff) {
		boolean merged = false;
		for (mc.sayda.mcraze.entity.buff.Buff existing : activeBuffs) {
			if (existing.getType() == buff.getType()) {
				existing.combine(buff);
				merged = true;
				break;
			}
		}
		if (!merged) {
			activeBuffs.add(buff);
		}
	}

	public void removeBuff(mc.sayda.mcraze.entity.buff.BuffType type) {
		activeBuffs.removeIf(b -> b.getType() == type);
	}

	public boolean hasBuff(mc.sayda.mcraze.entity.buff.BuffType type) {
		for (mc.sayda.mcraze.entity.buff.Buff b : activeBuffs) {
			if (b.getType() == type)
				return true;
		}
		return false;
	}

	public int getBuffAmplifier(mc.sayda.mcraze.entity.buff.BuffType type) {
		for (mc.sayda.mcraze.entity.buff.Buff b : activeBuffs) {
			if (b.getType() == type)
				return b.getAmplifier();
		}
		return -1;
	}

	public float baseSpeedMultiplier = 1.0f; // Multiplier set by commands/events (persists)

	protected void tickBuffs() {
		// Reset transient stats to base value
		this.speedMultiplier = baseSpeedMultiplier;

		java.util.Iterator<mc.sayda.mcraze.entity.buff.Buff> it = activeBuffs.iterator();
		while (it.hasNext()) {
			mc.sayda.mcraze.entity.buff.Buff buff = it.next();
			buff.tick();

			// Apply tick-based effects (Regeneration, Poison, etc)
			if (buff.getType() == mc.sayda.mcraze.entity.buff.BuffType.REGENERATION) {
				// Regen: Heal 1 HP every 50 ticks (2.5s) at level 0, faster with level
				int interval = 50 >> buff.getAmplifier();
				if (interval > 0 && buff.getDuration() % interval == 0) {
					heal(1);
				}
			} else if (buff.getType() == mc.sayda.mcraze.entity.buff.BuffType.SPEED) {
				this.speedMultiplier += 0.2f * (buff.getAmplifier() + 1);
			} else if (buff.getType() == mc.sayda.mcraze.entity.buff.BuffType.SLOWNESS) {
				this.speedMultiplier -= 0.15f * (buff.getAmplifier() + 1);
			} else if (buff.getType() == mc.sayda.mcraze.entity.buff.BuffType.WELL_FED) {
				this.speedMultiplier += 0.10f; // +10% Speed from Well Fed
			}

			if (buff.isExpired()) {
				it.remove();
			}
		}

		// Clamp minimum speed
		if (this.speedMultiplier < 0.1f)
			this.speedMultiplier = 0.1f;
	}

	public LivingEntity(boolean gravityApplies, float x, float y, int width, int height) {
		super(null, gravityApplies, x, y, width, height);
		this.maxHP = 100;
		this.hitPoints = maxHP;
		inventory = new Inventory(10, 4, 3);
	}

	/**
	 * Give items to this entity's inventory.
	 * 
	 * @param item  Item to give
	 * @param count Number of items
	 * @return Number of items that couldn't fit (0 if all added successfully)
	 */
	public int giveItem(Item item, int count) {

		return inventory.addItem(item, count);
	}

	public static final int MAX_AIR_TICKS = 300; // 15 seconds at 20 ticks/sec

	public int airRemaining() {
		return Math.max(MAX_AIR_TICKS - ticksUnderwater, 0);
	}

	public int getMaxHP() {
		return maxHP;
	}

	/**
	 * Get base jump velocity (negative for up)
	 * Overriden by subclasses (e.g. Zombie) to use custom values.
	 */
	protected float getBaseJumpVelocity() {
		return -0.3f;
	}

	public void jump(World world, int tileSize) {
		if (dead || jumping) {
			return;
		}

		if (!this.isInWater(world, tileSize)) {
			dy = getBaseJumpVelocity() * jumpMultiplier;
			jumping = true;
		} else {
			dy = -maxWaterDY - .000001f;// BIG HACK
		}
	}

	/**
	 * AI/Logic tick with access to server context (e.g. other entities/players)
	 * Called before updatePosition
	 */
	public void tick(mc.sayda.mcraze.server.SharedWorld sharedWorld) {
		tickBuffs();

		// Death sound broadcast
		if (dead && !lastDead) {
			sharedWorld.broadcastPacket(
					new mc.sayda.mcraze.network.packet.PacketPlaySound("death.wav", x, y, 1.0f, 16.0f));
			lastDead = true;
		}
	}

	@Override
	public void updatePosition(World world, int tileSize) {
		ticksAlive++;

		// Decrement timers
		if (invulnerabilityTicks > 0)
			invulnerabilityTicks--;
		if (damageFlashTicks > 0)
			damageFlashTicks--;

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
				dy = -walkSpeed * speedMultiplier * 2; // Upward movement
			} else if (sneaking) {
				dy = walkSpeed * speedMultiplier * 2; // Downward movement when sneaking
			} else {
				dy = 0; // No gravity when flying
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
				int checkY = (int) (nextBottom + 0.1f); // Slightly below feet
				boolean hasGroundLeft = !world.passable((int) nextLeft, checkY);
				boolean hasGroundRight = !world.passable((int) nextRight, checkY);

				// If moving would cause player to walk off edge, prevent it
				if (!hasGroundLeft && !hasGroundRight) {
					dx = 0; // Cancel horizontal movement
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

		// MCraze coordinate system: head is at player origin (top-left)
		if (this.isHeadUnderWater(world, tileSize)) {
			ticksUnderwater++;
			if (this.airRemaining() == 0) {
				this.takeDamage(5, DamageType.TRUE_DAMAGE);
				// back to 20% air (60 ticks grace period)
				ticksUnderwater = MAX_AIR_TICKS - 60;
			}
		} else {
			ticksUnderwater = 0;
		}
	}

	public void startLeft(boolean slow) {
		if (dead)
			return; // Prevent movement when dead
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
		if (dead)
			return; // Prevent movement when dead
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
		if (dead)
			return; // Prevent climbing when dead
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

	public void takeDamage(int amount) {
		takeDamage(amount, DamageType.PHYSICAL);
	}

	public void takeDamage(int amount, DamageType type) {
		takeDamage(amount, type, null);
	}

	public void takeDamage(int amount, DamageType type, mc.sayda.mcraze.entity.Entity attacker) {
		if (attacker != null) {
			this.lastAttacker = attacker;
		}

		// Respect invulnerability frames (cooldown)
		if (invulnerabilityTicks > 0) {
			// Debug: Invulnerability active
			return;
		}

		// Damage applied
		// Damage Calculation
		int finalDamage = amount;

		if (type == DamageType.PHYSICAL) {
			// Basic armor reduction
			float reduction = Math.min(getArmorValue() * 0.04f, 0.80f); // Cap at 80%?

			int resistLevel = getBuffAmplifier(mc.sayda.mcraze.entity.buff.BuffType.DAMAGE_RESISTANCE);
			if (resistLevel >= 0) {
				// Each level provides 20% resistance
				reduction += (resistLevel + 1) * 0.20f;
			}
			reduction = Math.min(reduction, 0.80f); // Cap at 80%

			finalDamage = (int) (amount * (1.0f - reduction));
		} else if (type == DamageType.MAGICAL) {
			float reduction = Math.min(getMagicResistanceValue() * 0.01f, 0.80f);

			int resistLevel = getBuffAmplifier(mc.sayda.mcraze.entity.buff.BuffType.DAMAGE_RESISTANCE);
			if (resistLevel >= 0) {
				reduction += (resistLevel + 1) * 0.20f;
			}
			reduction = Math.min(reduction, 0.80f);

			finalDamage = (int) (amount * (1.0f - reduction));
		}
		// TRUE_DAMAGE ignores everything

		this.hitPoints -= finalDamage;

		// Trigger red flash and invulnerability cooldown
		if (amount > 0) {
			this.damageFlashTicks = 10;
			int iFrameDuration = 10;
			if (hasBuff(mc.sayda.mcraze.entity.buff.BuffType.STEADFAST)) {
				iFrameDuration = 15; // +50% duration (0.75s) for Steadfast
			}
			this.invulnerabilityTicks = iFrameDuration; // 0.5s cooldown at 20tps (standard)
		}

		// Clamp health to 0 minimum (don't go below 0)
		if (this.hitPoints < 0) {
			this.hitPoints = 0;
		}

		// Health updated

		// Trigger death immediately when health reaches exactly 0
		if (this.hitPoints == 0 && !dead) {
			// Entity dying
			dead = true; // Mark as dead before calling onDeath
			onDeath();
		}
	}

	/**
	 * Check if this entity is dead (health <= 0)
	 * 
	 * @return true if dead
	 */
	public boolean isDead() {
		return hitPoints <= 0;
	}

	/**
	 * Called when the entity dies. Subclasses can override for specific death
	 * behavior.
	 */
	protected void onDeath() {
		// PERFORMANCE: Commented out console logging
		// System.out.println(getClass().getSimpleName() + " has died!");
	}

	public int getArmorValue() {
		return armor;
	}

	public int getMagicResistanceValue() {
		return magicResistance;
	}

	public void heal(int amount) {
		if (amount <= 0 || dead) {
			return;
		}

		// Clamp healing to avoid overflow
		int healAmount = Math.min(amount, getMaxHP() - this.hitPoints);
		if (healAmount <= 0) {
			return;
		}
		this.hitPoints += healAmount;
	}

	/**
	 * Apply impulse force (knockback) to this entity
	 * 
	 * @param sourceX X source of force (usually attacker x)
	 * @param force   Force magnitude (usually 0.4f)
	 */
	public void applyKnockback(float sourceX, float force) {
		if (this.hasBuff(mc.sayda.mcraze.entity.buff.BuffType.STEADFAST)) {
			force *= 0.5f; // 50% Resistance
		}

		// Side-scroller Knockback: Push horizontally away, slight lift
		float direction = (this.x < sourceX) ? -1.0f : 1.0f;

		this.dx = direction * force;
		this.dy = -0.2f; // Standard hop to break friction
	}
}
