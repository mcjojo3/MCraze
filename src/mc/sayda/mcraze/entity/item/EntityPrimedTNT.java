package mc.sayda.mcraze.entity.item;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.packet.PacketPlaySound;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.graphics.GraphicsHandler;
import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.util.StockMethods;
import java.io.IOException;

/**
 * Primed TNT entity that explodes after a fuse.
 * Spawns when a TNT block is broken.
 */
public class EntityPrimedTNT extends Entity {
    private static final long serialVersionUID = 1L;

    private int fuseTimer = 0;
    private static final int FUSE_TIME = 100; // 5.0 seconds fuse
    private final GameLogger logger = GameLogger.get();

    public EntityPrimedTNT(float x, float y) {
        super("assets/sprites/tiles/tnt.png", true, x, y, Constants.TILE_SIZE, Constants.TILE_SIZE);
        this.dx = 0;
        this.dy = 0;
    }

    @Override
    public void tick(SharedWorld sharedWorld) {
        if (dead)
            return;

        super.tick(sharedWorld); // Increment ticksAlive

        // Gravity
        // passable() returns true if we can move through it (Air/Water)
        // Check tile below center
        if (sharedWorld.getWorld().passable((int) (x + widthPX / Constants.TILE_SIZE / 2),
                (int) (y + heightPX / Constants.TILE_SIZE))) {
            dy += Constants.PHYSICS_GRAVITY;
        } else {
            dy = 0;
        }

        // Apply movement
        updatePosition(sharedWorld.getWorld(), Constants.TILE_SIZE);

        // Friction
        dx *= 0.9f;

        fuseTimer++;
        if (fuseTimer >= FUSE_TIME) {
            explode(sharedWorld);
        }
    }

    private void explode(SharedWorld sharedWorld) {
        this.dead = true;
        World world = sharedWorld.getWorld();

        // Explosion params
        float explosionRadius = 4.0f; // Slightly larger than Bomber
        int damage = 50; // High damage

        // Visuals
        sharedWorld.broadcastPacket(new mc.sayda.mcraze.network.packet.PacketExplosion(x, y, explosionRadius));
        sharedWorld.broadcastPacket(new PacketPlaySound("explode.wav", x, y, 1.5f, 32.0f));

        // Deal Damage to Entities
        for (Entity e : sharedWorld.getEntityManager().getAll()) {
            if (e instanceof LivingEntity) {
                float distSq = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
                if (distSq < explosionRadius * explosionRadius) {
                    ((LivingEntity) e).takeDamage(damage, mc.sayda.mcraze.entity.DamageType.PHYSICAL);

                    // Knockback
                    float dx = e.x - x;
                    float dy = e.y - y;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 0.01f) {
                        e.dx += (dx / len) * 2.0f;
                        e.dy += (dy / len) * 2.0f;
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
                            // Using mobBreakBlock from SharedWorld which handles drops etc.
                            sharedWorld.mobBreakBlock(i, j, this);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void draw(GraphicsHandler graphics, float cameraX, float cameraY, int screenWidth, int screenHeight,
            int tileSize) {
        mc.sayda.mcraze.util.Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth,
                screenHeight, tileSize, x, y);

        if (StockMethods.onScreen) {
            if (sprite == null)
                return;

            Color tint;
            // Flash White/Orange
            if ((fuseTimer % 4) < 2) {
                tint = new Color(255, 255, 255, 180); // White Flash
            } else {
                tint = new Color(255, 255, 255, 255); // Normal
            }

            float scale = (float) tileSize / Constants.TILE_SIZE;
            int drawW = (int) (widthPX * scale);
            int drawH = (int) (heightPX * scale);

            graphics.drawImage(sprite, pos.x, pos.y, drawW, drawH, tint);
        }
    }

    public int getFuseTimer() {
        return fuseTimer;
    }

    public void setFuseTimer(int fuseTimer) {
        this.fuseTimer = fuseTimer;
    }
}
