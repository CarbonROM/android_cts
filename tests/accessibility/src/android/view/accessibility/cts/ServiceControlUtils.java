/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.view.accessibility.cts;

import static com.android.compatibility.common.util.TestUtils.waitOn;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Utility methods for enabling and disabling the services used in this package
 */
public class ServiceControlUtils {
    public static final int TIMEOUT_FOR_SERVICE_ENABLE = 10000; // millis; 10s

    private static final String SETTING_ENABLE_SPEAKING_AND_VIBRATING_SERVICES =
            "android.view.accessibility.cts/.SpeakingAccessibilityService:"
            + "android.view.accessibility.cts/.VibratingAccessibilityService";

    private static final String SETTING_ENABLE_MULTIPLE_FEEDBACK_TYPES_SERVICE =
            "android.view.accessibility.cts/.SpeakingAndVibratingAccessibilityService";

    /**
     * Enable {@code SpeakingAccessibilityService} and {@code VibratingAccessibilityService}
     *
     * @param instrumentation A valid instrumentation
     */
    public static void enableSpeakingAndVibratingServices(Instrumentation instrumentation)
            throws IOException {
        Context context = instrumentation.getContext();

        // Get permission to enable accessibility
        UiAutomation uiAutomation = instrumentation.getUiAutomation();

        // Change the settings to enable the two services
        String alreadyEnabledServices = getEnabledServices(context.getContentResolver());
        ParcelFileDescriptor fd = uiAutomation.executeShellCommand("settings --user cur put secure "
                + Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES + " "
                + alreadyEnabledServices + ":"
                + SETTING_ENABLE_SPEAKING_AND_VIBRATING_SERVICES);
        InputStream in = new FileInputStream(fd.getFileDescriptor());
        byte[] buffer = new byte[4096];
        while (in.read(buffer) > 0);
        uiAutomation.destroy();

        // Wait for speaking service to be connected
        waitOn(SpeakingAccessibilityService.sWaitObjectForConnecting,
                () -> SpeakingAccessibilityService.sConnectedInstance != null,
                TIMEOUT_FOR_SERVICE_ENABLE, "Speaking accessibility service starts up");

        // Wait for vibrating service to be connected
        waitOn(VibratingAccessibilityService.sWaitObjectForConnecting,
                () -> VibratingAccessibilityService.sConnectedInstance != null,
                TIMEOUT_FOR_SERVICE_ENABLE, "Vibrating accessibility service starts up");
    }

    /**
     * Enable {@link SpeakingAndVibratingAccessibilityService} for tests requiring a service with
     * multiple feedback types
     *
     * @param instrumentation A valid instrumentation
     */
    public static void enableMultipleFeedbackTypesService(Instrumentation instrumentation)
            throws IOException {
        Context context = instrumentation.getContext();

        // Get permission to enable accessibility
        UiAutomation uiAutomation = instrumentation.getUiAutomation();

        // Change the settings to enable the services
        String alreadyEnabledServices = getEnabledServices(context.getContentResolver());
        ParcelFileDescriptor fd = uiAutomation.executeShellCommand("settings --user cur put secure "
                + Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES + " "
                + alreadyEnabledServices + ":"
                + SETTING_ENABLE_MULTIPLE_FEEDBACK_TYPES_SERVICE);
        InputStream in = new FileInputStream(fd.getFileDescriptor());
        byte[] buffer = new byte[4096];
        while (in.read(buffer) > 0);
        uiAutomation.destroy();

        // Wait for the service to be connected
        waitOn(SpeakingAndVibratingAccessibilityService.sWaitObjectForConnecting,
                () -> SpeakingAndVibratingAccessibilityService.sConnectedInstance != null,
                TIMEOUT_FOR_SERVICE_ENABLE,
                "Multiple feedback types accessibility service starts up");
    }

    /**
     * Turn off all accessibility services. Assumes permissions to write settings are already
     * set, which they are in
     * {@link ServiceControlUtils#enableSpeakingAndVibratingServices(Instrumentation)}.
     *
     * @param instrumentation A valid instrumentation
     */
    public static void turnAccessibilityOff(Instrumentation instrumentation) {
        final Object waitLockForA11yOff = new Object();
        AccessibilityManager manager = (AccessibilityManager) instrumentation
                .getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        // Updates to manager.isEnabled() aren't synchronized
        AtomicBoolean accessibilityEnabled = new AtomicBoolean(manager.isEnabled());
        AccessibilityManager.AccessibilityStateChangeListener listener = (boolean b) -> {
            synchronized (waitLockForA11yOff) {
                waitLockForA11yOff.notifyAll();
                accessibilityEnabled.set(b);
            }
        };
        manager.addAccessibilityStateChangeListener(listener);

        if (SpeakingAccessibilityService.sConnectedInstance != null) {
            SpeakingAccessibilityService.sConnectedInstance.disableSelf();
            SpeakingAccessibilityService.sConnectedInstance = null;
        }
        if (VibratingAccessibilityService.sConnectedInstance != null) {
            VibratingAccessibilityService.sConnectedInstance.disableSelf();
            VibratingAccessibilityService.sConnectedInstance = null;
        }
        if (SpeakingAndVibratingAccessibilityService.sConnectedInstance != null) {
            SpeakingAndVibratingAccessibilityService.sConnectedInstance.disableSelf();
            SpeakingAndVibratingAccessibilityService.sConnectedInstance = null;
        }
        waitOn(waitLockForA11yOff, () -> !accessibilityEnabled.get(), TIMEOUT_FOR_SERVICE_ENABLE,
                "Accessibility turns off");
    }

    public static String getEnabledServices(ContentResolver cr) {
        return Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    }

    /**
     * Wait for a specified condition that will change with a services state change
     *
     * @param context A valid context
     * @param condition The condition to check
     * @param timeoutMs The timeout in millis
     * @param conditionName The name to include in the assertion. If null, will be given a default.
     */
    public static void waitForConditionWithServiceStateChange(Context context,
            BooleanSupplier condition, long timeoutMs, String conditionName) {
        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        Object lock = new Object();
        AccessibilityManager.AccessibilityServicesStateChangeListener listener = (m) -> {
            synchronized (lock) {
                lock.notifyAll();
            }
        };
        manager.addAccessibilityServicesStateChangeListener(listener, null);
        try {
            waitOn(lock, condition, timeoutMs, conditionName);
        } finally {
            manager.removeAccessibilityServicesStateChangeListener(listener);
        }
    }
}
