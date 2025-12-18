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

<<<<<<<< Updated upstream:src/com/github/jleahey/minicraft/Item.java
package com.github.jleahey.minicraft;
========
package mc.sayda.mcraze.item;

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.util.Template;
>>>>>>>> Stashed changes:src/mc/sayda/mcraze/item/Item.java

public class Item extends Entity implements Cloneable {
	private static final long serialVersionUID = 1L;

	public String itemId;
	public String name;
	public Template template;

	public Item(String ref, int size, String itemId, String name, String[][] template, int templateCount) {
		super(ref, true, 0, 0, size, size);
		this.template = new Template(template, templateCount);
		this.itemId = itemId;
		this.name = name;
	}
	
	@Override
	public Item clone() {
		try {
			return (Item) super.clone();
		} catch (CloneNotSupportedException e) {
			return null; // should never happen
		}
	}
	
}
