package mc.sayda.mcraze.awtgraphics;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import mc.sayda.mcraze.graphics.Sprite;

/**
 * A resource manager for sprites in the game.
 * [singleton]
 */
public class AwtSpriteStore extends mc.sayda.mcraze.graphics.SpriteStore {

	@Override
	public Sprite loadSprite(String ref) {
		BufferedImage sourceImage = null;
		String actualRef = ref;

		try {
			URL url = this.getClass().getClassLoader().getResource(ref);

			if (url == null) {
				System.err.println("Warning: Can't find ref: " + ref + " - using missing texture");
				actualRef = "assets/sprites/other/missing.png";
				url = this.getClass().getClassLoader().getResource(actualRef);

				if (url == null) {
					fail("FATAL: Missing texture fallback not found: " + actualRef);
				}
			}

			sourceImage = ImageIO.read(url);
		} catch (IOException e) {
			System.err.println("Warning: Failed to load: " + ref + " - using missing texture");
			try {
				actualRef = "assets/sprites/other/missing.png";
				URL fallbackUrl = this.getClass().getClassLoader().getResource(actualRef);
				if (fallbackUrl == null) {
					fail("FATAL: Missing texture fallback not found: " + actualRef);
				}
				sourceImage = ImageIO.read(fallbackUrl);
			} catch (IOException fallbackError) {
				fail("FATAL: Failed to load missing texture fallback: " + fallbackError.getMessage());
			}
		}

		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration();
		Image image = gc.createCompatibleImage(sourceImage.getWidth(), sourceImage.getHeight(),
				Transparency.BITMASK);

		image.getGraphics().drawImage(sourceImage, 0, 0, null);

		return new AwtSprite(image, actualRef);
	}

	@Override
	public Sprite loadSprite(String ref, mc.sayda.mcraze.graphics.Color tint) {
		AwtSprite baseSprite = (AwtSprite) getSprite(ref);
		Image baseImage = baseSprite.image;

		int w = baseImage.getWidth(null);
		int h = baseImage.getHeight(null);

		BufferedImage tintedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g2d = tintedImage.createGraphics();

		g2d.drawImage(baseImage, 0, 0, null);

		g2d.setComposite(java.awt.AlphaComposite.SrcAtop);
		g2d.setColor(new java.awt.Color(tint.R, tint.G, tint.B, tint.A));
		g2d.fillRect(0, 0, w, h);

		g2d.dispose();

		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration();
		Image acceleratedImage = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);

		acceleratedImage.getGraphics().drawImage(tintedImage, 0, 0, null);

		return new AwtSprite(acceleratedImage, ref);
	}

	private void fail(String message) {
		System.err.println(message);
		System.exit(1);
	}
}
