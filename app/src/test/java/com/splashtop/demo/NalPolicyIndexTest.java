package com.splashtop.demo;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;

public class NalPolicyIndexTest {

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
    public void testIndex1() throws Exception {
        // Index 31 - 34 - 32, merge SPS with PPS, leave SEI and IDR separated
        //    SPS PPS SEI IDR
        // -> SPS PPS|SEI|IDR
        byte[] index = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x1F, // 31
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x22, // 34
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20, // 32
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        };
        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(mBuffer), new NalPolicyIndex(new ByteArrayInputStream(index)));

        parser.parse(header); // Parse PPS
        assertEquals(NalParser.NalType.NAL_SPS, header.type);
        assertEquals(0x1F, header.size);

        parser.parse(header); // Parse SEI
        assertEquals(NalParser.NalType.NAL_SEI, header.type);
        assertEquals(0x22, header.size);

        parser.parse(header); // Parse IDR
        assertEquals(NalParser.NalType.NAL_IDR_SLICE, header.type);
        assertEquals(0x20, header.size);
    }

    @Test
    public void testIndex2() throws Exception {
        // Index 31 - 66, merge SPS with PPS, and merge SEI with IDR
        //    SPS PPS SEI IDR
        // -> SPS PPS|SEI IDR
        byte[] index = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x1F, // 31
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x64,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x42, // 66
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0xFF,
        };
        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(mBuffer), new NalPolicyIndex(new ByteArrayInputStream(index)));

        parser.parse(header); // Parse SPS + PPS
        assertEquals(NalParser.NalType.NAL_SPS, header.type);
        assertEquals(0x1F, header.size);
        assertEquals(0x64, header.pts);

        parser.parse(header); // Parse SEI + IDR
        assertEquals(NalParser.NalType.NAL_SEI, header.type);
        assertEquals(0x42, header.size);
        assertEquals(0xFF, header.pts);
    }

    @Test
    public void testMillisecondTs() {
        byte[] index = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x11,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x22,
        };
        NalPolicyIndex policy = new NalPolicyIndex(new ByteArrayInputStream(index))
                .setPtsType(NalPolicyIndex.PtsType.MILLISECOND);

        NalParser.NalHeader header = new NalParser.NalHeader();
        policy.onNal(header, null, 0, 0x10);
        assertEquals(0x11 * 1000, header.pts);

        policy.onNal(header, null, 0, 0x20);
        assertEquals(0x22 * 1000, header.pts);
    }

    @Test
    public void testNanosecondTs() {
        byte[] index = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x10,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x86, (byte)0xA0, // 100000
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x20,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x03, (byte)0x0D, (byte)0x40, // 200000
        };
        NalPolicyIndex policy = new NalPolicyIndex(new ByteArrayInputStream(index))
                .setPtsType(NalPolicyIndex.PtsType.NANOSECOND);

        NalParser.NalHeader header = new NalParser.NalHeader();
        policy.onNal(header, null, 0, 0x10);
        assertEquals(100, header.pts);

        policy.onNal(header, null, 0, 0x20);
        assertEquals(200, header.pts);
    }
}
