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

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class PackageManagerTest extends StsExtraBusinessLogicTestCase {
    private static final String DUMMY_API15_APK_PATH =
            "/data/local/tmp/cts/security/CtsDummyTargetApi15TestApp.apk";
    private static final String DUMMY_API15_PACKAGE_NAME =
            "android.security.cts.dummy.api15";
    private static final ComponentName INVALID_COMPONENT_NAME =
            ComponentName.createRelative(DUMMY_API15_PACKAGE_NAME, ".InvalidClassName");

    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mPackageManager = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getPackageManager();

        installPackage(DUMMY_API15_APK_PATH);
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
        uninstallPackage(DUMMY_API15_PACKAGE_NAME);
    }

    @AsbSecurityTest(cveBugId = 240936919)
    @Test
    public void setComponentEnabledSetting_targetPkgIsApi15_withInvalidComponentName() {
        mPackageManager.setComponentEnabledSetting(
                INVALID_COMPONENT_NAME, COMPONENT_ENABLED_STATE_ENABLED, 0 /* flags */);
        assertThat(mPackageManager.getComponentEnabledSetting(INVALID_COMPONENT_NAME),
                not(is(COMPONENT_ENABLED_STATE_ENABLED)));
    }

    private static void installPackage(String apkPath) {
        assertThat(new File(apkPath).exists(), is(true));
        final StringBuilder cmd = new StringBuilder("pm install ");
        cmd.append(apkPath);
        final String result = runShellCommand(cmd.toString()).trim();
        assertThat(result, containsString("Success"));
    }

    private static void uninstallPackage(String packageName) {
        final StringBuilder cmd = new StringBuilder("pm uninstall ");
        cmd.append(packageName);
        runShellCommand(cmd.toString());
    }
}
