package com.example.fasttorch.torch;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;

/** Small platform boundary, allowing command/callback races to be tested without a flash. */
interface TorchHardware {
    String findRearFlash() throws CameraAccessException;
    void register(CameraManager.TorchCallback callback, Handler handler);
    void unregister(CameraManager.TorchCallback callback);
    void set(String cameraId, boolean enabled) throws CameraAccessException;

    final class Camera2 implements TorchHardware {
        private final CameraManager manager;

        Camera2(Context context) { manager = context.getSystemService(CameraManager.class); }

        @Override public String findRearFlash() throws CameraAccessException {
            if (manager == null) return null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                if (Boolean.TRUE.equals(c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                        && Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK)
                        .equals(c.get(CameraCharacteristics.LENS_FACING))) return id;
            }
            return null;
        }
        @Override public void register(CameraManager.TorchCallback callback, Handler handler) {
            manager.registerTorchCallback(callback, handler);
        }
        @Override public void unregister(CameraManager.TorchCallback callback) {
            manager.unregisterTorchCallback(callback);
        }
        @Override public void set(String cameraId, boolean enabled) throws CameraAccessException {
            manager.setTorchMode(cameraId, enabled);
        }
    }
}
