/*
* Copyright (C) 2019 The Android Open Source Project
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
* limitations under the License.
*/

package android.server.wm;

import static android.server.wm.WindowManagerState.STATE_PAUSED;
import static android.server.wm.WindowManagerState.STATE_RESUMED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.overlay.Components;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:ActivityRecordInputSinkTests
 */
@Presubmit
public class ActivityRecordInputSinkTests extends ActivityManagerTestBase {

    private static final String APP_SELF =
            ActivityRecordInputSinkTests.class.getPackage().getName() + ".cts";
    private static final String APP_A =
            android.server.wm.second.Components.class.getPackage().getName();

    private static final ComponentName TEST_ACTIVITY =
            new ComponentName(APP_SELF, "android.server.wm.ActivityRecordInputSinkTestsActivity");

    private static final ComponentName OVERLAY_IN_SAME_UID =
            Components.TranslucentFloatingActivity.getComponent(APP_SELF);
    private static final ComponentName OVERLAY_IN_DIFFERENT_UID =
            Components.TranslucentFloatingActivity.getComponent(APP_A);
    private static final ComponentName TRAMPOLINE_DIFFERENT_UID =
            Components.TrampolineActivity.getComponent(APP_A);

    private int mTouchCount;

    @Before
    public void setUp() {
        ActivityRecordInputSinkTestsActivity.sButtonClickCount.set(0);
    }

    @After
    public void tearDown() {
        stopTestPackage(APP_A);
    }

    @Test
    public void testOverlappingActivityInNewTask_BlocksTouches() {
        System.out.println("BEFORE");
        launchActivity(TEST_ACTIVITY);
        System.out.println("MIDDLE");

        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
        System.out.println("AFTER");

        launchActivityInNewTask(OVERLAY_IN_SAME_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_SAME_UID, STATE_RESUMED);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);

        mContext.sendBroadcast(new Intent(Components.TranslucentFloatingActivity.ACTION_FINISH));
        mWmState.waitAndAssertActivityRemoved(OVERLAY_IN_SAME_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskSameUid_DoesNotBlocksTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_SAME_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_SAME_UID, STATE_RESUMED);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskDifferentUid_DoesNotBlocksTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(OVERLAY_IN_DIFFERENT_UID);
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        mWmState.assertActivityDisplayed(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivityInSameTaskTrampolineDifferentUid_DoesNotBlockTouches() {
        launchActivity(TEST_ACTIVITY);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);

        launchActivityInSameTask(TRAMPOLINE_DIFFERENT_UID,
                Components.TrampolineActivity.buildTrampolineExtra(OVERLAY_IN_DIFFERENT_UID));
        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    @Test
    public void testOverlappingActivitySandwich_BlocksTouches() {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(TRAMPOLINE_DIFFERENT_UID);
        intent.replaceExtras(Components.TrampolineActivity.buildTrampolineExtra(TEST_ACTIVITY,
                OVERLAY_IN_DIFFERENT_UID));
        mContext.startActivity(intent);

        mWmState.waitAndAssertActivityState(OVERLAY_IN_DIFFERENT_UID, STATE_RESUMED);
        touchButtonsAndAssert(false /*expectTouchesToReachActivity*/);

        mContext.sendBroadcast(new Intent(Components.TranslucentFloatingActivity.ACTION_FINISH));
        mWmState.waitAndAssertActivityRemoved(OVERLAY_IN_DIFFERENT_UID);
        touchButtonsAndAssert(true /*expectTouchesToReachActivity*/);
    }

    private void launchActivityInSameTask(ComponentName componentName) {
        launchActivityInSameTask(componentName, null);
    }

    private void launchActivityInSameTask(ComponentName componentName, @Nullable Bundle extras) {
        Intent intent = new Intent(ActivityRecordInputSinkTestsActivity.LAUNCH_ACTIVITY_ACTION);
        intent.putExtra(ActivityRecordInputSinkTestsActivity.COMPONENT_EXTRA, componentName);
        intent.putExtra(ActivityRecordInputSinkTestsActivity.EXTRA_EXTRA, extras);
        mContext.sendBroadcast(intent);
    }


    private void touchButtonsAndAssert(boolean expectTouchesToReachActivity) {
        int displayId = mWmState.getDisplayByActivity(TEST_ACTIVITY);
        System.out.printf("displayId %d%n", displayId);
        System.out.printf("mWmState.getTaskByActivity(TEST_ACTIVITY) %s%n", mWmState.getTaskByActivity(TEST_ACTIVITY));
        System.out.printf(
                " mWmState.getTaskByActivity(TEST_ACTIVITY).mBounds %s%n",  mWmState.getTaskByActivity(TEST_ACTIVITY).mBounds);
        WindowManagerState.Activity activity = mWmState.getTaskByActivity(
                TEST_ACTIVITY).getActivity(TEST_ACTIVITY);
        System.out.printf("Activity.name: %s bounds: %s visible: %b%n", activity.name, activity.mBounds, activity.visible);
        Rect bounds =
                mWmState.getTaskByActivity(TEST_ACTIVITY).mBounds;
        bounds.offset(0, -bounds.height() / 3);
        click(bounds.centerX(), bounds.centerY(), displayId);
        mTouchCount += (expectTouchesToReachActivity ? 1 : 0);
        mInstrumentation.waitForIdleSync();
        assertThat(ActivityRecordInputSinkTestsActivity.sButtonClickCount.get())
                .isEqualTo(mTouchCount);

        bounds.offset(0, 2 * bounds.height() / 3);
        click(bounds.centerX(), bounds.centerY(), displayId);
        mTouchCount += (expectTouchesToReachActivity ? 1 : 0);
        mInstrumentation.waitForIdleSync();
        assertThat(ActivityRecordInputSinkTestsActivity.sButtonClickCount.get())
                .isEqualTo(mTouchCount);
    }

    private void click(int x, int y, int displayId) {
        final long downTime = SystemClock.elapsedRealtime();
        final MotionEvent downEvent = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, x, y, 0 /* metaState */);
        downEvent.setDisplayId(displayId);
        getInstrumentation().sendPointerSync(downEvent);
        final MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.elapsedRealtime(),
                MotionEvent.ACTION_UP, x, y, 0 /* metaState */);
        upEvent.setDisplayId(displayId);
        getInstrumentation().sendPointerSync(upEvent);
    }

}
