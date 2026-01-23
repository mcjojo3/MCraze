package mc.sayda.mcraze.entity.projectile;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.graphics.GraphicsHandler;

import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.util.StockMethods;

import java.util.List;

public class EntityBoulder extends LivingEntity {
    private static final long serialVersionUID = 1L;

    // Physics
    private float rollSpeed = 0.2f;
    private int lifeTimeTicks = 0;
    private static final int MAX_LIFETIME = 200; // 10 seconds approx

    // Damage
    private static final int IMPACT_DAMAGE = 50; // "A LOT" of damage

    // Rotation for visuals
    private float rotationAngle = 0;

    // Ownership for damage attribution
    public Entity owner;

    public EntityBoulder(float x, float y, boolean movingRight) {
        super(true, x, y, 32, 32); // Use LivingEntity constructor
        // Critical Fix: Set moveDirection because LivingEntity.updatePosition
        // overwrites dx based on it
        this.moveDirection = movingRight ? 1.0f : -1.0f;
        this.speedMultiplier = rollSpeed / this.walkSpeed; // 0.2f / 0.1f = 2.0f

        this.dx = movingRight ? rollSpeed : -rollSpeed;
        this.facingRight = movingRight;

        // Try to load sprite, safe fallback
        try {
            this.sprite = SpriteStore.get().getSprite("assets/sprites/entities/boulder.png");
        } catch (Exception e) {
        }
    }

    @Override
    public void tick(SharedWorld sharedWorld) {
        super.tick(sharedWorld);

        lifeTimeTicks++;
        if (lifeTimeTicks > MAX_LIFETIME) {
            this.dead = true;
            return;
        }

        // Check collisions with entities
        List<Entity> entities = sharedWorld.getAllEntities();

        for (Entity e : entities) {
            if (e != this && !e.dead && e instanceof LivingEntity) {
                if (e.collidesWith(this, 32)) { // 32 is standard tile size
                    // Deal damage
                    ((LivingEntity) e).takeDamage(IMPACT_DAMAGE, mc.sayda.mcraze.entity.DamageType.PHYSICAL);
                }
            }
        }

        // Stop if hits wall horizontal
        // dx is updated in updatePosition logic. If dx becomes 0 it means wall hit.
        if (Math.abs(dx) < 0.01f && onGround()) {
            this.dead = true; // Break on wall
        }

        // Visual Rotation
        rotationAngle += dx * 20;
    }

    private boolean onGround() {
        return Math.abs(dy) < 0.01f;
    }

    @Override
    public void draw(GraphicsHandler g, float cameraX, float cameraY, int screenWidth, int screenHeight, int tileSize) {
        Int2 pos = StockMethods.computeDrawLocationInPlace(cameraX, cameraY, screenWidth, screenHeight, tileSize, x, y);
        if (StockMethods.onScreen && sprite != null) {
            // Rotation would require Graphics2D/affine transform which basic
            // GraphicsHandler might not expose easily
            // For now just draw projectile
            sprite.draw(g, pos.x, pos.y, widthPX, heightPX);
        }
    }
}
