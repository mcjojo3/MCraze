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
 * A resource manager for sprites in the game. Its often quite important
 * how and where you get your game resources from. In most cases
 * it makes sense to have a central resource loader that goes away, gets
 * your resources and caches them for future use.
 * <p>
 * [singleton]
 * <p>
 * 
 * @author Kevin Glass
 */
public class AwtSpriteStore extends mc.sayda.mcraze.graphics.SpriteStore {

	@Override
	public Sprite loadSprite(String ref) {
		// otherwise, go away and grab the sprite from the resource
		// loader
		BufferedImage sourceImage = null;
		String actualRef = ref; // Track which texture we actually loaded

		try {
			// The ClassLoader.getResource() ensures we get the sprite
			// from the appropriate place, this helps with deploying the game
			// with things like webstart. You could equally do a file look
			// up here.
			URL url = this.getClass().getClassLoader().getResource(ref);

			if (url == null) {
				// Try loading fallback texture
				System.err.println("Warning: Can't find ref: " + ref + " - using missing texture");
				actualRef = "assets/sprites/other/missing.png";
				url = this.getClass().getClassLoader().getResource(actualRef);

				if (url == null) {
					fail("FATAL: Missing texture fallback not found: " + actualRef);
				}
			}

			// use ImageIO to read the image in
			sourceImage = ImageIO.read(url);
		} catch (IOException e) {
			// Try loading fallback texture on IO error
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

		// create an accelerated image of the right size to store our sprite in
		GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getDefaultScreenDevice().getDefaultConfiguration();
		Image image = gc.createCompatibleImage(sourceImage.getWidth(), sourceImage.getHeight(),
				Transparency.BITMASK);

		// draw our source image into the accelerated image
		image.getGraphics().drawImage(sourceImage, 0, 0, null);

		// create a sprite, add it the cache then return it
		Sprite sprite = (Sprite) new AwtSprite(image, actualRef);
		return sprite;
	}

	/**
	 * Utility method to handle resource loading failure
	 *
	 * @param message
	 *                The message to display on failure
	 */
	private void fail(String message) {
		// we're pretty dramatic here, if a resource isn't available
		// we dump the message and exit the game
		System.err.println(message);
		System.exit(1);
	}
}
