package com.example.fasttorch.torch;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fasttorch.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class TorchControllerTest {
    private static final class FakeHardware implements TorchHardware {
        CameraManager.TorchCallback callback;
        final List<Boolean> requests = new ArrayList<>();
        boolean fail;
        @Override public String findRearFlash() { return "rear-flash"; }
        @Override public void register(CameraManager.TorchCallback c, Handler h) { callback = c; }
        @Override public void unregister(CameraManager.TorchCallback c) { }
        @Override public void set(String id, boolean enabled) throws CameraAccessException {
            if (fail) throw new CameraAccessException(CameraAccessException.CAMERA_IN_USE);
            requests.add(enabled);
        }
        void observed(boolean on) { callback.onTorchModeChanged("rear-flash", on); }
    }
    private static final class Fixture implements TorchController.Listener {
        final FakeHardware hardware = new FakeHardware();
        final TorchController controller = new TorchController(hardware, new Handler(Looper.getMainLooper()), this);
        int confirmations;
        Runnable changed = () -> { };
        Fixture() { controller.initialize(); hardware.observed(false); }
        @Override public void onChanged() { changed.run(); }
        @Override public void onConfirmedGestureToggle() { confirmations++; }
    }

    @Test public void toggleWaitsForConfirmationAndSerializesRequests() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            try {
                f.controller.toggle(true);
                f.controller.toggle(true);
                assertEquals(TorchController.State.OFF, f.controller.state());
                assertFalse(f.controller.canToggle());
                assertEquals(1, f.hardware.requests.size());
                assertEquals(0, f.confirmations);
                f.hardware.observed(true);
                assertEquals(TorchController.State.ON, f.controller.state());
                assertEquals(1, f.confirmations);
                assertTrue(f.controller.canToggle());
            } finally { f.controller.close(); }
        });
    }

    @Test public void failedCommandDoesNotShowSuccessAndCanBeRetried() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            try {
                f.hardware.fail = true;
                f.controller.toggle(true);
                assertEquals(TorchController.State.OFF, f.controller.state());
                assertEquals(R.string.torch_failed, f.controller.error());
                assertEquals(0, f.confirmations);
                assertTrue(f.controller.canToggle());
                f.hardware.fail = false;
                f.controller.toggle(false);
                f.hardware.observed(true);
                assertEquals(TorchController.State.ON, f.controller.state());
            } finally { f.controller.close(); }
        });
    }

    @Test public void stopBeforeOnCallbackStillSendsOffAndIgnoresOldAcknowledgement() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            try {
                f.controller.toggle(true);
                f.controller.turnOffOwned();
                f.hardware.observed(true);
                assertTrue(f.controller.pending());
                assertEquals(0, f.confirmations);
                f.hardware.observed(false);
                assertFalse(f.controller.pending());
                assertEquals(TorchController.State.OFF, f.controller.state());
                assertEquals(Arrays.asList(true, false), f.hardware.requests);
            } finally { f.controller.close(); }
        });
    }

    @Test public void stoppingDoesNotTurnOffAnExternallyEnabledLight() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            f.hardware.observed(true);
            f.controller.turnOffOwned();
            f.controller.close();
            assertTrue(f.hardware.requests.isEmpty());
        });
    }

    @Test public void externalOffThenOnClearsOwnershipAndCameraContentionRecovers() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            try {
                f.controller.toggle(true); f.hardware.observed(true);
                f.hardware.observed(false); f.hardware.observed(true);
                f.controller.turnOffOwned();
                assertEquals(Arrays.asList(true), f.hardware.requests);
                f.hardware.callback.onTorchModeUnavailable("rear-flash");
                assertFalse(f.controller.canToggle());
                assertEquals(TorchController.State.UNAVAILABLE, f.controller.state());
                f.hardware.observed(false);
                assertTrue(f.controller.canToggle());
                assertEquals(0, f.controller.error());
            } finally { f.controller.close(); }
        });
    }

    @Test public void reconnectRecoversAndIgnoresCallbacksFromOldConnection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            try {
                CameraManager.TorchCallback old = f.hardware.callback;
                old.onTorchModeUnavailable("rear-flash");
                f.controller.reconnect();
                assertEquals(TorchController.State.UNKNOWN, f.controller.state());
                old.onTorchModeChanged("rear-flash", true);
                assertEquals(TorchController.State.UNKNOWN, f.controller.state());
                f.hardware.observed(false);
                assertTrue(f.controller.canToggle());
                assertTrue(f.hardware.requests.isEmpty());
            } finally { f.controller.close(); }
        });
    }

    @Test public void reconnectPreservesOwnedLightAndWaitsForPendingCommands() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            try {
                CameraManager.TorchCallback initial = f.hardware.callback;
                f.controller.toggle(false);
                f.controller.reconnect();
                assertSame(initial, f.hardware.callback);
                f.hardware.observed(true);
                f.controller.reconnect();
                f.hardware.observed(true);
                assertEquals(Arrays.asList(true), f.hardware.requests);
                f.controller.turnOffOwned();
                assertEquals(Arrays.asList(true, false), f.hardware.requests);
            } finally { f.controller.close(); }
            CameraManager.TorchCallback last = f.hardware.callback;
            f.controller.reconnect();
            assertSame(last, f.hardware.callback);
        });
    }

    @Test public void timedOutCommandCanReconnectWithoutRestartingService() throws Exception {
        CountDownLatch timedOut = new CountDownLatch(1);
        AtomicReference<Fixture> reference = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Fixture f = new Fixture();
            reference.set(f);
            f.changed = () -> {
                if (f.controller.error() == R.string.torch_timeout) timedOut.countDown();
            };
            f.controller.toggle(false);
        });
        try {
            assertTrue("Expected callback timeout", timedOut.await(5, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                Fixture f = reference.get();
                assertFalse(f.controller.canToggle());
                f.controller.reconnect();
                f.hardware.observed(true);
                assertTrue(f.controller.canToggle());
                assertEquals(0, f.controller.error());
                f.controller.turnOffOwned();
                assertEquals(Arrays.asList(true, false), f.hardware.requests);
            });
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> reference.get().controller.close());
        }
    }
}
