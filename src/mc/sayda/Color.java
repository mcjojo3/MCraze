package mc.sayda;

public class Color implements java.io.Serializable {
	
	private static final long serialVersionUID = 1L;
	
	// From AWT Color constant values
	public static final Color white = new Color(255, 255, 255);
	public static final Color darkGray = new Color(64, 64, 64);
	public static final Color black = new Color(0, 0, 0);
	public static final Color green = new Color(0, 255, 0);
	public static final Color gray = new Color(128, 128, 128);
	public static final Color blue = new Color(0, 0, 255);
	public static final Color LIGHT_GRAY = new Color(192, 192, 192);
	public static final Color DARK_GRAY = darkGray;
	public static final Color orange = new Color(255, 200, 0);
	
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

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;
		Color other = (Color) obj;
		return R == other.R && G == other.G && B == other.B && A == other.A;
	}

	@Override
	public int hashCode() {
		return (R << 24) | (G << 16) | (B << 8) | A;
	}

	/**
	 * Convert color to ARGB integer for network serialization
	 */
	public int toRGB() {
		return (A << 24) | (R << 16) | (G << 8) | B;
	}

	/**
	 * Create color from ARGB integer
	 */
	public static Color fromRGB(int argb) {
		int a = (argb >> 24) & 0xFF;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		return new Color(r, g, b, a);
	}
}
