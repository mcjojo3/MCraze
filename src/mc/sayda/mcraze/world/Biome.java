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

package mc.sayda.mcraze.world;

/**
 * Biome types with temperature and humidity properties
 */
public enum Biome {
	PLAINS("Plains", 0.5f, 0.5f), // Temperate, moderate humidity
	FOREST("Forest", 0.6f, 0.7f), // Warm, high humidity
	DESERT("Desert", 0.9f, 0.1f), // Hot, dry
	MOUNTAIN("Mountain", 0.2f, 0.3f), // Cold, moderately dry
	OCEAN("Ocean", 0.5f, 1.0f); // Temperate, very wet

	private final String displayName;
	private final float temperature; // 0.0 = cold, 1.0 = hot
	private final float humidity; // 0.0 = dry, 1.0 = wet

	Biome(String displayName, float temperature, float humidity) {
		this.displayName = displayName;
		this.temperature = temperature;
		this.humidity = humidity;
	}

	public String getDisplayName() {
		return displayName;
	}

	public float getTemperature() {
		return temperature;
	}

	public float getHumidity() {
		return humidity;
	}
}
