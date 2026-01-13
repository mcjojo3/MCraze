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

package mc.sayda.mcraze.entity;

import java.util.Random;
import mc.sayda.mcraze.world.World;

public class EntitySheep extends LivingEntity {
    private static final long serialVersionUID = 1L;

    private int actionTimer = 0;
    private Random random = new Random();

    // Actions
    private static final int ACTION_IDLE = 0;
    private static final int ACTION_WALK_LEFT = 1;
    private static final int ACTION_WALK_RIGHT = 2;
    private int currentAction = ACTION_IDLE;

    public EntitySheep(float x, float y) {
        // Sheep are 32x32 pixels
        super(true, x, y, 32, 32);
        // Load sheep sprite
        this.sprite = mc.sayda.mcraze.SpriteStore.get().getSprite("sprites/entities/sheep.png");

        // Set initial health
        this.hitPoints = 20; // 2x original health to make tool tiers more noticeable

        // Slower than player
        this.speedMultiplier = 0.5f;
    }

    @Override
    public void updatePosition(World world, int tileSize) {
        // Simple AI: Randomly switch actions
        actionTimer--;

        if (actionTimer <= 0) {
            // Pick new action
            float roll = random.nextFloat();

            if (roll < 0.6f) {
                currentAction = ACTION_IDLE;
                actionTimer = 60 + random.nextInt(120); // Idle for 1-3 seconds
            } else if (roll < 0.8f) {
                currentAction = ACTION_WALK_LEFT;
                actionTimer = 20 + random.nextInt(60); // Walk for 0.3-1.3 seconds
            } else {
                currentAction = ACTION_WALK_RIGHT;
                actionTimer = 20 + random.nextInt(60);
            }

            // 10% chance to jump if walking
            if (currentAction != ACTION_IDLE && random.nextFloat() < 0.1f) {
                this.jumping = false; // Reset to allow jump
                this.jump(world, tileSize);
            }
        }

        // Apply action
        switch (currentAction) {
            case ACTION_IDLE:
                stopLeft();
                stopRight();
                break;
            case ACTION_WALK_LEFT:
                startLeft(false);
                break;
            case ACTION_WALK_RIGHT:
                startRight(false);
                break;
        }

        // Apply physics/movement
        super.updatePosition(world, tileSize);
    }

    @Override
    public int getMaxHP() {
        return 10;
    }

    @Override
    public void draw(mc.sayda.mcraze.GraphicsHandler g, float cameraX, float cameraY, int screenWidth,
            int screenHeight, int tileSize) {
        mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(cameraX, cameraY,
                screenWidth,
                screenHeight, tileSize, x, y);
        if (mc.sayda.mcraze.util.StockMethods.onScreen) {
            // Apply red tint for damage indication
            if (damageFlashTicks > 0) {
                if (facingRight) {
                    g.drawImage(sprite, pos.x, pos.y, widthPX, heightPX, new mc.sayda.mcraze.Color(255, 0, 0, 128));
                } else {
                    // Flip horizontally
                    g.drawImage(sprite, pos.x + widthPX, pos.y, -widthPX, heightPX,
                            new mc.sayda.mcraze.Color(255, 0, 0, 128));
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
