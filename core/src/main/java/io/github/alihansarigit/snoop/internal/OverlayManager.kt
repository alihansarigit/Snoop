package io.github.alihansarigit.snoop.internal

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import io.github.alihansarigit.snoop.ui.SnoopOverlay
import io.github.alihansarigit.snoop.Snoop
import io.github.alihansarigit.snoop.diagnostics.BreadcrumbWindowCallbacks
import io.github.alihansarigit.snoop.diagnostics.ScreenshotCapture
import io.github.alihansarigit.snoop.feedback.BreadcrumbRecorder
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Attaches the [SnoopOverlay] ComposeView on top of every resumed activity, and —
 * while an activity is in the foreground — listens for a device shake to re-show a
 * hidden bubble. The overlay fills the activity content but only the bubble/dialog
 * consume touches, so the host UI keeps working underneath.
 */
internal class OverlayManager : Application.ActivityLifecycleCallbacks {

    private var sensorManager: SensorManager? = null
    private var resumedActivity: WeakReference<Activity>? = null
    private val shakeDetector = ShakeDetector(onShake = ::onShake)
    private val breadcrumbCallbacks = BreadcrumbWindowCallbacks()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onActivityResumed(activity: Activity) {
        attachOverlay(activity)
        breadcrumbCallbacks.install(activity)
        resumedActivity = WeakReference(activity)
        BreadcrumbRecorder.record("navigation", activity.javaClass.name)
        startShakeDetection(activity)
        scope.launch {
            delay(350)
            if (!activity.isFinishing && !activity.isDestroyed) {
                ScreenshotCapture.refreshLastGood(activity)
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        stopShakeDetection()
        resumedActivity = null
    }

    /** Re-sync sensor registration after `Snoop.shakeToShow` flips at runtime. */
    internal fun onShakeSettingChanged() {
        val activity = resumedActivity?.get() ?: return
        if (OverlayState.shakeToShowEnabled) startShakeDetection(activity) else stopShakeDetection()
    }

    private fun onShake() {
        if (!OverlayState.shakeToShowEnabled) return
        val activity = resumedActivity?.get() ?: return
        if (Snoop.config.shakeOpensFeedback) {
            Snoop.launchCapture(activity)
        } else {
            OverlayState.visible = true
        }
    }

    private fun attachOverlay(activity: Activity) {
        // A ComposeView needs a ViewTreeLifecycleOwner on its ancestor chain to build its window
        // recomposer. ComponentActivity (and AppCompat/Fragment activities) provide one; bare
        // android.app.Activity screens such as AdMob's full-screen AdActivity, Chrome Custom Tabs,
        // or Google Pay do not. Attaching there throws "ViewTreeLifecycleOwner not found from
        // ComposeView". A debug bubble over a third-party/ad screen isn't wanted anyway — skip it.
        if (activity !is LifecycleOwner) return

        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (content.findViewWithTag<ComposeView>(BUBBLE_TAG) != null) return

        val composeView = ComposeView(activity).apply {
            tag = BUBBLE_TAG
            setContent { SnoopOverlay() }
        }
        content.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun startShakeDetection(activity: Activity) {
        if (!OverlayState.shakeToShowEnabled || sensorManager != null) return
        val sm = activity.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager = sm
    }

    private fun stopShakeDetection() {
        sensorManager?.unregisterListener(shakeDetector)
        sensorManager = null
    }

    internal fun currentActivity(): Activity? = resumedActivity?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        BreadcrumbRecorder.record("lifecycle", "${activity.javaClass.simpleName}.created")
    }
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        breadcrumbCallbacks.uninstall(activity)
        BreadcrumbRecorder.record("lifecycle", "${activity.javaClass.simpleName}.destroyed")
    }

    private companion object {
        const val BUBBLE_TAG = "snoop_bubble"
    }
}
