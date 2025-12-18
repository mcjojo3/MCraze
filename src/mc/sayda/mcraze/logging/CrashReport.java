package mc.sayda.mcraze.logging;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Generates crash reports with system info and stack traces
 */
public class CrashReport {
	private String title;
	private Throwable cause;
	private StringBuilder details = new StringBuilder();

	public CrashReport(String title, Throwable cause) {
		this.title = title;
		this.cause = cause;
	}

	public CrashReport addDetail(String key, Object value) {
		details.append(key).append(": ").append(value).append("\n");
		return this;
	}

	/**
	 * Save crash report to file and return path
	 */
	public String save(String crashReportsDir) {
		try {
			// Create crash-reports directory
			Path crashDir = Paths.get(crashReportsDir, "crash-reports");
			Files.createDirectories(crashDir);

			// Create crash report file with timestamp
			String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
			String fileName = "crash-" + timestamp + ".txt";
			Path crashFile = crashDir.resolve(fileName);

			// Write crash report
			try (PrintWriter writer = new PrintWriter(new FileWriter(crashFile.toFile()))) {
				writer.println("---- MCraze Crash Report ----");
				writer.println();
				writer.println("Time: " + new Date());
				writer.println("Description: " + title);
				writer.println();

				// System information
				writer.println("-- System Details --");
				writer.println("Operating System: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ") version " + System.getProperty("os.version"));
				writer.println("Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor"));
				writer.println("Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor"));
				writer.println("Memory: " + (Runtime.getRuntime().freeMemory() / 1024 / 1024) + " MB free of " + (Runtime.getRuntime().totalMemory() / 1024 / 1024) + " MB");
				writer.println();

				// Custom details
				if (details.length() > 0) {
					writer.println("-- Game Details --");
					writer.println(details.toString());
				}

				// Stack trace
				if (cause != null) {
					writer.println("-- Stack Trace --");
					cause.printStackTrace(writer);
				}

				writer.println();
				writer.println("---- END OF CRASH REPORT ----");
			}

			return crashFile.toString();
		} catch (IOException e) {
			System.err.println("Failed to save crash report: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Save client crash report (to AppData)
	 */
	public String saveClient() {
		String appData = System.getenv("APPDATA");
		if (appData == null) {
			appData = System.getProperty("user.home");
		}
		String gameDir = Paths.get(appData, "MCraze").toString();
		return save(gameDir);
	}

	/**
	 * Save server crash report (to current directory)
	 */
	public String saveServer() {
		return save(System.getProperty("user.dir"));
	}

	/**
	 * Print crash report to console
	 */
	public void printToConsole() {
		System.err.println();
		System.err.println("---- MCraze Crash Report ----");
		System.err.println("Description: " + title);
		if (cause != null) {
			cause.printStackTrace();
		}
		System.err.println("---- END OF CRASH REPORT ----");
		System.err.println();
	}
}
