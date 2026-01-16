/*
 * Copyright 2026 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.util;

import mc.sayda.mcraze.logging.GameLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Manages saving and loading user credentials to/from AppData/Roaming
 * NOTE: This uses simple Base64 encoding, NOT encryption.
 * For production use, consider proper encryption.
 */
public class CredentialManager {
	private static final GameLogger logger = GameLogger.get();
	private static final String APP_NAME = "MCraze";
	private static final String CREDENTIALS_FILE = "credentials.dat";
	private static final String LAST_IP_FILE = "lastip.dat";

	/**
	 * Saved credentials container
	 */
	public static class SavedCredentials {
		public final String username;
		public final String password;

		public SavedCredentials(String username, String password) {
			this.username = username;
			this.password = password;
		}
	}

	/**
	 * Get the credentials directory path (AppData/Roaming/MCraze)
	 */
	private static Path getCredentialsDirectory() {
		String appData = System.getenv("APPDATA");
		if (appData == null) {
			// Fallback for non-Windows systems
			String userHome = System.getProperty("user.home");
			if (System.getProperty("os.name").toLowerCase().contains("mac")) {
				return Paths.get(userHome, "Library", "Application Support", APP_NAME);
			} else {
				// Linux and other Unix-like systems
				return Paths.get(userHome, ".config", APP_NAME);
			}
		}
		return Paths.get(appData, APP_NAME);
	}

	/**
	 * Get the credentials file path
	 */
	private static Path getCredentialsFilePath() {
		return getCredentialsDirectory().resolve(CREDENTIALS_FILE);
	}

	/**
	 * Save credentials to AppData/Roaming
	 * 
	 * @param username User's username
	 * @param password User's password
	 * @return true if saved successfully
	 */
	public static boolean saveCredentials(String username, String password) {
		try {
			// Create directory if it doesn't exist
			Path directory = getCredentialsDirectory();
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
			}

			// Encode credentials (simple Base64 - NOT secure encryption!)
			String combined = username + ":" + password;
			String encoded = Base64.getEncoder().encodeToString(combined.getBytes(StandardCharsets.UTF_8));

			// Write to file
			Path credentialsFile = getCredentialsFilePath();
			Files.write(credentialsFile, encoded.getBytes(StandardCharsets.UTF_8));

			Files.write(credentialsFile, encoded.getBytes(StandardCharsets.UTF_8));

			logger.info("Credentials saved to: " + credentialsFile);
			return true;
		} catch (IOException e) {
			logger.error("Failed to save credentials: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Load saved credentials from AppData/Roaming
	 * 
	 * @return SavedCredentials if found, null otherwise
	 */
	public static SavedCredentials loadCredentials() {
		try {
			Path credentialsFile = getCredentialsFilePath();

			// Check if file exists
			if (!Files.exists(credentialsFile)) {
				return null;
			}

			// Read and decode
			String encoded = new String(Files.readAllBytes(credentialsFile), StandardCharsets.UTF_8);
			String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

			// Parse username:password
			String[] parts = decoded.split(":", 2);
			if (parts.length != 2) {
				logger.error("Invalid credentials file format");
				return null;
			}

			logger.info("Credentials loaded from: " + credentialsFile);
			return new SavedCredentials(parts[0], parts[1]);
		} catch (IOException e) {
			logger.error("Failed to load credentials: " + e.getMessage());
			e.printStackTrace();
			return null;
		} catch (IllegalArgumentException e) {
			logger.error("Failed to decode credentials: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Delete saved credentials
	 * 
	 * @return true if deleted successfully or file didn't exist
	 */
	public static boolean deleteCredentials() {
		try {
			Path credentialsFile = getCredentialsFilePath();

			if (Files.exists(credentialsFile)) {
				Files.delete(credentialsFile);
				logger.info("Credentials deleted from: " + credentialsFile);
			}
			return true;
		} catch (IOException e) {
			logger.error("Failed to delete credentials: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Check if credentials are saved
	 * 
	 * @return true if credentials file exists
	 */
	public static boolean hasCredentials() {
		return Files.exists(getCredentialsFilePath());
	}

	/**
	 * Get the last IP file path
	 */
	private static Path getLastIPFilePath() {
		return getCredentialsDirectory().resolve(LAST_IP_FILE);
	}

	/**
	 * Save the last used multiplayer IP address
	 * 
	 * @param ipAddress The IP address to save
	 * @return true if saved successfully
	 */
	public static boolean saveLastIP(String ipAddress) {
		if (ipAddress == null || ipAddress.trim().isEmpty()) {
			return false;
		}

		try {
			// Create directory if it doesn't exist
			Path directory = getCredentialsDirectory();
			if (!Files.exists(directory)) {
				Files.createDirectories(directory);
			}

			// Write IP address to file
			Path ipFile = getLastIPFilePath();
			Files.write(ipFile, ipAddress.trim().getBytes(StandardCharsets.UTF_8));

			logger.info("Last IP saved to: " + ipFile);
			return true;
		} catch (IOException e) {
			logger.error("Failed to save last IP: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Load the last used multiplayer IP address
	 * 
	 * @return The last IP address, or null if not found
	 */
	public static String loadLastIP() {
		try {
			Path ipFile = getLastIPFilePath();

			// Check if file exists
			if (!Files.exists(ipFile)) {
				return null;
			}

			// Read IP address
			String ip = new String(Files.readAllBytes(ipFile), StandardCharsets.UTF_8).trim();
			logger.info("Last IP loaded from: " + ipFile);
			return ip.isEmpty() ? null : ip;
		} catch (IOException e) {
			logger.error("Failed to load last IP: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
}
