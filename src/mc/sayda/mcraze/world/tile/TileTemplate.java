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

import mc.sayda.mcraze.Constants.TileID;
import java.util.HashMap;
import java.util.Map;

public final class TileTemplate implements java.io.Serializable {
	private static final long serialVersionUID = 1L;

	public static final Map<String, TileTemplate> REGISTRY = new HashMap<>();

	private static TileTemplate register(String name, TileTemplate t) {
		REGISTRY.put(name.toLowerCase(), t);
		return t;
	}

	public static TileTemplate get(String name) {
		return REGISTRY.get(name.toLowerCase());
	}

	public static final TileTemplate tree = register("tree",
			new TileTemplate(
					new TileID[][] {
							{ TileID.NONE, TileID.LEAVES, TileID.LEAVES, TileID.NONE, TileID.NONE,
									TileID.NONE },
							{ TileID.LEAVES, TileID.LEAVES, TileID.LEAVES, TileID.LEAVES, TileID.NONE,
									TileID.NONE },
							{ TileID.LEAVES, TileID.LEAVES, TileID.LEAVES, TileID.WOOD, TileID.WOOD,
									TileID.WOOD },
							{ TileID.LEAVES, TileID.LEAVES, TileID.LEAVES, TileID.LEAVES, TileID.NONE,
									TileID.NONE },
							{ TileID.NONE, TileID.LEAVES, TileID.LEAVES, TileID.NONE, TileID.NONE,
									TileID.NONE } },
					null, 5, 2));

	// Dungeon template (6 tall, 7 wide) - TRANSPOSED for correct orientation
	// BBBBBBB - Roof
	// BbbbbbB - Walls with backdrop
	// BbbbbbB
	// BbbbbbB
	// BCbMbCB - Chests, Spawner in middle
	// BBBBBBB - Floor
	public static final TileTemplate dungeon = register("dungeon",
			new TileTemplate(
					new TileID[][] {
							{ TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE,
									TileID.COBBLE }, // Left wall
							{ TileID.COBBLE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.CHEST, TileID.COBBLE },
							{ TileID.COBBLE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.COBBLE },
							{ TileID.COBBLE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.SPAWNER, TileID.COBBLE },
							{ TileID.COBBLE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.COBBLE },
							{ TileID.COBBLE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.CHEST, TileID.COBBLE },
							{ TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE } // Right
																															// wall
					},
					new TileID[][] {
							{ TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE }, // Left
																												// wall
																												// backdrop
							{ TileID.NONE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.NONE, TileID.NONE },
							{ TileID.NONE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.NONE },
							{ TileID.NONE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.NONE, TileID.NONE },
							{ TileID.NONE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.NONE },
							{ TileID.NONE, TileID.COBBLE, TileID.COBBLE, TileID.COBBLE, TileID.NONE, TileID.NONE },
							{ TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE, TileID.NONE } // Right
																												// wall
																												// backdrop
					}, 0, 0));

	public TileID[][] template; // Foreground tiles
	public TileID[][] backdropTemplate; // Backdrop tiles (can be null)
	public int spawnX;
	public int spawnY;

	// Constructor without backdrops (for compatibility)
	private TileTemplate(TileID[][] tileIDs, TileID[][] backdropIDs, int spawnX, int spawnY) {
		this.template = tileIDs;
		this.backdropTemplate = backdropIDs;
		this.spawnX = spawnX;
		this.spawnY = spawnY;
	}
}
