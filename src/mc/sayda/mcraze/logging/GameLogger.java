package mc.sayda.mcraze.logging;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handles logging to both console and file with multiple log levels
 */
public class GameLogger {
	private static GameLogger instance;
	private PrintWriter fileWriter;
	private PrintWriter debugWriter;
	private String logFilePath;
	private String debugLogPath;
	private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
	private boolean debugEnabled = false;

	// Store original streams before redirection to avoid infinite recursion
	private static final PrintStream originalOut = System.out;
	private static final PrintStream originalErr = System.err;

	private GameLogger(String logDir, String logName, boolean enableDebug) throws IOException {
		this.debugEnabled = enableDebug;

		// Create logs directory
		Path logsPath = Paths.get(logDir, "logs");
		Files.createDirectories(logsPath);

		// Create log file with timestamp
		String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
		String fileName = logName + "-" + timestamp + ".log";
		logFilePath = logsPath.resolve(fileName).toString();

		// Open main log file writer
		fileWriter = new PrintWriter(new BufferedWriter(new FileWriter(logFilePath, true)), true);

		// Create debug log if enabled
		if (debugEnabled) {
			String debugFileName = logName + "-debug-" + timestamp + ".log";
			debugLogPath = logsPath.resolve(debugFileName).toString();
			debugWriter = new PrintWriter(new BufferedWriter(new FileWriter(debugLogPath, true)), true);

			// Create debug.log symlink
			Path debugLatestPath = logsPath.resolve("debug.log");
			try {
				if (Files.exists(debugLatestPath)) {
					Files.delete(debugLatestPath);
				}
				Files.copy(Paths.get(debugLogPath), debugLatestPath);
			} catch (Exception e) {
				// Ignore if can't create debug.log
			}
		}

		// Create "latest.log" symlink/copy
		Path latestPath = logsPath.resolve("latest.log");
		try {
			if (Files.exists(latestPath)) {
				Files.delete(latestPath);
			}
			Files.copy(Paths.get(logFilePath), latestPath);
		} catch (Exception e) {
			// Ignore if can't create latest.log
		}

		log("INFO", "=== Logging started at " + timestamp + " ===");
		log("INFO", "Log file: " + logFilePath);
		if (debugEnabled) {
			log("INFO", "Debug log: " + debugLogPath);
		}
	}

	/**
	 * Initialize client logger (uses AppData)
	 */
	public static void initClient(boolean debugMode) throws IOException {
		String appData = System.getenv("APPDATA");
		if (appData == null) {
			appData = System.getProperty("user.home");
		}
		String gameDir = Paths.get(appData, "MCraze").toString();
		instance = new GameLogger(gameDir, "client", debugMode);

		// Redirect System.out and System.err to logger
		System.setOut(new PrintStream(new LogOutputStream("INFO"), true));
		System.setErr(new PrintStream(new LogOutputStream("ERROR"), true));
	}

	/**
	 * Initialize server logger (uses current directory)
	 */
	public static void initServer(boolean debugMode) throws IOException {
		String serverDir = System.getProperty("user.dir");
		instance = new GameLogger(serverDir, "server", debugMode);

		// Redirect System.out and System.err to logger
		System.setOut(new PrintStream(new LogOutputStream("INFO"), true));
		System.setErr(new PrintStream(new LogOutputStream("ERROR"), true));
	}

	public static GameLogger get() {
		return instance;
	}

	/**
	 * Log a message with level
	 */
	public void log(String level, String message) {
		String timestamp = timeFormat.format(new Date());
		String formatted = "[" + timestamp + "] [" + level + "] " + message;

		// DEBUG messages only go to debug log if enabled
		if (level.equals("DEBUG")) {
			if (debugEnabled && debugWriter != null) {
				debugWriter.println(formatted);
			}
			return;  // Don't print DEBUG to console or main log
		}

		// Print non-DEBUG messages to console using ORIGINAL streams (not redirected ones)
		if (level.equals("ERROR")) {
			originalErr.println(formatted);
		} else {
			originalOut.println(formatted);
		}

		// Write to main log file
		if (fileWriter != null) {
			fileWriter.println(formatted);
		}

		// Also write to debug log if enabled
		if (debugEnabled && debugWriter != null) {
			debugWriter.println(formatted);
		}
	}

	public void debug(String message) {
		log("DEBUG", message);
	}

	public void info(String message) {
		log("INFO", message);
	}

	public void warn(String message) {
		log("WARN", message);
	}

	public void error(String message) {
		log("ERROR", message);
	}

	public void error(String message, Throwable t) {
		log("ERROR", message);
		if (t != null) {
			// Print stack trace to both console and files
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			String stackTrace = sw.toString();

			// Write to main log
			if (fileWriter != null) {
				fileWriter.println(stackTrace);
			}

			// Write to debug log
			if (debugEnabled && debugWriter != null) {
				debugWriter.println(stackTrace);
			}

			// Print to console using ORIGINAL stream (not redirected)
			t.printStackTrace(originalErr);
		}
	}

	/**
	 * Flush and close logger
	 */
	public void close() {
		log("INFO", "=== Logging ended ===");

		if (fileWriter != null) {
			fileWriter.flush();
			fileWriter.close();
		}

		if (debugWriter != null) {
			debugWriter.flush();
			debugWriter.close();
		}
	}

	public String getLogFilePath() {
		return logFilePath;
	}

	public boolean isDebugEnabled() {
		return debugEnabled;
	}

	/**
	 * Output stream that writes to logger (for System.out/err redirection)
	 */
	private static class LogOutputStream extends OutputStream {
		private String level;
		private StringBuilder buffer = new StringBuilder();

		public LogOutputStream(String level) {
			this.level = level;
		}

		@Override
		public void write(int b) throws IOException {
			if (b == '\n') {
				if (buffer.length() > 0 && instance != null) {
					instance.log(level, buffer.toString());
					buffer.setLength(0);
				}
			} else if (b != '\r') {
				buffer.append((char) b);
			}
		}

		@Override
		public void flush() throws IOException {
			if (buffer.length() > 0 && instance != null) {
				instance.log(level, buffer.toString());
				buffer.setLength(0);
			}
		}
	}
}
