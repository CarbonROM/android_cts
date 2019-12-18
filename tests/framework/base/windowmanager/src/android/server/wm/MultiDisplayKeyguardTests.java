/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.server.wm.UiDeviceUtils.pressBackButton;
import static android.server.wm.app.Components.DISMISS_KEYGUARD_ACTIVITY;
import static android.view.WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerState.DisplayContent;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Display tests that require a keyguard.
 *
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:MultiDisplayKeyguardTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class MultiDisplayKeyguardTests extends MultiDisplayTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(supportsInsecureLock());
    }

    /**
     * Tests whether a FLAG_DISMISS_KEYGUARD activity on a secondary display is visible (for an
     * insecure keyguard).
     */
    @Test
    public void testDismissKeyguardActivity_secondaryDisplay() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final DisplayContent newDisplay = createManagedVirtualDisplaySession().createDisplay();

        lockScreenSession.gotoKeyguard();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        launchActivityOnDisplay(DISMISS_KEYGUARD_ACTIVITY, newDisplay.mId);
        mAmWmState.waitForKeyguardShowingAndNotOccluded();
        mAmWmState.assertKeyguardShowingAndNotOccluded();
        mAmWmState.assertVisibility(DISMISS_KEYGUARD_ACTIVITY, true);
    }

    /**
     * Tests keyguard dialog shows on secondary display.
     * @throws Exception
     */
    @Test
    public void testShowKeyguardDialogOnSecondaryDisplay() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final DisplayContent publicDisplay = createManagedVirtualDisplaySession()
                .setPublicDisplay(true)
                .createDisplay();

        lockScreenSession.gotoKeyguard();
        assertTrue("KeyguardDialog must show on external public display",
                mAmWmState.waitForWithWmState(
                        state -> isKeyguardOnDisplay(state, publicDisplay.mId),
                        "keyguard window to show"));

        // Keyguard dialog mustn't be removed when press back key
        pressBackButton();
        mAmWmState.computeState(true);
        assertTrue("KeyguardDialog must not be removed when press back key",
                isKeyguardOnDisplay(mAmWmState.getWmState(), publicDisplay.mId));
    }

    /**
     * Tests keyguard dialog cannot be shown on private display.
     * @throws Exception
     */
    @Test
    public void testNoKeyguardDialogOnPrivateDisplay() {
        final LockScreenSession lockScreenSession = createManagedLockScreenSession();
        final VirtualDisplaySession virtualDisplaySession = createManagedVirtualDisplaySession();

        final DisplayContent privateDisplay =
                virtualDisplaySession.setPublicDisplay(false).createDisplay();
        final DisplayContent publicDisplay =
                virtualDisplaySession.setPublicDisplay(true).createDisplay();

        lockScreenSession.gotoKeyguard();
        assertTrue("KeyguardDialog must show on external public display",
                mAmWmState.waitForWithWmState(
                        state -> isKeyguardOnDisplay(state, publicDisplay.mId),
                        "keyguard window to show"));

        assertFalse("KeyguardDialog must not show on external private display",
                isKeyguardOnDisplay(mAmWmState.getWmState(), privateDisplay.mId));
    }

    private boolean isKeyguardOnDisplay(WindowManagerState windowManagerState, int displayId) {
        final List<WindowManagerState.WindowState> states =
                windowManagerState.getMatchingWindowType(TYPE_KEYGUARD_DIALOG);
        for (WindowManagerState.WindowState ws : states) {
            if (ws.getDisplayId() == displayId) return true;
        }
        return false;
    }
}
