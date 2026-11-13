package io.github.alihansarigit.snoop.feedback

import org.json.JSONArray
import org.json.JSONObject

internal fun FeedbackSession.toJson(): JSONObject = JSONObject().apply {
    put("schemaVersion", SESSION_SCHEMA_VERSION)
    put("id", id)
    put("name", name)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    closedAt?.let { put("closedAt", it) }
    put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
}

internal fun JSONObject.toFeedbackSession(): FeedbackSession? = runCatching {
    FeedbackSession(
        id = getString("id"),
        name = getString("name"),
        createdAt = getLong("createdAt"),
        updatedAt = getLong("updatedAt"),
        closedAt = if (has("closedAt")) getLong("closedAt") else null,
        items = optJSONArray("items").toFeedbackItems(),
    )
}.getOrNull()

internal fun PendingCrash.toJson(): JSONObject = JSONObject().apply {
    put("schemaVersion", SESSION_SCHEMA_VERSION)
    put("timestamp", timestamp)
    put("threadName", threadName)
    put("stackTrace", stackTrace)
    put("deviceInfo", deviceInfo.toJsonObject())
    put("breadcrumbs", JSONArray().apply { breadcrumbs.forEach { put(it.toJson()) } })
    put("logs", JSONArray(logs))
    screenshotRelativePath?.let { put("screenshotRelativePath", it) }
}

internal fun JSONObject.toPendingCrash(): PendingCrash? = runCatching {
    PendingCrash(
        timestamp = getLong("timestamp"),
        threadName = optString("threadName", "unknown"),
        stackTrace = getString("stackTrace"),
        deviceInfo = optJSONObject("deviceInfo").toStringMap(),
        breadcrumbs = optJSONArray("breadcrumbs").toBreadcrumbs(),
        logs = optJSONArray("logs").toStringList(),
        screenshotRelativePath = optNullableString("screenshotRelativePath"),
    )
}.getOrNull()

private fun FeedbackItem.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("createdAt", createdAt)
    put("category", category.toJson())
    put("note", note)
    put("attachments", JSONArray().apply { attachments.forEach { put(it.toJson()) } })
    put("breadcrumbs", JSONArray().apply { breadcrumbs.forEach { put(it.toJson()) } })
    semanticsSnapshot?.let { put("semanticsSnapshot", it) }
    put("networkTransactionIds", JSONArray(networkTransactionIds))
    stackTrace?.let { put("stackTrace", it) }
    put("deviceInfo", deviceInfo.toJsonObject())
    put("logs", JSONArray(logs))
    screenContext?.let { put("screenContext", it.toJson()) }
    put("pins", JSONArray().apply { pins.forEach { put(it.toJson()) } })
}

private fun JSONObject.toFeedbackItem(): FeedbackItem? = runCatching {
    FeedbackItem(
        id = getString("id"),
        createdAt = getLong("createdAt"),
        category = getJSONObject("category").toFeedbackCategory(),
        note = optString("note"),
        attachments = optJSONArray("attachments").toAttachments(),
        breadcrumbs = optJSONArray("breadcrumbs").toBreadcrumbs(),
        semanticsSnapshot = optNullableString("semanticsSnapshot"),
        networkTransactionIds = optJSONArray("networkTransactionIds").toLongList(),
        stackTrace = optNullableString("stackTrace"),
        deviceInfo = optJSONObject("deviceInfo").toStringMap(),
        logs = optJSONArray("logs").toStringList(),
        screenContext = optJSONObject("screenContext")?.toFeedbackScreenContext(),
        pins = optJSONArray("pins").toFeedbackPins(),
    )
}.getOrNull()

private fun FeedbackScreenContext.toJson(): JSONObject = JSONObject().apply {
    put("platformName", platformName)
    put("routeName", routeName)
    put("screenFileHint", screenFileHint)
    put("subScreen", subScreen)
}

private fun JSONObject.toFeedbackScreenContext(): FeedbackScreenContext = FeedbackScreenContext(
    platformName = optString("platformName"),
    routeName = optString("routeName"),
    screenFileHint = optString("screenFileHint"),
    subScreen = optString("subScreen"),
)

private fun FeedbackPin.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("xRatio", xRatio)
    put("yRatio", yRatio)
    put("componentName", componentName)
    put("userNote", userNote)
    put("timestamp", timestamp)
    put("subScreen", subScreen)
    put("croppedAttachmentPaths", JSONArray(croppedAttachmentPaths))
}

private fun JSONObject.toFeedbackPin(): FeedbackPin? = runCatching {
    FeedbackPin(
        id = getInt("id"),
        xRatio = getDouble("xRatio").toFloat(),
        yRatio = getDouble("yRatio").toFloat(),
        componentName = optString("componentName"),
        userNote = optString("userNote"),
        timestamp = getLong("timestamp"),
        subScreen = optString("subScreen"),
        croppedAttachmentPaths = optJSONArray("croppedAttachmentPaths").toStringList(),
    )
}.getOrNull()

private fun FeedbackCategory.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("label", label)
    put("colorArgb", colorArgb)
}

private fun JSONObject.toFeedbackCategory(): FeedbackCategory = FeedbackCategory(
    id = getString("id"),
    label = getString("label"),
    colorArgb = getInt("colorArgb"),
)

private fun FeedbackAttachment.toJson(): JSONObject = JSONObject().apply {
    put("type", type)
    put("relativePath", relativePath)
    put("mimeType", mimeType)
    put("createdAt", createdAt)
}

private fun Breadcrumb.toJson(): JSONObject = JSONObject().apply {
    put("timestamp", timestamp)
    put("type", type)
    put("label", label)
    x?.let { put("x", it) }
    y?.let { put("y", it) }
    put("metadata", metadata.toJsonObject())
}

private fun JSONObject.toBreadcrumb(): Breadcrumb? = runCatching {
    Breadcrumb(
        timestamp = getLong("timestamp"),
        type = getString("type"),
        label = getString("label"),
        x = if (has("x")) getDouble("x").toFloat() else null,
        y = if (has("y")) getDouble("y").toFloat() else null,
        metadata = optJSONObject("metadata").toStringMap(),
    )
}.getOrNull()

private fun Map<String, String>.toJsonObject(): JSONObject = JSONObject().apply {
    forEach { (key, value) -> put(key, value) }
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    return keys().asSequence().associateWith { optString(it) }
}

private fun JSONArray?.toFeedbackItems(): List<FeedbackItem> =
    mapObjects { it.toFeedbackItem() }

private fun JSONArray?.toAttachments(): List<FeedbackAttachment> =
    mapObjects {
        runCatching {
            FeedbackAttachment(
                type = it.getString("type"),
                relativePath = it.getString("relativePath"),
                mimeType = it.getString("mimeType"),
                createdAt = it.getLong("createdAt"),
            )
        }.getOrNull()
    }

private fun JSONArray?.toBreadcrumbs(): List<Breadcrumb> = mapObjects { it.toBreadcrumb() }

private fun JSONArray?.toFeedbackPins(): List<FeedbackPin> = mapObjects { it.toFeedbackPin() }

private fun JSONArray?.toLongList(): List<Long> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
        runCatching { getLong(index) }.getOrNull()
    }

private fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T?): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private const val SESSION_SCHEMA_VERSION = 1
