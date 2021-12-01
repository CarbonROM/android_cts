/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import androidx.test.InstrumentationRegistry;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.Exception;
import java.util.concurrent.TimeoutException;

/**
 * Tests permission review screen can't be tapjacked
 */
public class PermissionReviewTapjackingTest {

    private static String APK_DIRECTORY = "/data/local/tmp/cts/permission3";
    private static long IDLE_TIMEOUT_MILLIS = 1000;
    private static long TIMEOUT_MILLIS = 20000;

    private static String APP_APK_PATH_22 = APK_DIRECTORY + "/CtsUsePermissionApp22_2.apk";
    private static String APP_PACKAGE_NAME = "android.permission3.cts.usepermission";

    private static String HELPER_APP_OVERLAY = APK_DIRECTORY + "/CtsHelperAppOverlay.apk";
    private static String HELPER_PACKAGE_NAME = "android.permission3.cts.helper.overlay";

    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getContext();
    UiAutomation uiAutomation = instrumentation.getUiAutomation();
    UiDevice uiDevice = UiDevice.getInstance(instrumentation);
    PackageManager packageManager = context.getPackageManager();

    private long screenTimeoutBeforeTest = 0;

    private void waitForIdle() throws TimeoutException {
        uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS);
    }

    private UiObject2 waitFindObject(BySelector selector) throws TimeoutException {
        waitForIdle();
        UiObject2 view = uiDevice.wait(Until.findObject(selector), TIMEOUT_MILLIS);
        if (view == null) {
            throw new RuntimeException("View not found after waiting for " + TIMEOUT_MILLIS +
                    "ms: " + selector);
        }
        return view;
    }

    private void installPackage(String apkPath) {
        String output = runShellCommand("pm install " + apkPath).trim();
        Assert.assertEquals("Success", output);
    }

    private void pressHome() throws TimeoutException {
        uiDevice.pressHome();
        waitForIdle();
    }

    @Before
    public void setUp() throws Exception {
        runWithShellPermissionIdentity(() -> {
            screenTimeoutBeforeTest = Settings.System.getLong(
                    context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT
            );
            Settings.System.putLong(
                    context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, 1800000L
            );
        });

        uiDevice.wakeUp();
        runShellCommand(instrumentation, "wm dismiss-keyguard");

        UiObject2 close = uiDevice.findObject(By.text("Close"));
        if (close != null) {
            close.click();
        }
    }

    @Before
    public void installApp22AndApprovePermissionReview() {
        Assume.assumeFalse(packageManager.arePermissionsIndividuallyControlled());

        installPackage(APP_APK_PATH_22);
        installPackage(HELPER_APP_OVERLAY);

        runShellCommand(
                "appops set " + HELPER_PACKAGE_NAME + " android:system_alert_window allow");
    }

    @After
    public void tearDown() throws TimeoutException {
        runWithShellPermissionIdentity(() -> Settings.System.putLong(
                context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT,
                screenTimeoutBeforeTest
        ));

        pressHome();
    }

    @After
    public void uninstallPackages() {
        runShellCommand("pm uninstall " + APP_PACKAGE_NAME);
        runShellCommand("pm uninstall " + HELPER_PACKAGE_NAME);
    }

    @Test
    @AsbSecurityTest(cveBugId = 176094367)
    public void testOverlaysAreHidden() throws TimeoutException {
        context.startActivity(new Intent()
                .setComponent(new ComponentName(HELPER_PACKAGE_NAME,
                        HELPER_PACKAGE_NAME + ".OverlayActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        findOverlay();

        context.startActivity(new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME,
                        APP_PACKAGE_NAME + ".FinishOnCreateActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        waitFindObject(By.res("com.android.permissioncontroller:id/permissions_message"));

        try {
            findOverlay();
            Assert.fail("Overlay was displayed");
        } catch (Exception e) {
            // expected
        }

        System.out.println("pressHome!");
        pressHome();
        findOverlay();
    }

    private void findOverlay() throws TimeoutException {
        waitFindObject(By.text("Find me!"));
    }
}
