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

package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.ui.component.*;
import mc.sayda.mcraze.ui.menu.*;
import mc.sayda.mcraze.ui.screen.*;
import mc.sayda.mcraze.ui.container.*;
import mc.sayda.mcraze.graphics.*;
import mc.sayda.mcraze.player.*;
import mc.sayda.mcraze.player.data.*;
import mc.sayda.mcraze.world.*;
import mc.sayda.mcraze.world.tile.*;
import mc.sayda.mcraze.world.storage.*;

import java.util.ArrayList;
import java.util.List;

import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.network.Connection;
import mc.sayda.mcraze.network.packet.PacketInventoryDrag;
import mc.sayda.mcraze.util.Int2;

/**
 * Centralized drag-to-split handler for inventory UIs.
 * 
 * All container UIs (Inventory, ChestUI, FurnaceUI) use this shared class
 * to handle drag detection, state tracking, and packet sending.
 * 
 * Usage:
 * 1. Create one DragHandler per UI instance
 * 2. Call update() each frame with mouse state and current slot
 * 3. Call checkEndDrag() when mouse is released
 * 4. Call cancelDrag() when leaving bounds without completing
 * 
 * NOTE: Left-drag (even split) is currently DISABLED. Only right-drag
 * (place 1 per slot) is active.
 */
public class DragHandler {

    // Drag state
    private boolean isDragging = false;
    private boolean isLeftDrag = false;
    private List<Int2> dragSlots = new ArrayList<>();

    // Action cooldown to prevent spam
    private long lastActionTime = 0;
    private Int2 lastActionSlot = null;
    private static final long ACTION_COOLDOWN_MS = 50;

    // Container type for packet
    private PacketInventoryDrag.ContainerType containerType;

    /**
     * Create a DragHandler for a specific container type.
     * 
     * @param containerType The type of container (PLAYER_INVENTORY, CHEST, FURNACE)
     */
    public DragHandler(PacketInventoryDrag.ContainerType containerType) {
        this.containerType = containerType;
    }

    /**
     * Update drag state based on current mouse input.
     * Call this every frame when the UI is visible.
     * 
     * @param position    Current slot position (null if not over a slot)
     * @param leftHeld    Whether left mouse button is held
     * @param rightHeld   Whether right mouse button is held
     * @param holdingItem The item currently held by cursor (for checking if
     *                    dragging is possible)
     * @return true if the drag consumed the event (UI should not process further)
     */
    public boolean update(Int2 position, boolean leftHeld, boolean rightHeld, InventoryItem holdingItem) {
        // Can only drag if holding items and a button is held
        if (holdingItem == null || holdingItem.isEmpty() || (!leftHeld && !rightHeld)) {
            return false;
        }

        if (position == null) {
            return false;
        }

        // Already dragging - add new slots (interpolated)
        if (isDragging) {
            interpolateAndAdd(position);
            return true; // Consume event during drag
        }

        // Have a potential start slot - check if moved to different slot
        // "Start Drag" Transition
        if (dragSlots.size() > 0) {
            Int2 firstSlot = dragSlots.get(0);
            if (position.x != firstSlot.x || position.y != firstSlot.y) {
                System.out.println("DEBUG DragHandler: Starting drag! First: " + firstSlot.x + "," + firstSlot.y
                        + " New: " + position.x + "," + position.y);

                isDragging = true;
                isLeftDrag = leftHeld; // Determine drag type from button held

                // CRITICAL FIX: Interpolate from first slot to new position
                // Fixes skipping slots when mouse is flicked immediately after click
                interpolateAndAdd(position);

                return true;
            }
        } else {
            // First slot - track it but don't start drag yet
            dragSlots.clear();
            dragSlots.add(new Int2(position.x, position.y));
        }

        return false;
    }

    /**
     * Check if drag should end due to mouse release.
     * Call this every frame.
     * 
     * @param leftHeld   Whether left mouse button is held
     * @param rightHeld  Whether right mouse button is held
     * @param connection Connection to send packet to
     * @return true if drag ended and packet was sent
     */
    public boolean checkEndDrag(boolean leftHeld, boolean rightHeld, Connection connection) {
        if (isDragging && !leftHeld && !rightHeld) {
            System.out.println("DEBUG DragHandler: Mouse released, ending drag. Slots: " + dragSlots.size());
            endDrag(connection);
            return true;
        }
        return false;
    }

    /**
     * Clear potential drag slots if button released without starting a drag.
     * Call this when mouse released but not currently dragging.
     * 
     * @param leftHeld  Whether left mouse button is held
     * @param rightHeld Whether right mouse button is held
     */
    public void clearPotentialDrag(boolean leftHeld, boolean rightHeld) {
        if (!leftHeld && !rightHeld && dragSlots.size() > 0 && !isDragging) {
            dragSlots.clear();
        }
    }

    /**
     * Helper to interpolate slots between last registered slot and new position.
     * Prevents skipping slots during fast mouse movement.
     */
    private void interpolateAndAdd(Int2 targetPos) {
        if (dragSlots.isEmpty()) {
            addSlotIfNotPresent(targetPos);
            return;
        }

        Int2 lastSlot = dragSlots.get(dragSlots.size() - 1);
        int dx = targetPos.x - lastSlot.x;
        int dy = targetPos.y - lastSlot.y;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));

        // If we skipped slots (moved more than 1 unit), fill them in
        if (steps > 1) {
            for (int i = 1; i < steps; i++) {
                int interpX = lastSlot.x + (dx * i) / steps;
                int interpY = lastSlot.y + (dy * i) / steps;
                addSlotIfNotPresent(new Int2(interpX, interpY));
            }
        }

        // Add the final target slot
        addSlotIfNotPresent(targetPos);
    }

    private void addSlotIfNotPresent(Int2 pos) {
        boolean alreadyAdded = false;
        for (Int2 slot : dragSlots) {
            if (slot.x == pos.x && slot.y == pos.y) {
                alreadyAdded = true;
                break;
            }
        }
        if (!alreadyAdded) {
            System.out.println("DEBUG DragHandler: Adding slot: " + pos.x + "," + pos.y);
            dragSlots.add(new Int2(pos.x, pos.y));
        }
    }

    /**
     * Cancel any active or potential drag (e.g., when mouse leaves UI bounds).
     * 
     * @param connection Connection to send packet to (if drag was active)
     */
    public void cancelDrag(Connection connection) {
        if (isDragging) {
            System.out.println("DEBUG DragHandler: Canceling drag (left bounds). Slots: " + dragSlots.size());
            endDrag(connection);
        } else {
            dragSlots.clear();
        }
    }

    /**
     * End the drag operation and send packet to server.
     */
    private void endDrag(Connection connection) {
        if (connection != null && dragSlots.size() > 0) {
            // Server skips index 0 (the source slot where the drag started)
            // So we send slots as-is: [Start, Next1, Next2, ...]
            // Server processes: [Next1, Next2, ...] (skipping Start)
            PacketInventoryDrag packet = new PacketInventoryDrag(
                    dragSlots, isLeftDrag, containerType);
            connection.sendPacket(packet);
        }
        isDragging = false;
        dragSlots.clear();
    }

    /**
     * Check if currently in an active drag operation.
     */
    public boolean isDragging() {
        return isDragging;
    }

    /**
     * Check if an action to the given slot is on cooldown.
     * 
     * @param position Slot position to check
     * @return true if action should be blocked due to cooldown
     */
    public boolean isOnCooldown(Int2 position) {
        if (position == null)
            return false;

        long currentTime = System.currentTimeMillis();
        // REMOVED: Cooldown checked here was interfering with fast drags.
        // Safety against "accidental interactions after open" is handled by
        // Client.UI_GRACE_PERIOD_MS (200ms).
        return false;
    }

    /**
     * Record an action to update cooldown tracking.
     * Call this after successfully sending an action packet.
     * 
     * @param position Slot position that was acted upon
     */
    public void recordAction(Int2 position) {
        if (position != null) {
            lastActionTime = System.currentTimeMillis();
            lastActionSlot = new Int2(position.x, position.y);
        }
    }

    /**
     * Set the container type (for when reusing handler or changing context).
     */
    public void setContainerType(PacketInventoryDrag.ContainerType containerType) {
        this.containerType = containerType;
    }

    /**
     * Get the current container type.
     */
    public PacketInventoryDrag.ContainerType getContainerType() {
        return containerType;
    }
}
