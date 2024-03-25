package com.splashtop.demo;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Session layout controller
 * Manage multi-session view layout
 * Session activity will notify controller for new session view added or removed
 * Controller will compute for suitable layout, and tell activity display rect for each view
 */
public class SessionLayout {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Demo");

    private final Context mContext;
    private final SizeCallback mCallback;

    private final List<View> mList = new ArrayList<>();
    private View mFullView;
    private int mWidth;
    private int mHeight;

    interface SizeCallback {
        void onSize(View view, Rect bound);
    }

    interface Spec {
        Rect measure(Rect bound, int viewIdx);
    }

    /**
     * Use the whole surface bound
     */
    static class Spec1 implements Spec {
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            if (viewIdx >= 1) throw new RuntimeException("Range out of bound");
            return bound;
        }
    }

    /**
     * Landscape
     * A|B
     *
     * Portrait
     * A
     * -
     * B
     */
    static class Spec2 implements Spec {
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            if (viewIdx >= 2) throw new RuntimeException("Range out of bound");

            if (bound.width() > bound.height()) {
                // landscape
                int w = bound.width() / 2;
                int h = bound.height();
                switch (viewIdx) {
                case 0: return new Rect(bound.left + w * 0, bound.top + h * 0, bound.left + w * 1, bound.top + h * 1);
                case 1: return new Rect(bound.left + w * 1, bound.top + h * 0, bound.left + w * 2, bound.top + h * 1);
                }
            } else {
                // portrait
                int w = bound.width();
                int h = bound.height() / 2;
                switch (viewIdx) {
                case 0: return new Rect(bound.left + w * 0, bound.top + h * 0, bound.left + w * 1, bound.top + h * 1);
                case 1: return new Rect(bound.left + w * 0, bound.top + h * 1, bound.left + w * 1, bound.top + h * 2);
                }
            }
            return null;
        }
    }

    /**
     * A|B
     * -+-
     * C|D
     */
    static class Spec4 implements Spec {
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            if (viewIdx >= 4) throw new RuntimeException("Range out of bound");

            int w = bound.width() / 2;
            int h = bound.height() / 2;
            switch (viewIdx) {
            case 0: return new Rect(bound.left + w * 0, bound.top + h * 0, bound.left + w * 1, bound.top + h * 1);
            case 1: return new Rect(bound.left + w * 1, bound.top + h * 0, bound.left + w * 2, bound.top + h * 1);
            case 2: return new Rect(bound.left + w * 0, bound.top + h * 1, bound.left + w * 1, bound.top + h * 2);
            case 3: return new Rect(bound.left + w * 1, bound.top + h * 1, bound.left + w * 2, bound.top + h * 2);
            }
            return null; // Other views will not be sized
        }
    }

    /**
     * A|B|C
     * -+-+-
     * D|E|F
     */
    static class Spec6 implements Spec {
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            if (viewIdx >= 6) throw new RuntimeException("Range out of bound");

            int w = bound.width() / 3;
            int h = bound.height() / 2;
            switch (viewIdx) {
            case 0: return new Rect(bound.left + w * 0, bound.top + h * 0, bound.left + w * 1, bound.top + h * 1);
            case 1: return new Rect(bound.left + w * 1, bound.top + h * 0, bound.left + w * 2, bound.top + h * 1);
            case 2: return new Rect(bound.left + w * 2, bound.top + h * 0, bound.left + w * 3, bound.top + h * 1);
            case 3: return new Rect(bound.left + w * 0, bound.top + h * 1, bound.left + w * 1, bound.top + h * 2);
            case 4: return new Rect(bound.left + w * 1, bound.top + h * 1, bound.left + w * 2, bound.top + h * 2);
            case 5: return new Rect(bound.left + w * 2, bound.top + h * 1, bound.left + w * 3, bound.top + h * 2);
            }
            return null; // Other views will not be sized
        }
    }

    /**
     * A|B|C
     * -+-+-
     * D|E|F
     * -+-+-
     * G|H|I
     */
    static class Spec9 implements Spec {
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            int w = bound.width() / 3;
            int h = bound.height() / 3;
            switch (viewIdx) {
            case 0: return new Rect(bound.left + w * 0, bound.top + h * 0, bound.left + w * 1, bound.top + h * 1);
            case 1: return new Rect(bound.left + w * 1, bound.top + h * 0, bound.left + w * 2, bound.top + h * 1);
            case 2: return new Rect(bound.left + w * 2, bound.top + h * 0, bound.left + w * 3, bound.top + h * 1);
            case 3: return new Rect(bound.left + w * 0, bound.top + h * 1, bound.left + w * 1, bound.top + h * 2);
            case 4: return new Rect(bound.left + w * 1, bound.top + h * 1, bound.left + w * 2, bound.top + h * 2);
            case 5: return new Rect(bound.left + w * 2, bound.top + h * 1, bound.left + w * 3, bound.top + h * 2);
            case 6: return new Rect(bound.left + w * 0, bound.top + h * 2, bound.left + w * 1, bound.top + h * 3);
            case 7: return new Rect(bound.left + w * 1, bound.top + h * 2, bound.left + w * 2, bound.top + h * 3);
            case 8: return new Rect(bound.left + w * 2, bound.top + h * 2, bound.left + w * 3, bound.top + h * 3);
            }
            return null; // Other views will not be sized
        }
    }

    /**
     * A|B|C
     * -+-+-
     * D|E|F
     * -+-+-
     * G|H|I
     * -+-+-
     * J|K|L
     */
    static class Spec12 implements Spec {
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            int w = bound.width() / 3;
            int h = bound.height() / 4;
            switch (viewIdx) {
                case 0: return new Rect(bound.left + w * 0, bound.top + h * 0, bound.left + w * 1, bound.top + h * 1);
                case 1: return new Rect(bound.left + w * 1, bound.top + h * 0, bound.left + w * 2, bound.top + h * 1);
                case 2: return new Rect(bound.left + w * 2, bound.top + h * 0, bound.left + w * 3, bound.top + h * 1);
                case 3: return new Rect(bound.left + w * 0, bound.top + h * 1, bound.left + w * 1, bound.top + h * 2);
                case 4: return new Rect(bound.left + w * 1, bound.top + h * 1, bound.left + w * 2, bound.top + h * 2);
                case 5: return new Rect(bound.left + w * 2, bound.top + h * 1, bound.left + w * 3, bound.top + h * 2);
                case 6: return new Rect(bound.left + w * 0, bound.top + h * 2, bound.left + w * 1, bound.top + h * 3);
                case 7: return new Rect(bound.left + w * 1, bound.top + h * 2, bound.left + w * 2, bound.top + h * 3);
                case 8: return new Rect(bound.left + w * 2, bound.top + h * 2, bound.left + w * 3, bound.top + h * 3);
                case 9: return new Rect(bound.left + w * 0, bound.top + h * 3, bound.left + w * 1, bound.top + h * 4);
                case 10: return new Rect(bound.left + w * 1, bound.top + h * 3, bound.left + w * 2, bound.top + h * 4);
                case 11: return new Rect(bound.left + w * 2, bound.top + h * 3, bound.left + w * 3, bound.top + h * 4);
            }
            return null; // Other views will not be sized
        }
    }

    /**
     * AAA
     * AAA
     * AAA
     */
    static class SpecFullscreen implements Spec {
        int mIdx;
        public SpecFullscreen(Context ctx, int fullScreenIdx) {
            mIdx = fullScreenIdx;
        }
        @Override
        public Rect measure(Rect bound, int viewIdx) {
            if (viewIdx == mIdx) return bound;
            int w = bound.width() / 2;
            int h = bound.height() / 2;
            return new Rect(w, h, w, h);
        }
    }

    public SessionLayout(Context ctx, SizeCallback cb) {
        sLogger.trace("");
        mContext = ctx;
        mCallback = cb;
    }

    public synchronized void setSize(int width, int height) {
        sLogger.trace("width:{} height:{}", width, height);
        mWidth = width;
        mHeight = height;
        measureAll();
    }

    public synchronized void addView(View view) {
        sLogger.trace("view:0x{}", Integer.toHexString(view.hashCode()));
        mList.add(view);
        measureAll();
    }

    public synchronized void removeView(View view) {
        sLogger.trace("view:0x{}", Integer.toHexString(view.hashCode()));
        mList.remove(view);
        if (mFullView != null && mFullView.equals(view)) {
            mFullView = null;
        }
        measureAll();
    }

    public synchronized void removeAllView() {
        sLogger.trace("");
        mList.clear();
        mFullView = null;
    }

    public synchronized void fullScreenView(View view) {
        sLogger.trace("view:0x{}", Integer.toHexString(view.hashCode()));
        mFullView = view;
        measureAll();
    }

    public synchronized void resetView() {
        sLogger.trace("");
        mFullView = null;
        measureAll();
    }

    private Spec getMeasureSpec(int count) {
        if (mFullView != null) {
            return new SpecFullscreen(mContext, mList.indexOf(mFullView));
        }
        switch (count) {
        case 1: return new Spec1();
        case 2: return new Spec2();
        case 3:
        case 4:
            return new Spec4();
        case 5:
        case 6:
            return new Spec6();
        case 7:
        case 8:
        case 9:
            return new Spec9();
        default:
            return new Spec12();
        }
    }

    private void measureAll() {
        if (mWidth == 0 || mHeight == 0) return;
        Rect bound = new Rect(0, 0, mWidth, mHeight);
        Spec spec = getMeasureSpec(mList.size());
        for (int i = 0; i < mList.size(); i++) {
            Rect rect = spec.measure(bound, i);
            if (rect != null) {
                mCallback.onSize(mList.get(i), rect);
            }
        }
    }
}
