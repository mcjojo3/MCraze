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

package mc.sayda.mcraze.world.tile;

import mc.sayda.mcraze.world.*;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.gen.*;
import mc.sayda.mcraze.world.storage.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.graphics.*;

public class Tile implements java.io.Serializable {
	private static final long serialVersionUID = 2L; // Incremented for backdrop support

	public TileType type; // Foreground tile
	public TileType backdropType; // Background tile (nullable)
	public int metadata = 0; // Usage: Fluid level (0-8), Orientation, etc.

	public Tile(TileType type) {
		this.type = type;
		this.backdropType = null;
	}
}
