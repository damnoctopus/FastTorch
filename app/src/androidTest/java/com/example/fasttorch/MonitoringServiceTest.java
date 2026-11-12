package com.example.fasttorch;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fasttorch.service.GestureMonitoringService;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MonitoringServiceTest {
    @Test public void explicitStartEitherMonitorsOrReportsMissingHardwareAndStopsCleanly() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch bound = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<GestureMonitoringService> service = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                service.set(((GestureMonitoringService.LocalBinder) binder).service());
                bound.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertTrue(context.bindService(new Intent(context, GestureMonitoringService.class), connection, Context.BIND_AUTO_CREATE));
            try {
                assertTrue("Service binding timed out", bound.await(5, TimeUnit.SECONDS));
                Runnable observer = () -> {
                    if (service.get().isMonitoring() || service.get().message() != 0) started.countDown();
                };
                scenario.onActivity(activity -> {
                    service.get().addObserver(observer);
                    ContextCompat.startForegroundService(activity,
                            new Intent(activity, GestureMonitoringService.class).setAction(GestureMonitoringService.ACTION_ENABLE));
                });
                assertTrue("Service start timed out", started.await(5, TimeUnit.SECONDS));
                scenario.onActivity(activity -> {
                    GestureMonitoringService s = service.get();
                    if (!s.sensors().isAvailable()) assertEquals(R.string.no_sensor, s.message());
                    else if (!s.torch().hasFlash()) assertEquals(R.string.no_flash, s.message());
                    else { assertTrue(s.isMonitoring()); assertEquals(0, s.message()); }
                    s.removeObserver(observer);
                    s.stopMonitoring();
                    assertFalse(s.isMonitoring());
                    assertFalse(s.isPracticing());
                });
            } finally {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    if (service.get() != null) service.get().stopMonitoring();
                });
                context.unbindService(connection);
            }
        }
    }
}
