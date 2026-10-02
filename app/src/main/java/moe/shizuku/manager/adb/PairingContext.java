/*
 * 改编自 Shizuku AdbPairingClient.kt 的 PairingContext，Apache-2.0。
 * 上游源码：2650830c5b099ae0dd34fedf614d4f592ca05d65；许可见 assets/licenses。
 * 修改：仅保留预编译 JNI 所需的第三方身份，添加幂等关闭与并发访问保护。
 * 此类不是官方管理端功能，也不访问官方配置或凭据。
 */
package moe.shizuku.manager.adb;

import java.io.Closeable;

public final class PairingContext implements Closeable {
    static { System.loadLibrary("adb"); }
    private long nativePtr;
    public PairingContext(byte[] password) {
        nativePtr = nativeConstructor(true, password);
        if (nativePtr == 0) throw new IllegalStateException("Pairing context unavailable");
    }
    public synchronized byte[] message() { checkOpen(); return nativeMsg(nativePtr); }
    public synchronized boolean initCipher(byte[] peer) { checkOpen(); return nativeInitCipher(nativePtr, peer); }
    public synchronized byte[] encrypt(byte[] data) { checkOpen(); return nativeEncrypt(nativePtr, data); }
    public synchronized byte[] decrypt(byte[] data) { checkOpen(); return nativeDecrypt(nativePtr, data); }
    @Override public synchronized void close() {
        if (nativePtr != 0) { nativeDestroy(nativePtr); nativePtr = 0; }
    }
    private void checkOpen() { if (nativePtr == 0) throw new IllegalStateException("Pairing context closed"); }
    private static native long nativeConstructor(boolean client, byte[] password);
    private native byte[] nativeMsg(long ptr);
    private native boolean nativeInitCipher(long ptr, byte[] peer);
    private native byte[] nativeEncrypt(long ptr, byte[] data);
    private native byte[] nativeDecrypt(long ptr, byte[] data);
    private native void nativeDestroy(long ptr);
}
