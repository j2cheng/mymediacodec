package com.splashtop.demo;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class NalPolicyUptimeDtsTest {

    private final NalPolicy mDefaultPolicy = mock(NalPolicy.class);

    public NalPolicyUptimeDtsTest() {
        // Default generate
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                NalParser.NalHeader header = (NalParser.NalHeader) invocation.getArguments()[0];
                header.pts  = header.pts + 100000; // increase 100 millisecond
                return null;
            }
        }).when(mDefaultPolicy)
                .onNal(any(NalParser.NalHeader.class), any(), anyInt(), anyInt());
    }

    @Test
    public void testDtsDefault() {
        NalPolicy policy = new NalPolicyUptimeDts(mDefaultPolicy);
        NalParser.NalHeader header = new NalParser.NalHeader();
        header.type = NalParser.NalType.NAL_IDR_SLICE; // Send IDR to sync clock

        long startTime = System.currentTimeMillis();
        policy.onNal(header, null, 0, 0);
        long endTime = System.currentTimeMillis();
        assertTrue(Math.abs(endTime - startTime) < 10); // No delay, return within 10ms

        header.type = NalParser.NalType.NAL_SLICE; // Send SLICE will auto delay
        for (int i = 0; i < 3; i++) {
            startTime = endTime;
            policy.onNal(header, null, 0, 0);
            endTime = System.currentTimeMillis();
            assertTrue(Math.abs(endTime - startTime - 100) < 10); // About 100ms
        }
    }

    @Test
    public void testDtsWithAdjust() {
        NalPolicy policy = new NalPolicyUptimeDts(mDefaultPolicy, 60);
        NalParser.NalHeader header = new NalParser.NalHeader();
        header.type = NalParser.NalType.NAL_IDR_SLICE; // Send IDR to sync clock

        long startTime = System.currentTimeMillis();
        policy.onNal(header, null, 0, 0);
        long endTime = System.currentTimeMillis();
        assertTrue(Math.abs(endTime - startTime) < 10); // No delay, return within 10ms

        header.type = NalParser.NalType.NAL_SLICE; // Send SLICE will auto delay
        startTime = endTime;
        policy.onNal(header, null, 0, 0);
        endTime = System.currentTimeMillis();
        assertTrue(Math.abs(endTime - startTime) - 40 < 10); // Delay 40ms

        for (int i = 0; i < 3; i++) {
            startTime = endTime;
            policy.onNal(header, null, 0, 0);
            endTime = System.currentTimeMillis();
            assertTrue(Math.abs(endTime - startTime - 100) < 10); // About 100ms
        }
    }
}
