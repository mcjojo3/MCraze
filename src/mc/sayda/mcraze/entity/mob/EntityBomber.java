package mc.sayda.mcraze.entity.mob;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.logging.GameLogger;

import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.network.packet.PacketPlaySound;

import java.util.List;
import mc.sayda.mcraze.server.PlayerConnection;

import java.util.Random;

public class EntityBomber extends EntityZombie {
    private static final long serialVersionUID = 1L;

    private boolean isExploding = false;
    private int fuseTimer = 0;
    private static final int FUSE_TIME = 100; // 5.0 seconds fuse (longer fuse)
    private static final float BOMBER_SPEED_MULT = 1.1f; // Faster than normal zombies (0.75 * 1.1 = 0.825)

    public EntityBomber(boolean gravityApplies, float x, float y, int width, int height) {
        super(gravityApplies, x, y, width, height);
        this.speedMultiplier = 0.75f * BOMBER_SPEED_MULT;
        this.maxHP = 15; // Very low HP (Glass Cannon - killable before boom)
        this.hitPoints = maxHP;
    }

    private int stuckTimer = 0;

    @Override
    public void tick(SharedWorld sharedWorld) {
        if (dead)
            return;

        if (isExploding) {
            fuseTimer++;
            // Flash Bright Orange rapidly during fuse
            if (fuseTimer % 4 == 0) {
                // Custom color flash if supported, or just toggle red/normal
                // Re-using damageFlashTicks for visual feedback
                this.damageFlashTicks = 2;
                // ideally we'd set a custom flash color here if the engine supports it
            }

            if (fuseTimer >= FUSE_TIME) {
                explode(sharedWorld);
            }
            // Stop moving while exploding
            stopLeft();
            stopRight();

            // Apply gravity but no AI movement
            super.updatePosition(sharedWorld.getWorld(), Constants.TILE_SIZE);
            return;
        }

        super.tick(sharedWorld);

        // Check for explosion triggers
        if (!isExploding) {
            // Trigger 1: Hit a wall (horizontal collision)
            // We check if we tried to move but didn't actually move much horizontally
            boolean hitWall = (Math.abs(dx) < 0.01f && Math.abs(moveDirection) > 0);

            // Update Stuck Timer
            if (hitWall && currentAction == ACTION_ATTACK_FLAG) {
                stuckTimer++;
            } else {
                stuckTimer = 0;
            }

            // Trigger 2: Reached target (very close)
            boolean reachedTarget = false;
            // Access request: We need to know if we are in CHASE mode and close to target.
            // Since target is private in EntityZombie, we rely on collision logic or
            // distance check if we can.
            // Actually, EntityZombie handles attacking. We want to explode INSTEAD of
            // attacking.
            // But we can't easily override just the attack logic without copy-pasting code.
            // Workaround: Check distance to nearest player here.

            // Find nearest player
            List<PlayerConnection> players = sharedWorld.getPlayers();
            Entity nearest = null;
            float minDstSq = Float.MAX_VALUE;

            for (PlayerConnection pc : players) {
                Player p = pc.getPlayer();
                if (p != null) {
                    float dSq = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y);
                    if (dSq < minDstSq) {
                        minDstSq = dSq;
                        nearest = p;
                    }
                }
            }

            if (nearest != null) {
                if (minDstSq < 2.0f * 2.0f) { // Within 2 blocks
                    reachedTarget = true;
                }
            }

            // Trigger 3: Wall Breach (Smart Logic)
            // Conditions:
            // 1. Targeting Flag
            // 2. Physically stuck (hitWall) for > 2 seconds (40 ticks)
            // 3. Surrounded by a "Horde" (at least 2 other zombies nearby blocked by the
            // same wall)
            boolean breachWall = false;

            if (stuckTimer > 40) {
                int nearbyZombies = countNearbyZombies(sharedWorld, 3.0f);
                // Threshold: 3 total (Self + 2 others)
                if (nearbyZombies >= 3) {
                    breachWall = true;
                    GameLogger.get()
                            .info("Bomber initiating breach! (Stuck=" + stuckTimer + ", Horde=" + nearbyZombies + ")");
                }
            }

            if (reachedTarget || breachWall) {
                startExplosion(sharedWorld);
            }
        }
    }

    private int countNearbyZombies(SharedWorld sharedWorld, float radius) {
        int count = 0;
        float rSq = radius * radius;
        for (Entity e : sharedWorld.getEntityManager().getAll()) {
            if (e != this && e instanceof EntityZombie && !e.dead) {
                float dSq = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
                if (dSq < rSq) {
                    count++;
                }
            }
        }
        return count + 1; // Include self
    }

    private void startExplosion(SharedWorld sharedWorld) {
        isExploding = true;
        fuseTimer = 0;
        sharedWorld.broadcastPacket(new PacketPlaySound("fuse.wav")); // Conceptual sound
    }

    private void explode(SharedWorld sharedWorld) {
        this.dead = true;
        World world = sharedWorld.getWorld();

        // Explosion params
        float explosionRadius = 3.5f;
        int damage = 30;

        // Visuals
        // TODO: Spawn explosion particles via packet
        sharedWorld.broadcastPacket(new PacketPlaySound("explode.wav")); // Conceptual sound

        // Deal Damage to Entities
        for (Entity e : sharedWorld.getEntityManager().getAll()) {
            if (e instanceof LivingEntity && e != this) {
                float distSq = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
                if (distSq < explosionRadius * explosionRadius) {
                    ((LivingEntity) e).takeDamage(damage);

                    // Knockback
                    float dx = e.x - x;
                    float dy = e.y - y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0.01f) {
                        e.dx += (dx / len) * 1.5f;
                        e.dy += (dy / len) * 1.5f;
                    }
                }
            }
        }

        // Block Destruction (Respect mobGriefing)
        if (world.mobGriefing) {
            int r = (int) explosionRadius;
            int cx = (int) x;
            int cy = (int) y;

            for (int i = cx - r; i <= cx + r; i++) {
                for (int j = cy - r; j <= cy + r; j++) {
                    float distSq = (i - cx) * (i - cx) + (j - cy) * (j - cy);
                    if (distSq < explosionRadius * explosionRadius) {
                        // Don't break bedrock or unbreakable blocks
                        if (world.isBreakable(i, j)) {
                            // Probabilistic breaking based on distance? Or just clear it.
                            // Simple clear for now.
                            sharedWorld.mobBreakBlock(i, j, this);
                        }
                    }
                }
            }
        }

    }

    @Override
    public void draw(mc.sayda.mcraze.graphics.GraphicsHandler graphics, float cameraX, float cameraY, int screenWidth,
            int screenHeight, int tileSize) {

        mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(cameraX,
                cameraY,
                screenWidth, screenHeight, tileSize, x, y);

        if (mc.sayda.mcraze.util.StockMethods.onScreen) {
            if (sprite == null)
                return;

            mc.sayda.mcraze.graphics.Color tint;

            if (isExploding) {
                // Blink White and Deep Orange/Red
                // Change every 2 ticks
                if ((fuseTimer % 4) < 2) {
                    // White Flash (Bright)
                    tint = new mc.sayda.mcraze.graphics.Color(255, 255, 255, 150);
                } else {
                    // Deep Orange (Danger)
                    tint = new mc.sayda.mcraze.graphics.Color(255, 100, 0, 180);
                }
            } else {
                // Always Orange (Passive state)
                // 255, 165, 0 is Standard Orange. Alpha 100 for a visible but translucent tint.
                tint = new mc.sayda.mcraze.graphics.Color(255, 165, 0, 100);
            }

            // Override with Damage Flash if hurting (optional, but good polish)
            if (damageFlashTicks > 0) {
                tint = new mc.sayda.mcraze.graphics.Color(255, 0, 0, 150);
            }

            // Scale dimensions
            float scale = (float) tileSize / mc.sayda.mcraze.Constants.TILE_SIZE;
            int drawW = (int) (widthPX * scale);
            int drawH = (int) (heightPX * scale);

            if (facingRight) {
                graphics.drawImage(sprite, pos.x, pos.y, drawW, drawH, tint);
            } else {
                graphics.drawImage(sprite, pos.x + drawW, pos.y, -drawW, drawH, tint);
            }
        }
    }
}
