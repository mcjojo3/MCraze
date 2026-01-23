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

import mc.sayda.mcraze.entity.Entity;
import mc.sayda.mcraze.util.Template;

public class Item extends Entity implements Cloneable {
	private static final long serialVersionUID = 1L;

	/**
	 * Represents a single visual layer of an item, allowing for tinted overlays
	 * (e.g. potion bottles with colored liquid).
	 */
	public static class SpriteLayer implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		public String spriteRef;
		public mc.sayda.mcraze.graphics.Color tint;
		public transient mc.sayda.mcraze.graphics.Sprite sprite;

		public SpriteLayer(String spriteRef, mc.sayda.mcraze.graphics.Color tint) {
			this.spriteRef = spriteRef;
			this.tint = tint;
		}
	}

	public java.util.List<SpriteLayer> layers = new java.util.ArrayList<>();

	/**
	 * Renders all layers of the item in order.
	 * If no layers are defined, falls back to the default sprite.
	 */
	public void drawLayers(mc.sayda.mcraze.graphics.GraphicsHandler g, int x, int y, int width, int height) {
		drawLayers(g, x, y, width, height, null);
	}

	public void drawLayers(mc.sayda.mcraze.graphics.GraphicsHandler g, int x, int y, int width, int height,
			mc.sayda.mcraze.graphics.Color tintOverride) {
		if (layers == null || layers.isEmpty()) {
			if (sprite != null) {
				if (tintOverride != null) {
					sprite.draw(g, x, y, width, height, tintOverride);
				} else {
					sprite.draw(g, x, y, width, height);
				}
			}
			return;
		}

		for (SpriteLayer layer : layers) {
			if (layer.sprite == null && layer.spriteRef != null) {
				layer.sprite = mc.sayda.mcraze.graphics.SpriteStore.get().getSprite(layer.spriteRef, layer.tint);
			}
			if (layer.sprite != null) {
				if (tintOverride != null) {
					layer.sprite.draw(g, x, y, width, height, tintOverride);
				} else {
					layer.sprite.draw(g, x, y, width, height);
				}
			}
		}
	}

	public String itemId;
	public String name;
	public Template template;
	// Tool requirements for breaking/harvesting this item (if it's a block)
	public Tool.ToolType requiredToolType;
	public Tool.ToolPower requiredToolPower;

	// Class Requirement (null = available to all)
	public mc.sayda.mcraze.player.specialization.PlayerClass requiredClass;
	public mc.sayda.mcraze.player.specialization.SpecializationPath requiredPath; // [NEW] Path restriction
	public String hexColor; // [NEW] Optional hex color for sprite tinting (potions)
	public String remainsItemId; // [NEW] Item ID of what remains after use (e.g. "bucket", "bottle")
	public boolean isGrowable = false; // [NEW] Can it be planted in a pot?
	public int growthTime = 0; // [NEW] Ticks needed to grow

	// Fuel properties (null/0 if not fuel)
	public int fuelBurnTime = 0; // In ticks

	// Item despawn timer (prevents entity accumulation in world)
	private long spawnTime;
	private static final long DESPAWN_TIME_MS = 300000; // 5 minutes

	public Item(String ref, int size, String itemId, String name, String[][] template, int templateCount,
			boolean shapeless) {
		super(ref, true, 0, 0, size, size);
		this.template = new Template(template, templateCount, shapeless);
		this.itemId = itemId;
		this.name = name;
		this.spawnTime = System.currentTimeMillis();
		this.requiredClass = null; // Default: No class restriction
		this.requiredPath = null; // Default: No path restriction
		this.isMastercrafted = false; // Default: Standard item
	}

	// [NEW] Mastercrafted status (Visuals + Persistence)
	public boolean isMastercrafted = false;

	@Override
	public Item clone() {
		try {
			Item cloned = (Item) super.clone();
			// Reset spawn time for cloned items (e.g., when picking up from inventory)
			cloned.spawnTime = System.currentTimeMillis();
			// Copy complex/new fields
			cloned.remainsItemId = this.remainsItemId;
			cloned.isGrowable = this.isGrowable;
			cloned.growthTime = this.growthTime;
			// isMastercrafted is preserved by super.clone()
			return cloned;
		} catch (CloneNotSupportedException e) {
			return null; // should never happen
		}
	}

	/**
	 * Check if item should despawn (prevents entity accumulation)
	 * Items despawn after 5 minutes to prevent lag from dropped item accumulation
	 */
	public boolean shouldDespawn() {
		return System.currentTimeMillis() - spawnTime > DESPAWN_TIME_MS;
	}

}
