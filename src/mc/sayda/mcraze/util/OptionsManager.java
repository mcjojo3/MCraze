package mc.sayda.mcraze.util;

import mc.sayda.mcraze.logging.GameLogger;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages persistent game options (volume, FPS, last IP, etc.)
 * Saves to options.txt in AppData
 */
public class OptionsManager {
    private static final GameLogger logger = GameLogger.get();
    private static final String APP_NAME = "MCraze";
    private static final String OPTIONS_FILE = "options.txt";

    private static OptionsManager instance;

    // Options with defaults
    private float musicVolume = 0.5f;
    private boolean showFPS = false;
    private String lastServerIP = "localhost:25565";

    public static OptionsManager get() {
        if (instance == null) {
            instance = new OptionsManager();
            instance.load();
        }
        return instance;
    }

    private OptionsManager() {
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(float volume) {
        this.musicVolume = Math.max(0.0f, Math.min(1.0f, volume));
        save();
    }

    public boolean isShowFPS() {
        return showFPS;
    }

    public void setShowFPS(boolean showFPS) {
        this.showFPS = showFPS;
        save();
    }

    public String getLastServerIP() {
        return lastServerIP;
    }

    public void setLastServerIP(String lastServerIP) {
        this.lastServerIP = lastServerIP;
        save();
    }

    private Path getOptionsPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            String userHome = System.getProperty("user.home");
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                return Paths.get(userHome, "Library", "Application Support", APP_NAME, OPTIONS_FILE);
            } else {
                return Paths.get(userHome, ".config", APP_NAME, OPTIONS_FILE);
            }
        }
        return Paths.get(appData, APP_NAME, OPTIONS_FILE);
    }

    private void load() {
        try {
            Path path = getOptionsPath();
            if (Files.exists(path)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(path)) {
                    props.load(in);
                }

                if (props.containsKey("musicVolume")) {
                    musicVolume = Float.parseFloat(props.getProperty("musicVolume"));
                }
                if (props.containsKey("showFPS")) {
                    showFPS = Boolean.parseBoolean(props.getProperty("showFPS"));
                }
                if (props.containsKey("lastServerIP")) {
                    lastServerIP = props.getProperty("lastServerIP");
                }
            } else {
                // Migration: Try to load legacy IP if options don't exist yet
                String legacyIP = CredentialManager.loadLastIP();
                if (legacyIP != null) {
                    lastServerIP = legacyIP;
                    save(); // Save new options file immediately
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load options: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Path path = getOptionsPath();
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            Properties props = new Properties();
            props.setProperty("musicVolume", String.valueOf(musicVolume));
            props.setProperty("showFPS", String.valueOf(showFPS));
            props.setProperty("lastServerIP", lastServerIP != null ? lastServerIP : "");

            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "MCraze Game Options");
            }
        } catch (Exception e) {
            logger.error("Failed to save options: " + e.getMessage());
        }
    }
}
