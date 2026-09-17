package androidx.test.runner.lifecycle;
import java.util.concurrent.atomic.AtomicReference;
public final class ActivityLifecycleMonitorRegistry {
    private static final AtomicReference<ActivityLifecycleMonitor> INSTANCE = new AtomicReference<>();
    private ActivityLifecycleMonitorRegistry() {}
    public static ActivityLifecycleMonitor getInstance() {
        ActivityLifecycleMonitor m = INSTANCE.get();
        if (m == null) throw new IllegalStateException("No ActivityLifecycleMonitor registered");
        return m;
    }
    public static void registerInstance(ActivityLifecycleMonitor monitor) { INSTANCE.set(monitor); }
}
