package mc.sayda.util;

/**
 * A wrapper class that provides timing methods using standard Java timing.
 * Provides high-resolution timing for game loop timing and frame limiting.
 *
 * @author Kevin Glass (original), refactored to use System.nanoTime()
 */
public class SystemTimer {

	/**
	 * Get the high resolution time in milliseconds.
	 * Uses System.nanoTime() for better precision than System.currentTimeMillis().
	 *
	 * @return The high resolution time in milliseconds
	 */
	public static long getTime() {
		return System.nanoTime() / 1_000_000;
	}

	/**
	 * Sleep for a fixed number of milliseconds.
	 *
	 * @param duration The amount of time in milliseconds to sleep for
	 */
	public static void sleep(long duration) {
		if (duration > 0) {
			try {
				Thread.sleep(duration);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
}