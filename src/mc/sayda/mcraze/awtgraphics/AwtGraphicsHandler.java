package mc.sayda.mcraze.awtgraphics;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferStrategy;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;

import mc.sayda.mcraze.Game;
import mc.sayda.mcraze.graphics.Sprite;
import mc.sayda.mcraze.graphics.SpriteStore;

public class AwtGraphicsHandler extends mc.sayda.mcraze.graphics.GraphicsHandler {
	private Canvas canvas;
	private BufferStrategy strategy;
	private JFrame container;
	private Cursor myCursor = null;
	private JPanel panel;
	private AwtEventsHandler eventsHandler;

	// Color caching to avoid creating new Color objects on every setColor call
	private mc.sayda.mcraze.graphics.Color lastRequestedColor = null;
	private Color cachedAwtColor = null;

	@Override
	public void init(final Game game) {
		canvas = new Canvas();
		// create a frame to contain our game
		container = new JFrame("MCraze");

		try {
			// Fix deprecated URL constructor
			ImageIcon ii = new ImageIcon(java.net.URI.create("file:assets/sprites/other/mouse.png").toURL());
			Image im = ii.getImage();
			Toolkit tk = canvas.getToolkit();
			myCursor = tk.createCustomCursor(im, new Point(8, 8), "MyCursor");
		} catch (Exception e) {
			System.out.println("myCursor creation failed " + e);
		}

		// get hold the content of the frame and set up the resolution of the game
		panel = (JPanel) container.getContentPane();
		panel.setPreferredSize(new Dimension(screenWidth, screenHeight));
		panel.setLayout(null);
		panel.setCursor(myCursor);
		panel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				Dimension d = e.getComponent().getSize();
				canvas.setBounds(0, 0, d.width, d.height);
				screenWidth = d.width;
				screenHeight = d.height;
			}
		});

		// setup our canvas size and put it into the content of the frame
		canvas.setBounds(0, 0, screenWidth + 10, screenHeight + 10);
		panel.add(canvas);

		// Tell AWT not to bother repainting our canvas since we're
		// going to do that our self in accelerated mode
		canvas.setIgnoreRepaint(true);

		// finally make the window visible
		container.pack();
		container.setResizable(true);
		container.setVisible(true);

		// add a listener to respond to the user closing the window. If they
		// do we'd like to exit the game
		// TODO: add this back in
		container.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				game.goToMainMenu(); // this saves and cleans up appropriately
				game.quit();
			}
		});
		eventsHandler = new AwtEventsHandler(game, canvas);

		// request the focus so key events come to us
		canvas.requestFocus();

		// create the buffering strategy which will allow AWT
		// to manage our accelerated graphics
		canvas.createBufferStrategy(2);
		strategy = canvas.getBufferStrategy();
	}

	/**
	 * Initialize for crash display (no Game instance needed)
	 */
	public void initCrashDisplay(String title, int width, int height) {
		screenWidth = width;
		screenHeight = height;

		canvas = new Canvas();
		container = new JFrame(title);

		panel = (JPanel) container.getContentPane();
		panel.setPreferredSize(new Dimension(screenWidth, screenHeight));
		panel.setLayout(null);

		canvas.setBounds(0, 0, screenWidth, screenHeight);
		panel.add(canvas);
		canvas.setIgnoreRepaint(true);

		container.pack();
		container.setResizable(true);
		container.setVisible(true);

		// Close listener just disposes
		container.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				container.dispose();
			}
		});

		canvas.requestFocus();
		canvas.createBufferStrategy(2);
		strategy = canvas.getBufferStrategy();
	}

	@Override
	public boolean isWindowOpen() {
		return container != null && container.isVisible();
	}

	Graphics2D g;

	@Override
	public void startDrawing() {
		// Get hold of a graphics context for the accelerated
		// surface and blank it out
		g = (Graphics2D) strategy.getDrawGraphics();
		g.setColor(Color.black);
		g.fillRect(0, 0, screenWidth, screenHeight);

	}

	@Override
	public void finishDrawing() {
		g.dispose();
		strategy.show();
	}

	@Override
	public void setColor(mc.sayda.mcraze.graphics.Color color) {
		// Cache color objects to avoid creating new ones on every call
		if (lastRequestedColor == null || !color.equals(lastRequestedColor)) {
			cachedAwtColor = new Color(color.R, color.G, color.B, color.A);
			lastRequestedColor = color;
		}
		g.setColor(cachedAwtColor);
	}

	@Override
	public void fillRect(int x, int y, int width, int height) {
		g.fillRect(x, y, width, height);
	}

	@Override
	public void drawRect(int x, int y, int width, int height) {
		g.drawRect(x, y, width, height);
	}

	@Override
	public void drawString(String string, int x, int y) {
		g.drawString(string, x, y);
	}

	@Override
	public int getStringWidth(String string) {
		return g.getFontMetrics().stringWidth(string);
	}

	@Override
	public void fillOval(int x, int y, int width, int height) {
		g.fillOval(x, y, width, height);
	}

	@Override
	public void drawImage(Sprite sprite, int x, int y) {
		// Optimize: Use getSprite() to check cache first
		AwtSprite awtSprite = (AwtSprite) sprite;
		if (awtSprite.image == null) {
			AwtSprite other = (AwtSprite) SpriteStore.get().getSprite(awtSprite.ref);
			awtSprite.image = other.image;
		}
		g.drawImage(awtSprite.image, x, y, null);
	}

	@Override
	public void drawImage(Sprite sprite, int x, int y, mc.sayda.mcraze.graphics.Color tint) {
		int width = sprite.getWidth();
		int height = sprite.getHeight();
		drawImage(sprite, x, y, width, height, tint);
	}

	@Override
	public void drawImage(Sprite sprite, int x, int y, int width, int height) {
		AwtSprite awtSprite = (AwtSprite) sprite;
		if (awtSprite.image == null) {
			AwtSprite other = (AwtSprite) SpriteStore.get().getSprite(awtSprite.ref);
			awtSprite.image = other.image;
		}
		g.drawImage(awtSprite.image, x, y, width, height, null);
	}

	@Override
	public void drawImage(Sprite sprite, int x, int y, int width, int height,
			mc.sayda.mcraze.graphics.Color tint) {
		drawImage(sprite, x, y, width, height);
		java.awt.Color old = g.getColor();
		this.setColor(tint);
		this.fillRect(x, y, width, height);
		g.setColor(old);
	}

	/**
	 * Get the events handler for accessing input state
	 */
	public AwtEventsHandler getEventsHandler() {
		return eventsHandler;
	}

	private java.util.Stack<java.awt.geom.AffineTransform> transformStack = new java.util.Stack<>();

	@Override
	public void rotate(double theta, double x, double y) {
		g.rotate(theta, x, y);
	}

	@Override
	public void scale(double sx, double sy) {
		g.scale(sx, sy);
	}

	@Override
	public void translate(double tx, double ty) {
		g.translate(tx, ty);
	}

	@Override
	public void pushState() {
		transformStack.push(g.getTransform());
	}

	@Override
	public void popState() {
		if (!transformStack.isEmpty()) {
			g.setTransform(transformStack.pop());
		}
	}

	@Override
	public void setFont(String name, int style, int size) {
		g.setFont(new java.awt.Font(name, style, size));
	}
}
