package mc.sayda.mcraze.graphics;

import mc.sayda.mcraze.Game;

import mc.sayda.mcraze.awtgraphics.AwtGraphicsHandler;

public abstract class GraphicsHandler {
	public static final boolean awtMode = true;

	// Font style constants
	public static final int FONT_PLAIN = 0;
	public static final int FONT_BOLD = 1;
	public static final int FONT_ITALIC = 2;

	protected static int screenWidth = 854; // 16:9 aspect ratio
	protected static int screenHeight = 480;

	private static GraphicsHandler single;

	public int getScreenWidth() {
		return screenWidth;
	}

	public int getScreenHeight() {
		return screenHeight;
	}

	public static GraphicsHandler get() {
		if (single == null) {
			if (awtMode) {
				single = new AwtGraphicsHandler();
			} else {
				// android!
			}
		}
		return single;
	}

	public abstract void init(Game game);

	public abstract void startDrawing();

	public abstract void finishDrawing();

	public abstract void setColor(Color color);

	public abstract void fillRect(int x, int y, int width, int height);

	public abstract void drawRect(int x, int y, int width, int height);

	public abstract void drawString(String string, int x, int y);

	public abstract int getStringWidth(String string);

	public abstract void fillOval(int x, int y, int width, int height);

	public abstract void drawImage(Sprite sprite, int x, int y);

	public abstract void drawImage(Sprite sprite, int x, int y, Color tint);

	public abstract void drawImage(Sprite sprite, int x, int y, int width, int height);

	public abstract void drawImage(Sprite sprite, int x, int y, int width, int height, Color tint);

	public abstract boolean isWindowOpen();

	// Transform methods
	public abstract void rotate(double theta, double x, double y);

	public abstract void scale(double sx, double sy);

	public abstract void translate(double tx, double ty);

	public abstract void pushState();

	public abstract void popState();

	public abstract void setFont(String name, int style, int size);
}
