package mc.sayda.mcraze.entity.projectile;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.Constants;

public class EntityArrow extends EntityProjectile {

    public enum ArrowType {
        STONE(5, "assets/sprites/items/arrow_stone.png", "arrow_stone"),
        IRON(8, "assets/sprites/items/arrow_iron.png", "arrow_iron"),
        DIAMOND(12, "assets/sprites/items/arrow_diamond.png", "arrow_diamond");

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
        entity.takeDamage((int) damage);
        this.dead = true; // Destroy arrow on hit
        world.broadcastPacket(new mc.sayda.mcraze.network.packet.PacketPlaySound("hit.wav"));
    }

    @Override
    protected void onImpact(SharedWorld world) {
        this.dead = true;

        // Trap Activation
        // Check standard impact point (slightly ahead of current position)
        float checkX = x + dx * 1.5f; // Look ahead
        float checkY = y + dy * 1.5f;
        int tileX = (int) checkX;
        int tileY = (int) checkY;

        mc.sayda.mcraze.world.tile.Tile tile = world.getWorldAccess().getTile(tileX, tileY);
        if (tile != null && tile.type != null && tile.type.name == Constants.TileID.BOULDER_TRAP) {
            world.getWorldAccess().updateWithLock(w -> {
                w.removeTile(tileX, tileY);
            });
            world.broadcastBlockChange(tileX, tileY, null);

            // Spawn Boulder
            // Roll away from source player (owner) if available, otherwise fallback to
            // arrow direction
            boolean rollRight = this.dx > 0;
            if (this.owner != null) {
                // If owner is to the left (smaller X), roll right (increase X)
                rollRight = this.owner.x < tileX + 0.5f;
            }

            mc.sayda.mcraze.entity.projectile.EntityBoulder boulder = new mc.sayda.mcraze.entity.projectile.EntityBoulder(
                    tileX, tileY, rollRight);
            world.addEntity(boulder);
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
