package com.tinkersgt6.util;

/**
 * Small numeric helpers. Everything that flows into a TConstruct field goes through here, because
 * {@code ToolMaterial.durability} / {@code miningspeed} are plain {@code int} while GT6 can hand us values up to 1e9.
 */
public final class MathUtil {

    public static final long INT_MAX = Integer.MAX_VALUE;

    public static int clampInt(long value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return (int) value;
    }

    public static int clampInt(double value, int min, int max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return value > 0 ? max : min;
        if (value < min) return min;
        if (value > max) return max;
        return (int) Math.round(value);
    }

    public static float clampFloat(double value, float min, float max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return value > 0 ? max : min;
        if (value < min) return min;
        if (value > max) return max;
        return (float) value;
    }

    /** log(1 + v) / log(base), guarded against non-positive bases. */
    public static double logCompress(double value, double base) {
        if (base <= 1.0) base = 10.0;
        return Math.log1p(Math.max(0.0, value)) / Math.log(base);
    }

    public static int color(int r, int g, int b) {
        return ((clampInt(r, 0, 255) & 0xFF) << 16) | ((clampInt(g, 0, 255) & 0xFF) << 8)
            | (clampInt(b, 0, 255) & 0xFF);
    }

    private MathUtil() {}
}
