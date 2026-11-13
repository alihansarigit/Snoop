package io.github.alihansarigit.snoop.diagnostics

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties

internal data class UiTarget(
    val bounds: Rect,
    val componentName: String,
)

internal object UiTargetResolver {
    private const val MAX_NODES = 500
    private const val MAX_DEPTH = 24

    fun capture(root: View): List<UiTarget> {
        val composeTargets = runCatching { root.captureComposeTargets() }.getOrDefault(emptyList())
        val accessibilityTargets = captureAccessibilityTargets(root)
        return composeTargets + accessibilityTargets
    }

    private fun captureAccessibilityTargets(root: View): List<UiTarget> = runCatching {
        val rootNode = root.createAccessibilityNodeInfo() ?: return emptyList()
        val rootLocation = IntArray(2).also(root::getLocationOnScreen)
        val targets = mutableListOf<UiTarget>()
        var count = 0

        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (count++ >= MAX_NODES || depth > MAX_DEPTH) return
            val bounds = Rect().also(node::getBoundsInScreen)
            bounds.offset(-rootLocation[0], -rootLocation[1])
            node.componentName()?.let { name ->
                if (!bounds.isEmpty) targets += UiTarget(bounds, name)
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    try {
                        visit(child, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }

        try {
            visit(rootNode, 0)
        } finally {
            rootNode.recycle()
        }
        targets
    }.getOrDefault(emptyList())

    fun resolve(
        targets: List<UiTarget>,
        xRatio: Float,
        yRatio: Float,
        screenshotWidth: Int,
        screenshotHeight: Int,
    ): String? {
        val x = (xRatio * screenshotWidth).toInt()
        val y = (yRatio * screenshotHeight).toInt()
        return targets
            .asSequence()
            .filter { it.bounds.contains(x, y) }
            .minByOrNull { it.bounds.width().toLong() * it.bounds.height() }
            ?.componentName
    }

    private fun AccessibilityNodeInfo.componentName(): String? {
        val resourceName = viewIdResourceName
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
        if (resourceName != null) return resourceName

        val className = className
            ?.toString()
            ?.substringAfterLast('.')
            ?.takeIf(String::isNotBlank)
            ?: "UI element"
        val visibleLabel = when {
            isPassword -> null
            !text.isNullOrBlank() -> text.toString()
            !contentDescription.isNullOrBlank() -> contentDescription.toString()
            else -> null
        }?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(60)

        return when {
            visibleLabel != null -> "$className (\"$visibleLabel\")"
            isClickable || isFocusable -> className
            else -> null
        }
    }

    private fun View.captureComposeTargets(): List<UiTarget> {
        val targets = mutableListOf<UiTarget>()

        fun visitView(view: View) {
            if (view.tag == "snoop_bubble") return
            (view as? RootForTest)?.let { root ->
                visitSemantics(root.semanticsOwner.rootSemanticsNode, targets)
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visitView(view.getChildAt(index))
            }
        }

        visitView(this)
        return targets
    }

    private fun visitSemantics(node: SemanticsNode, targets: MutableList<UiTarget>) {
        val bounds = node.boundsInWindow
        node.config.componentName()?.let { name ->
            if (!bounds.isEmpty) {
                targets += UiTarget(
                    bounds = Rect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt(),
                    ),
                    componentName = name,
                )
            }
        }
        node.children.forEach { child -> visitSemantics(child, targets) }
    }

    private fun SemanticsConfiguration.componentName(): String? {
        val testTag = getOrNull(SemanticsProperties.TestTag)
        if (!testTag.isNullOrBlank()) return testTag

        val role = getOrNull(SemanticsProperties.Role)
        val text = getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(60)
            ?.takeIf(String::isNotBlank)
        val contentDescription = getOrNull(SemanticsProperties.ContentDescription)
            ?.firstOrNull()
            ?.take(60)
        return when {
            role != null && text != null -> "$role (\"$text\")"
            text != null -> "Text (\"$text\")"
            role != null && !contentDescription.isNullOrBlank() -> "$role (\"$contentDescription\")"
            !contentDescription.isNullOrBlank() -> contentDescription
            role != null -> role.toString()
            else -> null
        }
    }

    private fun <T> SemanticsConfiguration.getOrNull(
        key: androidx.compose.ui.semantics.SemanticsPropertyKey<T>,
    ): T? = if (contains(key)) get(key) else null
}
