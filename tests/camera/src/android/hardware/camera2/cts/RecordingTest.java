/*
 * Copyright (C) 2014 The Android Open Source Project Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package android.hardware.camera2.cts;

import static android.hardware.camera2.cts.CameraTestUtils.*;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;
import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus;

import static com.android.ex.camera2.blocking.BlockingSessionCallback.*;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.cts.helpers.StaticMetadata;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.HardwareBuffer;
import android.media.MediaMuxer;
import android.util.Size;
import android.hardware.camera2.cts.testcases.Camera2SurfaceViewTestCase;
import android.media.CamcorderProfile;
import android.media.EncoderProfiles;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import com.android.compatibility.common.util.MediaUtils;
import com.android.ex.camera2.blocking.BlockingSessionCallback;

import junit.framework.AssertionFailedError;

import org.junit.runners.Parameterized;
import org.junit.runner.RunWith;
import org.junit.Test;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

/**
 * CameraDevice video recording use case tests by using MediaRecorder and
 * MediaCodec.
 */
@LargeTest
@RunWith(Parameterized.class)
public class RecordingTest extends Camera2SurfaceViewTestCase {
    private static final String TAG = "RecordingTest";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);
    private static final boolean DEBUG_DUMP = Log.isLoggable(TAG, Log.DEBUG);
    private static final int RECORDING_DURATION_MS = 3000;
    private static final int PREVIEW_DURATION_MS = 3000;
    private static final float DURATION_MARGIN = 0.2f;
    private static final double FRAME_DURATION_ERROR_TOLERANCE_MS = 3.0;
    private static final float FRAMEDURATION_MARGIN = 0.2f;
    private static final float VID_SNPSHT_FRMDRP_RATE_TOLERANCE = 10.0f;
    private static final float FRMDRP_RATE_TOLERANCE = 5.0f;
    private static final int BIT_RATE_1080P = 16000000;
    private static final int BIT_RATE_MIN = 64000;
    private static final int BIT_RATE_MAX = 40000000;
    private static final int VIDEO_FRAME_RATE = 30;
    private static final int[] mCamcorderProfileList = {
            CamcorderProfile.QUALITY_HIGH,
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P,
            CamcorderProfile.QUALITY_CIF,
            CamcorderProfile.QUALITY_QCIF,
            CamcorderProfile.QUALITY_QVGA,
            CamcorderProfile.QUALITY_LOW,
    };

    private static final int[] mTenBitCodecProfileList = {
            HEVCProfileMain10,
            HEVCProfileMain10HDR10,
            HEVCProfileMain10HDR10Plus,
            //todo(b/215396395): DolbyVision
    };
    private static final int MAX_VIDEO_SNAPSHOT_IMAGES = 5;
    private static final int BURST_VIDEO_SNAPSHOT_NUM = 3;
    private static final int SLOWMO_SLOW_FACTOR = 4;
    private static final int MAX_NUM_FRAME_DROP_INTERVAL_ALLOWED = 4;
    private List<Size> mSupportedVideoSizes;
    private Surface mRecordingSurface;
    private Surface mPersistentSurface;
    private MediaRecorder mMediaRecorder;
    private String mOutMediaFileName;
    private int mVideoFrameRate;
    private Size mVideoSize;
    private long mRecordingStartTime;

    private Surface mIntermediateSurface;
    private ImageReader mIntermediateReader;
    private ImageWriter mIntermediateWriter;
    private ImageWriterQueuer mQueuer;
    private HandlerThread mIntermediateThread;
    private Handler mIntermediateHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private void doBasicRecording(boolean useVideoStab) throws Exception {
        doBasicRecording(useVideoStab, false);
    }

    private void doBasicRecording(boolean useVideoStab, boolean useIntermediateSurface)
            throws Exception {
        doBasicRecording(useVideoStab, useIntermediateSurface, false);
    }

    private void doBasicRecording(boolean useVideoStab, boolean useIntermediateSurface,
            boolean useEncoderProfiles) throws Exception {
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                Log.i(TAG, "Testing basic recording for camera " + mCameraIdsUnderTest[i]);
                StaticMetadata staticInfo = mAllStaticInfo.get(mCameraIdsUnderTest[i]);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support color outputs, skipping");
                    continue;
                }

                // External camera doesn't support CamcorderProfile recording
                if (staticInfo.isExternalCamera()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support CamcorderProfile, skipping");
                    continue;
                }

                if (!staticInfo.isVideoStabilizationSupported() && useVideoStab) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support video stabilization, skipping the stabilization"
                            + " test");
                    continue;
                }

                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(mCameraIdsUnderTest[i]);
                initSupportedVideoSize(mCameraIdsUnderTest[i]);

                basicRecordingTestByCamera(mCamcorderProfileList, useVideoStab,
                        useIntermediateSurface, useEncoderProfiles);
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * <p>
     * Test basic video stabilitzation camera recording.
     * </p>
     * <p>
     * This test covers the typical basic use case of camera recording with video
     * stabilization is enabled, if video stabilization is supported.
     * MediaRecorder is used to record the audio and video, CamcorderProfile is
     * used to configure the MediaRecorder. It goes through the pre-defined
     * CamcorderProfile list, test each profile configuration and validate the
     * recorded video. Preview is set to the video size.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testBasicVideoStabilizationRecording() throws Exception {
        doBasicRecording(/*useVideoStab*/true);
    }

    /**
     * <p>
     * Test basic camera recording.
     * </p>
     * <p>
     * This test covers the typical basic use case of camera recording.
     * MediaRecorder is used to record the audio and video, CamcorderProfile is
     * used to configure the MediaRecorder. It goes through the pre-defined
     * CamcorderProfile list, test each profile configuration and validate the
     * recorded video. Preview is set to the video size.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testBasicRecording() throws Exception {
        doBasicRecording(/*useVideoStab*/false);
    }

    /**
     * <p>
     * Test camera recording with intermediate surface.
     * </p>
     * <p>
     * This test is similar to testBasicRecording with a tweak where an intermediate
     * surface is setup between camera and MediaRecorder, giving application a chance
     * to decide whether to send a frame to recorder or not.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testIntermediateSurfaceRecording() throws Exception {
        doBasicRecording(/*useVideoStab*/false, /*useIntermediateSurface*/true);
    }

    /**
     * <p>
     * Test basic camera recording using encoder profiles.
     * </p>
     * <p>
     * This test covers the typical basic use case of camera recording.
     * MediaRecorder is used to record the audio and video,
     * EncoderProfiles are used to configure the MediaRecorder. It
     * goes through the pre-defined CamcorderProfile list, test each
     * encoder profile combination and validate the recorded video.
     * Preview is set to the video size.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testBasicEncoderProfilesRecording() throws Exception {
        doBasicRecording(/*useVideoStab*/false,  /*useIntermediateSurface*/false,
                /*useEncoderProfiles*/true);
    }

    /**
     * <p>
     * Test basic camera recording from a persistent input surface.
     * </p>
     * <p>
     * This test is similar to testBasicRecording except that MediaRecorder records
     * from a persistent input surface that's used across multiple recording sessions.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testRecordingFromPersistentSurface() throws Exception {
        if (!MediaUtils.checkCodecForDomain(true /* encoder */, "video")) {
            return; // skipped
        }
        mPersistentSurface = MediaCodec.createPersistentInputSurface();
        assertNotNull("Failed to create persistent input surface!", mPersistentSurface);

        try {
            doBasicRecording(/*useVideoStab*/false);
        } finally {
            mPersistentSurface.release();
            mPersistentSurface = null;
        }
    }

    /**
     * <p>
     * Test camera recording for all supported sizes by using MediaRecorder.
     * </p>
     * <p>
     * This test covers camera recording for all supported sizes by camera. MediaRecorder
     * is used to encode the video. Preview is set to the video size. Recorded videos are
     * validated according to the recording configuration.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testSupportedVideoSizes() throws Exception {
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                Log.i(TAG, "Testing supported video size recording for camera " + mCameraIdsUnderTest[i]);
                StaticMetadata staticInfo = mAllStaticInfo.get(mCameraIdsUnderTest[i]);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (staticInfo.isExternalCamera()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support CamcorderProfile, skipping");
                    continue;
                }
                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(mCameraIdsUnderTest[i]);

                initSupportedVideoSize(mCameraIdsUnderTest[i]);

                recordingSizeTestByCamera();
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * Test different start/stop orders of Camera and Recorder.
     *
     * <p>The recording should be working fine for any kind of start/stop orders.</p>
     */
    @Test
    public void testCameraRecorderOrdering() {
        // TODO: need implement
    }

    /**
     * <p>
     * Test camera recording for all supported sizes by using MediaCodec.
     * </p>
     * <p>
     * This test covers video only recording for all supported sizes (camera and
     * encoder). MediaCodec is used to encode the video. The recorded videos are
     * validated according to the recording configuration.
     * </p>
     */
    @Test
    public void testMediaCodecRecording() throws Exception {
        // TODO. Need implement.
    }

    private class MediaCodecListener extends MediaCodec.Callback {
        private final MediaMuxer mMediaMuxer;
        private final Object mCondition;
        private int mTrackId = -1;
        private boolean mEndOfStream = false;

        private MediaCodecListener(MediaMuxer mediaMuxer, Object condition) {
            mMediaMuxer = mediaMuxer;
            mCondition = condition;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            fail("Unexpected input buffer available callback!");
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index,
                MediaCodec.BufferInfo info) {
            synchronized (mCondition) {
                if (mTrackId < 0) {
                    return;
                }
                mMediaMuxer.writeSampleData(mTrackId, codec.getOutputBuffer(index), info);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) ==
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                    mEndOfStream = true;
                    mCondition.notifyAll();
                }

                if (!mEndOfStream) {
                    codec.releaseOutputBuffer(index, false);
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            fail("Codec error: " + e.getDiagnosticInfo());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            synchronized (mCondition) {
                mTrackId = mMediaMuxer.addTrack(format);
                mMediaMuxer.start();
            }
        }
    }

    private static long getDynamicRangeProfile(int codecProfile, StaticMetadata staticMeta) {
        if (!staticMeta.isCapabilitySupported(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)) {
            return DynamicRangeProfiles.PUBLIC_MAX;
        }

        DynamicRangeProfiles profiles = staticMeta.getCharacteristics().get(
                CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
        Set<Long> availableProfiles = profiles.getSupportedProfiles();
        switch (codecProfile) {
            case HEVCProfileMain10:
                return availableProfiles.contains(DynamicRangeProfiles.HLG10) ?
                        DynamicRangeProfiles.HLG10 : DynamicRangeProfiles.PUBLIC_MAX;
            case HEVCProfileMain10HDR10:
                return availableProfiles.contains(DynamicRangeProfiles.HDR10) ?
                        DynamicRangeProfiles.HDR10 : DynamicRangeProfiles.PUBLIC_MAX;
            case HEVCProfileMain10HDR10Plus:
                return availableProfiles.contains(DynamicRangeProfiles.HDR10_PLUS) ?
                        DynamicRangeProfiles.HDR10_PLUS : DynamicRangeProfiles.PUBLIC_MAX;
            //todo(b/215396395): DolbyVision
            default:
                return DynamicRangeProfiles.PUBLIC_MAX;
        }
    }

    private static int getTransferFunction(int codecProfile) {
        switch (codecProfile) {
            case HEVCProfileMain10:
                return MediaFormat.COLOR_TRANSFER_HLG;
            case HEVCProfileMain10HDR10:
            case HEVCProfileMain10HDR10Plus:
            //todo(b/215396395): DolbyVision
                return MediaFormat.COLOR_TRANSFER_ST2084;
            default:
                return MediaFormat.COLOR_TRANSFER_SDR_VIDEO;
        }
    }

    /**
     * <p>
     * Test basic camera 10-bit recording.
     * </p>
     * <p>
     * This test covers the typical basic use case of camera recording.
     * MediaCodec is used to record 10-bit video, CamcorderProfile and codec profiles
     * are used to configure the MediaCodec. It goes through the pre-defined
     * CamcorderProfile and 10-bit codec profile lists, tests each configuration and
     * validates the recorded video. Preview is set to the video size.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testBasic10BitRecording() throws Exception {
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                Log.i(TAG, "Testing 10-bit recording " + mCameraIdsUnderTest[i]);
                StaticMetadata staticInfo = mAllStaticInfo.get(mCameraIdsUnderTest[i]);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (staticInfo.isExternalCamera()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support CamcorderProfile, skipping");
                    continue;
                }

                int cameraId;
                try {
                    cameraId = Integer.valueOf(mCameraIdsUnderTest[i]);
                } catch (NumberFormatException e) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] + " cannot be parsed"
                            + " to an integer camera id, skipping");
                    continue;
                }

                for (int camcorderProfile : mCamcorderProfileList) {
                    if (!CamcorderProfile.hasProfile(cameraId, camcorderProfile)) {
                        continue;
                    }

                    for (int codecProfile : mTenBitCodecProfileList) {
                        CamcorderProfile profile = CamcorderProfile.get(cameraId, camcorderProfile);

                        Size videoSize = new Size(profile.videoFrameWidth,
                                profile.videoFrameHeight);
                        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
                        MediaFormat format = MediaFormat.createVideoFormat(
                                MediaFormat.MIMETYPE_VIDEO_HEVC, videoSize.getWidth(),
                                videoSize.getHeight());
                        format.setInteger(MediaFormat.KEY_PROFILE, codecProfile);
                        format.setInteger(MediaFormat.KEY_BIT_RATE, profile.videoBitRate);
                        format.setInteger(MediaFormat.KEY_FRAME_RATE, profile.videoFrameRate);
                        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                                CodecCapabilities.COLOR_FormatSurface);
                        format.setInteger(MediaFormat.KEY_COLOR_STANDARD,
                                MediaFormat.COLOR_STANDARD_BT2020);
                        format.setInteger(MediaFormat.KEY_COLOR_RANGE,
                                MediaFormat.COLOR_RANGE_FULL);
                        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER,
                                getTransferFunction(codecProfile));
                        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                        String codecName = list.findEncoderForFormat(format);
                        if (codecName == null) {
                            continue;
                        }

                        long dynamicRangeProfile = getDynamicRangeProfile(codecProfile, staticInfo);
                        if (dynamicRangeProfile == DynamicRangeProfiles.PUBLIC_MAX) {
                            continue;
                        }

                        MediaCodec mediaCodec = null;
                        mOutMediaFileName = mDebugFileNameBase + "/test_video.mp4";
                        MediaMuxer muxer = new MediaMuxer(mOutMediaFileName,
                                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                        SimpleCaptureCallback captureCallback = new SimpleCaptureCallback();
                        try {
                            mediaCodec = MediaCodec.createByCodecName(codecName);
                            assertNotNull(mediaCodec);

                            openDevice(mCameraIdsUnderTest[i]);

                            mediaCodec.configure(format, null, null,
                                    MediaCodec.CONFIGURE_FLAG_ENCODE);
                            Object condition = new Object();
                            mediaCodec.setCallback(new MediaCodecListener(muxer, condition),
                                    mHandler);

                            updatePreviewSurfaceWithVideo(videoSize, profile.videoFrameRate);

                            Surface recordingSurface = mediaCodec.createInputSurface();
                            assertNotNull(recordingSurface);

                            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
                            assertTrue("Both preview and recording surfaces should be valid",
                                    mPreviewSurface.isValid() && recordingSurface.isValid());

                            outputSurfaces.add(mPreviewSurface);
                            outputSurfaces.add(recordingSurface);

                            CameraCaptureSession.StateCallback mockCallback = mock(
                                    CameraCaptureSession.StateCallback.class);
                            BlockingSessionCallback sessionListener =
                                    new BlockingSessionCallback(mockCallback);

                            CaptureRequest.Builder recordingRequestBuilder =
                                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                            recordingRequestBuilder.addTarget(recordingSurface);
                            recordingRequestBuilder.addTarget(mPreviewSurface);
                            recordingRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                    new Range<>(profile.videoFrameRate, profile.videoFrameRate));
                            CaptureRequest recordingRequest = recordingRequestBuilder.build();

                            List<OutputConfiguration> outConfigurations =
                                    new ArrayList<>(outputSurfaces.size());
                            for (Surface s : outputSurfaces) {
                                OutputConfiguration config = new OutputConfiguration(s);
                                config.setDynamicRangeProfile(dynamicRangeProfile);
                                outConfigurations.add(config);
                            }

                            SessionConfiguration sessionConfig = new SessionConfiguration(
                                    SessionConfiguration.SESSION_REGULAR, outConfigurations,
                                    new HandlerExecutor(mHandler), sessionListener);
                            mCamera.createCaptureSession(sessionConfig);

                            CameraCaptureSession session = sessionListener.waitAndGetSession(
                                    SESSION_CONFIGURE_TIMEOUT_MS);

                            mediaCodec.start();
                            session.setRepeatingRequest(recordingRequest, captureCallback,
                                    mHandler);

                            SystemClock.sleep(RECORDING_DURATION_MS);

                            session.stopRepeating();
                            session.close();
                            verify(mockCallback, timeout(SESSION_CLOSE_TIMEOUT_MS).
                                    times(1)).onClosed(eq(session));

                            mediaCodec.signalEndOfInputStream();
                            synchronized (condition) {
                                condition.wait(SESSION_CLOSE_TIMEOUT_MS);
                            }

                            mediaCodec.stop();
                            muxer.stop();

                        } finally {
                            if (mediaCodec != null) {
                                mediaCodec.release();
                            }
                            if (muxer != null) {
                                muxer.release();
                            }
                        }

                        // Validation.
                        float frameDurationMinMs = 1000.0f / profile.videoFrameRate;
                        float durationMinMs =
                                captureCallback.getTotalNumFrames() * frameDurationMinMs;
                        float durationMaxMs = durationMinMs;
                        float frameDurationMaxMs = 0.f;

                        validateRecording(videoSize, durationMinMs, durationMaxMs,
                                frameDurationMinMs, frameDurationMaxMs,
                                FRMDRP_RATE_TOLERANCE);
                    }
                }
            } finally {
                closeDevice();
            }
        }
    }

    /**
     * <p>
     * Test video snapshot for each camera.
     * </p>
     * <p>
     * This test covers video snapshot typical use case. The MediaRecorder is used to record the
     * video for each available video size. The largest still capture size is selected to
     * capture the JPEG image. The still capture images are validated according to the capture
     * configuration. The timestamp of capture result before and after video snapshot is also
     * checked to make sure no frame drop caused by video snapshot.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testVideoSnapshot() throws Exception {
        videoSnapshotHelper(/*burstTest*/false);
    }

    /**
     * <p>
     * Test burst video snapshot for each camera.
     * </p>
     * <p>
     * This test covers burst video snapshot capture. The MediaRecorder is used to record the
     * video for each available video size. The largest still capture size is selected to
     * capture the JPEG image. {@value #BURST_VIDEO_SNAPSHOT_NUM} video snapshot requests will be
     * sent during the test. The still capture images are validated according to the capture
     * configuration.
     * </p>
     */
    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testBurstVideoSnapshot() throws Exception {
        videoSnapshotHelper(/*burstTest*/true);
    }

    /**
     * Test timelapse recording, where capture rate is slower than video (playback) frame rate.
     */
    @Test
    public void testTimelapseRecording() throws Exception {
        // TODO. Need implement.
    }

    @Test
    public void testSlowMotionRecording() throws Exception {
        slowMotionRecording();
    }

    @Test(timeout=60*60*1000) // timeout = 60 mins for long running tests
    public void testConstrainedHighSpeedRecording() throws Exception {
        constrainedHighSpeedRecording();
    }

    @Test
    public void testAbandonedHighSpeedRequest() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing bad suface for createHighSpeedRequestList for camera " + id);
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (!staticInfo.isConstrainedHighSpeedVideoSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support constrained high speed video, skipping");
                    continue;
                }

                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(id);

                StreamConfigurationMap config =
                        mStaticInfo.getValueFromKeyNonNull(
                                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] highSpeedVideoSizes = config.getHighSpeedVideoSizes();
                Size size = highSpeedVideoSizes[0];
                Range<Integer> fpsRange = getHighestHighSpeedFixedFpsRangeForSize(config, size);
                mCollector.expectNotNull("Unable to find the fixed frame rate fps range for " +
                        "size " + size, fpsRange);
                if (fpsRange == null) {
                    continue;
                }

                int captureRate = fpsRange.getLower();
                int videoFramerate = captureRate / SLOWMO_SLOW_FACTOR;
                // Skip the test if the highest recording FPS supported by CamcorderProfile
                if (fpsRange.getUpper() > getFpsFromHighSpeedProfileForSize(size)) {
                    Log.w(TAG, "high speed recording " + size + "@" + captureRate + "fps"
                            + " is not supported by CamcorderProfile");
                    continue;
                }

                mOutMediaFileName = mDebugFileNameBase + "/test_video.mp4";
                prepareRecording(size, videoFramerate, captureRate);
                updatePreviewSurfaceWithVideo(size, captureRate);

                List<Surface> outputSurfaces = new ArrayList<Surface>(2);
                assertTrue("Both preview and recording surfaces should be valid",
                        mPreviewSurface.isValid() && mRecordingSurface.isValid());

                outputSurfaces.add(mPreviewSurface);
                outputSurfaces.add(mRecordingSurface);

                mSessionListener = new BlockingSessionCallback();
                mSession = configureCameraSession(mCamera, outputSurfaces, /*highSpeed*/true,
                        mSessionListener, mHandler);

                CaptureRequest.Builder requestBuilder =
                        mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                requestBuilder.addTarget(mPreviewSurface);
                requestBuilder.addTarget(mRecordingSurface);

                // 1. Test abandoned MediaRecorder
                releaseRecorder();
                try {
                    List<CaptureRequest> slowMoRequests =
                            ((CameraConstrainedHighSpeedCaptureSession) mSession).
                            createHighSpeedRequestList(requestBuilder.build());
                    fail("Create high speed request on abandoned surface must fail!");
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, "Release recording surface test passed");
                    // expected
                }

                // 2. Test abandoned preview surface
                mMediaRecorder = new MediaRecorder();
                SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
                Surface previewSurface = new Surface(preview);
                preview.setDefaultBufferSize(size.getWidth(), size.getHeight());

                outputSurfaces = new ArrayList<Surface>();
                outputSurfaces.add(previewSurface);

                prepareRecording(size, videoFramerate, captureRate);
                updatePreviewSurfaceWithVideo(size, captureRate);

                mSession = configureCameraSession(mCamera, outputSurfaces, /*highSpeed*/true,
                        mSessionListener, mHandler);

                requestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                requestBuilder.addTarget(previewSurface);

                // Abandon preview surface.
                previewSurface.release();

                try {
                    List<CaptureRequest> slowMoRequests =
                            ((CameraConstrainedHighSpeedCaptureSession) mSession).
                            createHighSpeedRequestList(requestBuilder.build());
                    fail("Create high speed request on abandoned preview surface must fail!");
                } catch (IllegalArgumentException e) {
                    Log.i(TAG, "Release preview surface test passed");
                    // expected
                }
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * <p>
     * Test recording framerate accuracy when switching from low FPS to high FPS.
     * </p>
     * <p>
     * This test first record a video with profile of lowest framerate then record a video with
     * profile of highest framerate. Make sure that the video framerate are still accurate.
     * </p>
     */
    @Test
    public void testRecordingFramerateLowToHigh() throws Exception {
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                Log.i(TAG, "Testing recording framerate low to high for camera " + mCameraIdsUnderTest[i]);
                StaticMetadata staticInfo = mAllStaticInfo.get(mCameraIdsUnderTest[i]);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (staticInfo.isExternalCamera()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support CamcorderProfile, skipping");
                    continue;
                }
                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(mCameraIdsUnderTest[i]);

                initSupportedVideoSize(mCameraIdsUnderTest[i]);

                int minFpsProfileId = -1, minFps = 1000;
                int maxFpsProfileId = -1, maxFps = 0;
                int cameraId = Integer.valueOf(mCamera.getId());

                for (int profileId : mCamcorderProfileList) {
                    if (!CamcorderProfile.hasProfile(cameraId, profileId)) {
                        continue;
                    }
                    CamcorderProfile profile = CamcorderProfile.get(cameraId, profileId);
                    if (profile.videoFrameRate < minFps) {
                        minFpsProfileId = profileId;
                        minFps = profile.videoFrameRate;
                    }
                    if (profile.videoFrameRate > maxFps) {
                        maxFpsProfileId = profileId;
                        maxFps = profile.videoFrameRate;
                    }
                }

                int camcorderProfileList[] = new int[] {minFpsProfileId, maxFpsProfileId};
                basicRecordingTestByCamera(camcorderProfileList, /*useVideoStab*/false);
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * <p>
     * Test preview and video surfaces sharing the same camera stream.
     * </p>
     */
    @Test
    public void testVideoPreviewSurfaceSharing() throws Exception {
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                StaticMetadata staticInfo = mAllStaticInfo.get(mCameraIdsUnderTest[i]);
                if (staticInfo.isHardwareLevelLegacy()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] + " is legacy, skipping");
                    continue;
                }
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (staticInfo.isExternalCamera()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support CamcorderProfile, skipping");
                    continue;
                }
                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(mCameraIdsUnderTest[i]);

                initSupportedVideoSize(mCameraIdsUnderTest[i]);

                videoPreviewSurfaceSharingTestByCamera();
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * <p>
     * Test recording with same recording surface and different preview surfaces.
     * </p>
     * <p>
     * This test maintains persistent video surface while changing preview surface.
     * This exercises format/dataspace override behavior of the camera device.
     * </p>
     */
    @Test
    public void testRecordingWithDifferentPreviewSizes() throws Exception {
        if (!MediaUtils.checkCodecForDomain(true /* encoder */, "video")) {
            return; // skipped
        }
        mPersistentSurface = MediaCodec.createPersistentInputSurface();
        assertNotNull("Failed to create persistent input surface!", mPersistentSurface);

        try {
            doRecordingWithDifferentPreviewSizes();
        } finally {
            mPersistentSurface.release();
            mPersistentSurface = null;
        }
    }

    public void doRecordingWithDifferentPreviewSizes() throws Exception {
        for (int i = 0; i < mCameraIdsUnderTest.length; i++) {
            try {
                Log.i(TAG, "Testing recording with different preview sizes for camera " +
                        mCameraIdsUnderTest[i]);
                StaticMetadata staticInfo = mAllStaticInfo.get(mCameraIdsUnderTest[i]);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (staticInfo.isExternalCamera()) {
                    Log.i(TAG, "Camera " + mCameraIdsUnderTest[i] +
                            " does not support CamcorderProfile, skipping");
                    continue;
                }
                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(mCameraIdsUnderTest[i]);

                initSupportedVideoSize(mCameraIdsUnderTest[i]);

                Size maxPreviewSize = mOrderedPreviewSizes.get(0);
                List<Range<Integer> > fpsRanges = Arrays.asList(
                        mStaticInfo.getAeAvailableTargetFpsRangesChecked());
                int cameraId = Integer.valueOf(mCamera.getId());
                int maxVideoFrameRate = -1;
                for (int profileId : mCamcorderProfileList) {
                    if (!CamcorderProfile.hasProfile(cameraId, profileId)) {
                        continue;
                    }
                    CamcorderProfile profile = CamcorderProfile.get(cameraId, profileId);

                    Size videoSz = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
                    Range<Integer> fpsRange = new Range(
                            profile.videoFrameRate, profile.videoFrameRate);
                    if (maxVideoFrameRate < profile.videoFrameRate) {
                        maxVideoFrameRate = profile.videoFrameRate;
                    }

                    if (allowedUnsupported(cameraId, profileId)) {
                        continue;
                    }

                    if (mStaticInfo.isHardwareLevelLegacy() &&
                            (videoSz.getWidth() > maxPreviewSize.getWidth() ||
                             videoSz.getHeight() > maxPreviewSize.getHeight())) {
                        // Skip. Legacy mode can only do recording up to max preview size
                        continue;
                    }
                    assertTrue("Video size " + videoSz.toString() + " for profile ID " + profileId +
                                    " must be one of the camera device supported video size!",
                                    mSupportedVideoSizes.contains(videoSz));
                    assertTrue("Frame rate range " + fpsRange + " (for profile ID " + profileId +
                            ") must be one of the camera device available FPS range!",
                            fpsRanges.contains(fpsRange));

                    // Configure preview and recording surfaces.
                    mOutMediaFileName = mDebugFileNameBase + "/test_video_surface_reconfig.mp4";

                    // prepare preview surface by using video size.
                    List<Size> previewSizes = getPreviewSizesForVideo(videoSz,
                            profile.videoFrameRate);
                    if (previewSizes.size() <= 1) {
                        continue;
                    }

                    // 1. Do video recording using largest compatbile preview sizes
                    prepareRecordingWithProfile(profile);
                    updatePreviewSurface(previewSizes.get(0));
                    SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
                    startRecording(
                            /* useMediaRecorder */true, resultListener,
                            /*useVideoStab*/false, fpsRange, false);
                    SystemClock.sleep(RECORDING_DURATION_MS);
                    stopRecording(/* useMediaRecorder */true, /* useIntermediateSurface */false,
                            /* stopStreaming */false);

                    // 2. Reconfigure with the same recording surface, but switch to a smaller
                    // preview size.
                    prepareRecordingWithProfile(profile);
                    updatePreviewSurface(previewSizes.get(1));
                    SimpleCaptureCallback resultListener2 = new SimpleCaptureCallback();
                    startRecording(
                            /* useMediaRecorder */true, resultListener2,
                            /*useVideoStab*/false, fpsRange, false);
                    SystemClock.sleep(RECORDING_DURATION_MS);
                    stopRecording(/* useMediaRecorder */true);
                    break;
                }
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * Test camera preview and video surface sharing for maximum supported size.
     */
    private void videoPreviewSurfaceSharingTestByCamera() throws Exception {
        for (Size sz : mOrderedPreviewSizes) {
            if (!isSupported(sz, VIDEO_FRAME_RATE, VIDEO_FRAME_RATE)) {
                continue;
            }

            if (VERBOSE) {
                Log.v(TAG, "Testing camera recording with video size " + sz.toString());
            }

            // Configure preview and recording surfaces.
            mOutMediaFileName = mDebugFileNameBase + "/test_video_share.mp4";
            if (DEBUG_DUMP) {
                mOutMediaFileName = mDebugFileNameBase + "/test_video_share_" + mCamera.getId() +
                    "_" + sz.toString() + ".mp4";
            }

            // Allow external camera to use variable fps range
            Range<Integer> fpsRange = null;
            if (mStaticInfo.isExternalCamera()) {
                Range<Integer>[] availableFpsRange =
                        mStaticInfo.getAeAvailableTargetFpsRangesChecked();

                boolean foundRange = false;
                int minFps = 0;
                for (int i = 0; i < availableFpsRange.length; i += 1) {
                    if (minFps < availableFpsRange[i].getLower()
                            && VIDEO_FRAME_RATE == availableFpsRange[i].getUpper()) {
                        minFps = availableFpsRange[i].getLower();
                        foundRange = true;
                    }
                }
                assertTrue("Cannot find FPS range for maxFps " + VIDEO_FRAME_RATE, foundRange);
                fpsRange = Range.create(minFps, VIDEO_FRAME_RATE);
            }

            // Use AVC and AAC a/v compression format.
            prepareRecording(sz, VIDEO_FRAME_RATE, VIDEO_FRAME_RATE);

            // prepare preview surface by using video size.
            updatePreviewSurfaceWithVideo(sz, VIDEO_FRAME_RATE);

            // Start recording
            SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
            if (!startSharedRecording(/* useMediaRecorder */true, resultListener,
                    /*useVideoStab*/false, fpsRange)) {
                mMediaRecorder.reset();
                continue;
            }

            // Record certain duration.
            SystemClock.sleep(RECORDING_DURATION_MS);

            // Stop recording and preview
            stopRecording(/* useMediaRecorder */true);
            // Convert number of frames camera produced into the duration in unit of ms.
            float frameDurationMinMs = 1000.0f / VIDEO_FRAME_RATE;
            float durationMinMs = resultListener.getTotalNumFrames() * frameDurationMinMs;
            float durationMaxMs = durationMinMs;
            float frameDurationMaxMs = 0.f;
            if (fpsRange != null) {
                frameDurationMaxMs = 1000.0f / fpsRange.getLower();
                durationMaxMs = resultListener.getTotalNumFrames() * frameDurationMaxMs;
            }

            // Validation.
            validateRecording(sz, durationMinMs, durationMaxMs,
                    frameDurationMinMs, frameDurationMaxMs,
                    FRMDRP_RATE_TOLERANCE);

            break;
        }
    }

    /**
     * Test slow motion recording where capture rate (camera output) is different with
     * video (playback) frame rate for each camera if high speed recording is supported
     * by both camera and encoder.
     *
     * <p>
     * Normal recording use cases make the capture rate (camera output frame
     * rate) the same as the video (playback) frame rate. This guarantees that
     * the motions in the scene play at the normal speed. If the capture rate is
     * faster than video frame rate, for a given time duration, more number of
     * frames are captured than it can be played in the same time duration. This
     * generates "slow motion" effect during playback.
     * </p>
     */
    private void slowMotionRecording() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing slow motion recording for camera " + id);
                StaticMetadata staticInfo = mAllStaticInfo.get(id);
                if (!staticInfo.isColorOutputSupported()) {
                    Log.i(TAG, "Camera " + id +
                            " does not support color outputs, skipping");
                    continue;
                }
                if (!staticInfo.isHighSpeedVideoSupported()) {
                    continue;
                }

                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(id);

                StreamConfigurationMap config =
                        mStaticInfo.getValueFromKeyNonNull(
                                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] highSpeedVideoSizes = config.getHighSpeedVideoSizes();
                for (Size size : highSpeedVideoSizes) {
                    Range<Integer> fpsRange = getHighestHighSpeedFixedFpsRangeForSize(config, size);
                    mCollector.expectNotNull("Unable to find the fixed frame rate fps range for " +
                            "size " + size, fpsRange);
                    if (fpsRange == null) {
                        continue;
                    }

                    int captureRate = fpsRange.getLower();
                    int videoFramerate = captureRate / SLOWMO_SLOW_FACTOR;
                    // Skip the test if the highest recording FPS supported by CamcorderProfile
                    if (fpsRange.getUpper() > getFpsFromHighSpeedProfileForSize(size)) {
                        Log.w(TAG, "high speed recording " + size + "@" + captureRate + "fps"
                                + " is not supported by CamcorderProfile");
                        continue;
                    }

                    mOutMediaFileName = mDebugFileNameBase + "/test_slowMo_video.mp4";
                    if (DEBUG_DUMP) {
                        mOutMediaFileName = mDebugFileNameBase + "/test_slowMo_video_" + id + "_"
                                + size.toString() + ".mp4";
                    }

                    prepareRecording(size, videoFramerate, captureRate);

                    // prepare preview surface by using video size.
                    updatePreviewSurfaceWithVideo(size, captureRate);

                    // Start recording
                    SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
                    startSlowMotionRecording(/*useMediaRecorder*/true, videoFramerate, captureRate,
                            fpsRange, resultListener, /*useHighSpeedSession*/false);

                    // Record certain duration.
                    SystemClock.sleep(RECORDING_DURATION_MS);

                    // Stop recording and preview
                    stopRecording(/*useMediaRecorder*/true);
                    // Convert number of frames camera produced into the duration in unit of ms.
                    float frameDurationMs = 1000.0f / videoFramerate;
                    float durationMs = resultListener.getTotalNumFrames() * frameDurationMs;

                    // Validation.
                    validateRecording(size, durationMs, frameDurationMs, FRMDRP_RATE_TOLERANCE);
                }

            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    private void constrainedHighSpeedRecording() throws Exception {
        for (String id : mCameraIdsUnderTest) {
            try {
                Log.i(TAG, "Testing constrained high speed recording for camera " + id);

                if (!mAllStaticInfo.get(id).isConstrainedHighSpeedVideoSupported()) {
                    Log.i(TAG, "Camera " + id + " doesn't support high speed recording, skipping.");
                    continue;
                }

                // Re-use the MediaRecorder object for the same camera device.
                mMediaRecorder = new MediaRecorder();
                openDevice(id);

                StreamConfigurationMap config =
                        mStaticInfo.getValueFromKeyNonNull(
                                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] highSpeedVideoSizes = config.getHighSpeedVideoSizes();
                Log.v(TAG, "highSpeedVideoSizes:" + Arrays.toString(highSpeedVideoSizes));
                int previewFrameRate = Integer.MAX_VALUE;
                for (Size size : highSpeedVideoSizes) {
                    List<Range<Integer>> fixedFpsRanges =
                            getHighSpeedFixedFpsRangeForSize(config, size);
                    Range<Integer>[] highSpeedFpsRangesForSize =
                            config.getHighSpeedVideoFpsRangesFor(size);

                    Log.v(TAG, "highSpeedFpsRangesForSize for size - " + size + " : " +
                            Arrays.toString(highSpeedFpsRangesForSize));
                    // Map to store  max_fps and preview fps for each video size
                    HashMap<Integer, Integer> previewRateMap = new HashMap();
                    for (Range<Integer> r : highSpeedFpsRangesForSize ) {
                        if (r.getLower() != r.getUpper()) {
                            if (previewRateMap.containsKey(r.getUpper())) {
                                Log.w(TAG, "previewFps for max_fps already exists.");
                            } else {
                                previewRateMap.put(r.getUpper(), r.getLower());
                            }
                        }
                    }

                    mCollector.expectTrue("Unable to find the fixed frame rate fps range for " +
                            "size " + size, fixedFpsRanges.size() > 0);
                    // Test recording for each FPS range
                    for (Range<Integer> fpsRange : fixedFpsRanges) {
                        int captureRate = fpsRange.getLower();
                        previewFrameRate = previewRateMap.get(captureRate);
                        Log.v(TAG, "previewFrameRate: " + previewFrameRate + " captureRate: " +
                                captureRate);

                        Range<Integer> previewfpsRange =
                                new Range<Integer>(previewFrameRate, captureRate);

                        // Skip the test if the highest recording FPS supported by CamcorderProfile
                        if (fpsRange.getUpper() > getFpsFromHighSpeedProfileForSize(size)) {
                            Log.w(TAG, "high speed recording " + size + "@" + captureRate + "fps"
                                    + " is not supported by CamcorderProfile");
                            continue;
                        }

                        SimpleCaptureCallback previewResultListener = new SimpleCaptureCallback();

                        // prepare preview surface by using video size.
                        updatePreviewSurfaceWithVideo(size, captureRate);

                        startConstrainedPreview(previewfpsRange, previewResultListener);

                        mOutMediaFileName = mDebugFileNameBase + "/test_cslowMo_video_" +
                            captureRate + "fps_" + id + "_" + size.toString() + ".mp4";

                        // b/239101664 It appears that video frame rates higher than 30 fps may not
                        // trigger slow motion recording consistently.
                        int videoFrameRate = previewFrameRate > VIDEO_FRAME_RATE ?
                                VIDEO_FRAME_RATE : previewFrameRate;
                        Log.v(TAG, "videoFrameRate:" + videoFrameRate);

                        int cameraId = Integer.valueOf(mCamera.getId());
                        int videoEncoder = MediaRecorder.VideoEncoder.H264;
                        for (int profileId : mCamcorderProfileList) {
                            if (CamcorderProfile.hasProfile(cameraId, profileId)) {
                                CamcorderProfile profile =
                                        CamcorderProfile.get(cameraId, profileId);

                                if (profile.videoFrameHeight == size.getHeight() &&
                                        profile.videoFrameWidth == size.getWidth() &&
                                        profile.videoFrameRate == videoFrameRate) {
                                    videoEncoder = profile.videoCodec;
                                    // Since mCamcorderProfileList is a list representing different
                                    // resolutions, we can break when a profile with the same
                                    // dimensions as size is found
                                    break;
                                }
                            }
                        }

                        prepareRecording(size, videoFrameRate, captureRate, videoEncoder);

                        SystemClock.sleep(PREVIEW_DURATION_MS);

                        stopCameraStreaming();

                        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
                        // Start recording
                        startSlowMotionRecording(/*useMediaRecorder*/true, videoFrameRate,
                                captureRate, fpsRange, resultListener,
                                /*useHighSpeedSession*/true);

                        // Record certain duration.
                        SystemClock.sleep(RECORDING_DURATION_MS);

                        // Stop recording and preview
                        stopRecording(/*useMediaRecorder*/true);

                        startConstrainedPreview(previewfpsRange, previewResultListener);

                        // Convert number of frames camera produced into the duration in unit of ms.
                        float frameDurationMs = 1000.0f / videoFrameRate;
                        float durationMs = resultListener.getTotalNumFrames() * frameDurationMs;

                        // Validation.
                        validateRecording(size, durationMs, frameDurationMs, FRMDRP_RATE_TOLERANCE);

                        SystemClock.sleep(PREVIEW_DURATION_MS);

                        stopCameraStreaming();
                    }
                }
            } catch (NumberFormatException e) {
                fail("Cannot convert cameraId " + mCamera.getId() + " to int");
            } finally {
                closeDevice();
                releaseRecorder();
            }
        }
    }

    /**
     * Get high speed FPS from CamcorderProfiles for a given size.
     *
     * @param size The size used to search the CamcorderProfiles for the FPS.
     * @return high speed video FPS, 0 if the given size is not supported by the CamcorderProfiles.
     */
    private int getFpsFromHighSpeedProfileForSize(Size size) {
        for (int quality = CamcorderProfile.QUALITY_HIGH_SPEED_480P;
                quality <= CamcorderProfile.QUALITY_HIGH_SPEED_2160P; quality++) {
            if (CamcorderProfile.hasProfile(quality)) {
                CamcorderProfile profile = CamcorderProfile.get(quality);
                if (size.equals(new Size(profile.videoFrameWidth, profile.videoFrameHeight))){
                    return profile.videoFrameRate;
                }
            }
        }

        return 0;
    }

    private Range<Integer> getHighestHighSpeedFixedFpsRangeForSize(StreamConfigurationMap config,
            Size size) {
        Range<Integer>[] availableFpsRanges = config.getHighSpeedVideoFpsRangesFor(size);
        Range<Integer> maxRange = availableFpsRanges[0];
        boolean foundRange = false;
        for (Range<Integer> range : availableFpsRanges) {
            if (range.getLower().equals(range.getUpper()) && range.getLower() >= maxRange.getLower()) {
                foundRange = true;
                maxRange = range;
            }
        }

        if (!foundRange) {
            return null;
        }
        return maxRange;
    }

    private List<Range<Integer>> getHighSpeedFixedFpsRangeForSize(StreamConfigurationMap config,
            Size size) {
        Range<Integer>[] availableFpsRanges = config.getHighSpeedVideoFpsRangesFor(size);
        List<Range<Integer>> fixedRanges = new ArrayList<Range<Integer>>();
        for (Range<Integer> range : availableFpsRanges) {
            if (range.getLower().equals(range.getUpper())) {
                fixedRanges.add(range);
            }
        }
        return fixedRanges;
    }

    private void startConstrainedPreview(Range<Integer> fpsRange,
            CameraCaptureSession.CaptureCallback listener) throws Exception {
        List<Surface> outputSurfaces = new ArrayList<Surface>(1);
        assertTrue("Preview surface should be valid", mPreviewSurface.isValid());
        outputSurfaces.add(mPreviewSurface);
        mSessionListener = new BlockingSessionCallback();

        List<CaptureRequest> slowMoRequests = null;
        CaptureRequest.Builder requestBuilder =
            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        requestBuilder.addTarget(mPreviewSurface);
        CaptureRequest initialRequest = requestBuilder.build();
        CameraTestUtils.checkSessionConfigurationWithSurfaces(mCamera, mHandler,
                outputSurfaces, /*inputConfig*/ null, SessionConfiguration.SESSION_HIGH_SPEED,
                /*defaultSupport*/ true, "Constrained session configuration query failed");
        mSession = buildConstrainedCameraSession(mCamera, outputSurfaces, mSessionListener,
                mHandler, initialRequest);
        slowMoRequests = ((CameraConstrainedHighSpeedCaptureSession) mSession).
            createHighSpeedRequestList(initialRequest);

        mSession.setRepeatingBurst(slowMoRequests, listener, mHandler);
    }

    private void startSlowMotionRecording(boolean useMediaRecorder, int videoFrameRate,
            int captureRate, Range<Integer> fpsRange,
            CameraCaptureSession.CaptureCallback listener, boolean useHighSpeedSession)
            throws Exception {
        List<Surface> outputSurfaces = new ArrayList<Surface>(2);
        assertTrue("Both preview and recording surfaces should be valid",
                mPreviewSurface.isValid() && mRecordingSurface.isValid());
        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(mRecordingSurface);
        // Video snapshot surface
        if (mReaderSurface != null) {
            outputSurfaces.add(mReaderSurface);
        }
        mSessionListener = new BlockingSessionCallback();

        // Create slow motion request list
        List<CaptureRequest> slowMoRequests = null;
        if (useHighSpeedSession) {
            CaptureRequest.Builder requestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            requestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            requestBuilder.addTarget(mPreviewSurface);
            requestBuilder.addTarget(mRecordingSurface);
            CaptureRequest initialRequest = requestBuilder.build();
            mSession = buildConstrainedCameraSession(mCamera, outputSurfaces, mSessionListener,
                    mHandler, initialRequest);
            slowMoRequests = ((CameraConstrainedHighSpeedCaptureSession) mSession).
                    createHighSpeedRequestList(initialRequest);
        } else {
            CaptureRequest.Builder recordingRequestBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordingRequestBuilder.set(CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            recordingRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                    CaptureRequest.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO);

            CaptureRequest.Builder recordingOnlyBuilder =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            recordingOnlyBuilder.set(CaptureRequest.CONTROL_MODE,
                    CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            recordingOnlyBuilder.set(CaptureRequest.CONTROL_SCENE_MODE,
                    CaptureRequest.CONTROL_SCENE_MODE_HIGH_SPEED_VIDEO);
            int slowMotionFactor = captureRate / videoFrameRate;

            // Make sure camera output frame rate is set to correct value.
            recordingRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            recordingRequestBuilder.addTarget(mRecordingSurface);
            recordingRequestBuilder.addTarget(mPreviewSurface);
            recordingOnlyBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            recordingOnlyBuilder.addTarget(mRecordingSurface);

            CaptureRequest initialRequest = recordingRequestBuilder.build();
            mSession = configureCameraSessionWithParameters(mCamera, outputSurfaces,
                    mSessionListener, mHandler, initialRequest);

            slowMoRequests = new ArrayList<CaptureRequest>();
            slowMoRequests.add(initialRequest);// Preview + recording.

            for (int i = 0; i < slowMotionFactor - 1; i++) {
                slowMoRequests.add(recordingOnlyBuilder.build()); // Recording only.
            }
        }

        mSession.setRepeatingBurst(slowMoRequests, listener, mHandler);

        if (useMediaRecorder) {
            mMediaRecorder.start();
        } else {
            // TODO: need implement MediaCodec path.
        }

    }

    private void basicRecordingTestByCamera(int[] camcorderProfileList, boolean useVideoStab)
            throws Exception {
        basicRecordingTestByCamera(camcorderProfileList, useVideoStab, false);
    }

    private void basicRecordingTestByCamera(int[] camcorderProfileList, boolean useVideoStab,
            boolean useIntermediateSurface) throws Exception {
        basicRecordingTestByCamera(camcorderProfileList, useVideoStab,
                useIntermediateSurface, false);
    }

    /**
     * Test camera recording by using each available CamcorderProfile for a
     * given camera. preview size is set to the video size.
     */
    private void basicRecordingTestByCamera(int[] camcorderProfileList, boolean useVideoStab,
            boolean useIntermediateSurface, boolean useEncoderProfiles) throws Exception {
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        List<Range<Integer> > fpsRanges = Arrays.asList(
                mStaticInfo.getAeAvailableTargetFpsRangesChecked());
        int cameraId = Integer.valueOf(mCamera.getId());
        int maxVideoFrameRate = -1;

        // only validate recording for non-perf measurement runs
        boolean validateRecording = !isPerfMeasure();
        for (int profileId : camcorderProfileList) {
            if (!CamcorderProfile.hasProfile(cameraId, profileId)) {
                continue;
            }

            CamcorderProfile profile = CamcorderProfile.get(cameraId, profileId);
            Size videoSz = new Size(profile.videoFrameWidth, profile.videoFrameHeight);

            Range<Integer> fpsRange = new Range(profile.videoFrameRate, profile.videoFrameRate);
            if (maxVideoFrameRate < profile.videoFrameRate) {
                maxVideoFrameRate = profile.videoFrameRate;
            }

            if (allowedUnsupported(cameraId, profileId)) {
                continue;
            }

            if (mStaticInfo.isHardwareLevelLegacy() &&
                    (videoSz.getWidth() > maxPreviewSize.getWidth() ||
                     videoSz.getHeight() > maxPreviewSize.getHeight())) {
                // Skip. Legacy mode can only do recording up to max preview size
                continue;
            }
            assertTrue("Video size " + videoSz.toString() + " for profile ID " + profileId +
                            " must be one of the camera device supported video size!",
                            mSupportedVideoSizes.contains(videoSz));
            assertTrue("Frame rate range " + fpsRange + " (for profile ID " + profileId +
                    ") must be one of the camera device available FPS range!",
                    fpsRanges.contains(fpsRange));


            if (useEncoderProfiles) {
                // Iterate through all video-audio codec combination
                EncoderProfiles profiles = CamcorderProfile.getAll(mCamera.getId(), profileId);
                for (EncoderProfiles.VideoProfile videoProfile : profiles.getVideoProfiles()) {
                    boolean hasAudioProfile = false;
                    for (EncoderProfiles.AudioProfile audioProfile : profiles.getAudioProfiles()) {
                        hasAudioProfile = true;
                        doBasicRecordingByProfile(profiles, videoProfile, audioProfile,
                                useVideoStab, useIntermediateSurface, validateRecording);
                        // Only measure the default video profile of the largest video
                        // recording size when measuring perf
                        if (isPerfMeasure()) {
                            break;
                        }
                    }
                    // Timelapse profiles do not have audio track
                    if (!hasAudioProfile) {
                        doBasicRecordingByProfile(profiles, videoProfile, /* audioProfile */null,
                                useVideoStab, useIntermediateSurface, validateRecording);
                    }
                }
            } else {
                doBasicRecordingByProfile(
                        profile, useVideoStab, useIntermediateSurface, validateRecording);
            }

            if (isPerfMeasure()) {
                // Only measure the largest video recording size when measuring perf
                break;
            }
        }
        if (maxVideoFrameRate != -1) {
            // At least one CamcorderProfile is present, check FPS
            assertTrue("At least one CamcorderProfile must support >= 24 FPS",
                    maxVideoFrameRate >= 24);
        }
    }

    private void doBasicRecordingByProfile(
            CamcorderProfile profile, boolean userVideoStab,
            boolean useIntermediateSurface, boolean validate) throws Exception {
        Size videoSz = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
        int frameRate = profile.videoFrameRate;

        if (VERBOSE) {
            Log.v(TAG, "Testing camera recording with video size " + videoSz.toString());
        }

        // Configure preview and recording surfaces.
        mOutMediaFileName = mDebugFileNameBase + "/test_video.mp4";
        if (DEBUG_DUMP) {
            mOutMediaFileName = mDebugFileNameBase + "/test_video_" + mCamera.getId() + "_"
                    + videoSz.toString() + ".mp4";
        }

        setupMediaRecorder(profile);
        completeBasicRecording(videoSz, frameRate, userVideoStab, useIntermediateSurface, validate);
    }

    private void doBasicRecordingByProfile(
            EncoderProfiles profiles,
            EncoderProfiles.VideoProfile videoProfile, EncoderProfiles.AudioProfile audioProfile,
            boolean userVideoStab, boolean useIntermediateSurface, boolean validate)
                    throws Exception {
        Size videoSz = new Size(videoProfile.getWidth(), videoProfile.getHeight());
        int frameRate = videoProfile.getFrameRate();

        if (VERBOSE) {
            Log.v(TAG, "Testing camera recording with video size " + videoSz.toString() +
                  ", video codec " + videoProfile.getMediaType() + ", and audio codec " +
                  (audioProfile == null ? "(null)" : audioProfile.getMediaType()));
        }

        // Configure preview and recording surfaces.
        mOutMediaFileName = mDebugFileNameBase + "/test_video.mp4";
        if (DEBUG_DUMP) {
            mOutMediaFileName = mDebugFileNameBase + "/test_video_" + mCamera.getId() + "_"
                    + videoSz.toString() + "_" + videoProfile.getCodec();
            if (audioProfile != null) {
                mOutMediaFileName += "_" + audioProfile.getCodec();
            }
            mOutMediaFileName += ".mp4";
        }

        setupMediaRecorder(profiles, videoProfile, audioProfile);
        completeBasicRecording(videoSz, frameRate, userVideoStab, useIntermediateSurface, validate);
    }

    private void completeBasicRecording(
            Size videoSz, int frameRate, boolean useVideoStab,
            boolean useIntermediateSurface, boolean validate) throws Exception {
        prepareRecording(useIntermediateSurface);

        // prepare preview surface by using video size.
        updatePreviewSurfaceWithVideo(videoSz, frameRate);

        // Start recording
        SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
        startRecording(/* useMediaRecorder */true, resultListener, useVideoStab,
                useIntermediateSurface);

        // Record certain duration.
        SystemClock.sleep(RECORDING_DURATION_MS);

        // Stop recording and preview
        stopRecording(/* useMediaRecorder */true, useIntermediateSurface,
                /* stopCameraStreaming */true);
        // Convert number of frames camera produced into the duration in unit of ms.
        float frameDurationMs = 1000.0f / frameRate;
        float durationMs = 0.f;
        if (useIntermediateSurface) {
            durationMs = mQueuer.getQueuedCount() * frameDurationMs;
        } else {
            durationMs = resultListener.getTotalNumFrames() * frameDurationMs;
        }

        if (VERBOSE) {
            Log.v(TAG, "video frame rate: " + frameRate +
                            ", num of frames produced: " + resultListener.getTotalNumFrames());
        }

        if (validate) {
            validateRecording(videoSz, durationMs, frameDurationMs, FRMDRP_RATE_TOLERANCE);
        }
    }

    /**
     * Test camera recording for each supported video size by camera, preview
     * size is set to the video size.
     */
    private void recordingSizeTestByCamera() throws Exception {
        for (Size sz : mSupportedVideoSizes) {
            if (!isSupported(sz, VIDEO_FRAME_RATE, VIDEO_FRAME_RATE)) {
                continue;
            }

            if (VERBOSE) {
                Log.v(TAG, "Testing camera recording with video size " + sz.toString());
            }

            // Configure preview and recording surfaces.
            mOutMediaFileName = mDebugFileNameBase + "/test_video.mp4";
            if (DEBUG_DUMP) {
                mOutMediaFileName = mDebugFileNameBase + "/test_video_" + mCamera.getId() + "_"
                        + sz.toString() + ".mp4";
            }

            // Allow external camera to use variable fps range
            Range<Integer> fpsRange = null;
            if (mStaticInfo.isExternalCamera()) {
                Range<Integer>[] availableFpsRange =
                        mStaticInfo.getAeAvailableTargetFpsRangesChecked();

                boolean foundRange = false;
                int minFps = 0;
                for (int i = 0; i < availableFpsRange.length; i += 1) {
                    if (minFps < availableFpsRange[i].getLower()
                            && VIDEO_FRAME_RATE == availableFpsRange[i].getUpper()) {
                        minFps = availableFpsRange[i].getLower();
                        foundRange = true;
                    }
                }
                assertTrue("Cannot find FPS range for maxFps " + VIDEO_FRAME_RATE, foundRange);
                fpsRange = Range.create(minFps, VIDEO_FRAME_RATE);
            }

            // Use AVC and AAC a/v compression format.
            prepareRecording(sz, VIDEO_FRAME_RATE, VIDEO_FRAME_RATE);

            // prepare preview surface by using video size.
            updatePreviewSurfaceWithVideo(sz, VIDEO_FRAME_RATE);

            // Start recording
            SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
            startRecording(
                    /* useMediaRecorder */true, resultListener,
                    /*useVideoStab*/false, fpsRange, false);

            // Record certain duration.
            SystemClock.sleep(RECORDING_DURATION_MS);

            // Stop recording and preview
            stopRecording(/* useMediaRecorder */true);
            // Convert number of frames camera produced into the duration in unit of ms.
            float frameDurationMinMs = 1000.0f / VIDEO_FRAME_RATE;
            float durationMinMs = resultListener.getTotalNumFrames() * frameDurationMinMs;
            float durationMaxMs = durationMinMs;
            float frameDurationMaxMs = 0.f;
            if (fpsRange != null) {
                frameDurationMaxMs = 1000.0f / fpsRange.getLower();
                durationMaxMs = resultListener.getTotalNumFrames() * frameDurationMaxMs;
            }

            // Validation.
            validateRecording(sz, durationMinMs, durationMaxMs,
                    frameDurationMinMs, frameDurationMaxMs,
                    FRMDRP_RATE_TOLERANCE);
        }
    }

    /**
     * Initialize the supported video sizes.
     */
    private void initSupportedVideoSize(String cameraId)  throws Exception {
        int id = Integer.valueOf(cameraId);
        Size maxVideoSize = SIZE_BOUND_720P;
        if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_2160P)) {
            maxVideoSize = SIZE_BOUND_2160P;
        } else if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_QHD)) {
            maxVideoSize = SIZE_BOUND_QHD;
        } else if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_2K)) {
            maxVideoSize = SIZE_BOUND_2K;
        } else if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_1080P)) {
            maxVideoSize = SIZE_BOUND_1080P;
        }

        mSupportedVideoSizes =
                getSupportedVideoSizes(cameraId, mCameraManager, maxVideoSize);
    }

    /**
     * Simple wrapper to wrap normal/burst video snapshot tests
     */
    private void videoSnapshotHelper(boolean burstTest) throws Exception {
            for (String id : mCameraIdsUnderTest) {
                try {
                    Log.i(TAG, "Testing video snapshot for camera " + id);

                    StaticMetadata staticInfo = mAllStaticInfo.get(id);
                    if (!staticInfo.isColorOutputSupported()) {
                        Log.i(TAG, "Camera " + id +
                                " does not support color outputs, skipping");
                        continue;
                    }

                    if (staticInfo.isExternalCamera()) {
                        Log.i(TAG, "Camera " + id +
                                " does not support CamcorderProfile, skipping");
                        continue;
                    }

                    // Re-use the MediaRecorder object for the same camera device.
                    mMediaRecorder = new MediaRecorder();

                    openDevice(id);

                    initSupportedVideoSize(id);

                    videoSnapshotTestByCamera(burstTest);
                } finally {
                    closeDevice();
                    releaseRecorder();
                }
            }
    }

    /**
     * Returns {@code true} if the {@link CamcorderProfile} ID is allowed to be unsupported.
     *
     * <p>This only allows unsupported profiles when using the LEGACY mode of the Camera API.</p>
     *
     * @param profileId a {@link CamcorderProfile} ID to check.
     * @return {@code true} if supported.
     */
    private boolean allowedUnsupported(int cameraId, int profileId) {
        if (!mStaticInfo.isHardwareLevelLegacy()) {
            return false;
        }

        switch(profileId) {
            case CamcorderProfile.QUALITY_2160P:
            case CamcorderProfile.QUALITY_1080P:
            case CamcorderProfile.QUALITY_HIGH:
                return !CamcorderProfile.hasProfile(cameraId, profileId) ||
                        CamcorderProfile.get(cameraId, profileId).videoFrameWidth >= 1080;
        }
        return false;
    }

    /**
     * Test video snapshot for each  available CamcorderProfile for a given camera.
     *
     * <p>
     * Preview size is set to the video size. For the burst test, frame drop and jittering
     * is not checked.
     * </p>
     *
     * @param burstTest Perform burst capture or single capture. For burst capture
     *                  {@value #BURST_VIDEO_SNAPSHOT_NUM} capture requests will be sent.
     */
    private void videoSnapshotTestByCamera(boolean burstTest)
            throws Exception {
        final int NUM_SINGLE_SHOT_TEST = 5;
        final int FRAMEDROP_TOLERANCE = 8;
        final int FRAME_SIZE_15M = 15000000;
        final float FRAME_DROP_TOLERENCE_FACTOR = 1.5f;
        int kFrameDrop_Tolerence = FRAMEDROP_TOLERANCE;

        for (int profileId : mCamcorderProfileList) {
            int cameraId = Integer.valueOf(mCamera.getId());
            if (!CamcorderProfile.hasProfile(cameraId, profileId) ||
                    allowedUnsupported(cameraId, profileId)) {
                continue;
            }

            CamcorderProfile profile = CamcorderProfile.get(cameraId, profileId);
            Size QCIF = new Size(176, 144);
            Size FULL_HD = new Size(1920, 1080);
            Size videoSz = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
            Size maxPreviewSize = mOrderedPreviewSizes.get(0);

            if (mStaticInfo.isHardwareLevelLegacy() &&
                    (videoSz.getWidth() > maxPreviewSize.getWidth() ||
                     videoSz.getHeight() > maxPreviewSize.getHeight())) {
                // Skip. Legacy mode can only do recording up to max preview size
                continue;
            }

            if (!mSupportedVideoSizes.contains(videoSz)) {
                mCollector.addMessage("Video size " + videoSz.toString() + " for profile ID " +
                        profileId + " must be one of the camera device supported video size!");
                continue;
            }

            // For LEGACY, find closest supported smaller or equal JPEG size to the current video
            // size; if no size is smaller than the video, pick the smallest JPEG size.  The assert
            // for video size above guarantees that for LIMITED or FULL, we select videoSz here.
            // Also check for minFrameDuration here to make sure jpeg stream won't slow down
            // video capture
            Size videoSnapshotSz = mOrderedStillSizes.get(mOrderedStillSizes.size() - 1);
            // Allow a bit tolerance so we don't fail for a few nano seconds of difference
            final float FRAME_DURATION_TOLERANCE = 0.01f;
            long videoFrameDuration = (long) (1e9 / profile.videoFrameRate *
                    (1.0 + FRAME_DURATION_TOLERANCE));
            HashMap<Size, Long> minFrameDurationMap = mStaticInfo.
                    getAvailableMinFrameDurationsForFormatChecked(ImageFormat.JPEG);
            for (int i = mOrderedStillSizes.size() - 2; i >= 0; i--) {
                Size candidateSize = mOrderedStillSizes.get(i);
                if (mStaticInfo.isHardwareLevelLegacy()) {
                    // Legacy level doesn't report min frame duration
                    if (candidateSize.getWidth() <= videoSz.getWidth() &&
                            candidateSize.getHeight() <= videoSz.getHeight()) {
                        videoSnapshotSz = candidateSize;
                    }
                } else {
                    Long jpegFrameDuration = minFrameDurationMap.get(candidateSize);
                    assertTrue("Cannot find minimum frame duration for jpeg size " + candidateSize,
                            jpegFrameDuration != null);
                    if (candidateSize.getWidth() <= videoSz.getWidth() &&
                            candidateSize.getHeight() <= videoSz.getHeight() &&
                            jpegFrameDuration <= videoFrameDuration) {
                        videoSnapshotSz = candidateSize;
                    }
                }
            }
            Size defaultvideoSnapshotSz = videoSnapshotSz;

            /**
             * Only test full res snapshot when below conditions are all true.
             * 1. Camera is at least a LIMITED device.
             * 2. video size is up to max preview size, which will be bounded by 1080p.
             * 3. Full resolution jpeg stream can keep up to video stream speed.
             *    When full res jpeg stream cannot keep up to video stream speed, search
             *    the largest jpeg size that can susptain video speed instead.
             */
            if (mStaticInfo.isHardwareLevelAtLeastLimited() &&
                    videoSz.getWidth() <= maxPreviewSize.getWidth() &&
                    videoSz.getHeight() <= maxPreviewSize.getHeight()) {
                for (Size jpegSize : mOrderedStillSizes) {
                    Long jpegFrameDuration = minFrameDurationMap.get(jpegSize);
                    assertTrue("Cannot find minimum frame duration for jpeg size " + jpegSize,
                            jpegFrameDuration != null);
                    if (jpegFrameDuration <= videoFrameDuration) {
                        videoSnapshotSz = jpegSize;
                        break;
                    }
                    if (jpegSize.equals(videoSz)) {
                        throw new AssertionFailedError(
                                "Cannot find adequate video snapshot size for video size" +
                                        videoSz);
                    }
                }
            }

            if (videoSnapshotSz.getWidth() * videoSnapshotSz.getHeight() > FRAME_SIZE_15M)
                kFrameDrop_Tolerence = (int)(FRAMEDROP_TOLERANCE * FRAME_DROP_TOLERENCE_FACTOR);

            createImageReader(
                    videoSnapshotSz, ImageFormat.JPEG,
                    MAX_VIDEO_SNAPSHOT_IMAGES, /*listener*/null);

            // Full or better devices should support whatever video snapshot size calculated above.
            // Limited devices may only be able to support the default one.
            if (mStaticInfo.isHardwareLevelLimited()) {
                List<Surface> outputs = new ArrayList<Surface>();
                outputs.add(mPreviewSurface);
                outputs.add(mRecordingSurface);
                outputs.add(mReaderSurface);
                boolean isSupported = isStreamConfigurationSupported(
                        mCamera, outputs, mSessionListener, mHandler);
                if (!isSupported) {
                    videoSnapshotSz = defaultvideoSnapshotSz;
                    createImageReader(
                            videoSnapshotSz, ImageFormat.JPEG,
                            MAX_VIDEO_SNAPSHOT_IMAGES, /*listener*/null);
                }
            }

            if (videoSz.equals(QCIF) &&
                    ((videoSnapshotSz.getWidth() > FULL_HD.getWidth()) ||
                     (videoSnapshotSz.getHeight() > FULL_HD.getHeight()))) {
                List<Surface> outputs = new ArrayList<Surface>();
                outputs.add(mPreviewSurface);
                outputs.add(mRecordingSurface);
                outputs.add(mReaderSurface);
                boolean isSupported = isStreamConfigurationSupported(
                        mCamera, outputs, mSessionListener, mHandler);
                if (!isSupported) {
                    videoSnapshotSz = defaultvideoSnapshotSz;
                    createImageReader(
                            videoSnapshotSz, ImageFormat.JPEG,
                            MAX_VIDEO_SNAPSHOT_IMAGES, /*listener*/null);
                }
            }

            Log.i(TAG, "Testing video snapshot size " + videoSnapshotSz +
                    " for video size " + videoSz);

            if (VERBOSE) {
                Log.v(TAG, "Testing camera recording with video size " + videoSz.toString());
            }

            // Configure preview and recording surfaces.
            mOutMediaFileName = mDebugFileNameBase + "/test_video.mp4";
            if (DEBUG_DUMP) {
                mOutMediaFileName = mDebugFileNameBase + "/test_video_" + cameraId + "_"
                        + videoSz.toString() + ".mp4";
            }

            int numTestIterations = burstTest ? 1 : NUM_SINGLE_SHOT_TEST;
            int totalDroppedFrames = 0;

            for (int numTested = 0; numTested < numTestIterations; numTested++) {
                prepareRecordingWithProfile(profile);

                // prepare video snapshot
                SimpleCaptureCallback resultListener = new SimpleCaptureCallback();
                SimpleImageReaderListener imageListener = new SimpleImageReaderListener();
                CaptureRequest.Builder videoSnapshotRequestBuilder =
                        mCamera.createCaptureRequest((mStaticInfo.isHardwareLevelLegacy()) ?
                                CameraDevice.TEMPLATE_RECORD :
                                CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);

                // prepare preview surface by using video size.
                updatePreviewSurfaceWithVideo(videoSz, profile.videoFrameRate);

                prepareVideoSnapshot(videoSnapshotRequestBuilder, imageListener);
                Range<Integer> fpsRange = Range.create(profile.videoFrameRate,
                        profile.videoFrameRate);
                videoSnapshotRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        fpsRange);
                boolean videoStabilizationSupported = mStaticInfo.isVideoStabilizationSupported();
                if (videoStabilizationSupported) {
                   videoSnapshotRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                            mStaticInfo.getChosenVideoStabilizationMode());
                }
                CaptureRequest request = videoSnapshotRequestBuilder.build();

                // Start recording
                startRecording(/* useMediaRecorder */true, resultListener,
                        /*useVideoStab*/videoStabilizationSupported);
                long startTime = SystemClock.elapsedRealtime();

                // Record certain duration.
                SystemClock.sleep(RECORDING_DURATION_MS / 2);

                // take video snapshot
                if (burstTest) {
                    List<CaptureRequest> requests =
                            new ArrayList<CaptureRequest>(BURST_VIDEO_SNAPSHOT_NUM);
                    for (int i = 0; i < BURST_VIDEO_SNAPSHOT_NUM; i++) {
                        requests.add(request);
                    }
                    mSession.captureBurst(requests, resultListener, mHandler);
                } else {
                    mSession.capture(request, resultListener, mHandler);
                }

                // make sure recording is still going after video snapshot
                SystemClock.sleep(RECORDING_DURATION_MS / 2);

                // Stop recording and preview
                float durationMs = (float) stopRecording(/* useMediaRecorder */true);
                // For non-burst test, use number of frames to also double check video frame rate.
                // Burst video snapshot is allowed to cause frame rate drop, so do not use number
                // of frames to estimate duration
                if (!burstTest) {
                    durationMs = resultListener.getTotalNumFrames() * 1000.0f /
                        profile.videoFrameRate;
                }

                float frameDurationMs = 1000.0f / profile.videoFrameRate;
                // Validation recorded video
                validateRecording(videoSz, durationMs,
                        frameDurationMs, VID_SNPSHT_FRMDRP_RATE_TOLERANCE);

                if (burstTest) {
                    for (int i = 0; i < BURST_VIDEO_SNAPSHOT_NUM; i++) {
                        Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                        validateVideoSnapshotCapture(image, videoSnapshotSz);
                        image.close();
                    }
                } else {
                    // validate video snapshot image
                    Image image = imageListener.getImage(CAPTURE_IMAGE_TIMEOUT_MS);
                    validateVideoSnapshotCapture(image, videoSnapshotSz);

                    // validate if there is framedrop around video snapshot
                    totalDroppedFrames +=  validateFrameDropAroundVideoSnapshot(
                            resultListener, image.getTimestamp());

                    //TODO: validate jittering. Should move to PTS
                    //validateJittering(resultListener);

                    image.close();
                }
            }

            if (!burstTest) {
                Log.w(TAG, String.format("Camera %d Video size %s: Number of dropped frames " +
                        "detected in %d trials is %d frames.", cameraId, videoSz.toString(),
                        numTestIterations, totalDroppedFrames));
                mCollector.expectLessOrEqual(
                        String.format(
                                "Camera %d Video size %s: Number of dropped frames %d must not"
                                + " be larger than %d",
                                cameraId, videoSz.toString(), totalDroppedFrames,
                                kFrameDrop_Tolerence),
                        kFrameDrop_Tolerence, totalDroppedFrames);
            }
            closeImageReader();
        }
    }

    /**
     * Configure video snapshot request according to the still capture size
     */
    private void prepareVideoSnapshot(
            CaptureRequest.Builder requestBuilder,
            ImageReader.OnImageAvailableListener imageListener)
            throws Exception {
        mReader.setOnImageAvailableListener(imageListener, mHandler);
        assertNotNull("Recording surface must be non-null!", mRecordingSurface);
        requestBuilder.addTarget(mRecordingSurface);
        assertNotNull("Preview surface must be non-null!", mPreviewSurface);
        requestBuilder.addTarget(mPreviewSurface);
        assertNotNull("Reader surface must be non-null!", mReaderSurface);
        requestBuilder.addTarget(mReaderSurface);
    }

    /**
     * Find compatible preview sizes for video size and framerate.
     *
     * <p>Preview size will be capped with max preview size.</p>
     *
     * @param videoSize The video size used for preview.
     * @param videoFrameRate The video frame rate
     */
    private List<Size> getPreviewSizesForVideo(Size videoSize, int videoFrameRate) {
        if (mOrderedPreviewSizes == null) {
            throw new IllegalStateException("supported preview size list is not initialized yet");
        }
        final float FRAME_DURATION_TOLERANCE = 0.01f;
        long videoFrameDuration = (long) (1e9 / videoFrameRate *
                (1.0 + FRAME_DURATION_TOLERANCE));
        HashMap<Size, Long> minFrameDurationMap = mStaticInfo.
                getAvailableMinFrameDurationsForFormatChecked(ImageFormat.PRIVATE);
        Size maxPreviewSize = mOrderedPreviewSizes.get(0);
        ArrayList<Size> previewSizes = new ArrayList<>();
        if (videoSize.getWidth() > maxPreviewSize.getWidth() ||
                videoSize.getHeight() > maxPreviewSize.getHeight()) {
            for (Size s : mOrderedPreviewSizes) {
                Long frameDuration = minFrameDurationMap.get(s);
                if (mStaticInfo.isHardwareLevelLegacy()) {
                    // Legacy doesn't report min frame duration
                    frameDuration = new Long(0);
                }
                assertTrue("Cannot find minimum frame duration for private size" + s,
                        frameDuration != null);
                if (frameDuration <= videoFrameDuration &&
                        s.getWidth() <= videoSize.getWidth() &&
                        s.getHeight() <= videoSize.getHeight()) {
                    Log.v(TAG, "Add preview size " + s.toString() + " for video size " +
                            videoSize.toString());
                    previewSizes.add(s);
                }
            }
        }

        if (previewSizes.isEmpty()) {
            previewSizes.add(videoSize);
        }

        return previewSizes;
    }

    /**
     * Update preview size with video size.
     *
     * <p>Preview size will be capped with max preview size.</p>
     *
     * @param videoSize The video size used for preview.
     * @param videoFrameRate The video frame rate
     *
     */
    private void updatePreviewSurfaceWithVideo(Size videoSize, int videoFrameRate) {
        List<Size> previewSizes = getPreviewSizesForVideo(videoSize, videoFrameRate);
        updatePreviewSurface(previewSizes.get(0));
    }

    private void prepareRecordingWithProfile(CamcorderProfile profile) throws Exception {
        prepareRecordingWithProfile(profile, false);
    }

    /**
     * Configure MediaRecorder recording session with CamcorderProfile, prepare
     * the recording surface.
     */
    private void prepareRecordingWithProfile(CamcorderProfile profile,
            boolean useIntermediateSurface) throws Exception {
        // Prepare MediaRecorder.
        setupMediaRecorder(profile);
        prepareRecording(useIntermediateSurface);
    }

    private void setupMediaRecorder(CamcorderProfile profile) throws Exception {
        // Set-up MediaRecorder.
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(profile);

        mVideoFrameRate = profile.videoFrameRate;
        mVideoSize = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
    }

    private void setupMediaRecorder(
            EncoderProfiles profiles,
            EncoderProfiles.VideoProfile videoProfile,
            EncoderProfiles.AudioProfile audioProfile) throws Exception {
        // Set-up MediaRecorder.
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(profiles.getRecommendedFileFormat());
        mMediaRecorder.setVideoProfile(videoProfile);
        if (audioProfile != null) {
            mMediaRecorder.setAudioProfile(audioProfile);
        }

        mVideoFrameRate = videoProfile.getFrameRate();
        mVideoSize = new Size(videoProfile.getWidth(), videoProfile.getHeight());
    }

    private void prepareRecording(boolean useIntermediateSurface) throws Exception {
        // Continue preparing MediaRecorder
        mMediaRecorder.setOutputFile(mOutMediaFileName);
        if (mPersistentSurface != null) {
            mMediaRecorder.setInputSurface(mPersistentSurface);
            mRecordingSurface = mPersistentSurface;
        }
        mMediaRecorder.prepare();
        if (mPersistentSurface == null) {
            mRecordingSurface = mMediaRecorder.getSurface();
        }
        assertNotNull("Recording surface must be non-null!", mRecordingSurface);

        if (useIntermediateSurface) {
            mIntermediateReader = ImageReader.newInstance(
                    mVideoSize.getWidth(), mVideoSize.getHeight(),
                    ImageFormat.PRIVATE, /*maxImages*/3, HardwareBuffer.USAGE_VIDEO_ENCODE);

            mIntermediateSurface = mIntermediateReader.getSurface();
            mIntermediateWriter = ImageWriter.newInstance(mRecordingSurface, /*maxImages*/3,
                    ImageFormat.PRIVATE);
            mQueuer = new ImageWriterQueuer(mIntermediateWriter);

            mIntermediateThread = new HandlerThread(TAG);
            mIntermediateThread.start();
            mIntermediateHandler = new Handler(mIntermediateThread.getLooper());
            mIntermediateReader.setOnImageAvailableListener(mQueuer, mIntermediateHandler);
        }
    }

    /**
     * Configure MediaRecorder recording session with CamcorderProfile, prepare
     * the recording surface. Use AVC for video compression, AAC for audio compression.
     * Both are required for android devices by android CDD.
     */
    private void prepareRecording(Size sz, int videoFrameRate, int captureRate)
            throws Exception {
        // Prepare MediaRecorder.
        prepareRecording(sz, videoFrameRate, captureRate, MediaRecorder.VideoEncoder.H264);
    }

    /**
     * Configure MediaRecorder recording session with CamcorderProfile, prepare
     * the recording surface. Use AAC for audio compression as required for
     * android devices by android CDD.
     */
    private void prepareRecording(Size sz, int videoFrameRate, int captureRate,
            int videoEncoder) throws Exception {
        // Prepare MediaRecorder.
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setOutputFile(mOutMediaFileName);
        mMediaRecorder.setVideoEncodingBitRate(getVideoBitRate(sz));
        mMediaRecorder.setVideoFrameRate(videoFrameRate);
        mMediaRecorder.setCaptureRate(captureRate);
        mMediaRecorder.setVideoSize(sz.getWidth(), sz.getHeight());
        mMediaRecorder.setVideoEncoder(videoEncoder);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        if (mPersistentSurface != null) {
            mMediaRecorder.setInputSurface(mPersistentSurface);
            mRecordingSurface = mPersistentSurface;
        }
        mMediaRecorder.prepare();
        if (mPersistentSurface == null) {
            mRecordingSurface = mMediaRecorder.getSurface();
        }
        assertNotNull("Recording surface must be non-null!", mRecordingSurface);
        mVideoFrameRate = videoFrameRate;
        mVideoSize = sz;
    }

    private void startRecording(boolean useMediaRecorder,
            CameraCaptureSession.CaptureCallback listener, boolean useVideoStab) throws Exception {
        startRecording(useMediaRecorder, listener, useVideoStab, /*variableFpsRange*/null,
                /*useIntermediateSurface*/false);
    }

    private void startRecording(boolean useMediaRecorder,
            CameraCaptureSession.CaptureCallback listener, boolean useVideoStab,
            boolean useIntermediateSurface) throws Exception {
        startRecording(useMediaRecorder, listener, useVideoStab, /*variableFpsRange*/null,
                useIntermediateSurface);
    }

    private void startRecording(boolean useMediaRecorder,
            CameraCaptureSession.CaptureCallback listener, boolean useVideoStab,
            Range<Integer> variableFpsRange, boolean useIntermediateSurface) throws Exception {
        if (!mStaticInfo.isVideoStabilizationSupported() && useVideoStab) {
            throw new IllegalArgumentException("Video stabilization is not supported");
        }

        List<Surface> outputSurfaces = new ArrayList<Surface>(2);
        assertTrue("Both preview and recording surfaces should be valid",
                mPreviewSurface.isValid() && mRecordingSurface.isValid());
        outputSurfaces.add(mPreviewSurface);
        if (useIntermediateSurface) {
            outputSurfaces.add(mIntermediateSurface);
        } else {
            outputSurfaces.add(mRecordingSurface);
        }

        // Video snapshot surface
        if (mReaderSurface != null) {
            outputSurfaces.add(mReaderSurface);
        }
        mSessionListener = new BlockingSessionCallback();

        CaptureRequest.Builder recordingRequestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        // Make sure camera output frame rate is set to correct value.
        Range<Integer> fpsRange = (variableFpsRange == null) ?
                Range.create(mVideoFrameRate, mVideoFrameRate) : variableFpsRange;

        recordingRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        if (useVideoStab) {
            recordingRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    mStaticInfo.getChosenVideoStabilizationMode());

        }
        if (useIntermediateSurface) {
            recordingRequestBuilder.addTarget(mIntermediateSurface);
            if (mQueuer != null) {
                mQueuer.resetInvalidSurfaceFlag();
            }
        } else {
            recordingRequestBuilder.addTarget(mRecordingSurface);
        }
        recordingRequestBuilder.addTarget(mPreviewSurface);
        CaptureRequest recordingRequest = recordingRequestBuilder.build();
        mSession = configureCameraSessionWithParameters(mCamera, outputSurfaces, mSessionListener,
                mHandler, recordingRequest);
        mSession.setRepeatingRequest(recordingRequest, listener, mHandler);

        if (useMediaRecorder) {
            mMediaRecorder.start();
        } else {
            // TODO: need implement MediaCodec path.
        }
        mRecordingStartTime = SystemClock.elapsedRealtime();
    }

    /**
     * Start video recording with preview and video surfaces sharing the same
     * camera stream.
     *
     * @return true if success, false if sharing is not supported.
     */
    private boolean startSharedRecording(boolean useMediaRecorder,
            CameraCaptureSession.CaptureCallback listener, boolean useVideoStab,
            Range<Integer> variableFpsRange) throws Exception {
        if (!mStaticInfo.isVideoStabilizationSupported() && useVideoStab) {
            throw new IllegalArgumentException("Video stabilization is not supported");
        }

        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>(2);
        assertTrue("Both preview and recording surfaces should be valid",
                mPreviewSurface.isValid() && mRecordingSurface.isValid());
        OutputConfiguration sharedConfig = new OutputConfiguration(mPreviewSurface);
        sharedConfig.enableSurfaceSharing();
        sharedConfig.addSurface(mRecordingSurface);
        outputConfigs.add(sharedConfig);

        CaptureRequest.Builder recordingRequestBuilder =
                mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        // Make sure camera output frame rate is set to correct value.
        Range<Integer> fpsRange = (variableFpsRange == null) ?
                Range.create(mVideoFrameRate, mVideoFrameRate) : variableFpsRange;
        recordingRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
        if (useVideoStab) {
            recordingRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    mStaticInfo.getChosenVideoStabilizationMode());
        }
        CaptureRequest recordingRequest = recordingRequestBuilder.build();

        mSessionListener = new BlockingSessionCallback();
        mSession = tryConfigureCameraSessionWithConfig(mCamera, outputConfigs, recordingRequest,
                mSessionListener, mHandler);

        if (mSession == null) {
            Log.i(TAG, "Sharing between preview and video is not supported");
            return false;
        }

        recordingRequestBuilder.addTarget(mRecordingSurface);
        recordingRequestBuilder.addTarget(mPreviewSurface);
        mSession.setRepeatingRequest(recordingRequestBuilder.build(), listener, mHandler);

        if (useMediaRecorder) {
            mMediaRecorder.start();
        } else {
            // TODO: need implement MediaCodec path.
        }
        mRecordingStartTime = SystemClock.elapsedRealtime();
        return true;
    }


    private void stopCameraStreaming() throws Exception {
        if (VERBOSE) {
            Log.v(TAG, "Stopping camera streaming and waiting for idle");
        }
        // Stop repeating, wait for captures to complete, and disconnect from
        // surfaces
        mSession.close();
        mSessionListener.getStateWaiter().waitForState(SESSION_CLOSED, SESSION_CLOSE_TIMEOUT_MS);
    }

    private int stopRecording(boolean useMediaRecorder) throws Exception {
        return stopRecording(useMediaRecorder, /*useIntermediateSurface*/false,
                /*stopStreaming*/true);
    }

    // Stop recording and return the estimated video duration in milliseconds.
    private int stopRecording(boolean useMediaRecorder, boolean useIntermediateSurface,
            boolean stopStreaming) throws Exception {
        long stopRecordingTime = SystemClock.elapsedRealtime();
        if (useMediaRecorder) {
            if (stopStreaming) {
                stopCameraStreaming();
            }
            if (useIntermediateSurface) {
                mIntermediateReader.setOnImageAvailableListener(null, null);
                mQueuer.expectInvalidSurface();
            }

            mMediaRecorder.stop();
            // Can reuse the MediaRecorder object after reset.
            mMediaRecorder.reset();
        } else {
            // TODO: need implement MediaCodec path.
        }

        if (useIntermediateSurface) {
            mIntermediateReader.close();
            mQueuer.close();
            mIntermediateWriter.close();
            mIntermediateSurface.release();
            mIntermediateReader = null;
            mIntermediateSurface = null;
            mIntermediateWriter = null;
            mIntermediateThread.quitSafely();
            mIntermediateHandler = null;
        }

        if (mPersistentSurface == null && mRecordingSurface != null) {
            mRecordingSurface.release();
            mRecordingSurface = null;
        }
        return (int) (stopRecordingTime - mRecordingStartTime);
    }

    private void releaseRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    private void validateRecording(
            Size sz, float expectedDurationMs, float expectedFrameDurationMs,
            float frameDropTolerance) throws Exception {
        validateRecording(sz,
                expectedDurationMs,  /*fixed FPS recording*/0.f,
                expectedFrameDurationMs, /*fixed FPS recording*/0.f,
                frameDropTolerance);
    }

    private void validateRecording(
            Size sz,
            float expectedDurationMinMs,      // Min duration (maxFps)
            float expectedDurationMaxMs,      // Max duration (minFps). 0.f for fixed fps recording
            float expectedFrameDurationMinMs, // maxFps
            float expectedFrameDurationMaxMs, // minFps. 0.f for fixed fps recording
            float frameDropTolerance) throws Exception {
        File outFile = new File(mOutMediaFileName);
        assertTrue("No video is recorded", outFile.exists());
        float maxFrameDuration = expectedFrameDurationMinMs * (1.0f + FRAMEDURATION_MARGIN);
        if (expectedFrameDurationMaxMs > 0.f) {
            maxFrameDuration = expectedFrameDurationMaxMs * (1.0f + FRAMEDURATION_MARGIN);
        }

        if (expectedDurationMaxMs == 0.f) {
            expectedDurationMaxMs = expectedDurationMinMs;
        }

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(mOutMediaFileName);
            long durationUs = 0;
            int width = -1, height = -1;
            int numTracks = extractor.getTrackCount();
            int selectedTrack = -1;
            final String VIDEO_MIME_TYPE = "video";
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.contains(VIDEO_MIME_TYPE)) {
                    Log.i(TAG, "video format is: " + format.toString());
                    durationUs = format.getLong(MediaFormat.KEY_DURATION);
                    width = format.getInteger(MediaFormat.KEY_WIDTH);
                    height = format.getInteger(MediaFormat.KEY_HEIGHT);
                    selectedTrack = i;
                    extractor.selectTrack(i);
                    break;
                }
            }
            if (selectedTrack < 0) {
                throw new AssertionFailedError(
                        "Cannot find video track!");
            }

            Size videoSz = new Size(width, height);
            assertTrue("Video size doesn't match, expected " + sz.toString() +
                    " got " + videoSz.toString(), videoSz.equals(sz));
            float duration = (float) (durationUs / 1000);
            if (VERBOSE) {
                Log.v(TAG, String.format("Video duration: recorded %fms, expected [%f,%f]ms",
                                         duration, expectedDurationMinMs, expectedDurationMaxMs));
            }

            // Do rest of validation only for better-than-LEGACY devices
            if (mStaticInfo.isHardwareLevelLegacy()) return;

            // TODO: Don't skip this one for video snapshot on LEGACY
            assertTrue(String.format(
                    "Camera %s: Video duration doesn't match: recorded %fms, expected [%f,%f]ms.",
                    mCamera.getId(), duration,
                    expectedDurationMinMs * (1.f - DURATION_MARGIN),
                    expectedDurationMaxMs * (1.f + DURATION_MARGIN)),
                    duration > expectedDurationMinMs * (1.f - DURATION_MARGIN) &&
                            duration < expectedDurationMaxMs * (1.f + DURATION_MARGIN));

            // Check for framedrop
            long lastSampleUs = 0;
            int frameDropCount = 0;
            int expectedFrameCount = (int) (expectedDurationMinMs / expectedFrameDurationMinMs);
            ArrayList<Long> timestamps = new ArrayList<Long>(expectedFrameCount);
            while (true) {
                timestamps.add(extractor.getSampleTime());
                if (!extractor.advance()) {
                    break;
                }
            }
            Collections.sort(timestamps);
            long prevSampleUs = timestamps.get(0);
            for (int i = 1; i < timestamps.size(); i++) {
                long currentSampleUs = timestamps.get(i);
                float frameDurationMs = (float) (currentSampleUs - prevSampleUs) / 1000;
                if (frameDurationMs > maxFrameDuration) {
                    Log.w(TAG, String.format(
                        "Frame drop at %d: expectation %f, observed %f",
                        i, expectedFrameDurationMinMs, frameDurationMs));
                    frameDropCount++;
                }
                prevSampleUs = currentSampleUs;
            }
            float frameDropRate = 100.f * frameDropCount / timestamps.size();
            Log.i(TAG, String.format("Frame drop rate %d/%d (%f%%)",
                frameDropCount, timestamps.size(), frameDropRate));
            assertTrue(String.format(
                    "Camera %s: Video frame drop rate too high: %f%%, tolerance %f%%. " +
                    "Video size: %s, expectedDuration [%f,%f], expectedFrameDuration %f, " +
                    "frameDropCnt %d, frameCount %d",
                    mCamera.getId(), frameDropRate, frameDropTolerance,
                    sz.toString(), expectedDurationMinMs, expectedDurationMaxMs,
                    expectedFrameDurationMinMs, frameDropCount, timestamps.size()),
                    frameDropRate < frameDropTolerance);
        } finally {
            extractor.release();
            if (!DEBUG_DUMP) {
                outFile.delete();
            }
        }
    }

    /**
     * Validate video snapshot capture image object validity and test.
     *
     * <p> Check for size, format and jpeg decoding</p>
     *
     * @param image The JPEG image to be verified.
     * @param size The JPEG capture size to be verified against.
     */
    private void validateVideoSnapshotCapture(Image image, Size size) {
        CameraTestUtils.validateImage(image, size.getWidth(), size.getHeight(),
                ImageFormat.JPEG, /*filePath*/null);
    }

    /**
     * Validate if video snapshot causes frame drop.
     * Here frame drop is defined as frame duration >= 2 * expected frame duration.
     * Return the estimated number of frames dropped during video snapshot
     */
    private int validateFrameDropAroundVideoSnapshot(
            SimpleCaptureCallback resultListener, long imageTimeStamp) {
        double expectedDurationMs = 1000.0 / mVideoFrameRate;
        CaptureResult prevResult = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        long prevTS = getValueNotNull(prevResult, CaptureResult.SENSOR_TIMESTAMP);
        while (resultListener.hasMoreResults()) {
            CaptureResult currentResult =
                    resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            long currentTS = getValueNotNull(currentResult, CaptureResult.SENSOR_TIMESTAMP);
            if (currentTS == imageTimeStamp) {
                // validate the timestamp before and after, then return
                CaptureResult nextResult =
                        resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
                long nextTS = getValueNotNull(nextResult, CaptureResult.SENSOR_TIMESTAMP);
                double durationMs = (currentTS - prevTS) / 1000000.0;
                int totalFramesDropped = 0;

                // Snapshots in legacy mode pause the preview briefly.  Skip the duration
                // requirements for legacy mode unless this is fixed.
                if (!mStaticInfo.isHardwareLevelLegacy()) {
                    mCollector.expectTrue(
                            String.format(
                                    "Video %dx%d Frame drop detected before video snapshot: " +
                                            "duration %.2fms (expected %.2fms)",
                                    mVideoSize.getWidth(), mVideoSize.getHeight(),
                                    durationMs, expectedDurationMs
                            ),
                            durationMs <= (expectedDurationMs * MAX_NUM_FRAME_DROP_INTERVAL_ALLOWED)
                    );
                    // Log a warning is there is any frame drop detected.
                    if (durationMs >= expectedDurationMs * 2) {
                        Log.w(TAG, String.format(
                                "Video %dx%d Frame drop detected before video snapshot: " +
                                        "duration %.2fms (expected %.2fms)",
                                mVideoSize.getWidth(), mVideoSize.getHeight(),
                                durationMs, expectedDurationMs
                        ));
                    }

                    durationMs = (nextTS - currentTS) / 1000000.0;
                    mCollector.expectTrue(
                            String.format(
                                    "Video %dx%d Frame drop detected after video snapshot: " +
                                            "duration %.2fms (expected %.2fms)",
                                    mVideoSize.getWidth(), mVideoSize.getHeight(),
                                    durationMs, expectedDurationMs
                            ),
                            durationMs <= (expectedDurationMs * MAX_NUM_FRAME_DROP_INTERVAL_ALLOWED)
                    );
                    // Log a warning is there is any frame drop detected.
                    if (durationMs >= expectedDurationMs * 2) {
                        Log.w(TAG, String.format(
                                "Video %dx%d Frame drop detected after video snapshot: " +
                                        "duration %fms (expected %fms)",
                                mVideoSize.getWidth(), mVideoSize.getHeight(),
                                durationMs, expectedDurationMs
                        ));
                    }

                    double totalDurationMs = (nextTS - prevTS) / 1000000.0;
                    // Minus 2 for the expected 2 frames interval
                    totalFramesDropped = (int) (totalDurationMs / expectedDurationMs) - 2;
                    if (totalFramesDropped < 0) {
                        Log.w(TAG, "totalFrameDropped is " + totalFramesDropped +
                                ". Video frame rate might be too fast.");
                    }
                    totalFramesDropped = Math.max(0, totalFramesDropped);
                }
                return totalFramesDropped;
            }
            prevTS = currentTS;
        }
        throw new AssertionFailedError(
                "Video snapshot timestamp does not match any of capture results!");
    }

    /**
     * Validate frame jittering from the input simple listener's buffered results
     */
    private void validateJittering(SimpleCaptureCallback resultListener) {
        double expectedDurationMs = 1000.0 / mVideoFrameRate;
        CaptureResult prevResult = resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
        long prevTS = getValueNotNull(prevResult, CaptureResult.SENSOR_TIMESTAMP);
        while (resultListener.hasMoreResults()) {
            CaptureResult currentResult =
                    resultListener.getCaptureResult(WAIT_FOR_RESULT_TIMEOUT_MS);
            long currentTS = getValueNotNull(currentResult, CaptureResult.SENSOR_TIMESTAMP);
            double durationMs = (currentTS - prevTS) / 1000000.0;
            double durationError = Math.abs(durationMs - expectedDurationMs);
            long frameNumber = currentResult.getFrameNumber();
            mCollector.expectTrue(
                    String.format(
                            "Resolution %dx%d Frame %d: jittering (%.2fms) exceeds bound [%.2fms,%.2fms]",
                            mVideoSize.getWidth(), mVideoSize.getHeight(),
                            frameNumber, durationMs,
                            expectedDurationMs - FRAME_DURATION_ERROR_TOLERANCE_MS,
                            expectedDurationMs + FRAME_DURATION_ERROR_TOLERANCE_MS),
                    durationError <= FRAME_DURATION_ERROR_TOLERANCE_MS);
            prevTS = currentTS;
        }
    }

    /**
     * Calculate a video bit rate based on the size. The bit rate is scaled
     * based on ratio of video size to 1080p size.
     */
    private int getVideoBitRate(Size sz) {
        int rate = BIT_RATE_1080P;
        float scaleFactor = sz.getHeight() * sz.getWidth() / (float)(1920 * 1080);
        rate = (int)(rate * scaleFactor);

        // Clamp to the MIN, MAX range.
        return Math.max(BIT_RATE_MIN, Math.min(BIT_RATE_MAX, rate));
    }

    /**
     * Check if the encoder and camera are able to support this size and frame rate.
     * Assume the video compression format is AVC.
     */
    private boolean isSupported(Size sz, int captureRate, int encodingRate) throws Exception {
        // Check camera capability.
        if (!isSupportedByCamera(sz, captureRate)) {
            return false;
        }

        // Check encode capability.
        if (!isSupportedByAVCEncoder(sz, encodingRate)){
            return false;
        }

        if(VERBOSE) {
            Log.v(TAG, "Both encoder and camera support " + sz.toString() + "@" + encodingRate + "@"
                    + getVideoBitRate(sz) / 1000 + "Kbps");
        }

        return true;
    }

    private boolean isSupportedByCamera(Size sz, int frameRate) {
        // Check if camera can support this sz and frame rate combination.
        StreamConfigurationMap config = mStaticInfo.
                getValueFromKeyNonNull(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        long minDuration = config.getOutputMinFrameDuration(MediaRecorder.class, sz);
        if (minDuration == 0) {
            return false;
        }

        int maxFrameRate = (int) (1e9f / minDuration);
        return maxFrameRate >= frameRate;
    }

    /**
     * Check if encoder can support this size and frame rate combination by querying
     * MediaCodec capability. Check is based on size and frame rate. Ignore the bit rate
     * as the bit rates targeted in this test are well below the bit rate max value specified
     * by AVC specification for certain level.
     */
    private static boolean isSupportedByAVCEncoder(Size sz, int frameRate) {
        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC, sz.getWidth(), sz.getHeight());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        return mcl.findEncoderForFormat(format) != null;
    }

    private static class ImageWriterQueuer implements ImageReader.OnImageAvailableListener {
        public ImageWriterQueuer(ImageWriter writer) {
            mWriter = writer;
        }

        public void resetInvalidSurfaceFlag() {
            synchronized (mLock) {
                mExpectInvalidSurface = false;
            }
        }

        // Indicate that the writer surface is about to get released
        // and become invalid.
        public void expectInvalidSurface() {
            // If we sync on 'mLock', we risk a possible deadlock
            // during 'mWriter.queueInputImage(image)' which is
            // called while the lock is held.
            mExpectInvalidSurface = true;
        }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireNextImage();
            } finally {
                synchronized (mLock) {
                    if (image != null && mWriter != null) {
                        try {
                            mWriter.queueInputImage(image);
                            mQueuedCount++;
                        } catch (IllegalStateException e) {
                            // Per API documentation ISE are possible
                            // in case the writer surface is not valid.
                            // Re-throw in case we have some other
                            // unexpected ISE.
                            if (mExpectInvalidSurface) {
                                Log.d(TAG, "Invalid writer surface");
                                image.close();
                            } else {
                                throw e;
                            }
                        }
                    } else if (image != null) {
                        image.close();
                    }
                }
            }
        }

        public int getQueuedCount() {
            synchronized (mLock) {
                return mQueuedCount;
            }
        }

        public void close() {
            synchronized (mLock) {
                mWriter = null;
            }
        }

        private Object      mLock = new Object();
        private ImageWriter mWriter = null;
        private int         mQueuedCount = 0;
        private boolean     mExpectInvalidSurface = false;
    }
}
