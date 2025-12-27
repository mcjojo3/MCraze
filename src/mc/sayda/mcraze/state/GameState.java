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
 * Explicit game state enum to replace boolean flags.
 * Defines the complete lifecycle of the game from boot to shutdown.
 */
public enum GameState {
    /**
     * Initial boot state - initializing graphics, loading assets
     */
    BOOT,

    /**
     * Login screen showing (if not DEBUG mode)
     * Waiting for user authentication
     */
    LOGIN,

    /**
     * Main menu - world selection, multiplayer join, settings
     */
    MENU,

    /**
     * Loading world data, generating terrain, waiting for packets
     * Shows loading screen with progress
     */
    LOADING,

    /**
     * Playing the game - player exists, world exists, server ticking
     */
    IN_GAME,

    /**
     * Pause menu open (substate of IN_GAME)
     * Server continues ticking, client shows pause UI
     */
    PAUSED,

    /**
     * Cleanup and exit
     */
    SHUTDOWN;

    /**
     * Check if state allows gameplay (player/world exist)
     */
    public boolean isGameplayState() {
        return this == IN_GAME || this == PAUSED;
    }

    /**
     * Check if state allows server ticking
     */
    public boolean isServerActive() {
        return this == IN_GAME || this == PAUSED;
    }

    /**
     * Check if state allows packet processing
     */
    public boolean isPacketProcessing() {
        return this == LOADING || this == IN_GAME || this == PAUSED;
    }

    /**
     * Check if state shows a menu
     */
    public boolean isMenuState() {
        return this == LOGIN || this == MENU || this == PAUSED;
    }
}
