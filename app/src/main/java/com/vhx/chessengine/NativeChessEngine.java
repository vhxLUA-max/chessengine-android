package com.vhx.chessengine;

public final class NativeChessEngine {
    static { System.loadLibrary("cheeziengine"); }
    private long handle;

    public NativeChessEngine(String engineDirectory) {
        handle = nativeCreate(engineDirectory);
        if (handle == 0) throw new IllegalStateException("Failed to create native chess engine");
    }

    public synchronized boolean setPosition(String fen) { return nativeSetPosition(handle, fen); }
    public synchronized boolean analyze(int depth, int movetimeMs, int threads, int hashMb, int multiPv) {
        return nativeAnalyze(handle, depth, movetimeMs, threads, hashMb, multiPv);
    }
    public synchronized void stop() { nativeStop(handle); }
    public synchronized String getBestMove() { return nativeGetBestMove(handle); }
    public synchronized String getPrincipalVariation() { return nativeGetPrincipalVariation(handle); }
    public synchronized int getScoreCp() { return nativeGetScoreCp(handle); }
    public synchronized int getMate() { return nativeGetMate(handle); }
    public synchronized int getDepth() { return nativeGetDepth(handle); }
    public synchronized void close() {
        if (handle != 0) { nativeDestroy(handle); handle = 0; }
    }
    @Override protected void finalize() throws Throwable {
        close();
        super.finalize();
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
