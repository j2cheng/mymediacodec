package com.splashtop.demo;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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

public class MainActivity extends AppCompatActivity {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private static final boolean ENABLE_AUTO_ADD_SURFACEVIEW_SESSION = false;
    private static final boolean ENABLE_AUTO_ADD_TEXTUREVIEW_SESSION = false;
    private static final boolean ENABLE_AUTO_ADD_NOVIEW_SESSION = false;
    private static final boolean ENABLE_AUTO_START = false;

    private final List<Session> mSessionList = new ArrayList<>();
    private final Set<Integer> mRunningSet = new HashSet<>();
    private SessionLayout mSessionLayout;

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
                FragmentSessionBinding binding = FragmentSessionBinding.inflate(getLayoutInflater());
                binding.surface.setVisibility(View.VISIBLE);
                binding.texture.setVisibility(View.GONE);
                binding.textName.setText(((Button) v).getText());
                binding.textName.bringToFront();

                final DecoderInput input = new DecoderInputAssets(getApplicationContext());
                final Decoder decoder = new DecoderMediaCodec(getApplicationContext()).setInput(input);
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
}
