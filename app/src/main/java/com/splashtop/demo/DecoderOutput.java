package com.splashtop.demo;

import java.nio.ByteBuffer;

/**
 * Video frames output interface
 */
public interface DecoderOutput {

    /**
     * Notify when video format changed, usually for new video stream started
     * @param decoder
     * @param format
     */
    void onFormat(Decoder decoder, Decoder.VideoFormat format);

    /**
     * Notify when video frame available
     * @param decoder
     * @param info
     * @param buffer  DirectByteBuffer for valid frames
     *                May be null if flags contain EOS or works in Surface mode
     * @return Allow draw in Surface mode
     */
    boolean onBuffer(Decoder decoder, Decoder.VideoBufferInfo info, ByteBuffer buffer);

    /**
     * Notify when video stop playing
     * @param decoder
     */
    void onEnd(Decoder decoder);

    class Wrapper implements DecoderOutput {

        private final DecoderOutput mOutput;

        public Wrapper(DecoderOutput output) {
            mOutput = output;
        }

        @Override
        public void onFormat(Decoder decoder, Decoder.VideoFormat format) {
            if (mOutput != null) {
                mOutput.onFormat(decoder, format);
            }
        }

        @Override
        public boolean onBuffer(Decoder decoder, Decoder.VideoBufferInfo info, ByteBuffer buffer) {
            return (mOutput != null) && mOutput.onBuffer(decoder, info, buffer);
        }

        @Override
        public void onEnd(Decoder decoder) {
            if (mOutput != null) {
                mOutput.onEnd(decoder);
            }
        }
    }

    // Default output policy, allow draw all frames
    DecoderOutput ALLOW_ALL = new DecoderOutput() {
        @Override
        public void onFormat(Decoder decoder, Decoder.VideoFormat format) {
        }
        @Override
        public boolean onBuffer(Decoder decoder, Decoder.VideoBufferInfo info, ByteBuffer buffer) {
            return true;
        }
        @Override
        public void onEnd(Decoder decoder) {
        }
    };
}
