/*
 * Copyright 2025 SaydaGames (mc_jojo3)
 *
 * This file is part of MCraze
 *
 * MCraze is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * MCraze is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MCraze. If not, see http://www.gnu.org/licenses/.
 */

package mc.sayda.mcraze.ui.component;

import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.Sprite;
import mc.sayda.mcraze.ui.layout.Anchor;
import mc.sayda.mcraze.ui.layout.LayoutEngine;
import mc.sayda.mcraze.ui.layout.LayoutParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Container panel for UI components.
 * Can render an optional background sprite (9-patch or regular) and contains child components.
 *
 * Example usage:
 * <pre>
 * UIPanel panel = new UIPanel(200, 150)
 *     .setBackground(panelSprite, true)  // 9-patch background
 *     .setLayoutParams(new LayoutParams(Anchor.CENTER))
 *     .add(childComponent);
 *
 * panel.updateLayout(screenWidth, screenHeight);
 * panel.draw(g);
 * </pre>
 */
public class UIPanel implements UIComponent {
	private int width;
	private int height;
	private int x;
	private int y;
	private boolean visible = true;

	private LayoutParams layoutParams = new LayoutParams();

	private Sprite backgroundSprite;
	private NinePatch ninePatch;
	private boolean useNinePatch = false;

	private List<UIComponent> children = new ArrayList<>();

	/**
	 * Create a panel with specific dimensions
	 */
	public UIPanel(int width, int height) {
		this.width = width;
		this.height = height;
	}

	/**
	 * Set background sprite (optional).
	 *
	 * @param sprite Sprite to use as background
	 * @param useNinePatch If true, render as 9-patch (expandable)
	 * @return This panel for chaining
	 */
	public UIPanel setBackground(Sprite sprite, boolean useNinePatch) {
		this.backgroundSprite = sprite;
		this.useNinePatch = useNinePatch;
		if (useNinePatch && sprite != null) {
			this.ninePatch = new NinePatch(sprite, 4);  // 4px corners
		}
		return this;
	}

	/**
	 * Add a child component to this panel.
	 *
	 * @param child Child component to add
	 * @return This panel for chaining
	 */
	public UIPanel add(UIComponent child) {
		children.add(child);
		return this;
	}

	/**
	 * Set layout params (fluent API).
	 *
	 * @param params Layout parameters
	 * @return This panel for chaining
	 */
	public UIPanel setLayoutParams(LayoutParams params) {
		this.layoutParams = params;
		return this;
	}

	/**
	 * Convenience method to set anchor.
	 */
	public UIPanel setAnchor(Anchor anchor) {
		this.layoutParams.setAnchor(anchor);
		return this;
	}

	/**
	 * Convenience method to set margin.
	 */
	public UIPanel setMargin(int margin) {
		this.layoutParams.setMargin(margin);
		return this;
	}

	@Override
	public void updateLayout(int screenWidth, int screenHeight) {
		// Calculate our position from layout params
		int[] pos = LayoutEngine.calculatePosition(screenWidth, screenHeight,
		                                           width, height, layoutParams);
		this.x = pos[0];
		this.y = pos[1];

		// Update all children layouts
		for (UIComponent child : children) {
			child.updateLayout(screenWidth, screenHeight);
		}
	}

	@Override
	public void draw(GraphicsHandler g) {
		if (!visible) {
			return;
		}

		// Draw background if present
		if (backgroundSprite != null) {
			if (useNinePatch && ninePatch != null) {
				ninePatch.draw(g, x, y, width, height);
			} else {
				backgroundSprite.draw(g, x, y, width, height);
			}
		}

		// Draw all children
		for (UIComponent child : children) {
			child.draw(g);
		}
	}

	@Override
	public boolean onMouseClick(int mouseX, int mouseY, boolean leftClick) {
		if (!visible) {
			return false;
		}

		// Check children first (top to bottom)
		for (int i = children.size() - 1; i >= 0; i--) {
			if (children.get(i).onMouseClick(mouseX, mouseY, leftClick)) {
				return true;  // Child consumed the event
			}
		}

		// Panel itself doesn't handle clicks (just passes to children)
		return false;
	}

	@Override
	public void onMouseMove(int mouseX, int mouseY) {
		if (!visible) {
			return;
		}

		// Forward to all children
		for (UIComponent child : children) {
			child.onMouseMove(mouseX, mouseY);
		}
	}

	@Override
	public boolean contains(int x, int y) {
		return x >= this.x && x < this.x + width &&
		       y >= this.y && y < this.y + height;
	}

	@Override
	public int getX() {
		return x;
	}

	@Override
	public int getY() {
		return y;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public LayoutParams getLayoutParams() {
		return layoutParams;
	}

	@Override
	public boolean isVisible() {
		return visible;
	}

	@Override
	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	/**
	 * Get list of child components.
	 */
	public List<UIComponent> getChildren() {
		return children;
	}

	/**
	 * Clear all child components.
	 */
	public void clearChildren() {
		children.clear();
	}
}
