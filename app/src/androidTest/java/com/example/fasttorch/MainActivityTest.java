package com.example.fasttorch;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.view.View;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import org.hamcrest.Matcher;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.fasttorch.gesture.GestureConfig;
import com.example.fasttorch.settings.SettingsRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    /** Exercise touch scrolling; requestRectangleOnScreen is unreliable on this API 37 emulator. */
    private static ViewAction reveal() {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return withEffectiveVisibility(Visibility.VISIBLE);
            }
            @Override public String getDescription() { return "swipe until the control is visible"; }
            @Override public void perform(UiController ui, View target) {
                View scroll = target.getRootView().findViewById(R.id.main);
                for (int attempt = 0; attempt < 10; attempt++) {
                    if (isDisplayingAtLeast(90).matches(target)) return;
                    int[] targetPosition = new int[2];
                    int[] scrollPosition = new int[2];
                    target.getLocationOnScreen(targetPosition);
                    scroll.getLocationOnScreen(scrollPosition);
                    ViewAction swipe = targetPosition[1] < scrollPosition[1] + scroll.getPaddingTop()
                            ? swipeDown() : swipeUp();
                    swipe.perform(ui, scroll);
                    // A fling can continue after Espresso's main queue becomes idle.
                    int stableSamples = 0;
                    int previousScroll = scroll.getScrollY();
                    for (int sample = 0; sample < 25 && stableSamples < 3; sample++) {
                        ui.loopMainThreadForAtLeast(100);
                        int currentScroll = scroll.getScrollY();
                        stableSamples = currentScroll == previousScroll ? stableSamples + 1 : 0;
                        previousScroll = currentScroll;
                    }
                }
                throw new AssertionError("Control remained outside the viewport after touch scrolling");
            }
        };
    }

    @Before public void resetPreferences() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new SettingsRepository(context).setSensitivity(GestureConfig.NORMAL);
        new SettingsRepository(context).setVibration(false);
    }

    @Test public void sensitivityAndVibrationSurviveActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.sensitivity_high)).perform(reveal(), click());
            onView(withId(R.id.vibration_switch)).perform(reveal(), click());
            scenario.recreate();
            onView(withId(R.id.sensitivity_high)).perform(reveal()).check(matches(isChecked()));
            onView(withId(R.id.vibration_switch)).perform(reveal()).check(matches(isChecked()));
        }
    }

    @Test public void practiceDoesNotEnableBackgroundMonitoring() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SensorManager sensors = context.getSystemService(SensorManager.class);
        assumeTrue(sensors != null && sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.practice_button)).perform(reveal(), click());
            onView(withId(R.id.practice_status)).perform(reveal()).check(matches(withText(R.string.practice_ready)));
            onView(withId(R.id.monitor_switch)).perform(reveal()).check(matches(not(isChecked())));
            onView(withId(R.id.manual_button)).perform(reveal()).check(matches(not(isEnabled())));
            onView(withId(R.id.practice_button)).perform(reveal(), click());
            onView(withId(R.id.practice_status)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }
}
