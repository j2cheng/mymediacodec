package com.splashtop.demo;

import java.nio.ByteBuffer;

// Use timestamp as DTS, slow down feeding NALU
// Support sending NAL a little bit early before the DTS
// Simulate mirror stream will always provide a future timestamp for sync with audio playback
public class NalPolicyUptimeDts extends NalPolicy.Wrapper {

    private final long mAdjustNs; // Feed decoder before the timestamp a little bit
    private long mClockDiffNs; // Nanosecond 10^-9

    public NalPolicyUptimeDts(NalPolicy delegate) {
        this(delegate, 0);
    }

    public NalPolicyUptimeDts(NalPolicy delegate, long adjustMs) {
        super(delegate);
        mAdjustNs = adjustMs * 1000000;
    }

    @Override
    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
        Policy policy = super.onNal(hdr, buffer, offset, len);
        long nowNs = System.nanoTime();
        if (mClockDiffNs == 0 || hdr.type == NalParser.NalType.NAL_IDR_SLICE) {
            mClockDiffNs = hdr.pts * 1000 - nowNs;
        }
        long diffNs = hdr.pts * 1000 - mClockDiffNs - mAdjustNs;
        if (diffNs > nowNs) {
            try {
                Thread.sleep((diffNs - nowNs) / 1000000); // Convert to millisecond (10^-3)
            } catch (InterruptedException ex) {
            }
        }
        return policy;
    }
}
