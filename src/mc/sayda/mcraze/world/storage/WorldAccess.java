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

package mc.sayda.mcraze.world.storage;

import mc.sayda.mcraze.world.*;
import mc.sayda.mcraze.world.tile.*;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe wrapper for World that uses ReadWriteLock for tile access.
 * Allows concurrent reads (multiple threads can render) while ensuring
 * exclusive writes (only server tick modifies).
 */
public class WorldAccess {
	private final World world;
	private final ReadWriteLock tilesLock = new ReentrantReadWriteLock();

	public WorldAccess(World world) {
		this.world = world;
	}

	/**
	 * Get the underlying World instance (use with caution - not thread-safe)
	 */
	public World getWorld() {
		return world;
	}

	/**
	 * Thread-safe tile access with read lock
	 */
	public Tile getTile(int x, int y) {
		tilesLock.readLock().lock();
		try {
			if (x < 0 || y < 0 || x >= world.tiles.length || y >= world.tiles[0].length) {
				return null;
			}
			return world.tiles[x][y];
		} finally {
			tilesLock.readLock().unlock();
		}
	}

	/**
	 * Thread-safe tile modification with write lock (server tick only)
	 */
	public void setTile(int x, int y, Tile tile) {
		tilesLock.writeLock().lock();
		try {
			if (x >= 0 && y >= 0 && x < world.tiles.length && y < world.tiles[0].length) {
				world.tiles[x][y] = tile;
			}
		} finally {
			tilesLock.writeLock().unlock();
		}
	}

	/**
	 * Thread-safe bulk rendering - holds read lock for entire operation.
	 * Prevents world changes during rendering for consistent view.
	 */
	public void renderWithLock(RenderCallback callback) {
		tilesLock.readLock().lock();
		try {
			callback.render(world);
		} finally {
			tilesLock.readLock().unlock();
		}
	}

	/**
	 * Thread-safe world tick operation - holds write lock for entire tick.
	 * Ensures no rendering happens during world modifications.
	 */
	public void tickWithLock(TickCallback callback) {
		tilesLock.writeLock().lock();
		try {
			callback.tick(world);
		} finally {
			tilesLock.writeLock().unlock();
		}
	}

	/**
	 * Thread-safe world update operation - holds write lock for packet-based
	 * updates.
	 * Ensures no rendering happens during world modifications from network packets.
	 */
	public void updateWithLock(UpdateCallback callback) {
		tilesLock.writeLock().lock();
		try {
			callback.update(world);
		} finally {
			tilesLock.writeLock().unlock();
		}
	}

	/**
	 * Get tile dimensions (thread-safe)
	 */
	public int getWidth() {
		tilesLock.readLock().lock();
		try {
			return world.tiles.length;
		} finally {
			tilesLock.readLock().unlock();
		}
	}

	/**
	 * Get tile dimensions (thread-safe)
	 */
	public int getHeight() {
		tilesLock.readLock().lock();
		try {
			return world.tiles[0].length;
		} finally {
			tilesLock.readLock().unlock();
		}
	}

	/**
	 * Callback interface for rendering operations
	 */
	@FunctionalInterface
	public interface RenderCallback {
		void render(World world);
	}

	/**
	 * Callback interface for tick operations
	 */
	@FunctionalInterface
	public interface TickCallback {
		void tick(World world);
	}

	/**
	 * Callback interface for update operations (packet-based)
	 */
	@FunctionalInterface
	public interface UpdateCallback {
		void update(World world);
	}
}
