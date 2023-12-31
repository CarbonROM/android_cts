/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.app.Components.BROADCAST_RECEIVER_ACTIVITY;
import static android.server.wm.app.Components.DISMISS_KEYGUARD_ACTIVITY;
import static android.server.wm.app.Components.DISMISS_KEYGUARD_METHOD_ACTIVITY;
import static android.server.wm.app.Components.INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY;
import static android.server.wm.app.Components.INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY;
import static android.server.wm.app.Components.INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY;
import static android.server.wm.app.Components.KEYGUARD_LOCK_ACTIVITY;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ATTR_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_DIALOG_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY;
import static android.server.wm.app.Components.SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TURN_SCREEN_ON_ACTIVITY;
import static android.server.wm.app.Components.TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY;
import static android.server.wm.app.Components.TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.app.KeyguardManager.KeyguardLockedStateListener;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CommandSession.ActivitySession;
import android.server.wm.CommandSession.ActivitySessionClient;
import android.server.wm.app.Components;

import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:KeyguardTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class KeyguardTests extends KeyguardTestBase {
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(supportsInsecureLock());
        assertFalse(isUiModeLockedToVrHeadset());
    }

    @Test
    public void testKeyguardHidesActivity() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(TEST_ACTIVITY);
        mWmState.computeState(TEST_ACTIVITY);
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndNotOccluded();
        assertTrue(mKeyguardManager.isKeyguardLocked());
        mWmState.assertVisibility(TEST_ACTIVITY, false);

        mObjectTracker.close(lockScreenSession);
        assertFalse(mKeyguardManager.isKeyguardLocked());
    }

    @Test
    @FlakyTest(bugId = 110276714)
    public void testShowWhenLockedActivity() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    /**
     * Tests whether dialogs from SHOW_WHEN_LOCKED activities are also visible if Keyguard is
     * showing.
     */
    @Test
    public void testShowWhenLockedActivity_withDialog() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY);
        mWmState.waitFor((wmState) -> wmState.allWindowSurfacesShown(
                getWindowName(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY)),
                "Wait for all windows visible for " + SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY, true);
        assertTrue(mWmState.allWindowSurfacesShown(
                getWindowName(SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY)));
        mWmState.assertKeyguardShowingAndOccluded();
    }

    /**
     * Tests whether multiple SHOW_WHEN_LOCKED activities are shown if the topmost is translucent.
     */
    @Test
    public void testMultipleShowWhenLockedActivities() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        launchActivity(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY,
                SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(
                SHOW_WHEN_LOCKED_ACTIVITY, SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.computeState();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY, true);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    /**
     * Tests that when top SHOW_WHEN_LOCKED activity is finishing and the next one is also
     * SHOW_WHEN_LOCKED, it should be able to resume next SHOW_WHEN_LOCKED activity.
     */
    @Test
    public void testFinishMultipleShowWhenLockedActivities() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final ActivitySessionClient activitySession = createManagedActivityClientSession();

        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        final ActivitySession showWhenLockedActivitySession =
                activitySession.startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setNewTask(true)
                        .setMultipleTask(true)
                        .setTargetActivity(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY));

        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY, SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY, true);
        mWmState.assertFocusedActivity("Launching Activity must be focused",
                SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY);

        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY, true);
        mWmState.assertFocusedActivity("Top activity stay un-change",
                SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY);
        mWmState.assertKeyguardShowingAndOccluded();

        showWhenLockedActivitySession.finish();
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertFocusedActivity("ShowWhenLocked activity must occludes keyguard",
                SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED_ACTIVITY, the wallpaper should also be showing.
     */
    @Test
    public void testTranslucentShowWhenLockedActivity() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY, true);
        mWmState.waitAndAssertWindowShown(TYPE_WALLPAPER, true);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    /**
     * If we have a translucent SHOW_WHEN_LOCKED activity, the activity behind should not be shown.
     */
    @Test
    @FlakyTest
    public void testTranslucentDoesntRevealBehind() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(TEST_ACTIVITY);
        launchActivity(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.computeState(TEST_ACTIVITY, SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY);
        mWmState.computeState();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY, true);
        mWmState.assertVisibility(TEST_ACTIVITY, false);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    @Test
    public void testDialogShowWhenLockedActivity() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_DIALOG_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_DIALOG_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_DIALOG_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_DIALOG_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_DIALOG_ACTIVITY, true);
        mWmState.waitAndAssertWindowShown(TYPE_WALLPAPER, true);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    /**
     * Test that showWhenLocked activity is fullscreen when shown over keyguard
     */
    @Test
    @Presubmit
    // TODO (b/169271554): Temporarily switch activity to fullscreen when needing to showWhenLocked
    @Ignore
    public void testShowWhenLockedActivityWhileSplit() {
        assumeTrue(supportsSplitScreenMultiWindow());

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(SHOW_WHEN_LOCKED_ACTIVITY)
                        .setRandomData(true)
                        .setMultipleTask(false));
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertKeyguardShowingAndOccluded();
        WindowManagerState.Activity activity = mWmState.getActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        assertFalse(activity.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW);
    }

    /**
     * Tests whether an activity that has called setInheritShowWhenLocked(true) above a
     * SHOW_WHEN_LOCKED activity is visible if Keyguard is locked.
     */
    @Test
    @FlakyTest
    public void testInheritShowWhenLockedAdd() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);

        launchActivity(INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY);
        mWmState.computeState(
                SHOW_WHEN_LOCKED_ATTR_ACTIVITY, INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY, true);

        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndOccluded();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY, true);
    }

    /**
     * Tests whether an activity that has the manifest attribute inheritShowWhenLocked but then
     * calls setInheritShowWhenLocked(false) above a SHOW_WHEN_LOCKED activity is invisible if
     * Keyguard is locked.
     */
    @Test
    @FlakyTest
    public void testInheritShowWhenLockedRemove() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);

        launchActivity(INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY);
        mWmState.computeState(
                SHOW_WHEN_LOCKED_ATTR_ACTIVITY, INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY, true);

        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndNotOccluded();
        assertTrue(mKeyguardManager.isKeyguardLocked());
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY, false);
    }

    /**
     * Tests whether an activity that has the manifest attribute inheritShowWhenLocked above a
     * SHOW_WHEN_LOCKED activity is visible if Keyguard is locked.
     * */
    @Test
    @FlakyTest
    public void testInheritShowWhenLockedAttr() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);

        launchActivity(INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.computeState(
                SHOW_WHEN_LOCKED_ATTR_ACTIVITY, INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);

        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndOccluded();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);
    }

    /**
     * Tests whether an activity that doesn't have the manifest attribute inheritShowWhenLocked
     * above a SHOW_WHEN_LOCKED activity is invisible if Keyguard is locked.
     * */
    @Test
    @FlakyTest
    public void testNoInheritShowWhenLocked() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        launchActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);

        launchActivity(NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.computeState(
                SHOW_WHEN_LOCKED_ATTR_ACTIVITY, NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY, true);

        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        mWmState.assertKeyguardShowingAndNotOccluded();
        assertTrue(mKeyguardManager.isKeyguardLocked());
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
        mWmState.assertVisibility(NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY, false);
    }

    @Test
    public void testNoTransientConfigurationWhenShowWhenLockedRequestsOrientation() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final ActivitySessionClient activitySession = createManagedActivityClientSession();

        final ActivitySession showWhenLockedActivitySession =
                activitySession.startActivity(getLaunchActivityBuilder()
                        .setUseInstrumentation()
                        .setTargetActivity(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY));
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY, true);

        lockScreenSession.gotoKeyguard(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY);

        separateTestJournal();

        final int displayId = mWmState
                .getDisplayByActivity(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY);
        WindowManagerState.DisplayContent display = mWmState
                .getDisplay(displayId);
        final int origDisplayOrientation = display.mFullConfiguration.orientation;
        final int orientation = origDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE
                ? SCREEN_ORIENTATION_PORTRAIT
                : SCREEN_ORIENTATION_LANDSCAPE;
        showWhenLockedActivitySession.requestOrientation(orientation);

        mWmState.waitForActivityOrientation(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY,
                orientation == SCREEN_ORIENTATION_LANDSCAPE
                        ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT);

        display = mWmState.getDisplay(displayId);

        // If the window is a non-fullscreen window (e.g. a freeform window) or the display is
        // squared, there won't be activity lifecycle.
        if (display.mFullConfiguration.orientation != origDisplayOrientation) {
            assertActivityLifecycle(SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY,
                    false /* relaunched */);
        }
    }

    /**
     * Test that when a normal activity finished and an existing FLAG_DISMISS_KEYGUARD activity
     * becomes the top activity, it should be resumed.
     */
    @Test
    @FlakyTest
    public void testResumeDismissKeyguardActivityFromBackground() {
        testResumeOccludingActivityFromBackground(DISMISS_KEYGUARD_ACTIVITY);
    }

    /**
     * Test that when a normal activity finished and an existing SHOW_WHEN_LOCKED activity becomes
     * the top activity, it should be resumed.
     */
    @Test
    public void testResumeShowWhenLockedActivityFromBackground() {
        testResumeOccludingActivityFromBackground(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
    }

    private void testResumeOccludingActivityFromBackground(ComponentName occludingActivity) {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();

        // Launch an activity which is able to occlude keyguard.
        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(occludingActivity).execute();

        // Launch an activity without SHOW_WHEN_LOCKED and finish it.
        getLaunchActivityBuilder().setUseInstrumentation()
                .setMultipleTask(true)
                // Don't wait for activity visible because keyguard will show.
                .setWaitForLaunched(false)
                .setTargetActivity(BROADCAST_RECEIVER_ACTIVITY).execute();
        mWmState.waitForKeyguardShowingAndNotOccluded();
        // The activity should be launched in same TDA to ensure that
        // keyguard is showing and not occluded.
        assumeTrue("Should launch in same TDA",
                mWmState.getTaskDisplayArea(occludingActivity)
                        == mWmState.getTaskDisplayArea(BROADCAST_RECEIVER_ACTIVITY)
        );
        mWmState.assertKeyguardShowingAndNotOccluded();

        mBroadcastActionTrigger.finishBroadcastReceiverActivity();
        mWmState.waitForKeyguardShowingAndOccluded();

        // The occluding activity should be resumed because it becomes the top activity.
        mWmState.computeState(occludingActivity);
        mWmState.assertVisibility(occludingActivity, true);
        assertTrue(occludingActivity + " must be resumed.",
                mWmState.hasActivityState(occludingActivity,
                        WindowManagerState.STATE_RESUMED));
    }

    @Test
    public void testTurnScreenOnActivityOnAod() {
        final AodSession aodSession = createManagedAodSession();
        assumeTrue(aodSession.isAodAvailable());
        aodSession.setAodEnabled(true);

        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.sleepDevice();
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);

        final CommandSession.ActivitySessionClient activityClient =
                createManagedActivityClientSession();
        activityClient.startActivity(
                getLaunchActivityBuilder().setUseInstrumentation().setIntentExtra(extra -> {
                    extra.putBoolean(Components.TurnScreenOnActivity.EXTRA_SHOW_WHEN_LOCKED,
                            false);
                }).setTargetActivity(TURN_SCREEN_ON_ACTIVITY));

        mWmState.waitForAllStoppedActivities();
        // An activity without set showWhenLocked or dismissKeyguard cannot wakeup device and/or
        // unlock insecure keyguard even if it has setTurnScreenOn, so the device should stay
        // invisible and the display stay in dozing.
        mWmState.assertVisibility(TURN_SCREEN_ON_ACTIVITY, false);
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);
        assertFalse(isDisplayOn(DEFAULT_DISPLAY));
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity occludes Keyguard.
     */
    @Test
    public void testDismissKeyguardActivity() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);
        launchActivity(DISMISS_KEYGUARD_ACTIVITY);
        mWmState.waitForKeyguardShowingAndOccluded();
        mWmState.computeState(DISMISS_KEYGUARD_ACTIVITY);
        mWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
        mWmState.assertKeyguardShowingAndOccluded();
    }

    @Test
    public void testDismissKeyguardActivity_method() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        separateTestJournal();
        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);
        launchActivity(DISMISS_KEYGUARD_METHOD_ACTIVITY);
        mWmState.waitForKeyguardGone();
        mWmState.computeState(DISMISS_KEYGUARD_METHOD_ACTIVITY);
        mWmState.assertVisibility(DISMISS_KEYGUARD_METHOD_ACTIVITY, true);
        assertFalse(mWmState.getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceeded(DISMISS_KEYGUARD_METHOD_ACTIVITY);
    }

    @Test
    public void testDismissKeyguardActivity_method_notTop() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        separateTestJournal();
        lockScreenSession.gotoKeyguard();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);
        launchActivity(TEST_ACTIVITY);
        mBroadcastActionTrigger.dismissKeyguardByMethod();
        assertOnDismissError(BROADCAST_RECEIVER_ACTIVITY);
    }

    @Test
    public void testDismissKeyguardActivity_method_turnScreenOn() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        separateTestJournal();
        lockScreenSession.sleepDevice();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);
        launchActivity(TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY);
        mWmState.waitForKeyguardGone();
        mWmState.computeState(TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY);
        mWmState.assertVisibility(TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY, true);
        assertFalse(mWmState.getKeyguardControllerState().keyguardShowing);
        assertOnDismissSucceeded(TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY);
        assertTrue(isDisplayOn(DEFAULT_DISPLAY));
    }

    @Test
    public void testDismissKeyguard_fromShowWhenLocked_notAllowed() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
        mWmState.assertKeyguardShowingAndOccluded();
        mBroadcastActionTrigger.dismissKeyguardByFlag();
        mWmState.assertKeyguardShowingAndOccluded();
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);
    }

    @Test
    public void testDismissKeyguard_fromActivityOption_onlyOnce() {
        // TODO(b/228431314): Move this test from CTS to flicker test.
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();

        lockScreenSession.gotoKeyguard();
        launchActivityWithDismissKeyguard(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY, true);

        lockScreenSession.gotoKeyguard();
        assertFalse(mWmState.getKeyguardControllerState().mKeyguardGoingAway);
    }

    @Test
    public void testKeyguardLock() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();
        launchActivity(KEYGUARD_LOCK_ACTIVITY);
        mWmState.computeState(KEYGUARD_LOCK_ACTIVITY);
        mWmState.assertVisibility(KEYGUARD_LOCK_ACTIVITY, true);
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.assertKeyguardShowingAndNotOccluded();
    }


    /**
     * Turn on keyguard, and launch an activity on top of the keyguard.
     * Next, change the orientation of the device to rotate the activity.
     * The activity should still remain above keyguard at this point.
     * Send the 'finish' broadcast to dismiss the activity.
     * Ensure that the activity is gone, and the keyguard is visible.
     */
    @Test
    public void testUnoccludedRotationChange() {
        // Go home now to make sure Home is behind Keyguard.
        launchHomeActivity();
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final RotationSession rotationSession = createManagedRotationSession();
        lockScreenSession.gotoKeyguard();
        mWmState.assertKeyguardShowingAndNotOccluded();

        launchActivity(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.computeState(SHOW_WHEN_LOCKED_ACTIVITY);
        mWmState.assertVisibility(SHOW_WHEN_LOCKED_ACTIVITY, true);

        rotationSession.set(ROTATION_90);
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();
        mWmState.waitForKeyguardShowingAndNotOccluded();
        mWmState.waitForDisplayUnfrozen();
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        mWmState.assertValidity();
        mWmState.assertHomeActivityVisible(false);
        mWmState.assertKeyguardShowingAndNotOccluded();
        // The {@link SHOW_WHEN_LOCKED_ACTIVITY} has gone because of the 'finish' broadcast.
        mWmState.waitAndAssertActivityRemoved(SHOW_WHEN_LOCKED_ACTIVITY);
    }

    @Test
    public void testDismissKeyguardAttrActivity_method_turnScreenOn() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        lockScreenSession.sleepDevice();

        separateTestJournal();
        mWmState.computeState();
        assertTrue(mWmState.getKeyguardControllerState().keyguardShowing);
        launchActivity(TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY);
        mWmState.waitForKeyguardGone();
        mWmState.assertVisibility(TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY, true);
        assertFalse(mWmState.getKeyguardControllerState().keyguardShowing);
        assertTrue(isDisplayOn(DEFAULT_DISPLAY));
    }

    @Test
    public void testScreenOffWhileOccludedStopsActivityNoAod() {
        final AodSession aodSession = createManagedAodSession();
        aodSession.setAodEnabled(false);
        testScreenOffWhileOccludedStopsActivity(false /* assertAod */);
    }

    @Test
    public void testScreenOffWhileOccludedStopsActivityAod() {
        final AodSession aodSession = createManagedAodSession();
        assumeTrue(aodSession.isAodAvailable());
        aodSession.setAodEnabled(true);
        testScreenOffWhileOccludedStopsActivity(true /* assertAod */);
    }

    /**
     * @param assertAod {@code true} to check AOD status, {@code false} otherwise. Note that when
     *        AOD is disabled for the default display, AOD status shouldn't be checked.
     */
    private void testScreenOffWhileOccludedStopsActivity(boolean assertAod) {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            separateTestJournal();
            lockScreenSession.gotoKeyguard();
            mWmState.assertKeyguardShowingAndNotOccluded();
            launchActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
            waitAndAssertTopResumedActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, DEFAULT_DISPLAY,
                    "Activity with showWhenLocked attribute should be resumed.");
            mWmState.assertKeyguardShowingAndOccluded();
            if (assertAod) {
                mWmState.assertAodNotShowing();
            }
            lockScreenSession.sleepDevice();
            if (assertAod) {
                mWmState.assertAodShowing();
            }
            mWmState.waitForAllStoppedActivities();
            assertSingleLaunchAndStop(SHOW_WHEN_LOCKED_ATTR_ACTIVITY);
        }
    }

    @Test
    public void testScreenOffCausesSingleStopNoAod() {
        final AodSession aodSession = createManagedAodSession();
        aodSession.setAodEnabled(false);
        testScreenOffCausesSingleStop();
    }

    @Test
    public void testScreenOffCausesSingleStopAod() {
        final AodSession aodSession = createManagedAodSession();
        assumeTrue(aodSession.isAodAvailable());
        aodSession.setAodEnabled(true);
        testScreenOffCausesSingleStop();
    }

    private void testScreenOffCausesSingleStop() {
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            separateTestJournal();
            launchActivity(TEST_ACTIVITY);
            mWmState.assertVisibility(TEST_ACTIVITY, true);
            lockScreenSession.sleepDevice();
            mWmState.waitForAllStoppedActivities();
            assertSingleLaunchAndStop(TEST_ACTIVITY);
        }

    }

    @Test
    public void testAddKeyguardLockedStateListener_hideToShow_callbackInvoked() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE);
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final KeyguardLockedStateListener listener = getTestKeyguardLockedStateListener();
        mKeyguardManager.addKeyguardLockedStateListener(mContext.getMainExecutor(), listener);

        lockScreenSession.gotoKeyguard();
        lockScreenSession.gotoKeyguard();  // state not changed

        verify(listener, times(1)).onKeyguardLockedStateChanged(true);
        verify(listener, never()).onKeyguardLockedStateChanged(false);

        mKeyguardManager.removeKeyguardLockedStateListener(listener);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testAddKeyguardLockedStateListener_showToHide_callbackInvoked() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE);
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final KeyguardLockedStateListener listener = getTestKeyguardLockedStateListener();
        mKeyguardManager.addKeyguardLockedStateListener(mContext.getMainExecutor(), listener);

        lockScreenSession.gotoKeyguard();
        lockScreenSession.unlockDevice();
        lockScreenSession.unlockDevice();  // state not changed

        verify(listener, times(1)).onKeyguardLockedStateChanged(true);
        verify(listener, times(1)).onKeyguardLockedStateChanged(false);

        mKeyguardManager.removeKeyguardLockedStateListener(listener);
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testAddRemoveKeyguardLockedStateListener_callbackNotInvoked() {
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE);
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final KeyguardLockedStateListener listener = getTestKeyguardLockedStateListener();
        mKeyguardManager.addKeyguardLockedStateListener(mContext.getMainExecutor(), listener);
        mKeyguardManager.removeKeyguardLockedStateListener(listener);

        lockScreenSession.gotoKeyguard();
        lockScreenSession.unlockDevice();
        lockScreenSession.gotoKeyguard();

        verify(listener, never()).onKeyguardLockedStateChanged(anyBoolean());

        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }

    public KeyguardLockedStateListener getTestKeyguardLockedStateListener() {
        return spy(new TestKeyguardLockedStateListener());
    }

    public static class TestKeyguardLockedStateListener implements KeyguardLockedStateListener {
        @Override
        public void onKeyguardLockedStateChanged(boolean isKeyguardLocked) {
        }
    }
}
