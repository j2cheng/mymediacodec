package com.splashtop.demo;

import static android.os.SystemClock.sleep;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Range;
import android.view.Surface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

public class DecoderMediaCodec extends Decoder {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private static final String MIME_VIDEO_AVC = "video/avc";

    private static final int DUMP_FRAME_COUNT = 5;

    private static final boolean ENABLE_ROTATION = false;           // Let MediaCodec rotate the video internally instead rotate in view renderer manually
    private static final boolean ENABLE_DUMP_ALL_FRAMES = true;    // Print all frames log, default only print 9-frames to avoid too noisy
    private static final boolean ENABLE_PRINT_ALL_TRACE = false;    // Print all trace elapsed time, would be very noisy

    private Thread mInputThread;
    private Thread mOutputThread;
    private boolean mRequestQuit;

    private Surface mSurface;
    private MediaCodec mMediaCodec;

    private int inputCount = 0;
    private int outputCount = 0;


    private final Context mContext;

    public DecoderMediaCodec(Context ctx) {
        sLogger.trace("");
        mContext = ctx;

        //get all codec info
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            String name = codecInfo.getName();
            sLogger.debug("JRC Examinig " + (codecInfo.isEncoder() ? "encoder" : "decoder") + ": " + name);
            for (String type : codecInfo.getSupportedTypes()) {
                boolean ap = codecInfo.getCapabilitiesForType(type)
                        .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_AdaptivePlayback);
                sLogger.debug("JRC supports adaptive playback: " + ap);

                if(!codecInfo.isEncoder())
                {
                    boolean lowLat = codecInfo.getCapabilitiesForType(type).isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency);
                    sLogger.debug("JRC supports FEATURE_LowLatency: " + lowLat);                        
                }//else
            }
        }
    }

    @Override // Decoder
    public void start() {
        mRequestQuit = false;
        mInputThread = new Thread(mInputRunnable);
        mInputThread.setName("CodecI");
        mInputThread.start();

        sLogger.debug("mInputThread.start()");
    }

    @Override // Decoder
    public void stop() {
        sLogger.debug("{} join input", hashCode());
        // Use quit flag instead interrupt the thread, always quit from the flag break point
        mRequestQuit = true;
        if (mInputThread != null) {
            try {
                mInputThread.join();
            } catch (InterruptedException e) {
                sLogger.warn("Failed to join input thread\n", e);
            }
            mInputThread = null;
        }
        sLogger.debug("{} join input done", hashCode());
    }

    @Override // Decoder
    public void attachSurface(Surface surface) {
        sLogger.debug("{} surface:{}", hashCode(), surface);
        mSurface = surface;
    }

    @Override // Decoder
    public void detachSurface(Surface surface) {
        sLogger.debug("{} surface:{}", hashCode(), surface);
        stop();
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    private final Runnable mInputRunnable = new Runnable() {
        @Override
        public void run() {
            sLogger.info("{} + start input H264 data", DecoderMediaCodec.this.hashCode());

            VideoFormat videoInfo = readInputFormat();
            sLogger.debug("{} info:{}", DecoderMediaCodec.this.hashCode(), videoInfo);
            writeOutputFormat(videoInfo);

            String codecName = null;
            // codecName = "OMX.google.h264.decoder";
            // codecName = "c2.android.avc.decoder";
            //codecName = "OMX.qcom.video.decoder.avc.secure";
            //codecName = "c2.rk.hevc.decoder";
            try {
                mMediaCodec = TextUtils.isEmpty(codecName) ?
                        MediaCodec.createDecoderByType(MIME_VIDEO_AVC) :
                        MediaCodec.createByCodecName(codecName);
            } catch (IOException ex) {
                sLogger.error("Failed to create codec");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                sLogger.info("{} Codec name:<{}>", DecoderMediaCodec.this.hashCode(), mMediaCodec.getName());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    MediaCodecInfo codecInfo = mMediaCodec.getCodecInfo();
                    MediaCodecInfo.CodecCapabilities codecCapabilities = codecInfo.getCapabilitiesForType(MIME_VIDEO_AVC);
                    MediaCodecInfo.VideoCapabilities videoCapabilities = codecCapabilities.getVideoCapabilities();
                    MediaCodecInfo.CodecProfileLevel[] codecProfileLevels = codecCapabilities.profileLevels;

                    int width = videoCapabilities.getSupportedWidths().getUpper();
                    int height = videoCapabilities.getSupportedHeightsFor(width).getUpper();
                    sLogger.debug("{} Codec capabilities 720P@{} 1080P@{} 4K@{} MAX {}x{}@{}",
                            DecoderMediaCodec.this.hashCode(),
                            videoCapabilities.getSupportedFrameRatesFor(1280, 720).getUpper().intValue(),
                            videoCapabilities.getSupportedFrameRatesFor(1920, 1080).getUpper().intValue(),
                            videoCapabilities.getSupportedFrameRatesFor(3840, 2160).getUpper().intValue(),
                            width, height,
                            videoCapabilities.getSupportedFrameRatesFor(width, height).getUpper().intValue());

                    Range<Integer> range = videoCapabilities.getSupportedFrameRates();
                    int maxCodecLevel = 0;
                    StringBuilder s = new StringBuilder();

                    //TODO: not working  s.append(" isHardwareAccelerated=").append(codecInfo.isHardwareAccelerated());
                    // bool MediaCodecList::isSoftwareCodec(const AString &componentName) {
                    //     return componentName.startsWithIgnoreCase("OMX.google.")
                    //             || componentName.startsWithIgnoreCase("c2.android.")
                    //             || (!componentName.startsWithIgnoreCase("OMX.")
                    //                     && !componentName.startsWithIgnoreCase("c2."));
                    // }
                    // https://android.googlesource.com/platform/cts/+/android11-release/tests/tests/media/src/android/media/cts/DecoderTest.java
// format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency,
//                 true /* enable */);

// format.removeFeature(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency);
//         format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1);

//                
//                 private static boolean decoderSupportsKnownVendorLowLatencyOption(String decoderName) {
//                     // It's only possible to probe vendor parameters on Android 12 and above.
//                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                         MediaCodec testCodec = null;
//                         try {
//                             // Unfortunately we have to create an actual codec instance to get supported options.
//                             testCodec = MediaCodec.createByCodecName(decoderName);
            
//                             // See if any of the vendor parameters match ones we know about
//                             for (String supportedOption : testCodec.getSupportedVendorParameters()) {
//                                 for (String knownLowLatencyOption : knownVendorLowLatencyOptions) {
//                                     if (supportedOption.equalsIgnoreCase(knownLowLatencyOption)) {
//                                         LimeLog.info(decoderName + " supports known low latency option: " + supportedOption);
//                                         return true;
//                                     }
//                                 }
//                             }
//                         } catch (Exception e) {
//                             // Tolerate buggy codecs
//                             e.printStackTrace();
//                         } finally {
//                             if (testCodec != null) {
//                                 testCodec.release();
//                             }
//                         }
//                     }
//                     return false;
//                 }            


                


                    s.append(" name=<").append(codecInfo.getName()).append(">");
                    s.append(" width=<").append(videoCapabilities.getSupportedWidths()).append(">");
                    s.append(" heights=<").append(videoCapabilities.getSupportedHeights()).append(">");
                    s.append(" frameRates=<[").append(range.getLower().intValue()).append(", ").append(range.getUpper().intValue()).append(">");
                    s.append(" alignment=<").append(videoCapabilities.getWidthAlignment()).append("x").append(videoCapabilities.getHeightAlignment()).append(">");
                    s.append(" bitrate=<").append(videoCapabilities.getBitrateRange()).append(">");
                    if ((codecProfileLevels != null) && (codecProfileLevels.length != 0)) {
                        s.append(" profileLevels=<");
                        for (MediaCodecInfo.CodecProfileLevel profileLevel : codecProfileLevels) {
                            s.append("[profile=")
                                    .append(profileLevel.profile)
                                    .append(" level=")
                                    .append(profileLevel.level)
                                    .append("] ");
                            if (MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline == profileLevel.profile) {
                                maxCodecLevel = Math.max(maxCodecLevel, profileLevel.level);
                            }
                        }
                        s.append(">");
                    }
                    sLogger.debug("{} Codec info {}", DecoderMediaCodec.this.hashCode(), s);
                } catch (Throwable tr) { // Some device not implement
                    sLogger.warn("Failed to get codec capability\n", tr);
                }
            }

            MediaFormat fmt = MediaFormat.createVideoFormat(MIME_VIDEO_AVC, videoInfo.width, videoInfo.height);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                fmt.setInteger(MediaFormat.KEY_PUSH_BLANK_BUFFERS_ON_STOP, 1);
            }
            if (ENABLE_ROTATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                fmt.setInteger(MediaFormat.KEY_ROTATION, videoInfo.rotate);
            }

            ////TODO: try other format fmt.setInteger( MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible );
            ////TODO:sLogger.debug("JRC set MediaFormat:KEY_COLOR_FORMAT COLOR_FormatYUV422Flexible");

            // fmt.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE);
            // fmt.setInteger(MediaFormat.KEY_PRIORITY, 0);
            // sLogger.debug("JRC set MediaFormat:KEY_OPERATING_RATE KEY_PRIORITY");

            // fmt.setInteger(MediaFormat.KEY_CAPTURE_RATE, 30);
            // sLogger.debug("JRC set MediaFormat:KEY_CAPTURE_RATE 30");

            ////TODO:fmt.setInteger(MediaFormat.KEY_FRAME_RATE, 60);
            ////TODO:sLogger.debug("JRC set MediaFormat:KEY_FRAME_RATE 60");

            ////TODO:fmt.setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, 1);
            ////TODO:sLogger.debug("JRC set MediaFormat:KEY_ALLOW_FRAME_DROP");

            sLogger.debug("JRC {} MediaFormat:{}", DecoderMediaCodec.this.hashCode(), fmt);

            sLogger.info("{} config and start with surface:{}", DecoderMediaCodec.this.hashCode(), mSurface);
            mMediaCodec.configure(fmt, mSurface, null, 0);
            mMediaCodec.start();

            mOutputThread = new Thread(mOutputRunnable);
            mOutputThread.setName("CodecO");
            mOutputThread.start();

            long totalDequeue = 0;
            long totalEnqueue = 0;
            int count = 0;
            try {
                while (true) {
                    long t = traceBegin(DecoderMediaCodec.this.hashCode() + " dequeueInputBuffer");
                    int index = mMediaCodec.dequeueInputBuffer(-1);
                    Long thisDequeue = traceEnd(t);
                    totalDequeue += thisDequeue;
                    if (index < 0) {
                        throw new AssertionError("Failed to dequeue input buffer index:" + index);
                    }

                    ByteBuffer buffer;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        buffer = mMediaCodec.getInputBuffer(index);
                    } else {
                        buffer = mMediaCodec.getInputBuffers()[index];
                    }
                    VideoBufferInfo bufferInfo = readInputBuffer(buffer);
                    if (mRequestQuit) {
                        mMediaCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sLogger.info("{} got quit request", DecoderMediaCodec.this.hashCode());
                        break;
                    }
                    if (bufferInfo == null) {
                        mMediaCodec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sLogger.info("{} got EOS", DecoderMediaCodec.this.hashCode());
                        break;
                    }
                    if (ENABLE_DUMP_ALL_FRAMES || count <= DUMP_FRAME_COUNT) {
                        sLogger.info("JRC {} INPUT  count:{} index:{} offset:{} size:{} pts:{} flags:{} uptimeMillis:{}",
                                DecoderMediaCodec.this.hashCode(),
                                count, index,
                                bufferInfo.offset, bufferInfo.size, bufferInfo.pts, bufferInfo.flags,
                                SystemClock.uptimeMillis());
                        //sLogger.trace("buffer:\n" + dumpByteArray(out.array(), info.offset, Math.min(info.size, 64)));
                    }

                    int flags  = 0;
                    switch (bufferInfo.flags) {
                    case VideoBufferInfo.FLAG_CONFIG:
                        flags = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
                        break;
                    case VideoBufferInfo.FLAG_EOS:
                        flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        break;
                    }

                    // PINE64 (RK3328) requires pts continuously
                    // if pts interval have jitter MediaCodec will not output anything
                    t = traceBegin(DecoderMediaCodec.this.hashCode() + " queueInputBuffer");
                    mMediaCodec.queueInputBuffer(index, bufferInfo.offset, bufferInfo.size, bufferInfo.pts, flags);
                    Long thisEnqueue = traceEnd(t);
                    totalEnqueue += thisEnqueue;//traceEnd(t);

                    sLogger.debug(String.format(Locale.US, "JRC %d INPUT  count:%d,flags:%d, dequeue:%.6fs, enqueue:%.6fs",
                                 DecoderMediaCodec.this.hashCode(),
                                 count,flags,
                                 thisDequeue / 1000000000.0,
                                 thisEnqueue / 1000000000.0));
                    
                    count++;
                    inputCount = count;
                }
            } catch (Exception ex) {
                sLogger.warn("Failed to input - {}", ex.getMessage());
            }
            sLogger.debug(String.format(Locale.US, "JRC %d INPUT  total:%d dequeue:%.6fs avg:%.6fs enqueue:%.6fs avg:%.6fs",
                    DecoderMediaCodec.this.hashCode(),
                    count,
                    totalDequeue / 1000000000.0, count > 0 ? totalDequeue / count / 1000000000.0 : 0,
                    totalEnqueue / 1000000000.0, count > 0 ? totalEnqueue / count / 1000000000.0 : 0));

            sLogger.debug("{} join output", DecoderMediaCodec.this.hashCode());
            try {
                mOutputThread.join();
                mOutputThread = null;
            } catch (InterruptedException e) {
            }
            sLogger.debug("{} join output done", DecoderMediaCodec.this.hashCode());

            sLogger.info("{} stop and release", DecoderMediaCodec.this.hashCode());
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
            sLogger.info("{} -", DecoderMediaCodec.this.hashCode());
        }
    };

    private final Runnable mOutputRunnable = new Runnable() {
        @Override
        public void run() {
            BufferInfo info = new BufferInfo();
            long totalDequeue = 0;
            long totalRelease = 0;
            int count = 0;
            long startMs = SystemClock.uptimeMillis();
            long renderTimestampNs = SystemClock.elapsedRealtimeNanos();

            sLogger.info("JRC {} + start output video frame,startMs {}, renderTimestampNs: {}", 
                         DecoderMediaCodec.this.hashCode(),startMs,renderTimestampNs);
            try {
                while (!mRequestQuit) {
                    long t = traceBegin(DecoderMediaCodec.this.hashCode() + " dequeueOutputBuffer");
                    int index = mMediaCodec.dequeueOutputBuffer(info, -1);
                    long thisDequeue = traceEnd(t);                    
                    totalDequeue += thisDequeue;

                    if (index >= 0) { // Index of an output buffer that has been successfully decoded
                        if (ENABLE_DUMP_ALL_FRAMES || count <= DUMP_FRAME_COUNT) {
                            sLogger.trace("JRC {} OUTPUT count:{} index:{} offset:{} size:{} pts:{} flags:{} uptimeMillis:{}",
                                    DecoderMediaCodec.this.hashCode(),
                                    count, index,
                                    info.offset, info.size, info.presentationTimeUs, info.flags,
                                    SystemClock.uptimeMillis());
                        }
                        
                        ByteBuffer buffer;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            buffer = mMediaCodec.getOutputBuffer(index);
                        } else {
                            buffer = mMediaCodec.getOutputBuffers()[index];
                        }
                        VideoBufferInfo bufferInfo = new VideoBufferInfo();
                        bufferInfo.offset = info.offset;
                        bufferInfo.size = info.size;
                        bufferInfo.pts = info.presentationTimeUs;
                        bufferInfo.flags = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) > 0 ? VideoBufferInfo.FLAG_EOS : VideoBufferInfo.FLAG_FRAME;
                        boolean allowDraw = writeOutputBuffer(bufferInfo, buffer);
                        if (ENABLE_DUMP_ALL_FRAMES || count <= DUMP_FRAME_COUNT) {
                            sLogger.info("JRC dequeueOutputBuffer:  {} {} count:{} index:{} pts:{} size:{} uptimeMillis:{}, startMs {}",
                                    DecoderMediaCodec.this.hashCode(),
                                    allowDraw ? "DRAW  " : "DROP  ",
                                    count, index,
                                    info.presentationTimeUs,info.size,
                                    SystemClock.uptimeMillis(),
                                    startMs);
                        }

                        //adding sleep for TexturView
                        if(info.presentationTimeUs == 0)
                        {
                            //pass
                        }
                        else
                        {
                             long ptdMs = ((long)info.presentationTimeUs / (long)1000) ;
                             long currTimems = SystemClock.uptimeMillis();
                             long timeSinceStart = (currTimems - startMs);
                             long dff = ptdMs - timeSinceStart;

                            //if(false)
                            {
                                //sLogger.warn("JRC skips sleep: ptdMs: {} currTimems:{} startMs: {} {} = {}", ptdMs, currTimems,startMs,timeSinceStart, dff);
                                
                                //recaculate renderTimestampNs
                                // renderTimestampNs = renderTimestampNs + info.presentationTimeUs * 1000;
                                // renderTimestampNs = currTimems*1000000 + (count * 16666) * 1000;
                                renderTimestampNs = count * 16666 * 1000 + 10;
                                sLogger.warn("JRC skips sleep: ptdMs: {} currTimems:{} startMs: {} {} = {}, renderTimestampNs: {}", 
                                              ptdMs, 
                                              currTimems,
                                              startMs,
                                              timeSinceStart, 
                                              dff,
                                              renderTimestampNs);
                                
                            }
                            //else
                            {
                            while (info.presentationTimeUs / 1000 > SystemClock.uptimeMillis() - startMs) {
                                try {
                                    sleep(dff/4);
                                    sLogger.warn("JRC sleep - {}ms",dff/4);
                                } catch (Exception ex) {
                                    sLogger.warn("Failed to sleep - {}", ex.getMessage());
                                    break;
                                }
                            }
                        }
                        }
                        t = traceBegin(DecoderMediaCodec.this.hashCode() + " releaseOutputBuffer");
                        // mMediaCodec.releaseOutputBuffer(index, info.presentationTimeUs * 1000);
                        // mMediaCodec.releaseOutputBuffer(index, renderTimestampNs);
                        mMediaCodec.releaseOutputBuffer(index, true);
                        long thisRelease = traceEnd(t); 

                        sLogger.debug(String.format(Locale.US, "JRC %d [o %d,i %d,o-i %d] OUTPUT count:%d dequeue:%.6fs, release:%.6fs using pts",
                                    DecoderMediaCodec.this.hashCode(),
                                    outputCount,inputCount,(outputCount-inputCount),
                                    count,
                                    thisDequeue / 1000000000.0, 
                                    thisRelease / 1000000000.0));
                        
                        totalRelease += thisRelease;             
                        count++;

                        outputCount = count;

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) > 0) {
                            sLogger.info("{} EOS received, exit output loop", DecoderMediaCodec.this.hashCode());
                            break;
                        }
                    } else {
                        switch (index) { // Error happened
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            sLogger.trace("{} INFO_TRY_AGAIN_LATER", DecoderMediaCodec.this.hashCode());
                            continue;
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            MediaFormat fmt = mMediaCodec.getOutputFormat();
                            String mime = fmt.getString(MediaFormat.KEY_MIME);
                            int width = fmt.getInteger(MediaFormat.KEY_WIDTH);
                            int height = fmt.getInteger(MediaFormat.KEY_HEIGHT);
                            if (fmt.containsKey("crop-left") && fmt.containsKey("crop-right")) {
                                width = fmt.getInteger("crop-right") + 1 - fmt.getInteger("crop-left");
                            }
                            if (fmt.containsKey("crop-top") && fmt.containsKey("crop-bottom")) {
                                height = fmt.getInteger("crop-bottom") + 1 - fmt.getInteger("crop-top");
                            }
                            sLogger.trace("JRC {} INFO_OUTPUT_FORMAT_CHANGED mime:{} width:{} height:{} fmt:<{}>", DecoderMediaCodec.this.hashCode(), mime, width, height, fmt);
                                                        
                            try {
                                sLogger.debug("JRC INFO_OUTPUT_FORMAT_CHANGED MediaFormat:KEY_ALLOW_FRAM_DROP{}", fmt.getInteger(MediaFormat.KEY_ALLOW_FRAME_DROP));
                            } catch (Exception e) {
                                // Tolerate buggy codecs
                                e.printStackTrace();
                            }

                            VideoFormat videoInfo = new VideoFormat();
                            videoInfo.width = width;
                            videoInfo.height = height;
                            writeOutputFormat(videoInfo);
                            continue;
                        case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                            sLogger.debug("{} INFO_OUTPUT_BUFFERS_CHANGED", DecoderMediaCodec.this.hashCode());
                            continue;
                        }
                        break;
                    }
                }
            } catch (Exception ex) {
                sLogger.warn("Failed to output - {}", ex.getMessage());
            }
            writeOutputEnd();
            sLogger.debug(String.format(Locale.US, "JRC %d OUTPUT total:%d dequeue:%.6fs avg:%.6fs release:%.6fs avg:%.6fs",
                    DecoderMediaCodec.this.hashCode(),
                    count,
                    totalDequeue / 1000000000.0, totalDequeue / count / 1000000000.0,
                    totalRelease / 1000000000.0, totalRelease / count / 1000000000.0));

            sLogger.info("{} -", DecoderMediaCodec.this.hashCode());
        }
    };

    private long traceBegin(String name) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Trace.beginSection(name);
        }
        return System.nanoTime();
    }

    private long traceEnd(long t) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Trace.endSection();
        }
        long diff = System.nanoTime() - t;
        if (ENABLE_PRINT_ALL_TRACE) sLogger.trace("elapsed:{}", diff);
        return diff;
    }

    private String dumpByteArray(byte[] buffer, int offset, int size) {
        int length = Math.min(size, buffer.length);
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < length; i++) {
            hexString.append(String.format(Locale.US, "%02x", buffer[offset + i]));
            if ((i + 1) % 16 == 0) {
                hexString.append("\n");
            } else {
                hexString.append(" ");
            }
        }
        return hexString.toString().trim();
    }
}
