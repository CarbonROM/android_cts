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
 *
 *
 * This code was provided to AOSP by Zimperium Inc and was
 * written by:
 *
 * Simone "evilsocket" Margaritelli
 * Joshua "jduck" Drake
 */
package android.security.cts;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.TimedText;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.NetworkSecurityPolicy;
import android.util.Log;
import android.view.Surface;
import android.webkit.cts.CtsTestServer;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CrashUtils;
import com.android.compatibility.common.util.mainline.MainlineModule;
import com.android.compatibility.common.util.mainline.ModuleDetector;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Verify that the device is not vulnerable to any known Stagefright
 * vulnerabilities.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class StagefrightTest extends StsExtraBusinessLogicTestCase {
    static final String TAG = "StagefrightTest";

    private final long TIMEOUT_NS = 10000000000L;  // 10 seconds.
    private final static long CHECK_INTERVAL = 50;

    @Rule public TestName name = new TestName();

    class CodecConfig {
        boolean isAudio;
        /* Video Parameters - valid only when isAudio is false */
        int initWidth;
        int initHeight;
        /* Audio Parameters - valid only when isAudio is true */
        int sampleRate;
        int channelCount;

        public CodecConfig setVideoParams(int initWidth, int initHeight) {
            this.isAudio = false;
            this.initWidth = initWidth;
            this.initHeight = initHeight;
            return this;
        }

        public CodecConfig setAudioParams(int sampleRate, int channelCount) {
            this.isAudio = true;
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
            return this;
        }
    }

    /***********************************************************
     to prevent merge conflicts, add K tests below this comment,
     before any existing test methods
     ***********************************************************/

    @Test
    @AsbSecurityTest(cveBugId = 122472139)
    public void testStagefright_cve_2019_2244() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2019_2244, "video/mpeg2", 320, 420);
    }

    @Test
    @AsbSecurityTest(cveBugId = 36725407)
    public void testStagefright_bug_36725407() throws Exception {
        doStagefrightTest(R.raw.bug_36725407);
    }

    @Test
    @AsbSecurityTest(cveBugId = 29023649)
    public void testStagefright_cve_2016_3829() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3829, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 35645051)
    public void testStagefright_cve_2017_0643() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0643, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 37469795)
    public void testStagefright_cve_2017_0728() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0728, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 62187433)
    public void testStagefright_bug_62187433() throws Exception {
        doStagefrightTest(R.raw.bug_62187433);
    }

    @Test
    @AsbSecurityTest(cveBugId = 62673844)
    public void testStagefrightANR_bug_62673844() throws Exception {
        doStagefrightTestANR(R.raw.bug_62673844);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37079296)
    public void testStagefright_bug_37079296() throws Exception {
        doStagefrightTest(R.raw.bug_37079296);
    }

    @Test
    @AsbSecurityTest(cveBugId = 38342499)
    public void testStagefright_bug_38342499() throws Exception {
        doStagefrightTest(R.raw.bug_38342499);
    }

    @Test
    @AsbSecurityTest(cveBugId = 22771132)
    public void testStagefright_bug_22771132() throws Exception {
        doStagefrightTest(R.raw.bug_22771132);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21443020)
    public void testStagefright_bug_21443020() throws Exception {
        doStagefrightTest(R.raw.bug_21443020_webm);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34360591)
    public void testStagefright_bug_34360591() throws Exception {
        doStagefrightTest(R.raw.bug_34360591);
    }

    @Test
    @AsbSecurityTest(cveBugId = 35763994)
    public void testStagefright_bug_35763994() throws Exception {
        doStagefrightTest(R.raw.bug_35763994, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 33137046)
    public void testStagefright_bug_33137046() throws Exception {
        doStagefrightTest(R.raw.bug_33137046);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28532266)
    public void testStagefright_cve_2016_2507() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2507, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 31647370)
    public void testStagefright_bug_31647370() throws Exception {
        doStagefrightTest(R.raw.bug_31647370);
    }

    @Test
    @AsbSecurityTest(cveBugId = 32577290)
    public void testStagefright_bug_32577290() throws Exception {
        doStagefrightTest(R.raw.bug_32577290);
    }

    @Test
    @AsbSecurityTest(cveBugId = 20139950)
    public void testStagefright_cve_2015_1538_1() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_1);
    }

    @Test
    @AsbSecurityTest(cveBugId = 20139950)
    public void testStagefright_cve_2015_1538_2() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_2);
    }

    @Test
    @AsbSecurityTest(cveBugId = 20139950)
    public void testStagefright_cve_2015_1538_3() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_3);
    }

    @Test
    @AsbSecurityTest(cveBugId = 20139950)
    public void testStagefright_cve_2015_1538_4() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1538_4);
    }

    @Test
    @AsbSecurityTest(cveBugId = 20139950)
    public void testStagefright_cve_2015_1539() throws Exception {
        doStagefrightTest(R.raw.cve_2015_1539);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21468251)
    public void testStagefright_cve_2015_3824() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3824);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21467632)
    public void testStagefright_cve_2015_3826() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3826);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21468053)
    public void testStagefright_cve_2015_3827() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3827);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21467634)
    public void testStagefright_cve_2015_3828() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3828);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21467767)
    public void testStagefright_cve_2015_3829() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3829);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21132860)
    public void testStagefright_cve_2015_3836() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3836);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23034759)
    public void testStagefright_cve_2015_3864() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3864);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23034759)
    public void testStagefright_cve_2015_3864_b23034759() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3864_b23034759);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23306638)
    public void testStagefright_cve_2015_6598() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6598);
    }

    @Test
    @AsbSecurityTest(cveBugId = 31318219)
    public void testStagefright_cve_2016_6766() throws Exception {
        doStagefrightTest(R.raw.cve_2016_6766);
    }

    @Test
    @AsbSecurityTest(cveBugId = 27211885)
    public void testStagefright_cve_2016_2429_b_27211885() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2429_b_27211885,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 34031018)
    public void testStagefright_bug_34031018() throws Exception {
        doStagefrightTest(R.raw.bug_34031018_32bit, new CrashUtils.Config().checkMinAddress(false));
        doStagefrightTest(R.raw.bug_34031018_64bit, new CrashUtils.Config().checkMinAddress(false));
    }

    /***********************************************************
     to prevent merge conflicts, add L tests below this comment,
     before any existing test methods
     ***********************************************************/

    @Test
    @AsbSecurityTest(cveBugId = 65123471)
    public void testStagefright_bug_65123471() throws Exception {
        doStagefrightTest(R.raw.bug_65123471);
    }

    @Test
    @AsbSecurityTest(cveBugId = 72165027)
    public void testStagefright_bug_72165027() throws Exception {
        doStagefrightTest(R.raw.bug_72165027);
    }

    @Test
    @AsbSecurityTest(cveBugId = 65483665)
    public void testStagefright_bug_65483665() throws Exception {
        doStagefrightTest(R.raw.bug_65483665);
    }

    @Test
    @AsbSecurityTest(cveBugId = 62815506)
    public void testStagefright_cve_2017_0852_b_62815506() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0852_b_62815506,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 68160703)
    public void testStagefright_cve_2017_13229() throws Exception {
        doStagefrightTest(R.raw.cve_2017_13229);
    }

    @Test
    @AsbSecurityTest(cveBugId = 62534693)
    public void testStagefright_cve_2017_0763() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0763);
    }

    /***********************************************************
     to prevent merge conflicts, add M tests below this comment,
     before any existing test methods
     ***********************************************************/

    @Test
    @AsbSecurityTest(cveBugId = 73965890)
    public void testBug_73965890() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_73965890_framelen);
        doStagefrightTestRawBlob(R.raw.bug_73965890_hevc, "video/hevc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 30744884)
    public void testStagefright_cve_2016_3920() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3920, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 38448381)
    public void testStagefright_bug_38448381() throws Exception {
        doStagefrightTest(R.raw.bug_38448381);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28166152)
    public void testStagefright_cve_2016_3821() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3821, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 70897454)
    public void testStagefright_bug_70897454() throws Exception {
        doStagefrightTestRawBlob(R.raw.b70897454_avc, "video/avc", 320, 420);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28165659)
    public void testStagefright_cve_2016_3742_b_28165659() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3742_b_28165659);
    }

    @Test
    @AsbSecurityTest(cveBugId = 35039946)
    public void testStagefright_bug_35039946() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_35039946_hevc, "video/hevc", 320, 420);
    }

    @Test
    @AsbSecurityTest(cveBugId = 38115076)
    public void testStagefright_bug_38115076() throws Exception {
        doStagefrightTest(R.raw.bug_38115076, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 34618607)
    public void testStagefright_bug_34618607() throws Exception {
        doStagefrightTest(R.raw.bug_34618607, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 69478425)
    public void testStagefright_bug_69478425() throws Exception {
        doStagefrightTest(R.raw.bug_69478425);
    }

    @Test
    @AsbSecurityTest(cveBugId = 65735716)
    public void testStagefright_bug_65735716() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_65735716_avc, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 65717533)
    public void testStagefright_bug_65717533() throws Exception {
        doStagefrightTest(R.raw.bug_65717533_header_corrupt);
    }

    @Test
    @AsbSecurityTest(cveBugId = 38239864)
    public void testStagefright_bug_38239864() throws Exception {
        doStagefrightTest(R.raw.bug_38239864, (4 * 60 * 1000));
    }

    @Test
    @AsbSecurityTest(cveBugId = 35269635)
    public void testStagefright_cve_2017_0600() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0600, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 38014992)
    public void testBug_38014992() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_38014992_framelen);
        doStagefrightTestRawBlob(R.raw.bug_38014992_avc, "video/avc", 640, 480, frameSizes,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 35584425)
    public void testBug_35584425() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_35584425_framelen);
        doStagefrightTestRawBlob(R.raw.bug_35584425_avc, "video/avc", 352, 288, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 31092462)
    public void testBug_31092462() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_31092462_framelen);
        doStagefrightTestRawBlob(R.raw.bug_31092462_avc, "video/avc", 1280, 1024, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34097866)
    public void testBug_34097866() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_34097866_frame_len);
        doStagefrightTestRawBlob(R.raw.bug_34097866_avc, "video/avc", 352, 288, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33862021)
    public void testBug_33862021() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_33862021_frame_len);
        doStagefrightTestRawBlob(R.raw.bug_33862021_hevc, "video/hevc", 160, 96, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33387820)
    public void testBug_33387820() throws Exception {
        int[] frameSizes = {45, 3202, 430, 2526};
        doStagefrightTestRawBlob(R.raw.bug_33387820_avc, "video/avc", 320, 240, frameSizes,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 37008096)
    public void testBug_37008096() throws Exception {
        int[] frameSizes = {245, 12, 33, 140, 164};
        doStagefrightTestRawBlob(R.raw.bug_37008096_avc, "video/avc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34231163)
    public void testStagefright_bug_34231163() throws Exception {
        int[] frameSizes = {22, 357, 217, 293, 175};
        doStagefrightTestRawBlob(R.raw.bug_34231163_mpeg2, "video/mpeg2", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33933140)
    public void testStagefright_bug_33933140() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_33933140_framelen);
        doStagefrightTestRawBlob(R.raw.bug_33933140_avc, "video/avc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34097915)
    public void testStagefright_bug_34097915() throws Exception {
        int[] frameSizes = {4140, 593, 0, 15495};
        doStagefrightTestRawBlob(R.raw.bug_34097915_avc, "video/avc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34097213)
    public void testStagefright_bug_34097213() throws Exception {
        int[] frameSizes = {2571, 210, 33858};
        doStagefrightTestRawBlob(R.raw.bug_34097213_avc, "video/avc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28816956)
    public void testBug_28816956() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_28816956_framelen);
        doStagefrightTestRawBlob(
                R.raw.bug_28816956_hevc, "video/hevc", 352, 288, frameSizes,
                    new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 33818500)
    public void testBug_33818500() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_33818500_framelen);
        doStagefrightTestRawBlob(R.raw.bug_33818500_avc, "video/avc", 64, 32, frameSizes,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 64784973)
    public void testBug_64784973() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_64784973_framelen);
        doStagefrightTestRawBlob(R.raw.bug_64784973_hevc, "video/hevc", 1280, 720, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34231231)
    public void testBug_34231231() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_34231231_framelen);
        doStagefrightTestRawBlob(R.raw.bug_34231231_mpeg2, "video/mpeg2", 352, 288, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 63045918)
    public void testBug_63045918() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_63045918_framelen);
        doStagefrightTestRawBlob(R.raw.bug_63045918_hevc, "video/hevc", 352, 288, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33298089)
    public void testBug_33298089() throws Exception {
        int[] frameSizes = {3247, 430, 221, 2305};
        doStagefrightTestRawBlob(R.raw.bug_33298089_avc, "video/avc", 32, 64, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34672748)
    public void testStagefright_cve_2017_0599() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0599, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 36492741)
    public void testStagefright_bug_36492741() throws Exception {
        doStagefrightTest(R.raw.bug_36492741);
    }

    @Test
    @AsbSecurityTest(cveBugId = 38487564)
    public void testStagefright_bug_38487564() throws Exception {
        doStagefrightTest(R.raw.bug_38487564, (4 * 60 * 1000));
    }

    @Test
    @AsbSecurityTest(cveBugId = 37237396)
    public void testStagefright_bug_37237396() throws Exception {
        doStagefrightTest(R.raw.bug_37237396);
    }

    @Test
    @AsbSecurityTest(cveBugId = 25818142)
    public void testStagefright_cve_2016_0842() throws Exception {
        doStagefrightTest(R.raw.cve_2016_0842);
    }

    @Test
    @AsbSecurityTest(cveBugId = 63121644)
    public void testStagefright_bug_63121644() throws Exception {
        doStagefrightTest(R.raw.bug_63121644);
    }

    @Test
    @AsbSecurityTest(cveBugId = 30593752)
    public void testStagefright_cve_2016_6712() throws Exception {
        doStagefrightTest(R.raw.cve_2016_6712, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 34097231)
    public void testStagefright_bug_34097231() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_34097231_avc, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34097672)
    public void testStagefright_bug_34097672() throws Exception {
        doStagefrightTest(R.raw.bug_34097672);
    }


    @Test
    @AsbSecurityTest(cveBugId = 33751193)
    public void testStagefright_bug_33751193() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_33751193_avc, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 36993291)
    public void testBug_36993291() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36993291_avc, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33818508)
    public void testStagefright_bug_33818508() throws Exception {
        doStagefrightTest(R.raw.bug_33818508, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 32873375)
    public void testStagefright_bug_32873375() throws Exception {
        doStagefrightTest(R.raw.bug_32873375, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 63522067)
    public void testStagefright_bug_63522067() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_63522067_1_hevc, "video/hevc", 320, 420);
        doStagefrightTestRawBlob(R.raw.bug_63522067_2_hevc, "video/hevc", 320, 420);
        doStagefrightTestRawBlob(R.raw.bug_63522067_3_hevc, "video/hevc", 320, 420);
        doStagefrightTestRawBlob(R.raw.bug_63522067_4_hevc, "video/hevc", 320, 420);
    }

    @Test
    @AsbSecurityTest(cveBugId = 25765591)
    public void testStagefright_bug_25765591() throws Exception {
        doStagefrightTest(R.raw.bug_25765591);
    }

    @Test
    @AsbSecurityTest(cveBugId = 62673179)
    public void testStagefright_bug_62673179() throws Exception {
        doStagefrightTest(R.raw.bug_62673179_ts, (4 * 60 * 1000));
    }

    @Test
    @AsbSecurityTest(cveBugId = 69269702)
    public void testStagefright_bug_69269702() throws Exception {
        doStagefrightTest(R.raw.bug_69269702);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23213430)
    public void testStagefright_cve_2015_3867() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3867);
    }

    @Test
    @AsbSecurityTest(cveBugId = 65398821)
    public void testStagefright_bug_65398821() throws Exception {
        doStagefrightTest(R.raw.bug_65398821, ( 4 * 60 * 1000 ) );
    }

    @Test
    @AsbSecurityTest(cveBugId = 23036083)
    public void testStagefright_cve_2015_3869() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3869);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23452792)
    public void testStagefright_bug_23452792() throws Exception {
        doStagefrightTest(R.raw.bug_23452792);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28673410)
    public void testStagefright_cve_2016_3820() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3820);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28165661)
    public void testStagefright_cve_2016_3741() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3741);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28175045)
    public void testStagefright_cve_2016_2506() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2506);
    }

    @Test
    @AsbSecurityTest(cveBugId = 26751339)
    public void testStagefright_cve_2016_2428() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2428, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 28556125)
    public void testStagefright_cve_2016_3756() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3756);
    }

    @Test
    @AsbSecurityTest(cveBugId = 36592202)
    public void testStagefright_bug_36592202() throws Exception {
        Resources resources = getInstrumentation().getContext().getResources();
        AssetFileDescriptor fd = resources.openRawResourceFd(R.raw.bug_36592202);
        final int oggPageSize = 25627;
        byte [] blob = new byte[oggPageSize];
        // 127 bytes read and 25500 zeros constitute one Ogg page
        FileInputStream fis = fd.createInputStream();
        int numRead = fis.read(blob);
        fis.close();
        // Creating temp file
        final File tempFile = File.createTempFile("poc_tmp", ".ogg", null);
        try {
            final FileOutputStream tempFos = new FileOutputStream(tempFile.getAbsolutePath());
            int bytesWritten = 0;
            final long oggPagesRequired = 50000;
            long oggPagesAvailable = tempFile.getUsableSpace() / oggPageSize;
            long numOggPages = Math.min(oggPagesRequired, oggPagesAvailable);
            // Repeat data for specified number of pages
            for (int i = 0; i < numOggPages; i++) {
                tempFos.write(blob);
                bytesWritten += oggPageSize;
            }
            tempFos.close();
            final int fileSize = bytesWritten;
            final int timeout = (10 * 60 * 1000);
            runWithTimeout(new Runnable() {
                @Override
                public void run() {
                    try {
                        doStagefrightTestMediaCodec(tempFile.getAbsolutePath(),
                                new CrashUtils.Config().checkMinAddress(false));
                    } catch (Exception | AssertionError e) {
                        if (!tempFile.delete()) {
                            Log.e(TAG, "Failed to delete temporary PoC file");
                        }
                        fail("Operation was not successful");
                    }
                }
            }, timeout);
        } catch (Exception e) {
            fail("Failed to test b/36592202");
        } finally {
            if (!tempFile.delete()) {
                Log.e(TAG, "Failed to delete temporary PoC file");
            }
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 30822755)
    public void testStagefright_bug_30822755() throws Exception {
        doStagefrightTest(R.raw.bug_30822755);
    }

    @Test
    @AsbSecurityTest(cveBugId = 32322258)
    public void testStagefright_bug_32322258() throws Exception {
        doStagefrightTest(R.raw.bug_32322258, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 23248776)
    public void testStagefright_cve_2015_3873_b_23248776() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_23248776);
    }

    @Test
    @AsbSecurityTest(cveBugId = 35472997)
    public void testStagefright_bug_35472997() throws Exception {
        doStagefrightTest(R.raw.bug_35472997);
    }

    @Test
    @AsbSecurityTest(cveBugId = 20718524)
    public void testStagefright_cve_2015_3873_b_20718524() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_20718524);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34896431)
    public void testStagefright_bug_34896431() throws Exception {
        doStagefrightTest(R.raw.bug_34896431);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33641588)
    public void testBug_33641588() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_33641588_avc, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 22954006)
    public void testStagefright_cve_2015_3862_b_22954006() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3862_b_22954006,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 23213430)
    public void testStagefright_cve_2015_3867_b_23213430() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3867_b_23213430);
    }

    @Test
    @AsbSecurityTest(cveBugId = 21814993)
    public void testStagefright_cve_2015_3873_b_21814993() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3873_b_21814993);
    }

    @Test
    @AsbSecurityTest(cveBugId = 25812590)
    public void testStagefright_bug_25812590() throws Exception {
        doStagefrightTest(R.raw.bug_25812590);
    }

    @Test
    @AsbSecurityTest(cveBugId = 22882938)
    public void testStagefright_cve_2015_6600() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6600);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23227354)
    public void testStagefright_cve_2015_6603() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6603);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23129786)
    public void testStagefright_cve_2015_6604() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6604);
    }

    @Test
    @AsbSecurityTest(cveBugId = 24157524)
    public void testStagefright_bug_24157524() throws Exception {
        doStagefrightTestMediaCodec(R.raw.bug_24157524);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23031033)
    public void testStagefright_cve_2015_3871() throws Exception {
        doStagefrightTest(R.raw.cve_2015_3871);
    }

    @Test
    @AsbSecurityTest(cveBugId = 26070014)
    public void testStagefright_bug_26070014() throws Exception {
        doStagefrightTest(R.raw.bug_26070014);
    }

    @Test
    @AsbSecurityTest(cveBugId = 32915871)
    public void testStagefright_bug_32915871() throws Exception {
        doStagefrightTest(R.raw.bug_32915871);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28333006)
    public void testStagefright_bug_28333006() throws Exception {
        doStagefrightTest(R.raw.bug_28333006);
    }

    @Test
    @AsbSecurityTest(cveBugId = 14388161)
    public void testStagefright_bug_14388161() throws Exception {
        doStagefrightTestMediaPlayer(R.raw.bug_14388161);
    }

    @Test
    @AsbSecurityTest(cveBugId = 28470138)
    public void testStagefright_cve_2016_3755() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3755, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 29493002)
    public void testStagefright_cve_2016_3878_b_29493002() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3878_b_29493002,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 36819262)
    public void testBug_36819262() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36819262_mpeg2, "video/mpeg2", 640, 480);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23680780)
    public void testStagefright_cve_2015_6608_b_23680780() throws Exception {
        doStagefrightTest(R.raw.cve_2015_6608_b_23680780);
    }

    @Test
    @AsbSecurityTest(cveBugId = 36715268)
    public void testStagefright_bug_36715268() throws Exception {
        doStagefrightTest(R.raw.bug_36715268);
    }

    @Test
    @AsbSecurityTest(cveBugId = 27855419)
    public void testStagefright_bug_27855419_CVE_2016_2463() throws Exception {
        doStagefrightTest(R.raw.bug_27855419, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 19779574)
    public void testStagefright_bug_19779574() throws Exception {
        doStagefrightTest(R.raw.bug_19779574, new CrashUtils.Config().checkMinAddress(false));
    }

    /***********************************************************
     to prevent merge conflicts, add N tests below this comment,
     before any existing test methods
     ***********************************************************/

    @Test
    @AsbSecurityTest(cveBugId = 33090864)
    public void testBug_33090864() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_33090864_framelen);
        doStagefrightTestRawBlob(R.raw.bug_33090864_avc, "video/avc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 36279112)
    public void testStagefright_bug_36279112() throws Exception {
        doStagefrightTest(R.raw.bug_36279112, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 33129467)
    public void testStagefright_cve_2017_0640() throws Exception {
        int[] frameSizes = {21, 4};
        doStagefrightTestRawBlob(R.raw.cve_2017_0640_avc, "video/avc", 640, 480,
                frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37203196)
    public void testBug_37203196() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_37203196_framelen);
        doStagefrightTestRawBlob(R.raw.bug_37203196_mpeg2, "video/mpeg2", 48, 48, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 73552574)
    public void testBug_73552574() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_73552574_framelen);
        doStagefrightTestRawBlob(R.raw.bug_73552574_avc, "video/avc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 23285192)
    public void testStagefright_bug_23285192() throws Exception {
        doStagefrightTest(R.raw.bug_23285192);
    }

    @Test
    @AsbSecurityTest(cveBugId = 25928803)
    public void testStagefright_bug_25928803() throws Exception {
        doStagefrightTest(R.raw.bug_25928803);
    }

    @Test
    @AsbSecurityTest(cveBugId = 26399350)
    public void testBug_26399350() throws Exception {
        int[] frameSizes = {657, 54930};
        doStagefrightTestRawBlob(R.raw.bug_26399350_avc, "video/avc", 640, 480,
                frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 113260892)
    public void testBug_113260892() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_113260892_hevc, "video/hevc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 68342866)
    public void testStagefright_bug_68342866() throws Exception {
        NetworkSecurityPolicy policy = NetworkSecurityPolicy.getInstance();
        policy.setCleartextTrafficPermitted(true);
        Thread server = new Thread() {
            @Override
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(8080) {
                        {setSoTimeout(10_000);} // time out after 10 seconds
                    };
                    Socket conn = serverSocket.accept();
                ) {
                    OutputStream outputstream = conn.getOutputStream();
                    InputStream inputStream = conn.getInputStream();
                    byte input[] = new byte[65536];
                    inputStream.read(input, 0, 65536);
                    String inputStr = new String(input);
                    if (inputStr.contains("bug_68342866.m3u8")) {
                        byte http[] = ("HTTP/1.0 200 OK\r\nContent-Type: application/x-mpegURL\r\n\r\n")
                                .getBytes();
                        byte playlist[] = new byte[] { 0x23, 0x45, 0x58, 0x54,
                                0x4D, 0x33, 0x55, 0x0A, 0x23, 0x45, 0x58, 0x54,
                                0x2D, 0x58, 0x2D, 0x53, 0x54, 0x52, 0x45, 0x41,
                                0x4D, 0x2D, 0x49, 0x4E, 0x46, 0x46, 0x43, 0x23,
                                0x45, 0x3A, 0x54, 0x42, 0x00, 0x00, 0x00, 0x0A,
                                0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF,
                                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                                (byte) 0xFF, (byte) 0xFF, 0x3F, 0x2C, 0x4E,
                                0x46, 0x00, 0x00 };
                        outputstream.write(http);
                        outputstream.write(playlist);
                    }
                } catch (IOException e) {
                }
            }
        };
        server.start();
        String uri = "http://127.0.0.1:8080/bug_68342866.m3u8";
        final MediaPlayerCrashListener mpcl =
                new MediaPlayerCrashListener(new CrashUtils.Config().checkMinAddress(false));
        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                mp.setOnPreparedListener(mpcl);
                mp.setOnCompletionListener(mpcl);
                RenderTarget renderTarget = RenderTarget.create();
                Surface surface = renderTarget.getSurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    mp.setDataSource(uri);
                    mp.prepareAsync();
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                } finally {
                    closeQuietly(fd);
                }
                Looper.loop();
                mp.release();
                renderTarget.destroy();
            }
        });
        t.start();
        assertFalse("Device *IS* vulnerable to BUG-68342866",
                mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        t.stopLooper();
        t.join();
        policy.setCleartextTrafficPermitted(false);
        server.join();
    }

    @Test
    @AsbSecurityTest(cveBugId = 74114680)
    public void testStagefright_bug_74114680() throws Exception {
        doStagefrightTest(R.raw.bug_74114680_ts, (10 * 60 * 1000));
    }

    @Test
    @AsbSecurityTest(cveBugId = 70239507)
    public void testStagefright_bug_70239507() throws Exception {
        doStagefrightTestExtractorSeek(R.raw.bug_70239507,1311768465173141112L);
    }

    @Test
    @AsbSecurityTest(cveBugId = 33250932)
    public void testBug_33250932() throws Exception {
    int[] frameSizes = {65, 11, 102, 414};
    doStagefrightTestRawBlob(R.raw.bug_33250932_avc, "video/avc", 640, 480, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37430213)
    public void testStagefright_bug_37430213() throws Exception {
    doStagefrightTest(R.raw.bug_37430213);
    }

    @Test
    @AsbSecurityTest(cveBugId = 68664359)
    public void testStagefright_bug_68664359() throws Exception {
        doStagefrightTest(R.raw.bug_68664359, 60000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 68664359)
    public void testStagefright_bug_110435401() throws Exception {
        doStagefrightTest(R.raw.bug_110435401, 60000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 32589224)
    public void testStagefright_cve_2017_0474() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0474, 120000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 62872863)
    public void testStagefright_cve_2017_0765() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0765);
    }

    @Test
    @AsbSecurityTest(cveBugId = 70637599)
    public void testStagefright_cve_2017_13276() throws Exception {
        doStagefrightTest(R.raw.cve_2017_13276);
    }

    @Test
    @AsbSecurityTest(cveBugId = 31681434)
    public void testStagefright_cve_2016_6764() throws Exception {
        doStagefrightTest(R.raw.cve_2016_6764, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 38495900)
    public void testStagefright_cve_2017_13214() throws Exception {
        doStagefrightTest(R.raw.cve_2017_13214);
    }

    @Test
    @AsbSecurityTest(cveBugId = 35467107)
    public void testStagefright_bug_35467107() throws Exception {
        doStagefrightTest(R.raw.bug_35467107, new CrashUtils.Config().checkMinAddress(false));
    }

    /***********************************************************
     to prevent merge conflicts, add O tests below this comment,
     before any existing test methods
     ***********************************************************/
    @Test
    @AsbSecurityTest(cveBugId = 162756352)
    public void testStagefright_cve_2020_11184() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11184);
    }

    @Test
    @AsbSecurityTest(cveBugId = 130024844)
    public void testStagefright_cve_2019_2107() throws Exception {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
            getInstrumentation().getContext().getPackageManager(),
            MainlineModule.MEDIA_SOFTWARE_CODEC));
        int[] frameSizes = getFrameSizes(R.raw.cve_2019_2107_framelen);
        doStagefrightTestRawBlob(R.raw.cve_2019_2107_hevc, "video/hevc", 1920,
                1080, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 122473145)
    public void testStagefright_cve_2019_2245() throws Exception {
        doStagefrightTest(R.raw.cve_2019_2245);
    }

    @Test
    @AsbSecurityTest(cveBugId = 120483842)
    public void testStagefright_cve_2018_13925() throws Exception {
        doStagefrightTest(R.raw.cve_2018_13925);
    }

    @Test
    @AsbSecurityTest(cveBugId = 157905659)
    public void testStagefright_cve_2020_11139() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11139);
    }

    @Test
    @AsbSecurityTest(cveBugId = 150697436)
    public void testStagefright_cve_2020_3663() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3663);
    }

    @Test
    @AsbSecurityTest(cveBugId = 155653312)
    public void testStagefright_cve_2020_11122() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11122);
    }

    @Test
    @AsbSecurityTest(cveBugId = 153345450)
    public void testStagefright_cve_2020_3688() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3688);
    }

    @Test
    @AsbSecurityTest(cveBugId = 162756122)
    public void testStagefright_cve_2020_11168() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11168);
    }

    @Test
    @AsbSecurityTest(cveBugId = 150697838)
    public void testStagefright_cve_2020_3658() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3658);
    }

    @Test
    @AsbSecurityTest(cveBugId = 148816216)
    public void testStagefright_cve_2020_3633() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3633);
    }

    @Test
    @AsbSecurityTest(cveBugId = 150695050)
    public void testStagefright_cve_2020_3660() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3660);
    }

    @Test
    @AsbSecurityTest(cveBugId = 150695169)
    public void testStagefright_cve_2020_3661() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3661);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271944)
    public void testStagefright_cve_2019_14013() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14013);
    }

    @Test
    @AsbSecurityTest(cveBugId = 150696661)
    public void testStagefright_cve_2020_3662() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3662);
    }

    @Test
    @AsbSecurityTest(cveBugId = 170583712)
    public void testStagefright_cve_2021_0312() throws Exception {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
            getInstrumentation().getContext().getPackageManager(),
            MainlineModule.MEDIA));
        doStagefrightTestExtractorSeek(R.raw.cve_2021_0312, 2, new CrashUtils.Config()
                .setSignals(CrashUtils.SIGSEGV, CrashUtils.SIGBUS, CrashUtils.SIGABRT));
    }

    @Test
    @AsbSecurityTest(cveBugId = 77600398)
    public void testStagefright_cve_2018_9474() throws Exception {
        MediaPlayer mp = new MediaPlayer();
        RenderTarget renderTarget = RenderTarget.create();
        Surface surface = renderTarget.getSurface();
        mp.setSurface(surface);
        AssetFileDescriptor fd = getInstrumentation().getContext().getResources()
                .openRawResourceFd(R.raw.cve_2018_9474);

        mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        mp.prepare();

        MediaPlayer.TrackInfo[] trackInfos = mp.getTrackInfo();
        if (trackInfos == null || trackInfos.length == 0) {
            return;
        }

        MediaPlayer.TrackInfo trackInfo = trackInfos[0];

        int trackType = trackInfo.getTrackType();
        MediaFormat format = trackInfo.getFormat();

        Parcel data = Parcel.obtain();
        trackInfo.writeToParcel(data, 0);

        data.setDataPosition(0);
        int trackTypeFromParcel = data.readInt();
        String mimeTypeFromParcel = data.readString();
        data.recycle();

        if (trackType == trackTypeFromParcel) {
            assertFalse("Device *IS* vulnerable to CVE-2018-9474",
                        mimeTypeFromParcel.equals("und"));
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 130025324)
    public void testStagefright_cve_2019_2108() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2019_2108_hevc, "video/hevc", 320, 240,
            new CrashUtils.Config().setSignals(CrashUtils.SIGSEGV, CrashUtils.SIGBUS,
                                               CrashUtils.SIGABRT));
    }

    @Test
    @AsbSecurityTest(cveBugId = 25747670)
    public void testStagefright_cve_2016_3880() throws Exception {
        Thread server = new Thread() {
            @Override
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(8080) {
                        {setSoTimeout(10_000);} // time out after 10 seconds
                    };
                    Socket conn = serverSocket.accept()
                ) {
                    OutputStream outputstream = conn.getOutputStream();
                    InputStream inputStream = conn.getInputStream();
                    byte input[] = new byte[65536];
                    inputStream.read(input, 0, 65536);
                    String inputStr = new String(input);
                    if (inputStr.contains("DESCRIBE rtsp://127.0.0.1:8080/cve_2016_3880")) {
                        byte http[] = ("RTSP/1.0 200 OK\r\n"
                        + "Server: stagefright/1.2 (Linux;Android 9)\r\n"
                        + "Content-Type: application/sdp\r\n"
                        + "Content-Base: rtsp://127.0.0.1:8080/cve_2016_3880\r\n"
                        + "Content-Length: 379\r\n"
                        + "Cache-Control: no-cache\r\nCSeq: 1\r\n\r\n").getBytes();

                        byte sdp[] = ("v=0\r\no=- 64 233572944 IN IP4 127.0.0.0\r\n"
                        + "s=QuickTime\r\nt=0 0\r\na=range:npt=now-\r\n"
                        + "m=video 5434 RTP/AVP 96123456\r\nc=IN IP4 127.0.0.1\r\n"
                        + "b=AS:320000\r\na=rtpmap:96123456 H264/90000\r\n"
                        + "a=fmtp:96123456 packetization-mode=1;profile-level-id=42001E;"
                        + "sprop-parameter-sets=Z0IAHpZUBaHogA==,aM44gA==\r\n"
                        + "a=cliprect:0,0,480,270\r\na=framesize:96123456 720-480\r\n"
                        + "a=control:track1\r\n").getBytes();

                        outputstream.write(http);
                        outputstream.write(sdp);
                        outputstream.flush();
                    }
                } catch (IOException e) {
                }
            }
        };
        server.start();
        String uri = "rtsp://127.0.0.1:8080/cve_2016_3880";
        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(
                new CrashUtils.Config()
                        .setSignals(CrashUtils.SIGSEGV, CrashUtils.SIGBUS, CrashUtils.SIGABRT)
                        .appendAbortMessageExcludes("CHECK\\(IsRTSPVersion"));
        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                mp.setOnPreparedListener(mpcl);
                mp.setOnCompletionListener(mpcl);
                RenderTarget renderTarget = RenderTarget.create();
                Surface surface = renderTarget.getSurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    mp.setDataSource(uri);
                    mp.prepareAsync();
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                } finally {
                    closeQuietly(fd);
                }
                Looper.loop();
                mp.release();
            }
        });
        t.start();
        assertFalse("Device *IS* vulnerable to CVE-2016-3880",
                mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        t.stopLooper();
        t.join();
        server.join();
    }

    @Test
    @AsbSecurityTest(cveBugId = 170240631)
    public void testStagefright_bug170240631() throws Exception {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
            getInstrumentation().getContext().getPackageManager(),
            MainlineModule.MEDIA));
        doStagefrightTest(R.raw.bug170240631_ts);
    }

    @Test
    @AsbSecurityTest(cveBugId = 148816624)
    public void testStagefright_cve_2020_3641() throws Exception {
        doStagefrightTest(R.raw.cve_2020_3641);
    }

    @Test
    @AsbSecurityTest(cveBugId = 147103871)
    public void testStagefright_cve_2019_14127() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14127);
    }

    @Test
    @AsbSecurityTest(cveBugId = 147104052)
    public void testStagefright_cve_2019_14132() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14132);
    }

    @Test
    @AsbSecurityTest(cveBugId = 145545283)
    public void testStagefright_cve_2019_10591() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10591);
    }

    @Test
    @AsbSecurityTest(cveBugId = 143903858)
    public void testStagefright_cve_2019_10590() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10590);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271848)
    public void testStagefright_cve_2019_14004() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14004);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271498)
    public void testStagefright_cve_2019_14003() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14003);
    }

    @Test
    @AsbSecurityTest(cveBugId = 143903018)
    public void testStagefright_cve_2019_14057() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14057);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271634)
    public void testStagefright_cve_2019_10532() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10532);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142268949)
    public void testStagefright_cve_2019_10578() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10578);
    }

    @Test
    @AsbSecurityTest(cveBugId = 145545758)
    public void testStagefright_cve_2019_14061() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14061, 180000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271615)
    public void testStagefright_cve_2019_10611() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10611);
    }

    @Test
    @AsbSecurityTest(cveBugId = 132108754)
    public void testStagefright_cve_2019_10489() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10489);
    }

    @Test
    @AsbSecurityTest(cveBugId = 145545282)
    public void testStagefright_cve_2019_14048() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14048);
    }

    @Test
    @AsbSecurityTest(cveBugId = 129766432)
    public void testStagefright_cve_2019_2253() throws Exception {
        doStagefrightTest(R.raw.cve_2019_2253);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271692)
    public void testStagefright_cve_2019_10579() throws Exception {
        doStagefrightTestANR(R.raw.cve_2019_10579);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271965)
    public void testStagefright_cve_2019_14005() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14005);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271827)
    public void testStagefright_cve_2019_14006() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14006);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142270646)
    public void testStagefright_CVE_2019_14016() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14016);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142271515)
    public void testStagefright_CVE_2019_14017() throws Exception {
        doStagefrightTest(R.raw.cve_2019_14017);
    }

    @Test
    @AsbSecurityTest(cveBugId = 78029004)
    public void testStagefright_cve_2018_9412() throws Exception {
        doStagefrightTest(R.raw.cve_2018_9412, 180000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 142641801)
    public void testStagefright_bug_142641801() throws Exception {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
            getInstrumentation().getContext().getPackageManager(),
            MainlineModule.MEDIA));
        doStagefrightTest(R.raw.bug_142641801);
    }

    @Test
    @AsbSecurityTest(cveBugId = 134437379)
    public void testStagefright_cve_2019_10534() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10534);
    }

    @Test
    @AsbSecurityTest(cveBugId = 134437210)
    public void testStagefright_cve_2019_10533() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10533);
    }

    @Test
    @AsbSecurityTest(cveBugId = 134437115)
    public void testStagefright_cve_2019_10541() throws Exception {
        doStagefrightTest(R.raw.cve_2019_10541);
    }

    @Test
    @AsbSecurityTest(cveBugId = 62851602)
    public void testStagefright_cve_2017_13233() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2017_13233_hevc, "video/hevc", 640,
                480);
    }

    @Test
    @AsbSecurityTest(cveBugId = 130023983)
    public void testStagefright_cve_2019_2106() throws Exception {
        int[] frameSizes = {943, 3153};
        doStagefrightTestRawBlob(R.raw.cve_2019_2106_hevc, "video/hevc", 320,
                240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 34064500)
    public void testStagefright_cve_2017_0637() throws Exception {
        doStagefrightTest(R.raw.cve_2017_0637, 2 * 72000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 109678380)
    public void testStagefright_cve_2018_11287() throws Exception {
        doStagefrightTest(R.raw.cve_2018_11287, 180000);
    }

    @Test
    @AsbSecurityTest(cveBugId = 129766125)
    public void testStagefright_cve_2019_2327() throws Exception {
        doStagefrightTest(R.raw.cve_2019_2327);
    }

    @Test
    @AsbSecurityTest(cveBugId = 129766496)
    public void testStagefright_cve_2019_2322() throws Exception {
        doStagefrightTest(R.raw.cve_2019_2322);
    }

    @Test
    @AsbSecurityTest(cveBugId = 129766099)
    public void testStagefright_cve_2019_2334() throws Exception {
        doStagefrightTest(R.raw.cve_2019_2334);
    }

    @Test
    @AsbSecurityTest(cveBugId = 64380237)
    public void testStagefright_cve_2017_13204() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.cve_2017_13204_framelen);
        doStagefrightTestRawBlob(R.raw.cve_2017_13204_avc, "video/avc", 16, 16, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 70221445)
    public void testStagefright_cve_2017_17773() throws Exception {
        doStagefrightTest(R.raw.cve_2017_17773);
    }

    @Test
    @AsbSecurityTest(cveBugId = 68326816)
    public void testStagefright_cve_2017_18074() throws Exception {
        doStagefrightTest(R.raw.cve_2017_18074);
    }

    @Test
    @AsbSecurityTest(cveBugId = 74236854)
    public void testStagefright_cve_2018_5894() throws Exception {
        doStagefrightTest(R.raw.cve_2018_5894);
    }

    @Test
    @AsbSecurityTest(cveBugId = 77485139)
    public void testStagefright_cve_2018_5874() throws Exception {
        doStagefrightTest(R.raw.cve_2018_5874);
    }

    @Test
    @AsbSecurityTest(cveBugId = 77485183)
    public void testStagefright_cve_2018_5875() throws Exception {
        doStagefrightTest(R.raw.cve_2018_5875);
    }

    @Test
    @AsbSecurityTest(cveBugId = 77485022)
    public void testStagefright_cve_2018_5876() throws Exception {
        doStagefrightTest(R.raw.cve_2018_5876);
    }

    @Test
    @AsbSecurityTest(cveBugId = 77483830)
    public void testStagefright_cve_2018_5882() throws Exception {
        doStagefrightTest(R.raw.cve_2018_5882);
    }

    @Test
    @AsbSecurityTest(cveBugId = 65186291)
    public void testBug_65186291() throws Exception {
        int[] frameSizes = getFrameSizes(R.raw.bug_65186291_framelen);
        doStagefrightTestRawBlob(R.raw.bug_65186291_hevc, "video/hevc", 1920, 1080, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 67737022)
    public void testBug_67737022() throws Exception {
        doStagefrightTest(R.raw.bug_67737022);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37093318)
    public void testStagefright_bug_37093318() throws Exception {
        doStagefrightTest(R.raw.bug_37093318, (4 * 60 * 1000));
    }

    @Test
    @AsbSecurityTest(cveBugId = 73172046)
    public void testStagefright_bug_73172046() throws Exception {
        doStagefrightTest(R.raw.bug_73172046);

        Bitmap bitmap = BitmapFactory.decodeResource(
                getInstrumentation().getContext().getResources(), R.raw.bug_73172046);
        // OK if the decoding failed, but shouldn't cause crashes
        if (bitmap != null) {
            bitmap.recycle();
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 25765591)
    public void testStagefright_cve_2016_0824() throws Exception {
        doStagefrightTest(R.raw.cve_2016_0824);
    }

    @Test
    @AsbSecurityTest(cveBugId = 26365349)
    public void testStagefright_cve_2016_0815() throws Exception {
        doStagefrightTest(R.raw.cve_2016_0815);
    }

    @Test
    @AsbSecurityTest(cveBugId = 26221024)
    public void testStagefright_cve_2016_2454() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2454);
    }

    @Test
    @AsbSecurityTest(cveBugId = 31449945)
    public void testStagefright_cve_2016_6765() throws Exception {
        doStagefrightTest(R.raw.cve_2016_6765, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 28799341)
    public void testStagefright_cve_2016_2508() throws Exception {
        doStagefrightTest(R.raw.cve_2016_2508, new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 31373622)
    public void testStagefright_cve_2016_6699() throws Exception {
        doStagefrightTest(R.raw.cve_2016_6699);
    }

    @Test
    @AsbSecurityTest(cveBugId = 66734153)
    public void testStagefright_cve_2017_18155() throws Exception {
        doStagefrightTest(R.raw.cve_2017_18155);
    }

    @Test
    @AsbSecurityTest(cveBugId = 77599438)
    public void testStagefright_cve_2018_9423() throws Exception {
        doStagefrightTest(R.raw.cve_2018_9423);
    }

    @Test
    @AsbSecurityTest(cveBugId = 29770686)
    public void testStagefright_cve_2016_3879() throws Exception {
        doStagefrightTest(R.raw.cve_2016_3879, new CrashUtils.Config().checkMinAddress(false));
    }

    /***********************************************************
     to prevent merge conflicts, add P tests below this comment,
     before any existing test methods
     ***********************************************************/

    @Test
    @AsbSecurityTest(cveBugId = 179039901)
    public void testStagefright_cve_2021_1910() throws Exception {
        doStagefrightTest(R.raw.cve_2021_1910);
    }

    @Test
    @AsbSecurityTest(cveBugId = 175038625)
    public void testStagefright_cve_2020_11299() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11299);
    }

    @Test
    @AsbSecurityTest(cveBugId = 162756960)
    public void testStagefright_cve_2020_11196() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11196);
    }

    @Test
    @AsbSecurityTest(cveBugId = 112661641)
    public void testStagefright_cve_2018_9531() throws Exception {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
                getInstrumentation().getContext().getPackageManager(),
                MainlineModule.MEDIA_SOFTWARE_CODEC));
        int[] frameSizes = getFrameSizes(R.raw.cve_2018_9531_framelen);
        CodecConfig codecConfig = new CodecConfig().setAudioParams(48000, 8);
        doStagefrightTestRawBlob(R.raw.cve_2018_9531_aac, "audio/mp4a-latm", codecConfig,
                frameSizes, new CrashUtils.Config().setSignals(CrashUtils.SIGSEGV,
                        CrashUtils.SIGBUS, CrashUtils.SIGABRT));
    }

    @Test
    @AsbSecurityTest(cveBugId = 140322595)
    public void testStagefright_cve_2019_2222() throws Exception {
        // TODO(b/170987914): This also skips testing hw_codecs.
        // Update doStagefrightTestRawBlob to skip just the sw_codec test.
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
            getInstrumentation().getContext().getPackageManager(),
            MainlineModule.MEDIA_SOFTWARE_CODEC));
        int[] frameSizes = getFrameSizes(R.raw.cve_2019_2222_framelen);
        doStagefrightTestRawBlob(R.raw.cve_2019_2222_hevc, "video/hevc", 320, 240, frameSizes);
    }

    private void doStagefrightTest(final int rid) throws Exception {
        doStagefrightTest(rid, null);
    }

    /***********************************************************
     to prevent merge conflicts, add Q tests below this comment,
     before any existing test methods
     ***********************************************************/
    @Test
    @AsbSecurityTest(cveBugId = 240971780)
    public void testStagefright_cve_2022_33234() throws Exception {
         doStagefrightTest(R.raw.cve_2022_33234);
    }

    @Test
    @AsbSecurityTest(cveBugId = 235102508)
    public void testStagefright_cve_2022_25669() throws Exception {
         doStagefrightTest(R.raw.cve_2022_25669);
    }

    @Test
    @AsbSecurityTest(cveBugId = 223209306)
    public void testStagefright_cve_2022_22085() throws Exception {
         doStagefrightTest(R.raw.cve_2022_22085);
    }

    @Test
    @AsbSecurityTest(cveBugId = 223209816)
    public void testStagefright_cve_2022_22084() throws Exception {
         doStagefrightTest(R.raw.cve_2022_22084);
    }

    @Test
    @AsbSecurityTest(cveBugId = 223211218)
    public void testStagefright_cve_2022_22086() throws Exception {
         doStagefrightTest(R.raw.cve_2022_22086);
    }

    @Test
    @AsbSecurityTest(cveBugId = 228101819)
    public void testStagefright_cve_2022_25659() throws Exception {
         doStagefrightTest(R.raw.cve_2022_25659);
    }

    @Test
    @AsbSecurityTest(cveBugId = 223210917)
    public void testStagefright_cve_2022_22083() throws Exception {
         doStagefrightTest(R.raw.cve_2022_22083);
    }

    @Test
    @AsbSecurityTest(cveBugId = 223209610)
    public void testStagefright_cve_2022_22087() throws Exception {
         doStagefrightTest(R.raw.cve_2022_22087);
    }

    @Test
    @AsbSecurityTest(cveBugId = 228101835)
    public void testStagefright_cve_2022_25657() throws Exception {
         doStagefrightTest(R.raw.cve_2022_25657);
    }

    @Test
    @AsbSecurityTest(cveBugId = 231156126)
    public void testStagefright_cve_2022_22059() throws Exception {
         doStagefrightTest(R.raw.cve_2022_22059);
    }

    @Test
    @AsbSecurityTest(cveBugId = 157906313)
    public void testStagefright_cve_2020_11135() throws Exception {
        doStagefrightTest(R.raw.cve_2020_11135);
    }

    @Test
    @AsbSecurityTest(cveBugId = 136175447)
    public void testStagefright_cve_2019_2186() throws Exception {
        long end = System.currentTimeMillis() + 180000; // 3 minutes from now
        while (System.currentTimeMillis() < end) {
            doStagefrightTestRawBlob(R.raw.cve_2019_2186, "video/3gpp", 128, 96,
                    new CrashUtils.Config().setSignals(CrashUtils.SIGSEGV, CrashUtils.SIGBUS,
                            CrashUtils.SIGABRT));
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = 140692129)
    public void testStagefright_cve_2019_2223() throws Exception {
        // TODO(b/170987914): This also skips testing hw_codecs.
        // Update doStagefrightTestRawBlob to skip just the sw_codec test.
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
            getInstrumentation().getContext().getPackageManager(),
            MainlineModule.MEDIA_SOFTWARE_CODEC));
        int[] frameSizes = getFrameSizes(R.raw.cve_2019_2223_framelen);
        doStagefrightTestRawBlob(R.raw.cve_2019_2223_hevc, "video/hevc", 320, 240, frameSizes);
    }

    @Test
    @AsbSecurityTest(cveBugId = 118399205)
    public void testStagefright_cve_2019_1989() throws Exception {
        Object obj[] = getFrameInfo(R.raw.cve_2019_1989_info);
        int[] isHeader = (int[])obj [0];
        int[] frameSizes = (int[])obj [1];
        doStagefrightTestRawBlob(R.raw.cve_2019_1989_h264, "video/avc",
                1920, 1080, frameSizes, isHeader, new CrashUtils.Config());
    }

    private void doStagefrightTest(final int rid, CrashUtils.Config config) throws Exception {
        NetworkSecurityPolicy policy = NetworkSecurityPolicy.getInstance();
        policy.setCleartextTrafficPermitted(true);
        doStagefrightTestMediaPlayer(rid, config);
        doStagefrightTestMediaCodec(rid, config);
        doStagefrightTestMediaMetadataRetriever(rid, config);

        Context context = getInstrumentation().getContext();
        CtsTestServer server = null;
        try {
            server = new CtsTestServer(context);
        } catch (BindException e) {
            // Instant Apps security policy does not allow
            // listening for incoming connections.
            // Server based tests cannot be run.
            return;
        }
        Resources resources =  context.getResources();
        String rname = resources.getResourceEntryName(rid);
        String url = server.getAssetUrl("raw/" + rname);
        verifyServer(rid, url);
        doStagefrightTestMediaPlayer(url, config);
        doStagefrightTestMediaCodec(url, config);
        doStagefrightTestMediaMetadataRetriever(url, config);
        policy.setCleartextTrafficPermitted(false);
        server.shutdown();
    }

    // verify that CtsTestServer is functional by retrieving the asset
    // and comparing it to the resource
    private void verifyServer(final int rid, final String uri) throws Exception {
        Log.i(TAG, "checking server");
        URL url = new URL(uri);
        InputStream in1 = new BufferedInputStream(url.openStream());

        AssetFileDescriptor fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(rid);
        InputStream in2 = new BufferedInputStream(fd.createInputStream());

        while (true) {
            int b1 = in1.read();
            int b2 = in2.read();
            assertEquals("CtsTestServer fail", b1, b2);
            if (b1 < 0) {
                break;
            }
        }

        in1.close();
        in2.close();
        Log.i(TAG, "checked server");
    }

    private void doStagefrightTest(final int rid, int timeout) throws Exception {
        doStagefrightTest(rid, null, timeout);
    }

    private void doStagefrightTest(
            final int rid, CrashUtils.Config config, int timeout) throws Exception {
        runWithTimeout(new Runnable() {
            @Override
            public void run() {
                try {
                  doStagefrightTest(rid, config);
                } catch (Exception e) {
                  fail(e.toString());
                }
            }
        }, timeout);
    }

    private void doStagefrightTestANR(final int rid) throws Exception {
        doStagefrightTestANR(rid, null);
    }

    private void doStagefrightTestANR(
            final int rid, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaPlayerANR(rid, null, config);
    }

    public JSONArray getCrashReport(String testname, long timeout)
        throws InterruptedException {
        Log.i(TAG, CrashUtils.UPLOAD_REQUEST);
        File reportFile = new File(CrashUtils.DEVICE_PATH, testname);
        File lockFile = new File(CrashUtils.DEVICE_PATH, CrashUtils.LOCK_FILENAME);
        while ((!reportFile.exists() || !lockFile.exists()) && timeout > 0) {
            Thread.sleep(CHECK_INTERVAL);
            timeout -= CHECK_INTERVAL;
        }

        if (!reportFile.exists() || !reportFile.isFile() || !lockFile.exists()) {
            Log.e(TAG, "couldn't get the report or lock file");
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(reportFile))) {
            StringBuilder json = new StringBuilder();
            String line = reader.readLine();
            while (line != null) {
                json.append(line);
                line = reader.readLine();
            }
            return new JSONArray(json.toString());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to deserialize crash list with error " + e.getMessage());
            return null;
        }
    }

    class MediaPlayerCrashListener
        implements MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener {

        CrashUtils.Config config;

        private final Pattern[] validProcessPatterns = {
            Pattern.compile("adsprpcd"),
            Pattern.compile("android\\.hardware\\.cas@\\d+?\\.\\d+?-service"),
            Pattern.compile("android\\.hardware\\.drm@\\d+?\\.\\d+?-service"),
            Pattern.compile("android\\.hardware\\.drm@\\d+?\\.\\d+?-service\\.clearkey"),
            Pattern.compile("android\\.hardware\\.drm@\\d+?\\.\\d+?-service\\.widevine"),
            Pattern.compile("omx@\\d+?\\.\\d+?-service"),  // name:omx@1.0-service
            Pattern.compile("android\\.process\\.media"),
            Pattern.compile("mediadrmserver"),
            Pattern.compile("mediaextractor"),
            Pattern.compile("media\\.extractor"),
            Pattern.compile("media\\.metrics"),
            Pattern.compile("mediaserver"),
            Pattern.compile("media\\.codec"),
            Pattern.compile("media\\.swcodec"),
            Pattern.compile("\\[?sdcard\\]?"), // name:/system/bin/sdcard, user:media_rw
            // Match any vendor processes.
            // It should only catch crashes that happen during the test.
            Pattern.compile("vendor.*"),
        };

        MediaPlayerCrashListener() {
            this(null);
        }

        MediaPlayerCrashListener(CrashUtils.Config config) {
            if (config == null) {
                config = new CrashUtils.Config();
            }
            // if a different process is needed for a test, it should be added to the main list.
            config.setProcessPatterns(validProcessPatterns);
            this.config = config;
        }

        @Override
        public boolean onError(MediaPlayer mp, int newWhat, int extra) {
            Log.i(TAG, "error: " + newWhat + "/" + extra);
            // don't overwrite a more severe error with a less severe one
            if (what != MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                what = newWhat;
            }

            lock.lock();
            errored = true;
            condition.signal();
            lock.unlock();

            return true; // don't call oncompletion
        }

        @Override
        public void onPrepared(MediaPlayer mp) {
            mp.start();
        }

        @Override
        public void onCompletion(MediaPlayer mp) {
            // preserve error condition, if any
            lock.lock();
            completed = true;
            condition.signal();
            lock.unlock();
        }

        public int waitForError() throws InterruptedException {
            lock.lock();
            if (!errored && !completed) {
                if (condition.awaitNanos(TIMEOUT_NS) <= 0) {
                    Log.d(TAG, "timed out on waiting for error. " +
                          "errored: " + errored + ", completed: " + completed);
                }
            }
            lock.unlock();
            if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                // Sometimes mediaserver signals a decoding error first, and *then* crashes
                // due to additional in-flight buffers being processed, so wait a little
                // and see if more errors show up.
                Log.e(TAG, "couldn't get media crash yet, waiting 1 second");
                SystemClock.sleep(1000);
                JSONArray crashes = getCrashReport(name.getMethodName(), 5000);
                if (crashes == null) {
                    Log.e(TAG, "Crash results not found for test " + name.getMethodName());
                    return what;
                } else if (CrashUtils.securityCrashDetected(crashes, config)) {
                    return what;
                } else {
                    Log.i(TAG, "Crash ignored due to no security crash found for test " +
                        name.getMethodName());
                    // 0 is the code for no error.
                    return 0;
                }
            }
            Log.d(TAG, "waitForError finished with no errors.");
            return what;
        }

        public boolean waitForErrorOrCompletion() throws InterruptedException {
            lock.lock();
            if (condition.awaitNanos(TIMEOUT_NS) <= 0) {
                Log.d(TAG, "timed out on waiting for error or completion");
            }
            lock.unlock();
            return (what != 0 && what != MediaPlayer.MEDIA_ERROR_SERVER_DIED) || completed;
        }

        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        int what;
        boolean completed = false;
        boolean errored = false;
    }

    class LooperThread extends Thread {
        private Looper mLooper;

        LooperThread(Runnable runner) {
            super(runner);
        }

        @Override
        public void run() {
            Looper.prepare();
            mLooper = Looper.myLooper();
            super.run();
        }

        public void stopLooper() {
            mLooper.quitSafely();
        }
    }

    private void doStagefrightTestMediaPlayer(final int rid) throws Exception {
        doStagefrightTestMediaPlayer(rid, null, null);
    }

    private void doStagefrightTestMediaPlayer(
            final int rid, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaPlayer(rid, null, config);
    }

    private void doStagefrightTestMediaPlayer(final String url) throws Exception {
        doStagefrightTestMediaPlayer(url, null);
    }

    private void doStagefrightTestMediaPlayer(
            final String url, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaPlayer(-1, url, config);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    private void doStagefrightTestMediaPlayer(final int rid, final String uri) throws Exception {
        doStagefrightTestMediaPlayer(rid, uri, null);
    }

    private void doStagefrightTestMediaPlayer(final int rid, final String uri,
            CrashUtils.Config config) throws Exception {

        String name = uri != null ? uri :
            getInstrumentation().getContext().getResources().getResourceEntryName(rid);
        Log.i(TAG, "start mediaplayer test for: " + name);

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);

        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                mp.setOnPreparedListener(mpcl);
                mp.setOnCompletionListener(mpcl);
                RenderTarget renderTarget = RenderTarget.create();
                Surface surface = renderTarget.getSurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    if (uri == null) {
                        fd = getInstrumentation().getContext().getResources()
                                .openRawResourceFd(rid);

                        mp.setDataSource(fd.getFileDescriptor(),
                                         fd.getStartOffset(),
                                         fd.getLength());

                    } else {
                        mp.setDataSource(uri);
                    }
                    mp.prepareAsync();
                } catch (Exception e) {
                } finally {
                    closeQuietly(fd);
                }

                Looper.loop();
                mp.release();
                renderTarget.destroy();
            }
        });

        t.start();
        assertNotEquals("MediaPlayer encountered a security crash when testing MediaPlayer.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        t.stopLooper();
        t.join(); // wait for thread to exit so we're sure the player was released
    }

    /*
     * b/135207745
     */
    @Test
    @AsbSecurityTest(cveBugId = 124781927)
    public void testStagefright_cve_2019_2129() throws Exception {
        final int rid = R.raw.cve_2019_2129;
        String name = getInstrumentation().getContext().getResources().getResourceEntryName(rid);
        Log.i(TAG, "start mediaplayer test for: " + name);

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                super.onPrepared(mp);
                mp.setLooping(true);
            }
        };

        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                mp.setOnPreparedListener(mpcl);
                mp.setOnCompletionListener(mpcl);
                RenderTarget renderTarget = RenderTarget.create();
                Surface surface = renderTarget.getSurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    fd = getInstrumentation().getContext().getResources().openRawResourceFd(rid);
                    mp.setOnTimedTextListener(new MediaPlayer.OnTimedTextListener() {
                        @Override
                        public void onTimedText(MediaPlayer p, TimedText text) {
                            if (text != null) {
                                Log.d(TAG, "text = " + text.getText());
                            }
                        }
                    });
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    //  keep the original as in poc by not using prepareAsync
                    mp.prepare();
                    mp.selectTrack(2);
                } catch (Exception e) {
                    Log.e(TAG, "Exception is caught " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    closeQuietly(fd);
                }

                try {
                    //  here to catch & swallow the runtime crash in exception
                    //  after the place where original poc failed in
                    //  java.lang.IllegalArgumentException: parseParcel()
                    //  which is beyond test control.
                    Looper.loop();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception is caught on Looper.loop() " + e.getMessage());
                    e.printStackTrace();
                }
                mp.release();
                renderTarget.destroy();
            }
        });

        t.start();
        assertNotEquals("MediaPlayer encountered a security crash when testing CVE-2019-2129.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        t.stopLooper();
        t.join(); // wait for thread to exit so we're sure the player was released
    }

    private void doStagefrightTestMediaCodec(final int rid) throws Exception {
        doStagefrightTestMediaCodec(rid, null, null);
    }

    private void doStagefrightTestMediaCodec(
            final int rid, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaCodec(rid, null, config);
    }

    private void doStagefrightTestMediaCodec(final String url) throws Exception {
        doStagefrightTestMediaCodec(url, null);
    }

    private void doStagefrightTestMediaCodec(
            final String url, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaCodec(-1, url, config);
    }

    private void doStagefrightTestMediaCodec(final int rid, final String url) throws Exception {
        doStagefrightTestMediaCodec(rid, url, null);
    }

    private void doStagefrightTestMediaCodec(
            final int rid, final String url, CrashUtils.Config config) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                try {
                    AssetFileDescriptor fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        Resources resources =  getInstrumentation().getContext().getResources();
        MediaExtractor ex = new MediaExtractor();
        if (url == null) {
            AssetFileDescriptor fd = resources.openRawResourceFd(rid);
            try {
                ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (IOException e) {
                // ignore
            } finally {
                closeQuietly(fd);
            }
        } else {
            try {
                ex.setDataSource(url);
            } catch (Exception e) {
                // indicative of problems with our tame CTS test web server
            }
        }
        int numtracks = ex.getTrackCount();
        String rname = url != null ? url: resources.getResourceEntryName(rid);
        Log.i(TAG, "start mediacodec test for: " + rname + ", which has " + numtracks + " tracks");
        for (int t = 0; t < numtracks; t++) {
            // find all the available decoders for this format
            ArrayList<String> matchingCodecs = new ArrayList<String>();
            MediaFormat format = null;
            try {
                format = ex.getTrackFormat(t);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "could not get track format for track " + t);
                continue;
            }
            String mime = format.getString(MediaFormat.KEY_MIME);
            int numCodecs = MediaCodecList.getCodecCount();
            for (int i = 0; i < numCodecs; i++) {
                MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) {
                    continue;
                }
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                    if (caps != null) {
                        /* Add mainline skip to decoders in mainline module */
                        if (isCodecInMainlineModule(info.getName())) {
                            Log.i(TAG, "Skipping codec " + info.getName() +
                                        " as it is part of mainline");
                            continue;
                        }
                        if (info.isAlias()) {
                            Log.i(TAG, "Skipping codec " + info.getName() + " as it is an alias");
                            continue;
                        }
                        matchingCodecs.add(info.getName());
                        Log.i(TAG, "Found matching codec " + info.getName() + " for track " + t);
                    }
                } catch (IllegalArgumentException e) {
                    // type is not supported
                }
            }

            if (matchingCodecs.size() == 0) {
                Log.w(TAG, "no codecs for track " + t + ", type " + mime);
            }
            // decode this track once with each matching codec
            try {
                ex.selectTrack(t);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "couldn't select track " + t);
                // continue on with codec initialization anyway, since that might still crash
            }
            for (String codecName: matchingCodecs) {
                Log.i(TAG, "Decoding track " + t + " using codec " + codecName);
                ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                MediaCodec codec = MediaCodec.createByCodecName(codecName);
                RenderTarget renderTarget = RenderTarget.create();
                Surface surface = null;
                if (mime.startsWith("video/")) {
                    surface = renderTarget.getSurface();
                }
                try {
                    codec.configure(format, surface, null, 0);
                    codec.start();
                } catch (Exception e) {
                    Log.i(TAG, "Failed to start/configure:", e);
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                try {
                    ByteBuffer [] inputBuffers = codec.getInputBuffers();
                    long startTime = System.nanoTime();
                    while (System.nanoTime() - startTime < TIMEOUT_NS) {
                        int flags = ex.getSampleFlags();
                        long time = ex.getSampleTime();
                        ex.getCachedDuration();
                        int bufidx = codec.dequeueInputBuffer(5000);
                        if (bufidx >= 0) {
                            int n = ex.readSampleData(inputBuffers[bufidx], 0);
                            if (n < 0) {
                                flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                time = 0;
                                n = 0;
                            }
                            codec.queueInputBuffer(bufidx, 0, n, time, flags);
                            ex.advance();
                        }
                        int status = codec.dequeueOutputBuffer(info, 5000);
                        if (status >= 0) {
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                break;
                            }
                            if (info.presentationTimeUs > TIMEOUT_NS / 1000) {
                                Log.d(TAG, "stopping after 10 seconds worth of data");
                                break;
                            }
                            codec.releaseOutputBuffer(status, true);
                        }
                    }
                } catch (Exception e) {
                    // local exceptions ignored, not security issues
                } finally {
                    try {
                        codec.stop();
                    } catch (Exception e) {
                        // local exceptions ignored, not security issues
                    }
                    codec.release();
                    renderTarget.destroy();
                }
            }
            try {
                ex.unselectTrack(t);
            } catch (IllegalArgumentException e) {
                // since we're just cleaning up, we don't care if it fails
            }
        }
        ex.release();
        assertNotEquals("MediaPlayer encountered a security crash when testing media codecs.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        thr.stopLooper();
        thr.join();
    }

    private void doStagefrightTestMediaMetadataRetriever(final int rid) throws Exception {
        doStagefrightTestMediaMetadataRetriever(rid, null, null);
    }
    private void doStagefrightTestMediaMetadataRetriever(
            final int rid, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaMetadataRetriever(rid, null, config);
    }

    private void doStagefrightTestMediaMetadataRetriever(final String url) throws Exception {
        doStagefrightTestMediaMetadataRetriever(url, null);
    }

    private void doStagefrightTestMediaMetadataRetriever(
            final String url, CrashUtils.Config config) throws Exception {
        doStagefrightTestMediaMetadataRetriever(-1, url, config);
    }

    private void doStagefrightTestMediaMetadataRetriever(
            final int rid, final String url) throws Exception {
        doStagefrightTestMediaMetadataRetriever(rid, url, null);
    }

    private void doStagefrightTestMediaMetadataRetriever(
            final int rid, final String url, CrashUtils.Config config) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                AssetFileDescriptor fd = null;
                try {
                    fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        Resources resources =  getInstrumentation().getContext().getResources();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        if (url == null) {
            AssetFileDescriptor fd = resources.openRawResourceFd(rid);
            try {
                retriever.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (Exception e) {
                // ignore
            } finally {
                closeQuietly(fd);
            }
        } else {
            try {
                retriever.setDataSource(url, new HashMap<String, String>());
            } catch (Exception e) {
                // indicative of problems with our tame CTS test web server
            }
        }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        retriever.getEmbeddedPicture();
        retriever.getFrameAtTime();

        retriever.release();
        String rname = url != null ? url : resources.getResourceEntryName(rid);
        assertNotEquals("MediaPlayer encountered a security crash when retrieving media metadata.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        thr.stopLooper();
        thr.join();
    }

    @Test
    @AsbSecurityTest(cveBugId = 36215950)
    public void testBug36215950() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36215950, "video/hevc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 36816007)
    public void testBug36816007() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36816007, "video/avc", 320, 240,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 36895511)
    public void testBug36895511() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_36895511, "video/hevc", 320, 240,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 64836894)
    public void testBug64836894() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_64836894, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 35583675)
    public void testCve_2017_0687() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2017_0687, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37207120)
    public void testCve_2017_0696() throws Exception {
        doStagefrightTestRawBlob(R.raw.cve_2017_0696, "video/avc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37930177)
    public void testBug_37930177() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_37930177_hevc, "video/hevc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 37712181)
    public void testBug_37712181() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_37712181_hevc, "video/hevc", 320, 240);
    }

    @Test
    @AsbSecurityTest(cveBugId = 70897394)
    public void testBug_70897394() throws Exception {
        doStagefrightTestRawBlob(R.raw.bug_70897394_avc, "video/avc", 320, 240,
                new CrashUtils.Config().checkMinAddress(false));
    }

    @Test
    @AsbSecurityTest(cveBugId = 123700383)
    public void testBug_123700383() throws Exception {
        assertExtractorDoesNotHang(R.raw.bug_123700383);
    }

    @Test
    @AsbSecurityTest(cveBugId = 127310810)
    public void testBug_127310810() throws Exception {
        assertExtractorDoesNotHang(R.raw.bug_127310810);
    }

    @Test
    @AsbSecurityTest(cveBugId = 127312550)
    public void testBug_127312550() throws Exception {
        assertExtractorDoesNotHang(R.raw.bug_127312550);
    }

    @Test
    @AsbSecurityTest(cveBugId = 127313223)
    public void testBug_127313223() throws Exception {
        assertExtractorDoesNotHang(R.raw.bug_127313223);
    }

    @Test
    @AsbSecurityTest(cveBugId = 127313537)
    public void testBug_127313537() throws Exception {
        assertExtractorDoesNotHang(R.raw.bug_127313537);
    }

    @Test
    @AsbSecurityTest(cveBugId = 127313764)
    public void testBug_127313764() throws Exception {
        assertExtractorDoesNotHang(R.raw.bug_127313764);
    }

    @Test
    @AsbSecurityTest(cveBugId = 189402477)
    public void testStagefright_cve_2021_0635() throws Exception {
        doStagefrightTest(R.raw.cve_2021_0635_1);
        doStagefrightTest(R.raw.cve_2021_0635_2);
    }

    private int[] getFrameSizes(int rid) throws IOException {
        final Context context = getInstrumentation().getContext();
        final Resources resources =  context.getResources();
        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        FileInputStream fis = fd.createInputStream();
        byte[] frameInfo = new byte[(int) fd.getLength()];
        fis.read(frameInfo);
        fis.close();
        String[] valueStr = new String(frameInfo).trim().split("\\s+");
        int[] frameSizes = new int[valueStr.length];
        for (int i = 0; i < valueStr.length; i++)
            frameSizes[i] = Integer.parseInt(valueStr[i]);
        return frameSizes;
    }

    private Object[] getFrameInfo(int rid) throws IOException {
        final Context context = getInstrumentation().getContext();
        final Resources resources = context.getResources();
        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        FileInputStream fis = fd.createInputStream();
        byte[] frameInfo = new byte[(int) fd.getLength()];
        fis.read(frameInfo);
        fis.close();
        String[] lines = new String(frameInfo).trim().split("\\r?\\n");
        int isHeader[] = new int[lines.length];
        int frameSizes[] = new int[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String[] values = lines[i].trim().split("\\s+");
            isHeader[i] = Integer.parseInt(values[0]);
            frameSizes[i] = Integer.parseInt(values[1]);
        }
        return new Object[] {isHeader, frameSizes};
    }

    private void runWithTimeout(Runnable runner, int timeout) {
        Thread t = new Thread(runner);
        t.start();
        try {
            t.join(timeout);
        } catch (InterruptedException e) {
            fail("operation was interrupted");
        }
        assumeThat("operation not completed within timeout of " + timeout + "ms", t.isAlive(),
                   is(false));
    }

    private void releaseCodec(final MediaCodec codec) {
        runWithTimeout(new Runnable() {
            @Override
            public void run() {
                codec.release();
            }
        }, 5000);
    }

    private boolean isCodecInMainlineModule(String codecName) {
        boolean value = false;
        if (codecName.startsWith("c2.android.")) {
            try {
                value = ModuleDetector.moduleIsPlayManaged(
                        getInstrumentation().getContext().getPackageManager(),
                        MainlineModule.MEDIA_SOFTWARE_CODEC);
            } catch (Exception e) {
                Log.e(TAG, "Exception caught " + e.toString());
            }
        }
        return value;
    }

    private void doStagefrightTestRawBlob(
            int rid, String mime, int initWidth, int initHeight) throws Exception {
        doStagefrightTestRawBlob(rid, mime, initWidth, initHeight, new CrashUtils.Config());
    }

    private void doStagefrightTestRawBlob(int rid, String mime, int initWidth, int initHeight,
            CrashUtils.Config config) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);
        final Context context = getInstrumentation().getContext();
        final Resources resources =  context.getResources();

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                AssetFileDescriptor fd = null;
                try {
                    fd = resources.openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        byte [] blob = new byte[(int)fd.getLength()];
        FileInputStream fis = fd.createInputStream();
        int numRead = fis.read(blob);
        fis.close();

        // find all the available decoders for this format
        ArrayList<String> matchingCodecs = new ArrayList<String>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                if (caps != null) {
                    /* Add mainline skip to decoders in mainline module */
                    if (isCodecInMainlineModule(info.getName())) {
                        Log.i(TAG, "Skipping codec " + info.getName() +
                                    " as it is part of mainline");
                        continue;
                    }
                    if (info.isAlias()) {
                        Log.i(TAG, "Skipping codec " + info.getName() + " as it is an alias");
                        continue;
                    }
                    matchingCodecs.add(info.getName());
                }
            } catch (IllegalArgumentException e) {
                // type is not supported
            }
        }

        if (matchingCodecs.size() == 0) {
            Log.w(TAG, "no codecs for mime type " + mime);
        }
        String rname = resources.getResourceEntryName(rid);
        // decode this blob once with each matching codec
        for (String codecName: matchingCodecs) {
            Log.i(TAG, "Decoding blob " + rname + " using codec " + codecName);
            MediaCodec codec = MediaCodec.createByCodecName(codecName);
            MediaFormat format = MediaFormat.createVideoFormat(mime, initWidth, initHeight);
            try {
                codec.configure(format, null, null, 0);
                codec.start();
            } catch (Exception e) {
                Log.i(TAG, "Exception from codec " + codecName);
                releaseCodec(codec);
                continue;
            }

            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                ByteBuffer [] inputBuffers = codec.getInputBuffers();
                // enqueue the bad data a number of times, in case
                // the codec needs multiple buffers to fail.
                for(int i = 0; i < 64; i++) {
                    int bufidx = codec.dequeueInputBuffer(5000);
                    if (bufidx >= 0) {
                        Log.i(TAG, "got input buffer of size " + inputBuffers[bufidx].capacity());
                        inputBuffers[bufidx].rewind();
                        inputBuffers[bufidx].put(blob, 0, numRead);
                        codec.queueInputBuffer(bufidx, 0, numRead, 0, 0);
                    } else {
                        Log.i(TAG, "no input buffer");
                    }
                    bufidx = codec.dequeueOutputBuffer(info, 5000);
                    if (bufidx >= 0) {
                        Log.i(TAG, "got output buffer");
                        codec.releaseOutputBuffer(bufidx, false);
                    } else {
                        Log.i(TAG, "no output buffer");
                    }
                }
            } catch (Exception e) {
                // ignore, not a security issue
            } finally {
                releaseCodec(codec);
            }
        }

        assertNotEquals("MediaPlayer encountered a security crash when testing raw blobs.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        thr.stopLooper();
        thr.join();
    }

    private void doStagefrightTestRawBlob(int rid, String mime, int initWidth, int initHeight,
            int frameSizes[]) throws Exception {
        // check crash address by default
        doStagefrightTestRawBlob(rid, mime, initWidth, initHeight, frameSizes, new CrashUtils.Config());
    }

    private void doStagefrightTestRawBlob(int rid, String mime, int initWidth, int initHeight,
            int frameSizes[], CrashUtils.Config config) throws Exception {
        CodecConfig codecConfig = new CodecConfig().setVideoParams(initWidth, initHeight);
        doStagefrightTestRawBlob(rid, mime, codecConfig, frameSizes, config);
    }

    private void doStagefrightTestRawBlob(int rid, String mime, CodecConfig codecConfig,
            int frameSizes[], CrashUtils.Config config) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);
        final Context context = getInstrumentation().getContext();
        final Resources resources =  context.getResources();

        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {

                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                AssetFileDescriptor fd = null;
                try {
                    fd = resources.openRawResourceFd(R.raw.good);

                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }

                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized(mpcl) {
            mpcl.wait();
        }

        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        byte [] blob = new byte[(int)fd.getLength()];
        FileInputStream fis = fd.createInputStream();
        int numRead = fis.read(blob);
        fis.close();

        // find all the available decoders for this format
        ArrayList<String> matchingCodecs = new ArrayList<String>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                if (caps != null) {
                    /* Add mainline skip to decoders in mainline module */
                    if (isCodecInMainlineModule(info.getName())) {
                        Log.i(TAG, "Skipping codec " + info.getName() +
                                    " as it is part of mainline");
                        continue;
                    }
                    if (info.isAlias()) {
                        Log.i(TAG, "Skipping codec " + info.getName() + " as it is an alias");
                        continue;
                    }
                    matchingCodecs.add(info.getName());
                }
            } catch (IllegalArgumentException e) {
                // type is not supported
            }
        }

        if (matchingCodecs.size() == 0) {
            Log.w(TAG, "no codecs for mime type " + mime);
        }
        String rname = resources.getResourceEntryName(rid);
        // decode this blob once with each matching codec
        for (String codecName: matchingCodecs) {
            Log.i(TAG, "Decoding blob " + rname + " using codec " + codecName);
            MediaCodec codec = MediaCodec.createByCodecName(codecName);
            MediaFormat format;
            if (codecConfig.isAudio) {
                format = MediaFormat.createAudioFormat(mime, codecConfig.sampleRate,
                        codecConfig.channelCount);
            } else {
                format = MediaFormat.createVideoFormat(mime, codecConfig.initWidth,
                        codecConfig.initHeight);
            }
            try {
                codec.configure(format, null, null, 0);
                codec.start();
            } catch (Exception e) {
                Log.i(TAG, "Exception from codec " + codecName);
                releaseCodec(codec);
                continue;
            }

            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                ByteBuffer [] inputBuffers = codec.getInputBuffers();
                int numFrames = 0;
                if (frameSizes != null) {
                    numFrames = frameSizes.length;
                }

                if (0 == numFrames) {
                    fail("Improper picture length file");
                }

                int offset = 0;
                int bytesToFeed = 0;
                byte [] tempBlob = new byte[(int)inputBuffers[0].capacity()];
                for (int j = 0; j < numFrames; j++) {
                    int flags = 0;
                    int bufidx = codec.dequeueInputBuffer(5000);
                    if (bufidx >= 0) {
                        inputBuffers[bufidx].rewind();
                        if(j == (numFrames - 1)) {
                            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        }
                        if (codecConfig.isAudio) {
                            if (j == 0) {
                                flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                            }
                            inputBuffers[bufidx].put(blob, offset, frameSizes[j]);
                            bytesToFeed = frameSizes[j];
                        } else {
                            bytesToFeed = Math.min((int) (fd.getLength() - offset),
                                    inputBuffers[bufidx].capacity());
                            System.arraycopy(blob, offset, tempBlob, 0, bytesToFeed);
                            inputBuffers[bufidx].put(tempBlob, 0, inputBuffers[bufidx].capacity());
                        }
                        codec.queueInputBuffer(bufidx, 0, bytesToFeed, 0, flags);
                        offset = offset + frameSizes[j];
                    } else {
                        Log.i(TAG, "no input buffer");
                    }
                    bufidx = codec.dequeueOutputBuffer(info, 5000);
                    if (bufidx >= 0) {
                        codec.releaseOutputBuffer(bufidx, false);
                    } else {
                      Log.i(TAG, "no output buffer");
                    }
                }
            } catch (Exception e) {
                // ignore, not a security issue
            } finally {
                releaseCodec(codec);
            }
        }

        assertNotEquals(
                "MediaPlayer encountered a security crash when testing raw blobs with frame sizes.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        thr.stopLooper();
        thr.join();
    }

    private void doStagefrightTestRawBlob(int rid, String mime, int initWidth, int initHeight,
            int frameSizes[], int isHeader[], CrashUtils.Config config) throws Exception {

        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);
        final Context context = getInstrumentation().getContext();
        final Resources resources = context.getResources();
        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                AssetFileDescriptor fd = null;
                try {
                    fd = resources.openRawResourceFd(R.raw.good);
                    // the onErrorListener won't receive MEDIA_ERROR_SERVER_DIED until
                    // setDataSource has been called
                    mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    // this is a known-good file, so no failure should occur
                    fail("setDataSource of known-good file failed");
                }
                synchronized (mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        // wait until the thread has initialized the MediaPlayer
        synchronized (mpcl) {
            mpcl.wait();
        }

        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        byte[] blob = new byte[(int) fd.getLength()];
        FileInputStream fis = fd.createInputStream();
        int numRead = fis.read(blob);
        fis.close();

        // find all the available decoders for this format
        ArrayList<String> matchingCodecs = new ArrayList<String>();
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
                if (caps != null) {
                    /* Add mainline skip to decoders in mainline module */
                    if (isCodecInMainlineModule(info.getName())) {
                        Log.i(TAG, "Skipping codec " + info.getName() +
                                    " as it is part of mainline");
                        continue;
                    }
                    if (info.isAlias()) {
                        Log.i(TAG, "Skipping codec " + info.getName() + " as it is an alias");
                        continue;
                    }
                    matchingCodecs.add(info.getName());
                }
            } catch (IllegalArgumentException e) {
                // type is not supported
            }
        }

        if (matchingCodecs.size() == 0) {
            Log.w(TAG, "no codecs for mime type " + mime);
        }
        String rname = resources.getResourceEntryName(rid);
        // decode this blob once with each matching codec
        for (String codecName : matchingCodecs) {
            Log.i(TAG, "Decoding blob " + rname + " using codec " + codecName);
            MediaCodec codec = MediaCodec.createByCodecName(codecName);
            MediaFormat format = MediaFormat.createVideoFormat(mime, initWidth, initHeight);
            try {
                codec.configure(format, null, null, 0);
                codec.start();
            } catch (Exception e) {
                Log.i(TAG, "Exception from codec " + codecName);
                releaseCodec(codec);
                continue;
            }
            try {
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                ByteBuffer[] inputBuffers = codec.getInputBuffers();
                int numFrames = 0;
                if (frameSizes != null) {
                    numFrames = frameSizes.length;
                }
                if (0 == numFrames) {
                    fail("Improper picture length file");
                }
                int offset = 0;
                int j = 0;
                while (j < numFrames) {
                    int flags = 0;
                    int bufidx = codec.dequeueInputBuffer(5000);
                    if (bufidx >= 0) {
                        inputBuffers[bufidx].rewind();
                        Log.i(TAG, "Got buffer index " + bufidx + " with length "
                                + inputBuffers[bufidx].capacity());
                        if (isHeader[j] == 1) {
                            flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                        }
                        if (j == (numFrames - 1)) {
                            flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        }
                        Log.i(TAG, "Feeding frame " + j + " with framelen " + frameSizes[j]
                                + " offset " + offset + " and flags " + flags);
                        inputBuffers[bufidx].put(blob, offset, frameSizes[j]);
                        codec.queueInputBuffer(bufidx, 0, frameSizes[j], 0, flags);
                        offset = offset + frameSizes[j];
                        j++;
                    } else {
                        Log.i(TAG, "no input buffer");
                    }
                    bufidx = codec.dequeueOutputBuffer(info, 5000);
                    if (bufidx >= 0) {
                        codec.releaseOutputBuffer(bufidx, false);
                    } else {
                        Log.i(TAG, "no output buffer");
                    }
                }
            } catch (Exception e) {
                // ignore, not a security issue
            } finally {
                releaseCodec(codec);
            }
        }
        String cve = rname.replace("_", "-").toUpperCase();
        assertFalse("Device *IS* vulnerable to " + cve,
                mpcl.waitForError() == MediaPlayer.MEDIA_ERROR_SERVER_DIED);
        thr.stopLooper();
        thr.join();
    }

    private void doStagefrightTestMediaPlayerANR(final int rid, final String uri) throws Exception {
        doStagefrightTestMediaPlayerANR(rid, uri, null);
    }

    private void doStagefrightTestMediaPlayerANR(final int rid, final String uri,
            CrashUtils.Config config) throws Exception {
        String name = uri != null ? uri :
            getInstrumentation().getContext().getResources().getResourceEntryName(rid);
        Log.i(TAG, "start mediaplayerANR test for: " + name);

        final MediaPlayerCrashListener mpl = new MediaPlayerCrashListener(config);

        LooperThread t = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpl);
                mp.setOnPreparedListener(mpl);
                mp.setOnCompletionListener(mpl);
                RenderTarget renderTarget = RenderTarget.create();
                Surface surface = renderTarget.getSurface();
                mp.setSurface(surface);
                AssetFileDescriptor fd = null;
                try {
                    if (uri == null) {
                        fd = getInstrumentation().getContext().getResources()
                                .openRawResourceFd(rid);

                        mp.setDataSource(fd.getFileDescriptor(),
                                fd.getStartOffset(),
                                fd.getLength());
                    } else {
                        mp.setDataSource(uri);
                    }
                    mp.prepareAsync();
                } catch (Exception e) {
                } finally {
                    closeQuietly(fd);
                }

                Looper.loop();
                mp.release();
                renderTarget.destroy();
            }
        });

        t.start();
        assertTrue("MediaPlayer failed to complete when testing ANR.",
                mpl.waitForErrorOrCompletion());
        t.stopLooper();
        t.join(); // wait for thread to exit so we're sure the player was released
    }

    private void doStagefrightTestExtractorSeek(final int rid, final long offset) throws Exception {
        doStagefrightTestExtractorSeek(rid, offset, new CrashUtils.Config()); // check crash address by default
    }

    private void doStagefrightTestExtractorSeek(final int rid, final long offset,
            CrashUtils.Config config) throws Exception {
        final MediaPlayerCrashListener mpcl = new MediaPlayerCrashListener(config);
        LooperThread thr = new LooperThread(new Runnable() {
            @Override
            public void run() {
                MediaPlayer mp = new MediaPlayer();
                mp.setOnErrorListener(mpcl);
                try {
                    AssetFileDescriptor fd = getInstrumentation().getContext().getResources()
                        .openRawResourceFd(R.raw.good);
                    mp.setDataSource(fd.getFileDescriptor(),
                                     fd.getStartOffset(),
                                     fd.getLength());
                    fd.close();
                } catch (Exception e) {
                    fail("setDataSource of known-good file failed");
                }
                synchronized(mpcl) {
                    mpcl.notify();
                }
                Looper.loop();
                mp.release();
            }
        });
        thr.start();
        synchronized(mpcl) {
            mpcl.wait();
        }
        Resources resources =  getInstrumentation().getContext().getResources();
        MediaExtractor ex = new MediaExtractor();
        AssetFileDescriptor fd = resources.openRawResourceFd(rid);
        try {
            ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        } catch (IOException e) {
        } finally {
            closeQuietly(fd);
        }
        int numtracks = ex.getTrackCount();
        String rname = resources.getResourceEntryName(rid);
        Log.i(TAG, "start mediaextractor test for: " + rname + ", which has " + numtracks + " tracks");
        for (int t = 0; t < numtracks; t++) {
            try {
                ex.selectTrack(t);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "couldn't select track " + t);
            }
            ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            ex.advance();
            ex.seekTo(offset, MediaExtractor.SEEK_TO_NEXT_SYNC);
            try
            {
                ex.unselectTrack(t);
            }
            catch (Exception e) {
            }
        }
        ex.release();
        assertNotEquals("MediaPlayer encountered a security crash when testing extractor seeking.",
                MediaPlayer.MEDIA_ERROR_SERVER_DIED, mpcl.waitForError());
        thr.stopLooper();
        thr.join();
    }

    protected void assertExtractorDoesNotHang(int rid) throws Exception {
        // The media extractor has a watchdog, currently set to 10 seconds.
        final long timeoutMs = 12 * 1000;

        Thread thread = new Thread(() -> {
            MediaExtractor ex = new MediaExtractor();
            AssetFileDescriptor fd =
                    getInstrumentation().getContext().getResources().openRawResourceFd(rid);
            try {
                ex.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            } catch (IOException e) {
                // It is OK for the call to fail, we're only making sure it doesn't hang.
            } finally {
                closeQuietly(fd);
                ex.release();
            }
        });
        thread.start();

        thread.join(timeoutMs);
        boolean hung = thread.isAlive();
        if (hung) {
            // We don't have much to do at this point. Attempt to un-hang the thread, the media
            // extractor process is likely still spinning. At least we found a bug...
            // TODO: reboot the media extractor process.
            thread.interrupt();
        }

        assertFalse(hung);
    }
}
