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

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.media.DataSourceDesc;
import android.media.UriDataSourceDesc;
import android.media.MediaDrm;
import android.media.MediaPlayer2;
import android.media.MediaPlayer2.DrmEventCallback;
import android.media.MediaPlayer2.DrmInfo;
import android.media.MediaPlayer2.DrmPreparationInfo;
import android.media.MediaDrm.KeyRequest;
import android.media.cts.TestUtils.Monitor;
import android.net.Uri;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Base class for DRM tests which use MediaPlayer2 to play audio or video.
 */
public class MediaPlayer2DrmTestBase extends ActivityInstrumentationTestCase2<MediaStubActivity> {
    private static final Logger LOG = Logger.getLogger(MediaPlayerTestBase.class.getName());

    protected static final int STREAM_RETRIES = 3;

    protected Monitor mSetDataSourceCallCompleted = new Monitor();
    protected Monitor mOnPreparedCalled = new Monitor();
    protected Monitor mOnVideoSizeChangedCalled = new Monitor();
    protected Monitor mOnPlaybackCompleted = new Monitor();
    protected Monitor mOnPlaylistCompleted = new Monitor();
    protected Monitor mOnDrmInfoCalled = new Monitor();
    protected Monitor mOnDrmPreparedCalled = new Monitor();
    protected Monitor mOnErrorCalled = new Monitor();
    protected int mCallStatus = MediaPlayer2.CALL_STATUS_NO_ERROR;

    protected Context mContext;
    protected Resources mResources;

    protected MediaPlayer2 mPlayer = null;
    protected MediaStubActivity mActivity;

    private boolean mExpectError;
    protected ExecutorService mExecutor;
    protected MediaPlayer2.EventCallback mECb = new MediaPlayer2.EventCallback() {
        @Override
        public void onVideoSizeChanged(MediaPlayer2 mp, DataSourceDesc dsd, Size size) {
            Log.v(TAG, "VideoSizeChanged" + " w:" + size.getWidth()
                    + " h:" + size.getHeight());
            mOnVideoSizeChangedCalled.signal();
        }

        @Override
        public void onError(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
            assertTrue("Media player had error " + what + " playing video", mExpectError);
            mOnErrorCalled.signal();
        }

        @Override
        public void onInfo(MediaPlayer2 mp, DataSourceDesc dsd, int what, int extra) {
            if (what == MediaPlayer2.MEDIA_INFO_PREPARED) {
                mOnPreparedCalled.signal();
            } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_END) {
                Log.v(TAG, "playLoadedVideo: MEDIA_INFO_DATA_SOURCE_END");
                mOnPlaybackCompleted.signal();
            } else if (what == MediaPlayer2.MEDIA_INFO_DATA_SOURCE_LIST_END) {
                Log.v(TAG, "playLoadedVideo: MEDIA_INFO_DATA_SOURCE_LIST_END");
                mOnPlaylistCompleted.signal();
            }
        }

        @Override
        public void onCallCompleted(MediaPlayer2 mp, DataSourceDesc dsd,
                int what, int status) {
            if (what == MediaPlayer2.CALL_COMPLETED_SET_DATA_SOURCE) {
                mCallStatus = status;
                mSetDataSourceCallCompleted.signal();
            }
        }
    };

    public MediaPlayer2DrmTestBase() {
        super(MediaStubActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        getInstrumentation().waitForIdleSync();
        mContext = getInstrumentation().getTargetContext();
        try {
            runTestOnUiThread(new Runnable() {
                public void run() {
                    mPlayer = new MediaPlayer2(mContext);
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
        mResources = mContext.getResources();

        mExecutor = Executors.newFixedThreadPool(2);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mPlayer != null) {
            mPlayer.close();
            mPlayer = null;
        }
        mExecutor.shutdown();
        mActivity = null;
    }

    private static class PrepareFailedException extends Exception {}

    //////////////////////////////////////////////////////////////////////////////////////////
    // Modular DRM

    private static final String TAG = "MediaPlayer2DrmTestBase";

    protected static final int PLAY_TIME_MS = 60 * 1000;
    protected byte[] mKeySetId;
    protected boolean mAudioOnly;

    private static final byte[] CLEAR_KEY_CENC = {
            (byte) 0x1a, (byte) 0x8a, (byte) 0x20, (byte) 0x95,
            (byte) 0xe4, (byte) 0xde, (byte) 0xb2, (byte) 0xd2,
            (byte) 0x9e, (byte) 0xc8, (byte) 0x16, (byte) 0xac,
            (byte) 0x7b, (byte) 0xae, (byte) 0x20, (byte) 0x82
            };

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);

    final byte[] mClearKeyPssh = hexStringToByteArray(
            "0000003470737368"    // BMFF box header (4 bytes size + 'pssh')
            + "01000000"          // Full box header (version = 1 flags = 0)
            + "1077efecc0b24d02"  // SystemID
            + "ace33c1e52e2fb4b"
            + "00000001"          // Number of key ids
            + "60061e017e477e87"  // Key id
            + "7e57d00d1ed00d1e"
            + "00000000"          // Size of Data, must be zero
            );


    protected enum ModularDrmTestType {
        V0_SYNC_TEST,
        V1_ASYNC_TEST,
        V2_SYNC_CONFIG_TEST,
        V3_ASYNC_DRMPREPARED_TEST,
        V4_SYNC_OFFLINE_KEY,
        V5_ASYNC_CALLBACKS_TEST,
        V6_ASYNC_KEYREQUEST_ERR_TEST,
    }

    // TODO: After living on these tests for a while, we can consider grouping them based on
    // the asset such that each asset is downloaded once and played back with multiple tests.
    protected void playModularDrmVideoDownload(Uri uri, Uri path, int width, int height,
            ModularDrmTestType testType) throws Exception {
        final long downloadTimeOutSeconds = 600;
        Log.i(TAG, "Downloading file:" + path);
        MediaDownloadManager mediaDownloadManager = new MediaDownloadManager(mContext);
        final long id = mediaDownloadManager.downloadFileWithRetries(
                uri, path, downloadTimeOutSeconds, STREAM_RETRIES);
        assertFalse("Download " + uri + " failed.", id == -1);
        Uri file = mediaDownloadManager.getUriForDownloadedFile(id);
        Log.i(TAG, "Downloaded file:" + path + " id:" + id + " uri:" + file);

        try {
            playModularDrmVideo(file, width, height, testType);
        } finally {
            mediaDownloadManager.removeFile(id);
        }
    }

    protected void playModularDrmVideo(Uri uri, int width, int height,
            ModularDrmTestType testType) throws Exception {
        // Force gc for a clean start
        System.gc();

        playModularDrmVideoWithRetries(uri, width, height, PLAY_TIME_MS, testType);
    }

    protected void playModularDrmVideoWithRetries(Uri file, Integer width, Integer height,
            int playTime, ModularDrmTestType testType) throws Exception {

        // first the synchronous variation
        boolean playedSuccessfully = false;
        for (int i = 0; i < STREAM_RETRIES; i++) {
            try {
                Log.v(TAG, "playVideoWithRetries(" + testType + ") try " + i);
                playLoadedModularDrmVideo(file, width, height, playTime, testType);

                playedSuccessfully = true;
                break;
            } catch (PrepareFailedException e) {
                // we can fail because of network issues, so try again
                Log.w(TAG, "playVideoWithRetries(" + testType + ") failed on try " + i
                        + ", trying playback again");
                mPlayer.reset();
            }
        }
        assertTrue("Stream did not play successfully after all attempts (syncDrmSetup)",
                playedSuccessfully);
    }

    /**
     * Play a video which has already been loaded with setDataSource().
     * The DRM setup is performed synchronously.
     *
     * @param file data source
     * @param width width of the video to verify, or null to skip verification
     * @param height height of the video to verify, or null to skip verification
     * @param playTime length of time to play video, or 0 to play entire video
     * @param testType test type
     */
    private void playLoadedModularDrmVideo(final Uri file, final Integer width,
            final Integer height, int playTime, ModularDrmTestType testType) throws Exception {

        switch (testType) {
            case V0_SYNC_TEST:
            case V1_ASYNC_TEST:
            case V2_SYNC_CONFIG_TEST:
            case V3_ASYNC_DRMPREPARED_TEST:
            case V5_ASYNC_CALLBACKS_TEST:
            case V6_ASYNC_KEYREQUEST_ERR_TEST:
                playLoadedModularDrmVideo_Generic(file, width, height, playTime, testType);
                break;

            case V4_SYNC_OFFLINE_KEY:
                playLoadedModularDrmVideo_V4_offlineKey(file, width, height, playTime);
                break;

        }
    }

    private void playLoadedModularDrmVideo_Generic(final Uri file, final Integer width,
            final Integer height, int playTime, ModularDrmTestType testType) throws Exception {

        final float volume = 0.5f;

        mAudioOnly = (width == 0);

        mCallStatus = MediaPlayer2.CALL_STATUS_NO_ERROR;

        mPlayer.registerEventCallback(mExecutor, mECb);
        Log.v(TAG, "playLoadedVideo: setDataSource()");
        DataSourceDesc dsd = new DataSourceDesc.Builder().setDataSource(file).build();
        mPlayer.setDataSource(dsd);
        mSetDataSourceCallCompleted.waitForSignal();
        if (mCallStatus != MediaPlayer2.CALL_STATUS_NO_ERROR) {
            throw new PrepareFailedException();
        }

        SurfaceHolder surfaceHolder = mActivity.getSurfaceHolder();
        surfaceHolder.setKeepScreenOn(true);
        mPlayer.setSurface(surfaceHolder.getSurface());

        try {
            final AtomicBoolean drmCallbackError = new AtomicBoolean(false);
            final List<DataSourceDesc> dsds = Collections.singletonList(dsd);
            switch (testType) {
                case V0_SYNC_TEST:
                    preparePlayerAndDrm_V0_syncDrmSetup(dsd);
                    break;

                case V1_ASYNC_TEST:
                    preparePlayerAndDrm_V1_asyncDrmSetup(dsd);
                    break;

                case V2_SYNC_CONFIG_TEST:
                    preparePlayerAndDrm_V2_syncDrmSetupPlusConfig(dsd);
                    break;

                case V3_ASYNC_DRMPREPARED_TEST:
                    preparePlayerAndDrm_V3_asyncDrmSetupPlusDrmPreparedListener(dsd);
                    break;

                case V5_ASYNC_CALLBACKS_TEST:
                    DrmEventCallback eventCallback = new CtsDrmEventCallback(
                            drmCallbackError, dsds, MediaDrm.KEY_TYPE_STREAMING);
                    preparePlayerAndDrm_asyncDrmSetupCallbacks(eventCallback, drmCallbackError);
                    break;

                case V6_ASYNC_KEYREQUEST_ERR_TEST:
                    mExpectError = true;
                    DrmEventCallback misbehave = new CtsDrmBadServerResponseCallback(
                            drmCallbackError, dsds, MediaDrm.KEY_TYPE_STREAMING);
                    preparePlayerAndDrm_asyncDrmSetupCallbacks(misbehave , drmCallbackError);
                    break;

                default:
                    throw new IllegalArgumentException("invalid test type: " + testType);
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw new PrepareFailedException();
        }

        Log.v(TAG, "playLoadedVideo: play()");
        mPlayer.play();
        if (!mAudioOnly) {
            mOnVideoSizeChangedCalled.waitForSignal();
        }
        mPlayer.setPlayerVolume(volume);

        // waiting to complete
        if (playTime == 0) {
            Log.v(TAG, "playLoadedVideo: waiting for playback completion");
            mOnPlaybackCompleted.waitForSignal();
        } else {
            Log.v(TAG, "playLoadedVideo: waiting while playing for " + playTime);
            mOnPlaybackCompleted.waitForSignal(playTime);
        }

        try {
            Log.v(TAG, "playLoadedVideo: releaseDrm");
            mPlayer.reset();
            mPlayer.releaseDrm(dsd);
        } catch (Exception e) {
            e.printStackTrace();
            throw new PrepareFailedException();
        }
    }

    private void preparePlayerAndDrm_V0_syncDrmSetup(DataSourceDesc dsd) throws Exception {
        Log.v(TAG, "preparePlayerAndDrm_V0: calling prepare()");
        mPlayer.prepare();
        mOnPreparedCalled.waitForSignal();
        if (mCallStatus != MediaPlayer2.CALL_STATUS_NO_ERROR) {
            throw new IOException();
        }

        DrmInfo drmInfo = mPlayer.getDrmInfo(dsd);
        if (drmInfo != null) {
            setupDrm(dsd, drmInfo, true /* prepareDrm */,
                    true /* synchronousNetworking */, MediaDrm.KEY_TYPE_STREAMING);
            Log.v(TAG, "preparePlayerAndDrm_V0: setupDrm done!");
        }
    }

    private void preparePlayerAndDrm_V1_asyncDrmSetup(DataSourceDesc dsd) throws InterruptedException {
        final AtomicBoolean asyncSetupDrmError = new AtomicBoolean(false);

        mPlayer.setDrmEventCallback(mExecutor, new MediaPlayer2.DrmEventCallback() {
            @Override
            public DrmPreparationInfo onDrmInfo(MediaPlayer2 mp, DataSourceDesc dsd2,
                    DrmInfo drmInfo) {
                Log.v(TAG, "preparePlayerAndDrm_V1: onDrmInfo" + drmInfo);
                if (dsd != dsd2) {
                    Log.e(TAG, "preparePlayerAndDrm_V1: onDrmInfo dsd mismatch");
                    asyncSetupDrmError.set(true);
                    mOnDrmInfoCalled.signal();
                    return null;
                }

                // in the callback (async mode) so handling exceptions here
                try {
                    setupDrm(dsd2, drmInfo, true /* prepareDrm */,
                            true /* synchronousNetworking */, MediaDrm.KEY_TYPE_STREAMING);
                } catch (Exception e) {
                    Log.v(TAG, "preparePlayerAndDrm_V1: setupDrm EXCEPTION " + e);
                    asyncSetupDrmError.set(true);
                }

                mOnDrmInfoCalled.signal();
                Log.v(TAG, "preparePlayerAndDrm_V1: onDrmInfo done!");
                return null;
            }
        });

        Log.v(TAG, "preparePlayerAndDrm_V1: calling prepare()");
        mPlayer.prepare();

        mOnDrmInfoCalled.waitForSignal();

        // Waiting till the player is prepared
        mOnPreparedCalled.waitForSignal();

        // to handle setupDrm error (async) in the main thread rather than the callback
        if (asyncSetupDrmError.get()) {
            fail("preparePlayerAndDrm_V1: setupDrm");
        }
    }

    private void preparePlayerAndDrm_V2_syncDrmSetupPlusConfig(DataSourceDesc dsd)
            throws Exception {
        final AtomicBoolean drmConfigError = new AtomicBoolean(false);
        mPlayer.setDrmEventCallback(mExecutor, new MediaPlayer2.DrmEventCallback() {
            @Override
            public void onDrmConfig(MediaPlayer2 mp, DataSourceDesc dsd2, MediaDrm drmObj) {
                if (dsd != dsd2) {
                    Log.e(TAG, "preparePlayerAndDrm_V2: onDrmConfig dsd mismatch");
                    drmConfigError.set(true);
                    return;
                }

                String widevineSecurityLevel3 = "L3";
                String securityLevelProperty = "securityLevel";

                try {
                    String level = drmObj.getPropertyString(securityLevelProperty);
                    Log.v(TAG, "preparePlayerAndDrm_V2: getDrmPropertyString: "
                            + securityLevelProperty + " -> " + level);
                    drmObj.setPropertyString(securityLevelProperty, widevineSecurityLevel3);
                    level = drmObj.getPropertyString(securityLevelProperty);
                    Log.v(TAG, "preparePlayerAndDrm_V2: getDrmPropertyString: "
                            + securityLevelProperty + " -> " + level);
                } catch (Exception e) {
                    Log.v(TAG, "preparePlayerAndDrm_V2: onDrmConfig EXCEPTION " + e);
                }
            }
        });

        Log.v(TAG, "preparePlayerAndDrm_V2: calling prepare()");
        mPlayer.prepare();

        mOnPreparedCalled.waitForSignal();
        if (mCallStatus != MediaPlayer2.CALL_STATUS_NO_ERROR) {
            throw new IOException();
        }

        if (drmConfigError.get()) {
            fail("preparePlayerAndDrm_V2: onDrmConfig");
        }

        DrmInfo drmInfo = mPlayer.getDrmInfo(dsd);
        if (drmInfo != null) {
            setupDrm(dsd, drmInfo, true /* prepareDrm */,
                    true /* synchronousNetworking */, MediaDrm.KEY_TYPE_STREAMING);
            Log.v(TAG, "preparePlayerAndDrm_V2: setupDrm done!");
        }
    }

    private void preparePlayerAndDrm_V3_asyncDrmSetupPlusDrmPreparedListener(DataSourceDesc dsd)
            throws InterruptedException {
        final AtomicBoolean asyncSetupDrmError = new AtomicBoolean(false);

        mPlayer.setDrmEventCallback(mExecutor, new MediaPlayer2.DrmEventCallback() {
            @Override
            public DrmPreparationInfo onDrmInfo(MediaPlayer2 mp, DataSourceDesc dsd2,
                    DrmInfo drmInfo) {
                Log.v(TAG, "preparePlayerAndDrm_V3: onDrmInfo" + drmInfo);

                // DRM preperation
                List<UUID> supportedSchemes = drmInfo.getSupportedSchemes();
                if (dsd != dsd2 || supportedSchemes.isEmpty()) {
                    String msg = dsd != dsd2 ? "dsd mismatch" : "No supportedSchemes";
                    Log.e(TAG, "preparePlayerAndDrm_V3: onDrmInfo " + msg);
                    asyncSetupDrmError.set(true);
                    mOnDrmInfoCalled.signal();
                    // we won't call prepareDrm anymore but need to get passed the wait
                    mOnDrmPreparedCalled.signal();
                    return null;
                }

                // setting up with the first supported UUID
                // instead of supportedSchemes[0] in GTS
                UUID drmScheme = CLEARKEY_SCHEME_UUID;
                Log.d(TAG, "preparePlayerAndDrm_V3: onDrmInfo: selected " + drmScheme);

                Log.v(TAG, "preparePlayerAndDrm_V3: onDrmInfo: calling prepareDrm");
                mp.prepareDrm(dsd2, drmScheme);

                mOnDrmInfoCalled.signal();
                Log.v(TAG, "preparePlayerAndDrm_V3: onDrmInfo done!");
                return null;
            }

            @Override
            public void onDrmPrepared(MediaPlayer2 mp, DataSourceDesc dsd2, int status,
                    byte[] keySetId) {
                Log.v(TAG, "preparePlayerAndDrm_V3: onDrmPrepared status: " + status);

                if (dsd != dsd2) {
                    asyncSetupDrmError.set(true);
                    Log.e(TAG, "preparePlayerAndDrm_V3: onDrmPrepared dsd mismatch");
                    mOnDrmPreparedCalled.signal();
                    return;
                }

                if (status != MediaPlayer2.PREPARE_DRM_STATUS_SUCCESS) {
                    asyncSetupDrmError.set(true);
                    Log.e(TAG, "preparePlayerAndDrm_V3: onDrmPrepared did not succeed");
                }

                DrmInfo drmInfo = mPlayer.getDrmInfo(dsd2);

                // in the callback (async mode) so handling exceptions here
                try {
                    setupDrm(dsd2, drmInfo, false /* prepareDrm */,
                            true /* synchronousNetworking */, MediaDrm.KEY_TYPE_STREAMING);
                } catch (Exception e) {
                    Log.v(TAG, "preparePlayerAndDrm_V3: setupDrm EXCEPTION " + e);
                    asyncSetupDrmError.set(true);
                }

                mOnDrmPreparedCalled.signal();
                Log.v(TAG, "preparePlayerAndDrm_V3: onDrmPrepared done!");
            }
        });

        Log.v(TAG, "preparePlayerAndDrm_V3: calling prepare()");
        mPlayer.prepare();

        // Waiting till the player is prepared
        mOnPreparedCalled.waitForSignal();

        // Unlike v3, onDrmPrepared is not synced to onPrepared b/c of its own thread handler
        mOnDrmPreparedCalled.waitForSignal();

        // to handle setupDrm error (async) in the main thread rather than the callback
        if (asyncSetupDrmError.get()) {
            fail("preparePlayerAndDrm_V3: setupDrm");
        }
    }

    private class CtsDrmBadServerResponseCallback extends CtsDrmEventCallback {
        CtsDrmBadServerResponseCallback(
                AtomicBoolean drmCallbackError, List<DataSourceDesc> dsds, int keyType) {
            super(drmCallbackError, dsds, keyType);
        }

        @Override
        public byte[] onDrmKeyRequest(MediaPlayer2 mp, DataSourceDesc dsd, KeyRequest req) {
            return null;
        }
    }

    private class CtsDrmEventCallback extends MediaPlayer2.DrmEventCallback {
        final String DRM_EVENT_CB_TAG = CtsDrmEventCallback.class.getSimpleName();
        final AtomicBoolean mDrmCallbackError;
        final List<DataSourceDesc> mDsds;
        final int mKeyType;
        byte[] mOfflineKeySetId;

        CtsDrmEventCallback(AtomicBoolean drmCallbackError, List<DataSourceDesc> dsds,
                int keyType) {
            mDrmCallbackError = drmCallbackError;
            mDsds = new ArrayList<DataSourceDesc>(dsds);
            mKeyType = keyType;
        }

        @Override
        public DrmPreparationInfo onDrmInfo(MediaPlayer2 mp, DataSourceDesc dsd,
                DrmInfo drmInfo) {
            Log.v(DRM_EVENT_CB_TAG, "onDrmInfo" + drmInfo);

            // DRM preparation
            List<UUID> supportedSchemes = drmInfo.getSupportedSchemes();
            final DataSourceDesc curDsd = mDsds.get(0);
            if (curDsd != dsd || supportedSchemes.isEmpty()) {
                String msg = curDsd != dsd ? "dsd mismatch" : "No supportedSchemes";
                Log.e(DRM_EVENT_CB_TAG, "onDrmInfo " + msg);
                mDrmCallbackError.set(true);
                return null;
            }

            // setting up with the first supported UUID
            // instead of supportedSchemes[0] in GTS
            DrmPreparationInfo.Builder drmBuilder = new DrmPreparationInfo.Builder();
            UUID drmScheme = CLEARKEY_SCHEME_UUID;
            drmBuilder.setUuid(drmScheme);
            if (mOfflineKeySetId != null && mOfflineKeySetId.length > 0) {
                drmBuilder.setKeySetId(mOfflineKeySetId);
                return drmBuilder.build();
            }

            drmBuilder.setMimeType("cenc");
            drmBuilder.setKeyType(mKeyType);

            byte[] psshData = drmInfo.getPssh().get(drmScheme);
            byte[] initData;
            // diverging from GTS
            if (psshData == null) {
                initData = mClearKeyPssh;
                Log.d(DRM_EVENT_CB_TAG,
                        "CLEARKEY scheme not found in PSSH. Using default data.");
            } else {
                // Can skip conversion if ClearKey adds support for BMFF initData b/64863112
                initData = makeCencPSSH(drmScheme, psshData);
            }
            drmBuilder.setInitData(initData);
            Log.d(DRM_EVENT_CB_TAG,
                    "initData[" + drmScheme + "]: " + Arrays.toString(initData));

            Log.v(DRM_EVENT_CB_TAG, "onDrmInfo done!");
            drmBuilder.setOptionalParameters(null);
            return drmBuilder.build();
        }

        @Override
        public void onDrmConfig(MediaPlayer2 mp, DataSourceDesc dsd2, MediaDrm drmObj) {

            final DataSourceDesc curDsd = mDsds.get(0);
            if (curDsd != dsd2) {
                Log.e(DRM_EVENT_CB_TAG, "onDrmConfig dsd mismatch");
                mDrmCallbackError.set(true);
                return;
            }

            try {
                String securityLevelProperty = "securityLevel";
                String widevineSecurityLevel3 = "L3";
                String level = drmObj.getPropertyString(securityLevelProperty);
                Log.v(DRM_EVENT_CB_TAG, "getDrmPropertyString: "
                        + securityLevelProperty + " -> " + level);
                drmObj.setPropertyString(securityLevelProperty, widevineSecurityLevel3);
                level = drmObj.getPropertyString(securityLevelProperty);
                Log.v(DRM_EVENT_CB_TAG, "getDrmPropertyString: "
                        + securityLevelProperty + " -> " + level);
            } catch (Exception e) {
                Log.v(DRM_EVENT_CB_TAG, "onDrmConfig EXCEPTION " + e);
            }

        }

        @Override
        public byte[] onDrmKeyRequest(MediaPlayer2 mp, DataSourceDesc dsd, KeyRequest req) {
            byte[][] clearKeys = new byte[][] { CLEAR_KEY_CENC };
            byte[] response = createKeysResponse(req, clearKeys, mKeyType);
            return response;
        }

        @Override
        public void onDrmPrepared(MediaPlayer2 mp, DataSourceDesc dsd, int status,
                byte[] keySetId) {

            Log.v(DRM_EVENT_CB_TAG, "onDrmPrepared status: " + status);
            String errMsg = null;
            final DataSourceDesc curDsd = mDsds.remove(0);
            if (curDsd != dsd) {
                errMsg = "dsd mismatch";
            }

            if (status != MediaPlayer2.PREPARE_DRM_STATUS_SUCCESS) {
                errMsg = "drm prepare failed";
            }

            final boolean hasKeySetId = keySetId != null && keySetId.length > 0;
            final boolean isOffline = mKeyType == MediaDrm.KEY_TYPE_OFFLINE;
            mOfflineKeySetId = keySetId;
            if (hasKeySetId && !isOffline) {
                errMsg = "unexpected keySetId";
            }
            if (!hasKeySetId && isOffline) {
                errMsg = "expecting keySetId";
            }

            if (errMsg != null) {
                mDrmCallbackError.set(true);
                Log.e(DRM_EVENT_CB_TAG, "onDrmPrepared " + errMsg);
            }

        }
    }

    private void preparePlayerAndDrm_asyncDrmSetupCallbacks(DrmEventCallback eventCallback,
            AtomicBoolean drmCallbackError) throws InterruptedException {
        mPlayer.setDrmEventCallback(mExecutor, eventCallback);
        Log.v(TAG, "preparePlayerAndDrm_asyncDrmSetupCallbacks: calling prepare()");
        mPlayer.prepare();

        if (mExpectError) {
            mOnErrorCalled.waitForSignal();
        } else {
            // Waiting till the player is prepared
            mOnPreparedCalled.waitForSignal();
        }

        // handle error (async) in main thread rather than callbacks
        assertTrue("preparePlayerAndDrm_asyncDrmSetupCallbacks: setupDrm",
                drmCallbackError.get() == mExpectError);

    }

    private void playLoadedModularDrmVideo_V4_offlineKey(final Uri file, final Integer width,
            final Integer height, int playTime) throws Exception {
        final float volume = 0.5f;

        mAudioOnly = (width == 0) || (height == 0);
        mCallStatus = MediaPlayer2.CALL_STATUS_NO_ERROR;

        SurfaceHolder surfaceHolder = mActivity.getSurfaceHolder();
        Log.v(TAG, "V4_offlineKey: setSurface " + surfaceHolder);
        mPlayer.setSurface(surfaceHolder.getSurface());
        surfaceHolder.setKeepScreenOn(true);

        final AtomicBoolean drmCallbackError = new AtomicBoolean(false);
        UriDataSourceDesc.Builder dsdBuilder = new UriDataSourceDesc.Builder();
        DataSourceDesc dsd = dsdBuilder.setDataSource(file).build();
        DataSourceDesc dsd2 = dsdBuilder.build();
        List<DataSourceDesc> dsds = Arrays.asList(dsd, dsd2);

        Log.v(TAG, "V4_offlineKey: set(Next)DataSource()");
        mPlayer.registerEventCallback(mExecutor, mECb);
        mPlayer.setDataSource(dsd);
        mPlayer.setNextDataSource(dsd2);
        mPlayer.setDrmEventCallback(mExecutor,
                new CtsDrmEventCallback(drmCallbackError, dsds, MediaDrm.KEY_TYPE_OFFLINE));

        Log.v(TAG, "V4_offlineKey: prepare()");
        mPlayer.prepare();
        mOnPreparedCalled.waitForSignal();

        Log.v(TAG, "V4_offlineKey: play()");
        mPlayer.play();
        if (!mAudioOnly) {
            mOnVideoSizeChangedCalled.waitForSignal();
        }
        mPlayer.setPlayerVolume(volume);

        // wait for completion
        if (playTime == 0) {
            Log.v(TAG, "V4_offlineKey: waiting for playback completion");
            mOnPlaybackCompleted.waitForCountedSignals(dsds.size());
            mOnPlaylistCompleted.waitForSignal();
        } else {
            Log.v(TAG, "V4_offlineKey: waiting while playing for " + playTime);
            mOnPlaybackCompleted.waitForSignal(playTime);
            mPlayer.skipToNext();
            mOnPlaylistCompleted.waitForSignal(playTime);
        }

        mPlayer.close();
        // TODO: release the offline key
    }

    // Converts a BMFF PSSH initData to a raw cenc initData
    protected byte[] makeCencPSSH(UUID uuid, byte[] bmffPsshData) {
        byte[] pssh_header = new byte[] { (byte) 'p', (byte) 's', (byte) 's', (byte) 'h' };
        byte[] pssh_version = new byte[] { 1, 0, 0, 0 };
        int boxSizeByteCount = 4;
        int uuidByteCount = 16;
        int dataSizeByteCount = 4;
        // Per "W3C cenc Initialization Data Format" document:
        // box size + 'pssh' + version + uuid + payload + size of data
        int boxSize = boxSizeByteCount + pssh_header.length + pssh_version.length
                + uuidByteCount + bmffPsshData.length + dataSizeByteCount;
        int dataSize = 0;

        // the default write is big-endian, i.e., network byte order
        ByteBuffer rawPssh = ByteBuffer.allocate(boxSize);
        rawPssh.putInt(boxSize);
        rawPssh.put(pssh_header);
        rawPssh.put(pssh_version);
        rawPssh.putLong(uuid.getMostSignificantBits());
        rawPssh.putLong(uuid.getLeastSignificantBits());
        rawPssh.put(bmffPsshData);
        rawPssh.putInt(dataSize);

        return rawPssh.array();
    }

    /*
     * Sets up the DRM for the first DRM scheme from the supported list.
     *
     * @param drmInfo DRM info of the source
     * @param prepareDrm whether prepareDrm should be called
     * @param synchronousNetworking whether the network operation of key request/response will
     *        be performed synchronously
     */
    private void setupDrm(DataSourceDesc dsd, DrmInfo drmInfo, boolean prepareDrm,
            boolean synchronousNetworking, int keyType) throws Exception {
        Log.d(TAG, "setupDrm: drmInfo: " + drmInfo + " prepareDrm: " + prepareDrm
                + " synchronousNetworking: " + synchronousNetworking);
        try {
            byte[] initData = null;
            String mime = null;
            String keyTypeStr = "Unexpected";
            final AtomicBoolean prepareDrmFailed = new AtomicBoolean(false);

            switch (keyType) {
                case MediaDrm.KEY_TYPE_STREAMING:
                case MediaDrm.KEY_TYPE_OFFLINE:
                    // DRM preparation
                    List<UUID> supportedSchemes = drmInfo.getSupportedSchemes();
                    if (supportedSchemes.isEmpty()) {
                        fail("setupDrm: No supportedSchemes");
                    }

                    // instead of supportedSchemes[0] in GTS
                    UUID drmScheme = CLEARKEY_SCHEME_UUID;
                    Log.d(TAG, "setupDrm: selected " + drmScheme);

                    if (prepareDrm) {
                        final Monitor drmPrepared = new Monitor();
                        mPlayer.setDrmEventCallback(
                                mExecutor, new MediaPlayer2.DrmEventCallback() {
                            @Override
                            public void onDrmPrepared(MediaPlayer2 mp, DataSourceDesc dsd2,
                                    int status, byte[] keySetId) {
                                if (status != MediaPlayer2.PREPARE_DRM_STATUS_SUCCESS
                                        || dsd != dsd2) {
                                    prepareDrmFailed.set(true);
                                }
                                drmPrepared.signal();
                            }
                        });
                        mPlayer.prepareDrm(dsd, drmScheme);
                        drmPrepared.waitForSignal();
                        if (prepareDrmFailed.get()) {
                            fail("setupDrm: prepareDrm failed");
                        }
                    }

                    byte[] psshData = drmInfo.getPssh().get(drmScheme);
                    // diverging from GTS
                    if (psshData == null) {
                        initData = mClearKeyPssh;
                        Log.d(TAG, "setupDrm: CLEARKEY scheme not found in PSSH."
                                + " Using default data.");
                    } else {
                        // Can skip conversion if ClearKey adds support for BMFF initData b/64863112
                        initData = makeCencPSSH(CLEARKEY_SCHEME_UUID, psshData);
                    }
                    Log.d(TAG, "setupDrm: initData[" + drmScheme + "]: "
                            + Arrays.toString(initData));

                    // diverging from GTS
                    mime = "cenc";

                    keyTypeStr = (keyType == MediaDrm.KEY_TYPE_STREAMING)
                            ? "KEY_TYPE_STREAMING" : "KEY_TYPE_OFFLINE";
                    break;

                case MediaDrm.KEY_TYPE_RELEASE:
                    if (mKeySetId == null) {
                        fail("setupDrm: KEY_TYPE_RELEASE requires a valid keySetId.");
                    }
                    keyTypeStr = "KEY_TYPE_RELEASE";
                    break;

                default:
                    fail("setupDrm: Unexpected keyType " + keyType);
            }

            final MediaDrm.KeyRequest request = mPlayer.getDrmKeyRequest(
                    dsd,
                    (keyType == MediaDrm.KEY_TYPE_RELEASE) ? mKeySetId : null,
                    initData,
                    mime,
                    keyType,
                    null /* optionalKeyRequestParameters */
                    );

            Log.d(TAG, "setupDrm: mPlayer.getDrmKeyRequest(" + keyTypeStr
                    + ") request -> " + request);

            // diverging from GTS
            byte[][] clearKeys = new byte[][] { CLEAR_KEY_CENC };
            byte[] response = createKeysResponse(request, clearKeys, keyType);

            // null is returned when the response is for a streaming or release request.
            byte[] keySetId = mPlayer.provideDrmKeyResponse(
                    dsd,
                    (keyType == MediaDrm.KEY_TYPE_RELEASE) ? mKeySetId : null,
                    response);
            Log.d(TAG, "setupDrm: provideDrmKeyResponse -> " + Arrays.toString(keySetId));
            // storing offline key for a later restore
            mKeySetId = (keyType == MediaDrm.KEY_TYPE_OFFLINE) ? keySetId : null;

        } catch (MediaPlayer2.NoDrmSchemeException e) {
            Log.d(TAG, "setupDrm: NoDrmSchemeException");
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            Log.d(TAG, "setupDrm: Exception " + e);
            e.printStackTrace();
            throw e;
        }
    } // setupDrm

    private void setupDrmRestore(DataSourceDesc dsd, DrmInfo drmInfo, boolean prepareDrm)
            throws Exception {
        Log.d(TAG, "setupDrmRestore: drmInfo: " + drmInfo + " prepareDrm: " + prepareDrm);
        try {
            if (prepareDrm) {
                final AtomicBoolean prepareDrmFailed = new AtomicBoolean(false);

                // DRM preparation
                List<UUID> supportedSchemes = drmInfo.getSupportedSchemes();
                if (supportedSchemes.isEmpty()) {
                    fail("setupDrmRestore: No supportedSchemes");
                }

                // instead of supportedSchemes[0] in GTS
                UUID drmScheme = CLEARKEY_SCHEME_UUID;
                Log.d(TAG, "setupDrmRestore: selected " + drmScheme);

                final Monitor drmPrepared = new Monitor();
                mPlayer.setDrmEventCallback(
                        mExecutor, new MediaPlayer2.DrmEventCallback() {
                    @Override
                    public void onDrmPrepared(
                            MediaPlayer2 mp, DataSourceDesc dsd2, int status, byte[] keySetId) {
                        if (status != MediaPlayer2.PREPARE_DRM_STATUS_SUCCESS
                                || dsd != dsd2) {
                            prepareDrmFailed.set(true);
                        }
                        drmPrepared.signal();
                    }
                });
                mPlayer.prepareDrm(dsd, drmScheme);
                drmPrepared.waitForSignal();
                if (prepareDrmFailed.get()) {
                    fail("setupDrmRestore: prepareDrm failed");
                }
            }

            if (mKeySetId == null) {
                fail("setupDrmRestore: Offline key has not been setup.");
            }

            mPlayer.restoreDrmKeys(dsd, mKeySetId);

        } catch (MediaPlayer2.NoDrmSchemeException e) {
            Log.v(TAG, "setupDrmRestore: NoDrmSchemeException");
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            Log.v(TAG, "setupDrmRestore: Exception " + e);
            e.printStackTrace();
            throw e;
        }
    } // setupDrmRestore

    //////////////////////////////////////////////////////////////////////////////////////////////
    // Diverging from GTS

    // Clearkey helpers

    /**
     * Convert a hex string into byte array.
     */
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Extracts key ids from the pssh blob returned by getDrmKeyRequest() and
     * places it in keyIds.
     * keyRequestBlob format (section 5.1.3.1):
     * https://dvcs.w3.org/hg/html-media/raw-file/default/encrypted-media/encrypted-media.html
     *
     * @return size of keyIds vector that contains the key ids, 0 for error
     */
    private int getKeyIds(byte[] keyRequestBlob, Vector<String> keyIds) {
        if (0 == keyRequestBlob.length || keyIds == null) {
            Log.e(TAG, "getKeyIds: Empty keyRequestBlob or null keyIds.");
            return 0;
        }

        String jsonLicenseRequest = new String(keyRequestBlob);
        keyIds.clear();

        try {
            JSONObject license = new JSONObject(jsonLicenseRequest);
            Log.v(TAG, "getKeyIds: license: " + license);
            final JSONArray ids = license.getJSONArray("kids");
            Log.v(TAG, "getKeyIds: ids: " + ids);
            for (int i = 0; i < ids.length(); ++i) {
                keyIds.add(ids.getString(i));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON license = " + jsonLicenseRequest);
            return 0;
        }
        return keyIds.size();
    }

    /**
     * Creates the JSON Web Key string.
     *
     * @return JSON Web Key string.
     */
    private String createJsonWebKeySet(Vector<String> keyIds, Vector<String> keys, int keyType) {
        String jwkSet = "{\"keys\":[";
        for (int i = 0; i < keyIds.size(); ++i) {
            String id = new String(keyIds.get(i).getBytes(Charset.forName("UTF-8")));
            String key = new String(keys.get(i).getBytes(Charset.forName("UTF-8")));

            jwkSet += "{\"kty\":\"oct\",\"kid\":\"" + id +
                    "\",\"k\":\"" + key + "\"}";
        }
        jwkSet += "], \"type\":";
        if (keyType == MediaDrm.KEY_TYPE_OFFLINE || keyType == MediaDrm.KEY_TYPE_RELEASE) {
            jwkSet += "\"persistent-license\" }";
        } else {
            jwkSet += "\"temporary\" }";
        }
        return jwkSet;
    }

    /**
     * Retrieves clear key ids from KeyRequest and creates the response in place.
     */
    private byte[] createKeysResponse(MediaDrm.KeyRequest keyRequest, byte[][] clearKeys,
            int keyType) {

        Vector<String> keyIds = new Vector<String>();
        if (0 == getKeyIds(keyRequest.getData(), keyIds)) {
            Log.e(TAG, "No key ids found in initData");
            return null;
        }

        if (clearKeys.length != keyIds.size()) {
            Log.e(TAG, "Mismatch number of key ids and keys: ids=" + keyIds.size() + ", keys="
                    + clearKeys.length);
            return null;
        }

        // Base64 encodes clearkeys. Keys are known to the application.
        Vector<String> keys = new Vector<String>();
        for (int i = 0; i < clearKeys.length; ++i) {
            String clearKey =
                    Base64.encodeToString(clearKeys[i], Base64.NO_PADDING | Base64.NO_WRAP);
            keys.add(clearKey);
        }

        String jwkSet = createJsonWebKeySet(keyIds, keys, keyType);
        byte[] jsonResponse = jwkSet.getBytes(Charset.forName("UTF-8"));

        return jsonResponse;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // Playback/download helpers

    private static class MediaDownloadManager {
        private static final String TAG = "MediaDownloadManager";

        private final Context mContext;
        private final DownloadManager mDownloadManager;

        MediaDownloadManager(Context context) {
            mContext = context;
            mDownloadManager =
                    (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        }

        public long downloadFileWithRetries(Uri uri, Uri file, long timeout, int retries)
                throws Exception {
            long id = -1;
            for (int i = 0; i < retries; i++) {
                try {
                    id = downloadFile(uri, file, timeout);
                    if (id != -1) {
                        break;
                    }
                } catch (Exception e) {
                    removeFile(id);
                    Log.w(TAG, "Download failed " + i + " times ");
                }
            }
            return id;
        }

        public long downloadFile(Uri uri, Uri file, long timeout) throws Exception {
            Log.i(TAG, "uri:" + uri + " file:" + file + " wait:" + timeout + " Secs");
            final DownloadReceiver receiver = new DownloadReceiver();
            long id = -1;
            try {
                IntentFilter intentFilter =
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                mContext.registerReceiver(receiver, intentFilter);

                Request request = new Request(uri);
                request.setDestinationUri(file);
                id = mDownloadManager.enqueue(request);
                Log.i(TAG, "enqueue:" + id);

                receiver.waitForDownloadComplete(timeout, id);
            } finally {
                mContext.unregisterReceiver(receiver);
            }
            return id;
        }

        public void removeFile(long id) {
            Log.i(TAG, "removeFile:" + id);
            mDownloadManager.remove(id);
        }

        public Uri getUriForDownloadedFile(long id) {
            return mDownloadManager.getUriForDownloadedFile(id);
        }

        private final class DownloadReceiver extends BroadcastReceiver {
            private HashSet<Long> mCompleteIds = new HashSet<>();

            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (mCompleteIds) {
                    if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                        mCompleteIds.add(
                                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1));
                        mCompleteIds.notifyAll();
                    }
                }
            }

            private boolean isCompleteLocked(long... ids) {
                for (long id : ids) {
                    if (!mCompleteIds.contains(id)) {
                        return false;
                    }
                }
                return true;
            }

            public void waitForDownloadComplete(long timeoutSecs, long... waitForIds)
                    throws InterruptedException {
                if (waitForIds.length == 0) {
                    throw new IllegalArgumentException("Missing IDs to wait for");
                }

                final long startTime = SystemClock.elapsedRealtime();
                do {
                    synchronized (mCompleteIds) {
                        mCompleteIds.wait(1000);
                        if (isCompleteLocked(waitForIds)) {
                            return;
                        }
                    }
                } while ((SystemClock.elapsedRealtime() - startTime) < timeoutSecs * 1000);

                throw new InterruptedException(
                        "Timeout waiting for IDs " + Arrays.toString(waitForIds)
                        + "; received " + mCompleteIds.toString()
                        + ".  Make sure you have WiFi or some other connectivity for this test.");
            }
        }

    }  // MediaDownloadManager

}
