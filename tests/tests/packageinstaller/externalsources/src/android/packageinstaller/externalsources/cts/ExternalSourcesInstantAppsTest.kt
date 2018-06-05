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
package android.packageinstaller.externalsources.cts

import android.app.AppOpsManager.*
import android.platform.test.annotations.AppModeInstant
import android.support.test.InstrumentationRegistry
import android.support.test.filters.SmallTest
import android.support.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.AppOpsUtils
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
@AppModeInstant
class ExternalSourcesInstantAppsTest {
    private val pm = InstrumentationRegistry.getTargetContext().packageManager
    private val packageName = InstrumentationRegistry.getTargetContext().packageName

    private fun setAppOpsMode(mode: Int) {
        AppOpsUtils.setOpMode(packageName, "REQUEST_INSTALL_PACKAGES", mode)
    }

    @Test
    fun blockedSourceTest() {
        setAppOpsMode(MODE_ERRORED)
        assertFalse("Instant app $packageName allowed to install packages",
                pm.canRequestPackageInstalls())
    }

    @Test
    fun allowedSourceTest() {
        setAppOpsMode(MODE_ALLOWED)
        assertFalse("Instant app $packageName allowed to install packages",
                pm.canRequestPackageInstalls())
    }

    @Test
    fun defaultSourceTest() {
        setAppOpsMode(MODE_DEFAULT)
        assertFalse("Instant app $packageName allowed to install packages",
                pm.canRequestPackageInstalls())
    }

    @After
    fun resetAppOpsMode() {
        setAppOpsMode(MODE_DEFAULT)
    }
}
