package com.example.fasttorch.gesture;

/** Time-aware high-pass filter; output is reused to avoid per-sample allocation. */
public final class GravityFilter {
    private long previous = -1;
    private long started;
    private float gx, gy, gz;
    private final float[] linear = new float[3];

    public float[] filter(long timestamp, float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || timestamp < 0 || (previous >= 0 && timestamp <= previous)) {
            reset();
            return null;
        }
        if (previous < 0 || timestamp - previous > 250_000_000L) {
            gx = x;
            gy = y;
            gz = z;
            started = timestamp;
            previous = timestamp;
            return null;
        }
        double dt = (timestamp - previous) / 1_000_000_000.0;
        float alpha = (float) Math.exp(-dt / 0.35);
        gx = alpha * gx + (1 - alpha) * x;
        gy = alpha * gy + (1 - alpha) * y;
        gz = alpha * gz + (1 - alpha) * z;
        previous = timestamp;
        if (timestamp - started < 250_000_000L) return null;
        linear[0] = x - gx;
        linear[1] = y - gy;
        linear[2] = z - gz;
        return linear;
    }

    public void reset() {
        previous = -1;
    }
}
