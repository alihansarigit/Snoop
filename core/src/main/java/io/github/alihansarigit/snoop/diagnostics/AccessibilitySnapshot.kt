package io.github.alihansarigit.snoop.diagnostics

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Best-effort UI tree snapshot. It intentionally excludes user-visible text because text fields
 * frequently contain PII; stable ids, class names, content descriptions and bounds are retained.
 */
internal object AccessibilitySnapshot {
    private const val MAX_NODES = 250
    private const val MAX_DEPTH = 20

    fun capture(root: View): String? = runCatching {
        val node = root.createAccessibilityNodeInfo() ?: return null
        val output = StringBuilder()
        var count = 0

        fun visit(current: AccessibilityNodeInfo, depth: Int) {
            if (count++ >= MAX_NODES || depth > MAX_DEPTH) return
            val bounds = android.graphics.Rect()
            current.getBoundsInScreen(bounds)
            output.append("  ".repeat(depth))
                .append("- ")
                .append(current.className ?: "node")
            current.viewIdResourceName?.let { output.append(" id=").append(it) }
            current.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let {
                output.append(" contentDescription=\"").append(it.take(120)).append('"')
            }
            output.append(" bounds=").append(bounds.toShortString())
                .append(" clickable=").append(current.isClickable)
                .append('\n')
            for (index in 0 until current.childCount) {
                current.getChild(index)?.let { child ->
                    try {
                        visit(child, depth + 1)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }

        try {
            visit(node, 0)
        } finally {
            node.recycle()
        }
        output.toString().takeIf(String::isNotBlank)
    }.getOrNull()
}
