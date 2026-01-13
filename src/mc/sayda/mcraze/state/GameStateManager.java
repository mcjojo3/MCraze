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

package mc.sayda.mcraze.state;

import mc.sayda.mcraze.logging.GameLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages game state transitions with validation and lifecycle hooks.
 * Ensures valid state transitions and provides thread-safe access.
 */
public class GameStateManager {
    private GameState currentState = GameState.BOOT;
    private final Map<GameState, StateHandler> handlers = new HashMap<>();
    private final GameLogger logger = GameLogger.get();

    /**
     * Get the current game state
     * 
     * @return current state
     */
    public synchronized GameState getState() {
        return currentState;
    }

    /**
     * Register a handler for a specific state
     * 
     * @param state   the state to handle
     * @param handler the handler implementation
     */
    public void registerHandler(GameState state, StateHandler handler) {
        handlers.put(state, handler);
    }

    /**
     * Transition to a new state with validation
     * 
     * @param newState the state to transition to
     * @return true if transition succeeded
     * @throws IllegalStateException if transition is invalid
     */
    public synchronized boolean transitionTo(GameState newState) {
        if (currentState == newState) {
            if (logger != null) {
                logger.debug("GameStateManager: Already in state " + newState);
            }
            return true;
        }

        // Validate transition
        if (!isValidTransition(currentState, newState)) {
            String msg = "Invalid state transition: " + currentState + " -> " + newState;
            if (logger != null) {
                logger.error(msg);
            }
            throw new IllegalStateException(msg);
        }

        if (logger != null) {
            logger.info("GameStateManager: Transitioning " + currentState + " -> " + newState);
        }

        // Call exit handler for old state
        StateHandler oldHandler = handlers.get(currentState);
        if (oldHandler != null) {
            try {
                oldHandler.onExit();
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("GameStateManager: Error in exit handler for " + currentState, e);
                }
            }
        }

        // Transition
        GameState oldState = currentState;
        currentState = newState;

        // Call enter handler for new state
        StateHandler newHandler = handlers.get(newState);
        if (newHandler != null) {
            try {
                newHandler.onEnter();
            } catch (Exception e) {
                if (logger != null) {
                    logger.error("GameStateManager: Error in enter handler for " + newState, e);
                }
                // Rollback
                currentState = oldState;
                throw new RuntimeException("Failed to enter state " + newState, e);
            }
        }

        return true;
    }

    /**
     * Validate if a state transition is allowed
     * 
     * @param from current state
     * @param to   target state
     * @return true if transition is valid
     */
    private boolean isValidTransition(GameState from, GameState to) {
        // SHUTDOWN can be reached from any state
        if (to == GameState.SHUTDOWN) {
            return true;
        }

        // Valid transitions based on lifecycle
        switch (from) {
            case BOOT:
                return to == GameState.LOGIN || to == GameState.MENU;

            case LOGIN:
                return to == GameState.MENU;

            case MENU:
                return to == GameState.LOADING || to == GameState.LOGIN; // Allow logout

            case LOADING:
                return to == GameState.IN_GAME || to == GameState.MENU; // Can cancel loading

            case IN_GAME:
                return to == GameState.PAUSED || to == GameState.MENU;

            case PAUSED:
                return to == GameState.IN_GAME || to == GameState.MENU;

            case SHUTDOWN:
                return false; // Cannot transition from SHUTDOWN

            default:
                return false;
        }
    }

    /**
     * Forcefully set state without validation (use with caution!)
     * 
     * @param state the state to set
     */
    public synchronized void forceState(GameState state) {
        if (logger != null) {
            logger.warn("GameStateManager: Force setting state to " + state + " (bypassing validation)");
        }
        currentState = state;
    }

    /**
     * Check if current state allows gameplay
     */
    public boolean isGameplayState() {
        return getState().isGameplayState();
    }

    /**
     * Check if current state allows server ticking
     */
    public boolean isServerActive() {
        return getState().isServerActive();
    }

    /**
     * Check if current state allows packet processing
     */
    public boolean isPacketProcessing() {
        return getState().isPacketProcessing();
    }
}
