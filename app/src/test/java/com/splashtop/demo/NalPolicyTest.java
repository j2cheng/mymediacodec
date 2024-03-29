package com.splashtop.demo;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NalPolicyTest {

    private final byte[] mBuffer = new byte[] {
            // SPS 23 bytes
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x27, (byte)0x64, (byte)0x00, (byte)0x20,
            (byte)0xac, (byte)0x56, (byte)0x50, (byte)0x2d, (byte)0x02, (byte)0x86, (byte)0x9b, (byte)0x81,
            (byte)0x01, (byte)0x01, (byte)0x03, (byte)0x68, (byte)0x22, (byte)0x11, (byte)0x96,

            // PPS 8 bytes
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x28, (byte)0xee, (byte)0x37, (byte)0x27,

            // SEI 34 bytes
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x06, (byte)0x05, (byte)0x1a, (byte)0x47,
            (byte)0x56, (byte)0x4a, (byte)0xdc, (byte)0x5c, (byte)0x4c, (byte)0x43, (byte)0x3f, (byte)0x94,
            (byte)0xef, (byte)0xc5, (byte)0x11, (byte)0x3c, (byte)0xd1, (byte)0x43, (byte)0xa8, (byte)0x01,
            (byte)0xdd, (byte)0xcc, (byte)0xcc, (byte)0xdd, (byte)0x02, (byte)0x00, (byte)0x7a, (byte)0x12,
            (byte)0x00, (byte)0x80,

            // IDR 32 bytes
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x25, (byte)0xb8, (byte)0x20, (byte)0x02,
            (byte)0xbf, (byte)0xbe, (byte)0x99, (byte)0x45, (byte)0x91, (byte)0xe7, (byte)0x7f, (byte)0xf8,
            (byte)0x7d, (byte)0xda, (byte)0x1d, (byte)0x86, (byte)0x27, (byte)0x8a, (byte)0x98, (byte)0xab,
            (byte)0x9a, (byte)0x89, (byte)0xf2, (byte)0xf8, (byte)0x85, (byte)0x4d, (byte)0x2b, (byte)0x00,
    };

    @Test
    public void testPolicyCut() throws Exception {
        //    SPS PPS SEI IDR
        // -> SPS|PPS|SEI|IDR
        NalPolicy policy = new NalPolicy() {
            @Override
            public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                return Policy.CUT;
            }
        };

        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(mBuffer), policy);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_SPS, header.type);
        assertEquals(23, header.size);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_PPS, header.type);
        assertEquals(8, header.size);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_SEI, header.type);
        assertEquals(34, header.size);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_IDR_SLICE, header.type);
        assertEquals(32, header.size);
    }

    @Test
    public void testPolicyContinue() throws Exception {
        //    SPS PPS SEI IDR
        // -> SPS PPS|SEI IDR
        NalPolicy policy = new NalPolicy() {
            @Override
            public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                if (NalParser.NalType.NAL_SPS == hdr.type) return Policy.CONTINUE; // Merge SPS with PPS
                if (NalParser.NalType.NAL_SEI == hdr.type) return Policy.CONTINUE; // Merge SEI with IDR
                return Policy.CUT;
            }
        };

        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(mBuffer), policy);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_SPS, header.type);
        assertEquals(31, header.size);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_SEI, header.type);
        assertEquals(66, header.size);
    }

    @Test
    public void testPolicySkip() throws Exception {
        //    SPS PPS SEI IDR
        // ->             IDR
        NalPolicy policy = new NalPolicy() {
            @Override
            public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                if (NalParser.NalType.NAL_IDR_SLICE != hdr.type) return Policy.SKIP;
                return Policy.CUT;
            }
        };

        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(mBuffer), policy);

        parser.parse(header);
        assertEquals(NalParser.NalType.NAL_IDR_SLICE, header.type);
        assertEquals(32, header.size);
    }

    @Test
    public void testWrapper() {
        // Should pass through wrapped policy
        NalPolicy delegate = mock(NalPolicy.class);
        NalPolicy policy = new NalPolicy.Wrapper(delegate);

        NalParser.NalHeader header = mock(NalParser.NalHeader.class);
        ByteBuffer buffer = mock(ByteBuffer.class);
        policy.onNal(header, buffer, 10, 20);
        verify(delegate).onNal(eq(header), eq(buffer), eq(10), eq(20));
    }
}
