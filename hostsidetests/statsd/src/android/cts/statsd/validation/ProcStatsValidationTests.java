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
package android.cts.statsd.validation;

import static org.junit.Assert.assertTrue;

import android.cts.statsd.atom.ProcStateAtomTests;
import android.cts.statsd.atom.ProcStateTestCase;
import android.service.procstats.ProcessStatsProto;
import android.service.procstats.ProcessStatsProto.State;
import android.service.procstats.ProcessStatsProto.State.ProcessState;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.StatsLog.DimensionsValue;
import com.android.os.StatsLog.DurationBucketInfo;
import com.android.os.StatsLog.DurationMetricData;
import com.android.os.StatsLog.ValueBucketInfo;
import com.android.os.StatsLog.ValueMetricData;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.FileUtil;

import com.google.protobuf.TextFormat;
import com.google.protobuf.TextFormat.ParseException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * Side-by-side comparison between statsd and procstats.
 */
public class ProcStatsValidationTests extends ProcStateTestCase {

    private static final String TAG = "Statsd.ProcStatsValidationTests";

    private static final int EXTRA_WAIT_TIME_MS = 1_000; // as buffer when proc state changing.

    private StatsdConfig getConfig(String fileName) throws IOException {
        try {
            // TODO: Ideally, we should use real metrics that are also pushed to the fleet.
            File configFile = getBuildHelper().getTestFile(fileName);
            String configStr = FileUtil.readStringFromFile(configFile);
            StatsdConfig.Builder builder = StatsdConfig.newBuilder();
            TextFormat.merge(configStr, builder);
            return builder.build();
        } catch (ParseException e) {
            LogUtil.CLog.e(
                    "Failed to parse the config! line: " + e.getLine() + " col: " + e.getColumn(),
                    e);
        }
        return null;
    }

    // Test process state top duration for test package.
    // TODO: replace this with exclusive state for all states when statsd features are added
    public void testProcessStateTopDuration() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final String fileName = "PROCSTATSQ_PROCS_STATE_TOP_DURATION.pbtxt";
        StatsdConfig config = getConfig(fileName);
        LogUtil.CLog.d("Updating the following config:\n" + config.toString());
        uploadConfig(config);
        clearProcStats();
        Thread.sleep(WAIT_TIME_SHORT);

        // foreground service
        executeForegroundService();
        Thread.sleep(SLEEP_OF_FOREGROUND_SERVICE + EXTRA_WAIT_TIME_MS);
        // background
        executeBackgroundService(ACTION_BACKGROUND_SLEEP);
        Thread.sleep(SLEEP_OF_ACTION_BACKGROUND_SLEEP + EXTRA_WAIT_TIME_MS);
        // top
        executeForegroundActivity(ACTION_SLEEP_WHILE_TOP);
        Thread.sleep(SLEEP_OF_ACTION_SLEEP_WHILE_TOP + EXTRA_WAIT_TIME_MS);
        // Start extremely short-lived activity, so app goes into cache state (#1 - #3 above).
        executeBackgroundService(ACTION_END_IMMEDIATELY);
        final int cacheTime = 2_000; // process should be in cached state for up to this long
        Thread.sleep(cacheTime);
        // foreground
        // overlay should take 2 sec to appear. So this makes it 4 sec in TOP
        executeForegroundActivity(ACTION_SHOW_APPLICATION_OVERLAY);
        Thread.sleep(EXTRA_WAIT_TIME_MS + 5_000);

        // Sorted list of events in order in which they occurred.
        List<DurationMetricData> statsdData = getDurationMetricDataList();

        List<ProcessStatsProto> processStatsProtoList = getProcStatsProto();

        LogUtil.CLog.d("======================");

        String statsdPkgName = "com.android.server.cts.device.statsd";
        double durationInTopStatsd = 0;
        for (DurationMetricData d : statsdData) {
            List<DimensionsValue> dimensionsValuesInWhat = d.getDimensionLeafValuesInWhatList();
            if (dimensionsValuesInWhat.get(0).getValueStr().equals(statsdPkgName)
                    && dimensionsValuesInWhat.get(1).getValueStr().equals(statsdPkgName)) {
                LogUtil.CLog.d(d.toString());
                for (DurationBucketInfo bucket : d.getBucketInfoList()) {
                    durationInTopStatsd += bucket.getDurationNanos() / 1000 / 1000;
                }
            }
        }

        double durationInTopProcStats = 0;
        for (ProcessStatsProto p : processStatsProtoList) {
            if (p.getProcess().equals(statsdPkgName)) {
                LogUtil.CLog.d(p.toString());
                for (State s : p.getStatesList()) {
                    if (s.getProcessState() == ProcessState.TOP) {
                        durationInTopProcStats += s.getDurationMs();
                    }
                }
            }
        }
        // Verify that duration is within 1% difference
        assertTrue(Math.abs(durationInTopStatsd - durationInTopProcStats) / durationInTopProcStats
                < 0.1);
    }

    public void testProcessStateCachedEmptyDuration() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final String fileName = "PROCSTATSQ_PROCS_STATE_CACHED_EMPTY_DURATION.pbtxt";
        StatsdConfig config = getConfig(fileName);
        LogUtil.CLog.d("Updating the following config:\n" + config.toString());
        uploadConfig(config);
        clearProcStats();
        Thread.sleep(WAIT_TIME_SHORT);

        // foreground service
        executeForegroundService();
        Thread.sleep(SLEEP_OF_FOREGROUND_SERVICE + EXTRA_WAIT_TIME_MS);
        // background
        executeBackgroundService(ACTION_BACKGROUND_SLEEP);
        Thread.sleep(SLEEP_OF_ACTION_BACKGROUND_SLEEP + EXTRA_WAIT_TIME_MS);
        // top
        executeForegroundActivity(ACTION_SLEEP_WHILE_TOP);
        Thread.sleep(SLEEP_OF_ACTION_SLEEP_WHILE_TOP + EXTRA_WAIT_TIME_MS);
        // Start extremely short-lived activity, so app goes into cache state (#1 - #3 above).
        executeBackgroundService(ACTION_END_IMMEDIATELY);
        final int cacheTime = 2_000; // process should be in cached state for up to this long
        Thread.sleep(cacheTime);
        // foreground
        // overlay should take 2 sec to appear. So this makes it 4 sec in TOP
        executeForegroundActivity(ACTION_SHOW_APPLICATION_OVERLAY);
        Thread.sleep(EXTRA_WAIT_TIME_MS + 5_000);

        // Sorted list of events in order in which they occurred.
        List<DurationMetricData> statsdData = getDurationMetricDataList();

        List<ProcessStatsProto> processStatsProtoList = getProcStatsProto();

        LogUtil.CLog.d("======================");

        String statsdPkgName = "com.android.server.cts.device.statsd";
        double durationInStatsd = 0;
        for (DurationMetricData d : statsdData) {
            List<DimensionsValue> dimensionsValuesInWhat = d.getDimensionLeafValuesInWhatList();
            if (dimensionsValuesInWhat.get(0).getValueStr().equals(statsdPkgName)
                    && dimensionsValuesInWhat.get(1).getValueStr().equals(statsdPkgName)) {
                LogUtil.CLog.d(d.toString());
                for (DurationBucketInfo bucket : d.getBucketInfoList()) {
                    durationInStatsd += bucket.getDurationNanos() / 1000 / 1000;
                }
            }
        }

        double durationInProcStats = 0;
        for (ProcessStatsProto p : processStatsProtoList) {
            if (p.getProcess().equals(statsdPkgName)) {
                LogUtil.CLog.d(p.toString());
                for (State s : p.getStatesList()) {
                    if (s.getProcessState() == ProcessState.CACHED_EMPTY) {
                        durationInProcStats += s.getDurationMs();
                    }
                }
            }
        }
        // Verify that duration is within 1% difference
        assertTrue(Math.abs(durationInStatsd - durationInProcStats) / durationInProcStats
                < 0.1);
    }

    public void testProcessStatePssValue() throws Exception {
        if (statsdDisabled()) {
            return;
        }
        final String fileName = "PROCSTATSQ_PROCS_STATE_PSS_VALUE.pbtxt";
        StatsdConfig config = getConfig(fileName);
        LogUtil.CLog.d("Updating the following config:\n" + config.toString());
        uploadConfig(config);
        clearProcStats();
        Thread.sleep(WAIT_TIME_SHORT);

        // foreground service
        executeForegroundService();
        Thread.sleep(SLEEP_OF_FOREGROUND_SERVICE + EXTRA_WAIT_TIME_MS);
        // background
        executeBackgroundService(ACTION_BACKGROUND_SLEEP);
        Thread.sleep(SLEEP_OF_ACTION_BACKGROUND_SLEEP + EXTRA_WAIT_TIME_MS);
        // top
        executeForegroundActivity(ACTION_LONG_SLEEP_WHILE_TOP);
        Thread.sleep(SLEEP_OF_ACTION_LONG_SLEEP_WHILE_TOP + EXTRA_WAIT_TIME_MS);
        // Start extremely short-lived activity, so app goes into cache state (#1 - #3 above).
        executeBackgroundService(ACTION_END_IMMEDIATELY);
        final int cacheTime = 2_000; // process should be in cached state for up to this long
        Thread.sleep(cacheTime);
        // foreground
        // overlay should take 2 sec to appear. So this makes it 4 sec in TOP
        executeForegroundActivity(ACTION_SHOW_APPLICATION_OVERLAY);
        Thread.sleep(EXTRA_WAIT_TIME_MS + 5_000);

        // Sorted list of events in order in which they occurred.
        List<ValueMetricData> statsdData = getValueMetricDataList();

        List<ProcessStatsProto> processStatsProtoList = getProcStatsProto();

        LogUtil.CLog.d("======================");

        String statsdPkgName = "com.android.server.cts.device.statsd";
        double valueInStatsd = 0;
        for (ValueMetricData d : statsdData) {
            List<DimensionsValue> dimensionsValuesInWhat = d.getDimensionLeafValuesInWhatList();
            if (dimensionsValuesInWhat.get(0).getValueStr().equals(statsdPkgName)
                    && dimensionsValuesInWhat.get(1).getValueStr().equals(statsdPkgName)) {
                LogUtil.CLog.d(d.toString());
                for (ValueBucketInfo bucket : d.getBucketInfoList()) {
                    valueInStatsd = Math.max(bucket.getValueLong(), valueInStatsd);
                }
            }
        }

        double valueInProcStats = 0;
        for (ProcessStatsProto p : processStatsProtoList) {
            if (p.getProcess().equals(statsdPkgName)) {
                LogUtil.CLog.d(p.toString());
                for (State s : p.getStatesList()) {
                    valueInProcStats = Math.max(s.getPss().getMax(), valueInProcStats);
                }
            }
        }
        assertTrue(valueInProcStats > 0);
        assertTrue(valueInStatsd == valueInProcStats);
    }
}
