package com.example.fasttorch.service;

import android.app.Service;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import androidx.core.app.ServiceCompat;
import com.example.fasttorch.R;
import com.example.fasttorch.gesture.GestureConfig;
import com.example.fasttorch.gesture.SensorSource;
import com.example.fasttorch.notification.MonitoringNotification;
import com.example.fasttorch.settings.SettingsRepository;
import com.example.fasttorch.torch.TorchController;
import java.util.HashSet;
import java.util.Set;

/** Bound for manual/practice use; explicitly started and foreground only for monitoring. */
public final class GestureMonitoringService extends Service implements TorchController.Listener {
    public static final String ACTION_ENABLE = "com.example.fasttorch.ENABLE";
    public static final String ACTION_STOP = "com.example.fasttorch.STOP";
    private final LocalBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Runnable> observers = new HashSet<>();
    private SensorSource sensors;
    private TorchController torch;
    private SettingsRepository settings;
    private boolean monitoring, practice, destroyed;
    private int message, practiceCount;
    private long suppressUntil;

    public final class LocalBinder extends Binder {
        public GestureMonitoringService service() { return GestureMonitoringService.this; }
    }

    @Override public void onCreate() {
        super.onCreate();
        settings = new SettingsRepository(this);
        sensors = new SensorSource(this, main);
        torch = new TorchController(this, main, this);
        torch.initialize();
    }

    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_ENABLE.equals(intent.getAction())) {
            enable();
        } else if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
        } else if (!monitoring) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void enable() {
        if (monitoring) return;
        message = 0;
        try {
            // Promote before hardware work, even when a later capability check fails.
            ServiceCompat.startForeground(this, MonitoringNotification.ID,
                    MonitoringNotification.build(this, torch.state() == TorchController.State.ON, false),
                    Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE : 0);
            if (!sensors.isAvailable() || !torch.hasFlash()) {
                message = !sensors.isAvailable() ? R.string.no_sensor : R.string.no_flash;
                stopMonitoringInternal();
            } else {
                monitoring = true;
                if (!restartSensors()) {
                    message = R.string.sensor_failed;
                    stopMonitoringInternal();
                }
            }
        } catch (RuntimeException e) {
            message = R.string.service_failed;
            stopMonitoringInternal();
        }
        onChanged();
    }

    public void stopMonitoring() {
        message = 0;
        stopMonitoringInternal();
        onChanged();
    }

    private void stopMonitoringInternal() {
        monitoring = false;
        practice = false;
        sensors.stop();
        torch.turnOffOwned();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private boolean restartSensors() {
        sensors.stop();
        return (!monitoring && !practice) || sensors.start(settings.sensitivity(), this::onGesture);
    }

    private void onGesture() {
        if (destroyed || (!monitoring && !practice) || SystemClock.elapsedRealtime() < suppressUntil) return;
        if (practice) {
            practiceCount++;
            onChanged();
        } else {
            torch.toggle(true);
        }
    }

    public void setPractice(boolean enabled) {
        practice = enabled && sensors.isAvailable();
        practiceCount = 0;
        message = 0;
        if (!restartSensors()) {
            message = R.string.sensor_failed;
            stopMonitoringInternal();
        }
        onChanged();
    }

    public void setSensitivity(GestureConfig config) {
        settings.setSensitivity(config);
        if (!restartSensors()) {
            message = R.string.sensor_failed;
            stopMonitoringInternal();
        }
        onChanged();
    }

    public void toggleManually() { torch.toggle(false); }

    public void endVisibleSession() {
        if (practice) setPractice(false);
        if (!monitoring) torch.turnOffOwned();
    }

    @Override public boolean onUnbind(Intent intent) {
        endVisibleSession();
        return false;
    }

    public void addObserver(Runnable observer) { observers.add(observer); observer.run(); }
    public void removeObserver(Runnable observer) { observers.remove(observer); }
    public boolean isMonitoring() { return monitoring; }
    public boolean isPracticing() { return practice; }
    public int practiceCount() { return practiceCount; }
    public int message() { return message; }
    public SensorSource sensors() { return sensors; }
    public TorchController torch() { return torch; }

    @Override public void onChanged() {
        if (destroyed) return;
        for (Runnable observer : new HashSet<>(observers)) observer.run();
        if (monitoring) {
            getSystemService(NotificationManager.class).notify(MonitoringNotification.ID,
                    MonitoringNotification.build(this, torch.state() == TorchController.State.ON, practice));
        }
    }

    @Override public void onConfirmedGestureToggle() {
        if (!monitoring || !settings.vibration()) return;
        Vibrator vibrator = getSystemService(Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            suppressUntil = SystemClock.elapsedRealtime() + 1_000;
            vibrator.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        monitoring = false;
        practice = false;
        sensors.stop();
        torch.close();
        observers.clear();
        super.onDestroy();
    }
}
