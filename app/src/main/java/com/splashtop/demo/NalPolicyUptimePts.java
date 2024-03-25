package com.splashtop.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

// Convert timestamp to uptime based PTS (System.nanoTime is system uptime in nano seconds)
// Force re-sync the clock if receive IFrame or detect timestamp jumping
public class NalPolicyUptimePts extends NalPolicy.Wrapper {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private long mClockDiffUs;

    public NalPolicyUptimePts(NalPolicy delegate) {
        super(delegate);
    }

    @Override
    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
        Policy policy = super.onNal(hdr, buffer, offset, len);
        if (hdr.pts != 0) {
            sLogger.trace("FROM pts:{}", hdr.pts);
            long nowNs  = System.nanoTime(); // NanoSeconds 10^-9
            long diffUs = hdr.pts - nowNs / 1000; // Convert to MacroSecond 10^-6
            if (Math.abs(mClockDiffUs - diffUs) > 1000000 || hdr.type == NalParser.NalType.NAL_IDR_SLICE) {
                mClockDiffUs = diffUs; // Force sync clock for IDR frame or detect pts jump for 1s
                sLogger.debug("SYNC pts:{} nowNs:{}",
                        hdr.pts,
                        nowNs);
            }
            hdr.pts = (hdr.pts - mClockDiffUs);
            sLogger.trace("TO pts:{}", hdr.pts);
        }
        return policy;
    }
}
