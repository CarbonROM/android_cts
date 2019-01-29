/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package android.server.am.lifecycle;

import static android.server.am.StateLogger.log;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_DESTROY;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_PAUSE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_POST_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESTART;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESUME;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_START;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_STOP;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_TOP_POSITION_GAINED;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_TOP_POSITION_LOST;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.PRE_ON_CREATE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.server.am.lifecycle.LifecycleLog.ActivityCallback;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Util class that verifies correct activity state transition sequences. */
class LifecycleVerifier {

    static void assertLaunchSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, boolean includeCallbacks) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch");

        final List<ActivityCallback> expectedTransitions = getLaunchSequence(includeCallbacks);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    public static List<ActivityCallback> getLaunchSequence(boolean includeCallbacks) {
        return includeCallbacks
                ? Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                ON_TOP_POSITION_GAINED)
                : Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME);
    }

    static void assertLaunchSequence(Class<? extends Activity> launchingActivity,
            Class<? extends Activity> existingActivity, LifecycleLog lifecycleLog,
            boolean launchingIsTranslucent) {
        assertLaunchSequence(launchingActivity, existingActivity, lifecycleLog,
                launchingIsTranslucent, false /* includingCallbacks */);
    }

    static void assertLaunchSequence(Class<? extends Activity> launchingActivity,
            Class<? extends Activity> existingActivity, LifecycleLog lifecycleLog,
            boolean launchingIsTranslucent, boolean includingCallbacks) {
        final List<Pair<String, ActivityCallback>> observedTransitions = lifecycleLog.getLog();
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(launchingActivity, "launch");

        final List<Pair<String, ActivityCallback>> expectedTransitions = new ArrayList<>();
        // First top position will be lost
        if (includingCallbacks) {
            expectedTransitions.add(transition(existingActivity, ON_TOP_POSITION_LOST));
        }
        // Next the existing activity is paused and the next one is launched
        expectedTransitions.add(transition(existingActivity, ON_PAUSE));
        expectedTransitions.add(transition(launchingActivity, PRE_ON_CREATE));
        expectedTransitions.add(transition(launchingActivity, ON_CREATE));
        expectedTransitions.add(transition(launchingActivity, ON_START));
        if (includingCallbacks) {
            expectedTransitions.add(transition(launchingActivity, ON_POST_CREATE));
        }
        expectedTransitions.add(transition(launchingActivity, ON_RESUME));
        if (includingCallbacks) {
            expectedTransitions.add(transition(launchingActivity, ON_TOP_POSITION_GAINED));
        }
        if (!launchingIsTranslucent) {
            expectedTransitions.add(transition(existingActivity, ON_STOP));
        }

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndStopSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        assertLaunchAndStopSequence(activityClass, lifecycleLog, false /* includeCallbacks */,
                false /* onTop */);
    }

    static void assertLaunchAndStopSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, boolean includeCallbacks, boolean onTop) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and stop");

        final List<ActivityCallback> expectedTransitions = new ArrayList<>();
        expectedTransitions.addAll(Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START));
        if (includeCallbacks) {
            expectedTransitions.add(ON_POST_CREATE);
        }
        expectedTransitions.add(ON_RESUME);
        if (includeCallbacks && onTop) {
            expectedTransitions.addAll(Arrays.asList(ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST));
        }
        expectedTransitions.addAll(Arrays.asList(ON_PAUSE, ON_STOP));
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndPauseSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and pause");

        final List<ActivityCallback> expectedTransitions =
                Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRestartSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "restart");

        final List<ActivityCallback> expectedTransitions =
                Arrays.asList(ON_RESTART, ON_START);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRestartAndResumeSequence(Class<? extends Activity> activityClass,
                                              LifecycleLog lifecycleLog) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "restart and pause");

        final List<ActivityCallback> expectedTransitions =
                Arrays.asList(ON_RESTART, ON_START, ON_RESUME);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRecreateAndResumeSequence(Class<? extends Activity> activityClass,
                                              LifecycleLog lifecycleLog) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "recreateA  and pause");

        final List<ActivityCallback> expectedTransitions =
                Arrays.asList(ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndDestroySequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and destroy");

        final List<ActivityCallback> expectedTransitions = Arrays.asList(PRE_ON_CREATE, ON_CREATE,
                ON_START, ON_RESUME, ON_PAUSE, ON_STOP, ON_DESTROY);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertResumeToDestroySequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        assertResumeToDestroySequence(activityClass, lifecycleLog, false /* includeCallbacks */);
    }

    static void assertResumeToDestroySequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, boolean includeCallbacks) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and destroy");

        final List<ActivityCallback> expectedTransitions =
                getResumeToDestroySequence(includeCallbacks);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static List<ActivityCallback> getResumeToDestroySequence(boolean includeCallbacks) {
        return includeCallbacks
                ? Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP, ON_DESTROY)
                : Arrays.asList(ON_PAUSE, ON_STOP, ON_DESTROY);
    }

    static void assertResumeToStopSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        assertResumeToStopSequence(activityClass, lifecycleLog, false /* includeCallbacks */);
    }

    static void assertResumeToStopSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, boolean includeCallbacks) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "resumed to stopped");

        final List<ActivityCallback> expectedTransitions = new ArrayList<>();
        if (includeCallbacks) {
            expectedTransitions.add(ON_TOP_POSITION_LOST);
        }
        expectedTransitions.add(ON_PAUSE);
        expectedTransitions.add(ON_STOP);

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertStopToResumeSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, boolean includeCallbacks) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "stopped to resumed");

        final List<ActivityCallback> expectedTransitions = new ArrayList<>(
                Arrays.asList(ON_RESTART, ON_START, ON_RESUME));
        if (includeCallbacks) {
            expectedTransitions.add(ON_TOP_POSITION_GAINED);
        }

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRelaunchSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, ActivityCallback startState) {
        final List<ActivityCallback> expectedTransitions;
        if (startState == ON_PAUSE) {
            expectedTransitions = Arrays.asList(
                    ON_STOP, ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE);
        } else if (startState == ON_STOP) {
            expectedTransitions = Arrays.asList(
                    ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME, ON_PAUSE, ON_STOP);
        } else if (startState == ON_RESUME) {
            expectedTransitions = Arrays.asList(
                    ON_PAUSE, ON_STOP, ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME);
        } else if (startState == ON_TOP_POSITION_GAINED) {
            // Looks like we're tracking the callbacks here
            expectedTransitions = Arrays.asList(
                    ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP, ON_DESTROY, PRE_ON_CREATE, ON_CREATE,
                    ON_START, ON_POST_CREATE, ON_RESUME, ON_TOP_POSITION_GAINED);
        } else {
            throw new IllegalArgumentException("Start state not supported: " + startState);
        }
        assertSequence(activityClass, lifecycleLog, expectedTransitions, "relaunch");
    }

    static void assertSequence(Class<? extends Activity> activityClass, LifecycleLog lifecycleLog,
            List<ActivityCallback> expectedTransitions, String transition) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, transition);

        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    /**
     * Assert that the observed transitions of a particular activity happened in expected order.
     * There may be more observed transitions than in the expected array, only their order matters.
     *
     * Use this method when there is no need to verify the entire sequence, only that some
     * transitions happened after another.
     */
    static void assertOrder(LifecycleLog lifecycleLog, Class<? extends Activity> activityClass,
            List<LifecycleLog.ActivityCallback> expectedTransitionsOrder, String transition) {
        List<Pair<String, LifecycleLog.ActivityCallback>> expectedTransitions = new ArrayList<>();
        for (LifecycleLog.ActivityCallback callback : expectedTransitionsOrder) {
            expectedTransitions.add(transition(activityClass, callback));
        }
        assertOrder(lifecycleLog, expectedTransitions, transition);
    }

    /**
     * Assert that the observed transitions happened in expected order. There may be more observed
     * transitions than in the expected array, only their order matters.
     *
     * Use this method when there is no need to verify the entire sequence, only that some
     * transitions happened after another.
     */
    static void assertOrder(LifecycleLog lifecycleLog,
            List<Pair<String, LifecycleLog.ActivityCallback>> expectedTransitionsOrder,
            String transition) {
        final List<Pair<String, LifecycleLog.ActivityCallback>> observedTransitions =
                lifecycleLog.getLog();
        int nextObservedPosition = 0;
        for (Pair<String, LifecycleLog.ActivityCallback> expectedTransition
                : expectedTransitionsOrder) {
            while (nextObservedPosition < observedTransitions.size()
                    && !observedTransitions.get(nextObservedPosition).equals(expectedTransition))
            {
                nextObservedPosition++;
            }
            if (nextObservedPosition == observedTransitions.size()) {
                fail("Transition wasn't observed in the expected position: " + expectedTransition
                        + " during transition: " + transition);
            }
        }
    }

    /**
     * Assert that a transition was observer, no particular order.
     */
    static void assertTransitionObserved(LifecycleLog lifecycleLog,
            Pair<String, LifecycleLog.ActivityCallback> expectedTransition, String transition) {
        assertTrue("Transition " + expectedTransition + " must be observed during " + transition,
                lifecycleLog.getLog().contains(expectedTransition));
    }

    static void assertEmptySequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, String transition) {
        assertSequence(activityClass, lifecycleLog, new ArrayList<>(), transition);
    }

    /** Assert that a lifecycle sequence matches one of the possible variants. */
    static void assertSequenceMatchesOneOf(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog, List<List<ActivityCallback>> expectedTransitions,
            String transition) {
        final List<ActivityCallback> observedTransitions =
                lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, transition);

        boolean oneOfExpectedSequencesObserved = false;
        for (List<ActivityCallback> transitionVariant : expectedTransitions) {
            if (transitionVariant.equals(observedTransitions)) {
                oneOfExpectedSequencesObserved = true;
                break;
            }
        }
        assertTrue(errorMessage + "\nObserved transitions: " + observedTransitions
                        + "\nExpected one of: " + expectedTransitions,
                oneOfExpectedSequencesObserved);
    }

    /** Assert the entire sequence for all involved activities. */
    static void assertEntireSequence(
            List<Pair<String, LifecycleLog.ActivityCallback>> expectedTransitions,
            LifecycleLog lifecycleLog, String message) {
        final List<Pair<String, LifecycleLog.ActivityCallback>> observedTransitions =
                lifecycleLog.getLog();
        assertEquals(message, expectedTransitions, observedTransitions);
    }

    static Pair<String, ActivityCallback> transition(Class<? extends Activity> activityClass,
            ActivityCallback state) {
        return new Pair<>(activityClass.getCanonicalName(), state);
    }

    private static String errorDuringTransition(Class<? extends Activity> activityClass,
            String transition) {
        return "Failed verification during moving activity: " + activityClass.getCanonicalName()
                + " through transition: " + transition;
    }
}
