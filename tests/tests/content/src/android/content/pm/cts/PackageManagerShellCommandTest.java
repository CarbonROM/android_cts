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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Optional;

@RunWith(AndroidJUnit4.class)
@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class PackageManagerShellCommandTest {

    private static final String TEST_APP_PACKAGE = "com.example.helloworld";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_HW5 = "HelloWorld5.apk";
    private static final String TEST_HW5_SPLIT0 = "HelloWorld5_hdpi-v4.apk";
    private static final String TEST_HW5_SPLIT1 = "HelloWorld5_mdpi-v4.apk";
    private static final String TEST_HW5_SPLIT2 = "HelloWorld5_xhdpi-v4.apk";
    private static final String TEST_HW5_SPLIT3 = "HelloWorld5_xxhdpi-v4.apk";
    private static final String TEST_HW5_SPLIT4 = "HelloWorld5_xxxhdpi-v4.apk";
    private static final String TEST_HW7 = "HelloWorld7.apk";
    private static final String TEST_HW7_SPLIT0 = "HelloWorld7_hdpi-v4.apk";
    private static final String TEST_HW7_SPLIT1 = "HelloWorld7_mdpi-v4.apk";
    private static final String TEST_HW7_SPLIT2 = "HelloWorld7_xhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT3 = "HelloWorld7_xxhdpi-v4.apk";
    private static final String TEST_HW7_SPLIT4 = "HelloWorld7_xxxhdpi-v4.apk";

    private static String executeShellCommand(String command) throws IOException {
        final ParcelFileDescriptor stdout =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                        command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String executeShellCommand(String command, File input)
            throws IOException {
        final ParcelFileDescriptor[] pfds =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileInputStream inputStream = new FileInputStream(input);
             FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                     stdin)) {
            writeFullStream(inputStream, outputStream, input.length());
        }
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, -1);
        return result.toString("UTF-8");
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertEquals(expected, total);
        }
    }

    @Before
    public void checkNotInstalled() throws Exception {
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @After
    public void uninstall() throws Exception {
        uninstallPackage(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals(null, getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstall() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppInstallStdIn() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdate() throws Exception {
        installPackage(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        installPackage(TEST_HW7);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdateStdIn() throws Exception {
        installPackageStdIn(TEST_HW5);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        installPackageStdIn(TEST_HW7);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstall() throws Exception {
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstallStdIn() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsInstallDash() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Ignore("Until pm batch change is in.")
    @Test
    public void testSplitsBatchInstall() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdate() throws Exception {
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplits(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdateStdIn() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsStdIn(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4}, "");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUpdateDash() throws Exception {
        installSplitsStdIn(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsStdIn(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4}, "-");
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Ignore("Until pm batch change is in.")
    @Test
    public void testSplitsBatchUpdate() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        installSplitsBatch(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsUninstall() throws Exception {
        installSplits(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplits(TEST_APP_PACKAGE, new String[]{"config.hdpi"});
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplits(TEST_APP_PACKAGE, new String[]{"config.xxxhdpi", "config.xhdpi"});
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Ignore("Until pm batch change is in.")
    @Test
    public void testSplitsBatchUninstall() throws Exception {
        installSplitsBatch(new String[]{TEST_HW5, TEST_HW5_SPLIT0, TEST_HW5_SPLIT1, TEST_HW5_SPLIT2,
                TEST_HW5_SPLIT3, TEST_HW5_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplitsBatch(TEST_APP_PACKAGE, new String[]{"config.hdpi"});
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));
        uninstallSplitsBatch(TEST_APP_PACKAGE, new String[]{"config.xxxhdpi", "config.xhdpi"});
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Test
    public void testSplitsRemove() throws Exception {
        installSplits(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        String sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplits(sessionId, new String[]{"config.hdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplits(sessionId, new String[]{"config.xxxhdpi", "config.xhdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    @Ignore("Until pm batch change is in.")
    @Test
    public void testSplitsBatchRemove() throws Exception {
        installSplitsBatch(new String[]{TEST_HW7, TEST_HW7_SPLIT0, TEST_HW7_SPLIT1, TEST_HW7_SPLIT2,
                TEST_HW7_SPLIT3, TEST_HW7_SPLIT4});
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals("base, config.hdpi, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        String sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplitsBatch(sessionId, new String[]{"config.hdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xhdpi, config.xxhdpi, config.xxxhdpi",
                getSplits(TEST_APP_PACKAGE));

        sessionId = createUpdateSession(TEST_APP_PACKAGE);
        removeSplitsBatch(sessionId, new String[]{"config.xxxhdpi", "config.xhdpi"});
        commitSession(sessionId);
        assertEquals("base, config.mdpi, config.xxhdpi", getSplits(TEST_APP_PACKAGE));
    }

    private String createUpdateSession(String packageName) throws IOException {
        return createSession("-p " + packageName);
    }

    private String createSession(String arg) throws IOException {
        final String prefix = "Success: created install session [";
        final String suffix = "]\n";
        final String commandResult = executeShellCommand("pm install-create " + arg);
        assertTrue(commandResult, commandResult.startsWith(prefix));
        assertTrue(commandResult, commandResult.endsWith(suffix));
        return commandResult.substring(prefix.length(), commandResult.length() - suffix.length());
    }

    private void addSplits(String sessionId, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            File file = new File(splitName);
            assertEquals("Success: streamed " + file.length() + " bytes\n",
                    executeShellCommand("pm install-write " + sessionId + " " + file.getName() + " "
                            + splitName));
        }
    }

    private void addSplitsStdIn(String sessionId, String[] splitNames, String args)
            throws IOException {
        for (String splitName : splitNames) {
            File file = new File(splitName);
            assertEquals("Success: streamed " + file.length() + " bytes\n",
                    executeShellCommand(
                            "pm install-write -S " + file.length() + " " + sessionId + " "
                                    + file.getName() + " " + args, file));
        }
    }

    private void removeSplits(String sessionId, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            assertEquals("Success\n",
                    executeShellCommand("pm install-remove " + sessionId + " " + splitName));
        }
    }

    private void removeSplitsBatch(String sessionId, String[] splitNames) throws IOException {
        assertEquals("Success\n", executeShellCommand(
                "pm install-remove " + sessionId + " " + String.join(" ", splitNames)));
    }

    private void commitSession(String sessionId) throws IOException {
        assertEquals("Success\n", executeShellCommand("pm install-commit " + sessionId));
    }

    private boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm list packages");
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.substring(prefixLength).equals(packageName));
    }

    private String getSplits(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm dump " + packageName);
        final String prefix = "    splits=[";
        final int prefixLength = prefix.length();
        Optional<String> maybeSplits = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix)).findFirst();
        if (!maybeSplits.isPresent()) {
            return null;
        }
        String splits = maybeSplits.get();
        return splits.substring(prefixLength, splits.length() - 1);
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    private void installPackage(String baseName) throws IOException {
        assertEquals("Success\n",
                executeShellCommand("pm install -t -g " + createApkPath(baseName)));
    }

    private void installPackageStdIn(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm install -t -g -S " + file.length(), file));
    }

    private void installSplits(String[] baseNames) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        String sessionId = createSession(TEST_APP_PACKAGE);
        addSplits(sessionId, splits);
        commitSession(sessionId);
    }

    private void installSplitsStdIn(String[] baseNames, String args) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        String sessionId = createSession(TEST_APP_PACKAGE);
        addSplitsStdIn(sessionId, splits, args);
        commitSession(sessionId);
    }

    private void installSplitsBatch(String[] baseNames) throws IOException {
        String[] splits = Arrays.stream(baseNames).map(
                baseName -> createApkPath(baseName)).toArray(String[]::new);
        assertEquals("Success\n",
                executeShellCommand("pm install -t -g " + String.join(" ", splits)));
    }

    private void uninstallPackage(String packageName) throws IOException {
        assertEquals("Success\n", executeShellCommand("pm uninstall " + packageName));
    }

    private void uninstallSplits(String packageName, String[] splitNames) throws IOException {
        for (String splitName : splitNames) {
            assertEquals("Success\n",
                    executeShellCommand("pm uninstall " + packageName + " " + splitName));
        }
    }

    private void uninstallSplitsBatch(String packageName, String[] splitNames) throws IOException {
        assertEquals("Success\n", executeShellCommand(
                "pm uninstall " + packageName + " " + String.join(" ", splitNames)));
    }
}

