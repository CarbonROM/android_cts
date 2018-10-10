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
 * limitations under the License.
 */

package com.android.angleIntegrationTest.debugOption;

import com.android.angleIntegrationTest.common.AngleIntegrationTestActivity;
import com.android.angleIntegrationTest.common.GlesView;

import static org.junit.Assert.fail;
import android.content.Context;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.Override;

@RunWith(AndroidJUnit4.class)
public class AngleDebugOptionActivityTest {

    private final String TAG = this.getClass().getSimpleName();

    @Rule
    public ActivityTestRule<AngleIntegrationTestActivity> rule =
            new ActivityTestRule<>(AngleIntegrationTestActivity.class);

    private void validateDebugOption(boolean debugOptionOn) throws Exception {
        AngleIntegrationTestActivity activity = rule.getActivity();
        GlesView glesView = activity.getGlesView();
        String renderer = glesView.getRenderer();

        if (debugOptionOn) {
            if (!renderer.toLowerCase().contains("ANGLE".toLowerCase())) {
                fail("Failure - ANGLE was not loaded: '" + renderer + "'");
            }
        } else {
            if (renderer.toLowerCase().contains("ANGLE".toLowerCase())) {
                fail("Failure - ANGLE was loaded: '" + renderer + "'");
            }
        }

    }

    @Test
    public void testDebugOptionOn() throws Exception {
        validateDebugOption(true);
    }

    @Test
    public void testDebugOptionOff() throws Exception {
        validateDebugOption(false);
    }
}
