package mc.sayda.mcraze.entity.mob;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.logging.GameLogger;

import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.network.packet.PacketPlaySound;

import mc.sayda.mcraze.server.PlayerConnection;

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

    private final GameLogger logger = GameLogger.get();
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
            // Trigger 1: Stuck Detection (Wall Breach)
            // Relaxed check: include slow movement while wanting to move
            boolean effectivelyStuck = (Math.abs(dx) < 0.05f && Math.abs(moveDirection) > 0);

            // Update Stuck Timer
            if (effectivelyStuck
                    && (currentAction == ACTION_ATTACK_FLAG || (currentAction == ACTION_CHASE && target != null))) {
                stuckTimer++;
            } else {
                stuckTimer = 0;
            }

            // Trigger 2: Reached target (very close)
            boolean reachedTarget = false;

            // 1. Check distance to all players
            for (PlayerConnection pc : sharedWorld.getPlayers()) {
                Player p = pc.getPlayer();
                if (p != null && !p.dead && !p.godmode && !p.debugMode) {
                    float distanceX = Math.abs(p.x - x);
                    float distanceY = Math.abs(p.y - y);
                    // Strict X alignment (share 0.6 corridor), lenient Y (jumping/dropping)
                    if (distanceX < 0.6f && distanceY < 3.5f) {
                        reachedTarget = true;
                        break;
                    }
                }
            }

            // 2. Check distance to Kingdom Flag
            if (!reachedTarget) {
                mc.sayda.mcraze.util.Int2 flagLoc = sharedWorld.getFlagLocation();
                if (flagLoc != null) {
                    float distanceX = Math.abs(flagLoc.x - x);
                    float distanceY = Math.abs(flagLoc.y - y);
                    // Same strict corridor check for the flag
                    if (distanceX < 0.6f && distanceY < 3.5f) {
                        reachedTarget = true;
                    }
                }
            }

            // Trigger 3: Wall Breach or Overcrowding
            boolean overCrowded = countNearbyZombies(sharedWorld, 2.0f) >= 8;
            boolean breachWall = (stuckTimer > 40);

            if (reachedTarget || breachWall || overCrowded) {
                if (overCrowded && logger != null) {
                    logger.info("Bomber overcrowding trigger activated at " + (int) x + "," + (int) y);
                } else if (breachWall && logger != null) {
                    logger.info("Bomber wall breach triggered at " + (int) x + "," + (int) y);
                }
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
        sharedWorld.broadcastPacket(new PacketPlaySound("fuse.wav", x, y, 1.0f, 20.0f));
    }

    private void explode(SharedWorld sharedWorld) {
        this.dead = true;
        World world = sharedWorld.getWorld();

        // Explosion params
        float explosionRadius = 3.5f;
        int damage = 30;

        // Visuals
        sharedWorld.broadcastPacket(new mc.sayda.mcraze.network.packet.PacketExplosion(x, y, explosionRadius));
        sharedWorld.broadcastPacket(new PacketPlaySound("explode.wav", x, y, 1.5f, 32.0f));

        // Deal Damage to Entities
        for (Entity e : sharedWorld.getEntityManager().getAll()) {
            if (e instanceof LivingEntity && e != this) {
                float distSq = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
                if (distSq < explosionRadius * explosionRadius) {
                    ((LivingEntity) e).takeDamage(damage, mc.sayda.mcraze.entity.DamageType.PHYSICAL);

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
                            // [NEW] Check for Containers (Chest/Furnace) and drop items
                            mc.sayda.mcraze.world.tile.Tile tile = world.getTile(i, j);
                            if (tile != null && tile.type != null) {
                                if (tile.type.name == Constants.TileID.CHEST) {
                                    // Drop Chest Contents
                                    mc.sayda.mcraze.world.storage.ChestData chest = world.getChest(i, j);
                                    if (chest != null) {
                                        for (int xx = 0; xx < 10; xx++) {
                                            for (int yy = 0; yy < 3; yy++) {
                                                mc.sayda.mcraze.item.InventoryItem item = chest.items[xx][yy];
                                                if (item != null && !item.isEmpty()) {
                                                    mc.sayda.mcraze.item.Item drop = item.getItem().clone();
                                                    drop.x = i + 0.5f;
                                                    drop.y = j + 0.5f;
                                                    drop.dx = (this.random.nextFloat() - 0.5f) * 0.1f;
                                                    drop.dy = -0.1f;
                                                    sharedWorld.addEntity(drop);
                                                }
                                            }
                                        }
                                        // Clear data
                                        for (int xx = 0; xx < 10; xx++) {
                                            for (int yy = 0; yy < 3; yy++) {
                                                chest.items[xx][yy] = new mc.sayda.mcraze.item.InventoryItem(null);
                                            }
                                        }
                                    }
                                } else if (tile.type.name == Constants.TileID.FURNACE
                                        || tile.type.name == Constants.TileID.FURNACE_LIT) {
                                    // Drop Furnace Contents
                                    mc.sayda.mcraze.world.storage.FurnaceData furnace = world.getFurnace(i, j);
                                    if (furnace != null) {
                                        for (int xx = 0; xx < 3; xx++) {
                                            for (int yy = 0; yy < 2; yy++) {
                                                mc.sayda.mcraze.item.InventoryItem item = furnace.items[xx][yy];
                                                if (item != null && !item.isEmpty()) {
                                                    mc.sayda.mcraze.item.Item drop = item.getItem().clone();
                                                    drop.x = i + 0.5f;
                                                    drop.y = j + 0.5f;
                                                    drop.dx = (this.random.nextFloat() - 0.5f) * 0.1f;
                                                    drop.dy = -0.1f;
                                                    sharedWorld.addEntity(drop);
                                                }
                                            }
                                        }
                                        // Clear data
                                        for (int xx = 0; xx < 3; xx++) {
                                            for (int yy = 0; yy < 2; yy++) {
                                                furnace.items[xx][yy] = new mc.sayda.mcraze.item.InventoryItem(null);
                                            }
                                        }
                                    }
                                }
                            }

                            // Normal breaking logic (drops block item + clears tile)
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

    public boolean isExploding() {
        return isExploding;
    }

    public int getFuseTimer() {
        return fuseTimer;
    }

    public void setExploding(boolean exploding) {
        this.isExploding = exploding;
    }

    public void setFuseTimer(int fuseTimer) {
        this.fuseTimer = fuseTimer;
    }
}
