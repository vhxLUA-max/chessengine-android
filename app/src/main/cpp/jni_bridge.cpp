#include <jni.h>
#include <memory>
#include <string>
#include "native_engine.h"

static CheezieNative::Engine* fromHandle(jlong handle) {
    return reinterpret_cast<CheezieNative::Engine*>(handle);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeCreate(JNIEnv* env, jclass, jstring directory) {
    const char* chars = env->GetStringUTFChars(directory, nullptr);
    if (!chars) return 0;
    auto* engine = new CheezieNative::Engine(chars);
    env->ReleaseStringUTFChars(directory, chars);
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeSetPosition(JNIEnv* env, jclass, jlong handle, jstring fen) {
    const char* chars = env->GetStringUTFChars(fen, nullptr);
    if (!chars) return JNI_FALSE;
    const bool ok = fromHandle(handle)->setPosition(chars);
    env->ReleaseStringUTFChars(fen, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeAnalyze(JNIEnv*, jclass, jlong handle, jint depth, jint movetimeMs, jint threads, jint hashMb, jint multiPv) {
    return fromHandle(handle)->analyze(depth, movetimeMs, threads, hashMb, multiPv) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeStop(JNIEnv*, jclass, jlong handle) {
    fromHandle(handle)->stop();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetBestMove(JNIEnv* env, jclass, jlong handle) {
    return env->NewStringUTF(fromHandle(handle)->bestMove().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetPrincipalVariation(JNIEnv* env, jclass, jlong handle) {
    return env->NewStringUTF(fromHandle(handle)->principalVariation().c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetScoreCp(JNIEnv*, jclass, jlong handle) { return fromHandle(handle)->scoreCp(); }

extern "C" JNIEXPORT jint JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetMate(JNIEnv*, jclass, jlong handle) { return fromHandle(handle)->mate(); }

extern "C" JNIEXPORT jint JNICALL
Java_com_vhx_chessengine_NativeChessEngine_nativeGetDepth(JNIEnv*, jclass, jlong handle) { return fromHandle(handle)->depth(); }
