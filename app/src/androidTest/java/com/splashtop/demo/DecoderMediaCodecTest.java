package com.splashtop.demo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

@RunWith(AndroidJUnit4.class)
public class DecoderMediaCodecTest {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Test");

    @Rule
    public ActivityTestRule<DecoderOutputActivity> mActivityRule = new ActivityTestRule<>(DecoderOutputActivity.class);

    private DecoderOutputActivity mActivity;
    private SurfaceView mView;

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testInputZeroPtsOutputAll() {
        DecoderInputAssets.sPolicyFactory = new DecoderInputAssets.IndexPolicyFactory() {
            @Override
            public NalPolicy create() {
                NalPolicy policy = super.create(); // Load assets index
                policy = new NalPolicy.Wrapper(policy) { // Overwrite pts with 0
                    @Override
                    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                        Policy policy = super.onNal(hdr, buffer, offset, len);
                        hdr.pts = 0;
                        return policy;
                    }
                };
                return policy;
            }
        };

        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DecoderInput input = new DecoderInputFrameLimit(new DecoderInputAssets(ctx), 180); // limit 180 frame, about 3s
        decodeToSurfaceView(input, DecoderOutput.ALLOW_ALL);
    }

    @Test
    public void testInputUptimeOutputDropTooLateFrame() {
        DecoderInputAssets.sPolicyFactory = new DecoderInputAssets.IndexPolicyFactory() {
            @Override
            public NalPolicy create() {
                NalPolicy policy = super.create(); // Load assets index
                policy = new NalPolicyFixedRate(policy, 60); // Overwrite pts with fixed interval, start from 0
                policy = new NalPolicyUptimeDts(policy); // Use timestamp as dts, sleep to the dts
                policy = new NalPolicyUptimePts(policy); // Convert pts to current uptime based
                return policy;
            }
        };

        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DecoderInput  input  = new DecoderInputFrameLimit(new DecoderInputAssets(ctx), 180); // limit 180 frame, about 3s
        DecoderOutput output = new DecoderOutput() {
            private long mLastPtsUs;
            @Override
            public void onFormat(Decoder decoder, Decoder.VideoFormat format) {
            }
            @Override
            public boolean onBuffer(Decoder decoder, Decoder.VideoBufferInfo info, ByteBuffer buffer) {
                boolean allowDraw = true;
                long now = SystemClock.uptimeMillis();
                long diff = info.pts - now * 1000;
                if (info.pts > 0 && mLastPtsUs > 0) {
                    if (diff < -1000000) { // drop too late frames
                        // at least 1 frame per second
                        allowDraw = (Math.abs(info.pts - mLastPtsUs) > 1000000);
                    }
                }
                if (allowDraw) {
                    mLastPtsUs = info.pts;
                }
                return allowDraw;
            }
            @Override
            public void onEnd(Decoder decoder) {
            }
        };
        decodeToSurfaceView(input, output);
    }

    @Test
    public void testInputPtsJumpForwardOutputAll() {
        DecoderInputAssets.sPolicyFactory = new DecoderInputAssets.IndexPolicyFactory() {
            @Override
            public NalPolicy create() {
                NalPolicy policy = super.create(); // Load assets index
                policy = new NalPolicyFixedRate(policy, 60); // Overwrite pts with fixed interval, start from 0
                policy = new NalPolicyUptimeDts(policy); // Use timestamp as dts, sleep to the dts
                policy = new NalPolicy.Wrapper(policy) { // Overwrite pts, pause 2s after send 60 frames
                    private int mCount;
                    private boolean mPrint; // Print log only once, avoid too noisy
                    @Override
                    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                        Policy policy = super.onNal(hdr, buffer, offset, len);
                        if (mCount++ > 60) { // after 60 frame, force shift 2s forward
                            hdr.pts += 2000000;
                            if (!mPrint) {
                                mPrint = true;
                                sLogger.info("Jump forward");
                            }
                        }
                        return policy;
                    }
                };
                return policy;
            }
        };

        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DecoderInput input = new DecoderInputFrameLimit(new DecoderInputAssets(ctx), 120); // limit 120 frame, about 2s
        decodeToSurfaceView(input, DecoderOutput.ALLOW_ALL);
    }

    @Test
    public void testInputPtsJumpBackwardOutputAll() {
        DecoderInputAssets.sPolicyFactory = new DecoderInputAssets.IndexPolicyFactory() {
            @Override
            public NalPolicy create() {
                NalPolicy policy = super.create(); // Load assets index
                policy = new NalPolicyFixedRate(policy, 60); // Overwrite pts with fixed interval, start from 0
                policy = new NalPolicyUptimeDts(policy); // Use timestamp as dts, sleep to the dts
                policy = new NalPolicy.Wrapper(policy) { // Overwrite pts, pause 3s after send 100 frames
                    private int mCount;
                    @Override
                    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                        Policy policy = super.onNal(hdr, buffer, offset, len);
                        if (mCount++ % 60 == 0) { // every 60 frame, force jump the pts backward 0.066s, next frame will resume back
                            hdr.pts -= 66000;
                            sLogger.info("Jump backward");
                        }
                        return policy;
                    }
                };
                return policy;
            }
        };

        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DecoderInput input = new DecoderInputFrameLimit(new DecoderInputAssets(ctx), 180); // limit 180 frame, about 3s
        decodeToSurfaceView(input, DecoderOutput.ALLOW_ALL);
    }

    private void decodeToSurfaceView(DecoderInput input, DecoderOutput output) {
        final Instrumentation ins = InstrumentationRegistry.getInstrumentation();
        final Context ctx = ins.getContext();

        // Create a SurfaceView and set as content
        final SurfaceHolder.Callback cb = mock(SurfaceHolder.Callback.class);
        ins.runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mView = new SurfaceView(mActivity); // SurfaceView must create by UI thread
                mView.getHolder().addCallback(cb);
                mActivity.setContentView(mView);
            }
        });

        // Get surface
        ArgumentCaptor<SurfaceHolder> holder  = ArgumentCaptor.forClass(SurfaceHolder.class);
        verify(cb, timeout(5000)).surfaceCreated(holder.capture());
        Surface surface = holder.getValue().getSurface();

        // Get surface size
        ArgumentCaptor<Integer> width  = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> height = ArgumentCaptor.forClass(Integer.class);
        verify(cb, timeout(5000)).surfaceChanged(any(SurfaceHolder.class), anyInt(), width.capture(), height.capture());
        final int surfaceWidth  = width.getValue();
        final int surfaceHeight = height.getValue();

        // Prepare decoder, assemble with video input and output callback
        // The decoder actually works in surface mode, output callback just for decide draw or not onto the surface
        sLogger.info("JRC test create decoder and calling attachSurface");

        DecoderOutput out = spy(new DecoderOutput.Wrapper(output));
        Decoder decoder = new DecoderMediaCodec(ctx)
                .setInput(input)
                .setOutput(out);
        decoder.attachSurface(surface);
        decoder.start();

        // Auto scale the video within the SurfaceView
        ArgumentCaptor<Decoder.VideoFormat> format = ArgumentCaptor.forClass(Decoder.VideoFormat.class);
        verify(out, timeout(5000).times(2)).onFormat(any(Decoder.class), format.capture());
        ins.runOnMainSync(new SurfaceViewTransformTask(mView)
                .setSurfaceSize(surfaceWidth, surfaceHeight)
                .setVideoSize(format.getValue().width, format.getValue().height));

        // Wait EOS up to 15s, should long enough
        verify(out, timeout(15 * 1000)).onEnd(any(Decoder.class));
    }

    // Transform the view, scale video to full-fill the view but keep origin ratio
    static class SurfaceViewTransformTask implements Runnable {
        private final SurfaceView mView;
        private int mVideoWidth;
        private int mVideoHeight;
        private int mSurfaceWidth;
        private int mSurfaceHeight;
        public SurfaceViewTransformTask(@NonNull SurfaceView view) {
            mView = view;
        }
        public SurfaceViewTransformTask setVideoSize(int w, int h) {
            mVideoWidth  = w;
            mVideoHeight = h;
            return this;
        }
        public SurfaceViewTransformTask setSurfaceSize(int w, int h) {
            mSurfaceWidth  = w;
            mSurfaceHeight = h;
            return this;
        }
        @Override
        public void run() {
            float scale = Math.min((float) mSurfaceWidth / mVideoWidth, (float) mSurfaceHeight / mVideoHeight);
            float scaleX = scale * mVideoWidth / mSurfaceWidth;
            float scaleY = scale * mVideoHeight / mSurfaceHeight;

            mView.setTranslationX((mSurfaceWidth - scale * mVideoWidth) / 2);
            mView.setTranslationY((mSurfaceHeight - scale * mVideoHeight) / 2);
            mView.setPivotX(0);
            mView.setPivotY(0);
            mView.setScaleX(scaleX);
            mView.setScaleY(scaleY);
            mView.invalidate();
        }
    }

    // Limit the total frames input for Decoder
    static class DecoderInputFrameLimit extends DecoderInput.Wrapper {
        private final int mLimit;
        private int mCount;
        public DecoderInputFrameLimit(DecoderInput input, int limit) {
            super(input);
            mLimit = limit;
        }
        @Override
        public Decoder.VideoBufferInfo readBuffer(@NonNull Decoder decoder, @NonNull ByteBuffer buffer) {
            return (mCount++ < mLimit) ? super.readBuffer(decoder, buffer) : null;
        }
    }
}
