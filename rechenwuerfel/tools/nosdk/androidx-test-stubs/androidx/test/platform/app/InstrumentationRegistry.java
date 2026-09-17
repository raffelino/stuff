package androidx.test.platform.app;
import android.app.Instrumentation;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicReference;
public final class InstrumentationRegistry {
    private static final AtomicReference<Instrumentation> INSTRUMENTATION = new AtomicReference<>();
    private static final AtomicReference<Bundle> ARGUMENTS = new AtomicReference<>();
    private InstrumentationRegistry() {}
    public static Instrumentation getInstrumentation() {
        Instrumentation i = INSTRUMENTATION.get();
        if (i == null) throw new IllegalStateException("No instrumentation registered");
        return i;
    }
    public static Bundle getArguments() {
        Bundle b = ARGUMENTS.get();
        return b == null ? new Bundle() : new Bundle(b);
    }
    public static void registerInstance(Instrumentation instrumentation, Bundle arguments) {
        INSTRUMENTATION.set(instrumentation);
        ARGUMENTS.set(arguments == null ? new Bundle() : new Bundle(arguments));
    }
}
