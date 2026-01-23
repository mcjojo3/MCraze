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

package mc.sayda.mcraze.server;

import mc.sayda.mcraze.logging.GameLogger;
import mc.sayda.mcraze.state.GameState;
import mc.sayda.mcraze.state.GameStateManager;

/**
 * Dedicated thread for server tick logic at 60 TPS.
 * Separates server updates from client rendering for stable performance.
 *
 * Thread Model:
 * - This thread is the ONLY writer for world/entity state
 * - Main thread (Client rendering) reads using thread-safe accessors
 * - State-driven: Only ticks when in IN_GAME or PAUSED states
 */
public class ServerTickThread extends Thread {
	private final SharedWorld sharedWorld;
	private final GameStateManager stateManager;
	private volatile boolean running = true;
	private long tickCount = 0;
	private final mc.sayda.mcraze.logging.GameLogger logger = mc.sayda.mcraze.logging.GameLogger.get();

	// Target 60 TPS (16.67ms per tick)
	private static final long TARGET_TICK_TIME_MS = 16; // ~60 TPS
	private static final long TARGET_TICK_TIME_NS = TARGET_TICK_TIME_MS * 1_000_000;

	// Performance tracking
	private long lastTickTime = System.nanoTime();
	private long totalTickTime = 0;
	private int ticksSinceLastReport = 0;

	public ServerTickThread(SharedWorld sharedWorld, GameStateManager stateManager) {
		super("ServerTickThread");
		this.sharedWorld = sharedWorld;
		this.stateManager = stateManager;
		setDaemon(true); // Don't prevent JVM shutdown

		if (logger != null) {
			logger.info("ServerTickThread: Created with target " + (1000 / TARGET_TICK_TIME_MS) + " TPS");
		}
	}

	@Override
	public void run() {
		if (logger != null) {
			logger.info("ServerTickThread: Starting tick loop");
		}

		while (running) {
			long tickStart = System.nanoTime();

			try {
				// Get current game state
				GameState state = stateManager.getState();

				// CRITICAL: Only tick when in appropriate states
				if (state == GameState.IN_GAME || state == GameState.PAUSED) {
					if (sharedWorld != null && sharedWorld.isRunning()) {
						// Perform server tick (world updates, entity physics, packet processing)
						sharedWorld.tick();
						tickCount++;

						// Track performance
						long tickEnd = System.nanoTime();
						long tickDuration = tickEnd - tickStart;
						totalTickTime += tickDuration;
						ticksSinceLastReport++;

						// Log performance every 60 ticks (1 second at 60 TPS)
						if (logger != null && ticksSinceLastReport >= 60) {
							long avgTickTime = totalTickTime / ticksSinceLastReport;
							double avgTPS = 1_000_000_000.0 / avgTickTime;
							logger.debug(String.format("ServerTickThread: Avg tick time: %.2fms (%.1f TPS)",
									avgTickTime / 1_000_000.0, avgTPS));
							totalTickTime = 0;
							ticksSinceLastReport = 0;
						}

						// Calculate sleep time to maintain 60 TPS
						long sleepTime = TARGET_TICK_TIME_NS - tickDuration;
						if (sleepTime > 0) {
							Thread.sleep(sleepTime / 1_000_000, (int) (sleepTime % 1_000_000));
						} else if (logger != null && tickCount % 60 == 0) {
							// Warn if tick is taking too long
							logger.warn(String.format("ServerTickThread: Tick overrun by %.2fms",
									-sleepTime / 1_000_000.0));
						}
					} else {
						// SharedWorld not running, sleep briefly
						Thread.sleep(100);
					}
				} else {
					// Not in game, sleep longer to save CPU
					Thread.sleep(100);
					if (logger != null && tickCount > 0 && tickCount % 10 == 0) {
						logger.debug("ServerTickThread: Waiting for IN_GAME state (current: " + state + ")");
					}
				}

				lastTickTime = System.nanoTime();

			} catch (InterruptedException e) {
				if (logger != null) {
					logger.info("ServerTickThread: Interrupted, shutting down");
				}
				running = false;
				break;
			} catch (Exception e) {
				if (logger != null) {
					logger.error("ServerTickThread: Unexpected error in tick loop: " + e.getMessage());
					e.printStackTrace();
				}
				// Continue running despite error
			}
		}

		if (logger != null) {
			logger.info("ServerTickThread: Stopped after " + tickCount + " ticks");
		}
	}

	/**
	 * Signal the thread to stop gracefully
	 */
	public void shutdown() {
		if (logger != null) {
			logger.info("ServerTickThread: Shutdown requested");
		}
		running = false;
		interrupt(); // Wake up if sleeping
	}

	/**
	 * Check if the thread is still running
	 */
	public boolean isRunning() {
		return running && isAlive();
	}

	/**
	 * Get the current tick count
	 */
	public long getTickCount() {
		return tickCount;
	}
}
