package mc.sayda.mcraze.ui.view;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;
import mc.sayda.mcraze.item.InventoryItem;
import mc.sayda.mcraze.ui.ChestUI;
import mc.sayda.mcraze.ui.Inventory;
import mc.sayda.mcraze.ui.component.Tooltip;
import mc.sayda.mcraze.util.Int2;

/**
 * Legacy view component for rendering chest UI using manual loops.
 */
public class ChestView {
	private ChestUI chestUI;

	// Tooltip
	private Tooltip tooltip;

	// Constants
	private static final int TILE_SIZE = 16;
	private static final int SEPARATION = 15;

	// Layout state
	private int screenWidth;
	private int screenHeight;

	public ChestView(ChestUI chestUI) {
		this.chestUI = chestUI;
		this.tooltip = new Tooltip();
	}

	public void render(GraphicsHandler g, int screenWidth, int screenHeight,
			InventoryItem[][] chestItems, Inventory playerInventory) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;

		if (!chestUI.isVisible()) {
			return;
		}

		// Calculate positions (matches ChestUI logic)
		int chestPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
		int chestPanelHeight = 3 * (TILE_SIZE + SEPARATION) + SEPARATION;
		int playerPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
		int playerPanelHeight = 4 * (TILE_SIZE + SEPARATION) + SEPARATION;

		int totalHeight = chestPanelHeight + playerPanelHeight + SEPARATION;
		int chestX = screenWidth / 2 - chestPanelWidth / 2;
		int chestY = screenHeight / 2 - totalHeight / 2;
		int playerX = screenWidth / 2 - playerPanelWidth / 2;
		int playerY = chestY + chestPanelHeight + SEPARATION;

		// 1. Render Chest Panel
		g.setColor(Color.brown);
		g.fillRect(chestX, chestY, chestPanelWidth, chestPanelHeight);

		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 10; j++) {
				int sx = chestX + SEPARATION + j * (TILE_SIZE + SEPARATION);
				int sy = chestY + SEPARATION + i * (TILE_SIZE + SEPARATION);

				g.setColor(Color.darkGray);
				g.fillRect(sx - 2, sy - 2, TILE_SIZE + 4, TILE_SIZE + 4);
				g.setColor(Color.lightGray);
				g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

				if (chestItems != null && j < chestItems.length && i < chestItems[j].length) {
					InventoryItem item = chestItems[j][i];
					if (item != null && item.item != null && item.count > 0) {
						item.draw(g, sx, sy, TILE_SIZE);
					}
				}
			}
		}

		// 2. Render Player Panel
		g.setColor(Color.gray);
		g.fillRect(playerX, playerY, playerPanelWidth, playerPanelHeight);

		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 10; j++) {
				int sx = playerX + SEPARATION + j * (TILE_SIZE + SEPARATION);
				int sy = playerY + SEPARATION + i * (TILE_SIZE + SEPARATION);

				// Draw slot border (blue for selected hotbar, dark gray for others)
				// Map to player inventory rows (3, 4, 5 for main, 6 for hotbar)
				int invRow = (i < 3) ? (i + 3) : 6;
				if (invRow == 6 && j == playerInventory.hotbarIdx) {
					g.setColor(Color.blue);
					g.fillRect(sx - 4, sy - 4, TILE_SIZE + 8, TILE_SIZE + 8);
				} else {
					g.setColor(Color.darkGray);
					g.fillRect(sx - 2, sy - 2, TILE_SIZE + 4, TILE_SIZE + 4);
				}
				g.setColor(Color.lightGray);
				g.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);

				if (playerInventory != null && j < playerInventory.inventoryItems.length) {
					InventoryItem item = playerInventory.inventoryItems[j][invRow];
					item.draw(g, sx, sy, TILE_SIZE);
				}
			}
		}

		// 3. Render holding item (follows cursor)
		renderHoldingItem(g, playerInventory);

		// 4. Render Tooltip
		if (tooltip != null) {
			tooltip.updateLayout(screenWidth, screenHeight);
			tooltip.draw(g);
		}
	}

	private void renderHoldingItem(GraphicsHandler g, Inventory playerInventory) {
		if (playerInventory != null && playerInventory.holding != null && !playerInventory.holding.isEmpty()) {
			playerInventory.holding.draw(g, playerInventory.holdingX, playerInventory.holdingY, TILE_SIZE);
		}
	}

	public void updateTooltip(int mouseX, int mouseY) {
		InventoryItem hoveredItem = getItemAtMouse(mouseX, mouseY);
		if (hoveredItem != null && !hoveredItem.isEmpty()) {
			tooltip.clearLines()
					.addLine(hoveredItem.getItem().name)
					.addLine("Count: " + hoveredItem.getCount())
					.show(mouseX, mouseY);
		} else {
			tooltip.hide();
		}
	}

	private InventoryItem getItemAtMouse(int mouseX, int mouseY) {
		int chestPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
		int chestPanelHeight = 3 * (TILE_SIZE + SEPARATION) + SEPARATION;
		int playerPanelWidth = 10 * (TILE_SIZE + SEPARATION) + SEPARATION;
		int playerPanelHeight = 4 * (TILE_SIZE + SEPARATION) + SEPARATION;

		int totalHeight = chestPanelHeight + playerPanelHeight + SEPARATION;
		int chestX = screenWidth / 2 - chestPanelWidth / 2;
		int chestY = screenHeight / 2 - totalHeight / 2;
		int playerX = screenWidth / 2 - playerPanelWidth / 2;
		int playerY = chestY + chestPanelHeight + SEPARATION;

		// Check chest panel
		if (mouseX >= chestX && mouseX <= chestX + chestPanelWidth && mouseY >= chestY
				&& mouseY <= chestY + chestPanelHeight) {
			Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
					mouseX - chestX, mouseY - chestY,
					TILE_SIZE, SEPARATION,
					SEPARATION, SEPARATION,
					10, 3);
			if (slot != null) {
				return chestUI.getChestItems()[slot.x][slot.y];
			}
		}

		// Check player panel
		if (mouseX >= playerX && mouseX <= playerX + playerPanelWidth && mouseY >= playerY
				&& mouseY <= playerY + playerPanelHeight) {
			Int2 slot = mc.sayda.mcraze.util.SlotCoordinateHelper.getSlotAt(
					mouseX - playerX, mouseY - playerY,
					TILE_SIZE, SEPARATION,
					SEPARATION, SEPARATION,
					10, 4);
			if (slot != null) {
				int invRow = (slot.y < 3) ? (slot.y + 3) : 6;
				return chestUI.getPlayerInventory().inventoryItems[slot.x][invRow];
			}
		}

		return null;
	}

	public void invalidateLayout() {
		// Not strictly needed in manual rendering but kept for compatibility
	}
}
