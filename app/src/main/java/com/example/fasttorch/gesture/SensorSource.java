package com.example.fasttorch.gesture;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import java.util.function.Consumer;

/** Owns one subscription. Each start has isolated worker state and a cancellation token. */
public final class SensorSource {
    private final SensorManager manager;
    private final Sensor sensor;
    private final Handler main;
    private HandlerThread thread;
    private SensorEventListener listener;
    private int generation;

    public SensorSource(Context context, Handler main) {
        manager = context.getSystemService(SensorManager.class);
        Sensor wake = manager == null ? null
                : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
        sensor = wake != null ? wake : manager == null ? null
                : manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.main = main;
    }

    public boolean isAvailable() { return sensor != null; }
    public boolean isWakeUp() { return sensor != null && sensor.isWakeUpSensor(); }
    public String sensorName() { return sensor == null ? "" : sensor.getName(); }

    public boolean start(GestureConfig config, Runnable onGesture) {
        return start(config, onGesture, null);
    }

    public boolean start(GestureConfig config, Runnable onGesture,
                         Consumer<ChopGestureDetector.Feedback> onFeedback) {
        stop();
        if (sensor == null) return false;
        final int token = generation;
        GravityFilter filter = new GravityFilter();
        ChopGestureDetector detector = new ChopGestureDetector(config);
        thread = new HandlerThread("FastTorch-motion");
        thread.start();
        listener = new SensorEventListener() {
            private ChopGestureDetector.Feedback lastFeedback = ChopGestureDetector.Feedback.READY;
            private long lastFeedbackTime;
            @Override public void onSensorChanged(SensorEvent event) {
                long age = SystemClock.elapsedRealtimeNanos() - event.timestamp;
                if (age < 0 || age > 250_000_000L) {
                    filter.reset();
                    detector.reset();
                    return;
                }
                float[] v = filter.filter(event.timestamp,
                        event.values[0], event.values[1], event.values[2]);
                if (v == null) {
                    detector.reset();
                } else if (detector.accept(event.timestamp, v[0], v[1], v[2])) {
                    long detected = event.timestamp;
                    main.post(() -> {
                        if (token == generation
                                && SystemClock.elapsedRealtimeNanos() - detected < 500_000_000L) {
                            onGesture.run();
                        }
                    });
                }
                ChopGestureDetector.Feedback feedback = detector.feedback();
                if (onFeedback != null && feedback != lastFeedback
                        && event.timestamp - lastFeedbackTime >= 250_000_000L) {
                    lastFeedback = feedback;
                    lastFeedbackTime = event.timestamp;
                    long observed = event.timestamp;
                    main.post(() -> {
                        if (token == generation && SystemClock.elapsedRealtimeNanos() - observed < 500_000_000L) {
                            onFeedback.accept(feedback);
                        }
                    });
                }
            }
            @Override public void onAccuracyChanged(Sensor ignored, int accuracy) { }
        };
        try {
            if (manager.registerListener(listener, sensor, 20_000, 0,
                    new Handler(thread.getLooper()))) return true;
        } catch (RuntimeException ignored) {
            // Report failure through the service instead of leaving an idle worker running.
        }
        stop();
        return false;
    }

    public void stop() {
        generation++;
        if (listener != null) manager.unregisterListener(listener);
        if (thread != null) thread.quitSafely();
        listener = null;
        thread = null;
    }
}
