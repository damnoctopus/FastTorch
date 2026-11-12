package com.example.fasttorch.gesture;

/** Initial tuning values, pending validation with physical motion traces. */
public enum GestureConfig {
    LOW(17f), NORMAL(13f), HIGH(10f);

    public final float threshold;
    public final long minPairNs = 180_000_000L;
    public final long maxPairNs = 750_000_000L;
    public final long cooldownNs = 1_000_000_000L;

    GestureConfig(float threshold) {
        this.threshold = threshold;
    }

    public static GestureConfig fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return NORMAL;
        }
    }
}
