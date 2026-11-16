package com.example.fasttorch.gesture;

/**
 * Recognizes two acceleration/braking envelopes separated by a short quiet period.
 * Input is gravity-free acceleration in m/s^2 with monotonic nanosecond timestamps.
 * No Android dependencies; callers confine an instance to one thread.
 */
public final class ChopGestureDetector {
    public enum Feedback { READY, FIRST_STROKE, TOO_WEAK, TOO_SLOW, TOO_FAST, DIRECTION, INCOMPLETE, DETECTED }
    private static final long QUIET_NS = 60_000_000L;
    private static final long MIN_STROKE_NS = 60_000_000L;
    private static final long MAX_STROKE_NS = 450_000_000L;
    private final GestureConfig config;
    private long previous = -1, quietSince = -1, strokeStart, firstStart = -1;
    private long cooldownUntil;
    private boolean active, braking, armed;
    private float dx, dy, dz, firstX, firstY, firstZ;
    private Feedback feedback = Feedback.READY;
    private float weakPeak;

    public Feedback feedback() { return feedback; }

    public ChopGestureDetector(GestureConfig config) {
        this.config = config;
    }

    public boolean accept(long time, float x, float y, float z) {
        if (time < 0 || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || (previous >= 0 && time <= previous)) {
            reset();
            return false;
        }
        if (previous >= 0 && time - previous > 250_000_000L) reset();
        previous = time;
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        boolean quiet = magnitude < config.threshold * 0.3f;
        if (quiet) {
            if (quietSince < 0) quietSince = time;
        } else {
            quietSince = -1;
        }
        if (time < cooldownUntil) return false;
        if (firstStart >= 0 && time - firstStart > config.maxPairNs + MAX_STROKE_NS) {
            firstStart = -1;
            feedback = Feedback.TOO_SLOW;
        }
        if (active) {
            long duration = time - strokeStart;
            if (x * dx + y * dy + z * dz < -config.threshold * 0.5f) braking = true;
            if (duration > MAX_STROKE_NS) {
                feedback = Feedback.INCOMPLETE;
                active = false;
                armed = false;
                firstStart = -1;
                return false;
            }
            if (quiet && time - quietSince >= QUIET_NS) {
                active = false;
                armed = true;
                if (!braking || duration < MIN_STROKE_NS) {
                    feedback = Feedback.INCOMPLETE;
                    firstStart = -1;
                    return false;
                }
                long gap = strokeStart - firstStart;
                float agreement = dx * firstX + dy * firstY + dz * firstZ;
                if (firstStart >= 0 && gap >= config.minPairNs
                        && gap <= config.maxPairNs && agreement >= 0.5f) {
                    firstStart = -1;
                    armed = false;
                    quietSince = -1;
                    cooldownUntil = time + config.cooldownNs;
                    feedback = Feedback.DETECTED;
                    return true;
                }
                if (firstStart < 0) feedback = Feedback.FIRST_STROKE;
                else if (gap < config.minPairNs) feedback = Feedback.TOO_FAST;
                else if (gap > config.maxPairNs) feedback = Feedback.TOO_SLOW;
                else feedback = Feedback.DIRECTION;
                firstStart = strokeStart;
                firstX = dx;
                firstY = dy;
                firstZ = dz;
            }
            return false;
        }
        if (quiet && time - quietSince >= QUIET_NS) armed = true;
        if (armed && !active && firstStart < 0 && magnitude < config.threshold) {
            weakPeak = Math.max(weakPeak, magnitude);
            if (quiet && time - quietSince >= QUIET_NS && weakPeak >= config.threshold * 0.5f) {
                feedback = Feedback.TOO_WEAK;
                weakPeak = 0;
            }
        }
        if (armed && magnitude >= config.threshold) {
            weakPeak = 0;
            active = true;
            armed = false;
            braking = false;
            strokeStart = time;
            dx = x / magnitude;
            dy = y / magnitude;
            dz = z / magnitude;
        }
        return false;
    }

    public void reset() {
        feedback = Feedback.READY;
        weakPeak = 0;
        previous = -1;
        quietSince = -1;
        firstStart = -1;
        cooldownUntil = 0;
        active = false;
        braking = false;
        armed = false;
    }
}
