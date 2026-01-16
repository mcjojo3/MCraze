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

package mc.sayda.mcraze;

import java.io.BufferedInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.Mixer;

public class MusicPlayer {
	private final mc.sayda.mcraze.logging.GameLogger logger = mc.sayda.mcraze.logging.GameLogger.get();
	private Clip currentClip;
	private float volume = 0.5f; // Volume level (0.0 to 1.0)
	private boolean muted = false; // Muted state

	// Playlist support - separate playlists for different contexts
	private List<String> menuPlaylist;
	private List<String> dayPlaylist;
	private List<String> nightPlaylist;
	private List<String> cavePlaylist;
	private List<String> musicPlaylist;
	private List<String> currentPlaylist; // Active playlist
	private int currentTrackIndex = -1;
	private Random random;
	private String currentContext = "menu"; // Current music context (menu/cave)

	// State saving for one-time tracks (like Bad Apple)
	private String savedContext = null;
	private int savedTrackIndex = -1;

	// Selected mixer for audio playback (avoids PortMixer)
	private Mixer.Info selectedMixerInfo = null;

	public MusicPlayer(String filename) {
		this.random = new Random();

		// Load separate playlists
		this.menuPlaylist = loadPlaylistFromFolder("assets/sounds/menu");
		this.dayPlaylist = loadPlaylistFromFolder("assets/sounds/day");
		this.nightPlaylist = loadPlaylistFromFolder("assets/sounds/night");
		this.cavePlaylist = loadPlaylistFromFolder("assets/sounds/cave");
		this.musicPlaylist = loadPlaylistFromFolder("assets/sounds/music");

		// Start with menu playlist
		this.currentPlaylist = menuPlaylist;
		this.currentContext = "menu";

		try {
			// Find compatible mixer (avoids PortMixer which causes errors)
			selectedMixerInfo = findCompatibleMixer();

			if (selectedMixerInfo != null) {
				logger.info("Music system using mixer: " + selectedMixerInfo.getName());

				// Load first track from menu playlist
				if (!currentPlaylist.isEmpty()) {
					currentTrackIndex = random.nextInt(currentPlaylist.size());
					loadTrack(currentPlaylist.get(currentTrackIndex));
					play(); // Auto-play first track
				}
			} else {
				logger.error("No compatible audio mixer found - music disabled");
			}
		} catch (Exception e) {
			logger.error("Failed to initialize music system: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Find a compatible audio mixer (avoids Port mixers)
	 * Port mixers are for MIDI/volume controls, not audio playback
	 */
	private Mixer.Info findCompatibleMixer() {
		Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
		DataLine.Info clipInfo = new DataLine.Info(Clip.class, null);

		logger.info("Searching for compatible audio mixer...");
		logger.info("Available mixers: " + mixerInfos.length);

		for (Mixer.Info mixerInfo : mixerInfos) {
			String name = mixerInfo.getName().toLowerCase();

			// Skip Port mixers (they don't support audio playback)
			if (name.contains("port")) {
				System.out.println("  Skipping PortMixer: " + mixerInfo.getName());
				continue;
			}

			try {
				Mixer mixer = AudioSystem.getMixer(mixerInfo);

				// Check if this mixer supports Clips
				if (mixer.isLineSupported(clipInfo)) {
					System.out.println("  Found compatible mixer: " + mixerInfo.getName());
					System.out.println("    Description: " + mixerInfo.getDescription());
					return mixerInfo;
				} else {
					System.out.println("  Incompatible mixer (no Clip support): " + mixerInfo.getName());
				}
			} catch (Exception e) {
				System.out.println("  Failed to test mixer: " + mixerInfo.getName());
			}
		}

		System.err.println("WARNING: No compatible audio mixer found!");
		return null;
	}

	/**
	 * Load playlist from a specific folder
	 * 
	 * @param folderPath Path relative to src/
	 */
	private List<String> loadPlaylistFromFolder(String folderPath) {
		List<String> tracks = new ArrayList<>();

		try {
			// Try src/ prefix first (development - running from IDE)
			File soundsDir = new File("src/" + folderPath);
			if (!soundsDir.exists()) {
				// Try without src/ prefix (extracted JAR or production)
				soundsDir = new File(folderPath);
			}

			if (soundsDir.exists() && soundsDir.isDirectory()) {
				// Running from filesystem (development or extracted JAR)
				File[] files = soundsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));

				if (files != null) {
					for (File file : files) {
						tracks.add(folderPath + "/" + file.getName());
					}
				}
			} else {
				// Try loading from JAR resources (uber-JAR)
				try {
					java.net.URL resourceUrl = getClass().getClassLoader().getResource(folderPath);
					if (resourceUrl != null && resourceUrl.getProtocol().equals("jar")) {
						// Inside a JAR file - need to list resources differently
						String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
						java.util.jar.JarFile jar = new java.util.jar.JarFile(
								java.net.URLDecoder.decode(jarPath, "UTF-8"));
						java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();

						while (entries.hasMoreElements()) {
							java.util.jar.JarEntry entry = entries.nextElement();
							String name = entry.getName();
							if (name.startsWith(folderPath + "/") && name.toLowerCase().endsWith(".wav")) {
								tracks.add(name);
							}
						}
						jar.close();
					} else if (resourceUrl != null) {
						// Resource exists but not in JAR format - try as directory
						File resourceDir = new File(resourceUrl.toURI());
						if (resourceDir.isDirectory()) {
							File[] files = resourceDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
							if (files != null) {
								for (File file : files) {
									tracks.add(folderPath + "/" + file.getName());
								}
							}
						}
					} else {
						System.err.println(
								"Music folder not found: " + folderPath + " (not in filesystem or JAR resources)");
					}
				} catch (Exception jarEx) {
					System.err.println("Error loading music from JAR: " + jarEx.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("Error scanning music folder " + folderPath + ": " + e.getMessage());
		}

		System.out.println("Music playlist loaded from " + folderPath + ": " + tracks.size() + " tracks");
		return tracks;
	}

	/**
	 * Load a specific track
	 */
	private void loadTrack(String filename) {
		if (selectedMixerInfo == null) {
			System.err.println("No audio mixer selected - cannot load track");
			return;
		}

		try {
			// Stop and close current clip if playing
			if (currentClip != null) {
				currentClip.stop();
				currentClip.close();
			}

			// Load audio file
			java.net.URL trackURL = null;

			// First try classpath resource
			trackURL = getClass().getClassLoader().getResource(filename);

			if (trackURL == null) {
				// Try loading from file system
				File trackFile = new File(filename);
				if (!trackFile.exists()) {
					// Try src/ prefix for development environment
					trackFile = new File("src/" + filename);
				}
				if (trackFile.exists()) {
					trackURL = trackFile.toURI().toURL();
				} else {
					throw new java.io.FileNotFoundException("Track not found: " + filename);
				}
			}

			// Open audio stream
			AudioInputStream audioStream = AudioSystem.getAudioInputStream(
					new BufferedInputStream(trackURL.openStream()));

			// Get Clip from our selected mixer (NOT default mixer)
			currentClip = AudioSystem.getClip(selectedMixerInfo);

			// Open and prepare clip
			currentClip.open(audioStream);

			currentClip.start(); // play once

			// Add listener for track natural end
			currentClip.addLineListener(event -> {
				if (event.getType() == LineEvent.Type.STOP &&
						currentClip.getMicrosecondPosition() >= currentClip.getMicrosecondLength() - 1_000) {
					nextTrack();
				}
			});

			// Apply current volume and muted state
			updateVolume();

			System.out.println("Loaded track: " + trackURL);

		} catch (Exception e) {
			System.err.println("Failed to load track: " + filename);
			System.err.println("Reason: " + e.getMessage());

			// Only print stack trace in debug mode
			if (Boolean.getBoolean("debug.music")) {
				e.printStackTrace();
			}

			// Try to recover by loading next track
			if (!currentPlaylist.isEmpty() && currentTrackIndex >= 0 && currentTrackIndex < currentPlaylist.size()) {
				System.out.println("Removing failed track from playlist: " + filename);
				currentPlaylist.remove(currentTrackIndex);

				if (!currentPlaylist.isEmpty()) {
					// Adjust index if needed
					if (currentTrackIndex >= currentPlaylist.size()) {
						currentTrackIndex = 0;
					}

					String nextTrack = currentPlaylist.get(currentTrackIndex);
					System.out.println("Loading next available track: " + nextTrack);

					// Recursive call to load next track
					loadTrack(nextTrack);
				} else {
					System.err.println("No more valid music tracks available in " + currentContext + " playlist");
				}
			}
		}
	}

	/**
	 * Update volume on current clip
	 */
	private void updateVolume() {
		if (currentClip == null || !currentClip.isOpen()) {
			return;
		}

		try {
			// Get master gain control
			if (currentClip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
				FloatControl gainControl = (FloatControl) currentClip.getControl(FloatControl.Type.MASTER_GAIN);

				if (muted) {
					// Mute by setting to minimum
					gainControl.setValue(gainControl.getMinimum());
				} else {
					// Convert 0.0-1.0 to decibels
					// Most systems: min=-80dB, max=6dB
					float min = gainControl.getMinimum();
					float max = gainControl.getMaximum();

					// Use logarithmic scale for natural volume perception
					// volume=0.5 should be ~halfway in perceived loudness
					float gain;
					if (volume <= 0.0f) {
						gain = min;
					} else {
						// Logarithmic scaling: 20 * log10(volume)
						gain = (float) (20.0 * Math.log10(volume));

						// Clamp to valid range
						gain = Math.max(min, Math.min(max, gain));
					}

					gainControl.setValue(gain);
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to set volume: " + e.getMessage());
		}
	}

	/**
	 * Play (or resume) music
	 */
	public void play() {
		if (currentClip != null && currentClip.isOpen()) {
			currentClip.start();
		}
	}

	/**
	 * Pause music
	 */
	public void pause() {
		if (currentClip != null && currentClip.isRunning()) {
			currentClip.stop();
		}
	}

	/**
	 * Toggle mute state
	 */
	public void toggleSound() {
		muted = !muted;
		updateVolume();
		System.out.println(muted ? "Music muted" : "Music unmuted");
	}

	/**
	 * Set volume (0.0 = silent, 1.0 = max)
	 */
	public void setVolume(float volume) {
		this.volume = Math.max(0.0f, Math.min(1.0f, volume)); // Clamp between 0 and 1
		updateVolume();
	}

	/**
	 * Get current volume level
	 */
	public float getVolume() {
		return volume;
	}

	/**
	 * Set muted state
	 */
	public void setMuted(boolean muted) {
		this.muted = muted;
		updateVolume();
	}

	/**
	 * Get muted state
	 */
	public boolean isMuted() {
		return muted;
	}

	/**
	 * Skip to next random track
	 */
	public void nextTrack() {
		if (currentPlaylist.isEmpty()) {
			System.out.println("Music playlist is empty - no tracks to play");
			return;
		}

		// Pick a different random track
		int newIndex = random.nextInt(currentPlaylist.size());

		// Ensure we don't pick the same track twice in a row (if possible)
		if (currentPlaylist.size() > 1 && currentTrackIndex >= 0) {
			int attempts = 0;
			while (newIndex == currentTrackIndex && attempts < 10) {
				newIndex = random.nextInt(currentPlaylist.size());
				attempts++;
			}
		}

		currentTrackIndex = newIndex;
		loadTrack(currentPlaylist.get(currentTrackIndex));

		// Play the new track
		play();
	}

	/**
	 * Play a one-time track (interrupts current playback, but saves state to
	 * restore later)
	 * Used for special events like Bad Apple
	 * 
	 * @param filename Path to the audio file
	 */
	public void playOneTimeTrack(String filename) {
		// Save current state
		savedContext = currentContext;
		savedTrackIndex = currentTrackIndex;

		// Load and play the one-time track
		loadTrack(filename);
		play();

		System.out.println("Playing one-time track: " + filename);
	}

	/**
	 * Restore normal playlist playback after a one-time track
	 * Returns to the saved context and continues from there
	 */
	public void restoreNormalPlayback() {
		// Stop the current one-time track first
		if (currentClip != null) {
			currentClip.stop();
			currentClip.close();
		}

		if (savedContext != null) {
			System.out.println("Restoring normal playback to context: " + savedContext);

			// Restore context
			currentContext = savedContext;
			currentTrackIndex = savedTrackIndex;

			// Clear saved state
			savedContext = null;
			savedTrackIndex = -1;

			// Force reload of previous track
			if (currentPlaylist != null && !currentPlaylist.isEmpty()) {
				if (currentTrackIndex < 0 || currentTrackIndex >= currentPlaylist.size()) {
					currentTrackIndex = 0;
				}
				loadTrack(currentPlaylist.get(currentTrackIndex));
				play();
			} else {
				nextTrack();
			}
		} else {
			// No saved state, just play next track from current playlist
			nextTrack();
		}
	}

	/**
	 * Switch music context
	 * This will change the active playlist and start playing from the new playlist
	 * 
	 * @param context "menu" for main menu music, "day", "night", "cave" for in-game
	 *                music
	 */
	public void switchContext(String context) {
		if (context.equals(currentContext)) {
			return; // Already in this context
		}

		System.out.println("Switching music context: " + currentContext + " -> " + context);

		currentContext = context;

		// Switch to appropriate playlist
		if (context.equals("menu")) {
			currentPlaylist = menuPlaylist;
		} else if (context.equals("day")) {
			currentPlaylist = dayPlaylist;
		} else if (context.equals("night")) {
			currentPlaylist = nightPlaylist;
		} else if (context.equals("cave")) {
			currentPlaylist = cavePlaylist;
		} else if (context.equals("music")) {
			currentPlaylist = musicPlaylist;
		} else {
			System.err.println("Unknown music context: " + context);
			return;
		}

		// Start playing from new playlist
		if (!currentPlaylist.isEmpty()) {
			currentTrackIndex = random.nextInt(currentPlaylist.size());
			loadTrack(currentPlaylist.get(currentTrackIndex));
			play();
		} else {
			System.err.println("No tracks in " + context + " playlist");
		}
	}

	/**
	 * Close and cleanup resources
	 */
	public void close() {
		try {
			if (currentClip != null) {
				currentClip.stop();
				currentClip.close();
			}
		} catch (Exception e) {
			System.err.println("Error closing music player: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
