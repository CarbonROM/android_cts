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

package android.cts.backup.othersoundssettingsapp;

import static android.support.test.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.BackupUtils.LOCAL_TRANSPORT_TOKEN;

import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.support.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.BackupUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

/**
 * Device side routines to be invoked by the host side OtherSoundsSettingsHostSideTest. These
 * are not designed to be called in any other way, as they rely on state set up by the host side
 * test.
 */
@RunWith(AndroidJUnit4.class)
public class OtherSoundsSettingsTest {
    /** The name of the package for backup */
    private static final String SETTINGS_PACKAGE_NAME = "com.android.providers.settings";

    /** This is refer Settings.System.LOCKSCREEN_SOUNDS_ENABLED */
    private static final String SETTING_LOCKSCREEN_SOUNDS_ENABLED = "lockscreen_sounds_enabled";

    /** This is refer Settings.Global.CHARGING_SOUNDS_ENABLED */
    private static final String SETTING_CHARGING_SOUNDS_ENABLED = "charging_sounds_enabled";

    private ContentResolver mContentResolver;
    private BackupUtils mBackupUtils;

    @Before
    public void setUp() throws Exception {
        mContentResolver = getInstrumentation().getTargetContext().getContentResolver();
        mBackupUtils =
                new BackupUtils() {
                    @Override
                    protected InputStream executeShellCommand(String command) throws IOException {
                        ParcelFileDescriptor pfd =
                                getInstrumentation().getUiAutomation().executeShellCommand(command);
                        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                    }
                };
    }

    /**
     * Test backup and restore of Dial pad tones.
     *
     * Test logic:
     * 1. Check Dial pad tones exists.
     * 2. Backup Settings.
     * 3. Toggle Dial pad tones.
     * 4. Restore Settings.
     * 5. Check restored Dial pad tones is the same with backup value.
     */
    @Test
    public void testOtherSoundsSettings_dialPadTones() throws Exception {
        int originalValue =
                Settings.System.getInt(
                        mContentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, -1);
        assertTrue("Dial pad tones does not exist.", originalValue != -1);

        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE_NAME);

        boolean ret =
                Settings.System.putInt(
                        mContentResolver, Settings.System.DTMF_TONE_WHEN_DIALING,
                        1 - originalValue);
        assertTrue("Toggle Dial pad tones fail.", ret);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, SETTINGS_PACKAGE_NAME);

        int restoreValue =
                Settings.System.getInt(
                        mContentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, -1);
        assertTrue("Dial pad tones restore fail.", originalValue == restoreValue);
    }

    /**
     * Test backup and restore of Lock screen sounds.
     *
     * Test logic:
     * 1. Check Lock screen sounds exists.
     * 2. Backup Settings.
     * 3. Toggle Lock screen sounds.
     * 4. Restore Settings.
     * 5. Check restored Lock screen sounds is the same with backup value.
     *
     * Note:
     * 1. Because Settings.System.LOCKSCREEN_SOUNDS_ENABLED is @hide,
     * so we use SETTING_LOCKSCREEN_SOUNDS_ENABLED here.
     * 2. Settings.System.LOCKSCREEN_SOUNDS_ENABLE is in private secure settings,
     * we can't use Settings.System.putInt() to modify it, so we use shell-invocation method.
     */
    @Test
    public void testOtherSoundsSettings_lockScreenSounds() throws Exception {
        int originalValue =
                Settings.System.getInt(
                        mContentResolver, SETTING_LOCKSCREEN_SOUNDS_ENABLED, -1);
        assertTrue("Lock screen sounds does not exist.", originalValue != -1);

        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE_NAME);

        String command = String.format("settings put system %s %d",
                SETTING_LOCKSCREEN_SOUNDS_ENABLED, (1 - originalValue));
        mBackupUtils.executeShellCommandAndReturnOutput(command);

        int toggleValue =
                Settings.System.getInt(
                        mContentResolver, SETTING_LOCKSCREEN_SOUNDS_ENABLED, -1);
        assertTrue("Toggle Lock screen sounds fail.", toggleValue == (1 - originalValue));

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, SETTINGS_PACKAGE_NAME);

        int restoreValue =
                Settings.System.getInt(
                        mContentResolver, SETTING_LOCKSCREEN_SOUNDS_ENABLED, -1);
        assertTrue("Lock screen sounds restore fail.", originalValue == restoreValue);
    }

    /**
     * Test backup and restore of Charging sounds.
     *
     * Test logic:
     * 1. Check Charging sounds exists.
     * 2. Backup Settings.
     * 3. Toggle Charging sounds.
     * 4. Restore Settings.
     * 5. Check restored Charging sounds is the same with backup value.
     *
     * Note: Because Settings.Global.CHARGING_SOUNDS_ENABLED is @hide,
     * so we use SETTING_CHARGING_SOUNDS_ENABLED here.
     */
    @Test
    public void testOtherSoundsSettings_chargingSounds() throws Exception {
        int originalValue =
                Settings.Global.getInt(
                        mContentResolver, SETTING_CHARGING_SOUNDS_ENABLED, -1);
        assertTrue("Charging sounds does not exist.", originalValue != -1);

        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE_NAME);

        boolean ret =
                Settings.Global.putInt(
                        mContentResolver, SETTING_CHARGING_SOUNDS_ENABLED, 1 - originalValue);
        assertTrue("Toggle Charging sounds fail.", ret);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, SETTINGS_PACKAGE_NAME);

        int restoreValue =
                Settings.Global.getInt(
                        mContentResolver, SETTING_CHARGING_SOUNDS_ENABLED, -1);
        assertTrue("Charging sounds restore fail.", originalValue == restoreValue);
    }

    /**
     * Test backup and restore of Touch sounds.
     *
     * Test logic:
     * 1. Check Touch sounds exists.
     * 2. Backup Settings.
     * 3. Toggle Touch sounds.
     * 4. Restore Settings.
     * 5. Check restored Touch sounds is the same with backup value.
     */
    @Test
    public void testOtherSoundsSettings_touchSounds() throws Exception {
        int originalValue =
                Settings.System.getInt(
                        mContentResolver, Settings.System.SOUND_EFFECTS_ENABLED, -1);
        assertTrue("Touch sounds does not exist.", originalValue != -1);

        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE_NAME);

        boolean ret =
                Settings.System.putInt(
                        mContentResolver, Settings.System.SOUND_EFFECTS_ENABLED, 1 - originalValue);
        assertTrue("Toggle Touch sounds fail.", ret);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, SETTINGS_PACKAGE_NAME);

        int restoreValue =
                Settings.System.getInt(
                        mContentResolver, Settings.System.SOUND_EFFECTS_ENABLED, -1);
        assertTrue("Touch sounds restore fail.", originalValue == restoreValue);
    }

    /**
     * Test backup and restore of Touch vibration.
     *
     * Test logic:
     * 1. Check Touch vibration exists.
     * 2. Backup Settings.
     * 3. Toggle Touch vibration.
     * 4. Restore Settings.
     * 5. Check restored Touch vibration is the same with backup value.
     */
    @Test
    public void testOtherSoundsSettings_touchVibration() throws Exception {
        int originalValue =
                Settings.System.getInt(
                        mContentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, -1);
        assertTrue("Touch vibration does not exist.", originalValue != -1);

        mBackupUtils.backupNowAndAssertSuccess(SETTINGS_PACKAGE_NAME);

        boolean ret =
                Settings.System.putInt(
                        mContentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED,
                        1 - originalValue);
        assertTrue("Toggle Touch vibration fail.", ret);

        mBackupUtils.restoreAndAssertSuccess(LOCAL_TRANSPORT_TOKEN, SETTINGS_PACKAGE_NAME);

        int restoreValue =
                Settings.System.getInt(
                        mContentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, -1);
        assertTrue("Touch vibration restore fail.", originalValue == restoreValue);
    }
}