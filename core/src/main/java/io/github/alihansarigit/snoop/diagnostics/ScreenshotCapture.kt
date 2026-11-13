package io.github.alihansarigit.snoop.diagnostics

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class CapturedScreenshot(
    val bitmap: Bitmap,
    val png: ByteArray,
)

internal enum class AnnotationTool { PEN, CIRCLE, ARROW }

internal data class AnnotationStroke(
    val tool: AnnotationTool,
    /** Points normalized to the displayed image bounds. */
    val points: List<Offset>,
)

/** Captures foreground windows and maintains a compressed last-known-good crash snapshot. */
internal object ScreenshotCapture {
    @Volatile
    private var lastGoodPng: ByteArray? = null

    suspend fun capture(activity: Activity): CapturedScreenshot? {
        val view = activity.window.decorView.rootView
        if (view.width <= 0 || view.height <= 0) return null
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val captured = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            suspendCancellableCoroutine { continuation ->
                runCatching {
                    PixelCopy.request(activity.window, bitmap, { result ->
                        continuation.resume(result == PixelCopy.SUCCESS)
                    }, Handler(Looper.getMainLooper()))
                }.onFailure { continuation.resume(false) }
            }
        } else {
            false
        }
        if (!captured) {
            runCatching {
                bitmap.eraseColor(PixelFormat.TRANSPARENT)
                view.draw(Canvas(bitmap))
            }.getOrElse {
                bitmap.recycle()
                return null
            }
        }
        val png = bitmap.toPng()
        lastGoodPng = png
        return CapturedScreenshot(bitmap, png)
    }

    suspend fun refreshLastGood(activity: Activity) {
        capture(activity)?.bitmap?.recycle()
    }

    fun lastGoodPng(): ByteArray? = lastGoodPng?.copyOf()

    fun renderAnnotations(
        source: Bitmap,
        strokes: List<AnnotationStroke>,
        color: Int = 0xFFEF4444.toInt(),
    ): ByteArray {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (output.width.coerceAtMost(output.height) * 0.008f).coerceAtLeast(5f)
        }
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val points = stroke.points.map {
                Offset(it.x * output.width, it.y * output.height)
            }
            when (stroke.tool) {
                AnnotationTool.PEN -> canvas.drawPath(points.toPath(), paint)
                AnnotationTool.CIRCLE -> {
                    val first = points.first()
                    val last = points.last()
                    canvas.drawOval(
                        minOf(first.x, last.x),
                        minOf(first.y, last.y),
                        maxOf(first.x, last.x),
                        maxOf(first.y, last.y),
                        paint,
                    )
                }
                AnnotationTool.ARROW -> drawArrow(canvas, points.first(), points.last(), paint)
            }
        }
        return output.toPng().also { output.recycle() }
    }

    private fun List<Offset>.toPath(): Path = Path().apply {
        moveTo(first().x, first().y)
        drop(1).forEach { lineTo(it.x, it.y) }
    }

    private fun drawArrow(canvas: Canvas, start: Offset, end: Offset, paint: Paint) {
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)
        val angle = kotlin.math.atan2(end.y - start.y, end.x - start.x)
        val length = paint.strokeWidth * 5f
        val spread = Math.toRadians(28.0).toFloat()
        canvas.drawLine(
            end.x,
            end.y,
            end.x - length * kotlin.math.cos(angle - spread),
            end.y - length * kotlin.math.sin(angle - spread),
            paint,
        )
        canvas.drawLine(
            end.x,
            end.y,
            end.x - length * kotlin.math.cos(angle + spread),
            end.y - length * kotlin.math.sin(angle + spread),
            paint,
        )
    }

    private fun Bitmap.toPng(): ByteArray = ByteArrayOutputStream().use { stream ->
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
}
