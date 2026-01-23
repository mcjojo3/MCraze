package mc.sayda.mcraze.entity.projectile;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.server.SharedWorld;
import java.util.List;

public abstract class EntityProjectile extends Entity {

    protected Entity owner;
    public float damage;
    protected boolean active = true;
    protected int lifeTime = 0;
    protected int maxLifeTime = 600; // 30 seconds default

    public EntityProjectile(float x, float y, float width, float height, Entity owner) {
        // Pass null for sprite ref (not loaded in constructor), gravity true
        super(null, true, x, y, (int) width, (int) height);
        this.owner = owner;
        this.widthPX = (int) width;
        this.heightPX = (int) height;
    }

    // Use standard tick signature if Entity doesn't have one, but we likely call
    // this manually or from subclass
    // Removing @Override since Entity doesn't define tick(SharedWorld)
    public void tick(SharedWorld sharedWorld) {
        if (dead)
            return;
        lifeTime++;
        if (lifeTime > maxLifeTime) {
            this.dead = true;
            return;
        }

        // Projectile specific movement
        // Update position using Entity's physics
        float prevX = x;

        updatePosition(sharedWorld.getWorld(), mc.sayda.mcraze.Constants.TILE_SIZE);

        // Check Entity Collision
        checkEntityCollision(sharedWorld);

        // Check active collision with world
        // Calculate if we hit a wall (x movement stopped despite dx > 0)
        boolean movedX = Math.abs(x - prevX) > 0.0001f;
        boolean hitWall = Math.abs(dx) > 0.001f && !movedX; // If we had velocity but didn't move

        if (onGround() || hitWall) {
            onImpact(sharedWorld);
        }
    }

    public boolean onGround() {
        return Math.abs(dy) < 0.001f;
    }

    protected void checkEntityCollision(SharedWorld sharedWorld) {
        if (!active)
            return;

        List<Entity> entities = sharedWorld.getEntityManager().getAll();
        for (Entity e : entities) {
            if (e != this && e != owner && !e.dead) {
                if (e instanceof LivingEntity) {
                    if (this.intersects(e)) {
                        onHitEntity(sharedWorld, (LivingEntity) e);
                    }
                }
            }
        }
    }

    // Check if bounding boxes overlap
    public boolean intersects(Entity other) {
        // Simple AABB
        return (x < other.x + (float) other.widthPX / 32.0f && x + (float) widthPX / 32.0f > other.x &&
                y < other.y + (float) other.heightPX / 32.0f && y + (float) heightPX / 32.0f > other.y);
    }

    protected abstract void onHitEntity(SharedWorld world, LivingEntity entity);

    protected abstract void onImpact(SharedWorld world);
}
