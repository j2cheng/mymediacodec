package com.splashtop.demo;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

// Parse H264 stream buffer, split into NALU
// TODO: Cache the buffer and wrap as ByteBuffer when return a nal, reduce buffer copy
// TODO: If provide IndexPolicy, skip the CSD matching
public class NalParser {

    public enum NalType {
        NAL_SLICE, NAL_DPA, NAL_DPB, NAL_DPC, NAL_IDR_SLICE, NAL_SEI, NAL_SPS, NAL_PPS, NAL_AUD,
        NAL_END_SEQUENCE, NAL_END_STREAM, NAL_FILLER_DATA, NAL_SPS_EXT, NAL_AUXILIARY_SLICE,
        NAL_UNKNOWN
    }

    public static class NalHeader {
        public NalType type;
        public int size;
        public long pts;
    }

    public static class InvalidStreamException extends IOException {
        private static final long serialVersionUID = 1L;
        public InvalidStreamException(String message) {
            super(message);
        }
        public InvalidStreamException(String message, Throwable t) {
            super(message, t);
        }
    }

    private static final int CSD_MIN = 3;
    private static final int CSD_MAX = 8;

    private NalPolicy mPolicy;
    private InputStream mInputStream;
    private boolean mIsEos = false; // Stream drained, should stop when parse to the buffer tail
    private byte[] mBuffer = new byte[4096]; // Readed data
    private int mDataSize = 0; // Readed data size
    private StartCode mCsdCurrent = new StartCode();  // Offset of first CSD in buffer
    private StartCode mCsdNext = new StartCode();     // Offset of next CSD in buffer

    public NalParser() {
    }

    public NalParser(InputStream stream) {
        setStream(stream);
        setPolicy(new SimpleNalPolicy());
    }

    public NalParser(InputStream stream, NalPolicy policy) {
        setStream(stream);
        setPolicy(policy);
    }

    public void setStream(InputStream stream) {
        mInputStream = stream;
    }

    public void setPolicy(NalPolicy policy) {
        mPolicy = policy;
    }

    public ByteBuffer parse() throws IOException {
        return parse(null);
    }

    public ByteBuffer parse(NalHeader nalHeader) throws IOException {
        if (mInputStream == null) {
            throw new IOException("No stream");
        }

        while (! mIsEos || mDataSize > 0) {
            // Full fill the buffer
            if (mDataSize < mBuffer.length) {
                int readed = mInputStream.read(mBuffer, mDataSize, mBuffer.length - mDataSize);
                if (readed == -1) {
                    mIsEos = true;
                } else {
                    mDataSize += readed;
                }
            }

            if (!mCsdCurrent.valid()) {
                mCsdCurrent = findStartCode(mBuffer, 0, mDataSize);

                // Not started with CSD, H264 bit stream invalid
                if (mCsdCurrent.pos != 0) throw new InvalidStreamException("Csd " + mCsdCurrent);
            }
            if (!mCsdNext.valid()) {
                mCsdNext = findStartCode(mBuffer,
                        mCsdCurrent.pos + mCsdCurrent.length,
                        mDataSize - mCsdCurrent.pos - mCsdCurrent.length * 2 + 1);

                if (!mCsdNext.valid()) {
                    if (! mIsEos && mDataSize == mBuffer.length) {
                        byte[] temp = new byte[mBuffer.length * 2];
                        System.arraycopy(mBuffer, 0, temp, 0, mBuffer.length);
                        mBuffer = temp;
                    } else {
                        mCsdNext.pos = mDataSize;
                        mCsdNext.length = 0;
                    }
                }
            }

            if (mCsdCurrent.valid() && mCsdNext.valid()) {
                if (mPolicy != null) {
                    NalHeader header = new NalHeader();
                    header.size = mCsdNext.pos - mCsdCurrent.pos;
                    header.type = parseNalType(mBuffer[mCsdCurrent.pos + mCsdCurrent.length]);
                    NalPolicy.Policy policy = mPolicy.onNal(header,
                            ByteBuffer.wrap(mBuffer, mCsdCurrent.pos, mCsdNext.pos - mCsdCurrent.pos),
                            0,
                            mCsdNext.pos - mCsdCurrent.pos);
                    switch (policy) {
                    case CONTINUE:
                        mCsdCurrent = new StartCode(mCsdNext);
                        mCsdNext.reset();
                        break;
                    case CUT:
                        int size = mCsdNext.pos;
                        if (size != 0) {
                            byte[] data = new byte[size];
                            System.arraycopy(mBuffer, 0, data, 0, size);
                            System.arraycopy(mBuffer, size, mBuffer, 0, mDataSize - size);
                            if (nalHeader != null) {
                                nalHeader.size = size;
                                nalHeader.type = parseNalType(data[mCsdCurrent.length]);
                                nalHeader.pts  = header.pts;
                            }
                            mDataSize -= size;
                            mCsdCurrent = new StartCode(mCsdNext);
                            mCsdCurrent.pos = 0;
                            mCsdNext.reset();
                            return ByteBuffer.wrap(data);
                        }
                        break;
                    case SKIP:
                        System.arraycopy(mBuffer, mCsdNext.pos, mBuffer, 0, mDataSize - mCsdNext.pos);
                        mDataSize -= mCsdNext.pos;
                        mCsdCurrent = new StartCode(mCsdNext);
                        mCsdCurrent.pos = 0;
                        mCsdNext.reset();
                        break;
                    }
                }
            }
        }
        return null;
    }

    public static NalType parseNalType(byte header) {
        byte forbidden_bit = (byte) (header & 0x80); // 0b 1000 0000
        byte nal_ref_idc   = (byte) (header & 0x60); // 0b 0110 0000
        byte nal_unit_type = (byte) (header & 0x1F); // 0b 0001 1111
        switch (nal_unit_type) {
        case 1:  return NalType.NAL_SLICE;
        case 2:  return NalType.NAL_DPA;
        case 3:  return NalType.NAL_DPB;
        case 4:  return NalType.NAL_DPC;
        case 5:  return NalType.NAL_IDR_SLICE;
        case 6:  return NalType.NAL_SEI;
        case 7:  return NalType.NAL_SPS;
        case 8:  return NalType.NAL_PPS;
        case 9:  return NalType.NAL_AUD;
        case 10: return NalType.NAL_END_SEQUENCE;
        case 11: return NalType.NAL_END_STREAM;
        case 12: return NalType.NAL_FILLER_DATA;
        case 13: return NalType.NAL_SPS_EXT;
        case 19: return NalType.NAL_AUXILIARY_SLICE;
        default:
            break;
        }
        return NalType.NAL_UNKNOWN;
    }

    public static class StartCode {
        public int pos    = -1;
        public int length = -1;
        public StartCode() {
        }
        public StartCode(StartCode c) {
            pos     = c.pos;
            length  = c.length;
        }
        public boolean valid() {
            return (pos != -1) && (length != -1);
        }
        public void set(int p, int l) {
            pos     = p;
            length  = l;
        }
        public void reset() {
            pos     = -1;
            length  = -1;
        }
    }

    @NonNull
    public static StartCode findStartCode(byte[] data, int offset, int len) {
        StartCode csd = new StartCode();
        int matched = 0;
        for (int i = 0; i < len; i++) {
            switch (data[offset + i]) {
            case 0x00:
                matched++;
                break;
            case 0x01:
                matched++;
                if (CSD_MIN <= matched && matched <= CSD_MAX) {
                    csd.pos    = offset + i - matched + 1;
                    csd.length = matched;
                    return csd;
                }
                break;
            default:
                matched = 0;
                break;
            }
        }
        return csd;
    }

    private static class SimpleNalPolicy implements NalPolicy {
        @Override
        public Policy onNal(NalHeader hdr, ByteBuffer buffer, int offset, int len) {
            return Policy.CUT;
        }
    }
}
