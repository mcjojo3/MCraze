package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.media.BadAppleVideoData;

import java.io.IOException;

/**
 * Bad Apple video player - renders video frames using block-sized rectangles
 */
public class BadApplePlayer {

    private BadAppleVideoData videoData;
    private boolean isPlaying;
    private long startTime;
    private int currentFrame;

    private static final int BLOCK_SIZE = 4; // 4x4 pixels per "block"

    public BadApplePlayer() {
        this.isPlaying = false;
    }

    /**
     * Start playing Bad Apple
     */
    public void play() {
        try {
            GameLogger logger = GameLogger.get();
            if (logger != null) {
                logger.info("Starting Bad Apple playback");
            }

            videoData = new BadAppleVideoData("src/assets/bad_apple.badapple");
            isPlaying = true;
            startTime = System.currentTimeMillis();
            currentFrame = 0;

        } catch (IOException e) {
            GameLogger logger = GameLogger.get();
            if (logger != null) {
                logger.error("Failed to load Bad Apple video: " + e.getMessage());
            }
            isPlaying = false;
        }
    }

    /**
     * Stop playback and cleanup
     */
    public void stop() {
        isPlaying = false;
        if (videoData != null) {
            try {
                videoData.close();
            } catch (IOException e) {
                // Ignore
            }
            videoData = null;
        }

        GameLogger logger = GameLogger.get();
        if (logger != null) {
            logger.info("Stopped Bad Apple playback");
        }
    }

    /**
     * Render current frame
     */
    public void render(int screenWidth, int screenHeight) {
        BadAppleVideoData data = this.videoData;
        if (!isPlaying || data == null) {
            return;
        }

        // Calculate current frame based on elapsed time
        long elapsedMs = System.currentTimeMillis() - startTime;
        float elapsedSec = elapsedMs / 1000.0f;
        int targetFrame = (int) (elapsedSec * data.fps);

        // Check if video finished
        if (targetFrame >= data.frameCount) {
            stop();
            return;
        }

        // Get frame
        try {
            boolean[][] frame = data.getFrame(targetFrame);
            currentFrame = targetFrame;

            if (frame != null) {
                renderFrame(data, frame, screenWidth, screenHeight);
            }

        } catch (IOException e) {
            GameLogger logger = GameLogger.get();
            if (logger != null) {
                logger.error("Error reading frame: " + e.getMessage());
            }
            stop();
        }
    }

    /**
     * Render a frame using block rectangles
     */
    private void renderFrame(BadAppleVideoData data, boolean[][] frame, int screenWidth, int screenHeight) {
        GraphicsHandler g = GraphicsHandler.get();

        // Calculate scaling to fit screen while maintaining aspect ratio
        int videoWidth = data.width * BLOCK_SIZE;
        int videoHeight = data.height * BLOCK_SIZE;

        float scaleX = (float) screenWidth / videoWidth;
        float scaleY = (float) screenHeight / videoHeight;
        float scale = Math.min(scaleX, scaleY);

        int renderWidth = (int) (videoWidth * scale);
        int renderHeight = (int) (videoHeight * scale);
        int offsetX = (screenWidth - renderWidth) / 2;
        int offsetY = (screenHeight - renderHeight) / 2;

        int blockSize = (int) (BLOCK_SIZE * scale);

        // Fill background black
        g.setColor(mc.sayda.mcraze.Color.black);
        g.fillRect(0, 0, screenWidth, screenHeight);

        // Draw pixels as blocks
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                if (frame[y][x]) { // White pixel
                    g.setColor(mc.sayda.mcraze.Color.white);
                    int pixelX = offsetX + (x * blockSize);
                    int pixelY = offsetY + (y * blockSize);
                    g.fillRect(pixelX, pixelY, blockSize, blockSize);
                }
            }
        }

        // Draw progress bar at bottom
        g.setColor(mc.sayda.mcraze.Color.gray);
        int barWidth = screenWidth - 40;
        int barHeight = 4;
        int barX = 20;
        int barY = screenHeight - 30;
        g.fillRect(barX, barY, barWidth, barHeight);

        g.setColor(mc.sayda.mcraze.Color.white);
        float progress = (float) currentFrame / data.frameCount;
        int progressWidth = (int) (barWidth * progress);
        g.fillRect(barX, barY, progressWidth, barHeight);
    }

    /**
     * Check if currently playing
     */
    public boolean isPlaying() {
        return isPlaying;
    }
}
