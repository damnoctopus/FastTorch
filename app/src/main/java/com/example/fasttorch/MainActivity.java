package com.example.fasttorch;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.fasttorch.gesture.GestureConfig;
import com.example.fasttorch.service.GestureMonitoringService;
import com.example.fasttorch.settings.SettingsRepository;
import com.example.fasttorch.torch.TorchController;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

public class MainActivity extends AppCompatActivity {
    private GestureMonitoringService service;
    private SettingsRepository settings;
    private MaterialSwitch monitorSwitch;
    private MaterialButton manualButton, practiceButton;
    private boolean bound, rendering, visible, enableAfterPermission, starting;
    private int activityError;
    private final Runnable observer = () -> { starting = false; render(); };
    private final ActivityResultLauncher<String> notifications = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (enableAfterPermission && visible) startMonitoring();
                enableAfterPermission = false;
                render();
            });
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((GestureMonitoringService.LocalBinder) binder).service();
            service.addObserver(observer);
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service = null;
            render();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        settings = new SettingsRepository(this);
        monitorSwitch = findViewById(R.id.monitor_switch);
        manualButton = findViewById(R.id.manual_button);
        practiceButton = findViewById(R.id.practice_button);
        monitorSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (rendering || service == null) return;
            activityError = 0;
            if (enabled) requestMonitoring();
            else service.stopMonitoring();
            render();
        });
        manualButton.setOnClickListener(v -> { if (service != null) service.toggleManually(); });
        findViewById(R.id.retry_torch).setOnClickListener(v -> {
            if (service != null) service.reconnectTorch();
        });
        practiceButton.setOnClickListener(v -> {
            if (service != null) service.setPractice(!service.isPracticing());
        });
        MaterialButtonToggleGroup sensitivity = findViewById(R.id.sensitivity_group);
        GestureConfig config = settings.sensitivity();
        sensitivity.check(config == GestureConfig.LOW ? R.id.sensitivity_low
                : config == GestureConfig.HIGH ? R.id.sensitivity_high : R.id.sensitivity_normal);
        sensitivity.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            GestureConfig chosen = checkedId == R.id.sensitivity_low ? GestureConfig.LOW
                    : checkedId == R.id.sensitivity_high ? GestureConfig.HIGH : GestureConfig.NORMAL;
            settings.setSensitivity(chosen);
            if (service != null) service.setSensitivity(chosen);
        });
        MaterialSwitch vibration = findViewById(R.id.vibration_switch);
        vibration.setChecked(settings.vibration());
        vibration.setOnCheckedChangeListener((button, checked) -> settings.setVibration(checked));
        findViewById(R.id.notification_settings).setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())));
    }

    @Override protected void onStart() {
        super.onStart();
        visible = true;
        bound = bindService(new Intent(this, GestureMonitoringService.class), connection, Context.BIND_AUTO_CREATE);
        if (!bound) activityError = R.string.service_failed;
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    @Override protected void onStop() {
        visible = false;
        enableAfterPermission = false;
        starting = false;
        if (service != null) {
            service.removeObserver(observer);
            service.endVisibleSession();
        }
        if (bound) unbindService(connection);
        bound = false;
        service = null;
        super.onStop();
    }

    private void requestMonitoring() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !getPreferences(MODE_PRIVATE).getBoolean("notification_asked", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply();
            enableAfterPermission = true;
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startMonitoring();
        }
    }

    private void startMonitoring() {
        if (!visible || service == null) return;
        starting = true;
        render();
        try {
            ContextCompat.startForegroundService(this, new Intent(this, GestureMonitoringService.class)
                    .setAction(GestureMonitoringService.ACTION_ENABLE));
        } catch (RuntimeException e) {
            starting = false;
            activityError = R.string.service_failed;
            render();
        }
    }

    private void render() {
        if (monitorSwitch == null) return;
        rendering = true;
        boolean connected = service != null;
        boolean hasSensor = connected && service.sensors().isAvailable();
        boolean hasFlash = connected && service.torch().hasFlash();
        boolean monitoring = connected && service.isMonitoring();
        boolean practice = connected && service.isPracticing();
        monitorSwitch.setChecked(monitoring || starting);
        monitorSwitch.setEnabled(hasSensor && hasFlash && !enableAfterPermission && !starting);
        text(R.id.monitor_status, !connected ? R.string.monitoring_disconnected
                : !hasSensor ? R.string.no_sensor
                : enableAfterPermission || starting ? R.string.monitoring_starting
                : practice ? R.string.practice_monitoring
                : monitoring ? R.string.monitoring_on : R.string.monitoring_off);
        TorchController.State state = connected ? service.torch().state() : TorchController.State.UNKNOWN;
        boolean on = state == TorchController.State.ON;
        text(R.id.torch_status, on ? R.string.torch_on : state == TorchController.State.OFF ? R.string.torch_off
                : state == TorchController.State.UNKNOWN ? R.string.torch_unknown : R.string.torch_unavailable);
        manualButton.setEnabled(connected && service.torch().canToggle() && !practice);
        manualButton.setAlpha(manualButton.isEnabled() ? 1f : 0.45f);
        manualButton.setText(connected && service.torch().pending() ? R.string.torch_pending
                : on ? R.string.turn_off : R.string.turn_on);
        findViewById(R.id.torch_icon).setAlpha(on ? 1f : 0.65f);
        practiceButton.setEnabled(hasSensor);
        practiceButton.setText(practice ? R.string.practice_stop : R.string.practice);
        TextView practiceStatus = findViewById(R.id.practice_status);
        practiceStatus.setVisibility(practice ? View.VISIBLE : View.GONE);
        if (practice) practiceStatus.setText(service.practiceHint());
        TextView practiceCount = findViewById(R.id.practice_count);
        practiceCount.setVisibility(practice && service.practiceCount() > 0 ? View.VISIBLE : View.GONE);
        if (practice) practiceCount.setText(getString(R.string.practice_success, service.practiceCount()));
        findViewById(R.id.retry_torch).setVisibility(connected && service.torch().error() != 0
                && state != TorchController.State.NO_FLASH ? View.VISIBLE : View.GONE);
        findViewById(R.id.retry_torch).setEnabled(connected && !service.torch().pending());
        int error = activityError != 0 ? activityError : connected && service.message() != 0
                ? service.message() : connected ? service.torch().error() : 0;
        TextView errorView = findViewById(R.id.error_message);
        errorView.setVisibility(error == 0 ? View.GONE : View.VISIBLE);
        if (error != 0) errorView.setText(error);
        text(R.id.hardware_status, !connected ? R.string.device_checking
                : !hasFlash && state != TorchController.State.NO_FLASH ? R.string.device_flash_unknown
                : !hasSensor && !hasFlash ? R.string.no_hardware
                : !hasSensor ? R.string.no_sensor : !hasFlash ? R.string.device_sensor_only : R.string.device_ready);
        findViewById(R.id.power_status).setVisibility(hasSensor ? View.VISIBLE : View.GONE);
        findViewById(R.id.sensor_details).setVisibility(hasSensor ? View.VISIBLE : View.GONE);
        if (hasSensor) {
            text(R.id.power_status, service.sensors().isWakeUp() ? R.string.wake_sensor_found : R.string.no_wake_sensor);
            ((TextView) findViewById(R.id.sensor_details)).setText(getString(R.string.sensor_details, service.sensors().sensorName()));
        }
        boolean notificationsOff = !NotificationManagerCompat.from(this).areNotificationsEnabled();
        findViewById(R.id.notification_warning).setVisibility(notificationsOff ? View.VISIBLE : View.GONE);
        findViewById(R.id.notification_settings).setVisibility(notificationsOff ? View.VISIBLE : View.GONE);
        rendering = false;
    }

    private void text(int viewId, int stringId) { ((TextView) findViewById(viewId)).setText(stringId); }
}
