package com.splashtop.demo;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public interface DecoderInput {

    /**
     * Read video format
     *
     * @return format
     */
    Decoder.VideoFormat readFormat(@NonNull Decoder decoder);

    /**
     * Read video frame
     *
     * @return info
     */
    Decoder.VideoBufferInfo readBuffer(@NonNull Decoder decoder, @NonNull ByteBuffer buffer);

    class Wrapper implements DecoderInput {

        private final DecoderInput mInput;

        public Wrapper(DecoderInput input) {
            mInput = input;
        }

        @CallSuper
        @Override
        public Decoder.VideoFormat readFormat(@NonNull Decoder decoder) {
            if (mInput != null) {
                return mInput.readFormat(decoder);
            }
            return null;
        }

        @CallSuper
        @Override
        public Decoder.VideoBufferInfo readBuffer(@NonNull Decoder decoder, @NonNull ByteBuffer buffer) {
            if (mInput != null) {
                return mInput.readBuffer(decoder, buffer);
            }
            return null;
        }
    }
}
