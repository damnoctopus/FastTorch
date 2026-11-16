package com.example.fasttorch.gesture;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChopGestureDetectorTest {
    private static final class Trace {
        final ChopGestureDetector detector;
        long time;
        int events;
        int step = 20;

        Trace() { this(GestureConfig.NORMAL); }
        Trace(GestureConfig config) { detector = new ChopGestureDetector(config); quiet(160); }
        void sample(float x, float y, float z) {
            if (detector.accept(time * 1_000_000, x, y, z)) events++;
            time += step;
        }
        void hold(int ms, float x, float y, float z) {
            for (int t = 0; t < ms; t += step) sample(x, y, z);
        }
        void quiet(int ms) { hold(ms, 0, 0, 0); }
        void stroke(float x, float y, float z) {
            hold(60, x, y, z);
            hold(60, -x * 0.7f, -y * 0.7f, -z * 0.7f);
            quiet(100);
        }
        void pair() { stroke(24, 0, 0); quiet(80); stroke(24, 0, 0); }
    }

    @Test public void twoStrokesProduceExactlyOneEvent() {
        Trace t = new Trace(); t.pair(); t.quiet(600);
        assertEquals(1, t.events);
    }
    @Test public void singleStrokeWithBrakingAndReboundIsNotTwoChops() {
        Trace t = new Trace();
        t.hold(60, 24, 0, 0); t.hold(60, -24, 0, 0);
        t.hold(40, 18, 0, 0); t.hold(40, -12, 0, 0); t.quiet(1200);
        assertEquals(0, t.events);
    }
    @Test public void cooldownRejectsAnotherPairThenRearms() {
        Trace t = new Trace(); t.pair(); t.pair();
        assertEquals(1, t.events);
        t.quiet(1200); t.pair(); assertEquals(2, t.events);
    }
    @Test public void longPairGapDoesNotTrigger() {
        Trace t = new Trace(); t.stroke(24, 0, 0); t.quiet(1000); t.stroke(24, 0, 0);
        assertEquals(0, t.events);
    }
    @Test public void unrelatedDirectionsDoNotPair() {
        Trace t = new Trace(); t.stroke(24, 0, 0); t.stroke(0, 24, 0);
        assertEquals(0, t.events);
    }
    @Test public void unbrakedImpactsDoNotCountAsStrokes() {
        Trace t = new Trace();
        for (int i = 0; i < 8; i++) { t.hold(40, 24, 0, 0); t.quiet(160); }
        assertEquals(0, t.events);
    }
    @Test public void stationaryAndLowAmplitudeWalkingDoNotTrigger() {
        Trace t = new Trace();
        for (int i = 0; i < 1500; i++) t.sample((float) Math.sin(i * 0.2) * 4, 2, 1);
        assertEquals(0, t.events);
    }
    @Test public void supportedAxesAndSamplingRatesRecognizePairs() {
        for (int step : new int[]{10, 20, 30, 40}) {
            for (float[] axis : new float[][]{{24, 0, 0}, {0, -24, 0}, {0, 0, 24}, {17, 17, 0}}) {
                Trace t = new Trace(); t.step = step;
                t.stroke(axis[0], axis[1], axis[2]); t.quiet(80);
                t.stroke(axis[0], axis[1], axis[2]);
                assertEquals("step=" + step, 1, t.events);
            }
        }
    }
    @Test public void sampleDiscontinuityCannotCompleteAnOldPair() {
        Trace t = new Trace(); t.stroke(24, 0, 0); t.time += 500; t.quiet(100); t.stroke(24, 0, 0);
        assertEquals(0, t.events);
    }
    @Test public void invalidAndOutOfOrderSamplesResetCandidate() {
        Trace t = new Trace(); t.stroke(24, 0, 0); t.sample(Float.NaN, 0, 0);
        t.quiet(100); t.stroke(24, 0, 0); assertEquals(0, t.events);
        assertFalse(t.detector.accept(1, 24, 0, 0));
    }
    @Test public void continuousShakingWithoutQuietCannotPair() {
        Trace t = new Trace();
        for (int i = 0; i < 12; i++) { t.hold(60, 24, 0, 0); t.hold(60, -24, 0, 0); }
        t.quiet(100); assertEquals(0, t.events);
    }
    @Test public void highSensitivityAcceptsSmallerStrokesWhileLowRejects() {
        Trace high = new Trace(GestureConfig.HIGH), low = new Trace(GestureConfig.LOW);
        for (Trace t : new Trace[]{high, low}) { t.stroke(12, 0, 0); t.quiet(80); t.stroke(12, 0, 0); }
        assertEquals(1, high.events); assertEquals(0, low.events);
    }
    @Test public void resetForPracticeOrSettingsClearsFirstStroke() {
        Trace t = new Trace(); t.stroke(24, 0, 0); t.detector.reset(); t.quiet(100); t.stroke(24, 0, 0);
        assertEquals(0, t.events);
    }

    @Test public void practiceExplainsWeakMovementWithoutAcceptingIt() {
        Trace t = new Trace();
        t.stroke(8, 0, 0);
        assertEquals(ChopGestureDetector.Feedback.TOO_WEAK, t.detector.feedback());
        assertEquals(0, t.events);
    }

    @Test public void practiceTracksFirstStrokeAndSuccessfulPair() {
        Trace t = new Trace();
        t.stroke(24, 0, 0);
        assertEquals(ChopGestureDetector.Feedback.FIRST_STROKE, t.detector.feedback());
        t.quiet(80);
        t.stroke(24, 0, 0);
        assertEquals(ChopGestureDetector.Feedback.DETECTED, t.detector.feedback());
        assertEquals(1, t.events);
    }

    @Test public void practiceExplainsExpiredAndMisdirectedPairs() {
        Trace late = new Trace();
        late.stroke(24, 0, 0);
        late.quiet(1300);
        assertEquals(ChopGestureDetector.Feedback.TOO_SLOW, late.detector.feedback());
        Trace direction = new Trace();
        direction.stroke(24, 0, 0);
        direction.stroke(0, 24, 0);
        assertEquals(ChopGestureDetector.Feedback.DIRECTION, direction.detector.feedback());
        assertEquals(0, direction.events);
    }

    @Test public void incompleteStrokeHasFeedbackAndResetClearsIt() {
        Trace t = new Trace();
        t.hold(60, 24, 0, 0);
        t.quiet(100);
        assertEquals(ChopGestureDetector.Feedback.INCOMPLETE, t.detector.feedback());
        t.detector.reset();
        assertEquals(ChopGestureDetector.Feedback.READY, t.detector.feedback());
    }
}
