//package com.splashtop.demo.MainActivity
// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("mediacodec");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("mediacodec")
//      }
//    }

// Save as "HelloJNI.c"
#include <jni.h>        // JNI header provided by JDK
#include <stdio.h>      // C Standard IO Header
//#include "mediacodec.h"   // Generated
#include <android/log.h>
#define TAG "mediacodec"

// Implementation of the native method sayHello()
extern "C" JNIEXPORT void JNICALL Java_com_splashtop_demo_MainActivity_sayHello(JNIEnv *env, jobject thisObj,jstring arg) {
//    __android_log_printf   printf("mediacodec printf Hello World!\n");//ToString(env, arg)
    __android_log_print(ANDROID_LOG_DEBUG,TAG,"mediacodec __android_log_print Hello World!\n");
    printf("mediacodec printf Hello World!\n");//ToString(env, arg)

    return;
}