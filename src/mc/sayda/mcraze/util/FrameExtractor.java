package mc.sayda.mcraze.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to extract frames from Bad Apple MP4 and convert to compact binary
 * format.
 * 
 * Usage: Run this class's main method to convert bad_apple.mp4 to
 * bad_apple.badapple
 */
public class FrameExtractor {

    private static final int TARGET_WIDTH = 160;
    private static final int TARGET_HEIGHT = 120;
    private static final float TARGET_FPS = 30.0f;
    private static final int THRESHOLD = 128; // Gray value threshold for black/white

    public static void main(String[] args) throws Exception {
        String videoPath = "G:\\Github\\MCraze\\src\\assets\\bad_apple.mp4";
        String outputPath = "G:\\Github\\MCraze\\src\\assets\\bad_apple.badapple";
        String tempFramesDir = "G:\\Github\\MCraze\\temp_frames";

        System.out.println("Bad Apple Frame Extractor");
        System.out.println("=========================");

        // Step 1: Extract frames using FFmpeg
        System.out.println("Step 1: Extracting frames from MP4...");
        extractFrames(videoPath, tempFramesDir);

        // Step 2: Convert frames to binary and compress
        System.out.println("Step 2: Converting and compressing frames...");
        List<byte[]> compressedFrames = convertAndCompressFrames(tempFramesDir);

        // Step 3: Write binary file
        System.out.println("Step 3: Writing binary file...");
        writeBinaryFile(outputPath, compressedFrames);

        // Step 4: Cleanup
        System.out.println("Step 4: Cleaning up temporary files...");
        cleanupTempFiles(tempFramesDir);

        System.out.println("Done! Output: " + outputPath);
        System.out.println("Total frames: " + compressedFrames.size());
    }

    /**
     * Extract frames from video using FFmpeg
     */
    private static void extractFrames(String videoPath, String outputDir) throws Exception {
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // FFmpeg command to extract frames
        String[] command = {
                "C:\\\\Users\\\\johan\\\\Documents\\\\ffmpeg-2026-01-14-git-6c878f8b82-full_build\\\\bin\\\\ffmpeg.exe",
                "-i", videoPath,
                "-vf", "scale=" + TARGET_WIDTH + ":" + TARGET_HEIGHT + ",format=gray",
                "-r", String.valueOf(TARGET_FPS),
                outputDir + "\\frame_%05d.png"
        };

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("  FFmpeg: " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed with exit code: " + exitCode);
        }
    }

    /**
     * Convert PNG frames to 1-bit binary and apply RLE compression
     */
    private static List<byte[]> convertAndCompressFrames(String framesDir) throws Exception {
        List<byte[]> compressedFrames = new ArrayList<>();
        File dir = new File(framesDir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));

        if (files == null || files.length == 0) {
            throw new RuntimeException("No frames found in " + framesDir);
        }

        // Sort by filename
        java.util.Arrays.sort(files);

        for (int i = 0; i < files.length; i++) {
            if (i % 100 == 0) {
                System.out.println("  Processing frame " + (i + 1) + " / " + files.length);
            }

            BufferedImage img = ImageIO.read(files[i]);
            byte[] compressed = convertAndCompress(img);
            compressedFrames.add(compressed);
        }

        return compressedFrames;
    }

    /**
     * Convert image to 1-bit binary and compress with RLE
     */
    private static byte[] convertAndCompress(BufferedImage img) {
        // Convert to 1-bit black/white
        int width = img.getWidth();
        int height = img.getHeight();
        boolean[] pixels = new boolean[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF; // Get red channel (grayscale)
                pixels[y * width + x] = gray >= THRESHOLD; // true = white, false = black
            }
        }

        // RLE compression: [count][value] pairs
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            boolean currentValue = pixels[0];
            int count = 1;

            for (int i = 1; i < pixels.length; i++) {
                if (pixels[i] == currentValue && count < 255) {
                    count++;
                } else {
                    // Write run
                    dos.writeByte(count);
                    dos.writeBoolean(currentValue);

                    currentValue = pixels[i];
                    count = 1;
                }
            }

            // Write final run
            dos.writeByte(count);
            dos.writeBoolean(currentValue);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return baos.toByteArray();
    }

    /**
     * Write binary file with header and compressed frames
     */
    private static void writeBinaryFile(String outputPath, List<byte[]> frames) throws Exception {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputPath)))) {

            // Header
            dos.writeInt(TARGET_WIDTH);
            dos.writeInt(TARGET_HEIGHT);
            dos.writeInt(frames.size());
            dos.writeFloat(TARGET_FPS);

            // Frame offset table (for random access)
            int currentOffset = 16 + (frames.size() * 4); // Header + offset table
            for (byte[] frame : frames) {
                dos.writeInt(currentOffset);
                currentOffset += frame.length;
            }

            // Frame data
            for (byte[] frame : frames) {
                dos.write(frame);
            }
        }
    }

    /**
     * Cleanup temporary frame files
     */
    private static void cleanupTempFiles(String dir) {
        File directory = new File(dir);
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            directory.delete();
        }
    }
}
