package mc.sayda.mcraze.graphics;

import java.util.HashMap;

import mc.sayda.mcraze.awtgraphics.AwtSpriteStore;

public abstract class SpriteStore {
	/** The single instance of this class */
	protected static SpriteStore single;

	/**
	 * Get the single instance of this class
	 *
	 * @return The single instance of this class
	 */
	public static SpriteStore get() {
		if (single == null) {
			if (GraphicsHandler.awtMode) {
				single = new AwtSpriteStore();
			} else {
				// android!
			}
		}
		return single;
	}

	/** The cached sprite map, from reference to sprite instance */
	private HashMap<String, Sprite> sprites = new HashMap<String, Sprite>();

	public Sprite getSprite(String ref) {
		return getSprite(ref, null);
	}

	/**
	 * Retrieve a tinted sprite from the store. The tinted version is cached.
	 *
	 * @param ref  The reference to the image to use for the sprite
	 * @param tint Optional tint color (null for no tint)
	 * @return A sprite instance with the requested tint applied
	 */
	public Sprite getSprite(String ref, Color tint) {
		String key = ref;
		if (tint != null) {
			key = ref + "_tint_" + String.format("%02X%02X%02X%02X", tint.R, tint.G, tint.B, tint.A);
		}

		// if we've already got the sprite in the cache
		// then just return the existing version
		if (sprites.get(key) != null) {
			return sprites.get(key);
		}

		// create a sprite, add it the cache then return it
		Sprite sprite = (tint == null) ? loadSprite(ref) : loadSprite(ref, tint);
		sprites.put(key, sprite);

		return sprite;
	}

	public abstract Sprite loadSprite(String ref);

	public abstract Sprite loadSprite(String ref, Color tint);
}
