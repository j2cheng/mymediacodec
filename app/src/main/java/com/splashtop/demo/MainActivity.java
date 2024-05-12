package com.splashtop.demo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;

import android.graphics.Rect;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;

import com.splashtop.demo.databinding.ActivityMainBinding;
import com.splashtop.demo.databinding.FragmentSessionBinding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements Choreographer.FrameCallback{

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private static final boolean ENABLE_AUTO_ADD_SURFACEVIEW_SESSION = false;
    private static final boolean ENABLE_AUTO_ADD_TEXTUREVIEW_SESSION = false;
    private static final boolean ENABLE_AUTO_ADD_NOVIEW_SESSION = false;
    private static final boolean ENABLE_AUTO_START = false;

    private final List<Session> mSessionList = new ArrayList<>();
    private final Set<Integer> mRunningSet = new HashSet<>();
    private SessionLayout mSessionLayout;

    private ActivityMainBinding mBinding;
    private Choreographer choreographer;
    private long tick = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //testing only
        {
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            float refreshRating = display.getRefreshRate();
            sLogger.info("JRC onCreate refreshRating {}",refreshRating);

            if (GLES20.glGetString(GLES20.GL_RENDERER) == null ||
                GLES20.glGetString(GLES20.GL_VENDOR) == null ||
                GLES20.glGetString(GLES20.GL_VERSION) == null ||
                GLES20.glGetString(GLES20.GL_EXTENSIONS) == null ||
                GLES10.glGetString(GLES10.GL_RENDERER) == null ||
                GLES10.glGetString(GLES10.GL_VENDOR) == null ||
                GLES10.glGetString(GLES10.GL_VERSION) == null ||
                GLES10.glGetString(GLES10.GL_EXTENSIONS) == null) {
                // try to use SurfaceView
                sLogger.info("JRC onCreate try to use SurfaceView ");
            } else {
                // try to use TextureView
                sLogger.info("JRC onCreate try to use TextureView ");
            }

            choreographer = Choreographer.getInstance();
        }

        mSessionLayout = new SessionLayout(getApplicationContext(), new SessionLayout.SizeCallback() {
            @Override
            public void onSize(View view, Rect bound) {
                sLogger.trace("view:{} bound:{}", view, bound);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(bound.width(), bound.height());
                params.topMargin = bound.top;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    params.setMarginStart(bound.left);
                } else {
                    params.leftMargin = bound.left;
                }
                view.setLayoutParams(params);
            }
        });

        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        mBinding.layoutSession.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            int mWidth;
            int mHeight;
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                sLogger.trace("left:{} top:{} right:{} bottom:{} oldLeft:{} oldTop:{} oldRight:{} oldBottom:{}",
                        left, top, right, bottom,
                        oldLeft, oldTop, oldRight, oldBottom);

                int width  = right - left;
                int height = bottom - top;
                if (mWidth == width && mHeight == height) {
                    return;
                }
                sLogger.trace("layout width:{} height:{}", width, height);
                mWidth  = width;
                mHeight = height;
                mSessionLayout.setSize(width, height);
            }
        });
        mBinding.buttonSurfaceview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sLogger.info("JRC buttonSurfaceview.setOnClickListener");

                FragmentSessionBinding binding = FragmentSessionBinding.inflate(getLayoutInflater());
                //TODO: bind to surfaceviewwindow   binding.surfaceviewwindow.setVisibility(View.VISIBLE);
                binding.surface.setVisibility(View.VISIBLE);
                
                binding.texture.setVisibility(View.GONE);
                binding.textName.setText(((Button) v).getText());
                binding.textName.bringToFront();

                final DecoderInput input = new DecoderInputAssets(getApplicationContext());
                final Decoder decoder = new DecoderMediaCodec(getApplicationContext()).setInput(input);
                //TODO: bind to surfaceviewwindow final SessionSurfaceView session = new SessionSurfaceView(decoder, binding.surfaceviewwindow);
                final SessionSurfaceView session = new SessionSurfaceView(decoder, binding.surface);
                
                session.setOnStopListener(mOnStopListener);
                addSession(session, binding.getRoot());

                binding.frame.addOnLayoutChangeListener(session);
                binding.buttonRemove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        binding.frame.removeOnLayoutChangeListener(session);
                        removeSession(session, binding.getRoot());
                    }
                });
            }
        });
        mBinding.buttonTextureview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentSessionBinding binding = FragmentSessionBinding.inflate(getLayoutInflater());
                //TODO: bind to surfaceviewwindow binding.surfaceviewwindow.setVisibility(View.GONE);
                binding.surface.setVisibility(View.GONE);

                binding.texture.setVisibility(View.VISIBLE);
                binding.textName.setText(((Button) v).getText());
                binding.textName.bringToFront();

                final DecoderInput input = new DecoderInputAssets(getApplicationContext());
                final Decoder decoder = new DecoderMediaCodec(getApplicationContext()).setInput(input);
                final SessionTextureView session = new SessionTextureView(decoder, binding.texture);
                session.setOnStopListener(mOnStopListener);
                addSession(session, binding.getRoot());

                binding.buttonRemove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removeSession(session, binding.getRoot());
                    }
                });
            }
        });
        mBinding.buttonNoview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentSessionBinding binding = FragmentSessionBinding.inflate(getLayoutInflater());
                //TODO: bind to surfaceviewwindow binding.surfaceviewwindow.setVisibility(View.GONE);
                binding.surface.setVisibility(View.GONE);
                
                binding.texture.setVisibility(View.GONE);
                binding.textName.setText(((Button) v).getText());
                binding.textName.bringToFront();

                final DecoderInput input = new DecoderInputAssets(getApplicationContext());
                final Decoder decoder = new DecoderMediaCodec(getApplicationContext()).setInput(input);
                final Session session = new Session(decoder);
                session.setOnStopListener(mOnStopListener);
                addSession(session, binding.getRoot());

                binding.buttonRemove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removeSession(session, binding.getRoot());
                    }
                });
            }
        });

        mBinding.buttonStartStop.setOnClickListener(mOnClickListener);
        invalidateButton();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ENABLE_AUTO_ADD_SURFACEVIEW_SESSION) {
                    sLogger.info("Auto add SurfaceView session");
                    mBinding.buttonSurfaceview.performClick();
                }
                if (ENABLE_AUTO_ADD_TEXTUREVIEW_SESSION) {
                    sLogger.info("Auto add TextureView session");
                    mBinding.buttonTextureview.performClick();
                }
                if (ENABLE_AUTO_ADD_NOVIEW_SESSION) {
                    sLogger.info("Auto add NoView session");
                    mBinding.buttonNoview.performClick();
                }
                if (ENABLE_AUTO_START) {
                    sLogger.info("Auto start");
                    mBinding.buttonStartStop.performClick();
                }
            }
        }, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Your code to handle onDestroy event
        sLogger.info("JRC onDestroy");
    }

    private void addSession(Session session, View view) {
        sLogger.trace("Add session:{} view:{}", session, view);
        mSessionList.add(session);
        mSessionLayout.addView(view);
        mBinding.layoutSession.addView(view);
        invalidateButton();
    }

    private void removeSession(Session session, View view) {
        sLogger.trace("Del session:{} view:{}", session, view);
        mSessionList.remove(session);
        mSessionLayout.removeView(view);
        mBinding.layoutSession.removeView(view);
        invalidateButton();
    }

    @UiThread
    private void invalidateButton() {
        mBinding.buttonStartStop.setText(!mRunningSet.isEmpty() ? R.string.main_button_stop : R.string.main_button_start);
        mBinding.buttonStartStop.setEnabled(!mSessionList.isEmpty());
    }

    private final Session.OnStopListener mOnStopListener = new Session.OnStopListener() {
        @Override
        public void onStop(Session session) {
            sLogger.trace("session:{}", session);
            mRunningSet.remove(session.hashCode());
            sLogger.trace("running {}", mRunningSet);
            invalidateButton();
        }
    };

    private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            boolean started = mRunningSet.isEmpty();
            sLogger.trace("started:{}", started);

            for (Session session : mSessionList) {
                if (started) {
                    session.start();
                    mRunningSet.add(session.hashCode());
                    sLogger.trace("running {}", mRunningSet);
                } else {
                    session.stop();
                }
            }
            invalidateButton();
        }
    };

//implements Choreographer.FrameCallback
    @Override
    protected void onResume() {
        super.onResume();
        choreographer.postFrameCallback(this);
        sLogger.info("JRC onResume: postFrameCallback {}", SystemClock.uptimeMillis());

    }

    @Override
    protected void onPause() {
        super.onPause();
        choreographer.removeFrameCallback(this);
        sLogger.info("JRC onPause removeFrameCallback: {}", SystemClock.uptimeMillis());

    }

    @Override
    public void doFrame(long frameTimeNanos) {
        // This method will be called on every vsync.
        // Perform your rendering or other tasks here.
        // Remember to schedule the next vsync callback.
        choreographer.postFrameCallback(this);
        // sLogger.info("JRC doFrame frameTimeNanos:{}..{}, postFrameCallback: {}", 
        //               frameTimeNanos,
        //               frameTimeNanos/1000000, 
        //               SystemClock.uptimeMillis());

        tick++;

        if(tick%60 == 0)
        {
            sLogger.info("JRC doFrame frameTimeNanos:{}..{}, postFrameCallback: {}, tick: {}", 
                      frameTimeNanos,
                      frameTimeNanos/1000000, 
                      SystemClock.uptimeMillis(),
                      tick);
        }//else

        if(tick == 300)
        {
            sLogger.info("JRC click surfaceviewwindow button up");
//TODO: if you want to press button here
//            Button buttonFoo = (Button)findViewById(R.id.button_surfaceview);
//            sLogger.info("JRC click buttonFoo: {}",buttonFoo);
//
//            buttonFoo.performClick();

            sLogger.info("JRC click surfaceviewwindow button down");
        }//else


        if(tick == 400)
        {
            sayHello("John Cheng");
            
            //TODO: if you want to press button here
            sLogger.info("JRC skip click button_start_stop button up");
//
//            Button buttonFoo = (Button)findViewById(R.id.button_start_stop);
//            sLogger.info("JRC click buttonFoo: {}",buttonFoo);
//
//            buttonFoo.performClick();
//
//            sLogger.info("JRC click button_start_stop button down");

            if (getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
                // this device has a camera
                //return true;
                sLogger.info("JRC this device has camera FEATURE_CAMERA");

                int camCnt = Camera.getNumberOfCameras();
                sLogger.info("JRC this device has {} camera",camCnt);

                Camera c = null;
                try {
                    c = Camera.open(); // attempt to get a Camera instance
                }
                catch (Exception e){
                    // Camera is not available (in use or does not exist)
                }
                //return c; // returns null if camera is unavailable
                sLogger.info("JRC open {} camera",c);

            } else {
                // no camera on this device
                //return false;
                sLogger.info("JRC no camera on this device");
            }

        }//else

        
    }
//native code here
    static {
              System.loadLibrary("mediacodec");
    }

    // Declare an instance native method sayHello() which returns void
    public native void sayHello(String name);
}
