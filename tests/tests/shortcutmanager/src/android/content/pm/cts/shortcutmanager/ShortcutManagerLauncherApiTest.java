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
package android.content.pm.cts.shortcutmanager;

import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_GET_KEY_FIELDS_ONLY;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_GET_PERSISTED_DATA;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST;
import static android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import android.content.Intent;
import android.content.LocusId;
import android.content.pm.Capability;
import android.content.pm.CapabilityParams;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;

import java.util.List;

@SmallTest
public class ShortcutManagerLauncherApiTest extends ShortcutManagerCtsTestsBase {
    @Override
    protected String getOverrideConfig() {
        return "max_icon_dimension_dp=96,"
                + "max_icon_dimension_dp_lowram=96,"
                + "icon_format=PNG,"
                + "icon_quality=100";
    }

    public void testPinShortcuts() {
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_1", true);
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 3,
                    "Manifest shortcuts didn't show up");

            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcut("s2"),
                    makeShortcut("s3"),
                    makeShortcut("s4"),
                    makeShortcut("s5")
            )));
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .areAllDynamic()
                    .areAllEnabled();
        });
        runWithCallerWithStrictMode(mPackageContext2, () -> {
            enableManifestActivity("Launcher_manifest_1", true);
            enableManifestActivity("Launcher_manifest_3", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 3,
                    "Manifest shortcuts didn't show up");

            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcut("s2"),
                    makeShortcut("s3"),
                    makeShortcut("s4"),
                    makeShortcut("s5")
            )));
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .areAllDynamic()
                    .areAllEnabled();
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(
                    mPackageContext1.getPackageName(),
                    list("s1", "s2", "s3", "ms1", "ms21"), getUserHandle());
            getLauncherApps().pinShortcuts(
                    mPackageContext2.getPackageName(),
                    list("s2", "s3", "ms1", "ms31"), getUserHandle());
        });

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            getManager().removeDynamicShortcuts(list("s1", "s2"));
        });

        runWithCallerWithStrictMode(mPackageContext2, () -> {
            enableManifestActivity("Launcher_manifest_3", false);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 1,
                    "Manifest shortcuts didn't updated");

            getManager().removeDynamicShortcuts(list("s1", "s2"));
        });

        // Check the result.
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s3", "s4", "s5")
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1", "ms21", "ms22")
                    .areAllEnabled();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s1", "s2", "s3", "ms1", "ms21")
                    .areAllEnabled();
        });

        runWithCallerWithStrictMode(mPackageContext2, () -> {
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds("s3", "s4", "s5")
                    .areAllEnabled();
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1")
                    .areAllEnabled();
            assertWith(getManager().getPinnedShortcuts())
                    .haveIds("s2", "s3", "ms1", "ms31")

                    .selectByIds("s2", "s3", "ms1")
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByIds("ms31")
                    .areAllDisabled();
        });
    }

    public void testGetShortcuts() throws Exception {

        testPinShortcuts();

        Thread.sleep(2);
        final long time1 = System.currentTimeMillis();

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertTrue(getManager().updateShortcuts(list(
                    makeShortcutWithLocusId("s1", "ls1"),
                    makeShortcutWithLocusId("s2", "ls2"),
                    makeShortcut("s3"))));

            setTargetActivityOverride("Launcher_manifest_2");

            assertTrue(getManager().updateShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcut("s5"))));
        });

        Thread.sleep(2);
        final long time2 = System.currentTimeMillis();

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertTrue(getManager().updateShortcuts(list(
                    makeShortcutWithRank("s4", 999))));
        });

        runWithCallerWithStrictMode(mPackageContext2, () -> {
            setTargetActivityOverride("Launcher_manifest_1");

            assertTrue(getManager().updateShortcuts(list(
                    makeShortcut("s1"))));
        });

        Thread.sleep(2);
        final long time3 = System.currentTimeMillis();

        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s3", "s4", "s5")
                    .areAllNotWithKeyFieldsOnly();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_PINNED,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s1", "s2", "s3", "ms1", "ms21")
                    .areAllNotWithKeyFieldsOnly();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("ms1", "ms21", "ms22")
                    .areAllNotWithKeyFieldsOnly();

            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s3", "s4", "s5")
                    .areAllWithKeyFieldsOnly();

            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s3", "s4", "s5", "s1", "s2", "s3", "ms1", "ms21")
                    .areAllNotWithKeyFieldsOnly();

            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s1", "s2", "s3", "ms1", "ms21", "ms1", "ms21", "ms22")
                    .areAllNotWithKeyFieldsOnly();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s3", "s4", "s5", "s1", "s2", "ms1", "ms21", "ms1", "ms21", "ms22")
                    .areAllNotWithKeyFieldsOnly();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST
                            | FLAG_GET_KEY_FIELDS_ONLY,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s3", "s4", "s5", "s1", "s2", "ms1", "ms21", "ms1", "ms21", "ms22")
                    .areAllWithKeyFieldsOnly();


            // Package 2

            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext2.getPackageName(),
                    null,
                    0,
                    list(),
                    list()))
                    .haveIds("s2", "s3", "s4", "s5", "ms1", "ms31")
                    ;

            // With activity
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    "Launcher_manifest_2",
                    0,
                    list(),
                    list()))
                    .haveIds("ms21", "ms22", "s1", "s5")
                    .areAllNotWithKeyFieldsOnly();

            // With ids
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "ms1"),
                    list()))
                    .haveIds("s1", "s2", "ms1")
                    .areAllNotWithKeyFieldsOnly();

            // With locus ids
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list(),
                    list(new LocusId("ls1"), new LocusId("ls2"))))
                    .haveIds("s1", "s2")
                    .areAllNotWithKeyFieldsOnly();

            // With time.
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    time2,
                    list(),
                    list()))
                    .haveIds("s4")
                    .areAllNotWithKeyFieldsOnly();

            // No shortcuts have changed since time3.
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext1.getPackageName(),
                    null,
                    time3,
                    list(),
                    list()))
                    .isEmpty();
            assertWith(getShortcutsAsLauncher(
                    FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_MANIFEST,
                    mPackageContext2.getPackageName(),
                    null,
                    time3,
                    list(),
                    list()))
                    .isEmpty();
        });
    }

    public void testGetShortcutIcon() throws Exception {
        final Icon icon1 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_16x64));
        final Icon icon2 = Icon.createWithBitmap(BitmapFactory.decodeResource(
                getTestContext().getResources(), R.drawable.black_32x32));
        final Icon icon3 = loadPackageDrawableIcon(mPackageContext1, "black_64x16");
        final Icon icon4 = loadPackageDrawableIcon(mPackageContext1, "black_64x64");

        final Icon icon5 = loadPackageDrawableIcon(mPackageContext1, "black_16x16");

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 2,
                    "Manifest shortcuts didn't show up");

            assertTrue(getManager().setDynamicShortcuts(list(
                    makeShortcutWithIcon("s1", icon1),
                    makeShortcutWithIcon("s2", icon2),
                    makeShortcutWithIcon("s3", icon3),
                    makeShortcutWithIcon("s4", icon4))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        assertIconDimensions(icon1, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s1", true));
        assertIconDimensions(icon2, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s2", true));
        assertIconDimensions(icon3, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s3", true));
        assertIconDimensions(icon4, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s4", true));

        assertIconDimensions(icon5, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "ms21", true));

        assertIconDimensions(icon1, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s1", false));
        assertIconDimensions(icon2, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s2", false));
        assertIconDimensions(icon3, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s3", false));
        assertIconDimensions(icon4, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "s4", false));

        assertIconDimensions(icon5, getIconAsLauncher(
                mLauncherContext1, mPackageContext1.getPackageName(), "ms21", false));
    }
    @CddTest(requirement="3.8.1/C-1-2")
    public void testGetShortcutIconAdaptive() throws Exception {
        final Icon icon1 = Icon.createWithAdaptiveBitmap(BitmapFactory.decodeResource(
            getTestContext().getResources(), R.drawable.black_16x64));
        final Icon icon2 = Icon.createWithAdaptiveBitmap(BitmapFactory.decodeResource(
            getTestContext().getResources(), R.drawable.black_32x32));

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 2,
                "Manifest shortcuts didn't show up");

            assertTrue(getManager().setDynamicShortcuts(list(
                makeShortcutWithIcon("s1", icon1),
                makeShortcutWithIcon("s2", icon2))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        assertIconDimensions(icon1, getIconAsLauncher(
            mLauncherContext1, mPackageContext1.getPackageName(), "s1", true));
        assertIconDimensions(icon2, getIconAsLauncher(
            mLauncherContext1, mPackageContext1.getPackageName(), "s2", true));


        assertIconDimensions(icon1, getIconAsLauncher(
            mLauncherContext1, mPackageContext1.getPackageName(), "s1", false));
        assertIconDimensions(icon2, getIconAsLauncher(
            mLauncherContext1, mPackageContext1.getPackageName(), "s2", false));
    }

    // TODO: b/259468694
    public void setDynamicShortcuts_PersistsShortcutsToDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            // Verifies setDynamicShortcuts persists shortcuts into AppSearch
            manager.setDynamicShortcuts(list(
                    makeShortcutBuilder("s1")
                            .setShortLabel("Title-s1")
                            .setIntent(new Intent("main").putExtra("k1", "yyy"))
                            .addCapabilityBinding(
                                    new Capability.Builder("action.intent.START_EXERCISE").build(),
                                    new CapabilityParams.Builder("exercise.type", "running")
                                            .addAlias("jogging")
                                            .build())
                            .addCapabilityBinding(
                                    new Capability.Builder("action.intent.START_EXERCISE").build(),
                                    new CapabilityParams.Builder("exercise.duration", "10m")
                                            .build())
                            .build(),
                    makeShortcut("s2"),
                    makeShortcutExcludedFromLauncher("s3")
            ));
            // Verify shortcut excluded from launcher are not included in search result
            assertWith(manager.getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC))
                    .haveIds("s1", "s2")
                    .areAllDynamic();
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3"),
                    null);
            // Package 1
            assertWith(ret).haveIds("s1", "s2", "s3").forShortcutWithId("s1", si -> {
                assertEquals(list(new Capability.Builder(
                        "action.intent.START_EXERCISE").build()), si.getCapabilities());
                assertEquals(list(
                                new CapabilityParams.Builder("exercise.type", "running")
                                        .addAlias("jogging").build(),
                                new CapabilityParams.Builder("exercise.duration", "10m").build()),
                        si.getCapabilityParams(new Capability.Builder(
                                "action.intent.START_EXERCISE").build()));
            });
        });
    }

    // TODO: b/259468694
    public void removeAllDynamicShortcuts_RemovesShortcutsFromDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            // Verifies setDynamicShortcuts persists shortcuts into AppSearch
            manager.setDynamicShortcuts(list(
                    makeShortcutBuilder("s1")
                            .setShortLabel("Title-s1")
                            .setIntent(new Intent("main").putExtra("k1", "yyy"))
                            .addCapabilityBinding(
                                    new Capability.Builder("action.intent.START_EXERCISE").build(),
                                    new CapabilityParams.Builder("exercise.type", "running")
                                            .addAlias("jogging")
                                            .build())
                            .addCapabilityBinding(
                                    new Capability.Builder("action.intent.START_EXERCISE").build(),
                                    new CapabilityParams.Builder("exercise.duration", "10m")
                                            .build())
                            .build(),
                    makeShortcut("s2"),
                    makeShortcutExcludedFromLauncher("s3")
            ));
            // Verify shortcut excluded from launcher are not included in search result
            assertWith(manager.getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC))
                    .haveIds("s1", "s2")
                    .areAllDynamic()
                    .forShortcutWithId("s1", si -> {
                        assertEquals(list(new Capability.Builder(
                                "action.intent.START_EXERCISE").build()), si.getCapabilities());
                        assertEquals(list(
                                        new CapabilityParams.Builder("exercise.type", "running")
                                                .addAlias("jogging").build(),
                                        new CapabilityParams.Builder("exercise.duration", "10m")
                                                .build()),
                                si.getCapabilityParams(new Capability.Builder(
                                        "action.intent.START_EXERCISE").build()));
                    });
        });
        Thread.sleep(5000);
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            // Verifies removeAllDynamicShortcuts removes shortcuts from persistence layer
            getManager().removeAllDynamicShortcuts();
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3"),
                    null);
            assertWith(ret).isEmpty();
        });
    }

    // TODO: b/259468694
    public void addDynamicShortcuts_PersistsShortcutsToDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            manager.setDynamicShortcuts(list(
                    makeShortcutBuilder("s1")
                            .setShortLabel("Title-s1")
                            .setIntent(new Intent("main").putExtra("k1", "yyy"))
                            .addCapabilityBinding(
                                    new Capability.Builder("action.intent.START_EXERCISE").build(),
                                    new CapabilityParams.Builder("exercise.type", "running")
                                            .addAlias("jogging")
                                            .build())
                            .addCapabilityBinding(
                                    new Capability.Builder("action.intent.START_EXERCISE").build(),
                                    new CapabilityParams.Builder("exercise.duration", "10m")
                                            .build())
                            .build(),
                    makeShortcut("s2"),
                    makeShortcutExcludedFromLauncher("s3")
            ));
            // Verify shortcut excluded from launcher are not included in search result
            assertWith(manager.getShortcuts(ShortcutManager.FLAG_MATCH_DYNAMIC))
                    .haveIds("s1", "s2")
                    .areAllDynamic()
                    .forShortcutWithId("s1", si -> {
                        assertEquals(list(new Capability.Builder(
                                "action.intent.START_EXERCISE").build()), si.getCapabilities());
                        assertEquals(list(
                                        new CapabilityParams.Builder("exercise.type", "running")
                                                .addAlias("jogging").build(),
                                        new CapabilityParams.Builder("exercise.duration", "10m")
                                                .build()),
                                si.getCapabilityParams(new Capability.Builder(
                                        "action.intent.START_EXERCISE").build()));
                    });
            // Verifies addDynamicShortcuts persists shortcuts into AppSearch
            manager.addDynamicShortcuts(list(makeShortcut("s4"), makeShortcut("s5")));
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5"),
                    null);
            assertWith(ret).haveIds("s1", "s2", "s3", "s4", "s5");
        });
    }

    // TODO: b/259468694
    public void pushDynamicShortcuts_PersistsShortcutsToDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        SystemUtil.runShellCommand("cmd shortcut override-config max_shortcuts=5");
        runWithCallerWithStrictMode(mPackageContext1, () ->
                getManager().setDynamicShortcuts(list(
                        makeShortcutBuilder("s1")
                                .setShortLabel("Title-s1")
                                .setIntent(new Intent("main").putExtra("k1", "yyy"))
                                .addCapabilityBinding(
                                        new Capability.Builder(
                                                "action.intent.START_EXERCISE").build(),
                                        new CapabilityParams.Builder("exercise.type", "running")
                                                .addAlias("jogging")
                                                .build())
                                .addCapabilityBinding(
                                        new Capability.Builder(
                                                "action.intent.START_EXERCISE").build(),
                                        new CapabilityParams.Builder("exercise.duration", "10m")
                                                .build())
                                .build(),
                        makeShortcut("s2"),
                        makeShortcut("s3"),
                        makeShortcutExcludedFromLauncher("s4"),
                        makeShortcutExcludedFromLauncher("s5")
                )));
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5"),
                    null);
            assertWith(ret).haveIds("s1", "s2", "s3", "s4", "s5")
                    .forShortcutWithId("s1", si -> {
                        assertEquals(list(new Capability.Builder(
                                "action.intent.START_EXERCISE").build()), si.getCapabilities());
                        assertEquals(list(
                                        new CapabilityParams.Builder("exercise.type", "running")
                                                .addAlias("jogging").build(),
                                        new CapabilityParams.Builder("exercise.duration", "10m")
                                                .build()),
                                si.getCapabilityParams(new Capability.Builder(
                                "action.intent.START_EXERCISE").build()));
                    });
        });
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            // Verifies pushDynamicShortcuts further persists shortcuts into AppSearch without
            // removing previous shortcuts when max number of shortcuts is reached.
            getManager().pushDynamicShortcut(makeShortcut("s6"));
        });
        Thread.sleep(5000);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5", "s6"),
                    null);
            assertWith(ret).haveIds("s1", "s2", "s3", "s4", "s5", "s6");
        });
        SystemUtil.runShellCommand("cmd shortcut reset-config");
    }

    // TODO: b/259468694
    public void removeDynamicShortcuts_RemovesShortcutsFromDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            manager.setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcut("s2"),
                    makeShortcutExcludedFromLauncher("s3"),
                    makeShortcutExcludedFromLauncher("s4"),
                    makeShortcutExcludedFromLauncher("s5")
            ));
            // Verifies removeDynamicShortcuts removes shortcuts from persistence layer
            manager.removeDynamicShortcuts(list("s1"));
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5"),
                    null);
            assertWith(ret).haveIds("s2", "s3", "s4", "s5");
        });
    }

    // TODO: b/259468694
    public void removeLongLivedShortcuts_RemovesShortcutsFromDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            manager.setDynamicShortcuts(list(
                    makeShortcutExcludedFromLauncher("s1"),
                    makeShortcut("s2"),
                    makeShortcutExcludedFromLauncher("s3"),
                    makeShortcut("s4"),
                    makeShortcut("s5")
            ));
            manager.removeDynamicShortcuts(list("s2"));
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5"),
                    null);
            assertWith(ret).haveIds("s1", "s3", "s4", "s5");
        });
    }

    // TODO: b/259468694
    public void disableShortcuts_RemovesShortcutsFromDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            manager.setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcutExcludedFromLauncher("s2"),
                    makeShortcut("s3"),
                    makeShortcutExcludedFromLauncher("s4"),
                    makeShortcut("s5")
            ));
            // Verifies disableShortcuts removes shortcuts from persistence layer
            manager.disableShortcuts(list("s3"));
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5"),
                    null);
            assertWith(ret).haveIds("s1", "s2", "s4", "s5");
        });
    }

    // TODO: b/259468694
    public void updateShortcuts_UpdateShortcutsOnDisk() throws Exception {
        if (!isAppSearchEnabled()) {
            return;
        }
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            final ShortcutManager manager = getManager();
            manager.setDynamicShortcuts(list(
                    makeShortcut("s1"),
                    makeShortcutExcludedFromLauncher("s2"),
                    makeShortcut("s3"),
                    makeShortcutExcludedFromLauncher("s4"),
                    makeShortcut("s5")
            ));
            // Verifies shortcuts in persistence layer are being updated
            manager.updateShortcuts(list(makeShortcut("s3", "custom")));
        });
        Thread.sleep(5000);
        setDefaultLauncher(getInstrumentation(), mLauncherContext1);
        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            final List<ShortcutInfo> ret = getShortcutsAsLauncher(
                    FLAG_GET_PERSISTED_DATA,
                    mPackageContext1.getPackageName(),
                    null,
                    0,
                    list("s1", "s2", "s3", "s4", "s5"),
                    null);
            assertWith(ret)
                    .haveIds("s1", "s2", "s3", "s4", "s5")
                    .forShortcutWithId("s3", si -> {
                        assertEquals("custom", si.getShortLabel());
                    });
        });
    }
}
