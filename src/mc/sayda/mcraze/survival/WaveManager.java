package mc.sayda.mcraze.survival;

import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.network.packet.PacketChatMessage;
import mc.sayda.mcraze.graphics.Color;

/**
 * Manages the wave survival mechanics, including day/night cycles,
 * difficulty tracking, and wave state.
 */
public class WaveManager {

    private final SharedWorld sharedWorld;
    private boolean isWaveActive = false;
    private int currentDay = 0;

    public WaveManager(SharedWorld sharedWorld) {
        this.sharedWorld = sharedWorld;
    }

    /**
     * Called every tick by SharedWorld
     */
    public void tick() {
        long worldTime = sharedWorld.getTime();
        long timeOfDay = worldTime % WaveConfig.DAY_LENGTH;

        // Calculate current day (0-indexed)
        int newDay = (int) (worldTime / WaveConfig.DAY_LENGTH);

        if (newDay != currentDay) {
            currentDay = newDay;
            // New day logic (midnight/morning rollover) is handled by specific time
            // triggers below
        }

        // Robust Wave Logic: Use range checks instead of exact equality
        // This handles world loading (already at night) and tick skipping

        // Night / Wave Active Range: [DUSK_TIME, 24000)
        // Day / Wave Inactive Range: [DAWN_TIME, DUSK_TIME)

        if (timeOfDay >= WaveConfig.DUSK_TIME) {
            // It is Night
            if (!isWaveActive) {
                startNightWave();
            }
        } else {
            // It is Day (timeOfDay < DUSK_TIME)
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

        GameLogger.get().info("WaveManager: Night " + dayCount + " started! Difficulty: HP x" + hpMult);

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
        GameLogger.get().info("WaveManager: Night ended. Wave cleared.");

        // Notify players
        sharedWorld.broadcastPacket(
                new PacketChatMessage("Dawn Breaks. The horde retreats... for now.", new Color(255, 200, 100)));

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
        // Linear increase: +2.5% jump height per day
        return 1.0f + (currentDay * 0.025f);
    }
}
