package mc.sayda.mcraze.entity.projectile;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.Constants;

public class EntityArrow extends EntityProjectile {

    public enum ArrowType {
        STONE(5, "assets/sprites/entities/arrow_stone.png", "arrow_stone"),
        IRON(8, "assets/sprites/entities/arrow_iron.png", "arrow_iron"),
        GOLD(7, "assets/sprites/entities/arrow_gold.png", "arrow_gold"),
        DIAMOND(12, "assets/sprites/entities/arrow_diamond.png", "arrow_diamond");

        public int damage;
        public String spritePath;
        public String itemId;

        ArrowType(int damage, String path, String itemId) {
            this.damage = damage;
            this.spritePath = path;
            this.itemId = itemId;
        }
    }

    private ArrowType type;

    public ArrowType getType() {
        return type;
    }

    public EntityArrow(float x, float y, Entity owner, ArrowType type) {
        super(x, y, 16, 5, owner); // Small hitbox (16x5 px?)
        this.type = type;
        this.damage = type.damage;

        try {
            this.sprite = SpriteStore.get().getSprite(type.spritePath);
        } catch (Exception e) {
            // Handle missing sprite
        }
    }

    @Override
    protected void onHitEntity(SharedWorld world, LivingEntity entity) {
        if (dead)
            return;
        // Apply Class Bonuses if owner is a player
        int finalDamage = (int) damage;
        if (owner instanceof mc.sayda.mcraze.player.Player) {
            mc.sayda.mcraze.player.Player p = (mc.sayda.mcraze.player.Player) owner;
            float damageMulti = 1.0f;

            // Apply Class Ranged Damage Multipliers
            if (p.classStats != null) {
                damageMulti *= p.classStats.getRangedDamageMultiplier();
            }

            // Passive: Arcane Infusion (Elementalist) - +50% Magic Damage on attacks
            if (p.unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.ARCANE_INFUSION)) {
                damageMulti += 0.50f;
                finalDamage = (int) (damage * damageMulti); // Calculate final damage with bonus
                // Arcane Infusion converts damage to MAGICAL
                entity.lastAttacker = owner;
                entity.takeDamage(finalDamage, mc.sayda.mcraze.entity.DamageType.MAGICAL);
                this.dead = true;
                world.broadcastSound("hit.wav", x, y);
                return;
            }

            // Passive: Headshot Master (Marksman) - Landing 3 consecutive hits grants Focus
            // buff (+50% damage) for 10s
            if (p.unlockedPassives.contains(mc.sayda.mcraze.player.specialization.PassiveEffectType.HEADSHOT_MASTER)) {
                p.consecutiveBowHits++;
                if (p.consecutiveBowHits >= 3) {
                    p.addBuff(new mc.sayda.mcraze.entity.buff.Buff(mc.sayda.mcraze.entity.buff.BuffType.FOCUS, 200, 0)); // 10s
                    p.consecutiveBowHits = 0; // Reset
                    world.broadcastPacket(new mc.sayda.mcraze.network.packet.PacketChatMessage(
                            "Headshot Master! Focus Active!", mc.sayda.mcraze.graphics.Color.orange));
                }
            } else {
                p.consecutiveBowHits = 0; // Reset if skill not unlocked (cleanup)
            }

            // Passive: Focus Buff (+50% Damage)
            if (p.hasBuff(mc.sayda.mcraze.entity.buff.BuffType.FOCUS)) {
                damageMulti += 0.50f;
            }

            finalDamage = (int) (damage * damageMulti);
        }

        entity.lastAttacker = owner;
        entity.takeDamage(finalDamage, mc.sayda.mcraze.entity.DamageType.PHYSICAL);
        this.dead = true; // Destroy arrow on hit
        world.broadcastSound("hit.wav", x, y);

        // Apply knockback (Arrow imparts force based on its direction or owner
        // position)
        if (owner != null) {
            entity.applyKnockback(owner.x, 0.2f); // Less force than melee
        } else {
            // Fallback: use arrow position if unknown owner
            entity.applyKnockback(x, 0.2f);
        }
    }

    protected boolean stuck = false;
    protected int stuckTime = 0;

    @Override
    public void tick(SharedWorld sharedWorld) {
        if (stuck) {
            dx = 0;
            dy = 0;
            stuckTime++;
            if (stuckTime >= 6000) { // Despawn after 5 minutes
                dead = true;
            }

            // Check for pickup by players
            java.util.List<Entity> entities = sharedWorld.getEntityManager().getAll();
            for (Entity e : entities) {
                if (e instanceof mc.sayda.mcraze.player.Player && !e.dead) { // Only living players can pick up
                    if (this.intersects(e)) {
                        mc.sayda.mcraze.player.Player p = (mc.sayda.mcraze.player.Player) e;
                        mc.sayda.mcraze.item.Item item = mc.sayda.mcraze.Constants.itemTypes.get(type.itemId);
                        if (item != null) {
                            p.inventory.addItem(item.clone(), 1); // Add 1 arrow
                            sharedWorld.broadcastSound("assets/sounds/random/pop.wav", x, y); // Pop sound
                            dead = true;
                            // Update inventory UI for the player
                            for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
                                if (pc.getPlayer() == p) {
                                    sharedWorld.sendInventoryUpdate(pc);
                                    break;
                                }
                            }
                            return;
                        }
                    }
                }
            }
            return;
        }

        super.tick(sharedWorld);
    }

    @Override
    protected void onImpact(SharedWorld world) {
        // Stop calling super to avoid default behavior if any
        // Instead of dying, become stuck

        // Trap Activation logic (keep existing)
        float checkX = x + dx * 1.5f;
        float checkY = y + dy * 1.5f;
        int tileX = (int) checkX;
        int tileY = (int) checkY;

        mc.sayda.mcraze.world.tile.Tile tile = world.getWorldAccess().getTile(tileX, tileY);
        boolean hitTrap = false;

        if (tile != null && tile.type != null && tile.type.name == Constants.TileID.BOULDER_TRAP) {
            world.getWorldAccess().updateWithLock(w -> {
                w.removeTile(tileX, tileY);
            });
            world.broadcastBlockChange(tileX, tileY, null);

            // Spawn Boulder
            boolean rollRight = this.dx > 0;
            if (this.owner != null) {
                rollRight = this.owner.x < tileX + 0.5f;
            }

            mc.sayda.mcraze.entity.projectile.EntityBoulder boulder = new mc.sayda.mcraze.entity.projectile.EntityBoulder(
                    tileX, tileY, rollRight);
            world.addEntity(boulder);
            hitTrap = true;
        }

        if (hitTrap) {
            this.dead = true; // Destroy arrow if it triggers a trap
        } else {
            this.stuck = true; // Stick in the wall
            this.dx = 0;
            this.dy = 0;
            // Play stuck sound
            world.broadcastSound("hit.wav", x, y);

            // Reset consecutive hits on miss (stuck in wall)
            if (owner instanceof mc.sayda.mcraze.player.Player) {
                ((mc.sayda.mcraze.player.Player) owner).consecutiveBowHits = 0;
            }
        }
    }

    @Override
    public void draw(mc.sayda.mcraze.graphics.GraphicsHandler graphics, float cameraX, float cameraY, int screenWidth,
            int screenHeight, int tileSize) {

        // Rotation logic based on dx/dy?
        // For now, standard draw
        super.draw(graphics, cameraX, cameraY, screenWidth, screenHeight, tileSize);
    }
}
