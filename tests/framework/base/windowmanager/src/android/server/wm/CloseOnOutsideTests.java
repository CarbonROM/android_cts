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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Instrumentation;
import android.graphics.Insets;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.view.WindowMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests {@link android.view.Window#setCloseOnTouchOutside(boolean)} through exposed Activity API.
 */
@RunWith(AndroidJUnit4.class)
public class CloseOnOutsideTests {

    @Rule
    public final ActivityTestRule<CloseOnOutsideTestActivity> mTestActivity =
            new ActivityTestRule<>(CloseOnOutsideTestActivity.class, true, true);

    @Test
    public void withDefaults() {
        touchAndAssert(false /* shouldBeFinishing */);
    }

    @Test
    public void finishTrue() {
        mTestActivity.getActivity().setFinishOnTouchOutside(true);
        touchAndAssert(true /* shouldBeFinishing */);
    }

    @Test
    public void finishFalse() {
        mTestActivity.getActivity().setFinishOnTouchOutside(false);
        touchAndAssert(false /* shouldBeFinishing */);
    }

    // Tap the bottom right and check the Activity is finishing
    private void touchAndAssert(boolean shouldBeFinishing) {
        DisplayMetrics displayMetrics =
                mTestActivity.getActivity().getResources().getDisplayMetrics();
        WindowMetrics windowMetrics =
                mTestActivity.getActivity().getWindowManager().getCurrentWindowMetrics();
        Insets systemBarInsets =
                windowMetrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars());
        Insets statusBarInsets =
                windowMetrics.getWindowInsets().getInsets(WindowInsets.Type.statusBars());

        // DisplayMetrics.widthPixels and DisplayMetrics.heightPixels
        // do not include the navigation bar size, so subtract the status bar size.
        int appAreaWidth =
                displayMetrics.widthPixels - statusBarInsets.left - statusBarInsets.right;
        int appAreaHeight =
                displayMetrics.heightPixels - statusBarInsets.top - statusBarInsets.bottom;

        int width = (int) (systemBarInsets.left + (appAreaWidth * 0.875f));
        int height = (int) (systemBarInsets.top + (appAreaHeight * 0.875f));

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

        // First setup the new window size to be smaller than the entire activity, "revealing" a
        // clickable area outside the window.
        instrumentation.runOnMainSync(() -> mTestActivity.getActivity().setupWindowSize());

        // After that call is complete, run the test on the activity by simulating a touch outside
        // the Window.
        instrumentation.runOnMainSync(() -> {
            // To be safe, make sure nothing else is finishing the Activity
            assertFalse(mTestActivity.getActivity().isFinishing());
            mTestActivity.getActivity().dispatchTouchEvent(
                    MotionEvent.obtain(1, 1, MotionEvent.ACTION_DOWN, width, height, 0));
            mTestActivity.getActivity().dispatchTouchEvent(
                    MotionEvent.obtain(1, 1, MotionEvent.ACTION_UP, width, height, 0));
            assertEquals(shouldBeFinishing, mTestActivity.getActivity().isFinishing());
        });
    }
}
