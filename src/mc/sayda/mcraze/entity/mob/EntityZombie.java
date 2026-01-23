package mc.sayda.mcraze.entity.mob;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.player.Player;

import java.util.Random;

public class EntityZombie extends LivingEntity {
    private static final long serialVersionUID = 1L;
    protected final GameLogger logger = GameLogger.get();

    // AI State
    private int actionTimer = 0;
    private int attackCooldown = 0;
    protected Random random = new Random();

    // Actions
    private static final int ACTION_IDLE = 0;
    private static final int ACTION_WALK_LEFT = 1;
    private static final int ACTION_WALK_RIGHT = 2;
    protected static final int ACTION_CHASE = 3; // Dynamically track player
    protected static final int ACTION_ATTACK_FLAG = 4; // Target and break flag
    protected int currentAction = ACTION_IDLE;
    protected Entity target; // The current AI target (Player or Flag)
    private static final int TARGET_SCAN_INTERVAL = 20; // Scans every 20 ticks (1s)

    // Combat Stats
    private int attackDamage = 5;

    // Sprites
    private transient Sprite sprite_down;
    private transient Sprite sprite_left_foot;
    private transient Sprite sprite_right_foot;
    private transient Sprite currentSprite;

    // Server-side constructor
    public EntityZombie(boolean gravityApplies, float x, float y, int width, int height) {
        super(gravityApplies, x, y, width, height);
        // Dimensions handled by super from constructor args

        // Slightly slower than player (0.75x)
        this.speedMultiplier = 0.75f;

        // Load sprites (Client side only really, server doesn't need them but won't
        // hurt if store is mocked/safe)
        try {
            sprite_down = SpriteStore.get().getSprite("assets/sprites/entities/zombie.png");
            // Use zombie_left.png and zombie_right.png for walking frames
            // (Assuming these match player conventions of leg positions)
            sprite_left_foot = SpriteStore.get().getSprite("assets/sprites/entities/zombie_left.png");
            sprite_right_foot = SpriteStore.get().getSprite("assets/sprites/entities/zombie_right.png");

            currentSprite = sprite_down;
        } catch (Exception e) {
            // Fallback to missing texture if zombie sprite fails
            try {
                sprite_down = SpriteStore.get().getSprite("missing");
                currentSprite = sprite_down;
            } catch (Exception ex) {
                // Even missing failed? Just ignore on server.
            }
        }
    }

    @Override
    public void tick(SharedWorld sharedWorld) {
        super.tick(sharedWorld);

        // Zombie Burn in Sunlight (Horde Mode or normal Survival)
        // Check if it's day time
        if (!sharedWorld.getWorld().isNight()) {
            // Simplified Burn logic:
            if (sharedWorld.getWorld().getGameMode() == mc.sayda.mcraze.world.GameMode.HORDE
                    || sharedWorld.getWorld().getGameMode() == mc.sayda.mcraze.world.GameMode.SURVIVAL) {
                // Burn!
                if (ticksAlive % 100 == 0) { // Every 5 seconds
                    takeDamage(20, mc.sayda.mcraze.entity.DamageType.TRUE_DAMAGE); // Sun burn ignores armor
                    this.damageFlashTicks = 10; // Visual burn
                }
            }
        }

        // AI Logic: Determine State

        // Check for targets
        if (target == null || target.dead || random.nextInt(20) == 0) {
            // Scan for new target
            Entity nearest = null;
            float nearestDistSq = Float.MAX_VALUE;

            for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
                Player p = pc.getPlayer();
                // Check godmode and debugmode
                if (p != null && !p.dead && !p.godmode && !p.debugMode) {
                    float dx = p.x - x;
                    float dy = p.y - y;
                    float distSq = dx * dx + dy * dy;
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = p;
                    }
                }
            }

            // Aggro range 10 tiles = 320 px. (10 * 32)
            if (nearest != null && nearestDistSq < 100) {
                target = nearest;
                currentAction = ACTION_CHASE; // Immediately switch to chase
                actionTimer = 60;
            } else {
                // No player target found - Check for FLAG
                mc.sayda.mcraze.util.Int2 flagLoc = sharedWorld.getFlagLocation();
                if (flagLoc != null) {
                    // Target the flag!
                    currentAction = ACTION_ATTACK_FLAG;
                    actionTimer = 60;
                } else if (currentAction == ACTION_CHASE || currentAction == ACTION_ATTACK_FLAG) {
                    target = null;
                    currentAction = ACTION_IDLE; // Lost target / Flag gone
                }
            }
        }

        // Attack Logic if chasing
        if (attackCooldown > 0)
            attackCooldown--;

        // FLAG ATTACK LOGIC
        if (currentAction == ACTION_ATTACK_FLAG) {
            mc.sayda.mcraze.util.Int2 flagLoc = sharedWorld.getFlagLocation();
            if (flagLoc != null) {
                float distSq = (flagLoc.x - x) * (flagLoc.x - x) + (flagLoc.y - y) * (flagLoc.y - y);
                if (distSq < 1.0f * 1.0f) { // Range 1.0 blocks (reduced from 1.5 to prevent hitting through walls)
                    if (attackCooldown <= 0) {
                        // Break the flag!
                        sharedWorld.mobBreakBlock(flagLoc.x, flagLoc.y, this);
                        attackCooldown = 60; // Cooldown
                        currentAction = ACTION_IDLE; // Mission accomplished
                    }
                }
            } else {
                currentAction = ACTION_IDLE; // Flag is gone
            }
        }

        if (currentAction == ACTION_CHASE && target != null) {
            float distSq = (target.x - x) * (target.x - x) + (target.y - y) * (target.y - y);
            // Attack range: Close melee (approx 1.5 blocks)
            // Coordinates are in TILES, not pixels.
            if (distSq < 1.5f * 1.5f) {
                if (attackCooldown <= 0) {
                    // ATTACK!
                    if (((LivingEntity) target).invulnerabilityTicks <= 0) {
                        ((LivingEntity) target).takeDamage(attackDamage, mc.sayda.mcraze.entity.DamageType.PHYSICAL);

                        // Play hurt sound if target is player
                        if (target instanceof mc.sayda.mcraze.player.Player) {
                            sharedWorld.broadcastPacket(
                                    new mc.sayda.mcraze.network.packet.PacketPlaySound("hurt.wav", target.x, target.y));
                        }
                    }

                    // Knockback removed
                    attackCooldown = 20; // 1 second cooldown
                }
            }
        }

        // [NEW] Stacking & Conversion Logic
        // If 5+ zombies are stacked at the same spot, convert to a bomber.
        if (ticksAlive % 20 == 0 && !(this instanceof EntityBomber)) { // Check once per second
            if (countZombiesInStack(sharedWorld) >= 5) {
                convertToBomber(sharedWorld);
            }
        }
    }

    private int countZombiesInStack(SharedWorld sharedWorld) {
        int count = 0;
        // Check for zombies at almost exactly same position (overlapping)
        float range = 0.5f;
        for (Entity e : sharedWorld.getEntityManager().getAll()) {
            if (e != this && e instanceof EntityZombie && !(e instanceof EntityBomber) && !e.dead) {
                float dx = Math.abs(e.x - x);
                float dy = Math.abs(e.y - y);
                if (dx < range && dy < range) {
                    count++;
                }
            }
        }
        return count + 1; // Include self
    }

    private void convertToBomber(SharedWorld sharedWorld) {
        if (logger != null)
            logger.info("Zombie stack reached limit! Converting to Bomber at " + (int) x + "," + (int) y);
        EntityBomber bomber = new EntityBomber(gravityApplies, x, y, widthPX, heightPX);
        // Copy wave scaling if applicable
        if (sharedWorld.getWaveManager() != null) {
            bomber.applyWaveScaling(sharedWorld.getWaveManager().getCurrentHealthMultiplier(),
                    sharedWorld.getWaveManager().getCurrentDamageMultiplier());
            bomber.setJumpMultiplier(sharedWorld.getWaveManager().getJumpMultiplier());
        }
        sharedWorld.addEntity(bomber);
        this.dead = true; // Remove self
    }

    /**
     * Apply difficulty scaling from WaveManager
     */
    public void applyWaveScaling(float hpMultiplier, float damageMultiplier) {
        // Scale HP
        this.maxHP = (int) (100 * hpMultiplier); // Base HP 100
        this.hitPoints = this.maxHP;

        // Scale Damage
        this.attackDamage = (int) (5 * damageMultiplier); // Base Dmg 5
    }

    // Use centralized base jump from WaveConfig
    @Override
    protected float getBaseJumpVelocity() {
        return mc.sayda.mcraze.survival.WaveConfig.ZOMBIE_BASE_JUMP;
    }

    // Allow external setting of jump multiplier (called by WaveManager/Spawner)
    public void setJumpMultiplier(float jumpMult) {
        this.jumpMultiplier = jumpMult;
    }

    // [NEW] Fall damage control
    private boolean allowFallDamage = false;

    @Override
    public void updatePosition(World world, int tileSize) {
        // [NEW] Pre-check for Spike Traps before super.updatePosition applies fall
        // damage
        // Get tile at center bottom
        float left = x;
        float width = (float) widthPX / tileSize;
        float right = x + width;
        float bottom = y + (float) heightPX / tileSize;

        int tx = (int) ((left + right) / 2.0f);
        int ty = (int) (bottom + 0.01f);

        allowFallDamage = false;
        if (tx >= 0 && tx < world.width && ty >= 0 && ty < world.height) {
            if (world.tiles[tx][ty] != null && world.tiles[tx][ty].type.name == Constants.TileID.SPIKE_TRAP) {
                allowFallDamage = true;
            }
        }

        // AI State Machine (Navigation)
        if (currentAction == ACTION_CHASE && target != null) {
            // Update direction towards target
            float diffX = target.x - x;
            if (diffX < -0.5f) { // buffer to stop jitter
                startLeft(false);
                facingRight = false;
            } else if (diffX > 0.5f) {
                startRight(false);
                facingRight = true;
            } else {
                // Close enough x-wise, stop moving x?
                stopLeft();
                stopRight();
            }

            // Simple Jump: if wall in front or target is above
            if (target.y < y - 1.0f && random.nextInt(20) == 0) { // Target is above (y is inverted, smaller is higher)
                if (onGround()) { // Need safe jump check
                    jump(world, tileSize);
                }
            }

            // [NEW] Climbing logic for Water/Ladders
            if (this.isInWaterOrClimbable(world, tileSize)) {
                if (target.y < y - 0.5f || (Math.abs(dx) < 0.01f && Math.abs(moveDirection) > 0)) {
                    startClimb();
                } else {
                    endClimb();
                }
            } else {
                endClimb();
            }

        } else if (currentAction == ACTION_ATTACK_FLAG) {
            // Move towards Flag
            mc.sayda.mcraze.util.Int2 flagLoc = world.flagLocation;
            if (flagLoc != null) {
                float diffX = flagLoc.x - x;
                if (diffX < -0.5f) {
                    startLeft(false);
                    facingRight = false;
                } else if (diffX > 0.5f) {
                    startRight(false);
                    facingRight = true;
                } else {
                    stopLeft();
                    stopRight();
                }

                // Jump if flag is above
                if (flagLoc.y < y - 1.0f && random.nextInt(20) == 0) {
                    if (onGround())
                        jump(world, tileSize);
                }
            }
        } else {
            // Wander / Idle Logic
            actionTimer--;
            if (actionTimer <= 0) {
                // Pick new random action
                float roll = random.nextFloat();
                if (roll < 0.5f) {
                    currentAction = ACTION_IDLE;
                    actionTimer = 40 + random.nextInt(60);
                    stopLeft();
                    stopRight();
                } else if (roll < 0.75f) {
                    currentAction = ACTION_WALK_LEFT;
                    actionTimer = 30 + random.nextInt(60);
                    startLeft(true); // Walk slow
                } else {
                    currentAction = ACTION_WALK_RIGHT;
                    actionTimer = 30 + random.nextInt(60);
                    startRight(true); // Walk slow
                }
            }

            // Maintain velocity based on action state
            switch (currentAction) {
                case ACTION_IDLE:
                    stopLeft();
                    stopRight();
                    break;
                case ACTION_WALK_LEFT:
                    startLeft(true);
                    break;
                case ACTION_WALK_RIGHT:
                    startRight(true);
                    break;
            }
        }

        // Jump trigger for wandering (obstacles)
        if (currentAction != ACTION_IDLE && random.nextInt(100) == 0) {
            // Random jump
            jump(world, tileSize);
        }

        // IMPORTANT: LivingEntity.updatePosition() uses moveDirection to calculate
        super.updatePosition(world, tileSize);

        // Update Animation State
        boolean isMoving = (Math.abs(dx) > 0.001f);
        if (isMoving) {
            // 4-step cycle
            int walkCycle = (int) (ticksAlive / 8) % 4;
            switch (walkCycle) {
                case 0:
                    currentSprite = sprite_right_foot;
                    break;
                case 1:
                    currentSprite = sprite_down;
                    break;
                case 2:
                    currentSprite = sprite_left_foot;
                    break;
                case 3:
                    currentSprite = sprite_down;
                    break;
            }
        } else {
            currentSprite = sprite_down;
        }

        if (currentSprite != null) {
            this.sprite = currentSprite;
        }
    }

    @Override
    public void takeDamage(int amount, mc.sayda.mcraze.entity.DamageType type) {
        // [NEW] Zombie Fall Damage Exception
        if (type == mc.sayda.mcraze.entity.DamageType.FALL) {
            if (!allowFallDamage) {
                return; // Ignore fall damage unless on a trap
            }
        }
        super.takeDamage(amount, type);
    }

    // Helper helper
    private boolean onGround() {
        return Math.abs(dy) < 0.01f; // Rough check
    }

    @Override
    public void draw(mc.sayda.mcraze.graphics.GraphicsHandler graphics, float cameraX, float cameraY, int screenWidth,
            int screenHeight, int tileSize) {

        // Safety check: if sprite is null, try to reload or use fallback
        if (sprite == null) {
            if (currentSprite != null) {
                sprite = currentSprite;
            } else {
                return; // Don't draw if no sprite
            }
        }

        // Override draw to handle flipping, since Entity.draw just draws 'sprite'
        // We need to flip if !facingRight

        // Calculate positions (similar to Entity.draw)
        mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(cameraX, cameraY,
                screenWidth, screenHeight, tileSize, x, y);

        if (mc.sayda.mcraze.util.StockMethods.onScreen) {
            // Scale dimensions based on zoom
            float scale = (float) tileSize / mc.sayda.mcraze.Constants.TILE_SIZE;
            int drawW = (int) (widthPX * scale);
            int drawH = (int) (heightPX * scale);

            if (damageFlashTicks > 0) {
                if (facingRight) {
                    graphics.drawImage(sprite, pos.x, pos.y, drawW, drawH,
                            new mc.sayda.mcraze.graphics.Color(255, 0, 0, 128));
                } else {
                    graphics.drawImage(sprite, pos.x + drawW, pos.y, -drawW, drawH,
                            new mc.sayda.mcraze.graphics.Color(255, 0, 0, 128));
                }
            } else {
                if (facingRight) {
                    sprite.draw(graphics, pos.x, pos.y, drawW, drawH);
                } else {
                    sprite.draw(graphics, pos.x + drawW, pos.y, -drawW, drawH);
                }
            }
        }
    }
}
