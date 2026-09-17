package androidx.test.internal.runner.lifecycle;
import android.app.Activity;
import androidx.test.runner.lifecycle.ActivityLifecycleCallback;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitor;
import androidx.test.runner.lifecycle.Stage;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
public final class ActivityLifecycleMonitorImpl implements ActivityLifecycleMonitor {
    private static final class Entry { final WeakReference<Activity> ref; Stage stage; Entry(Activity a, Stage s) { ref = new WeakReference<>(a); stage = s; } }
    private final List<Entry> entries = new ArrayList<>();
    private final List<ActivityLifecycleCallback> callbacks = new CopyOnWriteArrayList<>();
    @Override public void addLifecycleCallback(ActivityLifecycleCallback callback) { callbacks.add(callback); }
    @Override public void removeLifecycleCallback(ActivityLifecycleCallback callback) { callbacks.remove(callback); }
    @Override public synchronized Stage getLifecycleStageOf(Activity activity) {
        for (Entry e : entries) if (e.ref.get() == activity) return e.stage;
        throw new IllegalArgumentException("Unknown activity: " + activity);
    }
    @Override public synchronized Collection<Activity> getActivitiesInStage(Stage stage) {
        List<Activity> out = new ArrayList<>();
        for (Entry e : entries) { Activity a = e.ref.get(); if (a != null && e.stage == stage) out.add(a); }
        return out;
    }
    public void signalLifecycleChange(Stage stage, Activity activity) {
        synchronized (this) {
            boolean found = false;
            for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
                Entry e = it.next(); Activity a = e.ref.get();
                if (a == null) { it.remove(); continue; }
                if (a == activity) { e.stage = stage; found = true; }
            }
            if (!found) entries.add(new Entry(activity, stage));
        }
        for (ActivityLifecycleCallback c : callbacks) c.onActivityLifecycleChanged(activity, stage);
    }
}
