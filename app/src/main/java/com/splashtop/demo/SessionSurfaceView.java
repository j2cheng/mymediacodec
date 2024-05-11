package com.splashtop.demo;

import static android.os.SystemClock.sleep;
import static android.view.Surface.CHANGE_FRAME_RATE_ALWAYS;

import android.graphics.PixelFormat;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionSurfaceView extends Session implements SurfaceHolder.Callback, View.OnLayoutChangeListener {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private final SurfaceView mSurfaceView;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoRotate;

    public SessionSurfaceView(Decoder decoder, SurfaceView view) {
        super(decoder);
        sLogger.trace("decoder:{} view:{}", decoder, view);
        mSurfaceView = view;
        mSurfaceView.getHolder().addCallback(this);

        // SurfaceHolder holder = mSurfaceView.getHolder();
        // try {
        //     sLogger.info("JRC calling setFrameRate.");
        //     holder.getSurface().setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,CHANGE_FRAME_RATE_ALWAYS);
        //     sLogger.info("JRC surface:setFrameRate is done.");
        // } catch (Exception e) {
        //     // Tolerate buggy codecs
        //     e.printStackTrace();
        // }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        sLogger.trace("");
        mSurfaceView.getHolder().removeCallback(this);
    }

    @Override // SurfaceHolder.Callback
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        sLogger.trace("surface:{}", holder.getSurface());
        //holder.setFormat(PixelFormat.RGBA_8888);
//        holder.setFixedSize(mSurfaceWidth,mSurfaceHeight);
       // holder.setFormat(PixelFormat.OPAQUE);
       // sLogger.info("JRC surfaceCreated: set to OPAQUE");

  //      sLogger.info("JRC surfaceCreated: isCurrentThread: {}", Looper.getMainLooper().isCurrentThread());
        
        postSurfaceCreate(holder.getSurface());
    }

    @Override // SurfaceHolder.Callback
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        sLogger.trace("surface:{} format:{} width:{} height:{}", holder.getSurface(), format, width, height);
        // DO NOT call onSurfaceSize(), or else will run into infinite loop        
    }

    @Override // SurfaceHolder.Callback
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        sLogger.trace("surface:{}", holder.getSurface());
        postSurfaceDestroy(holder.getSurface());
    }

    @Override
    public void onFormat(Decoder decoder, Decoder.VideoFormat fmt) {
        super.onFormat(decoder, fmt);
        sLogger.trace("fmt:{}", fmt);

        if (fmt == null) return;
        if (mVideoWidth == fmt.width && mVideoHeight == fmt.height && mVideoRotate == fmt.rotate) return;

        mVideoWidth  = fmt.width;
        mVideoHeight = fmt.height;
        mVideoRotate = fmt.rotate;
        invalidateInUiThread();
    }

    @Override // View.OnLayoutChangeListener
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        sLogger.trace("v:{}@0x{} left:{} top:{} right:{} bottom:{}", v.getClass().getName(), Integer.toHexString(v.hashCode()), left, top, right, bottom);
        int oldWidth = oldRight - oldLeft;
        int oldHeight = oldBottom - oldTop;
        int width = right - left;
        int height = bottom - top;
        if (width == oldWidth && height == oldHeight) return;
        onSurfaceSize(width, height);

//        sleep(4000);
//        sLogger.info("JRC onLayoutChange sleep for 4s");
    }

    private void onSurfaceSize(int width, int height) {
        sLogger.trace("width:{} height:{}", width, height);
        if (mSurfaceWidth == width && mSurfaceHeight == height) return;

        mSurfaceWidth = width;
        mSurfaceHeight = height;

//        sLogger.trace("before calling invalidateInUiThread, mSurfaceWidth:{} mSurfaceHeight:{}", mSurfaceWidth, mSurfaceHeight);
        invalidateInUiThread();
    }

    private void invalidateInUiThread() {
        mSurfaceView.post(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        });
    }

    private void invalidate() {
        if (mSurfaceWidth == 0 || mSurfaceHeight == 0) {
            sLogger.debug("surface:{}x{} invalid", mSurfaceWidth, mSurfaceHeight);
            return;
        }
        if (mVideoWidth == 0 || mVideoHeight == 0) {
            sLogger.debug("video:{}x{} invalid", mVideoWidth, mVideoHeight);
            return;
        }
        sLogger.debug("surface:{}x{} video:{}x{}@{}", mSurfaceWidth, mSurfaceHeight, mVideoWidth, mVideoHeight, mVideoRotate);

        boolean rotated = (mVideoRotate % 180 != 0);
        int streamWidth = rotated ? mVideoHeight : mVideoWidth;
        int streamHeight = rotated ? mVideoWidth : mVideoHeight;
        float scale = Math.min((float) mSurfaceWidth / streamWidth, (float) mSurfaceHeight / streamHeight);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mSurfaceView.getLayoutParams();
        params.width = (int) (streamWidth * scale);
        params.height = (int) (streamHeight * scale);
        sLogger.trace("scale:{} size:({}x{})", scale, params.width, params.height);

        mSurfaceView.setLayoutParams(params);
    }
}
