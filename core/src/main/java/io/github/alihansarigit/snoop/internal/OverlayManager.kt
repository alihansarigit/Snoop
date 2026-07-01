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
import io.github.alihansarigit.snoop.ui.SnoopOverlay
import java.lang.ref.WeakReference

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

    override fun onActivityResumed(activity: Activity) {
        attachOverlay(activity)
        resumedActivity = WeakReference(activity)
        startShakeDetection(activity)
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
        if (OverlayState.shakeToShowEnabled) OverlayState.visible = true
    }

    private fun attachOverlay(activity: Activity) {
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

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    private companion object {
        const val BUBBLE_TAG = "snoop_bubble"
    }
}
