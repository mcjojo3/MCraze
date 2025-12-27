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

package mc.sayda.mcraze.state;

/**
 * Interface for handling state lifecycle events.
 * Implement this to receive callbacks when entering/exiting a state.
 */
public interface StateHandler {
    /**
     * Called when entering this state
     */
    void onEnter();

    /**
     * Called when exiting this state
     */
    void onExit();

    /**
     * Called each game tick while in this state (optional)
     */
    default void tick() {
        // Optional override
    }
}
