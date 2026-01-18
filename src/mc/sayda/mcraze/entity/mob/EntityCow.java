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

/**
 * Cow entity. Drops beef and leather.
 */
public class EntityCow extends BasicAnimal {
    private static final long serialVersionUID = 1L;

    public EntityCow(float x, float y) {
        // Cow: 32x32, 12 HP, 0.45 speed (slightly slower than pig/sheep)
        super(x, y, 32, 32, "assets/sprites/entities/cow.png", 12, 0.45f);
        this.hitPoints = 12;
    }
}
