package com.splashtop.demo;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NalPolicyUptimePtsTest {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Test");

    private final NalPolicy mDefaultPolicy = mock(NalPolicy.class);

    public NalPolicyUptimePtsTest() {
        // Default generate timestamp 100000, 200000, 300000 ...
        doAnswer(new Answer() {
            private long count = 0;
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                NalParser.NalHeader header = (NalParser.NalHeader) invocation.getArguments()[0];
                header.pts  = ++count * 100000; // increase 100 millisecond
                return null;
            }
        }).when(mDefaultPolicy)
                .onNal(any(NalParser.NalHeader.class), any(), anyInt(), anyInt());
    }

    @Test
    public void testPtsDefault() {
        NalPolicy policy = new NalPolicyUptimePts(mDefaultPolicy);
        NalParser.NalHeader header = new NalParser.NalHeader();
        header.pts  = 0;
        header.type = NalParser.NalType.NAL_SLICE;
        long uptimeUs = System.nanoTime() / 1000;

        // First frame should always sync to current uptime
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs) < 10000);

        // Should convert to 100ms later
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs - 100000) < 10000);

        // Should convert to 200ms later
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs - 200000) < 10000);
    }

    @Test
    public void testPtsSyncIFrame() {
        NalPolicy policy = new NalPolicyUptimePts(mDefaultPolicy);
        NalParser.NalHeader header = new NalParser.NalHeader();
        header.type = NalParser.NalType.NAL_SLICE;
        long uptimeUs = System.nanoTime() / 1000;

        // First frame should always sync to current uptime
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs) < 10000);

        // IFrame should force re-sync to current uptime
        header.type = NalParser.NalType.NAL_IDR_SLICE;
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs) < 10000);
    }

    @Test
    public void testPtsSyncTimestampJump() {
        NalPolicy policy = new NalPolicyUptimePts(null);
        NalParser.NalHeader header = new NalParser.NalHeader();
        header.pts  = 100000;
        header.type = NalParser.NalType.NAL_SLICE;
        long uptimeUs = System.nanoTime() / 1000;

        // First frame should always sync to current uptime
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs) < 10000);

        // Timestamp jumping should also re-sync to current uptime
        header.pts = 2100000; // Jump for 2000000us, 2s
        policy.onNal(header, null, 0, 0);
        assertTrue(Math.abs(header.pts - uptimeUs) < 10000);
    }
}
