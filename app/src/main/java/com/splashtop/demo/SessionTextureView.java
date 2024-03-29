package com.splashtop.demo;

import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionTextureView extends Session implements TextureView.SurfaceTextureListener {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private final TextureView mTextureView;
    private SurfaceTexture mTexture;
    private Surface mSurface;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoRotate;

    public SessionTextureView(Decoder decoder, TextureView view) {
        super(decoder);
        sLogger.trace("decoder:{} view:{}", decoder, view);
        mTextureView = view;
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        sLogger.trace("");
        mTextureView.setSurfaceTextureListener(null);
    }

    @Override // TextureView.SurfaceTextureListener
    synchronized public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        sLogger.trace("width:{} height:{}", width, height);
        // XXX: Show/Hide the TextureView after created may not re-create SurfaceTexture again
        // Need create Surface from cached SurfaceTexture manually
        mTexture = surfaceTexture;
        mSurface = new Surface(mTexture);
        postSurfaceCreate(mSurface);
        onSurfaceSize(width, height);
    }

    @Override // TextureView.SurfaceTextureListener
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        sLogger.trace("width:{} height:{}", width, height);
        onSurfaceSize(width, height);
    }

    @Override // TextureView.SurfaceTextureListener
    synchronized public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        sLogger.trace("");
        postSurfaceDestroy(mSurface);
        mSurface.release();
        mSurface = null;
        return true;
    }

    @Override // TextureView.SurfaceTextureListener
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        //sLogger.trace(""); // Very noisy
    }

    @Override // DecoderOutput
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

    private void onSurfaceSize(int width, int height) {
        sLogger.trace("width:{} height:{}", width, height);
        if (mSurfaceWidth == width && mSurfaceHeight == height) return;

        mSurfaceWidth = width;
        mSurfaceHeight = height;
        invalidateInUiThread();
    }

    private void invalidateInUiThread() {
        mTextureView.post(new Runnable() {
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
        float scale = Math.min((float) mSurfaceWidth / mVideoWidth, (float) mSurfaceHeight / mVideoHeight);
        float scaleX = mVideoWidth * scale / mSurfaceWidth;
        float scaleY = mVideoHeight * scale / mSurfaceHeight;
        int rotate = mVideoRotate % 360;
        if (rotate == 90 || rotate == 270) {
            scale = Math.min((float) mSurfaceWidth / mVideoHeight, (float) mSurfaceHeight / mVideoWidth);
            scaleX = mVideoHeight * scale / mSurfaceHeight;
            scaleY = mVideoWidth * scale / mSurfaceWidth;
            sLogger.trace("ROTATED scale:{} scaleX:{} scaleY:{}", scale, scaleX, scaleY);
        } else {
            sLogger.trace("DEFAULT scale:{} scaleX:{} scaleY:{}", scale, scaleX, scaleY);
        }

        Matrix matrix = mTextureView.getTransform(null);
        matrix.setRotate(rotate, mSurfaceWidth / 2f, mSurfaceHeight / 2f);
        matrix.postScale(scaleX, scaleY, mSurfaceWidth / 2f, mSurfaceHeight / 2f);
        mTextureView.setTransform(matrix);
    }
}
