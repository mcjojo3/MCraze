package mc.sayda.mcraze;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.net.URL;

public class SoundManager {

    /**
     * Play a sound effect
     * 
     * @param soundPath relative path to sound file (e.g. "assets/sounds/hit.wav")
     * @param volume    volume 0.0 to 1.0 (default 1.0)
     */
    public static void playSound(String soundPath, float volume) {
        new Thread(() -> {
            try {
                URL url = resolvePath(soundPath);
                if (url == null) {
                    // System.err.println("Sound not found: " + soundPath);
                    return;
                }

                // Load audio stream
                AudioInputStream audioStream = AudioSystem
                        .getAudioInputStream(new BufferedInputStream(url.openStream()));

                // Get a clip (try default wrapper)
                Clip clip = AudioSystem.getClip();

                // Open clip
                clip.open(audioStream);

                // Volume control
                if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float range = gainControl.getMaximum() - gainControl.getMinimum();
                    // Logarithmic volume
                    float gain;
                    if (volume <= 0.0f) {
                        gain = gainControl.getMinimum();
                    } else {
                        gain = (float) (20.0 * Math.log10(volume));
                        gain = Math.max(gainControl.getMinimum(), Math.min(gainControl.getMaximum(), gain));
                    }
                    gainControl.setValue(gain);
                }

                // Start playback
                clip.start();

                // Cleanup listener
                clip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        clip.close();
                    }
                });

            } catch (Exception e) {
                // System.err.println("Failed to play sound " + soundPath + ": " +
                // e.getMessage());
            }
        }).start();
    }

    private static URL resolvePath(String filename) {
        // Try classpath
        URL url = SoundManager.class.getClassLoader().getResource(filename);
        if (url != null)
            return url;

        // Try filesystem
        File f = new File(filename);
        if (!f.exists())
            f = new File("src/" + filename);

        if (f.exists()) {
            try {
                return f.toURI().toURL();
            } catch (Exception e) {
            }
        }
        return null;
    }
}
