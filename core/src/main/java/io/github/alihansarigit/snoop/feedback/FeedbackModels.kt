package io.github.alihansarigit.snoop.feedback

/**
 * Built-in feedback categories. Custom categories use [FeedbackCategory] directly and are
 * persisted alongside sessions.
 */
internal object BuiltInCategories {
    val Bugfix = FeedbackCategory("bugfix", "Bugfix", 0xFFDC2626.toInt())
    val Feature = FeedbackCategory("feature", "Feature", 0xFF16A34A.toInt())
    val Refactor = FeedbackCategory("refactor", "Refactor", 0xFF9333EA.toInt())
    val UiUx = FeedbackCategory("ui-ux", "UI/UX", 0xFFEAB308.toInt())
    val Performance = FeedbackCategory("performance", "Performance", 0xFF2563EB.toInt())

    val all: List<FeedbackCategory> = listOf(Bugfix, Feature, Refactor, UiUx, Performance)
}

internal data class FeedbackCategory(
    val id: String,
    val label: String,
    val colorArgb: Int,
)

internal data class Breadcrumb(
    val timestamp: Long,
    val type: String,
    val label: String,
    val x: Float? = null,
    val y: Float? = null,
    val metadata: Map<String, String> = emptyMap(),
)

internal data class FeedbackAttachment(
    val type: String,
    val relativePath: String,
    val mimeType: String,
    val createdAt: Long,
)

internal data class FeedbackScreenContext(
    val platformName: String,
    val routeName: String,
    val screenFileHint: String = "",
    val subScreen: String = "",
)

internal data class CapturedFeedbackPin(
    val id: Int,
    val xRatio: Float,
    val yRatio: Float,
    val componentName: String,
    val userNote: String,
    val timestamp: Long,
    val subScreen: String = "",
    val croppedRegionPng: List<ByteArray> = emptyList(),
)

internal data class FeedbackPin(
    val id: Int,
    val xRatio: Float,
    val yRatio: Float,
    val componentName: String,
    val userNote: String,
    val timestamp: Long,
    val subScreen: String = "",
    val croppedAttachmentPaths: List<String> = emptyList(),
)

internal data class FeedbackItem(
    val id: String,
    val createdAt: Long,
    val category: FeedbackCategory,
    val note: String,
    val attachments: List<FeedbackAttachment> = emptyList(),
    val breadcrumbs: List<Breadcrumb> = emptyList(),
    val semanticsSnapshot: String? = null,
    val networkTransactionIds: List<Long> = emptyList(),
    val stackTrace: String? = null,
    val deviceInfo: Map<String, String> = emptyMap(),
    val logs: List<String> = emptyList(),
    val screenContext: FeedbackScreenContext? = null,
    val pins: List<FeedbackPin> = emptyList(),
)

internal data class FeedbackSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val closedAt: Long? = null,
    val items: List<FeedbackItem> = emptyList(),
)

internal data class FeedbackSharePayload(
    val markdown: String,
    val attachments: List<FeedbackAttachment>,
)

internal data class PendingCrash(
    val timestamp: Long,
    val threadName: String,
    val stackTrace: String,
    val deviceInfo: Map<String, String>,
    val breadcrumbs: List<Breadcrumb>,
    val logs: List<String>,
    val screenshotRelativePath: String? = null,
)
