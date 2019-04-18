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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AsyncUtils.await;
import static android.accessibilityservice.cts.utils.GestureUtils.add;
import static android.accessibilityservice.cts.utils.GestureUtils.click;
import static android.accessibilityservice.cts.utils.GestureUtils.dispatchGesture;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTap;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTapAndHold;
import static android.accessibilityservice.cts.utils.GestureUtils.swipe;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_GESTURE_DETECTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_ENTER;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_EXIT;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;

import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.cts.AccessibilityGestureDispatchTest.GestureDispatchActivity;
import android.accessibilityservice.cts.utils.EventCapturingClickListener;
import android.accessibilityservice.cts.utils.EventCapturingHoverListener;
import android.accessibilityservice.cts.utils.EventCapturingLongClickListener;
import android.accessibilityservice.cts.utils.EventCapturingTouchListener;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.PointF;
import android.platform.test.annotations.AppModeFull;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A set of tests for testing touch exploration. Each test dispatches a gesture and checks for the
 * appropriate hover and/or touch events followed by the appropriate accessibility events. Some
 * tests will then check for events from the view.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
public class TouchExplorerTest {
    // Constants
    private static final float GESTURE_LENGTH_INCHES = 1.0f;
    private TouchExplorationStubAccessibilityService mService;
    private Instrumentation mInstrumentation;
    private UiAutomation mUiAutomation;
    private boolean mHasTouchscreen;
    private boolean mScreenBigEnough;
    private EventCapturingHoverListener mHoverListener = new EventCapturingHoverListener(false);
    private EventCapturingTouchListener mTouchListener = new EventCapturingTouchListener(false);
    private EventCapturingClickListener mClickListener = new EventCapturingClickListener();
    private EventCapturingLongClickListener mLongClickListener =
            new EventCapturingLongClickListener();

    @Rule
    public ActivityTestRule<GestureDispatchActivity> mActivityRule =
            new ActivityTestRule<>(GestureDispatchActivity.class, false);

    Point mCenter; // Center of screen. Gestures all start from this point.
    PointF mTapLocation;
    float mSwipeDistance;
    View mView;

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = mInstrumentation.getUiAutomation();
        PackageManager pm = mInstrumentation.getContext().getPackageManager();
        mHasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        // Find screen size, check that it is big enough for gestures.
        // Gestures will start in the center of the screen, so we need enough horiz/vert space.
        WindowManager windowManager =
                (WindowManager)
                        mInstrumentation.getContext().getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        mCenter = new Point((int) metrics.widthPixels / 2, (int) metrics.heightPixels / 2);
        mTapLocation = new PointF(mCenter);
        mScreenBigEnough = (metrics.widthPixels / (2 * metrics.xdpi) > GESTURE_LENGTH_INCHES);
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        mService = TouchExplorationStubAccessibilityService.enableSelf(mInstrumentation);
        mView = mActivityRule.getActivity().findViewById(R.id.full_screen_text_view);
        mView.setOnHoverListener(mHoverListener);
        mView.setOnTouchListener(mTouchListener);
        mInstrumentation.runOnMainSync(
                () -> {
                    mSwipeDistance = mView.getWidth() / 4;
                    mView.setOnClickListener(mClickListener);
                    mView.setOnLongClickListener(mLongClickListener);
                });
    }

    @After
    public void tearDown() throws Exception {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        if (mService != null) {
            mService.runOnServiceSync(() -> mService.disableSelfAndRemove());
            mService = null;
        }
    }

    /** Test a slow swipe which should initiate touch exploration. */
    @Test
    @AppModeFull
    public void testSlowSwipe() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0), 400));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER,         ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_VIEW_HOVER_ENTER,
                TYPE_VIEW_HOVER_EXIT,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /** Test a fast swipe which should not initiate touch exploration. */
    @Test
    @AppModeFull
    public void testFastSwipe() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0)));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_GESTURE_DETECTION_START,
                TYPE_GESTURE_DETECTION_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /** Test a basic single tap which should initiate touch exploration. */
    @Test
    @AppModeFull
    public void testSingleTap() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(click(mTapLocation));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_VIEW_HOVER_ENTER,
                TYPE_VIEW_HOVER_EXIT,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
    }

    /**
     * Test the case where we double tap and no item has accessibility focus, so TouchExplorer sends
     * touch events to the last touch-explored coordinates to simulate a click.
     */
    @Test
    @AppModeFull
    public void testDoubleTapNoAccessibilityFocus() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        // Do a single tap so there is a valid last touch-explored location.
        dispatch(click(mTapLocation));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        // We don't really care about these events but we need to make sure all the events we want
        // to clear have arrived before we clear them.
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_VIEW_HOVER_ENTER,
                TYPE_VIEW_HOVER_EXIT,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        // The click gets delivered as a series of touch events.
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END, TYPE_VIEW_CLICKED);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we want to click on the item that has accessibility focus by using
     * AccessibilityNodeInfo.performAction.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAccessibilityFocus() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        // The click should not be delivered via touch events in this case.
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_INTERACTION_END,
                TYPE_VIEW_CLICKED);
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we double tap but there is neither an accessibility focus or a last
     * touch-explored location. Nothing should happen.
     */
    @Test
    @AppModeFull
    public void testDoubleTapNoFocus() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED, TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        mClickListener.assertNoneClicked();
    }

    /** Test the case where we want to long click on the item that has accessibility focus. */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldAccessibilityFocus() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        syncAccessibilityFocusToInputFocus();
        dispatch(doubleTapAndHold(mTapLocation));
        mHoverListener.assertNonePropagated();
        // The long click still gets delivered as a series of touch events even though we derive the
        // location from accessibility focus.
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_VIEW_LONG_CLICKED,
                TYPE_TOUCH_INTERACTION_END);
        mLongClickListener.assertLongClicked(mView);
    }

    /**
     * Test the case where we double tap and hold and no item has accessibility focus, so
     * TouchExplorer sends touch events to the last touch-explored coordinates to simulate a long
     * click.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldNoAccessibilityFocus() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        // Do a single tap so there is a valid last touch-explored location.
        dispatch(click(mTapLocation));
        mHoverListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        // We don't really care about these events but we need to make sure all the events we want
        // to clear have arrived before we clear them.
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED,
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_VIEW_HOVER_ENTER,
                TYPE_VIEW_HOVER_EXIT,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        dispatch(doubleTapAndHold(mTapLocation));
        mHoverListener.assertNonePropagated();
        // The click gets delivered as a series of touch events.
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START, TYPE_VIEW_LONG_CLICKED, TYPE_TOUCH_INTERACTION_END);
        mLongClickListener.assertLongClicked(mView);
    }

    /**
     * Test the case where we double tap and hold but there is neither an accessibility focus or a
     * last touch-explored location. Nothing should happen.
     */
    @Test
    @AppModeFull
    public void testDoubleTapAndHoldNoFocus() {
        if (!mHasTouchscreen || !mScreenBigEnough) return;
        dispatch(doubleTap(mTapLocation));
        mHoverListener.assertNonePropagated();
        mTouchListener.assertNonePropagated();
        mService.assertPropagated(
                TYPE_VIEW_FOCUSED, TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
        mService.clearEvents();
        mLongClickListener.assertNoneLongClicked();
    }

    public void dispatch(StrokeDescription firstStroke, StrokeDescription... rest) {
        GestureDescription.Builder builder =
                new GestureDescription.Builder().addStroke(firstStroke);
        for (StrokeDescription stroke : rest) {
            builder.addStroke(stroke);
        }
        dispatch(builder.build());
    }

    public void dispatch(GestureDescription gesture) {
        await(dispatchGesture(mService, gesture));
    }

    /** Set the accessibility focus to the element that has input focus. */
    private void syncAccessibilityFocusToInputFocus() {
        mService.runOnServiceSync(
                () -> {
                    mUiAutomation
                            .getRootInActiveWindow()
                            .findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            .performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
                });
    }
}
