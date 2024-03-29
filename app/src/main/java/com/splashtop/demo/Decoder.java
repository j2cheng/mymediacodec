package com.splashtop.demo;

import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

public abstract class Decoder {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private DecoderInput  mInput;
    private DecoderOutput mOutput;
    private VideoFormat   mFormat;

    public Decoder setInput(DecoderInput input) {
        sLogger.trace("input:{}", input);
        mInput = input;
        return this;
    }

    public Decoder setOutput(DecoderOutput output) {
        sLogger.trace("output:{}", output);
        mOutput = output;
        if (mOutput != null && mFormat != null) {
            mOutput.onFormat(this, mFormat);
        }
        return this;
    }

    protected VideoFormat readInputFormat() {
        return (mInput != null) ? mInput.readFormat(this) : null;
    }

    protected VideoBufferInfo readInputBuffer(ByteBuffer buffer) {
        return (mInput != null) ? mInput.readBuffer(this, buffer) : null;
    }

    protected void writeOutputFormat(VideoFormat format) {
        if (mOutput != null) {
            mOutput.onFormat(this, format);
        }
        mFormat = format;
    }

    protected boolean writeOutputBuffer(VideoBufferInfo info, ByteBuffer buffer) {
        return (mOutput != null) && mOutput.onBuffer(this, info, buffer);
    }

    protected void writeOutputEnd() {
        if (mOutput != null) {
            mOutput.onEnd(this);
        }
    }

    public abstract void attachSurface(Surface surface);
    public abstract void detachSurface(Surface surface);

    public abstract void start();
    public abstract void stop();

    public static class VideoFormat {
        public int width;
        public int height;
        public int rotate;
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("<0x");
            b.append(Integer.toHexString(hashCode()));
            b.append(" width:" + width);
            b.append(" height:" + height);
            b.append(" rotate:" + rotate);
            b.append(">");
            return b.toString();
        }
    }

    public static class VideoBufferInfo {
        public static final int FLAG_FRAME    = 0;
        public static final int FLAG_KEYFRAME = 1;
        public static final int FLAG_CONFIG   = 2;
        public static final int FLAG_EOS      = 3;
        public int offset;
        public int size;
        public long pts;
        public int flags;
        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("<0x");
            b.append(Integer.toHexString(hashCode()));
            b.append(" offset:" + offset);
            b.append(" size:" + size);
            b.append(" pts:" + pts);
            b.append(" flags:" + flags);
            b.append(">");
            return b.toString();
        }
    }
}
