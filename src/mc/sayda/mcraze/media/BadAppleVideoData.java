package mc.sayda.mcraze.media;

import java.io.*;

/**
 * Data structure to hold Bad Apple video data
 */
public class BadAppleVideoData {

    public final int width;
    public final int height;
    public final int frameCount;
    public final float fps;

    private final int[] frameOffsets;
    private final RandomAccessFile dataFile;

    public BadAppleVideoData(String filePath) throws IOException {
        // Try to load from JAR resources first (for packaged builds)
        File actualFile = getFileFromResources(filePath);

        dataFile = new RandomAccessFile(actualFile, "r");

        // Read header
        width = dataFile.readInt();
        height = dataFile.readInt();
        frameCount = dataFile.readInt();
        fps = dataFile.readFloat();

        // Read frame offset table
        frameOffsets = new int[frameCount];
        for (int i = 0; i < frameCount; i++) {
            frameOffsets[i] = dataFile.readInt();
        }
    }

    /**
     * Get file from resources (works both in development and when packaged as JAR)
     */
    private File getFileFromResources(String path) throws IOException {
        // Strip "src/" prefix if present (for backward compatibility)
        if (path.startsWith("src/")) {
            path = path.substring(4);
        }

        // First try as direct file (development mode)
        File directFile = new File(path);
        if (directFile.exists()) {
            return directFile;
        }

        // Also try with "src/" prefix (development mode)
        File srcFile = new File("src/" + path);
        if (srcFile.exists()) {
            return srcFile;
        }

        // Try to load from JAR resources (packaged mode)
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(path);
        if (resourceStream != null) {
            // Extract to temporary file
            File tempFile = File.createTempFile("bad_apple_", ".badapple");
            tempFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = resourceStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            } finally {
                resourceStream.close();
            }

            return tempFile;
        }

        // File not found anywhere
        throw new IOException("Bad Apple video file not found: " + path);
    }

    /**
     * Get a specific frame, decompressed
     * 
     * @param frameIndex Frame number (0-based)
     * @return 2D boolean array where true = white, false = black
     */
    public boolean[][] getFrame(int frameIndex) throws IOException {
        if (frameIndex < 0 || frameIndex >= frameCount) {
            return null;
        }

        // Seek to frame data
        dataFile.seek(frameOffsets[frameIndex]);

        // Determine frame size
        int frameSize;
        if (frameIndex < frameCount - 1) {
            frameSize = frameOffsets[frameIndex + 1] - frameOffsets[frameIndex];
        } else {
            frameSize = (int) (dataFile.length() - frameOffsets[frameIndex]);
        }

        // Read compressed data
        byte[] compressedData = new byte[frameSize];
        dataFile.readFully(compressedData);

        // Decompress RLE data
        return decompressFrame(compressedData);
    }

    /**
     * Decompress RLE-encoded frame data
     */
    private boolean[][] decompressFrame(byte[] compressed) throws IOException {
        boolean[][] pixels = new boolean[height][width];

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(compressed));
        int pixelIndex = 0;

        try {
            while (dis.available() > 0) {
                int count = dis.readUnsignedByte();
                boolean value = dis.readBoolean();

                for (int i = 0; i < count; i++) {
                    if (pixelIndex >= width * height)
                        break;
                    int y = pixelIndex / width;
                    int x = pixelIndex % width;
                    pixels[y][x] = value;
                    pixelIndex++;
                }
            }
        } catch (EOFException e) {
            // End of data
        }

        return pixels;
    }

    /**
     * Get duration in seconds
     */
    public float getDuration() {
        return frameCount / fps;
    }

    /**
     * Close the data file
     */
    public void close() throws IOException {
        if (dataFile != null) {
            dataFile.close();
        }
    }
}
