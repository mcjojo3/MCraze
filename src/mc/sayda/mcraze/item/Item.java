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

package mc.sayda.mcraze.item;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.util.Template;

public class Item extends Entity implements Cloneable {
	private static final long serialVersionUID = 1L;

	public String itemId;
	public String name;
	public Template template;

	// Item despawn timer (prevents entity accumulation in world)
	private long spawnTime;
	private static final long DESPAWN_TIME_MS = 300000; // 5 minutes

	public Item(String ref, int size, String itemId, String name, String[][] template, int templateCount,
			boolean shapeless) {
		super(ref, true, 0, 0, size, size);
		this.template = new Template(template, templateCount, shapeless);
		this.itemId = itemId;
		this.name = name;
		this.spawnTime = System.currentTimeMillis();
	}

	@Override
	public Item clone() {
		try {
			Item cloned = (Item) super.clone();
			// Reset spawn time for cloned items (e.g., when picking up from inventory)
			cloned.spawnTime = System.currentTimeMillis();
			return cloned;
		} catch (CloneNotSupportedException e) {
			return null; // should never happen
		}
	}

	/**
	 * Check if item should despawn (prevents entity accumulation)
	 * Items despawn after 5 minutes to prevent lag from dropped item accumulation
	 */
	public boolean shouldDespawn() {
		return System.currentTimeMillis() - spawnTime > DESPAWN_TIME_MS;
	}

}
