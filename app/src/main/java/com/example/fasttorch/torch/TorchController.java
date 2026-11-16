package com.example.fasttorch.torch;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;

import com.example.fasttorch.R;

/** Main-thread confined. Observed state, not a local toggle flag, drives commands. */
public final class TorchController {
    public enum State { UNKNOWN, OFF, ON, UNAVAILABLE, NO_FLASH }
    public interface Listener {
        void onChanged();
        void onConfirmedGestureToggle();
    }

    private final TorchHardware hardware;
    private final Handler handler;
    private final Listener listener;
    private String cameraId;
    private State state = State.UNKNOWN;
    private Boolean pending;
    private boolean pendingGesture, owned, closed, registered;
    private int error;
    private int callbackGeneration;
    private CameraManager.TorchCallback callback;
    private final Runnable timeout = this::onTimeout;

    private void onTimeout() {
        pending = null;
        pendingGesture = false;
        error = R.string.torch_timeout;
        // Ownership of an accepted ON request remains ours until an external change.
        state = State.UNKNOWN;
        listener.onChanged();
    }
    private CameraManager.TorchCallback createCallback(int generation) {
        return new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (closed || generation != callbackGeneration || !id.equals(cameraId)) return;
            boolean matched = pending != null && pending == enabled;
            boolean feedback = matched && pendingGesture;
            if (matched) {
                owned = enabled;
            } else if (pending == null) {
                // A change without a pending request is external. Duplicate callbacks
                // for the same state do not invalidate our best-effort ownership.
                if (!enabled || (state != State.UNKNOWN && state != State.ON)) owned = false;
            }
            state = enabled ? State.ON : State.OFF;
            if (pending != null && !matched) {
                // An older callback can arrive while a newer command is pending.
                listener.onChanged();
                return;
            }
            pending = null;
            pendingGesture = false;
            error = 0;
            handler.removeCallbacks(timeout);
            listener.onChanged();
            if (feedback) listener.onConfirmedGestureToggle();
        }
        @Override public void onTorchModeUnavailable(String id) {
            if (closed || generation != callbackGeneration || !id.equals(cameraId)) return;
            state = State.UNAVAILABLE;
            pending = null;
            pendingGesture = false;
            owned = false;
            error = R.string.torch_busy;
            handler.removeCallbacks(timeout);
            listener.onChanged();
        }
        };
    }

    public TorchController(Context context, Handler handler, Listener listener) {
        this(new TorchHardware.Camera2(context), handler, listener);
    }

    TorchController(TorchHardware hardware, Handler handler, Listener listener) {
        this.hardware = hardware;
        this.handler = handler;
        this.listener = listener;
    }

    public void initialize() {
        reconnect();
    }

    /** Re-read camera availability without changing the light or accepting stale callbacks. */
    public void reconnect() {
        if (closed || pending != null) return;
        handler.removeCallbacks(timeout);
        callbackGeneration++;
        state = State.UNKNOWN;
        error = 0;
        pendingGesture = false;
        try {
            if (registered) {
                hardware.unregister(callback);
                registered = false;
            }
            String previousCameraId = cameraId;
            cameraId = hardware.findRearFlash();
            if (cameraId == null || !cameraId.equals(previousCameraId)) owned = false;
            if (cameraId == null) {
                state = State.NO_FLASH;
                error = R.string.no_flash;
            } else {
                callback = createCallback(callbackGeneration);
                handler.postDelayed(timeout, 2_000);
                hardware.register(callback, handler);
                registered = true;
            }
        } catch (CameraAccessException | RuntimeException e) {
            handler.removeCallbacks(timeout);
            state = State.UNAVAILABLE;
            error = e instanceof SecurityException ? R.string.torch_restricted : R.string.torch_failed;
        }
        listener.onChanged();
    }

    public State state() { return state; }
    public int error() { return error; }
    public boolean hasFlash() { return cameraId != null; }
    public boolean pending() { return pending != null; }
    public boolean canToggle() {
        return !closed && pending == null && (state == State.ON || state == State.OFF);
    }

    public void toggle(boolean gesture) {
        if (!canToggle()) return;
        boolean target = state != State.ON;
        pending = target;
        pendingGesture = gesture;
        error = 0;
        try {
            hardware.set(cameraId, target);
            // Enables cleanup even if Stop arrives before the acknowledgement.
            owned = target;
            handler.postDelayed(timeout, 2_000);
        } catch (CameraAccessException | RuntimeException e) {
            pending = null;
            pendingGesture = false;
            error = e instanceof SecurityException ? R.string.torch_restricted : R.string.torch_failed;
        }
        listener.onChanged();
    }

    public void turnOffOwned() {
        if (closed || cameraId == null || !owned) return;
        handler.removeCallbacks(timeout);
        pendingGesture = false;
        try {
            pending = false;
            hardware.set(cameraId, false);
            owned = false;
            handler.postDelayed(timeout, 2_000);
        } catch (CameraAccessException | RuntimeException e) {
            pending = null;
            error = R.string.torch_off_failed;
        }
        listener.onChanged();
    }

    public void close() {
        turnOffOwned();
        closed = true;
        callbackGeneration++;
        handler.removeCallbacks(timeout);
        if (registered) hardware.unregister(callback);
    }
}
