/*
 * Copyright 2018 The Android Open Source Project
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
package android.media.cts;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.CallbackDataSourceDesc;
import android.media.DataSourceCallback;
import android.media.DataSourceDesc;
import android.media.FileDataSourceDesc;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer2;
import android.media.MediaPlayer2.SubtitleData;
import android.media.MediaPlayer2.TrackInfo;
import android.media.MediaRecorder;
import android.media.MediaTimestamp;
import android.media.PlaybackParams;
import android.media.SyncParams;
import android.media.UriDataSourceDesc;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.media.cts.R;
import android.media.cts.TestUtils.Monitor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.webkit.cts.CtsTestServer;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.MediaUtils;

import org.apache.http.Header;
import org.apache.http.HttpRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests for the MediaPlayer2 API and local video/audio playback.
 *
 * The files in res/raw used by testLocalVideo* are (c) copyright 2008,
 * Blender Foundation / www.bigbuckbunny.org, and are licensed under the Creative Commons
 * Attribution 3.0 License at http://creativecommons.org/licenses/by/3.0/us/.
 */
@SmallTest
@RequiresDevice
@AppModeFull(reason = "TODO: evaluate and port to instant")
public class MediaPlayer2Test extends MediaPlayer2TestBase {
    private String RECORDED_FILE;
    private static final String LOG_TAG = "MediaPlayer2Test";

    private static final int  RECORDED_VIDEO_WIDTH  = 176;
    private static final int  RECORDED_VIDEO_HEIGHT = 144;
    private static final long RECORDED_DURATION_MS  = 3000;
    private static final float FLOAT_TOLERANCE = .0001f;

    private final Vector<Integer> mTimedTextTrackIndex = new Vector<>();
    private final Monitor mOnTimedTextCalled = new Monitor();
    private int mSelectedTimedTextIndex;

    private final Vector<Integer> mSubtitleTrackIndex = new Vector<>();
    private final Monitor mOnSubtitleDataCalled = new Monitor();
    private int mSelectedSubtitleIndex;

    private File mOutFile;

    private int mBoundsCount;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RECORDED_FILE = new File(Environment.getExternalStorageDirectory(),
                "mediaplayer_record.out").getAbsolutePath();
        mOutFile = new File(RECORDED_FILE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mOutFile != null && mOutFile.exists()) {
            mOutFile.delete();
        }
    }

    // Bug 13652927
    public void testVorbisCrash() throws Exception {
        MediaPlayer2 mp = mPlayer;
        MediaPlayer2 mp2 = mPlayer2;
        AssetFileDescriptor afd2 = mResources.openRawResourceFd(R.raw.testmp3_2);
        mp2.setDataSource(new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd2.getFileDescriptor()),
                    afd2.getStartOffset(), afd2.getLength())
                .build());
        afd2.close();
        Monitor onPrepareCalled = new Monitor();
        Monitor onErrorCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    onPrepareCalled.signal();
                }
            }

            @Override
            public void onError(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                onErrorCalled.signal();
            }
        };
        mp2.registerEventCallback(mExecutor, ecb);
        mp2.prepare();
        onPrepareCalled.waitForSignal();
        mp2.unregisterEventCallback(ecb);

        mp2.loopCurrent(true);
        mp2.play();

        for (int i = 0; i < 20; i++) {
            try {
                AssetFileDescriptor afd = mResources.openRawResourceFd(R.raw.bug13652927);
                mp.setDataSource(new FileDataSourceDesc.Builder()
                        .setDataSource(ParcelFileDescriptor.dup(afd.getFileDescriptor()),
                            afd.getStartOffset(), afd.getLength())
                        .build());
                afd.close();
                mp.registerEventCallback(mExecutor, ecb);
                onPrepareCalled.reset();
                mp.prepare();
                onErrorCalled.waitForSignal();
            } catch (Exception e) {
                // expected to fail
                Log.i("@@@", "failed: " + e);
            }
            Thread.sleep(500);
            assertTrue("media player died", mp2.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            mp.reset();
        }
    }

    public void testPlayNullSourcePath() throws Exception {
        Monitor onSetDataSourceCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE) {
                    assertTrue(status != MediaPlayer2.CALL_STATUS_NO_ERROR);
                    onSetDataSourceCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        onSetDataSourceCalled.reset();
        mPlayer.setDataSource((DataSourceDesc) null);
        onSetDataSourceCalled.waitForSignal();
    }

    public void testPlayAudioFromDataURI() throws Exception {
        final int mp3Duration = 34909;
        final int tolerance = 70;
        final int seekDuration = 100;

        // This is "R.raw.testmp3_2", base64-encoded.
        final int resid = R.raw.testmp3_3;

        InputStream is = mContext.getResources().openRawResource(resid);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        StringBuilder builder = new StringBuilder();
        builder.append("data:;base64,");
        builder.append(reader.readLine());
        Uri uri = Uri.parse(builder.toString());

        MediaPlayer2 mp = createMediaPlayer2(mContext, uri);

        Monitor onPrepareCalled = new Monitor();
        Monitor onPlayCalled = new Monitor();
        Monitor onSeekToCalled = new Monitor();
        Monitor onLoopCurrentCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    onPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    onPlayCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_LOOP_CURRENT) {
                    onLoopCurrentCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    onSeekToCalled.signal();
                }
            }
        };
        mp.registerEventCallback(mExecutor, ecb);

        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();
            mp.setAudioAttributes(attributes);
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                            "MediaPlayer2Test");
            mp.setWakeLock(wakeLock);

            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            assertFalse(mp.isLooping());
            onLoopCurrentCalled.reset();
            mp.loopCurrent(true);
            onLoopCurrentCalled.waitForSignal();
            assertTrue(mp.isLooping());

            assertEquals(mp3Duration, mp.getDuration(mp.getCurrentDataSource()), tolerance);
            long pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < mp3Duration - seekDuration);

            onSeekToCalled.reset();
            mp.seekTo(pos + seekDuration, MediaPlayer2.SEEK_PREVIOUS_SYNC);
            onSeekToCalled.waitForSignal();
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test pause and restart
            mp.pause();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            // test stop and restart
            mp.reset();
            mp.registerEventCallback(mExecutor, ecb);
            mp.setDataSource(new UriDataSourceDesc.Builder()
                    .setDataSource(mContext, uri)
                    .build());
            onPrepareCalled.reset();
            mp.prepare();
            onPrepareCalled.waitForSignal();

            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            // waiting to complete
            while(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mp.close();
        }
    }

    public void testPlayAudio() throws Exception {
        final int resid = R.raw.testmp3_2;
        final int mp3Duration = 34909;
        final int tolerance = 70;
        final int seekDuration = 100;

        MediaPlayer2 mp = createMediaPlayer2(mContext, resid);

        float maxVol = mp.getMaxPlayerVolume();
        assertTrue(maxVol > 0.0);

        Monitor onPrepareCalled = new Monitor();
        Monitor onPlayCalled = new Monitor();
        Monitor onSeekToCalled = new Monitor();
        Monitor onLoopCurrentCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    onPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    onPlayCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_LOOP_CURRENT) {
                    onLoopCurrentCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    onSeekToCalled.signal();
                }
            }
        };
        mp.registerEventCallback(mExecutor, ecb);

        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();
            mp.setAudioAttributes(attributes);
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                            "MediaPlayer2Test");
            mp.setWakeLock(wakeLock);

            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            assertFalse(mp.isLooping());
            onLoopCurrentCalled.reset();
            mp.loopCurrent(true);
            onLoopCurrentCalled.waitForSignal();
            assertTrue(mp.isLooping());

            assertEquals(mp3Duration, mp.getDuration(mp.getCurrentDataSource()), tolerance);
            long pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < mp3Duration - seekDuration);

            onSeekToCalled.reset();
            mp.seekTo(pos + seekDuration, MediaPlayer2.SEEK_PREVIOUS_SYNC);
            onSeekToCalled.waitForSignal();
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test pause and restart
            mp.pause();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            // test stop and restart
            mp.reset();
            AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
            mp.setDataSource(new FileDataSourceDesc.Builder()
                    .setDataSource(ParcelFileDescriptor.dup(afd.getFileDescriptor()),
                        afd.getStartOffset(), afd.getLength())
                    .build());
            afd.close();

            mp.registerEventCallback(mExecutor, ecb);
            onPrepareCalled.reset();
            mp.prepare();
            onPrepareCalled.waitForSignal();

            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            // waiting to complete
            while(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mp.close();
        }
    }

    public void testConcurentPlayAudio() throws Exception {
        final int resid = R.raw.test1m1s; // MP3 longer than 1m are usualy offloaded
        final int tolerance = 70;

        List<MediaPlayer2> mps = Stream.generate(() -> createMediaPlayer2(mContext, resid))
                                      .limit(5).collect(Collectors.toList());

        try {
            for (MediaPlayer2 mp : mps) {
                Monitor onPlayCalled = new Monitor();
                Monitor onLoopCurrentCalled = new Monitor();
                MediaPlayer2.EventCallback ecb =
                    new MediaPlayer2.EventCallback() {
                        @Override
                        public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd,
                                int what, int status) {
                            if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                                onPlayCalled.signal();
                            } else if (what == MediaPlayer2.CALL_COMPLETED_LOOP_CURRENT) {
                                onLoopCurrentCalled.signal();
                            }
                        }
                    };
                mp.registerEventCallback(mExecutor, ecb);

                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build();
                mp.setAudioAttributes(attributes);
                PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock =
                        pm.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                                "MediaPlayer2Test");
                mp.setWakeLock(wakeLock);

                assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
                onPlayCalled.reset();
                mp.play();
                onPlayCalled.waitForSignal();
                assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

                assertFalse(mp.isLooping());
                onLoopCurrentCalled.reset();
                mp.loopCurrent(true);
                onLoopCurrentCalled.waitForSignal();
                assertTrue(mp.isLooping());

                long pos = mp.getCurrentPosition();
                assertTrue(pos >= 0);

                Thread.sleep(SLEEP_TIME); // Delay each track to be able to ear them
            }
            // Check that all mp3 are playing concurrently here
            for (MediaPlayer2 mp : mps) {
                long pos = mp.getCurrentPosition();
                Thread.sleep(SLEEP_TIME);
                assertEquals(pos + SLEEP_TIME, mp.getCurrentPosition(), tolerance);
            }
        } finally {
            mps.forEach(MediaPlayer2::close);
        }
    }

    public void testPlayAudioLooping() throws Exception {
        final int resid = R.raw.testmp3;

        MediaPlayer2 mp = createMediaPlayer2(mContext, resid);
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();
            mp.setAudioAttributes(attributes);
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                            "MediaPlayer2Test");
            mp.setWakeLock(wakeLock);

            mp.loopCurrent(true);
            Monitor onCompletionCalled = new Monitor();
            Monitor onRepeatCalled = new Monitor();
            Monitor onPlayCalled = new Monitor();
            MediaPlayer2.EventCallback ecb =
                new MediaPlayer2.EventCallback() {
                    @Override
                    public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd,
                            int what, int extra) {
                        if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_REPEAT) {
                            onRepeatCalled.signal();
                        } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                            Log.i("@@@", "got oncompletion");
                            onCompletionCalled.signal();
                        }
                    }

                    @Override
                    public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd,
                            int what, int status) {
                        if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                            onPlayCalled.signal();
                        }
                    }
                };
            mp.registerEventCallback(mExecutor, ecb);

            assertFalse(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            onPlayCalled.reset();
            mp.play();
            onPlayCalled.waitForSignal();
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            long duration = mp.getDuration();
            Thread.sleep(duration * 4); // allow for several loops
            assertTrue(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            assertEquals("wrong number of completion signals", 0,
                    onCompletionCalled.getNumSignal());
            assertTrue(onRepeatCalled.getNumSignal() > 0);
            mp.loopCurrent(false);

            // wait for playback to finish
            while(mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING) {
                Thread.sleep(SLEEP_TIME);
            }
            assertEquals("wrong number of completion signals", 1,
                    onCompletionCalled.getNumSignal());
        } finally {
            mp.close();
        }
    }

    public void testPlayMidi() throws Exception {
        final int resid = R.raw.midi8sec;
        final int midiDuration = 8000;
        final int tolerance = 70;
        final int seekDuration = 1000;

        MediaPlayer2 mp = createMediaPlayer2(mContext, resid);

        Monitor onPrepareCalled = new Monitor();
        Monitor onSeekToCalled = new Monitor();
        Monitor onLoopCurrentCalled = new Monitor();
        MediaPlayer2.EventCallback ecb =
            new MediaPlayer2.EventCallback() {
                @Override
                public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                    if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                        onPrepareCalled.signal();
                    }
                }

                @Override
                public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd,
                        int what, int status) {
                    if (what == MediaPlayer2.CALL_COMPLETED_LOOP_CURRENT) {
                        onLoopCurrentCalled.signal();
                    } else if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                        onSeekToCalled.signal();
                    }
                }
            };
        mp.registerEventCallback(mExecutor, ecb);

        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();
            mp.setAudioAttributes(attributes);
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                            "MediaPlayer2Test");
            mp.setWakeLock(wakeLock);

            mp.play();

            assertFalse(mp.isLooping());
            onLoopCurrentCalled.reset();
            mp.loopCurrent(true);
            onLoopCurrentCalled.waitForSignal();
            assertTrue(mp.isLooping());

            assertEquals(midiDuration, mp.getDuration(mp.getCurrentDataSource()), tolerance);
            long pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < midiDuration - seekDuration);

            onSeekToCalled.reset();
            mp.seekTo(pos + seekDuration, MediaPlayer2.SEEK_PREVIOUS_SYNC);
            onSeekToCalled.waitForSignal();
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test stop and restart
            mp.reset();
            AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
            mp.setDataSource(new FileDataSourceDesc.Builder()
                    .setDataSource(ParcelFileDescriptor.dup(afd.getFileDescriptor()),
                        afd.getStartOffset(), afd.getLength())
                    .build());
            afd.close();

            mp.registerEventCallback(mExecutor, ecb);
            onPrepareCalled.reset();
            mp.prepare();
            onPrepareCalled.waitForSignal();

            mp.play();

            Thread.sleep(SLEEP_TIME);
        } finally {
            mp.close();
        }
    }

    static class OutputListener {
        int mSession;
        AudioEffect mVc;
        Visualizer mVis;
        byte [] mVisData;
        boolean mSoundDetected;
        OutputListener(int session) {
            mSession = session;
            // creating a volume controller on output mix ensures that ro.audio.silent mutes
            // audio after the effects and not before
            mVc = new AudioEffect(
                    AudioEffect.EFFECT_TYPE_NULL,
                    UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b"),
                    0,
                    session);
            mVc.setEnabled(true);
            mVis = new Visualizer(session);
            int size = 256;
            int[] range = Visualizer.getCaptureSizeRange();
            if (size < range[0]) {
                size = range[0];
            }
            if (size > range[1]) {
                size = range[1];
            }
            assertTrue(mVis.setCaptureSize(size) == Visualizer.SUCCESS);

            mVis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer visualizer,
                        byte[] waveform, int samplingRate) {
                    if (!mSoundDetected) {
                        for (int i = 0; i < waveform.length; i++) {
                            // 8 bit unsigned PCM, zero level is at 128, which is -128 when
                            // seen as a signed byte
                            if (waveform[i] != -128) {
                                mSoundDetected = true;
                                break;
                            }
                        }
                    }
                }

                @Override
                public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
                }
            }, 10000 /* milliHertz */, true /* PCM */, false /* FFT */);
            assertTrue(mVis.setEnabled(true) == Visualizer.SUCCESS);
        }

        void reset() {
            mSoundDetected = false;
        }

        boolean heardSound() {
            return mSoundDetected;
        }

        void release() {
            mVis.release();
            mVc.release();
        }
    }

    public void testPlayAudioTwice() throws Exception {
        final int resid = R.raw.camera_click;

        MediaPlayer2 mp = createMediaPlayer2(mContext, resid);
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build();
            mp.setAudioAttributes(attributes);
            PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock =
                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                            "MediaPlayer2Test");
            mp.setWakeLock(wakeLock);

            OutputListener listener = new OutputListener(mp.getAudioSessionId());

            Thread.sleep(SLEEP_TIME);
            assertFalse("noise heard before test started", listener.heardSound());

            mp.play();
            Thread.sleep(SLEEP_TIME);
            assertFalse("player was still playing after " + SLEEP_TIME + " ms",
                    mp.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
            assertTrue("nothing heard while test ran", listener.heardSound());
            listener.reset();
            mp.seekTo(0, MediaPlayer2.SEEK_PREVIOUS_SYNC);
            mp.play();
            Thread.sleep(SLEEP_TIME);
            assertTrue("nothing heard when sound was replayed", listener.heardSound());
            listener.release();
        } finally {
            mp.close();
        }
    }

    public void testPlayVideo() throws Exception {
        playVideoTest(R.raw.testvideo, 352, 288);
    }

    public void testPlayVideoWithCookies() throws Exception {
        String cookieName = "foo";
        String cookieValue = "bar";
        HttpCookie cookie = new HttpCookie(cookieName, cookieValue);
        cookie.setDomain(".local");

        CtsTestServer foo = new CtsTestServer(mContext);
        String video = "video_decode_accuracy_and_capability-h264_270x480_30fps.mp4";
        Uri uri = Uri.parse(foo.getAssetUrl(video));
        List<HttpCookie> cookies = Collections.singletonList(cookie);
        Map<String, String> headers = Collections.<String, String>emptyMap();
        playVideoWithRetries(uri, headers, cookies, 270, 480, 0);

        HttpRequest req = foo.getLastRequest(video);
        for (Header h : req.getAllHeaders()) {
            String v = h.getValue();
            String regex = ".*" + cookie.getName() + ".*" + cookie.getValue() + ".*";
            Log.v(LOG_TAG, String.format("testCookies: header %s regex %s", h, regex));
            if (v.matches(regex)) {
                return;
            }
        }

        fail("missing cookie: " + cookie);
    }

    /**
     * Test for reseting a surface during video playback
     * After reseting, the video should continue playing
     * from the time setDisplay() was called
     */
    public void testVideoSurfaceResetting() throws Exception {
        final int tolerance = 150;
        final int audioLatencyTolerance = 1000;  /* covers audio path latency variability */
        final int seekPos = 4760;  // This is the I-frame position

        final CountDownLatch seekDone = new CountDownLatch(1);

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    seekDone.countDown();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        if (!checkLoadResource(R.raw.testvideo)) {
            return; // skip;
        }
        playLoadedVideo(352, 288, -1);

        Thread.sleep(SLEEP_TIME);

        long posBefore = mPlayer.getCurrentPosition();
        mPlayer.setDisplay(getActivity().getSurfaceHolder2());
        long posAfter = mPlayer.getCurrentPosition();

        /* temporarily disable timestamp checking because MediaPlayer2 now seeks to I-frame
         * position, instead of requested position. setDisplay invovles a seek operation
         * internally.
         */
        // TODO: uncomment out line below when MediaPlayer2 can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        Thread.sleep(SLEEP_TIME);

        mPlayer.seekTo(seekPos, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        seekDone.await();
        posAfter = mPlayer.getCurrentPosition();
        assertEquals(seekPos, posAfter, tolerance + audioLatencyTolerance);

        Thread.sleep(SLEEP_TIME / 2);
        posBefore = mPlayer.getCurrentPosition();
        mPlayer.setDisplay(null);
        posAfter = mPlayer.getCurrentPosition();
        // TODO: uncomment out line below when MediaPlayer2 can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        Thread.sleep(SLEEP_TIME);

        posBefore = mPlayer.getCurrentPosition();
        mPlayer.setDisplay(getActivity().getSurfaceHolder());
        posAfter = mPlayer.getCurrentPosition();

        // TODO: uncomment out line below when MediaPlayer2 can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        Thread.sleep(SLEEP_TIME);
    }

    public void testRecordedVideoPlayback0() throws Exception {
        testRecordedVideoPlaybackWithAngle(0);
    }

    public void testRecordedVideoPlayback90() throws Exception {
        testRecordedVideoPlaybackWithAngle(90);
    }

    public void testRecordedVideoPlayback180() throws Exception {
        testRecordedVideoPlaybackWithAngle(180);
    }

    public void testRecordedVideoPlayback270() throws Exception {
        testRecordedVideoPlaybackWithAngle(270);
    }

    private boolean hasCamera() {
        return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private Camera mCamera;
    private void testRecordedVideoPlaybackWithAngle(int angle) throws Exception {
        int width = RECORDED_VIDEO_WIDTH;
        int height = RECORDED_VIDEO_HEIGHT;
        final String file = RECORDED_FILE;
        final long durationMs = RECORDED_DURATION_MS;

        if (!hasCamera()) {
            return;
        }

        boolean isSupported = false;
        mCamera = Camera.open(0);
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> videoSizes = parameters.getSupportedVideoSizes();
        // getSupportedVideoSizes returns null when separate video/preview size
        // is not supported.
        if (videoSizes == null) {
            videoSizes = parameters.getSupportedPreviewSizes();
        }
        for (Camera.Size size : videoSizes)
        {
            if (size.width == width && size.height == height) {
                isSupported = true;
                break;
            }
        }
        mCamera.release();
        mCamera = null;
        if (!isSupported) {
            width = videoSizes.get(0).width;
            height = videoSizes.get(0).height;
        }
        checkOrientation(angle);
        recordVideo(width, height, angle, file, durationMs);
        checkDisplayedVideoSize(width, height, angle, file);
        checkVideoRotationAngle(angle, file);
    }

    private void checkOrientation(int angle) throws Exception {
        assertTrue(angle >= 0);
        assertTrue(angle < 360);
        assertTrue((angle % 90) == 0);
    }

    private void recordVideo(
            int w, int h, int angle, String file, long durationMs) throws Exception {

        MediaRecorder recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(file);
        recorder.setOrientationHint(angle);
        recorder.setVideoSize(w, h);
        recorder.setPreviewDisplay(getActivity().getSurfaceHolder2().getSurface());
        recorder.prepare();
        recorder.start();
        Thread.sleep(durationMs);
        recorder.stop();
        recorder.release();
        recorder = null;
    }

    private void checkDisplayedVideoSize(
            int w, int h, int angle, String file) throws Exception {

        int displayWidth  = w;
        int displayHeight = h;
        if ((angle % 180) != 0) {
            displayWidth  = h;
            displayHeight = w;
        }
        playVideoTest(file, displayWidth, displayHeight);
    }

    private void checkVideoRotationAngle(int angle, String file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file);
        String rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        retriever.release();
        retriever = null;
        assertNotNull(rotation);
        assertEquals(Integer.parseInt(rotation), angle);
    }

    public void testPlaylist() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        int resid = R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;  // skip
        }

        AssetFileDescriptor afd2 = mResources.openRawResourceFd(resid);
        DataSourceDesc dsd2 = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd2.getFileDescriptor()),
                        afd2.getStartOffset(), afd2.getLength())
                .build();
        afd2.close();

        resid = R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz;
        AssetFileDescriptor afd3 = mResources.openRawResourceFd(resid);
        DataSourceDesc dsd3 = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd3.getFileDescriptor()),
                        afd3.getStartOffset(), afd3.getLength())
                .build();
        afd3.close();

        ArrayList<DataSourceDesc> nextDSDs = new ArrayList<DataSourceDesc>(2);
        nextDSDs.add(dsd2);
        nextDSDs.add(dsd3);

        mPlayer.setNextDataSources(nextDSDs);

        Monitor onStartCalled = new Monitor();
        Monitor onStart2Called = new Monitor();
        Monitor onStart3Called = new Monitor();
        Monitor onCompletion2Called = new Monitor();
        Monitor onCompletion3Called = new Monitor();
        Monitor onListCompletionCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    Log.i(LOG_TAG, "testPlaylist: prepared dsd MediaId=" + dsd.getMediaId());
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_START) {
                    if (dsd == dsd2) {
                        Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_START dsd2");
                        onStart2Called.signal();
                    } else if (dsd == dsd3) {
                        Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_START dsd3");
                        onStart3Called.signal();
                    } else {
                        Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_START other");
                        onStartCalled.signal();
                    }
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    if (dsd == dsd2) {
                        Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_END dsd2");
                        onCompletion2Called.signal();
                    } else if (dsd == dsd3) {
                        Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_END dsd3");
                        onCompletion3Called.signal();
                    } else {
                        Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_END other");
                        mOnCompletionCalled.signal();
                    }
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_LIST_END) {
                    Log.i(LOG_TAG, "testPlaylist: MEDIA_INFO_DATA_SOURCE_LIST_END");
                    onListCompletionCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnCompletionCalled.reset();
        onCompletion2Called.reset();
        onCompletion3Called.reset();

        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mPlayer.prepare();

        mPlayer.play();

        onStartCalled.waitForSignal();
        onStart2Called.waitForSignal();
        onStart3Called.waitForSignal();
        mOnCompletionCalled.waitForSignal();
        onCompletion2Called.waitForSignal();
        onCompletion3Called.waitForSignal();
        onListCompletionCalled.waitForSignal();

        mPlayer.reset();
    }

    public void testSkipToNext() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        int resid = R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;  // skip
        }

        AssetFileDescriptor afd2 = mResources.openRawResourceFd(resid);
        DataSourceDesc dsd2 = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd2.getFileDescriptor()),
                        afd2.getStartOffset(), afd2.getLength())
                .build();
        afd2.close();

        resid = R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz;
        AssetFileDescriptor afd3 = mResources.openRawResourceFd(resid);
        DataSourceDesc dsd3 = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd3.getFileDescriptor()),
                        afd3.getStartOffset(), afd3.getLength())
                .build();
        afd3.close();

        ArrayList<DataSourceDesc> nextDSDs = new ArrayList<DataSourceDesc>(2);
        nextDSDs.add(dsd2);
        nextDSDs.add(dsd3);

        mPlayer.setNextDataSources(nextDSDs);

        Monitor onStartCalled = new Monitor();
        Monitor onStart2Called = new Monitor();
        Monitor onStart3Called = new Monitor();
        Monitor onCompletion2Called = new Monitor();
        Monitor onCompletion3Called = new Monitor();
        Monitor onListCompletionCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    Log.i(LOG_TAG, "testSkipToNext: prepared dsd MediaId=" + dsd.getMediaId());
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_START) {
                    if (dsd == dsd2) {
                        Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_START dsd2");
                        onStart2Called.signal();
                    } else if (dsd == dsd3) {
                        Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_START dsd3");
                        onStart3Called.signal();
                    } else {
                        Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_START other");
                        onStartCalled.signal();
                    }
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    if (dsd == dsd2) {
                        Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_END dsd2");
                        onCompletion2Called.signal();
                    } else if (dsd == dsd3) {
                        Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_END dsd3");
                        onCompletion3Called.signal();
                    } else {
                        Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_END other");
                        mOnCompletionCalled.signal();
                    }
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_LIST_END) {
                    Log.i(LOG_TAG, "testSkipToNext: MEDIA_INFO_DATA_SOURCE_LIST_END");
                    onListCompletionCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnCompletionCalled.reset();
        onCompletion2Called.reset();
        onCompletion3Called.reset();

        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mPlayer.prepare();

        mPlayer.play();
        Thread.sleep(1000);
        onStartCalled.waitForSignal();

        mPlayer.skipToNext();
        mOnCompletionCalled.waitForSignal(3000);
        assertTrue("current data source is not dsd2",
                mPlayer.getCurrentDataSource() == dsd2);
        Thread.sleep(1000);
        onStart2Called.waitForSignal();

        mPlayer.skipToNext();
        onCompletion2Called.waitForSignal(3000);
        assertTrue("current data source is not dsd2",
                mPlayer.getCurrentDataSource() == dsd3);
        onStart3Called.waitForSignal();

        onCompletion3Called.waitForSignal();
        onListCompletionCalled.waitForSignal();

        mPlayer.reset();
    }

    public void testClearNextDataSources() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        int resid = R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;  // skip
        }

        AssetFileDescriptor afd2 = mResources.openRawResourceFd(resid);
        DataSourceDesc dsd2 = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd2.getFileDescriptor()),
                        afd2.getStartOffset(), afd2.getLength())
                .build();
        afd2.close();

        mPlayer.setNextDataSource(dsd2);

        Monitor onStartCalled = new Monitor();
        Monitor onStart2Called = new Monitor();
        Monitor onCompletion2Called = new Monitor();
        Monitor onListCompletionCalled = new Monitor();
        Monitor onClearNextDataSourcesCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_START) {
                    if (dsd == dsd2) {
                        Log.i(LOG_TAG, "testClearNextDataSources: DATA_SOURCE_START dsd2");
                        onStart2Called.signal();
                    } else {
                        Log.i(LOG_TAG, "testClearNextDataSources: DATA_SOURCE_START other");
                        onStartCalled.signal();
                    }
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    if (dsd == dsd2) {
                        Log.i(LOG_TAG, "testClearNextDataSources: DATA_SOURCE_END dsd2");
                        onCompletion2Called.signal();
                    } else {
                        Log.i(LOG_TAG, "testClearNextDataSources: DATA_SOURCE_END other");
                        mOnCompletionCalled.signal();
                    }
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_LIST_END) {
                    Log.i(LOG_TAG, "testClearNextDataSources: DATA_SOURCE_LIST_END");
                    onListCompletionCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_CLEAR_NEXT_DATA_SOURCES) {
                    onClearNextDataSourcesCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnCompletionCalled.reset();
        onCompletion2Called.reset();

        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mPlayer.prepare();

        mPlayer.play();

        Thread.sleep(1000);  // sleep 1 second
        mPlayer.clearNextDataSources();

        onStartCalled.waitForSignal();
        mOnCompletionCalled.waitForSignal();
        onListCompletionCalled.waitForSignal();
        assertEquals("clearNextDataSources is confirmed none or more than once",
                1, onClearNextDataSourcesCalled.getNumSignal());
        assertEquals("next dsd was mistakenly started", 0, onStart2Called.getNumSignal());
        assertEquals("next dsd was mistakenly played", 0, onCompletion2Called.getNumSignal());

        mPlayer.reset();
    }

    // setPlaybackParams() with non-zero speed should NOT start playback.
    // zero speed is invalid.
    public void testSetPlaybackParamsSpeeds() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        Monitor onSetPlaybackParamsCompleteCalled = new Monitor();
        AtomicInteger setPlaybackParamsStatus =
                new AtomicInteger(MediaPlayer2.CALL_STATUS_NO_ERROR);
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    mOnCompletionCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    mOnSeekCompleteCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_SET_PLAYBACK_PARAMS) {
                    setPlaybackParamsStatus.set(status);
                    onSetPlaybackParamsCompleteCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnCompletionCalled.reset();
        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mPlayer.prepare();

        mOnSeekCompleteCalled.reset();
        mPlayer.seekTo(0, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        mOnSeekCompleteCalled.waitForSignal();

        float playbackRate = 0.f;
        onSetPlaybackParamsCompleteCalled.reset();
        mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        onSetPlaybackParamsCompleteCalled.waitForSignal();
        assertTrue("speed of zero is invalid",
                setPlaybackParamsStatus.intValue() == MediaPlayer2.CALL_STATUS_BAD_VALUE);

        playbackRate = 1.0f;

        int playTime = 2000;  // The testing clip is about 10 second long.
        onSetPlaybackParamsCompleteCalled.reset();
        mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        Thread.sleep(playTime);
        onSetPlaybackParamsCompleteCalled.waitForSignal();
        assertTrue("MediaPlayer2 should not be playing",
                mPlayer.getState() != MediaPlayer2.PLAYER_STATE_PLAYING);

        long duration = mPlayer.getDuration(mPlayer.getCurrentDataSource());
        mOnSeekCompleteCalled.reset();
        mPlayer.seekTo(duration - 1000, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        mOnSeekCompleteCalled.waitForSignal();

        mPlayer.play();

        mOnCompletionCalled.waitForSignal();
        assertFalse("MediaPlayer2 should not be playing",
                mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        onSetPlaybackParamsCompleteCalled.reset();
        mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        onSetPlaybackParamsCompleteCalled.waitForSignal();
        assertFalse("MediaPlayer2 should not be playing after EOS",
                mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        mPlayer.reset();
    }

    public void testPlaybackRate() throws Exception {
        final int toleranceMs = 1000;
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        Monitor onSetPlaybackParamsCompleteCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SET_PLAYBACK_PARAMS) {
                    onSetPlaybackParamsCompleteCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }
        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        SyncParams sync = new SyncParams().allowDefaults();
        mPlayer.setSyncParams(sync);
        sync = mPlayer.getSyncParams();

        float[] rates = { 0.25f, 0.5f, 1.0f, 2.0f };
        for (float playbackRate : rates) {
            mPlayer.seekTo(0, MediaPlayer2.SEEK_PREVIOUS_SYNC);
            int playTime = 4000;  // The testing clip is about 10 second long.
            onSetPlaybackParamsCompleteCalled.reset();
            mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
            mPlayer.play();
            Thread.sleep(playTime);
            onSetPlaybackParamsCompleteCalled.waitForSignal();
            PlaybackParams pbp = mPlayer.getPlaybackParams();
            assertEquals(
                    playbackRate, pbp.getSpeed(),
                    FLOAT_TOLERANCE + playbackRate * sync.getTolerance());
            assertTrue("MediaPlayer2 should still be playing",
                    mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

            long playedMediaDurationMs = mPlayer.getCurrentPosition();
            int diff = Math.abs((int)(playedMediaDurationMs / playbackRate) - playTime);
            if (diff > toleranceMs) {
                fail("Media player had error in playback rate " + playbackRate
                     + ", play time is " + playTime + " vs expected " + playedMediaDurationMs);
            }
            mPlayer.pause();
            pbp = mPlayer.getPlaybackParams();
            assertEquals(playbackRate, pbp.getSpeed(), FLOAT_TOLERANCE);
        }
        mPlayer.reset();
    }

    public void testSeekModes() throws Exception {
        // This clip has 2 I frames at 66687us and 4299687us.
        if (!checkLoadResource(
                R.raw.bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz)) {
            return; // skip
        }

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    mOnSeekCompleteCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        mOnSeekCompleteCalled.reset();
        mPlayer.play();

        final long seekPosMs = 3000;
        final long timeToleranceMs = 100;
        final long syncTime1Ms = 67;
        final long syncTime2Ms = 4300;

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to previous sync or next sync.
        long cp = runSeekMode(MediaPlayer2.SEEK_CLOSEST, seekPosMs);
        assertTrue("MediaPlayer2 did not seek to closest position",
                cp > seekPosMs && cp < syncTime2Ms);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or next sync.
        cp = runSeekMode(MediaPlayer2.SEEK_PREVIOUS_SYNC, seekPosMs);
        assertTrue("MediaPlayer2 did not seek to preivous sync position",
                cp < seekPosMs - timeToleranceMs);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or previous sync.
        cp = runSeekMode(MediaPlayer2.SEEK_NEXT_SYNC, seekPosMs);
        assertTrue("MediaPlayer2 did not seek to next sync position",
                cp > syncTime2Ms - timeToleranceMs);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or previous sync.
        cp = runSeekMode(MediaPlayer2.SEEK_CLOSEST_SYNC, seekPosMs);
        assertTrue("MediaPlayer2 did not seek to closest sync position",
                cp > syncTime2Ms - timeToleranceMs);

        mPlayer.reset();
    }

    private long runSeekMode(int seekMode, long seekPosMs) throws Exception {
        final int sleepIntervalMs = 100;
        int timeRemainedMs = 10000;  // total time for testing
        final int timeToleranceMs = 100;

        mPlayer.seekTo(seekPosMs, seekMode);
        mOnSeekCompleteCalled.waitForSignal();
        mOnSeekCompleteCalled.reset();
        long cp = -seekPosMs;
        while (timeRemainedMs > 0) {
            cp = mPlayer.getCurrentPosition();
            // Wait till MediaPlayer2 starts rendering since MediaPlayer2 caches
            // seek position as current position.
            if (cp < seekPosMs - timeToleranceMs || cp > seekPosMs + timeToleranceMs) {
                break;
            }
            timeRemainedMs -= sleepIntervalMs;
            Thread.sleep(sleepIntervalMs);
        }
        assertTrue("MediaPlayer2 did not finish seeking in time for mode " + seekMode,
                timeRemainedMs > 0);
        return cp;
    }

    public void testGetTimestamp() throws Exception {
        final int toleranceUs = 100000;
        final float playbackRate = 1.0f;
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        Monitor onPauseCalled = new Monitor();
        Monitor onSetPlaybackParamsCompleteCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PAUSE) {
                    onPauseCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_SET_PLAYBACK_PARAMS) {
                    onSetPlaybackParamsCompleteCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        mPlayer.play();
        onSetPlaybackParamsCompleteCalled.reset();
        mPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        Thread.sleep(SLEEP_TIME);  // let player get into stable state.
        onSetPlaybackParamsCompleteCalled.waitForSignal();
        long nt1 = System.nanoTime();
        MediaTimestamp ts1 = mPlayer.getTimestamp();
        long nt2 = System.nanoTime();
        assertTrue("Media player should return a valid time stamp", ts1 != null);
        assertEquals("MediaPlayer2 had error in clockRate " + ts1.getMediaClockRate(),
                playbackRate, ts1.getMediaClockRate(), 0.001f);
        assertTrue("The nanoTime of Media timestamp should be taken when getTimestamp is called.",
                nt1 <= ts1.getAnchorSystemNanoTime() && ts1.getAnchorSystemNanoTime() <= nt2);

        onPauseCalled.reset();
        mPlayer.pause();
        onPauseCalled.waitForSignal();
        ts1 = mPlayer.getTimestamp();
        assertTrue("Media player should return a valid time stamp", ts1 != null);
        assertTrue("Media player should have play rate of 0.0f when paused",
                ts1.getMediaClockRate() == 0.0f);

        mPlayer.seekTo(0, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        mPlayer.play();
        Thread.sleep(SLEEP_TIME);  // let player get into stable state.
        int playTime = 4000;  // The testing clip is about 10 second long.
        ts1 = mPlayer.getTimestamp();
        assertTrue("Media player should return a valid time stamp", ts1 != null);
        Thread.sleep(playTime);
        MediaTimestamp ts2 = mPlayer.getTimestamp();
        assertTrue("Media player should return a valid time stamp", ts2 != null);
        assertTrue("The clockRate should not be changed.",
                ts1.getMediaClockRate() == ts2.getMediaClockRate());
        assertEquals("MediaPlayer2 had error in timestamp.",
                ts1.getAnchorMediaTimeUs() + (long)(playTime * ts1.getMediaClockRate() * 1000),
                ts2.getAnchorMediaTimeUs(), toleranceUs);

        mPlayer.reset();
    }

    public void testLocalVideo_MKV_H265_1280x720_500kbps_25fps_AAC_Stereo_128kbps_44100Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz, 1280, 720);
    }
    public void testLocalVideo_MP4_H264_480x360_500kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_500kbps_25fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_500kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1000kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1000kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1350kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_25fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_128kbps_44110Hz_frag()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_fragmented,
                480, 360);
    }


    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_192kbps_44110Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz, 480, 360);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_12fps_aac_stereo_128kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_56kbps_25fps_aac_stereo_128kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_mono_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_mono_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_24kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_24kbps_22050hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_11025hz, 176, 144);
    }

    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playVideoTest(
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz, 176, 144);
    }

    private void readSubtitleTracks() throws Exception {
        mSubtitleTrackIndex.clear();
        List<TrackInfo> trackInfos = mPlayer.getTrackInfo(mPlayer.getCurrentDataSource());
        if (trackInfos == null || trackInfos.size() == 0) {
            return;
        }

        Vector<Integer> subtitleTrackIndex = new Vector<>();
        for (int i = 0; i < trackInfos.size(); ++i) {
            assertTrue(trackInfos.get(i) != null);
            if (trackInfos.get(i).getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                assertTrue(trackInfos.get(i).getLanguage() != null);
                assertTrue(trackInfos.get(i).getFormat() != null);
                subtitleTrackIndex.add(i);
            }
        }

        mSubtitleTrackIndex.addAll(subtitleTrackIndex);
    }

    private TrackInfo getSubtitleTrackInfo(int subtitleIndex) {
        int trackIndex = mSubtitleTrackIndex.get(subtitleIndex);
        List<TrackInfo> trackInfos = mPlayer.getTrackInfo();
        final TrackInfo trackInfo = trackInfos.get(trackIndex);
        return trackInfo;
    }

    private void selectSubtitleTrack(int subtitleIndex) throws Exception {
        final TrackInfo trackInfo = getSubtitleTrackInfo(subtitleIndex);
        // test both selectTrack API
        if (subtitleIndex == 0) {
            mPlayer.selectTrack(trackInfo);
        } else {
            mPlayer.selectTrack(mPlayer.getCurrentDataSource(), trackInfo);
        }
        mSelectedSubtitleIndex = subtitleIndex;
    }

    private void deselectSubtitleTrack(int subtitleIndex) throws Exception {
        mOnDeselectTrackCalled.reset();
        mPlayer.deselectTrack(mPlayer.getCurrentDataSource(), getSubtitleTrackInfo(subtitleIndex));
        mOnDeselectTrackCalled.waitForSignal();
        if (mSelectedSubtitleIndex == subtitleIndex) {
            mSelectedSubtitleIndex = -1;
        }
    }

    public void testDeselectTrackForSubtitleTracks() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_subtitle_tracks)) {
            return; // skip;
        }

        getInstrumentation().waitForIdleSync();

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_METADATA_UPDATE) {
                    mOnInfoCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    mOnSeekCompleteCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    mOnPlayCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_DESELECT_TRACK) {
                    mCallStatus = status;
                    mOnDeselectTrackCalled.signal();
                }
            }

            @Override
            public void onSubtitleData(MediaPlayer2 mp, DataSourceDesc dsd, SubtitleData data) {
                if (data != null && data.getData() != null) {
                    mOnSubtitleDataCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mPlayer.setDisplay(getActivity().getSurfaceHolder());
        mPlayer.setScreenOnWhilePlaying(true);
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "MediaPlayer2Test");
        mPlayer.setWakeLock(wakeLock);

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        mOnPlayCalled.reset();
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Run twice to check if repeated selection-deselection on the same track works well.
        for (int i = 0; i < 2; i++) {
            // Waits until at least one subtitle is fired. Timeout is 2.5 seconds.
            mOnSubtitleDataCalled.reset();
            selectSubtitleTrack(i);
            assertTrue(mOnSubtitleDataCalled.waitForSignal(2500));

            // Check selected track
            final int trackTypeSubtitle = TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE;
            final DataSourceDesc currentDataSource = mPlayer.getCurrentDataSource();
            assertTrue(mPlayer.getSelectedTrack(trackTypeSubtitle) != null);
            assertTrue(mPlayer.getSelectedTrack(currentDataSource, trackTypeSubtitle) != null);

            // Try deselecting track.
            deselectSubtitleTrack(i);
            mOnSubtitleDataCalled.reset();
            assertFalse(mOnSubtitleDataCalled.waitForSignal(1500));
        }

        // Deselecting unselected track: expected error status
        mCallStatus = MediaPlayer2.CALL_STATUS_NO_ERROR;
        deselectSubtitleTrack(0);
        assertTrue(mCallStatus != MediaPlayer2.CALL_STATUS_NO_ERROR);

        mPlayer.reset();
    }

    public void testChangeSubtitleTrack() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_subtitle_tracks)) {
            return; // skip;
        }

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_METADATA_UPDATE) {
                    mOnInfoCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    mOnPlayCalled.signal();
                }
            }

            @Override
            public void onSubtitleData(MediaPlayer2 mp, DataSourceDesc dsd, SubtitleData data) {
                if (data != null && data.getData() != null) {
                    mOnSubtitleDataCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mPlayer.setDisplay(getActivity().getSurfaceHolder());
        mPlayer.setScreenOnWhilePlaying(true);
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "MediaPlayer2Test");
        mPlayer.setWakeLock(wakeLock);

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        mOnPlayCalled.reset();
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Waits until at least two captions are fired. Timeout is 2.5 sec.
        selectSubtitleTrack(0);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        mOnSubtitleDataCalled.reset();
        selectSubtitleTrack(1);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        mPlayer.reset();
    }

    public void testGetTrackInfoForVideoWithSubtitleTracks() throws Throwable {
        if (!checkLoadResource(R.raw.testvideo_with_2_subtitle_tracks)) {
            return; // skip;
        }

        getInstrumentation().waitForIdleSync();

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_METADATA_UPDATE) {
                    mOnInfoCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    mOnPlayCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mPlayer.setDisplay(getActivity().getSurfaceHolder());
        mPlayer.setScreenOnWhilePlaying(true);
        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "MediaPlayer2Test");
        mPlayer.setWakeLock(wakeLock);

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        mOnPlayCalled.reset();
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        // The media metadata will be changed while playing since closed caption tracks are in-band
        // and those tracks will be found after processing a number of frames. These tracks will be
        // found within one second.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();
        assertEquals(2, mSubtitleTrackIndex.size());

        mPlayer.reset();
    }

    /*
     *  This test assumes the resources being tested are between 8 and 14 seconds long
     *  The ones being used here are 10 seconds long.
     */
    public void testResumeAtEnd() throws Throwable {
        int testsRun =
            testResumeAtEnd(R.raw.loudsoftmp3) +
            testResumeAtEnd(R.raw.loudsoftwav) +
            testResumeAtEnd(R.raw.loudsoftogg) +
            testResumeAtEnd(R.raw.loudsoftitunes) +
            testResumeAtEnd(R.raw.loudsoftfaac) +
            testResumeAtEnd(R.raw.loudsoftaac);
        if (testsRun == 0) {
            MediaUtils.skipTest("no decoder found");
        }
    }

    // returns 1 if test was run, 0 otherwise
    private int testResumeAtEnd(int res) throws Throwable {
        if (!loadResource(res)) {
            Log.i(LOG_TAG, "testResumeAtEnd: No decoder found for " +
                mContext.getResources().getResourceEntryName(res) +
                " --- skipping.");
            return 0; // skip
        }
        mOnCompletionCalled.reset();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    mOnCompletionCalled.signal();
                    mPlayer.play();
                }
            }
        };
        mPlayer.registerEventCallback(mExecutor, ecb);

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        // skip the first part of the file so we reach EOF sooner
        mPlayer.seekTo(5000, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        mPlayer.play();
        // sleep long enough that we restart playback at least once, but no more
        Thread.sleep(10000);
        assertTrue("MediaPlayer2 should still be playing",
                mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
        mPlayer.unregisterEventCallback(ecb);
        mPlayer.reset();
        assertEquals("wrong number of repetitions", 1, mOnCompletionCalled.getNumSignal());
        return 1;
    }

    public void testPositionAtEnd() throws Throwable {
        int testsRun =
            testPositionAtEnd(R.raw.test1m1shighstereo) +
            testPositionAtEnd(R.raw.loudsoftmp3) +
            testPositionAtEnd(R.raw.loudsoftwav) +
            testPositionAtEnd(R.raw.loudsoftogg) +
            testPositionAtEnd(R.raw.loudsoftitunes) +
            testPositionAtEnd(R.raw.loudsoftfaac) +
            testPositionAtEnd(R.raw.loudsoftaac);
        if (testsRun == 0) {
            MediaUtils.skipTest(LOG_TAG, "no decoder found");
        }
    }

    private int testPositionAtEnd(int res) throws Throwable {
        if (!loadResource(res)) {
            Log.i(LOG_TAG, "testPositionAtEnd: No decoder found for " +
                mContext.getResources().getResourceEntryName(res) +
                " --- skipping.");
            return 0; // skip
        }
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setInternalLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build();
        mPlayer.setAudioAttributes(attributes);

        mOnCompletionCalled.reset();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    mOnCompletionCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    mOnPlayCalled.signal();
                }
            }
        };
        mPlayer.registerEventCallback(mExecutor, ecb);

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        long duration = mPlayer.getDuration(mPlayer.getCurrentDataSource());
        assertTrue("resource too short", duration > 6000);
        mPlayer.seekTo(duration - 5000, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        mOnPlayCalled.reset();
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        while (mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING) {
            Log.i("@@@@", "position: " + mPlayer.getCurrentPosition());
            Thread.sleep(500);
        }
        Log.i("@@@@", "final position: " + mPlayer.getCurrentPosition());
        assertTrue(mPlayer.getCurrentPosition() > duration - 1000);
        mPlayer.reset();
        return 1;
    }

    public void testCallback() throws Throwable {
        final int mp4Duration = 8484;

        if (!checkLoadResource(R.raw.testvideo)) {
            return; // skip;
        }

        mPlayer.setDisplay(getActivity().getSurfaceHolder());
        mPlayer.setScreenOnWhilePlaying(true);

        mOnCompletionCalled.reset();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onVideoSizeChanged(MediaPlayer2 mp, DataSourceDesc dsd, Size size) {
                assertEquals(size, mp.getVideoSize());
                mOnVideoSizeChangedCalled.signal();
            }

            @Override
            public void onError(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                mOnErrorCalled.signal();
            }

            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                mOnInfoCalled.signal();

                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    mOnCompletionCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    mOnSeekCompleteCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    mOnPlayCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        assertFalse(mOnPrepareCalled.isSignalled());
        assertFalse(mOnVideoSizeChangedCalled.isSignalled());
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();
        mOnVideoSizeChangedCalled.waitForSignal();

        mOnSeekCompleteCalled.reset();
        mPlayer.seekTo(mp4Duration >> 1, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        mOnSeekCompleteCalled.waitForSignal();

        assertFalse(mOnCompletionCalled.isSignalled());
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        while(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING) {
            Thread.sleep(SLEEP_TIME);
        }
        assertFalse(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
        mOnCompletionCalled.waitForSignal();
        assertFalse(mOnErrorCalled.isSignalled());
        mPlayer.reset();
    }

    public void testRecordAndPlay() throws Exception {
        if (!hasMicrophone()) {
            MediaUtils.skipTest(LOG_TAG, "no microphone");
            return;
        }
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
                || !MediaUtils.checkEncoder(MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }
        File outputFile = new File(Environment.getExternalStorageDirectory(),
                "record_and_play.3gp");
        String outputFileLocation = outputFile.getAbsolutePath();
        try {
            recordMedia(outputFileLocation);

            Uri uri = Uri.parse(outputFileLocation);
            MediaPlayer2 mp = new MediaPlayer2(mContext);
            try {
                mp.setDataSource(new UriDataSourceDesc.Builder()
                        .setDataSource(mContext, uri)
                        .build());
                mp.prepare();
                Thread.sleep(SLEEP_TIME);
                playAndStop(mp);
            } finally {
                mp.close();
            }

            try {
                mp = createMediaPlayer2(mContext, uri);
                playAndStop(mp);
            } finally {
                if (mp != null) {
                    mp.close();
                }
            }

            try {
                mp = createMediaPlayer2(mContext, uri, getActivity().getSurfaceHolder());
                playAndStop(mp);
            } finally {
                if (mp != null) {
                    mp.close();
                }
            }
        } finally {
            outputFile.delete();
        }
    }

    private void playAndStop(MediaPlayer2 mp) throws Exception {
        mp.play();
        Thread.sleep(SLEEP_TIME);
        mp.reset();
    }

    private void recordMedia(String outputFile) throws Exception {
        MediaRecorder mr = new MediaRecorder();
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mr.setOutputFile(outputFile);

            mr.prepare();
            mr.start();
            Thread.sleep(SLEEP_TIME);
            mr.stop();
        } finally {
            mr.release();
        }
    }

    private boolean hasMicrophone() {
        return getActivity().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    // Smoke test playback from a DataSourceCallback.
    public void testPlaybackFromADataSourceCallback() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        final int duration = 10000;

        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;
        }

        TestDataSourceCallback dataSource =
                TestDataSourceCallback.fromAssetFd(mResources.openRawResourceFd(resid));
        // Test returning -1 from getSize() to indicate unknown size.
        dataSource.returnFromGetSize(-1);
        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());
        playLoadedVideo(null, null, -1);
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        // Test pause and restart.
        mPlayer.pause();
        Thread.sleep(SLEEP_TIME);
        assertFalse(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_PLAY) {
                    mOnPlayCalled.signal();
                }
            }
        };
        mPlayer.registerEventCallback(mExecutor, ecb);

        mOnPlayCalled.reset();
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        // Test reset.
        mPlayer.reset();
        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());

        mPlayer.registerEventCallback(mExecutor, ecb);

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        mOnPlayCalled.reset();
        mPlayer.play();
        mOnPlayCalled.waitForSignal();
        assertTrue(mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);

        // Test seek. Note: the seek position is cached and returned as the
        // current position so there's no point in comparing them.
        mPlayer.seekTo(duration - SLEEP_TIME, MediaPlayer2.SEEK_PREVIOUS_SYNC);
        while (mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING) {
            Thread.sleep(SLEEP_TIME);
        }
    }

    public void testNullDataSourceCallbackIsRejected() throws Exception {
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE) {
                    mCallStatus = status;
                    mOnPlayCalled.signal();
                }
            }
        };
        mPlayer.registerEventCallback(mExecutor, ecb);

        mCallStatus = MediaPlayer2.CALL_STATUS_NO_ERROR;
        mPlayer.setDataSource((DataSourceDesc) null);
        mOnPlayCalled.waitForSignal();
        assertTrue(mCallStatus != MediaPlayer2.CALL_STATUS_NO_ERROR);
    }

    public void testDataSourceCallbackIsClosedOnReset() throws Exception {
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE) {
                    mCallStatus = status;
                    mOnPlayCalled.signal();
                }
            }
        };
        mPlayer.registerEventCallback(mExecutor, ecb);

        TestDataSourceCallback dataSource = new TestDataSourceCallback(new byte[0]);
        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());
        mOnPlayCalled.waitForSignal();
        mPlayer.reset();
        assertTrue(dataSource.isClosed());
    }

    public void testPlaybackFailsIfDataSourceCallbackThrows() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;
        }

        setOnErrorListener();
        TestDataSourceCallback dataSource =
                TestDataSourceCallback.fromAssetFd(mResources.openRawResourceFd(resid));
        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());

        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        dataSource.throwFromReadAt();
        mPlayer.play();
        assertTrue(mOnErrorCalled.waitForSignal());
    }

    public void testPlaybackFailsIfDataSourceCallbackReturnsAnError() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        if (!MediaUtils.hasCodecsForResource(mContext, resid)) {
            return;
        }

        TestDataSourceCallback dataSource =
                TestDataSourceCallback.fromAssetFd(mResources.openRawResourceFd(resid));
        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());

        setOnErrorListener();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnPrepareCalled.reset();
        mPlayer.prepare();
        mOnPrepareCalled.waitForSignal();

        dataSource.returnFromReadAt(-2);
        mPlayer.play();
        assertTrue(mOnErrorCalled.waitForSignal());
    }

    public void testClose() throws Exception {
        assertTrue(loadResource(R.raw.testmp3_2));
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                .build();
        mPlayer.setAudioAttributes(attributes);
        mPlayer.prepare();
        mPlayer.play();
        mPlayer.close();
        mExecutor.shutdown();

        // Tests whether the notification from the player after the close() doesn't crash.
        Thread.sleep(SLEEP_TIME);
    }

    public void testPause() throws Exception {
        if (!checkLoadResource(
                R.raw.video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz)) {
            return; // skip
        }

        Monitor onStartCalled = new Monitor();
        Monitor mOnVideoSizeChangedCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onVideoSizeChanged(MediaPlayer2 mp, DataSourceDesc dsd, Size size) {
                if (size.getWidth() > 0 && size.getHeight() > 0) {
                    mOnVideoSizeChangedCalled.signal();
                }
            }

            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_START) {
                        Log.i(LOG_TAG, "testPause: MEDIA_INFO_DATA_SOURCE_START");
                        onStartCalled.signal();
                }
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mPlayer.setDisplay(mActivity.getSurfaceHolder());

        mPlayer.prepare();

        mPlayer.pause();

        onStartCalled.waitForSignal();
        mOnVideoSizeChangedCalled.waitForSignal();
        Thread.sleep(1000);

        assertFalse("MediaPlayer2 should not be playing",
                mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PLAYING);
        assertTrue("MediaPlayer2 should be paused",
                mPlayer.getState() == MediaPlayer2.PLAYER_STATE_PAUSED);
        mPlayer.reset();
    }

    public void testConsecutiveSeeks() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        final TestDataSourceCallback source =
                TestDataSourceCallback.fromAssetFd(mResources.openRawResourceFd(resid));
        final Monitor readAllowed = new Monitor();
        DataSourceCallback dataSource = new DataSourceCallback() {
            @Override
            public int readAt(long position, byte[] buffer, int offset, int size)
                    throws IOException {
                try {
                    readAllowed.waitForSignal();
                } catch (InterruptedException e) {
                    fail();
                }
                return source.readAt(position, buffer, offset, size);
            }

            @Override
            public long getSize() throws IOException {
                return source.getSize();
            }

            @Override
            public void close() throws IOException {
                source.close();
            }
        };
        final Monitor labelReached = new Monitor();
        final Monitor onSeekToCompleted = new Monitor();
        final ArrayList<Pair<Integer, Integer>> commandsCompleted = new ArrayList<>();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onCallCompleted(
                    MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                commandsCompleted.add(new Pair<>(what, status));
                if (what == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                    onSeekToCompleted.signal();
                }
            }

            @Override
            public void onError(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                mOnErrorCalled.signal();
            }

            @Override
            public void onCommandLabelReached(MediaPlayer2 mp, @NonNull Object label) {
                labelReached.signal();
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnErrorCalled.reset();

        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());

        // prepare() will be pending until readAllowed is signaled.
        mPlayer.prepare();

        mPlayer.seekTo(3000);
        mPlayer.seekTo(2000);
        mPlayer.seekTo(1000);
        mPlayer.notifyWhenCommandLabelReached(new Object());

        readAllowed.signal();
        labelReached.waitForSignal();

        assertFalse(mOnErrorCalled.isSignalled());
        assertEquals(5, commandsCompleted.size());
        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE,
                        MediaPlayer2.CALL_STATUS_NO_ERROR),
                commandsCompleted.get(0));
        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_PREPARE,
                        MediaPlayer2.CALL_STATUS_NO_ERROR),
                commandsCompleted.get(1));
        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_SEEK_TO,
                        MediaPlayer2.CALL_STATUS_SKIPPED),
                commandsCompleted.get(2));
        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_SEEK_TO,
                        MediaPlayer2.CALL_STATUS_SKIPPED),
                commandsCompleted.get(3));
        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_SEEK_TO,
                        MediaPlayer2.CALL_STATUS_NO_ERROR),
                commandsCompleted.get(4));

        commandsCompleted.clear();
        onSeekToCompleted.reset();
        mPlayer.seekTo(3000);
        onSeekToCompleted.waitForSignal();
        onSeekToCompleted.reset();
        mPlayer.seekTo(3000);
        onSeekToCompleted.waitForSignal();

        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_SEEK_TO,
                        MediaPlayer2.CALL_STATUS_NO_ERROR),
                commandsCompleted.get(0));
        assertEquals(
                new Pair<>(MediaPlayer2.CALL_COMPLETED_SEEK_TO,
                        MediaPlayer2.CALL_STATUS_SKIPPED),
                commandsCompleted.get(1));

        mPlayer.reset();
    }

    public void testFileDataSourceDesc() throws Exception {
        long startPosMs = 3000;
        long endPosMs = 7000;
        AssetFileDescriptor afd = mResources.openRawResourceFd(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz);
        FileDataSourceDesc fdsd = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd.getFileDescriptor()))
                .setStartPosition(startPosMs)
                .setEndPosition(endPosMs)
                .build();

        assertEquals(0, fdsd.getOffset());
        assertEquals(FileDataSourceDesc.FD_LENGTH_UNKNOWN, fdsd.getLength());
        assertEquals(startPosMs, fdsd.getStartPosition());
        assertEquals(endPosMs, fdsd.getEndPosition());

        FileDataSourceDesc fdsd2 = new FileDataSourceDesc.Builder(fdsd)
                .build();
        assertEquals(0, fdsd2.getOffset());
        assertEquals(FileDataSourceDesc.FD_LENGTH_UNKNOWN, fdsd2.getLength());
        assertEquals(startPosMs, fdsd2.getStartPosition());
        assertEquals(endPosMs, fdsd2.getEndPosition());

        afd.close();
    }

    public void testCallbackDataSourceDesc() throws Exception {
        TestDataSourceCallback dataSource = new TestDataSourceCallback(new byte[0]);
        CallbackDataSourceDesc cbdsd = new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build();
        assertEquals(dataSource, cbdsd.getDataSourceCallback());

        CallbackDataSourceDesc cbdsd2 = new CallbackDataSourceDesc.Builder(cbdsd)
                .build();
        assertEquals(dataSource, cbdsd2.getDataSourceCallback());
    }

    public void testStartEndPositions() throws Exception {
        long startPosMs = 3000;
        long endPosMs = 7000;
        AssetFileDescriptor afd = mResources.openRawResourceFd(
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz);
        FileDataSourceDesc fdsd = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd.getFileDescriptor()),
                    afd.getStartOffset(), afd.getLength())
                .setStartPosition(startPosMs)
                .setEndPosition(endPosMs)
                .build();
        assertEquals(afd.getStartOffset(), fdsd.getOffset());
        assertEquals(afd.getLength(), fdsd.getLength());
        mPlayer.setDataSource(fdsd);
        afd.close();
        mPlayer.setDisplay(mActivity.getSurfaceHolder());
        mPlayer.setScreenOnWhilePlaying(true);

        final Monitor videoRenderingStarted = new Monitor();
        final Monitor repeatCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_VIDEO_RENDERING_START) {
                    videoRenderingStarted.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                    mOnCompletionCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_REPEAT) {
                    repeatCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(
                    MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE) {
                    assertEquals(startPosMs, dsd.getStartPosition());
                    assertEquals(endPosMs, dsd.getEndPosition());
                }
            }

            @Override
            public void onError(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                mOnErrorCalled.signal();
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnErrorCalled.reset();

        // prepare() will be pending until readAllowed is signaled.
        mPlayer.prepare();
        mPlayer.play();

        videoRenderingStarted.waitForSignal();
        long startRenderingTime = mPlayer.getCurrentPosition();
        Log.i(LOG_TAG, "testStartEndPositions: start rendering time " + startRenderingTime
                + " vs requested " + startPosMs);
        assertEquals(startPosMs, startRenderingTime, 250);  // 250 ms tolerance

        mOnCompletionCalled.waitForSignal();
        long endRenderingTime = mPlayer.getCurrentPosition();
        Log.i(LOG_TAG, "testStartEndPositions: end rendering time " + endRenderingTime
                + " vs requested " + endPosMs);
        assertEquals(endPosMs, endRenderingTime, 250);  // 250 ms tolerance

        assertFalse(mOnErrorCalled.isSignalled());

        videoRenderingStarted.reset();
        mOnCompletionCalled.reset();
        mPlayer.play();

        videoRenderingStarted.waitForSignal();
        videoRenderingStarted.reset();
        startRenderingTime = mPlayer.getCurrentPosition();
        Log.i(LOG_TAG, "testStartEndPositions: replay start rendering time " + startRenderingTime
                + " vs requested " + startPosMs);
        assertEquals(startPosMs, startRenderingTime, 250);  // 250 ms tolerance

        repeatCalled.reset();
        mPlayer.loopCurrent(true);
        repeatCalled.waitForSignal();
        videoRenderingStarted.waitForSignal();
        startRenderingTime = mPlayer.getCurrentPosition();
        Log.i(LOG_TAG, "testStartEndPositions: looping start rendering time " + startRenderingTime
                + " vs requested " + startPosMs);
        assertEquals(startPosMs, startRenderingTime, 250);  // 250 ms tolerance

        Thread.sleep(1000);
        mPlayer.reset();
    }

    public void testCancelPendingCommands() throws Exception {
        final int resid = R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;

        final Monitor readRequested = new Monitor();
        final Monitor readAllowed = new Monitor();
        DataSourceCallback dataSource = new DataSourceCallback() {
            TestDataSourceCallback mTestSource =
                TestDataSourceCallback.fromAssetFd(mResources.openRawResourceFd(resid));
            @Override
            public int readAt(long position, byte[] buffer, int offset, int size)
                    throws IOException {
                try {
                    readRequested.signal();
                    readAllowed.waitForSignal();
                } catch (InterruptedException e) {
                    fail();
                }
                return mTestSource.readAt(position, buffer, offset, size);
            }

            @Override
            public long getSize() throws IOException {
                return mTestSource.getSize();
            }

            @Override
            public void close() throws IOException {
                mTestSource.close();
            }
        };

        final ArrayList<Integer> commandsCompleted = new ArrayList<>();
        setOnErrorListener();
        final Monitor labelReached = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    mOnPrepareCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(
                    MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                commandsCompleted.add(what);
            }

            @Override
            public void onError(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                mOnErrorCalled.signal();
            }

            @Override
            public void onCommandLabelReached(MediaPlayer2 mp, Object label) {
                labelReached.signal();
            }
        };
        synchronized (mEventCbLock) {
            mEventCallbacks.add(ecb);
        }

        mOnPrepareCalled.reset();
        mOnErrorCalled.reset();

        mPlayer.setDataSource(new CallbackDataSourceDesc.Builder()
                .setDataSource(dataSource)
                .build());


        // prepare() will be pending until readAllowed is signaled.
        mPlayer.prepare();

        Object playToken = mPlayer.play();
        Object seekToken = mPlayer.seekTo(1000);
        mPlayer.pause();

        readRequested.waitForSignal();

        // Cancel the pending commands while preparation is on hold.
        boolean canceled = mPlayer.cancelCommand(playToken);
        assertTrue("play command should be in pending queue", canceled);
        canceled = mPlayer.cancelCommand(seekToken);
        assertTrue("seekTo command should be in pending queue", canceled);

        Object dummy = new Object();
        canceled = mPlayer.cancelCommand(dummy);
        assertFalse("dummy command should not be in pending queue", canceled);

        // Make the on-going prepare operation fail and check the results.
        readAllowed.signal();
        mPlayer.notifyWhenCommandLabelReached(new Object());
        labelReached.waitForSignal();

        assertEquals(3, commandsCompleted.size());
        assertEquals(MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE, (int) commandsCompleted.get(0));
        assertEquals(MediaPlayer2.CALL_COMPLETED_PREPARE, (int) commandsCompleted.get(1));
        assertEquals(MediaPlayer2.CALL_COMPLETED_PAUSE, (int) commandsCompleted.get(2));
        assertEquals(0, mOnErrorCalled.getNumSignal());
    }

    public void testHandleFileDataSourceDesc() throws Exception {
        final int resid1 = R.raw.testmp3;
        final int resid2 = R.raw.testmp3_4;

        MediaPlayer2 mp = new MediaPlayer2(mContext);

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock =
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                        "MediaPlayer2Test");
        mp.setWakeLock(wakeLock);

        Monitor onPrepareCalled = new Monitor();
        Monitor onSetDataSourceCalled = new Monitor();
        Monitor onDataSourceListEndCalled = new Monitor();
        Monitor onSetNextDataSourceCalled = new Monitor();
        Monitor onClearNextDataSourcesCalled = new Monitor();
        MediaPlayer2.EventCallback ecb = new MediaPlayer2.EventCallback() {
            @Override
            public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
                if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                    onPrepareCalled.signal();
                } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_LIST_END) {
                    onDataSourceListEndCalled.signal();
                }
            }

            @Override
            public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd, int what, int status) {
                if (what == MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE) {
                    onSetDataSourceCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_CLEAR_NEXT_DATA_SOURCES) {
                    onClearNextDataSourcesCalled.signal();
                } else if (what == MediaPlayer2.CALL_COMPLETED_SET_NEXT_DATA_SOURCE) {
                    onSetNextDataSourceCalled.signal();
                }
            }
        };
        mp.registerEventCallback(mExecutor, ecb);

        FileDataSourceDesc fdsd1 = openFile(resid1);
        FileDataSourceDesc fdsd2 = openFile(resid2);

        mp.setDataSource(fdsd1);
        mp.setNextDataSource(fdsd2);
        mp.prepare();
        onPrepareCalled.waitForSignal();
        mp.reset();
        assertTrue(isFileDataSourceDescClosed(fdsd1));
        assertTrue(isFileDataSourceDescClosed(fdsd2));

        fdsd1 = openFile(resid1);
        fdsd2 = openFile(resid2);
        mp.setDataSource(fdsd1);
        mp.setNextDataSource(fdsd2);
        onClearNextDataSourcesCalled.reset();
        mp.clearNextDataSources();
        onClearNextDataSourcesCalled.waitForSignal();
        assertTrue(isFileDataSourceDescClosed(fdsd2));

        fdsd2 = openFile(resid2);
        onSetNextDataSourceCalled.reset();
        mp.setNextDataSource(fdsd2);
        onSetNextDataSourceCalled.waitForSignal();
        onSetNextDataSourceCalled.reset();
        mp.setNextDataSource(fdsd2);
        onSetNextDataSourceCalled.waitForSignal();
        assertFalse(isFileDataSourceDescClosed(fdsd2));

        onDataSourceListEndCalled.reset();
        mp.prepare();
        mp.play();
        onDataSourceListEndCalled.waitForSignal();
        assertTrue(isFileDataSourceDescClosed(fdsd1));

        mp.close();
    }

    private FileDataSourceDesc openFile(int resid) throws Exception {
        AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
        FileDataSourceDesc fdsd = new FileDataSourceDesc.Builder()
                .setDataSource(ParcelFileDescriptor.dup(afd.getFileDescriptor()),
                    afd.getStartOffset(), afd.getLength())
                .build();
        afd.close();
        return fdsd;
    }

    private boolean isFileDataSourceDescClosed(FileDataSourceDesc fdsd) {
        try {
            fdsd.getParcelFileDescriptor().getFd();
        } catch (IllegalStateException e) {
            return true;
        }
        return false;
    }
}
