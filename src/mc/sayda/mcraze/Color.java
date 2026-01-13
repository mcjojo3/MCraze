package mc.sayda.mcraze;

public class Color implements java.io.Serializable {

	private static final long serialVersionUID = 1L;

	// From AWT Color constant values
	public static final Color white = new Color(255, 255, 255);
	public static final Color darkGray = new Color(64, 64, 64);
	public static final Color black = new Color(0, 0, 0);
	public static final Color green = new Color(0, 255, 0);
	public static final Color gray = new Color(128, 128, 128);
	public static final Color blue = new Color(0, 0, 255);
	public static final Color lightGray = new Color(192, 192, 192);
	public static final Color orange = new Color(255, 200, 0);
    public static final Color red = new Color(255, 0, 0);

    public int R, G, B, A;

	public Color(int R, int G, int B) {
		this.R = R;
		this.G = G;
		this.B = B;
		this.A = 255;
	}

	public Color(int R, int G, int B, int A) {
		this.R = R;
		this.G = G;
		this.B = B;
		this.A = A;
	}

	// returns a new color, interpolated toward c by amount (in range [0,1])
	public Color interpolateTo(Color c, float amount) {
		int dR = (int) (amount * (c.R - this.R));
		int dG = (int) (amount * (c.G - this.G));
		int dB = (int) (amount * (c.B - this.B));
		return new Color(this.R + dR, this.G + dG, this.B + dB, this.A);
	}

	// Convert to RGB integer (for serialization)
	public int toRGB() {
		return (R << 16) | (G << 8) | B;
	}

	// Create from RGB integer
	public static Color fromRGB(int rgb) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		return new Color(r, g, b);
	}
}
