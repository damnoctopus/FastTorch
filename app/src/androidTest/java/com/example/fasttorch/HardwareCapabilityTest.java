package com.example.fasttorch;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fasttorch.gesture.GestureConfig;
import com.example.fasttorch.gesture.SensorSource;
import com.example.fasttorch.torch.TorchController;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HardwareCapabilityTest {
    private Context noHardware() {
        return new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Object getSystemService(String name) {
                if (Context.SENSOR_SERVICE.equals(name) || Context.CAMERA_SERVICE.equals(name)) return null;
                return super.getSystemService(name);
            }
        };
    }

    @Test public void absentSensorDisablesDetectionWithoutCrashing() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SensorSource source = new SensorSource(noHardware(), new Handler(Looper.getMainLooper()));
            assertFalse(source.isAvailable());
            assertFalse(source.isWakeUp());
            assertFalse(source.start(GestureConfig.NORMAL, () -> fail("No sensor can emit a gesture")));
            source.stop();
        });
    }

    @Test public void absentCameraReportsUnsupportedWithoutCrashing() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            TorchController controller = new TorchController(noHardware(), new Handler(Looper.getMainLooper()),
                    new TorchController.Listener() {
                        @Override public void onChanged() { }
                        @Override public void onConfirmedGestureToggle() { fail("No flash can toggle"); }
                    });
            controller.initialize();
            assertFalse(controller.hasFlash());
            assertFalse(controller.canToggle());
            assertEquals(TorchController.State.NO_FLASH, controller.state());
            assertEquals(R.string.no_flash, controller.error());
            controller.toggle(false);
            controller.close();
        });
    }
}
