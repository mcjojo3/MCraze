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

package mc.sayda.mcraze.entity.mob;

import mc.sayda.mcraze.entity.LivingEntity;
import mc.sayda.mcraze.graphics.SpriteStore;
import mc.sayda.mcraze.world.World;
import java.util.Random;

/**
 * Base class for passive farm animals (Sheep, Pig, Cow).
 * Handles common AI (wandering), rendering, and sprite management.
 */
public abstract class BasicAnimal extends LivingEntity {
    private static final long serialVersionUID = 1L;

    protected int actionTimer = 0;
    protected Random random = new Random();

    // AI States
    protected static final int ACTION_IDLE = 0;
    protected static final int ACTION_WALK_LEFT = 1;
    protected static final int ACTION_WALK_RIGHT = 2;
    protected int currentAction = ACTION_IDLE;

    public BasicAnimal(float x, float y, int width, int height, String spritePath, int maxHP, float speedMult) {
        super(true, x, y, width, height);

        // Load sprite
        try {
            this.sprite = SpriteStore.get().getSprite(spritePath);
        } catch (Exception e) {
            // e.printStackTrace();
            System.err.println("Failed to load sprite: " + spritePath);
        }

        this.maxHP = maxHP;
        this.hitPoints = maxHP; // Start at full health
        this.speedMultiplier = speedMult;
    }

    @Override
    public int getMaxHP() {
        return maxHP;
    }

    @Override
    public void updatePosition(World world, int tileSize) {
        if (!skipAI()) {
            updateAI(world, tileSize);
            applyMovementFromAction();
        }

        // Allow subclasses to hook in before physics
        customUpdatePosition(world, tileSize);

        // Apply physics/movement
        super.updatePosition(world, tileSize);
    }

    /**
     * Hook for subclasses to disable basic AI (e.g. Wolf when Angry)
     */
    protected boolean skipAI() {
        return false;
    }

    /**
     * Hook for subclasses to add custom logic before physics
     */
    protected void customUpdatePosition(World world, int tileSize) {
    }

    protected void updateAI(World world, int tileSize) {
        // Simple AI: Randomly switch actions
        actionTimer--;

        if (actionTimer <= 0) {
            // Pick new action
            float roll = random.nextFloat();

            if (roll < 0.6f) { // 60% chance to idle
                currentAction = ACTION_IDLE;
                actionTimer = 60 + random.nextInt(120); // Idle for 1-3 seconds
            } else if (roll < 0.8f) { // 20% walk left
                currentAction = ACTION_WALK_LEFT;
                actionTimer = 20 + random.nextInt(60); // Walk for 0.3-1.3 seconds
            } else { // 20% walk right
                currentAction = ACTION_WALK_RIGHT;
                actionTimer = 20 + random.nextInt(60);
            }

            // 10% chance to jump if walking (obstacle jumping)
            if (currentAction != ACTION_IDLE && random.nextFloat() < 0.1f) {
                this.jumping = false; // Reset to allow jump
                this.jump(world, tileSize);
            }
        }
    }

    protected void applyMovementFromAction() {
        // Apply action
        switch (currentAction) {
            case ACTION_IDLE:
                stopLeft();
                stopRight();
                break;
            case ACTION_WALK_LEFT:
                startLeft(false); // Walk at full speed (speedMultiplier applied in LivingEntity)
                break;
            case ACTION_WALK_RIGHT:
                startRight(false);
                break;
        }
    }

    @Override
    public void draw(mc.sayda.mcraze.graphics.GraphicsHandler g, float cameraX, float cameraY, int screenWidth,
            int screenHeight, int tileSize) {
        mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(cameraX, cameraY,
                screenWidth,
                screenHeight, tileSize, x, y);

        if (mc.sayda.mcraze.util.StockMethods.onScreen) {
            // Apply red tint for damage indication
            if (damageFlashTicks > 0) {
                if (facingRight) {
                    g.drawImage(sprite, pos.x, pos.y, widthPX, heightPX,
                            new mc.sayda.mcraze.graphics.Color(255, 0, 0, 128));
                } else {
                    // Flip horizontally
                    g.drawImage(sprite, pos.x + widthPX, pos.y, -widthPX, heightPX,
                            new mc.sayda.mcraze.graphics.Color(255, 0, 0, 128));
                }
            } else {
                if (facingRight) {
                    sprite.draw(g, pos.x, pos.y, widthPX, heightPX);
                } else {
                    // Flip horizontally
                    sprite.draw(g, pos.x + widthPX, pos.y, -widthPX, heightPX);
                }
            }
        }
    }
}
