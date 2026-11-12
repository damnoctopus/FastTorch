package com.example.fasttorch.gesture;

import org.junit.Test;
import static org.junit.Assert.*;

public class GravityFilterTest {
    @Test public void stationaryGravityIsRemovedInAnyOrientation() {
        for (float[] gravity : new float[][]{{0, 0, 9.81f}, {9.81f, 0, 0}, {0, -9.81f, 0}}) {
            GravityFilter filter = new GravityFilter();
            float[] result = null;
            for (long t = 0; t <= 1000; t += 20) result = filter.filter(t * 1_000_000, gravity[0], gravity[1], gravity[2]);
            assertNotNull(result);
            assertArrayEquals(new float[]{0, 0, 0}, result, 0.001f);
        }
    }
    @Test public void startupAndSampleGapsWarmUpBeforeEmitting() {
        GravityFilter filter = new GravityFilter();
        assertNull(filter.filter(0, 0, 0, 9.81f));
        assertNull(filter.filter(20_000_000, 0, 0, 9.81f));
        assertNull(filter.filter(1_000_000_000, 20, 0, 9.81f));
        assertNull(filter.filter(1_020_000_000, 0, 0, 9.81f));
    }
    @Test public void nonFiniteInputCannotPoisonFutureSamples() {
        GravityFilter filter = new GravityFilter();
        assertNull(filter.filter(0, Float.POSITIVE_INFINITY, 0, 0));
        float[] result = null;
        for (long t = 20; t <= 500; t += 20) result = filter.filter(t * 1_000_000, 0, 0, 9.81f);
        assertNotNull(result);
        assertArrayEquals(new float[]{0, 0, 0}, result, 0.001f);
    }
    @Test public void accelerationSurvivesGravityRemoval() {
        GravityFilter filter = new GravityFilter();
        for (long t = 0; t <= 500; t += 20) filter.filter(t * 1_000_000, 0, 0, 9.81f);
        float[] result = filter.filter(520_000_000, 24, 0, 9.81f);
        assertNotNull(result);
        assertTrue(result[0] > 20);
        assertEquals(0, result[2], 0.001f);
    }

    @Test public void rawAccelerometerPairSurvivesCompletePipeline() {
        GravityFilter filter = new GravityFilter();
        ChopGestureDetector detector = new ChopGestureDetector(GestureConfig.NORMAL);
        int events = 0;
        for (long t = 0; t < 2000; t += 20) {
            float x = 0;
            for (long start : new long[]{600, 940}) {
                if (t >= start && t < start + 60) x = 26;
                if (t >= start + 60 && t < start + 120) x = -20;
            }
            float[] linear = filter.filter(t * 1_000_000, x, 0, 9.81f);
            if (linear != null && detector.accept(t * 1_000_000, linear[0], linear[1], linear[2])) events++;
        }
        assertEquals(1, events);
    }
}
