package com.vhx.chessengine;

public final class NativeChessEngine implements AutoCloseable {
    static { System.loadLibrary("cheeziengine"); }
    private long handle;

    public NativeChessEngine(String engineDirectory) {
        handle = nativeCreate(engineDirectory);
        if (handle == 0) throw new IllegalStateException("Failed to create native chess engine");
    }

    public synchronized boolean setPosition(String fen) {
        ensureOpen();
        return nativeSetPosition(handle, fen);
    }

    public synchronized boolean analyze(int depth, int movetimeMs, int threads, int hashMb, int multiPv) {
        ensureOpen();
        return nativeAnalyze(handle, depth, movetimeMs, threads, hashMb, multiPv);
    }

    public synchronized void stop() {
        if (handle != 0) nativeStop(handle);
    }

    public synchronized String getBestMove() {
        ensureOpen();
        return nativeGetBestMove(handle);
    }

    public synchronized String getPrincipalVariation() {
        ensureOpen();
        return nativeGetPrincipalVariation(handle);
    }

    public synchronized int getScoreCp() {
        ensureOpen();
        return nativeGetScoreCp(handle);
    }

    public synchronized int getMate() {
        ensureOpen();
        return nativeGetMate(handle);
    }

    public synchronized int getDepth() {
        ensureOpen();
        return nativeGetDepth(handle);
    }

    @Override
    public synchronized void close() {
        if (handle != 0) {
            nativeDestroy(handle);
            handle = 0;
        }
    }

    public synchronized boolean isClosed() {
        return handle == 0;
    }

    private void ensureOpen() {
        if (handle == 0) throw new IllegalStateException("Native chess engine is closed");
    }

    private static native long nativeCreate(String engineDirectory);
    private static native void nativeDestroy(long handle);
    private static native boolean nativeSetPosition(long handle, String fen);
    private static native boolean nativeAnalyze(long handle, int depth, int movetimeMs, int threads, int hashMb, int multiPv);
    private static native void nativeStop(long handle);
    private static native String nativeGetBestMove(long handle);
    private static native String nativeGetPrincipalVariation(long handle);
    private static native int nativeGetScoreCp(long handle);
    private static native int nativeGetMate(long handle);
    private static native int nativeGetDepth(long handle);
}
