package mc.sayda.mcraze.server;

/**
 * Tracks block breaking progress for a single player
 */
public class BreakingState {
	public int ticks = 0;      // Number of ticks spent breaking current block
	public int x = -1;          // X coordinate of block being broken
	public int y = -1;          // Y coordinate of block being broken

	public void reset() {
		ticks = 0;
		x = -1;
		y = -1;
	}

	public boolean isBreaking(int targetX, int targetY) {
		return x == targetX && y == targetY;
	}
}
