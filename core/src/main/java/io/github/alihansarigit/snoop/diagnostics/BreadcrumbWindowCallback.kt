package io.github.alihansarigit.snoop.diagnostics

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.github.alihansarigit.snoop.feedback.BreadcrumbRecorder
import java.util.WeakHashMap

/** Installs a transparent Window.Callback wrapper to observe completed touches. */
internal class BreadcrumbWindowCallbacks {
    private val wrappers = WeakHashMap<Activity, Pair<Window.Callback, Window.Callback>>()

    fun install(activity: Activity) {
        if (wrappers.containsKey(activity)) return
        val original = activity.window.callback ?: return
        val wrapper = object : Window.Callback by original {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val target = findDeepestView(activity.window.decorView, event.rawX, event.rawY)
                    BreadcrumbRecorder.record(
                        type = "touch",
                        label = target?.stableLabel(activity) ?: "screen",
                        x = event.rawX,
                        y = event.rawY,
                    )
                }
                return original.dispatchTouchEvent(event)
            }
        }
        wrappers[activity] = original to wrapper
        activity.window.callback = wrapper
    }

    fun uninstall(activity: Activity) {
        val (original, wrapper) = wrappers.remove(activity) ?: return
        if (activity.window.callback === wrapper) activity.window.callback = original
    }

    private fun findDeepestView(view: View, rawX: Float, rawY: Float): View? {
        if (!view.contains(rawX, rawY) || view.visibility != View.VISIBLE) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                findDeepestView(view.getChildAt(index), rawX, rawY)?.let { return it }
            }
        }
        return view
    }

    private fun View.contains(rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        getLocationOnScreen(location)
        return rawX >= location[0] && rawX <= location[0] + width &&
            rawY >= location[1] && rawY <= location[1] + height
    }

    private fun View.stableLabel(activity: Activity): String {
        contentDescription?.toString()?.takeIf(String::isNotBlank)?.let {
            return "${javaClass.simpleName}[$it]"
        }
        if (id != View.NO_ID) {
            runCatching { activity.resources.getResourceEntryName(id) }.getOrNull()?.let {
                return "${javaClass.simpleName}#$it"
            }
        }
        return javaClass.simpleName
    }
}
