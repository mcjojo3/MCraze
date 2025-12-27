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

package mc.sayda.mcraze.server;

import mc.sayda.mcraze.entity.Entity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe entity management using CopyOnWriteArrayList.
 * Allows concurrent iteration (rendering) while server tick modifies the list.
 *
 * How it works:
 * - getAll() returns the internal list which is safe to iterate (snapshot semantics)
 * - Modifications (add/remove) only copy when needed
 * - Read operations are lock-free and very fast
 * - Write operations are slightly slower but infrequent (entity spawn/despawn)
 */
public class EntityManager {
	private final CopyOnWriteArrayList<Entity> entities = new CopyOnWriteArrayList<>();

	/**
	 * Get all entities (safe to iterate concurrently).
	 * Returns the internal list which uses copy-on-write semantics.
	 */
	public List<Entity> getAll() {
		return entities;
	}

	/**
	 * Add an entity (thread-safe, triggers copy if needed)
	 */
	public void add(Entity entity) {
		if (entity != null && !entities.contains(entity)) {
			entities.add(entity);
		}
	}

	/**
	 * Remove an entity (thread-safe, triggers copy if needed)
	 */
	public void remove(Entity entity) {
		entities.remove(entity);
	}

	/**
	 * Remove entity by UUID (thread-safe)
	 */
	public boolean removeByUUID(String uuid) {
		return entities.removeIf(e -> e.getUUID().equals(uuid));
	}

	/**
	 * Find entity by UUID (thread-safe read)
	 */
	public Entity findByUUID(String uuid) {
		for (Entity entity : entities) {
			if (entity.getUUID().equals(uuid)) {
				return entity;
			}
		}
		return null;
	}

	/**
	 * Clear all entities (thread-safe)
	 */
	public void clear() {
		entities.clear();
	}

	/**
	 * Get entity count (thread-safe read)
	 */
	public int size() {
		return entities.size();
	}

	/**
	 * Check if contains entity (thread-safe read)
	 */
	public boolean contains(Entity entity) {
		return entities.contains(entity);
	}
}
