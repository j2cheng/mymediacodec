package com.splashtop.demo;

import java.nio.ByteBuffer;

// Generate PTS in fixed frame rate, start from 0.0, simulate media-time pts
public class NalPolicyFixedRate extends NalPolicy.Wrapper {

    private long mFrameIntervalUs; // Microsecond 10^-6
    private long mLastPtsUs;

    public NalPolicyFixedRate(NalPolicy delegate, int fps) {
        super(delegate);
        setFps(fps);
    }

    public NalPolicyFixedRate setFps(int fps) {
        mFrameIntervalUs = (fps > 0) ? (long) ((1.0 / fps) * 1000000) : 0;
        return this;
    }

    @Override
    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
        Policy policy = super.onNal(hdr, buffer, offset, len);
        if (hdr != null) {
            if (hdr.type != NalParser.NalType.NAL_PPS && hdr.type != NalParser.NalType.NAL_SPS) {
                hdr.pts = (mLastPtsUs + mFrameIntervalUs);
                mLastPtsUs = mLastPtsUs + mFrameIntervalUs;
            }
        }
        return policy;
    }
}
