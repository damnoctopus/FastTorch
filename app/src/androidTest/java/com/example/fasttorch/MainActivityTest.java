package com.example.fasttorch;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
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
    @Before public void resetPreferences() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new SettingsRepository(context).setSensitivity(GestureConfig.NORMAL);
        new SettingsRepository(context).setVibration(false);
    }

    @Test public void sensitivityAndVibrationSurviveActivityRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.sensitivity_high)).perform(scrollTo(), click());
            onView(withId(R.id.vibration_switch)).perform(scrollTo(), click());
            scenario.recreate();
            onView(withId(R.id.sensitivity_high)).perform(scrollTo()).check(matches(isChecked()));
            onView(withId(R.id.vibration_switch)).perform(scrollTo()).check(matches(isChecked()));
        }
    }

    @Test public void practiceDoesNotEnableBackgroundMonitoring() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SensorManager sensors = context.getSystemService(SensorManager.class);
        assumeTrue(sensors != null && sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.practice_button)).perform(scrollTo(), click());
            onView(withId(R.id.practice_status)).perform(scrollTo()).check(matches(withText(R.string.practice_ready)));
            onView(withId(R.id.monitor_switch)).perform(scrollTo()).check(matches(not(isChecked())));
            onView(withId(R.id.manual_button)).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withId(R.id.practice_button)).perform(scrollTo(), click());
            onView(withId(R.id.practice_status)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }
}
