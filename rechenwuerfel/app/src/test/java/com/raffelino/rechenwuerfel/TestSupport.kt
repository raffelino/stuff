package com.raffelino.rechenwuerfel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration

/** Gemeinsame Helfer für die Oberflächentests. */
object TestSupport {

    /** Lässt die Zeit auf dem Main-Looper vergehen (Animationen, Timer, verzögerte Aufrufe). */
    fun idle(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun <T : Activity> launch(cls: Class<T>, intent: Intent? = null): ActivityController<T> {
        val controller = if (intent == null) Robolectric.buildActivity(cls) else Robolectric.buildActivity(cls, intent)
        controller.setup()
        idle()
        return controller
    }

    fun click(activity: Activity, id: Int) {
        val v = activity.findViewById<View>(id)
        assertTrue("View $id ist nicht sichtbar", v.isShown)
        assertTrue("View $id ist deaktiviert", v.isEnabled)
        assertTrue("Klick auf $id wurde nicht verarbeitet", v.performClick())
        idle()
    }

    fun text(activity: Activity, id: Int): String = activity.findViewById<TextView>(id).text.toString()

    fun assertVisible(activity: Activity, id: Int) {
        val v = activity.findViewById<View>(id)
        assertTrue("View $id sollte sichtbar sein", v.isShown)
    }

    fun assertGone(activity: Activity, id: Int) {
        val v = activity.findViewById<View>(id)
        assertTrue("View $id sollte nicht sichtbar sein", !v.isShown)
    }

    fun latestDialog(): AlertDialog {
        return ShadowAlertDialog.getLatestAlertDialog() ?: throw AssertionError("Kein Dialog geöffnet")
    }

    fun dialogMessage(): String = latestDialog().findViewById<TextView>(android.R.id.message).text.toString()

    fun assertNextActivity(activity: Activity, expected: Class<out Activity>): Intent {
        val intent = shadowOf(activity).nextStartedActivity ?: throw AssertionError("Es wurde keine Activity gestartet")
        assertEquals(expected.name, intent.component?.className)
        return intent
    }

    fun button(activity: Activity, id: Int): Button = activity.findViewById(id)
}
