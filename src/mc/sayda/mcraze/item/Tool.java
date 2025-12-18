/*
 * Copyright 2012 Jonathan Leahey
 * 
 * This file is part of Minicraft
 * 
 * Minicraft is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * Minicraft is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Minicraft. If not, see http://www.gnu.org/licenses/.
 */

<<<<<<<< Updated upstream:src/com/github/jleahey/minicraft/Tool.java
package com.github.jleahey.minicraft;
========
package mc.sayda.mcraze.item;
>>>>>>>> Stashed changes:src/mc/sayda/mcraze/item/Tool.java

public class Tool extends Item {
	private static final long serialVersionUID = 1L;
	
	public enum ToolType {
		Shovel, Pick, Axe, Sword
	};
	
	public enum ToolPower {
		Wood, Stone, Gold, Iron, Diamond
	};
	
	int totalUses;
	int uses;
	public ToolType toolType;
	public ToolPower toolPower;
	
	public Tool(String ref, int size, String itemId, String name, String[][] template, int templateCount,
			ToolType toolType, ToolPower toolPower) {
		super(ref, size, itemId, name, template, templateCount);
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
	}
	
	@Override
	public Tool clone() {
		Tool t = (Tool) super.clone();
		t.uses = 0;
		return t;
	}
}
