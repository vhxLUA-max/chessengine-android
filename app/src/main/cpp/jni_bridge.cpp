#include <jni.h>
#include <memory>
#include <string>
#include "native_engine.h"

static CheezieNative::Engine* fromHandle(jlong handle) {
    return reinterpret_cast<CheezieNative::Engine*>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeCreate(JNIEnv* env, jclass, jstring directory) {
    if (!directory) return 0;
    const char* chars = env->GetStringUTFChars(directory, nullptr);
    if (!chars) return 0;

    CheezieNative::Engine* engine = nullptr;
    try {
        engine = new CheezieNative::Engine(chars);
    } catch (...) {
        env->ReleaseStringUTFChars(directory, chars);
        return 0;
    }

    env->ReleaseStringUTFChars(directory, chars);
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeSetPosition(JNIEnv* env, jclass, jlong handle, jstring fen) {
    if (!handle || !fen) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (!chars) return JNI_FALSE;

    bool ok = false;
    try {
        ok = fromHandle(handle)->setPosition(chars);
    } catch (...) {
        ok = false;
    }

    env->ReleaseStringUTFChars(fen, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeAnalyze(JNIEnv*, jclass, jlong handle, jint depth, jint movetimeMs, jint threads, jint hashMb, jint multiPv) {
    if (!handle) return JNI_FALSE;
    try {
        return fromHandle(handle)->analyze(depth, movetimeMs, threads, hashMb, multiPv) ? JNI_TRUE : JNI_FALSE;
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeStop(JNIEnv*, jclass, jlong handle) {
    if (!handle) return;
    try {
        fromHandle(handle)->stop();
    } catch (...) {
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetBestMove(JNIEnv* env, jclass, jlong handle) {
    if (!handle) return env->NewStringUTF("");
    try {
        return env->NewStringUTF(fromHandle(handle)->bestMove().c_str());
    } catch (...) {
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetPrincipalVariation(JNIEnv* env, jclass, jlong handle) {
    if (!handle) return env->NewStringUTF("");
    try {
        return env->NewStringUTF(fromHandle(handle)->principalVariation().c_str());
    } catch (...) {
        return env->NewStringUTF("");
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetScoreCp(JNIEnv*, jclass, jlong handle) {
    if (!handle) return 0;
    try { return fromHandle(handle)->scoreCp(); } catch (...) { return 0; }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetMate(JNIEnv*, jclass, jlong handle) {
    if (!handle) return 0;
    try { return fromHandle(handle)->mate(); } catch (...) { return 0; }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetDepth(JNIEnv*, jclass, jlong handle) {
    if (!handle) return 0;
    try { return fromHandle(handle)->depth(); } catch (...) { return 0; }
}
