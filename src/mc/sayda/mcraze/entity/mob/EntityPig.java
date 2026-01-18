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

public class EntityPig extends BasicAnimal {
    private static final long serialVersionUID = 1L;

    public EntityPig(float x, float y) {
        // Pig: 32x32, 10 HP, 0.5 speed (same as sheep for now)
        super(x, y, 32, 32, "assets/sprites/entities/pig.png", 10, 0.5f);
        this.hitPoints = 10;
    }
}
