package androidx.test.runner.intent;
public final class IntentStubberRegistry {
    private static IntentStubber instance;
    private IntentStubberRegistry() {}
    public static synchronized void load(IntentStubber stubber) { instance = stubber; }
    public static synchronized boolean isLoaded() { return instance != null; }
    public static synchronized IntentStubber getInstance() {
        if (instance == null) throw new IllegalStateException("No IntentStubber loaded");
        return instance;
    }
    public static synchronized void reset() { instance = null; }
}
