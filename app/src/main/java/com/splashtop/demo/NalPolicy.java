package com.splashtop.demo;

import java.nio.ByteBuffer;

/*
 * NALU (NAL unit) split policy
 *
 * Work with NalParser, provide policy to split the nals buffer, can merge some nails
 *
 * Example NALU stream: NAL-A, NAL-B, NAL-C
 *
 * onNal(NAL-A): Return CUT
 * onNal(NAL-B): Return CUT
 * onNal(NAL-C): Return CUT
 * Will split into 3 buffers: NAL-A, NAL-B, NAL-C
 *
 * onNal(NAL-A): Return CONTINUE
 * onNal(NAL-B): Return CUT
 * onNal(NAL-C): Return CUT
 * Will split into 2 buffers: NAL-A NAL-B, NAL-C
 *
 * onNal(NAL-A): Return CUT
 * onNal(NAL-B): Return CONTINUE
 * onNal(NAL-C): Return CUT
 * Will split into 2 buffers: NAL-A, NAL-B NAL-C
 *
 * onNal(NAL-A): Return CUT
 * onNal(NAL-B): Return SKIP
 * onNal(NAL-C): Return CUT
 * Will split into 2 buffers: NAL-A, NAL-C
 */
public interface NalPolicy {

    enum Policy { CONTINUE, CUT, SKIP }

    Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len);

    class Wrapper implements NalPolicy {

        private final NalPolicy mDelegate;
        private final Policy mDefaultPolicy;

        public Wrapper(NalPolicy delegate) {
            this(delegate, Policy.CUT);
        }

        public Wrapper(NalPolicy delegate, Policy policy) {
            mDelegate = delegate;
            mDefaultPolicy = policy;
        }

        @Override
        public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
            return (mDelegate != null) ? mDelegate.onNal(hdr, buffer, offset, len) : mDefaultPolicy;
        }
    }

    interface Factory {
        NalPolicy create();
    }
}
