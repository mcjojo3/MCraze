package mc.sayda.mcraze.ui;

import mc.sayda.mcraze.Color;
import mc.sayda.mcraze.GraphicsHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable list component with visual scrollbar
 */
public class ScrollableList<T> {
	private List<T> items;
	private List<String> displayNames;
	private int selectedIndex = -1;
	private int scrollOffset = 0;

	private int x, y, width, height;
	private int offsetX;  // Horizontal offset from center
	private int itemHeight = 30;
	private int visibleItems;

	// Scrollbar
	private static final int SCROLLBAR_WIDTH = 10;

	public ScrollableList(int x, int y, int width, int height, int itemHeight) {
		this.offsetX = x;  // Store initial x as offset
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.itemHeight = itemHeight;
		this.visibleItems = height / itemHeight;
		this.items = new ArrayList<>();
		this.displayNames = new ArrayList<>();
	}

	/**
	 * Set the items in the list
	 */
	public void setItems(List<T> items, List<String> displayNames) {
		this.items = new ArrayList<>(items);
		this.displayNames = new ArrayList<>(displayNames);
		this.selectedIndex = -1;
		this.scrollOffset = 0;
	}

	/**
	 * Get the selected item
	 */
	public T getSelectedItem() {
		if (selectedIndex >= 0 && selectedIndex < items.size()) {
			return items.get(selectedIndex);
		}
		return null;
	}

	/**
	 * Get the selected index
	 */
	public int getSelectedIndex() {
		return selectedIndex;
	}

	/**
	 * Clear selection
	 */
	public void clearSelection() {
		selectedIndex = -1;
	}

	/**
	 * Handle mouse click
	 */
	public boolean handleClick(int mouseX, int mouseY) {
		// Check if click is within list bounds
		if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
			return false;
		}

		// Check if click is on scrollbar
		if (mouseX >= x + width - SCROLLBAR_WIDTH && items.size() > visibleItems) {
			// Clicked in scrollbar track - jump to position proportionally
			int trackHeight = height - 4;
			int relativeY = mouseY - (y + 2);
			int maxScroll = Math.max(0, items.size() - visibleItems);

			// Calculate target scroll position based on click position
			float clickRatio = (float) relativeY / trackHeight;
			scrollOffset = Math.max(0, Math.min(maxScroll, (int) (clickRatio * maxScroll)));
			return true;
		}

		// Check if click is on an item
		int relativeY = mouseY - y;
		int clickedIndex = (relativeY / itemHeight) + scrollOffset;

		if (clickedIndex >= 0 && clickedIndex < items.size()) {
			selectedIndex = clickedIndex;
			return true;
		}

		return false;
	}

	/**
	 * Handle mouse wheel scroll
	 */
	public void handleScroll(int mouseX, int mouseY, int wheelRotation) {
		// Check if mouse is over list
		if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) {
			return;
		}

		// Scroll by 3 items at a time for smoother scrolling
		int scrollAmount = wheelRotation * 3;
		int maxScroll = Math.max(0, items.size() - visibleItems);
		scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + scrollAmount));
	}

	/**
	 * Get scrollbar Y position
	 */
	private int getScrollbarY() {
		if (items.size() <= visibleItems) {
			return y + 2;
		}

		int trackHeight = height - 4;
		int scrollbarHeight = getScrollbarHeight();
		int maxScrollbarY = trackHeight - scrollbarHeight;
		int maxScroll = items.size() - visibleItems;

		return y + 2 + (scrollOffset * maxScrollbarY) / maxScroll;
	}

	/**
	 * Get scrollbar height
	 */
	private int getScrollbarHeight() {
		if (items.size() <= visibleItems) {
			return height - 4;
		}

		int trackHeight = height - 4;
		return Math.max(20, (visibleItems * trackHeight) / items.size());
	}

	/**
	 * Draw the list
	 */
	public void draw(GraphicsHandler g) {
		// Draw background
		g.setColor(new Color(40, 40, 40, 200));
		g.fillRect(x, y, width, height);

		// Draw border
		g.setColor(new Color(160, 160, 160, 255));
		g.drawRect(x, y, width, height);

		// Draw items
		int itemWidth = width - SCROLLBAR_WIDTH - 4;
		int maxVisible = Math.min(visibleItems, items.size() - scrollOffset);

		for (int i = 0; i < maxVisible; i++) {
			int itemIndex = scrollOffset + i;
			int itemY = y + (i * itemHeight);

			// Draw item background
			boolean isSelected = (itemIndex == selectedIndex);
			if (isSelected) {
				g.setColor(new Color(0, 200, 0, 180));
				g.fillRect(x + 2, itemY + 1, itemWidth, itemHeight - 2);
			}

			// Draw item text
			g.setColor(isSelected ? Color.white : new Color(200, 200, 200, 255));
			String displayName = displayNames.get(itemIndex);
			g.drawString(displayName, x + 8, itemY + (itemHeight / 2) + 4);

			// Draw separator line
			if (i < maxVisible - 1) {
				g.setColor(new Color(100, 100, 100, 255));
				g.drawRect(x + 2, itemY + itemHeight - 1, itemWidth, 1);
			}
		}

		// Draw scrollbar if needed
		if (items.size() > visibleItems) {
			int scrollbarX = x + width - SCROLLBAR_WIDTH - 2;

			// Draw scrollbar track
			g.setColor(new Color(60, 60, 60, 255));
			g.fillRect(scrollbarX, y + 2, SCROLLBAR_WIDTH, height - 4);

			// Draw scrollbar thumb
			int scrollbarY = getScrollbarY();
			int scrollbarHeight = getScrollbarHeight();

			g.setColor(new Color(140, 140, 140, 255));
			g.fillRect(scrollbarX, scrollbarY, SCROLLBAR_WIDTH, scrollbarHeight);

			// Draw scrollbar thumb border
			g.setColor(new Color(200, 200, 200, 255));
			g.drawRect(scrollbarX, scrollbarY, SCROLLBAR_WIDTH, scrollbarHeight);
		}
	}

	/**
	 * Update position (for centering with offset)
	 */
	public void updatePosition(int screenWidth) {
		this.x = (screenWidth - width) / 2 + offsetX;
	}

	public int getX() { return x; }
	public int getY() { return y; }
	public int getWidth() { return width; }
	public int getHeight() { return height; }
}
