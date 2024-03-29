package com.splashtop.demo;

import android.app.Activity;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecoderOutputActivity extends Activity {

    private static final Logger sLogger = LoggerFactory.getLogger("ST-Test");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sLogger.trace("");
    }

    @Override
    protected void onStart() {
        super.onStart();
        sLogger.trace("");
    }

    @Override
    protected void onStop() {
        super.onStop();
        sLogger.trace("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sLogger.trace("");
    }
}
