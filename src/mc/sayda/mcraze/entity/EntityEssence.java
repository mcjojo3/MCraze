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

import mc.sayda.mcraze.player.Player;
import mc.sayda.mcraze.server.SharedWorld;
import mc.sayda.mcraze.world.World;
import mc.sayda.mcraze.Constants;

/**
 * EntityEssence is a small floating orb that drops from entities.
 * It is attracted to Arcanist players and restores their essence when
 * collected.
 */
public class EntityEssence extends Entity {
    private static final long serialVersionUID = 1L;
    private final int value;
    private int lifeTicks = 0;
    private static final int MAX_LIFE = 600; // 30 seconds at 20fps

    private transient mc.sayda.mcraze.graphics.Sprite orbSprite;

    public EntityEssence(float x, float y, int value) {
        // gravityApplies = true (they fall slightly then hover/attract)
        super(null, true, x, y, 8, 8);
        this.value = value;

        try {
            this.orbSprite = mc.sayda.mcraze.graphics.SpriteStore.get()
                    .getSprite("assets/sprites/entities/essence_orb.png");
            this.sprite = this.orbSprite;
        } catch (Exception e) {
            // Ignore on server
        }
    }

    @Override
    public void draw(mc.sayda.mcraze.graphics.GraphicsHandler g, float cameraX, float cameraY, int screenWidth,
            int screenHeight, int tileSize) {
        if (orbSprite == null) {
            try {
                orbSprite = mc.sayda.mcraze.graphics.SpriteStore.get()
                        .getSprite("assets/sprites/entities/essence_orb.png");
                this.sprite = orbSprite;
            } catch (Exception e) {
                // Draw a simple circle if sprite fails
                mc.sayda.mcraze.util.Int2 pos = mc.sayda.mcraze.util.StockMethods.computeDrawLocationInPlace(cameraX,
                        cameraY, screenWidth, screenHeight, tileSize, x, y);
                if (mc.sayda.mcraze.util.StockMethods.onScreen) {
                    g.setColor(new mc.sayda.mcraze.graphics.Color(180, 40, 240));
                    g.fillOval(pos.x, pos.y, (int) (8 * ((float) tileSize / Constants.TILE_SIZE)),
                            (int) (8 * ((float) tileSize / Constants.TILE_SIZE)));
                }
                return;
            }
        }
        super.draw(g, cameraX, cameraY, screenWidth, screenHeight, tileSize);
    }

    @Override
    public void tick(SharedWorld world) {
        lifeTicks++;
        if (lifeTicks > MAX_LIFE) {
            this.dead = true;
            return;
        }

        // Attraction Logic: Find nearest Arcanist player
        Player nearest = null;
        float minDistSq = 100 * 100; // 100 block range

        for (mc.sayda.mcraze.server.PlayerConnection pc : world.getPlayers()) {
            Player p = pc.getPlayer();
            if (p != null && !p.dead && (p.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.ARCANIST
                    || p.selectedClass == mc.sayda.mcraze.player.specialization.PlayerClass.DRUID)) {
                float dx = p.x - this.x;
                float dy = p.y - this.y;
                float distSq = dx * dx + dy * dy;
                if (distSq < minDistSq) {
                    minDistSq = distSq;
                    nearest = p;
                }
            }
        }

        if (nearest != null) {
            // Move towards player
            float dx = nearest.x - this.x;
            float dy = nearest.y - this.y;
            float dist = (float) Math.sqrt(minDistSq);

            if (dist < 0.5f) {
                // Collected!
                collect(nearest);
                return;
            }

            // Fly speed increases as it gets closer
            float speed = 0.05f + (10.0f / (dist + 1.0f)) * 0.05f;
            this.dx = (dx / dist) * speed;
            this.dy = (dy / dist) * speed;

            // Disable gravity when attracting
            this.gravityApplies = false;
        } else {
            // Normal gravity/friction if no one is nearby
            this.gravityApplies = true;
            this.dx *= 0.9f;
        }
    }

    private void collect(Player player) {
        player.essence = Math.min(player.maxEssence, player.essence + value);
        this.dead = true;
    }

    @Override
    public void updatePosition(World world, int tileSize) {
        // Use basic position update
        super.updatePosition(world, tileSize);
    }
}
