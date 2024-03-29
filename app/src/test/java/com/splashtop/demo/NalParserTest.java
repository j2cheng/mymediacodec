package com.splashtop.demo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class NalParserTest {

    @Rule
    public ExpectedException mExpected = ExpectedException.none();

    @Test
    public void testParseNalType() {
        assertEquals(NalParser.NalType.NAL_SPS,         NalParser.parseNalType((byte) 0x67));
        assertEquals(NalParser.NalType.NAL_PPS,         NalParser.parseNalType((byte) 0x68));
        assertEquals(NalParser.NalType.NAL_IDR_SLICE,   NalParser.parseNalType((byte) 0x65));

        assertEquals(NalParser.NalType.NAL_IDR_SLICE,   NalParser.parseNalType((byte) 0x05));
        assertEquals(NalParser.NalType.NAL_SEI,         NalParser.parseNalType((byte) 0x06));

        assertEquals(NalParser.NalType.NAL_SLICE,       NalParser.parseNalType((byte) 0x41));
    }

    @Test
    public void testFindStartCode() {
        // 4-bytes at head
        byte[] buffer = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05
        };
        NalParser.StartCode csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertEquals(0, csd.pos);
        assertEquals(4, csd.length);

        // 4-bytes with offset
        buffer = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05,
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05
        };
        csd = NalParser.findStartCode(buffer, 4, buffer.length);
        assertEquals(8, csd.pos);
        assertEquals(4, csd.length);

        // 3-bytes at head
        buffer = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05
        };
        csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertEquals(0, csd.pos);
        assertEquals(3, csd.length);

        // 8-bytes at head
        buffer = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
                (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08,
        };
        csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertEquals(0, csd.pos);
        assertEquals(8, csd.length);

        // 4-bytes at middle
        buffer = new byte[] {
                (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
                (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08,
        };
        csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertEquals(4, csd.pos);
        assertEquals(4, csd.length);

        // 4-bytes at tail
        buffer = new byte[] {
                (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01,
        };
        csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertEquals(4, csd.pos);
        assertEquals(4, csd.length);

        // 2-bytes sequence invalid
        buffer = new byte[] {
                (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x00, (byte)0x01, (byte)0x02, (byte)0x00, (byte)0x01
        };
        csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertFalse(csd.valid());

        // 9-bytes sequence invalid
        buffer = new byte[] {
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
                (byte)0x01, (byte)0x02, (byte)0x03, (byte)0x04, (byte)0x05, (byte)0x06, (byte)0x07, (byte)0x08
        };
        csd = NalParser.findStartCode(buffer, 0, buffer.length);
        assertFalse(csd.valid());
    }

    @Test
    public void testOneSlice() throws Exception {
        // Only one slice
        byte[] buffer = new byte[] {
                // PPS 8 bytes
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x68, (byte)0xce, (byte)0x3c, (byte)0x80
        };

        ByteBuffer out;
        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(buffer));

        out = parser.parse(header);
        assertEquals(NalParser.NalType.NAL_PPS, header.type);
        assertEquals(buffer.length, header.size);
        assertEquals(header.size, out.remaining());

        out = parser.parse();
        assertNull(out);
    }

    @Test
    public void testTwoSlice() throws Exception {
        // Two slice
        byte[] buffer = new byte[] {
                // SPS 14 bytes
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x67, (byte)0x42, (byte)0x80, (byte)0x29,
                (byte)0x95, (byte)0xa0, (byte)0x14, (byte)0x01, (byte)0x6c, (byte)0x40,

                // PPS 8 bytes
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x68, (byte)0xce, (byte)0x3c, (byte)0x80
        };

        ByteBuffer out;
        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(buffer));

        out = parser.parse(header);
        assertEquals(NalParser.NalType.NAL_SPS, header.type);
        assertEquals(14, header.size);
        assertEquals(header.size, out.remaining());

        out = parser.parse(header);
        assertEquals(NalParser.NalType.NAL_PPS, header.type);
        assertEquals(8, header.size);
        assertEquals(header.size, out.remaining());

        out = parser.parse();
        assertNull(out);
    }

    @Test
    public void testPartialSlice() throws Exception {
        // Partial slice, missing the first csd
        byte[] buffer = new byte[] {
                // Invalid slice 10 bytes
                (byte)0x67, (byte)0x42, (byte)0x80, (byte)0x29, (byte)0x95, (byte)0xa0, (byte)0x14, (byte)0x01,
                (byte)0x6c, (byte)0x40,

                // PPS 8 bytes
                (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x68, (byte)0xce, (byte)0x3c, (byte)0x80
        };

        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(buffer));

        mExpected.expect(NalParser.InvalidStreamException.class);
        parser.parse(header);
        fail(); // Should not reach
    }

    @Test
    public void testInvalidSlice() throws Exception {
        // Invalid slice, no csd
        byte[] buffer = new byte[] {
                // Invalid slice 4 bytes
                (byte)0x68, (byte)0xce, (byte)0x3c, (byte)0x80
        };

        NalParser.NalHeader header = new NalParser.NalHeader();
        NalParser parser = new NalParser(new ByteArrayInputStream(buffer));

        mExpected.expect(NalParser.InvalidStreamException.class);
        parser.parse(header);
        fail(); // Should not reach
    }
}
