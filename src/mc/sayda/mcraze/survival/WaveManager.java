package mc.sayda.mcraze.survival;

import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.packet.PacketChatMessage;
import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.graphics.Color;

/**
 * Manages the wave survival mechanics, including day/night cycles,
 * difficulty tracking, and wave state.
 */
public class WaveManager {

    private final SharedWorld sharedWorld;
    private boolean isWaveActive = false;
    private int currentDay = 0;

    private final GameLogger logger = GameLogger.get();

    public WaveManager(SharedWorld sharedWorld) {
        this.sharedWorld = sharedWorld;
    }

    /**
     * Called every tick by SharedWorld
     */
    public void tick() {
        mc.sayda.mcraze.world.World world = sharedWorld.getWorld();
        if (world == null)
            return;

        long worldTime = world.getTicksAlive();
        float timeOfDay = world.getTimeOfDay();

        // Calculate current day (0-indexed)
        int newDay = (int) (worldTime / world.daylightSpeed);

        if (newDay != currentDay) {
            currentDay = newDay;
        }

        // Night / Wave Active: timeOfDay >= 0.5f (Dusk)
        if (timeOfDay >= 0.5f) {
            if (!isWaveActive) {
                startNightWave();
            }
        } else {
            if (isWaveActive) {
                endNightWave();
            }
        }
    }

    private void startNightWave() {
        if (isWaveActive)
            return;

        isWaveActive = true;

        // Determine difficulty for this night
        // Current day (e.g., Day 0 is first night)
        int dayCount = currentDay + 1; // Display as Day 1, 2, etc.

        float hpMult = WaveConfig.getHealthMultiplier(dayCount);
        float dmgMult = WaveConfig.getDamageMultiplier(dayCount);
        float spawnMult = WaveConfig.getSpawnCountMultiplier(dayCount);

        logger.info("WaveManager: Night " + dayCount + " started! Difficulty: HP x" + hpMult);

        // Notify players
        String message = "Night " + dayCount + " has fallen. The horde approaches...";
        Color color = new Color(255, 50, 50); // Red

        // Check for Boss Night (Every 7 days)
        if (dayCount % WaveConfig.DAYS_PER_BOSS == 0) {
            message = "⚠️ BLOOD MOON RISES! A Boss Approaches! ⚠️";
            color = new Color(200, 0, 0); // Darker Red
        }

        sharedWorld.broadcastPacket(new PacketChatMessage(message, color));

        // Update MobSpawner
        if (sharedWorld.getMobSpawner() != null) {
            sharedWorld.getMobSpawner().setWaveMode(true);
            sharedWorld.getMobSpawner().setDifficultyMultipliers(hpMult, dmgMult, spawnMult);
        }

        // Sync to clients
        sharedWorld.broadcastWaveSync();
    }

    private void endNightWave() {
        if (!isWaveActive)
            return;

        isWaveActive = false;
        logger.info("WaveManager: Night ended. Wave cleared.");

        // Notify players
        sharedWorld.broadcastPacket(
                new PacketChatMessage("Dawn Breaks. The horde retreats... for now.", new Color(255, 200, 100)));

        // Reward: Award 1 skillpoint every 7 days (Dawn of Day 8, 15, 22...)
        int dayCount = currentDay + 1;
        if (dayCount % 7 == 0) {
            sharedWorld.broadcastSkillpointReward(1);
        }

        // Reset MobSpawner
        if (sharedWorld.getMobSpawner() != null) {
            sharedWorld.getMobSpawner().setWaveMode(false);
        }

        // Sync to clients
        sharedWorld.broadcastWaveSync();
    }

    public boolean isWaveActive() {
        return isWaveActive;
    }

    public int getCurrentDay() {
        return currentDay + 1; // 1-based for display
    }

    public float getCurrentHealthMultiplier() {
        return WaveConfig.getHealthMultiplier(currentDay + 1);
    }

    public float getCurrentDamageMultiplier() {
        return WaveConfig.getDamageMultiplier(currentDay + 1);
    }

    public float getJumpMultiplier() {
        // Centralized in WaveConfig
        float jumpVelocity = WaveConfig.getZombieJumpHeight(currentDay + 1);
        // Mult is ratio of scaled velocity to base (-0.3f)
        return jumpVelocity / WaveConfig.ZOMBIE_BASE_JUMP;
    }
}
