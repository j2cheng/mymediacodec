package com.splashtop.demo;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

// Step to generate index file
// 1. Put h264 file in src/test/resources folder
// 2. Update H264_FILE with h264 resource file name
// 3. Run testParseAll() to verify all the NALU first
// 4. Adjust the policy and then run testGenerateIndex() to generate temp index file
// 5. Copy the index file from temp folder
public class NalPolicyIndexGenerator {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Test");

    private static final String H264_FILE = "H264_RESOURCE_FILE";

    private final InputStream mInput;

    @Rule
    public TemporaryFolder mTempFolder = new TemporaryFolder();

    public NalPolicyIndexGenerator() {
        mInput = getClass().getClassLoader().getResourceAsStream(H264_FILE);
    }

    //@Test
    public void testParseAll() throws Exception {
        NalParser parser = new NalParser(mInput);
        NalParser.NalHeader header = new NalParser.NalHeader();
        long count = 0;
        while (parser.parse(header) != null) {
            sLogger.debug("count:{} type:{} size:{} pts:{}", count++, header.type, header.size, header.pts);
        }
    }

    //@Test
    public void testGenerateIndex() throws Exception {
        File file = mTempFolder.newFile(H264_FILE + ".index");
        DataOutputStream output = new DataOutputStream(new FileOutputStream(file));
        NalParser parser = new NalParser(mInput, new NalPolicy() {
            @Override
            public Policy onNal(NalParser.NalHeader hdr, ByteBuffer buffer, int offset, int len) {
                if (NalParser.NalType.NAL_SPS == hdr.type) return Policy.CONTINUE; // Merge SPS with PPS
                if (NalParser.NalType.NAL_SEI == hdr.type) return Policy.CONTINUE; // Merge SEI with IDR
                return Policy.CUT;
            }
        });

        NalParser.NalHeader header = new NalParser.NalHeader();
        long count = 0;
        long interval = 1000000 / 60;
        while (parser.parse(header) != null) {
            header.pts = count * interval;
            output.writeInt(header.size);
            output.writeLong(header.pts);
            sLogger.debug("count:{} type:{} size:{} pts:{}", count, header.type, header.size, header.pts);
            count++;
        }
        sLogger.info("Output file <{}> please copy it out within 15s", file);
        Thread.sleep(15 * 1000);
    }

    @Test
    public void dummyTest() {
        // Provide an empty test case, to avoid test all may always stop here
    }
}
