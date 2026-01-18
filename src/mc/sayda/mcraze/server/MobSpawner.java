package mc.sayda.mcraze.server;

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.world.gen.Biome;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.util.Int2;
import mc.sayda.mcraze.server.PlayerConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MobSpawner {
    private final SharedWorld sharedWorld;
    private final World world;
    private final Random random = new Random();
    private final List<SpawnRule> spawnRules = new ArrayList<>();

    // Despawn distance (squared) - 128 tiles
    private static final float DESPAWN_DIST = 128.0f;
    private static final float DESPAWN_DIST_SQ = DESPAWN_DIST * DESPAWN_DIST;

    // Spawn range
    private static final int MIN_SPAWN_DIST = 24;
    private static final int MAX_SPAWN_DIST = 64;

    public MobSpawner(SharedWorld sharedWorld) {
        this.sharedWorld = sharedWorld;
        this.world = sharedWorld.getWorld();
    }

    public void addRule(SpawnRule rule) {
        spawnRules.add(rule);
    }

    /**
     * Attempt to spawn mobs near players
     */
    public void tick() {
        // RATE LIMIT: Only try spawning once every 60 ticks (3 seconds at 20 TPS)
        if (random.nextInt(60) != 0)
            return;

        if (spawnRules.isEmpty())
            return;

        List<PlayerConnection> players = sharedWorld.getPlayers();
        if (players.isEmpty())
            return;

        // Try to spawn for a random player
        PlayerConnection pc = players.get(random.nextInt(players.size()));
        Player player = pc.getPlayer();
        if (player == null)
            return;

        // Pick a random X distance (Left or Right)
        int dist = MIN_SPAWN_DIST + random.nextInt(MAX_SPAWN_DIST - MIN_SPAWN_DIST);
        if (random.nextBoolean())
            dist = -dist;

        int spawnX = (int) player.x + dist;

        // Validate X bounds
        if (spawnX < 0 || spawnX >= world.width)
            return;

        // Search the ENTIRE column for valid spots within distance
        List<Integer> validYs = new ArrayList<>();

        // Scan from top to bottom (0 to height-3)
        for (int y = 0; y < world.height - 2; y++) {
            // 1. Check if spot is physically valid (passable head/body, solid ground)
            if (isValidSpawnSpot(spawnX, y)) {
                // 2. Check distance to player
                float dx = spawnX - player.x;
                float dy = y - player.y;
                float distSq = dx * dx + dy * dy;

                // Euclidean distance check
                if (distSq >= MIN_SPAWN_DIST * MIN_SPAWN_DIST && distSq <= MAX_SPAWN_DIST * MAX_SPAWN_DIST) {
                    validYs.add(y);
                }
            }
        }

        if (validYs.isEmpty()) {
            // GameLogger.get().info("MobSpawner: No valid spots in column X=" + spawnX);
            return;
        }

        GameLogger.get().info("MobSpawner: Finding spot around " + player.username + " ... Found " + validYs.size()
                + " candidates at X=" + spawnX);

        // Pick a random valid Y from candidates
        int spawnY = validYs.get(random.nextInt(validYs.size()));

        // Check Biome
        Biome biome = world.getBiome(spawnX);

        // Pick a valid rule for this biome
        SpawnRule rule = pickRule(biome);
        if (rule == null) {
            return;
        }

        // Spawn the group
        int count = rule.groupMin + random.nextInt(rule.groupMax - rule.groupMin + 1);
        for (int i = 0; i < count; i++) {
            // Slight scatter X (keep Y close to ground)
            int currX = spawnX + random.nextInt(6) - 3;
            // For Y, scan small range around spawnY

            boolean spawned = false;
            for (int dy = -3; dy <= 3; dy++) {
                int currY = spawnY + dy;
                if (isValidSpawnSpot(currX, currY, rule.hostile)) {
                    spawnEntity(rule, currX, currY);
                    spawned = true;
                    break;
                }
            }
        }
    }

    /**
     * Check if a position is valid for spawning.
     * Origin is HEAD (x, y).
     * Requirements for 2-block tall mob:
     * - Head (y) is passable.
     * - Body/Feet (y+1) is passable.
     * - Ground (y+2) is solid.
     */
    private boolean isValidSpawnSpot(int x, int y) {
        if (x < 0 || x >= world.width || y < 0 || y >= world.height - 2) // Ensure y+2 is within bounds
            return false;

        // Check Head (y) -> Must be passable AND NOT LIQUID
        if (isSolid(x, y) || world.tiles[x][y].type.liquid)
            return false;

        // Check Body/Feet (y+1) -> Must be passable AND NOT LIQUID
        if (isSolid(x, y + 1) || world.tiles[x][y + 1].type.liquid)
            return false;

        // Check Ground (y+2) -> Must be Solid
        if (!isSolid(x, y + 2))
            return false;

        return true;
    }

    // Overloaded to support hostile check
    private boolean isValidSpawnSpot(int x, int y, boolean hostile) {
        if (!isValidSpawnSpot(x, y)) {
            // GameLogger.get().debug("Spawn spot invalid: " + x + ", " + y);
            return false;
        }

        if (hostile) {
            float light = world.getLightValue(x, y);
            // Verify if this is dark enough.
            // Note: During day, surface is bright. Night surface is dark. Caves are always
            // dark.
            // Tunable constant?
            if (light > 0.4f) {
                GameLogger.get().info("Spawn failed: Too bright for hostile (" + light + ") at " + x + ", " + y);
                return false;
            }
        }
        return true;
    }

    private boolean isSolid(int x, int y) {
        if (x < 0 || x >= world.width || y < 0 || y >= world.height)
            return true;

        // CRITICAL FIX: Use passable property to determine solidity
        // Originally treated AIR as solid because it wasn't explicitly excluded
        // Now checks if the block allows movement (passable)
        // Passable (Air, Water, etc) = Not Solid (False)
        // Not Passable (Dirt, Stone, etc) = Solid (True)
        return !world.tiles[x][y].type.passable;
    }

    private SpawnRule pickRule(Biome biome) {
        List<SpawnRule> candidates = new ArrayList<>();
        int totalWeight = 0;

        for (SpawnRule rule : spawnRules) {
            boolean validBiome = false;
            for (Biome b : rule.biomes) {
                if (b == biome) {
                    validBiome = true;
                    break;
                }
            }
            if (validBiome) {
                candidates.add(rule);
                totalWeight += rule.weight;
            }
        }

        if (candidates.isEmpty())
            return null;

        int pick = random.nextInt(totalWeight);
        int current = 0;
        for (SpawnRule rule : candidates) {
            current += rule.weight;
            if (pick < current)
                return rule;
        }
        return null;
    }

    private void spawnEntity(SpawnRule rule, int x, int y) {
        try {
            // Instantiate entity
            // Try 5-arg constructor first (standard for some entities)
            try {
                Entity entity = rule.entityClass
                        .getConstructor(boolean.class, float.class, float.class, int.class, int.class)
                        .newInstance(true, (float) x, (float) y, rule.width, rule.height);
                sharedWorld.getEntityManager().add(entity);
                GameLogger.get().info("Spawned " + rule.entityClass.getSimpleName() + " at " + x + "," + y);
                return;
            } catch (NoSuchMethodException e) {
                // Ignore, try fallback
            }

            // Fallback: Try 2-arg constructor (float x, float y) for simple entities like
            // Sheep
            try {
                Entity entity = rule.entityClass
                        .getConstructor(float.class, float.class)
                        .newInstance((float) x, (float) y);
                sharedWorld.getEntityManager().add(entity);
                GameLogger.get().info("Spawned " + rule.entityClass.getSimpleName() + " at " + x + "," + y);
                return;
            } catch (NoSuchMethodException e) {
                GameLogger.get()
                        .error("Entity " + rule.entityClass.getSimpleName() + " has no compatible constructor!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Despawn mobs far from players
     */
    public void despawnTick() {
        List<Entity> entities = sharedWorld.getEntityManager().getAll();
        List<PlayerConnection> players = sharedWorld.getPlayers();

        for (Entity e : entities) {
            // Don't despawn players or persistent entities (if any)
            if (e instanceof Player)
                continue;
            if (!(e instanceof LivingEntity))
                continue; // Only despawn mobs

            // Check distance to all players
            boolean tooFar = true;
            for (PlayerConnection pc : players) {
                Player p = pc.getPlayer();
                if (p == null)
                    continue;

                float dx = e.x - p.x;
                float dy = e.y - p.y;
                float distSq = dx * dx + dy * dy;

                if (distSq < DESPAWN_DIST_SQ) {
                    tooFar = false;
                    break;
                }
            }

            if (tooFar) {
                e.dead = true; // Mark for removal
                // Entity manager will clean it up
            }
        }
    }
}
