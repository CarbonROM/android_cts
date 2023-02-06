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

package android.security.cts

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.platform.test.annotations.AsbSecurityTest
import android.provider.Settings
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject2
import android.support.test.uiautomator.Until
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.sts.common.util.StsExtraBusinessLogicTestCase
import org.junit.After
import org.junit.Assert
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.lang.Exception

/**
 * Tests permission review screen can't be tapjacked
 */
class PermissionReviewTapjackingTest : StsExtraBusinessLogicTestCase {

    companion object {
        const val APK_DIRECTORY = "/data/local/tmp/cts/permission3"
        const val IDLE_TIMEOUT_MILLIS: Long = 1000
        const val TIMEOUT_MILLIS: Long = 20000

        const val APP_APK_PATH_22 = "$APK_DIRECTORY/CtsUsePermissionApp22_2.apk"
        const val APP_PACKAGE_NAME = "android.permission3.cts.usepermission"

        const val HELPER_APP_OVERLAY = "$APK_DIRECTORY/CtsHelperAppOverlay.apk"
        private const val HELPER_PACKAGE_NAME = "android.permission3.cts.helper.overlay"
    }

    protected val uiAutomation: UiAutomation = getInstrumentation().uiAutomation
    protected val uiDevice: UiDevice = UiDevice.getInstance(getInstrumentation())
    protected val packageManager: PackageManager = getContext().packageManager

    private var screenTimeoutBeforeTest: Long = 0

    protected fun waitForIdle() = uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)

    protected fun waitFindObject(selector: BySelector): UiObject2 {
        waitForIdle()
        val view = uiDevice.wait(Until.findObject(selector), TIMEOUT_MILLIS)
        if (view == null) {
            throw RuntimeException("View not found after waiting for " +
                    "${TIMEOUT_MILLIS}ms: $selector")
        }
        return view
    }

    protected fun installPackage(
        apkPath: String,
        reinstall: Boolean = false,
        expectSuccess: Boolean = true
    ) {
        val output = runShellCommand("pm install${if (reinstall) " -r" else ""} $apkPath").trim()
        if (expectSuccess) {
            Assert.assertEquals("Success", output)
        } else {
            Assert.assertNotEquals("Success", output)
        }
    }

    protected fun pressHome() {
        uiDevice.pressHome()
        waitForIdle()
    }

    constructor() : super()

    @Before
    fun setUp() {
        runWithShellPermissionIdentity {
            screenTimeoutBeforeTest = Settings.System.getLong(
                    getContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT
            )
            Settings.System.putLong(
                    getContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 1800000L
            )
        }

        uiDevice.wakeUp()
        runShellCommand(getInstrumentation(), "wm dismiss-keyguard")

        uiDevice.findObject(By.text("Close"))?.click()
    }

    @Before
    fun installApp22AndApprovePermissionReview() {
        assumeFalse(packageManager.arePermissionsIndividuallyControlled())

        installPackage(APP_APK_PATH_22)
        installPackage(HELPER_APP_OVERLAY)

        runShellCommand(
                "appops set $HELPER_PACKAGE_NAME android:system_alert_window allow")
    }

    @After
    fun tearDown() {
        runWithShellPermissionIdentity {
            Settings.System.putLong(
                    getContext().contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                    screenTimeoutBeforeTest
            )
        }

        pressHome()
    }

    @After
    fun uninstallPackages() {
        runShellCommand("pm uninstall $APP_PACKAGE_NAME")
        runShellCommand("pm uninstall $HELPER_PACKAGE_NAME")
    }

    @Test
    @AsbSecurityTest(cveBugId = [176094367])
    fun testOverlaysAreHidden() {
        getContext().startActivity(Intent()
                .setComponent(ComponentName(HELPER_PACKAGE_NAME,
                        "$HELPER_PACKAGE_NAME.OverlayActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        findOverlay()

        getContext().startActivity(Intent()
                .setComponent(ComponentName(APP_PACKAGE_NAME,
                        "$APP_PACKAGE_NAME.FinishOnCreateActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        waitFindObject(By.res("com.android.permissioncontroller:id/permissions_message"))

        try {
            findOverlay()
            Assert.fail("Overlay was displayed")
        } catch (e: Exception) {
            // expected
        }

        System.out.println("pressHome!")
        pressHome()
        findOverlay()
    }

    private fun findOverlay() = waitFindObject(By.text("Find me!"))
}
