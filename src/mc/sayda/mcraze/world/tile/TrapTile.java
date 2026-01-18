package mc.sayda.mcraze.world.tile;

/**
 * A special tile that remembers who placed it.
 * Used for Engineer traps to prevent unauthorized pickup/triggering.
 */
public class TrapTile extends Tile {
    private static final long serialVersionUID = 1L;

    public String ownerUUID;

    public TrapTile(TileType type, String ownerUUID) {
        super(type);
        this.ownerUUID = ownerUUID;
    }

    public boolean isOwner(String uuid) {
        return ownerUUID != null && ownerUUID.equals(uuid);
    }
}
