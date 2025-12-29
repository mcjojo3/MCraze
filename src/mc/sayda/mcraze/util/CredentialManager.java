/*
 * Copyright 2025 SaydaGames (mc_jojo3)
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
	private static final String APP_NAME = "MCraze";
	private static final String CREDENTIALS_FILE = "credentials.dat";

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

			System.out.println("Credentials saved to: " + credentialsFile);
			return true;
		} catch (IOException e) {
			System.err.println("Failed to save credentials: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Load saved credentials from AppData/Roaming
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
				System.err.println("Invalid credentials file format");
				return null;
			}

			System.out.println("Credentials loaded from: " + credentialsFile);
			return new SavedCredentials(parts[0], parts[1]);
		} catch (IOException e) {
			System.err.println("Failed to load credentials: " + e.getMessage());
			e.printStackTrace();
			return null;
		} catch (IllegalArgumentException e) {
			System.err.println("Failed to decode credentials: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Delete saved credentials
	 * @return true if deleted successfully or file didn't exist
	 */
	public static boolean deleteCredentials() {
		try {
			Path credentialsFile = getCredentialsFilePath();

			if (Files.exists(credentialsFile)) {
				Files.delete(credentialsFile);
				System.out.println("Credentials deleted from: " + credentialsFile);
			}
			return true;
		} catch (IOException e) {
			System.err.println("Failed to delete credentials: " + e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Check if credentials are saved
	 * @return true if credentials file exists
	 */
	public static boolean hasCredentials() {
		return Files.exists(getCredentialsFilePath());
	}
}
