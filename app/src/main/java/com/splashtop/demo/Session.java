package com.splashtop.demo;

import android.os.Handler;
import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public class Session implements DecoderOutput {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    public interface OnStopListener {
        void onStop(Session session);
    }

    private final Handler mHandler;
    private final Decoder mDecoder;
    private boolean mStarted;
    private OnStopListener mStopListener;

    public Session(Decoder decoder) {
        sLogger.trace("decoder:{}", decoder);
        mHandler = new Handler();
        mDecoder = decoder;
        mDecoder.setOutput(this);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        sLogger.trace("");
        mDecoder.setOutput(null);
    }

    @Override // DecoderOutput
    public void onFormat(Decoder decoder, Decoder.VideoFormat format) {
        sLogger.trace("fmt:{}", format);
    }

    @Override // DecoderOutput
    public boolean onBuffer(Decoder decoder, Decoder.VideoBufferInfo info, ByteBuffer buffer) {
        sLogger.trace("info:{}", info);
        return true;
    }

    @Override
    public void onEnd(Decoder decoder) {
        // Called by decoder output thread
        // Can not call stop decoder here for dead lock
        // Stop decoder will wait input thread quit, input thread will wait output thread quit
        // Post to handler to avoid dead lock
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                sLogger.trace("onVideoEnd");
                stop();
                if (mStopListener != null) {
                    mStopListener.onStop(Session.this);
                }
            }
        });
    }

    public void start() {
        sLogger.trace("");
        if (!mStarted) {
            mStarted = true;
            mDecoder.start();
        }
    }

    public void stop() {
        sLogger.trace("");
        if (mStarted) {
            mStarted = false;
            mDecoder.stop();
        }
    }

    public Session setOnStopListener(OnStopListener listener) {
        mStopListener = listener;
        return this;
    }

    protected void postSurfaceCreate(Surface surface) {
        sLogger.trace("surface:{}", surface);
        mDecoder.attachSurface(surface);
    }

    protected void postSurfaceDestroy(Surface surface) {
        sLogger.trace("surface:{}", surface);
        mDecoder.detachSurface(surface);
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("<0x");
        b.append(Integer.toHexString(hashCode()));
        b.append(" started:" + mStarted);
        b.append(">");
        return b.toString();
    }
}
