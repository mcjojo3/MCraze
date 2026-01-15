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

package mc.sayda.mcraze.item;

public class Tool extends Item {
	private static final long serialVersionUID = 1L;

	public enum ToolType {
		Shovel, Pick, Axe, Sword, Hoe, Bow
	};

	public enum ToolPower {
		Wood, Stone, Gold, Iron, Diamond, Dev
	};

	public int totalUses;
	public int uses;
	public ToolType toolType;
	public ToolPower toolPower;
	public int attackDamage; // Damage dealt when used as a weapon

	public Tool(String ref, int size, String itemId, String name, String[][] template, int templateCount,
			ToolType toolType, ToolPower toolPower, boolean shapeless) {
		super(ref, size, itemId, name, template, templateCount, shapeless);
		if (toolPower == ToolPower.Wood) {
			totalUses = 32;
		} else if (toolPower == ToolPower.Stone) {
			totalUses = 64;
		} else if (toolPower == ToolPower.Iron) {
			totalUses = 128;
		} else if (toolPower == ToolPower.Diamond) {
			totalUses = 256;
		} else {
			totalUses = 1024;
		}
		this.toolPower = toolPower;
		this.toolType = toolType;

		// Set attack damage based on tool type and power
		// Pre-1.9 Minecraft damage values
		if (toolType == ToolType.Sword) {
			switch (toolPower) {
				case Wood:
					attackDamage = 4; // 2 hearts
					break;
				case Stone:
					attackDamage = 5; // 2.5 hearts
					break;
				case Iron:
					attackDamage = 6; // 3 hearts
					break;
				case Gold:
					attackDamage = 4; // 2 hearts (gold is weak but fast)
					break;
				case Diamond:
					attackDamage = 7; // 3.5 hearts
					break;
				default:
					attackDamage = 1;
			}
		} else {
			// Non-sword tools deal base damage of 1 (fists deal 1 too)
			attackDamage = 1;
		}
	}

	@Override
	public Tool clone() {
		Tool t = (Tool) super.clone();
		t.uses = 0;
		return t;
	}
}
