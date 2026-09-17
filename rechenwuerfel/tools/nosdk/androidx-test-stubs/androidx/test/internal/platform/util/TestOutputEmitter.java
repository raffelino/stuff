package androidx.test.internal.platform.util;
public final class TestOutputEmitter {
    private TestOutputEmitter() {}
    public static void dumpThreadStates(String outputName) { }
    public static boolean captureWindowHierarchy(String outputName) { return false; }
    public static boolean takeScreenshot(String outputName) { return false; }
    public static void addOutputProperties(java.util.Map<String, java.io.Serializable> properties) { }
}
