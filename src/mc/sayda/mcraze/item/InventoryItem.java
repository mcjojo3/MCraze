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

import mc.sayda.mcraze.graphics.Color;
import mc.sayda.mcraze.graphics.GraphicsHandler;

public class InventoryItem implements java.io.Serializable, Cloneable {
	private static final long serialVersionUID = -2389571032163510795L;

	public final int maxCount = 64;
	public int count = 0;
	public Item item;

	public InventoryItem(Item item) {
		this.setItem(item);
	}

	// returns left overs
	public int add(Item item, int count) {
		if (this.isEmpty()) {
			this.setItem(item);
		}
		if (!this.getItem().itemId.equals(item.itemId)) {
			return count;
		}
		int maxCount = this.maxCount;
		if (this.getItem().getClass() == Tool.class) {
			maxCount = 1;
		}
		if (this.getCount() + count <= maxCount) {
			this.setCount(this.getCount() + count);
			return 0;
		} else {
			int leftOver = count - (maxCount - this.getCount());
			this.setCount(maxCount);
			return leftOver;
		}
	}

	// returns left overs
	public int remove(int count) {
		if (0 <= this.getCount() - count) {
			this.setCount(this.getCount() - count);
			return 0;
		} else if (this.getCount() == count) {
			this.setEmpty();
			return 0;
		} else {
			int leftOver = count - this.getCount();
			this.setEmpty();
			return leftOver;
		}
	}

	public void setEmpty() {
		this.setCount(0);
		this.setItem(null);
	}

	public boolean isEmpty() {
		return this.getCount() == 0 || this.getItem() == null;
	}

	public boolean isFull() {
		return getCount() >= maxCount;
	}

	public void stack(InventoryItem other) {
		if (other.getItem().getClass() != Tool.class) {
			int result = this.add(other.getItem(), other.getCount());
			other.remove(other.getCount() - result);
		}
	}

	public void draw(GraphicsHandler g, int x, int y, int tileSize) {
		if (this.getCount() <= 0) {
			return;
		}
		// Fix for invisible items: Reset color to white (opacity 1.0) before drawing
		g.setColor(Color.white);
		this.getItem().drawLayers(g, x, y, tileSize, tileSize);
		if (this.getCount() > 1) {
			g.setColor(Color.white);
			g.drawString("" + this.getCount(), x, y + tileSize / 2);
		}
		if (item.getClass() == Tool.class) {
			Tool tool = (Tool) item;
			if (tool.uses != 0) {
				int left = x + 2;
				int width = (int) (((float) (tool.totalUses - tool.uses) / tool.totalUses) * (tileSize));
				int top = y + tileSize - 4;
				int height = 2;

				// [NEW] Dark Green Background for durability bar
				g.setColor(new Color(0, 100, 0)); // Dark Green
				g.fillRect(left, top, tileSize - 4, height); // Full width background

				g.setColor(Color.green);
				g.fillRect(left, top, width, height); // Actual durability on top
			}
		}

		// [NEW] Draw mastercrafted indicator (Gold dot)
		if (item.isMastercrafted) {
			// Draw gold square in top-right
			g.setColor(new Color(255, 215, 0)); // Gold
			int dotSize = 4;
			g.fillRect(x + tileSize - dotSize, y, dotSize, dotSize);
			// Optional: slight border for visibility
			g.setColor(new Color(180, 140, 0)); // Darker gold
			g.drawRect(x + tileSize - dotSize, y, dotSize, dotSize);
		}
	}

	public void setItem(Item item) {
		this.item = item;
	}

	public Item getItem() {
		return item;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public int getCount() {
		return count;
	}

	@Override
	public InventoryItem clone() {
		try {
			InventoryItem cloned = (InventoryItem) super.clone();
			if (this.item != null) {
				// Note: Item.clone() is shallow if not overridden.
				// However, most Items are immutable flyweights except Tools.
				if (this.item instanceof Tool) {
					// Tool must be cloned to preserve uses
					cloned.item = (Item) this.item.clone();
				} else {
					// Regular items (resources) are immutable definitions usually?
					// Wait, Item.x/y are mutable.
					cloned.item = (Item) this.item.clone();
				}
			}
			return cloned;
		} catch (CloneNotSupportedException e) {
			throw new AssertionError();
		}
	}
}
