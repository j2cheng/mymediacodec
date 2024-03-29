package com.splashtop.demo;

import android.content.Context;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class DecoderInputAssets implements DecoderInput {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    public static class IndexPolicyFactory implements NalPolicy.Factory {
        private InputStream mStream;
        public IndexPolicyFactory input(InputStream stream) {
            mStream = stream;
            return this;
        }
        public NalPolicy create() {
            return new NalPolicyIndex(mStream);
        }
    }
    public static IndexPolicyFactory sPolicyFactory = new IndexPolicyFactory();

    private final Context mContext;
    private NalParser mParser;

    public DecoderInputAssets(Context ctx) {
        sLogger.trace("");
        mContext = ctx;
    }

    @Override
    public Decoder.VideoFormat readFormat(@NonNull Decoder decoder) {
        sLogger.trace("");
        Decoder.VideoFormat fmt = new Decoder.VideoFormat();
        fmt.width  = 720;
        fmt.height = 1280;
        fmt.rotate = 0;
        try {
            fmt.width  = (Integer) BuildConfig.class.getField("H264_WIDTH").get(BuildConfig.class);
            fmt.height = (Integer) BuildConfig.class.getField("H264_HEIGHT").get(BuildConfig.class);
        } catch (Exception ex) {
            sLogger.warn("Failed to load width and height - {}", ex.getMessage());
        }
        sLogger.debug("fmt:{}", fmt);

        try {
            InputStream data  = mContext.getAssets().open((String) BuildConfig.class.getField("H264_DATA").get(BuildConfig.class));
            InputStream index = mContext.getAssets().open((String) BuildConfig.class.getField("H264_INDEX").get(BuildConfig.class));
            mParser = new NalParser(data, sPolicyFactory.input(index).create());
        } catch (Exception ex) {
            sLogger.warn("Failed to open ");
        }
        return fmt;
    }

    @Override
    public Decoder.VideoBufferInfo readBuffer(@NonNull Decoder decoder, @NonNull ByteBuffer buffer) {
        NalParser.NalHeader header = new NalParser.NalHeader();
        try {
            ByteBuffer data = mParser.parse(header);
            if (data != null) {
                Decoder.VideoBufferInfo info = new Decoder.VideoBufferInfo();
                info.offset = 0;
                info.size   = header.size;
                info.pts    = header.pts;
                info.flags  = Decoder.VideoBufferInfo.FLAG_FRAME;
                switch (header.type) {
                case NAL_SEI: // SEI usually combined with IDR frame
                case NAL_IDR_SLICE:
                    info.flags = Decoder.VideoBufferInfo.FLAG_KEYFRAME;
                    break;
                case NAL_SPS:
                case NAL_PPS:
                    info.flags = Decoder.VideoBufferInfo.FLAG_CONFIG;
                    break;
                }

                buffer.rewind();
                buffer.put(data);
                return info;
            }
        } catch (IOException ex) {
            sLogger.info("Failed to read buffer - {}", ex.getMessage());
        }
        return null;
    }
}
