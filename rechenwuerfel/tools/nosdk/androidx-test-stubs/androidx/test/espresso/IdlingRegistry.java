package androidx.test.espresso;
import android.os.Looper;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
public final class IdlingRegistry {
    private static final IdlingRegistry INSTANCE = new IdlingRegistry();
    private final Set<IdlingResource> resources = new LinkedHashSet<>();
    private final Set<Looper> loopers = new LinkedHashSet<>();
    private IdlingRegistry() {}
    public static IdlingRegistry getInstance() { return INSTANCE; }
    public synchronized boolean register(IdlingResource... rs) { boolean ok = true; for (IdlingResource r : rs) ok &= resources.add(r); return ok; }
    public synchronized boolean unregister(IdlingResource... rs) { boolean ok = true; for (IdlingResource r : rs) ok &= resources.remove(r); return ok; }
    public synchronized void registerLooperAsIdlingResource(Looper l) { loopers.add(l); }
    public synchronized void unregisterLooperAsIdlingResource(Looper l) { loopers.remove(l); }
    public synchronized Collection<IdlingResource> getResources() { return Collections.unmodifiableSet(new LinkedHashSet<>(resources)); }
    public synchronized Collection<Looper> getLoopers() { return Collections.unmodifiableSet(new LinkedHashSet<>(loopers)); }
}
