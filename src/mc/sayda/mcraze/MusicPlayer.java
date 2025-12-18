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

package mc.sayda.mcraze;

import java.io.IOException;

import org.newdawn.easyogg.OggClip;

public class MusicPlayer {
	// No one wants a music player crashing their game... ;)
	OggClip ogg;
	private float volume = 0.5f;  // Default volume (0.0 to 1.0)

	public MusicPlayer(String filename) {
		try {
			ogg = new OggClip(filename);
			// Note: OggClip doesn't support volume control via setGain()
			// Volume must be controlled through OS/system mixer
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	boolean flipMute = true;

	public void toggleSound() {
		try {
			if (flipMute) {
				ogg.stop();
			} else {
				ogg.loop();
			}
			flipMute = !flipMute;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void play() {
		try {
			ogg.loop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void pause() {
		try {
			ogg.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			ogg.stop();
			ogg.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Set music volume
	 * @param volume Volume level from 0.0 (silent) to 1.0 (max)
	 * Note: OggClip doesn't support runtime volume control, this just stores the preference
	 */
	public void setVolume(float volume) {
		this.volume = Math.max(0.0f, Math.min(1.0f, volume));  // Clamp between 0 and 1
		// OggClip doesn't have volume control API
		// Users must adjust volume via system mixer
	}

	/**
	 * Get current music volume
	 * @return Volume level from 0.0 to 1.0
	 */
	public float getVolume() {
		return volume;
	}
}
