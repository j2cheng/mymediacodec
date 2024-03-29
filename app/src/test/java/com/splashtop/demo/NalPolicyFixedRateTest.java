package com.splashtop.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class NalPolicyFixedRateTest {

    @Test
    public void testWrapper() {
        // Should pass through wrapped policy
        NalPolicy delegate = mock(NalPolicy.class);
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                NalParser.NalHeader header = (NalParser.NalHeader) invocation.getArguments()[0];
                header.type = NalParser.NalType.NAL_IDR_SLICE;
                header.size = 10;
                return null;
            }
        }).when(delegate)
                .onNal(any(NalParser.NalHeader.class), any(), anyInt(), anyInt());

        NalPolicy policy = new NalPolicyFixedRate(delegate, 0);

        NalParser.NalHeader header = new NalParser.NalHeader();
        policy.onNal(header, null, 0, 0);

        // Verify the type and size only, pts will be override
        assertEquals(10, header.size);
        assertEquals(NalParser.NalType.NAL_IDR_SLICE, header.type);
    }

    @Test
    public void testPts() {
        NalPolicy policy = new NalPolicyFixedRate(null, 10); // 100ms interval
        NalParser.NalHeader header = new NalParser.NalHeader();
        header.type = NalParser.NalType.NAL_SPS;

        long lastPtsUs = 0;
        long diffPtsUs = 0;
        policy.onNal(header, null, 0, 0);
        assertEquals(0, header.pts); // SPS & PPS will provide 0 pts

        header.type = NalParser.NalType.NAL_IDR_SLICE;
        policy.onNal(header, null, 0, 0);
        assertTrue(header.pts != 0); // IDR will provide valid pts

        lastPtsUs = header.pts;
        header.type = NalParser.NalType.NAL_SLICE;
        policy.onNal(header, null, 0, 0);
        diffPtsUs = header.pts - lastPtsUs;
        assertTrue(diffPtsUs != 0);
        assertTrue(Math.abs(diffPtsUs - 100000) <= 10000); // 100ms

        lastPtsUs = header.pts;
        policy.onNal(header, null, 0, 0);
        diffPtsUs = header.pts - lastPtsUs;
        assertTrue(diffPtsUs != 0);
        assertTrue(Math.abs(diffPtsUs - 100000) <= 10000); // 100ms
    }
}
