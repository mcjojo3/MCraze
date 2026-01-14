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

import mc.sayda.mcraze.Constants;
import mc.sayda.mcraze.Constants.TileID;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.SpriteStore;

public class TileType implements java.io.Serializable {
	private static final long serialVersionUID = 2L; // Incremented for stable field

	/** The sprite that represents this Type */
	protected Sprite sprite;
	public TileID name;
	public boolean passable;
	public boolean liquid;
	public int lightBlocking;
	public int lightEmitting;
	public boolean stable; // true = block doesn't need ground support, false = needs support (plants)

	public TileType(String ref, TileID name) {
		this(ref, name, false, false, Constants.LIGHT_VALUE_OPAQUE);
	}

	public TileType(String ref, TileID name, boolean passable, boolean liquid, int lightBlocking) {
		this(ref, name, passable, liquid, lightBlocking, 0);
	}

	public TileType(String ref, TileID name, boolean passable, boolean liquid, int lightBlocking,
			int lightEmitting) {
		this(ref, name, passable, liquid, lightBlocking, lightEmitting, true);
	}

	public TileType(String ref, TileID name, boolean passable, boolean liquid, int lightBlocking,
			int lightEmitting, boolean stable) {
		this.sprite = SpriteStore.get().getSprite(ref);
		this.name = name;
		this.passable = passable;
		this.liquid = liquid;
		this.lightBlocking = lightBlocking;
		this.lightEmitting = lightEmitting;
		this.stable = stable;
	}

	public void draw(GraphicsHandler g, int x, int y) {
		sprite.draw(g, x, y);
	}
}
