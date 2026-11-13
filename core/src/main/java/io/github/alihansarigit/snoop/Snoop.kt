package io.github.alihansarigit.snoop

import android.app.Application
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.view.View
import androidx.annotation.RestrictTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import io.github.alihansarigit.snoop.diagnostics.AccessibilitySnapshot
import io.github.alihansarigit.snoop.diagnostics.CrashCaptureHandler
import io.github.alihansarigit.snoop.diagnostics.DiagnosticLogBuffer
import io.github.alihansarigit.snoop.diagnostics.AnnotationStroke
import io.github.alihansarigit.snoop.diagnostics.ScreenshotCapture
import io.github.alihansarigit.snoop.diagnostics.UiTargetResolver
import io.github.alihansarigit.snoop.feedback.AgentMarkdownExporter
import io.github.alihansarigit.snoop.feedback.BreadcrumbRecorder
import io.github.alihansarigit.snoop.feedback.BuiltInCategories
import io.github.alihansarigit.snoop.feedback.CapturedFeedbackPin
import io.github.alihansarigit.snoop.feedback.DesktopBridgeClient
import io.github.alihansarigit.snoop.feedback.DesktopBridgeConnection
import io.github.alihansarigit.snoop.feedback.DesktopBridgeExport
import io.github.alihansarigit.snoop.feedback.FeedbackCategory
import io.github.alihansarigit.snoop.feedback.FeedbackSharePayload
import io.github.alihansarigit.snoop.feedback.FeedbackScreenContext
import io.github.alihansarigit.snoop.feedback.FeedbackSession
import io.github.alihansarigit.snoop.feedback.SessionStore
import io.github.alihansarigit.snoop.feedback.toJson
import io.github.alihansarigit.snoop.internal.OverlayManager
import io.github.alihansarigit.snoop.internal.DebugDestination
import io.github.alihansarigit.snoop.internal.OverlayState
import io.github.alihansarigit.snoop.network.NetworkMockRegistry
import io.github.alihansarigit.snoop.network.NetworkReplayRegistry
import io.github.alihansarigit.snoop.store.DiskPersistence
import io.github.alihansarigit.snoop.store.NetworkLogStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Public entry point for Snoop.
 *
 * Snoop auto-installs through `androidx.startup`, so for most apps no code is
 * needed beyond adding the capture interceptor. The methods here let you control
 * the bubble and extend the Debug Hub with custom sections, and [config] holds
 * runtime settings such as redaction and disk persistence.
 */
object Snoop {

    /**
     * Central runtime configuration — redaction, body decoding and disk persistence.
     * Safe to touch from any thread; changes apply to later captures.
     */
    @JvmField
    val config: SnoopConfig = SnoopConfig().apply {
        onPersistenceToggled = { enabled ->
            if (enabled) startPersistence() else stopPersistence()
        }
    }

    /** Shared capture store. Used by capture adapters; not part of the app-facing API. */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    val store: NetworkLogStore = NetworkLogStore().apply { redactor = config::redact }

    /** Whether the floating bubble is shown. Backed by observable overlay state. */
    var enabled: Boolean
        get() = OverlayState.visible
        set(value) {
            OverlayState.visible = value
        }

    /**
     * Whether shaking the device brings a hidden bubble back on screen. Default `true`.
     * Set to `false` to disable it — e.g. if the host app has its own shake handling.
     * Takes effect immediately.
     */
    var shakeToShow: Boolean
        get() = OverlayState.shakeToShowEnabled
        set(value) {
            OverlayState.shakeToShowEnabled = value
            overlayManager?.onShakeSettingChanged()
        }

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal val customSections = mutableStateListOf<DebugSection>()

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal val developerActions = mutableStateListOf<DeveloperAction>()

    private var overlayManager: OverlayManager? = null
    private var appContext: Context? = null
    private var diskPersistence: DiskPersistence? = null
    private var sessionStore: SessionStore? = null
    private var crashHandler: CrashCaptureHandler? = null
    @Volatile
    private var feedbackScreenContextOverride: FeedbackScreenContext? = null
    private val captureScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal val feedbackCategories = mutableStateListOf<FeedbackCategory>().apply {
        addAll(BuiltInCategories.all)
    }

    /** Attach the overlay to every activity. Idempotent. */
    @JvmStatic
    fun install(application: Application) {
        if (overlayManager != null) return
        appContext = application.applicationContext
        val feedbackStore = SessionStore(File(application.filesDir, "snoop/feedback"))
        sessionStore = feedbackStore
        feedbackCategories.removeAll { it.id.startsWith("custom-") }
        feedbackCategories.addAll(feedbackStore.customCategories())
        val manager = OverlayManager()
        application.registerActivityLifecycleCallbacks(manager)
        overlayManager = manager
        OverlayState.pendingCrash = feedbackStore.readPendingCrash()
        OverlayState.pendingCrashBitmap = OverlayState.pendingCrash
            ?.let(feedbackStore::readPendingCrashScreenshot)
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        if (OverlayState.pendingCrash != null) OverlayState.visible = true
        if (config.crashCaptureEnabled) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            CrashCaptureHandler(application.applicationContext, feedbackStore, previous).also {
                crashHandler = it
                Thread.setDefaultUncaughtExceptionHandler(it)
            }
        }
        if (config.logcatCaptureEnabled) DiagnosticLogBuffer.startLogcatBestEffort()
        if (config.persistenceEnabled) startPersistence()
    }

    /** Show the bubble again after [hide]. */
    @JvmStatic
    fun show() {
        OverlayState.visible = true
    }

    /** Hide the bubble (the Debug Hub's "Hide overlay" action does this). Restore with [show]. */
    @JvmStatic
    fun hide() {
        OverlayState.destination = DebugDestination.CLOSED
        OverlayState.visible = false
    }

    @JvmStatic
    fun clear() {
        store.clear()
    }

    /** Open the network inspector directly, bypassing the Debug Hub. */
    @JvmStatic
    fun launchInspector(context: Context) {
        OverlayState.visible = true
        OverlayState.destination = DebugDestination.INSPECTOR
    }

    /** Capture the foreground activity and open the visual feedback editor. */
    @JvmStatic
    fun launchCapture(context: Context) {
        val activity = context as? Activity ?: overlayManager?.currentActivity() ?: return
        if (OverlayState.captureBusy) return
        OverlayState.visible = true
        OverlayState.captureBusy = true
        OverlayState.destination = DebugDestination.CLOSED
        captureScope.launch {
            awaitOverlayDismissed(activity.window.decorView)
            OverlayState.captureTargets = UiTargetResolver.capture(activity.window.decorView)
            val contextOverride = feedbackScreenContextOverride
            OverlayState.captureScreenContext = FeedbackScreenContext(
                platformName = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                routeName = contextOverride?.routeName?.takeIf(String::isNotBlank)
                    ?: activity.javaClass.name,
                screenFileHint = contextOverride?.screenFileHint.orEmpty(),
                subScreen = contextOverride?.subScreen.orEmpty(),
            )
            OverlayState.captureBitmap?.recycle()
            OverlayState.captureBitmap = ScreenshotCapture.capture(activity)?.bitmap
            OverlayState.captureBusy = false
            OverlayState.destination = DebugDestination.CAPTURE_FEEDBACK
        }
    }

    /** Supply Compose route, tab and optional source-file hints for later feedback captures. */
    @JvmStatic
    @JvmOverloads
    fun setFeedbackScreenContext(
        routeName: String,
        subScreen: String = "",
        screenFileHint: String = "",
    ) {
        feedbackScreenContextOverride = FeedbackScreenContext(
            platformName = "",
            routeName = routeName.trim().take(200),
            screenFileHint = screenFileHint.trim().take(300),
            subScreen = subScreen.trim().take(120),
        )
    }

    @JvmStatic
    fun clearFeedbackScreenContext() {
        feedbackScreenContextOverride = null
    }

    /** Start and activate a named feedback session. Returns its stable session id. */
    @JvmStatic
    fun startSession(name: String): String =
        sessionStore?.start(name)?.id.orEmpty()

    /** Resume an existing open session. */
    @JvmStatic
    fun resumeSession(sessionId: String): Boolean =
        sessionStore?.resume(sessionId) != null

    /**
     * Close the active session and write its AI-ready Markdown next to its attachments.
     * Returns the generated absolute path.
     */
    @JvmStatic
    fun closeSession(): String? {
        val feedbackStore = sessionStore ?: return null
        val session = feedbackStore.closeActive() ?: return null
        val markdown = AgentMarkdownExporter.export(session, store.transactions.value)
        return feedbackStore.writeExport(session.id, markdown).absolutePath
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun closeActiveSession(): Boolean =
        sessionStore?.closeActive() != null

    /** Generate AI-ready Markdown without closing the session. */
    @JvmStatic
    fun exportSession(sessionId: String): String? {
        val session = sessionStore?.find(sessionId) ?: return null
        return AgentMarkdownExporter.export(session, store.transactions.value)
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun createAgentBundle(sessionId: String): String? {
        return createAgentBundleFile(sessionId)?.name
    }

    private fun createAgentBundleFile(sessionId: String): File? {
        val feedbackStore = sessionStore ?: return null
        val session = feedbackStore.find(sessionId) ?: return null
        val markdown = AgentMarkdownExporter.export(session, store.transactions.value)
        feedbackStore.writeExport(session.id, markdown)
        return feedbackStore.writeAgentBundle(
            sessionId = session.id,
            markdown = markdown,
            attachmentPaths = session.items
                .flatMap { it.attachments }
                .map { it.relativePath },
        )
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun desktopBridgeStatus(): DesktopBridgeConnection {
        val context = appContext
            ?: return DesktopBridgeConnection(false, message = "Snoop is not installed")
        return DesktopBridgeClient.status(
            packageName = context.packageName,
            port = config.desktopBridgePort,
        )
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun sendSessionToProject(sessionId: String): DesktopBridgeExport {
        val context = checkNotNull(appContext) { "Snoop is not installed" }
        val bundle = checkNotNull(createAgentBundleFile(sessionId)) {
            "Agent export could not be created"
        }
        return DesktopBridgeClient.export(
            packageName = context.packageName,
            port = config.desktopBridgePort,
            sessionId = sessionId,
            archive = bundle,
        )
    }

    /**
     * Names a partial export after the pins it carries, e.g. `pin2-5-7`. The daemon files
     * it beside the session's `full` export rather than replacing it.
     */
    private fun selectionLabel(pinIdsByItem: Map<String, Set<Int>>): String =
        pinIdsByItem.values
            .flatten()
            .distinct()
            .sorted()
            .joinToString(separator = "-", prefix = "pin")

    /** Narrows [sessionId] to the pins in [pinIdsByItem]. Null when nothing is selected. */
    private fun selectedPinsSession(
        sessionId: String,
        pinIdsByItem: Map<String, Set<Int>>,
    ): FeedbackSession? {
        val session = sessionStore?.find(sessionId) ?: return null
        val selectedItems = session.items.mapNotNull { item ->
            val selectedIds = pinIdsByItem[item.id].orEmpty()
            val selectedPins = item.pins.filter { it.id in selectedIds }
            if (selectedPins.isEmpty()) return@mapNotNull null
            val selectedPaths = selectedPins
                .flatMap { it.croppedAttachmentPaths }
                .toSet()
            item.copy(
                pins = selectedPins,
                attachments = item.attachments.filter { attachment ->
                    attachment.type != "selected-region" ||
                        attachment.relativePath in selectedPaths
                },
            )
        }
        if (selectedItems.isEmpty()) return null
        return session.copy(items = selectedItems)
    }

    private fun FeedbackSession.selectedAttachmentPaths(): List<String> =
        items.flatMap { it.attachments }.map { it.relativePath }.distinct()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun exportSelectedPins(
        sessionId: String,
        pinIdsByItem: Map<String, Set<Int>>,
    ): FeedbackSharePayload? {
        val filteredSession = selectedPinsSession(sessionId, pinIdsByItem) ?: return null
        return FeedbackSharePayload(
            markdown = AgentMarkdownExporter.export(
                filteredSession,
                store.transactions.value,
            ),
            attachments = filteredSession.items
                .flatMap { it.attachments }
                .distinctBy { it.relativePath },
        )
    }

    /**
     * Transfers only the selected pins to the desktop project. It lands in its own
     * `<session-id>-selection` directory so a partial export never replaces the
     * full-session export the daemon already wrote.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun sendSelectedPinsToProject(
        sessionId: String,
        pinIdsByItem: Map<String, Set<Int>>,
    ): DesktopBridgeExport {
        val context = checkNotNull(appContext) { "Snoop is not installed" }
        val feedbackStore = checkNotNull(sessionStore) { "Snoop is not installed" }
        val filteredSession = checkNotNull(selectedPinsSession(sessionId, pinIdsByItem)) {
            "Select at least one pin"
        }
        val markdown = AgentMarkdownExporter.export(filteredSession, store.transactions.value)
        val selection = selectionLabel(pinIdsByItem)
        val bundle = feedbackStore.writeAgentBundle(
            sessionId = sessionId,
            markdown = markdown,
            attachmentPaths = filteredSession.selectedAttachmentPaths(),
            bundleName = "$sessionId-$selection-agent-feedback.zip",
            manifestJson = filteredSession.toJson().toString(2),
        )
        return DesktopBridgeClient.export(
            packageName = context.packageName,
            port = config.desktopBridgePort,
            sessionId = sessionId,
            archive = bundle,
            selection = selection,
        )
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun exportFeedbackItem(sessionId: String, itemId: String): String? {
        val session = sessionStore?.find(sessionId) ?: return null
        val item = session.items.firstOrNull { it.id == itemId } ?: return null
        return AgentMarkdownExporter.export(
            session.copy(items = listOf(item)),
            store.transactions.value,
        )
    }

    @JvmStatic
    fun deleteSession(sessionId: String): Boolean =
        sessionStore?.delete(sessionId) == true

    /** Stable, privacy-safe breadcrumb hook for host navigation and important UI actions. */
    @JvmStatic
    @JvmOverloads
    fun recordAction(
        tag: String,
        action: String = "action",
        metadata: Map<String, String> = emptyMap(),
    ) {
        BreadcrumbRecorder.record(action, tag, metadata = metadata)
    }

    /** Add an application log to the crash-safe 30-second ring buffer. */
    @JvmStatic
    @JvmOverloads
    fun log(tag: String, message: String, level: String = "I") {
        DiagnosticLogBuffer.add(level, tag, message)
    }

    @JvmStatic
    fun startLogCapture() = DiagnosticLogBuffer.startManualCapture()

    @JvmStatic
    fun stopLogCapture(): List<String> = DiagnosticLogBuffer.stopManualCapture()

    /** Register a debug-only response override. `*` wildcards are accepted in [urlPattern]. */
    @JvmStatic
    @JvmOverloads
    fun addNetworkMock(
        method: String,
        urlPattern: String,
        statusCode: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): String = NetworkMockRegistry.add(method, urlPattern, statusCode, body, headers)

    @JvmStatic
    fun removeNetworkMock(id: String): Boolean = NetworkMockRegistry.remove(id)

    @JvmStatic
    fun clearNetworkMocks() = NetworkMockRegistry.clear()

    /** Add and persist a custom feedback category. Returns its stable id. */
    @JvmStatic
    fun addFeedbackCategory(label: String, colorArgb: Int): String {
        val category = sessionStore?.addCustomCategory(label, colorArgb) ?: return ""
        feedbackCategories.add(category)
        return category.id
    }

    @JvmStatic
    fun replay(transactionId: Long): Boolean = NetworkReplayRegistry.replay(transactionId)

    /**
     * Add a custom Compose section to the Debug Hub — e.g. an environment switch
     * or a feature-flag toggle. Re-registering the same [title] replaces it.
     */
    fun registerSection(title: String, content: @Composable () -> Unit) {
        customSections.removeAll { it.title == title }
        customSections.add(DebugSection(title, content))
    }

    /** Register a host-owned environment switch or other safe debug action. */
    @JvmStatic
    fun registerDeveloperAction(title: String, action: Runnable) {
        developerActions.removeAll { it.title == title }
        developerActions.add(DeveloperAction(title.take(60), action))
    }

    /** Relaunch the host's default activity with a clean task. */
    @JvmStatic
    fun restartApp(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launchIntent.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK,
        )
        context.startActivity(launchIntent)
        (context as? Activity)?.finishAffinity()
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun activeSessionId(): String? = sessionStore?.active?.value?.id

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun feedbackSessionSnapshot(): List<FeedbackSession> =
        sessionStore?.sessions?.value.orEmpty()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun feedbackAttachment(sessionId: String, relativePath: String): ByteArray? =
        sessionStore?.readAttachment(sessionId, relativePath)

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun openSessionManager() {
        OverlayState.destination = DebugDestination.SESSIONS
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun openDebugHub() {
        OverlayState.destination = DebugDestination.HUB
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun openNetworkTools() {
        OverlayState.destination = DebugDestination.NETWORK_TOOLS
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun closeDebugUi() {
        OverlayState.destination = DebugDestination.CLOSED
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun isLogCaptureActive(): Boolean = DiagnosticLogBuffer.isManualCaptureActive()

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun saveCapturedFeedback(
        sessionId: String?,
        note: String,
        category: FeedbackCategory,
        strokes: List<AnnotationStroke>,
        pins: List<CapturedFeedbackPin>,
    ): Boolean {
        val bitmap = OverlayState.captureBitmap
        val png = bitmap?.let { ScreenshotCapture.renderAnnotations(it, strokes) }
        val activity = overlayManager?.currentActivity()
        val feedbackStore = sessionStore ?: return false
        val semantics = activity?.window?.decorView?.let(AccessibilitySnapshot::capture)
        val transactionIds = store.transactions.value.take(5).map { it.id }
        val logs = DiagnosticLogBuffer.snapshot()
        val saved = if (sessionId == null) {
            feedbackStore.addFeedback(
                note = note,
                category = category,
                screenshotPng = png,
                semanticsSnapshot = semantics,
                networkTransactionIds = transactionIds,
                logs = logs,
                screenContext = OverlayState.captureScreenContext,
                capturedPins = pins,
            )
        } else {
            feedbackStore.addFeedbackToSession(
                sessionId = sessionId,
                note = note,
                category = category,
                screenshotPng = png,
                semanticsSnapshot = semantics,
                networkTransactionIds = transactionIds,
                logs = logs,
                screenContext = OverlayState.captureScreenContext,
                capturedPins = pins,
            )
        }
        if (saved != null) dismissCapture()
        return saved != null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun dismissCapture(returnToHub: Boolean = true) {
        OverlayState.captureBitmap?.recycle()
        OverlayState.captureBitmap = null
        OverlayState.captureTargets = emptyList()
        OverlayState.captureScreenContext = null
        OverlayState.destination = if (returnToHub) {
            DebugDestination.HUB
        } else {
            DebugDestination.CLOSED
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun attachPendingCrash() {
        sessionStore?.attachPendingCrash()
        OverlayState.pendingCrashBitmap?.recycle()
        OverlayState.pendingCrashBitmap = null
        OverlayState.pendingCrash = null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun dismissPendingCrash() {
        sessionStore?.clearPendingCrash()
        OverlayState.pendingCrashBitmap?.recycle()
        OverlayState.pendingCrashBitmap = null
        OverlayState.pendingCrash = null
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    internal fun pendingCrashMarkdown(): String? {
        val crash = OverlayState.pendingCrash ?: return null
        return buildString {
            append("# Snoop crash report\n\n")
            append("Thread: `").append(crash.threadName).append("`\n\n")
            append("## Stack trace\n\n```text\n")
            append(crash.stackTrace.replace("```", "'''"))
            append("\n```\n\n")
            append("## Device\n\n")
            crash.deviceInfo.forEach { (key, value) ->
                append("- ").append(key).append(": `").append(value.replace("`", "'")).append("`\n")
            }
            append("\n## Recent logs\n\n```text\n")
            append(crash.logs.joinToString("\n").replace("```", "'''"))
            append("\n```\n")
        }
    }

    private suspend fun awaitOverlayDismissed(view: View) {
        repeat(2) {
            suspendCancellableCoroutine { continuation ->
                view.postOnAnimation {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }

    /**
     * Start disk persistence, restoring any retained transactions. Requires [install] to
     * have captured an application context (it has, once App Startup runs). Idempotent, and
     * invoked automatically when [SnoopConfig.persistenceEnabled] flips to `true`.
     */
    @Synchronized
    internal fun startPersistence() {
        if (diskPersistence != null) return
        val context = appContext ?: return
        val file = File(File(context.filesDir, "snoop"), "transactions.json")
        val persistence = DiskPersistence(file, config)
        store.restore(persistence.load())
        persistence.observe(store.transactions)
        diskPersistence = persistence
    }

    /** Stop disk persistence and release its background scope. Data already on disk is kept. */
    @Synchronized
    internal fun stopPersistence() {
        diskPersistence?.close()
        diskPersistence = null
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class DebugSection(
    val title: String,
    val content: @Composable () -> Unit,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class DeveloperAction(
    val title: String,
    val action: Runnable,
)
