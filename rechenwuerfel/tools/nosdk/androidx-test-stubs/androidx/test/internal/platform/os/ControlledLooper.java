package androidx.test.internal.platform.os;
public interface ControlledLooper {
    boolean areDrawCallbacksSupported();
    void drainMainThreadUntilIdle();
    void simulateWindowFocus(android.view.View decorView);
}
