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

package com.android.cts.net.hostside;

import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_METERED;
import static android.net.wifi.WifiConfiguration.METERED_OVERRIDE_NONE;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.cts.net.hostside.AbstractRestrictBackgroundNetworkTestCase.TAG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ActionListener;
import android.os.Process;
import android.util.Log;
import android.util.Pair;

import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.BatteryUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import androidx.test.platform.app.InstrumentationRegistry;

public class NetworkPolicyTestUtils {

    private static final int TIMEOUT_CHANGE_METEREDNESS_MS = 10_000;

    private static ConnectivityManager mCm;
    private static WifiManager mWm;

    private static Boolean mBatterySaverSupported;
    private static Boolean mDataSaverSupported;
    private static Boolean mDozeModeSupported;
    private static Boolean mAppStandbySupported;

    private NetworkPolicyTestUtils() {}

    public static boolean isBatterySaverSupported() {
        if (mBatterySaverSupported == null) {
            mBatterySaverSupported = BatteryUtils.isBatterySaverSupported();
        }
        return mBatterySaverSupported;
    }

    private static boolean isWear() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    /**
     * As per CDD requirements, if the device doesn't support data saver mode then
     * ConnectivityManager.getRestrictBackgroundStatus() will always return
     * RESTRICT_BACKGROUND_STATUS_DISABLED. So, enable the data saver mode and check if
     * ConnectivityManager.getRestrictBackgroundStatus() for an app in background returns
     * RESTRICT_BACKGROUND_STATUS_DISABLED or not.
     */
    public static boolean isDataSaverSupported() {
        if (isWear()) {
            return false;
        }
        if (mDataSaverSupported == null) {
            assertMyRestrictBackgroundStatus(RESTRICT_BACKGROUND_STATUS_DISABLED);
            try {
                setRestrictBackground(true);
                mDataSaverSupported = !isMyRestrictBackgroundStatus(
                        RESTRICT_BACKGROUND_STATUS_DISABLED);
            } finally {
                setRestrictBackground(false);
            }
        }
        return mDataSaverSupported;
    }

    public static boolean isDozeModeSupported() {
        if (mDozeModeSupported == null) {
            final String result = executeShellCommand("cmd deviceidle enabled deep");
            mDozeModeSupported = result.equals("1");
        }
        return mDozeModeSupported;
    }

    public static boolean isAppStandbySupported() {
        if (mAppStandbySupported == null) {
            mAppStandbySupported = AppStandbyUtils.isAppStandbyEnabled();
        }
        return mAppStandbySupported;
    }

    public static boolean isLowRamDevice() {
        final ActivityManager am = (ActivityManager) getContext().getSystemService(
                Context.ACTIVITY_SERVICE);
        return am.isLowRamDevice();
    }

    public static boolean isLocationEnabled() {
        final LocationManager lm = (LocationManager) getContext().getSystemService(
                Context.LOCATION_SERVICE);
        return lm.isLocationEnabled();
    }

    public static void setLocationEnabled(boolean enabled) {
        final LocationManager lm = (LocationManager) getContext().getSystemService(
                Context.LOCATION_SERVICE);
        lm.setLocationEnabledForUser(enabled, Process.myUserHandle());
        assertEquals("Couldn't change location enabled state", lm.isLocationEnabled(), enabled);
        Log.d(TAG, "Changed location enabled state to " + enabled);
    }

    public static boolean isActiveNetworkMetered(boolean metered) {
        return getConnectivityManager().isActiveNetworkMetered() == metered;
    }

    public static boolean canChangeActiveNetworkMeteredness() {
        final Network activeNetwork = getConnectivityManager().getActiveNetwork();
        final NetworkCapabilities networkCapabilities
                = getConnectivityManager().getNetworkCapabilities(activeNetwork);
        return networkCapabilities.hasTransport(TRANSPORT_WIFI);
    }

    public static Pair<String, Boolean> setupMeteredNetwork(boolean metered) throws Exception {
        if (isActiveNetworkMetered(metered)) {
            return null;
        }
        final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            final String ssid = getWifiManager().getConnectionInfo().getSSID();
            assertNotEquals(WifiManager.UNKNOWN_SSID, ssid);
            setWifiMeteredStatus(ssid, metered);
            return Pair.create(ssid, !metered);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    public static void resetMeteredNetwork(String ssid, boolean metered) throws Exception {
        setWifiMeteredStatus(ssid, metered);
    }

    public static void setWifiMeteredStatus(String ssid, boolean metered) throws Exception {
        final UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity();
            final WifiConfiguration currentConfig = getWifiConfiguration(ssid);
            currentConfig.meteredOverride = metered
                    ? METERED_OVERRIDE_METERED : METERED_OVERRIDE_NONE;
            BlockingQueue<Integer> blockingQueue = new LinkedBlockingQueue<>();
            getWifiManager().save(currentConfig, createActionListener(
                    blockingQueue, Integer.MAX_VALUE));
            Integer resultCode = blockingQueue.poll(TIMEOUT_CHANGE_METEREDNESS_MS,
                    TimeUnit.MILLISECONDS);
            if (resultCode == null) {
                fail("Timed out waiting for meteredness to change; ssid=" + ssid
                        + ", metered=" + metered);
            } else if (resultCode != Integer.MAX_VALUE) {
                fail("Error overriding the meteredness; ssid=" + ssid
                        + ", metered=" + metered + ", error=" + resultCode);
            }
            final boolean success = assertActiveNetworkMetered(metered, false /* throwOnFailure */);
            if (!success) {
                Log.i(TAG, "Retry connecting to wifi; ssid=" + ssid);
                blockingQueue = new LinkedBlockingQueue<>();
                getWifiManager().connect(currentConfig, createActionListener(
                        blockingQueue, Integer.MAX_VALUE));
                resultCode = blockingQueue.poll(TIMEOUT_CHANGE_METEREDNESS_MS,
                        TimeUnit.MILLISECONDS);
                if (resultCode == null) {
                    fail("Timed out waiting for wifi to connect; ssid=" + ssid);
                } else if (resultCode != Integer.MAX_VALUE) {
                    fail("Error connecting to wifi; ssid=" + ssid
                            + ", error=" + resultCode);
                }
                assertActiveNetworkMetered(metered, true /* throwOnFailure */);
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    private static WifiConfiguration getWifiConfiguration(String ssid) {
        final List<String> ssids = new ArrayList<>();
        for (WifiConfiguration config : getWifiManager().getConfiguredNetworks()) {
            if (config.SSID.equals(ssid)) {
                return config;
            }
            ssids.add(config.SSID);
        }
        fail("Couldn't find the wifi config; ssid=" + ssid
                + ", all=" + Arrays.toString(ssids.toArray()));
        return null;
    }

    private static ActionListener createActionListener(BlockingQueue<Integer> blockingQueue,
            int successCode) {
        return new ActionListener() {
            @Override
            public void onSuccess() {
                blockingQueue.offer(successCode);
            }

            @Override
            public void onFailure(int reason) {
                blockingQueue.offer(reason);
            }
        };
    }

    private static boolean assertActiveNetworkMetered(boolean expectedMeteredStatus,
            boolean throwOnFailure) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final NetworkCallback networkCallback = new NetworkCallback() {
            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities nc) {
                final boolean metered = !nc.hasCapability(NET_CAPABILITY_NOT_METERED);
                if (metered == expectedMeteredStatus) {
                    latch.countDown();
                }
            }
        };
        // Registering a callback here guarantees onCapabilitiesChanged is called immediately
        // with the current setting. Therefore, if the setting has already been changed,
        // this method will return right away, and if not it will wait for the setting to change.
        getConnectivityManager().registerDefaultNetworkCallback(networkCallback);
        try {
            if (!latch.await(TIMEOUT_CHANGE_METEREDNESS_MS, TimeUnit.MILLISECONDS)) {
                final String errorMsg = "Timed out waiting for active network metered status "
                        + "to change to " + expectedMeteredStatus + "; network = "
                        + getConnectivityManager().getActiveNetwork();
                if (throwOnFailure) {
                    fail(errorMsg);
                }
                Log.w(TAG, errorMsg);
                return false;
            }
            return true;
        } finally {
            getConnectivityManager().unregisterNetworkCallback(networkCallback);
        }
    }

    public static void setRestrictBackground(boolean enabled) {
        executeShellCommand("cmd netpolicy set restrict-background " + enabled);
        final String output = executeShellCommand("cmd netpolicy get restrict-background");
        final String expectedSuffix = enabled ? "enabled" : "disabled";
        assertTrue("output '" + output + "' should end with '" + expectedSuffix + "'",
                output.endsWith(expectedSuffix));
    }

    public static boolean isMyRestrictBackgroundStatus(int expectedStatus) {
        final int actualStatus = getConnectivityManager().getRestrictBackgroundStatus();
        if (expectedStatus != actualStatus) {
            Log.d(TAG, "MyRestrictBackgroundStatus: "
                    + "Expected: " + restrictBackgroundValueToString(expectedStatus)
                    + "; Actual: " + restrictBackgroundValueToString(actualStatus));
            return false;
        }
        return true;
    }

    // Copied from cts/tests/tests/net/src/android/net/cts/ConnectivityManagerTest.java
    private static String unquoteSSID(String ssid) {
        // SSID is returned surrounded by quotes if it can be decoded as UTF-8.
        // Otherwise it's guaranteed not to start with a quote.
        if (ssid.charAt(0) == '"') {
            return ssid.substring(1, ssid.length() - 1);
        } else {
            return ssid;
        }
    }

    public static String restrictBackgroundValueToString(int status) {
        switch (status) {
            case RESTRICT_BACKGROUND_STATUS_DISABLED:
                return "DISABLED";
            case RESTRICT_BACKGROUND_STATUS_WHITELISTED:
                return "WHITELISTED";
            case RESTRICT_BACKGROUND_STATUS_ENABLED:
                return "ENABLED";
            default:
                return "UNKNOWN_STATUS_" + status;
        }
    }

    public static String executeShellCommand(String command) {
        final String result = runShellCommand(command).trim();
        Log.d(TAG, "Output of '" + command + "': '" + result + "'");
        return result;
    }

    public static void assertMyRestrictBackgroundStatus(int expectedStatus) {
        final int actualStatus = getConnectivityManager().getRestrictBackgroundStatus();
        assertEquals(restrictBackgroundValueToString(expectedStatus),
                restrictBackgroundValueToString(actualStatus));
    }

    public static ConnectivityManager getConnectivityManager() {
        if (mCm == null) {
            mCm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        return mCm;
    }

    public static WifiManager getWifiManager() {
        if (mWm == null) {
            mWm = (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
        }
        return mWm;
    }

    public static Context getContext() {
        return getInstrumentation().getContext();
    }

    public static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }
}
