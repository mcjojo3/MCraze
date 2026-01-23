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

package mc.sayda.mcraze.entity.buff;

/**
 * Defines the types of buffs/debuffs available in the game.
 */
public enum BuffType {
    REGENERATION, // Heals over time
    SPEED, // Increases movement speed
    SLOWNESS, // Decreases movement speed
    DAMAGE_RESISTANCE, // Reduces incoming damage
    STRENGTH, // Increases melee damage
    WEAKNESS, // Decreases melee damage
    STEADFAST, // Increases damage immunity duration and knockback resistance
    FRENZY, // Stacking damage bonus (Vanguard)
    FOCUS, // Ranged damage bonus (Marksman)
    WELL_FED // Stat boost from food (Chef)
}
