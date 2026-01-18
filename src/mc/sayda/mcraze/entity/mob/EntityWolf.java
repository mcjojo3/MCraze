package mc.sayda.mcraze.entity.mob;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.player.Player;

import java.util.Random;

public class EntityWolf extends BasicAnimal {
    private static final long serialVersionUID = 1L;

    // Ownership
    private String ownerUUID = null; // Null means wild
    private boolean isSitting = false;

    // Rampage Mechanics
    private boolean isRampaging = false;
    private Entity rampageTarget = null;

    // AI State
    // BasicAnimal defines ACTION_IDLE=0, WALK_LEFT=1, WALK_RIGHT=2
    private static final int ACTION_FOLLOW = 3; // Follow owner (if peaceful)
    private static final int ACTION_CHASE = 4; // Chase target (if rampaging)

    private boolean movingLeft = false;
    private boolean movingRight = false;
    private boolean wantsToJump = false; // Set in tick(), executed in udpatePosition

    private transient Sprite sprite_idle;
    private transient Sprite sprite_sit;
    private transient Sprite sprite_angry;

    public EntityWolf(boolean gravityApplies, float x, float y, int width, int height) {
        // Pass dummy maxHP and speed, we will set them specifically
        super(x, y, width, height, "assets/sprites/entities/wolf.png", 20, 0.9f);
        this.speedMultiplier = 0.9f;
        try {
            sprite_idle = SpriteStore.get().getSprite("assets/sprites/entities/wolf.png");
            sprite_sit = SpriteStore.get().getSprite("assets/sprites/entities/wolf_sit.png");
            sprite_angry = SpriteStore.get().getSprite("assets/sprites/entities/wolf_angry.png");
            this.sprite = sprite_idle;
        } catch (Exception e) {
            // Fallback handled in draw
        }
    }

    public void setOwner(String uuid) {
        this.ownerUUID = uuid;
    }

    public String getOwner() {
        return ownerUUID;
    }

    public boolean isTamed() {
        return ownerUUID != null;
    }

    public void setSitting(boolean sitting) {
        this.isSitting = sitting;
    }

    @Override
    public void takeDamage(int amount) {
        super.takeDamage(amount);

        // Trigger Rampage if not already and hurt
        if (this.hitPoints < this.getMaxHP() && !this.dead) {
            if (!isRampaging) {
                isRampaging = true;
                isSitting = false; // Force stand up
            }
        }
    }

    @Override
    public void tick(SharedWorld sharedWorld) {
        super.tick(sharedWorld);

        if (dead)
            return;

        // --- Rampage AI ---
        if (isRampaging) {
            // If target is dead or invalid, find new one
            if (rampageTarget == null || rampageTarget.dead) {
                // If we JUST killed a target (it was valid before this tick), CALM DOWN
                if (rampageTarget != null && rampageTarget.dead) {
                    isRampaging = false; // Calm down
                    rampageTarget = null;
                    return;
                }

                // Otherwise scan for ANY living entity nearby (except self)
                java.util.List<Entity> allEntities = sharedWorld.getAllEntities(); // Public accessor
                Entity nearest = null;
                float nearestDistSq = Float.MAX_VALUE;

                // Scan Players
                for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
                    Player p = pc.getPlayer();
                    if (p != null && !p.dead && !p.godmode) {
                        float d = (p.x - x) * (p.x - x) + (p.y - y) * (p.y - y);
                        if (d < nearestDistSq) {
                            nearest = p;
                            nearestDistSq = d;
                        }
                    }
                }
                // Scan Mobs (Zombies, Sheep, other Wolves)
                for (Entity e : allEntities) {
                    if (e instanceof LivingEntity && e != this && !e.dead) {
                        float d = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
                        if (d < nearestDistSq) {
                            nearest = e;
                            nearestDistSq = d;
                        }
                    }
                }

                if (nearest != null && nearestDistSq < 15 * 15) { // 15 block range
                    rampageTarget = nearest;
                }
            }

            // Chase Logic
            if (rampageTarget != null) {
                currentAction = ACTION_CHASE;
                float diffX = rampageTarget.x - x;

                if (diffX < -0.5f) {
                    movingLeft = true;
                    movingRight = false;
                    facingRight = false;
                } else if (diffX > 0.5f) {
                    movingRight = true;
                    movingLeft = false;
                    facingRight = true;
                } else {
                    movingLeft = false;
                    movingRight = false;
                }

                // Jump flag - will be executed in updatePosition where world is available
                if (rampageTarget.y < y - 1 && onGround() && random.nextInt(10) == 0) {
                    wantsToJump = true;
                }

                // Attack
                float dA = (rampageTarget.x - x) * (rampageTarget.x - x)
                        + (rampageTarget.y - y) * (rampageTarget.y - y);
                if (dA < 2.0f && actionTimer <= 0) { // Attack range
                    // Only attack if target is not invulnerable
                    if (rampageTarget instanceof LivingEntity) {
                        LivingEntity targetLiving = (LivingEntity) rampageTarget;
                        if (targetLiving.invulnerabilityTicks <= 0) {
                            targetLiving.takeDamage(6); // High damage

                            // Play hurt sound (like zombie does)
                            sharedWorld.broadcastPacket(new mc.sayda.mcraze.network.packet.PacketPlaySound("hurt.wav"));
                        }
                    }
                    actionTimer = 20; // Cooldown (using super.actionTimer is safe)
                }
                if (actionTimer > 0)
                    actionTimer--;
            }

        } else {
            // --- Peace AI ---
            // If Tamed: Follow Owner
            if (isTamed() && !isSitting && ownerUUID != null) {
                // Find owner
                Player owner = null;
                for (mc.sayda.mcraze.server.PlayerConnection pc : sharedWorld.getPlayers()) {
                    if (pc.getPlayer() != null && pc.getPlayer().getUUID().equals(ownerUUID)) {
                        owner = pc.getPlayer();
                        break;
                    }
                }

                if (owner != null) {
                    float dist = (owner.x - x) * (owner.x - x) + (owner.y - y) * (owner.y - y);
                    if (dist > 5 * 5) { // Follow if > 5 blocks away
                        currentAction = ACTION_FOLLOW;
                        if (owner.x < x) {
                            movingLeft = true;
                            movingRight = false;
                            facingRight = false;
                        } else {
                            movingRight = true;
                            movingLeft = false;
                            facingRight = true;
                        }

                        if (owner.y < y - 1 && onGround() && random.nextInt(20) == 0)
                            wantsToJump = true;
                    } else {
                        // Close enough, stop following.
                        // We could transition to IDLE, but then BasicAnimal might pick a random wander.
                        // So we stay in FOLLOW state but stop moving? Or switch to IDLE?
                        // If we switch to IDLE, BasicAnimal takes over wandering near owner.
                        // User request: "Untamed not aggressive mode should follow basic animal logic"
                        // Tamed mode logic is not explicitly requested to change, but usually pets
                        // wander near owner.
                        // For now, let's just stop moving but keep customized state to prevent wild
                        // wandering away.
                        currentAction = ACTION_FOLLOW; // Keep following state
                        movingLeft = false;
                        movingRight = false;
                    }
                }
            } else if (!isSitting) {
                // Untamed & Not Sitting -> Use BasicAnimal Logic!
                // We do NOTHING here. super.tick() doesn't do AI.
                // updatePosition() will call skipAI(), which will return FALSE.
                // So BasicAnimal.updateAI() runs.
            } else {
                // Sitting
                currentAction = ACTION_IDLE; // Or custom SIT action
                movingLeft = false;
                movingRight = false;
            }
        }
    }

    @Override
    protected boolean skipAI() {
        // Skip BasicAnimal AI decision if we are doing something special
        return isRampaging || (isTamed() && ownerUUID != null) || isSitting;
    }

    @Override
    protected void customUpdatePosition(World world, int tileSize) {
        // Handle specialized movement application here (Chase/Follow)
        if (currentAction == ACTION_CHASE || currentAction == ACTION_FOLLOW) {
            // Determine if we should use slow walk (idle/wander/follow) or fast (chase)
            boolean slowWalk = (currentAction != ACTION_CHASE);

            // Apply movement
            if (movingLeft) {
                startLeft(slowWalk); // Slow for idle/wander, fast for chase
            } else {
                stopLeft();
            }
            if (movingRight) {
                startRight(slowWalk); // Slow for idle/wander, fast for chase
            } else {
                stopRight();
            }
        }

        // Execute jump if requested from tick() (target-based jump)
        if (wantsToJump) {
            wantsToJump = false;
            jump(world, tileSize);
        }
    }

    // Helper
    private boolean onGround() {
        return Math.abs(dy) < 0.01f;
    }

    @Override
    public void draw(GraphicsHandler g, float cameraX, float cameraY, int screenWidth, int screenHeight, int tileSize) {
        // Use custom draw mostly to handle sprite switching, but BasicAnimal draw logic
        // is good for basic stuff.
        // However, BasicAnimal draw logic doesn't support swapping sprites easily
        // unless we change 'this.sprite'.
        // So we override entirely.

        Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth, screenHeight, tileSize, x, y);
        if (StockMethods.onScreen) {
            Sprite s = sprite; // Default (idle)
            if (isRampaging && sprite_angry != null)
                s = sprite_angry;
            else if (isSitting && sprite_sit != null)
                s = sprite_sit;

            if (s != null) {
                // Apply red tint for damage indication
                if (damageFlashTicks > 0) {
                    if (facingRight) {
                        g.drawImage(s, pos.x, pos.y, widthPX, heightPX, new Color(255, 0, 0, 128));
                    } else {
                        g.drawImage(s, pos.x + widthPX, pos.y, -widthPX, heightPX, new Color(255, 0, 0, 128));
                    }
                } else {
                    if (facingRight) {
                        s.draw(g, pos.x, pos.y, widthPX, heightPX);
                    } else {
                        s.draw(g, pos.x + widthPX, pos.y, -widthPX, heightPX);
                    }
                }

                // Draw Red Eyes if Rampaging
                if (isRampaging) {
                    g.setColor(Color.red);
                    if (facingRight)
                        g.fillRect(pos.x + widthPX - 10, pos.y + 10, 4, 2);
                    else
                        g.fillRect(pos.x + 10, pos.y + 10, 4, 2);
                }
            }
        }
    }
}
