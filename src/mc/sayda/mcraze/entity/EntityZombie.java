package mc.sayda.mcraze.entity;

import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.SpriteStore;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.Constants;

import java.util.Random;

public class EntityZombie extends LivingEntity {
    private static final long serialVersionUID = 1L;

    // AI State
    private int actionTimer = 0;
    private int attackCooldown = 0;
    private Random random = new Random();

    // Actions
    private static final int ACTION_IDLE = 0;
    private static final int ACTION_WALK_LEFT = 1;
    private static final int ACTION_WALK_RIGHT = 2;
    private static final int ACTION_CHASE = 3; // Dynamically track player
    private int currentAction = ACTION_IDLE;

    private Entity target; // Target to chase/attack
    private static final float CHASE_SPEED_MULTIPLIER = 1.2f; // Faster when chasing

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

        // AI Logic: Determine State

        // Check for targets
        if (target == null || target.dead || random.nextInt(20) == 0) {
            // Scan for new target
            Entity nearest = null;
            float nearestDistSq = Float.MAX_VALUE;

            for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
                Player p = pc.getPlayer();
                // Check godmode
                if (p != null && !p.dead && !p.godmode) {
                    float dx = p.x - x;
                    float dy = p.y - y;
                    float distSq = dx * dx + dy * dy;
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = p;
                    }
                }
            }

            // Aggro range 10 tiles = 320 px.
            if (nearest != null && nearestDistSq < 320 * 320) {
                target = nearest;
                currentAction = ACTION_CHASE; // Immediately switch to chase
                actionTimer = 60;
            } else {
                if (currentAction == ACTION_CHASE) {
                    target = null;
                    currentAction = ACTION_IDLE; // Lost target
                }
            }
        }

        // Attack Logic if chasing
        if (attackCooldown > 0)
            attackCooldown--;

        if (currentAction == ACTION_CHASE && target != null) {
            float distSq = (target.x - x) * (target.x - x) + (target.y - y) * (target.y - y);
            // Attack range: Close melee (approx 1.5 blocks)
            // Coordinates are in TILES, not pixels.
            if (distSq < 1.5f * 1.5f) {
                if (attackCooldown <= 0) {
                    // ATTACK!
                    if (((LivingEntity) target).invulnerabilityTicks <= 0) {
                        ((LivingEntity) target).takeDamage(5);

                        // Play hurt sound if target is player
                        if (target instanceof mc.sayda.mcraze.entity.Player) {
                            sharedWorld.broadcastPacket(new mc.sayda.mcraze.network.packet.PacketPlaySound("hurt.wav"));
                        }
                    }

                    // Knockback removed
                    attackCooldown = 20; // 1 second cooldown
                }
            }
        }
    }

    @Override
    public void updatePosition(World world, int tileSize) {
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
            // Or obstacle jump (handled by LivingEntity/World logic usually? implemented
            // manually here)
            // We'll rely on our manual jump check below.

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

    // Helper helper
    private boolean onGround() {
        return Math.abs(dy) < 0.01f; // Rough check
    }

    @Override
    public void draw(mc.sayda.mcraze.GraphicsHandler graphics, float cameraX, float cameraY, int screenWidth,
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
            if (damageFlashTicks > 0) {
                if (facingRight) {
                    graphics.drawImage(sprite, pos.x, pos.y, widthPX, heightPX,
                            new mc.sayda.mcraze.Color(255, 0, 0, 128));
                } else {
                    graphics.drawImage(sprite, pos.x + widthPX, pos.y, -widthPX, heightPX,
                            new mc.sayda.mcraze.Color(255, 0, 0, 128));
                }
            } else {
                if (facingRight) {
                    sprite.draw(graphics, pos.x, pos.y, widthPX, heightPX);
                } else {
                    sprite.draw(graphics, pos.x + widthPX, pos.y, -widthPX, heightPX);
                }
            }
        }
    }
}
