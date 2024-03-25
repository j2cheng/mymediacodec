package com.splashtop.demo;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

// Work with NalParser, split nals buffer by index file
public class NalPolicyIndex implements NalPolicy {

    public enum PtsType { MILLISECOND, MICROSECOND, NANOSECOND }

    private PtsType mPtsType = PtsType.MICROSECOND;
    private DataInputStream mStream;
    private int mSizeParsed;
    private int mSizeIndex;
    private long mPtsIndex;

    public NalPolicyIndex(InputStream stream) {
        setStream(stream);
    }

    public NalPolicyIndex setStream(InputStream stream) {
        mStream = new DataInputStream(stream);
        return this;
    }

    public NalPolicyIndex setPtsType(PtsType t) {
        mPtsType = t;
        return this;
    }

    @Override
    public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
        try {
            if (mSizeParsed == 0) {
                mSizeIndex = mStream.readInt();
                mPtsIndex  = mStream.readLong();
            }
            mSizeParsed += len;
            if (mSizeParsed < mSizeIndex) {
                return Policy.CONTINUE;
            }
        } catch (IOException ex) {
        }
        mSizeParsed = 0;
        if (hdr != null) {
            long ptsUs = mPtsIndex;
            switch (mPtsType) {
            case MILLISECOND: ptsUs = mPtsIndex * 1000; break;
            case NANOSECOND:  ptsUs = mPtsIndex / 1000; break;
            }
            hdr.pts = ptsUs; // microsecond (10^-6)
        }
        return Policy.CUT;
    }
}
